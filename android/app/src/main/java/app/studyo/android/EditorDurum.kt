package app.studyo.android

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/** Bir projenin düzenleme durumu + önizleme oynatıcıları (ExoPlayer). Ekran kapanınca kapat() çağrılır. */
class EditorDurum(private val ctx: Context, id: String) {
    val kapsam = MainScope()
    val proje = MutableStateFlow(Depo.yukle(id) ?: Proje(id = id).also { Depo.kaydet(it) })
    val secili = MutableStateFlow(0)
    val sira = MutableStateFlow(0)          // önizlemede oynayan klip
    val konum = MutableStateFlow(0L)        // zaman çizelgesi (ms)
    val oynuyor = MutableStateFlow(false)
    val mesaj = MutableStateFlow<String?>(null)
    val uyari = MutableStateFlow<String?>(null)
    val mesgul = MutableStateFlow<String?>(null)
    val kaydediyor = MutableStateFlow(false)
    val kayitBas = MutableStateFlow(0L)

    val player: ExoPlayer = ExoPlayer.Builder(ctx).build()
    private val muzikPl: ExoPlayer = ExoPlayer.Builder(ctx).build()
    private val sesPl: ExoPlayer = ExoPlayer.Builder(ctx).build()
    private var kaydetIs: Job? = null
    private var tikIs: Job? = null
    private var sesAktif: String? = null
    private var kaydedici: MediaRecorder? = null
    private var kayitDosya: File? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sira.value = player.currentMediaItemIndex
                if (player.isPlaying) secili.value = player.currentMediaItemIndex
                sesUygula()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                oynuyor.value = isPlaying
                if (isPlaying) tikBasla() else { muzikPl.pause(); sesPl.pause(); sesAktif = null; konum.value = anlik() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (kaydediyor.value) kayitDurdur()
                    muzikPl.pause(); sesPl.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Kayit.hata("önizleme", error)
                mesaj.value = "Önizleme oynatılamadı (${error.errorCodeName}). Dışa aktarma ayrı motorla yapılır."
            }
        })
        listeKur(0, 0)
        muzikKur()
    }

    // ---------- zaman çizelgesi ----------
    fun baslangic(i: Int): Long = proje.value.klipler.take(i.coerceAtLeast(0)).sumOf { it.sureMs }
    fun anlik(): Long = if (proje.value.klipler.isEmpty()) 0 else baslangic(player.currentMediaItemIndex) + player.currentPosition.coerceAtLeast(0)

    private fun ogeler(p: Proje) = p.klipler.map { k ->
        val b = MediaItem.Builder().setUri(Uri.parse(k.uri)).setMediaId(k.id)
        if (k.foto) b.setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(k.fotoSureMs)
        else b.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(k.basMs).setEndPositionMs(k.sonMs).build())
        b.build()
    }

    fun listeKur(i: Int = player.currentMediaItemIndex, ms: Long = player.currentPosition) {
        val p = proje.value
        if (p.klipler.isEmpty()) { player.clearMediaItems(); konum.value = 0; return }
        val ii = i.coerceIn(0, p.klipler.lastIndex)
        player.setMediaItems(ogeler(p), ii, ms.coerceIn(0, (p.klipler[ii].sureMs - 1).coerceAtLeast(0)))
        player.prepare()
        sira.value = ii
        sesUygula()
    }

    /** Dışa aktarma sırasında donanım çözücüleri boşaltılır (büyük videolarda çakışmayı önler). */
    fun onizlemeBirak() { player.pause(); player.stop(); muzikPl.stop(); sesPl.stop() }
    fun onizlemeGeri() { listeKur(sira.value, 0); muzikKur() }

    private fun sesUygula() {
        val k = proje.value.klipler.getOrNull(player.currentMediaItemIndex)
        player.volume = if (k == null || kaydediyor.value || k.sessiz) 0f else k.ses.coerceIn(0f, 1f)
    }

    fun git(ms: Long) {
        val p = proje.value
        if (p.klipler.isEmpty()) return
        var t = ms.coerceIn(0, (p.toplamMs - 1).coerceAtLeast(0))
        for ((i, k) in p.klipler.withIndex()) {
            if (t < k.sureMs || i == p.klipler.lastIndex) { player.seekTo(i, t.coerceIn(0, (k.sureMs - 1).coerceAtLeast(0))); break }
            t -= k.sureMs
        }
        konum.value = ms
        muzikHizala()
    }

    private fun tikBasla() {
        tikIs?.cancel()
        tikIs = kapsam.launch {
            muzikHizala()
            while (isActive && player.isPlaying) {
                konum.value = anlik()
                sesTakip()
                delay(40)
            }
        }
    }

    fun oynatDurdur() {
        if (player.isPlaying) { player.pause(); return }
        if (player.playbackState == Player.STATE_ENDED) git(0)
        player.play()
    }

    fun sec(i: Int) {
        if (i !in proje.value.klipler.indices) return
        secili.value = i
        git(baslangic(i))
    }
    fun onceki() = sec((secili.value - 1).coerceAtLeast(0))
    fun sonraki() = sec((secili.value + 1).coerceAtMost(proje.value.klipler.lastIndex))

    // ---------- müzik / seslendirme önizlemesi ----------
    private fun muzikKur() {
        val m = proje.value.muzik
        if (m == null) { muzikPl.clearMediaItems(); return }
        muzikPl.setMediaItem(MediaItem.fromUri(m.uri))
        muzikPl.repeatMode = if (m.dongu) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        muzikPl.volume = m.ses.coerceIn(0f, 1f)
        muzikPl.prepare()
    }

    private fun muzikHizala() {
        val m = proje.value.muzik ?: return
        if (kaydediyor.value) { muzikPl.pause(); return }
        val t = anlik()
        val d = if (m.sureMs > 0) m.sureMs else muzikPl.duration.takeIf { it > 0 } ?: 0L
        if (!m.dongu && d > 0 && t >= d) { muzikPl.pause(); return }
        muzikPl.seekTo(if (m.dongu && d > 0) t % d else t)
        if (player.isPlaying) muzikPl.play() else muzikPl.pause()
    }

    private fun sesTakip() {
        if (kaydediyor.value) return
        val t = anlik()
        val s = proje.value.sesler.firstOrNull { t >= it.basMs && t < it.basMs + it.sureMs }
        if (s == null) {
            if (sesAktif != null) { sesPl.pause(); sesAktif = null }
            return
        }
        if (sesAktif != s.id) {
            sesAktif = s.id
            sesPl.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(s.yol))))
            sesPl.volume = s.ses.coerceIn(0f, 1f)
            sesPl.prepare(); sesPl.seekTo(t - s.basMs); sesPl.play()
        }
    }

    // ---------- düzenleme ----------
    fun guncelle(yapi: Boolean = false, f: (Proje) -> Proje) {
        val i = player.currentMediaItemIndex; val ms = player.currentPosition
        proje.value = f(proje.value).copy(guncelleme = System.currentTimeMillis())
        if (yapi) listeKur(i, ms) else sesUygula()
        kaydetPlanla()
    }

    private fun kaydetPlanla() {
        kaydetIs?.cancel()
        kaydetIs = kapsam.launch {
            delay(800)
            val p = proje.value
            withContext(Dispatchers.IO) { Depo.kaydet(p) }
        }
    }

    /** Hemen kaydeder (Kaydet düğmesi, ekrandan çıkış, dışa aktarma öncesi). */
    fun kaydetSimdi(bildir: Boolean = true) {
        kaydetIs?.cancel()
        Depo.kaydet(proje.value)
        if (bildir) mesaj.value = "Kaydedildi"
    }

    fun klipGuncelle(yapi: Boolean = false, f: (Klip) -> Klip) {
        val s = secili.value
        guncelle(yapi) { p -> if (s !in p.klipler.indices) p else p.copy(klipler = p.klipler.toMutableList().also { it[s] = f(it[s]) }) }
    }

    fun hepsine(f: (Klip) -> Klip, yapi: Boolean = false) = guncelle(yapi) { p -> p.copy(klipler = p.klipler.map(f)) }

    fun kirp(bas: Long, son: Long) {
        val s = secili.value
        val k = proje.value.klipler.getOrNull(s) ?: return
        val yeniBas = bas.coerceIn(0, (k.kaynakSureMs - 200).coerceAtLeast(0))
        val yeniSon = son.coerceIn(yeniBas + 200, k.kaynakSureMs.coerceAtLeast(yeniBas + 200))
        val basDegisti = yeniBas != k.basMs
        klipGuncelle(true) { it.copy(basMs = yeniBas, sonMs = yeniSon) }
        val st = baslangic(s)
        git(if (basDegisti) st else st + (yeniSon - yeniBas - 1500).coerceAtLeast(0))
    }

    fun tasi(yon: Int) {
        val s = secili.value; val h = s + yon; val n = proje.value.klipler.size
        if (s !in 0 until n || h !in 0 until n) return
        guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { Collections.swap(it, s, h) }) }
        sec(h)
    }

    fun klipCogalt() {
        val s = secili.value
        if (s !in proje.value.klipler.indices) return
        guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { it.add(s + 1, it[s].copy(id = yeniId())) }) }
        sec(s + 1)
    }

    fun klipSil() {
        val s = secili.value
        if (s !in proje.value.klipler.indices) return
        guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { it.removeAt(s) }) }
        val n = proje.value.klipler.size
        if (n > 0) sec(s.coerceAtMost(n - 1)) else secili.value = 0
    }

    fun adDegistir(ad: String) = guncelle { it.copy(ad = ad.ifBlank { "Video" }) }
    fun bicim(b: String) = guncelle { it.copy(bicim = b) }

    // ---------- içe aktarma ----------
    fun ekle(uriler: List<Uri>) {
        if (uriler.isEmpty()) return
        kapsam.launch {
            val yeni = ArrayList<Klip>(); val sorunlar = ArrayList<String>()
            for ((n, u) in uriler.withIndex()) {
                mesgul.value = "Ekleniyor ${n + 1}/${uriler.size}…"
                val sonuc = withContext(Dispatchers.IO) { runCatching { klipHazirla(u) } }
                sonuc.onSuccess { (k, sorun) -> if (k != null) yeni += k; if (sorun != null) sorunlar += sorun }
                    .onFailure { e -> Kayit.hata("ekle", e); sorunlar += "${u.lastPathSegment}: ${e.message ?: e.javaClass.simpleName}" }
            }
            mesgul.value = null
            if (yeni.isNotEmpty()) {
                val s = if (proje.value.klipler.isEmpty()) 0 else (secili.value + 1).coerceAtMost(proje.value.klipler.size)
                guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { it.addAll(s, yeni) }) }
                kaydetSimdi(false)
                sec(s)
                mesaj.value = "${yeni.size} klip eklendi"
            }
            if (sorunlar.isNotEmpty()) uyari.value = "Eklenemeyenler:\n\n" + sorunlar.joinToString("\n\n")
        }
    }

    private fun klipHazirla(u0: Uri): Pair<Klip?, String?> {
        val cr = ctx.contentResolver
        val tip = (if (u0.scheme == ContentResolver.SCHEME_CONTENT) cr.getType(u0) else null) ?: ""
        val (ad, boy) = Medya.adBoyut(ctx, u0)
        val kucuk = ad.lowercase()
        val foto = tip.startsWith("image/") || (tip.isEmpty() && listOf(".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif").any { kucuk.endsWith(it) })
        if (foto) {
            val r = Medya.fotoHazirla(ctx, u0) ?: return null to "$ad: fotoğraf açılamadı (desteklenmeyen biçim)."
            Kayit.yaz("Fotoğraf eklendi: $ad ${Medya.mb(boy)} → ${r.second}x${r.third}")
            return Klip(tur = "foto", uri = Uri.fromFile(r.first).toString(), ad = ad, gen = r.second, yuk = r.third, boyutBayt = boy) to null
        }
        var u = u0
        val kalici = try { cr.takePersistableUriPermission(u0, Intent.FLAG_GRANT_READ_URI_PERMISSION); true } catch (e: Exception) { false }
        if (!kalici && u0.scheme == ContentResolver.SCHEME_CONTENT) {
            u = try { Medya.kopyala(ctx, u0, ad, boy) } catch (e: Exception) { return null to "$ad: kopyalanamadı — ${e.message}" }
                ?: return null to "$ad: dosya okunamadı."
            Kayit.yaz("Kalıcı izin yok, uygulamaya kopyalandı: $ad")
        }
        val b = Medya.incele(ctx, u)
        Kayit.yaz("Video eklendi: $ad ${Medya.mb(boy)} ${b.gen}x${b.yuk} ${b.kodek} hdr=${b.hdr} ses=${b.sesVar} ${b.sureMs} ms sorun=${b.sorun}")
        if (b.sorun != null) return null to "$ad: ${b.sorun}"
        return Klip(tur = "video", uri = u.toString(), ad = ad, kaynakSureMs = b.sureMs, basMs = 0, sonMs = b.sureMs,
            gen = b.gen, yuk = b.yuk, boyutBayt = boy, kodek = b.kodek, hdr = b.hdr, sesVar = b.sesVar) to null
    }

    // ---------- müzik ----------
    fun muzikSec(u: Uri) {
        kapsam.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    val ad = Medya.adBoyut(ctx, u).first
                    Muzik(uri = u.toString(), ad = ad, sureMs = Medya.retrieverSure(ctx, u), ses = proje.value.muzik?.ses ?: 0.35f)
                }
            }
            r.onSuccess { m -> guncelle { it.copy(muzik = m) }; muzikKur(); muzikHizala() }
                .onFailure { uyari.value = "Müzik eklenemedi: ${it.message}" }
        }
    }

    fun muzikDegis(f: (Muzik) -> Muzik) {
        guncelle { p -> p.copy(muzik = p.muzik?.let(f)) }
        proje.value.muzik?.let {
            muzikPl.volume = it.ses.coerceIn(0f, 1f)
            muzikPl.repeatMode = if (it.dongu) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    fun muzikKaldir() { guncelle { it.copy(muzik = null) }; muzikPl.stop(); muzikPl.clearMediaItems() }

    // ---------- logo ----------
    fun logoSec(u: Uri) {
        kapsam.launch {
            val f = withContext(Dispatchers.IO) { runCatching { Medya.fotoHazirla(ctx, u, 1024, png = true, dizinAd = "logo") }.getOrNull() }
            if (f == null) { uyari.value = "Logo açılamadı."; return@launch }
            guncelle { p -> p.copy(logo = (p.logo ?: Logo(yol = "")).copy(yol = f.first.path)) }
        }
    }

    fun logoDegis(f: (Logo) -> Logo) = guncelle { p -> p.copy(logo = p.logo?.let(f)) }
    fun logoKaldir() = guncelle { it.copy(logo = null) }

    // ---------- seslendirme ----------
    @Suppress("DEPRECATION")
    private fun yeniKaydedici(): MediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    fun kayitBaslat() {
        if (kaydediyor.value || proje.value.klipler.isEmpty()) return
        val f = File(Depo.dizin("ses"), yeniId() + ".m4a")
        val r = yeniKaydedici()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(128000)
            r.setAudioChannels(1)
            r.setOutputFile(f.path)
            r.prepare(); r.start()
        } catch (e: Exception) {
            Kayit.hata("seslendirme", e); r.release(); f.delete()
            uyari.value = "Mikrofon açılamadı: ${e.message}"; return
        }
        if (player.playbackState == Player.STATE_ENDED) git(0)
        kaydedici = r; kayitDosya = f; kayitBas.value = anlik(); kaydediyor.value = true
        sesUygula(); muzikPl.pause(); sesPl.pause()
        player.play()
    }

    fun kayitDurdur() {
        val r = kaydedici ?: return
        kaydedici = null
        val f = kayitDosya; kayitDosya = null
        val ok = try { r.stop(); true } catch (e: Exception) { false } finally { r.release() }
        kaydediyor.value = false
        player.pause(); sesUygula()
        if (!ok || f == null || !f.exists() || f.length() < 500) {
            f?.delete(); uyari.value = "Kayıt çok kısa ya da alınamadı."; return
        }
        val sure = Medya.retrieverSure(ctx, Uri.fromFile(f)).takeIf { it > 0 } ?: (anlik() - kayitBas.value).coerceAtLeast(500)
        val s = SesKaydi(yol = f.path, basMs = kayitBas.value, sureMs = sure)
        guncelle { p -> p.copy(sesler = (p.sesler + s).sortedBy { it.basMs }) }
        kaydetSimdi(false)
        mesaj.value = "Seslendirme eklendi (${sureYaz(sure)})"
    }

    fun sesDegis(id: String, f: (SesKaydi) -> SesKaydi) = guncelle { p -> p.copy(sesler = p.sesler.map { if (it.id == id) f(it) else it }) }
    fun sesSil(id: String) = guncelle { p -> p.copy(sesler = p.sesler.filter { it.id != id }) }

    fun kapat() {
        if (kaydediyor.value) kayitDurdur()
        kaydetSimdi(false)
        kapsam.cancel()
        player.release(); muzikPl.release(); sesPl.release()
    }
}

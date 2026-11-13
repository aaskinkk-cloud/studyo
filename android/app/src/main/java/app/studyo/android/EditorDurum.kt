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

    /** Önizlemede üst üste binen geçiş: giden klibin gerçek son T ms'si ikinci oynatıcıda (dışa aktarmadaki kuyrukla aynı).
     *  Eskiden son kare MediaMetadataRetriever ile alınıyordu; HDR videoda bu kare soluk/parlak çıktığı için geçişte parlama görünüyordu. */
    val kuyrukPl: ExoPlayer = ExoPlayer.Builder(ctx).build()
    private var kuyrukAnahtar: String? = null
    fun kuyrukHazirla(k: Klip, T: Long) {
        val a = "${k.id}#${k.sonMs}#$T#${k.ses}#${k.sessiz}"
        if (a == kuyrukAnahtar) return
        kuyrukAnahtar = a
        val mi = MediaItem.Builder().setUri(Uri.parse(k.uri)).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder().setStartPositionMs((k.sonMs - T).coerceAtLeast(k.basMs)).setEndPositionMs(k.sonMs).build()).build()
        kuyrukPl.volume = if (k.sesVar && !k.sessiz) k.ses.coerceIn(0f, 1f) else 0f
        kuyrukPl.setMediaItem(mi); kuyrukPl.prepare(); kuyrukPl.seekTo(0); kuyrukPl.pause()
    }
    fun kuyrukEsle(ofs: Long, oynat: Boolean) {
        val sapma = kuyrukPl.currentPosition - ofs
        val fark = kotlin.math.abs(sapma)
        if (oynat) {
            // Oynarken atlama (seek) çözücüyü durdurup görüntüyü sıçratıyordu: küçük kaymalar hızla düzeltilir, yalnız büyük kaymada atlanır.
            if (!kuyrukPl.isPlaying) { if (fark > 80) kuyrukPl.seekTo(ofs); kuyrukPl.play() }
            else if (fark > 700) kuyrukPl.seekTo(ofs)
            val hiz = when { sapma < -40 -> 1.12f; sapma > 40 -> 0.9f; else -> 1f }
            if (kuyrukPl.playbackParameters.speed != hiz) kuyrukPl.setPlaybackSpeed(hiz)
        }
        else { if (kuyrukPl.isPlaying) kuyrukPl.pause(); if (kuyrukPl.playbackParameters.speed != 1f) kuyrukPl.setPlaybackSpeed(1f); if (fark > 40) kuyrukPl.seekTo(ofs) }
    }
    fun kuyrukBekle() { if (kuyrukPl.isPlaying) kuyrukPl.pause() }
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
                if (isPlaying) tikBasla() else { muzikPl.pause(); sesPl.pause(); sesAktif = null; katmanSesDurdur(); konum.value = anlik() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    if (kaydediyor.value) kayitDurdur()
                    muzikPl.pause(); sesPl.pause(); katmanSesDurdur()
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
    fun baslangic(i: Int): Long { val p = proje.value; return (0 until i.coerceIn(0, p.klipler.size)).sumOf { p.zKlip(it).sureMs } }
    fun anlik(): Long = (if (kompAktif.value) komp?.currentPosition?.coerceAtLeast(0) else null) ?: if (proje.value.klipler.isEmpty()) 0 else baslangic(player.currentMediaItemIndex) + player.currentPosition.coerceAtLeast(0)

    private fun ogeler(p: Proje): List<MediaItem> {
        val l = p.klipler.indices.map { p.zKlip(it) }.map { k ->
            val b = MediaItem.Builder().setUri(Uri.parse(k.uri)).setMediaId(k.id)
            if (k.foto) b.setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(k.fotoSureMs)
            else b.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(k.basMs).setEndPositionMs(k.sonMs).build())
            b.build()
        }.toMutableList()
        if (p.kapanisMs > 0) {
            // Kapanış kartı önizlemede de görünür (çıktıdakiyle aynı çizim, 1080 ölçeğinde)
            val (w, h) = Disa.boyut(p.bicim, 1080)
            val f = try { Disa.kapanisDosyasi(ctx, p, w, h, "kapanis_" + (p.kapanis.hashCode() * 31 + p.logo.hashCode() * 7 + p.bicim.hashCode() + (p.klipler.lastOrNull()?.let { it.uri.hashCode() + it.sonMs.hashCode() } ?: 0) * 13).toUInt()) } catch (e: Exception) { Kayit.hata("kapanış kartı", e); null }
            if (f != null) l += MediaItem.Builder().setUri(Uri.fromFile(f)).setMediaId("kapanis").setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(p.kapanisMs).build()
        }
        return l
    }

    fun listeKur(i: Int = player.currentMediaItemIndex, ms: Long = player.currentPosition) {
        val p = proje.value
        if (p.klipler.isEmpty()) { player.clearMediaItems(); konum.value = 0; return }
        val liste = ogeler(p)
        val ii = i.coerceIn(0, liste.lastIndex)
        val sure = if (ii < p.klipler.size) p.zKlip(ii).sureMs else p.kapanisMs
        player.setMediaItems(liste, ii, ms.coerceIn(0, (sure - 1).coerceAtLeast(0)))
        player.prepare()
        sira.value = ii
        sesUygula()
    }

    /** Dışa aktarma sırasında donanım çözücüleri boşaltılır (büyük videolarda çakışmayı önler). */
    fun onizlemeBirak() { kompSil(); player.pause(); player.stop(); muzikPl.stop(); sesPl.stop(); katmanPl.values.forEach { it.stop() } }
    fun onizlemeGeri() { listeKur(sira.value, 0); muzikKur() }

    private fun sesUygula() {
        val k = proje.value.klipler.getOrNull(player.currentMediaItemIndex)
        player.volume = if (k == null || kaydediyor.value || k.sessiz) 0f else k.ses.coerceIn(0f, 1f)
    }

    fun git(ms: Long) {
        if (kompAktif.value) { kompDuraklat(ms.coerceAtLeast(0)); return }
        val p = proje.value
        if (p.klipler.isEmpty()) return
        var t = ms.coerceIn(0, (p.toplamMs - 1).coerceAtLeast(0))
        var bulundu = false
        for (i in p.klipler.indices) {
            val d = p.zKlip(i).sureMs
            if (t < d) { player.seekTo(i, t); bulundu = true; break }
            t -= d
        }
        if (!bulundu) {
            if (p.kapanisMs > 0 && player.mediaItemCount > p.klipler.size) player.seekTo(p.klipler.size, t.coerceIn(0, p.kapanisMs - 1))
            else player.seekTo(p.klipler.lastIndex, (p.zKlip(p.klipler.lastIndex).sureMs - 1).coerceAtLeast(0))
        }
        konum.value = ms
        muzikHizala()
    }

    private fun tikBasla() {
        tikIs?.cancel()
        tikIs = kapsam.launch {
            muzikHizala()
            sonSesKonum = -1L
            while (isActive && player.isPlaying) {
                konum.value = anlik()
                sfxTik(konum.value)
                sesTakip()
                katmanSesTik(konum.value)
                delay(40)
            }
        }
    }

    fun oynatDurdur() {
        if (kompHazirlaniyor) { kompSil(); git(konum.value); return }
        if (kompAktif.value) { kompDuraklat(); return }
        if (player.isPlaying) { player.pause(); return }
        if (player.playbackState == Player.STATE_ENDED || konum.value >= proje.value.toplamMs - 60) git(0)
        if (kompBaslat()) return
        player.play()
    }

    // ---------- akıcı önizleme ----------
    // KÖK NEDEN: oynatırken önizleme iki ayrı oynatıcı (ana klip + giden klibin kuyruğu) ve üstünde Compose çizimleriyle
    // birleştiriliyordu; bunlar kare kare eşlenemez → geçişte sıçrama/takılma. Web Kareden Videoya her kareyi TEK tuvalde çizer.
    // Çözüm: oynatırken dışa aktarmadaki kompozisyonun AYNISI tek boru hattında oynatılır (Media3 CompositionPlayer):
    // geçiş, erime, yazılar, katmanlar, müzik — hepsi tek saatle. Durdurunca düzenleme önizlemesine dönülür.
    private var komp: androidx.media3.transformer.CompositionPlayer? = null
    /** Akıcı önizleme oynatılıyor/hazırlanıyor (yüzey ekranda, en altta). */
    val kompAktif = MutableStateFlow(false)
    /** İlk kare çizildi: düzenleme önizlemesi gizlenir (o ana kadar eski görüntü üstte kalır → siyah ekran görünmez). */
    val kompGorunur = MutableStateFlow(false)
    private var kompYuzey: android.view.SurfaceView? = null
    private var kompHazirlaniyor = false
    private var kompTikIs: Job? = null
    private var kompBekciIs: Job? = null
    private var kompHata = 0
    /** Üst üste iki kez açılamazsa bu oturumda standart önizleme kullanılır. */
    private var akiciKapali = false

    fun kompYuzeyBagla(sv: android.view.SurfaceView?) {
        val eski = kompYuzey
        kompYuzey = sv
        try {
            if (sv != null) komp?.setVideoSurfaceView(sv)
            else if (eski != null) komp?.clearVideoSurfaceView(eski)
        } catch (e: Exception) { Kayit.hata("akıcı önizleme yüzey", e) }
    }

    private fun kompBaslat(): Boolean {
        if (akiciKapali) return false
        if (kompHazirlaniyor) return true
        val p = proje.value
        if (p.klipler.isEmpty()) return false
        val bas = konum.value.coerceAtLeast(0)
        val hazir = komp
        if (hazir != null) {          // proje değişmedi (her düzenleme kompozisyonu siler): anında devam
            kompOynat(hazir, bas, -1L)
            return true
        }
        kompHazirlaniyor = true
        kompAktif.value = true; kompGorunur.value = false; oynuyor.value = true
        kapsam.launch {
            val t0 = System.currentTimeMillis()
            val (w, h) = Disa.boyut(p.bicim, 720)
            val comp = withContext(Dispatchers.Default) {
                runCatching { Disa.kompozisyon(ctx, p, w, h, Disa.HDR_COZUCU, onizleme = true) }.onFailure { Kayit.hata("akıcı önizleme kur", it) }.getOrNull()
            }
            if (!kompHazirlaniyor) { kompAktif.value = false; return@launch }      // bu arada durduruldu/değiştirildi
            kompHazirlaniyor = false
            if (comp == null) { kompBasarisiz(bas, "kompozisyon kurulamadı"); return@launch }
            try {
                val cp = androidx.media3.transformer.CompositionPlayer.Builder(ctx)
                    .setVideoGraphFactory(androidx.media3.effect.MultipleInputVideoGraph.Factory()).build()
                cp.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (komp === cp && kompAktif.value) oynuyor.value = isPlaying }
                    override fun onRenderedFirstFrame() { if (komp === cp) kompGorunur.value = true }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && komp === cp && kompAktif.value) kompDuraklat(proje.value.toplamMs - 1)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Kayit.hata("akıcı önizleme", error)
                        if (komp === cp) kompBasarisiz(cp.currentPosition.coerceAtLeast(0), "oynatıcı hatası ${error.errorCodeName}")
                    }
                })
                cp.setComposition(comp)
                komp = cp
                Kayit.yaz("Akıcı önizleme kuruldu: ${p.klipler.size} klip, ${comp.sequences.size} dizi, ${w}x$h, ${System.currentTimeMillis() - t0} ms")
                kompOynat(cp, bas, t0)
            } catch (e: Throwable) {
                Kayit.hata("akıcı önizleme başlat", e)
                kompBasarisiz(bas, "başlatılamadı")
            }
        }
        return true
    }

    private fun kompOynat(cp: androidx.media3.transformer.CompositionPlayer, bas: Long, t0: Long) {
        kompAktif.value = true
        kompYuzey?.let { try { cp.setVideoSurfaceView(it) } catch (_: Exception) {} }
        cp.seekTo(bas)
        if (cp.playbackState == Player.STATE_IDLE) cp.prepare()
        cp.play()
        kompTikIs?.cancel()
        kompTikIs = kapsam.launch {
            while (isActive && komp === cp && kompAktif.value) {
                val pos = cp.currentPosition.coerceAtLeast(0)
                konum.value = pos
                // ilk kare bildirimi gelmezse: oynatma ilerleyince yüzey öne alınır
                if (!kompGorunur.value && cp.isPlaying && pos > bas + 120) kompGorunur.value = true
                delay(40)
            }
        }
        // Takılma bekçisi: önce HAZIR olmasını bekler (büyük HDR kliplerde çözücüler 2 sn'yi geçebiliyor), sonra 1,5 sn ilerleme arar.
        kompBekciIs?.cancel()
        kompBekciIs = kapsam.launch {
            val b0 = System.currentTimeMillis()
            while (komp === cp && kompAktif.value && cp.playbackState != Player.STATE_READY && System.currentTimeMillis() - b0 < 12_000) delay(100)
            if (komp !== cp || !kompAktif.value) return@launch
            val hazirMs = System.currentTimeMillis() - b0
            Kayit.yaz("Akıcı önizleme hazır: $hazirMs ms (durum=${cp.playbackState})")
            val p0 = cp.currentPosition
            delay(1500)
            if (komp === cp && kompAktif.value && cp.playWhenReady && cp.currentPosition <= p0 + 150) {
                Kayit.yaz("Akıcı önizleme ilerlemedi: hazırlık=$hazirMs ms, durum=${cp.playbackState}, oynuyor=${cp.isPlaying}, konum=${cp.currentPosition}, dizi=${cp.currentTimeline.windowCount}")
                kompBasarisiz(bas, "ilerlemedi")
            } else if (komp === cp) kompHata = 0
        }
    }

    /** Akıcı önizleme açılamadı/takıldı: standart önizlemeyle aynı yerden oynamaya devam. İki kez olursa oturum boyunca kapanır. */
    private fun kompBasarisiz(bas: Long, neden: String) {
        kompHata++
        if (kompHata >= 2) akiciKapali = true
        Kayit.yaz("Akıcı önizleme başarısız ($neden), deneme $kompHata${if (akiciKapali) " — bu oturumda kapatıldı" else ""}")
        kompSil()
        git(bas)
        mesaj.value = if (akiciKapali) "Akıcı önizleme bu projede açılamadı; standart önizleme kullanılıyor." else "Akıcı önizleme yeniden hazırlanacak."
        player.play()
    }

    /** Duraklat: akıcı oynatıcı kapatılmaz (bir sonraki ▶ anında başlar); düzenleme önizlemesi aynı kareye gelir. */
    private fun kompDuraklat(konumaGit: Long = -1L) {
        val cp = komp
        val pos = if (konumaGit >= 0) konumaGit else cp?.currentPosition?.coerceAtLeast(0) ?: konum.value
        try { cp?.pause() } catch (_: Exception) {}
        kompTikIs?.cancel(); kompBekciIs?.cancel()
        kompAktif.value = false; kompGorunur.value = false; oynuyor.value = false
        git(pos)
    }

    /** Kompozisyon değişti ya da ekran kapanıyor: akıcı oynatıcı bırakılır. */
    private fun kompSil() {
        kompHazirlaniyor = false
        kompTikIs?.cancel(); kompBekciIs?.cancel()
        val cp = komp; komp = null
        kompAktif.value = false; kompGorunur.value = false
        oynuyor.value = player.isPlaying
        if (cp != null) try { cp.release() } catch (_: Exception) {}
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
    // geri al / yinele: 40 adım; 800 ms içindeki ardışık değişiklikler (yazı yazma, sürükleme) tek adım sayılır
    private val geriYigin = ArrayDeque<Proje>(); private val ileriYigin = ArrayDeque<Proje>(); private var sonAdim = 0L
    val geriVar = MutableStateFlow(false); val ileriVar = MutableStateFlow(false)
    private fun yiginBildir() { geriVar.value = geriYigin.isNotEmpty(); ileriVar.value = ileriYigin.isNotEmpty() }
    fun geriAl() { val o = geriYigin.removeLastOrNull() ?: return; ileriYigin.addLast(proje.value); geriYukle(o); mesaj.value = "Geri alındı" }
    fun yinele() { val o = ileriYigin.removeLastOrNull() ?: return; geriYigin.addLast(proje.value); geriYukle(o); mesaj.value = "Yinelendi" }
    private fun geriYukle(o: Proje) {
        val i = player.currentMediaItemIndex; val ms = player.currentPosition
        proje.value = o.copy(guncelleme = System.currentTimeMillis()); sonAdim = 0L
        secili.value = secili.value.coerceIn(0, (o.klipler.size - 1).coerceAtLeast(0))
        listeKur(i.coerceAtMost((o.klipler.size - 1).coerceAtLeast(0)), ms); kaydetPlanla(); yiginBildir()
    }

    fun guncelle(yapi: Boolean = false, f: (Proje) -> Proje) {
        if (komp != null || kompHazirlaniyor) { val calisiyor = kompAktif.value; val pos = konum.value; kompSil(); if (calisiyor) git(pos) }
        val i = player.currentMediaItemIndex; val ms = player.currentPosition
        val once = proje.value; val simdi = System.currentTimeMillis()
        if (yapi || simdi - sonAdim > 800) { geriYigin.addLast(once); if (geriYigin.size > 40) geriYigin.removeFirst() }
        sonAdim = simdi; ileriYigin.clear(); yiginBildir()
        proje.value = f(once).copy(guncelleme = simdi)
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

    /** Oynatıcının üstündeki klibi o andan ikiye böler. */
    fun klipBol() {
        val p = proje.value
        if (p.klipler.isEmpty()) return
        val t = konum.value
        var i = 0; var bas = 0L
        while (i < p.klipler.size && bas + p.zKlip(i).sureMs <= t) { bas += p.zKlip(i).sureMs; i++ }
        if (i >= p.klipler.size) { mesaj.value = "Bölmek için çizgiyi bir klibin üstüne getir"; return }
        val k = p.klipler[i]; val ofs = t - bas
        if (ofs < 300 || ofs > k.sureMs - 300) { mesaj.value = "Bölmek için çizgiyi klibin ortasına doğru getir"; return }
        val (a, b) = if (k.foto) k.copy(fotoSureMs = ofs) to k.copy(id = yeniId(), fotoSureMs = k.fotoSureMs - ofs)
            else k.copy(sonMs = k.basMs + ofs) to k.copy(id = yeniId(), basMs = k.basMs + ofs)
        guncelle(true) { pp -> pp.copy(klipler = pp.klipler.toMutableList().apply { set(i, a); add(i + 1, b) }) }
        secili.value = i + 1
        git(t)
        mesaj.value = "Klip bölündü"
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
            val vs = Varsayilan.secili(ctx)
            for (i in yeni.indices) {     // varsayılan ayarlar seçilen her klibe hemen uygulanır
                var kk = yeni[i]
                if ("kadraj" in vs) kk = kk.copy(doldur = true)
                if (kk.foto) kk = kk.copy(kenBurns = true, fotoSureMs = 5000, yuzOdak = "yuz" in vs)
                yeni[i] = kk
            }
            val ilkEkleme = proje.value.klipler.isEmpty()
            if (yeni.isNotEmpty()) {
                // biçim kendiliğinden değişmez: proje YouTube 16:9 açılır, dikey klipler Doldur ile kareyi doldurur
                val s = if (proje.value.klipler.isEmpty()) 0 else (secili.value + 1).coerceAtMost(proje.value.klipler.size)
                guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { it.addAll(s, yeni) }) }
                kaydetSimdi(false)
                sec(s)
                mesaj.value = "${yeni.size} klip eklendi"
                if (ilkEkleme) varsayilanUygula()   // ilk seçimde giriş, abone ol, kapanış ve YouTube 16:9 da gelir
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
            val fu = Uri.fromFile(r.first).toString()
            val od = Yuz.bul(fu)   // akıllı kadraj: yüz ya da ana nesne
            Kayit.yaz("Odak: $ad yüz=${od?.yuz} nesne=(${od?.nx}, ${od?.ny})")
            return odakla(Klip(tur = "foto", uri = fu, ad = ad, gen = r.second, yuk = r.third, boyutBayt = boy, tarihMs = Tarih.foto(ctx, u0)), od) to null
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
        val vk = Klip(tur = "video", uri = u.toString(), ad = ad, kaynakSureMs = b.sureMs, basMs = 0, sonMs = b.sureMs,
            gen = b.gen, yuk = b.yuk, boyutBayt = boy, kodek = b.kodek, hdr = b.hdr, sesVar = b.sesVar, kareHizi = b.kareHizi, tarihMs = Tarih.video(ctx, u))
        return odakla(vk, Yuz.videoBul(ctx, u, b.sureMs * 2 / 5, b.gen, b.yuk)) to null
    }

    private fun odakla(k: Klip, od: Yuz.Odak?): Klip = k.copy(yuzX = od?.yuz?.first ?: -1f, yuzY = od?.yuz?.second ?: -1f, yuzB = od?.yuz?.third ?: 0f,
        odakX = od?.nx ?: -1f, odakY = od?.ny ?: -1f, yuzBakildi = true)

    private fun odakBul(k: Klip): Yuz.Odak? = if (k.foto) Yuz.bul(k.uri)
        else Yuz.videoBul(ctx, Uri.parse(k.uri), k.basMs + (k.sonMs - k.basMs) * 2 / 5, k.gen, k.yuk)

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
    /** Telefondan bir ya da birkaç logo: kütüphaneye eklenir (şeffaf PNG korunur), sonuncusu projeye uygulanır. */
    fun logoSec(u: Uri) = logolarYukle(listOf(u))
    fun logolarYukle(l: List<Uri>) {
        if (l.isEmpty()) return
        kapsam.launch {
            mesgul.value = "Logo ekleniyor…"
            var son: LogoKaydi? = null; var hata = 0
            for (u in l) {
                val k = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = Medya.fotoHazirla(ctx, u, 1024, png = true, dizinAd = "logolar")?.first ?: error("açılamadı")
                        LogoKutup.ekle(f, Medya.adBoyut(ctx, u).first)
                    }.onFailure { Kayit.hata("logo ekle", it) }.getOrNull()
                }
                if (k == null) hata++ else son = k
            }
            mesgul.value = null
            son?.let { logoUygula(it) }
            if (hata > 0) uyari.value = "$hata logo açılamadı (desteklenmeyen biçim)."
        }
    }

    fun logoUygula(k: LogoKaydi) {
        kapsam.launch {
            val f = withContext(Dispatchers.IO) { runCatching { LogoKutup.projeKopyasi(k) }.getOrNull() }
            if (f == null) { uyari.value = "Logo dosyası bulunamadı."; return@launch }
            guncelle { p -> p.copy(logo = (p.logo ?: Logo(yol = "")).copy(yol = f.path, kutupId = k.id)) }
            mesaj.value = "Logo: ${k.ad}"
        }
    }

    fun logoDegis(f: (Logo) -> Logo) = guncelle { p -> p.copy(logo = p.logo?.let(f)) }
    fun logoKaldir() = guncelle { it.copy(logo = null) }

    // ---------- çok katmanlı düzenleme ----------
    val seciliKatman = MutableStateFlow<String?>(null)
    private val katmanPl = HashMap<String, ExoPlayer>()

    fun katmanDegis(id: String, f: (KatmanOge) -> KatmanOge) =
        guncelle { p -> p.copy(katmanlar = p.katmanlar.map { if (it.id == id) f(it) else it }) }

    private fun katmanEkleOge(o: KatmanOge) {
        guncelle(yapi = false) { p -> p.copy(katmanlar = p.katmanlar + o) }
        seciliKatman.value = o.id
    }

    /** Yeni katmanın aralığı: oynatıcının olduğu andan itibaren (videonun içinde kalır). */
    private fun yeniAralik(sure: Long): Pair<Long, Long> {
        val T = proje.value.toplamMs.coerceAtLeast(1000)
        val a = konum.value.coerceIn(0, (T - 500).coerceAtLeast(0))
        return a to minOf(T, a + sure).coerceAtLeast(a + 500)
    }

    fun katmanMetinEkle() {
        val (a, b) = yeniAralik(4000)
        katmanEkleOge(KatmanOge(tur = "metin", ad = "Metin", metin = "Yazınız", olcek = 0.08f, kutu = true, basMs = a, sonMs = b))
    }

    fun katmanCikartmaEkle(e: String) {
        val (a, b) = yeniAralik(3000)
        katmanEkleOge(KatmanOge(tur = "cikartma", ad = "Çıkartma $e", metin = e, olcek = 0.16f, anim = "zipla", basMs = a, sonMs = b))
    }

    fun katmanGorselEkle(l: List<Uri>) {
        if (l.isEmpty()) return
        kapsam.launch {
            mesgul.value = "Görsel ekleniyor…"
            var hata = 0
            for ((i, u) in l.withIndex()) {
                val r = withContext(Dispatchers.IO) {
                    runCatching {
                        val tip = ctx.contentResolver.getType(u) ?: ""
                        val saydam = tip.contains("png") || tip.contains("webp") || tip.contains("gif")
                        val f = Medya.fotoHazirla(ctx, u, 2048, png = saydam, dizinAd = "katman") ?: error("açılamadı")
                        Triple(f.first, Medya.adBoyut(ctx, u).first, f.second.toFloat() / f.third.coerceAtLeast(1))
                    }.onFailure { Kayit.hata("katman görsel", it) }.getOrNull()
                }
                if (r == null) { hata++; continue }
                val (a, b) = yeniAralik(4000)
                val oran = proje.value.let { val (w, h) = Disa.boyut(it.bicim, 1080); w.toFloat() / h }
                // görsel karenin yüksekliğini taşmasın
                val olc = minOf(0.4f, 0.6f * r.third / oran).coerceAtLeast(0.08f)
                katmanEkleOge(KatmanOge(tur = "gorsel", ad = r.second.substringBeforeLast('.').take(30), yol = r.first.path, olcek = olc,
                    x = 0.5f + 0.04f * i, y = 0.5f + 0.04f * i, basMs = a, sonMs = b))
            }
            mesgul.value = null
            if (hata > 0) uyari.value = "$hata görsel açılamadı."
        }
    }

    fun katmanLogoEkle(k: LogoKaydi) {
        kapsam.launch {
            val f = withContext(Dispatchers.IO) { runCatching { LogoKutup.projeKopyasi(k) }.getOrNull() }
            if (f == null) { uyari.value = "Logo dosyası bulunamadı."; return@launch }
            val (a, b) = yeniAralik(5000)
            katmanEkleOge(KatmanOge(tur = "gorsel", ad = "Logo · ${k.ad}", yol = f.path, olcek = 0.16f, x = 0.85f, y = 0.18f, basMs = a, sonMs = b))
        }
    }

    fun katmanSesEkle(u: Uri) {
        kapsam.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                    Medya.adBoyut(ctx, u).first to Medya.retrieverSure(ctx, u)
                }.getOrNull()
            }
            if (r == null || r.second <= 0) { uyari.value = "Ses dosyası açılamadı."; return@launch }
            val (a, b) = yeniAralik(r.second)
            katmanEkleOge(KatmanOge(tur = "ses", ad = r.first.substringBeforeLast('.').take(30), yol = u.toString(), anim = "yok",
                basMs = a, sonMs = b, kaynakSureMs = r.second))
        }
    }

    /** Katmanı oynatıcının olduğu yerden ikiye böler (ses katmanında kaynak başlangıcı da kayar). */
    fun katmanBol(id: String) {
        val o = proje.value.katmanlar.firstOrNull { it.id == id } ?: return
        val t = konum.value
        if (t < o.basMs + 200 || t > o.sonMs - 200) { mesaj.value = "Bölmek için çizgiyi katmanın üstüne getir"; return }
        val a = o.copy(sonMs = t)
        val b = o.copy(id = yeniId(), basMs = t, kaynakBasMs = o.kaynakBasMs + ((t - o.basMs) * o.hiz).toLong())
        guncelle { p -> val i = p.katmanlar.indexOfFirst { it.id == id }
            p.copy(katmanlar = p.katmanlar.toMutableList().apply { set(i, a); add(i + 1, b) }) }
        seciliKatman.value = b.id
        mesaj.value = "Bölündü"
    }

    fun katmanSil(id: String) {
        guncelle { p -> p.copy(katmanlar = p.katmanlar.filter { it.id != id }) }
        if (seciliKatman.value == id) seciliKatman.value = null
        katmanPl.remove(id)?.release()
    }

    fun katmanCogalt(id: String) {
        val o = proje.value.katmanlar.firstOrNull { it.id == id } ?: return
        val y = o.copy(id = yeniId(), ad = o.ad + " (kopya)", x = (o.x + 0.04f).coerceAtMost(1f), y = (o.y + 0.04f).coerceAtMost(1f), kilitli = false)
        guncelle { p -> val i = p.katmanlar.indexOfFirst { it.id == id }; p.copy(katmanlar = p.katmanlar.toMutableList().apply { add(i + 1, y) }) }
        seciliKatman.value = y.id
    }

    /** Sırayı değiştirir (liste alttan üste). */
    fun katmanTasi(nereden: Int, nereye: Int) {
        guncelle { p ->
            val l = p.katmanlar.toMutableList()
            if (nereden !in l.indices || nereye !in l.indices || nereden == nereye) return@guncelle p
            l.add(nereye, l.removeAt(nereden)); p.copy(katmanlar = l)
        }
    }

    /** Önizleme: o anda çalması gereken ses katmanlarını oynatıcılarla eşler (en çok 4 aynı anda). */
    private fun katmanSesTik(t: Long) {
        val p = proje.value
        val idler = p.katmanlar.map { it.id }.toSet()
        katmanPl.keys.filter { it !in idler }.forEach { katmanPl.remove(it)?.release() }
        val aktif = p.katmanlar.filter { it.tur == "ses" && !it.gizli && it.yol.isNotEmpty() && t >= it.basMs && t < it.sonMs }.take(4)
        val aktifId = aktif.map { it.id }.toSet()
        for ((id, pl) in katmanPl) if (id !in aktifId && pl.playWhenReady) pl.pause()
        for (o in aktif) {
            val pl = katmanPl.getOrPut(o.id) { ExoPlayer.Builder(ctx).build() }
            if (pl.currentMediaItem?.localConfiguration?.uri?.toString() != o.yol) { pl.setMediaItem(MediaItem.fromUri(o.yol)); pl.prepare() }
            pl.volume = o.ses.coerceIn(0f, 1f)
            val hiz = o.hiz.coerceIn(0.25f, 4f)
            if (pl.playbackParameters.speed != hiz) pl.setPlaybackSpeed(hiz)
            if (kaydediyor.value) { pl.pause(); continue }
            val hedef = o.kaynakBasMs + ((t - o.basMs) * hiz).toLong()
            if (!pl.playWhenReady) { pl.seekTo(hedef); pl.play() }
            else if (pl.playbackState == Player.STATE_READY && kotlin.math.abs(pl.currentPosition - hedef) > 300) pl.seekTo(hedef)
        }
    }

    private fun katmanSesDurdur() { katmanPl.values.forEach { it.pause() } }

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

    fun okumaDegis(f: (Proje) -> Proje) = guncelle(false, f)
    fun sesDegis(id: String, f: (SesKaydi) -> SesKaydi) = guncelle { p -> p.copy(sesler = p.sesler.map { if (it.id == id) f(it) else it }) }
    fun sesSil(id: String) = guncelle { p -> p.copy(sesler = p.sesler.filter { it.id != id }) }

    // ---------- görünüm: geçiş ve şablon ----------
    fun gecisDegis(f: (Gecis) -> Gecis) = guncelle(true) { it.copy(gecis = f(it.gecis)) }

    /** Klipler arasındaki geçiş simgesinden: i ile i+1 arası. null = projenin geçişi. */
    fun klipGecis(i: Int, tur: String?) = guncelle(true) { p ->
        if (i !in p.klipler.indices) p else p.copy(klipler = p.klipler.toMutableList().also { it[i] = it[i].copy(gecis = tur) })
    }
    /** Seçilen geçişi bütün klip aralarına uygular (klip başına seçimler temizlenir). */
    fun gecisHepsine(tur: String) = guncelle(true) { p -> p.copy(gecis = p.gecis.copy(tur = tur), klipler = p.klipler.map { it.copy(gecis = null) }) }
    fun girisDegis(f: (Giris) -> Giris) = guncelle { it.copy(giris = f(it.giris)) }
    fun hatirlaticiDegis(f: (Hatirlatici) -> Hatirlatici) = guncelle { it.copy(hatirlatici = f(it.hatirlatici)) }
    private var yapiIs: Job? = null
    /** Kapanış kartı yazısı değişince kart görüntüsü yeniden çizilir; yazarken her harfte değil, 700 ms durunca. */
    fun kapanisDegis(yapi: Boolean = true, f: (Kapanis) -> Kapanis) {
        guncelle(yapi) { it.copy(kapanis = f(it.kapanis)) }
        if (!yapi) { yapiIs?.cancel(); yapiIs = kapsam.launch { delay(700); listeKur() } }
    }

    /** Hazır paket: giriş başlığı, geçiş, hatırlatıcı ve kapanışı birlikte ayarlar. Klipler, müzik ve yazılar değişmez. */
    /** Varsayılan ayarlar (Şablon › Varsayılan ayarlar). Hangi maddelerin uygulanacağı ayarda saklanır (hepsi açık gelir).
     *  - kadraj: bütün klipler ekranı doldurur (Sığdır değil)
     *  - yuz: dikey kişi fotoğraflarında yüz bulunur; yavaş yakınlaşma yüze doğru (her fotoğraf 5 sn)
     *  - giris: "Renkli kutular" giriş başlığı (tarih bandı, renkli kutulu başlık satırları, konum bandı)
     *  - abone: koyu hap "Kanalıma abone ol · Yeni videolar için zili aç · ABONE OL"
     *  - kapanis: cam kartlı kapanış (İzlediğin için teşekkürler! · Beğen · Abone olundu · Paylaş)
     *  Klip sırası, müzik, yazılar değişmez; her şey sonra tek tek değiştirilebilir. */
    fun varsayilanUygula() {
        val sec = Varsayilan.secili(ctx)
        kapsam.launch {
            mesgul.value = "Varsayılan ayarlar uygulanıyor…"
            val p0 = proje.value
            // yüz bulunmamış dikey fotoğraflar (eski projeler) ve çekim tarihi: arka planda
            val yuzler = HashMap<String, Yuz.Odak?>()
            var tarih = 0L
            withContext(Dispatchers.IO) {
                for (k in p0.klipler) if (!k.yuzBakildi || k.odakX < 0f) yuzler[k.id] = odakBul(k)   // eski kliplerde odak yoksa bulunur
                tarih = p0.klipler.firstNotNullOfOrNull { it.tarihMs.takeIf { t -> t > 0 } }
                    ?: p0.klipler.firstOrNull { !it.foto }?.let { Tarih.video(ctx, Uri.parse(it.uri)) }?.takeIf { it > 0 }
                    ?: p0.olusturma
            }
            // varsayılan çıktı YouTube yatay 16:9; dikey video ve fotoğraflar bu kareyi doldurur
            val bicimDegisti = "kadraj" in sec && p0.bicim != "16:9"
            guncelle(true) { p ->
                p.copy(
                    bicim = if (bicimDegisti) "16:9" else p.bicim,
                    gecis = Gecis("erime", 800, p.gecis.siyahtanAc),   // fotoğraflar arası yumuşak geçiş, 0,8 sn (kısa klipte kendiliğinden kısalır)
                    klipler = p.klipler.map { k0 ->
                        var k = k0
                        if ("kadraj" in sec) k = k.copy(doldur = true)
                        if (k.foto) k = k.copy(kenBurns = true, fotoSureMs = 5000)
                        if (k.id in yuzler) k = odakla(k, yuzler[k.id])
                        k = k.copy(kayX = 0f, kayY = 0f)   // akıllı kadraj baştan
                        if ("yuz" in sec && k.foto) k = k.copy(yuzOdak = true)
                        k
                    },
                    giris = if ("giris" in sec) p.giris.copy(acik = true, stil = "r_haber", hareket = "trend", konum = "orta", sureMs = maxOf(p.giris.sureMs, 4000),
                        baslik = p.giris.baslik.ifBlank { p.ad.ifBlank { "Şefin Doğası" } },
                        bant = p.giris.bant.ifBlank { Tarih.yaz(tarih) + "|Kabataş, Ordu" }) else p.giris,
                    hatirlatici = if ("abone" in sec) p.hatirlatici.copy(acik = true, stil = "kart", anOran = 0.8f, yazi = "Kanalıma abone ol",
                        kucuk = "Yeni videolar için zili aç", dugme = "ABONE OL") else p.hatirlatici,
                    kapanis = if ("kapanis" in sec) p.kapanis.copy(acik = true, stil = "cam", sureMs = 5000, yazi = "İzlediğin için teşekkürler!") else p.kapanis,
                )
            }
            mesgul.value = null
            val yuzSay = proje.value.klipler.count { it.yuzEtkin }
            mesaj.value = "Varsayılan ayarlar uygulandı" + (if (bicimDegisti) " · biçim YouTube 16:9" else "") + if ("yuz" in sec && yuzSay > 0) " · $yuzSay fotoğrafta yüze yaklaşılıyor" else ""
        }
    }

    fun paketUygula(ad: String) {
        guncelle(true) { p ->
            val g = p.giris.copy(acik = true, baslik = p.giris.baslik.ifBlank { "Ahmet Aşkın" }, alt = p.giris.alt.ifBlank { "Şefin Doğası" })
            when (ad) {
                "reels" -> p.copy(giris = g.copy(stil = "bantlar", sureMs = 3000), gecis = Gecis("yakin", 500, false),
                    hatirlatici = p.hatirlatici.copy(acik = true), kapanis = p.kapanis.copy(acik = true, stil = "cta", sureMs = 5000))
                "sinema" -> p.copy(giris = g.copy(stil = "sinematik", sureMs = 4000), gecis = Gecis("siyah", 900, true),
                    hatirlatici = p.hatirlatici.copy(acik = false), kapanis = p.kapanis.copy(acik = true, stil = "sade", sureMs = 4000))
                else -> p.copy(giris = g.copy(stil = "altbant", sureMs = 3500), gecis = Gecis("beyaz", 700, false),
                    hatirlatici = p.hatirlatici.copy(acik = false), kapanis = p.kapanis.copy(acik = true, stil = "cta", sureMs = 5000))
            }
        }
        mesaj.value = "Paket uygulandı"
    }

    // ---------- altyazı ----------
    val seciliAltyazi = MutableStateFlow<String?>(null)

    fun altyaziEkle() {
        val p = proje.value
        if (p.toplamMs <= 0) return
        val a = anlik().coerceIn(0, (p.toplamMs - 500).coerceAtLeast(0))
        val y = Altyazi(basMs = a, sonMs = (a + 3000).coerceAtMost(p.toplamMs), metin = "")
        guncelle { it.copy(altyazilar = (it.altyazilar + y).sortedBy { s -> s.basMs }) }
        seciliAltyazi.value = y.id
    }

    fun altyaziHerKlibe() {
        var bas = 0L
        val pp = proje.value
        val yeni = pp.klipler.indices.map { i -> val d = pp.zKlip(i).sureMs; Altyazi(basMs = bas, sonMs = bas + d, metin = pp.klipler[i].yazi?.metin ?: "").also { bas += d } }
        guncelle { it.copy(altyazilar = (it.altyazilar + yeni).sortedBy { s -> s.basMs }) }
        mesaj.value = "${yeni.size} satır eklendi"
    }

    fun altyaziDegis(id: String, f: (Altyazi) -> Altyazi) = guncelle { p -> p.copy(altyazilar = p.altyazilar.map { if (it.id == id) f(it) else it }.sortedBy { it.basMs }) }
    fun altyaziSil(id: String) = guncelle { p -> p.copy(altyazilar = p.altyazilar.filter { it.id != id }) }
    fun altyaziTumunuSil() = guncelle { it.copy(altyazilar = emptyList()) }
    fun altyaziStilDegis(f: (AltyaziStil) -> AltyaziStil) = guncelle { it.copy(altyaziStil = f(it.altyaziStil)) }

    private fun srtZaman(ms: Long): String = String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)

    fun srt(): String = proje.value.altyazilar.filter { it.metin.isNotBlank() }.withIndex()
        .joinToString("\n") { (i, a) -> "${i + 1}\n${srtZaman(a.basMs)} --> ${srtZaman(a.sonMs)}\n${a.metin}\n" }

    fun srtYukle(u: Uri) {
        kapsam.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val metin = ctx.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    val zaman = Regex("(\\d+):(\\d+):(\\d+)[,.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,.](\\d+)")
                    fun ms(g: List<String>, o: Int) = g[o].toLong() * 3600000 + g[o + 1].toLong() * 60000 + g[o + 2].toLong() * 1000 + g[o + 3].padEnd(3, '0').take(3).toLong()
                    metin.replace("\r", "").split(Regex("\\n\\s*\\n")).mapNotNull { blok ->
                        val satirlar = blok.trim().lines()
                        val zi = satirlar.indexOfFirst { zaman.containsMatchIn(it) }
                        if (zi < 0) null else {
                            val g = zaman.find(satirlar[zi])!!.groupValues
                            Altyazi(basMs = ms(g, 1), sonMs = ms(g, 5), metin = satirlar.drop(zi + 1).joinToString("\n").trim())
                        }
                    }
                }
            }
            r.onSuccess { l ->
                if (l.isEmpty()) uyari.value = "Dosyada SRT altyazı bulunamadı."
                else { guncelle { it.copy(altyazilar = (it.altyazilar + l).sortedBy { s -> s.basMs }) }; mesaj.value = "${l.size} satır yüklendi" }
            }.onFailure { uyari.value = "SRT okunamadı: ${it.message}" }
        }
    }

    // ---------- kapak ----------
    val kapakResmi = MutableStateFlow<android.graphics.Bitmap?>(null)
    private var kapakIs: Job? = null

    fun kapakDegis(f: (Kapak) -> Kapak) { guncelle { it.copy(kapak = f(it.kapak)) }; kapakCiz() }

    /** Önizlemedeki anı kapak karesi yapar. */
    fun kapakKareSec() {
        val p = proje.value
        val i = player.currentMediaItemIndex
        val k = p.klipler.getOrNull(i) ?: p.klipler.lastOrNull() ?: return
        val yerel = if (i < p.klipler.size) player.currentPosition.coerceAtLeast(0) else 0L
        kapakDegis { it.copy(kaynakUri = k.uri, kaynakMs = if (k.foto) 0 else k.basMs + yerel, kaynakFoto = k.foto) }
        mesaj.value = "Kapak karesi seçildi"
    }

    private fun kapakKaynak(kp: Kapak): android.graphics.Bitmap? {
        if (kp.kaynakUri.isEmpty()) return null
        val u = Uri.parse(kp.kaynakUri)
        return if (kp.kaynakFoto) Medya.ornekleCoz(File(u.path ?: ""), 1920) else Medya.kareTam(ctx, u, kp.kaynakMs, 1920)
    }

    fun kapakCiz() {
        kapakIs?.cancel()
        kapakIs = kapsam.launch {
            delay(150)
            val p = proje.value
            val b = withContext(Dispatchers.Default) {
                runCatching {
                    val (w, h) = Cizim.kapakBoyut(p.bicim)
                    val olc = 640f / maxOf(w, h)
                    Cizim.kapak(p.kapak.copy(), kapakKaynak(p.kapak), p.logo, (w * olc).toInt(), (h * olc).toInt())
                }.getOrNull()
            }
            kapakResmi.value = b
        }
    }

    fun kapakKaydet() {
        kapsam.launch {
            mesgul.value = "Kapak kaydediliyor…"
            val p = proje.value
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val (w, h) = Cizim.kapakBoyut(p.bicim)
                    val b = Cizim.kapak(p.kapak, kapakKaynak(p.kapak), p.logo, w, h)
                    val ad = p.ad.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60) + "_kapak_" +
                        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".jpg"
                    Medya.resimKaydet(ctx, b, ad)
                }
            }
            mesgul.value = null
            r.onSuccess { mesaj.value = "Kapak kaydedildi: Pictures/Stüdyo" }.onFailure { Kayit.hata("kapak", it); uyari.value = "Kapak kaydedilemedi: ${it.message}" }
        }
    }

    /** Netlik (Laplace varyansı) ve pozlamaya göre kapak için en iyi kareleri önerir. */
    val kapakOnerileri = MutableStateFlow<List<Pair<Kapak, android.graphics.Bitmap>>>(emptyList())
    val kapakOneriHazirlaniyor = MutableStateFlow(false)

    private fun kareSkoru(b: android.graphics.Bitmap): Float {
        val k = Medya.olcekle(b, 160)
        val w = k.width; val h = k.height
        val px = IntArray(w * h); k.getPixels(px, 0, w, 0, 0, w, h)
        val g = FloatArray(w * h) { i -> val c = px[i]; (c shr 16 and 255) * 0.299f + (c shr 8 and 255) * 0.587f + (c and 255) * 0.114f }
        var ort = 0f; for (v in g) ort += v; ort /= g.size
        var t = 0.0; var t2 = 0.0; var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val l = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
            t += l; t2 += l * l; n++
        }
        val varyans = if (n > 0) (t2 / n - (t / n) * (t / n)).toFloat() else 0f
        val poz = (1f - kotlin.math.abs(ort - 125f) / 150f).coerceIn(0.15f, 1f)
        return varyans * poz
    }

    fun kapakOneriHazirla() {
        if (kapakOneriHazirlaniyor.value) return
        kapakOneriHazirlaniyor.value = true
        kapsam.launch {
            val p = proje.value
            val l = withContext(Dispatchers.IO) {
                val adaylar = ArrayList<Triple<Kapak, android.graphics.Bitmap, Float>>()
                val kl = p.klipler
                val adim = maxOf(1, kl.size / 12)
                for (i in kl.indices step adim) {
                    val k = kl[i]
                    val anlar = if (k.foto) listOf(0L) else listOf(0.3f, 0.6f).map { k.basMs + (k.sureMs * it).toLong() }
                    for (an in anlar) {
                        val b = try {
                            if (k.foto) Medya.ornekleCoz(File(Uri.parse(k.uri).path ?: ""), 480) else Medya.kare(ctx, Uri.parse(k.uri), an, 480)
                        } catch (e: Exception) { null } ?: continue
                        adaylar += Triple(p.kapak.copy(kaynakUri = k.uri, kaynakMs = if (k.foto) 0 else an, kaynakFoto = k.foto), b, kareSkoru(b))
                    }
                }
                adaylar.sortedByDescending { it.third }.take(6).map { it.first to it.second }
            }
            kapakOnerileri.value = l
            kapakOneriHazirlaniyor.value = false
            if (l.isEmpty()) uyari.value = "Kapak önerisi çıkarılamadı."
        }
    }

    fun kapakOneriSec(k: Kapak) = kapakDegis { it.copy(kaynakUri = k.kaynakUri, kaynakMs = k.kaynakMs, kaynakFoto = k.kaynakFoto) }

    // ---------- takip eden yazı ----------
    val takipSecimde = MutableStateFlow(false)
    val takipIlerleme = MutableStateFlow<Float?>(null)
    private var takipIs: Job? = null

    /** Önizlemede dokunulan nokta (oynayan klibin kaynak karesinde 0…1). İzleme hemen başlar. */
    fun takipNoktaSec(i: Int, x: Float, y: Float) {
        val p = proje.value
        val k = p.klipler.getOrNull(i) ?: return
        if (k.foto) { uyari.value = "Takip eden yazı yalnız videolarda çalışır."; return }
        takipSecimde.value = false
        player.pause()
        val bas = (k.basMs + player.currentPosition.coerceAtLeast(0)).coerceIn(k.basMs, (k.sonMs - 100).coerceAtLeast(k.basMs))
        secili.value = i
        guncelle { pp -> pp.copy(klipler = pp.klipler.toMutableList().also { l ->
            val eski = l[i].takip ?: Takip(metin = "Buraya bak")
            l[i] = l[i].copy(takip = eski.copy(basX = x, basY = y, basMs = bas, noktalar = emptyList()))
        }) }
        takipIs?.cancel()
        takipIs = kapsam.launch {
            takipIlerleme.value = 0f
            val r = runCatching { Takipci.izle(ctx, k, bas, x, y) { f -> takipIlerleme.value = f } }
            takipIlerleme.value = null
            r.onSuccess { n ->
                guncelle { pp -> pp.copy(klipler = pp.klipler.map { if (it.id == k.id) it.copy(takip = it.takip?.copy(noktalar = n)) else it }) }
                kaydetSimdi(false)
                Kayit.yaz("Takip: ${k.ad} ${n.size} nokta")
                mesaj.value = "Takip tamam (${n.size} kare)"
            }.onFailure { e ->
                if (e !is kotlinx.coroutines.CancellationException) { Kayit.hata("takip", e); uyari.value = "Takip yapılamadı: ${e.message}" }
            }
        }
    }

    fun takipIptal() { takipIs?.cancel(); takipIlerleme.value = null }
    fun takipDegis(f: (Takip) -> Takip) = klipGuncelle { it.copy(takip = it.takip?.let(f)) }
    fun takipKaldir() { takipIptal(); klipGuncelle { it.copy(takip = null) } }

    // ---------- nesne katmanları ----------
    val seciliNesne = MutableStateFlow<String?>(null)

    fun nesneEkle(u: Uri) {
        kapsam.launch {
            mesgul.value = "Nesne kesiliyor…"
            val (f, hata) = try { Nesneci.kes(ctx, u) } catch (e: Exception) { Kayit.hata("nesne", e); null to (e.message ?: "bilinmeyen hata") }
            mesgul.value = null
            if (f == null) { uyari.value = hata ?: "Nesne kesilemedi."; return@launch }
            val p = proje.value
            val a = anlik().coerceIn(0, (p.toplamMs - 500).coerceAtLeast(0))
            val orj = File(f.parentFile, f.nameWithoutExtension + "_orj.jpg")
            val n = Nesne(yol = f.path, basMs = a, sonMs = (a + 5000).coerceAtMost(p.toplamMs.coerceAtLeast(a + 500)), orjYol = if (orj.exists()) orj.path else "")
            guncelle { it.copy(nesneler = it.nesneler + n) }
            kaydetSimdi(false)
            seciliNesne.value = n.id
            mesaj.value = "Nesne eklendi — önizlemede sürükleyerek yerleştir"
        }
    }

    fun nesneDegis(id: String, f: (Nesne) -> Nesne) = guncelle { p -> p.copy(nesneler = p.nesneler.map { if (it.id == id) f(it) else it }) }
    fun nesneSil(id: String) { guncelle { p -> p.copy(nesneler = p.nesneler.filter { it.id != id }) }; if (seciliNesne.value == id) seciliNesne.value = null }

    // ---------- otomatik altyazı ----------
    val altyaziIlerleme = MutableStateFlow<Pair<String, Float>?>(null)
    private var altyaziIs: Job? = null

    fun altyaziOtomatik(kaynak: String) {
        if (altyaziIs?.isActive == true) return
        val p = proje.value
        if (kaynak == "ses" && p.sesler.isEmpty()) { uyari.value = "Projede seslendirme kaydı yok."; return }
        if (kaynak == "klip" && p.klipler.none { !it.foto && it.sesVar && !it.sessiz }) { uyari.value = "Sesi açık video klip yok."; return }
        altyaziIs = kapsam.launch {
            try {
                if (!Altyazici.modelHazir(ctx)) {
                    altyaziIlerleme.value = "Türkçe model iniyor (~35 MB, bir kez)" to 0f
                    Altyazici.modelIndir(ctx) { f -> altyaziIlerleme.value = "Türkçe model iniyor (~35 MB, bir kez)" to f }
                }
                altyaziIlerleme.value = "Konuşma yazıya dökülüyor" to 0f
                val satirlar = Altyazici.olustur(ctx, p, kaynak) { f -> altyaziIlerleme.value = "Konuşma yazıya dökülüyor" to f }
                // önceki otomatik satırları (kelime zamanlı) değiştir, elle yazılanlar kalır
                guncelle { pp -> pp.copy(altyazilar = (pp.altyazilar.filter { it.kelimeler.isEmpty() } + satirlar).sortedBy { it.basMs }) }
                kaydetSimdi(false)
                mesaj.value = if (satirlar.isEmpty()) "Konuşma bulunamadı" else "${satirlar.size} altyazı satırı oluştu"
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) { Kayit.hata("otomatik altyazı", e); uyari.value = "Altyazı oluşturulamadı: ${e.message}" }
            } finally {
                altyaziIlerleme.value = null
            }
        }
    }

    fun altyaziIptal() { altyaziIs?.cancel() }

    // ---------- videodan hareketli nesne ----------
    val nesneIlerleme = MutableStateFlow<Float?>(null)
    private var nesneIs: Job? = null

    fun videodanNesne(sureMs: Long) {
        val p = proje.value
        val i = player.currentMediaItemIndex
        val k = p.klipler.getOrNull(i)
        if (k == null || k.foto) { uyari.value = "Önce önizlemede nesnenin olduğu video klibe gel."; return }
        player.pause()
        val bas = (k.basMs + player.currentPosition.coerceAtLeast(0)).coerceIn(k.basMs, k.sonMs - 300)
        val sure = minOf(sureMs, k.sonMs - bas)
        val an = anlik()
        nesneIs?.cancel()
        nesneIs = kapsam.launch {
            nesneIlerleme.value = 0f
            val (n, hata) = try { Nesneci.videodanKes(ctx, k, bas, sure) { f -> nesneIlerleme.value = f } }
                catch (e: kotlinx.coroutines.CancellationException) { nesneIlerleme.value = null; return@launch }
                catch (e: Exception) { null to (e.message ?: "bilinmeyen hata") }
            nesneIlerleme.value = null
            if (n == null) { uyari.value = hata ?: "Nesne kesilemedi."; return@launch }
            val toplam = proje.value.toplamMs
            val yeni = n.copy(basMs = an, sonMs = (an + n.kareSayi * n.kareMs).coerceAtMost(toplam.coerceAtLeast(an + 500)))
            guncelle { it.copy(nesneler = it.nesneler + yeni) }
            kaydetSimdi(false)
            seciliNesne.value = yeni.id
            mesaj.value = "Hareketli nesne eklendi (${n.kareSayi} kare)"
        }
    }

    fun nesneIptal() { nesneIs?.cancel(); nesneIlerleme.value = null }

    // ---------- üst üste binen geçiş önizlemesi: giden klibin son karesi ----------
    val sonKareler = MutableStateFlow<Map<String, android.graphics.Bitmap>>(emptyMap())
    private val sonKareIstenen = HashSet<String>()

    fun sonKareAnahtar(k: Klip) = "${k.id}#${k.sonMs}"
    fun sonKareIste(k: Klip) {
        val a = sonKareAnahtar(k)
        if (!sonKareIstenen.add(a)) return
        kapsam.launch {
            val b = withContext(Dispatchers.IO) {
                runCatching {
                    if (k.foto) Medya.ornekleCoz(File(Uri.parse(k.uri).path ?: ""), 1280)
                    else Medya.kareTam(ctx, Uri.parse(k.uri), (k.sonMs - 40).coerceAtLeast(k.basMs), 1280)
                }.getOrNull()
            }
            if (b != null) sonKareler.value = sonKareler.value + (a to b)
        }
    }

    // ---------- reels efekt sesleri (önizleme) ----------
    private var sesHavuzu: android.media.SoundPool? = null
    private val sesKimlik = HashMap<String, Int>()
    private var sonSesKonum = -1L
    private var sfxAnlari: List<Pair<Long, String>> = emptyList()
    private var sfxAnahtar = 0

    private fun sfxHazirla() {
        val p = proje.value
        val g = p.giris
        val anahtar = g.hashCode() * 31 + p.toplamMs.hashCode()
        if (anahtar == sfxAnahtar) return
        sfxAnahtar = anahtar
        sfxAnlari = if (g.acik && g.reels && g.efektSes) {
            val (a, b) = Zaman.girisAralik(p)
            Reels.sesAnlari(g, Reels.seritler(g, 540, 960).size, a, b)
        } else emptyList()
        if (sfxAnlari.isNotEmpty() && sesHavuzu == null) {
            val h = android.media.SoundPool.Builder().setMaxStreams(4).build()
            for (t in listOf("vuus", "pop", "tok")) sesKimlik[t] = h.load(Sfx.dosya(ctx, t).path, 1)
            sesHavuzu = h
        }
    }

    /** Oynatırken her tikte çağrılır: geçilen efekt anlarında sesi çalar. */
    fun sfxTik(ms: Long) {
        sfxHazirla()
        val onceki = sonSesKonum
        sonSesKonum = ms
        if (onceki < 0 || ms < onceki || ms - onceki > 400) return
        val h = sesHavuzu ?: return
        val v = proje.value.giris.sesSeviye.coerceIn(0f, 1f)
        for ((an, tur) in sfxAnlari) if (an in (onceki + 1)..ms) sesKimlik[tur]?.let { h.play(it, v, v, 1, 0, 1f) }
    }

    fun kapat() {
        kompSil()
        if (kaydediyor.value) kayitDurdur()
        kaydetSimdi(false)
        kapsam.cancel()
        player.release(); muzikPl.release(); sesPl.release(); kuyrukPl.release()
        katmanPl.values.forEach { try { it.release() } catch (_: Exception) {} }; katmanPl.clear()
        try { sesHavuzu?.release() } catch (_: Exception) {}
    }
}

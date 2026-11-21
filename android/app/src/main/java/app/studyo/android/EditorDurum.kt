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
    val proje = MutableStateFlow((Depo.yukle(id) ?: Proje(id = id).also { Depo.kaydet(it) }).let { p ->
        // 3.43: önceki sürümde bozuk (NaN) kaydedilmiş katman değerleri açılışta onarılır
        fun Float.ok(v: Float) = if (isNaN() || isInfinite()) v else this
        p.copy(katmanlar = p.katmanlar.map { o -> o.copy(x = o.x.ok(0.5f), y = o.y.ok(0.5f), olcek = o.olcek.ok(if (o.tur == "metin") 0.075f else 0.35f),
            aci = o.aci.ok(0f), opaklik = o.opaklik.ok(1f), ses = o.ses.ok(1f), hiz = o.hiz.ok(1f)) })
    })
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
            // 3.80: düzenleme oynatıcısı kendi karesini çizdi → alttaki donmuş akıcı kare artık gerekmez (yüzey bırakılır, oynatıcı kalır)
            override fun onRenderedFirstFrame() { if (kompDurgun.value) kompDurgun.value = false }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                sira.value = player.currentMediaItemIndex
                // 3.42: seçili klip = oynatıcının üstündeki klip (izlerken ve sararken); klip şeridi buna göre kayar
                val ix = player.currentMediaItemIndex
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && ix in proje.value.klipler.indices) secili.value = ix
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
    fun onizlemeBirak() { kompSil(); player.pause(); player.stop(); kuyrukPl.stop(); kuyrukAnahtar = null; muzikPl.stop(); sesPl.stop(); katmanPl.values.forEach { it.stop() } }
    fun onizlemeGeri() { listeKur(sira.value, 0); muzikKur() }

    private fun sesUygula() {
        val k = proje.value.klipler.getOrNull(player.currentMediaItemIndex)
        player.volume = if (k == null || kaydediyor.value || k.sessiz) 0f else k.ses.coerceIn(0f, 1f)
        proje.value.muzik?.let { muzikPl.volume = muzikSesi(it, k) }
    }

    /** 3.52: t anında videonun kendi sesi (açık) ya da seslendirme var mı */
    private fun konusmaVar(t: Long): Boolean {
        val p = proje.value; var b = 0L
        for (i in p.klipler.indices) {
            val d = p.zKlip(i).sureMs
            if (t < b + d) { val k = p.klipler[i]; if (!k.foto && k.sesVar && !k.sessiz && k.ses > 0.01f) return true; break }
            b += d
        }
        return p.sesler.any { t in it.basMs..(it.basMs + it.sureMs) }
    }

    /** 3.46: o anki klibin kendi sesi varsa (ve açıksa) müzik otomatik kısılır. */
    private fun muzikSesi(m: Muzik, k: Klip? = proje.value.klipler.getOrNull(player.currentMediaItemIndex)): Float {
        val t = konum.value
        val kis = m.otoKis && (k != null && !k.foto && k.sesVar && !k.sessiz && k.ses > 0.01f || proje.value.sesler.any { t in it.basMs..(it.basMs + it.sureMs) }
            || proje.value.katmanlar.any { it.tur == "ses" && !it.otoKis && !it.gizli && it.ses > 0.01f && t in it.basMs..it.sonMs })
        return (m.ses * (if (kis) MUZIK_KIS else 1f) * muzikSolma(m, t, proje.value.toplamMs)).coerceIn(0f, 1f)   // 3.70: yumuşak giriş/çıkış
    }

    fun git(ms: Long) {
        if (kompAktif.value) { kompDuraklat(ms.coerceAtLeast(0)); return }
        val p = proje.value
        if (p.klipler.isEmpty()) return
        var t = ms.coerceIn(0, (p.toplamMs - 1).coerceAtLeast(0))
        var bulundu = false
        for (i in p.klipler.indices) {
            val d = p.zKlip(i).sureMs
            if (t < d) { player.seekTo(i, t); secili.value = i; bulundu = true; break }
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
                proje.value.muzik?.let { muzikPl.volume = muzikSesi(it) }
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
    /** 3.80 KÖK DÜZELTME (durdurunca siyah ekran): akıcı oynatıcı duraklatılınca yüzeyi kaldırılmaz; donmuş son kare düzenleme
     *  önizlemesinin ALTINDA kalır. Düzenleme oynatıcısı kendi karesini çizene kadar (ya da hiç çizemezse) bu kare görünür → siyah yok. */
    val kompDurgun = MutableStateFlow(false)
    private var kompYuzey: android.view.SurfaceView? = null
    private var kompHazirlaniyor = false
    private var kompTikIs: Job? = null
    private var kompBekciIs: Job? = null
    private var kompHazirlikIs: Job? = null
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
        kompHazirlikIs = kapsam.launch {
            val t0 = System.currentTimeMillis()
            val (w, h) = Disa.boyut(p.bicim, 720)
            // 3.32: geçişler kompozisyonun içinde tek tuvalde çizilir (TekTuval.kt); önceden hazırlanan parça ve bekleme yok
            val comp = withContext(Dispatchers.Default) {
                runCatching { Disa.kompozisyon(ctx, p, w, h, Disa.HDR_COZUCU, onizleme = true) }.onFailure { Kayit.hata("akıcı önizleme kur", it) }.getOrNull()
            }
            if (!kompHazirlaniyor) { kompAktif.value = false; return@launch }      // bu arada durduruldu/değiştirildi
            kompHazirlaniyor = false
            if (comp == null) { kompBasarisiz(bas, "kompozisyon kurulamadı"); return@launch }
            try {
                // 3.32: dizide yalnız klipler var (geçiş parçası yok) → Media3'ün varsayılan yolu, cihaz kaydında parçasız
                // projelerde her seferinde çalışan biçim. 3.20–3.31'deki çökme, diziye konan kısa mp4 parçalardan geliyordu.
                // 3.40: fotoğraflar dışa aktarmadaki gibi sRGB'ye çevrilerek açılır (Media3'ün önizleme çözücüsü çevirmiyordu;
                // telefon fotoğrafları Display P3) → önizleme renkleri = kayıt renkleri = geçişteki renkler.
                val cp = androidx.media3.transformer.CompositionPlayer.Builder(ctx).setImageDecoderFactory(srgbResimCozucu()).build()
                cp.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { if (komp === cp && kompAktif.value) oynuyor.value = isPlaying }
                    override fun onRenderedFirstFrame() { if (komp === cp) kompGorunur.value = true }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED && komp === cp && kompAktif.value) kompDuraklat(proje.value.toplamMs - 1)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Kayit.hata("akıcı önizleme", error)
                        var c: Throwable? = error.cause; var n = 0
                        while (c != null && n < 4) { Kayit.hata("  neden ${++n}", c); c = c.cause.takeIf { it !== c } }
                        if (komp === cp) kompBasarisiz(cp.currentPosition.coerceAtLeast(0), "oynatıcı hatası ${error.errorCodeName}")
                    }
                })
                cp.setComposition(comp)
                komp = cp
                Depo.onizlemeCokmesi = { e -> if (komp === cp) { Kayit.yaz("Akıcı önizleme iş parçacığı çöktü (uygulama korundu): ${e.javaClass.simpleName}"); kompBasarisiz(konum.value, "oynatıcı çöktü") } }
                Kayit.yaz("Akıcı önizleme kuruldu: ${p.klipler.size} klip, ${comp.sequences.size} dizi, ${w}x$h, ${System.currentTimeMillis() - t0} ms")
                kompOynat(cp, bas, t0)
            } catch (e: Throwable) {
                Kayit.hata("akıcı önizleme başlat", e)
                kompBasarisiz(bas, "başlatılamadı")
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun srgbResimCozucu(): androidx.media3.exoplayer.image.ImageDecoder.Factory =
        androidx.media3.exoplayer.image.BitmapFactoryImageDecoder.Factory { veri, uz ->
            val sinir = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(veri, 0, uz, sinir)
            var ss = 1
            while (maxOf(sinir.outWidth, sinir.outHeight) / ss > 4096) ss *= 2
            val o = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = ss
                inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
            }
            android.graphics.BitmapFactory.decodeByteArray(veri, 0, uz, o)
                ?: throw androidx.media3.exoplayer.image.ImageDecoderException("Fotoğraf açılamadı")
        }

    private fun kompOynat(cp: androidx.media3.transformer.CompositionPlayer, bas: Long, t0: Long) {
        kompDurgun.value = false
        kompAktif.value = true
        kompYuzey?.let { try { cp.setVideoSurfaceView(it) } catch (_: Exception) {} }
        MuzikZarf.sar(bas)
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
            // 3.33: 1,5 sn'lik tek ölçüm, geç ama sağlıklı başlayan oynatmayı da "takıldı" sayıyordu (kayıt 14.11 21:11:55 ve
            // 21:30:32: konum tam kıpırdamaya başlarken kesildi). Artık 5 sn boyunca ilerleme aranır; ne kadar geç başladığı yazılır.
            val p0 = cp.currentPosition
            val i0 = System.currentTimeMillis()
            var ilerledi = false
            while (komp === cp && kompAktif.value && cp.playWhenReady && System.currentTimeMillis() - i0 < 5000) {
                delay(100)
                if (cp.currentPosition > p0 + 150) { ilerledi = true; break }
            }
            val gecen = System.currentTimeMillis() - i0
            if (komp === cp && kompAktif.value && cp.playWhenReady && !ilerledi) {
                Kayit.yaz("Akıcı önizleme ilerlemedi: hazırlık=$hazirMs ms, bekleme=$gecen ms, durum=${cp.playbackState}, oynuyor=${cp.isPlaying}, başlangıç=$p0, konum=${cp.currentPosition}, dizi=${cp.currentTimeline.windowCount}")
                kompBasarisiz(bas, "ilerlemedi")
            } else if (komp === cp) {
                if (ilerledi && gecen > 600) Kayit.yaz("Akıcı önizleme geç başladı: $gecen ms (başlangıç=$p0)")
                kompHata = 0
            }
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
        kompDurgun.value = cp != null
        kompAktif.value = false; kompGorunur.value = false; oynuyor.value = false
        // düzenleme oynatıcısı hata yüzünden durmuşsa (ör. çözücü yetmedi) yeniden hazırlanır; yoksa sarma kare çizmez
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) { Kayit.yaz("Durdur: önizleme oynatıcısı boştaydı, yeniden hazırlandı"); player.prepare() }
        git(pos)
        // fotoğrafta kare bildirimi gelmeyebilir: fotoğraf hazırsa kısa süre sonra alttaki kare bırakılır (Sığdır'da kenarda görünmesin)
        if (cp != null) kapsam.launch {
            delay(1200)
            val k = proje.value.klipler.getOrNull(player.currentMediaItemIndex)
            if (kompDurgun.value && !kompAktif.value && k?.foto == true && player.playbackState == Player.STATE_READY) kompDurgun.value = false
        }
    }

    /** Kompozisyon değişti ya da ekran kapanıyor: akıcı oynatıcı bırakılır. */
    private fun kompSil() {
        if (kompHazirlaniyor) kompHazirlikIs?.cancel()
        kompHazirlikIs = null
        kompHazirlaniyor = false
        kompTikIs?.cancel(); kompBekciIs?.cancel()
        val cp = komp; komp = null
        kompAktif.value = false; kompGorunur.value = false; kompDurgun.value = false
        oynuyor.value = player.isPlaying
        if (cp != null) try { cp.release() } catch (_: Exception) {}
        // akıcı oynatıcının çözücüleri bırakıldı: düzenleme oynatıcısı o anki kareyi yeniden çizsin (siyah kalmasın)
        if (cp != null && !player.isPlaying && player.mediaItemCount > 0) try {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.seekTo(player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0))
        } catch (e: Exception) { Kayit.hata("önizleme kare yenile", e) }
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
        muzikPl.volume = muzikSesi(m)
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
        proje.value = bagliKatmanlar(once, f(once)).copy(guncelleme = simdi)
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

    // ---------- 3.76: klibe bağlı katmanlar ----------
    private fun klipBaslari(p: Proje): Map<String, Long> {
        val m = HashMap<String, Long>(); var t = 0L
        p.klipler.forEachIndexed { j, k -> m[k.id] = t; t += p.zKlip(j).sureMs }
        return m
    }

    /** Araya klip eklenince, klip silinince/taşınınca/kırpılınca: bağlı katman, klibinin başlangıcı ne kadar kaydıysa o kadar kayar.
     *  Katmanın kendisi bu adımda elle değiştirildiyse dokunulmaz. Klibi silinen katmanın bağı çözülür (katman yerinde kalır). */
    private fun bagliKatmanlar(once: Proje, yeni: Proje): Proje {
        if (yeni.katmanlar.none { it.klipId.isNotEmpty() }) return yeni
        val a = klipBaslari(once); val b = klipBaslari(yeni)
        if (a == b) return yeni
        val eski = once.katmanlar.associateBy { it.id }
        var degisti = false
        val l = yeni.katmanlar.map { o ->
            if (o.klipId.isEmpty()) return@map o
            val bs = b[o.klipId] ?: run { degisti = true; return@map o.copy(klipId = "") }
            val as0 = a[o.klipId] ?: return@map o
            val e = eski[o.id]
            if (e == null || e.basMs != o.basMs || e.sonMs != o.sonMs) return@map o
            val d = bs - as0
            if (d == 0L) o else { degisti = true; o.copy(basMs = (o.basMs + d).coerceAtLeast(0), sonMs = (o.sonMs + d).coerceAtLeast(300)) }
        }
        return if (degisti) yeni.copy(katmanlar = l) else yeni
    }

    /** ms anındaki klibin kimliği (yoksa boş). */
    fun klipIdAnda(ms: Long): String {
        val p = proje.value; var t = 0L
        p.klipler.forEachIndexed { j, k -> val d = p.zKlip(j).sureMs; if (ms < t + d) return k.id; t += d }
        return p.klipler.lastOrNull()?.id ?: ""
    }

    fun katmanKlibeBagla(id: String, bagla: Boolean) {
        val o = proje.value.katmanlar.firstOrNull { it.id == id } ?: return
        katmanDegis(id) { it.copy(klipId = if (bagla) klipIdAnda(o.basMs) else "") }
        val n = proje.value.klipler.indexOfFirst { it.id == proje.value.katmanlar.firstOrNull { k -> k.id == id }?.klipId } + 1
        mesaj.value = if (bagla) "Klip $n'e bağlandı · araya klip eklenince birlikte kayar" else "Klip bağı kaldırıldı"
    }

    fun yazilariKlibeBagla() {
        guncelle(yapi = false) { p -> p.copy(katmanlar = p.katmanlar.map { if (it.tur == "metin" && it.klipId.isEmpty()) it.copy(klipId = klipIdAnda(it.basMs)) else it }) }
        mesaj.value = "Bütün yazılar klibine bağlandı"
    }

    /** 3.76: Kırp sayfası — oynatıcı çizgisinden başı ya da sonu kes. */
    fun klipUcKes(bas: Boolean) {
        val s = secili.value; val k = proje.value.klipler.getOrNull(s) ?: return
        val st = baslangic(s); val yerel = konum.value - st
        if (yerel !in 250..(k.sureMs - 250)) { mesaj.value = "Önce çizgiyi klibin içinde kesilecek yere getir"; return }
        if (k.foto) {
            klipGuncelle(true) { it.copy(fotoSureMs = if (bas) k.fotoSureMs - yerel else yerel) }
            git(if (bas) st else st + yerel - 300)
        } else if (bas) kirp(k.basMs + yerel, k.sonMs) else kirp(k.basMs, k.basMs + yerel)
        mesaj.value = if (bas) "Baştan kesildi · geri al ile döner" else "Sondan kesildi · geri al ile döner"
    }

    fun klipGuncelle(yapi: Boolean = false, f: (Klip) -> Klip) {
        val s = secili.value
        guncelle(yapi) { p -> if (s !in p.klipler.indices) p else p.copy(klipler = p.klipler.toMutableList().also { it[s] = f(it[s]) }) }
    }

    fun hepsine(f: (Klip) -> Klip, yapi: Boolean = false) = guncelle(yapi) { p -> p.copy(klipler = p.klipler.map(f)) }

    // ---------- 3.83: yüz gizleme (Düzenle › Klip › Yüz gizle) ----------
    val gizleAraniyor = MutableStateFlow(false)
    val gizleIlerleme = MutableStateFlow(0f)
    /** İncelenen an (kaynak zamanı, ms) ve seçili yüz (id; -1 = yok). */
    val gizleAnMs = MutableStateFlow(0L)
    val gizleSecili = MutableStateFlow(-1)
    private var gizleIs: Job? = null

    private fun gizleDegis(f: (YuzGizle) -> YuzGizle) = klipGuncelle { it.copy(yuzGizle = f(it.yuzGizle ?: YuzGizle())) }

    /** Sayfa açılınca: incelenecek an oynatıcının yeri (bu klipteyse). */
    fun gizleBasla() {
        val s = secili.value; val k = proje.value.klipler.getOrNull(s) ?: return
        gizleAnMs.value = if (k.foto) 0L else if (player.currentMediaItemIndex == s) (k.basMs + player.currentPosition.coerceAtLeast(0)).coerceIn(k.basMs, k.sonMs) else k.basMs
    }

    /** Klipteki yüzleri bul (videoda takip izleriyle). Önceki elle eklenenler ve seçimleri korunur. */
    fun gizleAra() {
        val s = secili.value; val k = proje.value.klipler.getOrNull(s) ?: return
        if (gizleAraniyor.value) { gizleIs?.cancel(); gizleAraniyor.value = false; mesaj.value = "Arama durduruldu"; return }
        gizleAraniyor.value = true; gizleIlerleme.value = 0f
        gizleIs = kapsam.launch {
            val l = try { if (k.foto) YuzGizleme.fotoAra(ctx, k) else YuzGizleme.videoAra(ctx, k) { gizleIlerleme.value = it } }
                catch (e: kotlinx.coroutines.CancellationException) { gizleAraniyor.value = false; return@launch }
                catch (e: Throwable) { Kayit.hata("yüz gizle", e); null }
            gizleAraniyor.value = false
            if (l == null) { mesaj.value = "Yüzler aranamadı (hata kaydında)"; return@launch }
            val i = proje.value.klipler.indexOfFirst { it.id == k.id }.takeIf { it >= 0 } ?: return@launch
            guncelle { p -> p.copy(klipler = p.klipler.toMutableList().also { kl ->
                val g = kl[i].yuzGizle ?: YuzGizle()
                val elle = g.yuzler.filter { it.elle }
                val ilk = (elle.maxOfOrNull { it.id } ?: 0)
                val yeni = l.map { it.copy(id = ilk + it.id) }
                kl[i] = kl[i].copy(yuzGizle = g.copy(yuzler = elle + yeni, secili = g.secili.filter { id -> elle.any { it.id == id } }.toSet(), arandi = true))
            }) }
            mesaj.value = if (l.isEmpty()) "Yüz bulunamadı · kaçan yüze basılı tut = elle ekle" else "${l.size} yüz bulundu · gizlenecekleri seç"
        }
    }

    fun gizleSec(id: Int) = gizleDegis { g -> g.copy(secili = if (id in g.secili) g.secili - id else g.secili + id) }
    fun gizleHepsi(ac: Boolean) = gizleDegis { g -> g.copy(secili = if (ac) g.yuzler.map { it.id }.toSet() else emptySet()) }
    fun gizleGuc(v: Float) = gizleDegis { it.copy(guc = v.coerceIn(0.05f, 1f)) }
    fun gizleOlcek(id: Int, v: Float) = gizleDegis { g -> g.copy(yuzler = g.yuzler.map { if (it.id == id) it.copy(olcek = v.coerceIn(0.6f, 2.2f)) else it }) }
    fun gizleSil(id: Int) { gizleDegis { g -> g.copy(yuzler = g.yuzler.filter { it.id != id }, secili = g.secili - id) }; gizleSecili.value = -1 }
    fun gizleTemizle() { klipGuncelle { it.copy(yuzGizle = null) }; gizleSecili.value = -1; mesaj.value = "Yüz gizleme kaldırıldı" }

    /** Kaçan yüzü elle ekle (x, y: karede 0…1, ms: kaynak zamanı). Videoda o andan itibaren takip edilir; eklenen yüz gizlenir. */
    fun gizleElleEkle(x: Float, y: Float, ms: Long) {
        val s = secili.value; val k = proje.value.klipler.getOrNull(s) ?: return
        if (gizleAraniyor.value) return
        gizleAraniyor.value = true; gizleIlerleme.value = 0f
        if (!k.foto) mesaj.value = "Yüz takip ediliyor…"
        gizleIs = kapsam.launch {
            val iz = try { YuzGizleme.elleIz(ctx, k, x, y, ms, YuzGizleme.varsayilanBoy(k.yuzGizle)) { gizleIlerleme.value = it } }
                catch (e: kotlinx.coroutines.CancellationException) { gizleAraniyor.value = false; return@launch }
            gizleAraniyor.value = false
            val i = proje.value.klipler.indexOfFirst { it.id == k.id }.takeIf { it >= 0 } ?: return@launch
            var yeniId = 0
            guncelle { p -> p.copy(klipler = p.klipler.toMutableList().also { kl ->
                val g = kl[i].yuzGizle ?: YuzGizle()
                yeniId = (g.yuzler.maxOfOrNull { it.id } ?: 0) + 1
                kl[i] = kl[i].copy(yuzGizle = g.copy(yuzler = g.yuzler + GizliYuz(yeniId, iz, elle = true), secili = g.secili + yeniId))
            }) }
            gizleSecili.value = yeniId
            mesaj.value = if (k.foto || iz.size < 2) "Yüz eklendi ve gizlendi · boyutu aşağıdan ayarla"
                else "Yüz ${String.format(java.util.Locale("tr"), "%.1f", (iz.last().ms - iz.first().ms) / 1000f)} sn takip edildi ve gizlendi"
        }
    }

    // ---------- 3.74: yüz turu (Düzenle › Klip › Yüzler) ----------
    val yuzAraniyor = MutableStateFlow(false)
    /** Seçili turdaki durak (numara − 1); -1 = yok. */
    val turSecili = MutableStateFlow(-1)
    private var turOnizleIs: Job? = null

    /** Tur değişikliği: klip gerekirse uzatılır (geçişler rahat görünsün; web Kareden Videoya kfSure ile aynı). */
    private fun turDegis(mesajYaz: Boolean = true, f: (YuzTuru) -> YuzTuru) {
        val s = secili.value
        val k0 = proje.value.klipler.getOrNull(s)?.takeIf { it.foto } ?: return
        val t = f(k0.yuzTur ?: YuzTuru())
        val gerek = (kotlin.math.ceil(YuzTur.toplamSn(t) * 10.0) * 100).toLong()
        val uzat = t.noktalar.isNotEmpty() && k0.fotoSureMs + 50 < gerek
        klipGuncelle(yapi = uzat) { it.copy(yuzTur = t, kenBurns = if (t.noktalar.isNotEmpty()) true else it.kenBurns, fotoSureMs = if (uzat) gerek else it.fotoSureMs) }
        if (uzat && mesajYaz) mesaj.value = "Geçişler rahat görünsün diye fotoğraf " + String.format(java.util.Locale("tr"), "%.1f", gerek / 1000f) + " sn'ye uzatıldı"
    }

    /** Fotoğraftaki yüzleri bul; tur boşsa hepsini soldan sağa numaralı durak yap. */
    fun turYuzleriBul(ekle: Boolean = true) {
        val s = secili.value
        val k = proje.value.klipler.getOrNull(s)?.takeIf { it.foto } ?: return
        if (yuzAraniyor.value) return
        yuzAraniyor.value = true
        kapsam.launch {
            val l = try { YuzTur.yuzleriBul(k.uri) } catch (e: Throwable) { Kayit.hata("yüz turu", e); emptyList() }
            yuzAraniyor.value = false
            if (proje.value.klipler.getOrNull(s)?.id != k.id || secili.value != s) return@launch
            val (scC, hh, H) = turOlcek(k)
            val bos = k.yuzTur?.noktalar.isNullOrEmpty()
            turDegis { t -> t.copy(bulunan = l, arandi = true,
                noktalar = if (ekle && bos) l.map { it.copy(z = YuzTur.yuzYakinlik(it.yb, hh, scC, H)) } else t.noktalar) }
            mesaj.value = when {
                l.isEmpty() -> "Yüz bulunamadı · yaklaşılacak yerlere dokun"
                ekle && bos -> "${l.size} yüz bulundu ve numaralandı · numaraya dokun = seç"
                else -> "${l.size} yüz bulundu · halkaya dokun = durak ekle"
            }
            if (ekle && bos && l.isNotEmpty()) turSecili.value = 0
            if (l.isNotEmpty()) turSiluetHazirla(k.id)
        }
    }

    /** 3.87: silüet ışığı için kişilerin dış hattı (bulunan yüzler ve duraklar). Bir kez aranır; durağın bulunan yüzle eşleşeni alınır. */
    private var siluetIs: Job? = null
    fun turSiluetHazirla(klipId: String) {
        if (siluetIs?.isActive == true || !Siluet.kullanilabilir()) return
        val k = proje.value.klipler.firstOrNull { it.id == klipId }?.takeIf { it.foto } ?: return
        val t = k.yuzTur ?: return
        if (t.siluetArandi || t.vurguTur != "siluet" || !t.vurgu) return
        val hedef = (t.bulunan + t.noktalar).filter { it.yb > 0f && it.siluet.isEmpty() }
        if (hedef.isEmpty()) return
        siluetIs = kapsam.launch {
            val l = try { Siluet.hesapla(k.uri, hedef) } catch (e: Throwable) { Kayit.hata("silüet", e); hedef.map { emptyList() } }
            fun eslesen(n: OdakNoktasi): List<Float>? = hedef.indices.filter { l[it].isNotEmpty() }
                .minByOrNull { kotlin.math.hypot(hedef[it].x - n.x, hedef[it].y - n.y) }
                ?.takeIf { kotlin.math.hypot(hedef[it].x - n.x, hedef[it].y - n.y) < n.yb.coerceAtLeast(0.02f) * 0.8f }?.let { l[it] }
            val i = proje.value.klipler.indexOfFirst { it.id == klipId }.takeIf { it >= 0 } ?: return@launch
            guncelle { p -> p.copy(klipler = p.klipler.toMutableList().also { kl ->
                val tt = kl[i].yuzTur ?: return@also
                kl[i] = kl[i].copy(yuzTur = tt.copy(siluetArandi = true,
                    bulunan = tt.bulunan.map { n -> if (n.siluet.isEmpty() && n.yb > 0f) eslesen(n)?.let { n.copy(siluet = it) } ?: n else n },
                    noktalar = tt.noktalar.map { n -> if (n.siluet.isEmpty() && n.yb > 0f) eslesen(n)?.let { n.copy(siluet = it) } ?: n else n }))
            }) }
        }
    }

    /** (kadrajı dolduran ölçek, fotoğraf yüksekliği, çıkış yüksekliği) — 1080 referansında. */
    private fun turOlcek(k: Klip): Triple<Float, Float, Int> {
        val (W, H) = Disa.boyut(proje.value.bicim, 1080)
        val g = (if (k.gen > 0) k.gen else W).toFloat(); val h = (if (k.yuk > 0) k.yuk else H).toFloat()
        return Triple(kotlin.math.max(W / g, H / h), h, H)
    }

    /** Dokunulan yer (fotoğrafta 0…1): bulunan bir yüzün üstündeyse ona yapışır. */
    fun turNoktaEkle(x: Float, y: Float) {
        val k = proje.value.klipler.getOrNull(secili.value)?.takeIf { it.foto } ?: return
        val (scC, hh, H) = turOlcek(k)
        val oran = k.gen.toFloat() / k.yuk.coerceAtLeast(1)
        val yuz = k.yuzTur?.bulunan?.minByOrNull { kotlin.math.hypot((it.x - x) * oran, it.y - y) }
            ?.takeIf { kotlin.math.hypot((it.x - x) * oran, it.y - y) < it.yb * 0.75f }
        val n = yuz?.let { it.copy(z = YuzTur.yuzYakinlik(it.yb, hh, scC, H)) } ?: OdakNoktasi(x, y, 1.8f)
        turDegis { t -> t.copy(noktalar = t.noktalar + n) }
        turSecili.value = (k.yuzTur?.noktalar?.size ?: 0)
    }

    fun turNoktaTasi(i: Int, x: Float, y: Float) = turDegis(false) { t ->
        t.copy(noktalar = t.noktalar.mapIndexed { j, n -> if (j == i) n.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else n }) }

    fun turYakinlik(i: Int, z: Float, hepsi: Boolean = false) = turDegis(false) { t ->
        t.copy(noktalar = t.noktalar.mapIndexed { j, n -> if (hepsi || j == i) n.copy(z = z.coerceIn(1f, 6f)) else n }) }

    /** 3.76: bütün yüz duraklarına hazır yakınlık (hedef: başın kadraj yüksekliğindeki payı). Elle noktalar değişmez. */
    fun turYuzOdak(hedef: Float) {
        val k = proje.value.klipler.getOrNull(secili.value) ?: return
        val (scC, hh, H) = turOlcek(k)
        turDegis(false) { t -> t.copy(noktalar = t.noktalar.map { n -> if (n.yb > 0f) n.copy(z = YuzTur.yuzYakinlik(n.yb, hh, scC, H, hedef)) else n }) }
    }

    fun turIsim(i: Int, ad: String) = turDegis(false) { t -> t.copy(noktalar = t.noktalar.mapIndexed { j, n -> if (j == i) n.copy(ad = ad) else n }) }

    /** Sırayı değiştir: yon −1 = öne, +1 = sona doğru. */
    fun turSira(i: Int, yon: Int) {
        val l = proje.value.klipler.getOrNull(secili.value)?.yuzTur?.noktalar ?: return
        val h = i + yon; if (i !in l.indices || h !in l.indices) return
        turDegis(false) { t -> t.copy(noktalar = t.noktalar.toMutableList().also { Collections.swap(it, i, h) }) }
        turSecili.value = h
    }

    /** Sürükle-bırak sıralama: i. durağı h. sıraya taşı. */
    fun turSiraTasi(i: Int, h: Int) {
        val l = proje.value.klipler.getOrNull(secili.value)?.yuzTur?.noktalar ?: return
        if (i !in l.indices || h !in l.indices || i == h) return
        turDegis(false) { t -> t.copy(noktalar = t.noktalar.toMutableList().also { m -> val o = m.removeAt(i); m.add(h, o) }) }
        turSecili.value = h
    }

    fun turNoktaSil(i: Int) {
        turDegis(false) { t -> t.copy(noktalar = t.noktalar.filterIndexed { j, _ -> j != i }) }
        turSecili.value = -1
    }

    fun turAyar(f: (YuzTuru) -> YuzTuru) = turDegis(true, f)

    fun turTemizle() { turDegis(false) { t -> t.copy(noktalar = emptyList()) }; turSecili.value = -1; mesaj.value = "Duraklar temizlendi · geri al ile geri gelir" }

    /** Yalnız bu klibi baştan oynat, klip bitince dur. */
    fun turOnizle() {
        val s = secili.value
        val k = proje.value.klipler.getOrNull(s) ?: return
        if (oynuyor.value) oynatDurdur()
        val bas = baslangic(s); val son = bas + proje.value.zKlip(s).sureMs
        git(bas); oynatDurdur()
        turOnizleIs?.cancel()
        turOnizleIs = kapsam.launch {
            delay(300)
            while (isActive && oynuyor.value && konum.value < son - 80) delay(40)
            if (oynuyor.value && secili.value == s) oynatDurdur()
            if (proje.value.klipler.getOrNull(s)?.id == k.id) git(son - 120)
        }
    }

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

    /** Parmakla sürükleyerek taşıma (3.25): from sıradaki klip to sırasına. */
    fun klipTasi(from: Int, to: Int) {
        val n = proje.value.klipler.size
        if (from !in 0 until n || to !in 0 until n || from == to) return
        guncelle(true) { p -> p.copy(klipler = p.klipler.toMutableList().also { val k = it.removeAt(from); it.add(to, k) }) }
        sec(to)
    }

    /** Klibin üstündeki makas: çizgi seçili klibin içindeyse oradan, değilse klibin ortasından böler. */
    fun klipBolSecili() {
        val s = secili.value; val p = proje.value
        if (s !in p.klipler.indices) return
        val st = baslangic(s); val d = p.zKlip(s).sureMs
        if (konum.value !in (st + 300)..(st + d - 300)) git(st + d / 2)
        klipBol()
    }

    /** Klibin üstündeki ses simgesi: sesi kapat/aç. */
    fun sesAcKapat() = klipGuncelle(true) { it.copy(sessiz = !it.sessiz) }

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
    /** 3.81: meşgul penceresinde "Vazgeç" (verilirse). */
    val mesgulIptal = MutableStateFlow<(() -> Unit)?>(null)
    private var albumIs: Job? = null

    /** 3.81: Google Fotoğraflar paylaşılan albüm bağlantısından fotoğrafları indirip projeye ekler (AlbumIndir). */
    fun albumAl(link: String) {
        if (albumIs?.isActive == true) return
        albumIs = kapsam.launch {
            var l: List<Uri> = emptyList(); var h: String? = null; var iptal = false
            try {
                mesgul.value = "Google Fotoğraflar albümü açılıyor…"
                mesgulIptal.value = { albumIs?.cancel() }
                val r = AlbumIndir.indir(ctx, link) { i, n -> mesgul.value = "Albümden alınıyor $i / $n…" }
                l = r.first; h = r.second
            } catch (e: kotlinx.coroutines.CancellationException) { iptal = true }
            finally { mesgulIptal.value = null; mesgul.value = null }
            if (iptal) { mesaj.value = "Albüm alma durduruldu"; return@launch }
            if (l.isEmpty()) { uyari.value = h ?: "Albümden bir şey alınamadı."; return@launch }
            h?.let { mesaj.value = it }
            ekle(l)
        }
    }

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
            val vs = if (proje.value.bos) emptySet() else Varsayilan.secili(ctx)
            if (!proje.value.bos) for (i in yeni.indices) {     // varsayılan ayarlar seçilen her klibe hemen uygulanır
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
                if (ilkEkleme && !proje.value.bos) varsayilanUygula()   // ilk seçimde giriş, abone ol, kapanış ve YouTube 16:9 da gelir
                else if (!proje.value.bos && proje.value.muzik?.ad?.startsWith("Hazır müzik") == true) hazirMuzikYenile()   // video uzadı: müzik de uzasın
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
                    Muzik(uri = u.toString(), ad = ad, sureMs = Medya.retrieverSure(ctx, u), ses = proje.value.muzik?.ses ?: 0.5f)
                }
            }
            r.onSuccess { m -> guncelle { it.copy(muzik = m) }; muzikKur(); muzikHizala() }
                .onFailure { uyari.value = "Müzik eklenemedi: ${it.message}" }
        }
    }

    /** 3.61: yerleşik müzik (uygulamada üretilir, telifsiz). Video süresi kadar (+3 sn); klasik olmayanlar döngüye uygun. */
    val muzikUretiliyor = MutableStateFlow(false)
    fun hazirMuzik(anahtar: String) {
        if (muzikUretiliyor.value) return
        kapsam.launch {
            muzikUretiliyor.value = true
            val t = MuzikSentez.tur(anahtar)
            val sn = (proje.value.toplamMs / 1000 + 3).toInt()
            val r = withContext(Dispatchers.Default) { runCatching { MuzikSentez.dosya(ctx, anahtar, sn) } }
            muzikUretiliyor.value = false
            r.onSuccess { f ->
                val m = Muzik(uri = Uri.fromFile(f).toString(), ad = "Hazır müzik · ${t.ad}", ses = proje.value.muzik?.ses ?: 0.5f, dongu = !t.klasik,
                    sureMs = Medya.retrieverSure(ctx, Uri.fromFile(f)))
                guncelle { it.copy(muzik = m) }; muzikKur(); muzikHizala()
                mesaj.value = "Müzik: ${t.ad}"
            }.onFailure { Kayit.hata("müzik üret", it); uyari.value = "Müzik üretilemedi: ${it.message}" }
        }
    }
    /** 3.61: hazır müzik video uzayınca yeniden üretilir (aynı tür). */
    private fun hazirMuzikYenile() {
        val m = proje.value.muzik ?: return
        val anahtar = MuzikSentez.TURLER.firstOrNull { m.ad == "Hazır müzik · ${it.ad}" }?.anahtar ?: return
        if (m.sureMs >= proje.value.toplamMs + 1000) return
        hazirMuzik(anahtar)
    }

    /** 3.61: konuya göre (ML Kit görüntü etiketleme) müzik türü seçip ekler. */
    fun otomatikMuzik() {
        if (muzikUretiliyor.value) return
        kapsam.launch {
            muzikUretiliyor.value = true
            val tur = runCatching { MuzikSecici.konudanTur(ctx, proje.value) }.getOrDefault("huzur")
            muzikUretiliyor.value = false
            hazirMuzik(tur)
        }
    }

    fun muzikDegis(f: (Muzik) -> Muzik) {
        guncelle { p -> p.copy(muzik = p.muzik?.let(f)) }
        proje.value.muzik?.let {
            muzikPl.volume = muzikSesi(it)
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
                        val f = Medya.fotoHazirla(ctx, u, 2048, png = true, dizinAd = "logolar")?.first ?: error("açılamadı")   // 3.73: logo yazıları net kalsın (PNG, 2048 piksele kadar özgün)
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
            Varsayilan.sonLogoYaz(ctx, k.id)
            mesaj.value = "Logo: ${k.ad}"
        }
    }

    fun logoDegis(f: (Logo) -> Logo) = guncelle { p -> p.copy(logo = p.logo?.let(f)) }
    fun logoKaldir() = guncelle { it.copy(logo = null) }

    // ---------- çok katmanlı düzenleme ----------
    val seciliKatman = MutableStateFlow<String?>(null)
    private val katmanPl = HashMap<String, ExoPlayer>()

    fun katmanDegis(id: String, f: (KatmanOge) -> KatmanOge) =
        guncelle { p -> p.copy(katmanlar = p.katmanlar.map { if (it.id == id) katmanTemiz(runCatching { f(it) }.getOrDefault(it), p.toplamMs) else it }) }

    /** Hızlı yazı düzenleme penceresi açılacak katman (3.44) · katmanSayfasi artınca editör Katman bölümünü açar. */
    val katmanDuzenle = MutableStateFlow<String?>(null)
    /** 3.48: ses düzeyi penceresi — "muzik" ya da ses katmanının kimliği */
    val sesPenceresi = MutableStateFlow<String?>(null)
    val katmanSayfasi = MutableStateFlow(0)
    /** 3.61: Renk bölümünde seçili ayar (alt menü satırı ile Renk sayfası ortak). */
    val renkSekme = MutableStateFlow("hazir")
    /** 3.53: Hazır paketler › Boş proje — editör ekranı ayarlar (yeni boş projeyi açar). */
    var bosProjeAc: (String) -> Unit = {}
    /** 3.78: başka bir projeyi aç (editör yeni projeye geçer). */
    var projeAc: (String) -> Unit = {}

    /** 3.78: Yüzler › Ayrı video — seçilen kişilerden (duraklardan) yeni bir taslak: yalnız bu fotoğraf, seçilen biçimde (Reels 9:16, yatay, kare).
     *  Yüz yakınlıkları yeni biçime çevrilir (baş kadrajda aynı payı kaplar); isim kutusu, öne çıkarma, müzik, logo, geçiş ve renk korunur;
     *  giriş, abone, kapanış ve diğer kliplere ait yazılar alınmaz. Bu proje değişmez. Açılınca dışa aktarma ekranı gelir. */
    fun turAyriVideo(secilen: List<Int>, bicim: String) {
        val p = proje.value; val k = p.klipler.getOrNull(secili.value)?.takeIf { it.foto } ?: return
        val t = k.yuzTur ?: return
        val sec = secilen.filter { it in t.noktalar.indices }.distinct().sorted()
        if (sec.isEmpty()) { mesaj.value = "En az bir kişi seç"; return }
        val (W0, H0) = Disa.boyut(p.bicim, 1080); val (W1, H1) = Disa.boyut(bicim, 1080)
        val g = (if (k.gen > 0) k.gen else W0).toFloat(); val h = (if (k.yuk > 0) k.yuk else H0).toFloat()
        val sc0 = kotlin.math.max(W0 / g, H0 / h); val sc1 = kotlin.math.max(W1 / g, H1 / h)
        val nok = sec.map { t.noktalar[it] }.map { n ->
            if (n.yb > 0f) { val pay = n.z * n.yb * h * sc0 / H0; n.copy(z = (pay / kotlin.math.max(0.01f, n.yb * h * sc1 / H1)).coerceIn(1f, 6f)) } else n }
        val t2 = t.copy(noktalar = nok)
        val sure = (kotlin.math.ceil(YuzTur.toplamSn(t2) * 10.0) * 100).toLong().coerceAtLeast(1500)
        val k2 = k.copy(id = yeniId(), yuzTur = t2, fotoSureMs = sure, kenBurns = true, gecis = null)
        val simdi = System.currentTimeMillis()
        val tur = when (bicim) { "9:16" -> "Reels"; "1:1" -> "Kare"; else -> "Yatay" }
        val y = p.copy(id = yeniId(), ad = p.ad + " · " + (if (sec.size == t.noktalar.size) "Yüzler" else "${sec.size} kişi") + " · " + tur,
            olusturma = simdi, guncelleme = simdi, bicim = bicim, bos = true, arsiv = false, klipler = listOf(k2),
            sesler = emptyList(), katmanlar = emptyList(), altyazilar = emptyList(), nesneler = emptyList(), kapak = Kapak(),
            giris = p.giris.copy(acik = false), hatirlatici = p.hatirlatici.copy(acik = false), kapanis = p.kapanis.copy(acik = false))
        kaydetSimdi(false)
        Depo.kaydet(y)
        Kayit.yaz("Ayrı video taslağı: ${y.ad} · ${sec.size} durak · $bicim · ${sure / 1000} sn")
        Depo.acilincaDisa = y.id
        projeAc(y.id)
    }

    /** InShot tarzı uç seçimi (3.44): klipSon | videoSon | klipBas | videoBas. Klip sonu zaten seçiliyse bir sonraki klibin sonuna uzar. */
    fun katmanUc(o: KatmanOge, tur: String): KatmanOge {
        val p = proje.value; val T = p.toplamMs.coerceAtLeast(500)
        fun sinir(ms: Long): Pair<Long, Long> {
            var b = 0L
            for (i in p.klipler.indices) { val d = p.zKlip(i).sureMs; if (ms < b + d) return b to b + d; b += d }
            return b to T
        }
        val enUzun = if (o.tur == "ses" && o.kaynakSureMs > 0) ((o.kaynakSureMs - o.kaynakBasMs) / o.hiz.coerceAtLeast(0.25f)).toLong() else Long.MAX_VALUE
        return when (tur) {
            "klipSon" -> { var b = sinir(o.basMs).second; if (b <= o.sonMs + 50 && b < T) b = sinir(b).second; o.copy(sonMs = minOf(b, o.basMs + enUzun)) }
            "videoSon" -> o.copy(sonMs = minOf(T, o.basMs + enUzun))
            "klipBas" -> { var a = sinir(o.basMs).first; if (a >= o.basMs - 50 && a > 0) a = sinir(a - 1).first; o.copy(basMs = a, sonMs = minOf(o.sonMs, a + enUzun)) }
            "basaTasi" -> { val L = (o.sonMs - o.basMs).coerceAtLeast(300); o.copy(basMs = 0, sonMs = minOf(L, T)) }   // 3.45: süresi korunur, videonun başına taşınır
            else -> o.copy(basMs = 0, sonMs = minOf(o.sonMs, enUzun))
        }
    }

    /** 3.50: başı/sonu adım adım (± ms) — düğmelerle genişlet/daralt */
    fun katmanUcKaydir(id: String, bas: Long, son: Long) = katmanDegis(id) { o ->
        val T = proje.value.toplamMs
        val yb = (o.basMs + bas).coerceIn(0, o.sonMs - 300)
        val ys = (o.sonMs + son).coerceIn(yb + 300, T)
        o.copy(basMs = yb, sonMs = ys, kaynakBasMs = if (o.tur == "ses") (o.kaynakBasMs + ((yb - o.basMs) * o.hiz).toLong()).coerceAtLeast(0) else o.kaynakBasMs)
    }

    /** Tüm katmanlar için aynı değişiklik (örn. hepsini tüm video boyunca). */
    fun katmanHepsi(f: (KatmanOge) -> KatmanOge) =
        guncelle { p -> p.copy(katmanlar = p.katmanlar.map { if (it.kilitli) it else katmanTemiz(runCatching { f(it) }.getOrDefault(it), p.toplamMs) }) }

    /** Otomatik aralıklar (3.43): kaynak sesin yettiği kadarla sınırlı. tur: tum | klip | sona | basa */
    fun katmanAralik(o: KatmanOge, tur: String): KatmanOge {
        val p = proje.value; val T = p.toplamMs.coerceAtLeast(500)
        val k = konum.value.coerceIn(0, T - 300)
        val enUzun = if (o.tur == "ses" && o.kaynakSureMs > 0) ((o.kaynakSureMs - o.kaynakBasMs) / o.hiz.coerceAtLeast(0.25f)).toLong() else Long.MAX_VALUE
        val (a, b) = when (tur) {
            "klip" -> { val i = secili.value.coerceIn(0, (p.klipler.size - 1).coerceAtLeast(0)); val st = baslangic(i); st to st + p.zKlip(i).sureMs }
            "sona" -> k to T
            "basa" -> 0L to maxOf(k, 300L)
            else -> 0L to T
        }
        return o.copy(basMs = a, sonMs = minOf(b, a + enUzun).coerceAtLeast(a + 300),
            klipId = if (tur == "klip") (p.klipler.getOrNull(secili.value)?.id ?: o.klipId) else "")   // 3.76: klip boyunca = o klibe bağlı; diğerleri bağsız
    }

    /** KÖK DÜZELTME (3.43): iki parmak hareketinde NaN/sonsuz değer (parmaklar aynı noktadan başlayınca) katmana yazılıp
     *  çizimde roundToInt() ile uygulamayı çökertiyordu ve projeye kaydolup tekrar açılışta da çöküyordu. Her yazımda temizlenir. */
    private fun katmanTemiz(o: KatmanOge, toplam: Long): KatmanOge {
        fun Float.ok(v: Float) = if (isNaN() || isInfinite()) v else this
        val (mn, mx) = when (o.tur) { "metin" -> 0.02f to 0.3f; "ses" -> 0f to 10f; else -> 0.03f to 1.5f }
        val bas = o.basMs.coerceIn(0, (toplam - 300).coerceAtLeast(0))
        return o.copy(
            x = o.x.ok(0.5f).coerceIn(0f, 1f), y = o.y.ok(0.5f).coerceIn(0f, 1f),
            olcek = o.olcek.ok(if (o.tur == "metin") 0.075f else 0.35f).coerceIn(mn, mx),
            aci = o.aci.ok(0f).coerceIn(-360f, 360f), opaklik = o.opaklik.ok(1f).coerceIn(0f, 1f),
            ses = o.ses.ok(1f).coerceIn(0f, 1.5f), hiz = o.hiz.ok(1f).coerceIn(0.25f, 4f),
            basMs = bas, sonMs = o.sonMs.coerceIn(bas + 300, maxOf(toplam, bas + 300)),
        )
    }

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

    /** Yazı katmanı (3.42): varsayılan stilli başlık, kısa süre (3 sn) ve belirgin bitiş (solarak çıkar). */
    fun katmanMetinEkle(stil: String = "baslik", metin: String = "Yazınız", sure: Long = 3000, x: Float = 0.5f, y: Float = 0.5f,
                        olcek: Float = 0.075f, anim: String = "kay", ad: String = "Yazı") {
        val (a, b) = yeniAralik(sure)
        val o = KatmanOge(tur = "metin", ad = ad, metin = metin, olcek = olcek, kutu = false, stil = stil, anim = anim, x = x, y = y, basMs = a, sonMs = b,
            klipId = klipIdAnda(a))   // 3.76: yazı bulunduğu klibe bağlı başlar (araya klip eklenince birlikte kayar)
        katmanEkleOge(o)
        katmanDuzenle.value = o.id   // 3.44: eklenince yazı hemen düzenlenir
    }

    /** 3.74: kanca yazısı (web Kareden Videoya "Kanca yazısı videoda"): giriş başlığı bitince 3 sn, ortada (dikeyde biraz yukarıda). */
    fun kancaEkle() {
        val p = proje.value; if (p.klipler.isEmpty()) return
        val T = p.toplamMs.coerceAtLeast(1000)
        val a = (if (p.giris.acik) Zaman.girisAralik(p).second else 0L).coerceIn(0, (T - 1000).coerceAtLeast(0))
        val dikey = p.bicim == "9:16"
        val o = KatmanOge(tur = "metin", ad = "Kanca", metin = "Sonuna kadar izle!\nBu manzarayı kaçırma", olcek = if (dikey) 0.063f else 0.07f, stil = "kanca", anim = "pop",
            y = if (dikey) 0.4f else 0.5f, basMs = a, sonMs = minOf(T, a + 3000), klipId = klipIdAnda(a))
        katmanEkleOge(o); katmanDuzenle.value = o.id
        git(a + 400)
    }

    /** 3.53: eski klip yazısını (Klip.yazi) aynı görünümle yazı katmanına taşır; tek geri alma adımı. */
    fun klipYazisiniKatmanaTasi(i: Int) {
        val p0 = proje.value
        val k = p0.klipler.getOrNull(i) ?: return
        val y = k.yazi?.takeIf { it.metin.isNotBlank() } ?: return
        val bas = baslangic(i); val son = bas + p0.zKlip(i).sureMs
        val yy = when (y.konum) { "ust" -> 0.12f; "orta" -> 0.5f; else -> 0.88f }
        val o = KatmanOge(tur = "metin", ad = "Yazı", metin = y.metin, renk = y.renk, kutu = y.kutu, olcek = y.boyut, stil = "duz", anim = "solma",
            y = yy, basMs = bas, sonMs = son.coerceAtLeast(bas + 500))
        guncelle(yapi = true) { p -> p.copy(klipler = p.klipler.mapIndexed { j, kk -> if (j == i) kk.copy(yazi = null) else kk }, katmanlar = p.katmanlar + o) }
        seciliKatman.value = o.id
        mesaj.value = "Yazı katmana taşındı"
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
            val a = 0L; val b = proje.value.toplamMs.coerceAtLeast(500)   // 3.43: logo varsayılan olarak tüm video boyunca
            katmanEkleOge(KatmanOge(tur = "gorsel", ad = "Logo · ${k.ad}", yol = f.path, olcek = 0.14f, opaklik = 0.7f, x = 0.85f, y = 0.18f, basMs = a, sonMs = b))   // 3.72: logo %14 · %70
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
        // 3.48: kopya hemen sağına (orijinal bitince başlar), aynı yerde; sığmazsa üst üste biner (videonun sonu)
        val T = proje.value.toplamMs; val L = o.sonMs - o.basMs
        val yb = if (o.sonMs + 300 <= T) o.sonMs else o.basMs
        val y = o.copy(id = yeniId(), ad = o.ad.removeSuffix(" (kopya)") + " (kopya)", basMs = yb, sonMs = minOf(yb + L, T), kilitli = false,
            kaynakBasMs = if (o.tur == "ses" && yb == o.sonMs) o.kaynakBasMs + (L * o.hiz).toLong() else o.kaynakBasMs)
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
            pl.volume = (o.ses * (if (o.otoKis && konusmaVar(t)) MUZIK_KIS else 1f)).coerceIn(0f, 1f)   // 3.52: otomatik kısma
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
    /** 3.69: Görünüm › Şablon'un açık alt sayfası (klip kartındaki Giriş simgesine çift dokununca "giris" açılır). */
    val sablonSekme = MutableStateFlow("paket")
    fun hatirlaticiDegis(f: (Hatirlatici) -> Hatirlatici) = guncelle { it.copy(hatirlatici = f(it.hatirlatici)) }

    /** 3.82: logo geçişini aç/kapat. Açarken logo yoksa: köşe logosu, yoksa kütüphanedeki son kullanılan / ilk logo. */
    fun logoGecisAc(ac: Boolean) {
        if (!ac) { gecisDegis { it.copy(logo = false) }; return }
        val p = proje.value
        val yol = p.gecis.logoYol.ifEmpty { p.logo?.yol ?: "" }.ifEmpty {
            LogoKutup.yukle(); val l = LogoKutup.ogeler.value
            val son = runCatching { Varsayilan.sonLogo(ctx) }.getOrNull()
            (l.firstOrNull { it.id == son } ?: l.firstOrNull())?.yol ?: "" }
        if (yol.isEmpty()) { uyari.value = "Kütüphanede logo yok. Yayın › Logo'dan bir logo yükle (köşeye koyman gerekmez)."; return }
        gecisDegis { it.copy(logo = true, logoYol = yol) }
        logoGecisGoster()
    }

    /** 3.73: ilk logo geçişinin 1 sn öncesinden oynatır (nasıl göründüğü hemen izlensin). */
    fun logoGecisGoster() {
        val t = proje.value.logoGecisAnlari().firstOrNull() ?: run { mesaj.value = "Logo geçişi için en az 2 klip ve bir logo gerekli"; return }
        if (player.isPlaying || kompAktif.value) oynatDurdur()
        git((t - LogoGecis.ONCE - 1000).coerceAtLeast(0))
        oynatDurdur()
    }

    // ---------- 3.72: şablon yazılarını ekranda düzenleme ----------
    /** Önizlemenin altında açık ekran yazı düzenleyicisi: "giris" | "abone" | "kapanis" | null. */
    val ekranDuzen = MutableStateFlow<String?>(null)
    private var konuTur: String? = null
    private var oneriSira = 0

    /** Şablon öğesinin tam göründüğü ana gider ve durur (stil seçince "ekrana gelsin"). */
    fun sablonGoster(tur: String) {
        val p = proje.value
        if (p.klipler.isEmpty()) return
        val ms = when (tur) {
            "giris" -> { val (a, b) = Zaman.girisAralik(p); (a + minOf(1800L, (b - a) / 2)).coerceAtMost((b - 100).coerceAtLeast(a)) }
            "abone" -> { val (a, b) = Zaman.hatirlaticiAralik(p); (a + 1500).coerceAtMost(b - 100) }
            "kapanis" -> if (p.kapanisMs > 0) p.klipMs + minOf(1500L, p.kapanisMs / 2) else return
            else -> return
        }
        if (player.isPlaying) player.pause()
        git(ms.coerceIn(0, (p.toplamMs - 1).coerceAtLeast(0)))
    }

    /** Giriş başlığı için konuya uygun örnek yazı (ML Kit etiketleri, cihaz içinde). Başlık boşsa doldurur; zorla = başka öneri.
     *  Tarih/konum bandı ve alt başlık boşsa çekim tarihi · Kabataş, Ordu yazılır. Hepsi ekranda düzenlenebilir. */
    fun girisOneri(zorla: Boolean = false) {
        if (!zorla && proje.value.giris.baslik.isNotBlank()) return
        kapsam.launch {
            val tur = konuTur ?: runCatching { MuzikSecici.konudanTur(ctx, proje.value) }.getOrDefault("huzur").also { konuTur = it }
            val liste = KonuOneri.basliklar(tur)
            val baslik = liste[oneriSira++ % liste.size]
            val p = proje.value
            val tarih = p.klipler.firstNotNullOfOrNull { it.tarihMs.takeIf { t -> t > 0 } } ?: p.olusturma
            val gun = java.text.SimpleDateFormat("d MMMM yyyy", TR).format(java.util.Date(tarih))
            girisDegis { g ->
                g.copy(baslik = if (zorla || g.baslik.isBlank()) baslik else g.baslik,
                    bant = g.bant.ifBlank { Tarih.yaz(tarih) + "|Kabataş, Ordu" },
                    alt = g.alt.ifBlank { "$gun · Kabataş, Ordu" })
            }
        }
    }
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
                    gecis = p.gecis.copy(tur = "erime", sureMs = 800),   // fotoğraflar arası yumuşak geçiş, 0,8 sn (kısa klipte kendiliğinden kısalır)
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
            // 3.61: Yayın › Ayarlar'da saklanan son ayarlar varsa onlar uygulanır (stil, geçiş, logo yeri, kapak, altyazı, renk/filtre/efekt)
            val sk = Ayarlar.sakli(ctx)
            if (sk != null) guncelle(true) { p ->
                p.copy(
                    gecis = sk.gecis.copy(logo = p.gecis.logo, logoYol = p.gecis.logoYol, logoSekil = p.gecis.logoSekil),   // 3.82: logo geçişi korunur
                    giris = if ("giris" in sec) p.giris.copy(stil = sk.giris.stil, hareket = sk.giris.hareket, konum = sk.giris.konum, olcek = sk.giris.olcek,
                        efektSes = sk.giris.efektSes, sesSeviye = sk.giris.sesSeviye, boncuk = sk.giris.boncuk, sureMs = sk.giris.sureMs) else p.giris,
                    hatirlatici = if ("abone" in sec) sk.hatirlatici.copy(acik = true) else p.hatirlatici,
                    kapanis = if ("kapanis" in sec) sk.kapanis.copy(acik = true) else p.kapanis,
                    altyaziStil = sk.altyaziStil,
                    kapak = p.kapak.copy(stil = sk.kapak.stil, konum = sk.kapak.konum, vurgu = sk.kapak.vurgu, boyut = sk.kapak.boyut, canli = sk.kapak.canli, logo = sk.kapak.logo),
                    klipler = p.klipler.map { k -> k.copy(renk = sk.renk ?: k.renk, filtre = sk.filtre, filtreGuc = sk.filtreGuc, efekt = sk.efekt, efektGuc = sk.efektGuc) },
                )
            }
            // 3.55: logo varsayılanı — logo yoksa son kullanılan (yoksa en yeni) kütüphane logosu eklenir; konum/boyut/opaklık varsayılana döner
            var logoNotu = ""
            if ("logo" in sec) {
                val mevcut = proje.value.logo
                if (mevcut == null) {
                    val f = withContext(Dispatchers.IO) {
                        runCatching {
                            LogoKutup.yukle()
                            val l = LogoKutup.ogeler.value
                            val k = l.firstOrNull { it.id == sk?.logo?.kutupId } ?: l.firstOrNull { it.id == Varsayilan.sonLogo(ctx) } ?: l.maxByOrNull { it.eklenme }
                            k?.let { kk -> LogoKutup.projeKopyasi(kk)?.let { kk to it } }
                        }.onFailure { Kayit.hata("varsayılan logo", it) }.getOrNull()
                    }
                    if (f != null) guncelle { p -> p.copy(logo = (sk?.logo?.copy(x = -1f, y = -1f) ?: Logo(yol = "")).copy(yol = f.second.path, kutupId = f.first.id, boyut = 0.14f, opaklik = 0.7f)) }   // 3.72: varsayılan %14 · %70
                    else logoNotu = " · logo kütüphanesi boş (Yayın › Logo'dan ekle)"
                } else guncelle { p -> p.copy(logo = p.logo?.copy(konum = sk?.logo?.konum ?: "solUst", boyut = 0.14f, opaklik = 0.7f, x = -1f, y = -1f)) }   // 3.72: varsayılan %14 · %70
            }
            if ("muzik" in sec && proje.value.muzik == null) { val mt = sk?.muzikTur; if (mt != null) hazirMuzik(mt) else otomatikMuzik() }   // 3.61: saklı tür ya da konuya göre
            mesgul.value = null
            val yuzSay = proje.value.klipler.count { it.yuzEtkin }
            mesaj.value = "Varsayılan ayarlar uygulandı" + logoNotu + (if (bicimDegisti) " · biçim YouTube 16:9" else "") + if ("yuz" in sec && yuzSay > 0) " · $yuzSay fotoğrafta yüze yaklaşılıyor" else ""
        }
    }

    fun paketUygula(ad: String) {
        guncelle(true) { p ->
            val g = p.giris.copy(acik = true, baslik = p.giris.baslik.ifBlank { "Ahmet Aşkın" }, alt = p.giris.alt.ifBlank { "Şefin Doğası" })
            when (ad) {
                "reels" -> p.copy(giris = g.copy(stil = "bantlar", sureMs = 3000), gecis = p.gecis.copy(tur = "yakin", sureMs = 500, siyahtanAc = false),
                    hatirlatici = p.hatirlatici.copy(acik = true), kapanis = p.kapanis.copy(acik = true, stil = "cta", sureMs = 5000))
                "sinema" -> p.copy(giris = g.copy(stil = "sinematik", sureMs = 4000), gecis = p.gecis.copy(tur = "siyah", sureMs = 900, siyahtanAc = true),
                    hatirlatici = p.hatirlatici.copy(acik = false), kapanis = p.kapanis.copy(acik = true, stil = "sade", sureMs = 4000))
                else -> p.copy(giris = g.copy(stil = "altbant", sureMs = 3500), gecis = p.gecis.copy(tur = "beyaz", sureMs = 700, siyahtanAc = false),
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

    /** 3.49: toplu düzeltme — her satır sırayla bir altyazıya yazılır (zamanlar korunur); kelime zamanları satır süresine eşit dağıtılır. */
    fun altyaziTopluYaz(metin: String) {
        val satir = metin.replace("\r", "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val l = proje.value.altyazilar
        if (satir.isEmpty() || l.isEmpty()) { uyari.value = "Yapıştırılacak satır ya da altyazı yok."; return }
        guncelle { p -> p.copy(altyazilar = p.altyazilar.mapIndexed { i, a ->
            val y = satir.getOrNull(i) ?: return@mapIndexed a
            val k = y.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val d = ((a.sonMs - a.basMs) / k.size.coerceAtLeast(1)).coerceAtLeast(1)
            a.copy(metin = y, kelimeler = if (a.kelimeler.isEmpty()) emptyList() else k.mapIndexed { j, w -> Kelime(a.basMs + j * d, a.basMs + (j + 1) * d, w) })
        }) }
        mesaj.value = if (satir.size == l.size) "${l.size} satır düzeltildi" else "Satır sayısı farklı: ${satir.size} yapıştırıldı, ${l.size} altyazı var — sırayla yazıldı"
    }
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

    /** 3.58: Kapak › Stil önizlemeleri için küçük kaynak kare (seçili kapak karesi; yoksa null = degrade zemin). */
    suspend fun kapakKaynakKucuk(): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching { kapakKaynak(proje.value.kapak)?.let { b ->
            val o = 480f / maxOf(b.width, b.height)
            if (o < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * o).toInt().coerceAtLeast(1), (b.height * o).toInt().coerceAtLeast(1), true) else b
        } }.getOrNull()
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

    /** 3.70: seçili klibin hızı (0,25×–4×). Video klip bir kez işlenir (HizIsleyici); 1× = özgün dosya ve aralık geri gelir. */
    fun klipHiz(h: Float) {
        val p0 = proje.value
        val k = p0.klipler.getOrNull(secili.value) ?: return
        if (k.foto) { uyari.value = "Fotoğrafta hız yok; ekranda kalma süresini Kırp'tan ayarla."; return }
        if (kotlin.math.abs(h - k.hiz) < 0.01f) return
        val ozgun = k.asilUri.isEmpty()
        val aUri = if (ozgun) k.uri else k.asilUri
        val aBas = if (ozgun) k.basMs else k.asilBasMs
        val aSon = if (ozgun) k.sonMs else k.asilSonMs
        val aKay = if (ozgun) k.kaynakSureMs else k.asilKaynakSureMs
        if (kotlin.math.abs(h - 1f) < 0.01f) {
            if (ozgun) return
            guncelle(true) { p -> p.copy(klipler = p.klipler.map { if (it.id != k.id) it else it.copy(uri = aUri, basMs = aBas, sonMs = aSon, kaynakSureMs = aKay,
                hiz = 1f, asilUri = "", asilBasMs = 0, asilSonMs = 0, asilKaynakSureMs = 0) }) }
            mesaj.value = "Hız: normal (1×)"
            return
        }
        kapsam.launch {
            player.pause()
            mesgul.value = "Hız uygulanıyor (${HizIsleyici.yaz(h)})…"
            val r = runCatching { HizIsleyici.isle(ctx, aUri, aBas, aSon, h, k.sesVar) { pr -> mesgul.value = "Hız uygulanıyor (${HizIsleyici.yaz(h)}) %$pr" } }
            val f = r.getOrNull()
            if (f == null) {
                mesgul.value = null
                r.exceptionOrNull()?.let { Kayit.hata("klip hızı", it) }
                uyari.value = "Hız uygulanamadı: ${r.exceptionOrNull()?.message ?: "bilinmeyen hata"}"
                return@launch
            }
            val u = Uri.fromFile(f)
            val b = withContext(Dispatchers.IO) { runCatching { Medya.incele(ctx, u) }.getOrNull() }
            mesgul.value = null
            val sure = b?.sureMs?.takeIf { it > 0 } ?: ((aSon - aBas) / h).toLong().coerceAtLeast(100)
            val takipVardi = k.takip != null
            guncelle(true) { p -> p.copy(klipler = p.klipler.map { if (it.id != k.id) it else it.copy(uri = u.toString(), basMs = 0, sonMs = sure, kaynakSureMs = sure,
                hiz = h, takip = null, asilUri = aUri, asilBasMs = aBas, asilSonMs = aSon, asilKaynakSureMs = aKay) }) }
            mesaj.value = "Hız: ${HizIsleyici.yaz(h)} · süre ${sureOndalik(sure)}" + if (takipVardi) " · takip yazısını yeniden seç" else ""
        }
    }

    // ---------- 3.70: kayıtlı ayarlarım (adlı görünüm ayarları; başka projede tek dokunuş) ----------
    val kayitliAyarlar = MutableStateFlow(Ayarlar.kayitlilar(ctx))
    fun kayitliKaydet(ad: String) {
        Ayarlar.kayitliEkle(ctx, ad.trim().ifBlank { "Ayarlarım" }, proje.value, proje.value.klipler.getOrNull(secili.value))
        kayitliAyarlar.value = Ayarlar.kayitlilar(ctx); mesaj.value = "Kaydedildi: ${ad.trim().ifBlank { "Ayarlarım" }}"
    }
    fun kayitliSil(ad: String) { Ayarlar.kayitliSil(ctx, ad); kayitliAyarlar.value = Ayarlar.kayitlilar(ctx) }
    fun kayitliUygula(ad: String, sk: SakliAyar) {
        guncelle(true) { p ->
            p.copy(
                gecis = sk.gecis.copy(logo = p.gecis.logo, logoYol = p.gecis.logoYol, logoSekil = p.gecis.logoSekil),   // 3.82: logo geçişi korunur
                giris = p.giris.copy(acik = sk.giris.acik, stil = sk.giris.stil, hareket = sk.giris.hareket, konum = sk.giris.konum, olcek = sk.giris.olcek,
                    efektSes = sk.giris.efektSes, sesSeviye = sk.giris.sesSeviye, boncuk = sk.giris.boncuk, sureMs = sk.giris.sureMs),
                hatirlatici = sk.hatirlatici, kapanis = sk.kapanis, altyaziStil = sk.altyaziStil,
                kapak = p.kapak.copy(stil = sk.kapak.stil, konum = sk.kapak.konum, vurgu = sk.kapak.vurgu, boyut = sk.kapak.boyut, canli = sk.kapak.canli, logo = sk.kapak.logo),
                logo = p.logo?.let { l -> sk.logo?.let { s -> l.copy(konum = s.konum, boyut = s.boyut, opaklik = s.opaklik, x = -1f, y = -1f) } ?: l },
                klipler = p.klipler.map { k -> k.copy(renk = sk.renk, filtre = sk.filtre, filtreGuc = sk.filtreGuc, efekt = sk.efekt, efektGuc = sk.efektGuc) },
            )
        }
        if (proje.value.muzik == null) sk.muzikTur?.let { hazirMuzik(it) }
        mesaj.value = "Uygulandı: $ad"
    }

    /** 3.63: Kapak düzenleyici › İçe aktar — galeriden bir fotoğraf kapak zemini olur (uygulamaya kopyalanır; kaynak silinse de kalır). */
    fun kapakIceAktar(u: Uri) {
        kapsam.launch {
            mesgul.value = "Fotoğraf hazırlanıyor…"
            val r = withContext(Dispatchers.IO) { runCatching { Medya.fotoHazirla(ctx, u) } }
            mesgul.value = null
            val f = r.getOrNull()
            if (f == null) { r.exceptionOrNull()?.let { Kayit.hata("kapak içe aktar", it) }; uyari.value = "Fotoğraf açılamadı."; return@launch }
            kapakDegis { it.copy(kaynakUri = Uri.fromFile(f.first).toString(), kaynakMs = 0, kaynakFoto = true) }
            mesaj.value = "Kapak fotoğrafı içe aktarıldı"
        }
    }

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

    /** 3.48: ms anındaki klibin bitişi — kesilen nesne kendi klibinde kalır, sonrakine sarkmaz. */
    private fun klipSonu(ms: Long): Long {
        val p = proje.value; var b = 0L
        for (i in p.klipler.indices) { b += p.zKlip(i).sureMs; if (ms < b) return b }
        return p.toplamMs
    }

    fun nesneEkle(u: Uri) {
        kapsam.launch {
            mesgul.value = "Nesne kesiliyor…"
            val (f, hata) = try { Nesneci.kes(ctx, u) } catch (e: Exception) { Kayit.hata("nesne", e); null to (e.message ?: "bilinmeyen hata") }
            mesgul.value = null
            if (f == null) { uyari.value = hata ?: "Nesne kesilemedi."; return@launch }
            val p = proje.value
            val a = anlik().coerceIn(0, (p.toplamMs - 500).coerceAtLeast(0))
            val orj = File(f.parentFile, f.nameWithoutExtension + "_orj.jpg")
            val n = Nesne(yol = f.path, basMs = a, sonMs = (a + 5000).coerceAtMost(klipSonu(a)).coerceAtLeast(a + 500), orjYol = if (orj.exists()) orj.path else "")
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
        nesneIs = kapsam.launch { videodanKesIsle(k, bas, sure, an) }
    }

    /** 3.47: dışarıdan (galeriden) seçilen videodan hareketli nesne: videonun başından itibaren kesilir. */
    fun videodanNesneDis(u: Uri, sureMs: Long) {
        player.pause()
        val an = anlik()
        nesneIs?.cancel()
        nesneIs = kapsam.launch {
            nesneIlerleme.value = 0f
            val (k, h) = withContext(Dispatchers.IO) { runCatching { klipHazirla(u) }.getOrElse { null to (it.message ?: "video açılamadı") } }
            if (k == null || k.foto) { nesneIlerleme.value = null; uyari.value = h ?: "Bir video seç."; return@launch }
            videodanKesIsle(k, k.basMs, minOf(sureMs, k.sonMs - k.basMs), an)
        }
    }

    /** 3.47: ekrandaki (oynatıcının üstündeki) fotoğraf klibinden nesne kes. */
    /** 3.61: ekrandaki fotoğraftan kesme — arka plan kendiliğinden bulanık, kesilen nesne tam yerinde ön plan katmanı.
     *  Nesne ile arka planın üst üste oturması için bu klibin otomatik hareketi kapatılır (hareket ederse nesne kayardı). */
    fun ekrandanNesne() {
        val i = player.currentMediaItemIndex
        val k0 = proje.value.klipler.getOrNull(i)
        if (k0 == null || !k0.foto) { uyari.value = "Önizlemede bir fotoğraf klibine gel (videoysa \"Ekrandaki videodan\" kullan)."; return }
        kapsam.launch {
            mesgul.value = "Nesne kesiliyor…"
            val alan = FloatArray(4)
            val (f, hata) = try { Nesneci.kes(ctx, Uri.parse(k0.uri), alan) } catch (e: Exception) { Kayit.hata("nesne", e); null to (e.message ?: "bilinmeyen hata") }
            mesgul.value = null
            if (f == null) { uyari.value = hata ?: "Nesne kesilemedi."; return@launch }
            val p = proje.value
            val k = (p.klipler.getOrNull(i) ?: k0).copy(kenBurns = false)
            val (RW, RH) = Disa.boyut(p.bicim, 1080)
            val a = Yuz.an(k, RW, RH, 0f, i)
            // fotoğrafın karedeki yeri (önizleme ve dışa aktarmadaki yerleşimle aynı hesap)
            val gw = RW / a.vx * a.s; val gh = RH / a.vy * a.s
            val cx = RW / 2f + a.tx * (RW / a.vx) / 2f; val cy = RH / 2f - a.ty * (RH / a.vy) / 2f
            val ux = alan[0] + alan[2] / 2f; val uy = alan[1] + alan[3] / 2f
            val nx = (cx + (ux - 0.5f) * gw) / RW; val ny = (cy + (uy - 0.5f) * gh) / RH
            val olc = (alan[2] * gw / RW).coerceIn(0.02f, 3f)
            val bas = baslangic(i); val son = bas + p.zKlip(i).sureMs
            val orj = File(f.parentFile, f.nameWithoutExtension + "_orj.jpg")
            val n = Nesne(yol = f.path, x = nx, y = ny, olcek = olc, basMs = bas, sonMs = son.coerceAtLeast(bas + 500), golge = true, orjYol = if (orj.exists()) orj.path else "")
            guncelle(true) { pp -> pp.copy(klipler = pp.klipler.mapIndexed { j, kk -> if (j == i) kk.copy(kenBurns = false, arkaBulanik = if (kk.arkaBulanik > 0.01f) kk.arkaBulanik else 0.75f) else kk },
                nesneler = pp.nesneler + n) }
            kaydetSimdi(false)
            seciliNesne.value = n.id
            mesaj.value = "Nesne ön planda · iki parmakla büyüt/küçült, sürükle = taşı"
        }
    }

    private suspend fun videodanKesIsle(k: Klip, bas: Long, sure: Long, an: Long) {
        run {
            nesneIlerleme.value = 0f
            val (n, hata) = try { Nesneci.videodanKes(ctx, k, bas, sure) { f -> nesneIlerleme.value = f } }
                catch (e: kotlinx.coroutines.CancellationException) { nesneIlerleme.value = null; return }
                catch (e: Exception) { null to (e.message ?: "bilinmeyen hata") }
            nesneIlerleme.value = null
            if (n == null) { uyari.value = hata ?: "Nesne kesilemedi."; return }
            val toplam = proje.value.toplamMs
            val yeni = n.copy(basMs = an, sonMs = (an + n.kareSayi * n.kareMs).coerceAtMost(klipSonu(an)).coerceAtLeast(an + 500))
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
        Depo.onizlemeCokmesi = null
        kompSil()
        if (kaydediyor.value) kayitDurdur()
        kaydetSimdi(false)
        kapsam.cancel()
        player.release(); muzikPl.release(); sesPl.release(); kuyrukPl.release()
        katmanPl.values.forEach { try { it.release() } catch (_: Exception) {} }; katmanPl.clear()
        try { sesHavuzu?.release() } catch (_: Exception) {}
    }
}

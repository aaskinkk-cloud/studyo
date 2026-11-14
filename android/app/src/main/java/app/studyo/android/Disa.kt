package app.studyo.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Cozunurluk(val ad: String, val kisa: Int)
val COZUNURLUKLER = listOf(Cozunurluk("720p", 720), Cozunurluk("1080p", 1080), Cozunurluk("2K", 1440), Cozunurluk("4K", 2160))

/** Media3 Transformer kompozisyonu. Dosyalar akış halinde okunur/yazılır; hiçbir video belleğe tümüyle alınmaz. */
object Disa {
    private fun cift(n: Int) = n + (n and 1)
    fun boyut(bicim: String, kisa: Int): Pair<Int, Int> = when (bicim) {
        "9:16" -> kisa to cift(kisa * 16 / 9)
        "1:1" -> kisa to kisa
        else -> cift(kisa * 16 / 9) to kisa
    }

    fun bitHizi(kisa: Int, kalite: Float): Int =
        ((when { kisa <= 720 -> 6_000_000; kisa <= 1080 -> 12_000_000; kisa <= 1440 -> 20_000_000; else -> 40_000_000 }) * kalite).toInt()

    fun tahminiBayt(p: Proje, kisa: Int, kalite: Float): Long = ((bitHizi(kisa, kalite) + 192_000L) * p.toplamMs / 8000.0 * 1.1).toLong()

    /** Geçici dosya + galeriye kopya için iki kat + 300 MB pay. */
    fun gerekenBos(tahmin: Long) = tahmin * 2 + 300L * 1024 * 1024

    fun ciktiDizini(ctx: Context): File = ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir
    fun bosAlan(ctx: Context): Long = try { StatFs(ciktiDizini(ctx).path).availableBytes } catch (e: Exception) { -1 }

    private fun sesIsleyici(kat: Float) = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(1, 1).scaleBy(kat))
        putChannelMixingMatrix(ChannelMixingMatrix.createForConstantGain(2, 2).scaleBy(kat))
    }

    /** HDR yöntemi. Media3 görüntü hattının renk ayarını İLK klibe göre kurar; OpenGL ton eşlemesi yalnız ilk klip HDR ise
     *  devreye girer. Fotoğraf/SDR + HDR karışık projede sonraki HDR klip SDR hattına 10 bit girer ve çözücü hatası verir.
     *  Bu yüzden: 1) her HDR klip kendi çözücüsünde SDR'ye çevrilir (tüm girişler aynı renk düzeninde),
     *  2) telefon bunu desteklemezse HDR, SDR gibi okunur (her yerde çalışır; renkler biraz soluk olabilir). */
    const val HDR_COZUCU = Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_MEDIACODEC
    const val HDR_SDR_GIBI = Composition.HDR_MODE_EXPERIMENTAL_FORCE_INTERPRET_HDR_AS_SDR

    /** Kapanış kartını çıktı çözünürlüğünde JPEG olarak yazar (görüntü klibi olarak eklenir). */
    fun kapanisDosyasi(ctx: Context, p: Proje, W: Int, H: Int, ad: String): File {
        val f = File(ctx.cacheDir, "$ad.jpg")
        val son = p.klipler.lastOrNull()
        val arka = if (p.kapanis.stil != "cam" || son == null) null else try {
            if (son.foto) Medya.ornekleCoz(File(Uri.parse(son.uri).path ?: ""), 1280)
            else Medya.kareTam(ctx, Uri.parse(son.uri), (son.sonMs - 40).coerceAtLeast(son.basMs), 1280)
        } catch (e: Exception) { null }
        val b = Cizim.kapanis(p.kapanis, p.logo, W, H, arka)
        f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
        b.recycle()
        return f
    }

    /** Projenin çıkış kare hızı: videoların en sık kare hızı (24…60). Fotoğraf, kapanış kartı ve geçiş kuyruğu da bu hızda
     *  üretilir; eskiden fotoğraflar sabit 30 fps idi, 60 fps videoyla karışınca klip sınırında kare temposu değişiyordu. */
    fun hedefKareHizi(ctx: Context, p: Proje): Int {
        val l = p.klipler.filter { !it.foto }.map { k -> if (k.kareHizi > 0f) k.kareHizi else Medya.kareHizi(ctx, Uri.parse(k.uri)) }
            .filter { it in 10f..240f }.map { Math.round(it).coerceIn(24, 60) }
        if (l.isEmpty()) return 30
        return l.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key }).first().key
    }

    /** onizleme = true: akıcı önizleme (CompositionPlayer) için — damgalar kompozisyon zamanı, her öğenin süresi bildirilir.
     *  Dışa aktarma (false) 3.x'teki gibi kalır. */
    fun kompozisyon(ctx: Context, p: Proje, W: Int, H: Int, hdrKip: Int = HDR_COZUCU, onizleme: Boolean = false): Composition {
        ZamanKipi.onizleme = onizleme
        try { return kompozisyonKur(ctx, p, W, H, hdrKip, onizleme) } finally { ZamanKipi.onizleme = false }
    }

    /** Üst dizideki boşluk. Dışa aktarmada eskisi gibi "gap". Akıcı önizlemede gap kare üretmez; çok girişli birleştirici
     *  bütün girişlerden kare beklediği için ilk karede kalıyordu (3.13) → önizlemede boşluk, saydam bir resimle doldurulur
     *  (birleştirici pencere dışında zaten görünmez yapar). */
    private fun dolgu(ctx: Context, b: EditedMediaItemSequence.Builder, ms: Long, onizleme: Boolean, fps: Int, W: Int, H: Int) {
        if (ms <= 0) return
        if (!onizleme) { b.addGap(ms * 1000); return }
        val f = File(ctx.cacheDir, "saydam16.png")
        if (!f.exists()) f.outputStream().use { android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(ms).build()
        b.addItem(EditedMediaItem.Builder(mi).setFrameRate(fps)
            .setEffects(Effects(listOf(), listOf<Effect>(Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_STRETCH_TO_FIT)))).build())
    }

    private val sureOnbellek = HashMap<String, Long>()
    /** Önizlemede her öğenin kaynak süresi gerekir (CompositionPlayer). */
    private fun kaynakSureUs(ctx: Context, uri: String, bilinen: Long): Long {
        if (bilinen > 0) return bilinen * 1000
        val ms = sureOnbellek.getOrPut(uri) { Medya.retrieverSure(ctx, Uri.parse(uri)) }
        return if (ms > 0) ms * 1000 else 0L
    }
    private fun EditedMediaItem.Builder.sureli(ctx: Context, onizleme: Boolean, uri: String, bilinenMs: Long, enAzMs: Long): EditedMediaItem.Builder {
        if (!onizleme) return this
        val us = maxOf(kaynakSureUs(ctx, uri, bilinenMs), enAzMs * 1000)
        if (us > 0) setDurationUs(us)
        return this
    }

    private fun kompozisyonKur(ctx: Context, p: Proje, W: Int, H: Int, hdrKip: Int, onizleme: Boolean): Composition {
        val fps = hedefKareHizi(ctx, p)
        Kayit.yaz("  Kare hızı: $fps fps")
        val logo = p.logo?.let { Kaplama.logo(it, W, H) }
        // Zaman çizelgesinde aralıkla görünen öğeler (tüm kliplere ortak bitmapler)
        val zamanli = ArrayList<Triple<Yerlesim, Long, Long>>()
        val (gA, gB) = Zaman.girisAralik(p)
        val seritler = if (p.giris.acik && p.giris.reels) Reels.seritler(p.giris, W, H) else emptyList()
        if (!p.giris.reels) Cizim.giris(p.giris, W, H)?.let { zamanli += Triple(it, gA, gB) }
        // "Abone ol": kart stilinde canlı (soldan açılır, dokunulur, "Abone olundu"); klasikte eski durağan bant
        val hatKart = if (p.hatirlatici.acik && p.hatirlatici.stil != "klasik") Cizim.hatirlaticiKartSet(p.hatirlatici, W, H, p.logo, p.kapanis.kanal) else null
        val (hA, hB) = Zaman.hatirlaticiAralik(p)
        if (hatKart == null) Cizim.hatirlatici(p.hatirlatici, W, H, p.logo, p.kapanis.kanal)?.let { zamanli += Triple(it, hA, hB) }
        for (ns in p.nesneler) if (!ns.hareketli) Katman.nesne(ns, W, H)?.let { zamanli += Triple(it, ns.basMs, ns.sonMs) }
        // Serbest katmanlar (metin/görsel/çıkartma): bitmap bir kez çizilir, kliplere paylaştırılır
        val katmanBmp = p.katmanlar.filter { it.gorsel && !it.gizli && it.sonMs > it.basMs }.mapNotNull { o -> KatmanCizim.bitmap(o, W, H)?.let { o to it } }
        val altyazilar = if (p.altyaziStil.acik) p.altyazilar.filter { it.metin.isNotBlank() && it.sonMs > it.basMs } else emptyList()

        /** [bas, bas+sure) aralığındaki klibe eklenecek zamanlı bindirmeler (klip içi saatle). */
        fun katmanlar(bas: Long, sure: Long): List<TextureOverlay> {
            val ek = ArrayList<TextureOverlay>()
            val son = bas + sure
            for ((y, a, b) in zamanli) if (a < son && b > bas) ek += ZamanliBindirme(y, bas, a, b)
            if (seritler.isNotEmpty() && gA < son && gB > bas) for (sr in seritler) ek += ReelsBindirme(sr, p.giris.hareket, bas, gA, gB)
            if (p.giris.acik && p.giris.boncuk && gA < son && gB > bas) ek += BoncukBindirme(bas, gA, gB, W, H)
            for (ns in p.nesneler) if (ns.hareketli && ns.basMs < son && ns.sonMs > bas) ek += NesneKareBindirme(ns, bas, W, H)
            if (hatKart != null && hA < son && hB > bas) {
                ek += HatirlaticiBindirme(hatKart, bas, hA, hB - hA, W, H, false)
                ek += HatirlaticiBindirme(hatKart, bas, hA, hB - hA, W, H, true)
            }
            for ((o, bm) in katmanBmp) if (o.basMs < son && o.sonMs > bas) ek += KatmanBindirme(o, bm, bas)
            val satir = altyazilar.filter { it.basMs < son && it.sonMs > bas }
            if (satir.isNotEmpty()) ek += AltyaziBindirme(bas, satir, p.altyaziStil, W, H)
            return ek
        }

        val kapanisVar = p.kapanisMs > 0
        val n = p.klipler.size + if (kapanisVar) 1 else 0
        val toplam = p.toplamMs

        // ---- 3.20: TEK GÖRÜNTÜ DİZİSİ (web Kareden Videoya yöntemi: her karede iki klip tek yerde birleşir) ----
        // KÖK NEDEN (3.12–3.19: geçişte göz kırpma, klip sonunda 1–2 sn geri sarıp tekrar): üst üste binen geçiş, ana
        // videoda İKİ ayrı görüntü dizisiyle (iki çözücü + Media3 çok girişli birleştirici) kuruluyordu. Birleştirici iki
        // dizinin karelerini damgaya göre eşler; dizide öğe değişirken/boşluktan çıkarken eşleme kayınca kare atlıyor ya da
        // eski kare tekrar geliyordu. Kod düzeltmesiyle (3.13–3.19) bu kökten giderilemedi.
        // ÇÖZÜM: ana videoda tek görüntü dizisi (geçişsiz projede cihazda doğrulanan 3.1 yolu). Her geçiş, iki klibin
        // yalnız örtüşen T ms'sinden ayrı, kısa bir "geçiş parçası" olarak önceden işlenir (GecisHazir) ve dizide iki klibin
        // arasına konur: [A gövdesi][A→B geçiş parçası][B gövdesi]. Parçanın ilk karesi A'nın, son karesi B'nin devamıdır;
        // sınırlarda birleştirici yok → kırpma, sıçrama, geri sarma olamaz. Önizleme (▶) aynı kompozisyonu oynatır.
        val ogeler = ArrayList<EditedMediaItem>()
        var bas = 0L
        var gecisAdet = 0
        for (j in 0 until n) {
            val klipMi = j < p.klipler.size
            val tam = if (klipMi) p.klipler[j].sureMs else p.kapanisMs
            val tIn = if (j > 0) p.ortusme(j - 1) else 0L
            val tOut = if (klipMi) p.ortusme(j) else 0L
            val govdeBas = bas + tIn
            val govde = (tam - tIn - tOut).coerceAtLeast(40)
            val gec = Gecisler.etkiler(p.gecisAt(j - 1), if (klipMi) p.gecisAt(j) else p.gecis, govde, j > 0, j < n - 1, j == 0 && p.gecis.siyahtanAc, govdeBas)
            ogeler += if (klipMi) parca(ctx, onizleme, p.klipler[j], W, H, logo, gec, katmanlar(govdeBas, govde), j, tam, tIn, govde, fps, govdeBas) else {
                val f = kapanisDosyasi(ctx, p, W, H, if (onizleme) "kapanis_akici" else "kapanis_cikis")
                val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(govde).build()
                val v = ArrayList<Effect>()
                v += Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT)
                v += gec
                val kk = katmanlar(govdeBas, govde)
                if (kk.isNotEmpty()) v += OverlayEffect(kk)
                EditedMediaItem.Builder(mi).setFrameRate(fps).setEffects(Effects(listOf(), v)).build()
            }
            if (tOut > 0) {
                val gb = govdeBas + govde
                val f = GecisHazir.dosya(ctx, p, j, W, H, hdrKip, fps)
                if (!f.exists() || f.length() <= 0) throw IllegalStateException("Geçiş parçası hazır değil (${j + 1}→${j + 2})")
                val mi = MediaItem.Builder().setUri(Uri.fromFile(f))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(tOut).build()).build()
                val v = ArrayList<Effect>()
                v += Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT)
                val kap = ArrayList<TextureOverlay>()
                kap += katmanlar(gb, tOut)
                logo?.let { kap += bindir(it) }
                if (kap.isNotEmpty()) v += OverlayEffect(kap)
                ogeler += EditedMediaItem.Builder(mi).sureli(ctx, onizleme, Uri.fromFile(f).toString(), 0L, tOut).setEffects(Effects(listOf(), v)).build()
                gecisAdet++
            }
            bas += tam - tOut
        }
        val diziler = ArrayList<EditedMediaItemSequence>()
        diziler += EditedMediaItemSequence.Builder(ogeler).experimentalSetForceAudioTrack(true).build()
        // ---- geçiş sesleri (3.22): parçalar yalnız görüntüdür; geçiş süresince giden klibin son T ms'sinin sesi (dizi 1) ve
        // gelen klibin ilk T ms'sinin sesi (dizi 2) yalnız-ses dizileriyle aynı anda çalar (müzik/seslendirme ile aynı yol).
        fun sesOge(k: Klip, bas: Long, son: Long): EditedMediaItem {
            val mi = MediaItem.Builder().setUri(Uri.parse(k.uri))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(bas).setEndPositionMs(son).build()).build()
            val ap: List<AudioProcessor> = if (k.ses != 1f) listOf(sesIsleyici(k.ses)) else listOf()
            return EditedMediaItem.Builder(mi).setRemoveVideo(true).sureli(ctx, onizleme, k.uri, k.kaynakSureMs, son)
                .setEffects(Effects(ap, listOf<Effect>())).build()
        }
        val sesA = EditedMediaItemSequence.Builder(listOf<EditedMediaItem>()).experimentalSetForceAudioTrack(true)
        val sesB = EditedMediaItemSequence.Builder(listOf<EditedMediaItem>()).experimentalSetForceAudioTrack(true)
        var imA = 0L; var imB = 0L; var adA = 0; var adB = 0
        run {
            var b0 = 0L
            for (j in p.klipler.indices) {
                val T = p.ortusme(j)
                b0 += p.klipler[j].sureMs - T          // geçişin başladığı an (zaman çizelgesi)
                if (T <= 0) continue
                val A = p.klipler[j]
                if (!A.foto && A.sesVar && !A.sessiz) {
                    if (b0 > imA) sesA.addGap((b0 - imA) * 1000)
                    sesA.addItem(sesOge(A, (A.sonMs - T).coerceAtLeast(A.basMs), A.sonMs)); imA = b0 + T; adA++
                }
                val B = p.klipler.getOrNull(j + 1)
                if (B != null && !B.foto && B.sesVar && !B.sessiz) {
                    if (b0 > imB) sesB.addGap((b0 - imB) * 1000)
                    sesB.addItem(sesOge(B, B.basMs, (B.basMs + T).coerceAtMost(B.sonMs))); imB = b0 + T; adB++
                }
            }
        }
        if (adA > 0) diziler += sesA.build()
        if (adB > 0) diziler += sesB.build()
        Kayit.yaz("  Diziler: tek görüntü dizisi, ${ogeler.size} öğe ($gecisAdet geçiş parçası), geçiş sesi $adA+$adB")
        p.muzik?.let { m ->
            val mib = MediaItem.Builder().setUri(Uri.parse(m.uri))
            if (!m.dongu && m.sureMs > toplam)
                mib.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(toplam).build())
            val e = EditedMediaItem.Builder(mib.build()).setRemoveVideo(true).sureli(ctx, onizleme, m.uri, m.sureMs, if (!m.dongu && m.sureMs > toplam) toplam else 0L)
                .setEffects(Effects(listOf<AudioProcessor>(sesIsleyici(m.ses)), listOf<Effect>())).build()
            diziler += EditedMediaItemSequence.Builder(e).setIsLooping(m.dongu).build()
        }
        val sesler = p.sesler.sortedBy { it.basMs }.filter { it.basMs < toplam }
        if (sesler.isNotEmpty()) {
            val b = EditedMediaItemSequence.Builder(listOf<EditedMediaItem>()).experimentalSetForceAudioTrack(true)
            var imlec = 0L; var adet = 0
            for (s in sesler) {
                var bas = s.basMs; var kes = 0L
                if (bas < imlec) { kes = imlec - bas; bas = imlec }
                val son = minOf(s.basMs + s.sureMs, toplam)
                if (son - bas < 100) continue
                if (bas > imlec) b.addGap((bas - imlec) * 1000)
                val mi = MediaItem.Builder().setUri(Uri.fromFile(File(s.yol)))
                    .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(kes).setEndPositionMs(kes + (son - bas)).build())
                    .build()
                b.addItem(EditedMediaItem.Builder(mi).sureli(ctx, onizleme, Uri.fromFile(File(s.yol)).toString(), 0L, kes + (son - bas))
                    .setEffects(Effects(listOf<AudioProcessor>(sesIsleyici(s.ses)), listOf<Effect>())).build())
                imlec = son; adet++
            }
            if (adet > 0) diziler += b.build()
        }
        // ---- ses katmanları: her biri ayrı dizi (baştaki boşluk + kırpılmış ses; hız ve seviye) ----
        for (o in p.katmanlar) {
            if (o.tur != "ses" || o.gizli || o.yol.isEmpty()) continue
            val bas = o.basMs.coerceIn(0, toplam); val son = o.sonMs.coerceAtMost(toplam)
            if (son - bas < 100) continue
            val hiz = o.hiz.coerceIn(0.25f, 4f)
            val kaynakSon = (o.kaynakBasMs + ((son - bas) * hiz).toLong()).let { if (o.kaynakSureMs > 0) it.coerceAtMost(o.kaynakSureMs) else it }
            if (kaynakSon - o.kaynakBasMs < 100) continue
            val mi = MediaItem.Builder().setUri(Uri.parse(o.yol))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(o.kaynakBasMs).setEndPositionMs(kaynakSon).build()).build()
            val ap = ArrayList<AudioProcessor>()
            if (hiz != 1f) ap += androidx.media3.common.audio.SpeedChangingAudioProcessor(SabitHiz(hiz))
            ap += sesIsleyici(o.ses)
            val b = EditedMediaItemSequence.Builder(listOf<EditedMediaItem>()).experimentalSetForceAudioTrack(true)
            if (bas > 0) b.addGap(bas * 1000)
            b.addItem(EditedMediaItem.Builder(mi).sureli(ctx, onizleme, o.yol, o.kaynakSureMs, kaynakSon).setEffects(Effects(ap, listOf<Effect>())).build())
            diziler += b.build()
            Kayit.yaz("  Ses katmanı: ${o.ad} ${bas}-${son} ms, hız=$hiz, ses=${o.ses}")
        }
        // ---- reels başlık efekt sesleri ----
        if (seritler.isNotEmpty()) {
            val anlar = Reels.sesAnlari(p.giris, seritler.size, gA, gB).filter { it.first < toplam - 100 }.sortedBy { it.first }
            if (anlar.isNotEmpty()) {
                val b = EditedMediaItemSequence.Builder(listOf<EditedMediaItem>()).experimentalSetForceAudioTrack(true)
                var imlec = 0L
                for ((an, tur) in anlar) {
                    if (an < imlec) continue
                    if (an > imlec) b.addGap((an - imlec) * 1000)
                    val f = Sfx.dosya(ctx, tur)
                    val sure = if (tur == "vuus") 360L else if (tur == "pop") 110L else 70L
                    b.addItem(EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(f))).sureli(ctx, onizleme, Uri.fromFile(f).toString(), 0L, sure)
                        .setEffects(Effects(listOf<AudioProcessor>(sesIsleyici(p.giris.sesSeviye * 1.4f)), listOf<Effect>())).build())
                    imlec = an + sure
                }
                diziler += b.build()
            }
        }
        return Composition.Builder(diziler).setHdrMode(hdrKip).build()
    }

    /** Klibin bir parçası: klip içinde ofsMs'den başlayan uzMs'lik kısım (tamMs: klibin ekrandaki tam süresi).
     *  Hareket/yüz kadrajı ve takip yazısı tam klibe göre hesaplanır → parçalar birbirinin kesintisiz devamıdır. */
    private fun parca(ctx: Context, onizleme: Boolean, k: Klip, W: Int, H: Int, logo: Yerlesim?, gecis: List<Effect>, zamanli: List<TextureOverlay>,
                      i: Int, tamMs: Long, ofsMs: Long, uzMs: Long, fps: Int, cizelgeBas: Long, sonEtki: List<Effect> = emptyList(), sesKapali: Boolean = false): EditedMediaItem {
        val mib = MediaItem.Builder().setUri(Uri.parse(k.uri))
        val klipSon = (k.basMs + ofsMs + uzMs).coerceAtMost(k.sonMs)
        if (k.foto) mib.setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(uzMs)
        else mib.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(k.basMs + ofsMs).setEndPositionMs(klipSon).build())
        val v = ArrayList<Effect>()
        hareket(k, W, H, i, tamMs, ofsMs, cizelgeBas)?.let { v += it }   // akıllı kadraj + otomatik hareket
        v += Presentation.createForWidthAndHeight(W, H,
            if (k.doldur) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT)
        v += gecis
        val kap = ArrayList<TextureOverlay>()
        kap += zamanli
        k.yazi?.let { Kaplama.yazi(it, W, H) }?.let { kap += bindir(it) }
        if (!k.foto) k.takip?.let { t -> Katman.takipGorsel(t, W, H)?.let { kap += TakipBindirme(k, t, it, W, H, cizelgeBas, ofsMs) { ms -> Yuz.an(k, W, H, ms.toFloat() / tamMs.coerceAtLeast(1), i, tamMs / 1000f) } } }
        logo?.let { kap += bindir(it) }
        if (kap.isNotEmpty()) v += OverlayEffect(kap)
        v += sonEtki
        val sesli = !k.foto && k.sesVar && !k.sessiz && !sesKapali
        val a: List<AudioProcessor> = if (sesli && k.ses != 1f) listOf(sesIsleyici(k.ses)) else listOf()
        val b = EditedMediaItem.Builder(mib.build()).setEffects(Effects(a, v))
        if (!k.foto) b.sureli(ctx, onizleme, k.uri, k.kaynakSureMs, klipSon)
        if (k.foto) b.setFrameRate(fps)
        if (!k.foto && !sesli) b.setRemoveAudio(true)
        return b.build()
    }

    /** Geçiş parçası kompozisyonu (yalnız T ms): üstte giden klibin son T ms'si, altta gelen klibin (ya da kapanışın) ilk
     *  T ms'si. İki dizi de tek öğeli, aynı uzunlukta, 0'dan başlar, boşluk yok — birleştiricinin en basit, kararlı hâli.
     *  Logo ve zaman çizelgesi katmanları burada çizilmez; ana videoda parçanın üstüne bir kez konur (çift görünmez). */
    fun gecisKompozisyonu(ctx: Context, p: Proje, i: Int, W: Int, H: Int, hdrKip: Int, fps: Int): Composition {
        ZamanKipi.onizleme = false        // parça ayrı bir dışa aktarmadır: damgalar 0'dan başlar
        val A = p.klipler[i]
        val T = p.ortusme(i)
        val tur = p.gecisAt(i).tur
        val son = if (tur == "erime") listOf<Effect>(EriyenMaske(0L, T, T, -1L)) else emptyList()
        // 3.22: parça YALNIZ GÖRÜNTÜ (ses yok, zorunlu ses izi yok). Media3'te resim + zorunlu ses izi iki dizide yarışa
        // giriyordu (SequenceAssetLoader.onOutputFormat checkStateNotNull, cihaz kaydı 14.11 12:20). Geçişin sesi ana
        // kompozisyonda ayrı ses dizileriyle verilir (bkz. kompozisyonKur, "geçiş sesleri").
        val ust = parca(ctx, false, A, W, H, null, emptyList(), emptyList(), i, A.sureMs, A.sureMs - T, T, fps, 0L, son, sesKapali = true)
        val alt = if (i + 1 < p.klipler.size) {
            val B = p.klipler[i + 1]
            parca(ctx, false, B, W, H, null, emptyList(), emptyList(), i + 1, B.sureMs, 0L, T, fps, 0L, sesKapali = true)
        } else {
            val f = kapanisDosyasi(ctx, p, W, H, "kapanis_gecis")
            val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(T).build()
            EditedMediaItem.Builder(mi).setFrameRate(fps)
                .setEffects(Effects(listOf(), listOf<Effect>(Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT)))).build()
        }
        val d0 = EditedMediaItemSequence.Builder(listOf(ust)).build()   // ilk dizi = üstte
        val d1 = EditedMediaItemSequence.Builder(listOf(alt)).build()
        return Composition.Builder(listOf(d0, d1)).setHdrMode(hdrKip)
            .setVideoCompositorSettings(GecisBirlestirici(listOf(tur), listOf(longArrayOf(0L, T)), W, H)).build()
    }

    /** Yedek geçiş parçası (asıl parça bu telefonda işlenemezse): TEK dizi, iki öğe — A'nın son T ms'sinin ilk yarısı
     *  karararak biter, B'nin ilk T ms'sinin ikinci yarısı açılarak başlar. Birleştirici yok (geçişsiz projelerdeki yol).
     *  Parçanın ilk karesi yine A'nın, son karesi B'nin devamıdır; süre aynıdır, zaman çizelgesi kaymaz. */
    fun gecisYedekKompozisyonu(ctx: Context, p: Proje, i: Int, W: Int, H: Int, hdrKip: Int, fps: Int): Composition {
        ZamanKipi.onizleme = false
        val A = p.klipler[i]
        val T = p.ortusme(i)
        val y1 = T / 2; val y2 = T - y1
        fun sol(cikis: Boolean, sure: Long): List<Effect> {
            val saat = Saat(0L)
            return listOf(androidx.media3.effect.RgbMatrix { t, _ ->
                val f = (saat.yerelMs(t).toFloat() / sure.coerceAtLeast(1)).coerceIn(0f, 1f)
                val e = f * f * (3f - 2f * f)
                val k = if (cikis) 1f - e else e
                floatArrayOf(k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 1f)
            })
        }
        val ogeler = ArrayList<EditedMediaItem>()
        ogeler += parca(ctx, false, A, W, H, null, emptyList(), emptyList(), i, A.sureMs, A.sureMs - T, y1, fps, 0L, sol(true, y1), sesKapali = true)
        ogeler += if (i + 1 < p.klipler.size) {
            val B = p.klipler[i + 1]
            parca(ctx, false, B, W, H, null, emptyList(), emptyList(), i + 1, B.sureMs, y1, y2, fps, 0L, sol(false, y2), sesKapali = true)
        } else {
            val f = kapanisDosyasi(ctx, p, W, H, "kapanis_gecis")
            val mi = MediaItem.Builder().setUri(Uri.fromFile(f)).setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(y2).build()
            EditedMediaItem.Builder(mi).setFrameRate(fps)
                .setEffects(Effects(listOf(), listOf<Effect>(Presentation.createForWidthAndHeight(W, H, Presentation.LAYOUT_SCALE_TO_FIT)) + sol(false, y2))).build()
        }
        return Composition.Builder(listOf(EditedMediaItemSequence.Builder(ogeler).build())).setHdrMode(hdrKip).build()
    }

    internal fun kodlayici(ctx: Context, kisa: Int, kalite: Float) = DefaultEncoderFactory.Builder(ctx)
        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(bitHizi(kisa, kalite)).build())
        .setEnableFallback(true).build()

    private fun bindir(y: Yerlesim): TextureOverlay = BitmapOverlay.createStaticBitmapOverlay(
        y.bmp,
        StaticOverlaySettings.Builder().setBackgroundFrameAnchor(y.zx, y.zy).setOverlayFrameAnchor(y.kx, y.ky).setAlphaScale(y.alfa).build()
    )

    /** Fotoğrafta yavaş yakınlaşma (%8; dikey kişi fotoğrafında yüze doğru, bkz. Yuz). Zaman damgası klibin ilk karesinden ölçülür. */
    /** tamMs: klibin ekrandaki tam süresi (geçiş dahil), basMs: bu parçanın klip içindeki başlangıcı (kuyrukta tam − T).
     *  Hareket kapalı ve kadraj ortadaysa efekt eklenmez. */
    private fun hareket(k: Klip, W: Int, H: Int, i: Int, tamMs: Long, basMs: Long, cizelgeBas: Long): MatrixTransformation? {
        val D = tamMs / 1000f
        if (!k.kenBurns) { val a = Yuz.an(k, W, H, 0f, i, D); if (Yuz.sabitMi(a)) return null; val m = Yuz.matris(a); return MatrixTransformation { m } }
        val saat = Saat(cizelgeBas)
        return MatrixTransformation { t ->
            val f = ((basMs + saat.yerelMs(t).toFloat()) / tamMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            Yuz.matris(Yuz.an(k, W, H, f, i, D))
        }
    }
}

/** Kaydedilen dosyayı denetler: boş/eksik/bozuk dosya "tamamlandı" sayılmaz. */
object Dogrula {
    fun dosya(ctx: Context, uri: Uri, beklenenMs: Long, beklenenBoyut: Long): String? {
        val boy = try { ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L } catch (e: Exception) { -1L }
        if (boy <= 0) return "Dosya boş (0 bayt) ya da açılamadı."
        if (beklenenBoyut > 0 && boy != beklenenBoyut) return "Dosya eksik yazıldı (${Medya.mb(boy)} / ${Medya.mb(beklenenBoyut)})."
        var sure = 0L
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            var v = false
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) v = true
                if (f.containsKey(MediaFormat.KEY_DURATION)) sure = maxOf(sure, f.getLong(MediaFormat.KEY_DURATION) / 1000)
            }
            if (!v) return "Dosyada görüntü izi yok."
        } catch (e: Exception) {
            return "Dosya okunamadı: ${e.message}"
        } finally {
            ex.release()
        }
        if (sure <= 0) sure = Medya.retrieverSure(ctx, uri)
        if (beklenenMs > 0 && sure < beklenenMs * 0.95 - 700) return "Video eksik: ${sure / 1000} sn (beklenen ${beklenenMs / 1000} sn)."
        val kare = try { Medya.kare(ctx, uri, sure / 2, 64) } catch (e: Exception) { null }
        if (kare == null) return "Videodan görüntü okunamadı."
        return null
    }
}

sealed class DisaDurum {
    object Bos : DisaDurum()
    data class Calisiyor(val yuzde: Int, val kalanSn: Long, val asama: String) : DisaDurum()
    data class Duraklatildi(val mesaj: String, val devamEdebilir: Boolean) : DisaDurum()
    data class Dogrulaniyor(val asama: String) : DisaDurum()
    data class Tamam(val uri: String, val ad: String, val boyut: Long, val sureMs: Long, val sn: Long, val not: String = "") : DisaDurum()
    data class Hata(val mesaj: String, val ayrinti: String) : DisaDurum()
}

/** Dışa aktarma ön plan hizmeti: ekran kapansa da uygulama arka plana geçse de sürer (bildirimde görünür). */
class DisaServisi : Service() {
    companion object {
        val durum = MutableStateFlow<DisaDurum>(DisaDurum.Bos)
        var sonProje: String? = null
        private const val KANAL = "disa"
        private const val NO = 7

        fun baslat(ctx: Context, projeId: String, kisa: Int, kalite: Float) {
            sonProje = projeId
            durum.value = DisaDurum.Calisiyor(0, -1, "hazırlanıyor")
            val i = Intent(ctx, DisaServisi::class.java).setAction("basla").putExtra("p", projeId).putExtra("k", kisa).putExtra("q", kalite)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun devam(ctx: Context) {
            durum.value = DisaDurum.Calisiyor(0, -1, "devam ediliyor")
            ContextCompat.startForegroundService(ctx, Intent(ctx, DisaServisi::class.java).setAction("devam"))
        }

        fun komut(ctx: Context, eylem: String) {
            try { ctx.startService(Intent(ctx, DisaServisi::class.java).setAction(eylem)) } catch (e: Exception) { Kayit.hata("komut $eylem", e) }
        }

        fun sifirla(ctx: Context) {
            ctx.getSharedPreferences("disa", 0).getString("yol", null)?.let { File(it).delete() }
            ctx.getSharedPreferences("disa", 0).edit().clear().apply()
            durum.value = DisaDurum.Bos
        }

        /** Uygulama açılırken: önceki dışa aktarma sistem tarafından kesildiyse kullanıcıya söyle. */
        fun acilisKontrol(ctx: Context) {
            if (durum.value != DisaDurum.Bos) return
            val sp = ctx.getSharedPreferences("disa", 0)
            val yol = sp.getString("yol", null) ?: return
            sonProje = sp.getString("proje", null)
            if (sp.getBoolean("calisiyor", false)) {
                File(yol).delete()
                sp.edit().clear().apply()
                durum.value = DisaDurum.Hata("Önceki dışa aktarma yarıda kesildi (uygulama sistem tarafından kapatıldı).",
                    "Yarım dosya silindi, bozuk video bırakılmadı. Yeniden başlat. Sık oluyorsa: Ayarlar › Uygulamalar › Stüdyo › Pil › Kısıtlamasız.")
            } else {
                val tek = sp.getBoolean("tek", false) && File(yol).exists()
                durum.value = DisaDurum.Duraklatildi("Yarım kalan bir dışa aktarma var.", tek)
            }
        }
    }

    private var transformer: Transformer? = null
    private var kilit: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())
    private val kapsam = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tutucu = ProgressHolder()
    private var t0 = 0L
    private var sonBildirim = 0L
    private var proje: Proje? = null
    private var yol: File? = null
    private var eskiYol: File? = null
    private var beklenenMs = 0L

    private fun prefs(): SharedPreferences = getSharedPreferences("disa", 0)
    private fun nm(): NotificationManager = getSystemService(NotificationManager::class.java)!!

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "basla" -> {
                val onde = onPlana("Hazırlanıyor…")
                val pid = intent.getStringExtra("p")
                if (!onde) hata("Arka planda çalışma izni alınamadı; dışa aktarma başlatılmadı.", "Ön plan hizmeti reddedildi (hata kaydında ayrıntı var). Bozuk dosya oluşmaması için işlem başlatılmadı.")
                else if (pid == null) hata("Proje bulunamadı.", "") else basla(pid, intent.getIntExtra("k", 1080), intent.getFloatExtra("q", 1f), false, Disa.HDR_COZUCU)
            }
            "devam" -> {
                val onde = onPlana("Devam ediliyor…")
                val sp = prefs()
                val pid = sp.getString("proje", null)
                if (!onde) hata("Arka planda çalışma izni alınamadı; dışa aktarma başlatılmadı.", "Ön plan hizmeti reddedildi (hata kaydında ayrıntı var).")
                else if (pid == null) hata("Devam edilecek dışa aktarma bulunamadı.", "") else basla(pid, sp.getInt("kisa", 1080), sp.getFloat("kalite", 1f), true, sp.getInt("hdrKip", Disa.HDR_COZUCU))
            }
            "duraklat" -> duraklat()
            "iptal" -> iptal()
            else -> if (transformer == null && hazirlikIs?.isActive != true) bitir(false, null)
        }
        return START_NOT_STICKY
    }

    /** Ön plana geçer. ServiceCompat KULLANILMAZ: androidx.core, Android 15'in "mediaProcessing" türünü tanımadığı için
     *  türü siler ("type none") ve Android 15+ bunu reddeder → hizmet ön planda olmaz, ekran kapanınca uygulama dondurulur.
     *  @return ön plana geçebildiyse true */
    private fun onPlana(metin: String): Boolean {
        val n = bildirim(metin, 0, false)
        return try {
            when {
                Build.VERSION.SDK_INT >= 35 -> startForeground(NO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                Build.VERSION.SDK_INT >= 29 -> startForeground(NO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> startForeground(NO, n)
            }
            true
        } catch (e: Exception) {
            Kayit.hata("ön plan (mediaProcessing)", e)
            // Yedek: dataSync türü (Android 15'te de geçerli)
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(NO, n)
                true
            } catch (e2: Exception) {
                Kayit.hata("ön plan (dataSync)", e2); false
            }
        }
    }

    private fun bildirim(metin: String, yuzde: Int, bitti: Boolean): Notification {
        val nm = nm()
        if (nm.getNotificationChannel(KANAL) == null)
            nm.createNotificationChannel(NotificationChannel(KANAL, "Video dışa aktarma", NotificationManager.IMPORTANCE_LOW))
        val ac = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, KANAL)
            .setSmallIcon(if (bitti) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(if (bitti) "Stüdyo" else "Video oluşturuluyor")
            .setContentText(metin).setContentIntent(ac).setOnlyAlertOnce(true).setOngoing(!bitti).setAutoCancel(bitti)
        if (!bitti) {
            b.setProgress(100, yuzde, yuzde <= 0)
            b.addAction(0, "Duraklat", PendingIntent.getService(this, 1,
                Intent(this, DisaServisi::class.java).setAction("duraklat"), PendingIntent.FLAG_IMMUTABLE))
        }
        return b.build()
    }

    private fun okunabilir(u: Uri): Boolean = try {
        contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }

    private var kisa = 1080
    private var kalite = 1f
    private var hdrKip = Disa.HDR_COZUCU

    private fun basla(pid: String, kisa: Int, kalite: Float, devam: Boolean, hdrKip: Int) {
        this.kisa = kisa; this.kalite = kalite; this.hdrKip = hdrKip
        if (transformer != null || hazirlikIs?.isActive == true) return
        val p = Depo.yukle(pid)
        if (p == null) { hata("Proje bulunamadı.", ""); return }
        if (p.klipler.isEmpty()) { hata("Projede klip yok.", ""); return }
        sonProje = pid; proje = p; beklenenMs = p.toplamMs
        val (W, H) = Disa.boyut(p.bicim, kisa)
        val dizin = Disa.ciktiDizini(this)
        val tahmin = Disa.tahminiBayt(p, kisa, kalite)
        val bos = Disa.bosAlan(this)
        if (bos in 0 until Disa.gerekenBos(tahmin)) {
            hata("Telefonda yer yetersiz.", "Gereken yaklaşık ${Medya.mb(Disa.gerekenBos(tahmin))}, boş ${Medya.mb(bos)}. " +
                "(Geçici dosya + galeriye kopya için video boyutunun iki katı yer gerekir.)"); return
        }
        for (kl in p.klipler) if (!okunabilir(Uri.parse(kl.uri))) {
            hata("Bir dosyaya erişilemiyor: ${kl.ad}", "Dosya silinmiş, taşınmış ya da erişim izni kalkmış olabilir. Klibi silip yeniden ekle."); return
        }
        val sp = prefs()
        val eski = if (devam) sp.getString("yol", null)?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 } else null
        // Kaldığı yerden devam (Transformer.resume) yalnız tek diziyle çalışır: müzik, seslendirme, efekt sesi ve üst üste binen geçiş yoksa
        val tek = p.muzik == null && p.sesler.isEmpty() && Gecisler.pencereler(p).isEmpty() &&
            !(p.giris.acik && p.giris.reels && p.giris.efektSes)
        val cikti = File(dizin, "studyo_${System.currentTimeMillis()}.mp4")
        yol = cikti; eskiYol = eski
        sp.edit().putString("proje", pid).putInt("kisa", kisa).putFloat("kalite", kalite).putString("yol", cikti.path)
            .putBoolean("tek", tek).putBoolean("calisiyor", true).putInt("hdrKip", hdrKip).apply()
        kilit = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studyo:disa").apply {
            setReferenceCounted(false); acquire(6 * 3600_000L)
        }
        Kayit.yaz("Dışa aktarma ${if (eski != null) "DEVAM" else "BAŞLA"}: ${p.klipler.size} klip, ${p.toplamMs / 1000} sn, ${W}x$H, " +
            "${Disa.bitHizi(kisa, kalite) / 1000} kbps, müzik=${p.muzik != null}, seslendirme=${p.sesler.size}, boş=${Medya.mb(bos)}, tahmin=${Medya.mb(tahmin)}")
        p.klipler.forEach { Kayit.yaz("  ${it.tur} ${it.ad} ${Medya.mb(it.boyutBayt)} ${it.gen}x${it.yuk} ${it.kodek} hdr=${it.hdr} ${it.basMs}-${it.sonMs}") }
        Kayit.yaz("  HDR yöntemi: " + if (hdrKip == Disa.HDR_SDR_GIBI) "SDR gibi oku (yedek)" else "çözücüde SDR'ye çevir")
        // 3.20: önce üst üste binen geçişlerin kısa parçaları işlenir (önbellekte olan atlanır), sonra ana video tek dizi olarak
        hazirlikIs = kapsam.launch {
            try {
                GecisHazir.hazirla(this@DisaServisi, p, W, H, hdrKip, kisa) { k, n ->
                    durum.value = DisaDurum.Calisiyor(0, -1, "geçişler hazırlanıyor $k/$n")
                    nm().notify(NO, bildirim("Geçişler hazırlanıyor $k/$n", 0, false))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: GecisHatasi) {
                val hdrVar = p.klipler.any { it.hdr }
                if (hdrVar && hdrKip == Disa.HDR_COZUCU && (e.ad.contains("DECOD") || e.ad.contains("VIDEO_FRAME_PROCESSING"))) {
                    Kayit.yaz("Geçiş parçasında HDR çözücü hatası → yedek yöntemle (HDR'yi SDR gibi oku) yeniden deneniyor")
                    prefs().edit().clear().apply()
                    durum.value = DisaDurum.Calisiyor(0, -1, "HDR uyumlu yöntemle yeniden deneniyor")
                    hazirlikIs = null
                    h.post { basla(pid, kisa, kalite, false, Disa.HDR_SDR_GIBI) }
                    return@launch
                }
                hazirlikIs = null
                hata(hataMetni(e.ad), e.message ?: e.ad); return@launch
            } catch (e: Exception) {
                Kayit.hata("geçiş parçaları", e)
                hazirlikIs = null
                hata("Geçişler hazırlanamadı.", e.message ?: e.toString()); return@launch
            }
            hazirlikIs = null
            anaVideo(p, W, H, cikti, eski, tek)
        }
    }

    private var hazirlikIs: kotlinx.coroutines.Job? = null

    private fun anaVideo(p: Proje, W: Int, H: Int, cikti: File, eski: File?, tek: Boolean) {
        try {
            val comp = Disa.kompozisyon(this, p, W, H, hdrKip)
            val enc = Disa.kodlayici(this, kisa, kalite)
            val tr = Transformer.Builder(this)
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(enc).addListener(dinleyici)
                // Varsayılan 10 sn: büyük/4K klip geçişlerinde çözücü açılırken haksız yere "MUXING_TIMEOUT" verebiliyor.
                // 60 sn: gerçek takılmayı yine yakalar, sonsuz beklemez.
                .setMaxDelayBetweenMuxerSamplesMs(60_000)
                .build()
            transformer = tr; t0 = SystemClock.elapsedRealtime()
            if (eski != null && tek) tr.resume(comp, cikti.path, eski.path)
            else { eski?.delete(); eskiYol = null; tr.start(comp, cikti.path) }
            durum.value = DisaDurum.Calisiyor(0, -1, if (eski != null && tek) "kaldığı yerden devam" else "başladı")
            h.post(ilerleme)
        } catch (e: Exception) {
            Kayit.hata("başlatılamadı", e)
            transformer = null
            hata("Dışa aktarma başlatılamadı.", e.message ?: e.toString())
        }
    }

    private fun sureMetni(sn: Long): String = if (sn >= 3600) "${sn / 3600} sa ${sn % 3600 / 60} dk" else sureYaz(sn * 1000)

    private val ilerleme = object : Runnable {
        override fun run() {
            val tr = transformer ?: return
            val st = tr.getProgress(tutucu)
            if (st == Transformer.PROGRESS_STATE_AVAILABLE) {
                val p = tutucu.progress
                val gecen = (SystemClock.elapsedRealtime() - t0) / 1000
                val kalan = if (p >= 2) gecen * (100 - p) / p else -1
                durum.value = DisaDurum.Calisiyor(p, kalan, "kodlanıyor")
                if (SystemClock.elapsedRealtime() - sonBildirim > 1500) {
                    sonBildirim = SystemClock.elapsedRealtime()
                    nm().notify(NO, bildirim("%$p" + if (kalan >= 0) " · kalan ~${sureMetni(kalan)}" else "", p, false))
                }
            }
            h.postDelayed(this, 500)
        }
    }

    private val dinleyici = object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            h.removeCallbacks(ilerleme); transformer = null
            eskiYol?.delete(); eskiYol = null
            kaydetVeDogrula(exportResult)
        }

        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            h.removeCallbacks(ilerleme); transformer = null
            val ad = ExportException.getErrorCodeName(exportException.errorCode)
            Kayit.hata("Dışa aktarma hatası $ad", exportException)
            yol?.delete()
            val ayrinti = "$ad: ${exportException.message ?: ""}" + (exportException.cause?.let { " · ${it.javaClass.simpleName}: ${it.message}" } ?: "")
            val p = proje
            val hdrVar = p?.klipler?.any { it.hdr } == true
            if (p != null && hdrVar && hdrKip == Disa.HDR_COZUCU && (ad.contains("DECOD") || ad.contains("VIDEO_FRAME_PROCESSING"))) {
                // Aynı yöntemi tekrar denemek yerine yedek HDR yöntemine geç (bir kez)
                Kayit.yaz("HDR klipte çözücü hatası → yedek yöntemle (HDR'yi SDR gibi oku) otomatik yeniden deneniyor")
                try { kilit?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
                kilit = null
                prefs().edit().clear().apply()
                durum.value = DisaDurum.Calisiyor(0, -1, "HDR uyumlu yöntemle yeniden deneniyor")
                h.post { basla(p.id, kisa, kalite, false, Disa.HDR_SDR_GIBI) }
                return
            }
            hata(hataMetni(ad), ayrinti)
        }
    }

    private fun hataMetni(ad: String): String = when {
        ad.contains("DECOD") -> "Videolardan biri bu telefonda çözülemedi (biçim/kodek desteklenmiyor)."
        ad.contains("ENCOD") -> "Seçilen çözünürlükte video kodlayıcı açılamadı. Daha düşük bir çözünürlük seç."
        ad.contains("AUDIO") -> "Ses işlenemedi. Müzik ya da seslendirme dosyasını değiştirip dene."
        ad.contains("VIDEO_FRAME_PROCESSING") -> "Görüntü işlenemedi (efekt, yazı ya da HDR dönüştürme)."
        ad.contains("MUXING") -> "Video dosyası yazılamadı."
        ad.startsWith("ERROR_CODE_IO") -> "Bir dosya okunamadı ya da yazılamadı (silinmiş/taşınmış olabilir ya da yer bitti)."
        else -> "Video oluşturulamadı."
    }

    private fun kaydetVeDogrula(r: ExportResult) {
        val f = yol ?: return
        durum.value = DisaDurum.Dogrulaniyor("Dosya doğrulanıyor ve galeriye kaydediliyor…")
        nm().notify(NO, bildirim("Doğrulanıyor…", 100, false))
        val sn = (SystemClock.elapsedRealtime() - t0) / 1000
        kapsam.launch {
            val sonuc = withContext(Dispatchers.IO) { runCatching { galeriyeKaydet(f, r) } }
            withContext(Dispatchers.IO) { f.delete() }
            sonuc.onSuccess { t ->
                prefs().edit().clear().apply()
                durum.value = t.copy(sn = sn)
                Kayit.yaz("TAMAM ${t.ad} ${Medya.mb(t.boyut)} ${t.sureMs} ms, ${sn} sn sürdü")
                bitir(true, "Kaydedildi: ${t.ad}")
            }.onFailure { e ->
                Kayit.hata("Kaydetme/doğrulama", e)
                hata("Video kaydedilemedi.", e.message ?: e.toString())
            }
        }
    }

    private fun galeriyeKaydet(f: File, r: ExportResult): DisaDurum.Tamam {
        Dogrula.dosya(this, Uri.fromFile(f), beklenenMs, f.length())?.let { throw IllegalStateException(it) }
        val ad = (proje?.ad ?: "Stüdyo").replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60) + "_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, ad)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Stüdyo")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val d = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Stüdyo").apply { mkdirs() }
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(d, ad).path)
            }
        }
        val koleksiyon = if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = contentResolver.insert(koleksiyon, cv) ?: throw IllegalStateException("Galeride kayıt oluşturulamadı.")
        try {
            val o = contentResolver.openOutputStream(uri, "w") ?: throw IllegalStateException("Galeri dosyası açılamadı.")
            o.use { out -> f.inputStream().use { it.copyTo(out, 8 * 1024 * 1024) } }
            Dogrula.dosya(this, uri, beklenenMs, f.length())?.let { throw IllegalStateException(it) }
            if (Build.VERSION.SDK_INT >= 29)
                contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            throw e
        }
        return DisaDurum.Tamam(uri.toString(), "Movies/Stüdyo/$ad", f.length(), r.durationMs, 0,
            listOfNotNull(
                if (hdrKip == Disa.HDR_SDR_GIBI) "HDR klipler yedek yöntemle işlendi (telefonun çözücüsü HDR→SDR çevirisini yapamadı); renkler biraz soluk görünebilir." else null,
                if (GecisHazir.yedekSayisi > 0) "${GecisHazir.yedekSayisi} geçiş yedek yöntemle (kısa kararıp açılarak) yapıldı. Hata kaydını paylaşırsan asıl yöntemi düzeltirim." else null
            ).joinToString("\n"))
    }

    private fun duraklat() {
        if (transformer == null && hazirlikIs?.isActive == true) {
            hazirlikIs?.cancel(); hazirlikIs = null
            prefs().edit().putBoolean("calisiyor", false).putBoolean("tek", false).apply()
            Kayit.yaz("Duraklatıldı (geçişler hazırlanırken)")
            durum.value = DisaDurum.Duraklatildi("Duraklatıldı. Hazırlanan geçişler saklandı; devam edince kalanlardan sürer.", false)
            bitir(false, null); return
        }
        val tr = transformer ?: return
        h.removeCallbacks(ilerleme)
        try { tr.cancel() } catch (e: Exception) { Kayit.hata("duraklat", e) }
        transformer = null
        val tek = prefs().getBoolean("tek", false)
        prefs().edit().putBoolean("calisiyor", false).apply()
        Kayit.yaz("Duraklatıldı (tek dizi=$tek)")
        durum.value = DisaDurum.Duraklatildi(
            if (tek) "Duraklatıldı. Kaldığı yerden devam edebilirsin."
            else "Duraklatıldı. Müzik ya da seslendirme olan projelerde devam baştan başlar.", tek)
        bitir(false, null)
    }

    private fun iptal() {
        hazirlikIs?.cancel(); hazirlikIs = null
        h.removeCallbacks(ilerleme)
        try { transformer?.cancel() } catch (_: Exception) {}
        transformer = null
        yol?.delete(); eskiYol?.delete()
        sifirla(this)
        Kayit.yaz("İptal edildi")
        bitir(false, null)
    }

    private fun hata(m: String, a: String) {
        h.removeCallbacks(ilerleme)
        transformer = null
        prefs().edit().clear().apply()
        durum.value = DisaDurum.Hata(m, a)
        Kayit.yaz("HATA (kullanıcıya): $m · $a")
        bitir(true, m)
    }

    private fun bitir(goster: Boolean, metin: String?) {
        try { kilit?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        kilit = null
        if (goster && metin != null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            nm().notify(NO, bildirim(metin, 100, true))
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    /** Android 15: ön plan hizmet süresi dolarsa kontrollü duraklat (dosya bozulmaz). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Kayit.yaz("Sistem süre sınırı: duraklatılıyor")
        duraklat()
    }

    override fun onDestroy() {
        h.removeCallbacks(ilerleme)
        kapsam.cancel()
        try { kilit?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        super.onDestroy()
    }
}

package app.studyo.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Geçiş parçası hazırlanamadı. ad: Media3 hata kodu adı (HDR yedeğine geçiş kararı için). */
class GecisHatasi(val ad: String, mesaj: String) : Exception(mesaj)

/** Üst üste binen geçişlerin kısa parçalarını (T ms) önceden işler ve önbellekte tutar (3.20).
 *  Ana video tek görüntü dizisi olarak kurulur; bu parçalar iki klibin arasına konur (bkz. Disa.kompozisyonKur).
 *  Anahtar, parçayı etkileyen her şeyi içerir: klip değişmedikçe parça yeniden işlenmez. */
object GecisHazir {
    private const val SURUM = "g321"

    /** Son hazırlıkta yedek yöntemle yapılan geçiş sayısı (kullanıcıya not olarak gösterilir). */
    var yedekSayisi = 0
        private set

    private fun dizin(ctx: Context): File = File(ctx.cacheDir, "gecis").apply { mkdirs() }

    private fun anahtar(p: Proje, i: Int, W: Int, H: Int, hdrKip: Int, fps: Int): String {
        val sonraki = if (i + 1 < p.klipler.size) p.klipler[i + 1].toString()
        else "kapanis:${p.kapanis}|${p.logo}|${p.klipler.lastOrNull()}"
        val metin = "$SURUM|${p.klipler[i]}|$sonraki|${p.ortusme(i)}|${p.gecisAt(i).tur}|$W|$H|$fps|$hdrKip"
        return MessageDigest.getInstance("SHA-1").digest(metin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun dosya(ctx: Context, p: Proje, i: Int, W: Int, H: Int, hdrKip: Int, fps: Int): File =
        File(dizin(ctx), anahtar(p, i, W, H, hdrKip, fps) + ".mp4")

    /** Eksik geçiş parçalarını işler. ilerleme(sıra, toplam) ana iş parçacığında çağrılır.
     *  Önbellekte olanlar atlanır; iptal edilirse çalışan işlem durdurulur, yarım dosya bırakılmaz. */
    suspend fun hazirla(ctx: Context, p: Proje, W: Int, H: Int, hdrKip: Int, kisa: Int, ilerleme: (Int, Int) -> Unit = { _, _ -> }) {
        val fps = withContext(Dispatchers.IO) { Disa.hedefKareHizi(ctx, p) }
        val gerekli = p.klipler.indices.filter { p.ortusme(it) > 0 }.associateWith { dosya(ctx, p, it, W, H, hdrKip, fps) }
        withContext(Dispatchers.IO) {
            gerekli.values.forEach { if (it.exists()) it.setLastModified(System.currentTimeMillis()) }
            temizle(ctx, gerekli.values.map { it.name }.toSet())
        }
        val eksik = gerekli.filter { (_, f) -> !f.exists() || f.length() <= 0 }.keys.toList()
        if (eksik.isEmpty()) return
        yedekSayisi = 0
        Kayit.yaz("Geçiş parçaları: ${eksik.size} parça hazırlanacak (${W}x$H, $fps fps)")
        for ((sira, i) in eksik.withIndex()) {
            ilerleme(sira + 1, eksik.size)
            val hedef = dosya(ctx, p, i, W, H, hdrKip, fps)
            val gecici = File(hedef.path + ".yarim")
            gecici.delete()
            val A = p.klipler[i]; val B = p.klipler.getOrNull(i + 1)
            Kayit.yaz("  Geçiş ${i + 1}→${i + 2} hazırlanıyor: ${p.gecisAt(i).tur} ${p.ortusme(i)} ms · A=${A.tur} ${A.gen}x${A.yuk} ${A.kodek} hdr=${A.hdr} ses=${A.sesVar && !A.sessiz} · " +
                (B?.let { "B=${it.tur} ${it.gen}x${it.yuk} ${it.kodek} hdr=${it.hdr} ses=${it.sesVar && !it.sessiz}" } ?: "B=kapanış"))
            val t0 = System.currentTimeMillis()
            try {
                val comp = withContext(Dispatchers.Default) { Disa.gecisKompozisyonu(ctx, p, i, W, H, hdrKip, fps) }
                calistir(ctx, comp, gecici, kisa)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                // Asıl parça işlenemedi: nedeni kayda yazılır, aynı geçiş tek dizili yedek yöntemle hazırlanır (video yine oluşur)
                Kayit.yaz("  Geçiş ${i + 1}→${i + 2}: asıl yöntem başarısız (${e.message}) → yedek yöntem (kararıp açılma)")
                gecici.delete()
                val yedek = withContext(Dispatchers.Default) { Disa.gecisYedekKompozisyonu(ctx, p, i, W, H, hdrKip, fps) }
                try { calistir(ctx, yedek, gecici, kisa) } catch (e2: kotlinx.coroutines.CancellationException) { throw e2 } catch (e2: Exception) {
                    throw if (e is GecisHatasi) GecisHatasi(e.ad, "${e.message} · yedek: ${e2.message}") else e2
                }
                yedekSayisi++
            }
            if (!gecici.exists() || gecici.length() <= 0) { gecici.delete(); throw GecisHatasi("BOS", "Geçiş parçası boş çıktı (${i + 1}→${i + 2})") }
            if (!gecici.renameTo(hedef)) { gecici.delete(); throw GecisHatasi("IO", "Geçiş parçası kaydedilemedi") }
            Kayit.yaz("  Geçiş ${i + 1}→${i + 2}: ${p.ortusme(i)} ms, ${p.gecisAt(i).tur}, ${System.currentTimeMillis() - t0} ms")
        }
    }

    /** Transformer ana iş parçacığında kurulur ve çalışır; iptalde durdurulur. */
    private suspend fun calistir(ctx: Context, comp: Composition, cikti: File, kisa: Int) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { dev ->
            val h = Handler(Looper.getMainLooper())
            val tr = Transformer.Builder(ctx)
                .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(Disa.kodlayici(ctx, kisa, 2f))      // yüksek bit hızı: ikinci kodlamada kayıp görünmez
                .setMaxDelayBetweenMuxerSamplesMs(60_000)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (dev.isActive) dev.resume(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        cikti.delete()
                        val ad = ExportException.getErrorCodeName(exportException.errorCode)
                        // Asıl neden (cause) ayrıca yazılır: ExportException yığınının ilk satırları onu göstermiyordu
                        var c: Throwable? = exportException.cause; var neden = ""
                        while (c != null) { neden += " · ${c.javaClass.simpleName}: ${c.message}"; c = c.cause.takeIf { it !== c } }
                        Kayit.yaz("HATA Geçiş parçası $ad$neden")
                        Kayit.hata("Geçiş parçası nedeni", exportException.cause ?: exportException)
                        if (dev.isActive) dev.resumeWithException(GecisHatasi(ad, "$ad$neden"))
                    }
                }).build()
            dev.invokeOnCancellation { h.post { try { tr.cancel() } catch (_: Exception) {}; cikti.delete() } }
            try { tr.start(comp, cikti.path) } catch (e: Exception) {
                cikti.delete()
                if (dev.isActive) dev.resumeWithException(GecisHatasi("BASLAMA", "Geçiş başlatılamadı: ${e.message}"))
            }
        }
    }

    /** Önbellek: 5 günden eski parçalar ve 400 MB'ı aşan en eski parçalar silinir. */
    private fun temizle(ctx: Context, koru: Set<String>) {
        val l = dizin(ctx).listFiles()?.toMutableList() ?: return
        val simdi = System.currentTimeMillis()
        l.filter { it.name !in koru && (it.name.endsWith(".yarim") || simdi - it.lastModified() > 5 * 86_400_000L) }.forEach { it.delete() }
        val kalan = l.filter { it.exists() && it.name !in koru }.sortedBy { it.lastModified() }.toMutableList()
        var top = kalan.sumOf { it.length() }
        while (top > 400L * 1024 * 1024 && kalan.isNotEmpty()) { val f = kalan.removeAt(0); top -= f.length(); f.delete() }
    }
}

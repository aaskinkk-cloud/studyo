package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fotoğraftan nesne kesme: Google ML Kit "Subject Segmentation" (ücretsiz, telefonun içinde çalışır; fotoğraf dışarı gitmez).
 *  Model Google Play Hizmetleri üzerinden bir kez iner (manifestte bağımlılık olarak bildirildi). */
object Nesneci {
    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
    }

    /** Saydam kenarları kırpar (nesnenin çevresinde %3 pay bırakır); kırpma dikdörtgeniyle döner. */
    private fun saydamKirp(b: Bitmap): Bitmap? = saydamKirpAlan(b)?.let { (x, y, w, h) -> Bitmap.createBitmap(b, x, y, w, h) }

    private fun saydamKirpAlan(b: Bitmap): IntArray? {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 12) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < x0 || y1 < y0) return null
        val pay = (maxOf(x1 - x0, y1 - y0) * 0.03f).toInt()
        x0 = (x0 - pay).coerceAtLeast(0); y0 = (y0 - pay).coerceAtLeast(0)
        x1 = (x1 + pay).coerceAtMost(w - 1); y1 = (y1 + pay).coerceAtMost(h - 1)
        return intArrayOf(x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }


    /** @return kesilmiş nesnenin PNG dosyası ya da hata metni */
    suspend fun kes(ctx: Context, uri: Uri): Pair<File?, String?> {
        val hazir = withContext(Dispatchers.IO) { Medya.fotoHazirla(ctx, uri, 2048) } ?: return null to "Fotoğraf açılamadı."
        val b = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(hazir.first.path).also { hazir.first.delete() } } ?: return null to "Fotoğraf okunamadı."
        val ayirici = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        try {
            val sonuc = try {
                ayirici.process(InputImage.fromBitmap(b, 0)).bekle()
            } catch (e: Exception) {
                Kayit.hata("nesne kesme", e)
                val m = e.message ?: ""
                return null to if (m.contains("download", true) || m.contains("module", true) || m.contains("Waiting", true))
                    "Nesne kesme modeli Google Play Hizmetleri'nden iniyor. İnternete bağlı olarak 1-2 dakika sonra tekrar dene."
                else "Nesne kesilemedi: $m"
            }
            val on = sonuc.foregroundBitmap ?: return null to "Fotoğrafta belirgin bir nesne bulunamadı."
            val alan = withContext(Dispatchers.Default) { saydamKirpAlan(on) } ?: return null to "Fotoğrafta belirgin bir nesne bulunamadı."
            val kirp = Bitmap.createBitmap(on, alan[0], alan[1], alan[2], alan[3])
            val ad = yeniId()
            val f = File(Depo.dizin("nesne"), "$ad.png")
            withContext(Dispatchers.IO) {
                f.outputStream().use { kirp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // cam fırçası için özgün görüntü (aynı kırpım); ön plan bitmapi ile aynı boyutta olduğu varsayılır
                if (b.width == on.width && b.height == on.height) {
                    val o = Bitmap.createBitmap(b, alan[0], alan[1], alan[2], alan[3])
                    File(Depo.dizin("nesne"), "${ad}_orj.jpg").outputStream().use { o.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                }
            }
            Kayit.yaz("Nesne kesildi: ${kirp.width}x${kirp.height}")
            return f to null
        } finally {
            try { ayirici.close() } catch (_: Exception) {}
        }
    }

    /** Videodan hareketli nesne: [basMs, basMs+sureMs] (kaynak zamanı) aralığında saniyede ~15 kare, her karede nesne kesilir.
     *  Kareler 720 piksele küçültülüp PNG olarak yazılır; video belleğe alınmaz. @return Nesne (zaman alanları sonra ayarlanır) ya da hata */
    suspend fun videodanKes(ctx: Context, k: Klip, basMs: Long, sureMs: Long, ilerleme: (Float) -> Unit): Pair<Nesne?, String?> = withContext(Dispatchers.Default) {
        if (Build.VERSION.SDK_INT < 28) return@withContext null to "Bu özellik Android 9 ve üstünde çalışır."
        val r = MediaMetadataRetriever()
        val ayirici = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder().enableForegroundBitmap().build())
        val id = yeniId()
        val dizin = File(Depo.dizin("nesne"), id).apply { mkdirs() }
        try {
            r.setDataSource(ctx, Uri.parse(k.uri))
            val sayi = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            val donus = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (sayi < 2) return@withContext null to "Videonun kareleri okunamadı."
            val fps = sayi * 1000.0 / k.kaynakSureMs.coerceAtLeast(1)
            val adim = maxOf(1, Math.round(fps / 15.0).toInt())
            val kareMs = (adim * 1000.0 / fps).toLong().coerceAtLeast(1)
            val i0 = (basMs * fps / 1000).toInt().coerceIn(0, sayi - 1)
            val i1 = ((basMs + sureMs) * fps / 1000).toInt().coerceIn(i0, sayi - 1)
            val prm = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            val dikey = k.yuk > k.gen
            var yazilan = 0; var i = i0
            while (i <= i1) {
                ensureActive()
                val ham = try { r.getFrameAtIndex(i, prm) } catch (e: Exception) { null } ?: break
                var b = ham
                if ((b.height > b.width) != dikey && k.gen != k.yuk && donus % 180 != 0) {
                    b = Bitmap.createBitmap(ham, 0, 0, ham.width, ham.height, Matrix().apply { postRotate(donus.toFloat()) }, true)
                    if (b !== ham) ham.recycle()
                }
                val olc = 720f / maxOf(b.width, b.height)
                if (olc < 1f) { val k2 = Bitmap.createScaledBitmap(b, (b.width * olc).toInt(), (b.height * olc).toInt(), true); if (k2 !== b) b.recycle(); b = k2 }
                val on = try { ayirici.process(InputImage.fromBitmap(b, 0)).bekle().foregroundBitmap } catch (e: Exception) {
                    Kayit.hata("videodan nesne", e)
                    val m = e.message ?: ""
                    dizin.deleteRecursively()
                    return@withContext null to if (m.contains("download", true) || m.contains("module", true) || m.contains("Waiting", true))
                        "Nesne kesme modeli Google Play Hizmetleri'nden iniyor. 1-2 dakika sonra tekrar dene." else "Nesne kesilemedi: $m"
                }
                val cikti = on ?: Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
                File(dizin, String.format(java.util.Locale.US, "%04d.png", yazilan)).outputStream().use { cikti.compress(Bitmap.CompressFormat.PNG, 100, it) }
                b.recycle()
                yazilan++
                i += adim
                ilerleme(((i - i0).toFloat() / (i1 - i0 + 1)).coerceIn(0f, 1f))
            }
            if (yazilan == 0) { dizin.deleteRecursively(); return@withContext null to "Kare alınamadı." }
            Kayit.yaz("Videodan nesne: ${k.ad} $yazilan kare, ${kareMs} ms aralık")
            Nesne(id = id, yol = File(dizin, "0000.png").path, kareDizin = dizin.path, kareMs = kareMs, kareSayi = yazilan, golge = false) to null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) { dizin.deleteRecursively(); throw e }
            Kayit.hata("videodan nesne", e); dizin.deleteRecursively()
            null to "Nesne kesilemedi: ${e.message}"
        } finally {
            try { r.release() } catch (_: Exception) {}
            try { ayirici.close() } catch (_: Exception) {}
        }
    }
}

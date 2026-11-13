package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
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

    /** Saydam kenarları kırpar (nesnenin çevresinde %3 pay bırakır). */
    private fun saydamKirp(b: Bitmap): Bitmap? {
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
        return Bitmap.createBitmap(b, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
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
            val kirp = withContext(Dispatchers.Default) { saydamKirp(on) } ?: return null to "Fotoğrafta belirgin bir nesne bulunamadı."
            val f = File(Depo.dizin("nesne"), yeniId() + ".png")
            withContext(Dispatchers.IO) { f.outputStream().use { kirp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            Kayit.yaz("Nesne kesildi: ${kirp.width}x${kirp.height}")
            return f to null
        } finally {
            try { ayirici.close() } catch (_: Exception) {}
        }
    }
}

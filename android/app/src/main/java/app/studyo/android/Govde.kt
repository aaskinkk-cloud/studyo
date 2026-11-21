package app.studyo.android

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 3.77: kişinin gövde çevresi — ML Kit "Subject Segmentation" (ücretsiz, cihaz içinde; nesne kesmedekiyle aynı model).
 * Her durak için, durağın (yüzün) üstünde olduğu kişinin maskesi alınır; maskenin ağırlık merkezinden 120 ışın atılır,
 * her ışında maskenin en uzak noktası çevre sayılır, çevre yumuşatılıp biraz dışa açılır. Sonuç: kişiyi saran, düzgün, kapalı bir eğri
 * (kollar/boşluklar yumuşakça sarılır; ışık gövdenin çevresinde akıcı döner). Bulunamazsa boş döner (çizimde baş-omuz kapsülü kullanılır).
 */
object Govde {
    private const val ISIN = 120

    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    }

    /** Duraklar sırasıyla çevre listeleri (boş = bulunamadı). */
    suspend fun cevreler(uri: String, noktalar: List<OdakNoktasi>): List<List<Float>> = withContext(Dispatchers.Default) {
        val bos = noktalar.map { emptyList<Float>() }
        val b = YuzTur.oku(uri, 1024) ?: return@withContext bos
        val W = b.width; val H = b.height
        val ayirici = SubjectSegmentation.getClient(SubjectSegmenterOptions.Builder()
            .enableMultipleSubjects(SubjectSegmenterOptions.SubjectResultOptions.Builder().enableConfidenceMask().build())
            .enableForegroundConfidenceMask().build())
        try {
            val r = try { ayirici.process(InputImage.fromBitmap(b, 0)).bekle() } catch (e: Throwable) { Kayit.hata("gövde: ayırma", e); return@withContext bos }
            // her kişinin maskesi (tam fotoğraf boyunda değil, kendi kutusunda)
            class Kisi(val x0: Int, val y0: Int, val w: Int, val h: Int, val m: FloatArray)
            val kisiler = r.subjects.mapNotNull { s ->
                val fb: FloatBuffer = s.confidenceMask ?: return@mapNotNull null
                val arr = FloatArray(s.width * s.height); fb.rewind(); fb.get(arr, 0, min(arr.size, fb.remaining()))
                Kisi(s.startX, s.startY, s.width, s.height, arr)
            }
            val on = r.foregroundConfidenceMask?.let { fb -> FloatArray(W * H).also { fb.rewind(); fb.get(it, 0, min(it.size, fb.remaining())) } }
            val sonuc = noktalar.map { n ->
                val px = (n.x * W).toInt().coerceIn(0, W - 1); val py = (n.y * H).toInt().coerceIn(0, H - 1)
                fun ic(k: Kisi, x: Int, y: Int) = x >= k.x0 && y >= k.y0 && x < k.x0 + k.w && y < k.y0 + k.h && k.m[(y - k.y0) * k.w + (x - k.x0)] > 0.5f
                // durağın üstündeki kişi; yoksa kutusu en yakın olan (yüzün biraz altına da bakılır: yüz bazen maskenin kenarında kalır)
                val kisi = kisiler.firstOrNull { ic(it, px, py) || ic(it, px, (py + n.yb * H * 0.4f).toInt().coerceIn(0, H - 1)) }
                    ?: kisiler.minByOrNull { k -> hypot((px - (k.x0 + k.w / 2f)).toDouble(), (py - (k.y0 + k.h / 2f)).toDouble()) }
                        ?.takeIf { k -> px >= k.x0 - W * 0.05f && px <= k.x0 + k.w + W * 0.05f && py >= k.y0 - H * 0.05f && py <= k.y0 + k.h + H * 0.05f }
                val maske: (Int, Int) -> Boolean = when {
                    kisi != null -> { x, y -> ic(kisi, x, y) }
                    on != null -> { x, y -> x in 0 until W && y in 0 until H && on[y * W + x] > 0.5f }
                    else -> return@map emptyList()
                }
                cevre(maske, W, H, px, py, if (kisi != null) intArrayOf(kisi.x0, kisi.y0, kisi.w, kisi.h) else intArrayOf(0, 0, W, H))
            }
            Kayit.yaz("Gövde çevresi: ${kisiler.size} kişi, ${sonuc.count { it.isNotEmpty() }}/${noktalar.size} durak")
            sonuc
        } finally { try { ayirici.close() } catch (_: Exception) {}; b.recycle() }
    }

    /** Maskenin (kutu içinde) ağırlık merkezinden ışınlarla çevre; 0…1 fotoğraf koordinatı. */
    private fun cevre(m: (Int, Int) -> Boolean, W: Int, H: Int, px: Int, py: Int, kutu: IntArray): List<Float> {
        val (x0, y0, kw, kh) = kutu.toList()
        val adim = max(1, max(kw, kh) / 160)
        var sx = 0.0; var sy = 0.0; var n = 0
        var y = y0; while (y < y0 + kh) { var x = x0; while (x < x0 + kw) { if (m(x, y)) { sx += x; sy += y; n++ }; x += adim }; y += adim }
        if (n < 20) return emptyList()
        var cx = (sx / n).toFloat(); var cy = (sy / n).toFloat()
        // merkez maskenin dışındaysa (ör. kollar arası) yüz ile merkez arasındaki ilk iç noktaya kaydır
        if (!m(cx.toInt(), cy.toInt())) { for (t in 1..20) { val f = t / 20f; val qx = cx + (px - cx) * f; val qy = cy + (py - cy) * f; if (m(qx.toInt(), qy.toInt())) { cx = qx; cy = qy; break } } }
        val enUzun = hypot(kw.toFloat(), kh.toFloat())
        val r = FloatArray(ISIN)
        for (i in 0 until ISIN) {
            val a = -Math.PI / 2 + 2 * Math.PI * i / ISIN          // tepeden başlar, saat yönünde (y aşağı)
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            var d = 0f; var son = 0f
            while (d < enUzun) {
                val x = (cx + dx * d).toInt(); val yy = (cy + dy * d).toInt()
                if (x < 0 || yy < 0 || x >= W || yy >= H) break
                if (m(x, yy)) son = d
                d += adim
            }
            r[i] = son
        }
        // yumuşatma (dairesel kayan ortalama, iki kez) ve biraz dışa açma
        repeat(2) { val t = r.copyOf(); for (i in 0 until ISIN) { var s = 0f; for (k in -3..3) s += t[(i + k + ISIN) % ISIN]; r[i] = s / 7f } }
        val pay = max(kw, kh) * 0.035f
        val l = ArrayList<Float>(ISIN * 2)
        for (i in 0 until ISIN) {
            val a = -Math.PI / 2 + 2 * Math.PI * i / ISIN
            val rr = r[i] + pay
            l += (Math.round((cx + cos(a).toFloat() * rr) / W * 1000f) / 1000f)
            l += (Math.round((cy + sin(a).toFloat() * rr) / H * 1000f) / 1000f)
        }
        return if (l.all { it.isFinite() }) l else emptyList()   // 3.78: bozuk değer kaydedilmez
    }
}

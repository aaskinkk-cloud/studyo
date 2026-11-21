package app.studyo.android

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 3.87: yüz turunda kişinin SİLÜETİ (Ahmet'in gösterdiği Samsung Galeri efekti: ışık iki alt uçtan omuzları ve başı izleyerek tepede buluşur).
 * Kişi maskesi ML Kit Selfie Segmentation ile (ücretsiz, model uygulamada, cihazda) bir kez bulunur; yüzün bağlı olduğu kişinin dış hattı
 * (Moore sınır izleme) çıkarılır, alt kesimdeki düz kenar atılır → soldan sağa açık bir hat. Noktalar fotoğrafa göre 0…1 (x, y sırayla).
 * Hata ya da kapanma olursa uygulama etkilenmez; bekçi işareti kalırsa (o sırada uygulama kapandıysa) bir daha çalışmaz, ince çizgi kullanılır.
 */
object Siluet {
    const val NOKTA = 160
    private const val BEKCI = "siluetBekci"

    fun kullanilabilir(): Boolean = runCatching { !Depo.app.getSharedPreferences("ayar", 0).getBoolean(BEKCI, false) }.getOrDefault(true)

    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    }

    /** Her yüz için silüet (bulunamazsa boş liste). Sıra yuzler ile aynı. */
    suspend fun hesapla(uri: String, yuzler: List<OdakNoktasi>): List<List<Float>> = withContext(Dispatchers.Default) {
        if (yuzler.none { it.yb > 0f } || !kullanilabilir()) return@withContext yuzler.map { emptyList() }
        val sp = Depo.app.getSharedPreferences("ayar", 0)
        sp.edit().putBoolean(BEKCI, true).commit()
        Kayit.yaz("Silüet bulma başladı (${yuzler.size} yüz)")
        try {
            val kaynak = YuzTur.oku(uri, 1280) ?: return@withContext yuzler.map { emptyList() }
            val sc = min(1f, 640f / max(kaynak.width, kaynak.height))
            val b = if (sc < 1f) Bitmap.createScaledBitmap(kaynak, (kaynak.width * sc).roundToInt().coerceAtLeast(16), (kaynak.height * sc).roundToInt().coerceAtLeast(16), true) else kaynak
            val seg = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
            val maske = try { seg.process(InputImage.fromBitmap(b, 0)).bekle() } finally { runCatching { seg.close() } }
            val w = maske.width; val h = maske.height
            val buf = maske.buffer.order(ByteOrder.nativeOrder()); buf.rewind()
            val m = BooleanArray(w * h) { buf.float > 0.5f }
            if (b !== kaynak) b.recycle(); kaynak.recycle()
            val gen = genislet(m, w, h, max(1, (min(w, h) / 200f).roundToInt()))   // hat kişinin biraz dışında (efektteki gibi)
            yuzler.map { y -> if (y.yb <= 0f) emptyList() else runCatching { hat(gen, w, h, y) }.onFailure { Kayit.hata("silüet hattı", it) }.getOrNull() ?: emptyList() }
                .also { l -> Kayit.yaz("Silüet: ${l.count { it.isNotEmpty() }}/${yuzler.size} kişi") }
        } catch (e: Throwable) {
            Kayit.hata("silüet bulma", e); yuzler.map { emptyList() }
        } finally { sp.edit().putBoolean(BEKCI, false).apply() }
    }

    private fun genislet(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        // ayrılabilir kare genişletme (önce yatay, sonra dikey)
        val a = BooleanArray(w * h)
        for (y in 0 until h) { var son = -100000
            for (x in 0 until w) { if (m[y * w + x]) son = x; if (x - son <= r) a[y * w + x] = true }
            son = 100000
            for (x in w - 1 downTo 0) { if (m[y * w + x]) son = x; if (son - x <= r) a[y * w + x] = true } }
        val c = BooleanArray(w * h)
        for (x in 0 until w) { var son = -100000
            for (y in 0 until h) { if (a[y * w + x]) son = y; if (y - son <= r) c[y * w + x] = true }
            son = 100000
            for (y in h - 1 downTo 0) { if (a[y * w + x]) son = y; if (son - y <= r) c[y * w + x] = true } }
        return c
    }

    private fun hat(m: BooleanArray, w: Int, h: Int, yz: OdakNoktasi): List<Float>? {
        val fx = yz.x * w; val fy = yz.y * h; val fh = max(4f, yz.yb * h)
        val x0 = (fx - 3f * fh).roundToInt().coerceIn(0, w - 1); val x1 = (fx + 3f * fh).roundToInt().coerceIn(0, w - 1)
        val y0 = (fy - 1.4f * fh).roundToInt().coerceIn(0, h - 1); val y1 = (fy + 3f * fh).roundToInt().coerceIn(0, h - 1)
        val rw = x1 - x0 + 1; val rh = y1 - y0 + 1
        if (rw < 8 || rh < 8) return null
        fun ic(x: Int, y: Int) = x in x0..x1 && y in y0..y1 && m[y * w + x]
        // tohum: yüzün ortasına en yakın kişi pikseli
        var tx = -1; var ty = -1; var en = Float.MAX_VALUE
        val ara = (fh * 0.6f).roundToInt().coerceAtLeast(2)
        for (y in (fy.toInt() - ara)..(fy.toInt() + ara)) for (x in (fx.toInt() - ara)..(fx.toInt() + ara)) if (ic(x, y)) {
            val d = hypot(x - fx, y - fy); if (d < en) { en = d; tx = x; ty = y } }
        if (tx < 0) return null
        // bağlı bileşen (bölge içinde)
        val kom = BooleanArray(rw * rh)
        val yigin = IntArray(rw * rh); var n = 0
        kom[(ty - y0) * rw + (tx - x0)] = true; yigin[n++] = (ty - y0) * rw + (tx - x0)
        while (n > 0) {
            val i = yigin[--n]; val x = i % rw; val y = i / rw
            for (k in 0 until 4) {
                val nx = x + DX4[k]; val ny = y + DY4[k]
                if (nx < 0 || ny < 0 || nx >= rw || ny >= rh) continue
                val j = ny * rw + nx
                if (!kom[j] && m[(ny + y0) * w + nx + x0]) { kom[j] = true; yigin[n++] = j }
            }
        }
        fun k(x: Int, y: Int) = x in 0 until rw && y in 0 until rh && kom[y * rw + x]
        // başlangıç: en üstteki satırın en soldaki pikseli
        var sx = -1; var sy = -1
        loop@ for (y in 0 until rh) for (x in 0 until rw) if (kom[y * rw + x]) { sx = x; sy = y; break@loop }
        if (sx < 0) return null
        // Moore sınır izleme (8 komşu, saat yönü)
        val px = ArrayList<Int>(); val py = ArrayList<Int>()
        var cx = sx; var cy = sy; var geldi = 4      // batıdan gelindi sayılır (radyal tarama: bir önceki sınır pikselinden saat yönünde)
        val sinir = 4 * (rw + rh) * 4
        var adim = 0
        do {
            px += cx; py += cy
            var bulundu = false
            for (t in 0 until 8) {
                val d = (geldi + 1 + t) % 8
                val nx = cx + DX8[d]; val ny = cy + DY8[d]
                if (k(nx, ny)) { geldi = (d + 4) % 8; cx = nx; cy = ny; bulundu = true; break }
            }
            if (!bulundu) break
            adim++
        } while (!(cx == sx && cy == sy) && adim < sinir)
        if (px.size < 24) return null
        // bölge kesiminde kalan noktalar (alt kenar ve yanlar) atılır → açık hat
        val kes = BooleanArray(px.size) { i -> py[i] >= rh - 2 || py[i] + y0 >= fy + 2.6f * fh || px[i] <= 0 || px[i] >= rw - 1 || (y1 >= h - 1 && py[i] + y0 >= h - 2) }
        // tepe: yüzün üstündeki en yüksek sınır noktası
        var tepe = -1; var tepeY = Int.MAX_VALUE
        for (i in px.indices) if (!kes[i] && abs(px[i] + x0 - fx) < fh * 0.9f && py[i] < tepeY) { tepeY = py[i]; tepe = i }
        if (tepe < 0) return null
        // tepeden iki yöne kesime kadar yürü
        val N = px.size
        var a = tepe; var s = 0
        while (s < N && !kes[(a - 1 + N) % N]) { a = (a - 1 + N) % N; s++ }
        var bI = tepe; s = 0
        while (s < N && !kes[(bI + 1) % N]) { bI = (bI + 1) % N; s++ }
        val ham = ArrayList<Pair<Float, Float>>()
        var i = a; s = 0
        while (s <= N) { ham += (px[i] + x0 + 0.5f) to (py[i] + y0 + 0.5f); if (i == bI) break; i = (i + 1) % N; s++ }
        if (ham.size < 24) return null
        if (ham.first().first > ham.last().first) ham.reverse()
        // eşit aralıklı NOKTA noktaya yeniden örnekle, yumuşat
        val uz = FloatArray(ham.size); for (j in 1 until ham.size) uz[j] = uz[j - 1] + hypot(ham[j].first - ham[j - 1].first, ham[j].second - ham[j - 1].second)
        val L = uz.last(); if (L < fh) return null
        val rx = FloatArray(NOKTA); val ry = FloatArray(NOKTA); var j = 0
        for (q in 0 until NOKTA) {
            val hedef = L * q / (NOKTA - 1)
            while (j < ham.size - 2 && uz[j + 1] < hedef) j++
            val t = ((hedef - uz[j]) / (uz[j + 1] - uz[j]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
            rx[q] = ham[j].first + (ham[j + 1].first - ham[j].first) * t; ry[q] = ham[j].second + (ham[j + 1].second - ham[j].second) * t
        }
        val out = ArrayList<Float>(NOKTA * 2)
        for (q in 0 until NOKTA) {
            var ax = 0f; var ay = 0f; var c = 0
            for (o in -3..3) { val r = q + o; if (r in 0 until NOKTA) { ax += rx[r]; ay += ry[r]; c++ } }
            out += (ax / c / w).coerceIn(0f, 1f); out += (ay / c / h).coerceIn(0f, 1f)
        }
        return out
    }

    private val DX4 = intArrayOf(1, -1, 0, 0); private val DY4 = intArrayOf(0, 0, 1, -1)
    // 0 = doğu, saat yönünde: 1 güneydoğu, 2 güney, 3 güneybatı, 4 batı, 5 kuzeybatı, 6 kuzey, 7 kuzeydoğu
    private val DX8 = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1); private val DY8 = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /** Tepe (hattın en yüksek noktası; iki ışık burada buluşur). */
    fun tepe(s: List<Float>): Int {
        val n = s.size / 2; if (n < 2) return 0
        var t = 0; for (i in 1 until n) if (s[2 * i + 1] < s[2 * t + 1]) t = i
        return t.coerceIn(1, n - 2)
    }
}

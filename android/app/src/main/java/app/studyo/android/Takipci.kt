package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Videoda bir noktayı kare kare izler (şablon eşleme, ışık değişimine dayanıklı). Video belleğe alınmaz:
 *  kareler küçük gruplar halinde çözülür, 256 piksel genişliğe küçültülüp gri tona çevrilir. */
object Takipci {
    private const val GEN = 256

    private class Gri(val w: Int, val h: Int, val v: FloatArray)

    private fun griYap(b: Bitmap, donus: Int, dondur: Boolean): Gri {
        val s = GEN.toFloat() / max(b.width, b.height)
        val kw = (b.width * s).roundToInt().coerceAtLeast(8); val kh = (b.height * s).roundToInt().coerceAtLeast(8)
        val k = Bitmap.createScaledBitmap(b, kw, kh, true)
        val px = IntArray(kw * kh); k.getPixels(px, 0, kw, 0, 0, kw, kh)
        if (k !== b) k.recycle()
        val g = FloatArray(kw * kh) { i -> val c = px[i]; ((c shr 16 and 255) * 0.299f + (c shr 8 and 255) * 0.587f + (c and 255) * 0.114f) }
        if (!dondur || donus % 360 == 0) return Gri(kw, kh, g)
        return when ((donus % 360 + 360) % 360) {
            90 -> Gri(kh, kw, FloatArray(kw * kh) { i -> val x = i % kh; val y = i / kh; g[(kh - 1 - x) * kw + y] })
            180 -> Gri(kw, kh, FloatArray(kw * kh) { i -> g[kw * kh - 1 - i] })
            else -> Gri(kh, kw, FloatArray(kw * kh) { i -> val x = i % kh; val y = i / kh; g[x * kw + (kw - 1 - y)] })
        }
    }

    private fun yama(g: Gri, cx: Int, cy: Int, r: Int): FloatArray? {
        if (cx - r < 0 || cy - r < 0 || cx + r >= g.w || cy + r >= g.h) return null
        val n = 2 * r + 1
        val o = FloatArray(n * n)
        var ort = 0f
        for (j in 0 until n) for (i in 0 until n) { val v = g.v[(cy - r + j) * g.w + (cx - r + i)]; o[j * n + i] = v; ort += v }
        ort /= o.size
        for (i in o.indices) o[i] -= ort
        return o
    }

    /** Ortalaması çıkarılmış mutlak fark; düşük = benzer. */
    private fun fark(g: Gri, sablon: FloatArray, cx: Int, cy: Int, r: Int, sinir: Float): Float {
        val n = 2 * r + 1
        var ort = 0f
        for (j in 0 until n) { val sat = (cy - r + j) * g.w + (cx - r); for (i in 0 until n) ort += g.v[sat + i] }
        ort /= n * n
        var t = 0f
        for (j in 0 until n) {
            val sat = (cy - r + j) * g.w + (cx - r)
            for (i in 0 until n) t += abs(g.v[sat + i] - ort - sablon[j * n + i])
            if (t > sinir) return t
        }
        return t
    }

    /**
     * @param basMs kaynak dosyada başlangıç anı, x/y kaynak karede 0…1.
     * @return kaynak zamanında (ms) noktalar; klibin sonuna kadar.
     */
    suspend fun izle(ctx: Context, k: Klip, basMs: Long, x: Float, y: Float, ilerleme: (Float) -> Unit): List<TakipNokta> = withContext(Dispatchers.Default) {
        val r = MediaMetadataRetriever()
        val sonuc = ArrayList<TakipNokta>()
        try {
            r.setDataSource(ctx, Uri.parse(k.uri))
            val donus = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val sayi = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            val sure = k.kaynakSureMs.coerceAtLeast(1)
            val dikeyGoster = k.yuk > k.gen
            var sablon: FloatArray? = null
            var cx = 0; var cy = 0; var R = 10; var arama = 22
            var kayip = 0

            fun isle(b: Bitmap, ms: Long) {
                val dondur = (b.height > b.width) != dikeyGoster && k.gen != k.yuk
                val g = griYap(b, donus, dondur)
                if (sablon == null) {
                    R = (g.w * 0.045f).roundToInt().coerceIn(6, 14)
                    cx = (x * g.w).roundToInt().coerceIn(R + 1, g.w - R - 2)
                    cy = (y * g.h).roundToInt().coerceIn(R + 1, g.h - R - 2)
                    sablon = yama(g, cx, cy, R)
                    sonuc += TakipNokta(ms, x, y)
                    return
                }
                val s = sablon ?: return
                var enIyi = Float.MAX_VALUE; var bx = cx; var by = cy
                val a = if (kayip > 0) arama * 2 else arama
                var yy = max(R, cy - a)
                while (yy <= min(g.h - R - 1, cy + a)) {
                    var xx = max(R, cx - a)
                    while (xx <= min(g.w - R - 1, cx + a)) {
                        val f = fark(g, s, xx, yy, R, enIyi)
                        if (f < enIyi) { enIyi = f; bx = xx; by = yy }
                        xx++
                    }
                    yy++
                }
                val ortFark = enIyi / ((2 * R + 1) * (2 * R + 1))
                if (ortFark < 22f) {
                    cx = bx; cy = by; kayip = 0
                    // şablonu yavaşça güncelle (dönme/ışık değişimine uyum)
                    yama(g, cx, cy, R)?.let { yeni -> for (i in s.indices) s[i] = s[i] * 0.85f + yeni[i] * 0.15f }
                } else kayip++
                sonuc += TakipNokta(ms, cx.toFloat() / g.w, cy.toFloat() / g.h)
            }

            if (Build.VERSION.SDK_INT >= 28 && sayi > 1) {
                val fps = sayi * 1000.0 / sure
                val i0 = (basMs * fps / 1000.0).toInt().coerceIn(0, sayi - 1)
                val i1 = (k.sonMs * fps / 1000.0).toInt().coerceIn(i0, sayi - 1)
                val buyuk = k.gen.toLong() * k.yuk > 2_500_000L
                val grup = if (buyuk) 3 else 8
                val prm = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
                var i = i0
                while (i <= i1) {
                    ensureActive()
                    val n = min(grup, i1 - i + 1)
                    val kareler = try { r.getFramesAtIndex(i, n, prm) } catch (e: Exception) { Kayit.hata("takip kareleri", e); emptyList() }
                    if (kareler.isEmpty()) break
                    for ((j, b) in kareler.withIndex()) { isle(b, ((i + j) * 1000.0 / fps).toLong()); b.recycle() }
                    i += n
                    ilerleme((i - i0).toFloat() / (i1 - i0 + 1))
                }
            }
            if (sonuc.size < 2) {
                // Yedek yol: zamanla kare al (yavaş ama her cihazda çalışır)
                sonuc.clear(); sablon = null
                var t = basMs
                while (t <= k.sonMs) {
                    ensureActive()
                    val b = r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: break
                    isle(b, t); b.recycle()
                    t += 100
                    ilerleme(((t - basMs).toFloat() / (k.sonMs - basMs).coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        // titremeyi azalt: 5 karelik kayan ortalama
        if (sonuc.size < 5) sonuc else sonuc.indices.map { i ->
            val a = max(0, i - 2); val b = min(sonuc.lastIndex, i + 2)
            var sx = 0f; var sy = 0f
            for (j in a..b) { sx += sonuc[j].x; sy += sonuc[j].y }
            TakipNokta(sonuc[i].ms, sx / (b - a + 1), sy / (b - a + 1))
        }
    }
}

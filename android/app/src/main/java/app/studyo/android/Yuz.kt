package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Akıllı kadraj + yaklaş/uzaklaş (Kareden Videoya'daki mantık).
 * Odak: önce yüz (android.media.FaceDetector, cihaz içi, ücretsiz), yoksa ana nesne (renk farkı + kenar yoğunluğu, ortaya ağırlıklı).
 * Doldur'da kare odağa göre kaydırılır: yüz ekranın üstten ~%36–40'ında (baş kesilmez, omuzlar görünür), nesne tam ortada.
 * Hareket: Kareden Videoya'nın "otomatik" modu (an() açıklamasına bak); geçiş boyunca sürer, geri sıçrama yok.
 * Dışa aktarmada (MatrixTransformation, giriş karesinin NDC'si) ve önizlemede (graphicsLayer) AYNI hesap kullanılır.
 */
object Yuz {
    /** Bir karede odak: yüz (x, y, boy) ya da null; nesne odağı (x, y). Hepsi 0…1. */
    class Odak(val yuz: Triple<Float, Float, Float>?, val nx: Float, val ny: Float)

    /** Fotoğraf dosyası (uygulamaya yönü düzeltilmiş kopyalanmış) */
    fun bul(uri: String): Odak? = try {
        val yol = Uri.parse(uri).path
        val f = yol?.let { File(it) }
        if (f == null || !f.exists()) null else {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0) null else {
                var s = 1
                while (max(o.outWidth, o.outHeight) / (s * 2) >= 720) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })
                    ?.let { b -> val r = analiz(b); b.recycle(); r }
            }
        }
    } catch (e: Throwable) { Kayit.hata("odak (fotoğraf)", e); null }

    /** Video: klibin %40'ındaki kare (dönüş üstverisine göre düzeltilir). gen/yuk: dönüş uygulanmış boyut. */
    fun videoBul(ctx: android.content.Context, uri: Uri, anMs: Long, gen: Int, yuk: Int): Odak? = try {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, uri)
            var b = r.getFrameAtTime(anMs * 1000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (b != null && gen > 0 && yuk > 0 && (b.width > b.height) != (gen > yuk)) {
                val don = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 90
                val m = android.graphics.Matrix().apply { postRotate(don.toFloat()) }
                val d = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true); b.recycle(); b = d
            }
            b?.let { k ->
                val sc = 720f / max(k.width, k.height)
                val kk = if (sc < 1f) Bitmap.createScaledBitmap(k, (k.width * sc).toInt().coerceAtLeast(8), (k.height * sc).toInt().coerceAtLeast(8), true) else k
                val sonuc = analiz(kk); if (kk !== k) kk.recycle(); k.recycle(); sonuc
            }
        } finally { r.release() }
    } catch (e: Throwable) { Kayit.hata("odak (video)", e); null }

    private fun analiz(ham: Bitmap): Odak {
        // yüz: RGB_565 ve çift genişlik
        val w = ham.width and 1.inv(); val h = ham.height
        val b = if (w != ham.width || ham.config != Bitmap.Config.RGB_565) Bitmap.createBitmap(ham, 0, 0, w, h).copy(Bitmap.Config.RGB_565, false) else ham
        val dizi = arrayOfNulls<FaceDetector.Face>(8)
        val n = try { FaceDetector(b.width, b.height, dizi.size).findFaces(b, dizi) } catch (e: Throwable) { 0 }
        // birden çok yüz: büyüklerin ağırlıklı ortası (en büyüğün %40'ından küçükler sayılmaz)
        val yuzler = (0 until n).mapNotNull { dizi[it] }.filter { it.confidence() >= 0.3f }
        val yuz = if (yuzler.isEmpty()) null else {
            val en = yuzler.maxOf { it.eyesDistance() }
            val kul = yuzler.filter { it.eyesDistance() > en * 0.4f }
            var sx = 0f; var sy = 0f; var sw = 0f
            for (y in kul) { val m = android.graphics.PointF(); y.getMidPoint(m); val wt = y.eyesDistance() * y.eyesDistance()
                sx += m.x * wt; sy += (m.y + y.eyesDistance() * 0.35f) * wt; sw += wt }
            Triple((sx / sw / b.width).coerceIn(0f, 1f), (sy / sw / b.height).coerceIn(0f, 1f), (en * 2.6f / b.height).coerceIn(0.02f, 0.9f))
        }
        if (b !== ham) b.recycle()
        val (nx, ny) = nesne(ham)
        return Odak(yuz, nx, ny)
    }

    /** Ana nesnenin ortası — Kareden Videoya anaObje(): netlik (Laplace, yerel ortalama) + renk farkı + doygunluk,
     *  gökyüzü bastırılır, ortaya ağırlık; en güçlü noktadan büyüyen bölgenin (eşik %62) ağırlık merkezi. */
    private fun nesne(src: Bitmap): Pair<Float, Float> {
        val sc = min(1f, 128f / max(src.width, src.height))
        val w = (src.width * sc).toInt().coerceAtLeast(16); val h = (src.height * sc).toInt().coerceAtLeast(16); val n = w * h
        val t = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(n); t.getPixels(px, 0, w, 0, 0, w, h); if (t !== src) t.recycle()
        val L = FloatArray(n); val Sa = FloatArray(n); var mr = 0f; var mg = 0f; var mb = 0f
        for (i in 0 until n) { val c = px[i]; val r = (c shr 16 and 255).toFloat(); val gg = (c shr 8 and 255).toFloat(); val b = (c and 255).toFloat()
            L[i] = 0.299f * r + 0.587f * gg + 0.114f * b; val mx = max(r, max(gg, b)); val mn = min(r, min(gg, b)); Sa[i] = if (mx > 0f) (mx - mn) / mx else 0f
            mr += r; mg += gg; mb += b }
        mr /= n; mg /= n; mb /= n
        val ed = FloatArray(n)
        for (y in 1 until h - 1) for (x in 1 until w - 1) { val i = y * w + x; ed[i] = kotlin.math.abs(4 * L[i] - L[i - 1] - L[i + 1] - L[i - w] - L[i + w]) }
        fun kutu(A: FloatArray, r: Int): FloatArray {
            val B = FloatArray(n); val T = FloatArray(n)
            for (y in 0 until h) { var s = 0f; for (x in -r..r) s += A[y * w + x.coerceIn(0, w - 1)]
                for (x in 0 until w) { T[y * w + x] = s / (2 * r + 1); s += A[y * w + (x + r + 1).coerceIn(0, w - 1)] - A[y * w + (x - r).coerceIn(0, w - 1)] } }
            for (x in 0 until w) { var s = 0f; for (y in -r..r) s += T[y.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) { B[y * w + x] = s / (2 * r + 1); s += T[(y + r + 1).coerceIn(0, h - 1) * w + x] - T[(y - r).coerceIn(0, h - 1) * w + x] } }
            return B
        }
        val net = kutu(ed, max(2, Math.round(min(w, h) * 0.04f)))
        var netMx = 0f; for (v in net) if (v > netMx) netMx = v
        val sal = FloatArray(n)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val c = px[i]
            val dr = (c shr 16 and 255) - mr; val dg = (c shr 8 and 255) - mg; val db = (c and 255) - mb
            val cd = kotlin.math.sqrt(dr * dr + dg * dg + db * db) / 255f
            val nt = if (netMx > 0f) net[i] / netMx else 0f
            val gok = if (y < h * 0.55f && L[i] > 150f && Sa[i] < 0.28f && nt < 0.25f) 0.25f else 1f
            val ux = (x.toFloat() / w - 0.5f) * 1.1f; val uy = y.toFloat() / h - 0.5f
            val merkez = 1f - 0.45f * min(1f, kotlin.math.sqrt(ux * ux + uy * uy))
            sal[i] = (nt + cd * 0.7f + Sa[i] * 0.45f) * gok * merkez
        }
        val S3 = kutu(sal, max(3, Math.round(min(w, h) * 0.08f)))
        var pk = 0f; var pi = 0; for (i in 0 until n) if (S3[i] > pk) { pk = S3[i]; pi = i }
        if (pk <= 0f) return 0.5f to 0.5f
        val esik = pk * 0.62f; val gor = BooleanArray(n); val yig = ArrayDeque<Int>(); yig.addLast(pi); gor[pi] = true
        var sx = 0f; var sy = 0f; var sw = 0f
        while (yig.isNotEmpty()) {
            val i = yig.removeLast(); val x = i % w; val y = i / w; val v = S3[i] - esik; sx += v * x; sy += v * y; sw += v
            for (k in intArrayOf(i - 1, i + 1, i - w, i + w)) {
                if (k < 0 || k >= n || gor[k]) continue
                if ((k == i - 1 && x == 0) || (k == i + 1 && x == w - 1)) continue
                if (S3[k] >= esik) { gor[k] = true; yig.addLast(k) }
            }
        }
        return if (sw > 0f) (sx / sw / w) to (sy / sw / h) else 0.5f to 0.5f
    }

    /** Bir andaki dönüşüm. s: ölçek, tx/ty: giriş karesinin NDC'sinde kaydırma (y yukarı), vx/vy: görünür pencere (NDC yarı boyu). */
    class An(val s: Float, val tx: Float, val ty: Float, val vx: Float, val vy: Float) {
        /** Önizleme (çıkış görünümü): kaydırma piksel olarak, Compose y aşağı. */
        fun onizlemeX(gen: Float) = tx / vx * gen / 2f
        fun onizlemeY(yuk: Float) = -ty / vy * yuk / 2f
    }

    private fun sinirla(v: Float, a: Float, b: Float) = min(b, max(a, v))          // JS clamp ile aynı (a > b ise b)
    private fun yum(p: Float) = p * p * (3f - 2f * p)
    private const val HIZ = 3f              // Kareden Videoya varsayılanı: kb.rate = 3
    private const val YUZ_GUC = 0.30f       // kb.face = 30
    private const val TAVAN = 0.6f          // KBCAP

    /**
     * Klibin o andaki hareketi (3.35). f: 0…1, klibin ekrandaki TAM süresi içinde (gelen geçiş dahil) ilerleme; i: sıra; D: tam süre (sn).
     *  - otomatik, fotoğraf: odak noktasına doğru yavaş yaklaşma (saniyede ~%3); yatay fotoğraf + yüz: yüze yaklaşır (yüz %42 yükseklikte)
     *  - otomatik, video: saniyede %1,5 yaklaşma (Kareden Videoya)
     *  - yakin / uzak: odak noktasında yaklaşma / uzaklaşma · sag / sol / yukari / asagi: en çok karenin yarısı kadar kaydırma
     *  - odak: elle seçilen nokta → yüz → ana nesne → orta
     * Kadraj (hareketsiz hâli) akıllı doldurma: yüz üstten %36–50'de (baş kesilmez), yoksa ana nesne ortada; üstüne elle kaydırma.
     */
    fun an(k: Klip, W: Int, H: Int, f: Float, i: Int = 0, D: Float = k.sureMs / 1000f): An {
        val p = f.coerceIn(0f, 1f)
        val g = (if (k.gen > 0) k.gen else W).toFloat(); val h = (if (k.yuk > 0) k.yuk else H).toFloat()
        val sc0 = if (k.doldur) max(W / g, H / h) else min(W / g, H / h)
        val dw = g * sc0; val dh = h * sc0                       // s = 1'de ekrandaki boyut (Presentation)
        val yuzVar = k.yuzOdak && k.yuzB > 0f && k.yuzX >= 0f
        val fx = if (yuzVar) k.yuzX else if (k.odakX >= 0f) k.odakX else 0.5f
        val fy = if (yuzVar) k.yuzY else if (k.odakY >= 0f) k.odakY else 0.5f
        // akıllı kadraj (smartFill): ox/oy = görüntü merkezinin ekran ortasından kayması (W/H oranı, y aşağı)
        val mxX0 = max(0f, (dw - W) / 2f / W); val mxY0 = max(0f, (dh - H) / 2f / H)
        var hedef = 0.5f
        if (yuzVar) { hedef = if (dh > H * 1.25f) 0.36f else 0.4f; hedef = sinirla(max(hedef, k.yuzB * dh / H * 0.85f + 0.05f), 0.3f, 0.5f) }
        val ox = sinirla(sinirla((0.5f - fx) * dw / W, -mxX0, mxX0) + k.kayX * mxX0, -mxX0, mxX0)
        val oy = sinirla(sinirla((H * hedef - H / 2f - (fy - 0.5f) * dh) / H, -mxY0, mxY0) + k.kayY * mxY0, -mxY0, mxY0)

        var fw = dw; var fh = dh; var cx = W / 2f + ox * W; var cy = H / 2f + oy * H
        if (k.kenBurns) {
            // 3.35: dikey fotoğrafta otomatik KAYDIRMA kaldırıldı (Ahmet'in videosunda ölçüldü: 4 sn'de yerden gökyüzüne kayınca
            // ortalama parlaklık %60'a kadar artıyor, "parlama" gibi görünüyordu). Otomatik = odak noktasına doğru yavaş yaklaşma.
            // Odak: elle seçilen nokta → yüz → ana nesne → orta. Kaydırma yalnız Hareket panelinde seçilirse yapılır.
            val tur = k.hareket.ifEmpty { "otomatik" }
            val uzun = dh > H * 1.15f
            val secili = k.odakSecX >= 0f && k.odakSecY >= 0f
            val el = p * D
            val yakMax = min(TAVAN, max(0.12f, HIZ / 100f * D))        // saniyede ~%3 (Kareden Videoya hızı), en az %12
            fun yaklas(s: Float) {
                val (ax, ay) = odakNokta(k)
                val x0 = cx + (ax - 0.5f) * dw; val y0 = cy + (ay - 0.5f) * dh     // odak noktasının s = 1'deki ekran yeri
                val q = ((s - 1f) / yakMax).coerceIn(0f, 1f)
                val hx = x0 + (W / 2f - x0) * 0.35f * q; val hy = y0 + (H / 2f - y0) * 0.35f * q   // nokta hafifçe ortaya gelir
                fw = dw * s; fh = dh * s
                cx = hx - (ax - 0.5f) * fw; cy = hy - (ay - 0.5f) * fh
                if (fw >= W) cx = sinirla(cx, W - fw / 2f, fw / 2f); if (fh >= H) cy = sinirla(cy, H - fh / 2f, fh / 2f)
            }
            fun kaydir(yatay: Boolean, ileri: Boolean) {
                val tasma = if (yatay) dw - W else dh - H
                val s = if (tasma >= 0.15f * (if (yatay) W else H)) 1f else 1.15f
                fw = dw * s; fh = dh * s
                val mx = max(0f, (if (yatay) fw - W else fh - H) / 2f / (if (yatay) W else H))
                if (mx <= 0.001f) { yaklas(1f + yakMax * p); return }
                val tr = min(min(2f * mx, 0.06f * HIZ * D), 0.5f)            // en çok karenin yarısı kadar
                val f0 = sinirla(if (yatay) ox else oy, -mx, mx)
                var a = f0 - tr / 2f; var b = f0 + tr / 2f
                if (b > mx) { a -= b - mx; b = mx }; if (a < -mx) { b += -mx - a; a = -mx }
                a = max(a, -mx); b = min(b, mx)
                val bas = if (ileri) a else b; val son = if (ileri) b else a
                val o = bas + (son - bas) * yum(p)
                if (yatay) { cx = W / 2f + o * W; cy = H / 2f + sinirla(oy, -max(0f, (fh - H) / 2f / H), max(0f, (fh - H) / 2f / H)) * H }
                else { cy = H / 2f + o * H; cx = W / 2f + sinirla(ox, -max(0f, (fw - W) / 2f / W), max(0f, (fw - W) / 2f / W)) * W }
            }
            when {
                tur == "otomatik" && !k.foto -> { val s = 1f + min(TAVAN, HIZ / 200f * el); fw = dw * s; fh = dh * s }   // video: eskisi gibi
                tur == "otomatik" && k.foto && yuzVar && !uzun && !secili -> {
                    // yatay fotoğraf + yüz: yüze doğru yaklaşır, yüz ekranın %42 yüksekliğine gelir (Kareden Videoya)
                    val zf = sinirla(0.62f * H / max(1f, k.yuzB * dh), 1.05f, 4f)
                    val yuzEn = 1f + (zf - 1f) * YUZ_GUC
                    val e = yum(p); val sc = 1f + (yuzEn - 1f) * e
                    val fw1 = fw; val fh1 = fh; fw = fw1 * sc; fh = fh1 * sc
                    val px0 = cx + (fx - 0.5f) * fw1; val py0 = cy + (fy - 0.5f) * fh1
                    val m = if (yuzEn > 1.001f) (sc - 1f) / (yuzEn - 1f) * min(1f, (yuzEn - 1f) / 1.2f) else 0f
                    val hx = px0 + (W / 2f - px0) * m; val hy = py0 + (H * 0.42f - py0) * m
                    cx = sinirla(hx - (fx - 0.5f) * fw, W - fw / 2f, fw / 2f); cy = sinirla(hy - (fy - 0.5f) * fh, H - fh / 2f, fh / 2f)
                }
                tur == "uzak" -> yaklas(1f + yakMax * (1f - p))
                tur == "sag" -> kaydir(yatay = true, ileri = false)       // kamera sağa döner: görüntü sola akar
                tur == "sol" -> kaydir(yatay = true, ileri = true)
                tur == "yukari" -> kaydir(yatay = false, ileri = true)    // kamera yukarı: görüntü aşağı akar
                tur == "asagi" -> kaydir(yatay = false, ileri = false)
                else -> yaklas(1f + yakMax * p)                            // otomatik (fotoğraf) ve "yakin"
            }
        }
        // ekran → giriş karesinin NDC'si (MatrixTransformation, Presentation'dan önce)
        return An(fw / dw, (cx - W / 2f) / (dw / 2f), -(cy - H / 2f) / (dh / 2f), W / dw, H / dh)
    }

    /** Yaklaşmanın odak noktası (kaynak karede 0…1): elle seçilen → yüz → ana nesne → orta. */
    fun odakNokta(k: Klip): Pair<Float, Float> = when {
        k.odakSecX >= 0f && k.odakSecY >= 0f -> k.odakSecX to k.odakSecY
        k.yuzOdak && k.yuzB > 0f && k.yuzX >= 0f -> k.yuzX to k.yuzY
        k.odakX >= 0f && k.odakY >= 0f -> k.odakX to k.odakY
        else -> 0.5f to 0.5f
    }
    fun odakKaynak(k: Klip): String = when {
        k.odakSecX >= 0f && k.odakSecY >= 0f -> "senin seçtiğin nokta"
        k.yuzOdak && k.yuzB > 0f && k.yuzX >= 0f -> "yüz (otomatik)"
        k.odakX >= 0f && k.odakY >= 0f -> "ana nesne (otomatik)"
        else -> "orta (otomatik)"
    }

    /** Kaydırılabilir mi (önizlemede sürükleme için): taşan kısım var mı. */
    fun kayabilir(a: An) = a.s - a.vx > 0.001f || a.s - a.vy > 0.001f

    fun matris(a: An): android.graphics.Matrix = android.graphics.Matrix().apply { setScale(a.s, a.s); postTranslate(a.tx, a.ty) }
    fun sabitMi(a: An) = kotlin.math.abs(a.s - 1f) < 1e-4f && kotlin.math.abs(a.tx) < 1e-4f && kotlin.math.abs(a.ty) < 1e-4f
}

/** Önizleme oynatıcısında Sığdır/Doldur. resizeMode yalnız video yüzeyine uygulanır; fotoğraflar PlayerView'in ayrı
 *  görüntü katmanında gösterilir ve onun ölçeği imageDisplayMode ile ayarlanır (3.6'ya kadar bu eksikti: fotoğraf hep sığdırılıyordu). */
fun androidx.media3.ui.PlayerView.kadraj(doldur: Boolean) {
    resizeMode = if (doldur) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    try { setImageDisplayMode(if (doldur) androidx.media3.ui.PlayerView.IMAGE_DISPLAY_MODE_FILL else androidx.media3.ui.PlayerView.IMAGE_DISPLAY_MODE_FIT) }
    catch (e: IllegalStateException) { Kayit.yaz("Oynatıcıda görüntü katmanı yok: ${e.message}") }
}

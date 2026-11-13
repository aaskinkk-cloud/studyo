package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import android.net.Uri
import java.io.File
import kotlin.math.max

/**
 * Akıllı kadraj + yaklaş/uzaklaş (Kareden Videoya'daki mantık).
 * Odak: önce yüz (android.media.FaceDetector, cihaz içi, ücretsiz), yoksa ana nesne (renk farkı + kenar yoğunluğu, ortaya ağırlıklı).
 * Doldur'da kare odağa göre kaydırılır: yüz ekranın üstten ~%36–40'ında (baş kesilmez, omuzlar görünür), nesne tam ortada.
 * Hareket: fotoğraflar sırayla bir yaklaşır, bir uzaklaşır. Uzaklaşan fotoğraf, öncekinin vardığı orandan başlar; hareket
 * doğrusal ve geçiş boyunca sürer (geçişte durup geri sıçrama yok). Yakınlaşma odak noktasında sabit kalır.
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

    /** Ana nesnenin ortası: ortalama renkten uzaklık + kenar yoğunluğu, kenarlara doğru azalan ağırlık (Kareden Videoya salientPoint). */
    private fun nesne(src: Bitmap): Pair<Float, Float> {
        val sc = 96f / max(src.width, src.height)
        val w = (src.width * sc).toInt().coerceAtLeast(8); val h = (src.height * sc).toInt().coerceAtLeast(8)
        val t = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h); t.getPixels(px, 0, w, 0, 0, w, h); if (t !== src) t.recycle()
        var mr = 0f; var mg = 0f; var mb = 0f
        for (c in px) { mr += (c shr 16 and 255); mg += (c shr 8 and 255); mb += (c and 255) }
        val nn = px.size.toFloat(); mr /= nn; mg /= nn; mb /= nn
        val L = FloatArray(px.size) { val c = px[it]; 0.299f * (c shr 16 and 255) + 0.587f * (c shr 8 and 255) + 0.114f * (c and 255) }
        val sal = FloatArray(px.size); var mx = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x; val c = px[i]
            val dr = (c shr 16 and 255) - mr; val dg = (c shr 8 and 255) - mg; val db = (c and 255) - mb
            val cd = kotlin.math.sqrt(dr * dr + dg * dg + db * db)
            val ed = kotlin.math.abs(L[i + 1] - L[i - 1]) + kotlin.math.abs(L[i + w] - L[i - w])
            val ux = x.toFloat() / w - 0.5f; val uy = y.toFloat() / h - 0.5f
            val v = (cd * 0.65f + ed * 0.8f) * (1f - 0.35f * kotlin.math.sqrt(ux * ux + uy * uy))
            sal[i] = v; if (v > mx) mx = v
        }
        val esik = mx * 0.45f; var sx = 0f; var sy = 0f; var sw = 0f
        for (y in 1 until h - 1) for (x in 1 until w - 1) { val v = sal[y * w + x]; if (v > esik) { val wt = v - esik; sx += wt * x; sy += wt * y; sw += wt } }
        return if (sw > 0f) (sx / sw / w) to (sy / sw / h) else 0.5f to 0.5f
    }

    /** Bir andaki dönüşüm. s: ölçek, tx/ty: giriş karesinin NDC'sinde kaydırma (y yukarı), vx/vy: görünür pencere (NDC yarı boyu). */
    class An(val s: Float, val tx: Float, val ty: Float, val vx: Float, val vy: Float) {
        /** Önizleme (çıkış görünümü): kaydırma piksel olarak, Compose y aşağı. */
        fun onizlemeX(gen: Float) = tx / vx * gen / 2f
        fun onizlemeY(yuk: Float) = -ty / vy * yuk / 2f
    }

    /** Yaklaşma oranı (fotoğrafın sonunda): yüz varsa biraz daha fazla. */
    fun zMax(k: Klip) = if (k.yuzOdak && k.yuzB > 0f) 1.28f else 1.16f

    /** Klip i'nin hareketi: (uzaklaşıyor mu, oran). Hareketli fotoğraflar sırayla yaklaşır/uzaklaşır; uzaklaşan, öncekinin oranından başlar. */
    fun yon(p: Proje, i: Int): Pair<Boolean, Float> {
        val k = p.klipler.getOrNull(i) ?: return false to 1.16f
        var n = 0; var onceki: Klip? = null
        for (j in 0 until i) { val kj = p.klipler[j]; if (kj.foto && kj.kenBurns) { n++; onceki = kj } }
        val ters = n % 2 == 1
        return ters to (if (ters && onceki != null) zMax(onceki) else zMax(k))
    }

    /** f: 0…1, klibin TAM süresi içindeki ilerleme (geçiş örtüşmesi dahil). W/H: çıkış boyutu. ters/Z: yon(). */
    fun an(k: Klip, W: Int, H: Int, f: Float, ters: Boolean = false, Z: Float = zMax(k)): An {
        val e = f.coerceIn(0f, 1f)
        var vx = 1f; var vy = 1f
        if (k.doldur && k.gen > 0 && k.yuk > 0) { val a = k.gen.toFloat() / k.yuk; val A = W.toFloat() / H; if (a < A) vy = a / A else vx = A / a }
        val s = if (k.foto && k.kenBurns) (if (ters) Z - (Z - 1f) * e else 1f + (Z - 1f) * e) else 1f
        // odak noktası ve ekrandaki hedefi (üstten oran)
        val yuzVar = k.yuzOdak && k.yuzB > 0f && k.yuzX >= 0f
        val fx = if (yuzVar) k.yuzX else if (k.odakX >= 0f) k.odakX else 0.5f
        val fy = if (yuzVar) k.yuzY else if (k.odakY >= 0f) k.odakY else 0.5f
        val hedef = if (yuzVar) { val ekranBoy = k.yuzB * s / vy; max(if (vy < 0.8f) 0.36f else 0.4f, ekranBoy * 0.85f + 0.05f).coerceIn(0.3f, 0.5f) } else 0.5f
        val tx0 = -s * (2f * fx - 1f)
        val ty0 = (1f - 2f * hedef) * vy - s * (1f - 2f * fy)
        val mx = (s - vx).coerceAtLeast(0f); val my = (s - vy).coerceAtLeast(0f)
        val tx = (tx0 + k.kayX * mx).coerceIn(-mx, mx)
        val ty = (ty0 - k.kayY * my).coerceIn(-my, my)
        return An(s, tx, ty, vx, vy)
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

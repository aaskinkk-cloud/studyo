package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Yüze odaklanma (fotoğraf klipleri).
 * Bulma: Android'in cihaz içi yüz bulucusu (android.media.FaceDetector; ücretsiz, ağ yok). Fotoğraf uygulamaya yönü düzeltilmiş
 * olarak kopyalandığı için doğrudan o dosyadan okunur. En büyük (en yakın) yüz alınır.
 * Hareket: yavaş yakınlaşma yüze doğru; yüz baştan kadrajın ortasına alınır (doldurulmuş dikey fotoğrafta yüz kesilmez),
 * 5 sn içinde yüz ekran yüksekliğinin ~%38'ine kadar büyür. Kenar asla boş kalmaz (kaydırma sınırlıdır).
 * Dışa aktarmada (MatrixTransformation, giriş karesinin NDC'si) ve önizlemede (graphicsLayer, çıkış görünümü) AYNI hesap kullanılır.
 */
object Yuz {
    /** (x, y, boy) — x,y yüz merkezi 0…1, boy = yüz yüksekliği / fotoğraf yüksekliği. Bulunamazsa null. */
    fun bul(uri: String): Triple<Float, Float, Float>? {
      return try {
        val yol = Uri.parse(uri).path ?: return null
        val f = File(yol); if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= 720) s *= 2
        val ham = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }) ?: return null
        // FaceDetector: RGB_565 ve çift genişlik ister
        val w = ham.width and 1.inv(); val h = ham.height
        val b = if (w != ham.width || ham.config != Bitmap.Config.RGB_565) Bitmap.createBitmap(ham, 0, 0, w, h).copy(Bitmap.Config.RGB_565, false) else ham
        val dizi = arrayOfNulls<FaceDetector.Face>(6)
        val n = FaceDetector(b.width, b.height, dizi.size).findFaces(b, dizi)
        var en: FaceDetector.Face? = null
        for (i in 0 until n) { val y = dizi[i] ?: continue; if (y.confidence() >= 0.3f && (en == null || y.eyesDistance() > en.eyesDistance())) en = y }
        val sonuc = en?.let { y ->
            val m = android.graphics.PointF(); y.getMidPoint(m)
            val boy = (y.eyesDistance() * 2.6f) / b.height    // gözler arası × 2,6 ≈ yüz yüksekliği
            // orta nokta gözlerin arasıdır; yüz merkezi biraz aşağıda
            Triple((m.x / b.width).coerceIn(0f, 1f), ((m.y + y.eyesDistance() * 0.35f) / b.height).coerceIn(0f, 1f), boy.coerceIn(0.02f, 0.9f))
        }
        if (b !== ham) b.recycle(); ham.recycle()
        sonuc
      } catch (e: Throwable) { Kayit.hata("yüz bulma", e); null }
    }

    /** Bir andaki dönüşüm. s: ölçek, tx/ty: giriş karesinin NDC'sinde kaydırma (y yukarı), vx/vy: görünür pencere (NDC yarı boyu). */
    class An(val s: Float, val tx: Float, val ty: Float, val vx: Float, val vy: Float) {
        /** Önizleme (çıkış görünümü): kaydırma piksel olarak, Compose y aşağı. */
        fun onizlemeX(gen: Float) = tx / vx * gen / 2f
        fun onizlemeY(yuk: Float) = -ty / vy * yuk / 2f
    }

    /** f: 0…1 (klip içindeki ilerleme). W/H: çıkış boyutu. Yüz yoksa eski davranış: ortadan %8 yakınlaşma. */
    fun an(k: Klip, W: Int, H: Int, f: Float): An {
        val e = f.coerceIn(0f, 1f)
        if (!k.yuzEtkin || k.gen <= 0 || k.yuk <= 0) return An(1f + 0.08f * e, 0f, 0f, 1f, 1f)
        val a = k.gen.toFloat() / k.yuk; val A = W.toFloat() / H
        var vx = 1f; var vy = 1f
        if (k.doldur) { if (a < A) vy = a / A else vx = A / a }
        val yum = e * e * (3f - 2f * e)                                  // yumuşak başla, yumuşak bitir
        val s1 = (0.38f * vy / k.yuzB).coerceIn(1.08f, 2.2f)             // yüz ekran yüksekliğinin ~%38'i
        val s = 1f + (s1 - 1f) * yum
        val X = 2f * k.yuzX - 1f; val Y = 1f - 2f * k.yuzY
        val tx = (-s * X).coerceIn(-(s - vx).coerceAtLeast(0f), (s - vx).coerceAtLeast(0f))
        val ty = (-s * Y).coerceIn(-(s - vy).coerceAtLeast(0f), (s - vy).coerceAtLeast(0f))
        return An(s, tx, ty, vx, vy)
    }

    fun matris(a: An): android.graphics.Matrix = android.graphics.Matrix().apply { setScale(a.s, a.s); postTranslate(a.tx, a.ty) }

    @Suppress("unused") private fun sinir(v: Float, a: Float, b: Float) = min(max(v, a), b)
}

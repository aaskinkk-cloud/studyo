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

    /** f: 0…1 (klip içindeki ilerleme). W/H: çıkış boyutu. Bütün klipler için: fotoğrafta yavaş yakınlaşma (yüz varsa yüze),
     *  her klipte elle kaydırma (kayX/kayY, taşan kısım içinde; kenar asla boş kalmaz). */
    fun an(k: Klip, W: Int, H: Int, f: Float): An {
        val e = f.coerceIn(0f, 1f)
        var vx = 1f; var vy = 1f
        if (k.doldur && k.gen > 0 && k.yuk > 0) { val a = k.gen.toFloat() / k.yuk; val A = W.toFloat() / H; if (a < A) vy = a / A else vx = A / a }
        var s = 1f; var tx0 = 0f; var ty0 = 0f
        if (k.foto && k.kenBurns) {
            if (k.yuzEtkin && k.gen > 0 && k.yuk > 0) {
                val yum = e * e * (3f - 2f * e)                              // yumuşak başla, yumuşak bitir
                val s1 = (0.38f * vy / k.yuzB).coerceIn(1.08f, 2.2f)         // yüz ekran yüksekliğinin ~%38'i
                s = 1f + (s1 - 1f) * yum
                tx0 = -s * (2f * k.yuzX - 1f); ty0 = -s * (1f - 2f * k.yuzY)
            } else s = 1f + 0.08f * e
        }
        val mx = (s - vx).coerceAtLeast(0f); val my = (s - vy).coerceAtLeast(0f)
        val tx = (tx0 + k.kayX * mx).coerceIn(-mx, mx)
        val ty = (ty0 - k.kayY * my).coerceIn(-my, my)
        return An(s, tx, ty, vx, vy)
    }

    /** Kaydırılabilir mi (önizlemede sürükleme için): taşan kısım var mı. */
    fun kayabilir(a: An) = a.s - a.vx > 0.001f || a.s - a.vy > 0.001f

    fun matris(a: An): android.graphics.Matrix = android.graphics.Matrix().apply { setScale(a.s, a.s); postTranslate(a.tx, a.ty) }

    @Suppress("unused") private fun sinir(v: Float, a: Float, b: Float) = min(max(v, a), b)
}

/** Önizleme oynatıcısında Sığdır/Doldur. resizeMode yalnız video yüzeyine uygulanır; fotoğraflar PlayerView'in ayrı
 *  görüntü katmanında gösterilir ve onun ölçeği imageDisplayMode ile ayarlanır (3.6'ya kadar bu eksikti: fotoğraf hep sığdırılıyordu). */
fun androidx.media3.ui.PlayerView.kadraj(doldur: Boolean) {
    resizeMode = if (doldur) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    try { setImageDisplayMode(if (doldur) androidx.media3.ui.PlayerView.IMAGE_DISPLAY_MODE_FILL else androidx.media3.ui.PlayerView.IMAGE_DISPLAY_MODE_FIT) }
    catch (e: IllegalStateException) { Kayit.yaz("Oynatıcıda görüntü katmanı yok: ${e.message}") }
}

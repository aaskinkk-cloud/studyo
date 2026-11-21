package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 3.73: logo geçişi — seçilen klip aralarında (baş · orta · sona yakın) geçiş yerine logo arka plandan büyüyerek gelir,
 *  ~1,6 sn ekranda kalır, sonra izleyiciye doğru büyüyüp solarak kaybolur. Arkada hafif koyu hale (logo öne çıkar, kesme görünmez).
 *  Logo özgün PNG'den kademeli küçültmeyle çizilir (yazılar net). Önizleme ve dışa aktarma aynı hesabı kullanır. */
object LogoGecis {
    const val ONCE = 900L            // kesmeden önce başlar (kesme anında logo tam ekranda)
    const val SURE = 2800L           // toplam: 600 ms giriş · 1600 ms bekleme · 600 ms çıkış
    const val BOY = 0.42f            // logonun çapı: kısa kenarın oranı

    class An(val olcek: Float, val alfa: Float)

    private fun yumusak(k: Float) = 1f - (1f - k.coerceIn(0f, 1f)).pow(3)
    private fun geriTasma(k: Float): Float { val x = k.coerceIn(0f, 1f) - 1f; val c1 = 1.70158f; return 1f + (c1 + 1f) * x * x * x + c1 * x * x }

    /** ms: zaman çizelgesi anı. null = görünmüyor. */
    fun an(anlar: List<Long>, ms: Long): An? {
        for (t in anlar) {
            val y = ms - (t - ONCE)
            if (y < 0 || y > SURE) continue
            return when {
                y < 600 -> { val k = y / 600f; An(0.15f + 0.85f * geriTasma(k), yumusak(k)) }
                y < 2200 -> An(1f + 0.04f * ((y - 600) / 1600f), 1f)
                else -> { val k = (y - 2200) / 600f; An(1.04f + 0.9f * k * k, 1f - k * k * (3f - 2f * k)) }
            }
        }
        return null
    }

    /** Kademeli (yarıya yarıya) küçültme: büyük PNG tek adımda küçültülünce yazılar tırtıklı/bulanık olur. */
    fun netOlcekle(src: Bitmap, w: Int, h: Int): Bitmap {
        var b = src
        while (b.width / 2 >= w && b.height / 2 >= h) b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        return if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w.coerceAtLeast(1), h.coerceAtLeast(1), true)
    }

    /** 3.82: şekiller (geçiş paneli) */
    val SEKILLER = listOf("yuvarlak" to "Yuvarlak", "kare" to "Yuvarlak kare", "ozgun" to "Özgün")

    /** Logo bitmapi (yumuşak gölgeli); W×H çıkış karesi için piksel boyutunda. sekil: yuvarlak · kare (yuvarlak köşe) · ozgun */
    fun bitmap(yol: String, sekil: String, W: Int, H: Int): Bitmap? {
        if (yol.isEmpty()) return null
        val kaynak = BitmapFactory.decodeFile(yol) ?: return null
        val hedef = (BOY * min(W, H)).toInt().coerceAtLeast(16)
        val s = hedef.toFloat() / max(kaynak.width, kaynak.height)
        val sw = (kaynak.width * s).toInt().coerceAtLeast(1); val sh = (kaynak.height * s).toInt().coerceAtLeast(1)
        var b = netOlcekle(kaynak, sw, sh)
        if (sekil == "yuvarlak" || sekil == "kare") {
            val d = min(sw, sh)
            val o = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val sh2 = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh2.setLocalMatrix(Matrix().apply { setTranslate(-(sw - d) / 2f, -(sh - d) / 2f) })
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = sh2 }
            if (sekil == "yuvarlak") Canvas(o).drawCircle(d / 2f, d / 2f, d / 2f, p)
            else Canvas(o).drawRoundRect(android.graphics.RectF(0f, 0f, d.toFloat(), d.toFloat()), d * 0.22f, d * 0.22f, p)
            b = o
        }
        // yumuşak gölge (logo her zeminde seçilsin)
        val pay = (b.width * 0.08f).toInt().coerceAtLeast(4)
        val c = Bitmap.createBitmap(b.width + pay * 2, b.height + pay * 2, Bitmap.Config.ARGB_8888)
        val cv = Canvas(c)
        cv.drawBitmap(b.extractAlpha(), pay.toFloat(), pay * 1.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80000000.toInt(); maskFilter = BlurMaskFilter(pay * 0.8f, BlurMaskFilter.Blur.NORMAL) })
        cv.drawBitmap(b, pay.toFloat(), pay.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return c
    }

    /** Arkadaki koyu hale (küçük bitmap; ölçekle kareye yayılır). */
    fun hale(): Bitmap {
        val w = 160; val h = 160
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = RadialGradient(w / 2f, h / 2f, w * 0.75f, intArrayOf(0x8C000000.toInt(), 0x59000000, 0x26000000), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        })
        return b
    }
}

/** Dışa aktarma / akıcı önizleme: logo geçişi bindirmesi (klip içi saat → zaman çizelgesi ms). hale = true: arkadaki koyu katman. */
class LogoGecisBindirme(private val bmp: Bitmap, private val klipBasMs: Long, private val anlar: List<Long>,
                        private val W: Int, private val H: Int, private val hale: Boolean) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val a = LogoGecis.an(anlar, klipBasMs + saat.yerelMs(presentationTimeUs))
            ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val b = StaticOverlaySettings.Builder().setBackgroundFrameAnchor(0f, 0f).setOverlayFrameAnchor(0f, 0f)
        return if (hale) b.setScale(W.toFloat() / bmp.width, H.toFloat() / bmp.height).setAlphaScale(a.alfa.coerceIn(0f, 1f)).build()
        else b.setScale(a.olcek, a.olcek).setAlphaScale(a.alfa.coerceIn(0f, 1f)).build()
    }
}

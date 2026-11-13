package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Kaynak karedeki (0…1) bir noktanın çıktı karesindeki (piksel) yeri. Presentation'ın sığdır/doldur hesabıyla aynı;
 *  önizlemedeki PlayerView (FIT/ZOOM) de aynı geometriyi kullanır. */
object Geometri {
    fun ciktiya(k: Klip, x: Float, y: Float, W: Int, H: Int): Pair<Float, Float> {
        val g = (if (k.gen > 0) k.gen else W).toFloat()
        val h = (if (k.yuk > 0) k.yuk else H).toFloat()
        val s = if (k.doldur) max(W / g, H / h) else min(W / g, H / h)
        val dw = g * s; val dh = h * s
        return ((W - dw) / 2f + x * dw) to ((H - dh) / 2f + y * dh)
    }

    /** Çıktı karesindeki (piksel) noktayı kaynak kareye (0…1) çevirir; kaynak dışındaysa null. */
    fun kaynaga(k: Klip, px: Float, py: Float, W: Int, H: Int): Pair<Float, Float>? {
        val g = (if (k.gen > 0) k.gen else W).toFloat()
        val h = (if (k.yuk > 0) k.yuk else H).toFloat()
        val s = if (k.doldur) max(W / g, H / h) else min(W / g, H / h)
        val dw = g * s; val dh = h * s
        val x = (px - (W - dw) / 2f) / dw; val y = (py - (H - dh) / 2f) / dh
        return if (x in 0f..1f && y in 0f..1f) x to y else null
    }
}

object Katman {
    /** Takip noktasının klip içi andaki konumu (kaynak karede 0…1); takip o andan önce başlamadıysa null. */
    fun takipKonum(t: Takip, ms: Long): Pair<Float, Float>? {
        if (ms < t.basMs) return null
        val n = t.noktalar
        if (n.isEmpty()) return t.basX to t.basY
        if (ms <= n.first().ms) return n.first().x to n.first().y
        if (ms >= n.last().ms) return n.last().x to n.last().y
        var lo = 0; var hi = n.lastIndex
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (n[m].ms <= ms) lo = m else hi = m }
        val a = n[lo]; val b = n[hi]
        val f = if (b.ms == a.ms) 0f else (ms - a.ms).toFloat() / (b.ms - a.ms)
        return (a.x + (b.x - a.x) * f) to (a.y + (b.y - a.y) * f)
    }

    fun takipYazisi(t: Takip, W: Int, H: Int): Bitmap? {
        if (t.metin.isBlank()) return null
        val k = min(W, H).toFloat()
        val boy = t.boyut * k
        return Cizim.bloklar(listOf(
            Blok(t.metin, boy, t.renk.toInt(), zemin = if (t.kutu) 0xB3000000.toInt() else null, kontur = if (t.kutu) 0f else boy * 0.14f, golge = if (t.kutu) null else 0xAA000000.toInt()),
            Blok(t.alt, boy * 0.62f, t.renk.toInt(), zemin = if (t.kutu) 0xB3000000.toInt() else null, kalin = false, kontur = if (t.kutu) 0f else boy * 0.1f),
        ), (W * 0.6f).toInt(), bosluk = 0.1f)
    }

    /** Yazının alt ortası noktanın biraz üstünde durur (noktayı kapatmaz). */
    fun takipYerlesim(bmp: Bitmap, px: Float, py: Float, W: Int, H: Int): Yerlesim {
        val ust = (py - min(W, H) * 0.03f).coerceIn(bmp.height.toFloat(), H.toFloat())
        val cx = px.coerceIn(bmp.width / 2f, W - bmp.width / 2f)
        return Yerlesim(bmp, (cx / W) * 2f - 1f, 1f - (ust / H) * 2f, 0f, -1f)
    }

    /** Nesne PNG'sini çıktı ölçeğinde hazırlar (isteğe bağlı yumuşak gölgeyle). */
    fun nesne(n: Nesne, W: Int, H: Int): Yerlesim? {
        val kaynak = BitmapFactory.decodeFile(n.yol) ?: return null
        val gen = (n.olcek * W).roundToInt().coerceAtLeast(8)
        val yuk = (kaynak.height * gen.toFloat() / kaynak.width).roundToInt().coerceAtLeast(8)
        val olc = Bitmap.createScaledBitmap(kaynak, gen, yuk, true)
        val b = if (!n.golge) olc else {
            val pay = (min(W, H) * 0.02f).roundToInt().coerceAtLeast(2)
            val o = Bitmap.createBitmap(gen + pay * 2, yuk + pay * 2, Bitmap.Config.ARGB_8888)
            val c = Canvas(o)
            val alfa = olc.extractAlpha()
            c.drawBitmap(alfa, pay.toFloat(), pay * 1.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88000000.toInt(); maskFilter = BlurMaskFilter(pay.toFloat(), BlurMaskFilter.Blur.NORMAL) })
            c.drawBitmap(olc, pay.toFloat(), pay.toFloat(), null)
            o
        }
        return Yerlesim(b, n.x * 2f - 1f, 1f - n.y * 2f, 0f, 0f)
    }
}

/** Takip eden yazı bindirmesi (klibin kendi efektlerinde). */
class TakipBindirme(private val k: Klip, private val t: Takip, private val bmp: Bitmap, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val saat = Saat()
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = k.basMs + saat.yerelMs(presentationTimeUs)
        val p = Katman.takipKonum(t, ms) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val (px, py) = Geometri.ciktiya(k, p.first, p.second, W, H)
        val y = Katman.takipYerlesim(bmp, px, py, W, H)
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(y.zx.coerceIn(-1f, 1f), y.zy.coerceIn(-1f, 1f)).setOverlayFrameAnchor(y.kx, y.ky).build()
    }
}

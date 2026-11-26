package app.studyo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.hypot
import kotlin.math.min

/** 4.42: açılış efektleri — büyük nokta açılışı, logonun büyüyerek gelip gitmesi, "Bu videoda izleyecekleriniz" özeti.
 *  Önizleme ve dışa aktarma AYNI çizim işlevlerini kullanır (oranlar karenin kısa kenarına göre). */
object GirisEfekt {
    const val NOKTA_MS = 1500L
    val RENKLER = listOf(0xFFFFC107.toInt(), 0xFFFF7043.toInt(), 0xFF4FC3F7.toInt(), 0xFF66BB6A.toInt(), 0xFFEC407A.toInt(), 0xFFFFFFFF.toInt())

    fun logoToplam(g: Giris) = g.logoGelMs + g.logoKalMs + g.logoGitMs
    fun logoBas(g: Giris) = if (g.nokta) NOKTA_MS - 200L else 200L
    /** Giriş başlığının başladığı an: önce nokta, sonra logo, sonra başlık. */
    fun baslikBas(g: Giris): Long = when { g.logoBuyu -> logoBas(g) + logoToplam(g); g.nokta -> NOKTA_MS - 300L; else -> 200L }
    fun logoAralik(p: Proje): Pair<Long, Long>? = if (p.giris.logoBuyu) logoBas(p.giris).let { it to it + logoToplam(p.giris) } else null
    fun logoAn(g: Giris, ms: Long): LogoGecis.An? = if (!g.logoBuyu) null else LogoGecis.anSure(ms - logoBas(g), g.logoGelMs, g.logoKalMs, g.logoGitMs)
    fun ozetAralik(p: Proje): Pair<Long, Long>? {
        if (!p.ozet.acik || p.ozet.maddeListe.isEmpty() && p.ozet.baslik.isBlank()) return null
        val a = if (p.giris.acik) Zaman.girisAralik(p).second else (logoAralik(p)?.second ?: baslikBas(p.giris))
        val b = (a + p.ozet.sureMs).coerceAtMost(p.toplamMs)
        return if (b > a) a to b else null
    }
    /** Bu anda nokta ya da özet çiziliyor mu (önizlemede Canvas'ı boşuna kurmamak için). */
    fun gorunur(p: Proje, ms: Long): Boolean = (p.giris.nokta && ms in 0..NOKTA_MS) || ozetAralik(p)?.let { ms in it.first..it.second } == true
    fun ciz(c: Canvas, w: Float, h: Float, p: Proje, ms: Long): Boolean {
        var d = false
        ozetAralik(p)?.let { (a, b) -> if (ms in a..b) { ozetCiz(c, w, h, p.ozet, ms - a, b - a); d = true } }
        if (p.giris.nokta && ms in 0..NOKTA_MS) { noktaCiz(c, w, h, ms, p.giris.noktaRenk); d = true }
        return d
    }

    private val boya = Paint(Paint.ANTI_ALIAS_FLAG)
    private val yol = Path()
    private fun geri(k: Float): Float { val x = k.coerceIn(0f, 1f) - 1f; val c1 = 1.70158f; return 1f + (c1 + 1f) * x * x * x + c1 * x * x }

    /** Siyah ekranda renkli büyük nokta zıplayarak gelir → ekranı doldurur → ortadan halka gibi açılıp videoyu gösterir. */
    fun noktaCiz(c: Canvas, w: Float, h: Float, y: Long, renk: Int) {
        if (y < 0 || y > NOKTA_MS) return
        val kisa = min(w, h); val cx = w / 2f; val cy = h / 2f; val R = hypot(w, h) / 2f + 4f; val r1 = kisa * 0.32f
        boya.style = Paint.Style.FILL; boya.color = renk
        when {
            y < 450 -> { c.drawColor(Color.BLACK); c.drawCircle(cx, cy, r1 * geri(y / 450f).coerceAtLeast(0f), boya) }
            y < 850 -> { val k = (y - 450) / 400f; val e = k * k * (3f - 2f * k); c.drawColor(Color.BLACK); c.drawCircle(cx, cy, r1 + (R - r1) * e, boya) }
            else -> {
                val k = ((y - 850) / (NOKTA_MS - 850f)).coerceIn(0f, 1f); val e = 1f - (1f - k) * (1f - k) * (1f - k); val r = R * e
                yol.reset(); yol.fillType = Path.FillType.EVEN_ODD
                yol.addRect(0f, 0f, w, h, Path.Direction.CW); yol.addCircle(cx, cy, r, Path.Direction.CW)
                boya.alpha = (255 * (1f - 0.5f * k)).toInt(); c.drawPath(yol, boya)
                boya.style = Paint.Style.STROKE; boya.strokeWidth = kisa * 0.014f; boya.color = Color.WHITE; boya.alpha = (220 * (1f - k)).toInt()
                c.drawCircle(cx, cy, r, boya); boya.style = Paint.Style.FILL
            }
        }
    }

    private val baslikB = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    private val maddeB = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.WHITE }
    private val kutu = RectF()

    /** Koyu yarı saydam kart: başlık (vurgu renginde) + maddeler sırayla soldan kayarak gelir; kart yumuşak girer/çıkar. */
    fun ozetCiz(c: Canvas, w: Float, h: Float, o: Ozet, y: Long, sure: Long) {
        if (y < 0 || y > sure) return
        val m = o.maddeListe.take(6)
        val kisa = min(w, h)
        val gA = min((y / 400f).coerceIn(0f, 1f), ((sure - y) / 400f).coerceIn(0f, 1f))
        if (gA <= 0f) return
        var ts = kisa * 0.062f * o.olcek; var mt = kisa * 0.05f * o.olcek
        baslikB.textSize = ts; maddeB.textSize = mt
        var pad = kisa * 0.045f
        val enGenis = maxOf(if (o.baslik.isBlank()) 0f else baslikB.measureText(o.baslik), m.maxOfOrNull { maddeB.measureText(it) + mt * 1.3f } ?: 0f)
        val sinir = w * 0.86f - 2 * pad
        if (enGenis > sinir && enGenis > 0f) { val f = sinir / enGenis; ts *= f; mt *= f; baslikB.textSize = ts; maddeB.textSize = mt }
        val genis = (if (o.baslik.isBlank()) 0f else baslikB.measureText(o.baslik)).coerceAtLeast(m.maxOfOrNull { maddeB.measureText(it) + mt * 1.3f } ?: 0f)
        val bY = if (o.baslik.isBlank()) 0f else ts * 1.35f
        val ph = pad * 2 + bY + m.size * mt * 1.6f
        val pw = genis + pad * 2
        val x0 = w * 0.07f; val y0 = (h - ph) / 2f + (1f - gA) * kisa * 0.03f
        boya.style = Paint.Style.FILL; boya.color = 0xC0101418.toInt(); boya.alpha = (0xC0 * gA).toInt()
        kutu.set(x0, y0, x0 + pw, y0 + ph); c.drawRoundRect(kutu, kisa * 0.03f, kisa * 0.03f, boya)
        boya.color = o.renk; boya.alpha = (255 * gA).toInt()
        kutu.set(x0, y0 + pad * 0.8f, x0 + kisa * 0.012f, y0 + ph - pad * 0.8f); c.drawRect(kutu, boya)
        var yy = y0 + pad
        if (o.baslik.isNotBlank()) { baslikB.color = o.renk; baslikB.alpha = (255 * gA).toInt(); c.drawText(o.baslik, x0 + pad, yy + ts, baslikB); yy += bY }
        for (i in m.indices) {
            val ka = ((y - (500L + i * 350L)) / 350f).coerceIn(0f, 1f)
            if (ka <= 0f) break
            val a = ka * gA; val dx = (1f - ka) * mt * 1.5f
            val satY = yy + mt * 1.6f * i + mt * 1.15f
            boya.color = o.renk; boya.alpha = (255 * a).toInt(); c.drawCircle(x0 + pad + dx + mt * 0.32f, satY - mt * 0.35f, mt * 0.2f, boya)
            maddeB.alpha = (255 * a).toInt(); c.drawText(m[i], x0 + pad + dx + mt * 1.0f, satY, maddeB)
        }
    }
}

/** 4.42: kare kare çizilen tam ekran bindirme (büyük nokta · özet); her kare zaman çizelgesi anına göre çizilir. */
class GirisEfektBindirme(private val p: Proje, private val klipBasMs: Long, W: Int, H: Int) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    private val bmp = Bitmap.createBitmap(W.coerceAtLeast(2), H.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
    private val bos = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val cv = Canvas(bmp)
    private var sonMs = Long.MIN_VALUE
    private var gorunur = false
    private fun hazirla(t: Long) {
        val ms = klipBasMs + saat.yerelMs(t)
        if (ms == sonMs) return
        sonMs = ms
        if (!GirisEfekt.gorunur(p, ms)) { gorunur = false; return }
        bmp.eraseColor(0)
        gorunur = GirisEfekt.ciz(cv, bmp.width.toFloat(), bmp.height.toFloat(), p, ms)
        bmp.prepareToDraw()
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap { hazirla(presentationTimeUs); return if (gorunur) bmp else bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        hazirla(presentationTimeUs)
        return if (!gorunur) StaticOverlaySettings.Builder().setAlphaScale(0f).build() else StaticOverlaySettings.Builder().build()
    }
}

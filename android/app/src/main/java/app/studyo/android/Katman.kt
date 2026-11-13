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
    /** a: o andaki kadraj/hareket (Yuz.an); verilmezse hareketsiz akıllı kadraj. */
    fun ciktiya(k: Klip, x: Float, y: Float, W: Int, H: Int, a: Yuz.An = Yuz.an(k, W, H, 0f)): Pair<Float, Float> {
        val (dw, dh) = boyut(k, W, H)
        return (W / 2f + a.tx * dw / 2f + (x - 0.5f) * dw * a.s) to (H / 2f - a.ty * dh / 2f + (y - 0.5f) * dh * a.s)
    }

    private fun boyut(k: Klip, W: Int, H: Int): Pair<Float, Float> {
        val g = (if (k.gen > 0) k.gen else W).toFloat()
        val h = (if (k.yuk > 0) k.yuk else H).toFloat()
        val s = if (k.doldur) max(W / g, H / h) else min(W / g, H / h)
        return g * s to h * s
    }

    /** Çıktı karesindeki (piksel) noktayı kaynak kareye (0…1) çevirir; kaynak dışındaysa null. */
    fun kaynaga(k: Klip, px: Float, py: Float, W: Int, H: Int, a: Yuz.An = Yuz.an(k, W, H, 0f)): Pair<Float, Float>? {
        val (dw, dh) = boyut(k, W, H)
        val x = (px - W / 2f - a.tx * dw / 2f) / (dw * a.s) + 0.5f
        val y = (py - H / 2f + a.ty * dh / 2f) / (dh * a.s) + 0.5f
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

    /** Çizgili takip yazısı: iki yön (sağa / sola açılan) bitmap ve her birinde noktanın piksel yeri. */
    class TakipGorsel(val sag: Bitmap, val sagX: Float, val sagY: Float, val sol: Bitmap, val solX: Float, val solY: Float, val kutu: Boolean = false)

    fun takipGorsel(t: Takip, W: Int, H: Int): TakipGorsel? {
        if (t.metin.isBlank()) return null
        if (t.stil == "kutu") { val b = takipYazisi(t, W, H) ?: return null; return TakipGorsel(b, 0f, 0f, b, 0f, 0f, kutu = true) }
        val (a, ax, ay) = cizgili(t, W, H, 1)
        if (t.stil == "igne") return TakipGorsel(a, ax, ay, a, ax, ay)
        val (b, bx, by) = cizgili(t, W, H, -1)
        return TakipGorsel(a, ax, ay, b, bx, by)
    }

    /** Nokta sağ kenara yakınsa çizgi sola açılır. Bitmap'in nokta pikseli tam takip noktasına oturur. */
    fun takipYerlesimG(g: TakipGorsel, px: Float, py: Float, W: Int, H: Int): Yerlesim {
        if (g.kutu) return takipYerlesim(g.sag, px, py, W, H)
        val sol = px > W * 0.6f
        val b = if (sol) g.sol else g.sag; val dx = if (sol) g.solX else g.sagX; val dy = if (sol) g.solY else g.sagY
        val kx = (dx / b.width) * 2f - 1f; val ky = 1f - (dy / b.height) * 2f
        return Yerlesim(b, ((px / W) * 2f - 1f).coerceIn(-1f, 1f), (1f - (py / H) * 2f).coerceIn(-1f, 1f), kx.coerceIn(-1f, 1f), ky.coerceIn(-1f, 1f))
    }

    private fun cizgili(t: Takip, W: Int, H: Int, yon: Int): Triple<Bitmap, Float, Float> {
        val k = min(W, H).toFloat()
        val boy = t.boyut * k
        val baslik = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = t.renk.toInt(); textSize = boy; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setShadowLayer(boy * 0.12f, 0f, boy * 0.04f, 0x99000000.toInt())
        }
        val altP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = t.renk.toInt(); textSize = boy * 0.6f; typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            setShadowLayer(boy * 0.1f, 0f, boy * 0.03f, 0x99000000.toInt())
        }
        val cr = t.cizgiRenk.toInt()
        val cizgi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cr; style = Paint.Style.STROKE; strokeWidth = (k * 0.0035f).coerceAtLeast(2f); strokeCap = Paint.Cap.ROUND
            setShadowLayer(k * 0.004f, 0f, 0f, 0x66000000) }
        val halka = Paint(cizgi).apply { strokeWidth = (k * 0.0028f).coerceAtLeast(1.5f); color = (cr and 0x00FFFFFF) or 0xCC000000.toInt() }
        val nokta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(k * 0.004f, 0f, 0f, 0x88000000.toInt()) }
        val m = k * 0.02f; val R = k * 0.024f; val r0 = k * 0.0065f
        val alt = t.alt.trim()
        val bW = baslik.measureText(t.metin); val aW = if (alt.isEmpty()) 0f else altP.measureText(alt)
        val metinW = max(bW, aW)
        val metinH = boy * 1.05f + (if (alt.isEmpty()) 0f else boy * 0.78f)
        val bosluk = boy * 0.28f            // yazı ile çizgi arası
        return when (t.stil) {
            "igne" -> {
                val L = k * 0.10f
                val gw = max(metinW, R * 2) + m * 2; val gh = metinH + bosluk + L + R + m * 2
                val bm = Bitmap.createBitmap(gw.toInt() + 1, gh.toInt() + 1, Bitmap.Config.ARGB_8888); val c = Canvas(bm)
                val nx = gw / 2f; val ny = gh - m - R
                c.drawCircle(nx, ny, R, halka); c.drawCircle(nx, ny, r0, nokta)
                val ust = ny - R - L
                c.drawLine(nx, ny - R, nx, ust, cizgi)
                c.drawLine(nx - min(metinW / 2f, k * 0.06f), ust, nx + min(metinW / 2f, k * 0.06f), ust, cizgi)
                baslik.textAlign = Paint.Align.CENTER; altP.textAlign = Paint.Align.CENTER
                var y = ust - bosluk
                if (alt.isNotEmpty()) { c.drawText(alt, nx, y, altP); y -= boy * 0.78f }
                c.drawText(t.metin, nx, y, baslik)
                Triple(bm, nx, ny)
            }
            else -> {
                // cagri: eğik + yatay; kose: dikey + yatay
                val dx = if (t.stil == "kose") 0f else k * 0.10f
                val dy = if (t.stil == "kose") k * 0.12f else k * 0.09f
                val yatay = max(metinW + k * 0.03f, k * 0.18f)
                val gw = R + dx + yatay + m * 2 + R; val gh = R + dy + bosluk + metinH + m * 2
                val bm = Bitmap.createBitmap(gw.toInt() + 1, gh.toInt() + 1, Bitmap.Config.ARGB_8888); val c = Canvas(bm)
                val nx = if (yon > 0) m + R else gw - m - R; val ny = gh - m - R
                c.drawCircle(nx, ny, R, halka); c.drawCircle(nx, ny, r0, nokta)
                // halkanın kenarından çık
                val ux = if (t.stil == "kose") 0f else yon * dx; val uy = -dy
                val ul = kotlin.math.sqrt(ux * ux + uy * uy).coerceAtLeast(1f)
                val sx = nx + ux / ul * R; val sy = ny + uy / ul * R
                val dirX = nx + yon * dx; val dirY = ny - dy
                c.drawLine(sx, sy, dirX, dirY, cizgi)
                val sonX = dirX + yon * yatay
                c.drawLine(dirX, dirY, sonX, dirY, cizgi)
                val hiza = if (yon > 0) Paint.Align.LEFT else Paint.Align.RIGHT
                baslik.textAlign = hiza; altP.textAlign = hiza
                val tx = dirX + yon * k * 0.012f
                var y = dirY - bosluk
                if (alt.isNotEmpty()) { c.drawText(alt, tx, y, altP); y -= boy * 0.78f }
                c.drawText(t.metin, tx, y, baslik)
                Triple(bm, nx, ny)
            }
        }
    }

    /** Yazının alt ortası noktanın biraz üstünde durur (noktayı kapatmaz). */
    fun takipYerlesim(bmp: Bitmap, px: Float, py: Float, W: Int, H: Int): Yerlesim {
        val ust = (py - min(W, H) * 0.03f).coerceIn(bmp.height.toFloat(), H.toFloat())
        val cx = px.coerceIn(bmp.width / 2f, W - bmp.width / 2f)
        return Yerlesim(bmp, (cx / W) * 2f - 1f, 1f - (ust / H) * 2f, 0f, -1f)
    }

    fun kareNo(n: Nesne, ms: Long): Int = (((ms - n.basMs).coerceAtLeast(0)) / n.kareMs.coerceAtLeast(1)).toInt().coerceIn(0, (n.kareSayi - 1).coerceAtLeast(0))
    fun kareYolu(n: Nesne, i: Int): String = java.io.File(n.kareDizin, String.format(java.util.Locale.US, "%04d.png", i)).path

    /** Hareketli nesnenin i. karesi, çıktı genişliğinin n.olcek oranında. */
    fun nesneKaresi(n: Nesne, i: Int, W: Int): Bitmap? {
        val k = BitmapFactory.decodeFile(kareYolu(n, i)) ?: return null
        val gen = (n.olcek * W).roundToInt().coerceAtLeast(8)
        val yuk = (k.height * gen.toFloat() / k.width).roundToInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(k, gen, yuk, true).also { if (it !== k) k.recycle() }
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
class TakipBindirme(private val k: Klip, private val t: Takip, private val g: Katman.TakipGorsel, private val W: Int, private val H: Int, private val an: (Long) -> Yuz.An) : BitmapOverlay() {
    private val saat = Saat()
    private fun yer(us: Long): Yerlesim? {
        val yerel = saat.yerelMs(us)
        val p = Katman.takipKonum(t, k.basMs + yerel) ?: return null
        val (px, py) = Geometri.ciktiya(k, p.first, p.second, W, H, an(yerel))
        return Katman.takipYerlesimG(g, px, py, W, H)
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = yer(presentationTimeUs)?.bmp ?: g.sag
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val y = yer(presentationTimeUs) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(y.zx, y.zy).setOverlayFrameAnchor(y.kx, y.ky).build()
    }
}

/** Üst üste binen geçiş: birincil (üst) dizide giden klibin kuyruğu var; pencere içinde solar/kayar/büyür, dışında görünmez. */
class GecisBirlestirici(private val tur: String, private val pencereler: List<LongArray>, private val W: Int, private val H: Int) : androidx.media3.common.VideoCompositorSettings {
    override fun getOutputSize(inputSizes: MutableList<androidx.media3.common.util.Size>): androidx.media3.common.util.Size = androidx.media3.common.util.Size(W, H)
    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        if (inputId != 0) return StaticOverlaySettings.Builder().build()
        val ms = presentationTimeUs / 1000
        val p = pencereler.firstOrNull { ms >= it[0] && ms < it[0] + it[1] } ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val f = ((ms - p[0]).toFloat() / p[1]).coerceIn(0f, 1f)
        return Gecisler.ustKatman(tur, f).let { a ->
            StaticOverlaySettings.Builder().setAlphaScale(a.alfa).setBackgroundFrameAnchor(a.zx, 0f).setOverlayFrameAnchor(a.kx, 0f).setScale(a.olcek, a.olcek).build()
        }
    }
}

/** Videodan kesilmiş hareketli nesne: o anki kareyi (PNG) yükler; son kareden sonra son karede durur. */
class NesneKareBindirme(private val n: Nesne, private val klipBasMs: Long, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val saat = Saat()
    private val bos: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var sonIdx = -1
    private var bmp: Bitmap? = null
    private fun hazirla(t: Long): Long {
        val ms = klipBasMs + saat.yerelMs(t)
        val idx = Katman.kareNo(n, ms)
        if (idx != sonIdx) {
            sonIdx = idx
            bmp = Katman.nesneKaresi(n, idx, W)
        }
        return ms
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap { hazirla(presentationTimeUs); return bmp ?: bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = hazirla(presentationTimeUs)
        val a = Zaman.solma(ms, n.basMs, n.sonMs, 200)
        if (bmp == null || a <= 0f) return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor((n.x * 2f - 1f).coerceIn(-1f, 1f), (1f - n.y * 2f).coerceIn(-1f, 1f)).setOverlayFrameAnchor(0f, 0f).setAlphaScale(a).build()
    }
}

package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** 4.37: kesilen kişinin/nesnenin gövde ve baş çevresinde dönen elektrik tarama ışığı (Vita "Electric Scan", CapCut elektrik alevi örnek).
 *  Kontur, kesilen görüntünün saydamlığından bir kez çıkarılır (merkezden ışınlarla dış hat). Her karede: kenarda titreyen ince elektrik
 *  hattı + hat boyunca dönen parlak tarama başı. Önizleme ve dışa aktarma AYNI çizim işlevini kullanır. */
object Elektrik {
    val RENKLER = listOf(0xFF4FC3F7L to "Mavi", 0xFFB388FFL to "Mor", 0xFFFFD54FL to "Altın", 0xFFFFFFFFL to "Beyaz", 0xFF69F0AEL to "Yeşil")
    private const val N = 160
    private val onbellek = HashMap<String, FloatArray>()

    /** Dış hat: N nokta (x, y sırayla), bitmap boyutuna göre 0…1. Saydamlık eşiği %60 (yumuşak gölge hatta katılmaz). */
    @Synchronized fun kontur(b: Bitmap, anahtar: String): FloatArray = onbellek.getOrPut(anahtar + "|" + b.width + "x" + b.height) {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        var sx = 0.0; var sy = 0.0; var say = 0
        for (y in 0 until h step 2) for (x in 0 until w step 2) if ((px[y * w + x] ushr 24) > 153) { sx += x; sy += y; say++ }
        val cx = if (say > 0) (sx / say).toFloat() else w / 2f; val cy = if (say > 0) (sy / say).toFloat() else h / 2f
        val r0 = hypot(w.toFloat(), h.toFloat())
        val r = FloatArray(N)
        for (i in 0 until N) {
            val a = (i.toFloat() / N) * 2f * Math.PI.toFloat()
            val dx = cos(a); val dy = sin(a)
            var enUzak = 0f; var d = 0f
            while (d < r0) {
                val x = (cx + dx * d).toInt(); val y = (cy + dy * d).toInt()
                if (x < 0 || y < 0 || x >= w || y >= h) break
                if ((px[y * w + x] ushr 24) > 153) enUzak = d
                d += 1.5f
            }
            r[i] = enUzak
        }
        // yumuşat (komşu ortalaması, iki kez) — saç ucu / parmak gibi sivri çıkıntılar titremesin
        repeat(2) { val c = r.copyOf(); for (i in 0 until N) r[i] = (c[(i - 1 + N) % N] + c[i] * 2f + c[(i + 1) % N]) / 4f }
        if (onbellek.size > 30) onbellek.clear()
        FloatArray(N * 2) { j -> val i = j / 2; val a = (i.toFloat() / N) * 2f * Math.PI.toFloat()
            if (j % 2 == 0) (cx + cos(a) * r[i]) / w else (cy + sin(a) * r[i]) / h }
    }

    /** 4.45 KÖK NEDEN (4K dışa aktarmada yüz turlu/ışıklı fotoğraflarda kodlama ~10 kat yavaşlıyordu): ışıma her karede
     *  BlurMaskFilter ile yazılımda bulanıklaştırılıyordu (4K'da yarıçap 2×, her kare yeni filtre). Şimdi aynı rengin
     *  genişten dara 4 katmanlı yarı saydam çizgisi — bulanıklık hissi verir, bulanıklaştırma yok. Eski görünüm Ayarlar'da seçenek. */
    private val bulanikOnbellek = HashMap<Int, BlurMaskFilter>()
    /** 4.45 ölçüm: dışa aktarma sonunda Kayıt'a yazılır (çizim sayısı, toplam süre). */
    @Volatile var olcSay = 0; @Volatile var olcNs = 0L
    fun olcSifirla() { olcSay = 0; olcNs = 0L }
    fun olcMetni() = if (olcSay == 0) "elektrik çizimi yok" else "elektrik çizimi ${olcSay} kez, toplam ${olcNs / 1_000_000} ms (ort ${olcNs / olcSay / 1000} µs)"
    private fun isima(c: Canvas, yol: Path, alfa: Int, gen: Float, r: Float) {
        if (alfa <= 0) return
        if (Duzen.elektrikBulanik) {
            pIsik.maskFilter = bulanikOnbellek.getOrPut((r * 4f).toInt()) { BlurMaskFilter(r.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL) }
            pIsik.alpha = alfa.coerceIn(0, 255); pIsik.strokeWidth = gen; c.drawPath(yol, pIsik); pIsik.maskFilter = null; return
        }
        pIsik.maskFilter = null
        val katman = floatArrayOf(2.2f, 1.45f, 0.8f, 0.25f); val oran = floatArrayOf(0.10f, 0.18f, 0.28f, 0.45f)
        for (i in 0 until 4) { pIsik.alpha = (alfa * oran[i]).toInt().coerceIn(0, 255); pIsik.strokeWidth = gen + r * katman[i]; c.drawPath(yol, pIsik) }
    }

    private val pIsik = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pCekirdek = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    /** (ox, oy, w, h): kesilen görüntünün çizildiği dikdörtgen (piksel). t: saniye. olc: çizim ölçeği (1 = 1080p kare). */
    @Synchronized fun ciz(c: Canvas, ox: Float, oy: Float, w: Float, h: Float, k: FloatArray, t: Float, renk: Int, olc: Float) {
        val t0 = System.nanoTime(); try { ciz0(c, ox, oy, w, h, k, t, renk, olc) } finally { olcNs += System.nanoTime() - t0; olcSay++ }
    }
    private fun ciz0(c: Canvas, ox: Float, oy: Float, w: Float, h: Float, k: FloatArray, t: Float, renk: Int, olc: Float) {
        if (k.size < 8 || w < 4f || h < 4f) return
        val n = k.size / 2
        var mx = 0f; var my = 0f
        for (i in 0 until n) { mx += k[2 * i]; my += k[2 * i + 1] }
        mx /= n; my /= n
        val yol = Path()
        for (i in 0 until n) {
            // hat dışa doğru %3 açılır; elektrik titremesi: iki farklı hızda dalga (kenar boyunca kıvılcım hissi)
            val nx = k[2 * i] - mx; val ny = k[2 * i + 1] - my
            val boy = max(1e-4f, hypot(nx, ny))
            val tit = (sin(i * 1.7f + t * 23f) * cos(i * 0.9f - t * 17f) + 0.35f * sin(i * 5.3f + t * 41f)) * 0.012f
            val f = 1.03f + tit / boy
            val x = ox + (mx + nx * f) * w; val y = oy + (my + ny * f) * h
            if (i == 0) yol.moveTo(x, y) else yol.lineTo(x, y)
        }
        yol.close()
        // 1) ince sürekli hat (soluk, nefes alır gibi)
        val nefes = 0.55f + 0.25f * sin(t * 3.1f)
        pIsik.color = renk; isima(c, yol, (150 * nefes).toInt(), 7f * olc, 9f * olc)
        pCekirdek.color = renk; pCekirdek.alpha = (170 * nefes).toInt(); pCekirdek.strokeWidth = 1.6f * olc
        c.drawPath(yol, pCekirdek)
        // 2) dönen tarama başı: hattın %24'ü, 2,2 sn'de bir tur; parlak çekirdek + geniş ışıma
        val pm = PathMeasure(yol, true); val L = pm.length
        if (L <= 1f) return
        val bas = ((t / 2.2f) % 1f) * L; val uz = 0.24f * L
        val parca = Path()
        if (bas + uz <= L) pm.getSegment(bas, bas + uz, parca, true)
        else { pm.getSegment(bas, L, parca, true); pm.getSegment(0f, bas + uz - L, parca, true) }
        isima(c, parca, 235, 14f * olc, 16f * olc)
        pCekirdek.color = 0xFFFFFFFF.toInt(); pCekirdek.alpha = 240; pCekirdek.strokeWidth = 3f * olc
        c.drawPath(parca, pCekirdek)
        pCekirdek.color = renk
        // 3) tarama başında küçük kıvılcımlar
        val nok = FloatArray(2); val tan = FloatArray(2)
        pm.getPosTan((bas + uz) % L, nok, tan)
        for (j in 0 until 3) {
            val a = atan2(tan[1], tan[0]) + 1.57f + sin(t * 31f + j * 2.1f) * 0.9f
            val d = (6f + 9f * (0.5f + 0.5f * sin(t * 47f + j))) * olc
            pCekirdek.alpha = 200; pCekirdek.strokeWidth = 1.4f * olc
            c.drawLine(nok[0], nok[1], nok[0] + cos(a) * d, nok[1] + sin(a) * d, pCekirdek)
        }
    }

    /** 4.40: yüz turundaki kişi hattı (açık çizgi: iki alt uçtan omuzlar ve baş) — titreyen elektrik hattı + hat boyunca gidip gelen tarama başı.
     *  p: çıkış pikselinde noktalar (x, y sırayla). */
    @Synchronized fun cizHat(c: Canvas, p: FloatArray, t: Float, renk: Int, olc: Float, alfa: Float) {
        val t0 = System.nanoTime(); try { cizHat0(c, p, t, renk, olc, alfa) } finally { olcNs += System.nanoTime() - t0; olcSay++ }
    }
    private fun cizHat0(c: Canvas, p: FloatArray, t: Float, renk: Int, olc: Float, alfa: Float) {
        if (p.size < 8 || alfa <= 0.01f) return
        val n = p.size / 2
        val yol = Path()
        for (i in 0 until n) {
            val i0 = maxOf(0, i - 1); val i1 = minOf(n - 1, i + 1)
            val tx = p[2 * i1] - p[2 * i0]; val ty = p[2 * i1 + 1] - p[2 * i0 + 1]; val tb = max(1e-3f, hypot(tx, ty))
            val tit = (sin(i * 1.7f + t * 23f) * cos(i * 0.9f - t * 17f) + 0.35f * sin(i * 5.3f + t * 41f)) * 3.2f * olc
            val x = p[2 * i] - ty / tb * tit; val y = p[2 * i + 1] + tx / tb * tit
            if (i == 0) yol.moveTo(x, y) else yol.lineTo(x, y)
        }
        val nefes = (0.6f + 0.25f * sin(t * 3.1f)) * alfa
        pIsik.color = renk; isima(c, yol, (160 * nefes).toInt(), 7f * olc, 9f * olc)
        pCekirdek.color = renk; pCekirdek.alpha = (180 * nefes).toInt(); pCekirdek.strokeWidth = 1.6f * olc
        c.drawPath(yol, pCekirdek)
        val pm = PathMeasure(yol, false); val L = pm.length
        if (L <= 1f) return
        val f = (t / 1.6f) % 2f; val q = if (f < 1f) f else 2f - f            // gidip gelir
        val uz = 0.26f * L; val bas = q * (L - uz)
        val parca = Path(); pm.getSegment(bas, bas + uz, parca, true)
        isima(c, parca, (235 * alfa).toInt(), 14f * olc, 16f * olc)
        pCekirdek.color = 0xFFFFFFFF.toInt(); pCekirdek.alpha = (240 * alfa).toInt(); pCekirdek.strokeWidth = 3f * olc
        c.drawPath(parca, pCekirdek)
        pCekirdek.color = renk
    }

    /** Dışa aktarma: kaynak görüntü + çevresinde ışık, kenarlara pay bırakılmış tek bitmap (merkez aynı). buf yeniden kullanılır. */
    fun kare(src: Bitmap, k: FloatArray, t: Float, renk: Int, olc: Float, buf: Bitmap?): Bitmap {
        val pay = (max(src.width, src.height) * 0.12f).toInt().coerceAtLeast(8)
        val bw = src.width + pay * 2; val bh = src.height + pay * 2
        val b = if (buf != null && buf.width == bw && buf.height == bh && !buf.isRecycled) buf else Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        b.eraseColor(0)
        val c = Canvas(b)
        c.drawBitmap(src, pay.toFloat(), pay.toFloat(), null)
        ciz(c, pay.toFloat(), pay.toFloat(), src.width.toFloat(), src.height.toFloat(), k, t, renk, olc)
        return b
    }
}

/** 4.37: ışıklı, yerinde duran (fotoğrafa bağlı olmayan) kesilen nesne — her karede görüntü + elektrik ışığı. */
class ElektrikBindirme(private val y: Yerlesim, private val k: FloatArray, private val n: Nesne, private val klipBasMs: Long, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    private var buf: Bitmap? = null; private var sonUs = Long.MIN_VALUE; private var ms = 0L
    private fun hazirla(us: Long) {
        if (us == sonUs) return; sonUs = us
        ms = klipBasMs + saat.yerelMs(us)
        buf = Elektrik.kare(y.bmp, k, ms / 1000f, n.isikRenk.toInt(), minOf(W, H) / 1080f, buf)
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap { hazirla(presentationTimeUs); return buf!! }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        hazirla(presentationTimeUs)
        return NesneAnim.ayar(Yerlesim(buf!!, y.zx, y.zy, 0f, 0f), NesneAnim.an(n, ms), W, H, y.alfa)   // 4.39: hareketli giriş/çıkış
    }
}

/** 4.39: kesilen nesnenin giriş · çıkış · bekleme hareketi. Önizleme ve dışa aktarma AYNI hesabı kullanır.
 *  "sol" (solarak) eski davranışla birebir aynı (300 ms doğrusal solma). dy: karenin yüksekliğine oran (aşağı +). aci: derece (saat yönü +). */
object NesneAnim {
    val GIRISLER = listOf("sol" to "Solarak", "buyu" to "Büyüyerek", "kay" to "Kayarak", "yok" to "Anında")
    val BEKLEMELER = listOf("yok" to "Sabit", "nefes" to "Nefes", "salla" to "Salınım")
    class An(val alfa: Float, val olcek: Float, val dy: Float, val aci: Float)
    private val YOK = An(0f, 1f, 0f, 0f)
    val NOTR = An(1f, 1f, 0f, 0f)
    private fun yum(x: Float) = x * x * (3f - 2f * x)
    private fun geri(x: Float): Float { val c1 = 1.4f; val c3 = c1 + 1f; val t = x - 1f; return 1f + c3 * t * t * t + c1 * t * t }   // hafif taşarak oturma
    fun an(n: Nesne, ms: Long, solmaMs: Long = 300): An {
        if (ms < n.basMs || ms > n.sonMs) return YOK
        val g = ((ms - n.basMs).toFloat() / solmaMs).coerceIn(0f, 1f)
        val c = ((n.sonMs - ms).toFloat() / solmaMs).coerceIn(0f, 1f)
        var a = 1f; var s = 1f; var dy = 0f; var aci = 0f
        when (n.giris) {
            "sol" -> a = minOf(a, g)
            "buyu" -> { a = minOf(a, (g * 2f).coerceAtMost(1f)); s *= 0.25f + 0.75f * geri(g) }
            "kay" -> { a = minOf(a, yum(g)); dy += (1f - yum(g)) * 0.12f }
        }
        when (n.cikis) {
            "sol" -> a = minOf(a, c)
            "buyu" -> { a = minOf(a, (c * 2f).coerceAtMost(1f)); s *= 0.25f + 0.75f * yum(c) }
            "kay" -> { a = minOf(a, yum(c)); dy += (1f - yum(c)) * 0.12f }
        }
        val t = (ms - n.basMs) / 1000f
        when (n.bekleme) {
            "nefes" -> s *= 1f + 0.035f * kotlin.math.sin(t * 2f * Math.PI.toFloat() / 2.4f)
            "salla" -> aci = 3f * kotlin.math.sin(t * 2f * Math.PI.toFloat() / 3f)
        }
        return An(a.coerceIn(0f, 1f), s, dy, aci)
    }
    /** Media3 bindirme ayarı: merkez çapalı yerleşime hareket uygulanır. */
    fun ayar(y: Yerlesim, a: An, W: Int, H: Int, alfa: Float = 1f): OverlaySettings =
        Yerlesim(y.bmp, y.zx, y.zy - a.dy * 2f, 0f, 0f).ayar(StaticOverlaySettings.Builder(), W, H)
            .setScale(a.olcek, a.olcek).setRotationDegrees(-a.aci).setAlphaScale((alfa * a.alfa).coerceIn(0f, 1f)).build()
}

/** 4.39: yerinde duran kesilen nesne (ışıksız) — giriş/çıkış/bekleme hareketiyle. */
class NesneBindirme(private val y: Yerlesim, private val n: Nesne, private val klipBasMs: Long, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    override fun getBitmap(presentationTimeUs: Long): Bitmap = y.bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        NesneAnim.ayar(y, NesneAnim.an(n, klipBasMs + saat.yerelMs(presentationTimeUs)), W, H, y.alfa)
}

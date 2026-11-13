package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Reels tarzı şerit: bitmap + hedef merkez (OpenGL -1…1) + sıra gecikmesi. */
class Serit(val bmp: Bitmap, val cx: Float, val cy: Float, val gecikmeMs: Long)

/** Bir andaki konum: Yerlesim ile aynı anlam + ölçek. */
class AnKonum(val zx: Float, val zy: Float, val kx: Float, val ky: Float, val olcek: Float, val alfa: Float) {
    fun solUst(bw: Int, bh: Int, W: Int, H: Int): Pair<Float, Float> {
        val w = bw * olcek; val h = bh * olcek
        val px = (zx + 1f) / 2f * W; val py = (1f - zy) / 2f * H
        return (px - (kx + 1f) / 2f * w) to (py - (1f - ky) / 2f * h)
    }
}

object Reels {
    val STILLER = listOf(
        "r_haber" to "Renkli kutular", "r_strip" to "Beyaz şerit", "r_redwhite" to "Kırmızı-beyaz", "r_bang" to "Sarı ünlem", "r_green" to "Yeşil tırnak",
        "r_quote" to "Sarı balon", "r_alert" to "Ünlem kutusu", "r_dikkat" to "Dikkat", "r_torn" to "Yırtık kâğıt",
        "r_frame" to "Köşe çerçeve", "r_tricolor" to "Üç renk", "r_frekans" to "Kırmızı sekme", "r_alinti" to "Kırmızı alıntı",
    )
    private const val KIRMIZI = 0xFFE53935.toInt()
    private const val SARI = 0xFFFFD400.toInt()
    private const val SIYAH = 0xFF111111.toInt()
    private const val BEYAZ = 0xFFFFFFFF.toInt()
    private const val YESIL = 0xFF1FA855.toInt()
    private val AGIR: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    /** Anton (SIL OFL; res/font) — dar, yüksek manşet harfi. Yüklenemezse dar kalın sistem yazısı. */
    private val ANTON: Typeface by lazy {
        try { androidx.core.content.res.ResourcesCompat.getFont(Depo.app, R.font.anton) ?: Typeface.create("sans-serif-condensed", Typeface.BOLD) }
        catch (e: Throwable) { Typeface.create("sans-serif-condensed", Typeface.BOLD) }
    }
    private val CIP: Typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    /** Renkli kutular: manşet satırı (dolu kutu, Anton). */
    private fun kutuSatir(m: String, boy: Float, zemin: Int, yazi: Int): Bitmap {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = boy; typeface = ANTON; color = yazi }
        val r = android.graphics.Rect(); p.getTextBounds("HĞŞİ", 0, 4, r)
        val ust = -r.top.toFloat()                       // büyük harf + şapka yüksekliği
        val padX = boy * 0.30f; val padY = boy * 0.16f
        val w = (p.measureText(m) + 2 * padX).toInt().coerceAtLeast(4); val h = (ust + 2 * padY).toInt().coerceAtLeast(4)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = zemin })
        c.drawText(m, padX, padY + ust, p)
        return b
    }
    /** Renkli kutular: küçük bant (tarih / konum), harf aralıklı; takvim simgesi isteğe bağlı. */
    private fun cip(m: String, boy: Float, zemin: Int, yazi: Int, takvim: Boolean): Bitmap {
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = boy; typeface = CIP; color = yazi; letterSpacing = 0.12f }
        val r = android.graphics.Rect(); p.getTextBounds("HĞ", 0, 2, r)
        val ust = -r.top.toFloat()
        val padX = boy * 0.55f; val padY = boy * 0.42f; val ik = if (takvim) boy * 1.5f else 0f
        val w = (p.measureText(m) + 2 * padX + ik).toInt(); val h = (ust + 2 * padY).toInt()
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h * 0.16f, h * 0.16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = zemin })
        if (takvim) {
            val q = boy * 0.95f; val x0 = padX * 0.9f; val y0 = (h - q) / 2f
            val cz = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yazi; style = Paint.Style.STROKE; strokeWidth = boy * 0.1f }
            c.drawRoundRect(RectF(x0, y0, x0 + q, y0 + q), q * 0.12f, q * 0.12f, cz)
            c.drawLine(x0, y0 + q * 0.32f, x0 + q, y0 + q * 0.32f, cz)
        }
        c.drawText(m, padX + ik, padY + ust, p)
        return b
    }
    /** Satır bölme: '|' yoksa kelimeleri genişliğe göre dizer. */
    private fun sar(m: String, p: TextPaint, en: Float): List<String> {
        val o = ArrayList<String>(); var s = ""
        for (k in m.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val d = if (s.isEmpty()) k else "$s $k"
            if (s.isNotEmpty() && p.measureText(d) > en) { o += s; s = k } else s = d
        }
        if (s.isNotEmpty()) o += s
        return o
    }

    private fun yaziBoya(boy: Float, renk: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = boy; color = renk; typeface = AGIR }

    private fun duzen(m: String, p: TextPaint, en: Int): StaticLayout =
        StaticLayout.Builder.obtain(m, 0, m.length, p, en.coerceAtLeast(10)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(0f, 1.0f).build()

    private fun genis(l: StaticLayout): Float { var w = 1f; for (i in 0 until l.lineCount) w = max(w, l.getLineWidth(i)); return w }

    /** Tek şerit çizer: zemin şekli + yazı + süs. sol/üst boşluk = pad. */
    private fun serit(metin: String, boy: Float, yazi: Int, en: Int, padX: Float = boy * 0.45f, padY: Float = boy * 0.22f,
                      solEk: Float = 0f, altEk: Float = 0f, zemin: (Canvas, RectF) -> Unit): Bitmap {
        val p = yaziBoya(boy, yazi)
        val l = duzen(metin, p, (en - 2 * padX - solEk).toInt())
        val w = (genis(l) + 2 * padX + solEk).toInt().coerceAtLeast(4)
        val h = (l.height + 2 * padY + altEk).toInt().coerceAtLeast(4)
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        zemin(c, RectF(0f, 0f, w.toFloat(), h - altEk))
        c.save(); c.translate(padX + solEk, padY); l.draw(c); c.restore()
        return b
    }

    private fun dolu(renk: Int, r: Float = 0f): (Canvas, RectF) -> Unit = { c, rc -> c.drawRoundRect(rc, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = renk }) }

    /** Başlık satırlarını ve bantları stile göre şeritlere çevirir (yukarıdan aşağı). */
    fun bitmapler(stil: String, baslik: String, bant: String, W: Int, H: Int): List<Bitmap> {
        val k = min(W, H).toFloat()
        val boy = k * 0.062f
        val en = (W * 0.86f).toInt()
        val satirlar = baslik.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val bantlar = bant.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val o = ArrayList<Bitmap>()
        fun bantlariEkle(zemin: Int, yazi: Int) = bantlar.forEach { o += serit(it, boy * 0.85f, yazi, en, zemin = dolu(zemin)) }
        when (stil) {
            "r_haber" -> {
                // tarih bandı · renkli kutulu manşet satırları (sarı, deniz yeşili, mercan) · konum bandı
                val RENK = listOf(0xFFE3A64A.toInt() to 0xFF15100A.toInt(), 0xFF7DB8AA.toInt() to BEYAZ, 0xFFD06A5A.toInt() to BEYAZ)
                val bm = k * 0.105f
                bantlar.getOrNull(0)?.let { o += cip(it.uppercase(TR), k * 0.034f, 0xFFE3A64A.toInt(), 0xFF15100A.toInt(), true) }
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = bm; typeface = ANTON }
                val ms = if (satirlar.size > 1) satirlar.map { it.uppercase(TR) } else sar(satirlar.firstOrNull()?.uppercase(TR) ?: "", tp, W * 0.58f)
                ms.forEachIndexed { i, m -> val (z, y) = RENK[i % RENK.size]; o += kutuSatir(m, bm, z, y) }
                bantlar.getOrNull(1)?.let { o += cip(it.uppercase(TR), k * 0.036f, 0xFF161616.toInt(), BEYAZ, false) }
            }
            "r_redwhite" -> {
                satirlar.forEachIndexed { i, m -> o += if (i % 2 == 0) serit(m, boy, BEYAZ, en, zemin = dolu(KIRMIZI)) else serit(m, boy, KIRMIZI, en, zemin = dolu(BEYAZ)) }
                bantlariEkle(SIYAH, SARI)
            }
            "r_bang" -> {
                satirlar.forEachIndexed { i, m ->
                    val sb: Bitmap
                    if (i == 0) {
                        sb = serit(m, boy, SIYAH, en, solEk = boy * 1.3f) { c, rc ->
                            c.drawRect(rc, Paint().apply { color = SARI })
                            c.drawText("!!", rc.left + boy * 0.3f, rc.centerY() + boy * 0.38f, yaziBoya(boy * 1.05f, KIRMIZI))
                        }
                    } else {
                        sb = serit(m, boy, SIYAH, en, zemin = dolu(SARI))
                    }
                    o += sb
                }
                bantlariEkle(KIRMIZI, BEYAZ)
            }
            "r_green" -> {
                satirlar.forEachIndexed { i, m ->
                    o += serit(m, boy, BEYAZ, en, solEk = if (i == 0) boy * 1.1f else 0f) { c, rc ->
                        c.drawRoundRect(rc, boy * 0.15f, boy * 0.15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YESIL })
                        if (i == 0) c.drawText("“", rc.left + boy * 0.2f, rc.centerY() + boy * 0.75f, yaziBoya(boy * 1.9f, BEYAZ))
                    }
                }
                bantlariEkle(BEYAZ, YESIL)
            }
            "r_quote" -> {
                val m = satirlar.joinToString("\n")
                if (m.isNotEmpty()) o += serit(m, boy, SIYAH, en, padX = boy * 0.6f, padY = boy * 0.4f, altEk = boy * 0.5f) { c, rc ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SARI }
                    c.drawRoundRect(rc, boy * 0.5f, boy * 0.5f, p)
                    val yol = Path().apply { moveTo(rc.left + boy * 0.9f, rc.bottom - 2); lineTo(rc.left + boy * 0.7f, rc.bottom + boy * 0.5f); lineTo(rc.left + boy * 1.6f, rc.bottom - 2); close() }
                    c.drawPath(yol, p)
                }
                bantlariEkle(SIYAH, SARI)
            }
            "r_alert" -> {
                val m = satirlar.joinToString("\n")
                if (m.isNotEmpty()) o += serit(m, boy, SIYAH, en, padX = boy * 0.5f, padY = boy * 0.35f, solEk = boy * 1.3f) { c, rc ->
                    val kal = boy * 0.14f
                    c.drawRect(rc, Paint().apply { color = BEYAZ })
                    c.drawRect(RectF(rc.left + kal / 2, rc.top + kal / 2, rc.right - kal / 2, rc.bottom - kal / 2), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = kal; color = KIRMIZI })
                    val cx = rc.left + boy * 0.95f; val cy = rc.centerY()
                    c.drawCircle(cx, cy, boy * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KIRMIZI })
                    c.drawText("!", cx - boy * 0.12f, cy + boy * 0.33f, yaziBoya(boy * 0.9f, BEYAZ))
                }
                bantlariEkle(KIRMIZI, BEYAZ)
            }
            "r_dikkat" -> {
                val etiket = bantlar.firstOrNull() ?: "DİKKAT"
                o += serit(etiket.uppercase(TR), boy * 0.6f, BEYAZ, en, zemin = dolu(SIYAH))
                satirlar.forEach { o += serit(it, boy, SIYAH, en, zemin = dolu(BEYAZ)) }
                bantlar.drop(1).forEach { o += serit(it, boy * 0.85f, SIYAH, en, zemin = dolu(SARI)) }
            }
            "r_torn" -> {
                satirlar.forEach { m ->
                    o += serit(m, boy, SIYAH, en, padY = boy * 0.35f) { c, rc ->
                        val yol = Path(); val dis = boy * 0.12f; val adim = boy * 0.35f; val rnd = Random(m.hashCode())
                        yol.moveTo(rc.left, rc.top + dis)
                        var x = rc.left; while (x < rc.right) { x += adim; yol.lineTo(min(x, rc.right), rc.top + dis * rnd.nextFloat() * 2) }
                        x = rc.right; while (x > rc.left) { x -= adim; yol.lineTo(max(x, rc.left), rc.bottom - dis * rnd.nextFloat() * 2) }
                        yol.close()
                        c.drawPath(yol, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4EFE3.toInt() })
                    }
                }
                bantlar.forEach { m ->
                    o += serit(m, boy * 0.85f, SIYAH, en) { c, rc ->
                        c.drawRect(rc, Paint().apply { color = SARI })
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000; strokeWidth = boy * 0.25f }
                        var x = rc.left - rc.height(); while (x < rc.right) { c.drawLine(x, rc.bottom, x + rc.height(), rc.top, p); x += boy * 0.6f }
                    }
                }
            }
            "r_frame" -> {
                val m = satirlar.joinToString("\n")
                if (m.isNotEmpty()) o += serit(m, boy * 1.1f, SARI, en, padX = boy * 0.8f, padY = boy * 0.6f) { c, rc ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SARI; style = Paint.Style.STROKE; strokeWidth = boy * 0.14f; strokeCap = Paint.Cap.SQUARE }
                    val L = boy * 0.9f; val i = boy * 0.1f
                    c.drawRect(rc, Paint().apply { color = 0x66000000 })
                    c.drawLines(floatArrayOf(rc.left + i, rc.top + i, rc.left + i + L, rc.top + i, rc.left + i, rc.top + i, rc.left + i, rc.top + i + L,
                        rc.right - i, rc.bottom - i, rc.right - i - L, rc.bottom - i, rc.right - i, rc.bottom - i, rc.right - i, rc.bottom - i - L), p)
                }
                bantlariEkle(SARI, SIYAH)
            }
            "r_tricolor" -> {
                val renk = listOf(KIRMIZI to BEYAZ, SARI to SIYAH, SIYAH to SARI)
                (satirlar + bantlar).forEachIndexed { i, m -> val (z, y) = renk[i % 3]; o += serit(m, boy, y, en, zemin = dolu(z)) }
            }
            "r_frekans" -> {
                satirlar.forEach { m ->
                    o += serit(m, boy, SIYAH, en, solEk = boy * 0.45f) { c, rc ->
                        c.drawRect(rc, Paint().apply { color = SARI })
                        c.drawRect(RectF(rc.left, rc.top, rc.left + boy * 0.3f, rc.bottom), Paint().apply { color = KIRMIZI })
                    }
                }
                bantlariEkle(KIRMIZI, BEYAZ)
            }
            "r_alinti" -> {
                satirlar.forEachIndexed { i, m ->
                    o += serit(m, boy, SIYAH, en, solEk = if (i == 0) boy * 1.35f else 0f) { c, rc ->
                        c.drawRect(rc, Paint().apply { color = BEYAZ })
                        if (i == 0) {
                            c.drawRect(RectF(rc.left, rc.top, rc.left + boy * 1.1f, rc.bottom), Paint().apply { color = KIRMIZI })
                            c.drawText("“", rc.left + boy * 0.12f, rc.centerY() + boy * 0.8f, yaziBoya(boy * 1.8f, BEYAZ))
                        }
                    }
                }
                bantlariEkle(KIRMIZI, BEYAZ)
            }
            else -> { // r_strip
                satirlar.forEach { m ->
                    o += serit(m, boy, SIYAH, en, altEk = boy * 0.16f) { c, rc ->
                        c.drawRect(rc, Paint().apply { color = BEYAZ })
                        c.drawRect(RectF(rc.left, rc.bottom, rc.right, rc.bottom + boy * 0.16f), Paint().apply { color = KIRMIZI })
                    }
                }
                bantlariEkle(KIRMIZI, BEYAZ)
            }
        }
        return o
    }

    /** Giriş için şeritler: sola hizalı blok, seçilen konumda; her şerit 160 ms arayla gelir. */
    fun seritler(g: Giris, W: Int, H: Int): List<Serit> {
        val b = bitmapler(g.stil, g.baslik, g.bant, W, H)
        if (b.isEmpty()) return emptyList()
        val ara = min(W, H) * 0.012f
        val topH = b.sumOf { it.height.toDouble() }.toFloat() + ara * (b.size - 1)
        val solPx = W * 0.07f
        var y = when (g.konum) { "ust" -> H * 0.12f; "alt" -> H * 0.80f - topH; else -> (H - topH) / 2f }
        return b.mapIndexed { i, bm ->
            val cxPx = solPx + bm.width / 2f; val cyPx = y + bm.height / 2f
            y += bm.height + ara
            Serit(bm, (cxPx / W) * 2f - 1f, 1f - (cyPx / H) * 2f, i * 160L)
        }
    }

    /** Kapak için: şeritleri tek bitmapte birleştirir (sola hizalı). */
    fun statik(stil: String, baslik: String, bant: String, W: Int, H: Int): Bitmap? {
        val b = bitmapler(stil, baslik.replace("*", ""), bant, W, H)
        if (b.isEmpty()) return null
        val ara = (min(W, H) * 0.012f).toInt()
        val w = b.maxOf { it.width }; val h = b.sumOf { it.height } + ara * (b.size - 1)
        val o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(o)
        var y = 0f
        for (bm in b) { c.drawBitmap(bm, 0f, y, null); y += bm.height + ara }
        return o
    }

    private const val GIRIS_MS = 380L
    private const val CIKIS_MS = 320L

    private fun yumusat(f: Float) = 1f - (1f - f) * (1f - f) * (1f - f)

    /** Şeridin o andaki konumu; görünmüyorsa null. a-b: girişin görünür aralığı (ms). */
    fun an(s: Serit, hareket: String, ms: Long, a: Long, b: Long): AnKonum? {
        val gir = a + s.gecikmeMs
        if (ms < gir || ms > b) return null
        val cik = b - CIKIS_MS
        val fg = ((ms - gir).toFloat() / GIRIS_MS).coerceIn(0f, 1f)
        val fc = if (ms > cik) ((ms - cik).toFloat() / CIKIS_MS).coerceIn(0f, 1f) else 0f
        val e = yumusat(fg)
        if (fc > 0f) {   // çıkış: sağa kayarak
            val c = fc * fc
            return AnKonum(s.cx + (1f - s.cx) * c, s.cy, -c, 0f, 1f, 1f - fc * 0.3f)
        }
        return when (hareket) {
            "dus" -> { val z = if (fg < 1f) (1f - e) else 0f; AnKonum(s.cx, s.cy + (1f - s.cy) * z, 0f, -z, 1f, 1f) }
            "yuksel" -> { val z = 1f - e; AnKonum(s.cx, s.cy + (-1f - s.cy) * z, 0f, z, 1f, min(1f, fg * 2f)) }
            "buyu" -> { val sc = if (fg < 0.7f) yumusat(fg / 0.7f) * 1.08f else 1.08f - 0.08f * ((fg - 0.7f) / 0.3f); AnKonum(s.cx, s.cy, 0f, 0f, sc.coerceAtLeast(0.01f), min(1f, fg * 3f)) }
            "trend" -> { val z = 1f - e; val as1 = if (fg in 0.6f..1f) 0.04f * sin((fg - 0.6f) / 0.4f * PI.toFloat()) else 0f
                AnKonum((s.cx + (-1f - s.cx) * z + as1).coerceIn(-1f, 1f), s.cy, z, 0f, 1f + 0.1f * z, 1f) }
            else -> { val z = 1f - e; AnKonum(s.cx + (-1f - s.cx) * z, s.cy, z, 0f, 1f, 1f) }   // kay
        }
    }

    /** Efekt sesi anları (ms, tür) — girişte her şerit için, çıkışta bir kez. */
    fun sesAnlari(g: Giris, seritSayisi: Int, a: Long, b: Long): List<Pair<Long, String>> {
        if (!g.efektSes || seritSayisi == 0) return emptyList()
        val tur = when (g.hareket) { "dus" -> "tok"; "buyu" -> "pop"; else -> "vuus" }
        return (0 until seritSayisi).map { (a + it * 160L) to tur } + ((b - CIKIS_MS) to "vuus")
    }
}

/** Reels şeridi bindirmesi (klibin efektlerinde). */
class ReelsBindirme(private val s: Serit, private val hareket: String, private val klipBasMs: Long, private val a: Long, private val b: Long) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    override fun getBitmap(presentationTimeUs: Long): Bitmap = s.bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = klipBasMs + saat.yerelMs(presentationTimeUs)
        val k = Reels.an(s, hareket, ms, a, b) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(k.zx.coerceIn(-1f, 1f), k.zy.coerceIn(-1f, 1f))
            .setOverlayFrameAnchor(k.kx.coerceIn(-1f, 1f), k.ky.coerceIn(-1f, 1f)).setScale(k.olcek, k.olcek).setAlphaScale(k.alfa.coerceIn(0f, 1f)).build()
    }
}

/** Işıklı boncuklar: girişte yavaşça yükselen, parlayan ışık noktaları. Konumlar zamanın saf işlevi (önizleme = çıktı). */
object Boncuk {
    class Nokta(val x: Float, val y: Float, val r: Float, val alfa: Float, val renk: Int)
    private val tohum = (0 until 26).map { i -> val r = Random(i * 7919 + 13); floatArrayOf(r.nextFloat(), r.nextFloat(), 0.012f + r.nextFloat() * 0.03f, 0.04f + r.nextFloat() * 0.08f, r.nextFloat() * 6.28f, r.nextFloat()) }
    private val RENKLER = intArrayOf(0xFFFFE6A8.toInt(), 0xFFFFC46B.toInt(), 0xFFFFF4D6.toInt(), 0xFFA8E8FF.toInt())

    /** ms: girişin başından beri; solma: genel görünürlük. Konumlar 0…1, yarıçap kısa kenara oran. */
    fun noktalar(ms: Long, solma: Float): List<Nokta> {
        val t = ms / 1000f
        return tohum.mapIndexed { i, s ->
            val y = ((s[1] - t * s[3]) % 1.2f + 1.2f) % 1.2f - 0.1f
            val x = s[0] + 0.02f * sin(t * 0.8f + s[4])
            val parla = 0.55f + 0.45f * sin(t * (1.5f + s[5]) + s[4])
            Nokta(x, y, s[2], (parla * solma).coerceIn(0f, 1f), RENKLER[i % RENKLER.size])
        }
    }

    fun ciz(c: Canvas, w: Int, h: Int, noktalar: List<Nokta>) {
        val k = min(w, h).toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (n in noktalar) {
            if (n.alfa <= 0.01f) continue
            val r = n.r * k
            val a = (n.alfa * 255).toInt()
            val renk = (a shl 24) or (n.renk and 0xFFFFFF)
            p.shader = RadialGradient(n.x * w, n.y * h, r, intArrayOf(renk, (renk and 0xFFFFFF) or ((a / 3) shl 24), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(n.x * w, n.y * h, r, p)
        }
    }
}

/** Boncuk bindirmesi: yarım çözünürlükte çizilip 2 kat büyütülür (hafif). */
class BoncukBindirme(private val klipBasMs: Long, private val a: Long, private val b: Long, W: Int, H: Int) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    private val w = (W / 2).coerceAtLeast(2); private val h = (H / 2).coerceAtLeast(2)
    private val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    private val bos = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val ciz = Canvas(bmp)
    private var sonMs = Long.MIN_VALUE
    private var gorunur = false
    private fun hazirla(t: Long) {
        val ms = klipBasMs + saat.yerelMs(t)
        if (ms == sonMs) return
        sonMs = ms
        val s = Zaman.solma(ms, a, b, 600)
        gorunur = s > 0f
        if (!gorunur) return
        bmp.eraseColor(0)
        Boncuk.ciz(ciz, w, h, Boncuk.noktalar(ms - a, s))
        bmp.prepareToDraw()
    }
    // her karede aynı nesne yeniden çizildiği için generationId değişir; Media3 dokuyu yeniler
    override fun getBitmap(presentationTimeUs: Long): Bitmap { hazirla(presentationTimeUs); return if (gorunur) bmp else bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        hazirla(presentationTimeUs)
        return if (!gorunur) StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        else StaticOverlaySettings.Builder().setScale(2f, 2f).build()
    }
}

/** Kısa efekt sesleri: telif sorunu olmasın diye uygulama içinde sentezlenir (WAV, 44,1 kHz mono). */
object Sfx {
    private fun yaz(f: File, ornek: ShortArray) {
        DataOutputStream(FileOutputStream(f).buffered()).use { o ->
            fun i32(v: Int) { o.write(v and 255); o.write(v shr 8 and 255); o.write(v shr 16 and 255); o.write(v shr 24 and 255) }
            fun i16(v: Int) { o.write(v and 255); o.write(v shr 8 and 255) }
            val veri = ornek.size * 2
            o.writeBytes("RIFF"); i32(36 + veri); o.writeBytes("WAVE"); o.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(44100); i32(88200); i16(2); i16(16)
            o.writeBytes("data"); i32(veri)
            for (s in ornek) i16(s.toInt())
        }
    }

    fun dosya(ctx: Context, tur: String): File {
        val f = File(ctx.cacheDir, "sfx_$tur.wav")
        if (f.exists() && f.length() > 100) return f
        val sr = 44100
        val ornek: ShortArray = when (tur) {
            "pop" -> { val n = sr * 110 / 1000; ShortArray(n) { i -> val t = i.toFloat() / sr; val fr = 900f - 500f * (i.toFloat() / n); (sin(2 * PI * fr * t).toFloat() * (1f - i.toFloat() / n).let { it * it } * 26000).toInt().toShort() } }
            "tok" -> { val n = sr * 70 / 1000; val r = Random(3); ShortArray(n) { i -> val e = (1f - i.toFloat() / n).let { it * it * it }; ((sin(2 * PI * 180f * i / sr).toFloat() * 0.7f + (r.nextFloat() - 0.5f) * 0.6f) * e * 28000).toInt().toShort() } }
            else -> { // vuus: süzgeçli gürültü, yükselip alçalan
                val n = sr * 360 / 1000; val r = Random(7); var y = 0f
                ShortArray(n) { i -> val f0 = i.toFloat() / n; val zarf = sin(PI.toFloat() * f0); val k = 0.05f + 0.35f * f0
                    y += k * ((r.nextFloat() * 2 - 1) - y); (y * zarf * 30000).toInt().coerceIn(-32000, 32000).toShort() }
            }
        }
        yaz(f, ornek)
        return f
    }
}

package app.studyo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 3.60: giriş başlıklarının profesyonel çizimi — web Kareden Videoya'daki drawReels / drawIntroBody'nin birebir aktarımı
 *  (aynı yazı tipleri: Barlow Condensed, Montserrat, Anton, Archivo, Bebas Neue, Playfair Display — hepsi SIL OFL, res/font).
 *  Kök neden (önceki sürüm): Anton dışında telefonun sistem yazısı (Roboto Black), gevşek iç boşluklar, gölgesiz kutular ve
 *  basitleştirilmiş süsler (tırnak, köşe, ünlem) — başlıklar amatör görünüyordu. */
object Yazilar {
    private fun yukle(id: Int, yedek: Typeface): Typeface = try { androidx.core.content.res.ResourcesCompat.getFont(Depo.app, id) ?: yedek } catch (_: Throwable) { yedek }
    private val YEDEK_DAR = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val YEDEK_AGIR = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val barlow600 by lazy { yukle(R.font.barlow_condensed_600, YEDEK_DAR) }
    val barlow700 by lazy { yukle(R.font.barlow_condensed_700, YEDEK_DAR) }
    val barlow800 by lazy { yukle(R.font.barlow_condensed_800, YEDEK_DAR) }
    val mont600 by lazy { yukle(R.font.montserrat_600, YEDEK_AGIR) }
    val mont700 by lazy { yukle(R.font.montserrat_700, YEDEK_AGIR) }
    val mont800 by lazy { yukle(R.font.montserrat_800, YEDEK_AGIR) }
    val mont900 by lazy { yukle(R.font.montserrat_900, YEDEK_AGIR) }
    val mont900i by lazy { yukle(R.font.montserrat_900_italik, Typeface.create(YEDEK_AGIR, Typeface.ITALIC)) }
    val anton by lazy { yukle(R.font.anton, YEDEK_DAR) }
    val archivo600 by lazy { yukle(R.font.archivo_600, Typeface.DEFAULT_BOLD) }
    val archivo800 by lazy { yukle(R.font.archivo_800, YEDEK_AGIR) }
    val bebas by lazy { yukle(R.font.bebas_neue, YEDEK_DAR) }
    val playfair by lazy { yukle(R.font.playfair_700, Typeface.create(Typeface.SERIF, Typeface.BOLD)) }
    val tirnak: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    /** Web'deki aile + ağırlık eşlemesi: COND = Barlow Condensed, GEO = Montserrat, ANTON. */
    fun cond(w: Int) = when { w >= 800 -> barlow800; w >= 700 -> barlow700; else -> barlow600 }
    fun geo(w: Int, italik: Boolean = false) = if (italik) mont900i else when { w >= 900 -> mont900; w >= 800 -> mont800; w >= 700 -> mont700; else -> mont600 }
}

/** Başlığın bir parçası: kutusu (piksel, W×H karede), çizimi (mutlak koordinatlarla), sırası ve giriş biçimi. */
class BaslikOge(val x: Float, val y: Float, val w: Float, val h: Float, val pop: Boolean, val yon: Int, val ciz: (Canvas) -> Unit)

object ReelsCiz {
    const val RED = 0xFFE3101B.toInt(); const val YEL = 0xFFFFD60A.toInt(); const val GRN = 0xFF43A98A.toInt(); const val INK = 0xFF111111.toInt()
    const val BEYAZ = 0xFFFFFFFF.toInt()

    // ---------------- web yardımcılarının karşılıkları ----------------
    private fun boya(tf: Typeface, s: Float, renk: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = max(6f, s); color = renk }
    private fun bol(text: String) = text.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    private fun sar(p: Paint, text: String, maxW: Float, maxL: Int): List<String> {
        val o = ArrayList<String>(); var s = ""
        for (k in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) { val t = if (s.isEmpty()) k else "$s $k"; if (p.measureText(t) > maxW && s.isNotEmpty()) { o += s; s = k } else s = t }
        if (s.isNotEmpty()) o += s
        return o.take(maxL)
    }
    class Sig(val fs: Float, val lines: List<String>, val ws: List<Float>)
    /** rlFit: "|" ile elle satır, yoksa otomatik sar; satır sayısı ve genişliğe göre küçült. */
    private fun sigdir(text: String, tf: (Float) -> Typeface, fs0: Float, maxW: Float, maxL: Int): Sig {
        val ex = bol(text); var fs = fs0; var lines = ex
        for (n in 0 until 60) {
            val p = boya(tf(fs), fs)
            lines = if (ex.size > 1) ex else sar(p, ex.firstOrNull() ?: "", maxW, 99)
            if (lines.size <= max(maxL, ex.size) && lines.all { p.measureText(it) <= maxW }) break
            fs *= 0.94f; if (fs < 10f) break
        }
        val p = boya(tf(fs), fs)
        if (ex.size <= 1 && lines.size == 1 && maxL > 1) {
            val ws = lines[0].split(Regex("\\s+"))
            if (ws.size >= 4 && p.measureText(lines[0]) > maxW * 0.62f) { val h = ceil(ws.size / 2f).toInt(); lines = listOf(ws.take(h).joinToString(" "), ws.drop(h).joinToString(" ")) }
        }
        return Sig(fs, lines, lines.map { p.measureText(it) })
    }
    private fun golgeli(p: Paint, fs: Float, acik: Boolean) { if (acik) p.setShadowLayer(fs * 0.11f, 0f, fs * 0.06f, 0x47000000) else p.clearShadowLayer() }
    private fun dolgu(c: Canvas, x: Float, y: Float, w: Float, h: Float, renk: Int, golgeFs: Float = 0f, r: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = renk }; if (golgeFs > 0f) golgeli(p, golgeFs, true)
        if (r > 0f) c.drawRoundRect(RectF(x, y, x + w, y + h), min(r, min(w, h) / 2), min(r, min(w, h) / 2), p) else c.drawRect(x, y, x + w, y + h, p)
    }
    private fun cerceve(c: Canvas, x: Float, y: Float, w: Float, h: Float, renk: Int, kal: Float, r: Float = 0f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = renk; style = Paint.Style.STROKE; strokeWidth = kal }
        if (r > 0f) c.drawRoundRect(RectF(x, y, x + w, y + h), min(r, min(w, h) / 2), min(r, min(w, h) / 2), p) else c.drawRect(x, y, x + w, y + h, p)
    }
    /** rlText: yazı (sola / ortaya), isteğe bağlı gölge (sh). */
    private fun yaz(c: Canvas, s: String, x: Float, y: Float, tf: Typeface, fs: Float, renk: Int, orta: Boolean = false, sh: Float = 0f) {
        val p = boya(tf, fs, renk).apply { textAlign = if (orta) Paint.Align.CENTER else Paint.Align.LEFT }
        if (sh > 0f) p.setShadowLayer(sh * 0.5f, 0f, sh * 0.45f, 0x61000000)
        c.drawText(s, x, y, p)
    }
    private fun tirnak(c: Canvas, cx: Float, cy: Float, boy: Float, renk: Int) {
        val p = boya(Yazilar.tirnak, boy, renk).apply { textAlign = Paint.Align.CENTER }
        c.drawText("“", cx, cy + boy * 0.62f, p)
    }
    private fun kose(c: Canvas, x: Float, y: Float, arm: Float, th: Float, dx: Int, dy: Int, renk: Int) {
        val p = Paint().apply { color = renk }
        c.drawRect(if (dx > 0) x else x - arm, if (dy > 0) y else y - th, (if (dx > 0) x else x - arm) + arm, (if (dy > 0) y else y - th) + th, p)
        c.drawRect(if (dx > 0) x else x - th, if (dy > 0) y else y - arm, (if (dx > 0) x else x - th) + th, (if (dy > 0) y else y - arm) + arm, p)
    }
    private fun rnd(i: Int, k: Int): Float { val v = sin(i * 127.1 + k * 311.7) * 43758.5453; return (v - floor(v)).toFloat() }
    private fun yirtik(x: Float, y: Float, w: Float, h: Float, amp: Float): Path {
        val st = max(4f, amp * 2.4f); var s = 0
        fun j() = (rnd(s++, 7) - 0.5f) * 2f * amp
        val yol = Path(); var ilk = true
        fun e(px: Float, py: Float) { if (ilk) { yol.moveTo(px, py); ilk = false } else yol.lineTo(px, py) }
        var u = 0f; while (u <= w) { e(x + u, y + j()); u += st }
        var v = st; while (v <= h) { e(x + w + j() * 0.6f, y + v); v += st * 1.4f }
        u = w; while (u >= 0f) { e(x + u, y + h + j()); u -= st }
        v = h - st; while (v > 0f) { e(x + j() * 0.6f, y + v); v -= st * 1.4f }
        yol.close(); return yol
    }
    /** 2. vurgu bandı rengi (web BR): alt = false → zemin + yazı. Android'de tek bant listesi (hepsi 1. grup). */
    private fun br(bg: Int): Pair<Int, Int> = bg to (if (bg == YEL) INK else BEYAZ)

    /** Stilin parçaları. cyOran: dikey merkez (0…1). olcek: yazı boyutu çarpanı. */
    fun ogeler(stil: String, baslik: String, bant: String, W: Int, H: Int, olcek: Float = 1f, cyOran: Float = -1f): List<BaslikOge> {
        val Wf = W.toFloat(); val Hf = H.toFloat(); val P = W < H
        val title = baslik.replace("*", "").trim()
        val bands = bol(bant)
        val fs0 = (if (P) Wf * 0.105f else Hf * 0.098f) * olcek.coerceIn(0.5f, 2f)
        val maxW = if (P) Wf * 0.8f else Wf * 0.58f
        val CY = Hf * (if (cyOran >= 0f) cyOran else if (P) 0.47f else 0.5f)
        val liste = ArrayList<BaslikOge>()
        fun add(pop: Boolean, yon: Int, x: Float, y: Float, w: Float, h: Float, f: (Canvas) -> Unit) { liste += BaslikOge(x, y, w, h, pop, yon, f) }
        val cond = { w: Int -> { _: Float -> Yazilar.cond(w) } }
        val geo = { w: Int -> { _: Float -> Yazilar.geo(w) } }

        when (stil) {
            "r_strip", "r_redwhite", "r_bang", "r_green" -> {
                val F = sigdir(title, cond(700), fs0, maxW, 3); val fs = F.fs; val padX = fs * 0.26f; val sh = fs * 1.14f; val gap = fs * 0.1f
                val yb = stil == "r_redwhite"
                val bf = bands.map { b -> sigdir(b, cond(if (yb) 600 else 700), fs * (if (yb) 0.7f else 0.78f), maxW * 1.06f, 1) }
                val bH = bf.map { b -> b.fs * 1.4f }; val bGap = fs * (if (stil == "r_green") 0.3f else 0.22f)
                val stripsH = F.lines.size * (sh + gap) - gap
                val bandsH = if (bf.isNotEmpty()) bGap + bH.sumOf { h -> (h + fs * 0.12f).toDouble() }.toFloat() - fs * 0.12f else 0f
                val blockW = (F.ws.maxOrNull() ?: 0f) + padX * 2
                val lift = if (stil == "r_bang" || stil == "r_green") fs * 0.5f else 0f
                val top = CY - (stripsH + bandsH) / 2 + lift
                val x0 = (Wf - blockW) / 2 + when (stil) { "r_strip" -> fs * 0.15f; "r_bang" -> fs * 0.3f; else -> 0f }
                if (stil == "r_strip") { val bx = x0 - fs * 0.36f; val bw = fs * 0.2f; val by = top - fs * 0.14f; val bh = stripsH + fs * 0.28f
                    add(false, -1, bx, by, bw, bh) { c -> dolgu(c, bx, by, bw, bh, RED) } }
                if (stil == "r_bang") { val bw = fs * 1.3f; val bx = x0 - bw * 0.7f; val by = top - fs * 0.05f; val bh = stripsH + fs * 0.45f
                    add(true, -1, bx, by - fs * 1.9f, bw, bh + fs * 1.9f) { c ->
                        val yol = Path().apply { moveTo(bx + bw * 0.3f, by); lineTo(bx + bw, by); lineTo(bx + bw * 0.7f, by + bh); lineTo(bx, by + bh); close() }
                        c.drawPath(yol, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = YEL })
                        c.save(); c.translate(bx + bw * 0.45f, by + fs * 0.05f); c.rotate((-0.12f * 180f / PI).toFloat())
                        val p = boya(Yazilar.anton, fs * 2.3f, YEL).apply { textAlign = Paint.Align.CENTER }
                        val k = TextPaint(p).apply { style = Paint.Style.STROKE; strokeWidth = fs * 0.06f; color = 0x2E000000 }
                        c.drawText("!!", 0f, 0f, k); c.drawText("!!", 0f, 0f, p); c.restore()
                    } }
                if (stil == "r_green") { val s = fs * 1.05f; val qx = x0 - fs * 0.12f; val qy = top - s * 0.92f
                    add(true, -1, qx, qy, s, s) { c -> dolgu(c, qx, qy, s, s, GRN, fs); tirnak(c, qx + s / 2, qy + s / 2, s * 1.25f, BEYAZ) } }
                F.lines.forEachIndexed { i, ln ->
                    val y = top + i * (sh + gap); val w = F.ws[i] + padX * 2; val red = yb && i == 0
                    add(false, if (i % 2 == 1) 1 else -1, x0, y, w, sh) { c ->
                        dolgu(c, x0, y, w, sh, if (red) RED else BEYAZ, fs)
                        yaz(c, ln, x0 + padX, y + sh / 2 + fs * 0.34f, Yazilar.cond(700), fs, if (red) BEYAZ else INK)
                    }
                }
                var by = top + stripsH + bGap
                bf.forEachIndexed { j, b ->
                    val pad = b.fs * 0.45f; val bw = b.ws[0] + pad * 2; val bh = bH[j]; val y = by
                    val bx0 = if (yb) x0 + blockW - bw + fs * 0.1f else if (stil == "r_green") min(Wf - bw - Wf * 0.03f, x0 + blockW - bw * 0.85f) else x0 - fs * 0.22f
                    val bx = max(Wf * 0.03f, min(Wf - bw - Wf * 0.03f, bx0))
                    add(false, if (yb) 1 else -1, bx, y, bw, bh) { c ->
                        val (bgc, fgc) = br(if (yb) YEL else RED)
                        dolgu(c, bx, y, bw, bh, bgc, fs)
                        if (stil == "r_green") cerceve(c, bx, y, bw, bh, BEYAZ, max(2f, fs * 0.05f))
                        yaz(c, b.lines[0], bx + bw / 2, y + bh / 2 + b.fs * 0.34f, Yazilar.cond(if (yb) 600 else 700), b.fs, fgc, true, if (fgc == INK) 0f else b.fs * 0.08f)
                    }
                    if (stil == "r_green" && j == bf.lastIndex) { val r = b.fs * 0.48f; val qcx = bx + bw / 2; val qcy = y + bh + r * 0.45f
                        add(true, 1, qcx - r, qcy - r, r * 2, r * 2) { c ->
                            c.drawCircle(qcx, qcy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
                            c.drawCircle(qcx, qcy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BEYAZ; style = Paint.Style.STROKE; strokeWidth = max(2f, fs * 0.05f) })
                            tirnak(c, qcx, qcy, r * 2, BEYAZ)
                        } }
                    by += bh + fs * 0.12f
                }
            }
            "r_quote", "r_alert" -> {
                val q = stil == "r_quote"
                val F = sigdir(title, cond(800), fs0 * 1.04f, maxW * 0.84f, 3); val fs = F.fs; val pad = fs * 0.55f; val lh = fs * 1.08f
                val bw = (F.ws.maxOrNull() ?: 0f) + pad * 2.2f; val bh = F.lines.size * lh + pad * 1.35f + fs * 0.15f; val r = fs * (if (q) 0.62f else 0.68f)
                val bf = bands.map { b -> sigdir(b, cond(if (q) 800 else 700), fs * (if (q) 0.62f else 0.88f), maxW * 1.05f, 1) }
                val bH = bf.map { b -> b.fs * (if (q) 1.55f else 1.3f) }
                val bandsH = if (bf.isNotEmpty()) fs * 0.05f + bH.sumOf { h -> (h + fs * 0.1f).toDouble() }.toFloat() else 0f
                val top = CY - (bh + bandsH) / 2 + r * 0.5f; val bx = (Wf - bw) / 2
                add(true, -1, bx, top, bw, bh) { c ->
                    dolgu(c, bx, top, bw, bh, if (q) YEL else BEYAZ, fs, if (q) fs * 0.42f else fs * 0.1f)
                    val k = fs * (if (q) 0.1f else 0.13f)
                    cerceve(c, bx, top, bw, bh, if (q) INK else RED, k, if (q) fs * 0.42f else fs * 0.1f)
                    val ct = top + (bh - F.lines.size * lh) / 2 + fs * 0.12f
                    F.lines.forEachIndexed { i, ln -> yaz(c, ln, Wf / 2, ct + i * lh + lh / 2 + fs * 0.34f, Yazilar.cond(800), fs, INK, true) }
                }
                add(true, 1, Wf / 2 - r, top - r, r * 2, r * 2) { c ->
                    c.drawCircle(Wf / 2, top, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (q) YEL else RED })
                    c.drawCircle(Wf / 2, top, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (q) INK else BEYAZ; style = Paint.Style.STROKE; strokeWidth = fs * (if (q) 0.09f else 0.1f) })
                    if (q) tirnak(c, Wf / 2, top, r * 2, INK) else yaz(c, "!!", Wf / 2, top + r * 0.46f, Yazilar.anton, r * 1.3f, BEYAZ, true)
                }
                var y = top + bh + (if (q) -fs * 0.05f else fs * 0.1f)
                bf.forEachIndexed { j, b ->
                    val p2 = b.fs * 0.55f; val w2 = b.ws[0] + p2 * 2; val h2 = bH[j]; val x2 = min(Wf - w2 - Wf * 0.02f, (Wf - w2) / 2 + (if (q) 0f else fs * 0.5f)); val yy = y
                    add(false, 1, x2, yy, w2, h2) { c ->
                        val (bgc, fgc) = br(RED)
                        dolgu(c, x2, yy, w2, h2, bgc, fs, if (q) h2 * 0.18f else 0f)
                        if (q) {
                            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (bgc == YEL) RED else YEL; style = Paint.Style.STROKE; strokeWidth = max(1.5f, fs * 0.05f)
                                pathEffect = DashPathEffect(floatArrayOf(fs * 0.07f, fs * 0.1f), 0f) }
                            val rr = h2 * 0.12f
                            c.drawRoundRect(RectF(x2 + fs * 0.09f, yy + fs * 0.09f, x2 + w2 - fs * 0.09f, yy + h2 - fs * 0.09f), rr, rr, p)
                        }
                        yaz(c, b.lines[0], if (q) x2 + w2 / 2 else x2 + p2, yy + h2 / 2 + b.fs * 0.34f, Yazilar.cond(if (q) 800 else 700), b.fs, fgc, q, if (fgc == INK) 0f else b.fs * 0.08f)
                    }
                    y += h2 + fs * 0.1f
                }
            }
            "r_dikkat" -> {
                val label = (bands.firstOrNull() ?: "DİKKAT").uppercase(TR); val rest = bands.drop(1)
                val F = sigdir(title, cond(800), fs0, maxW * 0.9f, 3); val fs = F.fs; val pad = fs * 0.55f; val lh = fs * 1.1f
                val L = sigdir(label, cond(800), fs * 1.08f, maxW * 0.7f, 1); val lw = L.ws[0] + L.fs * 0.9f; val lH = L.fs * 1.32f
                val bw = min(Wf * 0.95f, max((F.ws.maxOrNull() ?: 0f) + pad * 2, if (P) Wf * 0.84f else Wf * 0.56f)); val bh = F.lines.size * lh + pad * 1.2f + lH * 0.25f
                val rf = rest.map { b -> sigdir(b, cond(700), fs * 0.8f, maxW, 1) }
                val restH = rf.sumOf { b -> (b.fs * 1.35f + fs * 0.12f).toDouble() }.toFloat()
                val top = CY - (lH * 0.75f + bh + restH) / 2 + lH * 0.75f; val bx = (Wf - bw) / 2
                add(false, -1, bx, top, bw, bh) { c ->
                    dolgu(c, bx, top, bw, bh, YEL, fs)
                    val ct = top + lH * 0.25f + (bh - lH * 0.25f - F.lines.size * lh) / 2
                    F.lines.forEachIndexed { i, ln -> yaz(c, ln, Wf / 2, ct + i * lh + lh / 2 + fs * 0.34f, Yazilar.cond(800), fs, INK, true) }
                }
                val lx = (Wf - lw) / 2; val ly = top - lH * 0.75f
                add(true, 1, lx, ly, lw, lH) { c ->
                    dolgu(c, lx, ly, lw, lH, INK, fs); cerceve(c, lx, ly, lw, lH, BEYAZ, max(2f, fs * 0.07f))
                    yaz(c, label, Wf / 2, ly + lH / 2 + L.fs * 0.36f, Yazilar.cond(800), L.fs, BEYAZ, true)
                }
                var y = top + bh + fs * 0.15f
                rf.forEachIndexed { j, b -> val w2 = b.ws[0] + b.fs * 1.0f; val h2 = b.fs * 1.35f; val x2 = (Wf - w2) / 2; val yy = y
                    add(false, if (j % 2 == 1) -1 else 1, x2, yy, w2, h2) { c -> val (bgc, fgc) = br(RED); dolgu(c, x2, yy, w2, h2, bgc)
                        yaz(c, b.lines[0], Wf / 2, yy + h2 / 2 + b.fs * 0.34f, Yazilar.cond(700), b.fs, fgc, true, if (fgc == INK) 0f else b.fs * 0.08f) }
                    y += h2 + fs * 0.12f }
            }
            "r_torn" -> {
                val F = sigdir(title, cond(800), fs0 * 1.08f, maxW * 0.82f, 3); val fs = F.fs; val pad = fs * 0.6f; val lh = fs * 1.08f
                val pw = (F.ws.maxOrNull() ?: 0f) + pad * 2.2f; val ph = F.lines.size * lh + pad * 1.3f; val px = (Wf - pw) / 2
                val bf = bands.map { b -> sigdir(b, cond(800), fs * 0.76f, maxW * 1.02f, 1) }; val bH = bf.map { b -> b.fs * 1.38f }
                val bandsH = if (bf.isNotEmpty()) fs * 0.9f + bH.sumOf { h -> (h + fs * 0.1f).toDouble() }.toFloat() + fs * 0.35f else 0f
                val top = CY - (ph + bandsH) / 2
                add(true, -1, px, top, pw, ph) { c ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BEYAZ }; golgeli(p, fs, true)
                    c.drawPath(yirtik(px, top, pw, ph, fs * 0.11f), p)
                    val ct = top + (ph - F.lines.size * lh) / 2
                    F.lines.forEachIndexed { i, ln -> yaz(c, ln, Wf / 2, ct + i * lh + lh / 2 + fs * 0.34f, Yazilar.cond(800), fs, INK, true) }
                }
                if (bf.isNotEmpty()) {
                    val maxB = bf.maxOf { b -> b.ws[0] + b.fs * 1.1f }; val rw = min(maxB * 0.62f, Wf * 0.5f); val rh = max(2f, fs * 0.08f)
                    var y = top + ph + fs * 0.55f; val ry1 = y
                    add(false, -1, (Wf - rw) / 2, ry1, rw, rh) { c -> dolgu(c, (Wf - rw) / 2, ry1, rw, rh, BEYAZ) }
                    y += fs * 0.35f
                    bf.forEachIndexed { j, b -> val w2 = b.ws[0] + b.fs * 1.1f; val h2 = bH[j]; val x2 = (Wf - w2) / 2; val yy = y
                        add(false, if (j % 2 == 1) -1 else 1, x2, yy, w2, h2) { c -> val (bgc, fgc) = br(RED); dolgu(c, x2, yy, w2, h2, bgc, fs)
                            yaz(c, b.lines[0], Wf / 2, yy + h2 / 2 + b.fs * 0.34f, Yazilar.cond(800), b.fs, fgc, true, if (fgc == INK) 0f else b.fs * 0.1f) }
                        y += h2 + fs * 0.1f }
                    val ry2 = y + fs * 0.25f
                    add(false, 1, (Wf - rw) / 2, ry2, rw, rh) { c -> dolgu(c, (Wf - rw) / 2, ry2, rw, rh, BEYAZ) }
                }
            }
            "r_frame" -> {
                val F = sigdir(title, geo(900), fs0 * 1.12f, maxW * 0.8f, 3); val fs = F.fs; val lh = fs * 1.0f
                val tw = F.ws.maxOrNull() ?: 0f; val th = F.lines.size * lh; val bx = Wf / 2 - tw / 2 - fs * 0.55f; val bw = tw + fs * 1.1f; val bh = th + fs * 0.75f
                val bf = bands.map { b -> sigdir(b, geo(800), fs * 0.62f, maxW * 0.98f, 1) }; val bH = bf.map { b -> b.fs * 1.6f }
                val bandsH = if (bf.isNotEmpty()) fs * 0.35f + bH.sumOf { h -> (h + fs * 0.06f).toDouble() }.toFloat() else 0f
                val by = CY - (bh + bandsH) / 2; val arm = fs * 1.05f; val cth = fs * 0.17f
                add(true, 1, bx, by, bw, bh) { c -> kose(c, bx, by, arm, cth, 1, 1, RED); kose(c, bx + bw, by, arm, cth, -1, 1, RED); kose(c, bx, by + bh, arm, cth, 1, -1, RED); kose(c, bx + bw, by + bh, arm, cth, -1, -1, RED) }
                F.lines.forEachIndexed { i, ln -> val cy = by + fs * 0.4f + i * lh + lh / 2
                    add(false, if (i % 2 == 1) 1 else -1, Wf / 2 - F.ws[i] / 2, cy - lh / 2, F.ws[i], lh) { c ->
                        c.save(); c.translate(Wf / 2, cy); c.rotate((-0.07f * 180f / PI).toFloat())
                        val p = boya(Yazilar.geo(900), fs, 0xFFF2EA12.toInt()).apply { textAlign = Paint.Align.CENTER }
                        val k = TextPaint(p).apply { style = Paint.Style.STROKE; strokeWidth = fs * 0.17f; strokeJoin = Paint.Join.ROUND; color = INK }
                        c.drawText(ln, 0f, fs * 0.36f, k); c.drawText(ln, 0f, fs * 0.36f, p); c.restore()
                    } }
                var y = by + bh + fs * 0.35f
                bf.forEachIndexed { j, b -> val w2 = b.ws[0] + b.fs * 1.3f; val h2 = bH[j]; val x2 = (Wf - w2) / 2 + (if (j % 2 == 1) -1 else 1) * fs * 0.18f; val yy = y
                    add(false, if (j % 2 == 1) 1 else -1, x2, yy, w2, h2) { c -> val (bgc, fgc) = br(INK); dolgu(c, x2, yy, w2, h2, bgc, fs); cerceve(c, x2, yy, w2, h2, 0xD9FFFFFF.toInt(), max(1.5f, fs * 0.04f))
                        yaz(c, b.lines[0], x2 + w2 / 2, yy + h2 / 2 + b.fs * 0.36f, Yazilar.geo(800), b.fs, fgc, true) }
                    y += h2 + fs * 0.06f }
            }
            "r_tricolor" -> {
                val F = sigdir(title, geo(900), fs0 * 0.98f, maxW * 0.9f, 3); val fs = F.fs; val pad = fs * 0.4f; val lh = fs * 1.3f
                val pI = boya(Yazilar.mont900i, fs)
                val ws = F.lines.mapIndexed { i, l -> if (i % 3 == 1) pI.measureText(l) * 1.03f else F.ws[i] }
                val bw = min(Wf * 0.96f, (ws.maxOrNull() ?: 0f) + pad * 2); val th = F.lines.size * lh
                val bf = if (bands.isNotEmpty()) sigdir(bands.joinToString(" "), geo(600), fs * 0.42f, maxW * 0.8f, 2) else null
                val bHh = if (bf != null) bf.lines.size * bf.fs * 1.25f + bf.fs * 0.7f else 0f
                val top = CY - (th + (if (bf != null) fs * 0.9f + bHh else 0f)) / 2; val bx = (Wf - bw) / 2
                val C = listOf(Triple(RED, BEYAZ, false), Triple(YEL, INK, true), Triple(INK, BEYAZ, false))
                F.lines.forEachIndexed { i, ln -> val cc = C[i % 3]; val y = top + i * lh
                    add(false, if (i % 2 == 1) 1 else -1, bx, y, bw, lh) { c -> dolgu(c, bx, y, bw, lh, cc.first)
                        yaz(c, ln, Wf / 2, y + lh / 2 + fs * 0.36f, if (cc.third) Yazilar.mont900i else Yazilar.geo(900), fs, cc.second, true) } }
                if (bf != null) { val w2 = (bf.ws.maxOrNull() ?: 0f) + bf.fs * 1.4f; val x2 = (Wf - w2) / 2; val y2 = top + th + fs * 0.9f
                    add(true, 1, x2, y2, w2, bHh) { c -> dolgu(c, x2, y2, w2, bHh, YEL, fs, bf.fs * 0.35f)
                        bf.lines.forEachIndexed { i, l -> yaz(c, l, Wf / 2, y2 + bf.fs * 0.35f + i * bf.fs * 1.25f + bf.fs * 0.95f, Yazilar.geo(600), bf.fs, INK, true) } } }
            }
            "r_frekans" -> {
                val F = sigdir(title, geo(800), fs0 * 0.8f, maxW * 0.84f, 3); val fs = F.fs; val pad = fs * 0.75f; val lh = fs * 1.28f
                val bw = (F.ws.maxOrNull() ?: 0f) + pad * 2; val bh = F.lines.size * lh + pad * 1.25f; val bx = (Wf - bw) / 2
                val bf = bands.map { b -> sigdir(b, cond(800), fs * 1.15f, maxW * 0.95f, 1) }; val bH = bf.map { b -> b.fs * 1.3f }
                val bandsH = if (bf.isNotEmpty()) fs * 0.9f + bH.sumOf { h -> (h + fs * 0.12f).toDouble() }.toFloat() else 0f
                val top = CY - (bh + bandsH) / 2 + fs * 0.3f
                add(false, -1, bx, top, bw, bh) { c -> dolgu(c, bx, top, bw, bh, YEL, fs, fs * 0.6f)
                    val ct = top + (bh - F.lines.size * lh) / 2
                    F.lines.forEachIndexed { i, ln -> yaz(c, ln, Wf / 2, ct + i * lh + lh / 2 + fs * 0.36f, Yazilar.geo(800), fs, INK, true) } }
                val tw = fs * 1.75f; val tH = fs * 1.05f; val tx = bx - fs * 0.05f; val ty = top - tH * 0.55f
                add(true, -1, tx, ty, tw, tH) { c -> dolgu(c, tx, ty, tw, tH, RED, fs); tirnak(c, tx + tw / 2, ty + tH / 2, tH * 1.5f, BEYAZ) }
                var y = top + bh + fs * 0.9f
                bf.forEachIndexed { j, b -> val w2 = b.ws[0] + b.fs * 0.9f; val h2 = bH[j]; val x2 = (Wf - w2) / 2; val yy = y; val arm = fs * 1.1f; val th = max(2f, fs * 0.11f); val o = fs * 0.3f
                    add(false, 1, x2 - o, yy - o, w2 + 2 * o, h2 + 2 * o) { c -> val (bgc, fgc) = br(RED); dolgu(c, x2, yy, w2, h2, bgc, fs)
                        yaz(c, b.lines[0], Wf / 2, yy + h2 / 2 + b.fs * 0.34f, Yazilar.cond(800), b.fs, fgc, true, if (fgc == INK) 0f else b.fs * 0.06f)
                        kose(c, x2 - o, yy - o, arm, th, 1, 1, BEYAZ); kose(c, x2 + w2 + o, yy + h2 + o, arm, th, -1, -1, BEYAZ) }
                    y += h2 + fs * 0.12f }
            }
            "r_alinti" -> {
                val RC = 0xFFD0121A.toInt()
                val F = sigdir(title, geo(800), fs0 * 1.02f, maxW * 0.86f, 3); val fs = F.fs; val pad = fs * 0.4f; val lh = fs * 1.36f; val gap = fs * 0.16f
                val barsH = F.lines.size * (lh + gap) - gap; val blockW = (F.ws.maxOrNull() ?: 0f) + pad * 2
                val bf = bands.map { b -> sigdir(b, cond(800), fs * 0.8f, maxW * 0.95f, 1) }; val bH = bf.map { b -> b.fs * 1.35f }
                val bandsH = if (bf.isNotEmpty()) fs * 0.42f + bH.sumOf { h -> (h + fs * 0.12f).toDouble() }.toFloat() - fs * 0.12f else 0f
                val top = CY - (barsH + bandsH) / 2 + fs * 0.35f; val x0 = max(Wf * 0.04f + fs * 0.7f, (Wf - blockW) / 2 + fs * 0.55f)
                val rx = x0 - fs * 0.7f; val rw = fs * 1.95f; val ry = top - fs * 0.3f; val rh = barsH + fs * 0.95f + (if (bf.isEmpty()) fs * 0.05f else 0f)
                val tw = fs * 1.25f; val th = fs * 0.95f; val tx = rx + (rw - tw) / 2 + fs * 0.18f; val ty = ry - th + fs * 0.02f
                add(false, -1, rx, ry, rw, rh) { c ->
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RC }; golgeli(p, fs, true)
                    val r = fs * 0.3f
                    val yol = Path().apply { addRoundRect(RectF(rx, ry, rx + rw, ry + rh), floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r), Path.Direction.CW) }
                    c.drawPath(yol, p)
                }
                add(true, -1, tx, ty, tw, th) { c ->
                    val r = fs * 0.48f
                    c.drawPath(Path().apply { addRoundRect(RectF(tx, ty, tx + tw, ty + th + fs * 0.05f), floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW) }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RC })
                    tirnak(c, tx + tw / 2, ty + th * 0.42f, th * 1.25f, BEYAZ)
                }
                F.lines.forEachIndexed { i, ln -> val y = top + i * (lh + gap); val w = F.ws[i] + pad * 2
                    add(false, -1, x0, y, w, lh) { c -> dolgu(c, x0, y, w, lh, BEYAZ, fs); yaz(c, ln, x0 + pad, y + lh / 2 + fs * 0.36f, Yazilar.geo(800), fs, INK) } }
                var by = top + barsH + fs * 0.42f
                bf.forEachIndexed { j, b -> val bw = b.ws[0] + b.fs * 0.9f; val bh = bH[j]; val y = by; val bx = min(Wf - bw - Wf * 0.03f, x0)
                    add(false, 1, bx, y, bw, bh) { c -> val (bgc, fgc) = br(RC); dolgu(c, bx, y, bw, bh, bgc, fs)
                        yaz(c, b.lines[0], bx + bw / 2, y + bh / 2 + b.fs * 0.34f, Yazilar.cond(800), b.fs, fgc, true, if (fgc == INK) 0f else b.fs * 0.06f) }
                    by += bh + fs * 0.12f }
            }
            "r_yil" -> {   // 3.82: Yıl etiketi (Kabataş Lisesi kapağı): sarı etiket (ilk satır, yıl kırmızı) · beyaz kutular (son satır büyük) · kırmızı konum bandı (iğneli)
                val sat = baslik.replace("*", "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
                val etiket = if (sat.size >= 2) sat[0] else ""
                val govde = (if (sat.size >= 2) sat.drop(1) else sat).ifEmpty { listOf("Başlık") }
                val fs = fs0 * 0.92f; val padX = fs * 0.3f; val gap = fs * 0.1f
                val ff = govde.mapIndexed { i, s -> sigdir(s, cond(800), if (i == govde.lastIndex && govde.size > 1) fs * 1.14f else fs, maxW, 1) }
                val ef = if (etiket.isNotEmpty()) sigdir(etiket, cond(800), fs * 0.66f, maxW, 1) else null
                val bf = bands.map { b -> sigdir(b, cond(800), fs * 0.7f, maxW, 1) }
                val eH = ef?.let { it.fs * 1.55f } ?: 0f
                val rH = ff.map { it.fs * 1.12f }; val bH = bf.map { it.fs * 1.5f }
                val toplamH = eH + rH.sum() + gap * (ff.size - 1).coerceAtLeast(0) + (if (bf.isNotEmpty()) gap * 1.6f + bH.sum() + gap * (bf.size - 1) else 0f)
                val blockW = max(ff.maxOf { (it.ws.firstOrNull() ?: 0f) } + padX * 2, ef?.let { (it.ws.firstOrNull() ?: 0f) + padX * 2 } ?: 0f)
                val x0 = max(Wf * 0.03f, (Wf - blockW) / 2); var y = CY - toplamH / 2
                if (ef != null) { val e = ef; val w = (e.ws.firstOrNull() ?: 0f) + padX * 2; val yy = y; val h = eH; val m0 = e.lines.firstOrNull() ?: ""
                    add(true, -1, x0, yy, w, h) { c ->
                        dolgu(c, x0, yy, w, h, YEL, fs)
                        val tf = Yazilar.cond(800); val bx = x0 + padX; val by = yy + h / 2 + e.fs * 0.34f
                        val m = Regex("\\d{4}").find(m0)
                        if (m == null) yaz(c, m0, bx, by, tf, e.fs, INK)
                        else { var x = bx; val ol = boya(tf, e.fs)
                            listOf(m0.substring(0, m.range.first) to INK, m.value to RED, m0.substring(m.range.last + 1) to INK).forEach { (s, r) ->
                                if (s.isNotEmpty()) { yaz(c, s, x, by, tf, e.fs, r); x += ol.measureText(s) } } }
                    }
                    y += h }
                ff.forEachIndexed { i, f -> val w = (f.ws.firstOrNull() ?: 0f) + padX * 2; val h = rH[i]; val yy = y; val ln = f.lines.firstOrNull() ?: ""
                    add(false, if (i % 2 == 1) 1 else -1, x0, yy, w, h) { c -> dolgu(c, x0, yy, w, h, BEYAZ, fs); yaz(c, ln, x0 + padX, yy + h / 2 + f.fs * 0.34f, Yazilar.cond(800), f.fs, INK) }
                    y += h + gap }
                if (bf.isNotEmpty()) y += gap * 0.6f
                bf.forEachIndexed { j, b -> val pin = b.fs * 0.9f; val pad = b.fs * 0.45f; val w = pin + (b.ws.firstOrNull() ?: 0f) + pad * 2; val h = bH[j]; val yy = y
                    val bx = max(Wf * 0.03f, min(Wf - w - Wf * 0.03f, x0 + blockW - w)); val ln = b.lines.firstOrNull() ?: ""
                    add(false, 1, bx, yy, w, h) { c ->
                        dolgu(c, bx, yy, w, h, RED, fs)
                        val cx = bx + pad + pin * 0.4f; val cy = yy + h / 2 - b.fs * 0.1f; val s = b.fs * 0.3f
                        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BEYAZ }
                        c.drawCircle(cx, cy - s * 0.2f, s, pp)
                        c.drawPath(Path().apply { moveTo(cx - s * 0.82f, cy + s * 0.15f); lineTo(cx + s * 0.82f, cy + s * 0.15f); lineTo(cx, cy + s * 1.75f); close() }, pp)
                        pp.color = RED; c.drawCircle(cx, cy - s * 0.2f, s * 0.4f, pp)
                        yaz(c, ln, bx + pad + pin, yy + h / 2 + b.fs * 0.34f, Yazilar.cond(800), b.fs, BEYAZ)
                    }
                    y += h + gap }
            }
            "r_bugun", "r_baloncuk", "r_menu", "r_canli", "r_cuma", "r_hikaye" ->
                SablonYazi.ciz(stil, bol(baslik.replace("*", "")) + bands, Wf, Hf, fs0, maxW, CY, liste)   // 3.91: Ahmet'in videosundaki şablonlar
            else -> haber(title, bands, Wf, Hf, olcek, CY, liste)   // r_haber: Renkli kutular (Stüdyo'ya özgü)
        }
        // taşma koruması: bütün blok karenin içinde kalsın (alt/üst konumda)
        if (liste.isNotEmpty()) {
            val ust = liste.minOf { o -> o.y }; val alt = liste.maxOf { o -> o.y + o.h }
            val pay = Hf * 0.04f
            val kay = when { ust < pay -> pay - ust; alt > Hf - pay -> (Hf - pay) - alt; else -> 0f }
            if (kay != 0f) return liste.map { o -> BaslikOge(o.x, o.y + kay, o.w, o.h, o.pop, o.yon) { c -> c.save(); c.translate(0f, kay); o.ciz(c); c.restore() } }
        }
        return liste
    }

    /** Renkli kutular (tarih · manşet satırları · konum): Anton manşet, Barlow çipler, gölgeli; sola hizalı blok. */
    private fun haber(title: String, bands: List<String>, Wf: Float, Hf: Float, olcek: Float, CY: Float, liste: MutableList<BaslikOge>) {
        val k = min(Wf, Hf) * olcek.coerceIn(0.5f, 2f)
        val RENK = listOf(0xFFE3A64A.toInt() to 0xFF15100A.toInt(), 0xFF6CB8AC.toInt() to BEYAZ, 0xFFE0645A.toInt() to BEYAZ)
        val bm = k * 0.105f; val x0 = Wf * 0.07f
        val tp = boya(Yazilar.anton, bm)
        val ms = (if (bol(title).size > 1) bol(title) else sar(tp, title, Wf * 0.58f, 4)).map { m -> m.uppercase(TR) }
        class S(val w: Float, val h: Float, val f: (Canvas, Float, Float) -> Unit)
        val ser = ArrayList<S>()
        fun cip(m: String, boy: Float, zemin: Int, yazi: Int, takvim: Boolean) {
            val p = boya(Yazilar.barlow700, boy, yazi).apply { letterSpacing = 0.12f }
            val padX = boy * 0.6f; val h = boy * 1.75f; val ik = if (takvim) boy * 1.45f else 0f; val w = p.measureText(m) + padX * 2 + ik
            ser += S(w, h) { c, x, y ->
                dolgu(c, x, y, w, h, zemin, boy, h * 0.22f)
                if (takvim) { val q = boy * 0.9f; val cx = x + padX * 0.85f; val cy = y + (h - q * 0.9f) / 2
                    val cz = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yazi; style = Paint.Style.STROKE; strokeWidth = max(1f, boy * 0.09f) }
                    c.drawRect(cx, cy, cx + q, cy + q * 0.9f, cz); c.drawLine(cx, cy + q * 0.28f, cx + q, cy + q * 0.28f, cz) }
                c.drawText(m, x + padX + ik, y + h / 2 + boy * 0.36f, p)
            }
        }
        bands.getOrNull(0)?.let { cip(it.uppercase(TR), k * 0.036f, 0xFFE3A64A.toInt(), 0xFF15100A.toInt(), true) }
        ms.forEachIndexed { i, m -> val (z, y) = RENK[i % RENK.size]
            val p = boya(Yazilar.anton, bm, y); val padX = bm * 0.3f; val w = p.measureText(m) + padX * 2; val h = bm * 1.22f
            ser += S(w, h) { c, x, yy -> dolgu(c, x, yy, w, h, z, bm); c.drawText(m, x + padX, yy + h / 2 + bm * 0.36f, p) } }
        bands.drop(1).forEach { cip(it.uppercase(TR), k * 0.038f, 0xFF161616.toInt(), BEYAZ, false) }
        val ara = k * 0.014f
        val topH = ser.sumOf { s -> s.h.toDouble() }.toFloat() + ara * (ser.size - 1)
        var y = CY - topH / 2
        ser.forEachIndexed { _, s -> val yy = y; liste += BaslikOge(x0, yy, s.w, s.h, false, -1) { c -> s.f(c, x0, yy) }; y += s.h + ara }
    }

    /** Parçaları ayrı bitmaplere çevirir (giriş animasyonu için): kutu + gölge payı, merkez aynı. */
    fun seritler(ogeler: List<BaslikOge>, W: Int, H: Int): List<Serit> {
        val m = min(W, H) * 0.06f
        return ogeler.mapIndexed { i, o ->
            val bw = (o.w + 2 * m).toInt().coerceAtLeast(2); val bh = (o.h + 2 * m).toInt().coerceAtLeast(2)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(b); c.translate(m - o.x, m - o.y); o.ciz(c)
            val cx = (o.x + o.w / 2) / W * 2f - 1f; val cy = 1f - (o.y + o.h / 2) / H * 2f
            Serit(b, cx, cy, 60L + i * 130L, o.yon, o.pop)
        }
    }

    /** Hepsini tek kareye çizer (kapak, önizleme küçük resmi). */
    fun tumunuCiz(c: Canvas, ogeler: List<BaslikOge>) { for (o in ogeler) o.ciz(c) }
}

/** 3.60: klasik giriş başlıkları (web drawIntroBody'nin son karesi): tam kare saydam bitmap. */
object KlasikCiz {
    private const val BEYAZ = 0xFFFFFFFF.toInt()
    private fun boya(tf: Typeface, s: Float, renk: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = max(6f, s); color = renk }
    private fun sigdir(p: TextPaint, text: String, fs0: Float, maxW: Float): Float { var fs = fs0; p.textSize = fs; while (p.measureText(text) > maxW && fs > 10f) { fs *= 0.94f; p.textSize = fs }; return fs }
    private fun sar(p: Paint, text: String, maxW: Float, maxL: Int): List<String> {
        val o = ArrayList<String>(); var s = ""
        for (k in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) { val t = if (s.isEmpty()) k else "$s $k"; if (p.measureText(t) > maxW && s.isNotEmpty()) { o += s; s = k } else s = t }
        if (s.isNotEmpty()) o += s
        return o.take(maxL)
    }

    fun ciz(g: Giris, W: Int, H: Int): Bitmap? {
        val title = g.baslik.replace("*", "").replace("|", " ").replace(Regex("\\s+"), " ").trim()
        val satirlarElle = g.baslik.replace("*", "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val sub = g.alt.trim()
        if (title.isEmpty() && sub.isEmpty()) return null
        val Wf = W.toFloat(); val Hf = H.toFloat(); val land = W >= H
        val short = min(Wf, Hf) * g.olcek.coerceIn(0.5f, 2f)
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        when (g.stil) {
            "kutulu", "bantlar" -> {   // web: stack / bands
                val bands = g.stil == "bantlar"; val T = if (bands) title.uppercase(TR) else title; val x0 = Wf * (if (land) 0.07f else 0.08f)
                val tf = if (bands) Yazilar.anton else Yazilar.archivo800
                var fs = short * (if (land) 0.11f else 0.1f); val p = boya(tf, fs, BEYAZ); val maxW = Wf * (if (land) 0.56f else 0.8f)
                fun satirlar(): List<String> = if (satirlarElle.size > 1) satirlarElle.map { if (bands) it.uppercase(TR) else it } else sar(p, T, maxW, 4)
                var lines = satirlar()
                while ((lines.size > 4 || lines.any { p.measureText(it) > maxW * 1.05f }) && fs > 14f) { fs *= 0.92f; p.textSize = fs; lines = satirlar() }
                if (lines.size == 1 && T.split(Regex("\\s+")).size >= 3) { val w = T.split(Regex("\\s+")); val h2 = ceil(w.size / 2f).toInt(); lines = listOf(w.take(h2).joinToString(" "), w.drop(h2).joinToString(" ")) }
                val fs2 = fs * 0.42f; val lh = fs * (if (bands) 1.32f else 1.12f); val subH = if (sub.isNotEmpty()) fs2 * 2.4f else 0f
                val top = Hf * 0.5f - (lines.size * lh + subH) / 2
                val cols = intArrayOf(0xFFE8A33D.toInt(), 0xFF6CB8AC.toInt(), 0xFFE0645A.toInt(), 0xFF8F7AE0.toInt())
                lines.forEachIndexed { i, ln ->
                    val y = top + i * lh; val tw = p.measureText(ln)
                    if (bands) {
                        val padX = fs * 0.32f; val bw = tw + padX * 2; val bh = fs * 1.18f
                        val z = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cols[i % cols.size]; setShadowLayer(fs * 0.12f, 0f, fs * 0.05f, 0x40000000) }
                        c.drawRect(x0, y, x0 + bw, y + bh, z)
                        p.color = if (i % cols.size == 0) 0xFF1A1206.toInt() else BEYAZ; p.clearShadowLayer()
                        c.drawText(ln, x0 + padX, y + bh * 0.83f, p)
                    } else {
                        p.color = if (i == lines.lastIndex && lines.size > 1) 0xFFFFD27A.toInt() else BEYAZ
                        p.setShadowLayer(fs * 0.11f, 0f, 0f, 0x99000000.toInt())
                        c.drawText(ln, x0, y + fs * 0.9f, p)
                        if (i == 0) c.drawRect(x0 - fs * 0.28f, y + fs * 0.1f, x0 - fs * 0.28f + max(3f, fs * 0.08f), y + fs * 0.1f + lines.size * lh - fs * 0.2f, Paint().apply { color = 0xFFE8A33D.toInt() })
                    }
                }
                if (sub.isNotEmpty()) {
                    val S2 = sub.uppercase(TR); val y = top + lines.size * lh + fs2 * 1.5f
                    val sp = boya(Yazilar.archivo600, fs2, BEYAZ).apply { letterSpacing = 0.15f }
                    if (bands) { val tw = sp.measureText(S2); c.drawRect(x0, y - fs2 * 1.15f, x0 + tw + fs2 * 1.2f, y + fs2 * 0.45f, Paint().apply { color = 0xD90E0F12.toInt() }); c.drawText(S2, x0 + fs2 * 0.6f, y, sp) }
                    else { sp.color = 0xEBFFFFFF.toInt(); sp.setShadowLayer(fs2 * 0.2f, 0f, 0f, 0x99000000.toInt()); c.drawText(S2, x0, y, sp) }
                }
            }
            "altbant" -> {   // web: lowerthird
                val p = boya(Yazilar.archivo800, 1f, BEYAZ); val fs = sigdir(p, title, short * (if (land) 0.062f else 0.058f), Wf * 0.7f); p.textSize = fs
                val fs2 = fs * 0.46f; val sp = boya(Yazilar.archivo600, fs2, 0xFFE8A33D.toInt()).apply { letterSpacing = 0.18f }
                val tw = p.measureText(title); val sw = if (sub.isNotEmpty()) sp.measureText(sub.uppercase(TR)) else 0f
                val pad = fs * 0.45f; val bw = max(tw, sw) + pad * 2; val bh = fs * 1.25f + (if (sub.isNotEmpty()) fs2 * 1.6f else 0f) + pad * 0.6f
                val x = Wf * 0.06f; val y = if (land) Hf * 0.66f else Hf * 0.6f
                c.drawRect(x, y, x + fs * 0.16f, y + bh, Paint().apply { color = 0xFFE8A33D.toInt() })
                c.drawRect(x + fs * 0.16f, y, x + fs * 0.16f + bw, y + bh, Paint().apply { color = 0xDB0E0F12.toInt() })
                c.drawText(title, x + fs * 0.16f + pad, y + pad * 0.4f + fs, p)
                if (sub.isNotEmpty()) c.drawText(sub.uppercase(TR), x + fs * 0.16f + pad, y + pad * 0.4f + fs + fs2 * 1.55f, sp)
            }
            "minimal" -> {
                val T = title.uppercase(TR)
                val p = boya(Yazilar.bebas, 1f, BEYAZ).apply { letterSpacing = 0.09f; textAlign = Paint.Align.CENTER }
                val fs = sigdir(p, T, short * (if (land) 0.13f else 0.11f), Wf * 0.7f); p.textSize = fs
                val tw = p.measureText(T); val pad = fs * 0.5f; val bw = tw + pad * 2; val bh = fs * 1.25f; val bx = Wf / 2 - bw / 2; val by = Hf * 0.47f - bh / 2
                c.drawRect(bx, by, bx + bw, by + bh, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF2FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = max(2f, short * 0.004f) })
                p.setShadowLayer(fs * 0.075f, 0f, 0f, 0x66000000)
                c.drawText(T, Wf / 2, by + bh * 0.5f + fs * 0.36f, p)
                if (sub.isNotEmpty()) { val fs2 = fs * 0.24f; val sp = boya(Yazilar.archivo600, fs2, BEYAZ).apply { letterSpacing = 0.4f; textAlign = Paint.Align.CENTER }
                    c.drawText(sub.uppercase(TR), Wf / 2, by + bh + fs2 * 2f, sp) }
            }
            "neon" -> {
                val T = title.uppercase(TR)
                val p = boya(Yazilar.bebas, 1f, 0xFFFFD6F6.toInt()).apply { letterSpacing = 0.05f; textAlign = Paint.Align.CENTER }
                val fs = sigdir(p, T, short * (if (land) 0.15f else 0.13f), Wf * 0.84f); p.textSize = fs
                val y = Hf * 0.5f + fs * 0.3f
                p.setShadowLayer(fs * 0.22f, 0f, 0f, 0xFFFF3FD0.toInt()); c.drawText(T, Wf / 2, y, p)
                p.setShadowLayer(fs * 0.07f, 0f, 0f, 0xFFFF3FD0.toInt()); c.drawText(T, Wf / 2, y, p)
                if (sub.isNotEmpty()) { val fs2 = fs * 0.28f; val sp = boya(Yazilar.bebas, fs2, 0xFFD8FBFF.toInt()).apply { letterSpacing = 0.3f; textAlign = Paint.Align.CENTER; setShadowLayer(fs2 * 0.4f, 0f, 0f, 0xFF3FE0FF.toInt()) }
                    c.drawText(sub.uppercase(TR), Wf / 2, y + fs2 * 1.9f, sp) }
            }
            else -> {   // sinematik (web: cinematic)
                val bar = Hf * (if (land) 0.11f else 0.08f)
                val siyah = Paint().apply { color = 0xFF000000.toInt() }
                c.drawRect(0f, 0f, Wf, bar, siyah); c.drawRect(0f, Hf - bar, Wf, Hf, siyah)
                val T = title.uppercase(TR)
                val p = boya(Yazilar.playfair, 1f, BEYAZ).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.13f * 0.09f / 0.095f }
                val fs = sigdir(p, T, short * (if (land) 0.095f else 0.085f), Wf * 0.84f); p.textSize = fs
                p.setShadowLayer(fs * 0.17f, 0f, 0f, 0x8C000000.toInt())
                c.drawText(T, Wf / 2, Hf * 0.5f + fs * 0.3f, p)
                if (sub.isNotEmpty()) {
                    val fs2 = fs * 0.3f; val S2 = sub.uppercase(TR)
                    val sp = boya(Yazilar.archivo600, fs2, 0xE0FFFFFF.toInt()).apply { letterSpacing = 0.35f; textAlign = Paint.Align.CENTER }
                    val sw = sp.measureText(S2); val y2 = Hf * 0.5f + fs * 0.3f + fs2 * 2.4f
                    c.drawText(S2, Wf / 2, y2, sp)
                    val lw = short * 0.08f; val cz = Paint().apply { color = 0xF2E8A33D.toInt() }; val kal = max(1.5f, fs2 * 0.08f)
                    c.drawRect(Wf / 2 - sw / 2 - fs2 - lw, y2 - fs2 * 0.38f, Wf / 2 - sw / 2 - fs2, y2 - fs2 * 0.38f + kal, cz)
                    c.drawRect(Wf / 2 + sw / 2 + fs2, y2 - fs2 * 0.38f, Wf / 2 + sw / 2 + fs2 + lw, y2 - fs2 * 0.38f + kal, cz)
                }
            }
        }
        return b
    }
}


/** 3.91: Ahmet'in gösterdiği şablon yazılar (giriş başlığı, kapak ve Yazı katmanında ortak). sat: satırlar (başlık satırları + bantlar).
 *  Bugün (beyaz eğik kutu) · Baloncuk (kırmızı-turuncu-sarı geçişli, kalın beyaz kontur) · Menü (üç satır, ortada sarı bant)
 *  · Canlı (turuncu geçişli italik + kırmızı bantlı alt yazı) · Kara Cuma (sarı/beyaz italik + sarı etiket) · Hikâye (siyah, beyaz kontur, yıpranmış). */
object SablonYazi {
    private const val BEYAZ = 0xFFFFFFFF.toInt(); private const val INK = 0xFF111111.toInt(); private const val SARI = 0xFFFFD60A.toInt()
    private val ATES = intArrayOf(0xFFFF4A1C.toInt(), 0xFFFF7A1A.toInt(), 0xFFFFC23A.toInt())
    private val TURUNCU = intArrayOf(0xFFFF5A1F.toInt(), 0xFFFFA126.toInt())

    private fun boya(tf: Typeface, fs: Float, renk: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = max(6f, fs); color = renk }
    private fun sigdir(s: String, tf: Typeface, fs0: Float, maxW: Float): Float { var fs = fs0; while (fs > 10f && boya(tf, fs).measureText(s) > maxW) fs *= 0.95f; return fs }

    /** Yazıyı (ortalı, taban çizgisi y) dolgu geçişi + kontur + gölgeyle çizer. */
    private fun yaz(c: Canvas, s: String, cx: Float, y: Float, tf: Typeface, fs: Float, dolgu: IntArray, kontur: Int = 0, konturKal: Float = 0f, golge: Boolean = true) {
        val p = boya(tf, fs).apply { textAlign = Paint.Align.CENTER }
        if (golge) {
            val g = TextPaint(p).apply { color = 0x73000000; style = Paint.Style.FILL_AND_STROKE; strokeWidth = konturKal; setShadowLayer(fs * 0.12f, 0f, fs * 0.08f, 0x99000000.toInt()) }
            c.drawText(s, cx, y + fs * 0.05f, g)
        }
        if (konturKal > 0f) c.drawText(s, cx, y, TextPaint(p).apply { color = kontur; style = Paint.Style.STROKE; strokeWidth = konturKal; strokeJoin = Paint.Join.ROUND })
        if (dolgu.size > 1) p.shader = android.graphics.LinearGradient(0f, y - fs * 0.8f, 0f, y + fs * 0.1f, dolgu, null, android.graphics.Shader.TileMode.CLAMP) else p.color = dolgu[0]
        c.drawText(s, cx, y, p)
    }
    /** Eğik bant (paralelkenar). */
    private fun bant(c: Canvas, x: Float, y: Float, w: Float, h: Float, renkler: IntArray, egim: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        if (renkler.size > 1) p.shader = android.graphics.LinearGradient(x, 0f, x + w, 0f, renkler, null, android.graphics.Shader.TileMode.CLAMP) else p.color = renkler[0]
        p.setShadowLayer(h * 0.18f, 0f, h * 0.1f, 0x55000000)
        c.drawPath(Path().apply { moveTo(x + egim, y); lineTo(x + w, y); lineTo(x + w - egim, y + h); lineTo(x, y + h); close() }, p)
    }

    fun ciz(stil: String, sat0: List<String>, Wf: Float, Hf: Float, fs0: Float, maxW: Float, CY: Float, liste: MutableList<BaslikOge>) {
        val sat = sat0.ifEmpty { listOf("Başlık") }
        val cx = Wf / 2
        fun add(pop: Boolean, yon: Int, x: Float, y: Float, w: Float, h: Float, f: (Canvas) -> Unit) { liste += BaslikOge(x, y, w, h, pop, yon, f) }
        when (stil) {
            "r_bugun" -> {   // beyaz eğik kutu, siyah ağır yazı; altında beyaz italik alt yazı
                val tf = Yazilar.mont900; val fs = sigdir(sat[0], tf, fs0 * 1.05f, maxW * 0.9f)
                val tw = boya(tf, fs).measureText(sat[0]); val padX = fs * 0.32f; val bh = fs * 1.25f; val bw = tw + padX * 2
                val alt = sat.drop(1).joinToString(" ").uppercase(TR); val afs = fs * 0.36f
                val top = CY - (bh + (if (alt.isNotEmpty()) afs * 1.6f else 0f)) / 2; val bx = cx - bw / 2
                add(true, -1, bx - fs * 0.2f, top - fs * 0.25f, bw + fs * 0.4f, bh + fs * 0.5f) { c ->
                    c.save(); c.rotate(-5f, cx, top + bh / 2)
                    c.drawRect(bx, top, bx + bw, top + bh, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BEYAZ; setShadowLayer(fs * 0.15f, 0f, fs * 0.08f, 0x66000000) })
                    c.drawText(sat[0], cx, top + bh / 2 + fs * 0.36f, boya(tf, fs, INK).apply { textAlign = Paint.Align.CENTER })
                    c.restore()
                }
                if (alt.isNotEmpty()) { val y = top + bh + afs * 1.25f
                    add(false, 1, bx, y - afs, bw, afs * 1.4f) { c ->
                        c.save(); c.rotate(-5f, cx, top + bh / 2)
                        c.drawText(alt, cx + fs * 0.4f, y, boya(Yazilar.mont900i, afs, BEYAZ).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.06f; setShadowLayer(afs * 0.3f, 0f, afs * 0.15f, 0x99000000.toInt()) })
                        c.restore()
                    } }
            }
            "r_baloncuk" -> {   // kırmızı → turuncu → sarı geçişli kalın yazı, kalın beyaz kontur, koyu gölge (satır satır)
                val tf = Yazilar.mont900; val l = sat.take(3)
                val fsl = l.mapIndexed { i, t -> sigdir(t, tf, fs0 * (if (i == 0) 1.25f else 1.05f), maxW) }
                val lh = fsl.map { it * 1.08f }; var y = CY - lh.sum() / 2
                l.forEachIndexed { i, t -> val fs = fsl[i]; val yy = y; val w = boya(tf, fs).measureText(t) + fs * 0.4f
                    add(i == 0, if (i % 2 == 0) -1 else 1, cx - w / 2, yy, w, lh[i]) { c -> yaz(c, t, cx, yy + fs * 0.86f, tf, fs, if (i == 0) ATES else intArrayOf(0xFFFF8A1E.toInt(), 0xFFFFD23A.toInt()), BEYAZ, fs * 0.2f) }
                    y += lh[i] }
            }
            "r_menu" -> {   // üç satır dar yazı: üst ve alt turuncu geçişli (koyu kontur), ortadaki beyaz yazı sarı eğik bant üstünde
                val tf = Yazilar.anton; val l = sat.take(3)
                val fsl = l.mapIndexed { i, t -> sigdir(t.uppercase(TR).takeIf { i > 0 } ?: t, tf, fs0 * (if (i == 1) 1.0f else 0.82f), maxW * 0.9f) }
                val lh = fsl.map { it * 1.18f }; var y = CY - lh.sum() / 2
                l.forEachIndexed { i, t0 -> val t = if (i > 0) t0.uppercase(TR) else t0; val fs = fsl[i]; val yy = y; val w = boya(tf, fs).measureText(t) + fs * 0.6f
                    add(i == 1, if (i % 2 == 0) -1 else 1, cx - w / 2, yy, w, lh[i]) { c ->
                        if (i == 1) { bant(c, cx - w / 2, yy + lh[i] * 0.08f, w, lh[i] * 0.86f, intArrayOf(0xFFFFE04A.toInt(), SARI), fs * 0.25f)
                            yaz(c, t, cx, yy + lh[i] * 0.08f + lh[i] * 0.43f + fs * 0.36f, tf, fs, intArrayOf(BEYAZ), INK, fs * 0.08f) }
                        else yaz(c, t, cx, yy + fs * 1.0f, tf, fs, TURUNCU, 0xFF3A1206.toInt(), fs * 0.1f)
                    }
                    y += lh[i] }
            }
            "r_canli" -> {   // büyük italik turuncu geçişli yazı (koyu kontur) + altında kırmızı-turuncu eğik bantta beyaz dar yazı
                val tf = Yazilar.mont900i; val fs = sigdir(sat[0], tf, fs0 * 1.15f, maxW); val alt = sat.drop(1).joinToString(" ").uppercase(TR)
                val bfs = fs * 0.5f; val bh = bfs * 1.45f; val h1 = fs * 1.15f
                val top = CY - (h1 + if (alt.isNotEmpty()) bh else 0f) / 2; val w1 = boya(tf, fs).measureText(sat[0]) + fs * 0.4f
                add(true, -1, cx - w1 / 2, top, w1, h1) { c -> yaz(c, sat[0], cx, top + fs * 0.92f, tf, fs, TURUNCU, 0xFF3A1206.toInt(), fs * 0.09f) }
                if (alt.isNotEmpty()) { val bf = Yazilar.barlow800; val bw = boya(bf, bfs).measureText(alt) + bfs * 1.4f; val y = top + h1
                    add(false, 1, cx - bw / 2, y, bw, bh) { c ->
                        bant(c, cx - bw / 2, y, bw, bh, intArrayOf(0xFFE3101B.toInt(), 0xFFFF7A1A.toInt()), bfs * 0.3f)
                        c.drawText(alt, cx, y + bh / 2 + bfs * 0.36f, boya(bf, bfs, BEYAZ).apply { textAlign = Paint.Align.CENTER; letterSpacing = 0.04f })
                    } }
            }
            "r_cuma" -> {   // sarı italik üst satır, beyaz büyük italik ikinci satır, altta sarı yuvarlak etiket (siyah yazı)
                val tf = Yazilar.mont900i
                val l1 = sat[0].uppercase(TR); val l2 = sat.getOrNull(1)?.uppercase(TR); val tag = sat.drop(2).joinToString(" ")
                val f1 = sigdir(l1, tf, fs0 * (if (l2 != null) 0.82f else 1.1f), maxW); val f2 = l2?.let { sigdir(it, tf, fs0 * 1.1f, maxW) } ?: 0f
                val tfs = fs0 * 0.32f; val th = tfs * 1.6f
                val H = f1 * 1.05f + (if (l2 != null) f2 * 1.05f else 0f) + (if (tag.isNotEmpty()) th + tfs * 0.4f else 0f)
                var y = CY - H / 2
                val y1 = y; val w1 = boya(tf, f1).measureText(l1) + f1 * 0.4f
                add(true, -1, cx - w1 / 2, y1, w1, f1 * 1.05f) { c -> yaz(c, l1, cx, y1 + f1 * 0.9f, tf, f1, intArrayOf(SARI), 0, 0f) }
                y += f1 * 1.05f
                if (l2 != null) { val y2 = y; val w2 = boya(tf, f2).measureText(l2) + f2 * 0.4f
                    add(false, 1, cx - w2 / 2, y2, w2, f2 * 1.05f) { c -> yaz(c, l2, cx, y2 + f2 * 0.9f, tf, f2, intArrayOf(BEYAZ), 0, 0f) }
                    y += f2 * 1.05f }
                if (tag.isNotEmpty()) { val y3 = y + tfs * 0.4f; val tw = boya(Yazilar.mont800, tfs).measureText(tag) + tfs * 1.4f
                    add(false, -1, cx - tw / 2, y3, tw, th) { c ->
                        c.drawRoundRect(RectF(cx - tw / 2, y3, cx + tw / 2, y3 + th), th * 0.3f, th * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SARI; setShadowLayer(th * 0.2f, 0f, th * 0.1f, 0x66000000) })
                        c.drawText(tag, cx, y3 + th / 2 + tfs * 0.36f, boya(Yazilar.mont800, tfs, INK).apply { textAlign = Paint.Align.CENTER })
                    } }
            }
            else -> {   // r_hikaye: siyah ağır yazı, kalın beyaz kontur, beyaz yıpranma lekeleri (satır satır)
                val tf = Yazilar.archivo800; val l = sat.take(3)
                val fsl = l.mapIndexed { i, t -> sigdir(t, tf, fs0 * (if (i == 0) 1.2f else 0.9f), maxW) }
                val lh = fsl.map { it * 1.12f }; var y = CY - lh.sum() / 2
                l.forEachIndexed { i, t -> val fs = fsl[i]; val yy = y; val w = boya(tf, fs).measureText(t) + fs * 0.5f
                    add(i == 0, if (i % 2 == 0) -1 else 1, cx - w / 2, yy, w, lh[i]) { c ->
                        val by = yy + fs * 0.88f
                        yaz(c, t, cx, by, tf, fs, intArrayOf(INK), BEYAZ, fs * 0.2f)
                        // yıpranma: harflerin içine düşen küçük beyaz lekeler (yazının yoluna kırpılır; aynı yazı hep aynı lekeler)
                        val yol = Path(); boya(tf, fs).apply { textAlign = Paint.Align.CENTER }.getTextPath(t, 0, t.length, cx, by, yol)
                        c.save(); c.clipPath(yol)
                        val r = java.util.Random(t.hashCode().toLong() * 31 + i); val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD9FFFFFF.toInt() }
                        val bw = boya(tf, fs).measureText(t)
                        repeat((bw / fs * 22).toInt().coerceIn(20, 260)) { c.drawCircle(cx - bw / 2 + r.nextFloat() * bw, by - fs * 0.78f + r.nextFloat() * fs * 0.85f, fs * (0.008f + r.nextFloat() * r.nextFloat() * 0.035f), p) }
                        c.restore()
                    }
                    y += lh[i] }
            }
        }
    }
}

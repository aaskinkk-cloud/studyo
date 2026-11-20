package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.C
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Çok katmanlı düzenleme: katman görüntüsü, animasyon ve filtre. Önizleme ve dışa aktarma AYNI bitmapi ve AYNI hesabı kullanır. */
object KatmanCizim {
    val CIKARTMALAR = listOf(
        "⭐", "❤️", "👍", "🔥", "✨", "🎉", "☕", "🍵", "🌲", "⛺", "🏕️", "🌄", "🌿", "🍂", "🌸", "🌻",
        "☀️", "🌙", "⚡", "🌧️", "❄️", "📍", "➡️", "⬅️", "⬆️", "⬇️", "✅", "❌", "❗", "❓", "😀", "😍",
        "😂", "🤔", "👏", "🙏", "🎵", "📷", "🎬", "🏞️",
    )
    val RENKLER = listOf(0xFFFFFFFF, 0xFF000000, 0xFFFFD400, 0xFFF5A524, 0xFFEF5350, 0xFF4CC38A, 0xFF2EC5D3, 0xFF7E57C2)

    /** Bitmapi etkileyen alanlar (değişince yeniden çizilir). Konum, açı, opaklık, zaman bitmapi değiştirmez. */
    fun anahtar(o: KatmanOge): Int = listOf(o.tur, o.yol, o.metin, o.renk, o.kutu, o.olcek, o.stil, o.altyazi, o.filtre, o.kirpL, o.kirpT, o.kirpR, o.kirpB).hashCode()

    // ---------------- 3.64: hareketli yazılar (kare kare çizilir) ----------------
    /** Giriş/çıkışı kendi çizimiyle hareket eden yazı stilleri. */
    val HAREKETLI = setOf("acilan", "serit", "daktilo", "ciftkutu", "altcizgi")
    /** Giriş ve çıkış animasyonunun kare sayısı (ADIM = tam görünüm). */
    const val ADIM = 30
    fun hareketli(o: KatmanOge): Boolean = o.tur == "metin" && o.stil in HAREKETLI

    /** O andaki animasyon karesi (0 = hiç görünmez … ADIM = tam). Hareketli olmayan katmanda hep ADIM. */
    fun adim(o: KatmanOge, ms: Long): Int {
        if (!hareketli(o)) return ADIM
        val uzun = o.metin.replace("\n", " ").length
        val gir = if (o.stil == "daktilo") (uzun * 55L).coerceIn(600L, 2200L) else 900L
        val cik = 400L
        val sure = (o.sonMs - o.basMs).coerceAtLeast(1L)
        val g = minOf(gir, sure * 2 / 3); val c = minOf(cik, sure / 4).coerceAtLeast(1L)
        val f = minOf((ms - o.basMs).toFloat() / g.coerceAtLeast(1L), (o.sonMs - ms).toFloat() / c, 1f).coerceIn(0f, 1f)
        return (f * ADIM).roundToInt().coerceIn(0, ADIM)
    }

    /** W×H çıktı karesi için katmanın bitmapi (ses katmanı ve boş metin için null). adim: hareketli yazının karesi. */
    fun bitmap(o: KatmanOge, W: Int, H: Int, adim: Int = ADIM): Bitmap? = try {
        val b = when (o.tur) {
            "metin" -> if (hareketli(o)) hareketliMetin(o, W, H, adim.coerceIn(0, ADIM) / ADIM.toFloat()) else metin(o, W, H)
            "cikartma" -> cikartma(o, W)
            "gorsel" -> gorsel(o, W)
            else -> null
        }
        if (b != null && o.altyazi.isNotBlank()) altyaziEkle(b, o.altyazi, W, H) else b
    } catch (e: Throwable) {
        Kayit.hata("katman çizimi ${o.tur}", e); null
    }

    /** Katman altyazısı (3.46): katman bitmapinin altına ortalı, koyu yarı saydam kutulu yazı; tek bitmap olduğu için
     *  önizleme, taşıma, büyütme ve dışa aktarma kendiliğinden birlikte çalışır. */
    private fun altyaziEkle(b: Bitmap, yazi: String, W: Int, H: Int): Bitmap {
        val px = min(W, H) * 0.038f
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px; color = 0xFFFFFFFF.toInt()
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL) }
        val enGen = maxOf(b.width, (W * 0.6f).toInt()).coerceAtMost(W)
        val lay = StaticLayout.Builder.obtain(yazi, 0, yazi.length, p, (enGen - px).toInt().coerceAtLeast(10)).setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(3).build()
        var lw = 1f; for (i in 0 until lay.lineCount) lw = max(lw, lay.getLineWidth(i))
        val pad = px * 0.4f; val ara = px * 0.3f
        val kw = lw + pad * 2; val kh = lay.height + pad * 2
        val gw = max(b.width.toFloat(), kw); val gh = b.height + ara + kh
        val out = Bitmap.createBitmap(gw.toInt().coerceAtLeast(2), gh.toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(b, (gw - b.width) / 2f, 0f, null)
        val y0 = b.height + ara; val x0 = (gw - kw) / 2f
        c.drawRoundRect(RectF(x0, y0, x0 + kw, y0 + kh), pad, pad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3000000.toInt() })
        c.save(); c.translate(x0 + pad - ((enGen - px) - lw) / 2f, y0 + pad); lay.draw(c); c.restore()
        return out
    }

    /** Stilli yazılar (3.42): altyazı görünümü yerine tasarımlı kartlar. Vurgu rengi = seçili renk (beyazsa turuncu). */
    private fun stilliMetin(o: KatmanOge, W: Int, H: Int): Bitmap? {
        val k = min(W, H).toFloat()
        val px = (o.olcek * k).coerceAtLeast(8f)
        val satirlar = o.metin.split('\n')
        val ust = satirlar.first().let { if (o.stil == "etiket") it.uppercase() else it }
        val alt = satirlar.drop(1).joinToString(" ").trim()
        val vurgu = (if (o.renk == 0xFFFFFFFFL) 0xFFF5A524L else o.renk).toInt()
        val kalin = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        val ince = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        val p1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px; typeface = kalin; color = 0xFFFFFFFF.toInt() }
        val p2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px * 0.58f; typeface = ince; color = 0xE6FFFFFF.toInt() }
        val enGen = W * 0.9f
        fun kisalt(t: String, p: TextPaint) = android.text.TextUtils.ellipsize(t, p, enGen * 0.8f, android.text.TextUtils.TruncateAt.END).toString()
        val u = kisalt(ust, p1); val a = if (alt.isEmpty()) "" else kisalt(alt, p2)
        val w1 = p1.measureText(u); val w2 = if (a.isEmpty()) 0f else p2.measureText(a)
        val f1 = p1.fontMetrics; val f2 = p2.fontMetrics
        val h1 = f1.descent - f1.ascent; val h2 = if (a.isEmpty()) 0f else f2.descent - f2.ascent
        val pad = px * 0.42f
        val boya = Paint(Paint.ANTI_ALIAS_FLAG)
        fun bmp(w: Float, h: Float) = Bitmap.createBitmap(w.toInt().coerceAtLeast(2), h.toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        return when (o.stil) {
            "konum" -> {     // 📍 iğne simgesi + yer adı, koyu cam kart, solda vurgu
                val ik = h1 * 0.9f
                val icGen = maxOf(w1, w2)
                val bw = pad + ik + pad * 0.6f + icGen + pad * 1.2f; val bh = pad * 2 + h1 + h2
                val b = bmp(bw, bh); val c = Canvas(b)
                boya.color = 0xD9121A22.toInt(); c.drawRoundRect(RectF(0f, 0f, bw, bh), bh * 0.5f, bh * 0.5f, boya)
                // iğne: daire + alt uç
                val cx = pad + ik / 2f; val cy = bh / 2f - ik * 0.12f; val r = ik * 0.36f
                boya.color = vurgu
                val yol = android.graphics.Path().apply {
                    addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
                    moveTo(cx - r * 0.82f, cy + r * 0.55f); lineTo(cx, cy + r * 2.1f); lineTo(cx + r * 0.82f, cy + r * 0.55f); close()
                }
                c.drawPath(yol, boya)
                boya.color = 0xFF121A22.toInt(); c.drawCircle(cx, cy, r * 0.42f, boya)
                val tx = pad + ik + pad * 0.6f
                c.drawText(u, tx, pad - f1.ascent, p1)
                if (a.isNotEmpty()) c.drawText(a, tx, pad + h1 - f2.ascent, p2)
                b
            }
            "altbant" -> {   // alt üçlü bant: solda vurgu çubuğu, üst satır koyu kartta, alt satır vurgu renginde
                val cub = px * 0.22f
                val g1 = w1 + pad * 2; val g2 = if (a.isEmpty()) 0f else w2 + pad * 2
                val bw = cub + maxOf(g1, g2); val bh = h1 + pad * 1.2f + (if (a.isEmpty()) 0f else h2 + pad * 0.8f)
                val b = bmp(bw, bh); val c = Canvas(b)
                boya.color = vurgu; c.drawRect(0f, 0f, cub, bh, boya)
                boya.color = 0xE6121A22.toInt(); c.drawRect(cub, 0f, cub + g1, h1 + pad * 1.2f, boya)
                c.drawText(u, cub + pad, pad * 0.6f - f1.ascent, p1)
                if (a.isNotEmpty()) {
                    val y0 = h1 + pad * 1.2f
                    boya.color = vurgu; c.drawRect(cub, y0, cub + g2, bh, boya)
                    p2.color = 0xFF121A22.toInt(); p2.typeface = kalin
                    c.drawText(a, cub + pad, y0 + pad * 0.4f - f2.ascent, p2)
                }
                b
            }
            "etiket" -> {    // hap biçimli etiket: vurgu zemin, koyu kalın yazı
                p1.color = 0xFF121A22.toInt(); p1.letterSpacing = 0.06f
                val ww = p1.measureText(u)
                val bw = ww + pad * 2.4f; val bh = h1 + pad * 1.1f
                val b = bmp(bw, bh); val c = Canvas(b)
                boya.color = vurgu; c.drawRoundRect(RectF(0f, 0f, bw, bh), bh / 2f, bh / 2f, boya)
                c.drawText(u, pad * 1.2f, pad * 0.55f - f1.ascent, p1)
                b
            }
            else -> {        // baslik: büyük beyaz yazı, ince koyu kontur + yumuşak gölge, altında vurgu çizgisi
                val cizgi = px * 0.14f
                val bw = maxOf(w1, w2) + pad * 2; val bh = pad + h1 + cizgi * 2.4f + h2 + pad
                val b = bmp(bw, bh); val c = Canvas(b)
                val y1 = pad - f1.ascent
                val kontur = TextPaint(p1).apply { style = Paint.Style.STROKE; strokeWidth = px * 0.1f; color = 0xCC000000.toInt(); strokeJoin = Paint.Join.ROUND
                    setShadowLayer(px * 0.18f, 0f, px * 0.06f, 0x99000000.toInt()) }
                val x1 = (bw - w1) / 2f
                c.drawText(u, x1, y1, kontur); c.drawText(u, x1, y1, p1)
                val cy = pad + h1 + cizgi * 0.6f
                boya.color = vurgu; val cw = minOf(w1, px * 3.2f)
                c.drawRoundRect(RectF((bw - cw) / 2f, cy, (bw + cw) / 2f, cy + cizgi), cizgi / 2f, cizgi / 2f, boya)
                if (a.isNotEmpty()) {
                    p2.setShadowLayer(px * 0.12f, 0f, px * 0.04f, 0xCC000000.toInt())
                    c.drawText(a, (bw - w2) / 2f, cy + cizgi * 1.8f - f2.ascent, p2)
                }
                b
            }
        }
    }

    /** 3.64: hareketli yazı karesi. Bütün kareler AYNI boyutta (konum kaymaz); f = 0…1 ilerleme. Renk beyazsa stilin kendi rengi. */
    private fun hareketliMetin(o: KatmanOge, W: Int, H: Int, f: Float): Bitmap? {
        if (o.metin.isBlank()) return null
        val k = min(W, H).toFloat()
        val px = (o.olcek * k).coerceAtLeast(8f)
        val satirlar = o.metin.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (satirlar.isEmpty()) return null
        val varsayilan = when (o.stil) { "acilan" -> 0xFFF08A24L; "serit" -> 0xFFE53935L; "daktilo" -> 0xFF4CC38AL; "ciftkutu" -> 0xFF7E57C2L; else -> 0xFFFFD400L }
        val vurgu = (if (o.renk == 0xFFFFFFFFL) varsayilan else o.renk).toInt()
        val kalin = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        val enGen = W * 0.8f
        fun kisalt(t: String, p: TextPaint) = android.text.TextUtils.ellipsize(t, p, enGen, android.text.TextUtils.TruncateAt.END).toString()
        fun ara(a: Float, b: Float): Float { val x = ((f - a) / (b - a)).coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
        fun geri(a: Float, b: Float): Float {   // hafif taşan (geri sekme) yumuşatma
            val x = ((f - a) / (b - a)).coerceIn(0f, 1f); val c1 = 1.70158f; val c3 = c1 + 1f
            return 1f + c3 * (x - 1f) * (x - 1f) * (x - 1f) + c1 * (x - 1f) * (x - 1f)
        }
        fun bmp(w: Float, h: Float) = Bitmap.createBitmap(w.toInt().coerceAtLeast(2), h.toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val boya = Paint(Paint.ANTI_ALIAS_FLAG)
        val p1 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = px; typeface = kalin; color = 0xFFFFFFFF.toInt() }
        val f1 = p1.fontMetrics; val h1 = f1.descent - f1.ascent
        val pad = px * 0.42f
        return when (o.stil) {
            "acilan" -> {   // turuncu yuvarlak kare + tırnak; beyaz kutu karenin içinden sağa doğru açılır (siyah yazı)
                p1.color = 0xFF111111.toInt()
                val u = kisalt(satirlar.joinToString(" "), p1)
                val kutuH = h1 + pad * 1.1f
                val S = kutuH * 1.8f
                val tirnakH = S * 0.5f
                val kx = S * 0.14f; val kutuW = p1.measureText(u) + pad * 2
                val bw = maxOf(kx + kutuW + pad * 0.3f, S + 2f); val bh = tirnakH + S * 0.12f + S
                val b = bmp(bw, bh); val c = Canvas(b)
                val sy = tirnakH + S * 0.12f
                // beyaz kutu: karenin sol iç kenarından sağa açılır
                val r = ara(0.28f, 1f)
                if (r > 0f) {
                    val ky = sy + (S - kutuH) / 2f
                    c.save(); c.clipRect(kx, ky, kx + kutuW * r, ky + kutuH)
                    boya.color = 0xFFFFFFFF.toInt(); c.drawRect(kx, ky, kx + kutuW, ky + kutuH, boya)
                    c.drawText(u, kx + pad, ky + pad * 0.55f - f1.ascent, p1)
                    c.restore()
                }
                // yuvarlak kare (çizgi): ortasından büyüyerek gelir
                val sk = geri(0f, 0.38f)
                if (sk > 0.01f) {
                    val cw = S * 0.1f
                    c.save(); c.scale(sk, sk, S / 2f, sy + S / 2f)
                    boya.color = vurgu; boya.style = Paint.Style.STROKE; boya.strokeWidth = cw
                    c.drawRoundRect(RectF(cw / 2f, sy + cw / 2f, S - cw / 2f, sy + S - cw / 2f), S * 0.2f, S * 0.2f, boya)
                    boya.style = Paint.Style.FILL
                    c.restore()
                }
                // tırnak işareti: hafif yukarıdan iner, belirir
                val ta = ara(0.15f, 0.45f)
                if (ta > 0f) {
                    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = tirnakH * 1.9f; typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
                        color = 0xFFFFFFFF.toInt(); alpha = (ta * 255).toInt(); setShadowLayer(px * 0.12f, 0f, px * 0.04f, 0x66000000) }
                    val tm = tp.fontMetrics
                    c.drawText("\u201C", S * 0.08f, tirnakH * 1.25f - tm.descent * 0.2f - (1f - ta) * tirnakH * 0.3f, tp)
                }
                b
            }
            "serit" -> {    // renkli şerit soldan süpürür, beyaz yazı içinden kayar; 2. satır beyaz şeritte koyu yazı
                val u = kisalt(satirlar[0], p1)
                val p2 = TextPaint(p1).apply { textSize = px * 0.62f; color = 0xFF111111.toInt() }
                val f2 = p2.fontMetrics; val h2 = f2.descent - f2.ascent
                val a = if (satirlar.size > 1) kisalt(satirlar.drop(1).joinToString(" "), p2) else ""
                val g1 = p1.measureText(u) + pad * 2; val y1 = h1 + pad * 0.9f
                val g2 = if (a.isEmpty()) 0f else p2.measureText(a) + pad * 1.6f; val y2 = if (a.isEmpty()) 0f else h2 + pad * 0.7f
                val bw = maxOf(g1, g2); val bh = y1 + y2
                val b = bmp(bw, bh); val c = Canvas(b)
                val s1 = ara(0f, 0.45f)
                if (s1 > 0f) { boya.color = vurgu; c.drawRect(0f, 0f, g1 * s1, y1, boya) }
                val t1 = ara(0.3f, 0.75f)
                if (t1 > 0f) { c.save(); c.clipRect(0f, 0f, g1, y1); c.drawText(u, pad - (1f - t1) * g1 * 0.5f, pad * 0.45f - f1.ascent, p1); c.restore() }
                if (a.isNotEmpty()) {
                    val s2 = ara(0.45f, 0.8f)
                    if (s2 > 0f) { boya.color = 0xFFFFFFFF.toInt(); c.drawRect(0f, y1, g2 * s2, bh, boya) }
                    val t2 = ara(0.6f, 1f)
                    if (t2 > 0f) { c.save(); c.clipRect(0f, y1, g2, bh); c.drawText(a, pad * 0.8f - (1f - t2) * g2 * 0.5f, y1 + pad * 0.35f - f2.ascent, p2); c.restore() }
                }
                b
            }
            "daktilo" -> {  // koyu kutuda harf harf yazılır, yanıp sönmeyen renkli imleç
                val u = kisalt(satirlar.joinToString(" "), p1)
                val imW = px * 0.14f
                val bw = p1.measureText(u) + pad * 2 + imW * 2; val bh = h1 + pad * 1.2f
                val b = bmp(bw, bh); val c = Canvas(b)
                val za = ara(0f, 0.12f)
                if (za > 0f) { boya.color = 0xE6121212.toInt(); boya.alpha = (za * 230).toInt(); c.drawRoundRect(RectF(0f, 0f, bw, bh), bh * 0.22f, bh * 0.22f, boya); boya.alpha = 255 }
                val n = (u.length * ((f - 0.1f) / 0.85f).coerceIn(0f, 1f)).roundToInt().coerceIn(0, u.length)
                val parca = u.substring(0, n)
                val ty = pad * 0.6f - f1.ascent
                c.drawText(parca, pad, ty, p1)
                if (za > 0.5f) {
                    val ix = pad + p1.measureText(parca) + imW * 0.4f
                    boya.color = vurgu; c.drawRect(ix, ty + f1.ascent * 0.92f, ix + imW, ty + f1.descent * 0.6f, boya)
                }
                b
            }
            "ciftkutu" -> { // iki kutu sırayla soldan kayar: üst renkli (beyaz yazı), alt beyaz (koyu yazı)
                val u = kisalt(satirlar[0], p1)
                val p2 = TextPaint(p1).apply { textSize = px * 0.72f; color = 0xFF111111.toInt() }
                val f2 = p2.fontMetrics; val h2 = f2.descent - f2.ascent
                val a = if (satirlar.size > 1) kisalt(satirlar.drop(1).joinToString(" "), p2) else ""
                val g1 = p1.measureText(u) + pad * 2; val y1 = h1 + pad
                val g2 = if (a.isEmpty()) 0f else p2.measureText(a) + pad * 1.8f; val y2 = if (a.isEmpty()) 0f else h2 + pad * 0.8f
                val ofs = pad * 0.9f
                val bw = maxOf(g1, ofs + g2); val bh = y1 + y2
                val b = bmp(bw, bh); val c = Canvas(b)
                val k1 = ara(0f, 0.55f)
                if (k1 > 0f) {
                    val dx = -(1f - k1) * g1
                    c.save(); c.translate(dx, 0f); boya.color = vurgu; c.drawRect(0f, 0f, g1, y1, boya); c.drawText(u, pad, pad * 0.5f - f1.ascent, p1); c.restore()
                }
                if (a.isNotEmpty()) {
                    val k2 = ara(0.35f, 0.9f)
                    if (k2 > 0f) {
                        val dx = -(1f - k2) * (ofs + g2)
                        c.save(); c.translate(dx, 0f); boya.color = 0xFFFFFFFF.toInt(); c.drawRect(ofs, y1, ofs + g2, bh, boya)
                        c.drawText(a, ofs + pad * 0.9f, y1 + pad * 0.4f - f2.ascent, p2); c.restore()
                    }
                }
                b
            }
            else -> {       // altcizgi: büyük yazı aşağıdan yükselir, alt çizgi ortadan iki yana uzar, 2. satır belirir
                val u = kisalt(satirlar[0], p1)
                p1.setShadowLayer(px * 0.16f, 0f, px * 0.05f, 0x99000000.toInt())
                val p2 = TextPaint(p1).apply { textSize = px * 0.55f; typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setShadowLayer(px * 0.1f, 0f, px * 0.04f, 0xCC000000.toInt()) }
                val f2 = p2.fontMetrics; val h2 = f2.descent - f2.ascent
                val a = if (satirlar.size > 1) kisalt(satirlar.drop(1).joinToString(" "), p2) else ""
                val w1 = p1.measureText(u); val w2 = if (a.isEmpty()) 0f else p2.measureText(a)
                val cz = px * 0.13f
                val bw = maxOf(w1, w2) + pad * 2; val bh = pad * 0.6f + h1 + cz * 2.6f + (if (a.isEmpty()) 0f else h2) + pad * 0.6f
                val b = bmp(bw, bh); val c = Canvas(b)
                val yuk = ara(0f, 0.55f)
                val ust = pad * 0.6f; val alt = ust + h1
                if (yuk > 0f) {
                    c.save(); c.clipRect(0f, 0f, bw, alt + cz * 0.4f)
                    c.drawText(u, (bw - w1) / 2f, ust - f1.ascent + (1f - yuk) * h1, p1)
                    c.restore()
                }
                val cy = alt + cz * 0.6f
                val ci = ara(0.35f, 0.85f)
                if (ci > 0f) { boya.color = vurgu; val yarim = w1 / 2f * ci; c.drawRoundRect(RectF(bw / 2f - yarim, cy, bw / 2f + yarim, cy + cz), cz / 2f, cz / 2f, boya) }
                if (a.isNotEmpty()) {
                    val ga = ara(0.6f, 1f)
                    if (ga > 0f) { p2.alpha = (ga * 255).toInt(); c.drawText(a, (bw - w2) / 2f, cy + cz * 2f - f2.ascent, p2) }
                }
                b
            }
        }
    }

    private fun metin(o: KatmanOge, W: Int, H: Int): Bitmap? {
        if (o.metin.isBlank()) return null
        if (o.stil != "duz") return stilliMetin(o, W, H)
        val px = (o.olcek * min(W, H)).coerceAtLeast(8f)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = o.renk.toInt(); textSize = px
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            if (!o.kutu) setShadowLayer(px * 0.12f, 0f, px * 0.05f, 0xCC000000.toInt())
        }
        val enGen = (W * 0.92f).toInt().coerceAtLeast(10)
        val lay = StaticLayout.Builder.obtain(o.metin, 0, o.metin.length, p, enGen).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()
        var lw = 1f
        for (i in 0 until lay.lineCount) lw = max(lw, lay.getLineWidth(i))
        val pad = (px * 0.35f).toInt().coerceAtLeast(2)
        val bw = lw.toInt() + pad * 2; val bh = lay.height + pad * 2
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (o.kutu) c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), pad.toFloat(), pad.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        c.save(); c.translate(pad - (enGen - lw) / 2f, pad.toFloat()); lay.draw(c); c.restore()
        return b
    }

    private fun cikartma(o: KatmanOge, W: Int): Bitmap? {
        if (o.metin.isBlank()) return null
        val hedef = (o.olcek * W).coerceIn(16f, W.toFloat())
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = hedef * 0.82f; textAlign = Paint.Align.CENTER }
        val fm = p.fontMetrics
        val b = Bitmap.createBitmap(hedef.toInt(), hedef.toInt(), Bitmap.Config.ARGB_8888)
        Canvas(b).drawText(o.metin, hedef / 2f, hedef / 2f - (fm.ascent + fm.descent) / 2f, p)
        return filtreli(b, o.filtre)
    }

    private fun gorsel(o: KatmanOge, W: Int): Bitmap? {
        if (o.yol.isEmpty()) return null
        val hedefG = (o.olcek * W).roundToInt().coerceAtLeast(8)
        val sinir = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(o.yol, sinir)
        if (sinir.outWidth <= 0) return null
        // kırpılmış genişlik hedefin altına düşmeyecek kadar örnekle
        val kirpG = sinir.outWidth * (1f - o.kirpL - o.kirpR).coerceIn(0.05f, 1f)
        var s = 1
        while (kirpG / (s * 2) >= hedefG) s *= 2
        val kaynak = BitmapFactory.decodeFile(o.yol, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val r = Rect(
            (kaynak.width * o.kirpL).toInt(), (kaynak.height * o.kirpT).toInt(),
            (kaynak.width * (1f - o.kirpR)).toInt(), (kaynak.height * (1f - o.kirpB)).toInt(),
        )
        if (r.width() < 2 || r.height() < 2) return null
        val hedefY = (hedefG.toFloat() * r.height() / r.width()).roundToInt().coerceAtLeast(2)
        val b = Bitmap.createBitmap(hedefG, hedefY, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        filtreMatrisi(o.filtre)?.let { p.colorFilter = ColorMatrixColorFilter(it) }
        Canvas(b).drawBitmap(kaynak, r, Rect(0, 0, hedefG, hedefY), p)
        kaynak.recycle()
        return b
    }

    private fun filtreli(b: Bitmap, f: String): Bitmap {
        val m = filtreMatrisi(f) ?: return b
        val o = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        Canvas(o).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        b.recycle(); return o
    }

    fun filtreMatrisi(f: String): ColorMatrix? = when (f) {
        "sb" -> ColorMatrix().apply { setSaturation(0f) }
        "sepya" -> ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        "canli" -> ColorMatrix().apply { setSaturation(1.45f) }
        "soguk" -> ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 6f, 0f, 0f, 1.12f, 0f, 14f, 0f, 0f, 0f, 1f, 0f))
        "sicak" -> ColorMatrix(floatArrayOf(1.12f, 0f, 0f, 0f, 14f, 0f, 1.02f, 0f, 0f, 4f, 0f, 0f, 0.86f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        else -> null
    }

    /** O andaki görünüm: saydamlık (opaklık dahil), ölçek çarpanı, dikey kayma (karenin oranı, + aşağı). */
    class An(val alfa: Float, val olcek: Float, val dy: Float)
    private val GIZLI = An(0f, 1f, 0f)

    fun an(o: KatmanOge, ms: Long): An {
        if (o.gizli || ms < o.basMs || ms > o.sonMs) return GIZLI
        if (hareketli(o)) return An(o.opaklik, 1f, 0f)   // 3.64: hareket bitmapin kendisinde (kare kare)
        val G = min(500L, ((o.sonMs - o.basMs) / 3).coerceAtLeast(1)).toFloat()
        fun yum(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }
        val gi = yum((ms - o.basMs) / G); val ci = yum((o.sonMs - ms) / G)
        val f = min(gi, ci)
        return when (o.anim) {
            "yok" -> An(o.opaklik, 1f, 0f)
            "kay" -> An(o.opaklik * f, 1f, (1f - f) * 0.08f)
            "buyu" -> An(o.opaklik * f, 0.6f + 0.4f * f, 0f)
            "zipla" -> {
                val g = ((ms - o.basMs) / (G * 1.4f)).coerceIn(0f, 1f)
                val z = if (g < 1f) 1f + 0.18f * sin(g * PI.toFloat()) * (1f - g) - 0.25f * (1f - g) * (1f - g) else 1f
                An(o.opaklik * f, z.coerceAtLeast(0.1f), 0f)
            }
            else -> An(o.opaklik * f, 1f, 0f)
        }
    }

    /** Ekrana görünen (dokunma ile seçilebilir) katmanlar, üstten alta. */
    fun gorunenler(p: Proje, ms: Long): List<KatmanOge> = p.katmanlar.filter { it.gorsel && !it.gizli && ms >= it.basMs && ms <= it.sonMs }.asReversed()
}

/** Dışa aktarma: bir klibe düşen katman bindirmesi (klip içi saatle zaman çizelgesi ms'sine çevrilir). */
class KatmanBindirme(private val o: KatmanOge, private val bmp: Bitmap, private val klipBasMs: Long, private val W: Int = 0, private val H: Int = 0) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    // 3.64: hareketli yazı — kare değişince yeni kare çizilir (yalnız son kare tutulur; bellek şişmez). Bütün kareler aynı boyutta.
    private var sonAdim = -1
    private var sonKare: Bitmap? = null
    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (!KatmanCizim.hareketli(o) || W <= 0 || H <= 0) return bmp
        val a = KatmanCizim.adim(o, klipBasMs + saat.yerelMs(presentationTimeUs))
        if (a >= KatmanCizim.ADIM) return bmp
        if (a != sonAdim || sonKare == null) {
            sonKare = KatmanCizim.bitmap(o, W, H, a)?.takeIf { it.width == bmp.width && it.height == bmp.height } ?: bmp
            sonAdim = a
        }
        return sonKare ?: bmp
    }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = klipBasMs + saat.yerelMs(presentationTimeUs)
        val a = KatmanCizim.an(o, ms)
        // Media3'te döndürme saat yönünün tersine; uygulamada (önizleme) saat yönünde → eksi
        return StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(o.x.coerceIn(0f, 1f) * 2f - 1f, 1f - 2f * (o.y + a.dy).coerceIn(0f, 1f))
            .setOverlayFrameAnchor(0f, 0f)
            .setScale(a.olcek, a.olcek)
            .setRotationDegrees(-o.aci)
            .setAlphaScale(a.alfa.coerceIn(0f, 1f))
            .build()
    }
}

/** Ses katmanı hızı (sabit). */
class SabitHiz(private val hiz: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = hiz
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/** Önizlemede parmakla taşıma/çimdikleme/döndürme sırasında geçici değerler (bırakınca katmana yazılır). */
data class KatmanGecici(val id: String, val x: Float, val y: Float, val carpan: Float, val aci: Float)

/** Dokunulan nokta (W×H referans pikseli) katmanın üstünde mi (döndürme dahil). */
fun katmanIsabet(o: KatmanOge, bw: Int, bh: Int, px: Float, py: Float, W: Int, H: Int): Boolean {
    val dx = px - o.x * W; val dy = py - o.y * H
    val r = Math.toRadians(-o.aci.toDouble())
    val rx = dx * kotlin.math.cos(r) - dy * kotlin.math.sin(r)
    val ry = dx * kotlin.math.sin(r) + dy * kotlin.math.cos(r)
    val pay = 0.03f * min(W, H)
    return kotlin.math.abs(rx) <= bw / 2f + pay && kotlin.math.abs(ry) <= bh / 2f + pay
}

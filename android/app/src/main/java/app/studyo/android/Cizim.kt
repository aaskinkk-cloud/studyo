package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Effect
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.max
import kotlin.math.min

/** Yazı bloğu: bir ya da birkaç satırlık metin, isteğe bağlı zemin rengi ve kontur. */
class Blok(
    val metin: CharSequence, val boy: Float, val renk: Int, val zemin: Int? = null,
    val kalin: Boolean = true, val kontur: Float = 0f, val golge: Int? = 0xAA000000.toInt(),
    val aralik: Float = 0f, val agir: Boolean = false, val parilti: Int? = null,
)

object Cizim {
    private val AGIR: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    private fun boya(b: Blok): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = b.renk; textSize = b.boy; letterSpacing = b.aralik
        typeface = if (b.agir) AGIR else if (b.kalin) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        when {
            b.parilti != null -> setShadowLayer(b.boy * 0.35f, 0f, 0f, b.parilti)
            b.golge != null && b.zemin == null -> setShadowLayer(b.boy * 0.10f, 0f, b.boy * 0.05f, b.golge)
        }
    }

    private fun duzen(m: CharSequence, p: TextPaint, gen: Int, sol: Boolean): StaticLayout =
        StaticLayout.Builder.obtain(m, 0, m.length, p, gen.coerceAtLeast(10))
            .setAlignment(if (sol) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.04f).setIncludePad(false).build()

    private fun genislik(l: StaticLayout): Float {
        var w = 1f
        for (i in 0 until l.lineCount) w = max(w, l.getLineWidth(i))
        return w
    }

    /** Blokları alt alta dizer; her blok kendi zemini ile. */
    fun bloklar(liste: List<Blok>, enGen: Int, sol: Boolean = false, bosluk: Float = 0.25f): Bitmap? {
        val ogeler = liste.filter { it.metin.isNotBlank() }
        if (ogeler.isEmpty()) return null
        class Ol(val b: Blok, val p: TextPaint, val l: StaticLayout, val lw: Float, val pad: Float)
        val olc = ogeler.map { b ->
            val p = boya(b)
            val pad = if (b.zemin != null) b.boy * 0.38f else b.boy * 0.15f + b.kontur
            val l = duzen(b.metin, p, (enGen - 2 * pad).toInt(), sol)
            Ol(b, p, l, genislik(l), pad)
        }
        val gen = olc.maxOf { it.lw + 2 * it.pad }.toInt().coerceAtLeast(2)
        val ara = olc.first().b.boy * bosluk
        val yuk = (olc.sumOf { (it.l.height + 2 * it.pad).toDouble() } + ara * (olc.size - 1)).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(gen, yuk, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        var y = 0f
        for (o in olc) {
            val bw = o.lw + 2 * o.pad
            val bh = o.l.height + 2 * o.pad
            val x0 = if (sol) 0f else (gen - bw) / 2f
            o.b.zemin?.let { z -> c.drawRoundRect(RectF(x0, y, x0 + bw, y + bh), o.b.boy * 0.22f, o.b.boy * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = z }) }
            val enG = o.l.width
            c.save()
            c.translate(x0 + o.pad - if (sol) 0f else (enG - o.lw) / 2f, y + o.pad)
            if (o.b.kontur > 0f) {
                val kp = TextPaint(o.p).apply { style = Paint.Style.STROKE; strokeWidth = o.b.kontur; strokeJoin = Paint.Join.ROUND; color = 0xFF000000.toInt(); clearShadowLayer() }
                duzen(o.b.metin.toString(), kp, enG, sol).draw(c)
            }
            o.l.draw(c)
            c.restore()
            y += bh + ara
        }
        return bmp
    }

    // ---------------- Giriş başlığı ----------------
    fun giris(g: Giris, W: Int, H: Int): Yerlesim? {
        if (!g.acik) return null
        val k = min(W, H).toFloat()
        val bas = g.baslik.replace("|", "\n").trim()
        val alt = g.alt.trim()
        val en = (W * 0.88f).toInt()
        return when (g.stil) {
            "altbant" -> bloklar(listOf(
                Blok(bas, k * 0.055f, 0xFF1B1204.toInt(), zemin = 0xFFF5A524.toInt()),
                Blok(alt, k * 0.036f, 0xFF1B2833.toInt(), zemin = 0xEEFFFFFF.toInt(), kalin = false),
            ), (W * 0.75f).toInt(), sol = true, bosluk = 0.12f)?.let { Yerlesim(it, -1f + 2f * 0.05f, -1f + 2f * 0.12f, -1f, -1f) }
            "kutulu" -> bloklar(listOf(
                Blok(bas, k * 0.07f, 0xFFFFFFFF.toInt(), zemin = 0xAA000000.toInt()),
                Blok(alt, k * 0.038f, 0xFFE6EDF3.toInt(), zemin = 0xAA000000.toInt(), kalin = false),
            ), en)?.let { Yerlesim(it, 0f, 0f, 0f, 0f) }
            "neon" -> bloklar(listOf(
                Blok(bas, k * 0.085f, 0xFFB8FBFF.toInt(), parilti = 0xFF00E5FF.toInt(), aralik = 0.04f),
                Blok(alt, k * 0.04f, 0xFFFFFFFF.toInt(), parilti = 0xFFFF4FD8.toInt(), kalin = false, aralik = 0.12f),
            ), en)?.let { Yerlesim(it, 0f, 0f, 0f, 0f) }
            "bantlar" -> bloklar(listOf(
                Blok(bas, k * 0.065f, 0xFF111111.toInt(), zemin = 0xFFFFD400.toInt(), agir = true),
                Blok(alt, k * 0.042f, 0xFFFFFFFF.toInt(), zemin = 0xFFE53935.toInt()),
            ), en, bosluk = 0.15f)?.let { Yerlesim(it, 0f, -0.3f, 0f, 0f) }
            "minimal" -> bloklar(listOf(
                Blok(bas, k * 0.06f, 0xFFFFFFFF.toInt(), kalin = false, aralik = 0.08f, golge = 0x99000000.toInt()),
                Blok(alt, k * 0.034f, 0xFFF5A524.toInt(), kalin = false, aralik = 0.2f, golge = 0x99000000.toInt()),
            ), en, bosluk = 0.4f)?.let { Yerlesim(it, 0f, -1f + 2f * 0.1f, 0f, -1f) }
            else -> bloklar(listOf(   // sinematik
                Blok(bas, k * 0.085f, 0xFFFFFFFF.toInt(), aralik = 0.06f, agir = true),
                Blok(alt, k * 0.04f, 0xFFE8E8E8.toInt(), kalin = false, aralik = 0.18f),
            ), en)?.let { Yerlesim(it, 0f, 0f, 0f, 0f) }
        }
    }

    // ---------------- Abone hatırlatıcı ----------------
    fun hatirlatici(h: Hatirlatici, W: Int, H: Int): Yerlesim? {
        if (!h.acik) return null
        val k = min(W, H).toFloat()
        return bloklar(listOf(
            Blok("▶  " + h.yazi.ifBlank { "Abone ol" }, k * 0.05f, 0xFFFFFFFF.toInt(), zemin = 0xFFE53935.toInt()),
            Blok(h.kucuk, k * 0.03f, 0xFF1B2833.toInt(), zemin = 0xEEFFFFFF.toInt(), kalin = false),
        ), (W * 0.6f).toInt(), sol = true, bosluk = 0.15f)?.let { Yerlesim(it, -1f + 2f * 0.05f, -1f + 2f * 0.2f, -1f, -1f) }
    }

    // ---------------- Altyazı ----------------
    fun altyazi(metin: String, st: AltyaziStil, W: Int, H: Int): Yerlesim? {
        if (metin.isBlank()) return null
        val k = min(W, H).toFloat()
        val boy = st.boyut * k
        val b = when (st.stil) {
            "kenarli" -> Blok(metin, boy, 0xFFFFFFFF.toInt(), kontur = boy * 0.16f, golge = null)
            "sari" -> Blok(metin, boy, 0xFFFFD400.toInt(), kontur = boy * 0.16f, golge = null)
            else -> Blok(metin, boy, 0xFFFFFFFF.toInt(), zemin = 0xB3000000.toInt(), kalin = false)
        }
        val bmp = bloklar(listOf(b), (W * 0.9f).toInt()) ?: return null
        val m = 0.07f
        return when (st.konum) {
            "ust" -> Yerlesim(bmp, 0f, 1f - 2f * m, 0f, 1f)
            "orta" -> Yerlesim(bmp, 0f, 0f, 0f, 0f)
            else -> Yerlesim(bmp, 0f, -1f + 2f * m, 0f, -1f)
        }
    }

    /** Kısa video altyazısı: kelimeler kalın, kenarlı; o an söylenen kelime vurgu renginde. */
    fun altyaziKelimeli(k: List<Kelime>, idx: Int, st: AltyaziStil, W: Int, H: Int): Yerlesim? {
        if (k.isEmpty()) return null
        val sb = SpannableStringBuilder()
        k.forEachIndexed { i, w ->
            if (i > 0) sb.append(' ')
            val a = sb.length; sb.append(w.metin)
            if (i == idx) sb.setSpan(ForegroundColorSpan(st.vurgu.toInt()), a, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val boy = st.boyut * 1.15f * min(W, H)
        val bmp = bloklar(listOf(Blok(sb, boy, 0xFFFFFFFF.toInt(), kontur = boy * 0.17f, golge = null, agir = true)), (W * 0.86f).toInt()) ?: return null
        val m = 0.12f
        return when (st.konum) {
            "ust" -> Yerlesim(bmp, 0f, 1f - 2f * m, 0f, 1f)
            "orta" -> Yerlesim(bmp, 0f, 0f, 0f, 0f)
            else -> Yerlesim(bmp, 0f, -1f + 2f * m, 0f, -1f)
        }
    }

    /** O an gösterilecek altyazı (satır + kelime vurgusu) — önizleme ve çıktı aynı fonksiyonu kullanır. */
    fun altyaziAn(satirlar: List<Altyazi>, ms: Long): Pair<Altyazi, Int>? {
        val s = satirlar.firstOrNull { ms >= it.basMs && ms < it.sonMs && it.metin.isNotBlank() } ?: return null
        val idx = if (s.kelimeler.isEmpty()) -1 else s.kelimeler.indexOfLast { it.basMs <= ms }.coerceAtLeast(0)
        return s to idx
    }

    fun altyaziCiz(s: Altyazi, idx: Int, st: AltyaziStil, W: Int, H: Int): Yerlesim? =
        if (st.stil == "kisa" && s.kelimeler.isNotEmpty()) altyaziKelimeli(s.kelimeler, idx, st, W, H) else altyazi(s.metin, st, W, H)

    private fun yuvarlakLogo(l: Logo?, cap: Int): Bitmap? {
        if (l == null || l.yol.isEmpty()) return null
        val k = BitmapFactory.decodeFile(l.yol) ?: return null
        val s = cap.toFloat() / min(k.width, k.height)
        val o = Bitmap.createBitmap(cap, cap, Bitmap.Config.ARGB_8888)
        val sh = BitmapShader(k, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        sh.setLocalMatrix(Matrix().apply { setScale(s, s); postTranslate(-(k.width * s - cap) / 2f, -(k.height * s - cap) / 2f) })
        Canvas(o).drawCircle(cap / 2f, cap / 2f, cap / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sh })
        return o
    }

    // ---------------- Kapanış kartı (aydınlık; karanlık bitiş yok) ----------------
    fun kapanis(kp: Kapanis, logo: Logo?, W: Int, H: Int): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val k = min(W, H).toFloat()
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(0xFF2EC5D3.toInt(), 0xFF6FB7C9.toInt(), 0xFFF5A524.toInt()), null, Shader.TileMode.CLAMP)
        })
        var y = H * (if (H > W) 0.26f else 0.14f)
        yuvarlakLogo(logo, (k * 0.3f).toInt())?.let { lg ->
            c.drawCircle(W / 2f, y + lg.height / 2f, lg.height / 2f + k * 0.012f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
            c.drawBitmap(lg, (W - lg.width) / 2f, y, null)
            y += lg.height + k * 0.05f
        }
        bloklar(listOf(
            Blok(kp.kanal, k * 0.075f, 0xFFFFFFFF.toInt(), agir = true, golge = 0x66000000),
            Blok(kp.yazi, k * 0.045f, 0xFFFFFFFF.toInt(), kalin = false, golge = 0x66000000),
        ), (W * 0.9f).toInt())?.let { t -> c.drawBitmap(t, (W - t.width) / 2f, y, null); y += t.height + k * 0.06f }
        if (kp.stil == "cta") {
            val etiketler = listOf("👍 Beğen" to 0xFFFFFFFF.toInt(), "🔔 Abone ol" to 0xFFE53935.toInt(), "↗ Paylaş" to 0xFFFFFFFF.toInt())
            var boy = k * 0.045f
            fun haplar(): List<Bitmap> = etiketler.mapNotNull { (m, z) ->
                bloklar(listOf(Blok(m, boy, if (z == 0xFFE53935.toInt()) 0xFFFFFFFF.toInt() else 0xFF1B2833.toInt(), zemin = z)), W)
            }
            var hp = haplar()
            val aralik = k * 0.03f
            while (hp.sumOf { it.width } + aralik * 2 > W * 0.94f && boy > k * 0.02f) { boy *= 0.88f; hp = haplar() }
            var x = (W - (hp.sumOf { it.width } + aralik * 2)) / 2f
            for (h in hp) { c.drawBitmap(h, x, y, null); x += h.width + aralik }
        }
        return b
    }

    // ---------------- Kapak (küçük resim) ----------------
    fun kapakBoyut(bicim: String): Pair<Int, Int> = when (bicim) { "9:16" -> 1080 to 1920; "1:1" -> 1080 to 1080; else -> 1280 to 720 }

    /** *yıldız* içindeki kelimeler vurgu renginde. */
    private fun vurgulu(metin: String, @Suppress("UNUSED_PARAMETER") taban: Int, vurgu: Int): CharSequence {
        val sb = SpannableStringBuilder()
        var acik = false
        var bas = 0
        for (ch in metin) {
            if (ch == '*') {
                if (acik) sb.setSpan(ForegroundColorSpan(vurgu), bas, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) else bas = sb.length
                acik = !acik
            } else sb.append(if (ch == '|') '\n' else ch)
        }
        if (acik) sb.setSpan(ForegroundColorSpan(vurgu), bas, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    fun kapak(kp: Kapak, kaynak: Bitmap?, logo: Logo?, W: Int, H: Int): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (kaynak != null) {
            val s = max(W.toFloat() / kaynak.width, H.toFloat() / kaynak.height)
            val sw = (W / s).toInt(); val sh = (H / s).toInt()
            val sx = (kaynak.width - sw) / 2; val sy = (kaynak.height - sh) / 2
            val p = Paint(Paint.FILTER_BITMAP_FLAG)
            if (kp.canli) p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(1.35f)
                val kt = 1.08f; val o = -0.04f * 255
                postConcat(ColorMatrix(floatArrayOf(kt, 0f, 0f, 0f, o, 0f, kt, 0f, 0f, o, 0f, 0f, kt, 0f, o, 0f, 0f, 0f, 1f, 0f)))
            })
            c.drawBitmap(kaynak, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, W, H), p)
            if (kp.canli) c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = RadialGradient(W / 2f, H / 2f, max(W, H) * 0.75f, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            })
        } else {
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), 0xFF243444.toInt(), 0xFF2EC5D3.toInt(), Shader.TileMode.CLAMP) })
        }
        val k = min(W, H).toFloat()
        val boy = kp.boyut * k
        val vurgu = kp.vurgu.toInt()
        if (kp.stil.startsWith("r_")) {
            Reels.statik(kp.stil, kp.yazi, kp.bant, W, H)?.let { t ->
                val m = k * 0.06f
                val y = when (kp.konum) { "ust" -> m; "orta" -> (H - t.height) / 2f; else -> H - t.height - m }
                c.drawBitmap(t, W * 0.06f, y, null)
            }
            if (kp.logo) yuvarlakLogo(logo, (k * 0.16f).toInt())?.let { lg -> c.drawBitmap(lg, W - lg.width - k * 0.04f, k * 0.04f, null) }
            return b
        }
        val blok = when (kp.stil) {
            "kutulu" -> Blok(vurgulu(kp.yazi, 0xFFFFFFFF.toInt(), vurgu), boy, 0xFFFFFFFF.toInt(), zemin = 0xCC000000.toInt(), agir = true)
            "bant" -> Blok(vurgulu(kp.yazi, 0xFF111111.toInt(), 0xFFE53935.toInt()), boy, 0xFF111111.toInt(), zemin = vurgu, agir = true)
            else -> Blok(vurgulu(kp.yazi, 0xFFFFFFFF.toInt(), vurgu), boy, 0xFFFFFFFF.toInt(), kontur = boy * 0.14f, agir = true, golge = 0xCC000000.toInt())
        }
        bloklar(listOf(blok), (W * 0.92f).toInt())?.let { t ->
            val m = k * 0.06f
            val y = when (kp.konum) { "ust" -> m; "orta" -> (H - t.height) / 2f; else -> H - t.height - m }
            c.drawBitmap(t, (W - t.width) / 2f, y, null)
        }
        if (kp.logo) yuvarlakLogo(logo, (k * 0.16f).toInt())?.let { lg ->
            val m = k * 0.04f
            c.drawBitmap(lg, m, m, null)
        }
        return b
    }
}

/** Zaman çizelgesindeki bir aralıkta görünen öğe (giriş başlığı, hatırlatıcı). Yumuşak giriş/çıkış: 300 ms. */
object Zaman {
    fun solma(ms: Long, a: Long, b: Long, yumusak: Long = 300): Float {
        if (ms < a || ms > b) return 0f
        return min(1f, min((ms - a).toFloat() / yumusak, (b - ms).toFloat() / yumusak)).coerceIn(0f, 1f)
    }

    /** Giriş başlığı ve hatırlatıcının zaman aralıkları (ms). */
    fun girisAralik(p: Proje): Pair<Long, Long> = 200L to (200L + p.giris.sureMs).coerceAtMost(p.toplamMs)
    fun hatirlaticiAralik(p: Proje): Pair<Long, Long> {
        val a = (p.klipMs * p.hatirlatici.anOran).toLong().coerceIn(0, (p.toplamMs - 4000).coerceAtLeast(0))
        return a to a + 4000
    }
}

/** Kare zamanını klibin kendi zamanına çevirir (ilk karenin damgası sıfır kabul edilir). */
class Saat { private var t0 = -1L; fun yerelMs(t: Long): Long { if (t0 < 0) t0 = t; return (t - t0) / 1000 } }

/** Zaman çizelgesinde belli aralıkta görünen bindirme (her klibe ayrı örnek). */
class ZamanliBindirme(private val y: Yerlesim, private val klipBasMs: Long, private val a: Long, private val b: Long) : BitmapOverlay() {
    private val saat = Saat()
    override fun getBitmap(presentationTimeUs: Long): Bitmap = y.bmp
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val ms = klipBasMs + saat.yerelMs(presentationTimeUs)
        return StaticOverlaySettings.Builder().setBackgroundFrameAnchor(y.zx, y.zy).setOverlayFrameAnchor(y.kx, y.ky)
            .setAlphaScale(y.alfa * Zaman.solma(ms, a, b)).build()
    }
}

/** Altyazı: o andaki satırın bitmapini verir; satır yoksa görünmez. */
class AltyaziBindirme(private val klipBasMs: Long, private val satirlar: List<Altyazi>, private val st: AltyaziStil, private val W: Int, private val H: Int) : BitmapOverlay() {
    private val saat = Saat()
    private val bos: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val onbellek = HashMap<String, Yerlesim?>()
    private var simdiki: Yerlesim? = null
    private fun guncelle(t: Long) {
        val ms = klipBasMs + saat.yerelMs(t)
        val an = Cizim.altyaziAn(satirlar, ms)
        simdiki = an?.let { (s, i) ->
            val anahtar = if (st.stil == "kisa") "${s.id}#$i" else s.id
            if (onbellek.size > 40) onbellek.clear()
            onbellek.getOrPut(anahtar) { Cizim.altyaziCiz(s, i, st, W, H) }
        }
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap { guncelle(presentationTimeUs); return simdiki?.bmp ?: bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        guncelle(presentationTimeUs)
        val y = simdiki
        return if (y == null) StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        else StaticOverlaySettings.Builder().setBackgroundFrameAnchor(y.zx, y.zy).setOverlayFrameAnchor(y.kx, y.ky).build()
    }
}

/** Geçişler: klibin başında/sonunda kararma, beyaz parlama ya da yakınlaşma. Son klibin sonu asla karartılmaz. */
object Gecisler {
    class An(val renk: Float, val beyaz: Boolean, val olcek: Float)
    private val YOK = An(0f, false, 1f)

    /** yerel: klip içi ms. giris/cikis: bu klibin başında/sonunda geçiş var mı. siyahtan: videonun ilk klibi siyahtan açılır. */
    fun hesapla(g: Gecis, yerel: Long, sure: Long, giris: Boolean, cikis: Boolean, siyahtan: Boolean): An {
        val L = (g.sureMs / 2).coerceAtLeast(100)
        if (siyahtan && yerel < 900) return An(1f - yerel / 900f, false, 1f)
        if (g.tur == "yok") return YOK
        var k = 0f
        if (giris && yerel < L) k = max(k, 1f - yerel.toFloat() / L)
        if (cikis && yerel > sure - L) k = max(k, (yerel - (sure - L)).toFloat() / L)
        k = k.coerceIn(0f, 1f)
        return when (g.tur) {
            "siyah" -> An(k, false, 1f)
            "beyaz" -> An(k, true, 1f)
            "yakin" -> An(k * k * 0.7f, true, 1f + 0.35f * k)
            else -> YOK
        }
    }

    /** Üst üste binen geçiş pencereleri: (giden klip sırası, başlangıç ms, süre ms). Çıktı ve önizleme aynı listeyi kullanır. */
    fun pencereler(p: Proje): List<Triple<Int, Long, Long>> {
        if (!p.gecis.cakisma) return emptyList()
        val sureler = p.klipler.map { it.sureMs } + if (p.kapanisMs > 0) listOf(p.kapanisMs) else emptyList()
        val n = sureler.size
        if (n < 2) return emptyList()
        val baslar = LongArray(n); var t = 0L
        for (i in 0 until n) { baslar[i] = t; t += sureler[i] }
        val toplam = t
        val o = ArrayList<Triple<Int, Long, Long>>()
        var imlec = 0L
        for (i in 0 until minOf(n - 1, p.klipler.size)) {
            val b0 = baslar[i + 1]
            val sonraki = if (i + 2 < n) baslar[i + 2] else toplam
            val T = minOf(p.gecis.sureMs, sureler[i + 1] - 100, sonraki - b0 - 100)
            if (T < 150 || b0 < imlec + 1) continue
            o += Triple(i, b0, T)
            imlec = b0 + T
        }
        return o
    }

    /** Üst üste binen geçişte üst katmanın (giden klibin kuyruğu) durumu; f: 0→1. */
    class Ust(val alfa: Float, val zx: Float, val kx: Float, val olcek: Float)
    fun ustKatman(tur: String, f: Float): Ust {
        val e = f * f * (3f - 2f * f)
        return when (tur) {
            "kaydir" -> Ust(1f, -e, e, 1f)                       // sola kayarak çıkar
            "yakincapraz" -> Ust(1f - e, 0f, 0f, 1f + 0.25f * e)  // büyüyerek solar
            else -> Ust(1f - e, 0f, 0f, 1f)                       // çapraz
        }
    }

    /** Önizleme için: zaman çizelgesindeki bir an. */
    fun onizleme(p: Proje, ms: Long): An {
        val n = p.klipler.size + if (p.kapanisMs > 0) 1 else 0
        if (n == 0) return YOK
        var t = ms; var i = 0
        while (i < n) {
            val d = if (i < p.klipler.size) p.klipler[i].sureMs else p.kapanisMs
            if (t < d || i == n - 1) return hesapla(p.gecis, t, d, i > 0, i < n - 1, i == 0 && p.gecis.siyahtanAc)
            t -= d; i++
        }
        return YOK
    }

    fun etkiler(g: Gecis, sure: Long, giris: Boolean, cikis: Boolean, siyahtan: Boolean): List<Effect> {
        if (g.tur == "yok" && !siyahtan) return emptyList()
        val saat = Saat()
        val e = ArrayList<Effect>()
        e += RgbMatrix { t, _ ->
            val a = hesapla(g, saat.yerelMs(t), sure, giris, cikis, siyahtan)
            val s = 1f - a.renk
            val o = if (a.beyaz) a.renk else 0f
            floatArrayOf(s, 0f, 0f, 0f, 0f, s, 0f, 0f, 0f, 0f, s, 0f, o, o, o, 1f)
        }
        if (g.tur == "yakin") e += MatrixTransformation { t ->
            val a = hesapla(g, saat.yerelMs(t), sure, giris, cikis, siyahtan)
            Matrix().apply { setScale(a.olcek, a.olcek) }
        }
        return e
    }
}

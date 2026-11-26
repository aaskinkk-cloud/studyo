package app.studyo.android

import android.graphics.Bitmap
import androidx.media3.effect.SingleColorLut
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 3.49: klip renk ve ışık ayarı. Hepsi -1…1, 0 = değişiklik yok. HSL: 8 renk bandı için ton / doygunluk / ışık. */
@Serializable
data class RenkAyar(
    val parlaklik: Float = 0f, val kontrast: Float = 0f, val doygunluk: Float = 0f, val sicaklik: Float = 0f,
    val vurgular: Float = 0f, val golgeler: Float = 0f, val ton: Float = 0f,
    val beyazlar: Float = 0f, val keskinlik: Float = 0f,   // 3.53: beyaz alanlar (eksi = azalt) · keskinlik 0…1 (GL keskinleştirme, LUT dışı)
    val hslTon: List<Float> = List(8) { 0f }, val hslDoy: List<Float> = List(8) { 0f }, val hslIsik: List<Float> = List(8) { 0f },
) {
    val bos: Boolean get() = listOf(parlaklik, kontrast, doygunluk, sicaklik, vurgular, golgeler, ton, beyazlar, keskinlik).all { abs(it) < 0.005f } &&
        (hslTon + hslDoy + hslIsik).all { abs(it) < 0.005f }
}

/** Renk dönüşümü tek yerde: dışa aktarma/akıcı önizleme 3B LUT (Media3 SingleColorLut) ile, ayar panelindeki
 *  önce/sonra görüntüsü aynı fonksiyonla piksel piksel — ikisi birebir aynı sonucu verir. */
object RenkLut {
    /** HSL bant merkezleri (derece): kırmızı, turuncu, sarı, yeşil, camgöbeği, mavi, mor, pembe */
    val BANT = floatArrayOf(0f, 30f, 60f, 120f, 180f, 225f, 275f, 320f)
    val BANT_RENK = longArrayOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60)
    val BANT_AD = listOf("Kırmızı", "Turuncu", "Sarı", "Yeşil", "Camgöbeği", "Mavi", "Mor", "Pembe")

    private fun ss(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    /** r,g,b 0…1 → ayarlanmış 0…1 (çıkış dizisine yazar) */
    fun donustur(a: RenkAyar, r0: Float, g0: Float, b0: Float, o: FloatArray) {
        var r = r0; var g = g0; var b = b0
        // --- HSL (bant bazlı + genel ton) ---
        val hslVar = abs(a.ton) > 0.005f || (a.hslTon + a.hslDoy + a.hslIsik).any { abs(it) > 0.005f }
        if (hslVar) {
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val l = (mx + mn) / 2f; val d = mx - mn
            var h = 0f; var s = 0f
            if (d > 1e-5f) {
                s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
                h = when (mx) { r -> ((g - b) / d + (if (g < b) 6f else 0f)); g -> ((b - r) / d + 2f); else -> ((r - g) / d + 4f) } * 60f
            }
            var hh = h + a.ton * 30f; var s2 = s; var l2 = l
            for (i in 0 until 8) {
                var dh = abs(h - BANT[i]); if (dh > 180f) dh = 360f - dh
                val w = (1f - dh / 40f).coerceIn(0f, 1f) * min(1f, s * 3f)      // gri tonlar etkilenmez
                if (w <= 0f) continue
                hh += w * a.hslTon[i] * 25f
                s2 *= 1f + w * a.hslDoy[i]
                l2 += w * a.hslIsik[i] * 0.25f
            }
            hh = ((hh % 360f) + 360f) % 360f; s2 = s2.coerceIn(0f, 1f); l2 = l2.coerceIn(0f, 1f)
            val q = if (l2 < 0.5f) l2 * (1 + s2) else l2 + s2 - l2 * s2; val p = 2 * l2 - q
            fun t2(t0: Float): Float { var t = t0; if (t < 0) t += 1f; if (t > 1) t -= 1f
                return when { t < 1f / 6 -> p + (q - p) * 6 * t; t < 0.5f -> q; t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6; else -> p } }
            val hn = hh / 360f
            r = t2(hn + 1f / 3); g = t2(hn); b = t2(hn - 1f / 3)
        }
        // --- sıcaklık ---
        r += a.sicaklik * 0.08f; b -= a.sicaklik * 0.08f
        fun kanal(x0: Float): Float {
            var x = x0.coerceIn(0f, 1f)
            if (abs(a.parlaklik) > 0.005f) x = x.pow(1f - a.parlaklik * 0.5f)
            x = (x - 0.5f) * (1f + a.kontrast * 0.8f) + 0.5f
            x += a.vurgular * 0.35f * ss(0.45f, 1f, x)          // eksi = patlayan parlak alanları geri çeker
            x += a.golgeler * 0.3f * (1f - ss(0f, 0.5f, x))
            x += a.beyazlar * 0.25f * ss(0.7f, 1f, x)            // 3.53: yalnız en parlak (beyaz) alanlar
            return x
        }
        r = kanal(r); g = kanal(g); b = kanal(b)
        if (abs(a.doygunluk) > 0.005f) {
            val y = 0.299f * r + 0.587f * g + 0.114f * b; val k = 1f + a.doygunluk
            r = y + (r - y) * k; g = y + (g - y) * k; b = y + (b - y) * k
        }
        o[0] = r.coerceIn(0f, 1f); o[1] = g.coerceIn(0f, 1f); o[2] = b.coerceIn(0f, 1f)
    }

    private val onbellek = HashMap<RenkAyar, SingleColorLut>()

    /** Media3 etkisi (33³ LUT). Aynı ayar için önbellekten. */
    /** Renk dönüşümü var mı (keskinlik LUT'a girmez). */
    fun lutVar(a: RenkAyar): Boolean = !a.copy(keskinlik = 0f).bos

    /** 3.59: filtreler (Efekt › Filtre). o: renk ayarından çıkan değer; güç kadar karıştırılır. */
    val FILTRELER = listOf("orangeteal" to "Orange & Teal", "sinematik" to "Sinematik", "altin" to "Altın saat", "vintage" to "Vintage", "sb" to "Siyah-beyaz", "serin" to "Serin mat")
    fun filtrele(tur: String, guc: Float, o: FloatArray) {
        if (tur == "yok" || guc <= 0.001f) return
        // 4.29: üst üste filtreler — "a:0.80|b:0.15" sırayla, her biri kendi gücüyle (biri diğerini silmez)
        if (':' in tur) { tur.split('|').forEach { p -> val ad = p.substringBefore(':'); val g = p.substringAfter(':').toFloatOrNull() ?: 0f; filtrele(ad, g * guc, o) }; return }
        val r = o[0]; val g = o[1]; val b = o[2]
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        var fr: Float; var fg: Float; var fb: Float
        when (tur) {
            "sinematik" -> {   // gölgeler camgöbeği, aydınlıklar turuncu, hafif kontrast
                val t = ss(0.15f, 0.85f, y)
                fr = r + (1f - t) * -0.06f + t * 0.08f; fg = g + (1f - t) * 0.02f + t * 0.02f; fb = b + (1f - t) * 0.08f + t * -0.08f
                fr = (fr - 0.5f) * 1.12f + 0.5f; fg = (fg - 0.5f) * 1.12f + 0.5f; fb = (fb - 0.5f) * 1.12f + 0.5f
            }
            "altin" -> { fr = r * 1.08f + 0.04f; fg = g * 1.02f + 0.02f; fb = b * 0.86f; val yy = 0.299f * fr + 0.587f * fg + 0.114f * fb
                fr = yy + (fr - yy) * 1.15f; fg = yy + (fg - yy) * 1.15f; fb = yy + (fb - yy) * 1.15f }
            "vintage" -> { fr = y + (r - y) * 0.65f; fg = y + (g - y) * 0.65f; fb = y + (b - y) * 0.65f
                fr = 0.08f + fr * 0.86f + 0.04f; fg = 0.07f + fg * 0.86f + 0.02f; fb = 0.06f + fb * 0.80f }
            "sb" -> { val v = ((y - 0.5f) * 1.12f + 0.5f); fr = v; fg = v; fb = v }
            "orangeteal" -> {
                // 4.29: profesyonel Orange & Teal — gölgeler camgöbeği/mavi, ten tonları ve aydınlıklar sıcak turuncu; orta tonlar korunur,
                // ten (kırmızı-sarı) bölgesi kaymasın diye maskeli; hafif S eğrisi ve doygunluk dengesi (doğal, abartısız)
                val t = ss(0.12f, 0.88f, y)
                val sicak = ((r - b) * 2.2f).coerceIn(0f, 1f)                 // ten / sıcak bölge maskesi
                val golge = (1f - t) * (1f - sicak * 0.6f)
                val isik = t * (0.6f + sicak * 0.4f)
                fr = r - golge * 0.07f + isik * 0.07f
                fg = g + golge * 0.025f + isik * 0.015f
                fb = b + golge * 0.09f - isik * 0.075f
                val yy = 0.299f * fr + 0.587f * fg + 0.114f * fb
                fr = yy + (fr - yy) * 1.08f; fg = yy + (fg - yy) * 1.02f; fb = yy + (fb - yy) * 1.08f
                fr = (fr - 0.5f) * 1.06f + 0.5f; fg = (fg - 0.5f) * 1.06f + 0.5f; fb = (fb - 0.5f) * 1.06f + 0.5f
            }
            else -> { fr = y + (r - y) * 0.85f; fg = y + (g - y) * 0.85f; fb = y + (b - y) * 0.85f   // serin mat
                fr = 0.06f + fr * 0.88f - 0.03f; fg = 0.06f + fg * 0.88f; fb = 0.06f + fb * 0.88f + 0.05f }
        }
        val k = guc.coerceIn(0f, 1f)
        o[0] = (r + (fr - r) * k).coerceIn(0f, 1f); o[1] = (g + (fg - g) * k).coerceIn(0f, 1f); o[2] = (b + (fb - b) * k).coerceIn(0f, 1f)
    }

    private val onbellekF = HashMap<Triple<RenkAyar, String, Float>, SingleColorLut>()
    /** 3.59: renk ayarı + filtre tek LUT'ta. */
    @Synchronized fun etki(a0: RenkAyar, filtre: String, guc: Float): SingleColorLut {
        if (filtre == "yok" || guc <= 0.001f) return etki(a0)
        val a = a0.copy(keskinlik = 0f)
        return onbellekF.getOrPut(Triple(a, filtre, guc)) {
            val n = 33; val o = FloatArray(3)
            val kup = Array(n) { ri -> Array(n) { gi -> IntArray(n) { bi ->
                donustur(a, ri / (n - 1f), gi / (n - 1f), bi / (n - 1f), o); filtrele(filtre, guc, o)
                (0xFF shl 24) or ((o[0] * 255 + 0.5f).toInt() shl 16) or ((o[1] * 255 + 0.5f).toInt() shl 8) or (o[2] * 255 + 0.5f).toInt()
            } } }
            if (onbellekF.size > 40) onbellekF.clear()
            SingleColorLut.createFromCube(kup)
        }
    }

    @Synchronized fun etki(a0: RenkAyar): SingleColorLut { val a = a0.copy(keskinlik = 0f); return onbellek.getOrPut(a) {
        val n = 33; val o = FloatArray(3)
        val kup = Array(n) { ri -> Array(n) { gi -> IntArray(n) { bi ->
            donustur(a, ri / (n - 1f), gi / (n - 1f), bi / (n - 1f), o)
            (0xFF shl 24) or ((o[0] * 255 + 0.5f).toInt() shl 16) or ((o[1] * 255 + 0.5f).toInt() shl 8) or (o[2] * 255 + 0.5f).toInt()
        } } }
        if (onbellek.size > 40) onbellek.clear()
        SingleColorLut.createFromCube(kup)
    } }

    /** Önce/sonra görüntüsü için bitmap'e uygular (küçük görüntü). */
    fun uygula(src: Bitmap, a: RenkAyar, filtre: String = "yok", guc: Float = 0f): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val o = FloatArray(3)
        for (i in px.indices) {
            val c = px[i]
            donustur(a, ((c shr 16) and 255) / 255f, ((c shr 8) and 255) / 255f, (c and 255) / 255f, o)
            filtrele(filtre, guc, o)
            px[i] = (c and -0x1000000) or ((o[0] * 255 + 0.5f).toInt() shl 16) or ((o[1] * 255 + 0.5f).toInt() shl 8) or (o[2] * 255 + 0.5f).toInt()
        }
        if (a.keskinlik > 0.005f) keskinlestir(px, w, h, a.keskinlik)
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 3.53: önce/sonra görüntüsü için keskinleştirme — Keskinlik GL etkisiyle aynı formül (4 komşu, güç = k · 1,6). */
    private fun keskinlestir(px: IntArray, w: Int, h: Int, k: Float) {
        val s = px.copyOf(); val g = k * 1.6f
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x; val c = s[i]
            var o = c and -0x1000000
            for (sh in intArrayOf(16, 8, 0)) {
                val m = (c shr sh) and 255
                val kom = ((s[i - 1] shr sh) and 255) + ((s[i + 1] shr sh) and 255) + ((s[i - w] shr sh) and 255) + ((s[i + w] shr sh) and 255)
                val v = (m + g * (m - kom / 4f)).toInt().coerceIn(0, 255)
                o = o or (v shl sh)
            }
            px[i] = o
        }
    }
}

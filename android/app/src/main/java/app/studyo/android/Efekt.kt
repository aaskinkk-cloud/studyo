package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 3.59: Efekt › Efekt — klip boyunca süren görüntü efektleri. Dışa aktarma ve akıcı önizleme: GL (EfektEtki);
 *  küçük resimler ve durağan önizleme: aynı formüllerin işlemci karşılığı (Efektler.uygula). */
object Efektler {
    val LISTE = listOf("vinyet" to "Vinyet", "gren" to "Film greni", "sinema" to "Sinema", "parilti" to "Parıltı", "vhs" to "Retro VHS")
    fun kod(tur: String) = when (tur) { "vinyet" -> 1; "gren" -> 2; "sinema" -> 3; "parilti" -> 4; "vhs" -> 5; else -> 0 }

    private fun ss(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }
    private fun rnd(x: Int, y: Int): Float { var h = x * 374761393 + y * 668265263; h = (h xor (h ushr 13)) * 1274126177; return ((h xor (h ushr 16)) and 0xFFFF) / 65535f }

    /** Bitmap'e efekt (küçük resim / durağan önizleme). */
    fun uygula(src: Bitmap, tur: String, guc: Float): Bitmap {
        val m = kod(tur); if (m == 0 || guc <= 0.001f) return src
        val w = src.width; val h = src.height
        val s = IntArray(w * h); src.getPixels(s, 0, w, 0, 0, w, h)
        val o = IntArray(w * h)
        val g = guc.coerceIn(0f, 1f)
        val r = max(1, (min(w, h) * 0.02f).toInt())
        fun ch(c: Int, sh: Int) = ((c shr sh) and 255) / 255f
        fun px(x: Int, y: Int) = s[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]
        for (y in 0 until h) for (x in 0 until w) {
            val c = s[y * w + x]
            var R = ch(c, 16); var G = ch(c, 8); var B = ch(c, 0)
            val u = x / (w - 1f).coerceAtLeast(1f); val v = y / (h - 1f).coerceAtLeast(1f)
            when (m) {
                1 -> { val dx = u - 0.5f; val dy = v - 0.5f; val d = sqrt(dx * dx + dy * dy) * 1.414f; val k = 1f - g * 0.85f * ss(0.35f, 1f, d); R *= k; G *= k; B *= k }
                2 -> { val n = (rnd(x, y) - 0.5f) * 0.22f * g; R += n; G += n; B += n }
                3 -> { if (v < 0.13f * g || v > 1f - 0.13f * g) { R = 0f; G = 0f; B = 0f } }
                4 -> {
                    var br = 0f
                    for ((ox, oy) in arrayOf(r to 0, -r to 0, 0 to r, 0 to -r, r to r, -r to -r, r to -r, -r to r)) {
                        val q = px(x + ox, y + oy); br += max(0f, (ch(q, 16) + ch(q, 8) + ch(q, 0)) / 3f - 0.55f)
                    }
                    val a = br / 8f * 2.2f * g; R += a; G += a * 0.95f; B += a * 0.85f
                }
                5 -> {
                    val d = max(1, (w * 0.004f * g).toInt())
                    R = ch(px(x + d, y), 16); B = ch(px(x - d, y), 0)
                    if ((y / max(1, h / 180)) % 2 == 0) { val k = 1f - 0.12f * g; R *= k; G *= k; B *= k }
                    val yy = 0.299f * R + 0.587f * G + 0.114f * B
                    R = yy + (R - yy) * (1f - 0.25f * g) + 0.03f * g; G = yy + (G - yy) * (1f - 0.25f * g); B = yy + (B - yy) * (1f - 0.25f * g) - 0.02f * g
                }
            }
            o[y * w + x] = (c and -0x1000000) or ((R.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((G.coerceIn(0f, 1f) * 255).toInt() shl 8) or (B.coerceIn(0f, 1f) * 255).toInt()
        }
        return Bitmap.createBitmap(o, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 3.61: arka plan bulanıklığı (durağan önizleme): küçült-büyüt (GL'deki Gauss'a yakın). */
    /** 3.72: arka plan bulanıklığı (durağan önizleme) — dışa aktarmadaki 4 geçişli GL Gauss'un karşılığı: küçültülmüş görüntüde
     *  3 kez kutu bulanıklığı (Gauss'a yakın, basamaksız), yumuşak büyütme ve %10 karartma (öndeki nesne öne çıksın). */
    fun bulaniklastir(src: Bitmap, guc: Float): Bitmap {
        if (guc <= 0.01f) return src
        val sigma = guc.coerceIn(0f, 1f) * min(src.width, src.height) * 0.036f
        val d = (sigma / 2.4f).coerceAtLeast(1f)
        val kw = max(8, (src.width / d).toInt()); val kh = max(8, (src.height / d).toInt())
        val k = Bitmap.createScaledBitmap(src, kw, kh, true)
        val px = IntArray(kw * kh); k.getPixels(px, 0, kw, 0, 0, kw, kh)
        val r = IntArray(kw * kh) { (px[it] shr 16) and 255 }; val g = IntArray(kw * kh) { (px[it] shr 8) and 255 }; val b = IntArray(kw * kh) { px[it] and 255 }
        repeat(3) { kutu(r, kw, kh); kutu(g, kw, kh); kutu(b, kw, kh) }
        for (i in px.indices) px[i] = (0xFF shl 24) or ((r[i] * 0.9f).toInt() shl 16) or ((g[i] * 0.9f).toInt() shl 8) or (b[i] * 0.9f).toInt()
        val kb = Bitmap.createBitmap(px, kw, kh, Bitmap.Config.ARGB_8888)
        return Bitmap.createScaledBitmap(kb, src.width, src.height, true)
    }

    /** Yarıçapı 2 olan yatay + dikey kutu bulanıklığı (kenarda sıkıştırma). */
    private fun kutu(c: IntArray, w: Int, h: Int, rr: Int = 2) {
        val t = IntArray(c.size); val n = 2 * rr + 1
        for (y in 0 until h) { val o = y * w; var s = 0
            for (x in -rr..rr) s += c[o + x.coerceIn(0, w - 1)]
            for (x in 0 until w) { t[o + x] = s / n; s += c[o + (x + rr + 1).coerceAtMost(w - 1)] - c[o + (x - rr).coerceAtLeast(0)] } }
        for (x in 0 until w) { var s = 0
            for (y in -rr..rr) s += t[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) { c[y * w + x] = s / n; s += t[(y + rr + 1).coerceAtMost(h - 1) * w + x] - t[(y - rr).coerceAtLeast(0) * w + x] } }
    }

    /** Geçiş önizleme küçük resmi (k = 0,5 anı): yeni kenar geçişlerinin görünümü. */
    fun gecisOrnek(src: Bitmap, tur: String): Bitmap {
        val w = src.width; val h = src.height
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        c.drawColor(android.graphics.Color.BLACK)
        val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        when (tur) {
            "dondur" -> { c.save(); c.rotate(35f, w / 2f, h / 2f); c.scale(1.25f, 1.25f, w / 2f, h / 2f); c.drawBitmap(src, 0f, 0f, p); c.restore()
                c.drawColor(0x40000000) }
            "dalis" -> { c.save(); c.scale(1.6f, 1.6f, w / 2f, h / 2f); c.drawBitmap(src, 0f, 0f, p); c.restore(); c.drawColor(0x30000000) }
            "bulanik" -> { val kk = Bitmap.createScaledBitmap(src, max(1, w / 14), max(1, h / 14), true)
                c.drawBitmap(kk, null, android.graphics.Rect(0, 0, w, h), p); c.drawColor(0x22FFFFFF) }
            "silme" -> { c.drawBitmap(src, 0f, 0f, p)
                c.drawRect(0f, 0f, w * 0.55f, h.toFloat(), android.graphics.Paint().apply { color = android.graphics.Color.BLACK }) }
            "sizma" -> { c.drawBitmap(src, 0f, 0f, p)
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), android.graphics.Paint().apply {
                    shader = android.graphics.RadialGradient(w * 0.15f, h * 0.2f, max(w, h) * 0.9f, intArrayOf(0xCCFFB040.toInt(), 0x55FF6A00, 0x00000000),
                        floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                }) }
            else -> c.drawBitmap(src, 0f, 0f, p)
        }
        return b
    }
}

/** 3.61: arka plan bulanıklığı — iki geçişli (yatay + dikey) Gauss. 3.72: dört geçiş (H, V, H, V; Kareden Videoya'daki gibi daha
 *  kremsi, basamaksız), yarıçap = güç × kısa kenarın %4'ü; son geçişte karart (0…1) ile hafif karartma — öndeki nesne öne çıkar. */
class BulanikEtki(private val guc: Float, private val yatay: Boolean, private val karart: Float = 0f) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = BulanikProgram(useHdr, guc, yatay, karart)
    companion object {
        /** Bir klibin arka plan bulanıklığı için etkiler (sırayla eklenir). */
        fun hepsi(guc: Float): List<BulanikEtki> = listOf(BulanikEtki(guc, true), BulanikEtki(guc, false), BulanikEtki(guc, true), BulanikEtki(guc, false, 0.10f))
    }
}

private class BulanikProgram(useHdr: Boolean, private val guc: Float, private val yatay: Boolean, private val karart: Float) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(KENAR_VS, FS)
    private var gen = 1; private var yuk = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { gen = inputWidth.coerceAtLeast(1); yuk = inputHeight.coerceAtLeast(1); return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val r = guc.coerceIn(0f, 1f) * min(gen, yuk) * 0.04f
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatUniform("uKarart", karart)
        program.setFloatsUniform("uAdim", if (yatay) floatArrayOf(r / 4f / gen, 0f) else floatArrayOf(0f, r / 4f / yuk))
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    override fun release() { super.release(); try { program.delete() } catch (_: Exception) {} }
    companion object {
        const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uAdim;
uniform float uKarart;
varying vec2 vTexSamplingCoord;
void main() {
  vec2 t = vTexSamplingCoord;
  vec4 s = texture2D(uTexSampler, t) * 0.1585;
  s += (texture2D(uTexSampler, t + uAdim) + texture2D(uTexSampler, t - uAdim)) * 0.1465;
  s += (texture2D(uTexSampler, t + uAdim * 2.0) + texture2D(uTexSampler, t - uAdim * 2.0)) * 0.1162;
  s += (texture2D(uTexSampler, t + uAdim * 3.0) + texture2D(uTexSampler, t - uAdim * 3.0)) * 0.0791;
  s += (texture2D(uTexSampler, t + uAdim * 4.0) + texture2D(uTexSampler, t - uAdim * 4.0)) * 0.0463;
  gl_FragColor = vec4(s.rgb / 0.9347 * (1.0 - uKarart), 1.0);
}
"""
    }
}

/** Efekt GL etkisi (dışa aktarma + akıcı önizleme). Çıkış çözünürlüğünde (Presentation'dan sonra) uygulanır. */
class EfektEtki(private val tur: String, private val guc: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = EfektProgram(useHdr, Efektler.kod(tur), guc)
}

private class EfektProgram(useHdr: Boolean, private val kip: Int, private val guc: Float) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(KENAR_VS, FS)
    private var gen = 1; private var yuk = 1

    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { gen = inputWidth.coerceAtLeast(1); yuk = inputHeight.coerceAtLeast(1); return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uBoy", floatArrayOf(gen.toFloat(), yuk.toFloat()))
        program.setFloatUniform("uKip", kip.toFloat())
        program.setFloatUniform("uGuc", guc.coerceIn(0f, 1f))
        program.setFloatUniform("uZaman", ((presentationTimeUs / 41666L) % 997L).toFloat())   // ~24 kare/sn gren değişimi
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    override fun release() { super.release(); try { program.delete() } catch (_: Exception) {} }

    companion object {
        const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uBoy;
uniform float uKip;
uniform float uGuc;
uniform float uZaman;
varying vec2 vTexSamplingCoord;
float rnd(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
  vec2 t = vTexSamplingCoord;
  vec4 c = texture2D(uTexSampler, t);
  vec3 r = c.rgb;
  float g = uGuc;
  if (uKip < 1.5) {
    float d = length(t - 0.5) * 1.414;
    r *= 1.0 - g * 0.85 * smoothstep(0.35, 1.0, d);
  } else if (uKip < 2.5) {
    float n = (rnd(floor(t * uBoy) + uZaman) - 0.5) * 0.22 * g;
    r += vec3(n);
  } else if (uKip < 3.5) {
    float y = 1.0 - t.y;
    if (y < 0.13 * g || y > 1.0 - 0.13 * g) r = vec3(0.0);
  } else if (uKip < 4.5) {
    float rr = min(uBoy.x, uBoy.y) * 0.02;
    vec2 a = vec2(rr) / uBoy;
    float br = 0.0;
    br += max(0.0, dot(texture2D(uTexSampler, t + vec2(a.x, 0.0)).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t - vec2(a.x, 0.0)).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t + vec2(0.0, a.y)).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t - vec2(0.0, a.y)).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t + a).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t - a).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t + vec2(a.x, -a.y)).rgb, vec3(0.3333)) - 0.55);
    br += max(0.0, dot(texture2D(uTexSampler, t + vec2(-a.x, a.y)).rgb, vec3(0.3333)) - 0.55);
    float k = br / 8.0 * 2.2 * g;
    r += vec3(k, k * 0.95, k * 0.85);
  } else {
    float d = max(1.0, uBoy.x * 0.004 * g) / uBoy.x;
    r.r = texture2D(uTexSampler, t + vec2(d, 0.0)).r;
    r.b = texture2D(uTexSampler, t - vec2(d, 0.0)).b;
    float satir = floor((1.0 - t.y) * uBoy.y / max(1.0, floor(uBoy.y / 180.0)));
    if (mod(satir, 2.0) < 0.5) r *= 1.0 - 0.12 * g;
    float yy = dot(r, vec3(0.299, 0.587, 0.114));
    r = yy + (r - yy) * (1.0 - 0.25 * g) + vec3(0.03 * g, 0.0, -0.02 * g);
  }
  gl_FragColor = vec4(clamp(r, 0.0, 1.0), c.a);
}
"""
    }
}

internal const val KENAR_VS = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

/** 3.59: yeni kenar geçişleri için GL (bulanık · silme · ışık sızması). Geçişin anı, diğer kenar geçişleri gibi Gecisler.hesapla'dan
 *  (siyah/parlama ile aynı zamanlama, aynı Saat). an: kare zamanı (µs) → geçiş durumu. */
class KenarEtki(private val an: (Long) -> Gecisler.An) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = KenarProgram(useHdr, an)
}

private class KenarProgram(useHdr: Boolean, private val an: (Long) -> Gecisler.An) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(KENAR_VS, FS)
    private var gen = 1; private var yuk = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { gen = inputWidth.coerceAtLeast(1); yuk = inputHeight.coerceAtLeast(1); return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val a = an(presentationTimeUs)
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uBoy", floatArrayOf(gen.toFloat(), yuk.toFloat()))
        program.setFloatUniform("uBulanik", a.bulanik)
        program.setFloatUniform("uSilme", a.silme)
        program.setFloatUniform("uSizma", a.sizma)
        program.setFloatUniform("uGiris", if (a.giris) 1f else 0f)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    override fun release() { super.release(); try { program.delete() } catch (_: Exception) {} }

    companion object {
        const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uBoy;
uniform float uBulanik;
uniform float uSilme;
uniform float uSizma;
uniform float uGiris;
varying vec2 vTexSamplingCoord;
void main() {
  vec2 t = vTexSamplingCoord;
  vec4 c = texture2D(uTexSampler, t);
  vec3 r = c.rgb;
  if (uBulanik > 0.001) {
    vec2 a = vec2(min(uBoy.x, uBoy.y) * 0.035 * uBulanik) / uBoy;
    vec3 s = r * 0.2;
    s += texture2D(uTexSampler, t + vec2(a.x, 0.0)).rgb * 0.1;
    s += texture2D(uTexSampler, t - vec2(a.x, 0.0)).rgb * 0.1;
    s += texture2D(uTexSampler, t + vec2(0.0, a.y)).rgb * 0.1;
    s += texture2D(uTexSampler, t - vec2(0.0, a.y)).rgb * 0.1;
    s += texture2D(uTexSampler, t + a * 0.7).rgb * 0.1;
    s += texture2D(uTexSampler, t - a * 0.7).rgb * 0.1;
    s += texture2D(uTexSampler, t + vec2(a.x, -a.y) * 0.7).rgb * 0.1;
    s += texture2D(uTexSampler, t + vec2(-a.x, a.y) * 0.7).rgb * 0.1;
    r = s;
  }
  if (uSilme > 0.001) {
    float yum = 0.04;
    float m = uGiris > 0.5 ? smoothstep(1.0 - uSilme - yum, 1.0 - uSilme + yum, t.x) : (1.0 - smoothstep(uSilme - yum, uSilme + yum, t.x));
    r = mix(r, vec3(0.0), m);
  }
  if (uSizma > 0.001) {
    float d = length((t - vec2(0.15, 0.8)) * vec2(1.0, uBoy.y / uBoy.x));
    float k = (1.0 - smoothstep(0.0, 1.1, d)) * uSizma;
    vec3 isik = vec3(1.0, 0.62, 0.22) * k;
    r = 1.0 - (1.0 - r) * (1.0 - isik);
  }
  gl_FragColor = vec4(clamp(r, 0.0, 1.0), c.a);
}
"""
    }
}


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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.sin

/** "Erime" geçişi — web Kareden Videoya'daki melt geçişinin birebir aynısı:
 *  192×108 değer gürültüsü (4 kat, yukarıdan aşağı akış eğilimi), eşik = ease(k)·(1+E), yumuşak kenar E = 0,14.
 *  Giden klip üstte; gürültünün eşiği geçtiği yerlerde saydamlaşır ve gelen klip "eriyerek" görünür.
 *  Önizleme (maske bitmapi) ve dışa aktarma (GL gölgelendirici, aynı gürültü dokusu) aynı sayıları kullanır. */
object Erime {
    const val GW = 192
    const val GH = 108
    const val E = 0.14f

    private fun hash(x: Double, y: Double, s: Double): Double { val v = sin(x * 127.1 + y * 311.7 + s * 74.7) * 43758.5453; return v - floor(v) }
    private fun vn(x: Double, y: Double, s: Double): Double {
        val xi = floor(x); val yi = floor(y); val xf = x - xi; val yf = y - yi
        val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
        val a = hash(xi, yi, s); val b = hash(xi + 1, yi, s); val c = hash(xi, yi + 1, s); val d = hash(xi + 1, yi + 1, s)
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    /** 0…1 gürültü, satır 0 = karenin üstü. */
    val gurultu: FloatArray by lazy {
        val n = FloatArray(GW * GH); var lo = 9.0; var hi = -9.0
        val ham = DoubleArray(GW * GH)
        for (y in 0 until GH) for (x in 0 until GW) {
            val fx = x.toDouble() / GW * 5; val fy = y.toDouble() / GH * 3
            var v = vn(fx, fy, 1.0) * 0.55 + vn(fx * 2.1, fy * 2.1, 2.0) * 0.27 + vn(fx * 4.3, fy * 4.3, 3.0) * 0.13 + vn(fx * 9, fy * 9, 4.0) * 0.05
            v = v * 0.72 + (y.toDouble() / GH) * 0.28          // erime yukarıdan aşağı akar
            ham[y * GW + x] = v; if (v < lo) lo = v; if (v > hi) hi = v
        }
        for (i in ham.indices) n[i] = ((ham[i] - lo) / (hi - lo)).toFloat()
        n
    }

    /** k: geçişin ilerlemesi 0…1 → eşik (web: ease = easeInOutQuad, th = ease(k)·(1+E)). */
    fun esik(k: Float): Float {
        val x = k.coerceIn(0f, 1f)
        val e = if (x < 0.5f) 2f * x * x else 1f - ((-2f * x + 2f) * (-2f * x + 2f)) / 2f
        return e * (1f + E)
    }

    // ---- önizleme: giden klibin görünürlük maskesi (alfa = 1 − gelen) ----
    private var maskeBmp: Bitmap? = null
    private val px = IntArray(GW * GH)
    fun maske(k: Float): Bitmap {
        val b = maskeBmp ?: Bitmap.createBitmap(GW, GH, Bitmap.Config.ARGB_8888).also { maskeBmp = it }
        val th = esik(k); val n = gurultu
        for (i in n.indices) {
            val m = ((th - n[i]) / E).coerceIn(0f, 1f)
            px[i] = (((1f - m) * 255f).toInt() shl 24) or 0x00FFFFFF
        }
        b.setPixels(px, 0, GW, 0, 0, GW, GH)
        return b
    }

    /** GL dokusu (LUMINANCE, doğrusal süzme). GL iş parçacığında çağrılır. */
    fun dokuYukle(): Int {
        val n = gurultu
        val buf = ByteBuffer.allocateDirect(n.size).order(ByteOrder.nativeOrder())
        for (v in n) buf.put((v * 255f).toInt().coerceIn(0, 255).toByte())
        buf.position(0)
        val t = IntArray(1)
        GLES20.glGenTextures(1, t, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, GW, GH, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buf)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return t[0]
    }
}

/** Dışa aktarma: giden klibin kuyruğuna uygulanan erime maskesi (çıkış alfası; birleştirici alfaya göre karıştırır). */
class EriyenMaske(private val sureMs: Long) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = EriyenProgram(useHdr, sureMs)
}

private class EriyenProgram(useHdr: Boolean, private val sureMs: Long) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VS, FS)
    private val doku: Int = Erime.dokuYukle()
    private var t0 = -1L

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        if (t0 < 0) t0 = presentationTimeUs
        val k = ((presentationTimeUs - t0) / 1000f / sureMs.coerceAtLeast(1)).coerceIn(0f, 1f)
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setSamplerTexIdUniform("uGurultu", doku, 1)
        program.setFloatUniform("uEsik", Erime.esik(k))
        program.setFloatUniform("uYum", Erime.E)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (_: Exception) {}
        GLES20.glDeleteTextures(1, intArrayOf(doku), 0)
    }

    companion object {
        const val VS = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""
        const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uGurultu;
uniform float uEsik;
uniform float uYum;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 c = texture2D(uTexSampler, vTexSamplingCoord);
  float n = texture2D(uGurultu, vec2(vTexSamplingCoord.x, 1.0 - vTexSamplingCoord.y)).r;
  float m = clamp((uEsik - n) / uYum, 0.0, 1.0);
  gl_FragColor = vec4(c.rgb, c.a * (1.0 - m));
}
"""
    }
}

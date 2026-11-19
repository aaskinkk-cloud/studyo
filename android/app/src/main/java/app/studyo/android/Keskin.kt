package app.studyo.android

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** 3.53: Keskinlik (Renk › Keskinlik). 4 komşulu keskinleştirme maskesi; güç = k · 1,6.
 *  Önce/sonra görüntüsündeki RenkLut.keskinlestir ile aynı formül. Önizleme ve dışa aktarma aynı etkiyi kullanır. */
class KeskinEtki(private val k: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = KeskinProgram(useHdr, k)
}

private class KeskinProgram(useHdr: Boolean, private val k: Float) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(VS, FS)
    private var gen = 1; private var yuk = 1

    init {
        program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size { gen = inputWidth.coerceAtLeast(1); yuk = inputHeight.coerceAtLeast(1); return Size(inputWidth, inputHeight) }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uAdim", floatArrayOf(1f / gen, 1f / yuk))
        program.setFloatUniform("uGuc", k * 1.6f)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun release() {
        super.release()
        try { program.delete() } catch (_: Exception) {}
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
uniform vec2 uAdim;
uniform float uGuc;
varying vec2 vTexSamplingCoord;
void main() {
  vec2 t = vTexSamplingCoord;
  vec4 c = texture2D(uTexSampler, t);
  vec3 kom = texture2D(uTexSampler, t + vec2(uAdim.x, 0.0)).rgb + texture2D(uTexSampler, t - vec2(uAdim.x, 0.0)).rgb
           + texture2D(uTexSampler, t + vec2(0.0, uAdim.y)).rgb + texture2D(uTexSampler, t - vec2(0.0, uAdim.y)).rgb;
  vec3 r = clamp(c.rgb + uGuc * (c.rgb - kom * 0.25), 0.0, 1.0);
  gl_FragColor = vec4(r, c.a);
}
"""
    }
}

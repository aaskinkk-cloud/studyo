package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.min

/** Kare üstüne bindirilen görüntü. Konumlar OpenGL koordinatıdır (-1…1, y yukarı) — Media3 ile aynı.
 *  Önizleme de dışa aktarma da AYNI bitmapi ve AYNI hesabı kullanır (ne görürsen o çıkar). */
class Yerlesim(val bmp: Bitmap, val zx: Float, val zy: Float, val kx: Float, val ky: Float, val alfa: Float = 1f) {
    /** W×H karede bitmapin sol-üst köşesi (piksel). */
    fun solUst(W: Int, H: Int): Pair<Float, Float> {
        val px = (zx + 1f) / 2f * W
        val py = (1f - zy) / 2f * H
        return (px - (kx + 1f) / 2f * bmp.width) to (py - (1f - ky) / 2f * bmp.height)
    }
}

object Kaplama {
    fun yazi(y: Yazi, W: Int, H: Int): Yerlesim? {
        if (y.metin.isBlank()) return null
        val px = y.boyut * min(W, H)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = y.renk.toInt(); textSize = px
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if (!y.kutu) setShadowLayer(px * 0.12f, 0f, px * 0.05f, 0xCC000000.toInt())
        }
        val enGen = (W * 0.9f).toInt().coerceAtLeast(10)
        val lay = StaticLayout.Builder.obtain(y.metin, 0, y.metin.length, p, enGen)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.05f).build()
        var lw = 1f
        for (i in 0 until lay.lineCount) lw = max(lw, lay.getLineWidth(i))
        val pad = (px * 0.35f).toInt().coerceAtLeast(2)
        val bw = lw.toInt() + pad * 2
        val bh = lay.height + pad * 2
        val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (y.kutu) c.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), pad.toFloat(), pad.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        c.save(); c.translate(pad - (enGen - lw) / 2f, pad.toFloat()); lay.draw(c); c.restore()
        val m = 0.06f
        return when (y.konum) {
            "ust" -> Yerlesim(b, 0f, 1f - 2f * m, 0f, 1f)
            "orta" -> Yerlesim(b, 0f, 0f, 0f, 0f)
            else -> Yerlesim(b, 0f, -1f + 2f * m, 0f, -1f)
        }
    }

    fun logo(l: Logo, W: Int, H: Int): Yerlesim? {
        if (l.yol.isEmpty()) return null
        val kaynak = BitmapFactory.decodeFile(l.yol) ?: return null
        val hedef = (l.boyut * min(W, H)).toInt().coerceAtLeast(8)
        val s = hedef.toFloat() / max(kaynak.width, kaynak.height)
        val sw = (kaynak.width * s).toInt().coerceAtLeast(1)
        val sh = (kaynak.height * s).toInt().coerceAtLeast(1)
        var b = Bitmap.createScaledBitmap(kaynak, sw, sh, true)
        if (l.yuvarlak) {
            val d = min(sw, sh)
            val o = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
            val sh2 = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            sh2.setLocalMatrix(Matrix().apply { setTranslate(-(sw - d) / 2f, -(sh - d) / 2f) })
            Canvas(o).drawCircle(d / 2f, d / 2f, d / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sh2 })
            b = o
        }
        val mx = 0.035f * min(W, H) / W * 2f
        val my = 0.035f * min(W, H) / H * 2f
        return when (l.konum) {
            "solUst" -> Yerlesim(b, -1f + mx, 1f - my, -1f, 1f, l.opaklik)
            "solAlt" -> Yerlesim(b, -1f + mx, -1f + my, -1f, -1f, l.opaklik)
            "sagAlt" -> Yerlesim(b, 1f - mx, -1f + my, 1f, -1f, l.opaklik)
            else -> Yerlesim(b, 1f - mx, 1f - my, 1f, 1f, l.opaklik)
        }
    }
}

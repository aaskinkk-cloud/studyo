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
    fun anahtar(o: KatmanOge): Int = listOf(o.tur, o.yol, o.metin, o.renk, o.kutu, o.olcek, o.filtre, o.kirpL, o.kirpT, o.kirpR, o.kirpB).hashCode()

    /** W×H çıktı karesi için katmanın bitmapi (ses katmanı ve boş metin için null). */
    fun bitmap(o: KatmanOge, W: Int, H: Int): Bitmap? = try {
        when (o.tur) {
            "metin" -> metin(o, W, H)
            "cikartma" -> cikartma(o, W)
            "gorsel" -> gorsel(o, W)
            else -> null
        }
    } catch (e: Throwable) {
        Kayit.hata("katman çizimi ${o.tur}", e); null
    }

    private fun metin(o: KatmanOge, W: Int, H: Int): Bitmap? {
        if (o.metin.isBlank()) return null
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
class KatmanBindirme(private val o: KatmanOge, private val bmp: Bitmap, private val klipBasMs: Long) : BitmapOverlay() {
    private val saat = Saat()
    override fun getBitmap(presentationTimeUs: Long): Bitmap = bmp
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

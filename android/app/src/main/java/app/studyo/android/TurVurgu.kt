package app.studyo.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3.76: yüz turunda odaklanan kişiyi öne çıkarma —
 *  - VURGU: kameranın vardığı yüzün etrafında ince beyaz çizgi tepeden başlayıp saat yönünde dolaşır (öndeki küçük ışık noktasıyla),
 *    kısa bir an tam kalır ve solarak kaybolur (~2,3 sn; durak kısaysa sıkışır). Karartma yok.
 *  - İSİM KUTUSU: kişinin adı başın altında (yer yoksa üstünde) yarı saydam koyu kutuda, ince açık çizgiyle; yükselerek gelir,
 *    2 sn ya da durak boyunca kalır, solarak gider.
 * Önizleme (Paneller) ve dışa aktarma / akıcı önizleme (TurHalkaBindirme, TurIsimBindirme) AYNI durum() hesabını kullanır.
 */
object TurVurgu {
    class Durum(val durak: Int, val yuz: Boolean, val cx: Float, val cy: Float, val rx: Float, val ry: Float,
                val sup: Float, val halkaAlfa: Float, val ad: String, val adAlfa: Float, val adDy: Float)

    private fun yum(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

    /** sn: klip içi zaman, D: klibin ekrandaki tam süresi (sn). null = çizilecek bir şey yok. */
    fun durum(k: Klip, W: Int, H: Int, sn: Float, D: Float): Durum? {
        if (!k.turEtkin) return null
        val t = k.yuzTur ?: return null
        if (!t.vurgu && !t.isim) return null
        val a = YuzTur.asama(t, sn, D) ?: return null
        val n = t.noktalar.getOrNull(a.durak) ?: return null
        val ad = n.ad.trim()
        val yuz = n.yb > 0f
        if (!(t.vurgu && yuz) && !(t.isim && ad.isNotEmpty())) return null
        val geo = Yuz.ekranGeo(k, W, H, Yuz.an(k, W, H, (sn / max(0.3f, D)).coerceIn(0f, 1f), 0, D))
        val fw = geo[0]; val fh = geo[1]
        val cx = geo[2] + (n.x - 0.5f) * fw; val cy = geo[3] + (n.y - 0.5f) * fh
        val kisa = min(W, H).toFloat()
        val ry = if (yuz) n.yb * fh * 0.6f else kisa * 0.08f
        val rx = if (yuz) ry * 0.8f else ry
        // halka zamanlaması (durak kısaysa orantılı sıkışır)
        val g = a.gecen; val h = a.sure
        val c = (min(2.3f, h - 0.15f) / 2.3f).coerceIn(0.3f, 1f)
        val bas = 0.12f * c; val ciz = 1.0f * c; val bek = 0.5f * c; val sol = 0.6f * c
        val sup = yum((g - bas) / ciz)
        val halkaA = if (!t.vurgu || !yuz || g < bas) 0f else if (g < bas + ciz + bek) 1f else (1f - (g - bas - ciz - bek) / sol).coerceIn(0f, 1f)
        // isim kutusu
        val adBas = min(0.35f, h * 0.15f)
        val adSon = if (t.isimDurak) h - 0.15f else min(adBas + 2f, h - 0.15f)
        val adA = if (!t.isim || ad.isEmpty()) 0f else min((g - adBas) / 0.4f, (adSon - g) / 0.4f).coerceIn(0f, 1f)
        val adDy = (1f - yum((g - adBas) / 0.5f)) * kisa * 0.014f
        if (halkaA <= 0.002f && adA <= 0.002f) return null
        return Durum(a.durak, yuz, cx, cy, rx, ry, sup, halkaA, ad, adA, adDy)
    }

    /** Halkayı mutlak koordinatlarla çizer (yazılım ve donanım tuvalinde aynı görünür: bulanıklık filtresi yok). */
    fun halkaCiz(c: Canvas, d: Durum, kisa: Float) {
        if (!d.yuz || d.halkaAlfa <= 0.002f || d.sup <= 0.001f) return
        val sw = max(1.5f, kisa * 0.0026f)
        val oval = RectF(d.cx - d.rx, d.cy - d.ry, d.cx + d.rx, d.cy + d.ry)
        val aci = 360f * d.sup
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = android.graphics.Color.WHITE }
        // yumuşak hale: iki geniş, çok saydam çizgi
        p.strokeWidth = sw * 4.5f; p.alpha = (255 * 0.08f * d.halkaAlfa).roundToInt(); c.drawArc(oval, -90f, aci, false, p)
        p.strokeWidth = sw * 2.4f; p.alpha = (255 * 0.16f * d.halkaAlfa).roundToInt(); c.drawArc(oval, -90f, aci, false, p)
        p.strokeWidth = sw; p.alpha = (255 * 0.92f * d.halkaAlfa).roundToInt(); c.drawArc(oval, -90f, aci, false, p)
        if (d.sup < 0.999f) {   // öndeki ışık noktası
            val r = Math.toRadians((-90f + aci).toDouble())
            val x = d.cx + d.rx * cos(r).toFloat(); val y = d.cy + d.ry * sin(r).toFloat()
            val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            f.alpha = (255 * 0.25f * d.halkaAlfa).roundToInt(); c.drawCircle(x, y, sw * 3.2f, f)
            f.alpha = (255 * d.halkaAlfa).roundToInt(); c.drawCircle(x, y, sw * 1.5f, f)
        }
    }

    /** İsim kutusu bitmapi (Montserrat 600, yarı saydam koyu kutu, solda ince açık çizgi). */
    fun isimBitmap(ad: String, W: Int, H: Int): Bitmap {
        val kisa = min(W, H).toFloat()
        val fs = kisa * 0.036f
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = fs; typeface = Yazilar.mont600; color = android.graphics.Color.WHITE; letterSpacing = 0.02f }
        val metin = android.text.TextUtils.ellipsize(ad, tp, W * 0.6f, android.text.TextUtils.TruncateAt.END).toString()
        val fm = tp.fontMetrics
        val padY = fs * 0.42f; val padX = fs * 0.75f; val cubuk = max(2f, fs * 0.09f)
        val bw = padX + cubuk + fs * 0.45f + tp.measureText(metin) + padX
        val bh = (fm.descent - fm.ascent) + padY * 2
        val b = Bitmap.createBitmap(bw.roundToInt().coerceAtLeast(2), bh.roundToInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val r = bh * 0.24f
        c.drawRoundRect(RectF(0f, 0f, bw, bh), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() })
        c.drawRoundRect(RectF(0.75f, 0.75f, bw - 0.75f, bh - 0.75f), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0x40FFFFFF })
        c.drawRect(RectF(padX, bh * 0.27f, padX + cubuk, bh * 0.73f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() })
        c.drawText(metin, padX + cubuk + fs * 0.45f, padY - fm.ascent, tp)
        return b
    }

    /** İsim kutusunun ortası (çıkış pikseli): başın altında, yer yoksa üstünde; kenarlardan taşmaz. */
    fun isimYeri(d: Durum, bw: Int, bh: Int, W: Int, H: Int): Pair<Float, Float> {
        val kisa = min(W, H).toFloat(); val m = kisa * 0.04f; val ara = kisa * 0.025f
        val x = d.cx.coerceIn(bw / 2f + m, max(bw / 2f + m, W - bw / 2f - m))
        var ust = d.cy + d.ry + ara + d.adDy
        if (ust + bh > H - m) ust = d.cy - d.ry - ara - bh + d.adDy
        ust = ust.coerceIn(m, max(m, H - m - bh))
        return x to ust + bh / 2f
    }

    internal fun ndc(px: Float, py: Float, W: Int, H: Int) = (px / W * 2f - 1f) to (1f - py / H * 2f)
}

/** Dışa aktarma / akıcı önizleme: öne çıkarma çizgisi (kare kare, ilerleme adımlı küçük bitmap; yalnız görünürken). */
class TurHalkaBindirme(private val k: Klip, private val W: Int, private val H: Int, klipBasMs: Long, private val ofsMs: Long, private val tamMs: Long) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    private val bos = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val kisa = min(W, H).toFloat()
    private var sonUs = Long.MIN_VALUE; private var sonD: TurVurgu.Durum? = null
    private var anahtar = ""; private var bmp: Bitmap = bos
    private fun hazirla(us: Long): TurVurgu.Durum? {
        if (us == sonUs) return sonD
        sonUs = us
        val d = TurVurgu.durum(k, W, H, (saat.yerelMs(us) + ofsMs) / 1000f, tamMs / 1000f)?.takeIf { it.yuz && it.halkaAlfa > 0.002f && it.sup > 0.001f }
        sonD = d
        if (d != null) {
            val ad = (kisa * 0.01f).coerceAtLeast(1f)
            val rx = (d.rx / ad).roundToInt() * ad; val ry = (d.ry / ad).roundToInt() * ad
            val adim = (d.sup * 40f).roundToInt()
            val yeni = "$adim/$rx/$ry"
            if (yeni != anahtar) {
                anahtar = yeni
                val pay = max(1.5f, kisa * 0.0026f) * 5f
                val w = (2 * (rx + pay)).roundToInt().coerceAtLeast(2); val h = (2 * (ry + pay)).roundToInt().coerceAtLeast(2)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                TurVurgu.halkaCiz(Canvas(b), TurVurgu.Durum(d.durak, true, w / 2f, h / 2f, rx, ry, adim / 40f, 1f, "", 0f, 0f), kisa)
                bmp = b
            }
        }
        return d
    }
    override fun getBitmap(presentationTimeUs: Long): Bitmap { val d = hazirla(presentationTimeUs); return if (d == null) bos else bmp }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val d = hazirla(presentationTimeUs) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val (zx, zy) = TurVurgu.ndc(d.cx, d.cy, W, H)
        return Yerlesim(bmp, zx, zy, 0f, 0f).ayar(StaticOverlaySettings.Builder(), W, H).setAlphaScale(d.halkaAlfa.coerceIn(0f, 1f)).build()
    }
}

/** Dışa aktarma / akıcı önizleme: isim kutusu (her durağın bitmapi bir kez çizilir). */
class TurIsimBindirme(private val k: Klip, private val W: Int, private val H: Int, klipBasMs: Long, private val ofsMs: Long, private val tamMs: Long) : BitmapOverlay() {
    private val saat = Saat(klipBasMs)
    private val bos = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private val kutular = HashMap<Int, Bitmap>()
    private var sonUs = Long.MIN_VALUE; private var sonD: TurVurgu.Durum? = null
    private fun durum(us: Long): TurVurgu.Durum? {
        if (us != sonUs) { sonUs = us; sonD = TurVurgu.durum(k, W, H, (saat.yerelMs(us) + ofsMs) / 1000f, tamMs / 1000f)?.takeIf { it.adAlfa > 0.002f && it.ad.isNotEmpty() } }
        return sonD
    }
    private fun kutu(d: TurVurgu.Durum): Bitmap = kutular.getOrPut(d.durak) { TurVurgu.isimBitmap(d.ad, W, H) }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = durum(presentationTimeUs)?.let { kutu(it) } ?: bos
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val d = durum(presentationTimeUs) ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        val b = kutu(d)
        val (x, y) = TurVurgu.isimYeri(d, b.width, b.height, W, H)
        val (zx, zy) = TurVurgu.ndc(x, y, W, H)
        return Yerlesim(b, zx, zy, 0f, 0f).ayar(StaticOverlaySettings.Builder(), W, H).setAlphaScale(d.adAlfa.coerceIn(0f, 1f)).build()
    }
}

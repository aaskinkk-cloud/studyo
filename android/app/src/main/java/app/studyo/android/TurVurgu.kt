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
                val sup: Float, val halkaAlfa: Float, val ad: String, val adAlfa: Float, val adDy: Float,
                /** 3.87: silüet ışığı — hat noktaları çıkış pikselinde (x,y sırayla), tepe sırası, ışığın kuyruğu (0…1) */
                val siluet: FloatArray? = null, val tepe: Int = 0, val kuyruk: Float = 0f)

    private fun yum(x: Float): Float { val k = x.coerceIn(0f, 1f); return k * k * (3f - 2f * k) }

    @Volatile private var hataYazildi = false
    private fun hataBir(yer: String, e: Throwable) { if (!hataYazildi) { hataYazildi = true; Kayit.hata("yüz turu çizimi ($yer)", e) } }

    /** 3.78: hiçbir hata uygulamayı kapatmasın — çizim hesabı korumalı; hata olursa o kare çizilmez, kayda bir kez yazılır. */
    fun durum(k: Klip, W: Int, H: Int, sn: Float, D: Float): Durum? = try {
        if (!sn.isFinite() || !D.isFinite() || W <= 0 || H <= 0) null else durum0(k, W, H, sn, D)?.takeIf { d ->
            d.cx.isFinite() && d.cy.isFinite() && d.rx.isFinite() && d.ry.isFinite() }
    } catch (e: Throwable) { hataBir("durum", e); null }

    private fun durum0(k: Klip, W: Int, H: Int, sn: Float, D: Float): Durum? {
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
        val g = a.gecen; val h = a.sure
        // 3.87: silüet ışığı — ışık iki alt uçtan kişinin hattını izleyerek başın tepesinde buluşur, sonra kuyruk tepeye çekilip söner (~2 sn)
        if (t.vurgu && yuz && t.vurguTur == "siluet" && n.siluet.size >= 16) {
            val c2 = (min(2.1f, h - 0.15f) / 2.1f).coerceIn(0.3f, 1f)
            val bas = 0.15f * c2; val buyu = 0.95f * c2; val bek = 0.12f * c2; val cek = 0.75f * c2
            val on = yum((g - bas) / buyu)
            val kuyruk = yum((g - bas - buyu - bek) / cek)
            val alfa = if (g < bas) 0f else (1f - ((kuyruk - 0.45f) / 0.55f).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }
            val pts = FloatArray(n.siluet.size)
            var i = 0
            while (i + 1 < n.siluet.size) {
                pts[i] = geo[2] + (n.siluet[i] - 0.5f) * fw; pts[i + 1] = geo[3] + (n.siluet[i + 1] - 0.5f) * fh; i += 2
            }
            if (pts.any { !it.isFinite() }) return null
            val adBas2 = min(0.35f, h * 0.15f)
            val adSon2 = if (t.isimDurak) h - 0.15f else min(adBas2 + 2f, h - 0.15f)
            val adA2 = if (!t.isim || ad.isEmpty()) 0f else min((g - adBas2) / 0.4f, (adSon2 - g) / 0.4f).coerceIn(0f, 1f)
            val adDy2 = (1f - yum((g - adBas2) / 0.5f)) * kisa * 0.014f
            if (alfa <= 0.002f && adA2 <= 0.002f) return null
            return Durum(a.durak, true, cx, cy, rx, ry, on, alfa, ad, adA2, adDy2, pts, Siluet.tepe(n.siluet), kuyruk)
        }
        // halka zamanlaması (durak kısaysa orantılı sıkışır)
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
    fun halkaCiz(c: Canvas, d: Durum, kisa: Float) = try { halkaCiz0(c, d, kisa) } catch (e: Throwable) { hataBir("çizgi", e) }
    private fun halkaCiz0(c: Canvas, d: Durum, kisa: Float) {
        if (!d.yuz || d.halkaAlfa <= 0.002f || d.sup <= 0.001f) return
        d.siluet?.let { siluetCiz(c, it, d.tepe, d.sup, d.kuyruk, d.halkaAlfa, kisa); return }
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

    /** 3.87: silüet ışığı. Hattın iki yarısı (soldaki uç → tepe, sağdaki uç → tepe) aynı anda çizilir: yarının [kuyruk, ön] aralığı.
     *  Yumuşak beyaz hale (geniş, çok saydam çizgiler) + parlak ince çekirdek + öndeki ışık noktası. Bulanıklık filtresi yok (iki tuvalde aynı). */
    fun siluetCiz(c: Canvas, p: FloatArray, tepe: Int, on: Float, kuyruk: Float, alfa: Float, kisa: Float) {
        val n = p.size / 2
        if (n < 3 || on <= kuyruk + 0.001f) return
        val sw = max(1.6f, kisa * 0.0030f)
        val yollar = ArrayList<android.graphics.Path>(2); val uclar = ArrayList<Pair<Float, Float>>(2)
        for (yari in 0..1) {
            val sira = if (yari == 0) (0..tepe).toList() else (n - 1 downTo tepe).toList()
            if (sira.size < 2) continue
            val uz = FloatArray(sira.size)
            for (j in 1 until sira.size) uz[j] = uz[j - 1] + kotlin.math.hypot(p[2 * sira[j]] - p[2 * sira[j - 1]], p[2 * sira[j] + 1] - p[2 * sira[j - 1] + 1])
            val L = uz.last(); if (L <= 0f) continue
            fun nokta(s: Float): Pair<Float, Float> {
                var j = 0; while (j < sira.size - 2 && uz[j + 1] < s) j++
                val t = ((s - uz[j]) / (uz[j + 1] - uz[j]).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
                val a = sira[j]; val b = sira[j + 1]
                return (p[2 * a] + (p[2 * b] - p[2 * a]) * t) to (p[2 * a + 1] + (p[2 * b + 1] - p[2 * a + 1]) * t)
            }
            val s0 = kuyruk * L; val s1 = on * L
            val yol = android.graphics.Path()
            val (ax, ay) = nokta(s0); yol.moveTo(ax, ay)
            for (j in sira.indices) if (uz[j] > s0 && uz[j] < s1) yol.lineTo(p[2 * sira[j]], p[2 * sira[j] + 1])
            val (bx, by) = nokta(s1); yol.lineTo(bx, by)
            yollar += yol; uclar += bx to by
        }
        val pn = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = android.graphics.Color.WHITE }
        for ((gen, opak) in listOf(7f to 0.06f, 4f to 0.12f, 2.2f to 0.30f, 1f to 0.95f)) {
            pn.strokeWidth = sw * gen; pn.alpha = (255 * opak * alfa).roundToInt().coerceIn(0, 255)
            yollar.forEach { c.drawPath(it, pn) }
        }
        if (on < 0.999f) {   // öndeki ışık noktaları (iki uç tepeye doğru koşar)
            val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
            for ((x, y) in uclar) {
                f.alpha = (255 * 0.18f * alfa).roundToInt(); c.drawCircle(x, y, sw * 4.5f, f)
                f.alpha = (255 * 0.45f * alfa).roundToInt(); c.drawCircle(x, y, sw * 2.4f, f)
                f.alpha = (255 * alfa).roundToInt(); c.drawCircle(x, y, sw * 1.2f, f)
            }
        }
    }

    /** Önizleme için isim kutusu önbelleği (her karede yeniden çizilmez). */
    private val onbellek = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Bitmap>?) = size > 12 }
    fun isimOnbellek(ad: String, W: Int, H: Int): Bitmap? = try { synchronized(onbellek) { onbellek.getOrPut("$ad|$W|$H") { isimBitmap(ad, W, H) } } } catch (e: Throwable) { hataBir("isim", e); null }

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
    // 3.87: silüet ışığı — hattın çevresindeki kutu yarım çözünürlükte çizilir, bindirme 2× ölçeklenir; iki bitmap nöbetleşe
    // (Media3 aynı bitmap nesnesini yeniden yüklemez). Kutu ortası çıkışta (sCx, sCy).
    private var sCx = 0f; private var sCy = 0f; private var sOlcek = 1f
    private var sNobet = 0; private val sBmp = arrayOfNulls<Bitmap>(2)
    private fun siluetHazirla(d: TurVurgu.Durum) {
        val p = d.siluet ?: return
        val pay = max(1.6f, kisa * 0.0030f) * 6f
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var i = 0; while (i + 1 < p.size) { x0 = min(x0, p[i]); x1 = max(x1, p[i]); y0 = min(y0, p[i + 1]); y1 = max(y1, p[i + 1]); i += 2 }
        x0 = max(x0 - pay, 0f); y0 = max(y0 - pay, 0f); x1 = min(x1 + pay, W.toFloat()); y1 = min(y1 + pay, H.toFloat())
        if (x1 - x0 < 4f || y1 - y0 < 4f) { bmp = bos; return }
        val ad = (kisa * 0.004f).coerceAtLeast(1f)
        val yeni = "s${(d.sup * 90f).roundToInt()}/${(d.kuyruk * 90f).roundToInt()}/${(x0 / ad).roundToInt()}/${(y0 / ad).roundToInt()}/${(x1 / ad).roundToInt()}/${(y1 / ad).roundToInt()}"
        if (yeni == anahtar && bmp !== bos) return
        anahtar = yeni
        val o = if (max(W, H) > 2000) 0.5f else 1f
        val bw = ((x1 - x0) * o).roundToInt().coerceAtLeast(2); val bh = ((y1 - y0) * o).roundToInt().coerceAtLeast(2)
        sNobet = 1 - sNobet
        val b = sBmp[sNobet]?.takeIf { it.width == bw && it.height == bh }?.also { it.eraseColor(0) } ?: Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { sBmp[sNobet] = it }
        val c = Canvas(b); c.scale(o, o); c.translate(-x0, -y0)
        TurVurgu.siluetCiz(c, p, d.tepe, d.sup, d.kuyruk, 1f, kisa)
        bmp = b; sCx = (x0 + x1) / 2f; sCy = (y0 + y1) / 2f; sOlcek = 1f / o
    }
    private fun hazirla(us: Long): TurVurgu.Durum? {
        if (us == sonUs) return sonD
        sonUs = us
        val d = TurVurgu.durum(k, W, H, (saat.yerelMs(us) + ofsMs) / 1000f, tamMs / 1000f)?.takeIf { it.yuz && it.halkaAlfa > 0.002f && it.sup > 0.001f && it.rx > 0.5f && it.ry > 0.5f }
        sonD = d
        if (d?.siluet != null) { siluetHazirla(d); return d }
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
    override fun getBitmap(presentationTimeUs: Long): Bitmap = try { val d = hazirla(presentationTimeUs); if (d == null) bos else bmp } catch (e: Throwable) { bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = try {
        val d = hazirla(presentationTimeUs)
        if (d == null) StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        else if (d.siluet != null) { val (zx, zy) = TurVurgu.ndc(sCx, sCy, W, H)
            StaticOverlaySettings.Builder().setBackgroundFrameAnchor(zx.coerceIn(-1f, 1f), zy.coerceIn(-1f, 1f)).setScale(sOlcek, sOlcek)
                .setAlphaScale(d.halkaAlfa.coerceIn(0f, 1f)).build() }
        else { val (zx, zy) = TurVurgu.ndc(d.cx, d.cy, W, H)
            Yerlesim(bmp, zx, zy, 0f, 0f).ayar(StaticOverlaySettings.Builder(), W, H).setAlphaScale(d.halkaAlfa.coerceIn(0f, 1f)).build() }
    } catch (e: Throwable) { StaticOverlaySettings.Builder().setAlphaScale(0f).build() }
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
    private fun kutu(d: TurVurgu.Durum): Bitmap = kutular.getOrPut(d.durak) { TurVurgu.isimOnbellek(d.ad, W, H) ?: bos }
    override fun getBitmap(presentationTimeUs: Long): Bitmap = try { durum(presentationTimeUs)?.let { kutu(it) } ?: bos } catch (e: Throwable) { bos }
    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = try {
        val d = durum(presentationTimeUs)
        if (d == null) StaticOverlaySettings.Builder().setAlphaScale(0f).build()
        else { val b = kutu(d)
            val (x, y) = TurVurgu.isimYeri(d, b.width, b.height, W, H)
            val (zx, zy) = TurVurgu.ndc(x, y, W, H)
            Yerlesim(b, zx, zy, 0f, 0f).ayar(StaticOverlaySettings.Builder(), W, H).setAlphaScale(d.adAlfa.coerceIn(0f, 1f)).build() }
    } catch (e: Throwable) { StaticOverlaySettings.Builder().setAlphaScale(0f).build() }
}

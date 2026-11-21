package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.opengl.GLES20
import android.os.Build
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 3.83: Yüz gizleme (Düzenle › Klip › Yüz gizle). Yalnız seçilen yüzler, yüz hattına uyan yumuşak kenarlı oval bir flu ile kapatılır.
 *  - Bulma: ML Kit yüz algılama (ücretsiz, cihaz içinde). Videoda saniyede ~10 kare taranır; kareler arası yüzler konum + boyut
 *    benzerliğiyle birbirine bağlanır (takip izi), kısa kopukluklar (yüz döndü, el kapattı) birleştirilir, iz yumuşatılır.
 *  - Elle ekleme: kaçan yüze basılı tut → fotoğrafta oval eklenir, videoda o andan itibaren şablon takibiyle (Takipci) izlenir.
 *  - Çizim: dışa aktarma ve akıcı önizlemede GL (kaynak karede, kadraj/hareketten ÖNCE → yüzle birlikte hareket eder);
 *    durağan önizlemede aynı ovaller işlemciyle.
 */
object YuzGizleme {
    /** Bir andaki oval (kaynak karede, y aşağı): merkez, yarıçaplar (en / boy oranı), eğim (derece). */
    data class Elips(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val aci: Float)

    const val EN_COK = 6           // bir karede en çok bulanık yüz (GL)
    private const val ADIM_MS = 100L      // videoda tarama aralığı (~10 kare/sn)
    private const val BAG_MS = 1200L      // bu kadar süre görünmeyen yüz hâlâ aynı iz sayılır
    private const val BIRLES_MS = 3000L   // kopuk iki iz, aradaki boşluk bundan kısaysa ve yerleri yakınsa birleşir
    private const val PAY_MS = 400L       // izin başından önce / sonundan sonra da bulanık kalsın (algılama gecikmesi)
    private const val TUT_MS = 1500L      // iki iz noktası arası bundan uzunsa arada yalnız uçlara yakın kısım kapatılır

    // ------------------------------------------------------------------ an

    /** Klibin kaynak zamanındaki (ms) bulanık ovaller — seçili yüzler, en büyükler önce (en çok EN_COK). */
    fun anlik(g: YuzGizle, ms: Long, foto: Boolean): List<Elips> =
        anlikHepsi(g, ms, foto).filter { it.first in g.secili }.map { it.second }.sortedByDescending { it.rx * it.ry }.take(EN_COK)

    /** O anda görünen bütün yüzler (seçili olsun olmasın) — önizlemede çerçeve ve dokunma için. */
    fun anlikHepsi(g: YuzGizle, ms: Long, foto: Boolean): List<Pair<Int, Elips>> {
        val l = ArrayList<Pair<Int, Elips>>()
        for (y in g.yuzler) {
            if (y.izler.isEmpty()) continue
            val iz = if (foto || y.izler.size == 1) y.izler[0].takeIf { foto || abs(it.ms - ms) <= PAY_MS * 2 } else araDeger(y.izler, ms)
            iz?.let { l += y.id to elips(it, y.olcek) }
        }
        return l
    }

    private fun araDeger(z: List<YuzIz>, ms: Long): YuzIz? {
        if (ms < z.first().ms - PAY_MS || ms > z.last().ms + PAY_MS) return null
        if (ms <= z.first().ms) return z.first()
        if (ms >= z.last().ms) return z.last()
        var lo = 0; var hi = z.lastIndex
        while (hi - lo > 1) { val m = (lo + hi) / 2; if (z[m].ms <= ms) lo = m else hi = m }
        val a = z[lo]; val b = z[hi]
        val ara = b.ms - a.ms
        if (ara > TUT_MS) return when { ms - a.ms <= PAY_MS -> a; b.ms - ms <= PAY_MS -> b; else -> null }
        val f = ((ms - a.ms).toFloat() / ara.coerceAtLeast(1)).coerceIn(0f, 1f)
        fun k(p: Float, q: Float) = p + (q - p) * f
        return YuzIz(ms, k(a.x, b.x), k(a.y, b.y), k(a.w, b.w), k(a.h, b.h), k(a.aci, b.aci))
    }

    /** Yüz kutusundan oval: alın, çene ve yanaklar dahil (kutu ML Kit'te kaştan çeneye kadardır). */
    private fun elips(i: YuzIz, olcek: Float) = Elips(i.x, i.y - i.h * 0.06f, i.w * 0.64f * olcek, i.h * 0.78f * olcek, i.aci.coerceIn(-35f, 35f))

    /** Bulanıklık yarıçapı (piksel): ovalin kısa yarıçapına ve güce göre. */
    fun yaricap(rxPx: Float, ryPx: Float, guc: Float) = min(rxPx, ryPx) * (0.22f + 0.6f * guc.coerceIn(0f, 1f))

    // ------------------------------------------------------------------ işlemci çizimi (durağan önizleme, küçük resim)

    /** Bitmap'teki ovalleri bulanıklaştırır (yeni bitmap). Kenarlar yumuşak; GL'dekiyle aynı oval ve yarıçap. */
    fun uygula(src: Bitmap, l: List<Elips>, guc: Float): Bitmap {
        if (l.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val W = src.width.toFloat(); val H = src.height.toFloat()
        for (e in l) {
            val rx = e.rx * W; val ry = e.ry * H
            val r = yaricap(rx, ry, guc)
            val R = max(rx, ry) + r * 1.5f
            val x0 = (e.cx * W - R).roundToInt().coerceIn(0, src.width - 1); val y0 = (e.cy * H - R).roundToInt().coerceIn(0, src.height - 1)
            val x1 = (e.cx * W + R).roundToInt().coerceIn(x0 + 1, src.width); val y1 = (e.cy * H + R).roundToInt().coerceIn(y0 + 1, src.height)
            val w = x1 - x0; val h = y1 - y0
            if (w < 4 || h < 4) continue
            val parca = Bitmap.createBitmap(out, x0, y0, w, h)
            val f = (r / 2.2f).coerceAtLeast(1f)
            val k1 = Bitmap.createScaledBitmap(parca, max(2, (w / f).toInt()), max(2, (h / f).toInt()), true)
            val k2 = Bitmap.createScaledBitmap(k1, max(1, k1.width / 2), max(1, k1.height / 2), true)
            val k3 = Bitmap.createScaledBitmap(k2, k1.width, k1.height, true)
            val bul = Bitmap.createScaledBitmap(k3, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
            // yumuşak kenarlı oval maske (dışı saydam)
            val mc = Canvas(bul)
            mc.translate(e.cx * W - x0, e.cy * H - y0); mc.rotate(-e.aci); mc.scale(rx, ry)
            mc.drawPaint(Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.74f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
            c.drawBitmap(bul, x0.toFloat(), y0.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
            listOf(parca, k1, k2, k3, bul).distinct().forEach { if (it !== out) it.recycle() }
        }
        return out
    }

    // ------------------------------------------------------------------ bulma

    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    }

    private fun algilayici(): FaceDetector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setMinFaceSize(0.02f).build())

    /** Karedeki yüzler (normalleştirilmiş). Gizlilik için küçük (arkadaki) yüzler de alınır. */
    private suspend fun bul(alg: FaceDetector, b: Bitmap, ms: Long): List<YuzIz> = try {
        alg.process(InputImage.fromBitmap(b, 0)).bekle().map { f ->
            val r = f.boundingBox
            YuzIz(ms, r.exactCenterX() / b.width, r.exactCenterY() / b.height, r.width().toFloat() / b.width, r.height().toFloat() / b.height, f.headEulerAngleZ)
        }.filter { it.w > 0.004f && it.h > 0.004f }
    } catch (e: Throwable) { Kayit.hata("yüz gizle: algılama", e); emptyList() }

    private class Iz(val id: Int) {
        val l = ArrayList<YuzIz>()
        var enIyi = 0f
        var resim: Bitmap? = null
        val son get() = l.last()
    }

    /** Yüz kutusunun çevresinden kare küçük resim (128 px). */
    private fun kirp(b: Bitmap, i: YuzIz): Bitmap? = try {
        val s = max(i.w * b.width, i.h * b.height) * 1.5f
        val x0 = (i.x * b.width - s / 2).roundToInt().coerceIn(0, b.width - 2); val y0 = (i.y * b.height - s / 2).roundToInt().coerceIn(0, b.height - 2)
        val w = s.roundToInt().coerceIn(2, b.width - x0); val h = s.roundToInt().coerceIn(2, b.height - y0)
        Bitmap.createScaledBitmap(Bitmap.createBitmap(b, x0, y0, w, h), 128, 128, true)
    } catch (_: Throwable) { null }

    /** Kareye göre yeni algılamaları izlere bağlar (konum + boyut). */
    private fun bagla(izler: MutableList<Iz>, d: List<YuzIz>, ms: Long, kw: Float, kh: Float, kare: Bitmap?, sonraki: () -> Int) {
        val aday = izler.filter { ms - it.son.ms <= BAG_MS }
        data class Cift(val iz: Iz, val j: Int, val m: Float)
        val ciftler = ArrayList<Cift>()
        for (iz in aday) d.forEachIndexed { j, y ->
            val s = iz.son
            val olc = max(s.h * kh, y.h * kh).coerceAtLeast(1f)
            val m = hypot((s.x - y.x) * kw, (s.y - y.y) * kh) / olc
            val oran = (y.h / s.h.coerceAtLeast(1e-4f))
            if (m < 1.1f && oran in 0.55f..1.8f) ciftler += Cift(iz, j, m)
        }
        val kul = HashSet<Iz>(); val kulD = HashSet<Int>()
        for (c in ciftler.sortedBy { it.m }) {
            if (c.iz in kul || c.j in kulD) continue
            kul += c.iz; kulD += c.j
            c.iz.l += d[c.j]
        }
        d.forEachIndexed { j, y -> if (j !in kulD) izler += Iz(sonraki()).also { it.l += y } }
        // her iz için en büyük (en net) yüzden küçük resim
        if (kare != null) for (iz in izler) { val y = iz.son; if (y.ms == ms && y.w * y.h > iz.enIyi) { iz.enIyi = y.w * y.h; iz.resim?.recycle(); iz.resim = kirp(kare, y) } }
    }

    /** Kopuk izleri birleştirir, kısa gürültüyü atar, izi yumuşatır, küçük resimleri kaydeder. */
    private fun bitir(ctx: Context, k: Klip, izler: List<Iz>, kw: Float, kh: Float, video: Boolean): List<GizliYuz> {
        val l = izler.sortedBy { it.l.first().ms }.toMutableList()
        if (video) {
            var degisti = true
            while (degisti) {
                degisti = false
                loop@ for (a in l) for (b in l) {
                    if (a === b || b.l.first().ms <= a.son.ms) continue
                    val bos = b.l.first().ms - a.son.ms
                    if (bos > BIRLES_MS) continue
                    val s = a.son; val y = b.l.first()
                    val m = hypot((s.x - y.x) * kw, (s.y - y.y) * kh) / max(s.h * kh, y.h * kh).coerceAtLeast(1f)
                    if (m < 1.6f && y.h / s.h.coerceAtLeast(1e-4f) in 0.5f..2f) {
                        a.l += b.l
                        if (b.enIyi > a.enIyi) { a.resim?.recycle(); a.resim = b.resim; a.enIyi = b.enIyi } else b.resim?.recycle()
                        l.remove(b); degisti = true; break@loop
                    }
                }
            }
        }
        val kalan = if (video) l.filter { it.l.size >= 2 } else l
        l.filter { it !in kalan }.forEach { it.resim?.recycle() }
        val klasor = File(ctx.filesDir, "yuzgizle").apply { mkdirs() }
        return kalan.mapIndexed { n, iz ->
            val id = n + 1
            val yol = iz.resim?.let { b ->
                try { val f = File(klasor, "${k.id}_${System.currentTimeMillis()}_$id.jpg"); f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) }; f.path }
                catch (_: Throwable) { "" } finally { b.recycle() }
            } ?: ""
            GizliYuz(id, yumusat(iz.l), resim = yol)
        }
    }

    /** 3 noktalık kayan ortalama (titreme azalır, iz yüzün gerisinde kalmaz). */
    private fun yumusat(z: List<YuzIz>): List<YuzIz> = if (z.size < 3) z else z.indices.map { i ->
        val a = max(0, i - 1); val b = min(z.lastIndex, i + 1); val n = (b - a + 1).toFloat()
        var x = 0f; var y = 0f; var w = 0f; var h = 0f; var c = 0f
        for (j in a..b) { x += z[j].x; y += z[j].y; w += z[j].w; h += z[j].h; c += z[j].aci }
        YuzIz(z[i].ms, x / n, y / n, max(w / n, z[i].w), max(h / n, z[i].h), c / n)   // boyut küçülmez: yüz taşmasın
    }

    /** Fotoğraftaki bütün yüzler. */
    suspend fun fotoAra(ctx: Context, k: Klip): List<GizliYuz> = withContext(Dispatchers.Default) {
        val b = YuzTur.oku(k.uri, 1600) ?: return@withContext emptyList()
        val alg = algilayici()
        try {
            val d = bul(alg, b, 0L).sortedBy { it.x }
            var n = 0
            val izler = ArrayList<Iz>()
            for (y in d) izler += Iz(++n).also { it.l += y; it.enIyi = y.w * y.h; it.resim = kirp(b, y) }
            bitir(ctx, k, izler, b.width.toFloat(), b.height.toFloat(), video = false).also { Kayit.yaz("Yüz gizle (foto): ${it.size} yüz") }
        } finally { try { alg.close() } catch (_: Exception) {}; b.recycle() }
    }

    /** Videodaki yüzler ve izleri (kaynak zamanı). Kareler sırayla çözülür (atlama yok, hızlı), saniyede ~10'u taranır. */
    suspend fun videoAra(ctx: Context, k: Klip, ilerleme: (Float) -> Unit): List<GizliYuz> = withContext(Dispatchers.Default) {
        val r = MediaMetadataRetriever()
        val alg = algilayici()
        val izler = ArrayList<Iz>()
        var no = 0
        var kw = 640f; var kh = 360f
        try {
            r.setDataSource(ctx, Uri.parse(k.uri))
            val donus = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val sayi = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
            val sure = k.kaynakSureMs.coerceAtLeast(1)
            val dikey = k.yuk > k.gen
            suspend fun isle(ham: Bitmap, ms: Long) {
                // küçült (algılama hızlı olsun) + gösterim yönüne çevir (bkz. Takipci)
                val s = 640f / max(ham.width, ham.height)
                var b = Bitmap.createScaledBitmap(ham, max(8, (ham.width * s).roundToInt()), max(8, (ham.height * s).roundToInt()), true)
                if ((b.height > b.width) != dikey && k.gen != k.yuk && donus % 360 != 0) {
                    val m = Matrix().apply { postRotate(donus.toFloat()) }
                    val d = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true); if (d !== b) b.recycle(); b = d
                }
                val a = if (b.config != Bitmap.Config.ARGB_8888) b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() } else b
                kw = a.width.toFloat(); kh = a.height.toFloat()
                val d = bul(alg, a, ms)
                bagla(izler, d, ms, kw, kh, a) { ++no }
                a.recycle()
            }
            var tamam = false
            if (Build.VERSION.SDK_INT >= 28 && sayi > 1) {
                val fps = sayi * 1000.0 / sure
                val adim = max(1, (fps * ADIM_MS / 1000.0).roundToInt())
                val i0 = (k.basMs * fps / 1000.0).toInt().coerceIn(0, sayi - 1)
                val i1 = (k.sonMs * fps / 1000.0).toInt().coerceIn(i0, sayi - 1)
                val grup = if (k.gen.toLong() * k.yuk > 2_500_000L) 3 else 6
                val prm = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.RGB_565 }
                var i = i0
                var okundu = 0
                while (i <= i1) {
                    ensureActive()
                    val n = min(grup, i1 - i + 1)
                    val kareler = try { r.getFramesAtIndex(i, n, prm) } catch (e: Exception) { Kayit.hata("yüz gizle kareleri", e); emptyList() }
                    if (kareler.isEmpty()) break
                    for ((j, b) in kareler.withIndex()) { if ((i + j - i0) % adim == 0) { isle(b, ((i + j) * 1000.0 / fps).toLong()); okundu++ }; b.recycle() }
                    i += n
                    ilerleme((i - i0).toFloat() / (i1 - i0 + 1))
                }
                tamam = okundu >= 2
            }
            if (!tamam) {
                // yedek yol: zamanla kare al (yavaş ama her cihazda çalışır)
                izler.forEach { it.resim?.recycle() }; izler.clear(); no = 0
                var t = k.basMs
                while (t <= k.sonMs) {
                    ensureActive()
                    val b = (if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
                        else r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)) ?: break
                    isle(b, t); b.recycle()
                    t += ADIM_MS * 2
                    ilerleme(((t - k.basMs).toFloat() / (k.sonMs - k.basMs).coerceAtLeast(1)).coerceIn(0f, 1f))
                }
            }
        } finally {
            try { r.release() } catch (_: Exception) {}
            try { alg.close() } catch (_: Exception) {}
        }
        bitir(ctx, k, izler, kw, kh, video = true).also { l -> Kayit.yaz("Yüz gizle (video): ${l.size} yüz, ${l.sumOf { it.izler.size }} iz noktası") }
    }

    /** Elle eklenen yüzün varsayılan boyu (karenin boyu oranı): bulunan yüzlerin ortancası, yoksa %12. */
    fun varsayilanBoy(g: YuzGizle?): Float = g?.yuzler?.mapNotNull { y -> y.izler.maxByOrNull { it.h }?.h }?.sorted()?.let { if (it.isEmpty()) null else it[it.size / 2] } ?: 0.12f

    /** Kaçan yüz için elle iz: fotoğrafta tek oval; videoda o andan itibaren şablon takibi. */
    suspend fun elleIz(ctx: Context, k: Klip, x: Float, y: Float, ms: Long, h: Float, ilerleme: (Float) -> Unit): List<YuzIz> {
        val en = (k.gen.takeIf { it > 0 } ?: 1).toFloat(); val boy = (k.yuk.takeIf { it > 0 } ?: 1).toFloat()
        val w = h * boy / en * 0.8f
        if (k.foto) return listOf(YuzIz(0L, x, y, w, h))
        val l = try { Takipci.izle(ctx, k, ms, x, y, ilerleme) } catch (e: Throwable) { Kayit.hata("yüz gizle: elle takip", e); emptyList() }
        return if (l.isEmpty()) listOf(YuzIz(ms, x, y, w, h)) else l.map { YuzIz(it.ms, it.x, it.y, w, h) }
    }

    /** Önizleme için kare (kaynak zamanı ms); en çok 1080 px. */
    fun kare(ctx: Context, k: Klip, ms: Long): Bitmap? = try {
        if (k.foto) YuzTur.oku(k.uri, 1080) else Medya.kareTam(ctx, Uri.parse(k.uri), ms, 1080)
    } catch (_: Throwable) { null }

    fun resim(yol: String): Bitmap? = if (yol.isEmpty()) null else try { BitmapFactory.decodeFile(yol) } catch (_: Throwable) { null }
}

/** 3.83: yüz gizleme GL etkisi — 4 geçiş (yatay, dikey, yatay, dikey). Klibin kaynak karesinde, renkten sonra, kadraj/hareketten önce.
 *  Her pikselde bulanıklık adımı oval maskeyle çarpılır: ovalin içi tam flu, kenarı yumuşak geçiş, dışı hiç değişmez.
 *  dogrudan = true: damga doğrudan klip içi zamandır (tek tuval geçişte gelen klip, bkz. TekTuval.kt). */
class YuzGizleEtki(private val k: Klip, private val yatay: Boolean, cizelgeBas: Long, private val ofsMs: Long, private val dogrudan: Boolean) : GlEffect {
    private val saat = Saat(cizelgeBas)   // kompozisyon kurulurken (zaman kipi doğru okunsun)
    fun ovaller(us: Long): List<YuzGizleme.Elips> {
        val g = k.yuzGizle ?: return emptyList()
        val ms = if (k.foto) 0L else k.basMs + (if (dogrudan) us / 1000 else ofsMs + saat.yerelMs(us))
        return YuzGizleme.anlik(g, ms, k.foto)
    }
    val guc: Float get() = k.yuzGizle?.guc ?: 0f
    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram = YuzGizleProgram(useHdr, this, yatay)

    companion object {
        fun hepsi(k: Klip, cizelgeBas: Long, ofsMs: Long, dogrudan: Boolean = false): List<YuzGizleEtki> =
            if (!k.gizleVar) emptyList() else listOf(true, false, true, false).map { YuzGizleEtki(k, it, cizelgeBas, ofsMs, dogrudan) }
    }
}

private class YuzGizleProgram(useHdr: Boolean, private val e: YuzGizleEtki, private val yatay: Boolean) : BaseGlShaderProgram(useHdr, 1) {
    private val program = GlProgram(KENAR_VS, FS)
    private var gen = 1; private var yuk = 1
    init { program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    override fun configure(inputWidth: Int, inputHeight: Int): Size { gen = inputWidth.coerceAtLeast(1); yuk = inputHeight.coerceAtLeast(1); return Size(inputWidth, inputHeight) }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val l = try { e.ovaller(presentationTimeUs) } catch (_: Throwable) { emptyList() }
        program.use()
        program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
        program.setFloatsUniform("uBoy", floatArrayOf(gen.toFloat(), yuk.toFloat()))
        program.setFloatsUniform("uYon", if (yatay) floatArrayOf(1f / gen, 0f) else floatArrayOf(0f, 1f / yuk))
        for (j in 0 until YuzGizleme.EN_COK) {
            val o = l.getOrNull(j)
            if (o == null) { program.setFloatsUniform("uO$j", floatArrayOf(0f, 0f, 0f, 0f)); program.setFloatsUniform("uR$j", floatArrayOf(1f, 0f, 0f)); continue }
            val rx = o.rx * gen; val ry = o.ry * yuk
            val a = Math.toRadians(o.aci.toDouble())
            program.setFloatsUniform("uO$j", floatArrayOf(o.cx * gen, o.cy * yuk, rx, ry))
            program.setFloatsUniform("uR$j", floatArrayOf(cos(a).toFloat(), sin(a).toFloat(), YuzGizleme.yaricap(rx, ry, e.guc)))
        }
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
    override fun release() { super.release(); try { program.delete() } catch (_: Exception) {} }

    companion object {
        private fun tek(j: Int) = "  oval(p, uO$j, uR$j, m, r);\n"
        val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uBoy;
uniform vec2 uYon;
uniform vec4 uO0; uniform vec4 uO1; uniform vec4 uO2; uniform vec4 uO3; uniform vec4 uO4; uniform vec4 uO5;
uniform vec3 uR0; uniform vec3 uR1; uniform vec3 uR2; uniform vec3 uR3; uniform vec3 uR4; uniform vec3 uR5;
varying vec2 vTexSamplingCoord;
// o: merkez (px, y aşağı), yarıçaplar · c: eğim cos, sin, bulanıklık yarıçapı (px)
void oval(vec2 p, vec4 o, vec3 c, inout float m, inout float r) {
  if (o.z < 0.5 || o.w < 0.5) return;
  vec2 d = p - o.xy;
  vec2 q = vec2(c.x * d.x - c.y * d.y, c.y * d.x + c.x * d.y) / o.zw;
  float k = 1.0 - smoothstep(0.74, 1.0, length(q));
  if (k > m) { m = k; r = c.z; }
}
void main() {
  vec2 t = vTexSamplingCoord;
  vec2 p = vec2(t.x * uBoy.x, (1.0 - t.y) * uBoy.y);
  float m = 0.0; float r = 0.0;
""" + (0 until YuzGizleme.EN_COK).joinToString("") { tek(it) } + """
  if (m < 0.002) { gl_FragColor = texture2D(uTexSampler, t); return; }
  vec2 a = uYon * (r * m / 4.0);
  vec4 s = texture2D(uTexSampler, t) * 0.1585;
  s += (texture2D(uTexSampler, t + a) + texture2D(uTexSampler, t - a)) * 0.1465;
  s += (texture2D(uTexSampler, t + a * 2.0) + texture2D(uTexSampler, t - a * 2.0)) * 0.1162;
  s += (texture2D(uTexSampler, t + a * 3.0) + texture2D(uTexSampler, t - a * 3.0)) * 0.0791;
  s += (texture2D(uTexSampler, t + a * 4.0) + texture2D(uTexSampler, t - a * 4.0)) * 0.0463;
  gl_FragColor = vec4(s.rgb / 0.9347, 1.0);
}
"""
    }
}

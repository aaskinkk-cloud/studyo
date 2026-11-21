package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * 3.74: YÜZ TURU — aynı fotoğrafta numaralı yüzlere / seçilen noktalara sırayla yumuşak kamera turu.
 * Web Kareden Videoya'daki Odak sisteminin (kfPlan / kfDurum) gelişmiş hâli:
 *  - durakta kalırken çok yavaş ek yaklaşma (kosinüs: yavaş başlar, yavaş biter) — görüntü hiç donmaz;
 *  - duraklar arası geçiş beşinci dereceden yumuşak eğriyle (başta ve sonda hız sıfır: "birden geçmez");
 *  - yakınlık geometrik ara değerle değişir (algıda eşit hızlı yaklaşma/uzaklaşma);
 *  - kavis: uzak duraklar arasında kamera hafifçe geri çekilip yeniden yaklaşır (sinematik; web'deki "dip");
 *  - yüz durağında yüz kadrajın biraz üstünde (%44) durur, elle noktada tam ortada;
 *  - karartma / bulanıklık YOK: fotoğrafın geri kalanı olduğu gibi kalır.
 * Önizleme, akıcı önizleme ve dışa aktarma Yuz.an() üzerinden AYNI hesabı kullanır.
 */
object YuzTur {
    /** Duraklar arası geçiş süresi (sn). */
    val GECIS = linkedMapOf("yavas" to 2.2f, "orta" to 1.4f, "hizli" to 0.85f)
    val GECIS_AD = mapOf("yavas" to "Yavaş", "orta" to "Orta", "hizli" to "Hızlı")
    /** Durakta kalırken ek yaklaşma. */
    val YAKLAS = listOf(0f to "Yok", 0.06f to "Hafif", 0.12f to "Orta", 0.22f to "Belirgin")
    /** Baştaki / sondaki tam görünüm durağının süresi (sn). */
    const val UC = 0.9f

    fun gecisSn(t: YuzTuru) = GECIS[t.gecis] ?: 2.2f

    /** Turun rahat görünmesi için gereken en kısa süre (sn). Klip bundan kısaysa uzatılır. */
    fun toplamSn(t: YuzTuru): Float {
        val n = t.noktalar.size; if (n == 0) return 0f
        val uc = (if (t.bastaTumu) 1 else 0) + (if (t.sondaTumu) 1 else 0)
        return uc * UC + n * t.bekleSn + (n - 1 + uc) * gecisSn(t)
    }

    /** Yüze göre yakınlık: baş kadraj yüksekliğinin `hedef` payı olsun (3.76 varsayılanı %55 — yalnız o kişi kadrajda; önce %32 idi,
     *  bir kişiye yaklaşınca yanındakiler de kadrajda kalıyordu). scC: kadrajı dolduran ölçek. */
    val YUZ_HEDEF = listOf(0.36f to "Geniş", 0.55f to "Yüz", 0.75f to "Yakın")
    fun yuzYakinlik(yb: Float, fotoYuk: Float, scC: Float, H: Int, hedef: Float = 0.55f): Float =
        (hedef / max(0.02f, yb * fotoYuk * scC / H)).coerceIn(1.2f, 6f).let { Math.round(it * 10f) / 10f }

    private class Durak(val x: Float, val y: Float, val z: Float, val h: Float, val p: Float, val ay: Float)
    /** O anki kamera: fotoğraftaki nokta (x, y), yakınlık z (kadrajı doldurana göre), noktanın ekrandaki yüksekliği ay (0…1). */
    class Kamera(val x: Float, val y: Float, val z: Float, val ay: Float)

    private fun bes(t: Float): Float { val k = t.coerceIn(0f, 1f); return k * k * k * (k * (k * 6f - 15f) + 10f) }
    private fun kos(t: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * t.coerceIn(0f, 1f))

    /** sn: klip içi zaman, D: klibin ekrandaki tam süresi. tx/ty/tz: tam görünüm durağı (klibin kendi kadrajı). */
    fun kamera(t: YuzTuru, sn: Float, D: Float, tx: Float, ty: Float, tz: Float): Kamera {
        val M = gecisSn(t)
        val seq = ArrayList<Durak>()
        if (t.bastaTumu) seq += Durak(tx, ty, tz, UC, 0.015f, 0.5f)
        t.noktalar.forEach { n -> seq += Durak(n.x, n.y, n.z.coerceIn(1f, 6f), t.bekleSn, t.yaklas, if (n.yb > 0f) 0.44f else 0.5f) }
        if (t.sondaTumu) seq += Durak(tx, ty, tz, UC, 0f, 0.5f)
        if (seq.size == 1) { val a = seq[0]; val q = (sn / max(0.3f, D)).coerceIn(0f, 1f); return Kamera(a.x, a.y, a.z * (1f + a.p * kos(q)), a.ay) }
        val top = toplamSn(t).coerceAtLeast(0.1f)
        var s = (sn / max(0.3f, D)).coerceIn(0f, 1f) * top     // plan klip süresine yayılır (klip uzunsa her şey biraz yavaşlar)
        for (j in seq.indices) {
            val a = seq[j]; val son = j == seq.lastIndex
            if (s <= a.h || son) { val q = if (a.h > 0f) (s / a.h).coerceIn(0f, 1f) else 1f; return Kamera(a.x, a.y, a.z * (1f + a.p * kos(q)), a.ay) }
            s -= a.h
            val b = seq[j + 1]
            if (s <= M) {
                val e = bes(s / M)
                val za = a.z * (1f + a.p); val zb = b.z
                var z = za * (zb / za).pow(e)                                           // geometrik: eşit algılanan hız
                if (t.kavis) {
                    val uz = hypot(b.x - a.x, b.y - a.y)
                    val dip = min(0.5f, uz * 0.8f) * sin(PI.toFloat() * e) * max(0f, min(za, zb) - 1f + 0.35f)
                    z = max(minOf(1f, za, zb), z - dip)
                }
                return Kamera(a.x + (b.x - a.x) * e, a.y + (b.y - a.y) * e, z, a.ay + (b.ay - a.ay) * e)
            }
            s -= M
        }
        val z = seq.last(); return Kamera(z.x, z.y, z.z, z.ay)
    }

    /** 3.76: şu an bir durakta mı? durak: noktalar sırası · gecen: o durağa varılalı geçen süre · sure: durakta kalma (plan saniyesi). */
    class Asama(val durak: Int, val gecen: Float, val sure: Float)
    fun asama(t: YuzTuru, sn: Float, D: Float): Asama? {
        val n = t.noktalar.size; if (n == 0) return null
        val M = gecisSn(t)
        val sira = ArrayList<Pair<Int, Float>>()     // (durak; -1 = tam görünüm, süre)
        if (t.bastaTumu) sira += -1 to UC
        repeat(n) { sira += it to t.bekleSn }
        if (t.sondaTumu) sira += -1 to UC
        var s = (sn / max(0.3f, D)).coerceIn(0f, 1f) * toplamSn(t).coerceAtLeast(0.1f)
        for (j in sira.indices) {
            val (d, h) = sira[j]
            if (s <= h || j == sira.lastIndex) return if (d >= 0) Asama(d, s, h) else null
            s -= h
            if (s <= M) return null
            s -= M
        }
        return null
    }

    // ---------------- yüz bulma ----------------

    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    }

    internal fun oku(uri: String, enCok: Int): Bitmap? {
        val f = Uri.parse(uri).path?.let { File(it) } ?: return null
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var s = 1
        while (max(o.outWidth, o.outHeight) / (s * 2) >= enCok) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** Fotoğraftaki yüzler, soldan sağa (numaralar bu sırayla). Önce ML Kit yüz algılama (ücretsiz, cihaz içinde; profil yüzleri de bulur),
     *  olmazsa Android'in yerleşik FaceDetector'ı. yb: baş yüksekliği (fotoğraf yüksekliğinin oranı). Küçük (arka plandaki) yüzler alınmaz. */
    suspend fun yuzleriBul(uri: String): List<OdakNoktasi> = withContext(Dispatchers.Default) {
        val b = oku(uri, 1600) ?: return@withContext emptyList()
        val ham = try {
            val alg = FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setMinFaceSize(0.03f).build())
            try { alg.process(InputImage.fromBitmap(b, 0)).bekle().map { f -> val r = f.boundingBox
                OdakNoktasi(r.exactCenterX() / b.width, (r.exactCenterY() - r.height() * 0.05f) / b.height, yb = r.height() * 1.3f / b.height) } }
            finally { try { alg.close() } catch (_: Exception) {} }
        } catch (e: Throwable) { Kayit.hata("yüz turu: ML Kit", e); null }
        val l = if (!ham.isNullOrEmpty()) ham else eskiBul(b)
        b.recycle()
        val en = l.maxOfOrNull { it.yb } ?: 0f
        l.filter { it.yb >= en * 0.33f }.map { it.copy(x = it.x.coerceIn(0f, 1f), y = it.y.coerceIn(0f, 1f), yb = it.yb.coerceIn(0.02f, 0.9f)) }.sortedBy { it.x }
            .also { Kayit.yaz("Yüz turu: ${it.size} yüz (ML Kit ${ham?.size ?: "yok"})") }
    }

    private fun eskiBul(src: Bitmap): List<OdakNoktasi> = try {
        val sc = min(1f, 1024f / max(src.width, src.height))
        val w0 = (src.width * sc).toInt(); val h = (src.height * sc).toInt().coerceAtLeast(8); val w = w0 and 1.inv()
        val k = Bitmap.createScaledBitmap(src, w.coerceAtLeast(8), h, true).copy(Bitmap.Config.RGB_565, false)
        val dizi = arrayOfNulls<FaceDetector.Face>(12)
        val n = FaceDetector(k.width, k.height, dizi.size).findFaces(k, dizi)
        val l = (0 until n).mapNotNull { dizi[it] }.filter { it.confidence() >= 0.3f }.map { f ->
            val m = android.graphics.PointF(); f.getMidPoint(m)
            OdakNoktasi(m.x / k.width, (m.y + f.eyesDistance() * 0.35f) / k.height, yb = f.eyesDistance() * 2.6f / k.height) }
        k.recycle(); l
    } catch (e: Throwable) { Kayit.hata("yüz turu: FaceDetector", e); emptyList() }

    /** Önizleme katmanı için fotoğraf (en çok 1280 px). */
    fun onizlemeResmi(uri: String): Bitmap? = try { oku(uri, 1280) } catch (e: Throwable) { null }
}

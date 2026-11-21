package app.studyo.android

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.C
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3.90: seslendirme efektleri (Ahmet'in isteği: sesi netleştiren, kalınlaştıran hazır ayarlar; mevcut kayıt korunur).
 * Özgün kayıt hiç değişmez; seçilen hazır ayar özgünden bir kez işlenip ayrı WAV olarak yazılır (`SesKaydi.islenmis`).
 * Önizleme ve dışa aktarma aynı dosyayı çalar → ikisi aynı duyulur. "Doğal" = özgün kayıt.
 * Zincir (cihazda, ücretsiz): yüksek geçiren süzgeç (uğultu/rüzgâr) → gürültü kapısı (sözcük aralarındaki fon sesi) → EQ (netlik/sıcaklık)
 * → sıkıştırıcı (seviye dengesi) → [kalın: perde −1,5 yarım ton, süre değişmez] → tepe −1 dBFS'e normalleştirme.
 */
object SesEfekt {
    val HAZIR = listOf("dogal" to "Doğal", "net" to "Net", "temiz" to "Temiz", "radyo" to "Radyo", "kalin" to "Kalın")
    val ACIKLAMA = mapOf(
        "dogal" to "Özgün kayıt, işlenmemiş.",
        "net" to "Boğukluk ve uğultu kesilir, konuşma öne çıkar.",
        "temiz" to "Sözcük aralarındaki fon sesi bastırılır, hafif netlik.",
        "radyo" to "Dolgun, dengeli, radyo spikeri tınısı.",
        "kalin" to "Ses biraz pesleşir (−1,5 yarım ton), sıcak ve dolgun.",
    )

    class Sonuc(val dosya: File, val sureMs: Long, val kirpBasMs: Long)

    /** Özgün kayıttan işlenmiş WAV üretir (efekt + hız + sessizlik kırpma). Hata olursa istisna fırlatır (özgün kayıt kalır). */
    fun isle(girdi: String, efekt: String, hiz: Float = 1f, kirp: Boolean = false): Sonuc {
        val (pcm, sr) = coz(girdi)
        var x = pcm
        // 3.95: baştaki/sondaki sessizlik (tepenin %3'ünün altı) 0,15 sn pay bırakılarak kırpılır
        var kirpBas = 0
        if (kirp && x.isNotEmpty()) {
            val tepe = x.maxOf { abs(it) }; val esik = tepe * 0.03f; val pay = (0.15f * sr).toInt()
            val ilk = x.indexOfFirst { abs(it) > esik }.takeIf { it >= 0 } ?: 0
            val son = x.indexOfLast { abs(it) > esik }.takeIf { it >= 0 } ?: (x.size - 1)
            kirpBas = (ilk - pay).coerceAtLeast(0); val bitis = (son + pay).coerceAtMost(x.size)
            if (bitis > kirpBas) x = x.copyOfRange(kirpBas, bitis)
        }
        val p = when (efekt) {
            "net" -> Ayar(hp = 90f, gate = true, gateDerin = 0.35f, eq = listOf(Eq(250f, -2f, 1.0f), Eq(3200f, 4.5f, 1.0f), Eq(8000f, 1.5f, 0.8f)), esik = -20f, oran = 3f)
            "temiz" -> Ayar(hp = 110f, gate = true, gateDerin = 0.12f, eq = listOf(Eq(3000f, 2.5f, 1.0f)), esik = -22f, oran = 2.5f)
            "radyo" -> Ayar(hp = 100f, gate = true, gateDerin = 0.3f, eq = listOf(Eq(180f, 3f, 0.9f), Eq(450f, -2f, 1.2f), Eq(3500f, 5f, 0.9f)), esik = -24f, oran = 4.5f)
            "kalin" -> Ayar(hp = 70f, gate = true, gateDerin = 0.35f, eq = listOf(Eq(150f, 3.5f, 0.9f), Eq(2800f, 2f, 1.0f)), esik = -20f, oran = 3f, perde = 2.0.pow(-1.5 / 12.0).toFloat())
            else -> Ayar()
        }
        if (p.hp > 0f) x = biquad(x, katsayiHP(p.hp, sr))
        if (p.gate) kapi(x, sr, p.gateDerin)
        for (e in p.eq) x = biquad(x, katsayiPeak(e.f, e.db, e.q, sr))
        if (p.oran > 1f) sikistir(x, sr, p.esik, p.oran)
        if (p.perde != 1f || abs(hiz - 1f) > 0.001f) x = perde(x, sr, p.perde, hiz.coerceIn(0.5f, 2f))
        if (efekt != "dogal") normallestir(x, 0.89f)
        val f = File(dosyaAdi(girdi, efekt, hiz, kirp))
        wavYaz(f, x, sr)
        return Sonuc(f, x.size * 1000L / sr, kirpBas * 1000L / sr)
    }
    fun dosyaAdi(girdi: String, efekt: String, hiz: Float, kirp: Boolean): String =
        File(File(girdi).parentFile, File(girdi).nameWithoutExtension + "_" + efekt + "_h" + (hiz * 100).toInt() + (if (kirp) "_k" else "") + ".wav").path

    private class Eq(val f: Float, val db: Float, val q: Float)
    private class Ayar(val hp: Float = 0f, val gate: Boolean = false, val gateDerin: Float = 1f, val eq: List<Eq> = emptyList(),
                       val esik: Float = 0f, val oran: Float = 1f, val perde: Float = 1f)

    // ---------------- çözme (m4a/AAC → mono float) ----------------
    private fun coz(yol: String): Pair<FloatArray, Int> {
        val ex = MediaExtractor(); ex.setDataSource(yol)
        val iz = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw IllegalStateException("ses izi yok")
        ex.selectTrack(iz)
        val bicim = ex.getTrackFormat(iz)
        var sr = bicim.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = bicim.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val dec = MediaCodec.createDecoderByType(bicim.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(bicim, null, null, 0); dec.start()
        val cikti = ArrayList<FloatArray>(); var toplam = 0
        val info = MediaCodec.BufferInfo(); var girdiBitti = false; var bitti = false
        try {
            while (!bitti) {
                if (!girdiBitti) {
                    val i = dec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val b = dec.getInputBuffer(i)!!
                        val n = ex.readSampleData(b, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); girdiBitti = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat; sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (o >= 0) {
                    val b = dec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val s = b.asShortBuffer(); val n = s.remaining() / ch.coerceAtLeast(1)
                    val a = FloatArray(n)
                    for (k in 0 until n) { var t = 0f; for (c in 0 until ch) t += s.get(k * ch + c); a[k] = t / ch / 32768f }
                    cikti += a; toplam += n
                    dec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) bitti = true
                }
            }
        } finally { runCatching { dec.stop() }; dec.release(); ex.release() }
        val tum = FloatArray(toplam); var p = 0
        for (a in cikti) { System.arraycopy(a, 0, tum, p, a.size); p += a.size }
        return tum to sr
    }

    // ---------------- süzgeçler (RBJ biquad) ----------------
    private fun katsayiHP(f: Float, sr: Int): FloatArray {
        val w = 2 * PI * f / sr; val al = sin(w) / (2 * 0.707); val c = cos(w)
        val a0 = 1 + al
        return floatArrayOf(((1 + c) / 2 / a0).toFloat(), (-(1 + c) / a0).toFloat(), ((1 + c) / 2 / a0).toFloat(), (-2 * c / a0).toFloat(), ((1 - al) / a0).toFloat())
    }
    private fun katsayiPeak(f: Float, db: Float, q: Float, sr: Int): FloatArray {
        val A = 10.0.pow(db / 40.0); val w = 2 * PI * f / sr; val al = sin(w) / (2 * q); val c = cos(w)
        val a0 = 1 + al / A
        return floatArrayOf(((1 + al * A) / a0).toFloat(), (-2 * c / a0).toFloat(), ((1 - al * A) / a0).toFloat(), (-2 * c / a0).toFloat(), ((1 - al / A) / a0).toFloat())
    }
    private fun biquad(x: FloatArray, k: FloatArray): FloatArray {
        val y = FloatArray(x.size); var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        for (i in x.indices) {
            val v = k[0] * x[i] + k[1] * x1 + k[2] * x2 - k[3] * y1 - k[4] * y2
            x2 = x1; x1 = x[i]; y2 = y1; y1 = v; y[i] = v
        }
        return y
    }

    // ---------------- dinamik ----------------
    /** Gürültü kapısı: zarf, en gürültülü bölümün %4'ünün (≈ −28 dB) altına inince kazanç yumuşakça `derin`e düşer (0,1 sn bekleme). */
    private fun kapi(x: FloatArray, sr: Int, derin: Float) {
        if (x.isEmpty()) return
        val tepe = x.maxOf { abs(it) }.coerceAtLeast(1e-4f)
        val esik = tepe * 0.04f
        val aAt = exp(-1.0 / (0.002 * sr)).toFloat(); val aRel = exp(-1.0 / (0.15 * sr)).toFloat()
        val zarfA = exp(-1.0 / (0.01 * sr)).toFloat()
        val bekle = (0.10 * sr).toInt()
        var zarf = 0f; var g = 1f; var sayac = 0
        for (i in x.indices) {
            val v = abs(x[i]); zarf = if (v > zarf) v else zarfA * zarf + (1 - zarfA) * v
            val acik = zarf > esik
            if (acik) sayac = bekle else if (sayac > 0) sayac--
            val hedef = if (acik || sayac > 0) 1f else derin
            g = if (hedef > g) aAt * g + (1 - aAt) * hedef else aRel * g + (1 - aRel) * hedef
            x[i] *= g
        }
    }
    /** Sıkıştırıcı (RMS zarf, 5 ms atak, 120 ms bırakma) + otomatik telafi kazancı. */
    private fun sikistir(x: FloatArray, sr: Int, esikDb: Float, oran: Float) {
        val aAt = exp(-1.0 / (0.005 * sr)).toFloat(); val aRel = exp(-1.0 / (0.12 * sr)).toFloat()
        var e = 0f
        for (i in x.indices) {
            val s = x[i] * x[i]; e = if (s > e) aAt * e + (1 - aAt) * s else aRel * e + (1 - aRel) * s
            val db = 10f * log10(max(e, 1e-10f))
            val azalt = if (db > esikDb) (db - esikDb) * (1f - 1f / oran) else 0f
            x[i] *= 10f.pow(-azalt / 20f)
        }
    }
    private fun normallestir(x: FloatArray, hedef: Float) {
        val t = x.maxOfOrNull { abs(it) } ?: return
        if (t < 1e-5f) return
        val k = hedef / t
        for (i in x.indices) x[i] = (x[i] * k).coerceIn(-1f, 1f)
    }

    /** Perde değişimi (süre aynı) — Media3 Sonic. */
    private fun perde(x: FloatArray, sr: Int, oran: Float, hiz: Float = 1f): FloatArray {
        val s = SonicAudioProcessor()
        s.setPitch(oran); s.setSpeed(hiz)   // 3.95: hız (süre değişir, perde korunur)
        s.configure(AudioProcessor.AudioFormat(sr, 1, C.ENCODING_PCM_16BIT)); s.flush()
        val out = java.io.ByteArrayOutputStream()
        fun topla() { while (true) { val o = s.output; if (!o.hasRemaining()) break; val b = ByteArray(o.remaining()); o.get(b); out.write(b) } }
        val parca = 4096; var i = 0
        while (i < x.size) {
            val n = min(parca, x.size - i)
            val bb = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (k in 0 until n) bb.putShort((x[i + k].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            bb.flip()
            while (bb.hasRemaining()) { s.queueInput(bb); topla() }
            i += n
        }
        s.queueEndOfStream(); topla()
        val b = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.nativeOrder()).asShortBuffer()
        return FloatArray(b.remaining()) { b.get(it) / 32768f }
    }

    private fun wavYaz(f: File, x: FloatArray, sr: Int) {
        val veri = x.size * 2
        RandomAccessFile(f, "rw").use { r ->
            r.setLength(0)
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + veri); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1); h.putInt(sr); h.putInt(sr * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(veri)
            r.write(h.array())
            val b = ByteBuffer.allocate(veri).order(ByteOrder.LITTLE_ENDIAN)
            for (v in x) b.putShort((v.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            r.write(b.array())
        }
    }
}

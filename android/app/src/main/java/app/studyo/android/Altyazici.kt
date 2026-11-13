package app.studyo.android

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** Otomatik altyazı: Vosk (Apache-2.0) + Türkçe küçük model (~35 MB, bir kez iner). Tamamen telefonda çalışır, ses dışarı gitmez.
 *  Ses akışla çözülür (MediaCodec), 16 kHz tek kanala çevrilip parça parça tanıyıcıya verilir; video belleğe alınmaz. */
object Altyazici {
    private const val ADRES = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"

    fun modelDizini(ctx: Context) = File(ctx.filesDir, "vosk-tr")
    /** Model kökü: içinde am/final.mdl (ya da final.mdl) bulunan klasör. */
    private fun modelKoku(d: File): File? {
        if (File(d, "am/final.mdl").isFile || File(d, "final.mdl").isFile) return d
        return d.listFiles()?.filter { it.isDirectory }?.firstNotNullOfOrNull { modelKoku(it) }
    }
    fun modelHazir(ctx: Context) = modelDizini(ctx).isDirectory && modelKoku(modelDizini(ctx)) != null
    fun modelYolu(ctx: Context): String = (modelKoku(modelDizini(ctx)) ?: modelDizini(ctx)).path

    suspend fun modelIndir(ctx: Context, ilerleme: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val zip = File(ctx.cacheDir, "vosk-tr.zip")
        val c = URL(ADRES).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Model indirilemedi (HTTP ${c.responseCode})")
            val toplam = c.contentLengthLong
            c.inputStream.use { i ->
                zip.outputStream().use { o ->
                    val b = ByteArray(256 * 1024); var n: Int; var say = 0L
                    while (i.read(b).also { n = it } >= 0) {
                        coroutineContext.ensureActive()
                        o.write(b, 0, n); say += n
                        if (toplam > 0) ilerleme((say.toFloat() / toplam) * 0.9f)
                    }
                }
            }
        } finally { c.disconnect() }
        // Kök neden (3.2): arşivin ilk klasörü atılıyordu; arşiv yapısı farklıysa am/ ve conf/ yanlış yere açılıyordu.
        // Şimdi tam yolla açılır, model kökü (am/final.mdl olan klasör) aranarak bulunur.
        if (zip.length() < 5_000_000L) { val b = zip.length(); zip.delete(); throw IllegalStateException("Model dosyası eksik indi ($b bayt). İnternet bağlantısını kontrol edip tekrar dene.") }
        val hedef = modelDizini(ctx)
        hedef.deleteRecursively(); hedef.mkdirs()
        val kok = hedef.canonicalPath
        var adet = 0
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val f = File(hedef, e.name)
                if (!f.canonicalPath.startsWith(kok)) throw IllegalStateException("Model arşivi hatalı")
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) }; adet++ }
                e = z.nextEntry
            }
        }
        zip.delete()
        if (!modelHazir(ctx)) {
            val ornek = hedef.walkTopDown().take(15).joinToString(", ") { it.relativeTo(hedef).path }
            Kayit.yaz("Vosk modeli açıldı ama kök bulunamadı: $adet dosya; ilkler: $ornek")
            hedef.deleteRecursively()
            throw IllegalStateException("Model arşivi beklenen yapıda değil ($adet dosya). Hata kaydını paylaş.")
        }
        Kayit.yaz("Vosk modeli hazır: ${modelYolu(ctx)} ($adet dosya)")
        ilerleme(1f)
    }

    /** Doğrusal yeniden örnekleme (akış halinde). */
    private class Ornekleyici(girisHz: Int) {
        private val adim = girisHz / 16000.0
        private var t = 0.0
        private var onceki = 0f
        fun isle(s: Float, cik: (Float) -> Unit) {
            while (t <= 1.0) { cik(onceki + (s - onceki) * t.toFloat()); t += adim }
            t -= 1.0; onceki = s
        }
    }

    /** Bir dosyanın [basMs, sonMs] aralığındaki sesini tanır; kelimeleri zaman çizelgesine (kayma = ofsetMs) çevirir. */
    private suspend fun tani(ctx: Context, model: Model, uri: Uri, basMs: Long, sonMs: Long, ofsetMs: Long, ilerleme: (Float) -> Unit): List<Kelime> {
        val ex = MediaExtractor()
        val sonuc = ArrayList<Kelime>()
        var dec: MediaCodec? = null
        val rec = Recognizer(model, 16000f)
        try {
            rec.setWords(true)
            ex.setDataSource(ctx, uri, null)
            var iz = -1
            for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { iz = i; break }
            if (iz < 0) return emptyList()
            ex.selectTrack(iz)
            val f = ex.getTrackFormat(iz)
            val d = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            dec = d
            d.configure(f, null, null, 0); d.start()
            ex.seekTo(basMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var hz = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var kanal = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var kayan = false
            var orn = Ornekleyici(hz)
            val tampon = ByteArray(8000); var tn = 0
            val bi = MediaCodec.BufferInfo()
            var girisBitti = false; var bitti = false
            fun sonuclari(json: String) {
                val a = JSONObject(json).optJSONArray("result") ?: return
                for (i in 0 until a.length()) {
                    val w = a.getJSONObject(i)
                    val m = w.optString("word").trim()
                    if (m.isEmpty()) continue
                    sonuc += Kelime(ofsetMs + (w.optDouble("start") * 1000).toLong(), ofsetMs + (w.optDouble("end") * 1000).toLong(), m)
                }
            }
            fun ver(s: Float) {
                val v = (s * 32767f).toInt().coerceIn(-32768, 32767)
                tampon[tn++] = (v and 255).toByte(); tampon[tn++] = (v shr 8 and 255).toByte()
                if (tn >= tampon.size) { if (rec.acceptWaveForm(tampon, tn)) sonuclari(rec.result); tn = 0 }
            }
            val sureUs = ((sonMs - basMs) * 1000).coerceAtLeast(1)
            while (!bitti) {
                coroutineContext.ensureActive()
                if (!girisBitti) {
                    val ii = d.dequeueInputBuffer(10000)
                    if (ii >= 0) {
                        val b = d.getInputBuffer(ii)!!
                        val n = ex.readSampleData(b, 0)
                        if (n < 0 || ex.sampleTime > sonMs * 1000 + 300_000) { d.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); girisBitti = true }
                        else { d.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = d.dequeueOutputBuffer(bi, 10000)
                if (oi >= 0) {
                    val ob = d.getOutputBuffer(oi)
                    if (ob != null && bi.size > 0) {
                        ob.position(bi.offset); ob.limit(bi.offset + bi.size); ob.order(ByteOrder.LITTLE_ENDIAN)
                        val kare = if (kayan) bi.size / 4 / kanal else bi.size / 2 / kanal
                        val fb = if (kayan) ob.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer() else null
                        val sb = if (!kayan) ob.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer() else null
                        for (k in 0 until kare) {
                            val tUs = bi.presentationTimeUs + k * 1_000_000L / hz
                            if (tUs < basMs * 1000 || tUs > sonMs * 1000) continue
                            var top = 0f
                            for (c in 0 until kanal) top += if (fb != null) fb.get(k * kanal + c) else sb!!.get(k * kanal + c) / 32768f
                            orn.isle(top / kanal) { ver(it) }
                        }
                        ilerleme(((bi.presentationTimeUs - basMs * 1000).toFloat() / sureUs).coerceIn(0f, 1f))
                    }
                    d.releaseOutputBuffer(oi, false)
                    if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) bitti = true
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = d.outputFormat
                    hz = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    kanal = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    kayan = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    orn = Ornekleyici(hz)
                }
            }
            if (tn > 0) rec.acceptWaveForm(tampon, tn)
            sonuclari(rec.finalResult)
        } finally {
            try { dec?.stop() } catch (_: Exception) {}
            try { dec?.release() } catch (_: Exception) {}
            ex.release()
            rec.close()
        }
        return sonuc
    }

    /** Kelimeleri okunur satırlara böler: en fazla 7 kelime / 38 harf / 3,5 sn; 0,7 sn'lik duraklamada yeni satır. */
    fun satirla(k: List<Kelime>): List<Altyazi> {
        val o = ArrayList<Altyazi>()
        var cur = ArrayList<Kelime>()
        fun bitir() {
            if (cur.isEmpty()) return
            o += Altyazi(basMs = cur.first().basMs, sonMs = cur.last().sonMs + 150, metin = cur.joinToString(" ") { it.metin }, kelimeler = cur.toList())
            cur = ArrayList()
        }
        for (w in k.sortedBy { it.basMs }) {
            val c = cur.lastOrNull()
            val uzun = cur.size >= 7 || (cur.sumOf { it.metin.length + 1 } + w.metin.length) > 38 || (cur.isNotEmpty() && w.sonMs - cur.first().basMs > 3500)
            if (c != null && (w.basMs - c.sonMs > 700 || uzun)) bitir()
            cur += w
        }
        bitir()
        // satırlar çakışmasın
        for (i in 0 until o.size - 1) if (o[i].sonMs > o[i + 1].basMs) o[i] = o[i].copy(sonMs = o[i + 1].basMs)
        return o
    }

    /** kaynak: "klip" (videoların kendi sesi) ya da "ses" (seslendirme kayıtları). */
    suspend fun olustur(ctx: Context, p: Proje, kaynak: String, ilerleme: (Float) -> Unit): List<Altyazi> = withContext(Dispatchers.Default) {
        val model = Model(modelYolu(ctx))
        try {
            val isler = ArrayList<Triple<Uri, LongArray, Long>>()   // dosya, [bas, son], zaman çizelgesi ofseti
            if (kaynak == "ses") {
                for (s in p.sesler) isler += Triple(Uri.fromFile(File(s.yol)), longArrayOf(0, s.sureMs), s.basMs)
            } else {
                var bas = 0L
                for (k in p.klipler) {
                    if (!k.foto && k.sesVar && !k.sessiz) isler += Triple(Uri.parse(k.uri), longArrayOf(k.basMs, k.sonMs), bas)   // tanıyıcı zamanı kırpma başından sayar
                    bas += k.sureMs
                }
            }
            val toplam = isler.sumOf { it.second[1] - it.second[0] }.coerceAtLeast(1)
            var biten = 0L
            val kelimeler = ArrayList<Kelime>()
            for ((u, ar, of) in isler) {
                val bu = ar[1] - ar[0]
                kelimeler += tani(ctx, model, u, ar[0], ar[1], of) { f -> ilerleme(((biten + bu * f) / toplam).coerceIn(0f, 1f)) }
                biten += bu
            }
            Kayit.yaz("Otomatik altyazı ($kaynak): ${isler.size} dosya, ${kelimeler.size} kelime")
            satirla(kelimeler)
        } finally {
            model.close()
        }
    }
}

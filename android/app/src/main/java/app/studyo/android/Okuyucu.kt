package app.studyo.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 4.02: metni sese çeviren okuyucu (Okur / Karanlık Oda'daki DFKI sesi). Piper "tr_TR-dfki-medium" modeli,
 * sherpa-onnx (Apache-2.0) ile tamamen telefonda çalışır; ücretsiz, internet yalnız modelin ilk indirilişinde gerekir (~67 MB, GitHub).
 * Motor bir kez yüklenip bellekte tutulur → ikinci okumadan itibaren hemen başlar. Dinle: cümle cümle üretilip akışla çalınır.
 */
object Okuyucu {
    private const val ADRES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-tr_TR-dfki-medium.tar.bz2"
    private const val AD = "vits-piper-tr_TR-dfki-medium"
    /** Ses tonu (perde çarpanı; süre değişmez). Tok = daha pes, anlatıcı tınısı. */
    val TONLAR = listOf(1.06f to "İnce", 1f to "Normal", 0.94f to "Tok", 0.89f to "Daha tok")

    fun dizin(ctx: Context) = File(ctx.filesDir, "okuyucu/$AD")
    fun hazir(ctx: Context): Boolean { val d = dizin(ctx)
        return File(d, "tr_TR-dfki-medium.onnx").isFile && File(d, "tokens.txt").isFile && File(d, "espeak-ng-data/phontab").isFile }

    suspend fun indir(ctx: Context, ilerleme: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val ar = File(ctx.cacheDir, "$AD.tar.bz2")
        val c = URL(ADRES).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("Ses indirilemedi (HTTP ${c.responseCode})")
            val toplam = c.contentLengthLong
            c.inputStream.use { i -> ar.outputStream().use { o ->
                val b = ByteArray(256 * 1024); var n: Int; var say = 0L
                while (i.read(b).also { n = it } >= 0) { coroutineContext.ensureActive(); o.write(b, 0, n); say += n
                    if (toplam > 0) ilerleme(say.toFloat() / toplam * 0.85f) }
            } }
        } finally { c.disconnect() }
        if (ar.length() < 20_000_000L) { val b = ar.length(); ar.delete(); throw IllegalStateException("Ses dosyası eksik indi ($b bayt). Bağlantıyı kontrol edip tekrar dene.") }
        val ust = File(ctx.filesDir, "okuyucu"); val hedef = dizin(ctx)
        hedef.deleteRecursively(); ust.mkdirs()
        val kok = ust.canonicalPath
        var adet = 0
        TarArchiveInputStream(BZip2CompressorInputStream(ar.inputStream().buffered(1 shl 16))).use { t ->
            var e = t.nextEntry
            while (e != null) {
                coroutineContext.ensureActive()
                val f = File(ust, e.name)
                if (!f.canonicalPath.startsWith(kok)) throw IllegalStateException("Ses arşivi hatalı")
                if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { t.copyTo(it) }; adet++ }
                if (adet % 40 == 0) ilerleme(0.85f + 0.15f * (adet / 400f).coerceAtMost(1f))
                e = t.nextEntry
            }
        }
        ar.delete()
        if (!hazir(ctx)) { hedef.deleteRecursively(); throw IllegalStateException("Ses arşivi beklenen yapıda değil ($adet dosya).") }
        Kayit.yaz("Okuyucu sesi hazır: ${hedef.path} ($adet dosya)")
        ilerleme(1f)
    }

    private var motor: OfflineTts? = null
    private val kilit = Mutex()
    @Volatile private var dur = false

    private fun motor(ctx: Context): OfflineTts = motor ?: run {
        val d = dizin(ctx).path
        val cfg = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = "$d/tr_TR-dfki-medium.onnx", tokens = "$d/tokens.txt", dataDir = "$d/espeak-ng-data"),
            numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4), debug = false, provider = "cpu"),
            maxNumSentences = 1)
        OfflineTts(config = cfg).also { motor = it }
    }

    /** Motoru önceden yükler (sayfa açılınca) → "Dinle"ye basınca bekleme olmaz. */
    suspend fun isit(ctx: Context) = withContext(Dispatchers.Default) { if (hazir(ctx)) kilit.withLock { try { motor(ctx) } catch (e: Throwable) { Kayit.hata("okuyucu", e) } } }

    fun durdur() { dur = true }

    /** Cümle cümle üretip anında çalar. Dönüş: tamamlandı mı (durdurulmadıysa true). */
    suspend fun dinle(ctx: Context, metin: String, hiz: Float, ton: Float): Boolean = withContext(Dispatchers.Default) {
        kilit.withLock {
            dur = false
            val m = motor(ctx); val sr = m.sampleRate()
            val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val tr = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, sr)).setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                if (ton != 1f) try { tr.playbackParams = PlaybackParams().setPitch(ton).setSpeed(1f) } catch (_: Throwable) {}
                tr.play()
                m.generateWithCallback(metin, 0, hiz.coerceIn(0.5f, 2f)) { parca ->
                    if (dur) 0 else { tr.write(parca, 0, parca.size, AudioTrack.WRITE_BLOCKING); if (dur) 0 else 1 }
                }
                if (!dur) { tr.stop() }   // stop(): tampondaki son cümle de çalınır
                !dur
            } finally {
                if (dur) { try { tr.pause(); tr.flush() } catch (_: Throwable) {} }
                tr.release()
            }
        }
    }

    /** Metni WAV dosyasına okur (ton uygulanmış). Dönüş: dosya + süre (ms). */
    suspend fun dosyaya(ctx: Context, metin: String, hiz: Float, ton: Float, hedef: File, ilerleme: (Float) -> Unit): Long = withContext(Dispatchers.Default) {
        kilit.withLock {
            dur = false
            val m = motor(ctx); val sr = m.sampleRate()
            val parcalar = ArrayList<FloatArray>(); var n = 0
            val toplamK = metin.length.coerceAtLeast(1)
            m.generateWithCallback(metin, 0, hiz.coerceIn(0.5f, 2f)) { p -> parcalar += p.copyOf(); n += p.size
                // ilerleme yaklaşık: üretilen sesin, metnin tahmini süresine oranı (~14 harf/sn)
                ilerleme((n.toFloat() / sr / (toplamK / 14f / hiz)).coerceIn(0f, 0.95f)); if (dur) 0 else 1 }
            if (dur) throw kotlinx.coroutines.CancellationException("durduruldu")
            var x = FloatArray(n); var i = 0
            for (p in parcalar) { System.arraycopy(p, 0, x, i, p.size); i += p.size }
            if (x.isEmpty()) throw IllegalStateException("Metinden ses üretilemedi")
            if (ton != 1f) x = SesEfekt.tonla(x, sr, ton)
            SesEfekt.wavKaydet(hedef, x, sr)
            ilerleme(1f)
            x.size * 1000L / sr
        }
    }
}

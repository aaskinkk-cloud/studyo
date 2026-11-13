package app.studyo.android

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Ses dalgası: dosya çözülür, her 50 ms için en yüksek genlik (0…1). Bellekte ve diskte (cache/dalga) saklanır. */
object Dalga {
    const val ADIM_MS = 50L
    private val bellek = HashMap<String, FloatArray>()
    private val kilit = Mutex()

    suspend fun oku(ctx: Context, uri: String): FloatArray? = withContext(Dispatchers.IO) {
        kilit.withLock {
            bellek[uri]?.let { return@withLock it }
            val dosya = File(File(ctx.cacheDir, "dalga").apply { mkdirs() }, "${uri.hashCode().toUInt()}.bin")
            val d = diskten(dosya) ?: hesapla(ctx, uri)?.also { diske(dosya, it) }
            d?.also { bellek[uri] = it }
        }
    }

    private fun diskten(f: File): FloatArray? = try {
        if (!f.exists()) null else {
            val b = f.readBytes()
            FloatArray(b.size) { (b[it].toInt() and 0xFF) / 255f }
        }
    } catch (_: Exception) { null }

    private fun diske(f: File, d: FloatArray) {
        try { f.writeBytes(ByteArray(d.size) { (d[it].coerceIn(0f, 1f) * 255).toInt().toByte() }) } catch (_: Exception) {}
    }

    private fun hesapla(ctx: Context, uri: String): FloatArray? {
        val ex = MediaExtractor()
        var kod: MediaCodec? = null
        try {
            val u = Uri.parse(uri)
            if (u.scheme == null || u.scheme == "file") ex.setDataSource(u.path ?: uri) else ex.setDataSource(ctx, u, null)
            var iz = -1; var fmt: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { iz = i; fmt = f; break }
            }
            if (iz < 0 || fmt == null) return null
            ex.selectTrack(iz)
            val sureUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) fmt.getLong(MediaFormat.KEY_DURATION) else 0L
            val enUzunUs = 20L * 60 * 1_000_000      // en çok 20 dk çözülür (çizim için yeterli)
            val n = ((minOf(sureUs, enUzunUs) / 1000) / ADIM_MS).toInt().coerceAtLeast(1)
            val tepe = FloatArray(n)
            kod = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
            kod.configure(fmt, null, null, 0); kod.start()
            val bilgi = MediaCodec.BufferInfo()
            var girisBitti = false; var bitti = false
            var hz = if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var kanal = if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            var kayan = false
            val t0 = System.currentTimeMillis()
            while (!bitti && System.currentTimeMillis() - t0 < 25_000) {
                if (!girisBitti) {
                    val gi = kod.dequeueInputBuffer(10_000)
                    if (gi >= 0) {
                        val buf = kod.getInputBuffer(gi)!!
                        val boy = ex.readSampleData(buf, 0)
                        if (boy < 0 || ex.sampleTime > enUzunUs) { kod.queueInputBuffer(gi, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); girisBitti = true }
                        else { kod.queueInputBuffer(gi, 0, boy, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val ci = kod.dequeueOutputBuffer(bilgi, 10_000)
                if (ci == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = kod.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) hz = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) kanal = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    kayan = of.containsKey(MediaFormat.KEY_PCM_ENCODING) && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (ci >= 0) {
                    val buf = kod.getOutputBuffer(ci)
                    if (buf != null && bilgi.size > 0) {
                        buf.position(bilgi.offset); buf.limit(bilgi.offset + bilgi.size)
                        val bb = buf.slice().order(ByteOrder.nativeOrder())
                        val ornekBoy = if (kayan) 4 else 2
                        val toplamOrnek = bilgi.size / ornekBoy
                        val usPerKare = 1_000_000.0 / hz
                        for (j in 0 until toplamOrnek step kanal.coerceAtLeast(1)) {
                            val v = if (kayan) abs(bb.getFloat(j * 4)) else abs(bb.getShort(j * 2).toInt()) / 32768f
                            val us = bilgi.presentationTimeUs + (j / kanal.coerceAtLeast(1)) * usPerKare
                            val k = ((us / 1000) / ADIM_MS).toInt()
                            if (k in 0 until n) tepe[k] = max(tepe[k], v)
                        }
                    }
                    kod.releaseOutputBuffer(ci, false)
                    if (bilgi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) bitti = true
                }
            }
            val enB = tepe.maxOrNull()?.takeIf { it > 0.001f } ?: return tepe
            for (i in tepe.indices) tepe[i] = (tepe[i] / enB).coerceIn(0f, 1f)
            return tepe
        } catch (e: Throwable) {
            Kayit.hata("ses dalgası", e); return null
        } finally {
            try { kod?.stop() } catch (_: Exception) {}
            try { kod?.release() } catch (_: Exception) {}
            ex.release()
        }
    }
}

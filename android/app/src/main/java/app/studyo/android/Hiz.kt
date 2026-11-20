package app.studyo.android

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 3.70: klip hızı. KÖK TASARIM: hız zaman çizgisine hesap olarak girmez (geçiş, katman, takip, dışa aktarma ve akıcı önizleme
 *  klip süresini her yerde kendi hesaplıyor; araya hız çarpanı sokmak titreme/kayma riskidir). Bunun yerine klibin seçili aralığı
 *  Media3'ün ses+görüntü birlikte hız etkisiyle (Effects.createExperimentalSpeedChangingEffect) BİR KEZ yeni dosyaya işlenir;
 *  sonra her yer onu normal bir klip olarak görür. Özgün dosya silinmez (1× = geri döner). */
object HizIsleyici {
    val HIZLAR = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f)
    fun yaz(h: Float): String = (if (h == h.toInt().toFloat()) h.toInt().toString() else h.toString().replace('.', ',')) + "×"

    suspend fun isle(ctx: Context, uri: String, basMs: Long, sonMs: Long, hiz: Float, sesli: Boolean, ilerle: (Int) -> Unit): File =
        withContext(Dispatchers.Main) {
            val cikti = File(Depo.dizin("hiz"), yeniId() + ".mp4")
            val mi = MediaItem.Builder().setUri(Uri.parse(uri)).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(basMs).setEndPositionMs(sonMs).build()).build()
            val etki = Effects.createExperimentalSpeedChangingEffect(SabitHiz(hiz.coerceIn(0.25f, 4f)))
            val sesler: List<AudioProcessor> = if (sesli) listOf(etki.first) else listOf()
            val goruntu: List<Effect> = listOf(etki.second)
            val eb = EditedMediaItem.Builder(mi).setEffects(Effects(sesler, goruntu))
            if (!sesli) eb.setRemoveAudio(true)
            val comp = Composition.Builder(listOf(EditedMediaItemSequence.Builder(listOf(eb.build())).build()))
                .setHdrMode(Disa.HDR_COZUCU).build()
            Kayit.yaz("Hız: ${yaz(hiz)} $basMs-$sonMs ms ses=$sesli → ${cikti.name}")
            suspendCancellableCoroutine { c ->
                val h = Handler(Looper.getMainLooper())
                val tutucu = ProgressHolder()
                var tr: Transformer? = null
                val izle = object : Runnable {
                    override fun run() {
                        val t = tr ?: return
                        if (t.getProgress(tutucu) == Transformer.PROGRESS_STATE_AVAILABLE) ilerle(tutucu.progress)
                        h.postDelayed(this, 500)
                    }
                }
                val t = Transformer.Builder(ctx)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            h.removeCallbacks(izle); if (c.isActive) c.resume(cikti)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            h.removeCallbacks(izle); cikti.delete()
                            if (c.isActive) c.resumeWithException(exportException)
                        }
                    }).build()
                tr = t
                c.invokeOnCancellation { h.post { h.removeCallbacks(izle); try { t.cancel() } catch (_: Exception) {}; cikti.delete() } }
                t.start(comp, cikti.path)
                h.post(izle)
            }
        }
}

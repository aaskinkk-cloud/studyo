package app.studyo.android

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 3.61: videonun konusuna göre müzik seçimi — Google ML Kit görüntü etiketleme (ücretsiz, cihaz içinde).
 *  Kliplerden en fazla 8 kare etiketlenir; etiketler müzik türlerine puan verir. Model yoksa/inmemişse "Sakin" (doğa videoları). */
object MuzikSecici {
    private val KURAL = listOf(
        "calm" to listOf("sky", "cloud", "mountain", "lake", "river", "sea", "beach", "forest", "tree", "plant", "flower", "sunset", "snow", "grass", "waterfall", "leaf", "garden", "field", "landscape", "nature", "hill"),
        "happy" to listOf("smile", "party", "fun", "dog", "cat", "pet", "child", "baby", "toy", "balloon", "dance", "festival", "event", "crowd", "team", "game", "playground", "bird"),
        "warm" to listOf("food", "cup", "tea", "coffee", "dessert", "bread", "candle", "bonfire", "fire", "cuisine", "dishware", "tableware", "drink", "kitchen", "hand", "flesh", "couple", "wedding"),
        "drive" to listOf("car", "vehicle", "motorcycle", "bicycle", "sport", "running", "road", "racing", "skateboard", "bus", "train", "boat", "jet", "wheel", "tire"),
        "k_adag" to listOf("building", "architecture", "monument", "church", "mosque", "castle", "museum", "painting", "tower", "temple", "bridge", "stained glass"),
        "k_dram" to listOf("storm", "lightning", "night", "darkness", "fog", "smoke", "rock", "cave", "military"),
        "k_bach" to listOf("book", "musical instrument", "piano", "desk", "room", "interior design", "paper"),
    )

    private suspend fun <T> Task<T>.bekle(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
    }

    suspend fun konudanTur(ctx: Context, p: Proje): String = withContext(Dispatchers.Default) {
        if (p.klipler.isEmpty()) return@withContext "calm"
        val puan = HashMap<String, Float>()
        val etiketci = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
        try {
            val adim = maxOf(1, p.klipler.size / 8)
            for (i in p.klipler.indices step adim) {
                val k = p.klipler[i]
                val b: Bitmap = Medya.kucukResim(ctx, k, 480) ?: continue
                val etiketler = try { etiketci.process(InputImage.fromBitmap(b, 0)).bekle() } catch (e: Exception) { Kayit.hata("etiket", e); break }
                for (e in etiketler) {
                    val ad = e.text.lowercase()
                    for ((tur, kelimeler) in KURAL) if (kelimeler.any { ad.contains(it) }) puan[tur] = (puan[tur] ?: 0f) + e.confidence
                }
            }
        } finally { try { etiketci.close() } catch (_: Exception) {} }
        Kayit.yaz("Müzik konusu puanları: $puan")
        puan.maxByOrNull { it.value }?.key ?: "calm"
    }
}

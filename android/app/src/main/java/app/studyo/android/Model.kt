package app.studyo.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
val TR: Locale = Locale("tr", "TR")
const val WEB_ADRES = "https://aaskinkk-cloud.github.io/studyo/"

fun yeniId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

fun sureYaz(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
fun sureOndalik(ms: Long): String = sureYaz(ms) + "," + ((ms.coerceAtLeast(0) % 1000) / 100)

@Serializable
data class Yazi(
    val metin: String = "",
    val konum: String = "alt",      // ust | orta | alt
    val boyut: Float = 0.07f,       // kısa kenarın oranı
    val renk: Long = 0xFFFFFFFF,
    val kutu: Boolean = true,
)

@Serializable
data class Klip(
    val id: String = yeniId(),
    val tur: String = "video",      // video | foto
    val uri: String,
    val ad: String = "",
    val kaynakSureMs: Long = 0,
    val basMs: Long = 0,
    val sonMs: Long = 0,
    val fotoSureMs: Long = 4000,
    val ses: Float = 1f,
    val sessiz: Boolean = false,
    val doldur: Boolean = false,
    val kenBurns: Boolean = true,
    val yazi: Yazi? = null,
    val gen: Int = 0,
    val yuk: Int = 0,
    val boyutBayt: Long = 0,
    val kodek: String = "",
    val hdr: Boolean = false,
    val sesVar: Boolean = true,
) {
    val foto: Boolean get() = tur == "foto"
    val sureMs: Long get() = if (foto) fotoSureMs else (sonMs - basMs).coerceAtLeast(100)
}

@Serializable
data class Muzik(val uri: String, val ad: String = "", val ses: Float = 0.35f, val dongu: Boolean = true, val sureMs: Long = 0)

@Serializable
data class SesKaydi(val id: String = yeniId(), val yol: String, val basMs: Long, val sureMs: Long, val ses: Float = 1f)

@Serializable
data class Logo(val yol: String, val konum: String = "sagUst", val boyut: Float = 0.16f, val opaklik: Float = 1f, val yuvarlak: Boolean = true)

@Serializable
data class Proje(
    val id: String = yeniId(),
    val ad: String = "Yeni video",
    val olusturma: Long = System.currentTimeMillis(),
    val guncelleme: Long = System.currentTimeMillis(),
    val bicim: String = "16:9",     // 16:9 | 9:16 | 1:1
    val klipler: List<Klip> = emptyList(),
    val muzik: Muzik? = null,
    val sesler: List<SesKaydi> = emptyList(),
    val logo: Logo? = null,
) {
    val toplamMs: Long get() = klipler.sumOf { it.sureMs }
}

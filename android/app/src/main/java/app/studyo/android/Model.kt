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
    val takip: Takip? = null,
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

/** Klip geçişi (tüm projede). Çakışmalı geçişler (çapraz/eriyerek/kaydırma) iki klibi aynı anda oynatmayı gerektirir; henüz yok. */
@Serializable
data class Gecis(val tur: String = "yok", val sureMs: Long = 700, val siyahtanAc: Boolean = false) {
    // yok | siyah | beyaz | yakin  (tek dizi)  ·  capraz | kaydir | yakincapraz  (üst üste binen: giden klibin devamı üstte solar)
    val cakisma: Boolean get() = tur == "capraz" || tur == "kaydir" || tur == "yakincapraz"
}

@Serializable
data class Giris(
    val acik: Boolean = false, val stil: String = "sinematik", val baslik: String = "", val alt: String = "", val sureMs: Long = 3500,
    val bant: String = "",               // reels stillerinde vurgu bantları (| ile ayrı bant)
    val hareket: String = "kay",         // reels giriş hareketi: trend | dus | kay | yuksel | buyu
    val konum: String = "orta",          // reels yazılarının yeri: ust | orta | alt
    val efektSes: Boolean = true, val sesSeviye: Float = 0.6f,
    val boncuk: Boolean = false,         // ışıklı boncuklar (girişte süzülen ışık noktaları)
) {
    val reels: Boolean get() = stil.startsWith("r_")
}

@Serializable
data class Hatirlatici(val acik: Boolean = false, val anOran: Float = 0.5f, val yazi: String = "Abone ol", val kucuk: String = "Yeni videolar için")

@Serializable
data class Kapanis(val acik: Boolean = false, val stil: String = "cam", val sureMs: Long = 5000, val kanal: String = "Şefin Doğası", val yazi: String = "İzlediğiniz için teşekkürler")

@Serializable
data class Kelime(val basMs: Long, val sonMs: Long, val metin: String)

@Serializable
data class Altyazi(val id: String = yeniId(), val basMs: Long, val sonMs: Long, val metin: String = "", val kelimeler: List<Kelime> = emptyList())

@Serializable
data class AltyaziStil(val acik: Boolean = true, val stil: String = "kutu", val boyut: Float = 0.05f, val konum: String = "alt", val vurgu: Long = 0xFFFFD400)   // kutu | kenarli | sari | kisa (o anki kelime renkli)

@Serializable
data class Kapak(
    val kaynakUri: String = "", val kaynakMs: Long = 0, val kaynakFoto: Boolean = false,
    val yazi: String = "", val stil: String = "kalin", val boyut: Float = 0.11f, val konum: String = "alt",
    val vurgu: Long = 0xFFFFD400, val logo: Boolean = true, val canli: Boolean = true, val bant: String = "",
)

/** Takip eden yazı: zamanlar KAYNAK dosyanın zamanı (ms; kırpma değişse de kaymaz), konumlar kaynak karede 0…1. */
@Serializable
data class TakipNokta(val ms: Long, val x: Float, val y: Float)

@Serializable
data class Takip(
    val metin: String = "", val alt: String = "", val boyut: Float = 0.05f, val renk: Long = 0xFFFFFFFF, val kutu: Boolean = true,
    val basX: Float = 0.5f, val basY: Float = 0.5f, val basMs: Long = 0, val noktalar: List<TakipNokta> = emptyList(),
)

/** Nesne katmanı: fotoğraftan kesilmiş saydam PNG; konum kare içinde 0…1 (merkez), genişlik karenin oranı, zaman çizelgesinde aralık. */
@Serializable
data class Nesne(
    val id: String = yeniId(), val yol: String, val x: Float = 0.5f, val y: Float = 0.55f, val olcek: Float = 0.4f,
    val basMs: Long = 0, val sonMs: Long = 5000, val golge: Boolean = true,
    /** Videodan kesilen hareketli nesne: kare dizini (PNG), kare aralığı, kare sayısı. Boşsa tek fotoğraf. */
    val kareDizin: String = "", val kareMs: Long = 0, val kareSayi: Int = 0,
) {
    val hareketli: Boolean get() = kareDizin.isNotEmpty() && kareSayi > 0
}

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
    val gecis: Gecis = Gecis(),
    val giris: Giris = Giris(),
    val hatirlatici: Hatirlatici = Hatirlatici(),
    val kapanis: Kapanis = Kapanis(),
    val altyazilar: List<Altyazi> = emptyList(),
    val altyaziStil: AltyaziStil = AltyaziStil(),
    val kapak: Kapak = Kapak(),
    val nesneler: List<Nesne> = emptyList(),
) {
    /** Yalnız kliplerin süresi */
    val klipMs: Long get() = klipler.sumOf { it.sureMs }
    /** Kapanış kartı varsa onunla birlikte videonun tamamı */
    val kapanisMs: Long get() = if (kapanis.acik && klipler.isNotEmpty()) kapanis.sureMs else 0L
    val toplamMs: Long get() = klipMs + kapanisMs
}

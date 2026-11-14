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
    /** Yüz (fotoğrafta): merkez 0…1 ve yüz yüksekliği (fotoğraf yüksekliğinin oranı). yuzB = 0 → yüz yok/bakılmadı. */
    val yuzX: Float = -1f, val yuzY: Float = -1f, val yuzB: Float = 0f, val yuzBakildi: Boolean = false,
    /** Yüze odaklan ve yaklaş (dikey kişi fotoğrafında yavaş yakınlaşma yüze doğru). */
    val yuzOdak: Boolean = true,
    /** Kadraj kaydırma (Doldur'da taşan kısım içinde): -1…1, 0 = orta. x+ sağa, y+ aşağı. Önizlemede parmakla sürüklenir. */
    val kayX: Float = 0f, val kayY: Float = 0f,
    /** Ana nesne odağı (yüz yoksa kadraj buna göre ortalanır): 0…1, -1 = bakılmadı */
    val odakX: Float = -1f, val odakY: Float = -1f,
    /** Çekim tarihi (EXIF / video üstverisi); 0 = bilinmiyor. Giriş başlığındaki tarih bandı için. */
    val tarihMs: Long = 0,
    /** Kaynak videonun kare hızı (0 = bilinmiyor; dışa aktarmada okunur). */
    val kareHizi: Float = 0f,
    /** Bu klipten sonrakine geçiş (klipler arasındaki geçiş simgesiyle seçilir); null = projenin geçişi. */
    val gecis: String? = null,
) {
    val foto: Boolean get() = tur == "foto"
    /** Yüze yaklaşma bu klipte etkin mi: fotoğraf, yavaş yakınlaşma açık, dikey, yüz bulunmuş. */
    val yuzEtkin: Boolean get() = foto && kenBurns && yuzOdak && yuzB > 0f
    val sureMs: Long get() = if (foto) fotoSureMs else (sonMs - basMs).coerceAtLeast(100)
}

@Serializable
data class Muzik(val uri: String, val ad: String = "", val ses: Float = 0.35f, val dongu: Boolean = true, val sureMs: Long = 0)

@Serializable
data class SesKaydi(val id: String = yeniId(), val yol: String, val basMs: Long, val sureMs: Long, val ses: Float = 1f)

@Serializable
data class Logo(
    val yol: String, val konum: String = "sagUst", val boyut: Float = 0.16f, val opaklik: Float = 1f, val yuvarlak: Boolean = true,
    val kutupId: String = "",                 // kütüphanedeki logo (seçili gösterimi için)
    val x: Float = -1f, val y: Float = -1f,   // serbest konum (merkez, 0…1); -1: köşe (konum) kullanılır
)

/** Klip geçişi (tüm projede). Çakışmalı geçişler (çapraz/eriyerek/kaydırma) iki klibi aynı anda oynatmayı gerektirir; henüz yok. */
@Serializable
data class Gecis(val tur: String = "yok", val sureMs: Long = 800, val siyahtanAc: Boolean = false) {
    // yok | siyah | beyaz | yakin  (tek dizi)  ·  capraz | kaydir | yakincapraz  (üst üste binen: giden klibin devamı üstte solar)
    val cakisma: Boolean get() = tur == "erime" || tur == "capraz" || tur == "kaydir" || tur == "yakincapraz"
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
/** stil: "kart" = koyu hap: yuvarlak logo + başlık + alt satır + kırmızı ABONE OL (varsayılan) · "klasik" = önceki kırmızı/beyaz bant */
data class Hatirlatici(val acik: Boolean = false, val anOran: Float = 0.5f, val yazi: String = "Kanalıma abone ol", val kucuk: String = "Yeni videolar için zili aç",
                       val stil: String = "kart", val dugme: String = "ABONE OL")

@Serializable
data class Kapanis(val acik: Boolean = false, val stil: String = "cam", val sureMs: Long = 5000, val kanal: String = "Şefin Doğası", val yazi: String = "İzlediğin için teşekkürler!")

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
    /** kutu (klasik) | cagri (halka + eğik çizgi + yatay çizgi) | igne (dikey çizgi) | kose (dik açılı çizgi) */
    val stil: String = "cagri", val cizgiRenk: Long = 0xFFF2C94C,
)

/** Nesne katmanı: fotoğraftan kesilmiş saydam PNG; konum kare içinde 0…1 (merkez), genişlik karenin oranı, zaman çizelgesinde aralık. */
@Serializable
data class Nesne(
    val id: String = yeniId(), val yol: String, val x: Float = 0.5f, val y: Float = 0.55f, val olcek: Float = 0.4f,
    val basMs: Long = 0, val sonMs: Long = 5000, val golge: Boolean = true,
    /** Videodan kesilen hareketli nesne: kare dizini (PNG), kare aralığı, kare sayısı. Boşsa tek fotoğraf. */
    val kareDizin: String = "", val kareMs: Long = 0, val kareSayi: Int = 0,
    /** Kesimden önceki özgün görüntü (aynı kırpımla) — cam fırçası "geri getir/cam" için. */
    val orjYol: String = "",
) {
    val hareketli: Boolean get() = kareDizin.isNotEmpty() && kareSayi > 0
}

/** Çok katmanlı düzenleme: serbest katman (metin | gorsel | cikartma | ses). Listedeki sıra = alttan üste.
 *  Zamanlar zaman çizelgesi ms'si. Konum kare içinde 0…1 (merkez). */
@Serializable
data class KatmanOge(
    val id: String = yeniId(),
    val tur: String = "metin",
    val ad: String = "",
    val basMs: Long = 0, val sonMs: Long = 4000,
    val yol: String = "",                 // gorsel: uygulama içi dosya · ses: içerik adresi (uri)
    val metin: String = "",               // metin ya da çıkartma (emoji)
    val renk: Long = 0xFFFFFFFF, val kutu: Boolean = false,
    val x: Float = 0.5f, val y: Float = 0.5f,
    val olcek: Float = 0.35f,             // gorsel/çıkartma: karenin genişliğine oran · metin: yazı boyu (kısa kenara oran)
    val aci: Float = 0f,                  // saat yönünde derece
    val opaklik: Float = 1f,
    val gizli: Boolean = false, val kilitli: Boolean = false,
    val anim: String = "solma",           // yok | solma | kay | buyu | zipla  (giriş ve çıkış)
    val filtre: String = "yok",           // yok | sb | sepya | canli | soguk | sicak
    val kirpL: Float = 0f, val kirpT: Float = 0f, val kirpR: Float = 0f, val kirpB: Float = 0f,
    val ses: Float = 1f, val hiz: Float = 1f, val kaynakBasMs: Long = 0, val kaynakSureMs: Long = 0,
) {
    val gorsel: Boolean get() = tur != "ses"
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
    /** Prompter (kayan yazı) metni ve ayarları */
    val okumaMetni: String = "", val okumaHiz: Float = 40f, val okumaBoy: Float = 34f,
    /** Arşivlenen proje ana listede görünmez (Arşiv bölümünde durur). */
    val arsiv: Boolean = false,
    /** Serbest katmanlar (çok katmanlı düzenleme) — sıra alttan üste. */
    val katmanlar: List<KatmanOge> = emptyList(),
) {
    /** Yalnız kliplerin süresi */
    val klipMs: Long get() = klipler.indices.sumOf { zKlip(it).sureMs }

    /** Üst üste binen (yumuşak) geçişte klip i ile sonraki arasındaki örtüşme (ms). Videoyu bu kadar kısaltır. */
    /** Klip i ile sonraki (ya da kapanış) arasındaki geçiş: klibe seçilmişse o, yoksa projenin geçişi. */
    fun gecisAt(i: Int): Gecis = klipler.getOrNull(i)?.gecis?.let { gecis.copy(tur = it) } ?: gecis

    fun ortusme(i: Int): Long {
        val g = gecisAt(i)
        if (!g.cakisma || i !in klipler.indices) return 0
        val sonraki = if (i + 1 < klipler.size) klipler[i + 1].sureMs else if (kapanis.acik) kapanis.sureMs else return 0
        val t = minOf(g.sureMs, klipler[i].sureMs / 3, sonraki / 3)
        return if (t >= 150) t else 0
    }

    /** Zaman çizelgesindeki hâli: sonu örtüşme kadar kısa (son kısmı üst katmanda gelen klibin üstünde solar). */
    fun zKlip(i: Int): Klip {
        val k = klipler[i]; val t = ortusme(i)
        return if (t == 0L) k else if (k.foto) k.copy(fotoSureMs = k.fotoSureMs - t) else k.copy(sonMs = k.sonMs - t)
    }
    /** Kapanış kartı varsa onunla birlikte videonun tamamı */
    val kapanisMs: Long get() = if (kapanis.acik && klipler.isNotEmpty()) kapanis.sureMs else 0L
    val toplamMs: Long get() = klipMs + kapanisMs
}

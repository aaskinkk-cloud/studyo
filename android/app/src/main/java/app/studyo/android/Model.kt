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
    /** 3.49: renk ve ışık ayarı (null = yok) */
    val renk: RenkAyar? = null,
    /** 3.59: filtre (LUT) ve efekt (GL), klip bazında. "yok" = kapalı; güç 0…1. */
    val filtre: String = "yok", val filtreGuc: Float = 0.8f,
    val efekt: String = "yok", val efektGuc: Float = 0.7f,
    /** 3.61: arka plan bulanıklığı (ekrandan nesne kesilince kendiliğinden; 0 = yok, 0…1). */
    val arkaBulanik: Float = 0f,
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
    /** 3.35 hareket türü (kenBurns açıkken): otomatik | yakin | uzak | sag | sol | yukari | asagi. kenBurns = false → sabit. */
    val hareket: String = "otomatik",
    /** 3.35 elle seçilen yaklaşma noktası (kaynak karede 0…1); -1 = otomatik (yüz → ana nesne → orta). */
    val odakSecX: Float = -1f, val odakSecY: Float = -1f,
    /** 3.70 klip hızı: klip bir kez hızlandırılmış/yavaşlatılmış dosyaya işlenir (uri o dosya). asil* = özgün dosya ve aralığı (1× = geri döner). */
    val hiz: Float = 1f, val asilUri: String = "", val asilBasMs: Long = 0, val asilSonMs: Long = 0, val asilKaynakSureMs: Long = 0,
    /** 3.74: yüzlere (ve seçilen noktalara) sırayla yaklaşma — aynı fotoğrafta numaralı noktalar arasında yumuşak kamera turu. null = yok. */
    val yuzTur: YuzTuru? = null,
    /** 3.83: yüz gizleme (bulanıklaştırma) — bulunan/elle eklenen yüzler, takip izleri ve seçilenler. null = yok. */
    val yuzGizle: YuzGizle? = null,
) {
    /** 3.83: bu klipte bulanıklaştırılacak yüz var mı. */
    val gizleVar: Boolean get() = yuzGizle?.let { g -> g.guc > 0.01f && g.yuzler.any { it.id in g.secili && it.izler.isNotEmpty() } } == true
    val foto: Boolean get() = tur == "foto"
    /** Yüze yaklaşma bu klipte etkin mi: fotoğraf, yavaş yakınlaşma açık, dikey, yüz bulunmuş. */
    val yuzEtkin: Boolean get() = foto && kenBurns && yuzOdak && yuzB > 0f
    val sureMs: Long get() = if (foto) fotoSureMs else (sonMs - basMs).coerceAtLeast(100)
    /** 3.74: yüz turu bu klipte etkin mi (fotoğraf, hareket açık, en az bir nokta). */
    val turEtkin: Boolean get() = foto && kenBurns && (yuzTur?.noktalar?.isNotEmpty() == true)
}

/** 3.74: turdaki bir durak. x, y: fotoğrafta 0…1 (durağın merkezi); z: yakınlık (1 = kadrajı dolduran tam görünüm, 2 = iki kat yakın);
 *  yb: yüz yüksekliği (fotoğraf yüksekliğinin oranı; 0 = elle seçilen nokta, yüz değil). */
@Serializable
data class OdakNoktasi(val x: Float, val y: Float, val z: Float = 1.8f, val yb: Float = 0f,
    /** 3.76: kişinin adı (isim kutusunda görünür; boş = kutu yok) */
    val ad: String = "",
    /** 3.87: kişinin silüet hattı (fotoğrafa göre 0…1, x,y sırayla; soldan sağa). Boş = bulunmadı (ince çizgi kullanılır). */
    val siluet: List<Float> = emptyList())

/** 3.74: yüz turu ayarları (web Kareden Videoya "Odak" sisteminin gelişmiş hâli; karartma/bulanıklık yok).
 *  bekleSn: her durakta kalma · gecis: yavas | orta | hizli (duraklar arası süre) · yaklas: durakta kalırken ek yavaş yaklaşma (0 = yok)
 *  bastaTumu / sondaTumu: tur fotoğrafın tamamıyla başlar / biter · kavis: geçişte hafif geri çekilip yeniden yaklaşma (sinematik)
 *  bulunan: fotoğrafta bulunan yüzler (önizlemede halka olarak gösterilir). */
@Serializable
data class YuzTuru(
    val noktalar: List<OdakNoktasi> = emptyList(),
    val bekleSn: Float = 3f, val gecis: String = "yavas", val yaklas: Float = 0.12f,
    val bastaTumu: Boolean = true, val sondaTumu: Boolean = true, val kavis: Boolean = true,
    val bulunan: List<OdakNoktasi> = emptyList(), val arandi: Boolean = false,
    /** 3.76: öne çıkarma çizgisi (odaklanan kişinin etrafında ince çizgi dolaşıp kaybolur) · isim kutusu · isimDurak: kutu durak boyunca (false = 2 sn) */
    val vurgu: Boolean = true, val isim: Boolean = true, val isimDurak: Boolean = false,
    /** 3.87: öne çıkarma biçimi — "siluet": ışık kişinin dış hattını iki alt uçtan başın tepesine doğru izler (Samsung Galeri efekti) · "cizgi": baş çevresinde ince çizgi (3.76) */
    val vurguTur: String = "siluet", val siluetArandi: Boolean = false,
)

@Serializable
data class Muzik(val uri: String, val ad: String = "", val ses: Float = 0.5f, val dongu: Boolean = true, val sureMs: Long = 0,
    /** 3.46: videonun kendi sesi olan kliplerde müzik otomatik kısılır (önizleme ve dışa aktarma). */
    val otoKis: Boolean = true,
    /** 3.70: yumuşak giriş/çıkış (ms; 0 = yok) — müzik başta yavaşça açılır, videonun sonunda yavaşça kısılır. */
    val girisMs: Long = 0, val cikisMs: Long = 0)

/** 3.70: müziğin yumuşak giriş/çıkış çarpanı (t: videodaki an, toplam: video süresi; ms). */
fun muzikSolma(m: Muzik, t: Long, toplam: Long): Float {
    var f = 1f
    if (m.girisMs > 0) f = minOf(f, (t.toFloat() / m.girisMs).coerceIn(0f, 1f))
    if (m.cikisMs > 0 && toplam > 0) f = minOf(f, ((toplam - t).toFloat() / m.cikisMs).coerceIn(0f, 1f))
    return f
}

/** Otomatik kısmada müziğin inen oranı. */
const val MUZIK_KIS = 0.35f

@Serializable
data class SesKaydi(val id: String = yeniId(), val yol: String, val basMs: Long, val sureMs: Long, val ses: Float = 1f,
    /** 3.90: ses efekti (dogal | net | temiz | radyo | kalin) ve özgünden işlenmiş dosya. Özgün kayıt (yol) hiç değişmez. */
    val efekt: String = "dogal", val islenmis: String = "") {
    /** Çalınacak dosya: efekt seçili ve işlenmiş dosya duruyorsa o, yoksa özgün kayıt. */
    val calYol: String get() = if (efekt != "dogal" && islenmis.isNotEmpty() && java.io.File(islenmis).exists()) islenmis else yol
}

@Serializable
data class Logo(
    val yol: String, val konum: String = "solUst", val boyut: Float = 0.14f, val opaklik: Float = 0.7f, val yuvarlak: Boolean = true,   // 3.53: varsayılan sol üst · %14 · %70
    val kutupId: String = "",                 // kütüphanedeki logo (seçili gösterimi için)
    val x: Float = -1f, val y: Float = -1f,   // serbest konum (merkez, 0…1); -1: köşe (konum) kullanılır
)

/** Klip geçişi (tüm projede). Çakışmalı geçişler (çapraz/eriyerek/kaydırma) iki klibi aynı anda oynatmayı gerektirir; henüz yok. */
@Serializable
data class Gecis(val tur: String = "yok", val sureMs: Long = 800, val siyahtanAc: Boolean = false,
                 /** 3.73: logo geçişi — baştaki, ortadaki ve sona yakın üç klip arasında geçiş yerine logo arka plandan büyüyerek gelir */
                 val logo: Boolean = false,
                 /** 3.82: geçiş logosu köşe logosundan bağımsız (kütüphaneden seçilir; köşe logosu kaldırılsa da kalır). Şekil: yuvarlak · kare · ozgun */
                 val logoYol: String = "", val logoSekil: String = "yuvarlak",
                 /** 3.88: logo geçişinin planı (Yayın › Logo) — "bos": baş · orta · son · "orta": yalnız ortada · "aralik": her logoAralikSn saniyede
                  *  · "adet": videoya eşit aralıkla logoAdet kez. logoYakala: hedef ana yakın (±3 sn) klip arası varsa logo oraya oturur (kesme logonun altında kalır). */
                 val logoPlan: String = "bos", val logoAralikSn: Int = 30, val logoAdet: Int = 3, val logoYakala: Boolean = true) {
    // yok | siyah | beyaz | yakin  (tek dizi)  ·  capraz | kaydir | yakincapraz  (üst üste binen: giden klibin devamı üstte solar)
    val cakisma: Boolean get() = tur == "erime" || tur == "capraz" || tur == "kaydir" || tur == "yakincapraz" || tur == "hafif" || tur == "akis"
}

@Serializable
data class Giris(
    val acik: Boolean = false, val stil: String = "sinematik", val baslik: String = "", val alt: String = "", val sureMs: Long = 3500,
    val bant: String = "",               // reels stillerinde vurgu bantları (| ile ayrı bant)
    val hareket: String = "kay",         // reels giriş hareketi: trend | dus | kay | yuksel | buyu
    val konum: String = "orta",          // reels yazılarının yeri: ust | orta | alt
    val efektSes: Boolean = true, val sesSeviye: Float = 0.6f,
    val boncuk: Boolean = false,         // ışıklı boncuklar (girişte süzülen ışık noktaları)
    val olcek: Float = 1f,               // 3.58: yazı boyutu çarpanı (0,6…1,6)
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
data class AltyaziStil(val acik: Boolean = true, val stil: String = "bant", val boyut: Float = 0.05f, val konum: String = "alt", val vurgu: Long = 0xFFFFD400)   // kutu | kenarli | sari | kisa (o anki kelime renkli)

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
    /** Bitiş anı (kaynak ms). -1 = otomatik: izlemenin bittiği yer (nesne kaybolunca) ya da klip sonu. */
    val sonMs: Long = -1,
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
    /** 3.76: bağlı olduğu klip (boş = bağlı değil). Araya klip eklenince / sıra değişince katman klibiyle birlikte kayar. */
    val klipId: String = "",
    /** 3.77: harf biçimi (yazılarda, çizimde uygulanır — klavye ve sesle yazma dahil): "" olduğu gibi · cumle · bas (Baş Harfler) · buyuk · kucuk */
    val harf: String = "",
    val anim: String = "solma",           // yok | solma | kay | buyu | zipla  (giriş ve çıkış)
    val filtre: String = "yok",           // yok | sb | sepya | canli | soguk | sicak
    val kirpL: Float = 0f, val kirpT: Float = 0f, val kirpR: Float = 0f, val kirpB: Float = 0f,
    val ses: Float = 1f, val hiz: Float = 1f, val kaynakBasMs: Long = 0, val kaynakSureMs: Long = 0,
    /** metin stili (3.42): duz | baslik | konum | altbant | etiket. 2. satır (\n sonrası) stilli yazılarda küçük alt satırdır. */
    val stil: String = "duz",
    /** 3.46: katmanın altyazısı — katmanın hemen altında çizilir, katmanla birlikte taşınır/büyür (altyapı). */
    val altyazi: String = "",
    /** 3.47: yalnız çizelgede satırı gizle (videoda görünür/çalar). Gerçek gizleme `gizli`. */
    val satirGizli: Boolean = false,
    /** 3.52: ses katmanı (müzik olarak eklenen) — videoda ses/konuşma varken otomatik kısılır */
    val otoKis: Boolean = true,
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
    /** Boş YouTube projesi (3.30): varsayılan ayarlar (giriş, abone, kapanış, kadraj, hareket) kendiliğinden uygulanmaz. */
    val bos: Boolean = false,
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
    fun gecisAt(i: Int): Gecis = if (i in logoGecisAralari()) gecis.copy(tur = "yok")   // 3.73: logo geçişinde kesme (logo örter)
        else klipler.getOrNull(i)?.gecis?.let { gecis.copy(tur = it) } ?: gecis

    /** 3.82: geçiş logosunun dosyası: kendi seçilen logo, yoksa köşe logosu. */
    val gecisLogoYol: String get() = gecis.logoYol.ifEmpty { logo?.yol ?: "" }

    /** 3.82: logo geçişinin yapıldığı klip araları (i = klip i ile i+1 arası): 15. saniyeye, ortaya ve bitime 15 sn kalaya EN YAKIN
     *  klip arası (kesme logonun tam ekranda olduğu ana denk gelir). Ham klip süreleriyle hesaplanır (örtüşme hesabıyla döngü olmaz).
     *  Kısa videoda yerler çakışırsa daha az logo olur. Standart geçişler / Tümüne uygula bunları değiştirmez. */
    fun logoGecisAralari(): Set<Int> = logoPlani().mapNotNull { it.second }.toSet()

    /** 3.88: logo geçişi planı (ham klip süreleriyle; örtüşme hesabına bağlı değil → döngü yok). Her öğe: (hedef an ms, oturduğu klip arası ya da null).
     *  Matematik: T = video süresi. Logo girişten (giriş açıksa giriş süresi + 0,5 sn, en az 4 sn) sonra ve bitime 4 sn kalana kadar konur.
     *   - bos (baş·orta·son): 15 sn, T/2, T−15 sn (T < 25 sn ise yalnız T/2)
     *   - orta: T/2
     *   - aralik A sn: A, 2A, 3A, … (T−4 sn'ye kadar)
     *   - adet N: T·k/(N+1), k = 1…N (eşit aralık)
     *  Yakala açıksa her hedef ±3 sn içindeki en yakın klip arasına oturur (o arada kesme yok, logo tam ekrandayken klip değişir); yoksa klibin
     *  ortasında üstte belirir. Logolar arası en az 6 sn (2,8 sn'lik logo üst üste binmez). */
    fun logoPlani(): List<Pair<Long, Int?>> {
        if (!gecis.logo || gecisLogoYol.isEmpty() || klipler.isEmpty()) return emptyList()
        val n = klipler.size - 1
        val sinir = LongArray(n.coerceAtLeast(0)); var t = 0L
        for (i in 0 until n) { t += klipler[i].sureMs; sinir[i] = t }
        val top = klipler.sumOf { it.sureMs }
        val ilk = maxOf(4_000L, if (giris.acik) giris.sureMs + 500 else 0L)
        val son = top - 4_000L
        if (son <= ilk) return emptyList()
        val ham: List<Long> = when (gecis.logoPlan) {
            "orta" -> listOf(top / 2)
            "aralik" -> { val a = gecis.logoAralikSn.coerceIn(5, 600) * 1000L; generateSequence(a) { it + a }.takeWhile { it <= son }.toList() }
            "adet" -> { val k = gecis.logoAdet.coerceIn(1, 12); (1..k).map { top * it / (k + 1) } }
            else -> if (top < 25_000) listOf(top / 2) else listOf(15_000L, top / 2, top - 15_000L)
        }
        val sonuc = ArrayList<Pair<Long, Int?>>()
        val kullanilan = HashSet<Int>()
        for (h0 in ham) {
            val h = h0.coerceIn(ilk, son)
            val yak = if (gecis.logoYakala) (0 until n).filter { it !in kullanilan && kotlin.math.abs(sinir[it] - h) <= 3_000 }.minByOrNull { kotlin.math.abs(sinir[it] - h) } else null
            val an = yak?.let { sinir[it] } ?: h
            if (sonuc.any { kotlin.math.abs(it.first - an) < 6_000 }) continue
            if (yak != null) kullanilan += yak
            sonuc += an to yak
        }
        return sonuc.sortedBy { it.first }
    }

    /** 3.73: logo geçişlerinin anları (zaman çizelgesi ms; logonun tam ekranda olduğu an). Klip arasına oturanlar gerçek kesme anında. */
    fun logoGecisAnlari(): List<Long> {
        val plan = logoPlani(); if (plan.isEmpty()) return emptyList()
        val kesme = HashMap<Int, Long>(); var t = 0L
        for (i in klipler.indices) { t += zKlip(i).sureMs; kesme[i] = t }
        return plan.map { (an, ara) -> if (ara != null) kesme[ara] ?: an else an }
    }

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


/** 3.83: yüz gizlemede bir yüzün bir andaki yeri. ms: kaynak dosyada zaman (fotoğrafta 0); x, y: yüz merkezi (0…1);
 *  w, h: yüz kutusunun eni/boyu (karenin eni/boyu oranı); aci: başın yana eğimi (derece). */
@Serializable
data class YuzIz(val ms: Long, val x: Float, val y: Float, val w: Float, val h: Float, val aci: Float = 0f)

/** 3.83: takip edilen bir yüz (videoda kare kare iz; fotoğrafta tek iz). olcek: bulanık alanın büyüklüğü çarpanı. elle: kullanıcı ekledi. */
@Serializable
data class GizliYuz(val id: Int, val izler: List<YuzIz>, val olcek: Float = 1f, val elle: Boolean = false, val resim: String = "")

/** 3.83: Düzenle › Klip › Yüz gizle. secili: bulanıklaşacak yüzler (yalnız seçilenler). guc: bulanıklık gücü 0…1. */
@Serializable
data class YuzGizle(val yuzler: List<GizliYuz> = emptyList(), val secili: Set<Int> = emptySet(), val guc: Float = 0.7f, val arandi: Boolean = false)

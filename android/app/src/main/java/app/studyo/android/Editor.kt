@file:OptIn(ExperimentalMaterial3Api::class)

package app.studyo.android

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class Bolum(val anahtar: String, val ad: String, val simge: String, val haplar: List<Pair<String, String>>)

private val ALT_RENK = longArrayOf(0xFFD9CF5A, 0xFF86C06E, 0xFF3FBFAE, 0xFFE8A06A, 0xFFDB7F93)

private val ESKI_SIMGE = mapOf("duzenle" to "✂", "gorunum" to "◐", "ses" to "♪", "metin" to "T", "yayin" to "⇪")

/** Önceki (3.2) yazılı hap. */
@Composable
private fun HapEski(metin: String, secili: Boolean, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(17.dp)
    Box(
        Modifier.height(34.dp).clip(sekil).background(if (secili) Renk.turuncu else Color.Transparent)
            .border(1.5.dp, if (secili) Renk.turuncu else Renk.cizgi, sekil).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(metin, color = if (secili) Renk.turuncuM else Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

/** Üst çubuk için ince yazılı düğme. */
@Composable
private fun InceDugme(metin: String, renk: Color, etkin: Boolean = true, mod: Modifier = Modifier, onClick: () -> Unit) {
    // 4.46: daha dar; yazı hiç kırılmaz (gece temasında "Dışa Aktar" yalnız "Dışa" görünüyordu)
    Box(mod.height(32.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = etkin, onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Text(metin, color = if (etkin) renk else renk.copy(alpha = 0.4f), fontSize = 12.5.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

private val HAP_SIMGE = mapOf(
    "klip" to "klip", "ekle" to "ekle", "nesne" to "katman", "bicim" to "bicim",
    "sablon" to "sihir", "gecis" to "gecis", "kadraj" to "kadraj", "hareket" to "hareket", "renk" to "gorunum",
    "klipses" to "ses", "muzik" to "nota", "seslendirme" to "mikrofon",
    "yazi" to "metin", "takip" to "takip", "altyazi" to "altyazi",
    "logo" to "logo", "kapak" to "kapak", "disa" to "disa", "katmanlar" to "katman", "filtre" to "palet", "efekt" to "yildiz", "ayarlar" to "kaydet",
    "paket" to "izgara", "hazir" to "kaydet", "yuz" to "yuz", "hazirlar" to "izgara",   // 3.70 · 4.41 · 4.46
    "kekle" to "ekle", "kses" to "ses", "foto" to "kapak", "video" to "klip", "kesilen" to "katman",   // 4.13: Katman alt menüleri
)

// 4.46: Paket + Hazır ana menüde ayrı "Hazır" bölümü (Görünüm'ün yanında); Ayarlar'dan önceki yerleşime dönülebilir
private val BOLUMLER: List<Bolum> get() = listOfNotNull(
    // 3.30: Ekle ve Biçim, Klip bölümüne taşındı (şeritteki + ile süre satırındaki + ve Biçim satırı)
    Bolum("duzenle", "Düzenle", "makas", listOf("klip" to "Klip")),
    // 4.29: Nesne ayrı ana menü (Düzenle ve Katman'daki kopyaları kaldırıldı — tek yer)
    Bolum("nesne", "Nesne", "kisi", listOf("foto" to "Fotoğraftan", "video" to "Videodan", "kesilen" to "Kesilenler", "yuz" to "Yüzler")),   // 4.41: Yüzler
    // 3.70: Görünüm › Şablon (giriş·abone·kapanış) · Paket (paketler·biçim) · Hazır (varsayılan·kayıtlı) · Hareket (yakınlaştırma·kaydırma·odak)
    if (Duzen.hazirEski) Bolum("gorunum", "Görünüm", "gorunum", listOf("sablon" to "Şablon", "paket" to "Paket", "hazir" to "Hazır", "hareket" to "Hareket"))
    else Bolum("gorunum", "Görünüm", "gorunum", listOf("sablon" to "Şablon", "hareket" to "Hareket")),
    if (Duzen.hazirEski) null else Bolum("hazirb", "Hazır", "izgara", listOf("hazirlar" to "Hazır")),   // 3.58: Kadraj → klip resmindeki simge · 3.59: Geçiş → Efekt
    Bolum("katman", "Katman", "katman", listOf("katmanlar" to "Katmanlar", "kekle" to "Ekle", "kses" to "Ses · görünür")),   // 4.13   // 3.75: Renk'in solunda (Ahmet'in isteği)
    // 3.53: Renk ayrı ana bölüm (ışık, kontrast, beyazlar, gölge, keskinlik… her biri kendi sayfasında)
    Bolum("renk", "Renk", "palet", listOf("renk" to "Renk")),
    // 3.59: Efekt bölümü — Filtre · Efekt · Geçiş (önizlemeli küçük resimler)
    Bolum("efekt", "Efekt", "sihir", listOf("filtre" to "Filtre", "efekt" to "Efekt", "gecis" to "Geçiş")),
    Bolum("ses", "Ses", "nota", listOf("klipses" to "Klip sesi", "muzik" to "Müzik", "seslendirme" to "Seslendirme")),
    Bolum("metin", "Metin", "metin", listOf("yazi" to "Yazı", "takip" to "Takip", "altyazi" to "Altyazı")),
    Bolum("yayin", "Yayın", "yildiz", listOf("logo" to "Logo", "kapak" to "Kapak", "ayarlar" to "Ayarlar", "disa" to "Dışa aktar")),   // 3.61: Ayarlar
)

@Composable
fun EditorEkrani(id: String, bekleyen: List<Uri>, bekleyenAlindi: () -> Unit, kapat: () -> Unit, ac: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val vm = remember(id) { EditorDurum(ctx.applicationContext, id) }
    DisposableEffect(vm) { onDispose { vm.kapat() } }
    LaunchedEffect(bekleyen) { if (bekleyen.isNotEmpty()) { vm.ekle(bekleyen); bekleyenAlindi() } }

    val p by vm.proje.collectAsState()
    val sec by vm.secili.collectAsState()
    // 4.05: "Yüzleri otomatik bul" açıksa yeni eklenen fotoğraflarda da tur kendiliğinden kurulur
    val fotoSayisi = p.klipler.count { it.foto }
    LaunchedEffect(fotoSayisi, p.yuzOto) { if (p.yuzOto) vm.turOtomatikYeni() }
    val mesaj by vm.mesaj.collectAsState()
    val uyari by vm.uyari.collectAsState()
    val mesgul by vm.mesgul.collectAsState()
    var bolum by rememberSaveable { mutableStateOf("duzenle") }
    var panelAcik by rememberSaveable { mutableStateOf(true) }
    // 3.44: önizlemede bir katmana dokununca Katman bölümü açılır
    val katmanIstek by vm.katmanSayfasi.collectAsState()
    LaunchedEffect(katmanIstek) { if (katmanIstek > 0) { bolum = "katman"; panelAcik = true } }
    val haplar = remember { mutableStateMapOf("duzenle" to "klip", "gorunum" to "sablon", "hazirb" to "hazirlar", "renk" to "renk", "efekt" to "filtre", "ses" to "muzik", "metin" to "yazi", "katman" to "katmanlar", "yayin" to "logo", "nesne" to "foto") }
    var disaAcik by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(id) { if (Depo.acilincaDisa == id) { Depo.acilincaDisa = null; disaAcik = true } }
    val albumIstek by AlbumIndir.istek.collectAsState()   // 3.81: Google Fotoğraflar'dan paylaşılan albüm bağlantısı
    LaunchedEffect(albumIstek) { albumIstek?.let { AlbumIndir.istek.value = null; vm.albumAl(it) } }   // 3.78: Ayrı video'dan gelen taslak
    var kapakAcik by rememberSaveable { mutableStateOf(false) }   // 3.63: tam ekran Kapak düzenleyici
    var aramaAcik by remember { mutableStateOf(false) }           // 3.70: ayar arama
    var tamEkranV by rememberSaveable { mutableStateOf(false) }   // 3.71: video tam ekran
    val ctxD = LocalContext.current
    var altMod by rememberSaveable { mutableStateOf(true) }
    // 4.13: Katman'da çalışırken başka bölümün sayfası (ör. Metin › Yazı) Katman'ın içinde açılır; alttaki Katman menüsü yerinde kalır
    var misafir by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    LaunchedEffect(bolum) { if (bolum != "katman") misafir = null }   // 3.61: alt menüler ana menünün yerinde · 3.87: açılışta Düzenle'nin alt menüleri hemen gelir
    // 3.53: ayar değişince o menünün başlığının yanında ✓ (bu düzenleme oturumunda değişen menüler)
    val degisen = remember(id) { mutableStateMapOf<String, Boolean>() }
    var oncekiP by remember(id) { mutableStateOf<Proje?>(null) }
    LaunchedEffect(p) {
        val o = oncekiP; oncekiP = p
        if (o != null && panelAcik && o.copy(guncelleme = 0L) != p.copy(guncelleme = 0L)) {
            degisen[bolum] = true; haplar[bolum]?.let { degisen["$bolum/$it"] = true }
        }
    }
    vm.projeAc = { pid -> ac(pid) }   // 3.78
    vm.bosProjeAc = { bicim ->
        // 4.34: boş şablon gerçekten boş — giriş, abone, kapanış, logo, geçiş, müzik yok (varsayılanlar da uygulanmaz: bos = true)
        val y0 = Proje(ad = (if (bicim == "9:16") "Boş Reels " else "Boş YouTube ") + java.text.SimpleDateFormat("d MMM HH:mm", TR).format(java.util.Date()), bicim = bicim, bos = true)
        val y = y0.copy(giris = y0.giris.copy(acik = false), hatirlatici = y0.hatirlatici.copy(acik = false), kapanis = y0.kapanis.copy(acik = false),
            logo = null, muzik = null, gecis = y0.gecis.copy(tur = "yok", logo = false, siyahtanAc = false))
        Depo.kaydet(y); ac(y.id)
    }

    val ekleSecici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.ekle(it) }
    val ekle = { ekleSecici.launch(arrayOf("video/*", "image/*")) }

    val cik = { vm.kaydetSimdi(false); kapat() }
    if (disaAcik) {
        DisaEkrani(vm) { disaAcik = false }
        return
    }
    if (kapakAcik) {
        KapakEditoru(vm) { kapakAcik = false }
        return
    }
    if (tamEkranV) {
        TamEkranVideo(vm, p) { tamEkranV = false }
        return
    }
    BackHandler { if (altMod && panelAcik) altMod = false else if (panelAcik) panelAcik = false else cik() }

    // 3.70: tek gezinme işlevi — bölüm › alt menü › alt sayfa (Renk'te alt menü = grup, alt sayfa = ayar)
    val git: (String, String, String?) -> Unit = { b, h, a ->
        bolum = b
        if (b == "renk") vm.renkSekme.value = a ?: (RENK_GRUPLARI.firstOrNull { it.anahtar == h }?.let { g -> AltHafiza.ham("renk_" + g.anahtar)?.takeIf { it in g.sayfalar } ?: g.sayfalar.first() } ?: "hazir")
        else { haplar[b] = h; if (a != null) AltHafiza.sec(h, a) }
        panelAcik = true; altMod = true
    }
    // 4.08: panel içi bağlantılar (ör. Varsayılan ayarlar › "Ayarla ›") aynı gezinmeyi kullanır
    val gitIstekS by vm.gitIstek.collectAsState()
    LaunchedEffect(gitIstekS) { gitIstekS?.let { (b, h, a) -> vm.gitIstek.value = null; git(b, h, a) } }
    if (aramaAcik) {
        val liste = remember {
            val l = ArrayList<AramaOge>()
            val ek = mapOf("hiz" to "yavaş çekim hızlandır", "giris" to "başlık açılış büyük nokta logo büyüyerek", "girisb" to "giriş başlık stil reels", "acilis" to "açılış efekt büyük nokta logo büyüyerek", "ozet" to "özet bu videoda izleyecekleriniz içerik madde liste", "abone" to "hatırlatıcı zil", "ayar" to "yumuşak giriş çıkış fade solma",
                "kayitli" to "sakla kaydet şablonum", "paket" to "biçim 16:9 9:16 reels shorts kare youtube sinema", "kesilen" to "nesne katman", "oto" to "otomatik altyazı konuşma",
                "takip" to "takip yazısı takip eden yazı nesne izle", "logo" to "büyük logo geçişi filigran", "prompter" to "kayan yazı okuma kamera çekim", "okuyucu" to "sesli okuyucu metin oku tts dfki seslendir anlatıcı",
                "kayitlar" to "ses efekti hız kırp sessizlik net temiz radyo kalın", "katmanlar" to "çizelge zaman çizelgesi katman", "kapak" to "küçük resim youtube kapak",
                "yuzler" to "yüz yaklaş odak nokta tur numara durak ışık silüet otomatik yüz bul tümüne grupla seçerek yakınlaşma yok kapat", "gizle" to "yüz gizle bulanık flu sansür kişi mahremiyet", "ayri" to "reels dikey ayrı video yüz kişi dışa aktar", "ekle" to "kanca dikkat hazır yazı")
            BOLUMLER.forEach { bb ->
                if (bb.anahtar == "renk") {
                    RENK_GRUPLARI.forEach { g ->
                        l += AramaOge("${bb.ad} › ${g.ad}", "renk", g.anahtar, g.sayfalar.first())
                        if (g.sayfalar.size > 1) g.sayfalar.forEach { sy -> RENK_SEKMELERI.firstOrNull { it.anahtar == sy }?.let { l += AramaOge("${bb.ad} › ${g.ad} › ${it.ad}", "renk", g.anahtar, sy) } }
                    }
                } else bb.haplar.forEach { (h, ad) ->
                    if (h == "disa") return@forEach
                    l += AramaOge("${bb.ad} › $ad", bb.anahtar, h, null, ek[h] ?: "")
                    ALT_SAYFALAR[h]?.forEach { a -> l += AramaOge("${bb.ad} › $ad › ${a.ad}", bb.anahtar, h, a.anahtar, ek[a.anahtar] ?: "") }
                }
            }
            // 4.01: sayfaların içindeki seçenekler de aranır (ör. "doğal" → Renk › Hazır › Doğal; "vinyet" → Efekt › Efekt › Vinyet)
            RENK_HAZIR.forEach { (ad, _) -> l += AramaOge("Renk › Hazır › $ad", "renk", "hazir", "hazir", "renk hazır ayar") }
            RenkLut.FILTRELER.forEach { (_, ad) -> l += AramaOge("Efekt › Filtre › $ad", "efekt", "filtre", null, "filtre") }
            Efektler.LISTE.forEach { (_, ad) -> l += AramaOge("Efekt › Efekt › $ad", "efekt", "efekt", null, "efekt") }
            (GECIS_TEMEL + GECIS_AKICI).forEach { (t, _) -> if (t.first != "yok") l += AramaOge("Efekt › Geçiş › ${t.second}", "efekt", "gecis", null, "geçiş") }
            GECIS_POPULER.forEach { (_, ad) -> l += AramaOge("Efekt › Geçiş › $ad", "efekt", "gecis", null, "geçiş") }
            Reels.STILLER.forEach { (_, ad) -> l += if (Duzen.hazirEski) AramaOge("Görünüm › Şablon › Giriş › $ad", "gorunum", "sablon", "giris", "giriş başlığı stil")
                else AramaOge("Hazır › Giriş başlığı › $ad", "hazirb", "hazirlar", "girisb", "giriş başlığı stil") }   // 4.48
            SesEfekt.HAZIR.forEach { (_, ad) -> l += AramaOge("Ses › Seslendirme › Kayıtlar › $ad", "ses", "seslendirme", "kayitlar", "ses efekti seslendirme") }
            l += AramaOge("Yayın › Ayarlar › Tema rengi", "yayin", "ayarlar", null, "tema renk vurgu turkuaz")
            l
        }
        AramaPenceresi(liste, { o -> aramaAcik = false; git(o.bolum, o.hap, o.alt) }) { aramaAcik = false }
    }

    LaunchedEffect(mesaj) { if (mesaj != null) { delay(2200); vm.mesaj.value = null } }
    val renkSekmeS by vm.renkSekme.collectAsState()

    BoxWithConstraints(Modifier.fillMaxSize().background(Renk.zemin)) {
        // Sade düzen (InShot gibi): önizleme ekranın %42'si ve hep görünür; panel açılınca klip şeridinin yerini alır.
        val ekranYuk = maxHeight
        val ekranGen = maxWidth
        val onizlemeYuk = (ekranYuk * Duzen.onizlemeOran).coerceAtLeast(72.dp)
        Column(Modifier.fillMaxSize().imePadding()) {
            // ---- üst çubuk: kamera bölgesi de başlığın renginde ----
            Row(
                Modifier.fillMaxWidth().background(if (Renk.sb) Renk.zemin else Renk.kart)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    // 3.72: siyah-beyaz temada üst çubuk olabildiğince dar (34 dp)
                    .then(if (Renk.sb) Modifier.height(34.dp).padding(horizontal = 2.dp) else Modifier.padding(horizontal = 4.dp, vertical = 2.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 4.46: kamera deliğinin altı boş kalır — sol grup · kamera boşluğu (ekranın tam ortasında) · sağ grup
                val gorunumK = androidx.compose.ui.platform.LocalView.current
                val yogK = LocalDensity.current
                val kameraBosluk = run {
                    val ins = gorunumK.rootWindowInsets
                    val r = ins?.displayCutout?.boundingRects?.firstOrNull { it.top <= 2 && it.width() < gorunumK.width / 2 }
                    when {
                        r != null -> with(yogK) { (r.width() + 12).toDp() }
                        WindowInsets.displayCutout.getTop(yogK) > 0 -> 40.dp
                        else -> 0.dp
                    }
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                // 3.63: siyah-beyaz temada VITA dizilişi: ← simge · ad · Kaydet · beyaz Dışa aktar; geri al/yinele oynatıcı satırında
                if (Renk.sb) Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = cik), contentAlignment = Alignment.Center) { Simge("geri", Renk.yazi, 20.dp) }
                else Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = cik), contentAlignment = Alignment.Center) { Simge("geri", Renk.soluk, 20.dp) }   // 4.28: yalnız simge
                var adMenu by remember { mutableStateOf(false) }
                var adDuzen by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { adMenu = true }.padding(horizontal = 6.dp, vertical = if (Renk.sb) 3.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.ad, color = Renk.soluk, fontSize = if (Renk.sb) 12.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Simge("asagi", Renk.soluk, 14.dp)
                    }
                    DropdownMenu(expanded = adMenu, onDismissRequest = { adMenu = false }) {
                        DropdownMenuItem(text = { Text("Adını değiştir") }, onClick = { adMenu = false; adDuzen = true })
                        DropdownMenuItem(text = { Text("Projelere dön") }, onClick = { adMenu = false; cik() })
                    }
                }
                if (adDuzen) {
                    var ad by remember { mutableStateOf(p.ad) }
                    AlertDialog(onDismissRequest = { adDuzen = false }, title = { Text("Proje adı") },
                        text = { OutlinedTextField(ad, { ad = it }, singleLine = true) },
                        confirmButton = { TextButton(onClick = { vm.adDegistir(ad); adDuzen = false }) { Text("Tamam") } },
                        dismissButton = { TextButton(onClick = { adDuzen = false }) { Text("Vazgeç") } })
                }
                // 3.75: ekran sürekli açık (düzenlerken ekran kararmasın) — dokun = aç/kapat, seçim hatırlanır
                EkranAcikDugmesi(vm)
                // 4.28: arama ve Ayarlar sistem satırında — küçük, soluk simgeler
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { aramaAcik = true }, contentAlignment = Alignment.Center) { Simge("ara", Renk.soluk.copy(alpha = 0.75f), 18.dp) }
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { git("yayin", "ayarlar", null) }, contentAlignment = Alignment.Center) {
                    Simge("menu", if (bolum == "yayin" && haplar["yayin"] == "ayarlar" && panelAcik) Renk.turuncu else Renk.soluk.copy(alpha = 0.75f), 18.dp) }
                }
                if (kameraBosluk > 0.dp) Spacer(Modifier.width(kameraBosluk))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                val geriVar by vm.geriVar.collectAsState(); val ileriVar by vm.ileriVar.collectAsState()
                if (!Renk.sb) {   // 3.72: siyah-beyaz temada geri al/yinele oynatıcı satırında (videonun üstünde ya da altında)
                val ub = if (Renk.sb) 30.dp else 28.dp; val us = if (Renk.sb) 17.dp else 16.dp   // 4.46: gece temasında küçük (Dışa aktar sığsın)
                Box(Modifier.size(ub).clip(CircleShape).clickable(enabled = geriVar) { vm.geriAl() }, contentAlignment = Alignment.Center) {
                    Simge("gerial", if (geriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), us) }
                Box(Modifier.size(ub).clip(CircleShape).clickable(enabled = ileriVar) { vm.yinele() }, contentAlignment = Alignment.Center) {
                    Simge("yinele", if (ileriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), us) }
                }
                if (Renk.sb) {
                    // 3.72: Kaydet ve Dışa aktar düğme değil, küçük yazı (Ahmet'in isteği)
                    val etkin = p.klipler.isNotEmpty()
                    Text("Kaydet", color = Renk.soluk, fontSize = 12.5.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(6.dp)).clickable { vm.kaydetSimdi() }.padding(horizontal = 8.dp, vertical = 6.dp))
                    Text("Dışa aktar", color = if (etkin) Color.White else Renk.soluk.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Normal, maxLines = 1, softWrap = false,   // 3.72: üst çubukta kalın yazı yok
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = etkin) { disaAcik = true }.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp))
                } else {
                    InceDugme("Kaydet", Renk.turkuaz, mod = Modifier.weight(1f, fill = false)) { vm.kaydetSimdi() }   // 4.46: yer daralırsa önce bu kısalır; Dışa Aktar hep tam
                    Box(Modifier.width(1.dp).height(16.dp).background(Renk.cizgi))
                    InceDugme("Dışa Aktar", Renk.turuncu, etkin = p.klipler.isNotEmpty()) { disaAcik = true }
                }
                }
            }
            val nesneModu = panelAcik && (bolum == "nesne" || bolum == "katman")   // 3.45: nesne kesme Katmanlar'da da
            // açılışta önizleme videonun oranına tam oturur (üstte/altta boşluk yok); tutamaç yine çalışır
            val tamOran = run {
                val oranV = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
                ((ekranGen / oranV) / ekranYuk).coerceIn(Duzen.ORAN_EN_AZ, Duzen.ORAN_EN_COK)
            }
            // 4.11 KÖK NEDEN: bu etki tam ekrandan/dışa aktarmadan/kapaktan dönünce ve her açılışta yeniden çalışıp elle verilen boyu silip
            // videonun oranına oturtuyordu. Artık yalnız elle boy verilmediyse ya da biçim değiştiyse oturtulur.
            val sonBicim = remember { mutableStateOf(ctx.getSharedPreferences("ayar", 0).getString("sonBicim", null)) }
            LaunchedEffect(p.id, p.bicim) {
                val bicimDegisti = sonBicim.value != null && sonBicim.value != p.bicim
                if (!Duzen.elleOran || bicimDegisti) { Duzen.onizlemeOran = tamOran; if (bicimDegisti) { Duzen.elleOran = false; Duzen.oranKaydet(ctx) } }
                sonBicim.value = p.bicim; ctx.getSharedPreferences("ayar", 0).edit().putString("sonBicim", p.bicim).apply()
            }
            // 3.72: Görünüm › Şablon açıkken hangi alt sayfa (giriş/abone/kapanış) — önizlemede yazıya dokun = ekranda düzenle
            val sablonSekmeS by vm.sablonSekme.collectAsState()
            val sablonSayfa = if (panelAcik && bolum == "gorunum" && haplar["gorunum"] == "sablon")
                (if (Duzen.yaziliMenu) sablonSekmeS else AltHafiza.al("sablon")).takeIf { it in setOf("giris", "ozet", "abone", "kapanis") }   // 4.48: özet de (parmakla taşıma)
                else if (panelAcik && bolum == "hazirb" && !Duzen.yaziliMenu && AltHafiza.al("hazirlar") == "girisb") "giris"   // 4.48: Hazır › Giriş başlığı
                else null
            val ekranDuzen by vm.ekranDuzen.collectAsState()
            LaunchedEffect(sablonSayfa) { if (sablonSayfa == null || (ekranDuzen != null && ekranDuzen != sablonSayfa)) vm.ekranDuzen.value = null }
            Box(Modifier.fillMaxWidth().height(onizlemeYuk).clipToBounds()) {
                OnizlemeKatmanli(vm, p, nesneModu, Modifier.fillMaxSize(), logoPaneli = panelAcik && bolum == "yayin" && haplar["yayin"] == "logo",
                    katmanPaneli = panelAcik && (bolum == "katman" || bolum == "metin" && haplar["metin"] == "yazi"), hareketPaneli = panelAcik && bolum == "gorunum" && haplar["gorunum"] == "hareket",
                    renkPaneli = panelAcik && (bolum == "renk" || bolum == "efekt" && haplar["efekt"] != "gecis"), sablonSayfa = sablonSayfa,
                    yuzPaneli = panelAcik && !Duzen.yaziliMenu && bolum == "nesne" && haplar["nesne"] == "yuz" && AltHafiza.al("yuz") == "yuzler",
                    gizlePaneli = panelAcik && !Duzen.yaziliMenu && bolum == "nesne" && haplar["nesne"] == "yuz" && AltHafiza.al("yuz") == "gizle")
                // 4.36: klip yokken ortada büyük, sade Play (YouTube gibi) — dokun = video/fotoğraf ekle
                if (p.klipler.isEmpty()) Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp)).clickable { ekle() }.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(78.dp).clip(CircleShape).background(Renk.turuncu), contentAlignment = Alignment.Center) { Simge("oynat", Renk.turuncuM, 40.dp) }
                    Text("Video ya da fotoğraf ekle", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
                }
                // 4.08: önizlemedeki klibi silme — sağ üstte ✕; 1. dokunuş kırmızı "Sil?", 2. dokunuş ekrandaki klibi siler (klip şeridinden de; geri al çalışır)
                val oynuyorS by vm.oynuyor.collectAsState()
                if (p.klipler.isNotEmpty() && !oynuyorS) {
                    val siraS by vm.sira.collectAsState()
                    var onizSilOnay by remember { mutableStateOf(false) }
                    LaunchedEffect(onizSilOnay, siraS) { if (onizSilOnay) { delay(3000); onizSilOnay = false } }
                    Row(Modifier.align(Alignment.TopEnd).padding(6.dp).height(28.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (onizSilOnay) Renk.kirmizi else Color(0x99000000)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(14.dp))
                        .clickable {
                            if (onizSilOnay) {
                                onizSilOnay = false
                                vm.sec(siraS.coerceIn(0, p.klipler.lastIndex)); vm.klipSil()
                                vm.mesaj.value = "Klip silindi · geri al ile geri gelir"
                            } else onizSilOnay = true
                        }.padding(horizontal = if (onizSilOnay) 9.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Simge("kapat", Color.White, 15.dp)
                        if (onizSilOnay) Text("Sil?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 3.dp))
                    }
                }
                // 4.21 KÖK NEDEN ("oynatıcı düğmeleri, oynat görünmüyor"): yazılı menü seçiliyken siyah-beyaz temada hiçbir oynatıcı çizilmiyordu
                // (üstteki yalnız yeni menüde, alttaki yazılı satır yalnız renkli temalarda). Artık siyah-beyaz temada her menü düzeninde var.
                if ((!Duzen.yaziliMenu || Renk.sb) && (!Renk.sb || Duzen.kontrolUstte) && p.klipler.isNotEmpty())
                    OynaticiKatman(vm, p, Modifier.align(Alignment.BottomCenter), tamEkran = { tamEkranV = true },
                        altaAl = if (Renk.sb) ({ Duzen.kontrolDegis(ctx) }) else null)
            }
            ekranDuzen?.let { t -> EkranYaziDuzenleyici(vm, p, t) }
            // 4.18 KÖK NEDEN ("çift dokununca yazıyı düzenleyemiyorum"): 4.13'te yazı kutusu Katman sayfasından kaldırılmıştı; çift dokunuş yazıyı
            // seçiyor ama kutu yalnız Metin › Yazı'da görünüyordu. Standart: seçili yazı varken düzenleme kutusu HER bölümde önizlemenin hemen altında
            // (Metin › Yazı sayfası zaten gösterdiği için orada tekrarlanmaz). Çift dokunuş / ✎ tutamacı / menüdeki "Yazıyı düzenle" klavyeyi açar.
            val secKatE by vm.seciliKatman.collectAsState()
            val secYaziE = p.katmanlar.firstOrNull { it.id == secKatE && it.tur == "metin" }
            // 4.20: Katman bölümünde kutu çizelgenin altında (KatmanBolumu) — kliplerin üstünü örtmez
            val yaziSayfasi = (bolum == "metin" && haplar["metin"] == "yazi") || misafir?.second == "yazi" || (bolum == "katman" && misafir == null)
            if (secYaziE != null && !yaziSayfasi && ekranDuzen == null) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) { YaziDuzenleBlok(vm, secYaziE) }   // 3.72: şablon yazısı ekranda düzenleme (anında önizlemeye yansır)
            if (Renk.sb && !Duzen.kontrolUstte && p.klipler.isNotEmpty()) OynaticiSatiriSB(vm, p, tamEkran = { tamEkranV = true }) { Duzen.kontrolDegis(ctx) }
            // tutamaç: sürükle = önizlemeyi büyüt/küçült (ayar alanı genişler), çift dokun = varsayılan boy
            val ctxT = LocalContext.current
            val yog = LocalDensity.current
            Box(Modifier.fillMaxWidth().height(26.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { Duzen.elleOran = true; Duzen.oranKaydet(ctxT) }) { ch, d ->
                        ch.consume()
                        val yukPx = with(yog) { ekranYuk.toPx() }
                        Duzen.onizlemeOran = (Duzen.onizlemeOran + d / yukPx).coerceIn(Duzen.ORAN_EN_AZ, Duzen.ORAN_EN_COK)
                    }
                }
                .pointerInput(tamOran) { detectTapGestures(onDoubleTap = { Duzen.onizlemeOran = tamOran; Duzen.elleOran = false; Duzen.oranKaydet(ctxT) }) },
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(64.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.turuncu.copy(alpha = 0.8f)))
            }
            if (Duzen.yaziliMenu && !Renk.sb) OynaticiSatiri(vm, p)
            // 4.52: alt menü / 3. seviye hesapları panelden önce (panel başlığındaki sekmeler de kullanır)
            val b = BOLUMLER.first { it.anahtar == bolum }
            val renkSekme by vm.renkSekme.collectAsState()
            val altlar: List<AltOge> = if (bolum == "renk") {
                // 3.70: Renk alt menüsü gruplu (Hazır · Işık · Renk · Detay · HSL); gruptaki ayarlar panelin tepesinde çip
                val ra = p.klipler.getOrNull(sec)?.renk ?: RenkAyar()
                RENK_GRUPLARI.map { g -> AltOge(g.ad, g.simge, Color(g.renk), panelAcik && renkGrubu(renkSekme).anahtar == g.anahtar, g.sayfalar.any { renkSekmeDegisti(ra, it) }) {
                    git("renk", g.anahtar, null) } }
            } else b.haplar.mapIndexed { sira, (k, ad) ->
                AltOge(ad, HAP_SIMGE[k] ?: "ekle", Color(ALT_RENK[sira % ALT_RENK.size]), panelAcik && haplar[bolum] == k, degisen["$bolum/$k"] == true) {
                    if (k == "disa") disaAcik = true else { haplar[bolum] = k; panelAcik = true; misafir = null }
                }
            }
            // 3.71: 3. katman çipleri altta, menünün hemen üstünde (üstte menü yok — Ahmet'in isteği)
            // 4.15: yeni düzende ayrı satır yok — çipler alt menü satırında seçili alt menünün hemen yanında (tek satır, sağa kayar)
            val sekmeMod = Duzen.menuSekme && !Duzen.yaziliMenu   // 4.52: alt menü ve 3. seviye panelin başlığında (C)
            val yeniAlt = (Duzen.altUstte || sekmeMod) && !Duzen.yaziliMenu
            val cipVeri: CipVeri? = if (panelAcik && !Duzen.yaziliMenu && misafir == null) {
                if (bolum == "renk") {
                    val g = renkGrubu(renkSekmeS)
                    if (g.sayfalar.size > 1) {
                        val ra = p.klipler.getOrNull(sec)?.renk ?: RenkAyar()
                        CipVeri(g.sayfalar.mapNotNull { sy -> RENK_SEKMELERI.firstOrNull { it.anahtar == sy }?.let { AltSayfa(it.anahtar, it.ad, it.simge) } },
                            renkSekmeS, { renkSekmeDegisti(ra, it) }, emptyList()) { sy -> vm.renkSekme.value = sy; AltHafiza.sec("renk_" + g.anahtar, sy) }
                    } else null
                } else {
                    val h = haplar[bolum] ?: ""
                    // 4.13: Görünüm › Şablon'da bu bölümde olmayan varsayılan maddelere bağlantılar (Varsayılan ayarlar'daki 7 maddeden)
                    val bag = if (h == "sablon") listOf<Triple<String, String, () -> Unit>>(
                        Triple("Kadraj", "kadraj") { git("duzenle", "klip", "kadraj") },
                        Triple("Yüze yaklaş", "kisi") { git("nesne", "yuz", "yuzler") },
                        Triple("Logo", "logo") { git("yayin", "logo", null) },
                        Triple("Müzik", "nota") { git("ses", "muzik", "hazir") },
                    ) else emptyList()
                    ALT_SAYFALAR[h]?.let { altListe -> CipVeri(altListe, AltHafiza.al(h), { altDegisti(p, p.klipler.getOrNull(sec), h, it) }, bag) { a -> AltHafiza.sec(h, a) } }
                }
            } else null
            Column(Modifier.fillMaxWidth().weight(1f)) {
                if (sekmeMod && panelAcik && misafir == null) PanelSekmeleri(altlar, cipVeri)
                if (bolum == "renk" && panelAcik) {
                    // 3.53: Renk — klip seçimi + sabit önizleme/ayar düğmeleri; yalnız seçili ayarın sayfası kayar
                    KlipSeridi(vm, p, sec, ekle, kucuk = true)
                    Box(Modifier.fillMaxWidth().weight(1f)) { RenkBolumu(vm, p, sec) }
                } else if (bolum == "katman" && panelAcik && misafir != null) {
                    // 4.13: misafir sayfa — başlıkta "‹ Katman" ve sayfanın asıl yeri; alttaki Katman menüsü değişmez
                    val (mb, mh, ma) = misafir!!
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable { misafir = null }.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Simge("sol", Renk.turuncu, 15.dp); Text(" Katman", color = Renk.turuncu, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        val bAd = BOLUMLER.firstOrNull { it.anahtar == mb }
                        Text("${bAd?.ad ?: ""} › ${bAd?.haplar?.firstOrNull { it.first == mh }?.second ?: mh}", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        Text("Orada aç ›", color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { misafir = null; git(mb, mh, ma) }.padding(horizontal = 8.dp, vertical = 5.dp))
                    }
                    if (mh in setOf("takip")) KlipSeridi(vm, p, sec, ekle, kucuk = true)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                        Panel(vm, p, sec, mb, mh, ekle, alt = if (Duzen.yaziliMenu) null else ma ?: AltHafiza.al(mh))
                        Spacer(Modifier.height(8.dp))
                    }
                } else if (bolum == "katman" && panelAcik && haplar["katman"] !in setOf("knesne", "khareket")) {
                    KatmanBolumu(vm, p, acMuzik = { bolum = "ses"; haplar["ses"] = "muzik" }, acSes = { bolum = "ses"; haplar["ses"] = "seslendirme" },
                        sayfa = haplar["katman"] ?: "katmanlar", misafirAc = { b2, h2, a2 -> misafir = Triple(b2, h2, a2) }, ekle = ekle)
                } else if (!panelAcik || (!Duzen.yaziliMenu && bolum == "duzenle" && haplar["duzenle"] == "klip" && AltHafiza.al("klip") == "klipler")) {
                    // 3.29: Düzenle › Klip ile panel kapalıyken AYNI görünüm (iki ayrı sayfa yok): büyük şerit + altında süre/kırpma
                    // 3.31: kaydırılabilir; şerit + bölüm kartları (Geçişler paneli biçiminde)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 10.dp)) {
                        // 3.35: büyük şerit gizlenebilir (izlerken klip resimleri göz yormasın); seçim hatırlanır
                        val ctxS = LocalContext.current
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (Duzen.seritGizli) "Klipler gizli · ${p.klipler.size} klip" else "Klipler", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            GizleGosterDugmesi(!Duzen.seritGizli, Color(TON[2])) { Duzen.seritDegis(ctxS) }   // 3.57: bölüm başlıklarıyla aynı belirgin düğme
                        }
                        // 3.53: "Kırp ve ekle" kartı kalktı — kırpma/süre klip resmindeki ✂ simgesinde, Biçim Görünüm › Şablon › Hazır paketler'de
                        if (!Duzen.seritGizli || p.klipler.isEmpty()) KlipSeridi(vm, p, sec, ekle, kapakAc = { kapakAcik = true },
                            yaziEkle = { vm.katmanMetinEkle(); bolum = "metin"; haplar["metin"] = "yazi"; panelAcik = true; altMod = true },
                            hareketliEkle = { vm.katmanMetinEkle("acilan", "Yazınız", 4000, 0.5f, 0.72f, 0.042f, anim = "yok", ad = "Alıntı açılır")
                                bolum = "metin"; haplar["metin"] = "yazi"; panelAcik = true; altMod = true },
                            nesneAc = { bolum = "nesne"; panelAcik = true; altMod = true },
                            takipAc = { bolum = "metin"; haplar["metin"] = "takip"; panelAcik = true; altMod = true },
                            bolumAc = { b, h -> bolum = b; if (b == "renk") vm.renkSekme.value = h else haplar[b] = h; panelAcik = true; altMod = true })
                        if (p.klipler.isEmpty()) Text("Video ya da fotoğraf eklemek için şeritteki + düğmesine dokun.", color = Renk.soluk, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                        else if (!Duzen.seritGizli) AcilirNot("Kırpmak ya da süre ayarlamak için seçili klibin üstündeki kırp simgesine dokun.")
                    }
                } else {
                    // klip seçimi gereken bölümlerde küçük şerit panelin üstünde kalır
                    val klipli = haplar[bolum] in setOf("klip", "kadraj", "hareket", "klipses", "takip", "filtre", "efekt")
                    if (klipli) KlipSeridi(vm, p, sec, ekle, kucuk = true)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                        Panel(vm, p, sec, bolum, haplar[bolum] ?: "", ekle, alt = if (Duzen.yaziliMenu) null else AltHafiza.al(haplar[bolum] ?: ""))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ---- 3.61: alt menüler + ana menü (Ahmet'in isteği) ----
            // Ana menüde bir bölüme dokununca alttaki ana menü kaybolur, yerine o bölümün alt menüleri gelir; başta Geri.
            // Geri: ana menü geri gelir, alt menüler bir üst satıra çıkar. Renk'in ayar düğmeleri (Parlaklık, Kontrast…) de bu satırlarda.
            val tekSatir = yeniAlt && (Duzen.cipTekSatir || bolum == "hazirb")   // 4.16: varsayılan ayrı satır (Ayarlar › Menü görünümü) · 4.47: Hazır hep yanında
            if (!tekSatir && !sekmeMod) cipVeri?.let { c -> AltSayfaCipleri(c.liste, c.secili, c.isaret, c.bag, c.sec) }
            // 3.89: sabit Düzenle düğmesi (yalnız düğme, alt menüleri değil) — satırın solunda kaymadan durur; küçük
            @Composable fun SabitDuzenle() {
                val on = bolum == "duzenle"
                // 4.06: sade görünüm — kutu/çerçeve yok, diğer menülerle aynı boyda; yalnız açıkken vurgu renginde; sabit olduğu ince ayırıcıyla belli
                if (Duzen.duzenleSade) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(54.dp).clip(RoundedCornerShape(10.dp)).clickable { bolum = "duzenle"; haplar["duzenle"] = "klip"; panelAcik = true; altMod = true }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk")) Simge("makas", if (on) Renk.turuncu else Renk.yazi, 20.dp * Duzen.menuOlcek)
                            if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk") && Duzen.menuStil != "simge") Spacer(Modifier.height(3.dp))
                            if (Duzen.menuStil != "simge") Text("Düzenle", color = if (on) Renk.turuncu else Renk.soluk, fontSize = 10.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                        }
                        Box(Modifier.padding(start = 2.dp, end = 4.dp).width(1.dp).height(30.dp).background(Renk.cizgi.copy(alpha = 0.6f)))
                    }
                    return
                }
                Column(Modifier.padding(end = 4.dp).width(62.dp).clip(RoundedCornerShape(11.dp))
                    .background(Renk.turuncu.copy(alpha = if (on) 0.22f else 0.08f)).border(1.dp, Renk.turuncu.copy(alpha = if (on) 0.7f else 0.35f), RoundedCornerShape(11.dp))
                    .clickable { bolum = "duzenle"; haplar["duzenle"] = "klip"; panelAcik = true; altMod = true }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("makas", Renk.turuncu, 21.dp)
                    Text("Düzenle", color = Renk.turuncu, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
            @Composable fun SabitArama() { }   // 4.28: arama ve Ayarlar üstteki sistem satırına taşındı
            if (altMod && panelAcik && !yeniAlt) {
                // alt menü modu: ana menünün yerinde — Geri + bölümün alt menüleri
                // 3.67: Geri sade (zeminsiz, soluk ince ok) ve sabit — alt menüler yanında kayar, Geri yerinde kalır
                Row(Modifier.fillMaxWidth().background(Renk.zemin).windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 2.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (Duzen.duzenleSabit && bolum != "duzenle") SabitDuzenle()
                    Column(Modifier.width(44.dp).clip(RoundedCornerShape(10.dp)).clickable { altMod = false }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Simge("sol", Renk.soluk, 20.dp)
                        Text(b.ad, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 9.5.sp, maxLines = 1)
                    }
                    Box(Modifier.padding(end = 4.dp).width(1.dp).height(32.dp).background(Renk.cizgi.copy(alpha = 0.6f)))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)) { altlar.forEach { AltDugme(it) } }
                    // 4.01: "Sabitle" düğmesi kaldırıldı (Ahmet'in isteği) — Düzenle hep sabit
                    SabitArama()
                }
            } else {
                if (Duzen.yaziliMenu && (altlar.size > 1 || bolum == "renk")) {   // 3.71: yeni düzende ana menünün üstünde ayrı alt menü satırı yok
                    if (bolum != "renk") {
                        // önceki yazılı haplar (⋯ menüsünden seçilebilir)
                        Row(Modifier.fillMaxWidth().background(Renk.kart).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            b.haplar.forEach { (k, ad) -> HapEski(ad, panelAcik && haplar[bolum] == k) { if (k == "disa") disaAcik = true else { haplar[bolum] = k; panelAcik = true } } }
                        }
                    } else {
                        // alt menüler ana menünün hemen üstünde (Renk dahil)
                        Row(Modifier.fillMaxWidth().background(Renk.kart).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)) { altlar.forEach { AltDugme(it) } }
                    }
                }
                // 4.14: alt menüler küçük satır olarak ana menünün üstünde; başında bölümün adı sabit (tema renginde), alt menüler yanında kayar
                if (yeniAlt && panelAcik && altlar.isNotEmpty() && !sekmeMod) {   // 4.52: sekme düzeninde bu satır panelin başında
                    Row(Modifier.fillMaxWidth().background(Renk.zemin).padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 4.17: yalnız simge (yazısız, dar)
                        Box(Modifier.size(32.dp * Duzen.menuOlcek).clip(CircleShape).background(Renk.turuncu.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Simge(b.simge, Renk.turuncu, 17.dp * Duzen.menuOlcek)
                        }
                        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(Renk.cizgi))
                        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            altlar.forEach { a ->
                                AltKucuk(a)
                                // 4.15: seçili alt menünün sayfaları hemen yanında, ince bir grup içinde (tek satır)
                                if (a.secili && tekSatir) cipVeri?.let { c -> SatirCipleri(c) }
                            }
                        }
                    }
                }
                // ana bölümler (yana kayar); dokununca alt menü modu açılır
                val anaKaydir = rememberScrollState()
                // 3.89: sabit Düzenle solda (sabitliyse), sabit menü arama sağda; diğer bölümler aradan kayar
                Row(Modifier.fillMaxWidth().background(Renk.zemin).windowInsetsPadding(WindowInsets.navigationBars).padding(start = 6.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                if (Duzen.duzenleSabit) SabitDuzenle()
                Row(Modifier.weight(1f).horizontalScroll(anaKaydir), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BOLUMLER.filter { !(Duzen.duzenleSabit && it.anahtar == "duzenle") }.forEach { bb ->
                        val on = bb.anahtar == bolum
                        val oncu = bb.anahtar == "duzenle"   // 3.87: Düzenle öncü menü — vurgu renginde, hafif dolgulu · 3.89: daha küçük (84 → 70 dp)
                        Column(
                            Modifier.width((if (oncu) 70.dp else 74.dp) * Duzen.menuOlcek).clip(RoundedCornerShape(12.dp))
                                .background(if (on) Renk.turuncu.copy(alpha = if (oncu) 0.22f else 0.12f) else if (oncu) Renk.turuncu.copy(alpha = 0.08f) else Color.Transparent)
                                .then(if (oncu) Modifier.border(1.dp, Renk.turuncu.copy(alpha = if (on) 0.7f else 0.35f), RoundedCornerShape(12.dp)) else Modifier).clickable {
                                bolum = bb.anahtar; if (bb.anahtar == "duzenle") haplar["duzenle"] = "klip"; if (bb.anahtar == "katman") haplar["katman"] = "katmanlar";   // 4.20: önce Katmanlar panelAcik = true; altMod = true
                            }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk")) Simge(bb.simge, if (on || oncu) Renk.turuncu else Renk.yazi, (if (oncu) 22.dp else 24.dp) * Duzen.menuOlcek)
                            if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk") && Duzen.menuStil != "simge") Spacer(Modifier.height(4.dp * Duzen.menuOlcek))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (Duzen.menuStil != "simge") Text(menuAd(bb.ad), color = if (on || oncu) Renk.turuncu else Renk.soluk, fontSize = 11.5.sp * Duzen.menuOlcek * menuYaziK, fontWeight = if (on || oncu) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                                if (degisen[bb.anahtar] == true) OnayIsareti(Modifier.padding(start = 2.dp), 10.dp)
                            }
                            // 4.14: yeni düzende seçili ana bölümün altında tema renginde çizgi (üstteki alt menü satırı bu bölüme ait)
                            if (yeniAlt) Box(Modifier.padding(top = 3.dp).width(28.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (on && panelAcik) Renk.turuncu else Color.Transparent))
                        }
                    }
                }
                SabitArama()
                }
            }
        }
        mesgul?.let { m ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Renk.kart).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Renk.turuncu)
                    Spacer(Modifier.height(10.dp))
                    Text(m, color = Renk.yazi)
                    val iptal by vm.mesgulIptal.collectAsState()   // 3.81
                    iptal?.let { f -> Text("Vazgeç", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { f() }.padding(8.dp)) }
                }
            }
        }
        mesaj?.let { m ->
            Text(m, color = Renk.yazi, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
                .clip(RoundedCornerShape(20.dp)).background(Renk.kart2).padding(horizontal = 16.dp, vertical = 9.dp))
        }
    }
    uyari?.let { u ->
        AlertDialog(onDismissRequest = { vm.uyari.value = null }, text = { Text(u) },
            confirmButton = { TextButton(onClick = { vm.uyari.value = null }) { Text("Tamam") } })
    }
}

@Composable
private fun Onizleme(vm: EditorDurum, p: Proje, modifier: Modifier) {
    val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
    val sira by vm.sira.collectAsState()
    val k = p.klipler.getOrNull(sira)
    Box(modifier.background(Color.Black).padding(4.dp), contentAlignment = Alignment.Center) {
        if (p.klipler.isEmpty()) {
            Text("Başlamak için alttan “＋ Ekle” ile video veya fotoğraf seç.\nBoyut sınırı yok; dosyalar kopyalanmaz.",
                color = Renk.soluk, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            return@Box
        }
        BoxWithConstraints(Modifier.aspectRatio(oran).clipToBounds().background(Color.Black)) {
            val ref = remember(p.bicim) { Disa.boyut(p.bicim, 1080) }
            var kbF by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(k?.id, k?.kenBurns, k?.fotoSureMs) {
                if (k != null && k.foto && k.kenBurns) {
                    while (true) {
                        withFrameNanos { }
                        kbF = (vm.player.currentPosition.toFloat() / k.fotoSureMs).coerceIn(0f, 1f)
                    }
                } else {
                    kbF = 0f
                }
            }
            val kbAn = k?.let { Yuz.an(it, ref.first, ref.second, kbF) }
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        player = vm.player
                        kareyiYenile(vm.player)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)   // 3.80: kare gelene kadar alttaki görüntü görünsün
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { v ->
                    v.kadraj(k?.doldur == true)
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val kb = kbAn?.s ?: 1f
                    scaleX = kb; scaleY = kb
                    translationX = kbAn?.onizlemeX(size.width) ?: 0f; translationY = kbAn?.onizlemeY(size.height) ?: 0f
                },
            )
            val yaziY = remember(k?.yazi, ref) { k?.yazi?.let { Kaplama.yazi(it, ref.first, ref.second) } }
            val logoY = remember(p.logo, ref) { p.logo?.let { Kaplama.logo(it, ref.first, ref.second) } }
            val olcek = constraints.maxWidth.toFloat() / ref.first
            val yogunluk = LocalDensity.current
            for (y in listOfNotNull(yaziY, logoY)) {
                val (x0, y0) = y.solUst(ref.first, ref.second)
                val gw = with(yogunluk) { (y.bmp.width * olcek).toDp() }
                val gh = with(yogunluk) { (y.bmp.height * olcek).toDp() }
                Image(
                    y.bmp.asImageBitmap(), null,
                    Modifier.offset { IntOffset((x0 * olcek).roundToInt(), (y0 * olcek).roundToInt()) }.size(gw, gh).alpha(y.alfa),
                )
            }
        }
    }
}

@Composable
private fun UstDugme(metin: String, simge: String, renk: Color, yaziRenk: Color, modifier: Modifier = Modifier, etkin: Boolean = true, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(24.dp)
    Row(
        modifier.height(44.dp).clip(sekil).background(Brush.verticalGradient(listOf(renk, renk.copy(alpha = 0.82f)))).alpha(if (etkin) 1f else 0.45f)
            .clickable(enabled = etkin, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Simge(simge, yaziRenk, 19.dp)
        Spacer(Modifier.width(7.dp))
        Text(metin, color = yaziRenk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

/** Kalın zaman çubuğu: dokun/sürükle = o ana git. */
@Composable
private fun ZamanCubugu(konum: Long, toplam: Long, git: (Long) -> Unit) {
    val f = (konum.toFloat() / toplam.coerceAtLeast(1)).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(20.dp)
            .pointerInput(toplam) { detectTapGestures { o -> git((o.x / size.width * toplam).toLong()) } }
            .pointerInput(toplam) { detectHorizontalDragGestures { ch, _ -> ch.consume(); git((ch.position.x / size.width * toplam).toLong().coerceIn(0, toplam)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.cizgi))
        Box(Modifier.width(w * f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.turuncu))
        Box(Modifier.offset(x = (w * f - 2.dp).coerceIn(0.dp, w - 4.dp)).width(4.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Renk.turuncu))
    }
}

/** Videonun üstünde, altta şeffaf kontrol şeridi (ayrı satır yer kaplamaz): oynat · başa · kare geri/ileri · ince çubuk · süre. */
@Composable
private fun OynaticiKatman(vm: EditorDurum, p: Proje, modifier: Modifier, tamEkran: (() -> Unit)? = null, tamEkranda: Boolean = false, altaAl: (() -> Unit)? = null) {
    val konum by vm.konum.collectAsState()
    val oyn by vm.oynuyor.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1)
    val beyaz = if (Duzen.oynaticiTema) Renk.turuncu else Color(0xF2FFFFFF)   // 4.18: tema renginde seçeneği
    // 3.71: şeffaf arka plan (koyu şerit yok); düğmeler parlak görüntüde de seçilsin diye hafif saydam yuvarlak üstünde
    val dz = Color(0x40000000)
    // 3.72: kontroller hep görünür (kendiliğinden gizlenme kaldırıldı — kontrollerin kaybolduğu sanılıyordu)
    val gorunurluk = 1f
    if (Renk.sb) {
        // 3.72: siyah-beyaz temada VITA dizilişi videonun üstünde (son tasarım korunur): süre · başa · önceki · büyük oynat · sonraki ·
        // geri al · yinele · (alta al) · tam ekran; altında ince zaman çubuğu. Altta hafif koyu geçiş, düğmeler parlak görüntüde de seçilir.
        val geriVar by vm.geriVar.collectAsState(); val ileriVar by vm.ileriVar.collectAsState()
        val f = (konum.coerceIn(0, toplam).toFloat() / toplam).coerceIn(0f, 1f)
        Column(modifier.fillMaxWidth().alpha(gorunurluk).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C000000))))
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${sureOndalik(konum)} / ${sureYaz(p.toplamMs)}", color = beyaz, fontSize = 11.5.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))   // 3.72: gruplar kendi boyunda — tam ekran düğmesi taşıp kaybolmaz
                if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.git(0) }, contentAlignment = Alignment.Center) { Simge("basa", beyaz, 15.dp) }
                if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.onceki() }, contentAlignment = Alignment.Center) { Simge("onceki", beyaz, 17.dp) }
                Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.oynatDurdur() }, contentAlignment = Alignment.Center) { Simge(if (oyn) "durdur" else "oynat", beyaz, 26.dp) }
                if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.sonraki() }, contentAlignment = Alignment.Center) { Simge("sonraki", beyaz, 17.dp) }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!tamEkranda) {
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = geriVar) { vm.geriAl() }, contentAlignment = Alignment.Center) {
                            Simge("gerial", if (geriVar) beyaz else beyaz.copy(alpha = 0.35f), 18.dp) }
                        Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = ileriVar) { vm.yinele() }, contentAlignment = Alignment.Center) {
                            Simge("yinele", if (ileriVar) beyaz else beyaz.copy(alpha = 0.35f), 18.dp) }
                    }
                    if (altaAl != null) Box(Modifier.size(30.dp).clip(CircleShape).clickable { altaAl() }, contentAlignment = Alignment.Center) { Simge("h_asagi", beyaz.copy(alpha = 0.7f), 15.dp) }
                    if (tamEkran != null) Box(Modifier.size(32.dp).clip(CircleShape).clickable { tamEkran() }, contentAlignment = Alignment.Center) {
                        Simge(if (tamEkranda) "kucult" else "tamekran", beyaz, 17.dp) }
                }
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().height(16.dp)
                    .pointerInput(toplam) { detectTapGestures { o -> vm.git((o.x / size.width * toplam).toLong()) } }
                    .pointerInput(toplam) { detectHorizontalDragGestures { ch, _ -> ch.consume(); vm.git((ch.position.x / size.width * toplam).toLong().coerceIn(0, toplam)) } },
                contentAlignment = Alignment.CenterStart,
            ) {
                val w = maxWidth
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
                Box(Modifier.width(w * f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                Box(Modifier.offset(x = (w * f - 5.dp).coerceIn(0.dp, w - 10.dp)).size(10.dp).clip(CircleShape).background(Color.White))
            }
        }
        return
    }
    Row(
        modifier.fillMaxWidth().alpha(gorunurluk).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x73000000))))   // 4.19: silik bant
            .padding(start = 4.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(dz).clickable { vm.oynatDurdur() }, contentAlignment = Alignment.Center) { Simge(if (oyn) "durdur" else "oynat", beyaz, 20.dp) }
        Spacer(Modifier.width(2.dp))
        if (!Duzen.oynaticiSade) Box(Modifier.size(28.dp).clip(CircleShape).background(dz).clickable { vm.git(0) }, contentAlignment = Alignment.Center) { Simge("basa", beyaz, 14.dp) }
        if (!Duzen.oynaticiSade) Box(Modifier.size(28.dp).clip(CircleShape).background(dz).clickable { vm.onceki() }, contentAlignment = Alignment.Center) { Simge("onceki", beyaz, 14.dp) }
        if (!Duzen.oynaticiSade) Box(Modifier.size(28.dp).clip(CircleShape).background(dz).clickable { vm.sonraki() }, contentAlignment = Alignment.Center) { Simge("sonraki", beyaz, 14.dp) }
        Spacer(Modifier.width(6.dp))
        val f = (konum.coerceIn(0, toplam).toFloat() / toplam).coerceIn(0f, 1f)
        BoxWithConstraints(
            Modifier.weight(1f).height(24.dp)
                .pointerInput(toplam) { detectTapGestures { o -> vm.git((o.x / size.width * toplam).toLong()) } }
                .pointerInput(toplam) { detectHorizontalDragGestures { ch, _ -> ch.consume(); vm.git((ch.position.x / size.width * toplam).toLong().coerceIn(0, toplam)) } },
            contentAlignment = Alignment.CenterStart,
        ) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x55FFFFFF)))
            Box(Modifier.width(w * f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Renk.turuncu))
            Box(Modifier.offset(x = (w * f - 6.dp).coerceIn(0.dp, w - 12.dp)).size(12.dp).clip(CircleShape).background(Color.White))
        }
        Spacer(Modifier.width(8.dp))
        Text("${sureOndalik(konum)} / ${sureYaz(p.toplamMs)}", color = beyaz, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(dz).padding(horizontal = 5.dp, vertical = 1.dp))
        if (altaAl != null) Box(Modifier.padding(start = 4.dp).size(28.dp).clip(CircleShape).background(dz).clickable { altaAl() }, contentAlignment = Alignment.Center) { Simge("h_asagi", beyaz, 14.dp) }
        if (tamEkran != null) Box(Modifier.padding(start = 4.dp).size(30.dp).clip(CircleShape).background(dz).clickable { tamEkran() }, contentAlignment = Alignment.Center) {
            Simge(if (tamEkranda) "kucult" else "tamekran", beyaz, 16.dp) }
    }
}

/** 3.71: video tam ekran — yalnız önizleme ve kontroller; yatay videoda ekran yan döner. Geri / küçült simgesi = çık. */
@Composable
private fun TamEkranVideo(vm: EditorDurum, p: Proje, cik: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { cik() }
    DisposableEffect(p.bicim) {
        val akt = ctx as? android.app.Activity
        if (p.bicim == "16:9") akt?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { akt?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        OnizlemeKatmanli(vm, p, false, Modifier.fillMaxSize())
        if (p.klipler.isNotEmpty()) OynaticiKatman(vm, p, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
            tamEkran = cik, tamEkranda = true)
    }
}

@Composable
private fun OynaticiSatiri(vm: EditorDurum, p: Proje) {
    val konum by vm.konum.collectAsState()
    val oyn by vm.oynuyor.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sureOndalik(konum), color = Renk.yazi, fontSize = 12.sp, modifier = Modifier.width(64.dp))
            Spacer(Modifier.weight(1f))
            if (!Duzen.oynaticiSade) Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.git(0) }, contentAlignment = Alignment.Center) { Simge("basa", Renk.yazi, 15.dp) }
            if (!Duzen.oynaticiSade) Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.onceki() }, contentAlignment = Alignment.Center) { Simge("onceki", Renk.yazi, 15.dp) }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(Renk.turuncu).clickable { vm.oynatDurdur() },
                contentAlignment = Alignment.Center,
            ) { Simge(if (oyn) "durdur" else "oynat", Color(0xFF111111), 16.dp) }
            Spacer(Modifier.width(6.dp))
            if (!Duzen.oynaticiSade) Box(Modifier.size(32.dp).clip(CircleShape).clickable { vm.sonraki() }, contentAlignment = Alignment.Center) { Simge("sonraki", Renk.yazi, 15.dp) }
            Spacer(Modifier.width(32.dp))
            Spacer(Modifier.weight(1f))
            Text(sureYaz(p.toplamMs), color = Renk.soluk, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        }
        ZamanCubugu(konum.coerceIn(0, toplam), toplam) { vm.git(it) }
    }
}

/** 3.63: siyah-beyaz tema (VITA dizilişi) — önizlemenin altında: süre · başa · önceki · oynat · sonraki · geri al · yinele; altında ince zaman çubuğu. */
@Composable
private fun OynaticiSatiriSB(vm: EditorDurum, p: Proje, tamEkran: () -> Unit = {}, ustteAl: () -> Unit = {}) {
    val konum by vm.konum.collectAsState()
    val oyn by vm.oynuyor.collectAsState()
    val geriVar by vm.geriVar.collectAsState(); val ileriVar by vm.ileriVar.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1)
    val yz = if (Duzen.oynaticiTema) Renk.turuncu else Renk.yazi   // 4.19: düğmeler tema renginde
    Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(horizontal = 12.dp)) {
        // 3.72 KÖK DÜZELTME: sağ grup (geri al · yinele · ▴ · ⛶) eşit "weight" payına sığmıyordu → son iki düğme (▴ üste al, ⛶ tam ekran)
        // ekranın dışında kalıp hiç görünmüyordu. Gruplar artık kendi boylarında, aradaki boşluk esnek; hepsi her ekranda sığar.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${sureOndalik(konum)} / ${sureYaz(p.toplamMs)}", color = yz, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.git(0) }, contentAlignment = Alignment.Center) { Simge("basa", yz, 16.dp) }
            if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.onceki() }, contentAlignment = Alignment.Center) { Simge("onceki", yz, 18.dp) }
            Box(Modifier.size(42.dp).clip(CircleShape).clickable { vm.oynatDurdur() }, contentAlignment = Alignment.Center) { Simge(if (oyn) "durdur" else "oynat", yz, 28.dp) }
            if (!Duzen.oynaticiSade) Box(Modifier.size(34.dp).clip(CircleShape).clickable { vm.sonraki() }, contentAlignment = Alignment.Center) { Simge("sonraki", yz, 18.dp) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = geriVar) { vm.geriAl() }, contentAlignment = Alignment.Center) {
                Simge("gerial", if (geriVar) yz else Renk.soluk.copy(alpha = 0.35f), 19.dp) }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = ileriVar) { vm.yinele() }, contentAlignment = Alignment.Center) {
                Simge("yinele", if (ileriVar) yz else Renk.soluk.copy(alpha = 0.35f), 19.dp) }
            // kontrolleri videonun üstüne al · tam ekran
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { ustteAl() }, contentAlignment = Alignment.Center) { Simge("h_yukari", yz, 18.dp) }
            Box(Modifier.size(32.dp).clip(CircleShape).clickable { tamEkran() }, contentAlignment = Alignment.Center) { Simge("tamekran", yz, 18.dp) }
        }
        ZamanCubugu(konum.coerceIn(0, toplam), toplam) { vm.git(it) }
    }
}

@Composable
private fun KlipSeridi(vm: EditorDurum, p: Proje, sec: Int, ekle: () -> Unit, kucuk: Boolean = false, kapakAc: (() -> Unit)? = null,
                       yaziEkle: (() -> Unit)? = null, hareketliEkle: (() -> Unit)? = null, nesneAc: (() -> Unit)? = null, takipAc: (() -> Unit)? = null,
                       bolumAc: ((String, String) -> Unit)? = null) {
    val git: (String, String) -> (() -> Unit)? = { b, h -> bolumAc?.let { f -> { f(b, h) } } }
    // 3.67: seçili klip kartındaki hareket yönü simgesi → Hareket sayfası (Geçişler biçiminde, açılır pencere)
    var hareketId by remember { mutableStateOf<String?>(null) }
    hareketId?.let { kid -> p.klipler.firstOrNull { it.id == kid }?.let { kk -> HareketSecimi(vm, kk) { hareketId = null } } ?: run { hareketId = null } }
    // 3.63: büyük şeridin başında Kapak kutusu (VITA gibi) — LazyRow sırası bir kayar
    val kapakVar = !kucuk && kapakAc != null && p.klipler.isNotEmpty()
    var gecisSecim by remember { mutableStateOf<Int?>(null) }
    gecisSecim?.let { i -> if (i < p.klipler.lastIndex) GecisSecimi(vm, p, i) { gecisSecim = null } }
    // 3.53: klip resmindeki kırp simgesi → Kırp / Süre sayfası (Geçişler biçiminde)
    var kirpId by remember { mutableStateOf<String?>(null) }
    kirpId?.let { kid -> p.klipler.firstOrNull { it.id == kid }?.let { kk -> KirpSecimi(vm, p, kk) { kirpId = null } } ?: run { kirpId = null } }
    // 3.58: Görünüm › Kadraj alt menüsü kaldırıldı — Sığdır/Doldur, tümüne uygula ve ortala klip resmindeki kadraj simgesinde
    var kadrajId by remember { mutableStateOf<String?>(null) }
    kadrajId?.let { kid -> p.klipler.firstOrNull { it.id == kid }?.let { kk -> KadrajSecimi(vm, p, kk) { kadrajId = null } } ?: run { kadrajId = null } }
    val durum = rememberLazyListState()
    // 3.73: oynarken (akıcı önizlemede de) oynayan klip seçili olur → şerit üstteki oynatmayla paralel ilerler, klip bitince sıradakine geçer
    LaunchedEffect(p.klipler) {
        vm.konum.collect { t ->
            if (!vm.oynuyor.value) return@collect
            val pp = vm.proje.value
            var kalan = t; var ix = pp.klipler.lastIndex
            for (j in pp.klipler.indices) { val d = pp.zKlip(j).sureMs; if (kalan < d) { ix = j; break }; kalan -= d }
            if (ix >= 0 && ix != vm.secili.value) vm.secili.value = ix
        }
    }
    // 3.42: etkin klip (oynatıcının üstündeki) şeridin ortasına kayar — izlerken ve sararken önizlemeyle eşleşir
    val yogS = LocalDensity.current
    LaunchedEffect(sec) {
        if (sec !in p.klipler.indices) return@LaunchedEffect
        val vp = durum.layoutInfo.viewportSize.width
        val kgPx = with(yogS) { (if (kucuk) 92.dp else 264.dp).toPx() }
        durum.animateScrollToItem(sec + (if (kapakVar) 1 else 0), -((vp - kgPx) / 2f).toInt().coerceAtLeast(0))
    }
    // 3.25 (Ahmet'in onayladığı taslak): seçili klip büyük; üstünde renkli küçük simgeler (ses, böl, çoğalt, sil) ve
    // kenarlarında sola/sağa taşı okları. Basılı tutup sürükleyerek de taşınır.
    val kw = if (kucuk) 92.dp else 112.dp
    val kwSecili = if (kucuk) 92.dp else 264.dp   // 3.67–3.68: simgeler sığsın diye seçili kart büyüdü (200 → 264)
    val yukseklik = if (kucuk) 62.dp else 216.dp   // 3.75: dört satır büyük simge sığsın (176 → 216)
    val yogunluk = LocalDensity.current
    val adimPx = with(yogunluk) { (kw + 8.dp + 38.dp).toPx() }
    var surukleId by remember { mutableStateOf<String?>(null) }
    var dx by remember { mutableFloatStateOf(0f) }
    var silOnayId by remember { mutableStateOf<String?>(null) }
    var albumAcik by remember { mutableStateOf(false) }
    var kameraAcik by remember { mutableStateOf(false) }
    LazyRow(
        state = durum,
        modifier = Modifier.fillMaxWidth().height(yukseklik).padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (kapakVar && kapakAc != null) item(key = "kapak_kutusu") {
            KapakKutusu(vm, p, Modifier.width(72.dp).fillMaxHeight(0.6f), kapakAc)
        }
        itemsIndexed(p.klipler, key = { _, k -> k.id }) { i, k ->
            val secili = i == sec
            val sekil = RoundedCornerShape(if (secili && !kucuk) 12.dp else 8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(if (secili) kwSecili else kw).fillMaxHeight(if (secili || kucuk) 1f else 0.6f)
                    .graphicsLayer { if (surukleId == k.id) { translationX = dx; scaleX = 1.04f; scaleY = 1.04f } }
                    .clip(sekil)
                    .border(if (secili) 2.5.dp else 0.5.dp, if (secili) Renk.turuncu else Renk.cizgi, sekil)
                    .clickable { vm.sec(i) }
                    .then(if (kucuk) Modifier else Modifier.pointerInput(k.id, i) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { surukleId = k.id; dx = 0f; vm.sec(i) },
                            onDrag = { c, d -> c.consume(); dx += d.x },
                            onDragEnd = {
                                val hedef = (i + (dx / adimPx).roundToInt()).coerceIn(0, p.klipler.lastIndex)
                                surukleId = null; dx = 0f
                                if (hedef != i) vm.klipTasi(i, hedef)
                            },
                            onDragCancel = { surukleId = null; dx = 0f },
                        )
                    }),
            ) {
                KlipKaresi(p, k, i, Modifier.fillMaxSize(), sigdir = secili && !kucuk)   // 3.72: önizlemedeki kadrajın aynısı
                // 3.75: karartma — üstteki oynatıcıyla yarışmasın (seçili kart belirgin koyu, diğerleri hafif); simgeler öne çıkar
                // 3.77: seçili kartta üst ve alt (simgeler) koyu, orta daha açık — fotoğraf ortada görünür
                if (!kucuk) Box(Modifier.fillMaxSize().background(if (secili) androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(0xA6000000), 0.3f to Color(0x59000000), 0.5f to Color(0x4D000000), 0.62f to Color(0x66000000), 1f to Color(0xB3000000))
                    else androidx.compose.ui.graphics.SolidColor(Color(0x47000000))))
                val sessiz = k.sessiz || (!k.foto && !k.sesVar)
                if (!(secili && !kucuk)) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(if (kucuk) 18.dp else 24.dp).clip(CircleShape).background(Color(0xCC0E151B)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Color.White, fontSize = if (kucuk) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
                }
                if (secili && !kucuk) {
                    // 3.75 (Ahmet'in isteği): simgeler büyük (36 dp) ve dört satırda — üst · ortanın üstü · orta (oynat) · alt
                    val B = 36.dp
                    KartIlerleme(vm, i, Modifier.align(Alignment.BottomStart))
                    if (i > 0) Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(30.dp).clip(CircleShape).background(Color(0xB3000000)).clickable { vm.tasi(-1) },
                        contentAlignment = Alignment.Center) { Simge("sol", Color(0xFF97C459), 18.dp) }
                    if (i < p.klipler.lastIndex) Box(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(30.dp).clip(CircleShape).background(Color(0xB3000000)).clickable { vm.tasi(1) },
                        contentAlignment = Alignment.Center) { Simge("ileri", Color(0xFF97C459), 18.dp) }
                    Column(Modifier.fillMaxSize().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        // 1) üst: numara · süre bilgisi · ses · süre/kırp · sil
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xCC0E151B)).border(1.dp, Color(0x55FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            val alt = buildList { add(sureOndalik(k.sureMs)); if (sessiz) add("Sessiz") }
                            Text(if (silOnayId == k.id) "Silmek için yine dokun" else alt.joinToString(" · "), color = Color.White, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (!k.foto && k.sesVar) SimgeDugmesi(if (k.sessiz) "sessiz" else "ses", 0xFF5DCAA5, boy = B, cift = git("ses", "klipses")) { vm.sesAcKapat() }
                            SimgeDugmesi("kirp", 0xFFFAC775, boy = B, cift = { kirpId = k.id }) { kirpId = k.id }
                            val onay = silOnayId == k.id
                            SimgeDugmesi("sil", if (onay) 0xFFFFFFFF else 0xFFED93B1, if (onay) Renk.kirmizi else Color(0xB3000000), boy = B) {
                                if (onay) { silOnayId = null; vm.klipSil() } else silOnayId = k.id
                            }
                        }
                        // 3.77: orta boş kalır (fotoğraf görünsün); simgeler altta iki satır
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            // 2) alttan ikinci: böl · çoğalt · nesne kes · kadraj · hareket · doğal renk
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                SimgeDugmesi("makas", 0xFFF0997B, boy = B, cift = git("duzenle", "klip")) { vm.klipBolSecili() }
                                SimgeDugmesi("cogalt", 0xFF85B7EB, boy = B, cift = git("duzenle", "klip")) { vm.klipCogalt() }
                                // 3.68: Nesne kes (fotoğraftan/videodan kesme); Kadraj (Sığdır/Doldur) ayrı tuval simgesinde
                                if (nesneAc != null) SimgeDugmesi("kadraj", 0xFF97C459, boy = B, cift = nesneAc) { nesneAc() }
                                SimgeDugmesi("tuval", 0xFF5DCAA5, boy = B, cift = { kadrajId = k.id }) { kadrajId = k.id }
                                // 3.87 (Ahmet'in isteği): hareket simgesi — dokun = bütün yönleri gösteren küçük menü; basılı tut = Hareket ayarları; çift dokun = Görünüm › Hareket
                                var yonMenu by remember(k.id) { mutableStateOf(false) }
                                Box(Modifier.size(B).clip(CircleShape).background(Color(0xB3000000)).border(1.dp, Color(0x40FFFFFF), CircleShape).pointerInput(k.id) {
                                    detectTapGestures(onTap = { yonMenu = true }, onLongPress = { hareketId = k.id },
                                        onDoubleTap = { bolumAc?.invoke("gorunum", "hareket") ?: run { hareketId = k.id } })
                                }, contentAlignment = Alignment.Center) {
                                    Simge(hareketSimgesi(k), Color(0xFFFFD54F), 21.dp)
                                    if (yonMenu) HareketYonMenusu(k, kapat = { yonMenu = false }, ayarlar = { yonMenu = false; hareketId = k.id },
                                        yuzTuru = { yonMenu = false; AltHafiza.sec("yuz", "yuzler"); bolumAc?.invoke("nesne", "yuz") }) { yeni -> yonMenu = false; hareketSec(vm, k, yeni) }
                                }
                                val dogal = k.renk == DOGAL_RENK
                                SimgeDugmesi("palet", if (dogal) 0xFF000000 else 0xFF97C459, if (dogal) Color(0xFF97C459) else Color(0xB3000000), boy = B, cift = git("renk", "hazir")) {
                                    vm.klipGuncelle { it.copy(renk = if (dogal) null else DOGAL_RENK) }
                                    vm.mesaj.value = if (dogal) "Doğal renk kaldırıldı" else "Doğal renk uygulandı"
                                }
                            }
                            // 3) en alt: takip · yazı · hareketli yazı · giriş başlığı · oynat
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                if (takipAc != null && !k.foto) SimgeDugmesi("takip", 0xFF85B7EB, boy = B, cift = takipAc) { takipAc() }
                                if (yaziEkle != null) SimgeDugmesi("metin", 0xFFFFFFFF, boy = B, cift = git("metin", "yazi")) { yaziEkle() }
                                if (hareketliEkle != null) SimgeDugmesi("hareketli_yazi", 0xFFFF9F43, boy = B, cift = git("metin", "yazi")) { hareketliEkle() }
                                // 3.69: Giriş yazısı — dokun = aç/kapat, çift dokun = Görünüm › Şablon › Giriş
                                SimgeDugmesi("baslik", if (p.giris.acik) 0xFF000000 else 0xFFD9CF5A, if (p.giris.acik) Color(0xFFD9CF5A) else Color(0xB3000000), boy = B,
                                    cift = { vm.sablonSekme.value = "giris"
                                        if (Duzen.hazirEski) { AltHafiza.sec("sablon", "giris"); bolumAc?.invoke("gorunum", "sablon") }
                                        else { AltHafiza.sec("hazirlar", "girisb"); bolumAc?.invoke("hazirb", "hazirlar") } }) {   // 4.48
                                    val ac = !p.giris.acik
                                    vm.girisDegis { it.copy(acik = ac) }
                                    vm.mesaj.value = if (ac) "Giriş yazısı açıldı · çift dokun = ayarlar" else "Giriş yazısı kapandı"
                                }
                                KartOynat(vm, i, Modifier)
                            }
                        }
                    }
                } else if (!kucuk) {
                    Text(sureOndalik(k.sureMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC0E151B)).padding(horizontal = 6.dp, vertical = 1.dp))
                    if (sessiz) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Color(0xB3000000)), contentAlignment = Alignment.Center) { Simge("sessiz", Color(0xFF5DCAA5), 12.dp) }
                }
                // 4.07: küçük şeritte (Yüzler vb. sayfalar) seçili klipte ✕ — 1. dokunuş kırmızı "Sil?", 2. dokunuş siler
                if (kucuk && secili) {
                    val onay = silOnayId == k.id
                    LaunchedEffect(onay) { if (onay) { kotlinx.coroutines.delay(3000); if (silOnayId == k.id) silOnayId = null } }
                    Row(Modifier.align(Alignment.TopEnd).padding(3.dp).height(20.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (onay) Renk.kirmizi else Color(0xB3000000)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                        .clickable { if (onay) { silOnayId = null; vm.klipSil(); vm.mesaj.value = "Klip silindi · geri al ile geri gelir" } else silOnayId = k.id }
                        .padding(horizontal = if (onay) 6.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Simge("kapat", Color.White, 12.dp)
                        if (onay) Text("Sil?", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                // 3.89 (Ahmet'in isteği): hareket düğmesi her klip resminde (hareket kapalı olsa da) — dokun = klibi seç + yön menüsü
                if (!(secili && !kucuk)) {
                    var yonMenuK by remember(k.id) { mutableStateOf(false) }
                    val bk = if (kucuk) 22.dp else 26.dp
                    Box(Modifier.align(Alignment.BottomStart).padding(4.dp).size(bk).clip(CircleShape).background(Color(0xB3000000))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape).clickable { vm.sec(i); yonMenuK = true }, contentAlignment = Alignment.Center) {
                        Simge(hareketSimgesi(k), Color(0xFFFFD54F), if (kucuk) 13.dp else 15.dp)
                        if (yonMenuK) HareketYonMenusu(k, kapat = { yonMenuK = false }, ayarlar = { yonMenuK = false; hareketId = k.id },
                            yuzTuru = { yonMenuK = false; AltHafiza.sec("yuz", "yuzler"); bolumAc?.invoke("nesne", "yuz") }) { yeni -> yonMenuK = false; hareketSec(vm, k, yeni) }
                    }
                }
            }
            // klipler arasındaki geçiş simgesi: dokun = Geçişler paneli (bu ara)
            if (i < p.klipler.lastIndex) {
                val tur = p.gecisAt(i).tur
                val ozel = k.gecis != null
                Box(Modifier.padding(start = 8.dp).size(if (kucuk) 24.dp else 30.dp).clip(CircleShape)
                    .background(if (tur == "yok") Renk.kart2 else Renk.turuncu.copy(alpha = if (ozel) 0.95f else 0.55f))
                    .border(1.dp, if (ozel) Color.White else Color.Transparent, CircleShape)
                    .clickable { gecisSecim = i },
                    contentAlignment = Alignment.Center) {
                    Simge(if (tur == "yok") "kapat" else "gecis", if (tur == "yok") Renk.soluk else Renk.turuncuM, if (kucuk) 14.dp else 17.dp)
                }
            }
            }
        }
        item {
            Box(
                Modifier.width(if (kucuk) 70.dp else 96.dp).fillMaxHeight(if (kucuk) 1f else 0.6f).clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Renk.turuncu, RoundedCornerShape(14.dp)).clickable { ekle() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("ekle", Renk.turuncu, if (kucuk) 20.dp else 26.dp)
                    if (!kucuk) Text("Ekle", color = Renk.turuncu, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        // 3.81: Google Fotoğraflar albüm bağlantısından ekle
        if (!kucuk) item {
            Box(Modifier.width(96.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(14.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(14.dp)).clickable { albumAcik = true },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("baglanti", Renk.yazi, 24.dp)
                    Text("Albüm", color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Text("Google Fotoğraflar", color = Renk.soluk, fontSize = 9.5.sp, maxLines = 1)
                }
            }
        }
        // 3.93: kamerayla çek (prompter'lı)
        if (!kucuk) item {
            Box(Modifier.width(96.dp).fillMaxHeight(0.6f).clip(RoundedCornerShape(14.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(14.dp)).clickable { kameraAcik = true },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("klip", Renk.yazi, 24.dp)
                    Text("Kamera", color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Text("prompter ile çek", color = Renk.soluk, fontSize = 9.5.sp, maxLines = 1)
                }
            }
        }
    }
    if (albumAcik) AlbumPenceresi(vm) { albumAcik = false }
    if (kameraAcik) KameraPrompter(vm) { kameraAcik = false }
    val ek = buildList {
        p.muzik?.let { add("♪ ${it.ad}") }
        if (p.sesler.isNotEmpty()) add("🎙 ${p.sesler.size} seslendirme")
        if (p.logo != null) add("◎ logo")
        if (p.katmanlar.isNotEmpty()) add("▣ ${p.katmanlar.size} katman")
    }
    if (false && ek.isNotEmpty() && !kucuk) Text(ek.joinToString("   "), color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp))
}

/** Büyük şeridin altında seçili klibin süresi (fotoğraf) ya da kırpma aralığı (video) — Geçişler biçiminde, tek satır. */
/** Geçişler paneli biçiminde bölüm kartı: koyu yuvarlak zemin, renk tonlu başlık. */
@Composable
private fun BolumKarti(baslik: String, ton: Color, sag: String = "", icerik: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(Renk.kart).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(16.dp).background(ton))
            Spacer(Modifier.width(8.dp))
            Text(baslik, color = ton, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (sag.isNotEmpty()) Text(sag, color = Renk.soluk, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 190.dp))
        }
        icerik()
    }
}

/** 3.53: Biçim satırı (YouTube · Reels · Kare · Boş proje) — Görünüm › Şablon › Hazır paketler'in ilk satırı. Önce Düzenle › Klip'teydi. */
@Composable
fun BicimSatiri(vm: EditorDurum, p: Proje) {
    val sari = Color(TON[0]); val yesil = Color(TON[1]); val turkuaz = Color(TON[2]); val pembe = Color(TON[4])
    val bosProje = vm.bosProjeAc
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(Triple("16:9", "YouTube", sari), Triple("9:16", "Reels", turkuaz), Triple("1:1", "Kare", yesil)).forEach { (b, ad, ton) ->
                val on = p.bicim == b
                val (w, h) = b.split(":").map { it.toFloat() }
                val olc = 26f / maxOf(w, h)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.bicim(b) }.padding(vertical = 2.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(if (on) Color.Transparent else Renk.kart2)
                        .border(if (on) 2.5.dp else 0.dp, if (on) Renk.yazi else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size((w * olc).dp, (h * olc).dp).border(2.dp, ton, RoundedCornerShape(3.dp)))
                    }
                    Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(b, color = ton.copy(alpha = 0.8f), fontSize = 9.5.sp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp).clip(RoundedCornerShape(12.dp)).clickable { bosProje("16:9") }.padding(vertical = 2.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(Renk.kart2).border(1.dp, pembe.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Simge("ekle", pembe, 24.dp)
                }
                Text("Boş proje", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                Text("YouTube · ayarsız", color = pembe.copy(alpha = 0.8f), fontSize = 9.5.sp, maxLines = 1)
            }
        }
}

@Composable
private fun KlipSureKirp(vm: EditorDurum, k: Klip) {
    Column(Modifier.fillMaxWidth()) {
        if (k.foto) {
            TonKaydirici("Süre", k.fotoSureMs / 1000f, 1f..15f, 0.5f, Renk.turuncu, { String.format(TR, "%.1f sn", it) }) { v ->
                vm.klipGuncelle(true) { it.copy(fotoSureMs = (v * 1000).toLong()) }
            }
        } else {
            var aralik by remember(k.id, k.basMs, k.sonMs) { mutableStateOf(k.basMs.toFloat()..k.sonMs.toFloat()) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Kırp", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.width(64.dp))
                RangeSlider(
                    value = aralik, onValueChange = { aralik = it },
                    valueRange = 0f..k.kaynakSureMs.coerceAtLeast(1).toFloat(),
                    onValueChangeFinished = { vm.kirp(aralik.start.toLong(), aralik.endInclusive.toLong()) },
                    colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
                    modifier = Modifier.weight(1f),
                )
                Text(sureOndalik((aralik.endInclusive - aralik.start).toLong()), color = Renk.turuncu, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
            }
            Text("${sureOndalik(aralik.start.toLong())} – ${sureOndalik(aralik.endInclusive.toLong())}", color = Renk.soluk, fontSize = 11.sp,
                modifier = Modifier.padding(start = 64.dp))
        }
        // 3.76: oynatıcı çizgisinden kes / böl (InShot'taki Kırp · Böl gibi). Çizgi kaydırıcıyla klip içinde taşınır.
        val konum by vm.konum.collectAsState()
        val pp by vm.proje.collectAsState()
        val i = pp.klipler.indexOfFirst { it.id == k.id }
        if (i >= 0) {
            val st = vm.baslangic(i); val yerel = (konum - st).coerceIn(0L, k.sureMs)
            val icinde = konum - st in 250..(k.sureMs - 250)
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Renk.cizgi))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Çizgi", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.width(64.dp))
                InceSlider(yerel.toFloat(), { v -> if (vm.secili.value != i) vm.sec(i); vm.git(st + v.toLong()) }, Modifier.weight(1f), valueRange = 0f..k.sureMs.coerceAtLeast(1).toFloat())
                Text(sureOndalik(yerel), color = Renk.yazi, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(60.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                fun hazir(f: () -> Unit) { if (vm.secili.value != i) vm.sec(i); f() }
                SimgeDugme("basa", "Başı kes", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = icinde) { hazir { vm.klipUcKes(bas = true) } }
                SimgeDugme("makas", "Böl", Modifier.weight(1f), renk = tonRenk(1, ""), etkin = icinde) { hazir { vm.klipBolSecili() } }
                SimgeDugme("sonraki", "Sonu kes", Modifier.weight(1f), renk = tonRenk(2, ""), etkin = icinde) { hazir { vm.klipUcKes(bas = false) } }
            }
            Text(if (icinde) "Başı kes: çizgiden öncesi gider · Sonu kes: çizgiden sonrası gider · Böl: çizgiden ikiye ayırır."
                else "Kesmek ya da bölmek için çizgiyi klibin içine getir.", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Klip kartının üstündeki renkli küçük simge düğmesi (3.25). */
@Composable
private fun SimgeDugmesi(ad: String, renk: Long, zemin: Color = Color(0xB3000000), boy: androidx.compose.ui.unit.Dp = 28.dp, cift: (() -> Unit)? = null, tik: () -> Unit) {
    // 3.69: çift dokunuş = ilgili bölüme git (tek dokunuş hızlı işlem)
    val tikG by rememberUpdatedState(tik); val ciftG by rememberUpdatedState(cift)
    // 3.75: büyük kartta (boy > 28) ince açık kenar — koyu fotoğrafta da belirgin
    Box(Modifier.size(boy).clip(CircleShape).background(zemin).then(if (boy > 28.dp) Modifier.border(1.dp, Color(0x40FFFFFF), CircleShape) else Modifier).then(
        if (cift == null) Modifier.clickable { tikG() }
        else Modifier.pointerInput(ad) { detectTapGestures(onTap = { tikG() }, onDoubleTap = { ciftG?.invoke() ?: tikG() }) }),
        contentAlignment = Alignment.Center) { Simge(ad, Color(renk), if (boy > 28.dp) boy * 0.58f else 16.dp) }
}

/** 3.53: Kırp / Süre sayfası — Geçişler paneli biçiminde (ortada başlık, sağda Tamam). Önce Düzenle › Klip'teki "Kırp ve ekle" kartındaydı. */
@Composable
private fun KirpSecimi(vm: EditorDurum, p: Proje, k: Klip, kapat: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = kapat, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)).background(Renk.kart).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (k.foto) "Süre" else "Kırp", color = Renk.yazi.copy(alpha = 0.8f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Klip ${p.klipler.indexOfFirst { it.id == k.id } + 1}/${p.klipler.size} · ${if (k.foto) "Fotoğraf" else "Video"}", color = Renk.soluk, fontSize = 11.sp)
                }
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { kapat() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("uygula", Renk.yazi, 26.dp)
                    Text("Tamam", color = Renk.soluk, fontSize = 10.5.sp)
                }
            }
            KlipSureKirp(vm, k)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 3.58: Kadraj sayfası (Geçişler biçiminde): solda Tümüne, ortada başlık, sağda Tamam; Sığdır / Doldur yuvarlak simgeler.
 *  Önce Görünüm › Kadraj alt menüsündeydi. Önizlemede sürükle = kaydır, çift dokun = ortala (değişmedi). */
@Composable
private fun KadrajSecimi(vm: EditorDurum, p: Proje, k: Klip, kapat: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = kapat, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)).background(Renk.kart).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.hepsine({ it.copy(doldur = k.doldur) }); vm.mesaj.value = "Kadraj tüm kliplere uygulandı"; kapat() }.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("cift", Renk.yazi, 26.dp); Text("Tümüne", color = Renk.soluk, fontSize = 10.5.sp)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Kadraj", color = Renk.yazi.copy(alpha = 0.8f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("Klip ${p.klipler.indexOfFirst { it.id == k.id } + 1}/${p.klipler.size}", color = Renk.soluk, fontSize = 11.sp)
                }
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { kapat() }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("uygula", Renk.yazi, 26.dp); Text("Tamam", color = Renk.soluk, fontSize = 10.5.sp)
                }
            }
            KadrajIcerik(vm, k)
        }
    }
}

/** 3.67: klibin hareket yönü simgesi (kartın üstünde): sabit · yakınlaş · uzaklaş · sağa · sola · yukarı · aşağı · otomatik. */
private fun hareketSimgesi(k: Klip): String = if (k.turEtkin) "yuz" else if (!k.kenBurns) "h_sabit" else when (k.hareket) {   // 3.74: yüz turu
    "uzak" -> "h_uzak"; "sag" -> "h_sag"; "sol" -> "h_sol"; "yukari" -> "h_yukari"; "asagi" -> "h_asagi"
    else -> "h_yakin"   // yakın ve otomatik: ikisi de odağa doğru yaklaşır (3.68: sihir değneği yerine gerçek yön)
}
/** 3.89: yön menüsünden seçim (seçili klibe uygulanır; yüz turu açıksa kapanır, geri al ile döner). */
private fun hareketSec(vm: EditorDurum, k: Klip, yeni: String) {
    val turVardi = k.turEtkin
    vm.klipGuncelle(true) { kk ->
        val t = if (turVardi) kk.yuzTur?.copy(noktalar = emptyList()) else kk.yuzTur
        if (yeni == "sabit") kk.copy(kenBurns = false, yuzTur = t) else kk.copy(kenBurns = true, hareket = yeni, yuzTur = t)
    }
    vm.mesaj.value = "Hareket: " + (HAREKET_ADI[yeni] ?: "Otomatik") + if (turVardi) " · yüz turu kapandı (geri al ile döner)" else ""
}

/** 3.87: klip kartındaki hareket simgesinden açılan küçük yön menüsü (3×3: yönler okların yerinde, ortada Sabit). */
@Composable
private fun HareketYonMenusu(k: Klip, kapat: () -> Unit, ayarlar: () -> Unit, yuzTuru: () -> Unit, sec: (String) -> Unit) {
    val secili = if (k.turEtkin) "tur" else if (!k.kenBurns) "sabit" else k.hareket.ifEmpty { "otomatik" }
    androidx.compose.ui.window.Popup(alignment = Alignment.BottomCenter, offset = androidx.compose.ui.unit.IntOffset(0, -120), onDismissRequest = kapat,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)) {
        Column(Modifier.width(222.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF0111417)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(18.dp)).padding(8.dp)) {
            Text("Hareket yönü", color = Color(0xFFFFD54F), fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
            val izgara = listOf(
                listOf("yakin" to "h_yakin", "yukari" to "h_yukari", "uzak" to "h_uzak"),
                listOf("sol" to "h_sol", "sabit" to "h_sabit", "sag" to "h_sag"),
                listOf("otomatik" to "sihir", "asagi" to "h_asagi", "ayar" to "hareket"),
            )
            izgara.forEach { satir ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    satir.forEach { (ad, simge) ->
                        val on = ad == secili
                        val r = if (on) Color(0xFF111417) else if (ad == "ayar") Color(0xFFB0B8BF) else Color(0xFFFFD54F)
                        Column(Modifier.width(66.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color(0xFFFFD54F) else Color.Transparent)
                            .clickable { if (ad == "ayar") ayarlar() else sec(ad) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Simge(simge, r, 22.dp)
                            Text(if (ad == "ayar") "Ayarlar" else HAREKET_ADI[ad] ?: "Otomatik", color = r, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
            if (k.foto) Row(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(if (secili == "tur") Color(0x33FFD54F) else Color.Transparent)
                .clickable(onClick = yuzTuru).padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Simge("yuz", Color(0xFFFFD54F), 20.dp); Spacer(Modifier.width(8.dp))
                Text(if (secili == "tur") "Yüz turu açık · ayarla" else "Yüz turu (yüzlere sırayla yaklaş)", color = Color(0xFFFFD54F), fontSize = 12.sp)
            }
        }
    }
}

private val HAREKET_ADI = mapOf("yakin" to "Yakınlaş", "uzak" to "Uzaklaş", "sag" to "Sağa", "sol" to "Sola", "yukari" to "Yukarı", "asagi" to "Aşağı", "sabit" to "Sabit")

/** 3.67: seçili klip kartındaki hareket simgesinden açılan Hareket sayfası (Görünüm › Hareket'in aynısı). */
@Composable
private fun HareketSecimi(vm: EditorDurum, k: Klip, kapat: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = kapat, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)).background(Renk.kart).heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
            PanelHareket(vm, k)
            Row(Modifier.fillMaxWidth().clickable { kapat() }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Simge("uygula", Renk.yazi, 20.dp); Spacer(Modifier.width(6.dp)); Text("Tamam", color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Kadraj seçimi (Sığdır / Doldur / Ortala) — klip kartındaki pencere ile Düzenle › Klip › Kadraj ortak. */
@Composable
private fun KadrajIcerik(vm: EditorDurum, k: Klip) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
        YuvarlakSecim("Sığdır · tamamı görünür", "tuval", Color(TON[2]), !k.doldur) { vm.klipGuncelle { it.copy(doldur = false) } }
        YuvarlakSecim("Doldur · kare dolar", "kadraj", Color(TON[1]), k.doldur) { vm.klipGuncelle { it.copy(doldur = true) } }
        if (k.kayX != 0f || k.kayY != 0f) YuvarlakSecim("Ortala", "h_odak", Color(TON[3]), false) { vm.klipGuncelle { it.copy(kayX = 0f, kayY = 0f) } }
    }
    Text(if (k.doldur) "Önizlemede parmakla sürükle = kaydır · çift dokun = ortala" else "Kaydırmak için Doldur'u seç",
        color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

/** 3.70: 3. katman çipindeki ✓ — o alt sayfada bir ayar/içerik var mı. */
private fun altDegisti(p: Proje, k: Klip?, hap: String, a: String): Boolean = when (hap) {
    "hazirlar" -> when (a) { "girisb" -> p.giris.acik; "acilis" -> p.giris.nokta || p.giris.logoBuyu; else -> false }   // 4.48
    "sablon" -> when (a) { "giris" -> p.giris.acik || p.giris.nokta || p.giris.logoBuyu; "ozet" -> p.ozet.acik; "abone" -> p.hatirlatici.acik; "kapanis" -> p.kapanis.acik; else -> false }
    "klip" -> k != null && when (a) { "hiz" -> k.hiz != 1f; "kadraj" -> k.doldur || k.kayX != 0f || k.kayY != 0f
        "kirp" -> !k.foto && (k.basMs > 0 || k.sonMs < k.kaynakSureMs); "yuzler" -> k.turEtkin; else -> false }
    "yuz" -> k != null && a == "yuzler" && k.turEtkin   // 4.41
    "hareket" -> k != null && k.kenBurns && when (a) { "yakin" -> k.hareket in setOf("otomatik", "yakin", "uzak", ""); "kay" -> k.hareket in setOf("sag", "sol", "yukari", "asagi")
        "odak" -> k.odakSecX >= 0f; else -> false }
    "muzik" -> p.muzik != null && when (a) { "hazir" -> p.muzik.ad.startsWith("Hazır müzik"); "dosya" -> !p.muzik.ad.startsWith("Hazır müzik"); "ayar" -> p.muzik.girisMs > 0 || p.muzik.cikisMs > 0; else -> false }
    "seslendirme" -> a == "kayitlar" && p.sesler.isNotEmpty()
    "yazi" -> a == "liste" && p.katmanlar.any { it.tur == "metin" }
    "altyazi" -> a == "satir" && p.altyazilar.isNotEmpty()
    "kapak" -> when (a) { "kare" -> p.kapak.kaynakUri.isNotEmpty(); "yazi" -> p.kapak.yazi.isNotBlank(); "efekt" -> p.kapak.canli || p.kapak.logo; else -> false }
    "nesne", "knesne", "khareket" -> a == "kesilen" && p.nesneler.isNotEmpty()
    else -> false
}

/** Klipler arasındaki geçiş simgesinden açılan Geçişler paneli (3.25, Ahmet'in görseli). */
@Composable
internal fun GecisSecimi(vm: EditorDurum, p: Proje, i: Int, kapat: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = kapat, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(18.dp)).background(Renk.kart).padding(vertical = 8.dp)) {
            GecisIcerik(vm, p, ara = i, kapat = kapat)
        }
    }
}

@Composable
private fun Panel(vm: EditorDurum, p: Proje, sec: Int, bolum: String, hap: String, ekle: () -> Unit, alt: String? = null) {
    val k = p.klipler.getOrNull(sec)
    when (hap) {
        "ekle" -> PanelEkle(ekle)
        "bicim" -> PanelBicim(vm, p)
        "muzik" -> PanelMuzik(vm, p, alt)
        "seslendirme" -> PanelSeslendirme(vm, p, alt)
        "logo" -> PanelLogoKutup(vm, p)
        "hazirlar" -> PanelHazir(vm, p, alt)   // 4.46 · 4.47: Paketler / Hazır ayarlar alt menünün yanındaki düğmelerden
        "sablon", "hazir" -> PanelSablon(vm, p, alt)   // 3.70: Görünüm'ün alt menüleri aynı sayfanın bölümleri
        "paket" -> PanelSablon(vm, p, if (alt == null) null else "paket")   // 3.71: Paket tek sayfa (Biçim + Paketler)
        "gecis" -> PanelGecis(vm, p)
        "filtre" -> PanelFiltre(vm, p, sec)   // 3.59
        "ayarlar" -> PanelAyarlar(vm, p, sec)   // 3.61
        "efekt" -> PanelEfekt(vm, p, sec)     // 3.59
        "altyazi" -> PanelAltyazi(vm, p, alt)
        "kapak" -> PanelKapak(vm, p, alt)
        "nesne" -> PanelNesne(vm, p, alt)
        "foto", "video", "kesilen" -> PanelNesne(vm, p, hap)   // 4.29: Nesne ana menüsü
        "knesne" -> PanelNesne(vm, p, alt ?: "foto")
        "khareket" -> PanelNesne(vm, p, "video")   // 4.16: tek sayfa (Kesilenler yalnız Nesne kes'te — tekrar yok)   // 4.13: Düzenle › Nesne'nin kendisi
        "katmanlar" -> PanelKatmanlar(vm, p)
        else -> if (k == null) {
            Text("Önce bir klip ekle ya da seç.", color = Renk.soluk, modifier = Modifier.padding(16.dp))
        } else when (hap) {
            "klip" -> when (alt) {   // 3.70: Düzenle › Klip › Kırp · Kadraj · Hız (Klipler = şerit görünümü)
                // 4.05: ✓✓ tümüne · ✓ yalnız bu klip
                "kirp" -> Kart(if (k.foto) "Süre · klip ${sec + 1}" else "Kırp · klip ${sec + 1}",
                    hepsine = if (k.foto) ({ val ms = k.fotoSureMs; vm.hepsine({ if (it.foto) it.copy(fotoSureMs = ms) else it }, yapi = true); vm.mesaj.value = "Süre bütün fotoğraflara uygulandı" }) else null,
                    buna = { vm.mesaj.value = "Yalnız bu klibe uygulandı" }) { KlipSureKirp(vm, k) }
                "kadraj" -> Kart("Kadraj · klip ${sec + 1}", hepsine = { vm.hepsine({ it.copy(doldur = k.doldur) }); vm.mesaj.value = "Tüm kliplere uygulandı" },
                    buna = { vm.mesaj.value = "Yalnız bu klibe uygulandı" }) { KadrajIcerik(vm, k) }
                "hiz" -> HizSayfasi(vm, k)
                "yuzler", "gizle", "ayri" -> LaunchedEffect(alt) { AltHafiza.sec("klip", "klipler"); vm.gitIstek.value = Triple("nesne", "yuz", alt) }   // 4.41: Nesne › Yüzler'e yönlendir
                else -> PanelKlip(vm, p, sec, k)
            }
            "yuz" -> when (alt) {   // 4.41: Nesne › Yüzler
                "gizle" -> YuzGizleSayfasi(vm, sec, k)
                "ayri" -> YuzAyriVideoSayfasi(vm, p, sec, k)
                else -> YuzTuruSayfasi(vm, p, sec, k)
            }
            "kadraj" -> PanelKadraj(vm, k)
            "hareket" -> PanelHareket(vm, k, alt)
            "renk" -> PanelRenk(vm, k)
            "klipses" -> PanelKlipSes(vm, k)
            "yazi" -> YaziSayfasi(vm, p, sec, alt)   // 3.53: Katman › Yazı ekle ile ortak
            "takip" -> PanelTakip(vm, k)
            else -> Text("Bölüm: $bolum", color = Renk.soluk)
        }
    }
}

@Composable
private fun PanelKlip(vm: EditorDurum, p: Proje, sec: Int, k: Klip) {
    Kart("Klip ${sec + 1}/${p.klipler.size}") {
        Text(k.ad, color = Renk.yazi, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val bilgi = buildList {
            if (k.gen > 0) add("${k.gen}×${k.yuk}")
            if (k.kodek.isNotEmpty()) add(k.kodek)
            if (k.hdr) add("HDR → SDR")
            if (k.boyutBayt > 0) add(Medya.mb(k.boyutBayt))
        }.joinToString(" · ")
        if (bilgi.isNotEmpty()) Text(bilgi, color = Renk.soluk, fontSize = 12.sp)
        if (!k.foto) {
            var aralik by remember(k.id, k.basMs, k.sonMs) { mutableStateOf(k.basMs.toFloat()..k.sonMs.toFloat()) }
            Spacer(Modifier.height(8.dp))
            Text("Kırp", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
            RangeSlider(
                value = aralik,
                onValueChange = { aralik = it },
                valueRange = 0f..k.kaynakSureMs.coerceAtLeast(1).toFloat(),
                onValueChangeFinished = { vm.kirp(aralik.start.toLong(), aralik.endInclusive.toLong()) },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
                startThumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(Renk.turuncu)) },
                endThumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(Renk.turuncu)) },
                track = { st ->
                    val span = (st.valueRange.endInclusive - st.valueRange.start).takeIf { it > 0f } ?: 1f
                    val a = ((st.activeRangeStart - st.valueRange.start) / span).coerceIn(0f, 1f)
                    val b = ((st.activeRangeEnd - st.valueRange.start) / span).coerceIn(0f, 1f)
                    val pasif = Renk.cizgi; val aktif = Renk.turuncu
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                        val y = size.height / 2
                        drawLine(pasif, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), size.height)
                        drawLine(aktif, androidx.compose.ui.geometry.Offset(size.width * a, y), androidx.compose.ui.geometry.Offset(size.width * b, y), size.height)
                    }
                },
            )
            Etiketli("Başlangıç · Bitiş", "${sureOndalik(aralik.start.toLong())} · ${sureOndalik(aralik.endInclusive.toLong())}")
            Etiketli("Süre", sureOndalik((aralik.endInclusive - aralik.start).toLong()))
        } else {
            var sn by remember(k.id, k.fotoSureMs) { mutableFloatStateOf(k.fotoSureMs / 1000f) }
            Spacer(Modifier.height(8.dp))
            Etiketli("Fotoğraf süresi", String.format(TR, "%.1f sn", sn))
            InceSlider(
                value = sn, onValueChange = { sn = it }, valueRange = 1f..15f,
                onValueChangeFinished = { vm.klipGuncelle(true) { it.copy(fotoSureMs = (sn * 1000).toLong()) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
            )
        }
        AcilirNot("Sil, böl, çoğalt ve ses: şeritte seçili klibin üstünde. Taşımak için oklara dokun ya da klibe basılı tutup sürükle.")
    }
}

@Composable
private fun PanelEkle(ekle: () -> Unit) {
    Kart("Klip ekle") {
        Dugme("＋ Video veya fotoğraf seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { ekle() }
        AcilirNot("Seçilen klipler seçili klibin arkasına eklenir. Videolar kopyalanmaz, telefondaki yerinden okunur; boyut sınırı yoktur (1 GB, 2 GB…). " +
            "Telefonun çözemediği biçimler eklenmez ve nedeni yazılır.")
    }
}

@Composable
private fun PanelBicim(vm: EditorDurum, p: Proje) {
    Kart("Video biçimi") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecimKarti("16:9", "YouTube\nyatay", p.bicim == "16:9", Modifier.weight(1f), sira = 0) { vm.bicim("16:9") }
            SecimKarti("9:16", "Shorts\ndikey", p.bicim == "9:16", Modifier.weight(1f), sira = 2) { vm.bicim("9:16") }
            SecimKarti("1:1", "Kare\n", p.bicim == "1:1", Modifier.weight(1f), sira = 3) { vm.bicim("1:1") }
        }
    }
}

@Composable
private fun PanelKadraj(vm: EditorDurum, k: Klip) {
    Kart("Kadraj", hepsine = { vm.hepsine({ it.copy(doldur = k.doldur) }); vm.mesaj.value = "Tüm kliplere uygulandı" },
        buna = { vm.mesaj.value = "Yalnız bu klibe uygulandı" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecimKarti("Sığdır", "Tamamı görünür,\nkenarlar siyah kalabilir", !k.doldur, Modifier.weight(1f)) { vm.klipGuncelle { it.copy(doldur = false) } }
            SecimKarti("Doldur", "Kare dolar,\ntaşan kısım kesilir", k.doldur, Modifier.weight(1f)) { vm.klipGuncelle { it.copy(doldur = true) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (k.doldur) "Önizlemede parmakla sürükleyerek kaydır · çift dokun = ortala" else "Kaydırmak için Doldur'u seç",
                color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (k.kayX != 0f || k.kayY != 0f) TextButton(onClick = { vm.klipGuncelle { it.copy(kayX = 0f, kayY = 0f) } }) { Text("Ortala", color = Renk.turuncu) }
        }
    }
}

/** Hareket türleri (3.35): (anahtar, ad, simge). Geçişler paneli biçiminde yuvarlak simgeler, TON renk ailesi. */
private val HAREKET_YAKIN = listOf(Triple("otomatik", "Otomatik", "sihir"), Triple("yakin", "Yakınlaş", "h_yakin"),
    Triple("uzak", "Uzaklaş", "h_uzak"), Triple("sabit", "Sabit", "h_sabit"))
private val HAREKET_KAY = listOf(Triple("sag", "Sağa", "h_sag"), Triple("sol", "Sola", "h_sol"),
    Triple("yukari", "Yukarı", "h_yukari"), Triple("asagi", "Aşağı", "h_asagi"))

/** Hareket paneli (3.35, Geçişler paneli biçiminde): solda "Tümüne", ortada başlık; YAKINLAŞMA ve KAYDIRMA türleri;
 *  ODAK: yaklaşmanın başladığı nokta — kendisi bulur (yüz → ana nesne → orta), önizlemede dokunarak değiştirilir. */
@Composable
private fun PanelHareket(vm: EditorDurum, k: Klip, alt: String? = null) {
    val secili = if (!k.kenBurns) "sabit" else k.hareket.ifEmpty { "otomatik" }
    fun sec(t: String) = vm.klipGuncelle { if (t == "sabit") it.copy(kenBurns = false) else it.copy(kenBurns = true, hareket = t) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.kart).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    vm.hepsine({ it.copy(kenBurns = k.kenBurns, hareket = k.hareket) }); vm.mesaj.value = "Hareket tüm kliplere uygulandı"
                }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Simge("cift", Renk.yazi, 26.dp)
                Text("Tümüne", color = Renk.soluk, fontSize = 10.5.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hareket", color = Renk.yazi.copy(alpha = 0.8f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(if (k.foto) "Fotoğraf" else "Video", color = Renk.soluk, fontSize = 11.sp)
            }
            Spacer(Modifier.width(48.dp))
        }
        listOf("YAKINLAŞMA" to HAREKET_YAKIN, "KAYDIRMA" to HAREKET_KAY).forEachIndexed { b, (baslik, liste) ->
            // 3.70: alt sayfa — Yakınlaştırma / Kaydırma / Odak (eski düzende hepsi bir arada)
            if (alt != null && !(alt == "yakin" && b == 0 || alt == "kay" && b == 1)) return@forEachIndexed
            Text(baslik, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                liste.forEachIndexed { n, (anah, ad, simge) ->
                    val on = secili == anah
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { sec(anah) }.padding(vertical = 2.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(if (on) Color.Transparent else Renk.kart2)
                            .border(if (on) 2.5.dp else 0.dp, if (on) Renk.yazi else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) { Simge(simge, Color(TON[(b * 4 + n) % TON.size]), 26.dp) }
                        Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (alt != "odak") Text(when (secili) {
            "otomatik" -> if (k.foto) "Odak noktasına doğru yavaş yaklaşır; görüntü kaymaz (ton değişmez)." else "Saniyede %1,5 yavaş yaklaşır."
            "yakin" -> "Odak noktasına doğru yaklaşır."
            "uzak" -> "Odak noktasından geriye, tam kadraja uzaklaşır."
            "sabit" -> "Hareket yok; kadraj sabit kalır."
            else -> "Görüntü seçilen yöne kayar (en çok karenin yarısı). Gökyüzü/yer gibi farklı bölgeler girerse ton değişebilir."
        }, color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        if (alt == "odak" && !(secili == "otomatik" || secili == "yakin" || secili == "uzak"))
            AcilirNot("Odak noktası yakınlaşma türlerinde kullanılır (Otomatik, Yakınlaş, Uzaklaş). Önce Yakınlaştırma sayfasından birini seç.")
        if ((alt == null || alt == "odak") && (secili == "otomatik" || secili == "yakin" || secili == "uzak")) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Renk.cizgi))
            Text("ODAK", color = Renk.soluk.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Simge("h_odak", Color(TON[3]), 24.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(Yuz.odakKaynak(k).replaceFirstChar { it.uppercase() }, color = Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text("Önizlemede fotoğrafa dokun: yaklaşma oradan başlar.", color = Renk.soluk, fontSize = 11.5.sp)
                }
                if (k.odakSecX >= 0f) Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        vm.klipGuncelle { it.copy(odakSecX = -1f, odakSecY = -1f) }; vm.mesaj.value = "Odak otomatik"
                    }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("sihir", Color(TON[2]), 22.dp)
                    Text("Otomatik", color = Renk.soluk, fontSize = 10.5.sp)
                }
            }
            if (k.foto && k.yuzB > 0f && k.odakSecX < 0f)
                AnahtarSatiri("Yüze odaklan", k.yuzOdak) { v -> vm.klipGuncelle { it.copy(yuzOdak = v) } }
        }
    }
}

@Composable
private fun PanelKlipSes(vm: EditorDurum, k: Klip) {
    val sesli = !k.foto && k.sesVar
    Kart("Klip sesi", hepsine = if (!sesli) null else ({ vm.hepsine({ it.copy(ses = k.ses, sessiz = k.sessiz) }); vm.mesaj.value = "Tüm kliplere uygulandı" }),
        buna = if (!sesli) null else ({ vm.mesaj.value = "Yalnız bu klibe uygulandı" })) {
        if (k.foto || !k.sesVar) {
            Text(if (k.foto) "Fotoğraf kliplerinde ses yok." else "Bu videoda ses izi yok (ya da telefon çözemiyor).", color = Renk.soluk, fontSize = 13.sp)
        } else {
            AnahtarSatiri("Sessiz", k.sessiz) { v -> vm.klipGuncelle { it.copy(sessiz = v) } }
            var s by remember(k.id, k.ses) { mutableFloatStateOf(k.ses) }
            Etiketli("Ses düzeyi", "%${(s * 100).roundToInt()}")
            InceSlider(
                value = s, onValueChange = { s = it }, valueRange = 0f..1.5f, enabled = !k.sessiz,
                onValueChangeFinished = { vm.klipGuncelle { it.copy(ses = s) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
            )
            AcilirNot("%100 üstü yalnız dışa aktarmada yükseltilir; önizleme en fazla %100 çalar.")
        }
    }
}

@Composable
private fun PanelMuzik(vm: EditorDurum, p: Proje, alt: String? = null) {
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.muzikSec(it) } }
    // 3.72: mevcut müziği değiştirmeden/kaldırmadan yeni müzik — ses katmanı olarak oynatıcının olduğu andan eklenir (birden fazla olabilir)
    val ekleSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.katmanSesEkle(it); vm.mesaj.value = "Müzik katman olarak eklendi · Katman bölümünde" } }
    // 3.61: yerleşik müzikler (uygulamada üretilir, telifsiz; silip kursan da vardır) + konuya göre otomatik
    val uretiyor by vm.muzikUretiliyor.collectAsState()
    if (alt == null || alt == "hazir") Kart("Hazır müzikler") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Renk.turuncu.copy(alpha = 0.14f)).border(1.dp, Renk.turuncu.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable(enabled = !uretiyor && p.klipler.isNotEmpty()) { vm.otomatikMuzik() }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Simge("sihir", Renk.turuncu, 18.dp); Spacer(Modifier.width(6.dp))
            Text(if (uretiyor) "Müzik hazırlanıyor…" else "Konuya göre otomatik seç", color = Renk.turuncu, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MuzikSentez.TURLER.forEach { t ->
                YuvarlakSecim(t.ad, t.simge, Color(t.renk), p.muzik?.ad == "Hazır müzik · ${t.ad}") { if (!uretiyor) vm.hazirMuzik(t.anahtar) }
            }
        }
        AcilirNot("Uygulamanın kendi ürettiği telifsiz müzikler; video süresi kadar hazırlanır. Otomatik seçim kliplere bakar: doğa → Huzur, insan → Neşeli, çay/sofra → Duygusal, yol → Tempolu, yapı → Klasik.")
    }
    if (alt == null || alt == "dosya" || alt == "ayar") Kart(if (alt == "ayar") "Müzik ayarı" else "Arka plan müziği") {
        val m = p.muzik
        if (m == null) {
            Dugme("♪ Müzik seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { sec.launch(arrayOf("audio/*")) }
            Text(if (alt == "ayar") "Önce müzik seç (Hazır ya da Dosyadan)." else "Telefondaki bir ses dosyasını seç (MP3, M4A, WAV…).", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text(m.ad, color = Renk.yazi, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.sureMs > 0) Text("Süre ${sureYaz(m.sureMs)}", color = Renk.soluk, fontSize = 12.sp)
            if (alt == "dosya") { DugmeSirasi("Yeni ekle" to { ekleSec.launch(arrayOf("audio/*")) }, "Değiştir" to { sec.launch(arrayOf("audio/*")) }, "Kaldır" to { vm.muzikKaldir() }); return@Kart }
            var s by remember(m.uri, m.ses) { mutableFloatStateOf(m.ses) }
            Etiketli("Müzik sesi", "%${(s * 100).roundToInt()}")
            InceSlider(value = s, onValueChange = { s = it }, valueRange = 0f..1f,
                onValueChangeFinished = { vm.muzikDegis { it.copy(ses = s) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
            AnahtarSatiri("Video boyunca tekrarla", m.dongu) { v -> vm.muzikDegis { it.copy(dongu = v) } }
            AnahtarSatiri("Videoda ses varken otomatik kıs", m.otoKis) { v -> vm.muzikDegis { it.copy(otoKis = v) } }
            // 3.70: yumuşak giriş / çıkış (başta yavaşça açılır, videonun sonunda yavaşça kısılır)
            TonKaydirici("Yumuşak giriş", m.girisMs / 1000f, 0f..6f, 0.5f, Renk.turuncu, { if (it < 0.05f) "yok" else String.format(TR, "%.1f sn", it) }) { v ->
                vm.muzikDegis { it.copy(girisMs = (v * 1000).toLong()) } }
            TonKaydirici("Yumuşak çıkış", m.cikisMs / 1000f, 0f..8f, 0.5f, Renk.turuncu, { if (it < 0.05f) "yok" else String.format(TR, "%.1f sn", it) }) { v ->
                vm.muzikDegis { it.copy(cikisMs = (v * 1000).toLong()) } }
            if (alt == null) DugmeSirasi("Yeni ekle" to { ekleSec.launch(arrayOf("audio/*")) }, "Değiştir" to { sec.launch(arrayOf("audio/*")) }, "Kaldır" to { vm.muzikKaldir() })
        }
    }
}

@Composable
private fun PanelSeslendirme(vm: EditorDurum, p: Proje, alt: String? = null) {
    val ctx = LocalContext.current
    val kaydediyor by vm.kaydediyor.collectAsState()
    val konum by vm.konum.collectAsState()
    val bas by vm.kayitBas.collectAsState()
    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.kayitBaslat() else vm.uyari.value = "Seslendirme için mikrofon izni gerekiyor."
    }
    if (alt == null || alt == "kayit" || kaydediyor) Kart("Seslendirme") {
        if (!kaydediyor) {
            Dugme("● Kaydı başlat (${sureYaz(konum)} anından)", Modifier.fillMaxWidth(), renk = Renk.kirmizi, yaziRenk = Color.White, etkin = p.klipler.isNotEmpty()) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.kayitBaslat()
                else izin.launch(Manifest.permission.RECORD_AUDIO)
            }
            AcilirNot("Video o andan oynar, sen konuşursun. Kayıt sırasında klip sesi ve müzik susar.")
        } else {
            Dugme("■ Durdur · kayıt ${sureYaz((konum - bas).coerceAtLeast(0))}", Modifier.fillMaxWidth(), renk = Renk.kirmizi, yaziRenk = Color.White) { vm.kayitDurdur() }
        }
    }
    var prompter by remember { mutableStateOf(false) }
    var kameraP by remember { mutableStateOf(false) }
    if (alt == null || alt == "prompter") Kart("Kayan yazıdan oku (prompter)") {
        var m by remember(p.id) { mutableStateOf(p.okumaMetni) }
        OutlinedTextField(m, { m = it; vm.okumaDegis { pp -> pp.copy(okumaMetni = it) } }, Modifier.fillMaxWidth().heightIn(min = 110.dp),
            placeholder = { Text("Okuyacağın metni buraya yaz ya da yapıştır") }, maxLines = 8)
        GenisDugme("Tam ekran oku ve kaydet", "prompter", Modifier.padding(top = 8.dp), vurgulu = true) { vm.player.pause(); prompter = true }
        GenisDugme("Kamerayla çek (metin üstte akar)", "klip", Modifier.padding(top = 6.dp)) { vm.player.pause(); kameraP = true }
        AcilirNot("Metin kayarken okursun; sesin önizlemedeki andan itibaren seslendirme olarak eklenir. Hız ve yazı boyutu ekranda ayarlanır.")
    }
    // 4.02: sesli okuyucu — metni DFKI sesiyle okur, seslendirme olarak ekler (Okur / Karanlık Oda'daki ses)
    if (alt == null || alt == "okuyucu") OkuyucuKart(vm, p)
    if (prompter) Prompter(vm) { prompter = false }
    if (kameraP) KameraPrompter(vm) { kameraP = false }
    if (alt == "kayitlar" && p.sesler.isEmpty()) Text("Henüz seslendirme kaydı yok. Kayıt ya da Prompter sayfasından kaydet.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(8.dp))
    if ((alt == null || alt == "kayitlar") && p.sesler.isNotEmpty()) Kart("Kayıtlar (${p.sesler.size})") {
        p.sesler.forEach { s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Renk.kart2).padding(10.dp)) {
                Text("${sureYaz(s.basMs)} → ${sureYaz(s.basMs + s.sureMs)}  (${sureYaz(s.sureMs)})", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
                var v by remember(s.id, s.ses) { mutableFloatStateOf(s.ses) }
                Etiketli("Ses", "%${(v * 100).roundToInt()}")
                InceSlider(value = v, onValueChange = { v = it }, valueRange = 0f..1.5f,
                    onValueChangeFinished = { vm.sesDegis(s.id) { it.copy(ses = v) } },
                    colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
                // 3.90: ses efekti (hazır ayarlar; özgün kayıt korunur — Doğal ile geri dönülür)
                Text("Ses efekti", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SesEfekt.HAZIR.forEach { (k, ad) ->
                        val on = s.efekt == k
                        Text(ad, color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) Renk.turuncu else Renk.kart).border(1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(16.dp))
                            .clickable { vm.sesEfekt(s.id, k) }.padding(horizontal = 13.dp, vertical = 7.dp))
                    }
                }
                Text(SesEfekt.ACIKLAMA[s.efekt] ?: "", color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
                if (p.sesler.size > 1 && s.efekt != "dogal") Text("Bu efekti bütün kayıtlara uygula", color = Renk.turuncu, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.sesEfekt(null, s.efekt) }.padding(vertical = 4.dp))
                // 3.95: hız (ses tonu korunur) · sessizlik kırpma · yerini kaydır
                Text("Hız (ses tonu korunur)", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f).forEach { h ->
                        val on = kotlin.math.abs(s.hiz - h) < 0.01f
                        Text(if (h == 1f) "1×" else String.format(TR, "%.2f", h).trimEnd('0').trimEnd(',', '.') + "×",
                            color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) Renk.turuncu else Renk.kart).border(1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(16.dp))
                            .clickable { vm.sesIsle(s.id) { it.copy(hiz = h) } }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                AnahtarSatiri("Baştaki ve sondaki sessizliği kırp", s.kirp) { v -> vm.sesIsle(s.id) { it.copy(kirp = v) } }
                Text("Yeri: ${sureYaz(s.basMs)}", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimgeDugme("geri", "−0,5 sn", Modifier.weight(1f)) { vm.sesKaydir(s.id, -500) }
                    SimgeDugme("ileri", "+0,5 sn", Modifier.weight(1f)) { vm.sesKaydir(s.id, 500) }
                    SimgeDugme("takip", "Oynatıcının yerine", Modifier.weight(1.4f), renk = Renk.turuncu) { vm.sesKaydir(s.id, buraya = true) }
                }
                if (p.sesler.size > 1 && (kotlin.math.abs(s.hiz - 1f) > 0.01f || s.kirp)) Text("Hızı ve kırpmayı bütün kayıtlara uygula", color = Renk.turuncu, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { val h = s.hiz; val k = s.kirp; vm.sesIsle(null) { it.copy(hiz = h, kirp = k) } }.padding(vertical = 4.dp))
                var silOnay by remember(s.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dugme("▶ Dinle", Modifier.weight(1f)) { vm.git(s.basMs); vm.player.play() }
                    Dugme(if (silOnay) "Emin misin?" else "Sil", Modifier.weight(1f), renk = if (silOnay) Renk.kirmizi else Renk.kart) {
                        if (silOnay) vm.sesSil(s.id) else silOnay = true
                    }
                }
            }
        }
    }
}

@Composable
private fun OkuyucuKart(vm: EditorDurum, p: Proje) {
    val ctx = LocalContext.current
    val is_ by vm.okuIs.collectAsState()
    val ilerleme by vm.okuIlerleme.collectAsState()
    val pr = remember { ctx.getSharedPreferences("okuyucu", android.content.Context.MODE_PRIVATE) }
    var hazir by remember { mutableStateOf(Okuyucu.hazir(ctx)) }
    LaunchedEffect(is_) { if (is_ == null) hazir = Okuyucu.hazir(ctx); if (hazir && is_ == null) Okuyucu.isit(ctx) }
    DisposableEffect(Unit) { onDispose { if (vm.okuIs.value == "dinle") vm.okuyucuDurdur() } }
    var metin by remember(p.id) { mutableStateOf(p.okumaMetni) }
    var ton by remember { mutableFloatStateOf(pr.getFloat("ton", 0.94f)) }
    var hiz by remember { mutableFloatStateOf(pr.getFloat("hiz", 1f)) }
    var efekt by remember { mutableStateOf(pr.getString("efekt", "radyo") ?: "radyo") }
    @Composable fun Cip(ad: String, on: Boolean, tik: () -> Unit) = Text(ad, color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Renk.turuncu else Renk.kart)
            .border(1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(16.dp)).clickable(onClick = tik).padding(horizontal = 13.dp, vertical = 7.dp))
    Kart("Sesli okuyucu (metinden ses)") {
        Text("Ses: DFKI (Türkçe · erkek)", color = Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
        if (!hazir) {
            if (is_ == "indir") {
                Text("Ses indiriliyor… %${(ilerleme * 100).roundToInt()}", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                LinearProgressIndicator(progress = { ilerleme }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
                Dugme("Vazgeç", Modifier.fillMaxWidth()) { vm.okuyucuDurdur() }
            } else {
                Text("İlk kullanımda ses bir kez indirilir (~67 MB, GitHub). Sonra internetsiz, telefonda çalışır.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 6.dp))
                Dugme("Sesi indir", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.okuyucuIndir() }
            }
            return@Kart
        }
        OutlinedTextField(metin, { metin = it; vm.okumaDegis { pp -> pp.copy(okumaMetni = it) } }, Modifier.fillMaxWidth().heightIn(min = 110.dp).padding(top = 6.dp),
            placeholder = { Text("Okunacak metni yaz ya da yapıştır (Prompter metniyle ortak)") }, maxLines = 8)
        Text("Ses tonu", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Okuyucu.TONLAR.forEach { (t, ad) -> Cip(ad, kotlin.math.abs(ton - t) < 0.005f) { ton = t; pr.edit().putFloat("ton", t).apply() } }
        }
        Text("Okuma hızı", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f).forEach { h ->
                Cip(if (h == 1f) "1×" else String.format(TR, "%.2f", h).trimEnd('0').trimEnd(',', '.') + "×", kotlin.math.abs(hiz - h) < 0.01f) { hiz = h; pr.edit().putFloat("hiz", h).apply() }
            }
        }
        Text("Eklenirken uygulanacak efekt", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SesEfekt.HAZIR.forEach { (k, ad) -> Cip(ad, efekt == k) { efekt = k; pr.edit().putString("efekt", k).apply() } }
        }
        Text("Tok, profesyonel anlatıcı için: Ses tonu Tok + Radyo ya da Kalın.", color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
        when (is_) {
            "ekle" -> {
                Text("Okunuyor ve ekleniyor… %${(ilerleme * 100).roundToInt()}", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
                LinearProgressIndicator(progress = { ilerleme }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
                Dugme("Durdur", Modifier.fillMaxWidth()) { vm.okuyucuDurdur() }
            }
            "dinle" -> Dugme("■ Durdur", Modifier.fillMaxWidth().padding(top = 8.dp), renk = Renk.kirmizi, yaziRenk = Color.White) { vm.okuyucuDurdur() }
            else -> Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dugme("▶ Dinle", Modifier.weight(1f), etkin = metin.isNotBlank()) { vm.okuyucuDinle(metin, hiz, ton) }
                Dugme("Seslendirme olarak ekle", Modifier.weight(1.6f), renk = Renk.turuncu, yaziRenk = Renk.turuncuM, etkin = metin.isNotBlank() && p.klipler.isNotEmpty()) {
                    vm.okuyucuEkle(metin, hiz, ton, efekt) }
            }
        }
        AcilirNot("Metin telefonda sese çevrilir (Piper DFKI sesi, ücretsiz, internet gerekmez). Dinle cümle cümle hemen okur. " +
            "Ekle: okunan ses, oynatma çizgisinin olduğu andan itibaren seslendirme kaydı olur; Kayıtlar'da efekt, hız, kırpma ve yer kaydırma uygulanabilir.")
    }
}

private val RENKLER = listOf(0xFFFFFFFF to "Beyaz", 0xFFFFD54F to "Sarı", 0xFFF5A524 to "Turuncu", 0xFF2EC5D3 to "Turkuaz", 0xFF111111 to "Siyah")

@Composable
fun PanelYazi(vm: EditorDurum, k: Klip) {
    val y = k.yazi ?: Yazi()
    Kart("Yazı (bu klipte)") {
        var metin by remember(k.id) { mutableStateOf(y.metin) }
        OutlinedTextField(
            value = metin,
            onValueChange = { metin = it; vm.klipGuncelle { kk -> kk.copy(yazi = (kk.yazi ?: Yazi()).copy(metin = it)) } },
            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Başlık ya da yazı") }, minLines = 1, maxLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ust" to "Üst", "orta" to "Orta", "alt" to "Alt").forEach { (kk, ad) ->
                SecimKarti(ad, "", y.konum == kk, Modifier.weight(1f)) { vm.klipGuncelle { it.copy(yazi = (it.yazi ?: Yazi()).copy(konum = kk)) } }
            }
        }
        var b by remember(k.id, y.boyut) { mutableFloatStateOf(y.boyut) }
        Etiketli("Boyut", "%${(b * 100).roundToInt()}")
        InceSlider(value = b, onValueChange = { b = it }, valueRange = 0.03f..0.16f,
            onValueChangeFinished = { vm.klipGuncelle { it.copy(yazi = (it.yazi ?: Yazi()).copy(boyut = b)) } },
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RENKLER.forEach { (r, _) ->
                Box(
                    Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(r))
                        .border(if (y.renk == r) 2.dp else 0.5.dp, if (y.renk == r) Color.White else Renk.cizgi, RoundedCornerShape(9.dp))
                        .clickable { vm.klipGuncelle { it.copy(yazi = (it.yazi ?: Yazi()).copy(renk = r)) } },
                )
            }
        }
        AnahtarSatiri("Arka kutu (okunaklı)", y.kutu) { v -> vm.klipGuncelle { it.copy(yazi = (it.yazi ?: Yazi()).copy(kutu = v)) } }
        DugmeSirasi(
            "Tüm kliplere kopyala" to { vm.hepsine({ it.copy(yazi = k.yazi) }) },
            "Kaldır" to { metin = ""; vm.klipGuncelle { it.copy(yazi = null) } },
        )
    }
}



/** 3.61: alt menü düğmesi (ana menü satırında ya da onun üstünde): renkli çizim simge + küçük yazı, seçiliyse nokta, ayar değiştiyse ✓. */
class AltOge(val ad: String, val simge: String, val renk: Color, val secili: Boolean, val isaret: Boolean, val tik: () -> Unit)

/** 4.15: alt menü satırındaki sayfa çipleri verisi. */
private class CipVeri(val liste: List<AltSayfa>, val secili: String, val isaret: (String) -> Boolean, val bag: List<Triple<String, String, () -> Unit>>, val sec: (String) -> Unit)

/** 4.15: seçili alt menünün sayfaları — hafif zeminli tek grup, içinde küçük çipler; sonra varsa bağlantılar (ad ›). */
@Composable
private fun SatirCipleri(c: CipVeri) {
    if (Duzen.cipRenkli) { SatirCipleriRenkli(c); return }   // 4.47
    Row(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Renk.kart).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        c.liste.forEach { a ->
            val on = a.anahtar == c.secili
            Row(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(if (on) Renk.yazi else Color.Transparent).clickable { c.sec(a.anahtar) }
                .padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk")) Simge(a.simge, if (on) Renk.zemin else Renk.soluk, 13.dp)
                if (Duzen.menuStil != "simge") Text(menuAd(a.ad), color = if (on) Renk.zemin else Renk.yazi, fontSize = 11.5.sp * menuYaziK, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp))
                if (c.isaret(a.anahtar)) OnayIsareti(Modifier.padding(start = 4.dp), 11.dp)
            }
        }
    }
    c.bag.forEach { (ad, simge, tik) -> BaglantiCipi(ad, simge, tik) }
}

/** 4.52 (C · panel başlığında sekmeler): alt menü altı çizili sekmeler, 3. seviye eşit bölmeli renkli seçici; altta yalnız ana menü. */
@Composable
private fun PanelSekmeleri(altlar: List<AltOge>, c: CipVeri?) {
    Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(horizontal = 10.dp)) {
        if (altlar.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            altlar.forEach { a ->
                Column(Modifier.clickable(onClick = a.tik).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Simge(a.simge, if (a.secili) Renk.turuncu else Renk.soluk, 15.dp)
                        Text(" " + menuAd(a.ad), color = if (a.secili) Renk.turuncu else Renk.soluk, fontSize = 13.sp * menuYaziK,
                            fontWeight = if (a.secili) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                    Box(Modifier.padding(top = 5.dp).width(36.dp).height(2.dp).background(if (a.secili) Renk.turuncu else Color.Transparent))
                }
            }
        }
        if (altlar.size > 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
        if (c != null && c.liste.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Renk.kart).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                c.liste.forEachIndexed { i, a ->
                    val on = a.anahtar == c.secili
                    val renk = Color(ALT_RENK[(i + 2) % ALT_RENK.size])
                    Row(Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(9.dp)).background(renk.copy(alpha = if (on) 0.95f else 0.14f)).clickable { c.sec(a.anahtar) }
                        .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Simge(a.simge, if (on) Color(0xFF111111) else renk, 13.dp)
                        Text(" " + menuAd(a.ad), color = if (on) Color(0xFF111111) else renk, fontSize = 11.sp * menuYaziK, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.isaret(a.anahtar)) OnayIsareti(Modifier.padding(start = 3.dp), 10.dp)
                    }
                }
            }
            if (c.bag.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { c.bag.forEach { (ad, simge, tik) -> BaglantiCipi(ad, simge, tik) } }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 4.47: alt menünün yanındaki sayfa düğmeleri — renkli, simgeli; başta "›" ve ince çerçeveli grup (alt menünün alt menüsü olduğu belli). */
@Composable
private fun SatirCipleriRenkli(c: CipVeri) {
    val m = Duzen.menuOlcek
    Text("›", color = Renk.soluk, fontSize = 16.sp * m, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 1.dp))
    Row(Modifier.height(34.dp * m).clip(RoundedCornerShape(17.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(17.dp)).padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        c.liste.forEachIndexed { i, a ->
            val on = a.anahtar == c.secili
            val renk = Color(ALT_RENK[(i + 2) % ALT_RENK.size])
            Row(Modifier.height(27.dp * m).clip(RoundedCornerShape(14.dp)).background(renk.copy(alpha = if (on) 0.95f else 0.16f)).clickable { c.sec(a.anahtar) }
                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Simge(a.simge, if (on) Color(0xFF111111) else renk, 14.dp * m)
                if (Duzen.menuStil != "simge") Text(menuAd(a.ad), color = if (on) Color(0xFF111111) else renk, fontSize = 11.5.sp * m * menuYaziK, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp))
                if (c.isaret(a.anahtar)) OnayIsareti(Modifier.padding(start = 4.dp), 11.dp)
            }
        }
    }
    c.bag.forEach { (ad, simge, tik) -> BaglantiCipi(ad, simge, tik) }
}

/** 4.14: üstteki küçük alt menü satırının düğmesi — simge + ad yan yana; seçili = tema renginde çerçeve ve yazı. */
@Composable
private fun AltKucuk(o: AltOge) {
    val sekil = RoundedCornerShape(16.dp)
    val m = Duzen.menuOlcek
    // 4.21: "renkli" = önceki yazılı renkli alt başlıklar (InShot gibi): her düğme kendi renginde dolgulu hap, seçili tam renk
    if (Duzen.menuStil == "renkli") {
        Row(Modifier.height(32.dp * m).clip(sekil).background(o.renk.copy(alpha = if (o.secili) 0.95f else 0.22f)).clickable(onClick = o.tik).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(o.ad, color = if (o.secili) Color(0xFF111111) else o.renk, fontSize = 12.5.sp * m, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (o.isaret) OnayIsareti(Modifier.padding(start = 4.dp), 11.dp)
        }
        return
    }
    Row(Modifier.height(32.dp * m).clip(sekil).background(if (o.secili) Renk.turuncu.copy(alpha = 0.12f) else Renk.kart2)
        .border(1.dp, if (o.secili) Renk.turuncu.copy(alpha = 0.75f) else Color.Transparent, sekil).clickable(onClick = o.tik).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk")) Simge(o.simge, if (o.secili) Renk.turuncu else Renk.soluk, 15.dp * m)
        if (Duzen.menuStil != "simge") Text(menuAd(o.ad), color = if (o.secili) Renk.turuncu else Renk.yazi, fontSize = 12.sp * m * menuYaziK, fontWeight = if (o.secili) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
            modifier = Modifier.padding(start = 5.dp))
        if (o.isaret) OnayIsareti(Modifier.padding(start = 4.dp), 11.dp)
    }
}

@Composable
private fun AltDugme(o0: AltOge) {
    val o = if (Renk.ins) AltOge(o0.ad, o0.simge, if (o0.secili) Color.White else Color(0xFFB4B4B4), o0.secili, o0.isaret, o0.tik)   // 3.91: InShot — gri, seçili beyaz
        else if (Renk.sb) AltOge(o0.ad, o0.simge, Renk.tonla(o0.renk), o0.secili, o0.isaret, o0.tik) else o0
    Column(Modifier.width(76.dp * Duzen.menuOlcek).clip(RoundedCornerShape(10.dp)).clickable(onClick = o.tik).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk")) Simge(o.simge, if (o.secili) o.renk else o.renk.copy(alpha = 0.7f), 22.dp * Duzen.menuOlcek)
        if ((Duzen.menuStil != "yazi" && Duzen.menuStil != "renkli" && Duzen.menuStil != "buyuk") && Duzen.menuStil != "simge") Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Duzen.menuStil != "simge") Text(o.ad, color = if (o.secili) o.renk else o.renk.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = if (o.secili) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            if (o.isaret) OnayIsareti(Modifier.padding(start = 3.dp), 11.dp)
        }
        // 3.91: InShot temasında seçili alt menünün altında çizgi (sekme gibi); diğerlerinde nokta
        if (Renk.ins) Box(Modifier.padding(top = 3.dp).width(26.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(if (o.secili) Color.White else Color.Transparent))
        else Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (o.secili) o.renk else Color.Transparent))
    }
}


/** 3.72: klip kartındaki resim, oynatıcıdaki görüntünün AYNISI: video biçimi (16:9 / 9:16 / 1:1), kadraj (Doldur/Sığdır, yüz odağı,
 *  elle kaydırma, hareketin ilk karesi) ve renk/filtre önizlemedeki hesapla (Yuz.an + aynı yerleşim) çizilir.
 *  sigdir = true: karenin tamamı kartın içinde (seçili büyük kart; simgeler üst/alt siyah bantta) · false: kart kare ile doldurulur. */
@Composable
private fun KlipKaresi(p: Proje, k: Klip, i: Int, modifier: Modifier, sigdir: Boolean) {
    val ctx = LocalContext.current
    val ham by produceState<android.graphics.Bitmap?>(null, k.uri, k.basMs) { value = Medya.kucukResim(ctx, k, 480) }
    val renkli by produceState<android.graphics.Bitmap?>(null, ham, k.renk, k.filtreKod) {
        val h = ham
        value = if (h == null) null else if (k.renk?.bos != false && !k.filtreAcik) h
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { RenkLut.uygula(h, k.renk ?: RenkAyar(), k.filtreKod, 1f) }.getOrDefault(h) }
    }
    val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
    BoxWithConstraints(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        val cw = maxWidth; val ch = maxHeight
        val genis = cw / ch > oran
        val fw = if (genis == sigdir) ch * oran else cw
        val fh = if (genis == sigdir) ch else cw / oran
        Box(Modifier.requiredSize(fw, fh).clipToBounds(), contentAlignment = Alignment.Center) {
            renkli?.let { b ->
                val (RW, RH) = remember(p.bicim) { Disa.boyut(p.bicim, 1080) }
                val a = Yuz.an(k, RW, RH, 0f, i)
                Image(b.asImageBitmap(), null, Modifier.requiredSize(fw / a.vx, fh / a.vy).graphicsLayer {
                    scaleX = a.s; scaleY = a.s
                    translationX = a.tx * size.width / 2f; translationY = -a.ty * size.height / 2f
                }, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
}


/** 3.73: seçili klip kartının ortasında oynat/duraklat. Başka klipteyken dokunursa önce bu klibin başına gider. */
@Composable
private fun KartOynat(vm: EditorDurum, i: Int, modifier: Modifier) {
    val oyn by vm.oynuyor.collectAsState()
    Box(modifier.size(38.dp).clip(CircleShape).background(Color(0x99000000)).border(1.5.dp, Color(0x66FFFFFF), CircleShape).clickable {
        if (oyn) vm.oynatDurdur()
        else {
            val bas = vm.baslangic(i); val son = bas + (vm.proje.value.klipler.getOrNull(i)?.let { vm.proje.value.zKlip(i).sureMs } ?: 0L)
            if (vm.konum.value !in bas until son) vm.git(bas)
            vm.oynatDurdur()
        }
    }, contentAlignment = Alignment.Center) { Simge(if (oyn) "durdur" else "oynat", Color.White, 22.dp) }
}

/** 3.73: klip içindeki oynatma konumu — kartın alt kenarında ince çubuk (yalnız bu kendini yeniden çizer). */
@Composable
private fun KartIlerleme(vm: EditorDurum, i: Int, modifier: Modifier) {
    val konum by vm.konum.collectAsState()
    val p = vm.proje.value
    if (i !in p.klipler.indices) return
    val bas = vm.baslangic(i); val d = p.zKlip(i).sureMs.coerceAtLeast(1)
    val f = ((konum - bas).toFloat() / d).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x55000000))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Renk.turuncu))
    }
}

/** 3.75: ekran sürekli açık — düzenleme ekranındayken telefon ekranı kararmaz (FLAG_KEEP_SCREEN_ON karşılığı, View.keepScreenOn).
 *  Üst çubukta, Kaydet'in solunda; açıkken dolu beyaz, kapalıyken soluk. Seçim hatırlanır; editörden çıkınca bırakılır. */
@Composable
private fun EkranAcikDugmesi(vm: EditorDurum) {
    val ctx = LocalContext.current
    val gorunum = androidx.compose.ui.platform.LocalView.current
    var acik by remember { mutableStateOf(ctx.getSharedPreferences("ayar", 0).getBoolean("ekranAcik", false)) }
    DisposableEffect(acik) { gorunum.keepScreenOn = acik; onDispose { gorunum.keepScreenOn = false } }
    // 3.77: düğme görünümü yok — yalnız simge + yazı; açıkken hafif beyaz, kapalıyken gri (temaya göre)
    val renk = if (acik) Renk.soluk else Renk.soluk.copy(alpha = 0.45f)
    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable {
            acik = !acik
            ctx.getSharedPreferences("ayar", 0).edit().putBoolean("ekranAcik", acik).apply()
            vm.mesaj.value = if (acik) "Ekran sürekli açık" else "Ekran normal (kendiliğinden kararır)"
        }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Simge("ekran_acik", renk, 17.dp)   // 4.28: yalnız simge, soluk (açıkken biraz daha belirgin)
    }
    Spacer(Modifier.width(2.dp))
}


/** 3.81: Google Fotoğraflar albüm bağlantısı — panodaki bağlantı kendiliğinden gelir; "Fotoğrafları al" indirir ve ekler. */
@Composable
private fun AlbumPenceresi(vm: EditorDurum, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val pano = remember { runCatching { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
        .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull() }
    // 3.96: birden fazla bağlantı — her satıra bir bağlantı; "Panodan ekle" yeni satıra yapıştırır
    var metin by remember { mutableStateOf(AlbumIndir.bagla(pano) ?: "") }
    val linkler = AlbumIndir.baglar(metin)
    val link = linkler.firstOrNull()
    fun panodan() {
        val yeni = runCatching { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull()
        val eklenecek = AlbumIndir.baglar(yeni).filter { it !in linkler }
        if (eklenecek.isEmpty()) vm.mesaj.value = "Panoda yeni albüm bağlantısı yok" else metin = (metin.trimEnd() + "\n" + eklenecek.joinToString("\n")).trim()
    }
    AlertDialog(onDismissRequest = kapat, containerColor = Renk.kart,
        title = { Text("Google Fotoğraflar albümleri", color = Renk.yazi, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(metin, { metin = it }, Modifier.fillMaxWidth().heightIn(min = 96.dp), singleLine = false, minLines = 3, maxLines = 8,
                    label = { Text("Albüm bağlantıları (her satıra bir tane)") }, placeholder = { Text("https://photos.app.goo.gl/…") })
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (linkler.isEmpty()) "Bağlantı yok" else "${linkler.size} albüm", color = if (linkler.isEmpty()) Renk.soluk else Renk.turuncu, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                    SimgeDugme("yukle", "Panodan ekle", renk = Renk.turuncu) { panodan() }
                }
                Text(if (link != null) "Albümlerdeki fotoğraf ve videolar sırayla, özgün boyutlarında indirilip videoya eklenir (albüm sırasıyla)." else "Bağlantıları yapıştır (photos.app.goo.gl ya da photos.google.com/share).",
                    color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                AcilirNot("Google Fotoğraflar'da albümü aç › Paylaş › Bağlantı oluştur › kopyala (ya da doğrudan Stüdyo'yu seç). " +
                    "Bağlantı \"bağlantıya sahip olan herkes\" ile paylaşılmış olmalı. Yalnız birkaç fotoğraf istiyorsan: Google Fotoğraflar'da seç › Paylaş › Stüdyo.")
            }
        },
        confirmButton = { TextButton(enabled = link != null, onClick = { vm.albumAl(linkler); kapat() }) {
            Text(if (linkler.size > 1) "${linkler.size} albümü al" else "Fotoğrafları al", color = if (link != null) Renk.yazi else Renk.soluk) } },
        dismissButton = { TextButton(onClick = kapat) { Text("Vazgeç", color = Renk.soluk) } })
}

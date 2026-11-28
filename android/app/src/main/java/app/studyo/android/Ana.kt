package app.studyo.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.androidbrowserhelper.trusted.LauncherActivity
import java.text.SimpleDateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val paylasilan = mutableStateOf<List<Uri>>(emptyList())
    private var sonPaylasim: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Depo.baslat(this)
        Renk.yukle(this)
        Duzen.yukle(this)
        AnaRenk.yukle(this)
        DisaServisi.acilisKontrol(this)
        enableEdgeToEdge()
        tamEkran()
        paylasimAl(intent)
        setContent {
            StudyoTema {
                Uygulama(paylasilan.value, { paylasilan.value = emptyList() }, ::webAc, ::webUrl)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        paylasimAl(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) tamEkran()
    }

    /** Tam ekran: sistem çubukları gizli, kamera bölgesini uygulamanın kendi başlığı boyar. */
    private fun tamEkran() {
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun paylasimAl(i: Intent?) {
        i ?: return
        val l = ArrayList<Uri>()
        when (i.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { l += it }
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(i, Intent.EXTRA_STREAM, Uri::class.java)?.let { l.addAll(it) }
        }
        if (l.isNotEmpty()) { sonPaylasim = i; paylasilan.value = l; return }
        // 3.81: Google Fotoğraflar › Paylaş › Stüdyo (albüm bağlantısı, düz metin) → açık projeye ya da yeni projeye eklenir
        if (i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) AlbumIndir.bagla(i.getStringExtra(Intent.EXTRA_TEXT))?.let { AlbumIndir.istek.value = it }
    }

    /** Web Stüdyo (fotoğraf editörü, önceki video editörü, web projeleri) — Chrome ile, 2.x'teki gibi. */
    fun webAc(paylasimla: Boolean) {
        val s = sonPaylasim
        val i = if (paylasimla && s != null) Intent(s).setClass(this, LauncherActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(this, LauncherActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(WEB_ADRES + "foto/?yerel=1&studyo=1&renk=" + String.format("%06X", AnaRenk.SECENEK[AnaRenk.i].first and 0xFFFFFFL)))   // 4.57: fotoğraf ekranı doğrudan (web ana sayfası hiç yüklenmez; önce ?git=foto ile ana sayfa + çerçeve)
        try { startActivity(i) } catch (e: Exception) { Kayit.hata("web aç", e) }
    }

    /** 3.85: belirli bir web sayfası (ör. Karanlık Oda) — aynı Chrome kabuğuyla. */
    fun webUrl(adres: String) {
        try { startActivity(Intent(this, LauncherActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(adres))) } catch (e: Exception) { Kayit.hata("web aç", e) }
    }
}

@Composable
fun Uygulama(paylasilan: List<Uri>, paylasimBitti: () -> Unit, webAc: (Boolean) -> Unit, webUrl: (String) -> Unit = {}) {
    var acik by rememberSaveable { mutableStateOf<String?>(null) }
    var bekleyen by remember { mutableStateOf<List<Uri>>(emptyList()) }
    // 3.81: paylaşılan albüm bağlantısı — editör açık değilse yeni proje açılır, editör bağlantıyı alıp indirir
    val albumIstek by AlbumIndir.istek.collectAsState()
    LaunchedEffect(albumIstek, acik) {
        if (albumIstek != null && acik == null) { val p = Proje(ad = "Albüm " + SimpleDateFormat("d MMM HH:mm", TR).format(Date())); Depo.kaydet(p); acik = p.id }
    }

    if (paylasilan.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = paylasimBitti,
            title = { Text("${paylasilan.size} dosya paylaşıldı") },
            text = { Text("Ne yapılsın?") },
            confirmButton = {
                TextButton(onClick = {
                    val p = Proje(ad = "Video " + SimpleDateFormat("d MMM HH:mm", TR).format(Date()))
                    Depo.kaydet(p); bekleyen = paylasilan; acik = p.id; paylasimBitti()
                }) { Text("Yeni video projesi") }
            },
            dismissButton = { TextButton(onClick = { webAc(true); paylasimBitti() }) { Text("Fotoğraf editörü (web)") } },
        )
    }

    val a = acik
    if (a == null) {
        AnaSayfa(
            ac = { acik = it },
            yeni = {
                val p = Proje(ad = "Video " + SimpleDateFormat("d MMM HH:mm", TR).format(Date()))
                Depo.kaydet(p); acik = p.id
            },
            webAc = { webAc(false) },
            webUrl = webUrl,
            yeniSecimle = { l ->
                val p = Proje(ad = "Video " + SimpleDateFormat("d MMM HH:mm", TR).format(Date()))
                Depo.kaydet(p); bekleyen = l; acik = p.id
            },
        )
    } else {
        key(a) {
            EditorEkrani(a, bekleyen, bekleyenAlindi = { bekleyen = emptyList() }, kapat = { acik = null }, ac = { acik = it })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnaSayfa(ac: (String) -> Unit, yeni: () -> Unit, webAc: () -> Unit, webUrl: (String) -> Unit = {}, yeniSecimle: (List<Uri>) -> Unit = {}) {
    val ctx = LocalContext.current
    var projeler by remember { mutableStateOf(Depo.listele()) }
    var silinecek by remember { mutableStateOf<Proje?>(null) }
    var adDegis by remember { mutableStateOf<Proje?>(null) }
    var menu by remember { mutableStateOf(false) }
    val disa by DisaServisi.durum.collectAsState()
    var secim by remember { mutableStateOf<Set<String>?>(null) }      // null: seçim kipi kapalı
    var arsivde by remember { mutableStateOf(false) }
    var topluSilOnay by remember { mutableStateOf(false) }
    var tvVideo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tumu by rememberSaveable { mutableStateOf(false) }   // 4.57: uzun listede 3 proje + "Tamamını göster / Gizle" (web fotoğraf 2.31 gibi)
    val gorunen = projeler.filter { it.arsiv == arsivde }
    val arsivSayisi = projeler.count { it.arsiv }
    val seciliProjeler = secim?.let { s -> gorunen.filter { it.id in s } } ?: emptyList()
    // Dışa aktarılmakta olan proje silinemez
    val disadaki = if (disa is DisaDurum.Calisiyor || disa is DisaDurum.Dogrulaniyor) DisaServisi.sonProje else null
    BackHandler(enabled = secim != null || arsivde) { if (secim != null) secim = null else arsivde = false }
    LaunchedEffect(arsivde) { secim = null; tumu = false }
    // 3.61: uygulama silinip yeniden kurulunca ayarları ve logoları yedekten geri yükleme (⋯ menüsünde ve boş listede)
    val ctxY = LocalContext.current
    val geriYukle = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) Thread {
            val r = runCatching { Ayarlar.yedekOku(ctxY, u) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(ctxY, r.fold({ "Yedek geri yüklendi ($it logo). Uygulamayı kapatıp yeniden aç." }, { "Geri yüklenemedi: ${it.message}" }), android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    if (Duzen.anaStudyo) AnaSayfaStudyo(
        projeler = projeler, gorunen = gorunen, arsivde = arsivde, arsivSayisi = arsivSayisi, secim = secim, disa = disa,
        arsivDegis = { arsivde = it }, secimDegis = { secim = it }, yeni = yeni, yeniSecimle = yeniSecimle, webAc = webAc, webUrl = webUrl, ac = ac,
        yenile = { projeler = Depo.listele() }, topluSil = { topluSilOnay = true }, geriYukle = { geriYukle.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        silIste = { silinecek = it }, adIste = { adDegis = it }, tvIste = { tvVideo = it },
    ) else if (Renk.ko) AnaSayfaKO(
        projeler = projeler, gorunen = gorunen, arsivde = arsivde, arsivSayisi = arsivSayisi, secim = secim, disa = disa,
        arsivDegis = { arsivde = it }, secimDegis = { secim = it }, yeni = yeni, webAc = webAc, ac = ac,
        yenile = { projeler = Depo.listele() }, topluSil = { topluSilOnay = true }, geriYukle = { geriYukle.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        silIste = { silinecek = it }, adIste = { adDegis = it }, tvIste = { tvVideo = it },
    ) else
    Column(Modifier.fillMaxSize().background(Renk.zemin)) {
        Row(
            Modifier.fillMaxWidth().background(Renk.kart)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Stüdyo", color = Renk.yazi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Video düzenleyici · yerel uygulama", color = Renk.soluk, fontSize = 12.sp)
            }
            Box {
                TextButton(onClick = { menu = true }) { Text("⋯", color = Renk.yazi, fontSize = 24.sp) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // 4.57: video ana sayfasının rengi — küçük renk alanı (dokun = seç, hatırlanır; fotoğraf tarafı sarı kalır)
                    AnaRenkAlani(ctx)
                    HorizontalDivider(color = Renk.cizgi)
                    DropdownMenuItem(text = { Text("Hata kaydını paylaş") }, onClick = { menu = false; kayitPaylas(ctx) })
                    DropdownMenuItem(text = { Text("Yedekten geri yükle (ayarlar, logolar)") }, onClick = { menu = false; geriYukle.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
                    // 3.63: üç tema — gece · gündüz · siyah-beyaz (VITA düzeni)
                    DropdownMenuItem(text = { Text(if (Renk.tema == 0) "✓  Gece teması" else "     Gece teması", color = if (Renk.tema == 0) Renk.turuncu else Renk.yazi) }, onClick = { menu = false; Renk.sec(ctx, 0) })
                    DropdownMenuItem(text = { Text(if (Renk.tema == 1) "✓  Gündüz teması" else "     Gündüz teması", color = if (Renk.tema == 1) Renk.turuncu else Renk.yazi) }, onClick = { menu = false; Renk.sec(ctx, 1) })
                    DropdownMenuItem(text = { Text(if (Renk.tema == 2) "✓  Siyah-beyaz tema (VITA düzeni)" else "     Siyah-beyaz tema (VITA düzeni)", color = if (Renk.tema == 2) Renk.turuncu else Renk.yazi) }, onClick = { menu = false; Renk.sec(ctx, 2) })
                    DropdownMenuItem(text = { Text(if (Renk.tema == 4) "✓  InShot teması (koyu, turkuaz)" else "     InShot teması (koyu, turkuaz)", color = if (Renk.tema == 4) Renk.turuncu else Renk.yazi) }, onClick = { menu = false; Renk.sec(ctx, 4) })
                    DropdownMenuItem(text = { Text("Stüdyo ana sayfası (açık, mavi-turuncu)") }, onClick = { menu = false; Duzen.anaStudyoSec(ctx, true) })
                    DropdownMenuItem(text = { Text(if (Renk.tema == 3) "✓  Karanlık Oda teması (yeni)" else "     Karanlık Oda teması (yeni)", color = if (Renk.tema == 3) Renk.turuncu else Renk.yazi) }, onClick = { menu = false; Renk.sec(ctx, 3) })
                    // 4.23: "Alt başlıklar: yazılı (önceki)" kaldırıldı — eski düzen arama, Yüzler, oynatıcı gibi özellikleri gizliyordu
                    DropdownMenuItem(text = { Text("Pil kısıtlamasını kaldır") }, onClick = { menu = false; pilAyari(ctx) })
                    DropdownMenuItem(text = { Text("Sürüm ${BuildConfigBilgi.surum(ctx)}") }, onClick = { menu = false })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(16.dp)).background(AnaRenk.c).clickable(onClick = yeni).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("＋", color = AnaRenk.m, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Yeni video", color = AnaRenk.m, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(16.dp)).background(Renk.kart2)
                            .pointerInput(Unit) { detectTapGestures(onTap = { webAc() }, onLongPress = { webUrl(WEB_ADRES) }) }.padding(14.dp),   // 4.55: uzun bas = önceki web Stüdyo
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Simge("kapak", AnaRenk.c, 28.dp)
                        Column {
                            Text("Fotoğraf", color = Renk.yazi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("Karanlık Oda · uzun bas: önceki Stüdyo", color = Renk.soluk, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                }
            }
            if (disa != DisaDurum.Bos) item { DisaSerit(disa) { DisaServisi.sonProje?.let { id -> if (Depo.yukle(id) != null) ac(id) } } }
            stickyHeader {
                val s = secim
                Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(top = 6.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (s != null) Text("${s.size} seçili", color = Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        else {
                            // 4.57: fotoğraf ana ekranındaki haplar gibi — Son çalışmalarım · Arşiv
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AnaHap("Son çalışmalarım", !arsivde) { arsivde = false }
                                AnaHap("Arşiv" + if (arsivSayisi > 0) " · $arsivSayisi" else "", arsivde) { arsivde = true }
                            }
                            if (gorunen.isNotEmpty()) { Spacer(Modifier.width(8.dp)); BaslikDugmesi("Seç") { secim = emptySet() } }
                        }
                    }
                    if (s != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimgeDugme("uygula", "Tümünü seç", Modifier.weight(1f), renk = Renk.turkuaz, etkin = s.size < gorunen.size) { secim = gorunen.map { it.id }.toSet() }
                        SimgeDugme("gerial", "Seçimi kaldır", Modifier.weight(1f), renk = Renk.soluk, etkin = s.isNotEmpty()) { secim = emptySet() }
                        SimgeDugme(if (arsivde) "yinele" else "asagi", if (arsivde) "Arşivden çıkar" else "Arşivle", Modifier.weight(1f), renk = Renk.turuncu, etkin = s.isNotEmpty()) {
                            Depo.arsivle(seciliProjeler, !arsivde); projeler = Depo.listele(); secim = null
                        }
                        SimgeDugme("sil", "Sil", Modifier.weight(1f), renk = Renk.kirmizi, etkin = s.isNotEmpty()) { topluSilOnay = true }
                        SimgeDugme("kapat", "Bitti", Modifier.weight(1f)) { secim = null }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(0.6.dp).background(Renk.cizgi))
                }
            }
            if (gorunen.isEmpty()) item { Column {
                Text(
                    if (arsivde) "Arşiv boş."
                    else "Henüz video projesi yok. “Yeni video” ile başla. Web Stüdyo'daki eski projeler “Fotoğraf + önceki Stüdyo” içinde duruyor.",
                    color = Renk.soluk, fontSize = 13.sp)
                if (!arsivde && projeler.isEmpty()) TextButton(onClick = { geriYukle.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                    Text("Uygulamayı yeniden mi kurdun? Yedekten geri yükle", color = Renk.turuncu, fontSize = 13.sp)
                }
            } }
            val listede = if (tumu || secim != null) gorunen else gorunen.take(3)
            items(listede, key = { it.id }) { p ->
                val s = secim
                ProjeKarti(p,
                    ac = { if (s != null) secim = if (p.id in s) s - p.id else s + p.id else ac(p.id) },
                    cogalt = { Depo.cogalt(p); projeler = Depo.listele() },
                    adDegistir = { adDegis = p },
                    sil = { silinecek = p },
                    arsivle = { Depo.arsivle(listOf(p), !p.arsiv); projeler = Depo.listele() },
                    secimde = s != null, secili = s?.contains(p.id) == true,
                    uzunBas = { secim = (s ?: emptySet()) + p.id },
                    tv = remember(p.id, disa) { TvYayin.cikti(ctxY, p.id) }?.let { c -> { tvVideo = c } })
            }
            if (secim == null && gorunen.size > 3) item(key = "gosterGizle") { GosterGizle(tumu, gorunen.size - 3) { tumu = !tumu } }
        }
    }

    // 3.73: Televizyonda izle (projenin son dışa aktarılan videosu)
    tvVideo?.let { (u, ad) -> TvPenceresi(u, ad) { tvVideo = null } }
    if (topluSilOnay) {
        val silinecekler = seciliProjeler.filter { it.id != disadaki }
        val atlanan = seciliProjeler.size - silinecekler.size
        AlertDialog(
            onDismissRequest = { topluSilOnay = false },
            title = { Text("${silinecekler.size} proje silinsin mi?") },
            text = {
                Text("Telefondaki videoların kendisi silinmez. Projelerin seslendirme kayıtları ve uygulama içi kopyaları silinir. Bu işlem geri alınamaz." +
                    if (atlanan > 0) "\n\nŞu anda dışa aktarılan proje silinmeyecek." else "")
            },
            confirmButton = {
                TextButton(enabled = silinecekler.isNotEmpty(), onClick = {
                    Depo.topluSil(silinecekler); projeler = Depo.listele(); secim = null; topluSilOnay = false
                }) { Text("Sil", color = Renk.kirmizi) }
            },
            dismissButton = { TextButton(onClick = { topluSilOnay = false }) { Text("Vazgeç") } },
        )
    }
    silinecek?.let { p ->
        AlertDialog(
            onDismissRequest = { silinecek = null },
            title = { Text("“${p.ad}” silinsin mi?") },
            text = { Text("Telefondaki videoların kendisi silinmez. Projenin seslendirme kayıtları ve uygulama içi kopyaları silinir.") },
            confirmButton = { TextButton(onClick = { Depo.sil(p); projeler = Depo.listele(); silinecek = null }) { Text("Sil", color = Renk.kirmizi) } },
            dismissButton = { TextButton(onClick = { silinecek = null }) { Text("Vazgeç") } },
        )
    }
    adDegis?.let { p ->
        var ad by remember(p.id) { mutableStateOf(p.ad) }
        AlertDialog(
            onDismissRequest = { adDegis = null },
            title = { Text("Proje adı") },
            text = { OutlinedTextField(ad, { ad = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { Depo.kaydet(p.copy(ad = ad.ifBlank { p.ad }, guncelleme = System.currentTimeMillis())); projeler = Depo.listele(); adDegis = null }) { Text("Tamam") }
            },
            dismissButton = { TextButton(onClick = { adDegis = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
fun DisaSerit(d: DisaDurum, onClick: () -> Unit) {
    val (metin, renk) = when (d) {
        is DisaDurum.Calisiyor -> "Video oluşturuluyor · %${d.yuzde}" to Renk.turuncu
        is DisaDurum.Dogrulaniyor -> "Video doğrulanıyor…" to Renk.turuncu
        is DisaDurum.Duraklatildi -> "Yarım kalan dışa aktarma · devam etmek için dokun" to Renk.turkuaz
        is DisaDurum.Tamam -> "Son video kaydedildi: ${d.ad}" to Renk.yesil
        is DisaDurum.Hata -> d.mesaj to Renk.kirmizi
        else -> "" to Renk.soluk
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(renk.copy(alpha = 0.15f)).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(metin, color = renk, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("›", color = renk, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjeKarti(
    p: Proje, ac: () -> Unit, cogalt: () -> Unit, adDegistir: () -> Unit, sil: () -> Unit, arsivle: () -> Unit,
    secimde: Boolean, secili: Boolean, uzunBas: () -> Unit, tv: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val sekil = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(sekil).background(if (secili) Renk.turuncu.copy(alpha = 0.12f) else Renk.kart)
            .border(if (secili) 1.5.dp else 0.dp, if (secili) Renk.turuncu else Color.Transparent, sekil)
            .combinedClickable(onClick = ac, onLongClick = uzunBas).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secimde) {
            Box(
                Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape)
                    .background(if (secili) Renk.turuncu else Color.Transparent)
                    .border(1.5.dp, if (secili) Renk.turuncu else Renk.soluk, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (secili) Text("✓", color = Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        // 4.26: düzenli kart — sabit küçük kapak, yazılar tek satır (taşarsa …), sağda dar simge sütunu (TV üstte, ⋯ altta)
        KlipResmi(p.klipler.firstOrNull(), Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.ad, color = Renk.yazi, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.klipler.size} klip · ${sureYaz(p.toplamMs)} · ${p.bicim}", color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(SimpleDateFormat("d MMM yyyy · HH:mm", TR).format(Date(p.guncelleme)), color = Renk.soluk.copy(alpha = 0.8f), fontSize = 11.5.sp, maxLines = 1)
        }
        if (!secimde) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 3.73: dışa aktarılmış videosu olan projede TV simgesi (dokun = televizyonda izle)
            if (tv != null) Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = tv), contentAlignment = Alignment.Center) { Simge("tv", Renk.turuncu, 19.dp) }
            Box {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Simge("menu", Renk.soluk, 18.dp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Aç") }, onClick = { menu = false; ac() })
                if (tv != null) DropdownMenuItem(text = { Text("Televizyonda izle") }, onClick = { menu = false; tv() })
                DropdownMenuItem(text = { Text("Çoğalt") }, onClick = { menu = false; cogalt() })
                DropdownMenuItem(text = { Text("Adını değiştir") }, onClick = { menu = false; adDegistir() })
                DropdownMenuItem(text = { Text(if (p.arsiv) "Arşivden çıkar" else "Arşivle") }, onClick = { menu = false; arsivle() })
                DropdownMenuItem(text = { Text("Seç") }, onClick = { menu = false; uzunBas() })
                DropdownMenuItem(text = { Text("Sil", color = Renk.kirmizi) }, onClick = { menu = false; sil() })
            }
            }
        }
    }
}

/** Başlık yanındaki ince çizgili küçük düğme. */
@Composable
fun BaslikDugmesi(metin: String, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(16.dp)
    Text(metin, color = AnaRenk.c, fontSize = 12.5.sp, fontWeight = FontWeight.Light,
        modifier = Modifier.clip(sekil).border(0.8.dp, AnaRenk.c.copy(alpha = 0.5f), sekil).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp))
}

fun pilAyari(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}


/** 4.57: başlık hapı (fotoğraf ana ekranındaki "Son çalışmalarım · ★ Favoriler" gibi): seçili = ana sayfa rengi dolgulu. */
@Composable
fun AnaHap(metin: String, on: Boolean, tik: () -> Unit) {
    Text(metin, color = if (on) AnaRenk.m else Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(if (on) AnaRenk.c else Color.Transparent)
            .border(1.dp, if (on) AnaRenk.c else Renk.cizgi, CircleShape).clickable(onClick = tik).padding(horizontal = 14.dp, vertical = 7.dp))
}

/** 4.57: liste altında simgeli "Tamamını göster · n daha" / "Gizle" (fotoğraf ana ekranı 2.31 ile aynı). */
@Composable
fun GosterGizle(acik: Boolean, kalan: Int, tik: () -> Unit) {
    val sekil = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().height(44.dp).clip(sekil).background(Renk.kart).border(1.dp, Renk.cizgi, sekil).clickable(onClick = tik),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.graphicsLayer { rotationZ = if (acik) 180f else 0f }) { Simge("asagi", AnaRenk.c, 18.dp) }
        Spacer(Modifier.width(8.dp))
        Text(if (acik) "Gizle" else "Tamamını göster", color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (!acik) Text("  $kalan daha", color = Renk.soluk, fontSize = 12.sp)
    }
}

/** 4.57: ⋯ menüsündeki küçük renk alanı — video ana sayfasının rengi. */
@Composable
fun AnaRenkAlani(ctx: Context) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("Ana sayfa rengi · ${AnaRenk.ad}", color = Renk.soluk, fontSize = 12.sp)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnaRenk.SECENEK.forEachIndexed { i, (r, ad) ->
                val on = i == AnaRenk.i
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(r)).border(if (on) 2.5.dp else 0.dp, if (on) Renk.yazi else Color.Transparent, CircleShape)
                    .clickable { AnaRenk.sec(ctx, i) }, contentAlignment = Alignment.Center) {
                    if (on) Text("✓", color = AnaRenk.m, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 4.57: video ana sayfasının kendi rengi — fotoğraf ana ekranı (sarı) ile karışmasın diye varsayılan mercan. Hatırlanır. */
object AnaRenk {
    val SECENEK = listOf(0xFFFF7A59L to "Mercan", 0xFF3FC1B8L to "Turkuaz", 0xFF9B7BF7L to "Mor", 0xFF4C8DF6L to "Mavi",
        0xFF4CC38AL to "Yeşil", 0xFFEF5350L to "Kırmızı", 0xFFF2C94CL to "Sarı")
    var i by mutableIntStateOf(0)
    val c: Color get() = Color(SECENEK[i].first)
    val ad: String get() = SECENEK[i].second
    /** kart üstündeki yazı: açık renkte koyu, koyu renkte beyaz */
    val m: Color get() = c.let { if (0.299f * it.red + 0.587f * it.green + 0.114f * it.blue > 0.55f) Color(0xFF1B1204) else Color.White }
    fun yukle(ctx: Context) { i = ctx.getSharedPreferences("ayar", 0).getInt("anaRenk", 0).coerceIn(0, SECENEK.size - 1) }
    fun sec(ctx: Context, n: Int) { i = n.coerceIn(0, SECENEK.size - 1); ctx.getSharedPreferences("ayar", 0).edit().putInt("anaRenk", i).apply() }
}

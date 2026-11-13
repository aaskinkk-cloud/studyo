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
import androidx.compose.ui.draw.clip
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
        DisaServisi.acilisKontrol(this)
        enableEdgeToEdge()
        tamEkran()
        paylasimAl(intent)
        setContent {
            StudyoTema {
                Uygulama(paylasilan.value, { paylasilan.value = emptyList() }, ::webAc)
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
        if (l.isNotEmpty()) { sonPaylasim = i; paylasilan.value = l }
    }

    /** Web Stüdyo (fotoğraf editörü, önceki video editörü, web projeleri) — Chrome ile, 2.x'teki gibi. */
    fun webAc(paylasimla: Boolean) {
        val s = sonPaylasim
        val i = if (paylasimla && s != null) Intent(s).setClass(this, LauncherActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        else Intent(this, LauncherActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(WEB_ADRES))
        try { startActivity(i) } catch (e: Exception) { Kayit.hata("web aç", e) }
    }
}

@Composable
fun Uygulama(paylasilan: List<Uri>, paylasimBitti: () -> Unit, webAc: (Boolean) -> Unit) {
    var acik by rememberSaveable { mutableStateOf<String?>(null) }
    var bekleyen by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
        )
    } else {
        key(a) {
            EditorEkrani(a, bekleyen, bekleyenAlindi = { bekleyen = emptyList() }, kapat = { acik = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnaSayfa(ac: (String) -> Unit, yeni: () -> Unit, webAc: () -> Unit) {
    val ctx = LocalContext.current
    var projeler by remember { mutableStateOf(Depo.listele()) }
    var silinecek by remember { mutableStateOf<Proje?>(null) }
    var adDegis by remember { mutableStateOf<Proje?>(null) }
    var menu by remember { mutableStateOf(false) }
    val disa by DisaServisi.durum.collectAsState()
    var secim by remember { mutableStateOf<Set<String>?>(null) }      // null: seçim kipi kapalı
    var arsivde by remember { mutableStateOf(false) }
    var topluSilOnay by remember { mutableStateOf(false) }
    val gorunen = projeler.filter { it.arsiv == arsivde }
    val arsivSayisi = projeler.count { it.arsiv }
    val seciliProjeler = secim?.let { s -> gorunen.filter { it.id in s } } ?: emptyList()
    // Dışa aktarılmakta olan proje silinemez
    val disadaki = if (disa is DisaDurum.Calisiyor || disa is DisaDurum.Dogrulaniyor) DisaServisi.sonProje else null
    BackHandler(enabled = secim != null || arsivde) { if (secim != null) secim = null else arsivde = false }
    LaunchedEffect(arsivde) { secim = null }

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
                    DropdownMenuItem(text = { Text("Hata kaydını paylaş") }, onClick = { menu = false; kayitPaylas(ctx) })
                    DropdownMenuItem(text = { Text(if (Renk.gunduz) "Gece teması" else "Gündüz teması") }, onClick = { menu = false; Renk.degistir(ctx) })
                    DropdownMenuItem(text = { Text(if (Duzen.yaziliMenu) "Alt başlıklar: simgeli" else "Alt başlıklar: yazılı (önceki)") }, onClick = { menu = false; Duzen.degistir(ctx) })
                    DropdownMenuItem(text = { Text(if (Duzen.eskiSerit) "Zaman çizelgesi: çok satırlı" else "Zaman çizelgesi: klip şeridi (önceki)") }, onClick = { menu = false; Duzen.seritDegistir(ctx) })
                    DropdownMenuItem(text = { Text("Pil kısıtlamasını kaldır") }, onClick = { menu = false; pilAyari(ctx) })
                    DropdownMenuItem(text = { Text("Sürüm ${BuildConfigBilgi.surum(ctx)}") }, onClick = { menu = false })
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(16.dp)).background(Renk.turuncu).clickable(onClick = yeni).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("＋", color = Renk.turuncuM, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Yeni video", color = Renk.turuncuM, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(
                        Modifier.weight(1f).height(108.dp).clip(RoundedCornerShape(16.dp)).background(Renk.kart2).clickable(onClick = webAc).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("◑", color = Renk.turkuaz, fontSize = 26.sp)
                        Column {
                            Text("Fotoğraf", color = Renk.yazi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text("+ önceki Stüdyo (web)", color = Renk.soluk, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (disa != DisaDurum.Bos) item { DisaSerit(disa) { DisaServisi.sonProje?.let { id -> if (Depo.yukle(id) != null) ac(id) } } }
            stickyHeader {
                val s = secim
                Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(top = 6.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when { s != null -> "${s.size} seçili"; arsivde -> "Arşiv"; else -> "Son çalışmalarım" },
                            color = Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                        )
                        if (s == null) {
                            if (arsivde || arsivSayisi > 0) BaslikDugmesi(if (arsivde) "← Çalışmalarım" else "Arşiv · $arsivSayisi") { arsivde = !arsivde }
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
            if (gorunen.isEmpty()) item {
                Text(
                    if (arsivde) "Arşiv boş."
                    else "Henüz video projesi yok. “Yeni video” ile başla. Web Stüdyo'daki eski projeler “Fotoğraf + önceki Stüdyo” içinde duruyor.",
                    color = Renk.soluk, fontSize = 13.sp)
            }
            items(gorunen, key = { it.id }) { p ->
                val s = secim
                ProjeKarti(p,
                    ac = { if (s != null) secim = if (p.id in s) s - p.id else s + p.id else ac(p.id) },
                    cogalt = { Depo.cogalt(p); projeler = Depo.listele() },
                    adDegistir = { adDegis = p },
                    sil = { silinecek = p },
                    arsivle = { Depo.arsivle(listOf(p), !p.arsiv); projeler = Depo.listele() },
                    secimde = s != null, secili = s?.contains(p.id) == true,
                    uzunBas = { secim = (s ?: emptySet()) + p.id })
            }
        }
    }

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
    secimde: Boolean, secili: Boolean, uzunBas: () -> Unit,
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
        KlipResmi(p.klipler.firstOrNull(), Modifier.width(118.dp).height(68.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.ad, color = Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.klipler.size} klip · ${sureYaz(p.toplamMs)} · ${p.bicim}", color = Renk.soluk, fontSize = 12.5.sp)
            Text(SimpleDateFormat("d MMM yyyy HH:mm", TR).format(Date(p.guncelleme)), color = Renk.soluk, fontSize = 12.sp)
        }
        if (!secimde) Box {
            TextButton(onClick = { menu = true }) { Text("⋯", color = Renk.yazi, fontSize = 22.sp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Aç") }, onClick = { menu = false; ac() })
                DropdownMenuItem(text = { Text("Çoğalt") }, onClick = { menu = false; cogalt() })
                DropdownMenuItem(text = { Text("Adını değiştir") }, onClick = { menu = false; adDegistir() })
                DropdownMenuItem(text = { Text(if (p.arsiv) "Arşivden çıkar" else "Arşivle") }, onClick = { menu = false; arsivle() })
                DropdownMenuItem(text = { Text("Seç") }, onClick = { menu = false; uzunBas() })
                DropdownMenuItem(text = { Text("Sil", color = Renk.kirmizi) }, onClick = { menu = false; sil() })
            }
        }
    }
}

/** Başlık yanındaki ince çizgili küçük düğme. */
@Composable
fun BaslikDugmesi(metin: String, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(16.dp)
    Text(metin, color = Renk.turkuaz, fontSize = 12.5.sp, fontWeight = FontWeight.Light,
        modifier = Modifier.clip(sekil).border(0.8.dp, Renk.turkuaz.copy(alpha = 0.5f), sekil).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp))
}

fun pilAyari(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
}

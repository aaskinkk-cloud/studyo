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
private fun InceDugme(metin: String, renk: Color, etkin: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled = etkin, onClick = onClick).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(metin, color = if (etkin) renk else renk.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Normal, maxLines = 1)
    }
}

private val HAP_SIMGE = mapOf(
    "klip" to "klip", "ekle" to "ekle", "nesne" to "katman", "bicim" to "bicim",
    "sablon" to "sihir", "gecis" to "gecis", "kadraj" to "kadraj", "hareket" to "hareket",
    "klipses" to "ses", "muzik" to "nota", "seslendirme" to "mikrofon",
    "yazi" to "metin", "takip" to "takip", "altyazi" to "altyazi",
    "logo" to "logo", "kapak" to "kapak", "disa" to "disa",
)

private val BOLUMLER = listOf(
    Bolum("duzenle", "Düzenle", "makas", listOf("klip" to "Klip", "ekle" to "Ekle", "nesne" to "Nesne", "bicim" to "Biçim")),
    Bolum("gorunum", "Görünüm", "gorunum", listOf("sablon" to "Şablon", "gecis" to "Geçiş", "kadraj" to "Kadraj", "hareket" to "Hareket")),
    Bolum("ses", "Ses", "nota", listOf("klipses" to "Klip sesi", "muzik" to "Müzik", "seslendirme" to "Seslendirme")),
    Bolum("metin", "Metin", "metin", listOf("yazi" to "Yazı", "takip" to "Takip", "altyazi" to "Altyazı")),
    Bolum("yayin", "Yayın", "yildiz", listOf("logo" to "Logo", "kapak" to "Kapak", "disa" to "Dışa aktar")),
)

@Composable
fun EditorEkrani(id: String, bekleyen: List<Uri>, bekleyenAlindi: () -> Unit, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val vm = remember(id) { EditorDurum(ctx.applicationContext, id) }
    DisposableEffect(vm) { onDispose { vm.kapat() } }
    LaunchedEffect(bekleyen) { if (bekleyen.isNotEmpty()) { vm.ekle(bekleyen); bekleyenAlindi() } }

    val p by vm.proje.collectAsState()
    val sec by vm.secili.collectAsState()
    val mesaj by vm.mesaj.collectAsState()
    val uyari by vm.uyari.collectAsState()
    val mesgul by vm.mesgul.collectAsState()
    var bolum by rememberSaveable { mutableStateOf("duzenle") }
    var panelAcik by rememberSaveable { mutableStateOf(true) }
    val haplar = remember { mutableStateMapOf("duzenle" to "klip", "gorunum" to "sablon", "ses" to "muzik", "metin" to "yazi", "yayin" to "logo") }
    var disaAcik by rememberSaveable { mutableStateOf(false) }

    val ekleSecici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.ekle(it) }
    val ekle = { ekleSecici.launch(arrayOf("video/*", "image/*")) }

    val cik = { vm.kaydetSimdi(false); kapat() }
    if (disaAcik) {
        DisaEkrani(vm) { disaAcik = false }
        return
    }
    BackHandler { if (panelAcik) panelAcik = false else cik() }

    LaunchedEffect(mesaj) { if (mesaj != null) { delay(2200); vm.mesaj.value = null } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Renk.zemin)) {
        // Sade düzen (InShot gibi): önizleme ekranın %42'si ve hep görünür; panel açılınca klip şeridinin yerini alır.
        val ekranYuk = maxHeight
        val onizlemeYuk = (ekranYuk * Duzen.onizlemeOran).coerceAtLeast(180.dp)
        Column(Modifier.fillMaxSize().imePadding()) {
            // ---- üst çubuk: kamera bölgesi de başlığın renginde ----
            Row(
                Modifier.fillMaxWidth().background(Renk.kart)
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InceDugme("← Geri", Renk.yazi, onClick = cik)
                var adMenu by remember { mutableStateOf(false) }
                var adDuzen by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { adMenu = true }.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.ad, color = Renk.soluk, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
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
                InceDugme("Kaydet", Renk.turkuaz) { vm.kaydetSimdi() }
                Box(Modifier.width(1.dp).height(16.dp).background(Renk.cizgi))
                InceDugme("Dışa Aktar", Renk.turuncu, etkin = p.klipler.isNotEmpty()) { disaAcik = true }
            }
            val nesneModu = panelAcik && bolum == "duzenle" && haplar["duzenle"] == "nesne"
            OnizlemeKatmanli(vm, p, nesneModu, Modifier.fillMaxWidth().height(onizlemeYuk).padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(18.dp)))
            // tutamaç: sürükle = önizlemeyi büyüt/küçült (ayar alanı genişler), çift dokun = varsayılan boy
            val ctxT = LocalContext.current
            val yog = LocalDensity.current
            Box(Modifier.fillMaxWidth().height(26.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { Duzen.oranKaydet(ctxT) }) { ch, d ->
                        ch.consume()
                        val yukPx = with(yog) { ekranYuk.toPx() }
                        Duzen.onizlemeOran = (Duzen.onizlemeOran + d / yukPx).coerceIn(0.25f, 0.75f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { Duzen.onizlemeOran = 0.5f; Duzen.oranKaydet(ctxT) }) },
                contentAlignment = Alignment.Center) {
                Box(Modifier.width(64.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Renk.turuncu.copy(alpha = 0.8f)))
            }
            OynaticiSatiri(vm, p)
            Column(Modifier.fillMaxWidth().weight(1f)) {
                if (!panelAcik) {
                    Spacer(Modifier.weight(1f))
                    KlipSeridi(vm, p, sec, ekle)
                    Spacer(Modifier.weight(1f))
                } else {
                    // klip seçimi gereken bölümlerde küçük şerit panelin üstünde kalır
                    val klipli = haplar[bolum] in setOf("klip", "kadraj", "hareket", "klipses", "yazi", "takip")
                    if (klipli) KlipSeridi(vm, p, sec, ekle, kucuk = true)
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                        Panel(vm, p, sec, bolum, haplar[bolum] ?: "", ekle)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ---- haplar + 5 bölüm ----
            val b = BOLUMLER.first { it.anahtar == bolum }
            if (!Duzen.simgeliMenu) {
                Row(
                    Modifier.fillMaxWidth().background(Renk.kart).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    b.haplar.forEach { (k, ad) ->
                        HapEski(ad, panelAcik && haplar[bolum] == k) {
                            if (k == "disa") disaAcik = true else { haplar[bolum] = k; panelAcik = true }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().background(Renk.kart).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 4.dp)) {
                    BOLUMLER.forEach { bb ->
                        val on = bb.anahtar == bolum && panelAcik
                        Column(
                            Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).clickable {
                                if (bb.anahtar == bolum && panelAcik) panelAcik = false else { bolum = bb.anahtar; panelAcik = true }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text(ESKI_SIMGE[bb.anahtar] ?: "", color = if (on) Renk.turuncu else Renk.soluk, fontSize = 20.sp)
                            Text(bb.ad, color = if (on) Renk.turuncu else Renk.soluk, fontSize = 11.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                b.haplar.forEach { (k, ad) ->
                    Hap(ad, panelAcik && haplar[bolum] == k, HAP_SIMGE[k], Modifier.weight(1f)) {
                        if (k == "disa") disaAcik = true
                        else if (panelAcik && haplar[bolum] == k) panelAcik = false
                        else { haplar[bolum] = k; panelAcik = true }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Renk.kart)
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BOLUMLER.forEachIndexed { i, bb ->
                    if (i > 0) Box(Modifier.width(1.dp).height(30.dp).background(Renk.cizgi))
                    val on = bb.anahtar == bolum
                    Column(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp)).clickable {
                            if (bb.anahtar == bolum && panelAcik) panelAcik = false else { bolum = bb.anahtar; panelAcik = true }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Simge(bb.simge, if (on) Renk.turuncu else Renk.yazi, 24.dp)
                        Spacer(Modifier.height(3.dp))
                        Text(bb.ad, color = if (on) Renk.turuncu else Renk.yazi, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
            }
        }
        mesgul?.let { m ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Renk.kart).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Renk.turuncu)
                    Spacer(Modifier.height(10.dp))
                    Text(m, color = Renk.yazi)
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
            var kb by remember { mutableFloatStateOf(1f) }
            LaunchedEffect(k?.id, k?.kenBurns, k?.fotoSureMs) {
                if (k != null && k.foto && k.kenBurns) {
                    while (true) {
                        withFrameNanos { }
                        kb = 1f + 0.08f * (vm.player.currentPosition.toFloat() / k.fotoSureMs).coerceIn(0f, 1f)
                    }
                } else {
                    kb = 1f
                }
            }
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        player = vm.player
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { v ->
                    v.resizeMode = if (k?.doldur == true) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = kb; scaleY = kb },
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
        Modifier.fillMaxWidth().height(30.dp)
            .pointerInput(toplam) { detectTapGestures { o -> git((o.x / size.width * toplam).toLong()) } }
            .pointerInput(toplam) { detectHorizontalDragGestures { ch, _ -> ch.consume(); git((ch.position.x / size.width * toplam).toLong().coerceIn(0, toplam)) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val w = maxWidth
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Renk.cizgi))
        Box(Modifier.width(w * f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(listOf(Renk.turuncu, Renk.turuncu.copy(alpha = 0.85f)))))
        Box(Modifier.offset(x = (w * f - 3.dp).coerceIn(0.dp, w - 6.dp)).width(6.dp).height(26.dp).clip(RoundedCornerShape(3.dp)).background(Renk.turuncu))
    }
}

@Composable
private fun OynaticiSatiri(vm: EditorDurum, p: Proje) {
    val konum by vm.konum.collectAsState()
    val oyn by vm.oynuyor.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(sureOndalik(konum), color = Renk.yazi, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(76.dp))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.onceki() }, contentAlignment = Alignment.Center) { Simge("onceki", Renk.yazi, 28.dp) }
            Spacer(Modifier.width(14.dp))
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Renk.turuncu, Renk.turuncu.copy(alpha = 0.85f)))).clickable { vm.oynatDurdur() },
                contentAlignment = Alignment.Center,
            ) { Simge(if (oyn) "durdur" else "oynat", Color(0xFF111111), 30.dp) }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.size(48.dp).clip(CircleShape).clickable { vm.sonraki() }, contentAlignment = Alignment.Center) { Simge("sonraki", Renk.yazi, 28.dp) }
            Spacer(Modifier.weight(1f))
            Text(sureYaz(p.toplamMs), color = Renk.soluk, fontSize = 15.sp, textAlign = TextAlign.End, modifier = Modifier.width(76.dp))
        }
        ZamanCubugu(konum.coerceIn(0, toplam), toplam) { vm.git(it) }
    }
}

@Composable
private fun KlipSeridi(vm: EditorDurum, p: Proje, sec: Int, ekle: () -> Unit, kucuk: Boolean = false) {
    val durum = rememberLazyListState()
    LaunchedEffect(sec) { if (sec in p.klipler.indices) durum.animateScrollToItem(sec) }
    val kw = if (kucuk) 92.dp else 136.dp
    LazyRow(
        state = durum,
        modifier = Modifier.fillMaxWidth().height(if (kucuk) 62.dp else 96.dp).padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(p.klipler, key = { _, k -> k.id }) { i, k ->
            val sekil = RoundedCornerShape(14.dp)
            Box(
                Modifier.width(kw).fillMaxHeight().clip(sekil)
                    .border(if (i == sec) 3.dp else 1.5.dp, if (i == sec) Renk.turuncu else Renk.turkuaz.copy(alpha = 0.7f), sekil)
                    .clickable { vm.sec(i) },
            ) {
                KlipResmi(k, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(if (kucuk) 18.dp else 24.dp).clip(CircleShape).background(Color(0xCC0E151B)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Color.White, fontSize = if (kucuk) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
                }
                val rozet = buildList { if (k.foto) add("Foto"); if (k.sessiz || (!k.foto && !k.sesVar)) add("Sessiz"); if (k.yazi?.metin?.isNotBlank() == true) add("T") }
                if (rozet.isNotEmpty()) Text(rozet.joinToString(" · "), color = Color.White, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp))
                if (!kucuk) Text(sureOndalik(k.sureMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC0E151B)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        item {
            Box(
                Modifier.width(if (kucuk) 70.dp else 110.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Renk.turuncu, RoundedCornerShape(14.dp)).clickable { ekle() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Simge("ekle", Renk.turuncu, if (kucuk) 20.dp else 28.dp)
                    if (!kucuk) Text("Ekle", color = Renk.turuncu, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
    val ek = buildList {
        p.muzik?.let { add("♪ ${it.ad}") }
        if (p.sesler.isNotEmpty()) add("🎙 ${p.sesler.size} seslendirme")
        if (p.logo != null) add("◎ logo")
    }
    if (ek.isNotEmpty() && !kucuk) Text(ek.joinToString("   "), color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp))
}

@Composable
private fun Panel(vm: EditorDurum, p: Proje, sec: Int, bolum: String, hap: String, ekle: () -> Unit) {
    val k = p.klipler.getOrNull(sec)
    when (hap) {
        "ekle" -> PanelEkle(ekle)
        "bicim" -> PanelBicim(vm, p)
        "muzik" -> PanelMuzik(vm, p)
        "seslendirme" -> PanelSeslendirme(vm, p)
        "logo" -> PanelLogo(vm, p)
        "sablon" -> PanelSablon(vm, p)
        "gecis" -> PanelGecis(vm, p)
        "altyazi" -> PanelAltyazi(vm, p)
        "kapak" -> PanelKapak(vm, p)
        "nesne" -> PanelNesne(vm, p)
        else -> if (k == null) {
            Text("Önce bir klip ekle ya da seç.", color = Renk.soluk, modifier = Modifier.padding(16.dp))
        } else when (hap) {
            "klip" -> PanelKlip(vm, p, sec, k)
            "kadraj" -> PanelKadraj(vm, k)
            "hareket" -> PanelHareket(vm, k)
            "klipses" -> PanelKlipSes(vm, k)
            "yazi" -> PanelYazi(vm, k)
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
            )
            Etiketli("Başlangıç · Bitiş", "${sureOndalik(aralik.start.toLong())} · ${sureOndalik(aralik.endInclusive.toLong())}")
            Etiketli("Süre", sureOndalik((aralik.endInclusive - aralik.start).toLong()))
        } else {
            var sn by remember(k.id, k.fotoSureMs) { mutableFloatStateOf(k.fotoSureMs / 1000f) }
            Spacer(Modifier.height(8.dp))
            Etiketli("Fotoğraf süresi", String.format(TR, "%.1f sn", sn))
            Slider(
                value = sn, onValueChange = { sn = it }, valueRange = 1f..15f,
                onValueChangeFinished = { vm.klipGuncelle(true) { it.copy(fotoSureMs = (sn * 1000).toLong()) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
            )
        }
        var silOnay by remember(k.id) { mutableStateOf(false) }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dugme("◀ Sola", Modifier.weight(1f), etkin = sec > 0) { vm.tasi(-1) }
            Dugme("Sağa ▶", Modifier.weight(1f), etkin = sec < p.klipler.lastIndex) { vm.tasi(1) }
            Dugme("Çoğalt", Modifier.weight(1f)) { vm.klipCogalt() }
            Dugme(if (silOnay) "Emin misin?" else "Sil", Modifier.weight(1f), renk = if (silOnay) Renk.kirmizi else Renk.kart2) {
                if (silOnay) { silOnay = false; vm.klipSil() } else silOnay = true
            }
        }
    }
}

@Composable
private fun PanelEkle(ekle: () -> Unit) {
    Kart("Klip ekle") {
        Dugme("＋ Video veya fotoğraf seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { ekle() }
        Text("Seçilen klipler seçili klibin arkasına eklenir. Videolar kopyalanmaz, telefondaki yerinden okunur; boyut sınırı yoktur (1 GB, 2 GB…). " +
            "Telefonun çözemediği biçimler eklenmez ve nedeni yazılır.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PanelBicim(vm: EditorDurum, p: Proje) {
    Kart("Video biçimi") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecimKarti("16:9", "YouTube\nyatay", p.bicim == "16:9", Modifier.weight(1f)) { vm.bicim("16:9") }
            SecimKarti("9:16", "Shorts\ndikey", p.bicim == "9:16", Modifier.weight(1f)) { vm.bicim("9:16") }
            SecimKarti("1:1", "Kare\n", p.bicim == "1:1", Modifier.weight(1f)) { vm.bicim("1:1") }
        }
    }
}

@Composable
private fun PanelKadraj(vm: EditorDurum, k: Klip) {
    Kart("Kadraj") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecimKarti("Sığdır", "Tamamı görünür,\nkenarlar siyah kalabilir", !k.doldur, Modifier.weight(1f)) { vm.klipGuncelle { it.copy(doldur = false) } }
            SecimKarti("Doldur", "Kare dolar,\ntaşan kısım kesilir", k.doldur, Modifier.weight(1f)) { vm.klipGuncelle { it.copy(doldur = true) } }
        }
        Spacer(Modifier.height(8.dp))
        GenisDugme("Tüm kliplere uygula", "makas") { vm.hepsine({ it.copy(doldur = k.doldur) }); vm.mesaj.value = "Tüm kliplere uygulandı" }
    }
}

@Composable
private fun PanelHareket(vm: EditorDurum, k: Klip) {
    Kart("Fotoğraf hareketi") {
        if (!k.foto) {
            Text("Bu ayar fotoğraf klipleri içindir. Seçili klip bir video.", color = Renk.soluk, fontSize = 13.sp)
        } else {
            AnahtarSatiri("Yavaş yakınlaşma", k.kenBurns) { v -> vm.klipGuncelle { it.copy(kenBurns = v) } }
            GenisDugme("Tüm fotoğraflara uygula", "makas") { vm.hepsine({ if (it.foto) it.copy(kenBurns = k.kenBurns) else it }); vm.mesaj.value = "Tüm fotoğraflara uygulandı" }
        }
    }
}

@Composable
private fun PanelKlipSes(vm: EditorDurum, k: Klip) {
    Kart("Klip sesi") {
        if (k.foto || !k.sesVar) {
            Text(if (k.foto) "Fotoğraf kliplerinde ses yok." else "Bu videoda ses izi yok (ya da telefon çözemiyor).", color = Renk.soluk, fontSize = 13.sp)
        } else {
            AnahtarSatiri("Sessiz", k.sessiz) { v -> vm.klipGuncelle { it.copy(sessiz = v) } }
            var s by remember(k.id, k.ses) { mutableFloatStateOf(k.ses) }
            Etiketli("Ses düzeyi", "%${(s * 100).roundToInt()}")
            Slider(
                value = s, onValueChange = { s = it }, valueRange = 0f..1.5f, enabled = !k.sessiz,
                onValueChangeFinished = { vm.klipGuncelle { it.copy(ses = s) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi),
            )
            Text("%100 üstü yalnız dışa aktarmada yükseltilir; önizleme en fazla %100 çalar.", color = Renk.soluk, fontSize = 12.sp)
            GenisDugme("Tüm kliplere uygula", "makas", Modifier.padding(top = 6.dp)) { vm.hepsine({ it.copy(ses = k.ses, sessiz = k.sessiz) }); vm.mesaj.value = "Tüm kliplere uygulandı" }
        }
    }
}

@Composable
private fun PanelMuzik(vm: EditorDurum, p: Proje) {
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.muzikSec(it) } }
    Kart("Arka plan müziği") {
        val m = p.muzik
        if (m == null) {
            Dugme("♪ Müzik seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { sec.launch(arrayOf("audio/*")) }
            Text("Telefondaki bir ses dosyasını seç (MP3, M4A, WAV…).", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Text(m.ad, color = Renk.yazi, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.sureMs > 0) Text("Süre ${sureYaz(m.sureMs)}", color = Renk.soluk, fontSize = 12.sp)
            var s by remember(m.uri, m.ses) { mutableFloatStateOf(m.ses) }
            Etiketli("Müzik sesi", "%${(s * 100).roundToInt()}")
            Slider(value = s, onValueChange = { s = it }, valueRange = 0f..1f,
                onValueChangeFinished = { vm.muzikDegis { it.copy(ses = s) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
            AnahtarSatiri("Video boyunca tekrarla", m.dongu) { v -> vm.muzikDegis { it.copy(dongu = v) } }
            DugmeSirasi("Değiştir" to { sec.launch(arrayOf("audio/*")) }, "Kaldır" to { vm.muzikKaldir() })
        }
    }
}

@Composable
private fun PanelSeslendirme(vm: EditorDurum, p: Proje) {
    val ctx = LocalContext.current
    val kaydediyor by vm.kaydediyor.collectAsState()
    val konum by vm.konum.collectAsState()
    val bas by vm.kayitBas.collectAsState()
    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.kayitBaslat() else vm.uyari.value = "Seslendirme için mikrofon izni gerekiyor."
    }
    Kart("Seslendirme") {
        if (!kaydediyor) {
            Dugme("● Kaydı başlat (${sureYaz(konum)} anından)", Modifier.fillMaxWidth(), renk = Renk.kirmizi, yaziRenk = Color.White, etkin = p.klipler.isNotEmpty()) {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.kayitBaslat()
                else izin.launch(Manifest.permission.RECORD_AUDIO)
            }
            Text("Video o andan oynar, sen konuşursun. Kayıt sırasında klip sesi ve müzik susar.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Dugme("■ Durdur · kayıt ${sureYaz((konum - bas).coerceAtLeast(0))}", Modifier.fillMaxWidth(), renk = Renk.kirmizi, yaziRenk = Color.White) { vm.kayitDurdur() }
        }
    }
    if (p.sesler.isNotEmpty()) Kart("Kayıtlar (${p.sesler.size})") {
        p.sesler.forEach { s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Renk.kart2).padding(10.dp)) {
                Text("${sureYaz(s.basMs)} → ${sureYaz(s.basMs + s.sureMs)}  (${sureYaz(s.sureMs)})", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
                var v by remember(s.id, s.ses) { mutableFloatStateOf(s.ses) }
                Etiketli("Ses", "%${(v * 100).roundToInt()}")
                Slider(value = v, onValueChange = { v = it }, valueRange = 0f..1.5f,
                    onValueChangeFinished = { vm.sesDegis(s.id) { it.copy(ses = v) } },
                    colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
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

private val RENKLER = listOf(0xFFFFFFFF to "Beyaz", 0xFFFFD54F to "Sarı", 0xFFF5A524 to "Turuncu", 0xFF2EC5D3 to "Turkuaz", 0xFF111111 to "Siyah")

@Composable
private fun PanelYazi(vm: EditorDurum, k: Klip) {
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
        Slider(value = b, onValueChange = { b = it }, valueRange = 0.03f..0.16f,
            onValueChangeFinished = { vm.klipGuncelle { it.copy(yazi = (it.yazi ?: Yazi()).copy(boyut = b)) } },
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RENKLER.forEach { (r, _) ->
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(r))
                        .border(if (y.renk == r) 3.dp else 1.dp, if (y.renk == r) Renk.turuncu else Renk.cizgi, RoundedCornerShape(10.dp))
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

@Composable
private fun PanelLogo(vm: EditorDurum, p: Proje) {
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.logoSec(it) } }
    Kart("Logo (tüm videoda)") {
        val l = p.logo
        if (l == null || l.yol.isEmpty()) {
            Dugme("◎ Logo seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { sec.launch(arrayOf("image/*")) }
            Text("PNG (saydam) ya da fotoğraf. Yuvarlak kırpma açık gelir.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("solUst" to "Sol üst", "sagUst" to "Sağ üst", "solAlt" to "Sol alt", "sagAlt" to "Sağ alt").forEach { (kk, ad) ->
                    SecimKarti(ad, "", l.konum == kk, Modifier.weight(1f)) { vm.logoDegis { it.copy(konum = kk) } }
                }
            }
            var b by remember(l.yol, l.boyut) { mutableFloatStateOf(l.boyut) }
            Etiketli("Boyut", "%${(b * 100).roundToInt()}")
            Slider(value = b, onValueChange = { b = it }, valueRange = 0.06f..0.35f,
                onValueChangeFinished = { vm.logoDegis { it.copy(boyut = b) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
            var o by remember(l.yol, l.opaklik) { mutableFloatStateOf(l.opaklik) }
            Etiketli("Opaklık", "%${(o * 100).roundToInt()}")
            Slider(value = o, onValueChange = { o = it }, valueRange = 0.2f..1f,
                onValueChangeFinished = { vm.logoDegis { it.copy(opaklik = o) } },
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
            AnahtarSatiri("Yuvarlak", l.yuvarlak) { v -> vm.logoDegis { it.copy(yuvarlak = v) } }
            DugmeSirasi("Değiştir" to { sec.launch(arrayOf("image/*")) }, "Kaldır" to { vm.logoKaldir() })
        }
    }
}

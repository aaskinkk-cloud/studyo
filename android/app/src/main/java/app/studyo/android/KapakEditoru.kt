package app.studyo.android

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// 3.63: Kapak düzenleyici (VITA düzeni, Ahmet'in onayladığı taslak). Zaman çizgisinin başındaki Kapak kutusundan açılır.
// Üstte ✕ · geri al / yinele · Kaydet; ortada kapak; altında Videodan seç / İçe aktar, kare şeridi ve araçlar
// (Şablon · Yazı · Efekt · Öneriler · Foto). Araç sayfalarında ✕ · başlık · ✓ satırı altta. Yayın › Kapak paneli aynen duruyor.

private val KLASIK_KAPAK = listOf("kalin" to "Kalın kenarlı", "kutulu" to "Kutulu", "bant" to "Renkli bant")

@Composable
fun KapakEditoru(vm: EditorDurum, kapat: () -> Unit) {
    val p by vm.proje.collectAsState()
    val resim by vm.kapakResmi.collectAsState()
    val mesaj by vm.mesaj.collectAsState()
    val mesgul by vm.mesgul.collectAsState()
    val uyari by vm.uyari.collectAsState()
    val geriVar by vm.geriVar.collectAsState()
    val ileriVar by vm.ileriVar.collectAsState()
    var arac by rememberSaveable { mutableStateOf("") }
    var aracOnce by remember { mutableStateOf<Kapak?>(null) }
    val iceAktar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.kapakIceAktar(u) }
    LaunchedEffect(Unit) { vm.player.pause(); vm.kapakCiz() }
    LaunchedEffect(mesaj) { if (mesaj != null) { delay(2200); vm.mesaj.value = null } }
    BackHandler { if (arac.isNotEmpty()) arac = "" else kapat() }

    val kp = p.kapak
    val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
    val iceMi = kp.kaynakFoto && kp.kaynakUri.isNotEmpty() && p.klipler.none { it.uri == kp.kaynakUri }
    val aracAc: (String) -> Unit = { a -> aracOnce = p.kapak; arac = a }

    Box(Modifier.fillMaxSize().background(Renk.zemin)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
            // ---- üst çubuk ----
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = kapat), contentAlignment = Alignment.Center) { Simge("kapat", Renk.yazi, 24.dp) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = geriVar) { vm.geriAl() }, contentAlignment = Alignment.Center) {
                    Simge("gerial", if (geriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), 22.dp) }
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = ileriVar) { vm.yinele() }, contentAlignment = Alignment.Center) {
                    Simge("yinele", if (ileriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), 22.dp) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.height(36.dp).clip(RoundedCornerShape(6.dp)).background(Renk.turuncu).clickable { vm.kapakKaydet() }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Kaydet", color = Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // ---- kapak önizlemesi ----
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(oran, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(4.dp)).background(Color.Black)) {
                    val r = resim
                    if (r != null) Image(r.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else KlipResmi(p.klipler.firstOrNull(), Modifier.fillMaxSize())
                }
            }
            if (arac.isEmpty()) {
                // ---- kaynak: videodan seç / içe aktar ----
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally)) {
                    SekmeYazisi("Videodan seç", !iceMi) { if (iceMi && p.klipler.isNotEmpty()) vm.kapakKareSec() }
                    SekmeYazisi("İçe aktar", iceMi) { iceAktar.launch(arrayOf("image/*")) }
                }
                if (p.klipler.isNotEmpty()) {
                    KareSecici(vm, p)
                    Text("Kare seçmek için kaydır ya da dokun", color = Renk.soluk, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                } else Text("Önce video ya da fotoğraf ekle; ya da İçe aktar ile bir fotoğraf seç.", color = Renk.soluk, fontSize = 12.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp))
                // ---- araçlar ----
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AracDugmesi("Şablon", "izgara") { aracAc("sablon") }
                    AracDugmesi("Yazı", "metin", kp.yazi.isNotBlank()) { aracAc("yazi") }
                    AracDugmesi("Efekt", "yildiz", kp.canli || kp.logo) { aracAc("efekt") }
                    AracDugmesi("Öneriler", "sihir") { aracAc("oneri") }
                    AracDugmesi("Foto", "fotoekle", iceMi) { iceAktar.launch(arrayOf("image/*")) }
                }
            } else {
                // ---- araç sayfası: içerik + altta ✕ · başlık · ✓ ----
                Column(Modifier.fillMaxWidth().background(Renk.kart)) {
                    when (arac) {
                        "sablon" -> SablonIzgara(vm, p)
                        else -> Column(Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            when (arac) {
                                "yazi" -> KapakYaziAraci(vm, kp)
                                "efekt" -> KapakEfektAraci(vm, kp)
                                else -> KapakOneriAraci(vm, kp)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).clickable {
                            aracOnce?.let { o -> if (o != p.kapak) vm.kapakDegis { o } }; arac = ""
                        }, contentAlignment = Alignment.Center) { Simge("kapat", Renk.yazi, 24.dp) }
                        Text(when (arac) { "sablon" -> "Şablon"; "yazi" -> "Yazı"; "efekt" -> "Efekt"; else -> "Öneriler" },
                            color = Renk.yazi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        Box(Modifier.size(46.dp).clip(CircleShape).clickable { arac = "" }, contentAlignment = Alignment.Center) { Simge("uygula", Renk.yazi, 26.dp) }
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
private fun SekmeYazisi(ad: String, on: Boolean, tik: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = tik).padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
        Box(Modifier.padding(top = 3.dp).width(24.dp).height(2.dp).background(if (on) Renk.yazi else Color.Transparent))
    }
}

@Composable
private fun AracDugmesi(ad: String, simge: String, isaret: Boolean = false, tik: () -> Unit) {
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = tik).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(30.dp)) {
            Simge(simge, Renk.yazi, 26.dp, Modifier.align(Alignment.Center))
            if (isaret) Box(Modifier.align(Alignment.TopEnd).size(7.dp).clip(CircleShape).background(Renk.yazi))
        }
        Text(ad, color = Renk.soluk, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Videonun tamamı küçük resim şeridi; beyaz çizgi oynatıcının anı. Kaydır/dokun = o kare kapak olur. */
@Composable
private fun KareSecici(vm: EditorDurum, p: Proje) {
    val konum by vm.konum.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1L)
    val f = (konum.toFloat() / toplam).coerceIn(0f, 1f)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp)
            .pointerInput(toplam) { detectTapGestures { o -> vm.git((o.x / size.width * toplam).toLong().coerceIn(0L, toplam - 1)); vm.kapakKareSec() } }
            .pointerInput(toplam) {
                detectHorizontalDragGestures(onDragEnd = { vm.kapakKareSec() }) { ch, _ ->
                    ch.consume(); vm.git((ch.position.x / size.width * toplam).toLong().coerceIn(0L, toplam - 1))
                }
            },
    ) {
        val w = maxWidth
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
            p.klipler.take(60).forEach { k -> KlipResmi(k, Modifier.weight(k.sureMs.coerceAtLeast(1L).toFloat()).fillMaxHeight()) }
        }
        Box(Modifier.fillMaxSize().border(1.dp, Renk.yazi.copy(alpha = 0.4f), RoundedCornerShape(6.dp)))
        Box(Modifier.offset(x = (w * f - 1.5.dp).coerceIn(0.dp, w - 3.dp)).width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.White))
    }
}

/** Şablon ızgarası: her şablon projenin kendi kapak karesi ve yazısıyla çizilir. */
@Composable
private fun SablonIzgara(vm: EditorDurum, p: Proje) {
    val kp = p.kapak
    var kat by rememberSaveable { mutableStateOf("tumu") }
    val liste = when (kat) { "klasik" -> KLASIK_KAPAK; "reels" -> Reels.STILLER; else -> KLASIK_KAPAK + Reels.STILLER }
    val kaynak by produceState<Bitmap?>(null, kp.kaynakUri, kp.kaynakMs) { value = vm.kapakKaynakKucuk() }
    val (oW, oH) = Disa.boyut(p.bicim, 360)
    val sutun = if (oW > oH) 2 else 3
    val ilk = p.klipler.firstOrNull()
    val yazi = kp.yazi.ifBlank { "Şefin *Doğası*" }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf("tumu" to "Tümü", "klasik" to "Klasik", "reels" to "Reels").forEach { (k, ad) -> SekmeYazisi(ad, kat == k) { kat = k } }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(sutun),
            modifier = Modifier.fillMaxWidth().height(290.dp).padding(horizontal = 10.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(liste.size) { n ->
                val (s, ad) = liste[n]
                val on = kp.stil == s
                val bmp by produceState<Bitmap?>(null, s, kaynak, yazi, kp.vurgu, kp.konum, kp.boyut, kp.bant, p.bicim, p.logo) {
                    value = withContext(Dispatchers.Default) { runCatching { Cizim.kapak(kp.copy(stil = s, yazi = yazi), kaynak, p.logo, oW, oH) }.getOrNull() }
                }
                Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (!on) vm.kapakDegis { it.copy(stil = s) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(oW.toFloat() / oH).clip(RoundedCornerShape(6.dp)).background(Renk.kart2)
                        .border(if (on) 2.5.dp else 0.5.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(6.dp))) {
                        if (kaynak == null && ilk != null) KlipResmi(ilk, Modifier.fillMaxSize().alpha(0.85f))
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                        if (on) OnayIsareti(Modifier.align(Alignment.TopEnd).padding(4.dp), 16.dp)
                    }
                    Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun KapakYaziAraci(vm: EditorDurum, kp: Kapak) {
    MetinEditoru(kp.yazi, "Kapak yazısı", { y -> vm.kapakDegis { it.copy(yazi = y) } }, vurguVar = true, vurguRenk = Color(kp.vurgu),
        renkler = VURGULAR.map { it.first }, seciliRenk = kp.vurgu, renkSec = { r -> vm.kapakDegis { it.copy(vurgu = r) } },
        boyut = kp.boyut, boyutAralik = 0.06f..0.18f, boyutDegis = { v -> vm.kapakDegis { it.copy(boyut = v) } })
    if (kp.stil.startsWith("r_")) {
        Spacer(Modifier.height(8.dp))
        MetinEditoru(kp.bant, "Bantlar (her satır ayrı bant)", { y -> vm.kapakDegis { it.copy(bant = y) } }, enCokSatir = 3)
    }
    Text("YERİ", color = Renk.soluk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    KonumSecici(kp.konum, Renk.tonla(Color(TON[0]))) { s -> vm.kapakDegis { it.copy(konum = s) } }
}

@Composable
private fun KapakEfektAraci(vm: EditorDurum, kp: Kapak) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        YuvarlakSecim("Canlı renkler", "gorunum", Color(TON[1]), kp.canli) { vm.kapakDegis { it.copy(canli = !it.canli) } }
        YuvarlakSecim("Logo", "logo", Color(TON[2]), kp.logo) { vm.kapakDegis { it.copy(logo = !it.logo) } }
    }
    AcilirNot("Canlı renkler: renkleri canlandırır, kenarları hafifçe karartır. Logo: projenin logosu kapağın köşesine konur.")
}

@Composable
private fun KapakOneriAraci(vm: EditorDurum, kp: Kapak) {
    val oneriler by vm.kapakOnerileri.collectAsState()
    val hazirlaniyor by vm.kapakOneriHazirlaniyor.collectAsState()
    LaunchedEffect(Unit) { if (oneriler.isEmpty()) vm.kapakOneriHazirla() }
    Text(if (hazirlaniyor) "En net ve canlı kareler aranıyor…" else "Dokun = kapak karesi olsun", color = Renk.soluk, fontSize = 12.5.sp)
    if (oneriler.isNotEmpty()) LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(oneriler.size) { n ->
            val (k, b) = oneriler[n]
            val on = k.kaynakUri == kp.kaynakUri && k.kaynakMs == kp.kaynakMs
            Image(b.asImageBitmap(), null, Modifier.height(150.dp).aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1)).clip(RoundedCornerShape(8.dp))
                .border(if (on) 2.5.dp else 0.5.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(8.dp))
                .clickable { vm.kapakOneriSec(k) }, contentScale = ContentScale.Crop)
        }
    }
    if (!hazirlaniyor) Text("Yeniden ara", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(8.dp))
            .clickable { vm.kapakOneriHazirla() }.padding(horizontal = 14.dp, vertical = 8.dp))
}

/** Zaman çizgisinin başındaki Kapak kutusu (VITA gibi): kapak karesi varsa kapak, yoksa ilk klip; dokun = Kapak düzenleyici. */
@Composable
fun KapakKutusu(vm: EditorDurum, p: Proje, modifier: Modifier = Modifier, tik: () -> Unit) {
    val resim by vm.kapakResmi.collectAsState()
    LaunchedEffect(p.kapak.kaynakUri) { if (p.kapak.kaynakUri.isNotEmpty() && vm.kapakResmi.value == null) vm.kapakCiz() }
    val sekil = RoundedCornerShape(8.dp)
    Box(modifier.clip(sekil).background(Color.Black).border(1.5.dp, Renk.yazi.copy(alpha = 0.85f), sekil).clickable(onClick = tik)) {
        val r = resim
        if (p.kapak.kaynakUri.isNotEmpty() && r != null) Image(r.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else KlipResmi(p.klipler.firstOrNull(), Modifier.fillMaxSize().alpha(0.6f))
        Column(Modifier.fillMaxSize().background(Color(0x66000000)), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Simge("degistir", Color.White, 18.dp)
            Text("Kapak", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

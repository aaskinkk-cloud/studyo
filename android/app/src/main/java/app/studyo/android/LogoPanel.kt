package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Yayın › Logo: kişisel logo kütüphanesi + projedeki logonun ayarları. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PanelLogoKutup(vm: EditorDurum, p: Proje) {
    // 4.49: kütüphane Hazır › Açılış efektleri'nde; burada bağlantı (önceki yerleşim Ayarlar'da seçenek)
    if (Duzen.logoKutupEski) LogoKutupKarti(vm, p)
    else Kart("Logo kütüphanem") {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Renk.turuncu.copy(alpha = 0.12f))
            .combinedClickable(onClick = { vm.gitIstek.value = if (Duzen.hazirEski) Triple("gorunum", "sablon", "giris") else Triple("hazirb", "hazirlar", "acilis") }).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Simge("logo", Renk.turuncu, 20.dp)
            Text(if (Duzen.hazirEski) "  Logo kütüphanesi: Görünüm › Şablon › Giriş" else "  Logo kütüphanesi: Hazır › Açılış efektleri", color = Renk.turuncu, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("›", color = Renk.turuncu, fontSize = 16.sp)
        }
    }
    PanelLogoAyarlar(vm, p)
}

/** 4.49: logo kütüphanesi kartı (yükle · boş · logolar) — Hazır › Açılış efektleri'nde ve (önceki yerleşimde) Yayın › Logo'da. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LogoKutupKarti(vm: EditorDurum, p: Proje) {
    val yukleyici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> vm.logolarYukle(l) }
    val yukle = { yukleyici.launch(arrayOf("image/png", "image/*")) }
    val kutup by LogoKutup.ogeler.collectAsState()
    var hazir by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { LogoKutup.yukle() }; hazir = true }
    var duzenlenen by remember { mutableStateOf<LogoKaydi?>(null) }
    val l = p.logo?.takeIf { it.yol.isNotEmpty() }

    Kart("Logo kütüphanem · ${kutup.size}") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Renk.turuncu.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = yukle).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Simge("ekle", Renk.turuncu, 26.dp) }
                    Text("Yükle", color = Renk.turuncu, fontSize = 11.sp, fontWeight = FontWeight.Light)
                }
            }
            // 4.34: Boş — ana logo yok (video boyunca sol üstte logo görünmez)
            item {
                val bos = l == null
                Column(Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).border(if (bos) 2.dp else 0.8.dp, if (bos) Renk.turuncu else Renk.cizgi, RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = { vm.logoKaldir(); vm.mesaj.value = "Logo yok (Boş)" }).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Simge("kapat", if (bos) Renk.turuncu else Renk.soluk, 24.dp) }
                    Text("Boş", color = if (bos) Renk.turuncu else Renk.yazi, fontSize = 11.sp, fontWeight = FontWeight.Light)
                }
            }
            items(kutup, key = { it.id }) { k ->
                val secili = l?.kutupId == k.id
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                        .border(if (secili) 2.dp else 0.8.dp, if (secili) Renk.turuncu else Renk.cizgi, RoundedCornerShape(12.dp))
                        .combinedClickable(onClick = { vm.logoUygula(k) }, onLongClick = { duzenlenen = k })
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LogoResmi(k.yol, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                    Text(k.ad, color = if (secili) Renk.turuncu else Renk.yazi, fontSize = 11.sp, fontWeight = FontWeight.Light,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
        Text(
            if (hazir && kutup.isEmpty()) "Henüz logo yok. “＋ Yükle” ile bir ya da birkaç logo seç (şeffaf PNG desteklenir)."
            else "Dokun: projeye ekle · Uzun bas: adını değiştir / sil. Logolar tüm projelerde kullanılır.",
            color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }

    duzenlenen?.let { k ->
        var ad by remember(k.id) { mutableStateOf(k.ad) }
        var silOnay by remember(k.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { duzenlenen = null },
            title = { Text(if (silOnay) "“${k.ad}” kütüphaneden silinsin mi?" else "Logo") },
            text = {
                if (silOnay) Text("Bu logoyu kullanan projelerdeki logo silinmez; yalnızca kütüphaneden kalkar.")
                else Column {
                    LogoResmi(k.yol, Modifier.size(90.dp).clip(RoundedCornerShape(10.dp)).align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(ad, { ad = it }, singleLine = true, label = { Text("Adı") })
                }
            },
            confirmButton = {
                if (silOnay) TextButton(onClick = { LogoKutup.sil(k.id); duzenlenen = null }) { Text("Sil", color = Renk.kirmizi) }
                else TextButton(onClick = { LogoKutup.adDegistir(k.id, ad); duzenlenen = null }) { Text("Kaydet") }
            },
            dismissButton = {
                Row {
                    if (!silOnay) TextButton(onClick = { silOnay = true }) { Text("Sil", color = Renk.kirmizi) }
                    TextButton(onClick = { duzenlenen = null }) { Text("Vazgeç") }
                }
            },
        )
    }
}

/** Yayın › Logo'nun kütüphane dışındaki bölümleri (logo geçişi, projedeki logo). */
@Composable
private fun PanelLogoAyarlar(vm: EditorDurum, p: Proje) {
    val l = p.logo?.takeIf { it.yol.isNotEmpty() }
    val yukleyici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { ll -> vm.logolarYukle(ll) }
    val yukle = { yukleyici.launch(arrayOf("image/png", "image/*")) }
    // 3.88: büyük logo geçişi (Ahmet'in isteği: aynı ya da başka logo, kaç kez / hangi aralıkla — geçiş türünden bağımsız)
    LogoGecisKarti(vm, p)

    if (l != null) Kart("Projedeki logo") {
        AcilirNot("Önizlemede logoyu parmağınla sürükleyerek istediğin yere koyabilirsin.")
        val serbest = l.x >= 0f && l.y >= 0f
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("solUst" to "Sol üst", "sagUst" to "Sağ üst", "solAlt" to "Sol alt", "sagAlt" to "Sağ alt").forEach { (kk, ad) ->
                SecimKarti(ad, "", !serbest && l.konum == kk, Modifier.weight(1f)) { vm.logoDegis { it.copy(konum = kk, x = -1f, y = -1f) } }
            }
        }
        if (serbest) Text("Serbest konumda (köşeye almak için yukarıdan seç).", color = Renk.turuncu, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        var b by remember(l.yol, l.boyut) { mutableFloatStateOf(l.boyut) }
        Etiketli("Boyut", "%${(b * 100).roundToInt()}")
        InceSlider(value = b, onValueChange = { b = it }, valueRange = 0.06f..0.40f, onValueChangeFinished = { vm.logoDegis { it.copy(boyut = b) } })
        var o by remember(l.yol, l.opaklik) { mutableFloatStateOf(l.opaklik) }
        Etiketli("Opaklık", "%${(o * 100).roundToInt()}")
        InceSlider(value = o, onValueChange = { o = it }, valueRange = 0.1f..1f, onValueChangeFinished = { vm.logoDegis { it.copy(opaklik = o) } })
        AnahtarSatiri("Yuvarlak kırp", l.yuvarlak) { v -> vm.logoDegis { it.copy(yuvarlak = v) } }
        // 3.72: tek dokunuşla varsayılan boyut ve opaklık
        if (kotlin.math.abs(l.boyut - 0.14f) > 0.005f || kotlin.math.abs(l.opaklik - 0.7f) > 0.005f)
            SimgeDugme("uygula", "Varsayılan · %14 · %70", Modifier.fillMaxWidth(), renk = Renk.turuncu) { vm.logoDegis { it.copy(boyut = 0.14f, opaklik = 0.7f) } }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimgeDugme("yukle", "Yeni logo yükle", Modifier.weight(1f), renk = Renk.turkuaz, onClick = yukle)
            SimgeDugme("sil", "Projeden kaldır", Modifier.weight(1f), renk = Renk.kirmizi) { vm.logoKaldir() }
        }
    }

}

/** Saydamlığı görünür kılan damalı zemin üzerinde logo. */
@Composable
fun LogoResmi(yol: String, modifier: Modifier) {
    val b by produceState<Bitmap?>(null, yol) {
        value = withContext(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(yol, o)
                var s = 1
                while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 160) s *= 2
                BitmapFactory.decodeFile(yol, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (_: Exception) { null }
        }
    }
    val a = Renk.kart2; val c = Renk.cizgi
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val k = 8.dp.toPx()
            var y = 0f; var sira = 0
            while (y < size.height) {
                var x = 0f; var i = sira
                while (x < size.width) { drawRect(if (i % 2 == 0) a else c, Offset(x, y), Size(k, k)); x += k; i++ }
                y += k; sira++
            }
        }
        b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) }
    }
}

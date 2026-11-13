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
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Text("＋", color = Renk.turuncu, fontSize = 26.sp) }
                    Text("Yükle", color = Renk.turuncu, fontSize = 11.sp, fontWeight = FontWeight.Light)
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

    if (l != null) Kart("Projedeki logo") {
        Text("Önizlemede logoyu parmağınla sürükleyerek istediğin yere koyabilirsin.", color = Renk.soluk, fontSize = 12.sp)
        val serbest = l.x >= 0f && l.y >= 0f
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("solUst" to "Sol üst", "sagUst" to "Sağ üst", "solAlt" to "Sol alt", "sagAlt" to "Sağ alt").forEach { (kk, ad) ->
                SecimKarti(ad, "", !serbest && l.konum == kk, Modifier.weight(1f)) { vm.logoDegis { it.copy(konum = kk, x = -1f, y = -1f) } }
            }
        }
        if (serbest) Text("Serbest konumda (köşeye almak için yukarıdan seç).", color = Renk.turuncu, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        var b by remember(l.yol, l.boyut) { mutableFloatStateOf(l.boyut) }
        Etiketli("Boyut", "%${(b * 100).roundToInt()}")
        Slider(value = b, onValueChange = { b = it }, valueRange = 0.06f..0.40f, onValueChangeFinished = { vm.logoDegis { it.copy(boyut = b) } },
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        var o by remember(l.yol, l.opaklik) { mutableFloatStateOf(l.opaklik) }
        Etiketli("Opaklık", "%${(o * 100).roundToInt()}")
        Slider(value = o, onValueChange = { o = it }, valueRange = 0.1f..1f, onValueChangeFinished = { vm.logoDegis { it.copy(opaklik = o) } },
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        AnahtarSatiri("Yuvarlak kırp", l.yuvarlak) { v -> vm.logoDegis { it.copy(yuvarlak = v) } }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IkonDugme("⤓", "Yeni yükle", Modifier.weight(1f), onClick = yukle)
            IkonDugme("✕", "Projeden kaldır", Modifier.weight(1f), renk = Renk.kirmizi) { vm.logoKaldir() }
        }
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

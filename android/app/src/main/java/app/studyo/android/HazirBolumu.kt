package app.studyo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 4.46: ana menüde bağımsız "Hazır" bölümü — Görünüm'deki Paket ve Hazır alt menüleri tek sayfada, büyük önizleme kartları ızgarası.
 *  Üstte Paket / Hazır sekmeleri. Eski yerleşim (Görünüm › Paket · Hazır) Ayarlar'da seçenek olarak durur (Duzen.hazirEski). */
@Composable
internal fun PanelHazir(vm: EditorDurum, p: Proje, alt: String? = null) {
    // 4.47: sayfa alt menünün yanındaki Paketler / Hazır ayarlar düğmelerinden gelir (alt); yazılı menüde (alt = null) sayfa içi sekmeler
    var sekmeIc by rememberSaveable { mutableStateOf("paket") }
    val sekme = alt ?: sekmeIc
    var son by remember { mutableStateOf<String?>(null) }   // son uygulanan paket (kartta ✓)
    val turuncu = Color(TON[3]); val yesil = Color(TON[1])
    // sekmeler: iki eşit hap
    if (alt == null) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Triple("paket", "Paketler", turuncu), Triple("hazir", "Hazır ayarlar", yesil)).forEach { (k, ad, ton) ->
            val on = sekme == k
            Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) ton.copy(alpha = 0.22f) else Renk.kart2)
                .then(if (on) Modifier.border(1.dp, ton, RoundedCornerShape(18.dp)) else Modifier).clickable { sekmeIc = k }, contentAlignment = Alignment.Center) {
                Text(ad, color = if (on) ton else Renk.soluk, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
    if (sekme == "girisb" || sekme == "acilis") { PanelSablon(vm, p, sekme); return }   // 4.48: giriş başlığı · açılış efektleri
    if (sekme == "paket") {
        Kart("Video biçimi", ton = turuncu) { BicimSatiri(vm, p) }
        Text("Tek dokunuşla giriş, geçiş, abone ve kapanış", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("youtube", "YouTube", "oynat") to "alt bant · parlama · kapanış",
                   Triple("reels", "Reels", "yildiz") to "renkli bant · yakınlaşma",
                   Triple("sinema", "Sinema", "kadraj") to "sinematik · siyahtan").forEachIndexed { i, (t, alt) ->
                val ton = Color(TON[(i + 3) % TON.size])
                BuyukKart(t.second, alt, t.third, ton, son == t.first, Modifier.weight(1f)) { vm.paketUygula(t.first); son = t.first }
            }
        }
        Text("Klipler, müzik ve yazılar değişmez.", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    } else {
        var acik by rememberSaveable { mutableStateOf("varsayilan") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BuyukKart("Varsayılan", "yeni projelerin ayarları", "uygula", yesil, acik == "varsayilan", Modifier.weight(1f)) { acik = "varsayilan" }
            BuyukKart("Kayıtlı ayarlarım", "sakla · geri yükle", "kaydet", Color(TON[2]), acik == "kayitli", Modifier.weight(1f)) { acik = "kayitli" }
        }
        Spacer(Modifier.height(4.dp))
        if (acik == "varsayilan") Kart("Varsayılan ayarlar", ton = yesil) { VarsayilanSayfa(vm) } else KayitliAyarlarSayfasi(vm)
    }
}

/** Büyük önizleme kartı: tonlu degrade zemin, büyük simge, başlık, alt satır; seçiliyken çerçeve + ✓. */
@Composable
internal fun BuyukKart(ad: String, alt: String, simge: String, ton: Color, secili: Boolean, mod: Modifier, tik: () -> Unit) {
    val sekil = RoundedCornerShape(16.dp)
    Column(mod.height(118.dp).clip(sekil)
        .background(Brush.verticalGradient(listOf(ton.copy(alpha = 0.30f), ton.copy(alpha = 0.08f))))
        .border(if (secili) 1.5.dp else 0.5.dp, if (secili) ton else ton.copy(alpha = 0.35f), sekil)
        .clickable { tik() }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(contentAlignment = Alignment.TopEnd) {
            Simge(simge, ton, 34.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text((if (secili) "✓ " else "") + ad, color = Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(alt, color = Renk.soluk, fontSize = 10.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 13.sp)
    }
}

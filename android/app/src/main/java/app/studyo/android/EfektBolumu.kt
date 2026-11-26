package app.studyo.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 3.59: Efekt bölümü (Ahmet'in onayladığı taslak): Filtre · Efekt · Geçiş. Küçük resimler yalnız üstteki videonun o anki karesinden;
 *  dokun = uygula (sonuç üstteki önizlemede), Yok = kaldır, Tümüne = bütün kliplere. */

/** Seçili klibin, oynatıcının bulunduğu andaki karesi (yoksa klibin ilk karesi), küçük boy. */
@Composable
fun AnlikKare(vm: EditorDurum, p: Proje, sec: Int, boy: Int = 220): Bitmap? {
    val ctx = LocalContext.current
    val k = p.klipler.getOrNull(sec) ?: return null
    val sira by vm.sira.collectAsState()
    val konum by vm.konum.collectAsState()
    val kaynakMs = if (k.foto) 0L else if (sira == sec) k.basMs + vm.player.currentPosition.coerceAtLeast(0) else k.basMs
    // yaklaşık 1 sn'lik adımlarla yenilenir (sararken her karede çözmesin)
    val adim = kaynakMs / 1000
    val b by produceState<Bitmap?>(null, k.id, k.uri, adim, konum / 1000) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (k.foto) Medya.ornekleCoz(java.io.File(android.net.Uri.parse(k.uri).path ?: ""), boy)
                else Medya.kareTam(ctx, android.net.Uri.parse(k.uri), kaynakMs, boy)
            }.getOrNull()
        }
    }
    return b
}

/** Geçişler başlığı biçiminde üst satır: solda Tümüne, ortada başlık (+✓), sağda boşluk. */
@Composable
private fun EfektBasligi(baslik: String, alt: String, etkin: Boolean, tumune: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = tumune).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Simge("cift", Renk.yazi, 22.dp); Text("Tümüne", color = Renk.soluk, fontSize = 10.5.sp)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(baslik, color = Renk.yazi.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (etkin) OnayIsareti(Modifier.padding(start = 6.dp))
            }
            Text(alt, color = Renk.soluk, fontSize = 11.sp)
        }
        Spacer(Modifier.width(56.dp))
    }
}

/** Önizlemeli kart: üstte efektli küçük resim, altta ad; seçili = renkli çerçeve + ✓. */
@Composable
fun OnizlemeKarti(ad: String, secili: Boolean, ton: Color, uret: suspend () -> Bitmap?, anahtar: Any?, tik: () -> Unit) {
    val bmp by produceState<Bitmap?>(null, anahtar) { value = withContext(Dispatchers.Default) { runCatching { uret() }.getOrNull() } }
    Column(Modifier.width(84.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = tik).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(78.dp, 56.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Renk.kart2)
                .border(if (secili) 2.5.dp else 0.5.dp, if (secili) ton else Renk.cizgi, RoundedCornerShape(10.dp))) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop) }
            }
            if (secili) OnayIsareti(Modifier.align(Alignment.TopEnd).padding(2.dp), 15.dp)
        }
        Text(ad, color = if (secili) Renk.yazi else Renk.soluk, fontSize = 11.sp, fontWeight = if (secili) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GucSatiri(etiket: String, deger: Float, ton: Color, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiket, color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.width(46.dp))
        InceSlider(value = v, onValueChange = { v = it }, valueRange = 0f..1f, onValueChangeFinished = { bitti(v) }, renk = ton,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(thumbColor = ton, activeTrackColor = ton, inactiveTrackColor = Renk.cizgi))
        Text("${(v * 100).roundToInt()}", color = ton, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}

@Composable
fun PanelFiltre(vm: EditorDurum, p: Proje, sec: Int) {
    val k = p.klipler.getOrNull(sec) ?: run { Text("Önce bir klip ekle ya da seç.", color = Renk.soluk, modifier = Modifier.padding(16.dp)); return }
    val kare = AnlikKare(vm, p, sec)
    val ton = Color(TON[3])
    // 4.29: filtreler üst üste birikir — dokun = ekle / çıkar; her etkin filtrenin gücü ayrı (biri diğerini silmez)
    fun etkin(kk: Klip): Map<String, Float> = (if (kk.filtre != "yok") mapOf(kk.filtre to kk.filtreGuc) else emptyMap()) + kk.ekFiltreler
    fun yaz(m: Map<String, Float>) = vm.klipGuncelle { it.copy(filtre = m.keys.firstOrNull() ?: "yok", filtreGuc = m.values.firstOrNull() ?: 0.8f, ekFiltreler = m.entries.drop(1).associate { e -> e.key to e.value }) }
    val et = etkin(k)
    EfektBasligi("Filtre", "Klip ${sec + 1}/${p.klipler.size}" + if (et.size > 1) " · ${et.size} filtre" else "", k.filtreAcik) {
        vm.hepsine({ it.copy(filtre = k.filtre, filtreGuc = k.filtreGuc, ekFiltreler = k.ekFiltreler) }); vm.mesaj.value = "Filtreler tüm kliplere uygulandı"
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnizlemeKarti("Yok", et.isEmpty(), ton, { kare }, kare) { yaz(emptyMap()) }
        RenkLut.FILTRELER.forEach { (t, ad) ->
            OnizlemeKarti(ad, t in et, ton, { kare?.let { RenkLut.uygula(it, RenkAyar(), t, 1f) } }, kare to t) {
                yaz(if (t in et) et - t else et + (t to if (et.isEmpty()) 0.8f else 0.5f))
            }
        }
    }
    et.forEach { (t, g) ->
        GucSatiri(RenkLut.FILTRELER.firstOrNull { it.first == t }?.second ?: t, g, ton) { v -> yaz(et.toMutableMap().also { it[t] = v }) }
    }
    Not2("Küçük resimler üstteki videonun şu anki karesinden. Sonuç önizlemede görünür; önizlemeye basılı tut = filtresiz.")
}

@Composable
fun PanelEfekt(vm: EditorDurum, p: Proje, sec: Int) {
    val k = p.klipler.getOrNull(sec) ?: run { Text("Önce bir klip ekle ya da seç.", color = Renk.soluk, modifier = Modifier.padding(16.dp)); return }
    val kare = AnlikKare(vm, p, sec)
    val ton = Color(TON[4])
    EfektBasligi("Efekt", "Klip ${sec + 1}/${p.klipler.size}", k.efekt != "yok") {
        vm.hepsine({ it.copy(efekt = k.efekt, efektGuc = k.efektGuc) }); vm.mesaj.value = "Efekt tüm kliplere uygulandı"
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnizlemeKarti("Yok", k.efekt == "yok", ton, { kare }, kare) { vm.klipGuncelle { it.copy(efekt = "yok") } }
        Efektler.LISTE.forEach { (t, ad) ->
            OnizlemeKarti(ad, k.efekt == t, ton, { kare?.let { Efektler.uygula(it, t, 0.85f) } }, kare to t) { vm.klipGuncelle { it.copy(efekt = t) } }
        }
    }
    if (k.efekt != "yok") GucSatiri("Güç", k.efektGuc, ton) { v -> vm.klipGuncelle { it.copy(efektGuc = v) } }
    Not2("Efekt klip boyunca sürer; oynatırken ve dışa aktarmada uygulanır. Durağanken sonucu önizlemede görürsün.")
}

@Composable
private fun Not2(m: String) = AcilirNot(m)   // 3.72: açıklamalar kapalı gelir


package app.studyo.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/** 3.72: şablon yazılarını ekranda düzenleme (giriş başlığı, abone hatırlatıcı, kapanış). Önizlemenin hemen altında açılır;
 *  her harf anında önizlemeye yansır. Çok satırlı alanlarda Enter = yeni satır/bant (kayıtta "|"). ✓ ya da Geri = kapat. */
private class EkranAlan(val etiket: String, val deger: String, val cokSatir: Boolean, val yaz: (String) -> Unit)

@Composable
fun EkranYaziDuzenleyici(vm: EditorDurum, p: Proje, tur: String) {
    val kapat = { vm.ekranDuzen.value = null }
    BackHandler { kapat() }
    val g = p.giris; val h = p.hatirlatici; val kp = p.kapanis
    val (baslik, alanlar) = when (tur) {
        "giris" -> "Giriş başlığı" to buildList<EkranAlan> {
            add(EkranAlan("Başlık", g.baslik, true) { y -> vm.girisDegis { it.copy(baslik = y) } })
            if (g.reels) add(EkranAlan(if (g.stil == "r_haber") "Tarih ve konum" else "Vurgu bantları", g.bant, true) { y -> vm.girisDegis { it.copy(bant = y) } })
            else add(EkranAlan("Alt başlık", g.alt, false) { y -> vm.girisDegis { it.copy(alt = y) } })
        }
        "abone" -> "Abone hatırlatıcı" to buildList<EkranAlan> {
            add(EkranAlan("Yazı", h.yazi, false) { y -> vm.hatirlaticiDegis { it.copy(yazi = y) } })
            add(EkranAlan("Küçük yazı", h.kucuk, false) { y -> vm.hatirlaticiDegis { it.copy(kucuk = y) } })
            if (h.stil != "klasik") add(EkranAlan("Düğme", h.dugme, false) { y -> vm.hatirlaticiDegis { it.copy(dugme = y) } })
        }
        else -> "Kapanış" to listOf(
            EkranAlan("Yazı", kp.yazi, false) { y -> vm.kapanisDegis(false) { it.copy(yazi = y) } },
            EkranAlan("Kanal adı", kp.kanal, false) { y -> vm.kapanisDegis(false) { it.copy(kanal = y) } },
        )
    }
    Column(Modifier.fillMaxWidth().background(Renk.kart).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Simge("degistir", Renk.turuncu, 16.dp)
            Text(baslik, color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp).weight(1f))
            if (tur == "giris") {
                KucukEylem("sihir", "Başka öneri") { vm.girisOneri(zorla = true) }
                KucukEylem("eksi", null) { vm.girisDegis { it.copy(olcek = ((it.olcek - 0.1f) * 10).let { v -> Math.round(v) / 10f }.coerceIn(0.6f, 1.6f)) } }
                KucukEylem("ekle", null) { vm.girisDegis { it.copy(olcek = ((it.olcek + 0.1f) * 10).let { v -> Math.round(v) / 10f }.coerceIn(0.6f, 1.6f)) } }
            }
            KucukEylem("uygula", "Tamam", vurgu = true, tik = kapat)
        }
        val ilkOdak = remember { FocusRequester() }
        alanlar.forEachIndexed { i, a ->
            key(tur, a.etiket) { EkranMetinAlani(a, if (i == 0) Modifier.focusRequester(ilkOdak) else Modifier) }
        }
        LaunchedEffect(tur) { runCatching { ilkOdak.requestFocus() } }
    }
}

@Composable
private fun KucukEylem(simge: String, yazi: String?, vurgu: Boolean = false, tik: () -> Unit) {
    Row(Modifier.padding(start = 4.dp).clip(RoundedCornerShape(14.dp)).background(if (vurgu) Renk.turuncu.copy(alpha = 0.16f) else Color.Transparent)
        .clickable(onClick = tik).padding(horizontal = if (yazi != null) 8.dp else 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Simge(simge, if (vurgu) Renk.turuncu else Renk.yazi, 15.dp)
        if (yazi != null) Text(yazi, color = if (vurgu) Renk.turuncu else Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** İnce, etiketli metin alanı; dışarıdan gelen değişiklik (ör. "Başka öneri") imleci bozmadan alana yansır. */
@Composable
private fun EkranMetinAlani(a: EkranAlan, modifier: Modifier) {
    fun gor(s: String) = if (a.cokSatir) s.replace('|', '\n') else s
    var tfv by remember { mutableStateOf(TextFieldValue(gor(a.deger), TextRange(gor(a.deger).length))) }
    LaunchedEffect(a.deger) { val v = gor(a.deger); if (v != tfv.text) tfv = TextFieldValue(v, TextRange(v.length)) }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(a.etiket, color = Renk.soluk, fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(78.dp))
        BasicTextField(
            value = tfv,
            onValueChange = { y ->
                val t = if (a.cokSatir) y else y.copy(text = y.text.replace("\n", " "))
                tfv = t; a.yaz(if (a.cokSatir) t.text.replace('\n', '|') else t.text)
            },
            modifier = modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Renk.kart2).border(1.dp, Renk.cizgi, RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
            textStyle = TextStyle(color = Renk.yazi, fontSize = 14.sp),
            cursorBrush = SolidColor(Renk.turuncu),
            singleLine = !a.cokSatir,
            maxLines = if (a.cokSatir) 3 else 1,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),   // 3.77
        )
        if (tfv.text.isNotEmpty()) Box(Modifier.padding(start = 2.dp).size(28.dp).clip(CircleShape).clickable { tfv = TextFieldValue(""); a.yaz("") },
            contentAlignment = Alignment.Center) { Simge("kapat", Renk.soluk, 13.dp) }
    }
}

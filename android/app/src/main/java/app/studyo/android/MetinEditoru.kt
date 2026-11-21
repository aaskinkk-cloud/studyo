package app.studyo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 3.58: kısa metin düzenleyici (kapak yazısı, giriş başlığı, bantlar).
 *  - Enter = alt satır / yeni blok; işaret yazmaya gerek yok (kayıtta "|" olarak saklanır, çizimler aynen çalışır).
 *  - "Kalın · vurgu" düğmesi: seçili kelimeleri vurgular; yıldız yazmaya gerek yok (kayıtta *…* olarak saklanır, kutuda yıldız görünmez,
 *    vurgulu kısım kalın ve vurgu renginde görünür).
 *  - İsteğe bağlı: vurgu rengi daireleri, yazı boyutu (A− / A+). */
@Composable
fun MetinEditoru(
    deger: String, etiket: String, onDegis: (String) -> Unit, modifier: Modifier = Modifier,
    vurguVar: Boolean = false, vurguRenk: Color = Renk.turuncu,
    renkler: List<Long>? = null, seciliRenk: Long = 0L, renkSec: ((Long) -> Unit)? = null,
    boyut: Float? = null, boyutAralik: ClosedFloatingPointRange<Float> = 0f..1f, boyutAdim: Float = 0.01f, boyutDegis: ((Float) -> Unit)? = null,
    enCokSatir: Int = 4,
) {
    fun gor(s: String) = s.replace('|', '\n')
    var tfv by remember { mutableStateOf(TextFieldValue(gor(deger), TextRange(gor(deger).length))) }
    LaunchedEffect(deger) { val g = gor(deger); if (g != tfv.text) tfv = TextFieldValue(g, TextRange(g.length)) }
    fun yaz(y: TextFieldValue) { tfv = y; onDegis(y.text.replace('\n', '|')) }

    Column(modifier.fillMaxWidth()) {
        // ---- araç çubuğu (yana kayar) ----
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (vurguVar) {
                AracHap("B", "Kalın · vurgu", vurguRenk, kalin = true) { yaz(vurguDegistir(tfv)) }
                if ('*' in tfv.text) AracHap("✕", "Vurguyu kaldır", Renk.soluk) { yaz(TextFieldValue(tfv.text.replace("*", ""), TextRange(tfv.text.replace("*", "").length))) }
            }
            AracHap("↵", "Yeni satır", Color(TON[2])) {
                val s = tfv.selection.min; val e = tfv.selection.max
                val t = tfv.text.substring(0, s) + "\n" + tfv.text.substring(e)
                yaz(TextFieldValue(t, TextRange(s + 1)))
            }
            if (boyut != null && boyutDegis != null) {
                AracHap("A−", "", Color(TON[0])) { boyutDegis((boyut - boyutAdim).coerceIn(boyutAralik.start, boyutAralik.endInclusive)) }
                Text("%${(boyut * 100).roundToInt()}", color = Color(TON[0]), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                AracHap("A+", "", Color(TON[0])) { boyutDegis((boyut + boyutAdim).coerceIn(boyutAralik.start, boyutAralik.endInclusive)) }
            }
            if (renkler != null && renkSec != null) renkler.forEach { r ->
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(r))
                    .border(if (seciliRenk == r) 2.5.dp else 0.5.dp, if (seciliRenk == r) Color.White else Renk.cizgi, CircleShape).clickable { renkSec(r) })
            }
        }
        OutlinedTextField(
            value = tfv, onValueChange = { yaz(it) }, modifier = Modifier.fillMaxWidth(),
            label = { Text(etiket) }, minLines = 2, maxLines = enCokSatir,
            visualTransformation = if (vurguVar) VurguGorunumu(vurguRenk) else VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),   // 3.77
        )
        Text("Enter = alt satır (yeni blok)" + if (vurguVar) " · kelimeyi seç → Kalın" else "", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun AracHap(simge: String, ad: String, ton: Color, kalin: Boolean = false, tik: () -> Unit) {
    val sekil = RoundedCornerShape(14.dp)
    Row(Modifier.height(30.dp).clip(sekil).border(1.dp, ton.copy(alpha = 0.7f), sekil).background(ton.copy(alpha = 0.12f)).clickable(onClick = tik)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(simge, color = ton, fontSize = 14.sp, fontWeight = if (kalin) FontWeight.Black else FontWeight.SemiBold)
        if (ad.isNotEmpty()) Text(ad, color = ton, fontSize = 11.5.sp, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Seçimin (seçim yoksa imlecin bulunduğu kelimenin) çevresine *…* ekler; zaten vurguluysa kaldırır. */
private fun vurguDegistir(v: TextFieldValue): TextFieldValue {
    val t = v.text
    var s = v.selection.min; var e = v.selection.max
    if (s == e) {   // kelimeyi bul
        while (s > 0 && !t[s - 1].isWhitespace() && t[s - 1] != '*') s--
        while (e < t.length && !t[e].isWhitespace() && t[e] != '*') e++
        if (s == e) return v
    }
    // seçim zaten *…* içindeyse kaldır
    if (s > 0 && e < t.length && t[s - 1] == '*' && t[e] == '*') {
        val y = t.substring(0, s - 1) + t.substring(s, e) + t.substring(e + 1)
        return TextFieldValue(y, TextRange(s - 1, e - 1))
    }
    val ic = t.substring(s, e).replace("*", "")
    val y = t.substring(0, s) + "*" + ic + "*" + t.substring(e)
    return TextFieldValue(y, TextRange(s + 1, s + 1 + ic.length))
}

/** Kutuda yıldızlar görünmez; *…* arası kalın ve vurgu renginde. */
private class VurguGorunumu(private val renk: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val t = text.text
        val o2t = IntArray(t.length + 1)
        val t2o = ArrayList<Int>(t.length + 1)
        var n = 0; var acik = false; var bas = 0
        val spanlar = ArrayList<Pair<Int, Int>>()
        val sb = StringBuilder()
        for (i in t.indices) {
            o2t[i] = n
            if (t[i] == '*') {
                if (acik) spanlar += bas to n else bas = n
                acik = !acik
            } else { t2o += i; sb.append(t[i]); n++ }
        }
        o2t[t.length] = n; t2o += t.length
        if (acik) spanlar += bas to n
        val a = buildAnnotatedString {
            append(sb.toString())
            for ((x, y) in spanlar) if (y > x) addStyle(SpanStyle(color = renk, fontWeight = FontWeight.Bold), x, y)
        }
        val m = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = o2t[offset.coerceIn(0, t.length)]
            override fun transformedToOriginal(offset: Int): Int = t2o[offset.coerceIn(0, t2o.size - 1)]
        }
        return TransformedText(a, m)
    }
    override fun equals(other: Any?) = other is VurguGorunumu && other.renk == renk
    override fun hashCode() = renk.hashCode()
}

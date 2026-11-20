package app.studyo.android

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/** 3.53: Renk ana bölümü. Üstte sabit: Geçişler biçiminde başlık (Tümüne · Renk ✓ · Sıfırla) ve yatay kayan ayar düğmeleri
 *  (3.56: önce/sonra kutusu kalktı; sonuç ana önizlemede görünür, önizlemeye basılı tut = önce). Altta yalnız seçili ayarın sayfası kayar.
 *  Veri değişmedi: klip.renk (RenkAyar); dışa aktarma ve önizleme aynı dönüşümü kullanır. */
private data class RenkAyarTanim(val anahtar: String, val ad: String, val simge: String, val renk: Long,
                                 val al: (RenkAyar) -> Float, val yaz: (RenkAyar, Float) -> RenkAyar, val enAz: Float = -1f, val aciklama: String = "")

private val RENK_AYARLARI = listOf(
    RenkAyarTanim("parlaklik", "Parlaklık", "g_beyaz", 0xFFFAC775, { it.parlaklik }, { a, v -> a.copy(parlaklik = v) }),
    RenkAyarTanim("kontrast", "Kontrast", "gorunum", 0xFFB4B2A9, { it.kontrast }, { a, v -> a.copy(kontrast = v) }),
    RenkAyarTanim("vurgular", "Vurgular", "yildiz", 0xFFEF9F27, { it.vurgular }, { a, v -> a.copy(vurgular = v) }, aciklama = "Sola: patlayan parlak alanları geri çeker."),
    RenkAyarTanim("beyazlar", "Beyazlar", "r_beyaz", 0xFFF1EFE8, { it.beyazlar }, { a, v -> a.copy(beyazlar = v) }, aciklama = "Sola: yalnız en beyaz alanları azaltır (gökyüzü, ışık)."),
    RenkAyarTanim("golgeler", "Gölgeler", "g_siyah", 0xFF85B7EB, { it.golgeler }, { a, v -> a.copy(golgeler = v) }, aciklama = "Sağa: karanlık alanları açar."),
    RenkAyarTanim("keskinlik", "Keskinlik", "r_keskin", 0xFF5DCAA5, { it.keskinlik }, { a, v -> a.copy(keskinlik = v) }, enAz = 0f, aciklama = "Ayrıntıları belirginleştirir; fazlası kumlanma yapar."),
    RenkAyarTanim("doygunluk", "Doygunluk", "r_damla", 0xFFED93B1, { it.doygunluk }, { a, v -> a.copy(doygunluk = v) }),
    RenkAyarTanim("sicaklik", "Sıcaklık", "r_termo", 0xFFF0997B, { it.sicaklik }, { a, v -> a.copy(sicaklik = v) }, aciklama = "Sola serin (mavi), sağa sıcak (sarı)."),
    RenkAyarTanim("ton", "Ton", "gecis", 0xFFAFA9EC, { it.ton }, { a, v -> a.copy(ton = v) }, aciklama = "Bütün renkleri renk çemberinde kaydırır."),
)

private val RENK_HAZIR = listOf(
    "Patlamayı azalt" to RenkAyar(vurgular = -0.6f, golgeler = 0.15f, kontrast = -0.05f, beyazlar = -0.3f),
    "Doğal" to RenkAyar(kontrast = 0.08f, doygunluk = 0.1f),
    "Canlı" to RenkAyar(kontrast = 0.15f, doygunluk = 0.35f, vurgular = -0.2f),
    "Net" to RenkAyar(kontrast = 0.1f, keskinlik = 0.35f),
    "Sıcak" to RenkAyar(sicaklik = 0.4f, doygunluk = 0.1f),
    "Serin" to RenkAyar(sicaklik = -0.35f),
    "Yeşili canlandır" to RenkAyar(hslDoy = List(8) { if (it == 3 || it == 2) 0.35f else 0f }, hslIsik = List(8) { if (it == 3) 0.1f else 0f }),
)

private fun hslDegisti(a: RenkAyar) = (a.hslTon + a.hslDoy + a.hslIsik).any { abs(it) > 0.005f }

/** 3.61: Renk alt menüsü (ana menü satırında / üstünde): Hazır · ayarlar · HSL. */
class RenkSekme(val anahtar: String, val ad: String, val simge: String, val renk: Long)
val RENK_SEKMELERI: List<RenkSekme> = listOf(RenkSekme("hazir", "Hazır", "sihir", 0xFFFAC775)) +
    RENK_AYARLARI.map { RenkSekme(it.anahtar, it.ad, it.simge, it.renk) } + RenkSekme("hsl", "HSL", "r_palet", 0xFF97C459)
fun renkSekmeDegisti(a: RenkAyar, k: String): Boolean = when (k) {
    "hazir" -> false; "hsl" -> hslDegisti(a)
    else -> RENK_AYARLARI.firstOrNull { it.anahtar == k }?.let { abs(it.al(a)) > 0.005f } ?: false
}

@Composable
fun RenkBolumu(vm: EditorDurum, p: Proje, sec: Int) {
    val k = p.klipler.getOrNull(sec)
    if (k == null) {
        Text("Önce bir klip ekle ya da seç.", color = Renk.soluk, modifier = Modifier.padding(16.dp)); return
    }
    val a = k.renk ?: RenkAyar()
    fun d(f: (RenkAyar) -> RenkAyar) = vm.klipGuncelle { it.copy(renk = f(it.renk ?: RenkAyar()).takeIf { r -> !r.bos }) }
    val ayar by vm.renkSekme.collectAsState()   // 3.61: ayar düğmeleri alttaki menü satırında

    Column(Modifier.fillMaxSize()) {
        // ---- sabit üst: başlık (Geçişler biçimi) ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                vm.hepsine({ it.copy(renk = k.renk) }); vm.mesaj.value = "Renk ayarı tüm kliplere uygulandı"
            }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Simge("cift", Renk.yazi, 22.dp); Text("Tümüne", color = Renk.soluk, fontSize = 10.5.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Renk", color = Renk.yazi.copy(alpha = 0.85f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (!a.bos) OnayIsareti(Modifier.padding(start = 6.dp))
                }
                Text("Klip ${sec + 1}/${p.klipler.size}", color = Renk.soluk, fontSize = 11.sp)
            }
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = !a.bos) { d { RenkAyar() }; vm.mesaj.value = "Renk ayarı sıfırlandı" }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Simge("gerial", if (a.bos) Renk.soluk.copy(alpha = 0.4f) else Renk.yazi, 22.dp); Text("Sıfırla", color = Renk.soluk, fontSize = 10.5.sp)
            }
        }
        // 3.56: ayrı önce/sonra kutusu kaldırıldı — sonuç doğrudan ana önizlemede (basılı tut = önce)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
        // ---- kayan alt: seçili ayarın sayfası ----
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            val t = RENK_AYARLARI.firstOrNull { it.anahtar == ayar }
            when {
                t != null -> AyarSayfasi(t, a) { v -> d { t.yaz(it, v) } }
                ayar == "hsl" -> HslSayfasi(a) { f -> d(f) }
                else -> {
                    Text("Hazır ayarlar", color = Renk.soluk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    RENK_HAZIR.chunked(2).forEachIndexed { s, satir ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            satir.forEachIndexed { j, (ad, r) -> SecimKarti(ad, "", a == r, Modifier.weight(1f), sira = s * 2 + j, simge = "gorunum") { d { r } } }
                            if (satir.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("Bir hazır ayar seçip ardından tek tek ayarlarla ince ayar yapabilirsin. Sonuç üstteki önizlemede görünür; önizlemeye basılı tutunca ayarsız hâli görünür.",
                        color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Ayar değiştiyse başlığın yanında görünen yeşil onay. */
@Composable
fun OnayIsareti(modifier: Modifier = Modifier, boy: androidx.compose.ui.unit.Dp = 16.dp) =
    Box(modifier.size(boy).clip(CircleShape).background(Color(0xFF1D9E75)), contentAlignment = Alignment.Center) { Simge("uygula", Color.White, boy * 0.7f) }

@Composable
internal fun AyarDugmesi(ad: String, simge: String, renk: Long, secili: Boolean, degisti: Boolean, tik: () -> Unit) {
    Column(Modifier.width(64.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = tik).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (secili) Color.Transparent else Renk.kart2)
                .border(if (secili) 2.5.dp else 0.dp, if (secili) Renk.yazi else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                Simge(simge, Renk.tonla(Color(renk)), 22.dp)
            }
            if (degisti) OnayIsareti(Modifier.align(Alignment.TopEnd), 14.dp)
        }
        Text(ad, color = if (secili) Renk.yazi else Renk.soluk, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun AyarSayfasi(t: RenkAyarTanim, a: RenkAyar, yaz: (Float) -> Unit) {
    val aralik = t.enAz..1f
    var v by remember(t.anahtar, t.al(a)) { mutableFloatStateOf(t.al(a)) }
    fun kaydet(x: Float) { val y = (Math.round(x * 20f) / 20f).coerceIn(aralik.start, aralik.endInclusive); v = y; yaz(y) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Simge(t.simge, Color(t.renk), 20.dp)
        Text(t.ad, color = Renk.yazi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        if (abs(t.al(a)) > 0.005f) OnayIsareti(Modifier.padding(start = 6.dp))
        Spacer(Modifier.weight(1f))
        Text((if (v > 0.004f && t.enAz < 0f) "+" else "") + "${(v * 100).roundToInt()}", color = Renk.turuncu, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { kaydet(v - 0.05f) }, contentAlignment = Alignment.Center) { Simge("eksi", Renk.yazi, 16.dp) }
        InceSlider(value = v, onValueChange = { v = it }, valueRange = aralik, onValueChangeFinished = { kaydet(v) },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { kaydet(v + 0.05f) }, contentAlignment = Alignment.Center) { Simge("ekle", Renk.yazi, 16.dp) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t.aciklama, color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
        if (abs(v) > 0.005f) Text("Sıfırla", color = Renk.turuncu, fontSize = 12.5.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { kaydet(0f) }.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
private fun HslSayfasi(a: RenkAyar, d: ((RenkAyar) -> RenkAyar) -> Unit) {
    var b by rememberSaveable { mutableIntStateOf(3) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Simge("r_palet", Color(0xFF97C459), 20.dp)
        Text("HSL · renk renk", color = Renk.yazi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        if (hslDegisti(a)) OnayIsareti(Modifier.padding(start = 6.dp))
    }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        RenkLut.BANT_RENK.forEachIndexed { i, r ->
            val degisti = abs(a.hslTon[i]) + abs(a.hslDoy[i]) + abs(a.hslIsik[i]) > 0.005f
            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(r))
                .border(if (b == i) 3.dp else if (degisti) 1.5.dp else 0.dp, if (b == i) Color.White else Renk.turuncu, CircleShape).clickable { b = i })
        }
    }
    Text(RenkLut.BANT_AD[b], color = Color(RenkLut.BANT_RENK[b]), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    fun L(l: List<Float>, v: Float) = l.toMutableList().also { it[b] = v }
    key(b) {
        HslKaydirici("Ton", a.hslTon[b]) { v -> d { it.copy(hslTon = L(it.hslTon, v)) } }
        HslKaydirici("Doygunluk", a.hslDoy[b]) { v -> d { it.copy(hslDoy = L(it.hslDoy, v)) } }
        HslKaydirici("Işık", a.hslIsik[b]) { v -> d { it.copy(hslIsik = L(it.hslIsik, v)) } }
    }
}

@Composable
private fun HslKaydirici(ad: String, deger: Float, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(ad, color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.width(78.dp))
        InceSlider(value = v, onValueChange = { v = it }, valueRange = -1f..1f, onValueChangeFinished = { bitti(v) }, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
        Text("${(v * 100).roundToInt()}", color = Renk.turuncu, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
    }
}

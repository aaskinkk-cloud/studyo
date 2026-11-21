package app.studyo.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 3.70: üç katmanlı gezinme (Ahmet'in onayladığı taslak): 1. ana menü · 2. alt menü satırı · 3. panelin tepesinde alt sayfa çipleri;
// en üstte yol (Görünüm › Şablon › Giriş) ve ayar arama. Her alt menünün son açılan alt sayfası hatırlanır.

class AltSayfa(val anahtar: String, val ad: String, val simge: String)

/** 3. katman: alt menü anahtarı → alt sayfalar (ilk = varsayılan). */
val ALT_SAYFALAR: Map<String, List<AltSayfa>> = mapOf(
    "klip" to listOf(AltSayfa("klipler", "Klipler", "klip"), AltSayfa("kirp", "Kırp", "kirp"), AltSayfa("kadraj", "Kadraj", "tuval"), AltSayfa("hiz", "Hız", "hareket"), AltSayfa("yuzler", "Yüzler", "yuz"), AltSayfa("ayri", "Ayrı video", "disa")),
    "nesne" to listOf(AltSayfa("foto", "Fotoğraftan", "kapak"), AltSayfa("video", "Videodan", "klip"), AltSayfa("kesilen", "Kesilenler", "katman")),
    "sablon" to listOf(AltSayfa("giris", "Giriş", "baslik"), AltSayfa("abone", "Abone", "zil"), AltSayfa("kapanis", "Kapanış", "kalp")),
    "hazir" to listOf(AltSayfa("varsayilan", "Varsayılan", "uygula"), AltSayfa("kayitli", "Kayıtlı ayarlarım", "kaydet")),
    "hareket" to listOf(AltSayfa("yakin", "Yakınlaştırma", "h_yakin"), AltSayfa("kay", "Kaydırma", "h_sag"), AltSayfa("odak", "Odak", "h_odak")),
    "muzik" to listOf(AltSayfa("hazir", "Hazır", "sihir"), AltSayfa("dosya", "Dosyadan", "yukle"), AltSayfa("ayar", "Ayar", "ses")),
    "seslendirme" to listOf(AltSayfa("kayit", "Kayıt", "mikrofon"), AltSayfa("prompter", "Prompter", "prompter"), AltSayfa("kayitlar", "Kayıtlar", "nota")),
    "yazi" to listOf(AltSayfa("ekle", "Yazı", "metin"), AltSayfa("liste", "Videodaki", "katman")),   // 3.75: Hareketli, Yazı kartına katıldı
    "altyazi" to listOf(AltSayfa("oto", "Otomatik", "sihir"), AltSayfa("satir", "Satırlar", "altyazi"), AltSayfa("duzelt", "Toplu düzelt", "degistir"), AltSayfa("gorunum", "Görünüm", "gorunum")),
    "kapak" to listOf(AltSayfa("kare", "Kare", "kapak"), AltSayfa("yazi", "Yazı", "metin"), AltSayfa("stil", "Stil", "sihir"), AltSayfa("efekt", "Efekt", "yildiz")),
)

/** Renk'in 2. katmanı: grup → ayar sayfaları (vm.renkSekme anahtarları). */
class RenkGrup(val anahtar: String, val ad: String, val simge: String, val renk: Long, val sayfalar: List<String>)
val RENK_GRUPLARI = listOf(
    RenkGrup("hazir", "Hazır", "sihir", 0xFFFAC775, listOf("hazir")),
    RenkGrup("isik", "Işık", "g_beyaz", 0xFFFAC775, listOf("parlaklik", "kontrast", "vurgular", "beyazlar", "golgeler")),
    RenkGrup("renk", "Renk", "r_damla", 0xFFED93B1, listOf("doygunluk", "sicaklik", "ton")),
    RenkGrup("detay", "Detay", "r_keskin", 0xFF5DCAA5, listOf("keskinlik")),
    RenkGrup("hsl", "HSL", "r_palet", 0xFF97C459, listOf("hsl")),
)
fun renkGrubu(sayfa: String): RenkGrup = RENK_GRUPLARI.firstOrNull { sayfa in it.sayfalar } ?: RENK_GRUPLARI[0]

/** Son açılan alt sayfalar (alt menü anahtarı → alt sayfa); "ayar" tercihinde saklanır. */
object AltHafiza {
    private val harita = mutableStateMapOf<String, String>()
    private var yuklendi = false
    private fun yukle() {
        if (yuklendi) return; yuklendi = true
        runCatching { Depo.app.getSharedPreferences("ayar", 0).all.forEach { (k, v) -> if (k.startsWith("alt_") && v is String) harita[k.removePrefix("alt_")] = v } }
    }
    fun al(hap: String): String { yukle(); return harita[hap]?.takeIf { a -> ALT_SAYFALAR[hap]?.any { it.anahtar == a } != false } ?: ALT_SAYFALAR[hap]?.firstOrNull()?.anahtar ?: "" }
    fun ham(k: String): String? { yukle(); return harita[k] }
    fun sec(hap: String, a: String) {
        yukle(); harita[hap] = a
        runCatching { Depo.app.getSharedPreferences("ayar", 0).edit().putString("alt_$hap", a).apply() }
    }
}

/** En üstteki yol satırı: ‹ (ana menüye) · Bölüm › Alt menü › Alt sayfa · 🔍 ara. */
@Composable
fun YolSatiri(parcalar: List<String>, anaMenu: () -> Unit, ara: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = anaMenu), contentAlignment = Alignment.Center) { Simge("sol", Renk.soluk, 18.dp) }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            parcalar.forEachIndexed { i, ad ->
                if (i > 0) Text("  ›  ", color = Renk.soluk.copy(alpha = 0.6f), fontSize = 12.sp)
                val son = i == parcalar.lastIndex
                Text(ad, color = if (son) Renk.yazi else Renk.soluk, fontSize = 12.5.sp, fontWeight = if (son) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    modifier = if (i == 0) Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = anaMenu) else Modifier)
            }
        }
        Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = ara), contentAlignment = Alignment.Center) { Simge("ara", Renk.soluk, 19.dp) }
    }
}

/** 3. katman çipleri (yana kayar): seçili = dolu, ayarı değişmiş = ✓. */
@Composable
fun AltSayfaCipleri(liste: List<AltSayfa>, secili: String, isaret: (String) -> Boolean, sec: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        liste.forEach { a ->
            val on = a.anahtar == secili
            val sekil = RoundedCornerShape(16.dp)
            Row(Modifier.height(32.dp).clip(sekil).background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, sekil)
                .clickable { sec(a.anahtar) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Simge(a.simge, if (on) Renk.zemin else Renk.soluk, 15.dp)
                Spacer(Modifier.width(5.dp))
                Text(a.ad, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                if (isaret(a.anahtar)) OnayIsareti(Modifier.padding(start = 5.dp), 13.dp)
            }
        }
    }
}

/** Arama kaydı: yazılan söz yola ya da anahtar kelimelere uyarsa listelenir; dokununca o sayfa açılır. */
class AramaOge(val yol: String, val bolum: String, val hap: String, val alt: String?, val kelimeler: String = "")

@Composable
fun AramaPenceresi(liste: List<AramaOge>, git: (AramaOge) -> Unit, kapat: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val tr = java.util.Locale("tr")
    val sonuc = remember(q) {
        val s = q.trim().lowercase(tr)
        if (s.isEmpty()) liste.take(40) else liste.filter { o -> (o.yol + " " + o.kelimeler).lowercase(tr).contains(s) }.take(40)
    }
    val odak = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { odak.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = kapat, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).heightIn(max = 560.dp).clip(RoundedCornerShape(18.dp)).background(Renk.kart).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Simge("ara", Renk.soluk, 20.dp); Spacer(Modifier.width(8.dp))
                OutlinedTextField(q, { q = it }, Modifier.weight(1f).focusRequester(odak), singleLine = true,
                    placeholder = { Text("Ayar ara: giriş, keskinlik, hız, müzik…") })
                Box(Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).clickable(onClick = kapat), contentAlignment = Alignment.Center) { Simge("kapat", Renk.yazi, 20.dp) }
            }
            if (sonuc.isEmpty()) Text("Bulunamadı.", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                items(sonuc.size) { n ->
                    val o = sonuc[n]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { git(o) }.padding(horizontal = 8.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Simge("ileri", Renk.soluk, 14.dp); Spacer(Modifier.width(8.dp))
                        Text(o.yol, color = Renk.yazi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Düzenle › Klip › Hız: 0,25×–4× (yavaş çekim / hızlandırma). Video bir kez işlenir; 1× özgün hâle döner. */
@Composable
fun HizSayfasi(vm: EditorDurum, k: Klip) {
    Kart("Hız") {
        if (k.foto) { Text("Fotoğrafta hız yok; ekranda kalma süresini Kırp sayfasından ayarla.", color = Renk.soluk, fontSize = 12.5.sp); return@Kart }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HizIsleyici.HIZLAR.forEach { h ->
                val on = kotlin.math.abs(k.hiz - h) < 0.01f
                Column(Modifier.width(58.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.klipHiz(h) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(50.dp).clip(CircleShape).background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(HizIsleyici.yaz(h), color = if (on) Renk.zemin else Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(when { h < 1f -> "yavaş"; h > 1f -> "hızlı"; else -> "normal" }, color = Renk.soluk, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
        val ozgunMs = if (k.asilUri.isNotEmpty()) k.asilSonMs - k.asilBasMs else k.sonMs - k.basMs
        Text("Şu an ${HizIsleyici.yaz(k.hiz)} · ekranda ${sureOndalik(k.sureMs)}" + (if (k.hiz != 1f) " (özgünü ${sureOndalik(ozgunMs)})" else ""),
            color = Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.padding(top = 8.dp))
        AcilirNot("Seçili klibin kırpılmış aralığı bir kez işlenir (birkaç saniye sürer); özgün dosya silinmez, 1× ile geri döner. Ses de aynı hızda çalar. " +
            "Hızdan sonra kırpma yeni klibe göre yapılır; hızı yeniden değiştirince özgün aralık kullanılır.")
    }
}

/** Görünüm › Hazır › Kayıtlı ayarlarım: o anki görünüm ayarlarını adıyla sakla; başka projede tek dokunuşla uygula. */
@Composable
fun KayitliAyarlarSayfasi(vm: EditorDurum) {
    val liste by vm.kayitliAyarlar.collectAsState()
    var ad by remember { mutableStateOf("") }
    Kart("Kayıtlı ayarlarım") {
        AcilirNot("Giriş stili, abone kartı, kapanış, geçiş, logonun yeri, kapak stili, altyazı görünümü, seçili klibin renk/filtre/efekti ve hazır müzik türü adıyla saklanır. " +
            "Yazılar ve dosyalar saklanmaz. Yedeğe de girer.", "Neler saklanır?")
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(ad, { ad = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Ad (ör. Yayla serisi)") })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(Renk.turuncu).clickable { vm.kayitliKaydet(ad); ad = "" }.padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center) { Text("Kaydet", color = Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
        if (liste.isEmpty()) Text("Henüz kayıt yok.", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        liste.forEach { (isim, s) ->
            var silOnay by remember(isim) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Renk.kart2).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(isim, color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("giriş ${s.giris.stil} · geçiş ${s.gecis.tur}" + (s.muzikTur?.let { " · müzik" } ?: ""), color = Renk.soluk, fontSize = 11.sp, maxLines = 1)
                }
                Text("Uygula", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.kayitliUygula(isim, s) }.padding(10.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (silOnay) Renk.kirmizi else Color.Transparent)
                    .clickable { if (silOnay) vm.kayitliSil(isim) else silOnay = true }, contentAlignment = Alignment.Center) {
                    Simge("sil", if (silOnay) Color.White else Renk.kirmizi, 18.dp)
                }
            }
        }
    }
}

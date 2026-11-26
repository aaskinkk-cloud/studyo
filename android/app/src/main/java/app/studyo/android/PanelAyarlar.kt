package app.studyo.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 3.61: Yayın › Ayarlar — son ayarlarımı sakla + yedekle / geri yükle (uygulama silinip kurulsa da ayarlar ve logolar geri gelir). */
@Composable
fun PanelAyarlar(vm: EditorDurum, p: Proje, sec: Int) {
    val ctx = LocalContext.current
    val kapsam = rememberCoroutineScope()
    var sakli by remember { mutableStateOf(Ayarlar.sakli(ctx)) }
    var yedekUri by remember { mutableStateOf(Ayarlar.otoYedekUri(ctx)) }
    val tarih = { ms: Long -> java.text.SimpleDateFormat("d MMMM yyyy HH:mm", TR).format(java.util.Date(ms)) }

    val yedekOlustur = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        u ?: return@rememberLauncherForActivityResult
        kapsam.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ayarlar.yedekYaz(ctx, u) } }
            r.onSuccess { yedekUri = u; vm.mesaj.value = "Yedek oluşturuldu · bundan sonra kendiliğinden güncellenir" }
                .onFailure { Kayit.hata("yedek", it); vm.uyari.value = "Yedek oluşturulamadı: ${it.message}" }
        }
    }
    val yedekAc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u ?: return@rememberLauncherForActivityResult
        kapsam.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ayarlar.yedekOku(ctx, u) } }
            r.onSuccess { n -> sakli = Ayarlar.sakli(ctx); yedekUri = Ayarlar.otoYedekUri(ctx)
                vm.uyari.value = "Yedek geri yüklendi ($n logo). Ayarların tamamının etkin olması için uygulamayı kapatıp yeniden aç." }
                .onFailure { Kayit.hata("geri yükle", it); vm.uyari.value = "Geri yüklenemedi: ${it.message}" }
        }
    }

    // 4.01: tema rengi (InShot temasındaki turkuaz vurgunun yerine)
    Kart("Tema rengi") {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 4.26: "Tema" = temanın kendi rengi (elle seçim kalkar)
            if (!Renk.ins) Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { Renk.vurguTema(ctx) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val on = !Renk.vurguElle
                Box(Modifier.size(34.dp).clip(CircleShape).background(Renk.kart2).border(if (on) 2.5.dp else 1.dp, if (on) Color.White else Renk.cizgi, CircleShape),
                    contentAlignment = Alignment.Center) { Simge("sihir", Renk.yazi, 16.dp) }
                Text("Tema", color = if (on) Renk.yazi else Renk.soluk, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Renk.VURGULAR.forEach { (v, ad) ->
                val on = (Renk.ins || Renk.vurguElle) && Renk.vurgu == v
                Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { Renk.vurguSec(ctx, v) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Color(v)).border(if (on) 2.5.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) { if (on) Simge("uygula", if (v == 0xFFE6E6E6L || v == 0xFFF2C94CL) Color.Black else Color.White, 18.dp) }
                    Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 10.5.sp, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
    // 4.06: sabit Düzenle düğmesinin görünümü (önceki kutulu görünüm seçenek olarak kalır)
    Kart("Menü görünümü") {
        // 4.17: menü boyutu — dört seçenek, her birinde o boydaki örnek simge; seçili = tema renginde çerçeve
        Text("Menü boyutu", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.75f to "En küçük", 0.85f to "Küçük", 0.92f to "Orta", 1f to "Büyük", 1.1f to "En büyük").forEach { (v, ad) ->
                val on = kotlin.math.abs(Duzen.menuOlcek - v) < 0.01f
                Column(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Renk.turuncu.copy(alpha = 0.14f) else Renk.kart2)
                    .border(if (on) 1.5.dp else 1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(14.dp)).clickable { Duzen.menuOlcekSec(ctx, v) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Simge("katman", if (on) Renk.turuncu else Renk.yazi, 22.dp * v)
                    Text(ad, color = if (on) Renk.turuncu else Renk.soluk, fontSize = 11.sp * v, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        // 4.18: menü görünümü — simge + yazı · yalnız simge · yalnız yazı (bütün menülerde)
        Text("Menü görünümü", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(Renk.kart2).padding(3.dp)) {
            listOf("ikisi" to "Simge+yazı", "simge" to "Simge", "yazi" to "Yazı", "renkli" to "Renkli", "buyuk" to "BÜYÜK").forEach { (v, ad) ->
                val on = Duzen.menuStil == v
                Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(11.dp)).background(if (on) Renk.turuncu else Color.Transparent)
                    .clickable { Duzen.menuStilSec(ctx, v) }, contentAlignment = Alignment.Center) {
                    Text(ad, color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        AnahtarSatiri("Oynatma düğmeleri ve kare çizgisi tema renginde", Duzen.oynaticiTema) { Duzen.oynaticiTemaSec(ctx, it) }
        AnahtarSatiri("Oynatıcı sade (Başa · Önceki · Sonraki gizli)", Duzen.oynaticiSade) { Duzen.oynaticiSadeSec(ctx, it) }   // 4.19
        AnahtarSatiri("Önizlemedeki hızlı düğmeleri gizle", Duzen.hizliGizli) { Duzen.hizliGizli = it; Duzen.hizliKaydet(ctx) }
        AnahtarSatiri("Sabit Düzenle düğmesi sade (çerçevesiz)", Duzen.duzenleSade) { Duzen.duzenleSadeSec(ctx, it) }
        AnahtarSatiri("Alt menüler üstte, ana menü altta sabit", Duzen.altUstte) { Duzen.altUstteSec(ctx, it) }
        AnahtarSatiri("Sayfa düğmeleri tek satırda (alt menünün yanında)", Duzen.cipTekSatir) { Duzen.cipTekSatirSec(ctx, it) }   // 4.16: kapalı = ayrı satır
        AnahtarSatiri("Geçiş ışık ölçümü (teşhis, Hata kaydına yazar)", Duzen.isikTeshis) { Duzen.isikTeshis = it; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("isikTeshis2", it).apply() }   // 4.30
        AnahtarSatiri("Kesilenler: önceki liste görünümü", Duzen.kesilenEski) { Duzen.kesilenEskiSec(ctx, it) }   // 4.15   // 4.14 (kapalı = önceki düzen)
    }
    // 4.39: yüz turunda durakta bekleme — kişi sayısına göre (gruplamada kullanılır)
    Kart("Yüz turu · durakta bekleme") {
        listOf("1 kişi", "2 kişi", "3 kişi", "4 kişi", "5+ kişi").forEachIndexed { i, ad ->
            TonKaydirici(ad, Duzen.yuzBekle[i], 1f..8f, 0.5f, Renk.turuncu, { String.format(TR, "%.1f sn", it) }) { v -> Duzen.yuzBekle[i] = v; Duzen.yuzBekleKaydet(ctx) }
        }
        AcilirNot("Yüzler › Durakları grupla (Otomatik, 2'li… ve Tümüne) bu süreleri kullanır. Değişiklik sonraki gruplamada uygulanır.")
    }
    Kart("Son ayarlarımı sakla", ton = Color(TON[1])) {
        AcilirNot("Bu projedeki giriş stili, abone kartı, kapanış, geçiş, logonun yeri, kapak stili, altyazı görünümü, seçili klibin renk/filtre/efekti ve hazır müzik türü saklanır. Yeni videolarda Varsayılan ayarlar bunlarla uygulanır.")
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(Color(TON[1]).copy(alpha = 0.16f))
            .border(1.dp, Color(TON[1]).copy(alpha = 0.7f), RoundedCornerShape(12.dp)).clickable {
                Ayarlar.sakla(ctx, p, p.klipler.getOrNull(sec)); sakli = Ayarlar.sakli(ctx); vm.mesaj.value = "Ayarların saklandı"
            }.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Simge("kaydet", Color(TON[1]), 18.dp); Spacer(Modifier.width(6.dp))
            Text("Bu projedeki ayarları sakla", color = Color(TON[1]), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        sakli?.let { s ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OnayIsareti(); Spacer(Modifier.width(6.dp))
                Text("Saklı: ${tarih(s.zaman)} · giriş ${s.giris.stil}" + (s.muzikTur?.let { " · müzik ${MuzikSentez.tur(it).ad}" } ?: ""), color = Renk.yazi, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("Sil", color = Renk.kirmizi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { Ayarlar.sil(ctx); sakli = null }.padding(8.dp))
            }
        }
    }
    Kart("Yedek · uygulama silinse de kalır", ton = Color(TON[2])) {
        AcilirNot("Ayarlar ve logo kütüphanen telefonda seçtiğin bir dosyaya (örneğin Belgeler/Stüdyo-yedek.zip) yazılır; sonra her değişiklikte kendiliğinden güncellenir. Uygulamayı silip yeniden kurunca \"Yedekten geri yükle\" ile hepsi geri gelir.")
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            YuvarlakSecim(if (yedekUri != null) "Yedeği yenile" else "Yedek oluştur", "kaydet", Color(TON[2]), yedekUri != null) {
                val u = yedekUri
                if (u != null) kapsam.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Ayarlar.yedekYaz(ctx, u, kalici = false) } }
                    r.onSuccess { vm.mesaj.value = "Yedek güncellendi" }.onFailure { yedekOlustur.launch("Stüdyo-yedek.zip") }
                } else yedekOlustur.launch("Stüdyo-yedek.zip")
            }
            YuvarlakSecim("Yeni yere yedekle", "disa", Color(TON[3]), false) { yedekOlustur.launch("Stüdyo-yedek.zip") }
            YuvarlakSecim("Yedekten geri yükle", "yukle", Color(TON[0]), false) { yedekAc.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
        }
    }
}

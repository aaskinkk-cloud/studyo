package app.studyo.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

    Kart("Son ayarlarımı sakla", ton = Color(TON[1])) {
        Text("Bu projedeki giriş stili, abone kartı, kapanış, geçiş, logonun yeri, kapak stili, altyazı görünümü, seçili klibin renk/filtre/efekti ve hazır müzik türü saklanır. Yeni videolarda Varsayılan ayarlar bunlarla uygulanır.",
            color = Renk.soluk, fontSize = 12.sp)
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
        Text("Ayarlar ve logo kütüphanen telefonda seçtiğin bir dosyaya (örneğin Belgeler/Stüdyo-yedek.zip) yazılır; sonra her değişiklikte kendiliğinden güncellenir. Uygulamayı silip yeniden kurunca \"Yedekten geri yükle\" ile hepsi geri gelir.",
            color = Renk.soluk, fontSize = 12.sp)
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

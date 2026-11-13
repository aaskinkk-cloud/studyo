package app.studyo.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private fun turSimge(t: String) = when (t) { "metin" -> "metin"; "gorsel" -> "kapak"; "cikartma" -> "yildiz"; "ses" -> "nota"; else -> "katman" }
private fun turAdi(t: String) = when (t) { "metin" -> "Metin"; "gorsel" -> "Görsel"; "cikartma" -> "Çıkartma"; "ses" -> "Ses"; else -> t }

/** Katman bölümü: ekle · zaman çizelgesi (sıra, başlangıç/bitiş) · seçili katmanın ayarları. */
@Composable
fun PanelKatmanlar(vm: EditorDurum, p: Proje) {
    val gorselSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.katmanGorselEkle(it) }
    val sesSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.katmanSesEkle(it) } }
    var cikartmaAcik by remember { mutableStateOf(false) }
    var logoAcik by remember { mutableStateOf(false) }
    val secId by vm.seciliKatman.collectAsState()
    val konum by vm.konum.collectAsState()

    if (p.klipler.isEmpty()) {
        Text("Önce en az bir klip ekle; katmanlar videonun üstüne yerleşir.", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        return
    }
    Kart("Katman ekle") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SimgeDugme("metin", "Metin", Modifier.weight(1f), renk = tonRenk(0, "")) { vm.katmanMetinEkle() }
            SimgeDugme("kapak", "Görsel", Modifier.weight(1f), renk = tonRenk(1, "")) { gorselSec.launch(arrayOf("image/*")) }
            SimgeDugme("logo", "Logo", Modifier.weight(1f), renk = tonRenk(2, "")) { logoAcik = true }
            SimgeDugme("yildiz", "Çıkartma", Modifier.weight(1f), renk = tonRenk(3, "")) { cikartmaAcik = true }
            SimgeDugme("nota", "Ses", Modifier.weight(1f), renk = tonRenk(4, "")) { sesSec.launch(arrayOf("audio/*")) }
        }
        Text("Yeni katman oynatıcının bulunduğu andan başlar ve yukarıdaki çizelgede kendi satırına yerleşir. Önizlemede katmana dokun = seç · sürükle = taşı · iki parmak = büyüt/döndür.",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
    }

    p.katmanlar.firstOrNull { it.id == secId }?.let { KatmanAyarlari(vm, p, it, konum) }

    if (cikartmaAcik) AlertDialog(
        onDismissRequest = { cikartmaAcik = false },
        title = { Text("Çıkartma") },
        text = {
            Column {
                KatmanCizim.CIKARTMALAR.chunked(6).forEach { satir ->
                    Row(Modifier.fillMaxWidth()) {
                        satir.forEach { e ->
                            Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.katmanCikartmaEkle(e); cikartmaAcik = false },
                                contentAlignment = Alignment.Center) { Text(e, fontSize = 26.sp) }
                        }
                        repeat(6 - satir.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { cikartmaAcik = false }) { Text("Kapat") } },
    )
    if (logoAcik) {
        val kutup by LogoKutup.ogeler.collectAsState()
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { LogoKutup.yukle() } }
        AlertDialog(
            onDismissRequest = { logoAcik = false },
            title = { Text("Kütüphaneden logo") },
            text = {
                if (kutup.isEmpty()) Text("Kütüphanede logo yok. Yayın › Logo bölümünden yükleyebilirsin.")
                else Column {
                    kutup.chunked(4).forEach { satir ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            satir.forEach { k ->
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { vm.katmanLogoEkle(k); logoAcik = false }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    LogoResmi(k.yol, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)))
                                    Text(k.ad, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(4 - satir.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { logoAcik = false }) { Text("Kapat") } },
        )
    }
}

@Composable
private fun KatSlider(etiket: String, deger: Float, aralik: ClosedFloatingPointRange<Float>, yazi: (Float) -> String, etkin: Boolean, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    Etiketli(etiket, yazi(v))
    InceSlider(value = v, onValueChange = { v = it }, valueRange = aralik, enabled = etkin, onValueChangeFinished = { bitti(v) })
}

/** Seçili katmanın ayarları. Kilitliyken yalnız kilit açılabilir. */
@Composable
private fun KatmanAyarlari(vm: EditorDurum, p: Proje, o: KatmanOge, konum: Long) {
    val e = !o.kilitli
    fun d(f: (KatmanOge) -> KatmanOge) = vm.katmanDegis(o.id, f)
    Kart("Seçili katman · ${turAdi(o.tur)}") {
        if (o.kilitli) Text("Kilitli: değiştirmek için çizelgenin üstündeki “Kilidi aç”a dokun.", color = Renk.turuncu, fontSize = 12.sp)
        Etiketli("Zaman", "${sureOndalik(o.basMs)} – ${sureOndalik(o.sonMs)}  ·  ${sureOndalik(o.sonMs - o.basMs)}")
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            SimgeDugme("basa", "Başı = şimdi", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = e && konum < o.sonMs - 300) { d { it.copy(basMs = konum.coerceAtLeast(0)) } }
            SimgeDugme("sonraki", "Sonu = şimdi", Modifier.weight(1f), renk = tonRenk(1, ""), etkin = e && konum > o.basMs + 300) { d { it.copy(sonMs = konum.coerceAtMost(p.toplamMs)) } }
            SimgeDugme("oynat", "Başına git", Modifier.weight(1f), renk = tonRenk(2, "")) { vm.git(o.basMs) }
            SimgeDugme("kadraj", "Tüm video", Modifier.weight(1f), renk = tonRenk(3, ""), etkin = e && o.tur != "ses") { d { it.copy(basMs = 0, sonMs = p.toplamMs) } }
        }

        when (o.tur) {
            "metin" -> {
                var m by remember(o.id) { mutableStateOf(o.metin) }
                OutlinedTextField(m, { m = it; d { k -> k.copy(metin = it) } }, enabled = e, label = { Text("Yazı") },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    KatmanCizim.RENKLER.forEach { r ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(r))
                            .border(if (o.renk == r) 3.dp else 1.dp, if (o.renk == r) Renk.turuncu else Renk.cizgi, CircleShape)
                            .clickable(enabled = e) { d { it.copy(renk = r) } })
                    }
                }
                AnahtarSatiri("Arka kutu", o.kutu) { v -> if (e) d { it.copy(kutu = v) } }
            }
            "ses" -> {
                KatSlider("Ses seviyesi", o.ses, 0f..1.5f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(ses = v) } }
                Etiketli("Hız", "${o.hiz}×")
                Row(Modifier.fillMaxWidth()) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEachIndexed { j, h ->
                        SecimKarti("${h}×", "", o.hiz == h, Modifier.weight(1f), etkin = e, sira = j, simge = "hareket") {
                            d { k ->
                                // hız değişince süre, kaynağın yettiği kadarla sınırlanır
                                val enUzun = if (k.kaynakSureMs > 0) ((k.kaynakSureMs - k.kaynakBasMs) / h).toLong() else Long.MAX_VALUE
                                k.copy(hiz = h, sonMs = minOf(k.sonMs, k.basMs + enUzun).coerceAtLeast(k.basMs + 300))
                            }
                        }
                    }
                }
                if (o.kaynakSureMs > 1000) KatSlider("Sesin başladığı yer", o.kaynakBasMs.toFloat(), 0f..(o.kaynakSureMs - 500).toFloat(), { sureOndalik(it.toLong()) }, e) { v ->
                    d { it.copy(kaynakBasMs = v.toLong()) }
                }
                Text("Önizlemede ses en çok %100 duyulur; %100 üstü dışa aktarmada uygulanır.", color = Renk.soluk, fontSize = 11.sp)
            }
        }

        if (o.gorsel) {
            val (mn, mx) = if (o.tur == "metin") 0.02f to 0.3f else 0.03f to 1.5f
            KatSlider("Boyut", o.olcek, mn..mx, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(olcek = v) } }
            KatSlider("Döndür", o.aci, -180f..180f, { "${it.roundToInt()}°" }, e) { v -> d { it.copy(aci = v) } }
            KatSlider("Opaklık", o.opaklik, 0.05f..1f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(opaklik = v) } }
            Row(Modifier.fillMaxWidth()) {
                SimgeDugme("kadraj", "Ortala", Modifier.weight(1f), renk = tonRenk(4, ""), etkin = e) { d { it.copy(x = 0.5f, y = 0.5f) } }
                SimgeDugme("yinele", "Düz (0°)", Modifier.weight(1f), renk = tonRenk(5, ""), etkin = e) { d { it.copy(aci = 0f) } }
                SimgeDugme("gerial", "Boyut sıfırla", Modifier.weight(1f), renk = tonRenk(6, ""), etkin = e) {
                    d { it.copy(olcek = when (it.tur) { "metin" -> 0.08f; "cikartma" -> 0.16f; else -> 0.35f }) }
                }
            }
            Etiketli("Giriş / çıkış", "")
            Row(Modifier.fillMaxWidth()) {
                listOf("yok" to "Yok", "solma" to "Solma", "kay" to "Kayma", "buyu" to "Büyüme", "zipla" to "Zıplama").forEachIndexed { j, (k, ad) ->
                    SecimKarti(ad, "", o.anim == k, Modifier.weight(1f), etkin = e, sira = j,
                        simge = when (k) { "yok" -> "kapat"; "solma" -> "gorunum"; "kay" -> "yukle"; "buyu" -> "kadraj"; else -> "yildiz" }) { d { it.copy(anim = k) } }
                }
            }
            if (o.tur != "metin") {
                Etiketli("Filtre", "")
                Row(Modifier.fillMaxWidth()) {
                    listOf("yok" to "Yok", "sb" to "S/B", "sepya" to "Sepya", "canli" to "Canlı", "soguk" to "Soğuk", "sicak" to "Sıcak").forEachIndexed { j, (k, ad) ->
                        SecimKarti(ad, "", o.filtre == k, Modifier.weight(1f), etkin = e, sira = j, simge = if (k == "yok") "kapat" else "gorunum") { d { it.copy(filtre = k) } }
                    }
                }
            }
            if (o.tur == "gorsel") Kart("Kırp", baslangicAcik = false) {
                KatSlider("Soldan", o.kirpL, 0f..0.45f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(kirpL = v) } }
                KatSlider("Sağdan", o.kirpR, 0f..0.45f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(kirpR = v) } }
                KatSlider("Üstten", o.kirpT, 0f..0.45f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(kirpT = v) } }
                KatSlider("Alttan", o.kirpB, 0f..0.45f, { "%${(it * 100).roundToInt()}" }, e) { v -> d { it.copy(kirpB = v) } }
            }
        }

    }
}

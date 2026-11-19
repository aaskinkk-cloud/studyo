package app.studyo.android

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
fun PanelKatmanlar(vm: EditorDurum, p: Proje, takipAc: () -> Unit = {}) {
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
        // tek sıra, sağa kayar
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val g = Modifier.width(68.dp)
            SimgeDugme("metin", "Yazı ekle", g, renk = tonRenk(0, "")) { vm.katmanMetinEkle() }
            SimgeDugme("takip", "Takip yazısı", g, renk = tonRenk(5, "")) { takipAc() }
            SimgeDugme("kapak", "Görsel", g, renk = tonRenk(1, "")) { gorselSec.launch(arrayOf("image/*")) }
            SimgeDugme("logo", "Logo", g, renk = tonRenk(2, "")) { logoAcik = true }
            SimgeDugme("yildiz", "Çıkartma", g, renk = tonRenk(3, "")) { cikartmaAcik = true }
            SimgeDugme("nota", "Ses", g, renk = tonRenk(4, "")) { sesSec.launch(arrayOf("audio/*")) }
        }
        Text("Hazır yazılar", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        HazirYazilar(vm)
        Text("Yeni katman oynatıcının bulunduğu andan başlar, kısa süre kalır ve solarak biter; sonunu çizelgede ucundan çekerek ya da \"Sonu = şimdi\" ile ayarlarsın. Önizlemede katmana dokun = seç · sürükle = taşı · iki parmak = büyüt/döndür.",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))
    }

    p.katmanlar.firstOrNull { it.id == secId }?.let { KatmanAyarlari(vm, p, it, konum) }
    TumKatmanlar(vm, p)
    // 3.45: fotoğraftan / videodan nesne kesme Katmanlar'da da (Düzenle › Nesne ile aynı panel)
    var nesneAcik by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(p.nesneler.isNotEmpty()) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Renk.kart).clickable { nesneAcik = !nesneAcik }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Simge("makas", Renk.turuncu, 16.dp); Spacer(Modifier.width(8.dp))
        Text("Nesne kes · fotoğraf / video${if (p.nesneler.isNotEmpty()) " · ${p.nesneler.size}" else ""}", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        GizleGosterDugmesi(nesneAcik, Renk.turuncu) { nesneAcik = !nesneAcik }   // 3.57
    }
    if (nesneAcik) PanelNesne(vm, p)

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
            SimgeDugme("kadraj", "Tüm video", Modifier.weight(1f), renk = tonRenk(3, ""), etkin = e) { d { vm.katmanAralik(it, "tum") } }
        }
        // 3.46: katman altyazısı (katmanın altında, onunla birlikte taşınır)
        if (o.tur != "ses") {
            var ay by remember(o.id) { mutableStateOf(o.altyazi) }
            OutlinedTextField(ay, { ay = it; d { k -> k.copy(altyazi = it) } }, enabled = e, label = { Text("Altyazı (katmanın altında)") },
                maxLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        UcDugmeleri(vm, o)   // 3.50: baş/son ± 0,5 sn
        // otomatik baş/son (3.43): tek dokunuşla
        Row(Modifier.fillMaxWidth()) {
            SimgeDugme("klip", "Bu klip boyunca", Modifier.weight(1f), renk = tonRenk(4, ""), etkin = e) { d { vm.katmanAralik(it, "klip") } }
            SimgeDugme("sonraki", "Şimdiden sona", Modifier.weight(1f), renk = tonRenk(5, ""), etkin = e) { d { vm.katmanAralik(it, "sona") } }
            SimgeDugme("basa", "Baştan şimdiye", Modifier.weight(1f), renk = tonRenk(6, ""), etkin = e) { d { vm.katmanAralik(it, "basa") } }
            if (o.tur == "ses") SimgeDugme(if (o.ses <= 0.001f) "sessiz" else "ses", if (o.ses <= 0.001f) "Sesi aç" else "Sessiz", Modifier.weight(1f), renk = tonRenk(7, ""), etkin = e) {
                d { it.copy(ses = if (it.ses <= 0.001f) 1f else 0f) } }
            else SimgeDugme("gorunum", if (o.gizli) "Göster" else "Gizle", Modifier.weight(1f), renk = tonRenk(7, ""), etkin = e) { d { it.copy(gizli = !it.gizli) } }
        }

        when (o.tur) {
            "metin" -> {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("baslik" to "Başlık", "konum" to "Konum", "altbant" to "Alt bant", "etiket" to "Etiket", "duz" to "Düz").forEachIndexed { j, (st, ad) ->
                        SecimKarti(ad, "", o.stil == st, Modifier.width(76.dp), etkin = e, sira = j, simge = "metin") { d { it.copy(stil = st) } }
                    }
                }
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
                if (o.stil == "duz") AnahtarSatiri("Arka kutu", o.kutu) { v -> if (e) d { it.copy(kutu = v) } }
                else Text("2. satır (alt satır) küçük yazılır. Renk = vurgu rengi.", color = Renk.soluk, fontSize = 11.sp)
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

/** Popüler yazı örnekleri (3.42): dokun = oynatıcının olduğu andan kısa süreli yazı ekle (sonra metni düzenlenir). */
private data class HazirYazi(val ad: String, val stil: String, val metin: String, val x: Float, val y: Float, val olcek: Float, val sure: Long, val renk: Long)

@Composable
private fun HazirYazilar(vm: EditorDurum) {
    val bugun = remember { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr")).format(java.util.Date()) }
    val l = remember(bugun) { listOf(
        HazirYazi("Konum", "konum", "Kabataş, Ordu\nKaradeniz", 0.3f, 0.84f, 0.055f, 3500, 0xFFF5A524),
        HazirYazi("Başlık", "baslik", "Başlık yazın\n$bugun", 0.5f, 0.5f, 0.085f, 3000, 0xFFF5A524),
        HazirYazi("Alt bant", "altbant", "Ad Soyad\nAçıklama", 0.3f, 0.82f, 0.055f, 4000, 0xFF2EC5D3),
        HazirYazi("Etiket", "etiket", "Doğa", 0.18f, 0.14f, 0.05f, 3000, 0xFF86C06E),
        HazirYazi("Tarih", "etiket", bugun, 0.78f, 0.12f, 0.045f, 3000, 0xFFFFFFFF),
        HazirYazi("Yer adı", "konum", "Yayla yolu", 0.5f, 0.15f, 0.05f, 3000, 0xFFFF6B6B),
    ) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        l.forEach { h ->
            val v = Color(if (h.renk == 0xFFFFFFFFL) 0xFFF5A524 else h.renk)
            val ilk = h.metin.substringBefore('\n')
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B242D)).border(0.5.dp, v.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .clickable { vm.katmanMetinEkle(h.stil, h.metin, h.sure, h.x, h.y, h.olcek, ad = h.ad).also { vm.katmanDegis(vm.seciliKatman.value ?: "") { it.copy(renk = h.renk) } } }
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                    when (h.stil) {
                        "konum" -> Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xD9121A22)).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(v)); Spacer(Modifier.width(4.dp))
                            Text(ilk, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        "altbant" -> Row(Modifier.height(18.dp)) { Box(Modifier.width(3.dp).fillMaxHeight().background(v))
                            Text(ilk, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.background(Color(0xE6121A22)).padding(horizontal = 4.dp)) }
                        "etiket" -> Text(ilk.uppercase(), color = Color(0xFF121A22), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(v).padding(horizontal = 7.dp, vertical = 2.dp))
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(ilk, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.width(26.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(v)) }
                    }
                }
                Text(h.ad, color = Renk.soluk, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Tüm katmanlar (3.43): müzik ve her katman tek satırda — ses/opaklık düzeyi, kapat/aç, tek dokunuşla tüm video. */
@Composable
private fun TumKatmanlar(vm: EditorDurum, p: Proje) {
    if (p.katmanlar.isEmpty() && p.muzik == null) return
    Kart("Ses ve görünürlük · tümü") {
        p.muzik?.let { m ->
            HizliSatir("nota", "Müzik · ${m.ad.ifBlank { "parça" }}", Color(0xFF4CC38A), m.ses, 0f..1f, m.ses <= 0.001f, "Ses",
                kapat = { vm.muzikDegis { it.copy(ses = if (it.ses <= 0.001f) 0.5f else 0f) } }, tum = null) { v -> vm.muzikDegis { it.copy(ses = v) } }
            AnahtarSatiri("Videoda ses varken müziği otomatik kıs", m.otoKis) { v -> vm.muzikDegis { it.copy(otoKis = v) } }
        }
        for (o in p.katmanlar.asReversed()) key(o.id) {
            val ses = o.tur == "ses"
            val ad = (if (o.satirGizli) "◌ " else "") + o.ad.ifBlank { o.metin.lineSequence().firstOrNull() ?: "" }.ifBlank { turAdi(o.tur) }
            HizliSatir(turSimge(o.tur), ad, when (o.tur) { "metin" -> Color(0xFF7E57C2); "gorsel" -> Color(0xFF2EC5D3); "cikartma" -> Color(0xFFF5A524); "ses" -> Color(0xFF4CC38A); else -> Renk.soluk },
                if (ses) o.ses else o.opaklik, if (ses) 0f..1.5f else 0.05f..1f, if (ses) o.ses <= 0.001f else o.gizli, if (ses) "Ses" else "Opaklık",
                kapat = { vm.katmanDegis(o.id) { if (ses) it.copy(ses = if (it.ses <= 0.001f) 1f else 0f) else it.copy(gizli = !it.gizli) } },
                tum = { vm.katmanDegis(o.id) { vm.katmanAralik(it, "tum") } }, kilitli = o.kilitli,
                sec = { vm.katmanDegis(o.id) { it.copy(satirGizli = false) }; vm.seciliKatman.value = o.id }) { v -> vm.katmanDegis(o.id) { if (ses) it.copy(ses = v) else it.copy(opaklik = v) } }
        }
        if (p.katmanlar.size > 1) DugmeSirasi(
            "Hepsi tüm video" to { vm.katmanHepsi { vm.katmanAralik(it, "tum") }; vm.mesaj.value = "Tüm katmanlar video boyunca" },
            "Hepsi bu klipte" to { vm.katmanHepsi { vm.katmanAralik(it, "klip") }; vm.mesaj.value = "Tüm katmanlar bu klip boyunca" },
        )
    }
}

@Composable
private fun HizliSatir(simge: String, ad: String, renk: Color, deger: Float, aralik: ClosedFloatingPointRange<Float>, kapali: Boolean, olcu: String,
                       kapat: () -> Unit, tum: (() -> Unit)?, kilitli: Boolean = false, sec: (() -> Unit)? = null, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).then(if (sec != null) Modifier.clickable(onClick = sec) else Modifier), verticalAlignment = Alignment.CenterVertically) {
                Simge(simge, renk, 16.dp); Spacer(Modifier.width(6.dp))
                Text(ad, color = if (kapali) Renk.soluk else Renk.yazi, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("$olcu %${(v * 100).roundToInt()}", color = Renk.soluk, fontSize = 11.sp)
            }
            if (tum != null) SimgeDugme("kadraj", "Tüm video", Modifier.width(60.dp), renk = tonRenk(3, ""), etkin = !kilitli, onClick = tum)
            SimgeDugme(if (olcu == "Ses") (if (kapali) "sessiz" else "ses") else "gorunum",
                if (olcu == "Ses") (if (kapali) "Aç" else "Kapat") else (if (kapali) "Göster" else "Gizle"), Modifier.width(56.dp),
                renk = if (kapali) Renk.kirmizi else tonRenk(4, ""), etkin = !kilitli, onClick = kapat)
        }
        InceSlider(value = v, onValueChange = { v = it }, valueRange = aralik, enabled = !kilitli && !kapali, onValueChangeFinished = { bitti(v) })
    }
}

/** 3.53: Metin › Yazı — Katman › Yazı ekle ile ORTAK veri (yazı katmanları) ve aynı ayarlar. İki ayrı yazı sayfası yok:
 *  buradan eklenen yazı Katmanlar çizelgesinde de görünür, oradan eklenen burada. Eski klip yazısı (Klip.yazi) silinmez;
 *  istenirse tek dokunuşla yazı katmanına taşınır. */
@Composable
fun YaziSayfasi(vm: EditorDurum, p: Proje, sec: Int) {
    val secId by vm.seciliKatman.collectAsState()
    val konum by vm.konum.collectAsState()
    val duzenle by vm.katmanDuzenle.collectAsState()
    LaunchedEffect(duzenle) { if (duzenle != null) vm.katmanDuzenle.value = null }   // bu sayfada ayarlar zaten açık; açılır pencere gerekmez
    if (p.klipler.isEmpty()) {
        Text("Önce en az bir klip ekle; yazılar videonun üstüne yerleşir.", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.padding(16.dp)); return
    }
    val yazilar = p.katmanlar.filter { it.tur == "metin" }
    Kart("Yazı ekle") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SimgeDugme("metin", "Yazı ekle", Modifier.width(72.dp), renk = tonRenk(0, "")) { vm.katmanMetinEkle() }
            Text("Yazı oynatıcının bulunduğu andan başlar. Katman › Yazı ekle ile aynı yazıdır.", color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        Text("Hazır yazılar", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        HazirYazilar(vm)
    }
    yazilar.firstOrNull { it.id == secId }?.let { KatmanAyarlari(vm, p, it, konum) }
    if (yazilar.isNotEmpty()) Kart("Videodaki yazılar (${yazilar.size})") {
        for (o in yazilar) key(o.id) {
            val secili = o.id == secId
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp))
                .background(if (secili) Renk.kart2 else Color.Transparent)
                .clickable { vm.seciliKatman.value = o.id; vm.git(o.basMs) }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Simge("metin", if (secili) Renk.turuncu else Renk.soluk, 16.dp)
                Text(o.metin.lineSequence().firstOrNull()?.ifBlank { null } ?: "Yazı", color = if (o.gizli) Renk.soluk else Renk.yazi, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text("${sureOndalik(o.basMs)} – ${sureOndalik(o.sonMs)}", color = Renk.soluk, fontSize = 11.sp)
            }
        }
    }
    p.klipler.getOrNull(sec)?.let { k -> EskiKlipYazisi(vm, k, sec) }
}

/** 3.53: eski "klip yazısı" (3.4 öncesi veri) — görünmeye devam eder; yazı katmanına taşınabilir ya da kaldırılabilir. */
@Composable
fun EskiKlipYazisi(vm: EditorDurum, k: Klip, i: Int) {
    val y = k.yazi?.takeIf { it.metin.isNotBlank() } ?: return
    Kart("Bu klibin eski yazısı") {
        Text(y.metin, color = Renk.yazi, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("Bu yazı eski biçimde klibe bağlı. Yazı katmanına taşıyınca diğer yazılar gibi düzenlenir.", color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
        DugmeSirasi(
            "Yazı katmanına taşı" to { vm.klipYazisiniKatmanaTasi(i) },
            "Kaldır" to { vm.klipGuncelle { it.copy(yazi = null) } },
        )
    }
}

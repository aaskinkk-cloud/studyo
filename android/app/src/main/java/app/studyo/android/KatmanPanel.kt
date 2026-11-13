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
        Text("Yeni katman oynatıcının bulunduğu andan başlar. Önizlemede katmana dokun = seç · sürükle = taşı · iki parmak = büyüt/döndür.",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
    }

    if (p.katmanlar.isNotEmpty()) Kart("Katmanlar · ${p.katmanlar.size}") {
        Text("Üstteki satır en üstteki katmandır. ≡ tutup sürükle = sıra · çubuğun ucunu sürükle = başı/sonu · ortasını sürükle = kaydır · dokun = oraya git.",
            color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        val n = p.katmanlar.size
        val toplam = p.toplamMs.coerceAtLeast(1000)
        for (d in 0 until n) {
            val i = n - 1 - d
            val o = p.katmanlar[i]
            key(o.id) { KatmanSatiri(vm, o, i, n, toplam, konum, o.id == secId) }
        }
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

/** Zaman çizelgesi satırı: tutamaç (sıra) + ad + süre çubuğu (başı/sonu/kaydır). */
@Composable
private fun KatmanSatiri(vm: EditorDurum, o: KatmanOge, i: Int, n: Int, toplam: Long, konum: Long, secili: Boolean) {
    val yog = LocalDensity.current
    val satirPx = with(yog) { 64.dp.toPx() }
    val ucPx = with(yog) { 22.dp.toPx() }
    val ton = tonRenk(i, o.id)
    val oG by rememberUpdatedState(o); val iG by rememberUpdatedState(i)
    var kayma by remember { mutableFloatStateOf(0f) }               // sıra sürüklemesi (görsel)
    var gec by remember { mutableStateOf<Pair<Long, Long>?>(null) } // süre sürüklemesi (geçici)
    val (bas, son) = gec ?: (o.basMs to o.sonMs)
    val sekil = RoundedCornerShape(10.dp)
    Row(
        Modifier.fillMaxWidth().height(64.dp).graphicsLayer { translationY = kayma }
            .clip(sekil).background(if (secili) ton.copy(alpha = 0.10f) else Color.Transparent)
            .border(if (secili) 1.dp else 0.5.dp, if (secili) ton else Renk.cizgi.copy(alpha = 0.5f), sekil)
            .clickable { vm.seciliKatman.value = o.id },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // sıra tutamacı
        Box(Modifier.width(30.dp).fillMaxHeight().pointerInput(o.id) {
            detectVerticalDragGestures(
                onDragEnd = { kayma = 0f }, onDragCancel = { kayma = 0f },
            ) { ch, dy ->
                ch.consume()
                kayma += dy
                // görünüm ters sıralı: aşağı = alt katmana (liste sırası küçülür)
                if (kayma > satirPx * 0.6f && iG > 0) { vm.katmanTasi(iG, iG - 1); kayma -= satirPx }
                else if (kayma < -satirPx * 0.6f && iG < n - 1) { vm.katmanTasi(iG, iG + 1); kayma += satirPx }
            }
        }, contentAlignment = Alignment.Center) { Text("≡", color = Renk.soluk, fontSize = 20.sp) }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Simge(turSimge(o.tur), if (o.gizli) Renk.soluk else ton, 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(o.ad.ifBlank { turAdi(o.tur) }.let { if (o.tur == "metin") o.metin.lineSequence().firstOrNull() ?: it else it },
                    color = if (o.gizli) Renk.soluk else Renk.yazi, fontSize = 12.5.sp, fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val etiket = buildList { if (o.gizli) add("gizli"); if (o.kilitli) add("kilitli") }.joinToString(" · ")
                Text(if (etiket.isEmpty()) "${sureOndalik(bas)}–${sureOndalik(son)}" else etiket, color = Renk.soluk, fontSize = 10.5.sp)
            }
            // süre çubuğu
            Box(Modifier.fillMaxWidth().height(26.dp).padding(top = 4.dp)
                .pointerInput(o.id) {
                    detectTapGestures { t -> vm.seciliKatman.value = oG.id; vm.git((t.x / size.width * toplam).toLong().coerceIn(0, toplam)) }
                }
                .pointerInput(o.id, o.kilitli, toplam) {
                    if (o.kilitli) return@pointerInput
                    var kip = 0; var b0 = 0L; var s0 = 0L; var biriken = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { t ->
                            val w = size.width.toFloat()
                            val xb = oG.basMs / toplam.toFloat() * w; val xs = oG.sonMs / toplam.toFloat() * w
                            kip = when {
                                abs(t.x - xb) < ucPx && abs(t.x - xb) <= abs(t.x - xs) -> 1
                                abs(t.x - xs) < ucPx -> 2
                                t.x in xb..xs -> 3
                                else -> 0
                            }
                            b0 = oG.basMs; s0 = oG.sonMs; biriken = 0f
                            vm.seciliKatman.value = oG.id
                        },
                        onDragEnd = {
                            gec?.let { (a, b) -> vm.katmanDegis(oG.id) { it.copy(basMs = a, sonMs = b) } }
                            gec = null; kip = 0
                        },
                        onDragCancel = { gec = null; kip = 0 },
                    ) { ch, dx ->
                        if (kip == 0) return@detectHorizontalDragGestures
                        ch.consume()
                        biriken += dx
                        val d = (biriken / size.width * toplam).toLong()
                        val enUzun = if (oG.tur == "ses" && oG.kaynakSureMs > 0) ((oG.kaynakSureMs - oG.kaynakBasMs) / oG.hiz.coerceAtLeast(0.25f)).toLong() else Long.MAX_VALUE
                        gec = when (kip) {
                            1 -> (b0 + d).coerceIn(maxOf(0L, s0 - enUzun), s0 - 300) to s0
                            2 -> b0 to (s0 + d).coerceIn(b0 + 300, minOf(toplam, b0 + enUzun))
                            else -> { val L = s0 - b0; val a = (b0 + d).coerceIn(0, (toplam - L).coerceAtLeast(0)); a to a + L }
                        }
                    }
                }) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    drawRoundRect(Renk.cizgi.copy(alpha = 0.35f), Offset(0f, h * 0.35f), Size(w, h * 0.3f), CornerRadius(h * 0.15f))
                    val xb = bas / toplam.toFloat() * w; val xs = son / toplam.toFloat() * w
                    val renk = if (o.gizli) Renk.soluk else ton
                    drawRoundRect(renk.copy(alpha = if (secili) 0.85f else 0.55f), Offset(xb, 0f), Size((xs - xb).coerceAtLeast(3f), h), CornerRadius(h * 0.25f))
                    if (secili && !o.kilitli) {
                        drawRoundRect(Color.White, Offset(xb + 2f, h * 0.2f), Size(5f, h * 0.6f), CornerRadius(3f))
                        drawRoundRect(Color.White, Offset(xs - 7f, h * 0.2f), Size(5f, h * 0.6f), CornerRadius(3f))
                    }
                    val xk = konum.coerceIn(0, toplam) / toplam.toFloat() * w
                    drawLine(Renk.turuncu, Offset(xk, 0f), Offset(xk, h), 2.5f)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
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
    val i = p.katmanlar.indexOfFirst { it.id == o.id }
    var silOnay by remember(o.id) { mutableStateOf(false) }
    Kart("Seçili katman · ${turAdi(o.tur)}") {
        if (o.kilitli) Text("Kilitli: değiştirmek için aşağıdan kilidi aç.", color = Renk.turuncu, fontSize = 12.sp)
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

        // işlemler: tek sıra, boş hücre yok
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SimgeDugme("yukle", "Öne al", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = e && i < p.katmanlar.size - 1) { vm.katmanTasi(i, i + 1) }
            SimgeDugme("asagi", "Arkaya al", Modifier.weight(1f), renk = tonRenk(1, ""), etkin = e && i > 0) { vm.katmanTasi(i, i - 1) }
            SimgeDugme("cogalt", "Çoğalt", Modifier.weight(1f), renk = tonRenk(2, "")) { vm.katmanCogalt(o.id) }
            SimgeDugme("gorunum", if (o.gizli) "Göster" else "Gizle", Modifier.weight(1f), renk = tonRenk(3, ""), etkin = e) { d { it.copy(gizli = !it.gizli) } }
            SimgeDugme(if (o.kilitli) "uygula" else "kapat", if (o.kilitli) "Kilidi aç" else "Kilitle", Modifier.weight(1f), renk = tonRenk(4, "")) { d { it.copy(kilitli = !it.kilitli) } }
            SimgeDugme("sil", if (silOnay) "Emin misin?" else "Sil", Modifier.weight(1f), renk = Renk.kirmizi, etkin = e) {
                if (silOnay) vm.katmanSil(o.id) else silOnay = true
            }
        }
        if (silOnay) Text("Silmek için “Emin misin?”e bir kez daha dokun.", color = Renk.kirmizi, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

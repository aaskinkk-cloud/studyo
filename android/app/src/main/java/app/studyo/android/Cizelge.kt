package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val SATIR_VIDEO = 52.dp
private val SATIR = 36.dp
private val ETIKET = 62.dp
private val CETVEL = 18.dp

private fun katRenk(t: String): Color = when (t) {
    "metin" -> Color(0xFF7E57C2); "gorsel" -> Color(0xFF2EC5D3); "cikartma" -> Color(0xFFF5A524); "ses" -> Color(0xFF4CC38A); else -> Color(0xFF8AA0B2)
}

/** Oynarken her ekran karesinde konum (önizlemeyle aynı yöntem). */
@Composable
private fun akiciKonum(vm: EditorDurum): Long {
    val akis by vm.konum.collectAsState()
    val oyn by vm.oynuyor.collectAsState()
    var kare by remember { mutableLongStateOf(0L) }
    LaunchedEffect(oyn) {
        if (!oyn) return@LaunchedEffect
        var son = vm.anlik()
        while (true) { withFrameNanos { }; val t = vm.anlik(); if (t >= son || son - t > 250) son = t; kare = son }
    }
    return if (oyn) kare else akis
}

/** Çok satırlı zaman çizelgesi (InShot tarzı): oynatma çizgisi ortada sabit, çizelge altından kayar.
 *  1. satır videolar (kareler), altında her katman ayrı satır (üstteki = en üstteki katman), müzik ve seslendirme.
 *  Tek parmak yatay = sar · iki parmak = yakınlaştır · çubuğa dokun = seç · seçili çubuğu sürükle = taşı/başı-sonu/satır. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CokluCizelge(vm: EditorDurum, p: Proje, acMuzik: () -> Unit, acSes: () -> Unit, modifier: Modifier, acPanel: (String, Int) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val konum = akiciKonum(vm)
    val secK by vm.seciliKatman.collectAsState()
    val toplam = p.toplamMs.coerceAtLeast(1)
    val pxMs = with(yog) { Duzen.cizelgePps.dp.toPx() } / 1000f
    val konumG by rememberUpdatedState(konum)
    val pxMsG by rememberUpdatedState(pxMs)
    val toplamG by rememberUpdatedState(toplam)

    Column(modifier) {
        // ---- işlemler: tek yerde, tek sıra. Katman seçiliyse katmana, video klibi seçiliyse klibe uygulanır (seçim yoksa soluk) ----
        val ko = p.katmanlar.firstOrNull { it.id == secK }
        val ki = p.katmanlar.indexOfFirst { it.id == secK }
        var gizliGoster by remember { mutableStateOf(false) }
        val gizliSay = p.katmanlar.count { it.satirGizli }
        val secKlip by vm.secili.collectAsState()
        var klipSecili by remember { mutableStateOf(false) }
        LaunchedEffect(secK) { if (secK != null) klipSecili = false }
        val kl = if (ko == null && klipSecili) p.klipler.getOrNull(secKlip) else null
        var silOnay by remember(secK, klipSecili, secKlip) { mutableStateOf(false) }
        val e = ko != null && !ko.kilitli
        // 3.45: araç sırası tek satır (sağa kayar) ya da alt alta satırlar; seçim hatırlanır
        val tercih = remember { ctx.getSharedPreferences("duzen", 0) }
        var ikiSatir by remember { mutableStateOf(tercih.getBoolean("katmanIkiSatir", false)) }
        val aktifKlip = p.klipler.getOrNull(secKlip)
        val araclar: @Composable () -> Unit = {
            // 3.42: yazı ekleme her zaman ilk sırada; sıra sağa kayar
            SimgeDugme("metin", "Yazı ekle", Modifier.width(62.dp), renk = Renk.turuncu) { vm.katmanMetinEkle() }
            // 3.45: oynatıcının üstündeki klibi tek dokunuşla kadraja doldur / eski haline (Sığdır) döndür
            if (aktifKlip != null) SimgeDugme("kadraj", if (aktifKlip.doldur) "Sığdır" else "Doldur", Modifier.width(62.dp), renk = tonRenk(2, "")) {
                vm.klipGuncelle { it.copy(doldur = !it.doldur) }; vm.mesaj.value = if (aktifKlip.doldur) "Klip sığdırıldı" else "Klip kadraja dolduruldu"
            }
            if (kl != null) {
                val n = p.klipler.size
                SimgeDugme("makas", "Böl", Modifier.width(62.dp), renk = tonRenk(0, "")) { vm.klipBolSecili() }
                SimgeDugme("cogalt", "Çoğalt", Modifier.width(62.dp), renk = tonRenk(1, "")) { vm.klipCogalt() }
                SimgeDugme("yukle", "Öne al", Modifier.width(62.dp), renk = tonRenk(2, ""), etkin = secKlip > 0) { vm.tasi(-1) }
                SimgeDugme("asagi", "Sona al", Modifier.width(62.dp), renk = tonRenk(3, ""), etkin = secKlip < n - 1) { vm.tasi(1) }
                SimgeDugme(if (kl.sessiz) "sessiz" else "ses", if (kl.sessiz) "Sesi aç" else "Sessiz", Modifier.width(62.dp), renk = tonRenk(4, ""),
                    etkin = !kl.foto && kl.sesVar) { vm.sesAcKapat() }
                SimgeDugme("kapat", "Bırak", Modifier.width(62.dp), renk = tonRenk(5, "")) { klipSecili = false }
                SimgeDugme("sil", if (silOnay) "Emin misin?" else "Sil", Modifier.width(62.dp), renk = Renk.kirmizi) {
                    if (silOnay) { vm.klipSil(); silOnay = false; if (vm.proje.value.klipler.isEmpty()) klipSecili = false } else silOnay = true
                }
            } else {
            SimgeDugme("makas", "Böl", Modifier.width(62.dp), renk = tonRenk(0, ""), etkin = e) { ko?.let { vm.katmanBol(it.id) } }
            SimgeDugme("kadraj", "Tüm video", Modifier.width(62.dp), renk = tonRenk(3, ""), etkin = e) { ko?.let { vm.katmanDegis(it.id) { o -> vm.katmanAralik(o, "tum") } } }
            // 3.50: uçlar menüsü her zaman araç sırasında (çubuğun ucuna dokunmak da açar) + süreyi düğmeyle genişlet/daralt
            Box {
                var acUc by remember { mutableStateOf(false) }
                SimgeDugme("sonraki", "Uçlar", Modifier.width(62.dp), renk = tonRenk(5, ""), etkin = e) { acUc = true }
                androidx.compose.material3.DropdownMenu(expanded = acUc && ko != null, onDismissRequest = { acUc = false }, containerColor = Renk.kart) {
                    listOf("klipBas" to "Klip başı  |‹", "videoBas" to "Video başı  |«", "klipSon" to "Klip sonu  ›|", "videoSon" to "Video sonu  »|", "basaTasi" to "Videonun başına taşı  |◀").forEach { (t, ad) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(ad, color = Renk.yazi) }, onClick = {
                            ko?.let { vm.katmanDegis(it.id) { o -> vm.katmanUc(o, t) } }; if (t == "basaTasi") vm.git(0); acUc = false })
                    }
                }
            }
            SimgeDugme("eksi", "Kısalt", Modifier.width(62.dp), renk = tonRenk(6, ""), etkin = e) { ko?.let { vm.katmanUcKaydir(it.id, 0, -500) } }
            SimgeDugme("ekle", "Uzat", Modifier.width(62.dp), renk = tonRenk(7, ""), etkin = e) { ko?.let { vm.katmanUcKaydir(it.id, 0, 500) } }
            SimgeDugme("basa", "Başı=şimdi", Modifier.width(62.dp), renk = tonRenk(6, ""), etkin = e && konum < (ko?.sonMs ?: 0) - 300) { ko?.let { vm.katmanDegis(it.id) { o -> o.copy(basMs = konum.coerceAtLeast(0)) } } }
            SimgeDugme("sonraki", "Sonu=şimdi", Modifier.width(62.dp), renk = tonRenk(7, ""), etkin = e && konum > (ko?.basMs ?: Long.MAX_VALUE) + 300) { ko?.let { vm.katmanDegis(it.id) { o -> o.copy(sonMs = konum.coerceAtMost(toplam)) } } }
            SimgeDugme("cogalt", "Çoğalt", Modifier.width(62.dp), renk = tonRenk(1, ""), etkin = ko != null) { ko?.let { vm.katmanCogalt(it.id) } }
            SimgeDugme("yukle", "Öne al", Modifier.width(62.dp), renk = tonRenk(2, ""), etkin = e && ki < p.katmanlar.size - 1) { vm.katmanTasi(ki, ki + 1) }
            SimgeDugme("asagi", "Arkaya al", Modifier.width(62.dp), renk = tonRenk(3, ""), etkin = e && ki > 0) { vm.katmanTasi(ki, ki - 1) }
            SimgeDugme("gorunum", if (ko?.gizli == true) "Videoda göster" else "Videoda gizle", Modifier.width(62.dp), renk = tonRenk(4, ""), etkin = e) { ko?.let { vm.katmanDegis(it.id) { o -> o.copy(gizli = !o.gizli) } } }
            // 3.47: yalnız çizelgede satırı gizle — katman videoda görünür/çalar (geniş çalışma alanı için)
            SimgeDugme("katman", if (ko?.satirGizli == true) "Satırı göster" else "Satırı gizle", Modifier.width(62.dp), renk = tonRenk(6, ""), etkin = ko != null) {
                ko?.let { vm.katmanDegis(it.id) { o -> o.copy(satirGizli = !o.satirGizli) }; if (!it.satirGizli) { vm.seciliKatman.value = null; vm.mesaj.value = "Satır gizlendi · videoda görünmeye devam eder" } }
            }
            SimgeDugme(if (ko?.kilitli == true) "uygula" else "kapat", if (ko?.kilitli == true) "Kilidi aç" else "Kilitle", Modifier.width(62.dp), renk = tonRenk(5, ""), etkin = ko != null) {
                ko?.let { vm.katmanDegis(it.id) { o -> o.copy(kilitli = !o.kilitli) } }
            }
            SimgeDugme("sil", if (silOnay) "Emin misin?" else "Sil", Modifier.width(62.dp), renk = Renk.kirmizi, etkin = e) {
                if (silOnay) { ko?.let { vm.katmanSil(it.id) }; silOnay = false } else silOnay = true
            }
            }
                    SimgeDugme("katman", if (ikiSatir) "Tek satır" else "Alt alta", Modifier.width(62.dp), renk = Renk.soluk) {
                ikiSatir = !ikiSatir; tercih.edit().putBoolean("katmanIkiSatir", ikiSatir).apply()
            }
        }
        if (ikiSatir) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) { araclar() }
        else Row(Modifier.fillMaxWidth().height(50.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) { araclar() }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFF14191E))) {
            val izGen = with(yog) { (maxWidth - ETIKET).toPx() }
            val merkez = izGen / 2f
            fun xMs(ms: Long): Float = merkez + (ms - konum) * pxMs

            // ---- sarma (tek parmak yatay) ve yakınlaştırma (iki parmak) ----
            val alan = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val ilk = awaitFirstDown(requireUnconsumed = false)
                    var kip = 0                 // 0 karar yok · 1 sar · 2 yakınlaştır · 3 dikey (dokunma)
                    var tx = 0f; var ty = 0f; var bas = konumG; var d0 = 1f; var pps0 = Duzen.cizelgePps
                    do {
                        val ev = awaitPointerEvent()
                        val basili = ev.changes.filter { it.pressed }
                        if (basili.size >= 2) {
                            val d = abs(basili[0].position.x - basili[1].position.x).coerceAtLeast(12f)
                            if (kip != 2) { kip = 2; d0 = d; pps0 = Duzen.cizelgePps }
                            else Duzen.cizelgePps = (pps0 * d / d0).coerceIn(4f, 400f)
                            ev.changes.forEach { it.consume() }
                        } else if (basili.size == 1 && kip != 2 && kip != 3) {
                            val c = basili[0]
                            if (c.isConsumed) continue
                            val dx = c.position.x - c.previousPosition.x; val dy = c.position.y - c.previousPosition.y
                            if (kip == 0) {
                                tx += dx; ty += dy
                                if (abs(tx) > viewConfiguration.touchSlop && abs(tx) > abs(ty)) {
                                    kip = 1; bas = konumG; tx = 0f
                                    if (vm.player.isPlaying) vm.player.pause()
                                } else if (abs(ty) > viewConfiguration.touchSlop) kip = 3
                            } else {
                                tx += dx
                                vm.git((bas - tx / pxMsG).toLong().coerceIn(0, toplamG - 1))
                                c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    if (kip == 2) Duzen.ppsKaydet(ctx)
                }
            }

            Column(Modifier.fillMaxSize().then(alan)) {
                // ---- cetvel ----
                Row(Modifier.fillMaxWidth().height(CETVEL)) {
                    // 3.47: köşe = gizli satırları göster/gizle (gizli satır varsa)
                    Box(Modifier.width(ETIKET).fillMaxHeight().then(if (gizliSay > 0) Modifier.clickable { gizliGoster = !gizliGoster } else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (gizliSay == 0) Text("Zaman", color = Renk.soluk, fontSize = 10.sp)
                        else Text(if (gizliGoster) "◉ Gizle" else "◌ Gizli $gizliSay", color = Renk.turuncu, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight()) {
                        val adimlar = longArrayOf(500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000)
                        val enAz = 56.dp.toPx()
                        val adim = adimlar.firstOrNull { it * pxMs >= enAz } ?: 600000L
                        var t = ((konum - merkez / pxMs).toLong() / adim).coerceAtLeast(0) * adim
                        val paint = android.graphics.Paint().apply { color = 0xFF8AA0B2.toInt(); textSize = 9.sp.toPx(); isAntiAlias = true }
                        while (true) {
                            val x = xMs(t); if (x > size.width) break
                            if (t <= toplam) {
                                drawLine(Color(0xFF8AA0B2), Offset(x, size.height * 0.55f), Offset(x, size.height), 1.5f)
                                drawContext.canvas.nativeCanvas.drawText(sureYaz(t), x + 3f, size.height * 0.5f, paint)
                            }
                            t += adim
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    // ---- video satırı: yalnız başvuru (kareler); klip işleri Düzenle › Klip'te ----
                    IzSatiri("klip", "Video", SATIR_VIDEO, Renk.turkuaz, onClick = { vm.seciliKatman.value = null; klipSecili = false }) {
                        var bas = 0L
                        for (i in p.klipler.indices) {
                            val zk = p.zKlip(i); val k = p.klipler[i]
                            val b0 = bas; bas += zk.sureMs
                            val x0 = xMs(b0); val x1 = xMs(b0 + zk.sureMs)
                            if (x1 < -50f || x0 > izGen + 50f) continue
                            key(k.id) { KlipCubugu(i, k, x0, x1 - x0, izGen, klipSecili && i == secKlip && secK == null,
                                tasi = { dx ->
                                    // 3.46: basılı tut + sağa/sola sürükle = klibi taşı; bırakılan yerdeki klibin sırasına geçer
                                    val hedefMs = (b0 + zk.sureMs / 2 + dx / pxMs).toLong().coerceIn(0, toplam - 1)
                                    var t = 0L; var j = 0
                                    while (j < p.klipler.size - 1 && t + p.zKlip(j).sureMs <= hedefMs) { t += p.zKlip(j).sureMs; j++ }
                                    if (j != i) { vm.klipTasi(i, j); klipSecili = true; vm.mesaj.value = "Klip ${i + 1} → ${j + 1}. sıraya taşındı" }
                                }) {
                                vm.seciliKatman.value = null; vm.secili.value = i; klipSecili = true } }
                        }
                    }
                    // ---- katman satırları (üstteki en üstte) ----
                    val n = p.katmanlar.size
                    for (d in 0 until n) {
                        val i = n - 1 - d
                        val o = p.katmanlar[i]
                        if (o.satirGizli && !gizliGoster) continue   // 3.47: yalnız çizelgede gizli satır (videoda görünür); "Zaman" köşesinden gösterilir
                        key(o.id) {
                            IzSatiri(turSimgeC(o.tur), (if (o.satirGizli) "◌ " else "") + o.ad.ifBlank { o.metin }.ifBlank { "Katman" }, SATIR, katRenk(o.tur).copy(alpha = if (o.gizli || o.satirGizli) 0.4f else 1f),
                                tasi = if (o.kilitli) null else { adim -> vm.katmanTasi(i, (i - adim).coerceIn(0, n - 1)); vm.mesaj.value = if (adim < 0) "Öne alındı" else "Arkaya gönderildi" },
                                onClick = {
                                    // 3.45: simgeye her dokunuşta aynı türdeki bir sonraki öğeye geçer (zamana göre), sahnesine gider
                                    val ayni = p.katmanlar.filter { it.tur == o.tur }.sortedBy { it.basMs }
                                    val simdiki = ayni.indexOfFirst { it.id == secK }
                                    val hedef = if (simdiki >= 0) ayni[(simdiki + 1) % ayni.size] else o
                                    vm.seciliKatman.value = hedef.id; vm.git(hedef.basMs)
                                }) {
                                KatmanCubugu(vm, o, i, n, xMs(o.basMs), (o.sonMs - o.basMs) * pxMs, o.id == secK, pxMs, toplam, izGen)
                            }
                        }
                    }
                    // ---- klip yazıları ve takip yazıları (3.44): katman değiller ama ekranda görünürler; burada da bulunur ----
                    val yaziK = p.klipler.indices.filter { p.klipler[it].yazi?.metin?.isNotBlank() == true }
                    if (yaziK.isNotEmpty()) IzSatiri("metin", "Klip yazısı", SATIR, Color(0xFFB39DDB), onClick = { val i = yaziK.firstOrNull { it > secKlip } ?: yaziK.first(); vm.sec(i); acPanel("yazi", i) }) {
                        var bas = 0L
                        for (i in p.klipler.indices) {
                            val d = p.zKlip(i).sureMs; val b0 = bas; bas += d
                            if (i !in yaziK) continue
                            key("y" + p.klipler[i].id) {
                                EkCubuk(p.klipler[i].yazi!!.metin.lineSequence().first(), xMs(b0), d * pxMs, izGen, Color(0xFFB39DDB)) { vm.sec(i); acPanel("yazi", i) }
                            }
                        }
                    }
                    val takipK = p.klipler.indices.filter { p.klipler[it].takip != null }
                    if (takipK.isNotEmpty()) IzSatiri("takip", "Takip yazısı", SATIR, Color(0xFFF2C94C), onClick = { val i = takipK.firstOrNull { it > secKlip } ?: takipK.first(); vm.sec(i); acPanel("takip", i) }) {
                        var bas = 0L
                        for (i in p.klipler.indices) {
                            val zk = p.zKlip(i); val b0 = bas; bas += zk.sureMs
                            val t = p.klipler[i].takip ?: continue
                            val k = p.klipler[i]
                            // kaynak zamanı → çizelge zamanı (hız 1 kabulüyle klip içi)
                            val a = b0 + (t.basMs - k.basMs).coerceAtLeast(0)
                            val son = b0 + (Katman.takipSon(t).coerceAtMost(k.sonMs) - k.basMs).coerceIn(0, zk.sureMs)
                            key("t" + k.id) { EkCubuk(t.metin.ifBlank { "Takip" }, xMs(a), (son - a).coerceAtLeast(300) * pxMs, izGen, Color(0xFFF2C94C)) { vm.sec(i); vm.git(a); acPanel("takip", i) } }
                        }
                    }
                    // ---- müzik ----
                    p.muzik?.let { m ->
                        val son = if (m.dongu || m.sureMs <= 0) p.toplamMs else minOf(m.sureMs, p.toplamMs)
                        IzSatiri("nota", "Müzik", SATIR, Color(0xFF4CC38A), onClick = { vm.git(0); vm.sesPenceresi.value = "muzik" }) {
                            SesCubugu(m.uri, m.ad, xMs(0), son * pxMs, izGen, Color(0xFF4CC38A), 0L, m.sureMs, 1f, pxMs) { vm.sesPenceresi.value = "muzik" }
                        }
                    }
                    // ---- seslendirme ----
                    if (p.sesler.isNotEmpty()) IzSatiri("mikrofon", "Ses kaydı", SATIR, Color(0xFFEF5350), onClick = {
                        val l = p.sesler.sortedBy { it.basMs }; val h = l.firstOrNull { it.basMs > konum + 100 } ?: l.first(); vm.git(h.basMs)
                    }) {
                        for (s in p.sesler) key(s.id) {
                            SesCubugu(android.net.Uri.fromFile(java.io.File(s.yol)).toString(), "", xMs(s.basMs), s.sureMs * pxMs, izGen, Color(0xFFEF5350), 0L, s.sureMs, 1f, pxMs, acSes)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            // ---- oynatma çizgisi (ortada sabit) + zaman ----
            Box(Modifier.offset { IntOffset((with(yog) { ETIKET.toPx() } + merkez - 1.5f).roundToInt(), 0) }.width(3.dp).fillMaxHeight().background(Color.White))
            Text(sureOndalik(konum), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset { IntOffset((with(yog) { ETIKET.toPx() } + merkez + 6f).roundToInt(), 0) }
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 1.dp))
        }
    }
}

private fun turSimgeC(t: String) = when (t) { "metin" -> "metin"; "gorsel" -> "kapak"; "cikartma" -> "yildiz"; "ses" -> "nota"; else -> "katman" }

/** Bir iz satırı: solda simge + ad (sabit), sağda çubuklar (mutlak konumlu). */
@Composable
private fun IzSatiri(simge: String, ad: String, yuk: androidx.compose.ui.unit.Dp, renk: Color, onClick: () -> Unit,
                     tasi: ((Int) -> Unit)? = null, icerik: @Composable BoxScope.() -> Unit) {
    // 3.47: satır başlığını yukarı/aşağı sürükle = katmanı öne/arkaya taşı (satır parmağı izler)
    var dy by remember { mutableFloatStateOf(0f) }
    val satirPx = with(LocalDensity.current) { yuk.toPx() }
    Row(Modifier.fillMaxWidth().height(yuk).zIndex(if (dy != 0f) 1f else 0f).graphicsLayer { translationY = dy; if (dy != 0f) { shadowElevation = 12f } }
        .background(if (dy != 0f) Color(0xFF22303C) else Color.Transparent).padding(vertical = 2.dp)) {
        Row(Modifier.width(ETIKET).fillMaxHeight().background(Color(0xFF14191E)).clickable(onClick = onClick)
            .then(if (tasi == null) Modifier else Modifier.pointerInput(tasi) {
                detectVerticalDragGestures(
                    onDragEnd = { val a = (dy / satirPx).roundToInt(); dy = 0f; if (a != 0) tasi(a) },
                    onDragCancel = { dy = 0f },
                ) { ch, d -> ch.consume(); dy += d }
            })
            .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Simge(simge, renk, 14.dp)
            Spacer(Modifier.width(3.dp))
            Text(ad, color = Color(0xFFC9D4DE), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), content = icerik)
    }
}

/** Görünür parça: çubuk ekrandan uzunsa yalnız ekrandaki kısmı ölçülür. KÖK DÜZELTME (3.41): eskiden tam genişlik verilince
 *  Compose genişliği ekran genişliğine kırpıyordu → uzun klip/katman yarıda bitip arkası siyah görünüyordu. (görünür x, görünür w, kayma) */
private fun gorunur(x: Float, w: Float, izGen: Float): Triple<Float, Float, Float> {
    val pay = 40f
    val v0 = maxOf(x, -pay); val v1 = minOf(x + w, izGen + pay)
    return Triple(v0, (v1 - v0).coerceAtLeast(0f), x - v0)
}

/** Video klibi: kareler yan yana, sıra numarası; dokun = seç (araç sırası klibe uygulanır). */
@Composable
private fun KlipCubugu(i: Int, k: Klip, x: Float, w: Float, izGen: Float, secili: Boolean, tasi: (Float) -> Unit = {}, sec: () -> Unit) {
    var surDx by remember { mutableFloatStateOf(0f) }; var surIyor by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val resim by produceState<ImageBitmap?>(null, k.uri, k.basMs) { value = Medya.kucukResim(ctx, k, 160)?.asImageBitmap() }
    val sekil = RoundedCornerShape(6.dp)
    val (vx, vw, kay) = gorunur(x, w.coerceAtLeast(6f), izGen)
    if (vw <= 0f) return
    Box(Modifier.offset { IntOffset((vx + surDx).roundToInt(), if (surIyor) -6 else 0) }.width(with(yog) { vw.toDp() }).fillMaxHeight()
        .graphicsLayer { if (surIyor) { alpha = 0.85f; scaleY = 1.06f } }
        .padding(horizontal = 1.dp).clip(sekil).background(Color(0xFF243444))
        .border(if (secili || surIyor) 2.dp else 0.5.dp, if (surIyor) Renk.turuncu else if (secili) Color.White else Color(0x55FFFFFF), sekil)
        .pointerInput(k.id) { detectTapGestures(onTap = { sec() }) }
        .pointerInput(k.id, i) {
            detectDragGesturesAfterLongPress(
                onDragStart = { surIyor = true; surDx = 0f; sec() },
                onDragEnd = { val d = surDx; surIyor = false; surDx = 0f; tasi(d) },
                onDragCancel = { surIyor = false; surDx = 0f },
            ) { ch, d -> ch.consume(); surDx += d.x }
        }) {
        resim?.let { im ->
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height; val tw = (h * im.width / im.height.coerceAtLeast(1)).coerceAtLeast(8f)
                // kareler klibin gerçek başına göre dizilir (ekran dışı kısım atlanır), kaydırınca kareler de kayar
                var xx = kay + kotlin.math.floor(-kay / tw) * tw
                while (xx < size.width) {
                    drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset(xx.toInt(), 0), IntSize(tw.toInt() + 1, h.toInt()))
                    xx += tw
                }
            }
        }
        if (kay > -4f) Text("${i + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 3.dp))
    }
}

/** Katman çubuğu: dokun = seç; seçiliyken uçlar = başı/sonu, gövde = kaydır (yatay) ve satır değiştir (dikey). */
@Composable
private fun KatmanCubugu(vm: EditorDurum, o: KatmanOge, i: Int, n: Int, x: Float, w: Float, secili: Boolean, pxMs: Float, toplam: Long, izGen: Float) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val renk = katRenk(o.tur)
    val oG by rememberUpdatedState(o); val iG by rememberUpdatedState(i)
    var gec by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }   // (sol px, sağ px, dikey px)
    val (sl, sr, dy) = gec ?: Triple(0f, 0f, 0f)
    val ucPx = with(yog) { 20.dp.toPx() }
    val satirPx = with(yog) { SATIR.toPx() }
    val sekil = RoundedCornerShape(6.dp)
    val kucuk by produceState<ImageBitmap?>(null, o.yol, o.tur) {
        value = if (o.tur == "gorsel" && o.yol.isNotEmpty()) withContext(Dispatchers.IO) {
            try {
                val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(o.yol, op)
                var s = 1; while (maxOf(op.outWidth, op.outHeight) / (s * 2) >= 96) s *= 2
                BitmapFactory.decodeFile(o.yol, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            } catch (_: Exception) { null }
        } else null
    }
    val dalga by produceState<FloatArray?>(null, o.yol, o.tur) { value = if (o.tur == "ses" && o.yol.isNotEmpty()) Dalga.oku(ctx, o.yol) else null }
    val gx = x + sl; val gw = (w - sl + sr).coerceAtLeast(8f)
    val (vx, vw, kay) = gorunur(gx, gw, izGen)
    val kayG by rememberUpdatedState(kay); val gwG by rememberUpdatedState(gw)
    var ucMenu by remember { mutableIntStateOf(0) }   // -1 baş menüsü · 1 son menüsü (3.44)
    if (vw <= 0f && gec == null) return
    val solGor = kay > -1f; val sagGor = vx + vw >= gx + gw - 1f
    Box(
        Modifier.offset { IntOffset(vx.roundToInt(), dy.roundToInt()) }.width(with(yog) { vw.coerceAtLeast(1f).toDp() }).fillMaxHeight()
            .clip(sekil).background(renk.copy(alpha = (if (secili) 0.85f else 0.55f) * (if (o.gizli) 0.5f else 1f)))
            .border(if (secili) 2.dp else 0.dp, if (secili) Color.White else Color.Transparent, sekil)
            .pointerInput(o.id, secili) { detectTapGestures(onTap = { t ->
                // 3.44: seçili değilse seç; seçiliyse uçlara dokun = Klip/Video sonu (başı) menüsü, ortaya dokun = yazıyı düzenle
                // 3.50: seçili değilken de uca dokunmak menüyü açar (tek dokunuş: seç + menü)
                val g = t.x - kayG
                if (!secili) {
                    vm.seciliKatman.value = o.id
                    if (!oG.kilitli) { if (g > gwG - ucPx * 1.6f) ucMenu = 1 else if (g < ucPx * 1.6f) ucMenu = -1 }
                    return@detectTapGestures
                }
                when {
                    oG.kilitli -> {}
                    g > gwG - ucPx * 1.6f -> ucMenu = 1
                    g < ucPx * 1.6f -> ucMenu = -1
                    oG.tur == "metin" -> vm.katmanDuzenle.value = o.id
                    else -> ucMenu = 1        // 3.51: ses dahil ortaya dokun = menü (ses düzeyi de menüde)
                }
            }) }
            .then(if (o.kilitli) Modifier else Modifier.pointerInput(o.id, w, secili) {
                var yan = 0
                // 3.45: seçili değilken basılı tut + sürükle = seç ve taşı (yatay kaydırma çizelgeyi sarar); seçiliyken doğrudan sürükle.
                // Yukarı/aşağı sürükle = satır değiştir (yukarı = öne, aşağı = arkaya)
                val surukle: suspend androidx.compose.ui.input.pointer.PointerInputScope.(
                    (Offset) -> Unit, () -> Unit, () -> Unit, (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit) -> Unit =
                    if (secili) { bas, bit, ipt, h -> detectDragGestures(onDragStart = bas, onDragEnd = bit, onDragCancel = ipt, onDrag = h) }
                    else { bas, bit, ipt, h -> detectDragGesturesAfterLongPress(onDragStart = { t -> vm.seciliKatman.value = o.id; bas(t) }, onDragEnd = bit, onDragCancel = ipt, onDrag = h) }
                surukle(
                    { t -> val g = t.x - kayG; yan = if (!secili) 0 else if (g < ucPx) -1 else if (g > gwG - ucPx) 1 else 0; gec = Triple(0f, 0f, 0f) },
                    {
                        val (a, b, v) = gec ?: Triple(0f, 0f, 0f); gec = null
                        val oo = oG
                        val enUzun = if (oo.tur == "ses" && oo.kaynakSureMs > 0) ((oo.kaynakSureMs - oo.kaynakBasMs) / oo.hiz.coerceAtLeast(0.25f)).toLong() else Long.MAX_VALUE
                        when (yan) {
                            -1 -> {
                                val d = (a / pxMs).toLong()
                                val yeniBas = (oo.basMs + d).coerceIn(0, oo.sonMs - 300)
                                val fark = yeniBas - oo.basMs
                                vm.katmanDegis(oo.id) { it.copy(basMs = yeniBas, kaynakBasMs = (it.kaynakBasMs + (fark * it.hiz).toLong()).coerceAtLeast(0)) }
                            }
                            1 -> {
                                val d = (b / pxMs).toLong()
                                vm.katmanDegis(oo.id) { it.copy(sonMs = (it.sonMs + d).coerceIn(it.basMs + 300, minOf(toplam, it.basMs + enUzun))) }
                            }
                            else -> {
                                val d = (a / pxMs).toLong(); val L = oo.sonMs - oo.basMs
                                val yb = (oo.basMs + d).coerceIn(0, (toplam - L).coerceAtLeast(0))
                                if (yb != oo.basMs) vm.katmanDegis(oo.id) { it.copy(basMs = yb, sonMs = yb + L) }
                                // dikey: satır (katman sırası) değiştir; aşağı = alttaki katman
                                val adim = (v / satirPx).roundToInt()
                                if (adim != 0) { vm.katmanTasi(iG, (iG - adim).coerceIn(0, n - 1)); vm.mesaj.value = if (adim < 0) "Öne alındı" else "Arkaya gönderildi" }
                            }
                        }
                    },
                    { gec = null },
                ) { ch, d ->
                    ch.consume()
                    val (a, b, v) = gec ?: Triple(0f, 0f, 0f)
                    gec = when (yan) {
                        -1 -> Triple(a + d.x, b, v)
                        1 -> Triple(a, b + d.x, v)
                        else -> Triple(a + d.x, b + d.x, v + d.y)
                    }
                }
            }),
    ) {
        when (o.tur) {
            "ses" -> dalga?.let { DalgaCiz(it, o.kaynakBasMs + (-kay / pxMs * o.hiz).toLong(), o.hiz, Color.White.copy(alpha = 0.85f)) }
            "gorsel" -> kucuk?.let { im ->
                Canvas(Modifier.fillMaxSize()) {
                    val h = size.height; val tw = (h * im.width / im.height.coerceAtLeast(1)).coerceAtLeast(8f)
                    drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset((kay + 4f).toInt(), 0), IntSize(tw.toInt(), h.toInt()))
                }
            }
        }
        val yazi = when (o.tur) { "metin" -> o.metin.lineSequence().firstOrNull() ?: ""; "cikartma" -> o.metin; else -> o.ad }
        Text(yazi, color = Color.White, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = if (o.tur == "gorsel" && solGor) 40.dp else 12.dp, end = 12.dp))
        if (secili && !o.kilitli) {
            if (solGor) Box(Modifier.align(Alignment.CenterStart).width(8.dp).fillMaxHeight().background(Color.White))
            if (sagGor) Box(Modifier.align(Alignment.CenterEnd).width(8.dp).fillMaxHeight().background(Color.White))
        }
        if (o.kilitli) Simge("kapat", Color.White, 12.dp, Modifier.align(Alignment.TopEnd).padding(2.dp))
        Box(Modifier.align(if (ucMenu < 0) Alignment.BottomStart else Alignment.BottomEnd)) {
            androidx.compose.material3.DropdownMenu(expanded = ucMenu != 0, onDismissRequest = { ucMenu = 0 }, containerColor = Renk.kart) {
                // 3.51: tek, eksiksiz menü — dokunulan uca göre sıralanır (sağ uç: sonlar önce, sol uç: başlar önce); ses katmanında ses düzeyi
                fun sec(t: String) { vm.katmanDegis(o.id) { vm.katmanUc(it, t) }; if (t == "basaTasi") vm.git(0); ucMenu = 0 }
                val basl = listOf("klipBas" to ("Klip başı" to "|‹"), "videoBas" to ("Video başı" to "|«"))
                val sonl = listOf("klipSon" to ("Klip sonu" to "›|"), "videoSon" to ("Video sonu" to "»|"))
                (if (ucMenu < 0) basl + sonl else sonl + basl).plus("basaTasi" to ("Videonun başına taşı" to "|◀")).forEach { (t, ad) ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text(ad.first, color = Renk.yazi) },
                        trailingIcon = { Text(ad.second, color = Renk.turuncu, fontWeight = FontWeight.Bold) }, onClick = { sec(t) })
                }
                if (o.tur == "ses") androidx.compose.material3.DropdownMenuItem(text = { Text(if (o.ses <= 0.001f) "Ses düzeyi · kapalı" else "Ses düzeyi · %${(o.ses * 100).roundToInt()}", color = Renk.yazi) },
                    trailingIcon = { Simge(if (o.ses <= 0.001f) "sessiz" else "ses", Renk.turkuaz, 18.dp) }, onClick = { ucMenu = 0; vm.sesPenceresi.value = o.id })
            }
        }
    }
}

/** Klip yazısı / takip yazısı çubuğu: dokun = o sahneye git ve düzenleme penceresini aç. */
@Composable
private fun EkCubuk(yazi: String, x: Float, w: Float, izGen: Float, renk: Color, ac: () -> Unit) {
    val yog = LocalDensity.current
    val (vx, vw, _) = gorunur(x, w.coerceAtLeast(6f), izGen)
    if (vw <= 0f) return
    Box(Modifier.offset { IntOffset(vx.roundToInt(), 0) }.width(with(yog) { vw.toDp() }).fillMaxHeight()
        .clip(RoundedCornerShape(6.dp)).background(renk.copy(alpha = 0.5f)).clickable(onClick = ac)) {
        Text(yazi, color = Color.White, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp))
    }
}

/** Müzik / seslendirme çubuğu: ses dalgası; dokun = ilgili panel. */
@Composable
private fun SesCubugu(uri: String, ad: String, x: Float, w: Float, izGen: Float, renk: Color, kaynakBas: Long, sure: Long, hiz: Float, pxMs: Float, ac: () -> Unit) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val dalga by produceState<FloatArray?>(null, uri) { value = Dalga.oku(ctx, uri) }
    val (vx, vw, kay) = gorunur(x, w.coerceAtLeast(6f), izGen)
    if (vw <= 0f) return
    Box(Modifier.offset { IntOffset(vx.roundToInt(), 0) }.width(with(yog) { vw.toDp() }).fillMaxHeight()
        .clip(RoundedCornerShape(6.dp)).background(renk.copy(alpha = 0.45f)).clickable(onClick = ac)) {
        dalga?.let { DalgaCiz(it, kaynakBas + (-kay / pxMs * hiz).toLong(), hiz, Color.White.copy(alpha = 0.8f), dongu = sure > 0) }
        if (ad.isNotEmpty()) Text(ad, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
    }
}

/** Dalga çizimi: çubuğun her pikseli için (kaynak zamanı) tepe değeri. dongu: müzik tekrar ederse dalga başa sarar. */
@Composable
private fun DalgaCiz(d: FloatArray, kaynakBas: Long, hiz: Float, renk: Color, dongu: Boolean = false) {
    val pxMs = with(LocalDensity.current) { Duzen.cizelgePps.dp.toPx() } / 1000f
    Canvas(Modifier.fillMaxSize()) {
        val h = size.height; val orta = h / 2f
        val adim = 3f
        // KÖK DÜZELTME (3.48): örnekleme noktaları kaynak zamanına sabitlenir. Eskiden her karede çubuğun başından 3 px'te bir
        // örnekleniyordu; kaydırınca örnek noktaları kayıp dalga her karede değişiyor, müzik çubuğu yanıp sönüyordu.
        val faz = ((kaynakBas / hiz.coerceAtLeast(0.01f)) * pxMs) % adim
        var xx = -faz
        while (xx < size.width) {
            val ms = kaynakBas + ((xx + faz) / pxMs * hiz).toLong() - (faz / pxMs * hiz).toLong()
            var j = (ms / Dalga.ADIM_MS).toInt()
            if (dongu && d.isNotEmpty()) j %= d.size
            val v = d.getOrNull(j) ?: 0f
            val y = (v * h * 0.42f).coerceAtLeast(0.8f)
            drawRoundRect(renk, Offset(xx, orta - y), Size(2f, y * 2), CornerRadius(1f))
            xx += adim
        }
    }
}


/** Katman bölümü: başlığa dokun = çizelgeyi aç/kapat · tutamaç = çizelgeyi büyüt/küçült (çift dokunuş = varsayılan) ·
 *  altında katman ekleme ve seçili katmanın ayarları. Katmanla ilgili her işlem yalnız burada. */
@Composable
fun KatmanBolumu(vm: EditorDurum, p: Proje, acMuzik: () -> Unit, acSes: () -> Unit) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    var acik by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var ekPanel by remember { mutableStateOf<Pair<String, Int>?>(null) }   // ("yazi"|"takip", klip sırası)
    val takipSecimde by vm.takipSecimde.collectAsState()
    LaunchedEffect(takipSecimde) { if (takipSecimde) ekPanel = null }      // nokta seçimi önizlemede yapılır
    ekPanel?.let { (tur, i) ->
        val k = p.klipler.getOrNull(i)
        if (k == null) ekPanel = null
        else androidx.compose.ui.window.Dialog(onDismissRequest = { ekPanel = null }) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).clip(RoundedCornerShape(14.dp)).background(Renk.zemin).verticalScroll(rememberScrollState()).padding(8.dp)) {
                if (tur == "takip") PanelTakip(vm, k) else PanelYazi(vm, k)
                Dugme("Tamam", Modifier.fillMaxWidth().padding(top = 6.dp)) { ekPanel = null }
            }
        }
    }
    YaziDuzenleDiyalog(vm, p)
    SesDiyalog(vm, p, acMuzik)
    val secKat by vm.seciliKatman.collectAsState(); val secNes by vm.seciliNesne.collectAsState()
    LaunchedEffect(secKat) { if (secKat != null) vm.seciliNesne.value = null }
    LaunchedEffect(secNes) { if (secNes != null) vm.seciliKatman.value = null }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp).background(Renk.kart).clickable { acik = !acik }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Simge("katman", Renk.turuncu, 18.dp)
            Spacer(Modifier.width(8.dp))
            Text("Katmanlar · ${p.katmanlar.size}", color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // 3.47: geri al / yinele (başlıkta)
            val geriVar by vm.geriVar.collectAsState(); val ileriVar by vm.ileriVar.collectAsState()
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).clickable(enabled = geriVar) { vm.geriAl() }, contentAlignment = Alignment.Center) {
                Simge("gerial", if (geriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), 18.dp) }
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).clickable(enabled = ileriVar) { vm.yinele() }, contentAlignment = Alignment.Center) {
                Simge("yinele", if (ileriVar) Renk.yazi else Renk.soluk.copy(alpha = 0.35f), 18.dp) }
            Spacer(Modifier.width(6.dp))
            Text(if (acik) "▾" else "▸", color = Renk.soluk, fontSize = 16.sp)
        }
        if (acik) {
            if (p.klipler.isEmpty()) Text("Önce Düzenle › Ekle ile en az bir klip ekle.", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            else {
                CokluCizelge(vm, p, acMuzik, acSes, Modifier.fillMaxWidth().height(Duzen.katmanYuk.dp)) { tur, i -> ekPanel = tur to i }
                Box(Modifier.fillMaxWidth().height(22.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(onDragEnd = { Duzen.katmanYukKaydet(ctx) }) { ch, d ->
                            ch.consume()
                            Duzen.katmanYuk = (Duzen.katmanYuk + with(yog) { d.toDp().value }).coerceIn(120f, 560f)
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { Duzen.katmanYuk = 240f; Duzen.katmanYukKaydet(ctx) }) },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.width(56.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Renk.turuncu.copy(alpha = 0.8f)))
                }
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
            PanelKatmanlar(vm, p, takipAc = { ekPanel = "takip" to vm.secili.value })
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Hızlı yazı düzenleme (3.44): eklenince, çizelgede seçili yazıya ya da ekrandaki yazıya dokununca açılır. */
@Composable
private fun YaziDuzenleDiyalog(vm: EditorDurum, p: Proje) {
    val id by vm.katmanDuzenle.collectAsState()
    val o = p.katmanlar.firstOrNull { it.id == id && it.tur == "metin" } ?: return
    var m by remember(o.id) { mutableStateOf(o.metin) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.katmanDuzenle.value = null },
        containerColor = Renk.kart,
        title = { Text("Yazıyı düzenle", color = Renk.yazi, fontSize = 16.sp) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(m, { m = it; vm.katmanDegis(o.id) { k -> k.copy(metin = it) } }, Modifier.fillMaxWidth(),
                    label = { Text("Yazı (2. satır = küçük alt satır)") }, maxLines = 3, enabled = !o.kilitli)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("baslik" to "Başlık", "konum" to "Konum", "altbant" to "Alt bant", "etiket" to "Etiket", "duz" to "Düz").forEachIndexed { j, (st, ad) ->
                        SecimKarti(ad, "", o.stil == st, Modifier.width(72.dp), etkin = !o.kilitli, sira = j, simge = "metin") { vm.katmanDegis(o.id) { it.copy(stil = st) } }
                    }
                }
                var ay by remember(o.id) { mutableStateOf(o.altyazi) }
                androidx.compose.material3.OutlinedTextField(ay, { ay = it; vm.katmanDegis(o.id) { k -> k.copy(altyazi = it) } }, Modifier.fillMaxWidth().padding(top = 6.dp),
                    label = { Text("Altyazı (yazının altında)") }, maxLines = 2, enabled = !o.kilitli)
                Text("${sureOndalik(o.basMs)} – ${sureOndalik(o.sonMs)} · ${sureOndalik(o.sonMs - o.basMs)}", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                UcDugmeleri(vm, o)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.katmanDuzenle.value = null }) { Text("Tamam", color = Renk.turuncu) } },
    )
}

/** Ses düzeyi penceresi (3.48): müzik ya da ses katmanı — düzey, kapat/aç, otomatik kısma; uç işlemleri çubuğun uçlarında. */
@Composable
private fun SesDiyalog(vm: EditorDurum, p: Proje, acMuzik: () -> Unit) {
    val id by vm.sesPenceresi.collectAsState()
    val m = p.muzik.takeIf { id == "muzik" }
    val o = p.katmanlar.firstOrNull { it.id == id && it.tur == "ses" }
    if (m == null && o == null) return
    val deger = m?.ses ?: o!!.ses
    val tavan = if (m != null) 1f else 1.5f
    var v by remember(id, deger) { mutableFloatStateOf(deger) }
    fun yaz(x: Float) { if (m != null) vm.muzikDegis { it.copy(ses = x) } else vm.katmanDegis(o!!.id) { it.copy(ses = x) } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.sesPenceresi.value = null },
        containerColor = Renk.kart,
        title = { Text(if (m != null) "Müzik · ${m.ad.ifBlank { "parça" }}" else o!!.ad.ifBlank { "Ses" }, color = Renk.yazi, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(if (v <= 0.001f) "Kapalı" else "Ses %${(v * 100).roundToInt()}", color = Renk.turuncu, fontWeight = FontWeight.SemiBold)
                InceSlider(value = v, onValueChange = { v = it }, valueRange = 0f..tavan, onValueChangeFinished = { yaz(v) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dugme(if (v <= 0.001f) "Sesi aç" else "Sesi kapat", Modifier.weight(1f)) { val x = if (v <= 0.001f) (if (m != null) 0.5f else 1f) else 0f; v = x; yaz(x) }
                    Dugme("%50", Modifier.weight(1f)) { v = 0.5f; yaz(0.5f) }
                }
                if (m != null) {
                    AnahtarSatiri("Konuşma / video sesi varken otomatik kıs", m.otoKis) { b -> vm.muzikDegis { it.copy(otoKis = b) } }
                    androidx.compose.material3.TextButton(onClick = { vm.sesPenceresi.value = null; acMuzik() }) { Text("Müzik ayarları (değiştir, döngü…)", color = Renk.turkuaz) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.sesPenceresi.value = null }) { Text("Tamam", color = Renk.turuncu) } },
    )
}

/** 3.50: başı ve sonu yarım saniyelik adımlarla — solda baş (◀ erken · ▶ geç), sağda son (◀ kısalt · ▶ uzat). */
@Composable
fun UcDugmeleri(vm: EditorDurum, o: KatmanOge) {
    val e = !o.kilitli
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Baş", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.width(26.dp))
        SimgeDugme("onceki", "−0,5", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = e) { vm.katmanUcKaydir(o.id, -500, 0) }
        SimgeDugme("sonraki", "+0,5", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = e) { vm.katmanUcKaydir(o.id, 500, 0) }
        Spacer(Modifier.width(8.dp))
        Text("Son", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.width(26.dp))
        SimgeDugme("onceki", "−0,5", Modifier.weight(1f), renk = tonRenk(1, ""), etkin = e) { vm.katmanUcKaydir(o.id, 0, -500) }
        SimgeDugme("sonraki", "+0,5", Modifier.weight(1f), renk = tonRenk(1, ""), etkin = e) { vm.katmanUcKaydir(o.id, 0, 500) }
    }
}

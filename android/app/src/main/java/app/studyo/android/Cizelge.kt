package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
@Composable
fun CokluCizelge(
    vm: EditorDurum, p: Proje, sec: Int, ekle: () -> Unit,
    acKatman: () -> Unit, acKlip: () -> Unit, acMuzik: () -> Unit, acSes: () -> Unit, modifier: Modifier,
) {
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
        // ---- seçili öğe için temel gereçler (tek sıra) ----
        val ko = p.katmanlar.firstOrNull { it.id == secK }
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (ko != null) {
                val e = !ko.kilitli
                SimgeDugme("makas", "Böl", Modifier.weight(1f), renk = tonRenk(0, ""), etkin = e) { vm.katmanBol(ko.id) }
                SimgeDugme("cogalt", "Çoğalt", Modifier.weight(1f), renk = tonRenk(1, "")) { vm.katmanCogalt(ko.id) }
                SimgeDugme("gorunum", if (ko.gizli) "Göster" else "Gizle", Modifier.weight(1f), renk = tonRenk(2, ""), etkin = e) { vm.katmanDegis(ko.id) { it.copy(gizli = !it.gizli) } }
                SimgeDugme(if (ko.kilitli) "uygula" else "kapat", if (ko.kilitli) "Kilidi aç" else "Kilitle", Modifier.weight(1f), renk = tonRenk(3, "")) { vm.katmanDegis(ko.id) { it.copy(kilitli = !it.kilitli) } }
                SimgeDugme("sil", "Sil", Modifier.weight(1f), renk = Renk.kirmizi, etkin = e) { vm.katmanSil(ko.id) }
                SimgeDugme("menu", "Ayarlar", Modifier.weight(1f), renk = tonRenk(4, "")) { acKatman() }
            } else if (p.klipler.isNotEmpty()) {
                val k = p.klipler.getOrNull(sec)
                SimgeDugme("makas", "Böl", Modifier.weight(1f), renk = tonRenk(0, "")) { vm.klipBol() }
                SimgeDugme("cogalt", "Çoğalt", Modifier.weight(1f), renk = tonRenk(1, "")) { vm.klipCogalt() }
                SimgeDugme("ses", if (k?.sessiz == true) "Sesi aç" else "Sessiz", Modifier.weight(1f), renk = tonRenk(2, ""), etkin = k != null && !k.foto) {
                    vm.klipGuncelle { it.copy(sessiz = !it.sessiz) }
                }
                SimgeDugme("katman", "Katman ekle", Modifier.weight(1f), renk = tonRenk(3, "")) { vm.seciliKatman.value = null; acKatman() }
                SimgeDugme("menu", "Klip ayarı", Modifier.weight(1f), renk = tonRenk(4, "")) { acKlip() }
            }
        }

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
                    Box(Modifier.width(ETIKET).fillMaxHeight().clickable { vm.seciliKatman.value = null; acKatman() }, contentAlignment = Alignment.Center) {
                        Text("＋ Katman", color = Renk.turuncu, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
                    // ---- video satırı ----
                    IzSatiri("klip", "Video", SATIR_VIDEO, Renk.turkuaz, onClick = { vm.seciliKatman.value = null; acKlip() }) {
                        var bas = 0L
                        for (i in p.klipler.indices) {
                            val zk = p.zKlip(i); val k = p.klipler[i]
                            val b0 = bas; bas += zk.sureMs
                            val x0 = xMs(b0); val x1 = xMs(b0 + zk.sureMs)
                            if (x1 < -50f || x0 > izGen + 50f) continue
                            key(k.id) { KlipCubugu(vm, p, i, k, x0, x1 - x0, i == sec && secK == null, pxMs) }
                        }
                        // ekle
                        val xs = xMs(toplam)
                        if (xs < izGen + 10f) Box(Modifier.offset { IntOffset((xs + 6f).roundToInt(), 0) }.size(40.dp, SATIR_VIDEO - 8.dp).padding(top = 4.dp)
                            .clip(RoundedCornerShape(8.dp)).border(1.dp, Renk.turuncu, RoundedCornerShape(8.dp)).clickable { ekle() },
                            contentAlignment = Alignment.Center) { Simge("ekle", Renk.turuncu, 20.dp) }
                    }
                    // ---- katman satırları (üstteki en üstte) ----
                    val n = p.katmanlar.size
                    for (d in 0 until n) {
                        val i = n - 1 - d
                        val o = p.katmanlar[i]
                        key(o.id) {
                            IzSatiri(turSimgeC(o.tur), o.ad.ifBlank { o.metin }.ifBlank { "Katman" }, SATIR, katRenk(o.tur).copy(alpha = if (o.gizli) 0.4f else 1f),
                                onClick = { vm.seciliKatman.value = o.id }) {
                                KatmanCubugu(vm, o, i, n, xMs(o.basMs), (o.sonMs - o.basMs) * pxMs, o.id == secK, pxMs, toplam, acKatman)
                            }
                        }
                    }
                    // ---- müzik ----
                    p.muzik?.let { m ->
                        val son = if (m.dongu || m.sureMs <= 0) p.toplamMs else minOf(m.sureMs, p.toplamMs)
                        IzSatiri("nota", "Müzik", SATIR, Color(0xFF4CC38A), onClick = acMuzik) {
                            SesCubugu(m.uri, m.ad, xMs(0), son * pxMs, Color(0xFF4CC38A), 0L, m.sureMs, 1f, pxMs, acMuzik)
                        }
                    }
                    // ---- seslendirme ----
                    if (p.sesler.isNotEmpty()) IzSatiri("mikrofon", "Ses kaydı", SATIR, Color(0xFFEF5350), onClick = acSes) {
                        for (s in p.sesler) key(s.id) {
                            SesCubugu(android.net.Uri.fromFile(java.io.File(s.yol)).toString(), "", xMs(s.basMs), s.sureMs * pxMs, Color(0xFFEF5350), 0L, s.sureMs, 1f, pxMs, acSes)
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
private fun IzSatiri(simge: String, ad: String, yuk: androidx.compose.ui.unit.Dp, renk: Color, onClick: () -> Unit, icerik: @Composable BoxScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(yuk).padding(vertical = 2.dp)) {
        Row(Modifier.width(ETIKET).fillMaxHeight().background(Color(0xFF14191E)).clickable(onClick = onClick).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Simge(simge, renk, 14.dp)
            Spacer(Modifier.width(3.dp))
            Text(ad, color = Color(0xFFC9D4DE), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), content = icerik)
    }
}

/** Video klibi: kareler yan yana; seçiliyken uçlardan kırp (video: baş/son, fotoğraf: süre). */
@Composable
private fun KlipCubugu(vm: EditorDurum, p: Proje, i: Int, k: Klip, x: Float, w: Float, secili: Boolean, pxMs: Float) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val resim by produceState<ImageBitmap?>(null, k.uri, k.basMs) { value = Medya.kucukResim(ctx, k, 160)?.asImageBitmap() }
    var gec by remember { mutableStateOf<Pair<Float, Float>?>(null) }   // sürükleme: (sol kayma px, sağ kayma px)
    val (sl, sr) = gec ?: (0f to 0f)
    val kG by rememberUpdatedState(k)
    val ucPx = with(yog) { 20.dp.toPx() }
    val sekil = RoundedCornerShape(6.dp)
    Box(
        Modifier.offset { IntOffset((x + sl).roundToInt(), 0) }.width(with(yog) { (w - sl + sr).coerceAtLeast(6f).toDp() }).fillMaxHeight()
            .padding(horizontal = 1.dp).clip(sekil).background(Color(0xFF243444))
            .border(if (secili) 2.dp else 0.5.dp, if (secili) Color.White else Color(0x55FFFFFF), sekil)
            .pointerInput(k.id) { detectTapGestures { vm.seciliKatman.value = null; vm.sec(i) } }
            .then(if (!secili) Modifier else Modifier.pointerInput(k.id, w) {
                var yan = 0
                detectDragGestures(
                    onDragStart = { t -> yan = if (t.x < ucPx) -1 else if (t.x > size.width - ucPx) 1 else 0; gec = 0f to 0f },
                    onDragEnd = {
                        val (a, b) = gec ?: (0f to 0f); gec = null
                        val kk = kG
                        if (yan == -1 && abs(a) > 2f) {
                            val d = (a / pxMs).toLong()
                            if (kk.foto) vm.klipGuncelle(true) { it.copy(fotoSureMs = (it.fotoSureMs - d).coerceIn(500, 600_000)) }
                            else vm.kirp(kk.basMs + d, kk.sonMs)
                        } else if (yan == 1 && abs(b) > 2f) {
                            val d = (b / pxMs).toLong()
                            if (kk.foto) vm.klipGuncelle(true) { it.copy(fotoSureMs = (it.fotoSureMs + d).coerceIn(500, 600_000)) }
                            else vm.kirp(kk.basMs, kk.sonMs + d)
                        }
                    },
                    onDragCancel = { gec = null },
                ) { ch, d ->
                    if (yan == 0) return@detectDragGestures
                    ch.consume()
                    val (a, b) = gec ?: (0f to 0f)
                    gec = if (yan == -1) (a + d.x) to b else a to (b + d.x)
                }
            }),
    ) {
        resim?.let { im ->
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height; val tw = (h * im.width / im.height.coerceAtLeast(1)).coerceAtLeast(8f)
                var xx = 0f
                while (xx < size.width) {
                    drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset(xx.toInt(), 0), IntSize(tw.toInt() + 1, h.toInt()))
                    xx += tw
                }
            }
        }
        Text("${i + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(3.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 3.dp))
        if (secili) {
            Box(Modifier.align(Alignment.CenterStart).width(10.dp).fillMaxHeight().background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.width(2.dp).height(14.dp).background(Color(0xFF243444)))
            }
            Box(Modifier.align(Alignment.CenterEnd).width(10.dp).fillMaxHeight().background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.width(2.dp).height(14.dp).background(Color(0xFF243444)))
            }
            Text(sureOndalik(((w - sl + sr) / pxMs).toLong()), color = Color.White, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 2.dp).background(Color(0xAA000000)).padding(horizontal = 3.dp))
        }
    }
}

/** Katman çubuğu: dokun = seç; seçiliyken uçlar = başı/sonu, gövde = kaydır (yatay) ve satır değiştir (dikey). */
@Composable
private fun KatmanCubugu(vm: EditorDurum, o: KatmanOge, i: Int, n: Int, x: Float, w: Float, secili: Boolean, pxMs: Float, toplam: Long, ac: () -> Unit) {
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
    Box(
        Modifier.offset { IntOffset((x + sl).roundToInt(), dy.roundToInt()) }.width(with(yog) { (w - sl + sr).coerceAtLeast(8f).toDp() }).fillMaxHeight()
            .graphicsLayer { alpha = if (o.gizli) 0.45f else 1f }
            .clip(sekil).background(renk.copy(alpha = if (secili) 0.85f else 0.55f))
            .border(if (secili) 2.dp else 0.dp, if (secili) Color.White else Color.Transparent, sekil)
            .pointerInput(o.id) { detectTapGestures(onTap = { vm.seciliKatman.value = o.id }, onDoubleTap = { vm.seciliKatman.value = o.id; ac() }) }
            .then(if (!secili || o.kilitli) Modifier else Modifier.pointerInput(o.id, w) {
                var yan = 0
                detectDragGestures(
                    onDragStart = { t -> yan = if (t.x < ucPx) -1 else if (t.x > size.width - ucPx) 1 else 0; gec = Triple(0f, 0f, 0f) },
                    onDragEnd = {
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
                                if (adim != 0) vm.katmanTasi(iG, (iG - adim).coerceIn(0, n - 1))
                            }
                        }
                    },
                    onDragCancel = { gec = null },
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
            "ses" -> dalga?.let { DalgaCiz(it, o.kaynakBasMs, o.hiz, Color.White.copy(alpha = 0.85f)) }
            "gorsel" -> kucuk?.let { im ->
                Canvas(Modifier.fillMaxSize()) {
                    val h = size.height; val tw = (h * im.width / im.height.coerceAtLeast(1)).coerceAtLeast(8f)
                    drawImage(im, IntOffset.Zero, IntSize(im.width, im.height), IntOffset(4, 0), IntSize(tw.toInt(), h.toInt()))
                }
            }
        }
        val yazi = when (o.tur) { "metin" -> o.metin.lineSequence().firstOrNull() ?: ""; "cikartma" -> o.metin; else -> o.ad }
        Text(yazi, color = Color.White, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = if (o.tur == "gorsel") 40.dp else 12.dp, end = 12.dp))
        if (secili && !o.kilitli) {
            Box(Modifier.align(Alignment.CenterStart).width(8.dp).fillMaxHeight().background(Color.White))
            Box(Modifier.align(Alignment.CenterEnd).width(8.dp).fillMaxHeight().background(Color.White))
        }
        if (o.kilitli) Simge("kapat", Color.White, 12.dp, Modifier.align(Alignment.TopEnd).padding(2.dp))
    }
}

/** Müzik / seslendirme çubuğu: ses dalgası; dokun = ilgili panel. */
@Composable
private fun SesCubugu(uri: String, ad: String, x: Float, w: Float, renk: Color, kaynakBas: Long, sure: Long, hiz: Float, pxMs: Float, ac: () -> Unit) {
    val ctx = LocalContext.current
    val yog = LocalDensity.current
    val dalga by produceState<FloatArray?>(null, uri) { value = Dalga.oku(ctx, uri) }
    Box(Modifier.offset { IntOffset(x.roundToInt(), 0) }.width(with(yog) { w.coerceAtLeast(6f).toDp() }).fillMaxHeight()
        .clip(RoundedCornerShape(6.dp)).background(renk.copy(alpha = 0.45f)).clickable(onClick = ac)) {
        dalga?.let { DalgaCiz(it, kaynakBas, hiz, Color.White.copy(alpha = 0.8f), dongu = sure > 0) }
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
        var xx = 0f
        while (xx < size.width) {
            val ms = kaynakBas + (xx / pxMs * hiz).toLong()
            var j = (ms / Dalga.ADIM_MS).toInt()
            if (dongu && d.isNotEmpty()) j %= d.size
            val v = d.getOrNull(j) ?: 0f
            val y = (v * h * 0.42f).coerceAtLeast(0.8f)
            drawRoundRect(renk, Offset(xx, orta - y), Size(2f, y * 2), CornerRadius(1f))
            xx += adim
        }
    }
}

package app.studyo.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 3.74: Düzenle › Klip › Yüzler — yüzlere / seçilen noktalara sırayla yaklaşma (yüz turu). Hesap: YuzTur.kt.

private fun yuvarla1(v: Float) = String.format(java.util.Locale("tr"), "%.1f", v)

/** Düzenle › Klip › Yüzler sayfası. */
@Composable
fun YuzTuruSayfasi(vm: EditorDurum, p: Proje, sec: Int, k: Klip) {
    Kart("Yüzler · klip ${sec + 1}") {
        if (!k.foto) {
            Text("Yüzlere yaklaşma fotoğraf kliplerinde çalışır. Üstteki şeritten bir fotoğraf seç.", color = Renk.soluk, fontSize = 12.5.sp); return@Kart
        }
        val t = k.yuzTur ?: YuzTuru()
        val secN by vm.turSecili.collectAsState()
        val ariyor by vm.yuzAraniyor.collectAsState()
        val n = t.noktalar.size
        val s = secN.takeIf { it in 0 until n }
        // fotoğraf ilk açıldığında yüzler bir kez aranır (halkalar önizlemede görünsün; durak eklemez)
        LaunchedEffect(k.id) { if (!t.arandi && !ariyor) vm.turYuzleriBul(ekle = false) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            YuvarlakSecim(if (ariyor) "Aranıyor…" else "Yüzleri bul", "yuz", Color(TON[2]), false) {
                if (n == 0) vm.turYuzleriBul(ekle = true)
                else { vm.turTemizle(); vm.turYuzleriBul(ekle = true) }
            }
            YuvarlakSecim("Önizle", "oynat", Color(TON[1]), false) { if (n > 0) vm.turOnizle() else vm.mesaj.value = "Önce bir durak ekle" }
            YuvarlakSecim("Temizle", "sil", Color(TON[4 % TON.size]), false) { if (n > 0) vm.turTemizle() }
        }
        Text(if (n == 0) "Önizlemede yüze ya da yaklaşılacak yere dokun = durak ekle." else
            "Tur: $n durak · ${yuvarla1(YuzTur.toplamSn(t))} sn (klip ${yuvarla1(k.fotoSureMs / 1000f)} sn)",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))

        if (n > 0) {
            TurBaslik("DURAKLAR · SIRA")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                t.noktalar.forEachIndexed { j, d ->
                    val on = j == s
                    Column(Modifier.width(52.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.turSecili.value = if (on) -1 else j }.padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text("${j + 1}", color = if (on) Renk.zemin else Renk.yazi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(if (d.yb > 0f) "yüz" else "nokta", color = Renk.soluk, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        Text("${yuvarla1(d.z)}×", color = if (on) Renk.yazi else Renk.soluk, fontSize = 10.sp)
                    }
                    if (j < n - 1) Simge("ileri", Renk.soluk.copy(alpha = 0.5f), 12.dp)
                }
            }
            if (s != null) {
                val d = t.noktalar[s]
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SimgeDugme("sol", "Öne al", Modifier.weight(1f), renk = Color(TON[0]), etkin = s > 0) { vm.turSira(s, -1) }
                    SimgeDugme("sag", "Sona al", Modifier.weight(1f), renk = Color(TON[1]), etkin = s < n - 1) { vm.turSira(s, 1) }
                    SimgeDugme("sil", "Durağı sil", Modifier.weight(1f), renk = Renk.kirmizi) { vm.turNoktaSil(s) }
                }
                TurBaslik("YAKINLIK · ${s + 1}. DURAK")
                var z by remember(k.id, s, d.z) { mutableFloatStateOf(d.z) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uzak", color = Renk.soluk, fontSize = 11.sp)
                    InceSlider(z, { z = it; vm.turYakinlik(s, it) }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 1f..4f)
                    Text("${yuvarla1(z)}×", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Önizlemedeki kesik çerçeve bu durakta görünecek alandır.", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text("Tümüne", color = Renk.yazi, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .clickable { vm.turYakinlik(s, z, hepsi = true); vm.mesaj.value = "Bütün duraklar ${yuvarla1(z)}×" }.padding(8.dp))
                }
            } else Text("Numaraya dokun = seç (yakınlık, sıra, sil). Önizlemede numarayı sürükle = taşı.", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

            TurBaslik("AKIŞ")
            var bek by remember(k.id, t.bekleSn) { mutableFloatStateOf(t.bekleSn) }
            Etiketli("Her durakta kalma", "${yuvarla1(bek)} sn")
            InceSlider(bek, { bek = it }, valueRange = 1.5f..6f, onValueChangeFinished = { vm.turAyar { it.copy(bekleSn = (bek * 10).roundToInt() / 10f) } })
            Text("Geçiş", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                YuzTur.GECIS.keys.forEachIndexed { j, g ->
                    YuvarlakSecim(YuzTur.GECIS_AD[g] ?: g, listOf("g_hafif", "g_akis", "hareket")[j], Color(TON[j % TON.size]), t.gecis == g) { vm.turAyar { it.copy(gecis = g) } }
                }
            }
            Text("Durakta yavaş yaklaşma", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                YuzTur.YAKLAS.forEach { (v, ad) ->
                    val on = kotlin.math.abs(t.yaklas - v) < 0.005f
                    Text(ad, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(16.dp))
                        .clickable { vm.turAyar { it.copy(yaklas = v) } }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            AnahtarSatiri("Önce fotoğrafın tamamı", t.bastaTumu) { v -> vm.turAyar { it.copy(bastaTumu = v) } }
            AnahtarSatiri("Sonda fotoğrafın tamamı", t.sondaTumu) { v -> vm.turAyar { it.copy(sondaTumu = v) } }
            AnahtarSatiri("Sinematik kavis (geçişte hafif geri çekil)", t.kavis) { v -> vm.turAyar { it.copy(kavis = v) } }
        }
        AcilirNot("Duraklar numara sırasıyla gezilir. Kamera her durakta biraz kalır ve çok yavaş yaklaşır, sonra yumuşakça (birden değil) bir sonrakine geçer. " +
            "Fotoğrafın geri kalanı olduğu gibi kalır: karartma ya da bulanıklık yok. Yüz yoksa da istediğin yerlere dokunarak durak ekleyebilirsin. " +
            "Tur sığmazsa fotoğrafın süresi kendiliğinden uzar (Kırp sayfasından değişir). Hareket 'Sabit' yapılırsa tur durur, duraklar silinmez.")
    }
}

@Composable
private fun TurBaslik(metin: String) {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Renk.cizgi))
    Text(metin, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

/** Önizleme üstü (Yüzler sayfası açık, duraklatılmışken): fotoğrafın tamamı, bulunan yüzler (ince halka), numaralı duraklar, sıra çizgisi
 *  ve seçili durağın görünecek alanı (kesik çerçeve). Dokun = durak ekle / numaraya dokun = seç · numarayı sürükle = taşı. */
@Composable
fun YuzTuruKatmani(vm: EditorDurum, p: Proje, k: Klip, modifier: Modifier = Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(null, k.uri) { value = withContext(Dispatchers.IO) { YuzTur.onizlemeResmi(k.uri) } }
    val secN by vm.turSecili.collectAsState()
    val t = k.yuzTur ?: YuzTuru()
    val kSon by rememberUpdatedState(k)
    var boyut by remember { mutableStateOf(IntSize.Zero) }
    var surukle by remember { mutableStateOf<Pair<Int, Offset>?>(null) }   // (durak, fotoğraftaki yeni yer 0…1)
    val yog = androidx.compose.ui.platform.LocalDensity.current
    val b = bmp
    // fotoğrafın önizlemedeki dikdörtgeni (sığdır)
    fun cerceve(): Pair<Offset, Size> {
        val bw = (b?.width ?: k.gen).toFloat().coerceAtLeast(1f); val bh = (b?.height ?: k.yuk).toFloat().coerceAtLeast(1f)
        val sc = min(boyut.width / bw, boyut.height / bh)
        val w = bw * sc; val h = bh * sc
        return Offset((boyut.width - w) / 2f, (boyut.height - h) / 2f) to Size(w, h)
    }
    fun ekrana(x: Float, y: Float): Offset { val (o, s) = cerceve(); return Offset(o.x + x * s.width, o.y + y * s.height) }
    fun fotoya(e: Offset): Offset? { val (o, s) = cerceve(); if (s.width < 2f) return null
        val x = (e.x - o.x) / s.width; val y = (e.y - o.y) / s.height
        return if (x in -0.02f..1.02f && y in -0.02f..1.02f) Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)) else null }
    fun yakin(e: Offset, pay: Float): Int? = kSon.yuzTur?.noktalar?.withIndex()?.map { (j, n) -> j to (ekrana(n.x, n.y) - e).getDistance() }
        ?.filter { it.second < pay }?.minByOrNull { it.second }?.first

    Box(modifier.background(Color.Black).onSizeChanged { boyut = it }
        .pointerInput(k.id) { detectTapGestures { e ->
            val j = yakin(e, 24.dp.toPx())
            if (j != null) { vm.turSecili.value = if (vm.turSecili.value == j) -1 else j; return@detectTapGestures }
            fotoya(e)?.let { vm.turNoktaEkle(it.x, it.y) }
        } }
        .pointerInput(k.id) {
            var j = -1
            detectDragGestures(
                onDragStart = { e -> j = yakin(e, 30.dp.toPx()) ?: -1; if (j >= 0) { vm.turSecili.value = j; fotoya(e)?.let { surukle = j to it } } },
                onDragEnd = { surukle?.let { (i, o) -> vm.turNoktaTasi(i, o.x, o.y) }; surukle = null; j = -1 },
                onDragCancel = { surukle = null; j = -1 }) { ch, _ ->
                if (j < 0) return@detectDragGestures
                ch.consume(); fotoya(ch.position)?.let { surukle = j to it }
            }
        }) {
        if (b != null) {
            val (o, s) = cerceve()
            androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                .size(with(yog) { s.width.toDp() }, with(yog) { s.height.toDp() }))
        }
        val (W, H) = remember(p.bicim) { Disa.boyut(p.bicim, 1080) }
        Canvas(Modifier.fillMaxSize()) {
            val (o, s) = cerceve(); if (s.width < 2f) return@Canvas
            val noktalar = t.noktalar.mapIndexed { j, n -> surukle?.takeIf { it.first == j }?.let { n.copy(x = it.second.x, y = it.second.y) } ?: n }
            // bulunan ama durak olmayan yüzler: ince halka
            t.bulunan.filter { f -> noktalar.none { hypot(it.x - f.x, it.y - f.y) < 0.02f } }.forEach { f ->
                drawCircle(Color.White.copy(alpha = 0.7f), radius = f.yb * s.height * 0.55f, center = ekrana(f.x, f.y), style = Stroke(1.2.dp.toPx()))
            }
            // seçili durağın görünecek alanı
            noktalar.getOrNull(secN)?.let { n ->
                val g = (if (k.gen > 0) k.gen else W).toFloat(); val h = (if (k.yuk > 0) k.yuk else H).toFloat()
                val scC = max(W / g, H / h)
                val vw = (W / (g * scC * n.z)).coerceAtMost(1f); val vh = (H / (h * scC * n.z)).coerceAtMost(1f)
                val ay = if (n.yb > 0f) 0.44f else 0.5f
                val x0 = (n.x - vw / 2f).coerceIn(0f, 1f - vw); val y0 = (n.y - vh * ay).coerceIn(0f, 1f - vh)
                drawRect(Color.White, topLeft = Offset(o.x + x0 * s.width, o.y + y0 * s.height), size = Size(vw * s.width, vh * s.height),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
            }
            // sıra çizgisi
            for (j in 0 until noktalar.size - 1) drawLine(Color.White.copy(alpha = 0.55f), ekrana(noktalar[j].x, noktalar[j].y), ekrana(noktalar[j + 1].x, noktalar[j + 1].y), 1.5.dp.toPx())
            // numaralı duraklar
            val r = 13.dp.toPx()
            val yazi = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 13.sp.toPx(); typeface = android.graphics.Typeface.DEFAULT_BOLD }
            noktalar.forEachIndexed { j, n ->
                val c = ekrana(n.x, n.y); val on = j == secN
                drawCircle(Color.Black.copy(alpha = 0.35f), r + 3.dp.toPx(), c)
                drawCircle(if (on) Color.Black else Color.White, r, c)
                if (on) drawCircle(Color.White, r, c, style = Stroke(2.dp.toPx()))
                yazi.color = if (on) android.graphics.Color.WHITE else android.graphics.Color.BLACK
                drawContext.canvas.nativeCanvas.drawText("${j + 1}", c.x, c.y - (yazi.descent() + yazi.ascent()) / 2f, yazi)
            }
        }
        Text(if (t.noktalar.isEmpty()) "Yüze ya da yere dokun = durak ekle" else "Dokun = durak ekle · numarayı sürükle = taşı",
            color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp))
    }
}


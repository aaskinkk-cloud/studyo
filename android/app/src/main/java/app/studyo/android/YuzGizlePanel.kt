package app.studyo.android

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// 3.83: Düzenle › Klip › Yüz gizle — yalnız seçilen yüzler yumuşak oval flu ile gizlenir (videoda takipli). Hesap: YuzGizleme.kt.

private fun sn1(ms: Long) = String.format(java.util.Locale("tr"), "%.1f", ms / 1000f)

@Composable
private fun GizleBaslik(metin: String) {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Renk.cizgi))
    Text(metin, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

/** Düzenle › Klip › Yüz gizle sayfası. */
@Composable
fun YuzGizleSayfasi(vm: EditorDurum, sec: Int, k: Klip) {
    Kart("Yüz gizle · klip ${sec + 1}") {
        val g = k.yuzGizle ?: YuzGizle()
        val ariyor by vm.gizleAraniyor.collectAsState()
        val ilerleme by vm.gizleIlerleme.collectAsState()
        val an by vm.gizleAnMs.collectAsState()
        val secY by vm.gizleSecili.collectAsState()
        LaunchedEffect(k.id) { vm.gizleBasla(); vm.gizleSecili.value = -1 }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            YuvarlakSecim(if (ariyor) "Durdur %${(ilerleme * 100).roundToInt()}" else if (g.arandi) "Yeniden bul" else "Yüzleri bul", if (ariyor) "durdur" else "ara", Color(TON[2]), ariyor) { vm.gizleAra() }
            YuvarlakSecim("Hepsini gizle", "gozkapali", Color(TON[0]), g.yuzler.isNotEmpty() && g.secili.size == g.yuzler.size) { if (g.yuzler.isNotEmpty()) vm.gizleHepsi(true) }
            YuvarlakSecim("Hiçbiri", "goz", Color(TON[1]), false) { if (g.secili.isNotEmpty()) vm.gizleHepsi(false) }
            YuvarlakSecim("Kaldır", "sil", Color(TON[4 % TON.size]), false) { if (k.yuzGizle != null) vm.gizleTemizle() }
        }
        Text(when {
            ariyor && !k.foto -> "Video taranıyor (saniyede ~10 kare) · yüzler kare kare izleniyor…"
            ariyor -> "Aranıyor…"
            !g.arandi && g.yuzler.isEmpty() -> "Önce \"Yüzleri bul\". Sonra gizlenecek yüzlere dokun."
            g.yuzler.isEmpty() -> "Yüz bulunamadı. Önizlemede kaçan yüze basılı tut = elle ekle."
            else -> "${g.yuzler.size} yüz · ${g.secili.size} gizli · önizlemede ovale dokun = gizle / göster"
        }, color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp))

        if (!k.foto) {
            GizleBaslik("AN · ${sn1(an - k.basMs)} sn")
            var t by remember(k.id) { mutableFloatStateOf((an - k.basMs).toFloat()) }
            LaunchedEffect(an) { t = (an - k.basMs).toFloat() }
            InceSlider(t.coerceIn(0f, (k.sonMs - k.basMs).toFloat().coerceAtLeast(1f)), { t = it; vm.gizleAnMs.value = k.basMs + it.toLong() },
                valueRange = 0f..(k.sonMs - k.basMs).toFloat().coerceAtLeast(1f))
        }

        if (g.yuzler.isNotEmpty()) {
            GizleBaslik("YÜZLER · dokun = gizle / göster")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.yuzler.forEach { y ->
                    val on = y.id in g.secili
                    val resim by produceState<Bitmap?>(null, y.resim) { value = withContext(Dispatchers.IO) { YuzGizleme.resim(y.resim) } }
                    Column(Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable {
                        vm.gizleSec(y.id); vm.gizleSecili.value = y.id
                        // videoda yüz o anda görünmüyorsa göründüğü ana git
                        if (!k.foto && YuzGizleme.anlikHepsi(g, an, false).none { it.first == y.id }) vm.gizleAnMs.value = y.izler[y.izler.size / 2].ms
                    }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Renk.kart2)
                            .border(if (y.id == secY) 2.5.dp else 1.dp, if (on || y.id == secY) Renk.yazi else Renk.cizgi, CircleShape), contentAlignment = Alignment.Center) {
                            val b = resim
                            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Text("${y.id}", color = Renk.yazi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (on) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Simge("gozkapali", Color.White, 20.dp) }
                        }
                        Text(if (on) "gizli" else if (y.elle) "elle" else if (k.foto) "açık" else "${sn1(y.izler.first().ms - k.basMs)} sn",
                            color = if (on) Renk.yazi else Renk.soluk, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            g.yuzler.firstOrNull { it.id == secY }?.let { y ->
                GizleBaslik("ALAN · ${if (y.id in g.secili) "gizli" else "açık"} yüz")
                var o by remember(k.id, y.id) { mutableFloatStateOf(y.olcek) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dar", color = Renk.soluk, fontSize = 11.sp)
                    InceSlider(o, { o = it }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 0.6f..2.2f, onValueChangeFinished = { vm.gizleOlcek(y.id, o) })
                    Text("Geniş", color = Renk.soluk, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SimgeDugme(if (y.id in g.secili) "goz" else "gozkapali", if (y.id in g.secili) "Göster" else "Gizle", Modifier.weight(1f), renk = Color(TON[0])) { vm.gizleSec(y.id) }
                    SimgeDugme("sil", "Listeden sil", Modifier.weight(1f), renk = Renk.kirmizi) { vm.gizleSil(y.id) }
                }
            }

            GizleBaslik("BULANIKLIK")
            var gu by remember(k.id) { mutableFloatStateOf(g.guc) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hafif", color = Renk.soluk, fontSize = 11.sp)
                InceSlider(gu, { gu = it }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 0.1f..1f, onValueChangeFinished = { vm.gizleGuc(gu) })
                Text("Güçlü", color = Renk.soluk, fontSize = 11.sp)
            }
        }
        AcilirNot("Yalnız seçtiğin yüzler gizlenir. Bulanıklık yüz hattına uyan oval biçimde, kenarı yumuşak; başın eğimine ve büyüklüğüne uyar. " +
            "Videoda yüzler kare kare izlenir; yüz kısa süre dönse ya da kapansa da (1,5 sn'ye kadar) gizli kalır. " +
            "Otomatik bulma her yüzü yakalamayabilir (çok yandan, çok küçük, bulanık yüzler): o zaman önizlemede yüze basılı tut — fotoğrafta oval eklenir, videoda o andan itibaren izlenir. " +
            "Duraklatınca önizleme gerçek sonucu gösterir; oynatırken akıcı önizlemede ve dışa aktarmada uygulanır.")
    }
}

/** Önizleme üstü (Yüz gizle sayfası açık, duraklatılmışken): incelenen anın karesi, gizli yüzler bulanık (ince düz çizgi),
 *  açık yüzler kesik çizgili oval. Ovale dokun = gizle / göster · boş yere basılı tut = kaçan yüzü elle ekle. */
@Composable
fun YuzGizleKatmani(vm: EditorDurum, k: Klip, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val an by vm.gizleAnMs.collectAsState()
    val secY by vm.gizleSecili.collectAsState()
    val ariyor by vm.gizleAraniyor.collectAsState()
    val g = k.yuzGizle ?: YuzGizle()
    val ms = if (k.foto) 0L else an.coerceIn(k.basMs, k.sonMs)
    val kare by produceState<Bitmap?>(null, k.uri, ms) { delay(90); value = withContext(Dispatchers.IO) { YuzGizleme.kare(ctx, k, ms) } }
    val sonuc by produceState<Bitmap?>(null, kare, k.yuzGizle) {
        val b = kare; value = b?.let { withContext(Dispatchers.Default) { if (k.gizleVar) YuzGizleme.uygula(it, YuzGizleme.anlik(g, ms, k.foto), g.guc) else it } }
    }
    var boyut by remember { mutableStateOf(IntSize.Zero) }
    val yog = androidx.compose.ui.platform.LocalDensity.current
    val gSon by rememberUpdatedState(g); val msSon by rememberUpdatedState(ms)
    val bw = (kare?.width ?: k.gen).toFloat().coerceAtLeast(1f); val bh = (kare?.height ?: k.yuk).toFloat().coerceAtLeast(1f)
    fun cerceve(): Pair<Offset, Size> {
        val sc = min(boyut.width / bw, boyut.height / bh); val w = bw * sc; val h = bh * sc
        return Offset((boyut.width - w) / 2f, (boyut.height - h) / 2f) to Size(w, h)
    }
    fun kareye(e: Offset): Offset? { val (o, s) = cerceve(); if (s.width < 2f) return null
        val x = (e.x - o.x) / s.width; val y = (e.y - o.y) / s.height
        return if (x in 0f..1f && y in 0f..1f) Offset(x, y) else null }
    /** Dokunulan oval (en küçüğü — iç içe yüzlerde öndeki). */
    fun ovalde(e: Offset): Int? { val (o, s) = cerceve(); if (s.width < 2f) return null
        return YuzGizleme.anlikHepsi(gSon, msSon, k.foto).filter { (_, el) ->
            val dx = e.x - (o.x + el.cx * s.width); val dy = e.y - (o.y + el.cy * s.height)
            val a = Math.toRadians(el.aci.toDouble()); val c = cos(a).toFloat(); val sn = sin(a).toFloat()
            val qx = (c * dx - sn * dy) / (el.rx * s.width).coerceAtLeast(1f); val qy = (sn * dx + c * dy) / (el.ry * s.height).coerceAtLeast(1f)
            qx * qx + qy * qy <= 1.15f
        }.minByOrNull { it.second.rx * it.second.ry }?.first }

    Box(modifier.background(Color.Black).onSizeChanged { boyut = it }
        .pointerInput(k.id) { detectTapGestures(
            onTap = { e -> ovalde(e)?.let { id -> vm.gizleSec(id); vm.gizleSecili.value = id } },
            onLongPress = { e -> if (ovalde(e) == null) kareye(e)?.let { vm.gizleElleEkle(it.x, it.y, msSon) } }) }) {
        (sonuc ?: kare)?.let { b ->
            val (o, s) = cerceve()
            Image(b.asImageBitmap(), null, Modifier.offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                .size(with(yog) { s.width.toDp() }, with(yog) { s.height.toDp() }))
        }
        Canvas(Modifier.fillMaxSize()) {
            val (o, s) = cerceve(); if (s.width < 2f) return@Canvas
            for ((id, el) in YuzGizleme.anlikHepsi(g, ms, k.foto)) {
                val on = id in g.secili
                val c = Offset(o.x + el.cx * s.width, o.y + el.cy * s.height)
                val w = el.rx * s.width * 2; val h = el.ry * s.height * 2
                rotate(-el.aci, c) {
                    drawOval(Color.Black.copy(alpha = 0.35f), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), style = Stroke(3.dp.toPx()))
                    drawOval(Color.White.copy(alpha = if (on) 0.55f else 0.9f), Offset(c.x - w / 2, c.y - h / 2), Size(w, h),
                        style = Stroke((if (id == secY) 2.5f else 1.3f).dp.toPx(), pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
                }
            }
        }
        Text(if (ariyor) "Aranıyor…" else if (g.yuzler.isEmpty()) "Kaçan yüze basılı tut = elle ekle" else "Ovale dokun = gizle / göster · boşa basılı tut = yüz ekle",
            color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp))
    }
}

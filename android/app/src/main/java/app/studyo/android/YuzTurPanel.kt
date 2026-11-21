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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
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
        LaunchedEffect(k.id, t.vurguTur, t.vurgu, t.noktalar.size) { vm.turSiluetHazirla(k.id) }   // 3.87: silüet ışığı için kişi hattı

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
                        Text(d.ad.trim().substringBefore(' ').take(8).ifEmpty { if (d.yb > 0f) "yüz" else "nokta" }, color = if (d.ad.isNotBlank()) Renk.yazi else Renk.soluk,
                            fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
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
                // 3.76: isim kutusu — kişinin adı (boş bırakılırsa kutu çıkmaz)
                var ad by remember(k.id, s) { mutableStateOf(d.ad) }
                OutlinedTextField(ad, { ad = it; vm.turIsim(s, it) }, Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true,
                    label = { Text("${s + 1}. kişinin adı · isim kutusu") }, placeholder = { Text("Ad Soyad") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                TurBaslik("YAKINLIK · ${s + 1}. DURAK")
                var z by remember(k.id, s, d.z) { mutableFloatStateOf(d.z) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Uzak", color = Renk.soluk, fontSize = 11.sp)
                    InceSlider(z, { z = it; vm.turYakinlik(s, it) }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 1f..6f)
                    Text("${yuvarla1(z)}×", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                }
                // 3.76: bütün yüz duraklarına hazır yakınlık — yalnız o kişi kadrajda olsun
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Yüzlere:", color = Renk.soluk, fontSize = 11.5.sp)
                    YuzTur.YUZ_HEDEF.forEach { (h, ad2) ->
                        Text(ad2, color = Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Renk.kart2)
                            .border(1.dp, Renk.cizgi, RoundedCornerShape(16.dp)).clickable { vm.turYuzOdak(h); vm.mesaj.value = "Bütün yüzler: $ad2" }
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                    }
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
            // 3.76: odaklanan kişiyi öne çıkarma
            TurBaslik("ÖNE ÇIKARMA")
            AnahtarSatiri("Kişiyi ışıkla öne çıkar", t.vurgu) { v -> vm.turAyar { it.copy(vurgu = v) } }
            // 3.87: ışık biçimi — silüet (Samsung Galeri'deki gibi: iki alt uçtan omuzları ve başı izleyip tepede buluşur) · ince çizgi (3.76)
            if (t.vurgu) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("siluet" to "Silüet ışığı", "cizgi" to "Baş çevresinde çizgi").forEach { (v, ad2) ->
                        val on = t.vurguTur == v
                        Text(ad2, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(16.dp))
                            .clickable { vm.turAyar { it.copy(vurguTur = v) } }.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
                if (t.vurguTur == "siluet") {
                    val eksik = t.noktalar.count { it.yb > 0f && it.siluet.isEmpty() }
                    if (t.siluetArandi && eksik > 0) Text("$eksik kişinin hattı bulunamadı; onlarda baş çevresinde çizgi kullanılır.", color = Renk.soluk, fontSize = 11.5.sp)
                    else if (!t.siluetArandi && eksik > 0) Text("Kişilerin hattı bulunuyor…", color = Renk.soluk, fontSize = 11.5.sp)
                }
            }
            AnahtarSatiri("İsim kutusu", t.isim) { v -> vm.turAyar { it.copy(isim = v) } }
            if (t.isim) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to "2 saniye", true to "Durak boyunca").forEach { (v, ad2) ->
                    val on = t.isimDurak == v
                    Text(ad2, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(16.dp))
                        .clickable { vm.turAyar { it.copy(isimDurak = v) } }.padding(horizontal = 14.dp, vertical = 7.dp))
                }
            }
            if (t.isim && t.noktalar.none { it.ad.isNotBlank() }) Text("İsim için bir numaraya dokun, adını yaz.", color = Renk.soluk, fontSize = 11.sp)
            TurBaslik("BAŞ · SON")
            AnahtarSatiri("Önce fotoğrafın tamamı", t.bastaTumu) { v -> vm.turAyar { it.copy(bastaTumu = v) } }
            AnahtarSatiri("Sonda fotoğrafın tamamı", t.sondaTumu) { v -> vm.turAyar { it.copy(sondaTumu = v) } }
            AnahtarSatiri("Sinematik kavis (geçişte hafif geri çekil)", t.kavis) { v -> vm.turAyar { it.copy(kavis = v) } }
        }
        AcilirNot("Duraklar numara sırasıyla gezilir. Kamera her durakta biraz kalır ve çok yavaş yaklaşır, sonra yumuşakça (birden değil) bir sonrakine geçer. " +
            "Fotoğrafın geri kalanı olduğu gibi kalır: karartma ya da bulanıklık yok. Yüz yoksa da istediğin yerlere dokunarak durak ekleyebilirsin. " +
            "Bir numaraya dokunup adını yazarsan kameranın o kişiye vardığı anda isim kutusu çıkar; öne çıkarma çizgisi kişinin etrafında bir tur atıp kaybolur. " +
            "Yüzler: Geniş/Yüz/Yakın bütün yüz duraklarının yakınlığını birden ayarlar. Tur sığmazsa fotoğrafın süresi kendiliğinden uzar (Kırp sayfasından değişir). Hareket 'Sabit' yapılırsa tur durur, duraklar silinmez.")
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
                if (n.ad.isNotBlank()) {   // 3.76: adı numaranın altında
                    val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 11.sp.toPx(); color = android.graphics.Color.WHITE
                        setShadowLayer(4f, 0f, 1f, android.graphics.Color.BLACK) }
                    drawContext.canvas.nativeCanvas.drawText(n.ad.trim().take(18), c.x, c.y + r + 14.sp.toPx(), ip)
                }
            }
        }
        Text(if (t.noktalar.isEmpty()) "Yüze ya da yere dokun = durak ekle" else "Dokun = durak ekle · numarayı sürükle = taşı",
            color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp))
    }
}


/** 3.78: Düzenle › Klip › Ayrı video — seçilen kişilerden (yüz duraklarından) ayrı bir video: Reels (dikey), yatay ya da kare.
 *  Bu proje değişmez; yeni bir taslak açılır ve dışa aktarma ekranı gelir. */
@Composable
fun YuzAyriVideoSayfasi(vm: EditorDurum, p: Proje, sec: Int, k: Klip) {
    Kart("Ayrı video · klip ${sec + 1}") {
        val t = k.yuzTur
        if (!k.foto || t == null || t.noktalar.isEmpty()) {
            Text("Önce Yüzler sekmesinde kişileri (durakları) ekle; sonra buradan seçtiklerinle ayrı bir video yaparsın.", color = Renk.soluk, fontSize = 12.5.sp)
            return@Kart
        }
        val n = t.noktalar.size
        var secili by remember(k.id, n) { mutableStateOf((0 until n).toSet()) }
        var bicim by remember { mutableStateOf("9:16") }
        TurBaslik("KİŞİLER")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            t.noktalar.forEachIndexed { j, d ->
                val on = j in secili
                Column(Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).clickable { secili = if (on) secili - j else secili + j }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, CircleShape),
                        contentAlignment = Alignment.Center) { Text("${j + 1}", color = if (on) Renk.zemin else Renk.soluk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Text(d.ad.trim().substringBefore(' ').take(8).ifEmpty { if (d.yb > 0f) "yüz" else "nokta" }, color = if (on) Renk.yazi else Renk.soluk,
                        fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tümü", color = Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { secili = (0 until n).toSet() }.padding(6.dp))
            Text("Hiçbiri", color = Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { secili = emptySet() }.padding(6.dp))
        }
        TurBaslik("BİÇİM")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Triple("9:16", "Reels · dikey", "bicim"), Triple("16:9", "Yatay", "tuval"), Triple("1:1", "Kare", "kadraj")).forEachIndexed { j, (b, ad, sm) ->
                YuvarlakSecim(ad, sm, Color(TON[j % TON.size]), bicim == b) { bicim = b }
            }
        }
        val t2 = t.copy(noktalar = t.noktalar.filterIndexed { j, _ -> j in secili })
        Text(if (secili.isEmpty()) "Kişi seçilmedi." else "${secili.size} kişi · yaklaşık ${String.format(java.util.Locale("tr"), "%.1f", YuzTur.toplamSn(t2))} sn · isim kutusu ve öne çıkarma dahil",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (secili.isEmpty()) Renk.kart2 else Renk.yazi)
            .clickable(enabled = secili.isNotEmpty()) { vm.turAyriVideo(secili.toList(), bicim) }, contentAlignment = Alignment.Center) {
            Text("Videoyu oluştur", color = if (secili.isEmpty()) Renk.soluk else Renk.zemin, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        AcilirNot("Bu proje değişmez. Seçtiğin kişilerle yeni bir taslak açılır (Projeler'de durur) ve dışa aktarma ekranı gelir. " +
            "Yakınlıklar seçilen biçime göre yeniden hesaplanır; isim kutusu, öne çıkarma, müzik, logo ve renk korunur; giriş, abone ve kapanış eklenmez.")
    }
}

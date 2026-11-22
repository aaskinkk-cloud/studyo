@file:OptIn(ExperimentalMaterial3Api::class)

package app.studyo.android

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import kotlin.math.abs
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val kaydiriciRenk: SliderColors @Composable get() = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi)

/** Seçenek hapları: eşit genişlikte, gerekirse iki sıra (boş hücre yok). */
@Composable
fun Secenekler(liste: List<Pair<String, String>>, secili: String, sutun: Int = 3, sec: (String) -> Unit) {
    liste.chunked(sutun).forEach { sira ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sira.forEach { (k, ad) ->
                val on = k == secili
                if (Duzen.yaziliMenu) {
                    val sekil = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(sekil).background(if (on) Renk.turuncu.copy(alpha = 0.18f) else Renk.kart2)
                            .border(1.5.dp, if (on) Renk.turuncu else Color.Transparent, sekil).clickable { sec(k) },
                        contentAlignment = Alignment.Center,
                    ) { Text(ad, color = if (on) Renk.turuncu else Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                } else {
                    val ton = tonRenk(liste.indexOfFirst { it.first == k }, ad)
                    val sekil = RoundedCornerShape(50)
                    Box(Modifier.weight(1f).clip(sekil).border(if (on) 1.2.dp else 0.7.dp, ton.copy(alpha = if (on) 0.95f else 0.35f), sekil)
                        .background(if (on) ton.copy(alpha = 0.10f) else Color.Transparent).clickable { sec(k) }.padding(vertical = 6.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center) {
                        Text(ad, color = if (on) ton else ton.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Kaydirici(etiket: String, deger: Float, aralik: ClosedFloatingPointRange<Float>, yazi: (Float) -> String, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    Etiketli(etiket, yazi(v))
    InceSlider(value = v, onValueChange = { v = it }, valueRange = aralik, onValueChangeFinished = { bitti(v) }, colors = kaydiriciRenk)
}

@Composable
private fun Not(m: String) = AcilirNot(m)   // 3.72: bütün açıklamalar kapalı gelir (dokun = aç)

// ===================================================== ÖNİZLEME =====================================================

/** Önizleme: dışa aktarmadaki bitmaplerin ve geçiş hesabının AYNISI (1080 ölçeğinde çizilip küçültülür). */
@Composable
fun OnizlemeKatmanli(vm: EditorDurum, p: Proje, nesnePaneli: Boolean, modifier: Modifier, logoPaneli: Boolean = false, katmanPaneli: Boolean = false, hareketPaneli: Boolean = false, renkPaneli: Boolean = false,
                     sablonSayfa: String? = null, yuzPaneli: Boolean = false, gizlePaneli: Boolean = false) {
    val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
    val sira by vm.sira.collectAsState()
    val konumAkis by vm.konum.collectAsState()
    val oynuyorAkis by vm.oynuyor.collectAsState()
    // Oynarken zaman HER EKRAN KARESİNDE oynatıcıdan okunur (eskiden 40 ms'lik döngüyle ~25 kez/sn güncelleniyordu:
    // geçişteki kararma/parlama/yakınlaşma ve üst katman solması basamak basamak ilerliyor, titreme/sıçrama gibi görünüyordu).
    // Klip sınırında oynatıcının birkaç ms'lik geri değeri yok sayılır; gerçek atlama (>250 ms) kabul edilir.
    var kareMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(oynuyorAkis) {
        if (!oynuyorAkis) return@LaunchedEffect
        var son = vm.anlik()
        while (true) {
            withFrameNanos { }
            val t = vm.anlik()
            if (t >= son || son - t > 250) son = t
            kareMs = son
        }
    }
    val konum = if (oynuyorAkis) kareMs else konumAkis
    val takipSecim by vm.takipSecimde.collectAsState()
    val seciliNesne by vm.seciliNesne.collectAsState()
    val k = p.klipler.getOrNull(sira)
    // kare dışı koyu gri, kare sınırı ince açık çizgi: videonun nerede bittiği görünür
    Box(modifier.background(Color(0xFF1C1E22)), contentAlignment = Alignment.Center) {
        if (p.klipler.isEmpty()) {
            Text("Başlamak için alttan “＋ Ekle” ile video veya fotoğraf seç.\nBoyut sınırı yok; dosyalar kopyalanmaz.",
                color = Renk.soluk, textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            return@Box
        }
        BoxWithConstraints(Modifier.aspectRatio(oran).clipToBounds().background(Color.Black).border(1.dp, Color(0x99FFFFFF))) {
            // Akıcı önizleme (oynarken): dışa aktarmanın aynısı tek boru hattında; düzenleme çizimleri gerekmez
            // Yüzey en altta durur; ilk kare çizilene kadar düzenleme önizlemesi üstte kalır (siyah ekran görünmez),
            // ilk kare gelince yalnız yüzey kalır.
            val kompA by vm.kompAktif.collectAsState()
            val kompG by vm.kompGorunur.collectAsState()
            val kompD by vm.kompDurgun.collectAsState()
            // 3.80: duraklatılınca da yüzey yerinde kalır (donmuş son kare düzenleme önizlemesinin altında) — siyah ekran yok
            if (kompA || kompD) AndroidView(factory = { c -> android.view.SurfaceView(c).also { vm.kompYuzeyBagla(it) } },
                onRelease = { vm.kompYuzeyBagla(null) }, modifier = Modifier.fillMaxSize())
            // 3.68: akıcı oynatmada önizlemeye dokun = dur
            if (kompA && kompG) { Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.oynatDurdur() } }); return@BoxWithConstraints }
            val ref = remember(p.bicim) { Disa.boyut(p.bicim, 1080) }
            val RW = ref.first; val RH = ref.second
            val olcek = constraints.maxWidth.toFloat() / RW
            val yog = LocalDensity.current
            var kbF by remember { mutableFloatStateOf(0f) }   // fotoğraf klibinde ilerleme 0…1 (yavaş yakınlaşma / yüze yaklaşma)
            LaunchedEffect(k?.id, k?.kenBurns, k?.sureMs) {
                if (k != null && k.kenBurns) {      // fotoğraf ve video: otomatik hareket (Kareden Videoya)
                    while (true) {
                        withFrameNanos { }
                        kbF = (vm.player.currentPosition.toFloat() / k.sureMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    }
                } else kbF = 0f
            }
            // elle kaydırma: sürüklerken yerel değer, bırakınca klibe yazılır
            var surKay by remember(k?.id) { mutableStateOf<Offset?>(null) }
            // 3.87: videoda sürükleme sırasında (ve bırakınca 1,5 sn) karenin kendisi bitmap olarak gösterilir — oynatıcı yeniden çizerken kararmaz
            var videoDondur by remember(k?.id) { mutableStateOf(false) }
            LaunchedEffect(surKay == null) { if (surKay == null && videoDondur) { kotlinx.coroutines.delay(1500); videoDondur = false } }
            val kG = k?.let { kk -> surKay?.let { kk.copy(kayX = it.x, kayY = it.y) } ?: kk }
            val kbAn = kG?.let { Yuz.an(it, RW, RH, kbF, sira) }
            val an = Gecisler.onizleme(p, konum)
            // KÖK DÜZELTME (3.7): oynatıcı görünümü görüntünün KENDİ boyutunda (s=1'deki dw×dh) ortalanır ve Sığdır modunda tamamını çizer;
            // kaydırma/yaklaşma bu tam görüntüye uygulanır, kare (dış kutu) keser. Eskiden görünüm kareye kırpılıp SONRA kaydırılıyordu:
            // dikey fotoğraf yukarı/aşağı kayarken taşan kısım hiç görünmüyor, yerine siyah bant çıkıyordu (yarım ekran). Dışa aktarma zaten doğruydu.
            fun gorunumGen(a: Yuz.An?) = with(yog) { (constraints.maxWidth / (a?.vx ?: 1f)).toDp() }
            fun gorunumYuk(a: Yuz.An?) = with(yog) { (constraints.maxHeight / (a?.vy ?: 1f)).toDp() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { c ->
                        (LayoutInflater.from(c).inflate(R.layout.oynatici, null, false) as PlayerView).apply { player = vm.player; kareyiYenile(vm.player) }
                    },
                    update = { v -> v.kadraj(false) },
                    modifier = Modifier.requiredSize(gorunumGen(kbAn), gorunumYuk(kbAn)).graphicsLayer {
                        val kb = kbAn?.s ?: 1f
                        scaleX = kb * an.olcek; scaleY = kb * an.olcek
                        translationX = (kbAn?.tx ?: 0f) * size.width / 2f; translationY = -(kbAn?.ty ?: 0f) * size.height / 2f
                        rotationZ = an.aci   // 3.59: Döndür geçişi
                    }.then(if (an.bulanik > 0.01f) Modifier.blur((24f * an.bulanik).dp) else Modifier),   // 3.59: Bulanık geçişi (Android 12+)
                )
            }
            // 3.56: Renk bölümünde ayarın sonucu doğrudan ana önizlemede (durağanken). Önceden durağan oynatıcı ham görüntüyü gösterdiği için
            // Renk sayfasında ayrı bir önce/sonra kutusu gerekiyordu; o kutu kaldırıldı. Oynarken akıcı önizleme rengi zaten uygular.
            // Aynı yerleşim (requiredSize + graphicsLayer) → kare oynatıcı görüntüsünün tam üstüne oturur. Basılı tut = önce.
            val goruntuAyari = k != null && (k.renk?.bos == false || (k.filtre != "yok" && k.filtreGuc > 0.001f) || (k.efekt != "yok" && k.efektGuc > 0.001f) || k.arkaBulanik > 0.01f || k.gizleVar)
            // 3.61: arka planı bulanık klip durağanken her bölümde bulanık görünür (kesilen nesne önünde)
            // 3.87 KÖK DÜZELTME (fotoğrafı sürükleyince / düzenleyince ekranın kararması): duraklatılmışken fotoğraf klibi oynatıcının yüzeyine
            // bağlı kalmadan, fotoğrafın kendisinden (aynı yerleşim + aynı kaydırma/yaklaşma) çizilir. Oynatıcı sarma, akıcı oynatıcıyı bırakma,
            // yüzey yeniden kurma gibi anlarda kareyi bir an boşaltıyordu (perde saydam → siyah zemin). Fotoğraf durağan olduğu için görüntü aynıdır.
            val fotoSabit = k != null && k.foto && !(kompA && kompG)
            val ayarGoster = (renkPaneli || (k?.arkaBulanik ?: 0f) > 0.01f || k?.gizleVar == true) && goruntuAyari
            if ((ayarGoster || fotoSabit || videoDondur) && !oynuyorAkis && k != null && !gizlePaneli) {
                val ctxR = LocalContext.current
                val kaynakMs = if (k.foto) 0L else k.basMs + vm.player.currentPosition.coerceAtLeast(0)
                val ham by produceState<Bitmap?>(null, k.id, k.uri, kaynakMs) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            if (k.foto) Medya.ornekleCoz(java.io.File(android.net.Uri.parse(k.uri).path ?: ""), 1080)
                            else Medya.kareTam(ctxR, android.net.Uri.parse(k.uri), kaynakMs, 1080)
                        }.getOrNull()
                    }
                }
                // 3.72: Android 12+ arka plan bulanıklığı gerçek Gauss (RenderEffect, dışa aktarmadaki 4 geçişli GL Gauss'un karşılığı) + %10 karartma;
                // eski sürümlerde yazılım bulanıklığı
                val gpuBulanik = android.os.Build.VERSION.SDK_INT >= 31 && k.arkaBulanik > 0.01f
                val ayarli by produceState<Bitmap?>(null, ham, k.renk, k.filtre, k.filtreGuc, k.efekt, k.efektGuc, k.arkaBulanik, k.yuzGizle, goruntuAyari) {
                    value = if (!goruntuAyari) ham else ham?.let { b -> withContext(Dispatchers.Default) {
                        val r0 = RenkLut.uygula(b, k.renk ?: RenkAyar(), k.filtre, k.filtreGuc)
                        // 3.83: yüz gizleme (dışa aktarmadaki ovallerin aynısı)
                        val r = k.yuzGizle?.takeIf { k.gizleVar }?.let { g -> YuzGizleme.uygula(r0, YuzGizleme.anlik(g, kaynakMs, k.foto), g.guc) } ?: r0
                        Efektler.uygula(if (gpuBulanik) r else Efektler.bulaniklastir(r, k.arkaBulanik), k.efekt, k.efektGuc) } }
                }
                val bulanikDp = with(yog) { (k.arkaBulanik.coerceIn(0f, 1f) * 0.035f * minOf(constraints.maxWidth, constraints.maxHeight)).toDp() }
                var onceGoster by remember { mutableStateOf(false) }
                (if (onceGoster) ham else ayarli ?: ham)?.let { bm ->
                    Box(Modifier.fillMaxSize().background(Color.Black).then(if (renkPaneli) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { onceGoster = true; tryAwaitRelease(); onceGoster = false }) } else Modifier),
                        contentAlignment = Alignment.Center) {
                        val gauss = gpuBulanik && !onceGoster
                        Image(bm.asImageBitmap(), null, Modifier.requiredSize(gorunumGen(kbAn), gorunumYuk(kbAn)).graphicsLayer {
                            val kb = kbAn?.s ?: 1f
                            scaleX = kb * an.olcek; scaleY = kb * an.olcek
                            translationX = (kbAn?.tx ?: 0f) * size.width / 2f; translationY = -(kbAn?.ty ?: 0f) * size.height / 2f
                        }.then(if (gauss) Modifier.blur(bulanikDp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle) else Modifier),
                            contentScale = ContentScale.Fit,
                            colorFilter = if (gauss) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToScale(0.9f, 0.9f, 0.9f, 1f) }) else null)
                        if (renkPaneli) Text(if (onceGoster) "Önce" else "Basılı tut = önce", color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            if (an.renk > 0f) Box(Modifier.fillMaxSize().background((if (an.beyaz) Color.White else Color.Black).copy(alpha = an.renk.coerceIn(0f, 1f))))
            // 3.59: Silme ve Işık sızması geçişleri (dışa aktarmadaki KenarEtki ile aynı biçim)
            if (an.silme > 0.001f) {
                val yum = 0.04f
                val duraklar: Array<Pair<Float, Color>> = if (an.giris)
                    arrayOf(0f to Color.Transparent, (1f - an.silme - yum).coerceIn(0f, 1f) to Color.Transparent, (1f - an.silme + yum).coerceIn(0f, 1f) to Color.Black, 1f to Color.Black)
                else arrayOf(0f to Color.Black, (an.silme - yum).coerceIn(0f, 1f) to Color.Black, (an.silme + yum).coerceIn(0f, 1f) to Color.Transparent, 1f to Color.Transparent)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(*duraklar)))
            }
            if (an.sizma > 0.001f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = an.sizma.coerceIn(0f, 1f) }.background(Brush.radialGradient(
                listOf(Color(0xE6FFA040), Color(0x66FF6A00), Color.Transparent), center = Offset(0.15f * constraints.maxWidth, 0.2f * constraints.maxHeight),
                radius = 1.1f * maxOf(constraints.maxWidth, constraints.maxHeight))))

            // üst üste binen geçiş: giden klibin son karesi üstte solar/kayar (çıktıda giden klibin devamı oynar)
            val pencereler = remember(p.klipler, p.gecis, p.kapanis) { Gecisler.pencereler(p) }
            LaunchedEffect(pencereler) { for ((i, _, _) in pencereler) if (i in p.klipler.indices && p.klipler[i].foto) vm.sonKareIste(p.zKlip(i)) }
            val sonKareler by vm.sonKareler.collectAsState()
            val oynuyor by vm.oynuyor.collectAsState()
            // GELEN fotoğraf ALTA (opak) — giden klibin kuyruğundan ÖNCE çizilmeli. Eskiden sonra çiziliyordu:
            // video → fotoğraf geçişinde giden videonun kuyruğunu tamamen örtüyor, geçiş "kesme" gibi görünüyordu.
            LaunchedEffect(pencereler) { for ((i, _, _) in pencereler) p.klipler.getOrNull(i + 1)?.let { if (it.foto) vm.sonKareIste(it) } }
            pencereler.firstOrNull { konum >= it.second && konum < it.second + it.third }?.let { (i, b0, _) ->
                val yerel = (konum - b0).coerceAtLeast(0)
                p.klipler.getOrNull(i + 1)?.takeIf { it.foto }?.let { kg ->
                    sonKareler[vm.sonKareAnahtar(kg)]?.let { bm ->
                        val a = Yuz.an(kg, RW, RH, yerel.toFloat() / kg.sureMs.coerceAtLeast(1), i + 1)
                        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            Image(bm.asImageBitmap(), null, Modifier.requiredSize(gorunumGen(a), gorunumYuk(a)).graphicsLayer {
                                scaleX = a.s; scaleY = a.s; translationX = a.tx * size.width / 2f; translationY = -a.ty * size.height / 2f
                            }, contentScale = ContentScale.FillBounds)
                        }
                    }
                }
            }
            // video kuyruğu: pencereye 2 sn kala hazırlanır, pencere içinde ana oynatıcıyla eş zamanlı oynar
            val yakin = pencereler.firstOrNull { konum >= it.second - 2000 && konum < it.second + it.third && p.klipler.getOrNull(it.first)?.foto == false }
            if (pencereler.any { p.klipler.getOrNull(it.first)?.foto == false }) {
                val icinde = yakin != null && konum >= yakin.second
                val yTur = yakin?.let { p.gecisAt(it.first).tur } ?: p.gecis.tur
                val u = if (icinde) Gecisler.ustKatman(yTur, ((konum - yakin!!.second).toFloat() / yakin.third).coerceIn(0f, 1f)) else null
                if (yakin != null) {
                    val kk = p.zKlip(yakin.first)
                    LaunchedEffect(kk.id, kk.sonMs, yakin.third, kk.ses, kk.sessiz) { vm.kuyrukHazirla(kk, yakin.third) }
                    SideEffect { if (icinde) vm.kuyrukEsle(konum - yakin.second, oynuyor) else vm.kuyrukBekle() }
                } else SideEffect { vm.kuyrukBekle() }
                val gen = constraints.maxWidth.toFloat()
                val ka = yakin?.let { y -> val kx = p.klipler[y.first]
                    Yuz.an(kx, RW, RH, ((p.zKlip(y.first).sureMs + (konum - y.second).coerceAtLeast(0)).toFloat() / kx.sureMs.coerceAtLeast(1)), y.first) }
                Box(Modifier.fillMaxSize().clipToBounds()
                    .erimeMaskesi(yTur == "erime" && icinde, ((konum - (yakin?.second ?: 0L)).toFloat() / (yakin?.third ?: 1L)).coerceIn(0f, 1f)),
                    contentAlignment = Alignment.Center) {
                    AndroidView(
                        factory = { c -> (LayoutInflater.from(c).inflate(R.layout.oynatici, null, false) as PlayerView).apply { player = vm.kuyrukPl } },
                        update = { v -> v.kadraj(false) },
                        modifier = Modifier.requiredSize(gorunumGen(ka), gorunumYuk(ka))
                            .graphicsLayer {
                            alpha = u?.alfa ?: 0f; scaleX = (u?.olcek ?: 1f) * (ka?.s ?: 1f); scaleY = (u?.olcek ?: 1f) * (ka?.s ?: 1f)
                            translationX = ((u?.zx ?: 0f) - (u?.kx ?: 0f)) * gen / 2f + (ka?.tx ?: 0f) * size.width / 2f
                            translationY = -(ka?.ty ?: 0f) * size.height / 2f
                        },
                    )
                }
            }
            // Giden fotoğraf (üstte, solar/erir) — gelen klip altta.
            pencereler.firstOrNull { konum >= it.second && konum < it.second + it.third }?.let { (i, b0, T) ->
                val gen = constraints.maxWidth.toFloat()
                val yerel = (konum - b0).coerceAtLeast(0)
                p.klipler.getOrNull(i)?.takeIf { it.foto }?.let { kc ->
                    sonKareler[vm.sonKareAnahtar(p.zKlip(i))]?.let { bm ->
                        val gTur = p.gecisAt(i).tur
                        val u = Gecisler.ustKatman(gTur, (yerel.toFloat() / T).coerceIn(0f, 1f))
                        val a = Yuz.an(kc, RW, RH, (p.zKlip(i).sureMs + yerel).toFloat() / kc.sureMs.coerceAtLeast(1), i)
                        Box(Modifier.fillMaxSize().clipToBounds().erimeMaskesi(gTur == "erime", (yerel.toFloat() / T).coerceIn(0f, 1f)), contentAlignment = Alignment.Center) {
                            Image(bm.asImageBitmap(), null, Modifier.requiredSize(gorunumGen(a), gorunumYuk(a)).graphicsLayer {
                                alpha = u.alfa; scaleX = u.olcek * a.s; scaleY = u.olcek * a.s
                                translationX = (u.zx - u.kx) * gen / 2f + a.tx * size.width / 2f; translationY = -a.ty * size.height / 2f
                            }, contentScale = ContentScale.FillBounds)
                        }
                    }
                }
            }

            @Composable
            fun Ciz(y: Yerlesim, alfa: Float) {
                if (alfa <= 0.01f) return
                val (x0, y0) = y.solUst(RW, RH)
                Image(y.bmp.asImageBitmap(), null,
                    Modifier.offset { IntOffset((x0 * olcek).roundToInt(), (y0 * olcek).roundToInt()) }
                        .size(with(yog) { (y.bmp.width * olcek).toDp() }, with(yog) { (y.bmp.height * olcek).toDp() })
                        .alpha((y.alfa * alfa).coerceIn(0f, 1f)))
            }

            // klip yazısı ve logo
            val yaziY = remember(k?.yazi, ref) { k?.yazi?.let { Kaplama.yazi(it, RW, RH) } }
            val logoY = remember(p.logo, ref) { p.logo?.let { Kaplama.logo(it, RW, RH) } }
            yaziY?.let { if (sira < p.klipler.size) Ciz(it, 1f) }
            // 3.76: yüz turu — öne çıkarma çizgisi ve isim kutusu (dışa aktarmadaki TurVurgu hesabının aynısı)
            if (k != null && k.turEtkin) LaunchedEffect(k.id) { vm.turSiluetHazirla(k.id) }   // 3.87: silüet ışığı (eski projelerde bir kez bulunur)
            if (k != null && k.turEtkin && sira < p.klipler.size && !yuzPaneli) {
                val D = k.sureMs / 1000f
                val tv = TurVurgu.durum(k, RW, RH, kbF * D, D)
                if (tv != null) {
                    if (tv.yuz && tv.halkaAlfa > 0.002f) Canvas(Modifier.fillMaxSize()) {
                        val nc = drawContext.canvas.nativeCanvas
                        nc.save(); nc.scale(olcek, olcek)
                        TurVurgu.halkaCiz(nc, tv, kotlin.math.min(RW, RH).toFloat())
                        nc.restore()
                    }
                    if (tv.adAlfa > 0.002f && tv.ad.isNotEmpty()) TurVurgu.isimOnbellek(tv.ad, RW, RH)?.let { ib ->   // 3.78: koşullu remember yerine önbellek
                        val (ix, iy) = TurVurgu.isimYeri(tv, ib.width, ib.height, RW, RH)
                        val (zx, zy) = TurVurgu.ndc(ix, iy, RW, RH)
                        Ciz(Yerlesim(ib, zx, zy, 0f, 0f), tv.adAlfa)
                    }
                }
            }
            // nesneler · 3.72: parmakla taşıma/büyütme sırasında geçici dönüşüm (bitmap yeniden çizilmez, parmağı akıcı izler); bırakınca kaydedilir
            var nesGecici by remember { mutableStateOf<NesneGecici?>(null) }
            val nesBoyut = remember { HashMap<String, Pair<Int, Int>>() }
            @Composable
            fun CizN(y: Yerlesim, alfa: Float, n: Nesne) {
                if (alfa <= 0.01f) return
                nesBoyut[n.id] = y.bmp.width to y.bmp.height
                val g = nesGecici?.takeIf { it.id == n.id }
                val (x0, y0) = y.solUst(RW, RH)
                val dx = (g?.dx ?: 0f) * RW; val dy = (g?.dy ?: 0f) * RH
                val secili = nesnePaneli && seciliNesne == n.id
                Image(y.bmp.asImageBitmap(), null,
                    Modifier.offset { IntOffset(((x0 + dx) * olcek).roundToInt(), ((y0 + dy) * olcek).roundToInt()) }
                        .size(with(yog) { (y.bmp.width * olcek).toDp() }, with(yog) { (y.bmp.height * olcek).toDp() })
                        .graphicsLayer { val c = g?.carpan ?: 1f; scaleX = c; scaleY = c; this.alpha = (y.alfa * alfa).coerceIn(0f, 1f) })
                // 4.08: seçili nesnenin çerçevesi — kesikli çizgi, köşelerde büyüt/küçült tutamaçları, ortada taşıma simgesi (parmağı akıcı izler)
                if (secili) {
                    val c = g?.carpan ?: 1f
                    val w = y.bmp.width * olcek * c; val h = y.bmp.height * olcek * c
                    val cx = (x0 + dx) * olcek + y.bmp.width * olcek / 2f; val cy = (y0 + dy) * olcek + y.bmp.height * olcek / 2f
                    NesneCerceve(cx - w / 2f, cy - h / 2f, w, h)
                }
            }
            p.nesneler.forEach { n ->
                key(n.id) {
                    if (n.hareketli) {
                        val a = Zaman.solma(konum, n.basMs, n.sonMs, 200)
                        val idx = Katman.kareNo(n, konum)
                        val kb2 by produceState<Bitmap?>(null, n.id, idx, n.olcek, a > 0f) {
                            value = if (a > 0f) withContext(Dispatchers.IO) { Katman.nesneKaresi(n, idx, RW) } else null
                        }
                        kb2?.let { CizN(Yerlesim(it, n.x * 2f - 1f, 1f - n.y * 2f, 0f, 0f), a, n) }
                    } else {
                        val ny = remember(n.yol, n.olcek, n.golge, n.x, n.y, ref) { Katman.nesne(n, RW, RH) }
                        ny?.let { CizN(it, Zaman.solma(konum, n.basMs, n.sonMs), n) }
                    }
                }
            }
            // takip eden yazı
            val t = k?.takip
            if (k != null && t != null && !k.foto && sira < p.klipler.size) {
                val tb = remember(t.metin, t.alt, t.boyut, t.renk, t.kutu, t.stil, t.cizgiRenk, ref) { Katman.takipGorsel(t, RW, RH) }
                val src = k.basMs + (konum - vm.baslangic(sira)).coerceAtLeast(0)
                val nk = Katman.takipKonum(t, src)
                if (tb != null && nk != null) {
                    val (px, py) = Geometri.ciktiya(k, nk.first, nk.second, RW, RH, kbAn ?: Yuz.an(k, RW, RH, 0f))
                    Ciz(Katman.takipYerlesimG(tb, px, py, RW, RH), 1f)
                }
            }
            // giriş başlığı (klasik ya da reels şeritleri), ışıklı boncuklar
            // 3.72: ekranda görünen giriş yazısının kutuları (dokununca ekranda düzenleme açılsın)
            val girisKutular = remember { ArrayList<androidx.compose.ui.geometry.Rect>() }
            girisKutular.clear()
            val (gA, gB) = Zaman.girisAralik(p)
            if (p.giris.acik && p.giris.boncuk) {
                val sb = Zaman.solma(konum, gA, gB, 600)
                if (sb > 0f) Canvas(Modifier.fillMaxSize()) {
                    val kk = minOf(size.width, size.height)
                    for (nk in Boncuk.noktalar(konum - gA, sb)) {
                        if (nk.alfa <= 0.01f) continue
                        val c = Offset(nk.x * size.width, nk.y * size.height); val rr = nk.r * kk
                        val renk = Color(nk.renk)
                        drawCircle(Brush.radialGradient(listOf(renk.copy(alpha = nk.alfa), renk.copy(alpha = nk.alfa / 3f), Color.Transparent), c, rr), rr, c)
                    }
                }
            }
            if (p.giris.reels) {
                val seritler = remember(p.giris, ref) { if (p.giris.acik) Reels.seritler(p.giris, RW, RH) else emptyList() }
                for (sr in seritler) {
                    val ak = Reels.an(sr, p.giris.hareket, konum, gA, gB) ?: continue
                    val (x0, y0) = ak.solUst(sr.bmp.width, sr.bmp.height, RW, RH)
                    if (ak.alfa > 0.05f) girisKutular += androidx.compose.ui.geometry.Rect(x0 * olcek, y0 * olcek, (x0 + sr.bmp.width * ak.olcek) * olcek, (y0 + sr.bmp.height * ak.olcek) * olcek)
                    Image(sr.bmp.asImageBitmap(), null,
                        Modifier.offset { IntOffset((x0 * olcek).roundToInt(), (y0 * olcek).roundToInt()) }
                            .size(with(yog) { (sr.bmp.width * ak.olcek * olcek).toDp() }, with(yog) { (sr.bmp.height * ak.olcek * olcek).toDp() })
                            .alpha(ak.alfa.coerceIn(0f, 1f)))
                }
            } else {
                val girisY = remember(p.giris, ref) { Cizim.giris(p.giris, RW, RH) }
                girisY?.let {
                    val a = Zaman.solma(konum, gA, gB)
                    Ciz(it, a)
                    if (a > 0.05f) { val (x0, y0) = it.solUst(RW, RH); girisKutular += androidx.compose.ui.geometry.Rect(x0 * olcek, y0 * olcek, (x0 + it.bmp.width) * olcek, (y0 + it.bmp.height) * olcek) }
                }
            }
            if (p.hatirlatici.stil == "klasik") {
                val hatY = remember(p.hatirlatici, p.logo, p.kapanis.kanal, ref) { Cizim.hatirlatici(p.hatirlatici, RW, RH, p.logo, p.kapanis.kanal) }
                hatY?.let { val (a, b) = Zaman.hatirlaticiAralik(p); Ciz(it, Zaman.solma(konum, a, b)) }
            } else if (p.hatirlatici.acik) {
                // canlı kart: dışa aktarmadaki HatAnim hesabının aynısı
                val hk = remember(p.hatirlatici, p.logo, p.kapanis.kanal, ref) { Cizim.hatirlaticiKartSet(p.hatirlatici, RW, RH, p.logo, p.kapanis.kanal) }
                val (ha, hb) = Zaman.hatirlaticiAralik(p)
                HatAnim.an(konum - ha, hb - ha, hk.normal.width.toFloat() / RW)?.let { x ->
                    val solX = (HatAnim.SOL + x.dx) * RW
                    val ust = HatAnim.ORTA_Y * RH - hk.normal.height / 2f
                    val kb = if (x.abone) hk.abone else hk.normal
                    Image(kb.asImageBitmap(), null,
                        Modifier.offset { IntOffset((solX * olcek).roundToInt(), (ust * olcek).roundToInt()) }
                            .size(with(yog) { (kb.width * olcek).toDp() }, with(yog) { (kb.height * olcek).toDp() }).alpha(x.alfa.coerceIn(0f, 1f)))
                    if (x.nokta > 0.001f) {
                        val nx = solX + hk.dx * kb.width - hk.nokta.width / 2f
                        val ny = ust + hk.dy * kb.height - hk.nokta.height / 2f
                        Image(hk.nokta.asImageBitmap(), null,
                            Modifier.offset { IntOffset((nx * olcek).roundToInt(), (ny * olcek).roundToInt()) }
                                .size(with(yog) { (hk.nokta.width * olcek).toDp() }, with(yog) { (hk.nokta.height * olcek).toDp() })
                                .graphicsLayer { scaleX = x.bas; scaleY = x.bas; alpha = x.nokta.coerceIn(0f, 1f) })
                    }
                }
            }
            // serbest katmanlar: dışa aktarmadaki bitmap ve animasyonun aynısı; seçili katman panel açıkken çerçeveli
            val seciliK by vm.seciliKatman.collectAsState()
            var katGecici by remember { mutableStateOf<KatmanGecici?>(null) }
            val katBoyut = remember { HashMap<String, Pair<Int, Int>>() }
            for (o in p.katmanlar) {
                if (!o.gorsel) continue
                key(o.id) {
                    // 3.64: hareketli yazı kare kare (dışa aktarmayla aynı kare); seçili ve duraklatılmışken tam görünür (düzenlenebilsin)
                    val adimK = if (oynuyorAkis || !(katmanPaneli && seciliK == o.id)) KatmanCizim.adim(o, konum) else KatmanCizim.ADIM
                    val bm = remember(KatmanCizim.anahtar(o), ref, adimK) { KatmanCizim.bitmap(o, RW, RH, adimK) }
                    if (bm != null) {
                        katBoyut[o.id] = bm.width to bm.height
                        val g = katGecici?.takeIf { it.id == o.id }
                        val a = KatmanCizim.an(o, konum)
                        val secili = katmanPaneli && seciliK == o.id
                        if (a.alfa > 0.01f || (secili && !o.gizli && konum in o.basMs..o.sonMs)) {   // 3.42: süresi dışında hayalet kalmaz
                            fun Float.ok(v: Float) = if (isNaN() || isInfinite()) v else this
                            val x = (g?.x ?: o.x).ok(0.5f); val y = ((g?.y ?: o.y) + a.dy).ok(0.5f)
                            val sk = (a.olcek * (g?.carpan ?: 1f)).ok(1f).coerceIn(0.01f, 20f); val aci = (o.aci + (g?.aci ?: 0f)).ok(0f)
                            val w = bm.width * olcek; val h = bm.height * olcek
                            val alfa = if (a.alfa > 0.01f) a.alfa.coerceIn(0f, 1f) else 0.35f
                            Image(bm.asImageBitmap(), null,
                                Modifier.offset { IntOffset((x * RW * olcek - w / 2f).roundToInt(), (y * RH * olcek - h / 2f).roundToInt()) }
                                    .size(with(yog) { w.toDp() }, with(yog) { h.toDp() })
                                    .graphicsLayer { scaleX = sk; scaleY = sk; rotationZ = aci; this.alpha = alfa }
                                    .then(if (secili) Modifier.border(1.dp, if (o.kilitli) Renk.soluk else Renk.turuncu) else Modifier))
                        }
                    }
                }
            }
            LaunchedEffect(katGecici) {
                val g = katGecici ?: return@LaunchedEffect
                delay(400)
                vm.katmanDegis(g.id) { o ->
                    val (mn, mx) = if (o.tur == "metin") 0.02f to 0.3f else 0.03f to 1.5f
                    var aci = (o.aci + g.aci) % 360f; if (aci > 180f) aci -= 360f; if (aci < -180f) aci += 360f
                    if (!g.x.isFinite() || !g.y.isFinite() || !g.carpan.isFinite() || !aci.isFinite()) o
                    else o.copy(x = g.x, y = g.y, olcek = (o.olcek * g.carpan).coerceIn(mn, mx), aci = aci)
                }
                katGecici = null
            }
            if (p.altyaziStil.acik) {
                val sa = Cizim.altyaziAn(p.altyazilar, konum)
                val ay = remember(sa?.first?.id, sa?.first?.metin, sa?.second, p.altyaziStil, ref) { sa?.let { Cizim.altyaziCiz(it.first, it.second, p.altyaziStil, RW, RH) } }
                ay?.let { Ciz(it, 1f) }
            }
            // logo: sürüklenirken aynı bitmap parmağı izler (yeniden çizilmez); bırakınca kaydedilir
            var logoSurukle by remember { mutableStateOf<Pair<Float, Float>?>(null) }
            logoY?.let { ly ->
                val s = logoSurukle
                if (sira < p.klipler.size || s != null)
                    Ciz(if (s == null) ly else Yerlesim(ly.bmp, s.first * 2f - 1f, 1f - 2f * s.second, 0f, 0f, ly.alfa), 1f)
            }

            // 3.73: logo geçişi (dışa aktarmadaki LogoGecis hesabının aynısı): arkada koyu hale, logo büyüyerek gelir ve kaybolur
            val logoAnlar = remember(p.klipler, p.gecis, p.logo, p.kapanis, p.giris) { p.logoGecisAnlari() }   // 3.82: köşe logosundan bağımsız
            if (logoAnlar.isNotEmpty()) {
                val lgBmp = remember(p.gecisLogoYol, p.gecis.logoSekil, ref) { runCatching { LogoGecis.bitmap(p.gecisLogoYol, p.gecis.logoSekil, RW, RH) }.getOrNull() }
                val lgAn = LogoGecis.an(logoAnlar, konum)
                if (lgBmp != null && lgAn != null) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = lgAn.alfa }.background(Brush.radialGradient(
                        listOf(Color(0x8C000000), Color(0x59000000), Color(0x26000000)))))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Image(lgBmp.asImageBitmap(), null,
                            Modifier.size(with(yog) { (lgBmp.width * olcek).toDp() }, with(yog) { (lgBmp.height * olcek).toDp() })
                                .graphicsLayer { scaleX = lgAn.olcek; scaleY = lgAn.olcek; alpha = lgAn.alfa.coerceIn(0f, 1f) })
                    }
                }
            }

            // dokunma: takip noktası seçimi / seçili nesneyi sürükleme
            // önizlemede parmakla sürükle = klibi kadraj içinde kaydır (yalnız taşan kısım kadar). Klip ve Katman bölümünde ortak (3.41)
            val kSonS by rememberUpdatedState(k)
            val gwS = constraints.maxWidth.toFloat(); val ghS = constraints.maxHeight.toFloat()
            val klipSurukle: Modifier = if (k == null || sira >= p.klipler.size) Modifier else Modifier
                    .pointerInput(k.id, k.doldur, gwS, ghS) {
                        detectDragGestures(
                            onDragStart = { surKay = Offset(kSonS!!.kayX, kSonS!!.kayY); if (kSonS?.foto == false) videoDondur = true },
                            onDragEnd = { surKay?.let { o -> vm.klipGuncelle { it.copy(kayX = o.x, kayY = o.y) } }; surKay = null },
                            onDragCancel = { surKay = null },
                        ) { ch, d ->
                            val ks = kSonS!!; val a = Yuz.an(ks, RW, RH, kbF, sira)
                            if (!Yuz.kayabilir(a)) return@detectDragGestures
                            ch.consume()
                            val pxX = (a.s - a.vx) / a.vx * gwS / 2f; val pxY = (a.s - a.vy) / a.vy * ghS / 2f
                            val o = surKay ?: Offset(ks.kayX, ks.kayY)
                            surKay = Offset(if (pxX > 0.5f) (o.x + d.x / pxX).coerceIn(-1f, 1f) else o.x,
                                if (pxY > 0.5f) (o.y + d.y / pxY).coerceIn(-1f, 1f) else o.y)
                        }
                    }
            if (takipSecim && k != null) {
                Box(Modifier.fillMaxSize().background(Color(0x332EC5D3)).pointerInput(k.id) {
                    detectTapGestures { o -> Geometri.kaynaga(k, o.x / olcek, o.y / olcek, RW, RH, kbAn ?: Yuz.an(k, RW, RH, 0f))?.let { vm.takipNoktaSec(sira, it.first, it.second) } }
                }) {
                    Text("Takip edilecek yere dokun", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            } else if (nesnePaneli && katmanPaneli && seciliNesne != null) {
                // 3.45: Katman bölümünde kesilen nesne seçiliyse sürükle = nesneyi taşı · 3.72: iki parmak = büyüt/küçült
                NesneDokunma(vm, p, konum, olcek, RW, RH, nesBoyut, { nesGecici }, { nesGecici = it }, Modifier)   // 3.72: klip kaydırma eklenmez (alttaki bulanık arka plan nesneyle birlikte kayıyordu)
            } else if (katmanPaneli) {
                val pG by rememberUpdatedState(p); val konumG by rememberUpdatedState(konum)
                val payPx = with(yog) { 28.dp.toPx() } / olcek     // 3.45: dokunma alanı katmanın çevresinde genişletilir (küçük yazı kolay tutulur)
                fun isabet(t: Offset): KatmanOge? {
                    val px = t.x / olcek; val py = t.y / olcek
                    return KatmanCizim.gorunenler(pG, konumG).firstOrNull { o ->
                        val (bw, bh) = katBoyut[o.id] ?: return@firstOrNull false
                        val sc = KatmanCizim.an(o, konumG).olcek.let { if (it.isFinite()) it else 1f }
                        katmanIsabet(o, (bw * sc + payPx * 2).toInt(), (bh * sc + payPx * 2).toInt(), px, py, RW, RH)
                    }
                }
                Box(Modifier.fillMaxSize()
                    // 3.45: tek hareket — katmanın üstüne bas ve hemen sürükle (önce seçmek gerekmez); iki parmak = büyüt/döndür;
                    // kısa dokunuş = seç (yazıysa düzenle); boş yere dokun = seçimi kaldır; boş yerde sürükle = klibi kadrajda kaydır
                    .pointerInput(olcek) {
                        awaitEachGesture {
                            val ilk = awaitFirstDown(requireUnconsumed = false)
                            val hedef = isabet(ilk.position)
                            var tasindi = false; var topPan = Offset.Zero
                            if (hedef != null && !hedef.kilitli) {
                                do {
                                    val ev = awaitPointerEvent()
                                    val zoom = ev.calculateZoom(); val pan = ev.calculatePan(); val rot = ev.calculateRotation()
                                    if (!tasindi) {
                                        topPan += pan
                                        if (topPan.getDistance() > viewConfiguration.touchSlop || ev.changes.count { it.pressed } > 1) { tasindi = true; vm.seciliKatman.value = hedef.id }
                                    }
                                    if (tasindi && zoom.isFinite() && rot.isFinite() && pan.x.isFinite() && pan.y.isFinite() && zoom > 0f) {
                                        val g = katGecici?.takeIf { it.id == hedef.id } ?: KatmanGecici(hedef.id, hedef.x, hedef.y, 1f, 0f)
                                        katGecici = g.copy(
                                            x = (g.x + pan.x / (olcek * RW)).coerceIn(0f, 1f), y = (g.y + pan.y / (olcek * RH)).coerceIn(0f, 1f),
                                            carpan = (g.carpan * zoom).coerceIn(0.1f, 8f), aci = g.aci + rot,
                                        )
                                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (ev.changes.any { it.pressed })
                            } else {
                                // boşta: yalnız dokunuşu izle (sürükleme klipSurukle'ye kalır)
                                do {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull() ?: break
                                    topPan += c.position - c.previousPosition
                                    if (topPan.getDistance() > viewConfiguration.touchSlop) tasindi = true
                                } while (ev.changes.any { it.pressed })
                            }
                            if (!tasindi) {
                                vm.seciliKatman.value = hedef?.id   // 3.75: yalnız seçer (klavye açılmaz) — yazı kartı açılır, stile dokun = stil değişir
                            }
                        }
                    }
                    .then(klipSurukle))
            } else if (logoPaneli && logoY != null) {
                val ly = logoY
                Box(Modifier.fillMaxSize().pointerInput(ly, olcek) {
                    detectDragGestures(
                        onDragStart = {
                            val (x0, y0) = ly.solUst(RW, RH)
                            logoSurukle = (x0 + ly.bmp.width / 2f) / RW to (y0 + ly.bmp.height / 2f) / RH
                        },
                        onDragEnd = {
                            logoSurukle?.let { (x, y) -> vm.logoDegis { it.copy(x = x, y = y) } }
                            logoSurukle = null
                        },
                        onDragCancel = { logoSurukle = null },
                    ) { ch, d ->
                        ch.consume()
                        val (x, y) = logoSurukle ?: return@detectDragGestures
                        val yx = ly.bmp.width / 2f / RW; val yy = ly.bmp.height / 2f / RH
                        logoSurukle = (x + d.x / (olcek * RW)).coerceIn(yx, 1f - yx) to (y + d.y / (olcek * RH)).coerceIn(yy, 1f - yy)
                    }
                })
            } else if (nesnePaneli && (seciliNesne != null || p.nesneler.isNotEmpty())) {   // 4.08: nesne varken arka plan hiç kaymaz
                // 3.72: Düzenle › Nesne: nesneye dokun = seç · sürükle = taşı · iki parmak = büyüt/küçült (Kareden Videoya'daki gibi);
                // boş yere dokun = seçimi kaldır; arka plan sabit kalır
                NesneDokunma(vm, p, konum, olcek, RW, RH, nesBoyut, { nesGecici }, { nesGecici = it }, Modifier)   // 3.72: klip kaydırma eklenmez (alttaki bulanık arka plan nesneyle birlikte kayıyordu)
            } else if (sablonSayfa != null && sablonGorunur(p, sablonSayfa, konum)) {
                // 3.72: Görünüm › Şablon: ekrandaki giriş/abone/kapanış yazısına dokun = ekranda düzenle (önizlemenin altında);
                // girişte iki parmak = yazı boyutu. Değişiklik anında önizlemeye yansır.
                var birikim by remember { mutableFloatStateOf(1f) }
                Box(Modifier.fillMaxSize()
                    .pointerInput(sablonSayfa) { detectTapGestures(onTap = { vm.ekranDuzen.value = sablonSayfa }) }
                    .then(if (sablonSayfa == "giris") Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            if (zoom.isFinite() && zoom > 0f) {
                                birikim *= zoom
                                if (kotlin.math.abs(birikim - 1f) > 0.04f) { val c = birikim; birikim = 1f; vm.girisDegis { g -> g.copy(olcek = (g.olcek * c).coerceIn(0.6f, 1.6f)) } }
                            }
                        }
                    } else Modifier))
                val duzenAcik by vm.ekranDuzen.collectAsState()
                if (duzenAcik == null) Text(if (sablonSayfa == "giris") "Yazıya dokun = düzenle · iki parmak = boyut" else "Yazıya dokun = düzenle", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 9.dp, vertical = 3.dp))
            } else if (k != null && sira < p.klipler.size) {
                // izlerken de: parmakla sürükle = fotoğrafı/videoyu kadraj içinde kaydır (yalnız taşan kısım kadar); çift dokun = ortala
                val gw = constraints.maxWidth.toFloat(); val gh = constraints.maxHeight.toFloat()
                val kSon by rememberUpdatedState(k)
                // 3.35: Hareket paneli açıkken dokun = yaklaşmanın odak noktası (dokunulan yer kaynak kareye çevrilir)
                if (hareketPaneli && (kSon.kenBurns && kSon.hareket in setOf("otomatik", "yakin", "uzak") || !kSon.kenBurns)) {
                    val an0 = Yuz.an(kSon, RW, RH, kbF, sira)
                    val (ox0, oy0) = Yuz.odakNokta(kSon)
                    val (mx, my) = Geometri.ciktiya(kSon, ox0, oy0, RW, RH, an0)
                    val elle = kSon.odakSecX >= 0f
                    Box(Modifier.offset { IntOffset((mx * olcek).roundToInt() - with(yog) { 14.dp.roundToPx() }, (my * olcek).roundToInt() - with(yog) { 14.dp.roundToPx() }) }
                        .size(28.dp).border(2.dp, if (elle) Color(TON[3]) else Color.White.copy(alpha = 0.75f), CircleShape))
                }
                Box(Modifier.fillMaxSize()
                    .pointerInput(k.id, hareketPaneli, olcek) { detectTapGestures(
                        onTap = { o ->
                            if (!hareketPaneli) {
                                // 3.72: duraklatılmışken ekrandaki giriş başlığına dokun = ekranda düzenleme (her bölümde)
                                val pay = 16f * density
                                if (!vm.oynuyor.value && girisKutular.any { r -> o.x >= r.left - pay && o.x <= r.right + pay && o.y >= r.top - pay && o.y <= r.bottom + pay }) {
                                    vm.ekranDuzen.value = "giris"; return@detectTapGestures
                                }
                                // 3.44: ekrandaki katmana (yazı, logo…) dokun = Katman bölümünde o katman seçilir; yazıysa düzenleme açılır
                                val px = o.x / olcek; val py = o.y / olcek; val km = vm.konum.value
                                val bul = KatmanCizim.gorunenler(vm.proje.value, km).firstOrNull { ko ->
                                    val (bw, bh) = katBoyut[ko.id] ?: return@firstOrNull false
                                    val sc = KatmanCizim.an(ko, km).olcek
                                    katmanIsabet(ko, (bw * sc).toInt(), (bh * sc).toInt(), px, py, RW, RH)
                                } ?: run { vm.oynatDurdur(); return@detectTapGestures }   // 3.68: boş yere dokun = dur / oynat
                                vm.seciliKatman.value = bul.id; vm.katmanSayfasi.value++
                                // 3.75: yazıya dokun = seç; düzenleme Katman ekle kartının başında (klavye ✎ ile açılır)
                                return@detectTapGestures
                            }
                            val ks = kSon
                            val kn = Geometri.kaynaga(ks, o.x / olcek, o.y / olcek, RW, RH, Yuz.an(ks, RW, RH, kbF, sira)) ?: return@detectTapGestures
                            vm.hepsine({ c -> if (c.id != ks.id) c else c.copy(odakSecX = kn.first, odakSecY = kn.second, kenBurns = true,
                                hareket = if (c.kenBurns && c.hareket in setOf("otomatik", "yakin", "uzak")) c.hareket else "yakin") })
                            vm.mesaj.value = "Yaklaşma buradan başlayacak"
                        },
                        onDoubleTap = { vm.klipGuncelle { it.copy(kayX = 0f, kayY = 0f) }; vm.mesaj.value = "Kadraj ortalandı" }) }
                    .then(klipSurukle))
            }

            // 3.66: seçili katmanın tutamaçları (InShot gibi): ✕ sil · ✎ düzenle (yazı) · ⤡ tek parmakla büyüt/döndür · ⧉ çoğalt;
            // önizlemenin kenarında hızlı düğmeler (küçült, büyüt, ortala, düzelt, öne al, kilitle). Değişiklik katGecici'den kaydedilir.
            if (katmanPaneli && !takipSecim) {
                val so = p.katmanlar.firstOrNull { it.id == seciliK && it.gorsel && !it.gizli }
                val boy = so?.let { katBoyut[it.id] }
                if (so != null && boy != null && konum >= so.basMs && konum <= so.sonMs) {
                    val g = katGecici?.takeIf { it.id == so.id }
                    val anO = KatmanCizim.an(so, konum).olcek.let { if (it.isFinite() && it > 0f) it else 1f }
                    val sk = anO * (g?.carpan ?: 1f)
                    val cx = (g?.x ?: so.x) * RW * olcek; val cy = (g?.y ?: so.y) * RH * olcek
                    val pay = with(yog) { 6.dp.toPx() }
                    val hw = boy.first * olcek * sk / 2f + pay; val hh = boy.second * olcek * sk / 2f + pay
                    val rad = Math.toRadians((so.aci + (g?.aci ?: 0f)).toDouble())
                    val cs = kotlin.math.cos(rad).toFloat(); val sn = kotlin.math.sin(rad).toFloat()
                    fun kose(sx: Float, sy: Float): Offset { val dx = sx * hw; val dy = sy * hh; return Offset(cx + dx * cs - dy * sn, cy + dx * sn + dy * cs) }
                    val gwT = constraints.maxWidth.toFloat(); val ghT = constraints.maxHeight.toFloat()
                    val rT = with(yog) { 15.dp.toPx() }
                    val koseV by rememberUpdatedState(kose(1f, 1f) - Offset(cx, cy))
                    val soG by rememberUpdatedState(so)
                    if (!so.kilitli) {
                        KatmanTutamac(kose(-1f, -1f), rT, gwT, ghT, "kapat", Renk.kirmizi, Color.White, Modifier.clickable {
                            vm.katmanSil(so.id); vm.mesaj.value = "Silindi · geri al ile geri gelir" })
                        if (so.tur == "metin") KatmanTutamac(kose(1f, -1f), rT, gwT, ghT, "degistir", Color.White, Color.Black, Modifier.clickable { vm.katmanDuzenle.value = so.id })
                        KatmanTutamac(kose(-1f, 1f), rT, gwT, ghT, "cogalt", Color.White, Color.Black, Modifier.clickable {
                            vm.katmanCogalt(so.id); vm.mesaj.value = "Çoğaltıldı (hemen ardından başlar)" })
                        KatmanTutamac(kose(1f, 1f), rT, gwT, ghT, "kadraj", Color.White, Color.Black, Modifier.pointerInput(so.id) {
                            var v0 = Offset(1f, 1f); var v = Offset(1f, 1f)
                            var taban = KatmanGecici(so.id, so.x, so.y, 1f, 0f)
                            detectDragGestures(onDragStart = {
                                v0 = koseV; v = v0
                                val s0 = soG
                                taban = katGecici?.takeIf { it.id == s0.id } ?: KatmanGecici(s0.id, s0.x, s0.y, 1f, 0f)
                            }) { ch, d ->
                                ch.consume()
                                val s0 = soG
                                if (katGecici?.id != s0.id) { taban = KatmanGecici(s0.id, s0.x, s0.y, 1f, 0f); v0 = v }   // ara kayıttan sonra yeniden çapala
                                v += d
                                val l0 = v0.getDistance().coerceAtLeast(1f)
                                val oran = v.getDistance() / l0
                                val aci = Math.toDegrees((kotlin.math.atan2(v.y, v.x) - kotlin.math.atan2(v0.y, v0.x)).toDouble()).toFloat()
                                if (oran.isFinite() && aci.isFinite())
                                    katGecici = taban.copy(carpan = (taban.carpan * oran).coerceIn(0.1f, 8f), aci = taban.aci + aci)
                            }
                        })
                    }
                    // hızlı düğmeler yazının üstünü örtmesin: yazı üst bölgedeyse altta (oynatıcı şeridinin üstünde) dursun
                    val ustte = cy > ghT * 0.35f
                    Row(Modifier.align(if (ustte) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = if (ustte) 6.dp else 48.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val (mn, mx) = if (so.tur == "metin") 0.02f to 0.3f else 0.03f to 1.5f
                        if (so.kilitli) HizliTus("Kilidi aç", "kapat") { vm.katmanDegis(so.id) { it.copy(kilitli = false) } }
                        else {
                            HizliTus("Küçült", "eksi") { vm.katmanDegis(so.id) { it.copy(olcek = (it.olcek * 0.9f).coerceIn(mn, mx)) } }
                            HizliTus("Büyüt", "ekle") { vm.katmanDegis(so.id) { it.copy(olcek = (it.olcek * 1.1f).coerceIn(mn, mx)) } }
                            HizliTus("Ortala", "h_odak") { vm.katmanDegis(so.id) { it.copy(x = 0.5f) } }
                            HizliTus("Düzelt", "yinele") { vm.katmanDegis(so.id) { it.copy(aci = 0f) } }
                            val i = p.katmanlar.indexOfFirst { it.id == so.id }
                            if (i in 0 until p.katmanlar.lastIndex) HizliTus("Öne al", "katman") { vm.katmanTasi(i, p.katmanlar.lastIndex) }
                            HizliTus("Kilitle", "kapat") { vm.katmanDegis(so.id) { it.copy(kilitli = true) }; vm.mesaj.value = "Kilitlendi (taşınmaz)" }
                        }
                    }
                }
            }
            // 3.74: Düzenle › Klip › Yüzler — duraklatılmışken fotoğrafın tamamı, yüz halkaları ve numaralı duraklar (dokun = ekle, sürükle = taşı)
            if (yuzPaneli && !oynuyorAkis && k != null && k.foto) YuzTuruKatmani(vm, p, k, Modifier.fillMaxSize())
            // 3.83: Düzenle › Klip › Yüz gizle — duraklatılmışken karenin tamamı, bulanık (seçili) ve kesik çizgili (seçilmemiş) ovaller
            if (gizlePaneli && !oynuyorAkis && k != null) YuzGizleKatmani(vm, k, Modifier.fillMaxSize())
        }
    }
}

/** 3.72: şablon öğesi o anda ekranda mı (dokununca ekranda düzenleme açılsın). */
internal fun sablonGorunur(p: Proje, tur: String, konum: Long): Boolean = when (tur) {
    "giris" -> p.giris.acik && Zaman.girisAralik(p).let { (a, b) -> konum in a..b }
    "abone" -> p.hatirlatici.acik && Zaman.hatirlaticiAralik(p).let { (a, b) -> konum in a..b }
    "kapanis" -> p.kapanisMs > 0 && konum >= p.klipMs
    else -> false
}

/** 4.08: seçili nesnenin çerçevesi (ekran pikseli): kesikli dikdörtgen + 4 köşe tutamacı (sürükle = büyüt/küçült) + ortada taşıma simgesi. */
@Composable
private fun NesneCerceve(sol: Float, ust: Float, w: Float, h: Float) {
    val yog = LocalDensity.current
    val tut = with(yog) { 9.dp.toPx() }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val kesik = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 9f), 0f)
        val tl = Offset(sol, ust); val sz = androidx.compose.ui.geometry.Size(w, h)
        drawRect(Color(0x99000000), tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.5f, pathEffect = kesik))
        drawRect(Color.White, tl, sz, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f, pathEffect = kesik))
        listOf(tl, Offset(sol + w, ust), Offset(sol, ust + h), Offset(sol + w, ust + h)).forEach { k ->
            drawCircle(Color(0x66000000), tut + 2.5f, k)
            drawCircle(Color.White, tut, k)
            drawCircle(Renk.turuncu, tut * 0.45f, k)
        }
    }
    val r = with(yog) { 15.dp.toPx() }
    if (w > r * 3 && h > r * 3) Box(Modifier.offset { IntOffset((sol + w / 2f - r).roundToInt(), (ust + h / 2f - r).roundToInt()) }.size(30.dp)
        .clip(CircleShape).background(Color(0x99000000)).border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        Simge("hareket", Color.White, 16.dp)
    }
}

/** 3.72: kesilen nesnenin parmakla geçici dönüşümü (normalleştirilmiş kayma + büyütme çarpanı). */
internal data class NesneGecici(val id: String, val dx: Float, val dy: Float, val carpan: Float)

/** 3.72: önizlemede kesilen nesne: dokun = seç · sürükle = taşı · iki parmak = büyüt/küçült. Bırakınca tek adımda kaydedilir (geri al çalışır).
 *  Nesneye değmeyen kısa dokunuş seçimi kaldırır. Arka plan (klip) bu modda kaymaz. */
@Composable
private fun NesneDokunma(vm: EditorDurum, p: Proje, konum: Long, olcek: Float, RW: Int, RH: Int, boyut: Map<String, Pair<Int, Int>>,
                         gecici: () -> NesneGecici?, geciciYaz: (NesneGecici?) -> Unit, ek: Modifier) {
    val pG by rememberUpdatedState(p); val konumG by rememberUpdatedState(konum)
    val yog = LocalDensity.current
    val payPx = with(yog) { 22.dp.toPx() } / olcek
    fun isabet(t: Offset): String? {
        val px = t.x / olcek; val py = t.y / olcek
        return pG.nesneler.lastOrNull { n ->
            if (konumG < n.basMs || konumG > n.sonMs) return@lastOrNull false
            val (bw, bh) = boyut[n.id] ?: return@lastOrNull false
            kotlin.math.abs(px - n.x * RW) <= bw / 2f + payPx && kotlin.math.abs(py - n.y * RH) <= bh / 2f + payPx
        }?.id
    }
    Box(Modifier.fillMaxSize().pointerInput(olcek, RW, RH) {
        awaitEachGesture {
            val ilk = awaitFirstDown(requireUnconsumed = false)
            // 4.08: seçili nesnenin köşe tutamacına basıldıysa tek parmakla büyüt/küçült (merkeze uzaklık oranı)
            val secN = vm.seciliNesne.value?.let { id -> pG.nesneler.firstOrNull { it.id == id } }?.takeIf { konumG in it.basMs..it.sonMs }
            val merkez = secN?.let { Offset(it.x * RW * olcek, it.y * RH * olcek) }
            val koseYakin = with(yog) { 28.dp.toPx() }
            val kose = secN != null && merkez != null && boyut[secN.id]?.let { (bw, bh) ->
                val hw = bw * olcek / 2f; val hh = bh * olcek / 2f
                listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(-hw, hh), Offset(hw, hh)).any { (merkez + it - ilk.position).getDistance() <= koseYakin }
            } == true
            if (kose && secN != null && merkez != null) {
                val d0 = (ilk.position - merkez).getDistance().coerceAtLeast(1f)
                ilk.consume()
                var degisti = false
                do {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.pressed } ?: break
                    val c = ((ch.position - merkez).getDistance() / d0).coerceIn(0.1f, 10f)
                    geciciYaz(NesneGecici(secN.id, 0f, 0f, c)); degisti = true
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
                if (degisti) gecici()?.takeIf { it.id == secN.id }?.let { g -> vm.nesneDegis(secN.id) { n -> n.copy(olcek = (n.olcek * g.carpan).coerceIn(0.02f, 3f)) } }
                geciciYaz(null)
                return@awaitEachGesture
            }
            val vurulan = isabet(ilk.position)
            val hedef = vurulan ?: vm.seciliNesne.value?.takeIf { id -> pG.nesneler.any { it.id == id } }
            var tasindi = false; var topPan = Offset.Zero; var parmak2 = false
            do {
                val ev = awaitPointerEvent()
                val zoom = ev.calculateZoom(); val pan = ev.calculatePan()
                if (ev.changes.count { it.pressed } > 1) parmak2 = true
                if (!tasindi) {
                    topPan += pan
                    // seçili nesne ekranın her yerinden sürüklenir (küçük nesne de rahat tutulur); iki parmak = büyüt/küçült
                    if (hedef != null && (parmak2 || topPan.getDistance() > viewConfiguration.touchSlop)) { tasindi = true; vm.seciliNesne.value = hedef }
                }
                if (tasindi && hedef != null && zoom.isFinite() && zoom > 0f && pan.x.isFinite() && pan.y.isFinite()) {
                    val g = gecici()?.takeIf { it.id == hedef } ?: NesneGecici(hedef, 0f, 0f, 1f)
                    geciciYaz(g.copy(dx = g.dx + pan.x / (olcek * RW), dy = g.dy + pan.y / (olcek * RH), carpan = (g.carpan * zoom).coerceIn(0.1f, 10f)))
                    ev.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (ev.changes.any { it.pressed })
            if (tasindi && hedef != null) {
                gecici()?.takeIf { it.id == hedef }?.let { g ->
                    vm.nesneDegis(hedef) { n -> n.copy(x = (n.x + g.dx).coerceIn(0f, 1f), y = (n.y + g.dy).coerceIn(0f, 1f), olcek = (n.olcek * g.carpan).coerceIn(0.02f, 3f)) }
                }
                geciciYaz(null)
            } else if (topPan.getDistance() <= viewConfiguration.touchSlop) {
                vm.seciliNesne.value = vurulan
            }
        }
    }.then(ek))
}

/** 3.66: önizlemedeki katman tutamacı (yuvarlak simge); merkez önizleme sınırlarının içine çekilir. */
@Composable
private fun KatmanTutamac(m: Offset, r: Float, gw: Float, gh: Float, simge: String, zemin: Color, renk: Color, ek: Modifier) {
    val x = m.x.coerceIn(r, (gw - r).coerceAtLeast(r)); val y = m.y.coerceIn(r, (gh - r).coerceAtLeast(r))
    Box(Modifier.offset { IntOffset((x - r).roundToInt(), (y - r).roundToInt()) }.size(30.dp).clip(CircleShape).background(zemin)
        .border(1.dp, Color(0x55000000), CircleShape).then(ek), contentAlignment = Alignment.Center) { Simge(simge, renk, 16.dp) }
}

/** 3.66: önizlemenin kenarındaki hızlı düğme (simge + küçük yazı). */
@Composable
private fun HizliTus(ad: String, simge: String, tik: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = tik).padding(horizontal = 6.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Simge(simge, Color.White, 16.dp)
        Text(ad, color = Color.White, fontSize = 9.5.sp, maxLines = 1)
    }
}

// ===================================================== ŞABLON =====================================================

/** Varsayılan ayarlar: hangi maddelerin uygulanacağı simgeli seçimlerle (dokun = dahil et / çıkar, seçim hatırlanır), altta tek uygula düğmesi.
 *  Görünüm 3.5 düzeninde: arka plansız, üstte simge, altta küçük yazı, alt menüyle aynı ton ailesi; seçili olan tam renk + nokta. */
@Composable
private fun VarsayilanSayfa(vm: EditorDurum) {
    val ctx = LocalContext.current
    var sec by remember { mutableStateOf(Varsayilan.secili(ctx)) }
    val mesgul by vm.mesgul.collectAsState()
    // 3.57: Geçişler paneli düzeni — yuvarlak simgeler, yana kayar (sıkışmaz); seçili = beyaz halka + ✓, dokun = dahil et / çıkar
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Varsayilan.MADDELER.forEachIndexed { i, m ->
            val on = m[0] in sec
            val ton = tonRenk(i, m[1])
            Column(Modifier.width(86.dp).clip(RoundedCornerShape(12.dp)).clickable { Varsayilan.degistir(ctx, m[0]); sec = Varsayilan.secili(ctx) }
                .padding(vertical = 4.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(if (on) Color.Transparent else Renk.kart2)
                        .border(if (on) 2.5.dp else 0.dp, if (on) Renk.yazi else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        Simge(m[2], if (on) ton else ton.copy(alpha = 0.45f), 26.dp)
                    }
                    if (on) OnayIsareti(Modifier.align(Alignment.TopEnd), 16.dp)
                }
                Text(m[1], color = if (on) Renk.yazi else Renk.soluk, fontSize = 11.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
                Text(m[3], color = Renk.soluk.copy(alpha = if (on) 0.9f else 0.5f), fontSize = 9.5.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 3, minLines = 3)
                // 4.08: ilgili bölüme giden bağlantı (seçimi değiştirmez; açıklamalar 3 satıra sabit → bağlantılar tek hizada)
                Varsayilan.BOLUM[m[0]]?.let { (b, h, a) ->
                    Text("Ayarla ›", color = ton, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).clickable {
                            if (h == "sablon" && a != null) vm.sablonSekme.value = a
                            vm.gitIstek.value = Triple(b, h, a)
                        }.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }
    Text("Dokun = dahil et / çıkar · yana kaydır", color = Renk.soluk, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    Spacer(Modifier.height(6.dp))
    val etkin = sec.isNotEmpty() && mesgul == null
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Renk.turuncu.copy(alpha = if (etkin) 0.85f else 0.3f), RoundedCornerShape(12.dp))
        .clickable(enabled = etkin) { vm.varsayilanUygula() }.padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Simge("sihir", if (etkin) Renk.turuncu else Renk.soluk, 18.dp); Spacer(Modifier.width(6.dp))
        Text(if (sec.isEmpty()) "Uygulanacak madde seç" else "Seçili ${sec.size} ayarı uygula", color = if (etkin) Renk.turuncu else Renk.soluk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Not("Her zaman: YouTube 16:9, fotoğraf 5 sn, bir yaklaş bir uzaklaş, 0,8 sn yumuşak geçiş, kadraj yüze/ana nesneye göre. Fotoğraf ve video seçince kendiliğinden uygulanır. Seçtiğin maddeler hatırlanır.")
}

@Composable
fun PanelSablon(vm: EditorDurum, p: Proje, alt: String? = null) {
    if (Duzen.yaziliMenu) PanelSablonEski(vm, p) else PanelSablonYeni(vm, p, alt)
}

/** 3.27: Şablon sayfası Geçişler paneli biçiminde — her bölüm kendi renk tonunda (Varsayılan ayarlardaki TON ailesi),
 *  seçenekler simge ve önizleme olarak, süreler − kaydırıcı + ile; özellik anahtarı bölüm başlığında. */
@Composable
private fun PanelSablonYeni(vm: EditorDurum, p: Proje, alt: String? = null) {
    val sari = Color(TON[0]); val yesil = Color(TON[1]); val turkuaz = Color(TON[2]); val turuncu = Color(TON[3]); val pembe = Color(TON[4])
    // 3.54: seçenekler tek sayfada yığılmasın — Renk bölümündeki gibi yatay alt düğmeler, her biri kendi sayfası (✓ = özellik açık)
    val sekmeIc by vm.sablonSekme.collectAsState()   // 3.69: dışarıdan da açılır (klip kartı › Giriş)
    // 3.70: alt sayfa üstteki 3. katman çiplerinden gelir (Şablon: giriş/abone/kapanış · Paket: paket/biçim · Hazır: varsayılan/kayıtlı); iç düğme satırı yalnız eski düzende
    val sekme = alt ?: sekmeIc
    if (alt == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AyarDugmesi("Paketler", "sihir", TON[3], sekme == "paket", false) { vm.sablonSekme.value = "paket" }
        AyarDugmesi("Giriş", "baslik", TON[0], sekme == "giris", p.giris.acik) { vm.sablonSekme.value = "giris" }
        AyarDugmesi("Abone", "zil", TON[4], sekme == "abone", p.hatirlatici.acik) { vm.sablonSekme.value = "abone" }
        AyarDugmesi("Kapanış", "kalp", TON[2], sekme == "kapanis", p.kapanis.acik) { vm.sablonSekme.value = "kapanis" }
        AyarDugmesi("Varsayılan", "uygula", TON[1], sekme == "varsayilan", false) { vm.sablonSekme.value = "varsayilan" }
    }
    if (alt == null) Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
    // 3.72: sayfa açılınca o öğe ekrana gelir (tam göründüğü ana gidilir); giriş başlığı boşsa konuya uygun örnek yazı yazılır
    LaunchedEffect(sekme) {
        val pp = vm.proje.value
        val acik = when (sekme) { "giris" -> pp.giris.acik; "abone" -> pp.hatirlatici.acik; "kapanis" -> pp.kapanis.acik; else -> false }
        if (acik) { vm.sablonGoster(sekme); if (sekme == "giris") vm.girisOneri() }
    }
    if (sekme == "bicim") Kart("Video biçimi", ton = turuncu) { BicimSatiri(vm, p) }
    if (sekme == "kayitli") KayitliAyarlarSayfasi(vm)
    if (sekme == "paket") Kart("Hazır paketler", ton = turuncu) {
        // 3.53: Biçim paketlerin ilk satırı · 3.71: Paket tek sayfa (Biçim + Paketler birlikte)
        Text("Biçim", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        BicimSatiri(vm, p)
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Renk.cizgi))
        Text("Paket", color = Renk.soluk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        TonSecici(listOf(Triple("youtube", "YouTube", "oynat"), Triple("reels", "Reels", "yildiz"), Triple("sinema", "Sinema", "kadraj")), "", ilkSira = 3,
            alt = listOf("alt bant · parlama · kapanış", "renkli bant · yakınlaşma", "sinematik · siyahtan")) { vm.paketUygula(it) }
        Not("Giriş, geçiş, abone ve kapanışı tek dokunuşla ayarlar. Klipler, müzik ve yazılar değişmez.")
    }
    val g = p.giris
    if (sekme == "giris") Kart("Giriş başlığı", ton = sari, anahtar = g.acik, anahtarDegis = { v -> vm.girisDegis { it.copy(acik = v) }; if (v) { vm.girisOneri(); vm.sablonGoster("giris") } }) {
        if (!g.acik) { Not("Kapalı. Açmak için başlıktaki anahtara dokun."); return@Kart }
        // 3.72: ekranda düzenle · konuya uygun başka örnek
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TonHap("Ekranda düzenle", "degistir", false, sari) { vm.sablonGoster("giris"); vm.ekranDuzen.value = "giris" }
            TonHap("Başka öneri", "sihir", false, turkuaz) { vm.girisOneri(zorla = true); vm.sablonGoster("giris") }
        }
        GirisBandi(vm, p, "KLASİK", GIRIS_KLASIK, sari)
        GirisBandi(vm, p, "REELS TARZI · şeritler kayarak gelir", Reels.STILLER, sari)
        // 3.58: kısa metin düzenleyici — Enter = alt satır (| yazmaya gerek yok), A−/A+ = yazı boyutu
        Spacer(Modifier.height(8.dp))
        MetinEditoru(g.baslik, "Başlık", { y -> vm.girisDegis { it.copy(baslik = y) } },
            boyut = g.olcek, boyutAralik = 0.6f..1.6f, boyutAdim = 0.1f, boyutDegis = { v -> vm.girisDegis { it.copy(olcek = v) } }, enCokSatir = 4)
        if (g.reels) {
            Spacer(Modifier.height(6.dp))
            MetinEditoru(g.bant, if (g.stil == "r_haber") "Tarih ve konum (her satır ayrı kutu)" else "Vurgu bantları (her satır ayrı bant)",
                { y -> vm.girisDegis { it.copy(bant = y) } }, enCokSatir = 3)
            Bolum("HAREKET")
            TonSecici(listOf(Triple("trend", "Trend", "sihir"), Triple("dus", "Düşerek", "asagi"), Triple("kay", "Kayarak", "sag"),
                Triple("yuksel", "Yükselerek", "disa"), Triple("buyu", "Büyüyerek", "g_buyu")), g.hareket) { s -> vm.girisDegis { it.copy(hareket = s) } }
            Bolum("KONUM VE EFEKT")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KonumSecici(g.konum, sari) { s -> vm.girisDegis { it.copy(konum = s) } }
                Spacer(Modifier.weight(1f))
                TonHap("Efekt sesi", "ses", g.efektSes, turkuaz) { vm.girisDegis { it.copy(efektSes = !it.efektSes) } }
                Spacer(Modifier.width(6.dp))
                TonHap("Boncuk", "yildiz", g.boncuk, pembe) { vm.girisDegis { it.copy(boncuk = !it.boncuk) } }
            }
            if (g.efektSes) TonKaydirici("Efekt sesi", g.sesSeviye, 0.1f..1f, 0.1f, turkuaz, { "%${(it * 100).roundToInt()}" }) { v -> vm.girisDegis { it.copy(sesSeviye = v) } }
        } else {
            var alt by remember(p.id) { mutableStateOf(g.alt) }
            LaunchedEffect(g.alt) { if (g.alt != alt) alt = g.alt }   // 3.72: ekranda düzenleme / öneri buraya da yansısın
            OutlinedTextField(alt, { alt = it; vm.girisDegis { gg -> gg.copy(alt = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("Alt başlık (yer, tarih…)") }, singleLine = true)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TonHap("Boncuk", "yildiz", g.boncuk, pembe) { vm.girisDegis { it.copy(boncuk = !it.boncuk) } }
            }
        }
        TonKaydirici("Ekranda", g.sureMs / 1000f, 2f..8f, 0.5f, sari, { String.format(TR, "%.1f sn", it) }) { v -> vm.girisDegis { it.copy(sureMs = (v * 1000).toLong()) } }
    }
    val h = p.hatirlatici
    if (sekme == "abone") Kart("Abone hatırlatıcı", ton = pembe, anahtar = h.acik, anahtarDegis = { v -> vm.hatirlaticiDegis { it.copy(acik = v) }; if (v) vm.sablonGoster("abone") }) {
        if (!h.acik) { Not("Kapalı. Açmak için başlıktaki anahtara dokun."); return@Kart }
        TonHap("Ekranda düzenle", "degistir", false, pembe) { vm.sablonGoster("abone"); vm.ekranDuzen.value = "abone" }
        Spacer(Modifier.height(6.dp))
        OnizlemeBandi(p, listOf("kart" to "Koyu hap", "klasik" to "Kırmızı bant"), h.stil, pembe, h.copy(stil = "") to p.logo,
            { s, W, H -> Cizim.hatirlatici(h.copy(acik = true, stil = s), W, H, p.logo, p.kapanis.kanal)?.let { yerlesimBmp(it, W, H) } }) { s -> vm.hatirlaticiDegis { it.copy(stil = s) }; vm.sablonGoster("abone") }
        var y by remember(p.id) { mutableStateOf(h.yazi) }
        LaunchedEffect(h.yazi) { if (h.yazi != y) y = h.yazi }   // 3.72: ekranda düzenleme buraya da yansısın
        OutlinedTextField(y, { y = it; vm.hatirlaticiDegis { hh -> hh.copy(yazi = it) } }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Yazı") }, singleLine = true)
        var kc by remember(p.id) { mutableStateOf(h.kucuk) }
        LaunchedEffect(h.kucuk) { if (h.kucuk != kc) kc = h.kucuk }
        OutlinedTextField(kc, { kc = it; vm.hatirlaticiDegis { hh -> hh.copy(kucuk = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Küçük yazı") }, singleLine = true)
        if (h.stil != "klasik") {
            var dg by remember(p.id) { mutableStateOf(h.dugme) }
            LaunchedEffect(h.dugme) { if (h.dugme != dg) dg = h.dugme }
            OutlinedTextField(dg, { dg = it; vm.hatirlaticiDegis { hh -> hh.copy(dugme = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Düğme yazısı") }, singleLine = true)
        }
        TonKaydirici("Zamanı", h.anOran, 0.1f..0.9f, 0.05f, pembe, { "%${(it * 100).roundToInt()}" }) { v -> vm.hatirlaticiDegis { it.copy(anOran = v) } }
    }
    val kp = p.kapanis
    if (sekme == "kapanis") Kart("Kapanış", ton = turkuaz, anahtar = kp.acik, anahtarDegis = { v -> vm.kapanisDegis { it.copy(acik = v) }; if (v) vm.sablonGoster("kapanis") }) {
        if (!kp.acik) { Not("Kapalı. Açmak için başlıktaki anahtara dokun."); return@Kart }
        TonHap("Ekranda düzenle", "degistir", false, turkuaz) { vm.sablonGoster("kapanis"); vm.ekranDuzen.value = "kapanis" }
        Spacer(Modifier.height(6.dp))
        OnizlemeBandi(p, listOf("cam" to "Cam kartlar", "cta" to "Renkli haplar", "sade" to "Sade"), kp.stil, turkuaz, kp.copy(stil = "") to p.logo,
            { s, W, H -> Cizim.kapanis(kp.copy(acik = true, stil = s), p.logo, W, H, null) }) { s -> vm.kapanisDegis { it.copy(stil = s) }; vm.sablonGoster("kapanis") }
        var kn by remember(p.id) { mutableStateOf(kp.kanal) }
        LaunchedEffect(kp.kanal) { if (kp.kanal != kn) kn = kp.kanal }
        OutlinedTextField(kn, { kn = it; vm.kapanisDegis(false) { k -> k.copy(kanal = it) } }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Kanal adı") }, singleLine = true)
        var yz by remember(p.id) { mutableStateOf(kp.yazi) }
        LaunchedEffect(kp.yazi) { if (kp.yazi != yz) yz = kp.yazi }
        OutlinedTextField(yz, { yz = it; vm.kapanisDegis(false) { k -> k.copy(yazi = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Yazı") }, singleLine = true)
        TonKaydirici("Süre", kp.sureMs / 1000f, 2f..8f, 0.5f, turkuaz, { String.format(TR, "%.1f sn", it) }) { v -> vm.kapanisDegis { it.copy(sureMs = (v * 1000).toLong()) } }
    }
    if (sekme == "varsayilan") Kart("Varsayılan ayarlar", ton = yesil) { VarsayilanSayfa(vm) }
}

@Composable
private fun Bolum(m: String) = Text(m, color = Renk.soluk.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

/** Varsayılan ayarlardaki seçici biçimi: renk tonlu simge + ad (+ küçük açıklama), seçilende renkli ve altında nokta. */
@Composable
internal fun TonSecici(liste: List<Triple<String, String, String>>, secili: String, ilkSira: Int = 0, alt: List<String>? = null, onSec: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        liste.forEachIndexed { i, (k, ad, simge) ->
            val on = secili == k
            val ton = tonRenk(i + ilkSira, ad)
            val r = if (on || secili.isEmpty()) ton else ton.copy(alpha = 0.55f)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { onSec(k) }.padding(vertical = 6.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Simge(simge, r, 22.dp)
                Text(ad, color = r, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                alt?.getOrNull(i)?.let { Text(it, color = Renk.soluk, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 10.5.sp, maxLines = 2) }
                Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (on) ton else Color.Transparent))
            }
        }
    }
}

/** Açık/kapalı küçük hap (renk tonlu). */
@Composable
internal fun TonHap(ad: String, simge: String, acik: Boolean, ton: Color, tik: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(if (acik) ton.copy(alpha = 0.18f) else Renk.kart2).clickable { tik() }.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Simge(simge, if (acik) ton else Renk.soluk, 15.dp); Spacer(Modifier.width(4.dp))
        Text(ad, color = if (acik) ton else Renk.soluk, fontSize = 11.5.sp, fontWeight = if (acik) FontWeight.Bold else FontWeight.Normal)
    }
}

/** Başlığın yeri: üç küçük kare (bant üstte / ortada / altta). */
@Composable
internal fun KonumSecici(secili: String, ton: Color, onSec: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ust" to "Üstte", "orta" to "Ortada", "alt" to "Altta").forEach { (k, ad) ->
            val on = secili == k
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSec(k) }.padding(2.dp)) {
                Box(Modifier.size(44.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(Renk.kart2).border(if (on) 2.dp else 0.dp, if (on) ton else Color.Transparent, RoundedCornerShape(6.dp))) {
                    Box(Modifier.align(when (k) { "ust" -> Alignment.TopCenter; "alt" -> Alignment.BottomCenter; else -> Alignment.Center })
                        .padding(horizontal = 6.dp, vertical = 5.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(2.dp)).background(if (on) ton else Renk.soluk.copy(alpha = 0.6f)))
                }
                Text(ad, color = if (on) ton else Renk.soluk, fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Geçişler panelindeki süre satırı: etiket · − · kaydırıcı · + · değer (renk tonlu). */
@Composable
internal fun TonKaydirici(etiket: String, deger: Float, aralik: ClosedFloatingPointRange<Float>, adim: Float, ton: Color, yaz: (Float) -> String, bitti: (Float) -> Unit) {
    var v by remember(deger) { mutableFloatStateOf(deger) }
    fun ayarla(x: Float) { val y = (Math.round(x / adim) * adim).coerceIn(aralik.start, aralik.endInclusive); v = y; bitti(y) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiket, color = Renk.soluk, fontSize = 12.5.sp, maxLines = 1, modifier = Modifier.width(64.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { ayarla(v - adim) }, contentAlignment = Alignment.Center) { Simge("eksi", Renk.yazi, 15.dp) }
        InceSlider(value = v, onValueChange = { v = it }, valueRange = aralik, onValueChangeFinished = { ayarla(v) }, renk = ton,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(thumbColor = ton, activeTrackColor = ton, inactiveTrackColor = Renk.cizgi))
        Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { ayarla(v + adim) }, contentAlignment = Alignment.Center) { Simge("ekle", Renk.yazi, 15.dp) }
        Text(yaz(v), color = ton, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
    }
}

/** Stil önizleme bandı (sağa kayar): her kutu ilk klibin resmi üstünde o stilin çizimi; seçili renk tonlu halkalı. */
@Composable
private fun OnizlemeBandi(p: Proje, liste: List<Pair<String, String>>, secili: String, ton: Color, anahtar: Any?, ciz: (String, Int, Int) -> Bitmap?, onSec: (String) -> Unit) {
    val (oW, oH) = Disa.boyut(p.bicim, 360)
    val genDp = if (oW > oH) 128.dp else if (oW == oH) 92.dp else 66.dp
    val yukDp = genDp * (oH.toFloat() / oW)
    val ilk = p.klipler.firstOrNull()
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(liste.size) { n ->
            val (k, ad) = liste[n]
            val on = secili == k
            val bmp by produceState<Bitmap?>(null, k, anahtar, p.bicim) { value = withContext(Dispatchers.Default) { runCatching { ciz(k, oW, oH) }.getOrNull() } }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(genDp + 6.dp).clip(RoundedCornerShape(12.dp))
                .clickable { onSec(k) }.padding(vertical = 2.dp)) {
                Box(Modifier.size(genDp, yukDp).clip(RoundedCornerShape(10.dp)).background(Renk.kart2)
                    .border(if (on) 2.5.dp else 0.5.dp, if (on) ton else Renk.cizgi, RoundedCornerShape(10.dp))) {
                    if (ilk != null) KlipResmi(ilk, Modifier.fillMaxSize().alpha(0.85f))
                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
                }
                Text(ad, color = if (on) ton else Renk.soluk, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Yerlesim'i W×H saydam bitmap üstüne, videodaki yerinde çizer. */
private fun yerlesimBmp(y: Yerlesim, W: Int, H: Int): Bitmap {
    val o = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val (x0, y0) = y.solUst(W, H)
    android.graphics.Canvas(o).drawBitmap(y.bmp, x0, y0, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = (y.alfa * 255).toInt().coerceIn(0, 255) })
    return o
}

@Composable
private fun PanelSablonEski(vm: EditorDurum, p: Proje) {
    Kart("Varsayılan ayarlar") { VarsayilanSayfa(vm) }
    Kart("Hazır paketler") {
        Not("Giriş başlığı, geçiş, abone hatırlatıcı ve kapanışı tek dokunuşla ayarlar. Klipler, müzik ve yazılar değişmez.")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecimKarti("YouTube", "alt bant · beyaz\nparlama · kapanış", false, Modifier.weight(1f)) { vm.paketUygula("youtube") }
            SecimKarti("Reels", "renkli bant ·\nyakınlaşma", false, Modifier.weight(1f)) { vm.paketUygula("reels") }
            SecimKarti("Sinema", "sinematik ·\nsiyahtan", false, Modifier.weight(1f)) { vm.paketUygula("sinema") }
        }
    }
    val g = p.giris
    Kart("Giriş başlığı") {
        AnahtarSatiri("Videonun başında başlık göster", g.acik) { v -> vm.girisDegis { it.copy(acik = v) } }
        if (g.acik) {
            if (Duzen.yaziliMenu) {   // önceki görünüm (⋯ › yazılı menü)
                Text("Klasik", color = Renk.soluk, fontSize = 12.5.sp)
                Secenekler(GIRIS_KLASIK, g.stil) { s -> vm.girisDegis { it.copy(stil = s) } }
                Text("Reels tarzı · şeritler sırayla kayarak gelir", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                Secenekler(Reels.STILLER, g.stil) { s -> vm.girisDegis { it.copy(stil = s) } }
            } else {
                GirisBandi(vm, p, "KLASİK", GIRIS_KLASIK)
                GirisBandi(vm, p, "REELS TARZI · şeritler kayarak gelir", Reels.STILLER)
            }
            var bas by remember(p.id) { mutableStateOf(g.baslik) }
            OutlinedTextField(bas, { bas = it; vm.girisDegis { gg -> gg.copy(baslik = it) } }, Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("Başlık (satır bölmek için |)") }, maxLines = 2)
            if (g.reels) {
                var bant by remember(p.id) { mutableStateOf(g.bant) }
                OutlinedTextField(bant, { bant = it; vm.girisDegis { gg -> gg.copy(bant = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text(if (g.stil == "r_haber") "Tarih | konum (ör. 26 Eylül 2026|Kabataş, Ordu)" else "Vurgu bantları (| ile ayrı bant)") }, singleLine = true)
                Text("Giriş hareketi", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                Secenekler(listOf("trend" to "Trend", "dus" to "Düşerek", "kay" to "Kayarak", "yuksel" to "Yükselerek", "buyu" to "Büyüyerek"), g.hareket) { s -> vm.girisDegis { it.copy(hareket = s) } }
                Secenekler(listOf("ust" to "Üstte", "orta" to "Ortada", "alt" to "Altta"), g.konum) { s -> vm.girisDegis { it.copy(konum = s) } }
                AnahtarSatiri("Efekt sesleri (vuuş · tok · pop)", g.efektSes) { v -> vm.girisDegis { it.copy(efektSes = v) } }
                if (g.efektSes) Kaydirici("Efekt sesi", g.sesSeviye, 0.1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> vm.girisDegis { it.copy(sesSeviye = v) } }
            } else {
                var alt by remember(p.id) { mutableStateOf(g.alt) }
                OutlinedTextField(alt, { alt = it; vm.girisDegis { gg -> gg.copy(alt = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp),
                    label = { Text("Alt başlık (yer, tarih…)") }, singleLine = true)
            }
            AnahtarSatiri("Işıklı boncuklar (girişte süzülen ışıklar)", g.boncuk) { v -> vm.girisDegis { it.copy(boncuk = v) } }
            Kaydirici("Ekranda kalma", g.sureMs / 1000f, 2f..8f, { String.format(TR, "%.1f sn", it) }) { v -> vm.girisDegis { it.copy(sureMs = (v * 1000).toLong()) } }
        }
    }
    val h = p.hatirlatici
    Kart("Abone hatırlatıcı", baslangicAcik = h.acik) {
        AnahtarSatiri("Videoda “Abone ol” göster (4 sn)", h.acik) { v -> vm.hatirlaticiDegis { it.copy(acik = v) } }
        if (h.acik) {
            Secenekler(listOf("kart" to "Koyu hap · ABONE OL", "klasik" to "Kırmızı bant (önceki)"), h.stil, sutun = 2) { s -> vm.hatirlaticiDegis { it.copy(stil = s) } }
            var y by remember(p.id) { mutableStateOf(h.yazi) }
            OutlinedTextField(y, { y = it; vm.hatirlaticiDegis { hh -> hh.copy(yazi = it) } }, Modifier.fillMaxWidth(), label = { Text("Yazı") }, singleLine = true)
            var kc by remember(p.id) { mutableStateOf(h.kucuk) }
            OutlinedTextField(kc, { kc = it; vm.hatirlaticiDegis { hh -> hh.copy(kucuk = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Küçük yazı") }, singleLine = true)
            if (h.stil != "klasik") {
                var dg by remember(p.id) { mutableStateOf(h.dugme) }
                OutlinedTextField(dg, { dg = it; vm.hatirlaticiDegis { hh -> hh.copy(dugme = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Düğme yazısı") }, singleLine = true)
                Not("Logo eklediysen solda yuvarlak görünür; yoksa kanal adının baş harfi.")
            }
            Kaydirici("Videonun neresinde", h.anOran, 0.1f..0.9f, { "%${(it * 100).roundToInt()}" }) { v -> vm.hatirlaticiDegis { it.copy(anOran = v) } }
        }
    }
    val kp = p.kapanis
    Kart("Kapanış", baslangicAcik = kp.acik) {
        AnahtarSatiri("Videonun sonuna kapanış kartı ekle (aydınlık)", kp.acik) { v -> vm.kapanisDegis { it.copy(acik = v) } }
        if (kp.acik) {
            Secenekler(listOf("cam" to "Cam kartlar", "cta" to "Renkli haplar", "sade" to "Sade"), kp.stil) { s -> vm.kapanisDegis { it.copy(stil = s) } }
            var kn by remember(p.id) { mutableStateOf(kp.kanal) }
            OutlinedTextField(kn, { kn = it; vm.kapanisDegis(false) { k -> k.copy(kanal = it) } }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Kanal adı") }, singleLine = true)
            var yz by remember(p.id) { mutableStateOf(kp.yazi) }
            OutlinedTextField(yz, { yz = it; vm.kapanisDegis(false) { k -> k.copy(yazi = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Yazı") }, singleLine = true)
            Kaydirici("Süre", kp.sureMs / 1000f, 2f..8f, { String.format(TR, "%.1f sn", it) }) { v -> vm.kapanisDegis { it.copy(sureMs = (v * 1000).toLong()) } }
            Not("Logo eklediysen kartın ortasında yuvarlak olarak görünür. Kart karanlık değildir.")
        }
    }
}

// ===================================================== GEÇİŞ =====================================================

@Composable
fun PanelGecis(vm: EditorDurum, p: Proje) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Renk.kart).padding(vertical = 6.dp)) {
        GecisIcerik(vm, p, ara = null, kapat = null)
        Box(Modifier.padding(horizontal = 14.dp)) {
            AnahtarSatiri("Videoyu siyahtan açarak başlat", p.gecis.siyahtanAc) { v -> vm.gecisDegis { it.copy(siyahtanAc = v) } }
            // 3.82: logo geçişi — 15. saniye · orta · bitime 15 sn kala, klip arasında logo ekrana doğru büyür (2,8 sn).
            // Köşe logosundan bağımsız (köşe logosu kaldırılsa da kalır); Tümüne uygula / standart geçişler bunu değiştirmez.
            // 3.88: logo geçişinin bütün ayarları Yayın › Logo'da (aynı iş iki yerde olmasın); burada yalnız durum
            val nL = p.logoGecisAnlari().size
            Text(if (p.gecis.logo) "Logo geçişi açık · $nL yerde (ayarları: Yayın › Logo › Büyük logo geçişi)" else "Logo geçişi: Yayın › Logo › Büyük logo geçişi",
                color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
    }
}

private val GIRIS_KLASIK = listOf("sinematik" to "Sinematik", "altbant" to "Alt bant", "kutulu" to "Kutulu", "neon" to "Neon", "bantlar" to "Renkli bant", "minimal" to "Sade")

/** Giriş başlığı stilleri (3.26, Geçişler paneli biçiminde): sağa kayan bant; her stil ilk klibin resmi üstünde kendi
 *  başlığınla önizlenir, seçili olan beyaz halkalı, adı altında. */
@Composable
private fun GirisBandi(vm: EditorDurum, p: Proje, baslik: String, liste: List<Pair<String, String>>, ton: Color = Renk.yazi) {
    val g = p.giris
    val metin = g.baslik.ifBlank { "Başlığın burada" }
    Bolum(baslik)
    OnizlemeBandi(p, liste, g.stil, ton, listOf(metin, g.bant, g.alt, g.konum),
        { k, W, H -> girisOrnek(g.copy(acik = true, stil = k, baslik = metin), W, H) }) { k ->
        // 3.72: stile dokun = başlık ekrana gelir (tam göründüğü an), boşsa konuya uygun örnek yazı; yazıya dokunup ekranda düzenlenir
        vm.girisDegis { it.copy(stil = k) }; vm.girisOneri(); vm.sablonGoster("giris")
    }
}

/** Giriş başlığının son (yerleşmiş) hâli, oW×oH saydam bitmap üstünde — videodaki konum ve boyutla. */
private fun girisOrnek(g: Giris, W: Int, H: Int): Bitmap? {
    val o = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(o)
    if (g.reels) {
        val ss = Reels.seritler(g, W, H)
        if (ss.isEmpty()) return null
        for (sr in ss) {
            val px = (sr.cx + 1f) / 2f * W - sr.bmp.width / 2f
            val py = (1f - sr.cy) / 2f * H - sr.bmp.height / 2f
            c.drawBitmap(sr.bmp, px, py, null)
        }
    } else {
        val y = Cizim.giris(g, W, H) ?: return null
        val (x0, y0) = y.solUst(W, H)
        c.drawBitmap(y.bmp, x0, y0, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply { alpha = (y.alfa * 255).toInt().coerceIn(0, 255) })
    }
    return o
}

/** 3.59: popüler geçişler (klip sınırında, önizlemeli küçük resimlerle). */
internal val GECIS_POPULER = listOf("dondur" to "Döndür", "bulanik" to "Bulanık", "silme" to "Silme", "dalis" to "Dalış", "sizma" to "Işık sızması")

/** Geçiş türleri (3.25): (anahtar, ad, simge, renk). Üst üste binenler TEMEL'de, klip sınırındakiler IŞIK VE HAREKET'te. */
internal val GECIS_TEMEL = listOf(
    Triple("yok", "Yok", "g_yok") to 0xFFE6EDF3, Triple("erime", "Erime", "g_erime") to 0xFFF5A524,
    Triple("capraz", "Yumuşak", "g_capraz") to 0xFF5DCAA5, Triple("kaydir", "Kaydır", "g_kaydir") to 0xFF85B7EB,
)
/** 3.39: kesme hissi vermeyen akıcı geçişler. */
internal val GECIS_AKICI = listOf(
    Triple("hafif", "Hafif erime", "g_hafif") to 0xFFD9CF5A, Triple("akis", "Akış", "g_akis") to 0xFF3FBFAE,
)
private val GECIS_ISIK = listOf(
    Triple("yakincapraz", "Büyüyerek", "g_buyu") to 0xFFED93B1, Triple("siyah", "Siyahtan", "g_siyah") to 0xFFB4B2A9,
    Triple("beyaz", "Parlama", "g_beyaz") to 0xFFFAC775, Triple("yakin", "Yakınlaşma", "g_yakin") to 0xFFAFA9EC,
)

/** Geçişler paneli (Ahmet'in görseli, 14.11.2026): solda "Tümüne", ortada başlık, sağda ✓; altında süre (− kaydırıcı +),
 *  sonra yuvarlak simgeli türler. ara = klipler arasındaki simgeden açıldıysa o ara (i → i+1); null = projenin geçişi. */
@Composable
fun GecisIcerik(vm: EditorDurum, p: Proje, ara: Int?, kapat: (() -> Unit)?) {
    val simdiki = if (ara != null) p.gecisAt(ara).tur else p.gecis.tur
    var sec by remember(ara, simdiki) { mutableStateOf(simdiki) }
    fun uygula(t: String) {
        sec = t
        if (ara == null) vm.gecisDegis { it.copy(tur = t) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { vm.gecisHepsine(sec); kapat?.invoke() }.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Simge("cift", Renk.yazi, 26.dp)
                Text("Tümüne", color = Renk.soluk, fontSize = 10.5.sp)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Geçişler", color = Renk.yazi.copy(alpha = 0.8f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(if (ara != null) "Klip ${ara + 1} → ${ara + 2}" else "Bütün klipler", color = Renk.soluk, fontSize = 11.sp)
            }
            if (kapat != null) Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    if (ara != null) vm.klipGecis(ara, if (sec == p.gecis.tur) null else sec)
                    kapat()
                }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Simge("uygula", Renk.yazi, 26.dp)
                Text("Tamam", color = Renk.soluk, fontSize = 10.5.sp)
            } else Spacer(Modifier.width(48.dp))
        }
        // süre: standart 0,8 sn (Kareden Videoya "YouTube anı videosu"), 0,2–2,0 sn, ± 0,1 sn
        var sn by remember(p.gecis.sureMs) { mutableFloatStateOf(p.gecis.sureMs / 1000f) }
        fun sureYaz(v: Float) { val y = (Math.round(v * 10f) / 10f).coerceIn(0.2f, 2f); sn = y; vm.gecisDegis { it.copy(sureMs = (y * 1000).toLong()) } }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Süre", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.width(38.dp))
            Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { sureYaz(sn - 0.1f) }, contentAlignment = Alignment.Center) { Simge("eksi", Renk.yazi, 16.dp) }
            InceSlider(value = sn, onValueChange = { sn = (Math.round(it * 10f) / 10f) }, valueRange = 0.2f..2f,
                onValueChangeFinished = { sureYaz(sn) }, modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
            Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, Renk.cizgi, CircleShape).clickable { sureYaz(sn + 0.1f) }, contentAlignment = Alignment.Center) { Simge("ekle", Renk.yazi, 16.dp) }
            Text(String.format(TR, "%.1f sn", sn), color = Renk.turuncu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End, modifier = Modifier.width(56.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
        // 3.59: kategori hapları (Popüler · Temel · Akıcı · Işık); Popüler'de küçük resimler üstteki videonun o anki karesinden
        var kategori by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (sec in GECIS_POPULER.map { it.first }) "pop" else if (sec == "yok" || GECIS_TEMEL.any { it.first.first == sec }) "temel" else if (GECIS_AKICI.any { it.first.first == sec }) "akici" else if (GECIS_ISIK.any { it.first.first == sec }) "isik" else "pop") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("pop" to "Popüler", "temel" to "Temel", "akici" to "Akıcı", "isik" to "Işık ve hareket").forEach { (kk, ad) ->
                val on = kategori == kk
                Text(ad, color = if (on) Renk.turuncuM else Renk.soluk, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Renk.turuncu else Color.Transparent)
                        .border(1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(14.dp)).clickable { kategori = kk }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
        if (kategori == "pop") {
            val secK by vm.secili.collectAsState()
            val kare = AnlikKare(vm, p, if (ara != null) ara else secK)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OnizlemeKarti("Yok", sec == "yok", Renk.turuncu, { kare }, kare) { uygula("yok") }
                GECIS_POPULER.forEach { (t, ad) ->
                    OnizlemeKarti(ad, sec == t, Renk.turuncu, { kare?.let { Efektler.gecisOrnek(it, t) } }, kare to t) { uygula(t) }
                }
            }
        }
        listOf(Triple("temel", "TEMEL", GECIS_TEMEL), Triple("akici", "AKICI", GECIS_AKICI), Triple("isik", "IŞIK VE HAREKET", GECIS_ISIK)).filter { it.first == kategori }.forEach { (_, baslik, liste) ->
            Text(baslik, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (liste.size >= 4) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp)) {
                liste.forEach { (t, renk) ->
                    val (k, ad, simge) = t
                    val secili = sec == k
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { uygula(k) }.padding(vertical = 2.dp)) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(if (secili) Color.Transparent else Renk.kart2)
                            .border(if (secili) 2.5.dp else 0.dp, if (secili) Renk.yazi else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) { Simge(simge, Color(renk), 26.dp) }
                        Text(ad, color = if (secili) Renk.yazi else Renk.soluk, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Text(if (sec in Gecisler.YENI_KENAR) "Klip sınırında uygulanır: giden klip sonunda, gelen klip başında; süreyi yukarıdan ayarla. Denemek için oynatıcıyı iki klibin birleştiği yerin hemen öncesine getirip oynat."
            else if (sec == "akis") "Giden kare hafifçe yaklaşırken gelen kare yumuşakça yerine oturur; ortada solma yok, kesme hissi vermez."
            else if (sec == "hafif") "Sakin, yumuşak kenarlı bir erime; dokusu Erime'den hafif."
            else if (Gecis(sec).cakisma) "İki klip geçiş süresince birlikte oynar; giden klip gelenin üstünde kaybolur."
            else if (sec == "yok") "Klipler doğrudan kesilerek birleşir." else "Geçiş her klip sınırında uygulanır; videonun sonu karartılmaz.",
            color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ===================================================== ALTYAZI =====================================================

@Composable
fun PanelAltyazi(vm: EditorDurum, p: Proje, alt: String? = null) {
    val ctx = LocalContext.current
    val yukle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.srtYukle(it) } }
    val kaydet = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { u ->
        if (u != null) try {
            ctx.contentResolver.openOutputStream(u, "w")?.use { it.write(vm.srt().toByteArray(Charsets.UTF_8)) }
            vm.mesaj.value = "SRT kaydedildi"
        } catch (e: Exception) { vm.uyari.value = "SRT kaydedilemedi: ${e.message}" }
    }
    var silOnay by remember { mutableStateOf(false) }
    val oto by vm.altyaziIlerleme.collectAsState()
    var kaynak by remember { mutableStateOf(if (p.sesler.isNotEmpty()) "ses" else "klip") }
    // 3.55: dört kart tek sayfada yığılmasın — alt düğmeler (✓ = o bölümde içerik/ayar var)
    var sekmeIc by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(if (p.altyazilar.isEmpty()) "oto" else "satir") }
    val sekme = alt ?: sekmeIc   // 3.70: yeni düzende 3. katman çiplerinden
    if (alt == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AyarDugmesi("Otomatik", "sihir", TON[3], sekme == "oto", false) { sekmeIc = "oto" }
        AyarDugmesi("Satırlar", "altyazi", TON[0], sekme == "satir", p.altyazilar.isNotEmpty()) { sekmeIc = "satir" }
        AyarDugmesi("Toplu düzelt", "degistir", TON[4], sekme == "duzelt", false) { sekmeIc = "duzelt" }
        AyarDugmesi("Görünüm", "gorunum", TON[2], sekme == "gorunum", false) { sekmeIc = "gorunum" }
    }
    var oncekiSay by remember { mutableIntStateOf(p.altyazilar.size) }
    LaunchedEffect(p.altyazilar.size) {
        if (oncekiSay == 0 && p.altyazilar.isNotEmpty() && sekme == "oto") { if (alt == null) sekmeIc = "satir" else AltHafiza.sec("altyazi", "satir") }
        oncekiSay = p.altyazilar.size
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
    if (sekme == "duzelt" && p.altyazilar.isEmpty()) Not("Önce altyazı oluştur ya da SRT yükle; sonra bütün satırları burada tek metin olarak düzeltebilirsin.")
    if (sekme == "oto") Kart("Otomatik altyazı") {
        val o = oto
        if (o != null) {
            Text("${o.first} · %${(o.second * 100).roundToInt()}", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { o.second }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
            Dugme("İptal", Modifier.fillMaxWidth()) { vm.altyaziIptal() }
        } else {
            Secenekler(listOf("klip" to "Videoların sesi", "ses" to "Seslendirmem"), kaynak, sutun = 2) { kaynak = it }
            Dugme("▶ Altyazıyı oluştur", Modifier.fillMaxWidth().padding(top = 6.dp), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.altyaziOtomatik(kaynak) }
            Not("Konuşma telefonun içinde yazıya dökülür; ses dışarı gitmez. İlk kullanımda Türkçe model (~35 MB) bir kez iner, Wi-Fi önerilir. " +
                "Kelime zamanları tutulur: “Kelime vurgulu” görünümde o an söylenen kelime renklenir. Elle yazdığın satırlar silinmez.")
        }
    }
    // 3.49: tanıma hatalarını toplu düzelt — tümünü kopyala, bir yapay zekâya düzelttir, yapıştır ve uygula
    if (sekme == "duzelt" && p.altyazilar.isNotEmpty()) Kart("Toplu düzelt (kopyala · yapıştır)") {
        val pano = androidx.compose.ui.platform.LocalClipboardManager.current
        var metin by remember(p.altyazilar.size) { mutableStateOf(p.altyazilar.joinToString("\n") { it.metin.replace('\n', ' ') }) }
        Not("Her satır bir altyazı kutusudur (zamanları korunur). \"Kopyala\" → bir yapay zekâya \"satır sayısını ve sırasını koruyarak yazım hatalarını düzelt\" de → düzeltilmiş metni kopyala → \"Yapıştır\" → \"Uygula\".")
        OutlinedTextField(metin, { metin = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 260.dp).padding(top = 4.dp),
            label = { Text("${metin.lines().count { it.isNotBlank() }} satır · ${p.altyazilar.size} altyazı") })
        DugmeSirasi(
            "Kopyala" to { pano.setText(androidx.compose.ui.text.AnnotatedString(metin)); vm.mesaj.value = "Altyazılar kopyalandı" },
            "Yapıştır" to { pano.getText()?.text?.let { metin = it } ?: run { vm.mesaj.value = "Panoda metin yok" } },
            "Uygula" to { vm.altyaziTopluYaz(metin) },
        )
    }
    if (sekme == "satir") Kart("Altyazı satırları (${p.altyazilar.size})") {
        DugmeSirasi("＋ Şu anda ekle" to { vm.altyaziEkle() }, "Her klibe satır" to { vm.altyaziHerKlibe() })
        DugmeSirasi("SRT yükle" to { yukle.launch(arrayOf("*/*")) }, "SRT kaydet" to { kaydet.launch(p.ad.take(40) + ".srt") },
            (if (silOnay) "Emin misin?" else "Tümünü sil") to { if (silOnay) { vm.altyaziTumunuSil(); silOnay = false } else silOnay = true })
        Not("Önizlemede istediğin ana gel, “Şu anda ekle”ye dokun (3 sn'lik satır). Otomatik konuşma-yazı bu sürümde yok.")
        p.altyazilar.forEach { a ->
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Renk.kart2).padding(10.dp)) {
                Text("${sureOndalik(a.basMs)} → ${sureOndalik(a.sonMs)}", color = Renk.turuncu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                var m by remember(a.id) { mutableStateOf(a.metin) }
                OutlinedTextField(m, { m = it; vm.altyaziDegis(a.id) { s -> s.copy(metin = it) } }, Modifier.fillMaxWidth(), maxLines = 3, placeholder = { Text("Altyazı") })
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dugme("Başı = şu an", Modifier.weight(1f), renk = Renk.kart) { val t = vm.anlik(); vm.altyaziDegis(a.id) { it.copy(basMs = t, sonMs = maxOf(it.sonMs, t + 300)) } }
                    Dugme("Sonu = şu an", Modifier.weight(1f), renk = Renk.kart) { val t = vm.anlik(); vm.altyaziDegis(a.id) { it.copy(sonMs = maxOf(t, it.basMs + 300)) } }
                    Dugme("Git", Modifier.weight(0.6f), renk = Renk.kart) { vm.git(a.basMs) }
                    Dugme("Sil", Modifier.weight(0.6f), renk = Renk.kart) { vm.altyaziSil(a.id) }
                }
            }
        }
    }
    val st = p.altyaziStil
    if (sekme == "gorunum") Kart("Altyazı görünümü") {
        AnahtarSatiri("Altyazıları videoya yaz", st.acik) { v -> vm.altyaziStilDegis { it.copy(acik = v) } }
        Secenekler(listOf("bant" to "İnce siyah bant", "kutu" to "Koyu kutu", "kenarli" to "Kenarlı", "sari" to "Sarı kenarlı", "kisa" to "Kelime vurgulu"), st.stil, sutun = 2) { s -> vm.altyaziStilDegis { it.copy(stil = s) } }
        if (st.stil == "kisa") Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0xFFFFD400, 0xFF35E06B, 0xFF27D3FF, 0xFFFF5FA2, 0xFFFFFFFF).forEach { r ->
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(r))
                    .border(if (st.vurgu == r) 2.dp else 0.5.dp, if (st.vurgu == r) Color.White else Renk.cizgi, RoundedCornerShape(9.dp))
                    .clickable { vm.altyaziStilDegis { it.copy(vurgu = r) } })
            }
        }
        Secenekler(listOf("alt" to "Altta", "orta" to "Ortada", "ust" to "Üstte"), st.konum) { s -> vm.altyaziStilDegis { it.copy(konum = s) } }
        Kaydirici("Yazı boyutu", st.boyut, 0.03f..0.09f, { "%${(it * 100).roundToInt()}" }) { v -> vm.altyaziStilDegis { it.copy(boyut = v) } }
    }
}

// ===================================================== KAPAK =====================================================

internal val VURGULAR = listOf(0xFFFFD400 to "Sarı", 0xFFFF3B30 to "Kırmızı", 0xFF35E06B to "Yeşil", 0xFF27D3FF to "Mavi", 0xFFFF8A00 to "Turuncu")

@Composable
fun PanelKapak(vm: EditorDurum, p: Proje, alt: String? = null) {
    // 3.58: Kapak yeniden — Geçişler/Renk düzeni: üstte büyük kapak önizlemesi + Kaydet, altında görsel alt düğmeler
    // (Kare · Yazı · Stil · Efekt), her biri kendi sayfası; stiller yazı yerine kapağın kendisiyle çizilmiş küçük resimler.
    val resim by vm.kapakResmi.collectAsState()
    val oneriler by vm.kapakOnerileri.collectAsState()
    val hazirlaniyor by vm.kapakOneriHazirlaniyor.collectAsState()
    LaunchedEffect(Unit) { vm.kapakCiz() }
    val kp = p.kapak
    val sari = Color(TON[0]); val yesil = Color(TON[1]); val turkuaz = Color(TON[2]); val turuncu = Color(TON[3]); val pembe = Color(TON[4])
    val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth(if (oran < 1f) 0.5f else 1f).align(Alignment.CenterHorizontally).aspectRatio(oran).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
            resim?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(Renk.turuncu).clickable { vm.kapakKaydet() }.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Simge("kaydet", Renk.turuncuM, 18.dp); Spacer(Modifier.width(6.dp))
            Text("Kapağı JPG kaydet", color = Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
    var sekmeIc by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("kare") }
    val sekme = alt ?: sekmeIc   // 3.70: yeni düzende 3. katman çiplerinden
    if (alt == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AyarDugmesi("Kare", "kapak", TON[2], sekme == "kare", kp.kaynakUri.isNotEmpty()) { sekmeIc = "kare" }
        AyarDugmesi("Yazı", "metin", TON[0], sekme == "yazi", kp.yazi.isNotBlank()) { sekmeIc = "yazi" }
        AyarDugmesi("Stil", "sihir", TON[3], sekme == "stil", false) { sekmeIc = "stil" }
        AyarDugmesi("Efekt", "yildiz", TON[4], sekme == "efekt", kp.canli || kp.logo) { sekmeIc = "efekt" }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Renk.cizgi))
    Spacer(Modifier.height(8.dp))
    when (sekme) {
        "kare" -> {
            Bolum("KAPAK KARESİ")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                YuvarlakSecim("Önizlemedeki kare", "kapak", turkuaz, false) { vm.kapakKareSec() }
                YuvarlakSecim(if (hazirlaniyor) "Hazırlanıyor…" else "Öneriler", "sihir", turuncu, false) { vm.kapakOneriHazirla() }
            }
            if (oneriler.isNotEmpty()) {
                Bolum("ÖNERİLER · dokun = seç")
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(oneriler.size) { n ->
                        val (k, b) = oneriler[n]
                        val on = k.kaynakUri == kp.kaynakUri && k.kaynakMs == kp.kaynakMs
                        Image(b.asImageBitmap(), null, Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
                            .border(if (on) 2.5.dp else 0.5.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(10.dp))
                            .clickable { vm.kapakOneriSec(k) }, contentScale = ContentScale.Crop)
                    }
                }
            } else Not("Oynatıcıyı istediğin ana getirip \"Önizlemedeki kare\"ye dokun ya da \"Öneriler\" ile en canlı kareleri getir.")
        }
        "yazi" -> {
            MetinEditoru(kp.yazi, "Kapak yazısı", { y -> vm.kapakDegis { it.copy(yazi = y) } }, vurguVar = true, vurguRenk = Color(kp.vurgu),
                renkler = VURGULAR.map { it.first }, seciliRenk = kp.vurgu, renkSec = { r -> vm.kapakDegis { it.copy(vurgu = r) } },
                boyut = kp.boyut, boyutAralik = 0.06f..0.18f, boyutDegis = { v -> vm.kapakDegis { it.copy(boyut = v) } })
            if (kp.stil.startsWith("r_")) {
                Spacer(Modifier.height(8.dp))
                MetinEditoru(kp.bant, "Bantlar (her satır ayrı bant)", { y -> vm.kapakDegis { it.copy(bant = y) } }, enCokSatir = 3)
            }
            Bolum("YERİ")
            KonumSecici(kp.konum, sari) { s -> vm.kapakDegis { it.copy(konum = s) } }
        }
        "stil" -> {
            val kaynak by produceState<Bitmap?>(null, kp.kaynakUri, kp.kaynakMs) { value = vm.kapakKaynakKucuk() }
            val ciz: (String, Int, Int) -> Bitmap? = { s, W, H -> Cizim.kapak(kp.copy(stil = s, yazi = kp.yazi.ifBlank { "Şefin *Doğası*" }), kaynak, p.logo, W, H) }
            Bolum("KLASİK")
            OnizlemeBandi(p, listOf("kalin" to "Kalın kenarlı", "kutulu" to "Kutulu", "bant" to "Renkli bant"), kp.stil, turuncu, Triple(kaynak, kp.copy(stil = ""), p.logo), ciz) { s -> vm.kapakDegis { it.copy(stil = s) } }
            Bolum("REELS TARZI")
            OnizlemeBandi(p, Reels.STILLER, kp.stil, pembe, Triple(kaynak, kp.copy(stil = ""), p.logo), ciz) { s -> vm.kapakDegis { it.copy(stil = s) } }
        }
        else -> {
            Bolum("EFEKTLER · dokun = aç / kapat")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                YuvarlakSecim("Canlı renkler", "gorunum", yesil, kp.canli) { vm.kapakDegis { it.copy(canli = !it.canli) } }
                YuvarlakSecim("Logo", "logo", turkuaz, kp.logo) { vm.kapakDegis { it.copy(logo = !it.logo) } }
            }
            Not("Canlı renkler: renkleri canlandırır, kenarları hafifçe karartır. Logo: projenin logosu kapağın köşesine konur.")
        }
    }
    Not("Kayıt yeri: Galeri › Pictures/Stüdyo. YouTube'a yüklerken “Küçük resim” olarak seç.")
}

/** 3.58: Geçişler panelindeki gibi yuvarlak simgeli seçim (seçili/açık = beyaz halka + ✓). */
@Composable
internal fun YuvarlakSecim(ad: String, simge: String, ton: Color, on: Boolean, modifier: Modifier = Modifier.width(84.dp), tik: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = tik).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(if (on) Color.Transparent else Renk.kart2)
                .border(if (on) 2.5.dp else 0.dp, if (on) Renk.yazi else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Simge(simge, Renk.tonla(ton), 26.dp) }
            if (on) OnayIsareti(Modifier.align(Alignment.TopEnd), 16.dp)
        }
        Text(ad, color = if (on) Renk.yazi else Renk.soluk, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

// ===================================================== TAKİP EDEN YAZI =====================================================

private val YAZI_RENK = listOf(0xFFFFFFFF, 0xFFFFD54F, 0xFFF5A524, 0xFF2EC5D3, 0xFF111111)

@Composable
fun PanelTakip(vm: EditorDurum, k: Klip) {
    val ilerleme by vm.takipIlerleme.collectAsState()
    val secimde by vm.takipSecimde.collectAsState()
    Kart("Takip eden yazı (bu klipte)") {
        if (k.foto) { Not("Takip eden yazı yalnız videolarda çalışır. Bir video klibi seç."); return@Kart }
        val t = k.takip
        val f = ilerleme
        when {
            f != null -> {
                Text("Nokta izleniyor… %${(f * 100).roundToInt()}", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
                Dugme("İptal", Modifier.fillMaxWidth()) { vm.takipIptal() }
            }
            secimde -> {
                Text("Önizlemede takip edilecek yere dokun.", color = Renk.turkuaz, fontWeight = FontWeight.SemiBold)
                Not("Yazı o andan noktayı izler; nesne kaybolunca ya da klip bitince kendiliğinden biter. Bitişi sonra elle de ayarlayabilirsin.")
                Dugme("Vazgeç", Modifier.fillMaxWidth().padding(top = 6.dp)) { vm.takipSecimde.value = false }
            }
            t == null -> {
                Dugme("◎ Takip edilecek noktayı seç", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.player.pause(); vm.takipSecimde.value = true }
                Not("Örnek: yürüyen birinin üstünde ad, bir çiçeğin yanında açıklama. İzleme telefonda yapılır.")
            }
            else -> {
                Text(if (t.noktalar.isEmpty()) "Nokta seçildi, izleme yok" else "${t.noktalar.size} karede izlendi · ${sureOndalik(t.basMs - k.basMs)} anından", color = Renk.soluk, fontSize = 12.5.sp)
                var m by remember(k.id) { mutableStateOf(t.metin) }
                OutlinedTextField(m, { m = it; vm.takipDegis { tt -> tt.copy(metin = it) } }, Modifier.fillMaxWidth().padding(top = 6.dp), label = { Text("Yazı") }, maxLines = 2)
                var a by remember(k.id) { mutableStateOf(t.alt) }
                OutlinedTextField(a, { a = it; vm.takipDegis { tt -> tt.copy(alt = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Alt satır (isteğe bağlı)") }, singleLine = true)
                // görünüm: yan yana, çizgi simgeli
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(Triple("cagri", "Zarif", "t_cagri"), Triple("kose", "Köşe", "t_kose"), Triple("igne", "İğne", "t_igne"), Triple("kutu", "Kutulu", "t_kutu"))
                        .forEachIndexed { i, (s, ad, sm) -> SecimKarti(ad, "", t.stil == s, Modifier.weight(1f), sira = i, simge = sm) { vm.takipDegis { it.copy(stil = s) } } }
                }
                Kaydirici("Boyut", t.boyut, 0.03f..0.1f, { "%${(it * 100).roundToInt()}" }) { v -> vm.takipDegis { it.copy(boyut = v) } }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    YAZI_RENK.forEach { r ->
                        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(r))
                            .border(if (t.renk == r) 2.dp else 0.5.dp, if (t.renk == r) Color.White else Renk.cizgi, RoundedCornerShape(9.dp))
                            .clickable { vm.takipDegis { it.copy(renk = r) } })
                    }
                }
                if (t.stil == "kutu") AnahtarSatiri("Arka kutu", t.kutu) { v -> vm.takipDegis { it.copy(kutu = v) } }
                else {
                    Text("Çizgi rengi", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0xFFF2C94C, 0xFFFFFFFF, 0xFF2EC5D3, 0xFFFF6B6B, 0xFF86C06E).forEach { r ->
                            Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(r))
                                .border(if (t.cizgiRenk == r) 2.dp else 0.5.dp, if (t.cizgiRenk == r) Color.White else Renk.cizgi, RoundedCornerShape(9.dp))
                                .clickable { vm.takipDegis { it.copy(cizgiRenk = r) } })
                        }
                    }
                }
                // bitiş: otomatik (nesne kaybolunca / klip sonu) ya da elle (3.42)
                if (!k.foto) {
                    val bas = (t.basMs - k.basMs).coerceAtLeast(0) / 1000f
                    val tavan = (k.sonMs - k.basMs) / 1000f
                    val son = ((Katman.takipSon(t).coerceAtMost(k.sonMs) - k.basMs) / 1000f).coerceIn(bas, tavan)
                    if (tavan - bas > 0.3f) Kaydirici(if (t.sonMs >= 0) "Bitiş (elle)" else "Bitiş (otomatik)", son, (bas + 0.2f).coerceAtMost(tavan)..tavan, { sureOndalik((it * 1000).toLong()) }) { v ->
                        vm.takipDegis { it.copy(sonMs = k.basMs + (v * 1000).toLong()) }
                    }
                    DugmeSirasi(
                        "Bitiş: şu an" to {
                            val yerel = (vm.konum.value - vm.baslangic(vm.secili.value)).coerceAtLeast(0)
                            val ms = k.basMs + yerel
                            if (ms <= t.basMs + 200) vm.mesaj.value = "Bitiş, başlangıçtan sonra olmalı" else { vm.takipDegis { it.copy(sonMs = ms) }; vm.mesaj.value = "Yazı burada bitecek" }
                        },
                        "Otomatik bitiş" to { vm.takipDegis { it.copy(sonMs = -1) }; vm.mesaj.value = "Nesne kaybolunca biter" },
                    )
                }
                DugmeSirasi("Noktayı yeniden seç" to { vm.player.pause(); vm.takipSecimde.value = true }, "Kaldır" to { vm.takipKaldir() })
            }
        }
    }
}

// ===================================================== NESNE =====================================================

@Composable
fun PanelNesne(vm: EditorDurum, p: Proje, alt: String? = null) {
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.nesneEkle(it) } }
    var dsn by remember { mutableFloatStateOf(5f) }
    val videoSec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.videodanNesneDis(it, (dsn * 1000).toLong()) } }
    val secili by vm.seciliNesne.collectAsState()
    if (alt == null || alt == "foto") Kart("Nesne kes · katman") {
        // 3.47: ekrandaki fotoğraftan ya da dışarıdan (galeri) fotoğraftan
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dugme("✂ Ekrandaki fotoğraftan", Modifier.weight(1f), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.ekrandanNesne() }
            Dugme("✂ Galeriden fotoğraf", Modifier.weight(1f)) { sec.launch(arrayOf("image/*")) }
        }
        // 3.61: arka plan bulanıklığı (seçili klip)
        val sk by vm.secili.collectAsState()
        p.klipler.getOrNull(sk)?.let { kk ->
            TonKaydirici("Arka plan", kk.arkaBulanik, 0f..1f, 0.05f, Color(TON[2]), { if (it < 0.01f) "yok" else "%${(it * 100).roundToInt()}" }) { v -> vm.klipGuncelle { it.copy(arkaBulanik = v) } }
        }
        // 3.71: uzun açıklama kapalı (dokun = aç)
        AcilirNot("Ekrandaki fotoğraftan kesince arka plan kendiliğinden bulanıklaşır, nesne tam yerinde ön plana katman olarak gelir (nesnenin oturması için o klibin hareketi kapanır). " +
            "Fotoğraftaki ana nesne (insan, çay bardağı, hayvan…) arka planından ayrılır ve videonun üstüne katman olarak eklenir. " +
            "Telefonun içinde yapılır (Google ML Kit). İlk kullanımda model bir kez iner. Seçili nesneyi önizlemede parmakla sürükle.")
    }
    val nIl by vm.nesneIlerleme.collectAsState()
    if (alt == null || alt == "video") Kart("Videodan hareketli nesne", baslangicAcik = alt != null) {
        val f = nIl
        if (f != null) {
            Text("Kareler kesiliyor… %${(f * 100).roundToInt()}", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
            Dugme("İptal", Modifier.fillMaxWidth()) { vm.nesneIptal() }
        } else {
            var sn by remember { mutableFloatStateOf(dsn) }
            Etiketli("Süre", String.format(TR, "%.0f sn", sn))
            InceSlider(value = sn, onValueChange = { sn = it; dsn = it }, valueRange = 2f..10f, steps = 7, colors = kaydiriciRenk)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dugme("✂ Ekrandaki videodan", Modifier.weight(1f), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.videodanNesne((sn * 1000).toLong()) }
                Dugme("✂ Galeriden video", Modifier.weight(1f)) { videoSec.launch(arrayOf("video/*")) }
            }
            AcilirNot("Önizlemede nesnenin (insan, hayvan…) göründüğü video ana gel. Saniyede 15 kare kesilir; sonuç hareketli katman olur, istediğin klibin üstüne koyabilirsin (örneğin başka bir manzaranın önüne). 5 sn ≈ 1 dk sürer.")
        }
    }
    if (alt == "kesilen" && p.nesneler.isEmpty()) Not("Henüz kesilen nesne yok. Fotoğraftan ya da Videodan sayfasında kes.")
    if ((alt == null || alt == "kesilen") && p.nesneler.isNotEmpty()) Kart("Katmanlar (${p.nesneler.size})") {
        p.nesneler.forEach { n -> key(n.id) {
            val on = n.id == secili
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Renk.turuncu.copy(alpha = 0.14f) else Renk.kart2)
                .border(if (on) 2.dp else 0.dp, if (on) Renk.turuncu else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { vm.seciliNesne.value = n.id; vm.git(n.basMs + 1) }.padding(10.dp)) {   // 3.72: dokun = seç + nesnenin klibine git
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val b by produceState<Bitmap?>(null, n.yol) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(n.yol)?.let { Medya.olcekle(it, 160) } } }
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A4A5A))) { b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${sureOndalik(n.basMs)} → ${sureOndalik(n.sonMs)}" + if (n.hareketli) " · hareketli" else "", color = Renk.yazi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(if (on) "Seçili · sürükle = taşı · iki parmak = büyüt" else "Dokun = seç ve klibine git", color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (on) {
                    Kaydirici("Boyut", n.olcek, 0.05f..2f, { "%${(it * 100).roundToInt()}" }) { v -> vm.nesneDegis(n.id) { it.copy(olcek = v) } }
                    if (!n.hareketli) {
                        AnahtarSatiri("Yumuşak gölge", n.golge) { v -> vm.nesneDegis(n.id) { it.copy(golge = v) } }
                        var firca by remember(n.id) { mutableStateOf(false) }
                        GenisDugme("Cam fırçası (bardak, şişe…)", "takip", Modifier.padding(vertical = 4.dp)) { firca = true }
                        if (firca) CamFircasi(vm, n) { firca = false }
                    }
                    var silOnay by remember(n.id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dugme("Başı = şu an", Modifier.weight(1f), renk = Renk.kart) { val t = vm.anlik(); vm.nesneDegis(n.id) { it.copy(basMs = t, sonMs = maxOf(it.sonMs, t + 500)) } }
                        Dugme("Sonu = şu an", Modifier.weight(1f), renk = Renk.kart) { val t = vm.anlik(); vm.nesneDegis(n.id) { it.copy(sonMs = maxOf(t, it.basMs + 500)) } }
                        Dugme(if (silOnay) "Emin misin?" else "Sil", Modifier.weight(0.8f), renk = if (silOnay) Renk.kirmizi else Renk.kart) { if (silOnay) vm.nesneSil(n.id) else silOnay = true }
                    }
                }
            }
        } }
    }
}


/** Önizlemede erime: içerik ekran dışı katmanda çizilir, sonra gürültü maskesiyle (DstIn) kırpılır — dışa aktarmadaki maskenin aynısı. */
fun Modifier.erimeMaskesi(aktif: Boolean, k: Float): Modifier = if (!aktif) this else this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val m = Erime.maske(k).asImageBitmap()
        drawImage(m, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(m.width, m.height),
            androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
    }

// ===================================================== RENK (3.49) =====================================================

/** Renk ve ışık: parlaklık, kontrast, doygunluk, sıcaklık, vurgular (patlamayı azalt), gölgeler, ton + 8 bantlı HSL.
 *  Klip bazında; Kart'ın "tümüne uygula" düğmesiyle bütün kliplere. Önce/sonra görüntüsü dışa aktarmayla aynı dönüşüm. */
@Composable
fun PanelRenk(vm: EditorDurum, k: Klip) {
    val ctx = LocalContext.current
    val a = k.renk ?: RenkAyar()
    fun d(f: (RenkAyar) -> RenkAyar) = vm.klipGuncelle { it.copy(renk = f(it.renk ?: RenkAyar()).takeIf { r -> !r.bos }) }
    val kaynak by produceState<Bitmap?>(null, k.uri, k.basMs) { value = withContext(Dispatchers.IO) { Medya.kucukResim(ctx, k, 360) } }
    val sonra by produceState<Bitmap?>(null, kaynak, a) { value = kaynak?.let { b -> withContext(Dispatchers.Default) { RenkLut.uygula(b, a) } } }
    var once by remember { mutableStateOf(false) }
    Kart("Renk ve ışık", hepsine = { vm.hepsine({ it.copy(renk = k.renk) }); vm.mesaj.value = "Renk ayarı tüm kliplere uygulandı" }) {
        (if (once) kaynak else sonra ?: kaynak)?.let { b ->
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onPress = { once = true; tryAwaitRelease(); once = false }) }) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Text(if (once) "Önce" else "Sonra · basılı tut = önce", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        // hazır ayarlar
        Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Patlamayı azalt" to RenkAyar(vurgular = -0.6f, golgeler = 0.15f, kontrast = -0.05f),
                "Doğal" to RenkAyar(kontrast = 0.08f, doygunluk = 0.1f),
                "Canlı" to RenkAyar(kontrast = 0.15f, doygunluk = 0.35f, vurgular = -0.2f),
                "Sıcak" to RenkAyar(sicaklik = 0.4f, doygunluk = 0.1f),
                "Serin" to RenkAyar(sicaklik = -0.35f),
                "Yeşili canlandır" to RenkAyar(hslDoy = List(8) { if (it == 3 || it == 2) 0.35f else 0f }, hslIsik = List(8) { if (it == 3) 0.1f else 0f }),
                "Sıfırla" to RenkAyar(),
            ).forEachIndexed { j, (ad, r) -> SecimKarti(ad, "", a == r && !(ad == "Sıfırla" && !a.bos), Modifier.width(96.dp), sira = j, simge = "gorunum") { d { r } } }
        }
        Kaydirici("Parlaklık", a.parlaklik, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(parlaklik = v) } }
        Kaydirici("Kontrast", a.kontrast, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(kontrast = v) } }
        Kaydirici("Vurgular (patlamayı azalt: sola)", a.vurgular, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(vurgular = v) } }
        Kaydirici("Gölgeler", a.golgeler, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(golgeler = v) } }
        Kaydirici("Doygunluk", a.doygunluk, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(doygunluk = v) } }
        Kaydirici("Sıcaklık", a.sicaklik, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(sicaklik = v) } }
        Kaydirici("Ton (tüm renkler)", a.ton, -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(ton = v) } }
    }
    Kart("HSL · renk renk", baslangicAcik = false, hepsine = { vm.hepsine({ it.copy(renk = k.renk) }); vm.mesaj.value = "Renk ayarı tüm kliplere uygulandı" }) {
        var b by remember { mutableIntStateOf(3) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RenkLut.BANT_RENK.forEachIndexed { i, r ->
                val degisti = abs(a.hslTon[i]) + abs(a.hslDoy[i]) + abs(a.hslIsik[i]) > 0.005f
                Box(Modifier.size(30.dp).clip(CircleShape).background(Color(r))
                    .border(if (b == i) 3.dp else if (degisti) 1.5.dp else 0.dp, if (b == i) Color.White else Renk.turuncu, CircleShape).clickable { b = i })
            }
        }
        Text(RenkLut.BANT_AD[b], color = Color(RenkLut.BANT_RENK[b]), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        fun L(l: List<Float>, v: Float) = l.toMutableList().also { it[b] = v }
        key(b) {
            Kaydirici("Ton", a.hslTon[b], -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(hslTon = L(it.hslTon, v)) } }
            Kaydirici("Doygunluk", a.hslDoy[b], -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(hslDoy = L(it.hslDoy, v)) } }
            Kaydirici("Işık", a.hslIsik[b], -1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> d { it.copy(hslIsik = L(it.hslIsik, v)) } }
        }
        Not("Ayarlar oynatırken önizlemede ve dışa aktarmada uygulanır; durağan önizleme yukarıdaki önce/sonra görüntüsündedir.")
    }
}


/** 3.88: Yayın › Logo › Büyük logo geçişi — aç/kapat, logo (köşe logosuyla aynı ya da kütüphaneden başka), şekil, plan (kaç kez / ne sıklıkla), yerler. */
@Composable
fun LogoGecisKarti(vm: EditorDurum, p: Proje) {
    val g = p.gecis
    Kart("Büyük logo geçişi", baslangicAcik = g.logo, anahtar = g.logo, anahtarDegis = { v -> vm.logoGecisAc(v) }) {
        AcilirNot("Logo ekranın ortasında arka plandan büyüyerek gelir, ~1,6 sn kalır, izleyiciye doğru büyüyüp kaybolur (toplam 2,8 sn). " +
            "Klip arasına denk gelirse kesme logonun altında kalır; değilse fotoğrafın üstünde belirir. Geçiş türünden (geçiş yok dahil) bağımsızdır.")
        if (!g.logo) { Text("Açmak için sağdaki anahtar.", color = Renk.soluk, fontSize = 12.sp); return@Kart }
        LogoGecisAyari(vm, p)
        Text("Ne zaman", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("bos" to "Baş · orta · son", "orta" to "Yalnız ortada", "aralik" to "Belli arayla", "adet" to "Kaç kez").forEach { (k, ad) ->
                val on = g.logoPlan == k
                Text(ad, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(16.dp))
                    .clickable { vm.gecisDegis { it.copy(logoPlan = k) } }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        if (g.logoPlan == "aralik") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 20, 30, 45, 50, 60, 90, 120).forEach { s ->
                    val on = g.logoAralikSn == s
                    Text("$s sn", color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                        .background(if (on) Renk.turuncu else Renk.kart2).clickable { vm.gecisDegis { it.copy(logoAralikSn = s) } }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        if (g.logoPlan == "adet") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Kaç kez", color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.weight(1f))
                SimgeDugme("eksi", "Azalt", etkin = g.logoAdet > 1, renk = Renk.turuncu) { vm.gecisDegis { it.copy(logoAdet = (it.logoAdet - 1).coerceAtLeast(1)) } }
                Text("${g.logoAdet}", color = Renk.yazi, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                SimgeDugme("ekle", "Artır", etkin = g.logoAdet < 12, renk = Renk.turuncu) { vm.gecisDegis { it.copy(logoAdet = (it.logoAdet + 1).coerceAtMost(12)) } }
            }
        }
        AnahtarSatiri("Klip arasına denk getir (±3 sn)", g.logoYakala) { v -> vm.gecisDegis { it.copy(logoYakala = v) } }
        // yerler: dokun = o yerin 1 sn öncesinden oynat
        val anlar = p.logoGecisAnlari()
        Text(if (anlar.isEmpty()) "Bu plana uyan yer yok (video kısa olabilir)." else "${anlar.size} yerde · dokun = izle", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            anlar.forEach { t ->
                val sn = t / 1000
                Text(String.format(TR, "%d:%02d", sn / 60, sn % 60), color = Renk.turuncu, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Renk.turuncu.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).clickable { vm.logoGecisGoster(t) }.padding(horizontal = 11.dp, vertical = 6.dp))
            }
        }
    }
}

/** 3.82: logo geçişi ayarı — kütüphaneden logo, şekil, ilkini göster. */
@Composable
private fun LogoGecisAyari(vm: EditorDurum, p: Proje) {
    val kutup by LogoKutup.ogeler.collectAsState()
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { LogoKutup.yukle() } }
    Text("Logo", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    if (kutup.isEmpty()) Text("Kütüphanede logo yok. Yukarıdaki kütüphaneden yükle (köşeye koyman gerekmez).", color = Renk.soluk, fontSize = 12.sp)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 3.88: köşe logosuyla aynı (köşe logosu değişirse geçiş de değişir)
        p.logo?.yol?.takeIf { it.isNotEmpty() }?.let { ky ->
            val on = g0(p).logoYol.isEmpty()
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).border(if (on) 2.5.dp else 1.dp, if (on) Renk.turuncu else Renk.cizgi, RoundedCornerShape(12.dp))
                    .clickable { vm.gecisDegis { it.copy(logoYol = "") } }.padding(4.dp)) { LogoResmi(ky, Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))) }
                Text("Köşedeki", color = if (on) Renk.turuncu else Renk.soluk, fontSize = 10.sp)
            }
        }
        kutup.forEach { k ->
            val on = p.gecis.logoYol == k.yol
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).border(if (on) 2.5.dp else 1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(12.dp))
                .clickable { vm.gecisDegis { it.copy(logoYol = k.yol) } }.padding(4.dp)) { LogoResmi(k.yol, Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp))) }
        }
    }
    Text("Şekil", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LogoGecis.SEKILLER.forEach { (k, ad) ->
            val on = p.gecis.logoSekil == k
            Text(ad, color = if (on) Renk.zemin else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp))
                .background(if (on) Renk.yazi else Renk.kart2).border(1.dp, if (on) Renk.yazi else Renk.cizgi, RoundedCornerShape(16.dp))
                .clickable { vm.gecisDegis { it.copy(logoSekil = k) } }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}
private fun g0(p: Proje) = p.gecis

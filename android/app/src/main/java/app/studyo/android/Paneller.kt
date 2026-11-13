@file:OptIn(ExperimentalMaterial3Api::class)

package app.studyo.android

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
private fun Not(m: String) = Text(m, color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))

// ===================================================== ÖNİZLEME =====================================================

/** Önizleme: dışa aktarmadaki bitmaplerin ve geçiş hesabının AYNISI (1080 ölçeğinde çizilip küçültülür). */
@Composable
fun OnizlemeKatmanli(vm: EditorDurum, p: Proje, nesnePaneli: Boolean, modifier: Modifier, logoPaneli: Boolean = false, katmanPaneli: Boolean = false) {
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
            val kompA by vm.kompAktif.collectAsState()
            if (kompA) {
                AndroidView(factory = { c -> android.view.SurfaceView(c).also { vm.kompYuzeyBagla(it) } },
                    onRelease = { vm.kompYuzeyBagla(null) }, modifier = Modifier.fillMaxSize())
                return@BoxWithConstraints
            }
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
                        (LayoutInflater.from(c).inflate(R.layout.oynatici, null, false) as PlayerView).apply { player = vm.player }
                    },
                    update = { v -> v.kadraj(false) },
                    modifier = Modifier.requiredSize(gorunumGen(kbAn), gorunumYuk(kbAn)).graphicsLayer {
                        val kb = kbAn?.s ?: 1f
                        scaleX = kb * an.olcek; scaleY = kb * an.olcek
                        translationX = (kbAn?.tx ?: 0f) * size.width / 2f; translationY = -(kbAn?.ty ?: 0f) * size.height / 2f
                    },
                )
            }
            if (an.renk > 0f) Box(Modifier.fillMaxSize().background((if (an.beyaz) Color.White else Color.Black).copy(alpha = an.renk.coerceIn(0f, 1f))))

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
            // nesneler
            p.nesneler.forEach { n ->
                key(n.id) {
                    if (n.hareketli) {
                        val a = Zaman.solma(konum, n.basMs, n.sonMs, 200)
                        val idx = Katman.kareNo(n, konum)
                        val kb2 by produceState<Bitmap?>(null, n.id, idx, n.olcek, a > 0f) {
                            value = if (a > 0f) withContext(Dispatchers.IO) { Katman.nesneKaresi(n, idx, RW) } else null
                        }
                        kb2?.let { Ciz(Yerlesim(it, n.x * 2f - 1f, 1f - n.y * 2f, 0f, 0f), a) }
                    } else {
                        val ny = remember(n.yol, n.olcek, n.golge, n.x, n.y, ref) { Katman.nesne(n, RW, RH) }
                        ny?.let { Ciz(it, Zaman.solma(konum, n.basMs, n.sonMs)) }
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
                    Image(sr.bmp.asImageBitmap(), null,
                        Modifier.offset { IntOffset((x0 * olcek).roundToInt(), (y0 * olcek).roundToInt()) }
                            .size(with(yog) { (sr.bmp.width * ak.olcek * olcek).toDp() }, with(yog) { (sr.bmp.height * ak.olcek * olcek).toDp() })
                            .alpha(ak.alfa.coerceIn(0f, 1f)))
                }
            } else {
                val girisY = remember(p.giris, ref) { Cizim.giris(p.giris, RW, RH) }
                girisY?.let { Ciz(it, Zaman.solma(konum, gA, gB)) }
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
                    val bm = remember(KatmanCizim.anahtar(o), ref) { KatmanCizim.bitmap(o, RW, RH) }
                    if (bm != null) {
                        katBoyut[o.id] = bm.width to bm.height
                        val g = katGecici?.takeIf { it.id == o.id }
                        val a = KatmanCizim.an(o, konum)
                        val secili = katmanPaneli && seciliK == o.id
                        if (a.alfa > 0.01f || (secili && !o.gizli)) {
                            val x = g?.x ?: o.x; val y = (g?.y ?: o.y) + a.dy
                            val sk = a.olcek * (g?.carpan ?: 1f); val aci = o.aci + (g?.aci ?: 0f)
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
                    o.copy(x = g.x, y = g.y, olcek = (o.olcek * g.carpan).coerceIn(mn, mx), aci = aci)
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

            // dokunma: takip noktası seçimi / seçili nesneyi sürükleme
            if (takipSecim && k != null) {
                Box(Modifier.fillMaxSize().background(Color(0x332EC5D3)).pointerInput(k.id) {
                    detectTapGestures { o -> Geometri.kaynaga(k, o.x / olcek, o.y / olcek, RW, RH, kbAn ?: Yuz.an(k, RW, RH, 0f))?.let { vm.takipNoktaSec(sira, it.first, it.second) } }
                }) {
                    Text("Takip edilecek yere dokun", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            } else if (katmanPaneli) {
                val pG by rememberUpdatedState(p); val konumG by rememberUpdatedState(konum)
                val kSec = p.katmanlar.firstOrNull { it.id == seciliK }
                val kSecG by rememberUpdatedState(kSec)
                Box(Modifier.fillMaxSize()
                    .pointerInput(olcek) {
                        detectTapGestures { t ->
                            val px = t.x / olcek; val py = t.y / olcek
                            val bul = KatmanCizim.gorunenler(pG, konumG).firstOrNull { o ->
                                val (bw, bh) = katBoyut[o.id] ?: return@firstOrNull false
                                val s = KatmanCizim.an(o, konumG).olcek
                                katmanIsabet(o, (bw * s).toInt(), (bh * s).toInt(), px, py, RW, RH)
                            }
                            vm.seciliKatman.value = bul?.id
                        }
                    }
                    .pointerInput(kSec?.id, kSec?.kilitli, olcek) {
                        if (kSec == null || kSec.kilitli) return@pointerInput
                        detectTransformGestures { _, pan, zoom, rot ->
                            val o = kSecG ?: return@detectTransformGestures
                            val g = katGecici?.takeIf { it.id == o.id } ?: KatmanGecici(o.id, o.x, o.y, 1f, 0f)
                            katGecici = g.copy(
                                x = (g.x + pan.x / (olcek * RW)).coerceIn(0f, 1f), y = (g.y + pan.y / (olcek * RH)).coerceIn(0f, 1f),
                                carpan = (g.carpan * zoom).coerceIn(0.1f, 8f), aci = g.aci + rot,
                            )
                        }
                    })
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
            } else if (nesnePaneli && seciliNesne != null) {
                val id = seciliNesne!!
                Box(Modifier.fillMaxSize().pointerInput(id, olcek) {
                    detectDragGestures { ch, d ->
                        ch.consume()
                        vm.nesneDegis(id) { it.copy(x = (it.x + d.x / (olcek * RW)).coerceIn(0f, 1f), y = (it.y + d.y / (olcek * RH)).coerceIn(0f, 1f)) }
                    }
                })
            } else if (k != null && sira < p.klipler.size) {
                // izlerken de: parmakla sürükle = fotoğrafı/videoyu kadraj içinde kaydır (yalnız taşan kısım kadar); çift dokun = ortala
                val gw = constraints.maxWidth.toFloat(); val gh = constraints.maxHeight.toFloat()
                val kSon by rememberUpdatedState(k)
                Box(Modifier.fillMaxSize()
                    .pointerInput(k.id) { detectTapGestures(onDoubleTap = { vm.klipGuncelle { it.copy(kayX = 0f, kayY = 0f) }; vm.mesaj.value = "Kadraj ortalandı" }) }
                    .pointerInput(k.id, k.doldur, gw, gh) {
                        detectDragGestures(
                            onDragStart = { surKay = Offset(kSon.kayX, kSon.kayY) },
                            onDragEnd = { surKay?.let { o -> vm.klipGuncelle { it.copy(kayX = o.x, kayY = o.y) } }; surKay = null },
                            onDragCancel = { surKay = null },
                        ) { ch, d ->
                            val ks = kSon; val a = Yuz.an(ks, RW, RH, kbF, sira)
                            if (!Yuz.kayabilir(a)) return@detectDragGestures
                            ch.consume()
                            val pxX = (a.s - a.vx) / a.vx * gw / 2f; val pxY = (a.s - a.vy) / a.vy * gh / 2f
                            val o = surKay ?: Offset(ks.kayX, ks.kayY)
                            surKay = Offset(if (pxX > 0.5f) (o.x + d.x / pxX).coerceIn(-1f, 1f) else o.x,
                                if (pxY > 0.5f) (o.y + d.y / pxY).coerceIn(-1f, 1f) else o.y)
                        }
                    })
            }
        }
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Varsayilan.MADDELER.forEachIndexed { i, m ->
            val on = m[0] in sec
            val ton = tonRenk(i, m[1])
            val r = if (on) ton else Renk.soluk.copy(alpha = 0.45f)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { Varsayilan.degistir(ctx, m[0]); sec = Varsayilan.secili(ctx) }
                .padding(vertical = 6.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Simge(m[2], r, 22.dp)
                Text(m[1], color = r, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                    maxLines = 2, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp))
                Text(m[3], color = Renk.soluk.copy(alpha = if (on) 0.9f else 0.5f), fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 10.5.sp, maxLines = 3)
                Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (on) ton else Color.Transparent))
            }
        }
    }
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
fun PanelSablon(vm: EditorDurum, p: Proje) {
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
            Text("Klasik", color = Renk.soluk, fontSize = 12.5.sp)
            Secenekler(listOf("sinematik" to "Sinematik", "altbant" to "Alt bant", "kutulu" to "Kutulu", "neon" to "Neon", "bantlar" to "Renkli bant", "minimal" to "Sade"), g.stil) { s -> vm.girisDegis { it.copy(stil = s) } }
            Text("Reels tarzı · şeritler sırayla kayarak gelir", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
            Secenekler(Reels.STILLER, g.stil) { s -> vm.girisDegis { it.copy(stil = s) } }
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
    val g = p.gecis
    Kart("Klip geçişi") {
        Text("Klip üstüne binen", color = Renk.soluk, fontSize = 12.5.sp)
        Secenekler(listOf("erime" to "Erime", "capraz" to "Yumuşak", "kaydir" to "Kaydırarak", "yakincapraz" to "Büyüyerek"), g.tur, sutun = 4) { s -> vm.gecisDegis { it.copy(tur = s) } }
        Text("Klip sınırında", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        Secenekler(listOf("yok" to "Yok", "siyah" to "Siyahtan", "beyaz" to "Parlama", "yakin" to "Yakınlaşma"), g.tur, sutun = 4) { s -> vm.gecisDegis { it.copy(tur = s) } }
        if (g.tur != "yok") Kaydirici("Geçiş süresi", g.sureMs / 1000f, 0.3f..2f, { String.format(TR, "%.1f sn", it) }) { v -> vm.gecisDegis { it.copy(sureMs = (v * 1000).toLong()) } }
        AnahtarSatiri("Videoyu siyahtan açarak başlat", g.siyahtanAc) { v -> vm.gecisDegis { it.copy(siyahtanAc = v) } }
        Not(if (g.cakisma) "Giden klibin son kısmı gelen klibin üstünde kaybolur (Erime: lekeler hâlinde yukarıdan aşağı erir, web Kareden Videoya'daki gibi). İki klip de geçiş boyunca oynamaya devam eder. Her geçiş videoyu geçiş süresi kadar kısaltır."
            else "Geçiş her klip sınırında uygulanır; videonun sonu hiçbir zaman karartılmaz. Önizlemede de görünür.")
    }
}

// ===================================================== ALTYAZI =====================================================

@Composable
fun PanelAltyazi(vm: EditorDurum, p: Proje) {
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
    Kart("Otomatik altyazı") {
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
    Kart("Altyazı satırları (${p.altyazilar.size})") {
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
    Kart("Altyazı görünümü") {
        AnahtarSatiri("Altyazıları videoya yaz", st.acik) { v -> vm.altyaziStilDegis { it.copy(acik = v) } }
        Secenekler(listOf("kutu" to "Koyu kutu", "kenarli" to "Kenarlı", "sari" to "Sarı kenarlı", "kisa" to "Kelime vurgulu"), st.stil, sutun = 2) { s -> vm.altyaziStilDegis { it.copy(stil = s) } }
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

private val VURGULAR = listOf(0xFFFFD400 to "Sarı", 0xFFFF3B30 to "Kırmızı", 0xFF35E06B to "Yeşil", 0xFF27D3FF to "Mavi", 0xFFFF8A00 to "Turuncu")

@Composable
fun PanelKapak(vm: EditorDurum, p: Proje) {
    val resim by vm.kapakResmi.collectAsState()
    val oneriler by vm.kapakOnerileri.collectAsState()
    val hazirlaniyor by vm.kapakOneriHazirlaniyor.collectAsState()
    LaunchedEffect(Unit) { vm.kapakCiz() }
    val kp = p.kapak
    Kart("Kapak (küçük resim)") {
        val oran = when (p.bicim) { "9:16" -> 9f / 16f; "1:1" -> 1f; else -> 16f / 9f }
        Box(Modifier.fillMaxWidth(if (oran < 1f) 0.55f else 1f).align(Alignment.CenterHorizontally).aspectRatio(oran).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
            resim?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
        Spacer(Modifier.height(8.dp))
        DugmeSirasi("Önizlemedeki kare" to { vm.kapakKareSec() }, (if (hazirlaniyor) "Hazırlanıyor…" else "Kapak önerileri") to { vm.kapakOneriHazirla() })
        if (oneriler.isNotEmpty()) {
            oneriler.chunked(3).forEach { sira ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sira.forEach { (k, b) ->
                        val on = k.kaynakUri == kp.kaynakUri && k.kaynakMs == kp.kaynakMs
                        Image(b.asImageBitmap(), null, Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .border(if (on) 3.dp else 0.dp, if (on) Renk.turuncu else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { vm.kapakOneriSec(k) }, contentScale = ContentScale.Crop)
                    }
                    repeat(3 - sira.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    Kart("Kapak yazısı") {
        var y by remember(p.id) { mutableStateOf(kp.yazi) }
        OutlinedTextField(y, { y = it; vm.kapakDegis { k -> k.copy(yazi = it) } }, Modifier.fillMaxWidth(),
            label = { Text("Yazı — vurgu için *yıldız* içine al, satır için |") }, maxLines = 3)
        Secenekler(listOf("kalin" to "Kalın kenarlı", "kutulu" to "Kutulu", "bant" to "Renkli bant"), kp.stil) { s -> vm.kapakDegis { it.copy(stil = s) } }
        Text("Reels stilleri (satır: |, bant: aşağıda)", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
        Secenekler(Reels.STILLER, kp.stil) { s -> vm.kapakDegis { it.copy(stil = s) } }
        if (kp.stil.startsWith("r_")) {
            var bant by remember(p.id) { mutableStateOf(kp.bant) }
            OutlinedTextField(bant, { bant = it; vm.kapakDegis { k -> k.copy(bant = it) } }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Bantlar (| ile ayrı bant)") }, singleLine = true)
        }
        Secenekler(listOf("ust" to "Üstte", "orta" to "Ortada", "alt" to "Altta"), kp.konum) { s -> vm.kapakDegis { it.copy(konum = s) } }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VURGULAR.forEach { (r, _) ->
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(9.dp)).background(Color(r))
                    .border(if (kp.vurgu == r) 2.dp else 0.dp, if (kp.vurgu == r) Color.White else Color.Transparent, RoundedCornerShape(9.dp))
                    .clickable { vm.kapakDegis { it.copy(vurgu = r) } })
            }
        }
        Kaydirici("Yazı boyutu", kp.boyut, 0.06f..0.18f, { "%${(it * 100).roundToInt()}" }) { v -> vm.kapakDegis { it.copy(boyut = v) } }
        AnahtarSatiri("Renkleri canlandır, kenarları karart", kp.canli) { v -> vm.kapakDegis { it.copy(canli = v) } }
        AnahtarSatiri("Logoyu kapağa koy", kp.logo) { v -> vm.kapakDegis { it.copy(logo = v) } }
        Spacer(Modifier.height(6.dp))
        Dugme("Kapağı JPG kaydet", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.kapakKaydet() }
        Not("Kayıt yeri: Galeri › Pictures/Stüdyo. YouTube'a yüklerken “Küçük resim” olarak seç.")
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
                Not("Yazı o andan klibin sonuna kadar noktayı izler. İstediğin ana önce oynatıcıdan gel.")
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
                DugmeSirasi("Noktayı yeniden seç" to { vm.player.pause(); vm.takipSecimde.value = true }, "Kaldır" to { vm.takipKaldir() })
            }
        }
    }
}

// ===================================================== NESNE =====================================================

@Composable
fun PanelNesne(vm: EditorDurum, p: Proje) {
    val sec = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.nesneEkle(it) } }
    val secili by vm.seciliNesne.collectAsState()
    Kart("Nesne kes · katman") {
        Dugme("✂ Fotoğraftan nesne kes", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { sec.launch(arrayOf("image/*")) }
        Not("Fotoğraftaki ana nesne (insan, çay bardağı, hayvan…) arka planından ayrılır ve videonun üstüne katman olarak eklenir. " +
            "Telefonun içinde yapılır (Google ML Kit). İlk kullanımda model bir kez iner. Seçili nesneyi önizlemede parmakla sürükle.")
    }
    val nIl by vm.nesneIlerleme.collectAsState()
    Kart("Videodan hareketli nesne", baslangicAcik = false) {
        val f = nIl
        if (f != null) {
            Text("Kareler kesiliyor… %${(f * 100).roundToInt()}", color = Renk.yazi, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Renk.turuncu, trackColor = Renk.cizgi)
            Dugme("İptal", Modifier.fillMaxWidth()) { vm.nesneIptal() }
        } else {
            var sn by remember { mutableFloatStateOf(5f) }
            Etiketli("Süre", String.format(TR, "%.0f sn", sn))
            InceSlider(value = sn, onValueChange = { sn = it }, valueRange = 2f..10f, steps = 7, colors = kaydiriciRenk)
            Dugme("✂ Bu andan itibaren kes", Modifier.fillMaxWidth(), renk = Renk.turuncu, yaziRenk = Renk.turuncuM) { vm.videodanNesne((sn * 1000).toLong()) }
            Not("Önizlemede nesnenin (insan, hayvan…) göründüğü video ana gel. Saniyede 15 kare kesilir; sonuç hareketli katman olur, istediğin klibin üstüne koyabilirsin (örneğin başka bir manzaranın önüne). 5 sn ≈ 1 dk sürer.")
        }
    }
    if (p.nesneler.isNotEmpty()) Kart("Katmanlar (${p.nesneler.size})") {
        p.nesneler.forEach { n -> key(n.id) {
            val on = n.id == secili
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (on) Renk.turuncu.copy(alpha = 0.14f) else Renk.kart2)
                .border(if (on) 2.dp else 0.dp, if (on) Renk.turuncu else Color.Transparent, RoundedCornerShape(10.dp))
                .clickable { vm.seciliNesne.value = n.id }.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val b by produceState<Bitmap?>(null, n.yol) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(n.yol)?.let { Medya.olcekle(it, 160) } } }
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A4A5A))) { b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${sureOndalik(n.basMs)} → ${sureOndalik(n.sonMs)}" + if (n.hareketli) " · hareketli" else "", color = Renk.yazi, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(if (on) "Seçili · önizlemede sürükle" else "Seçmek için dokun", color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (on) {
                    Kaydirici("Boyut", n.olcek, 0.1f..1f, { "%${(it * 100).roundToInt()}" }) { v -> vm.nesneDegis(n.id) { it.copy(olcek = v) } }
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

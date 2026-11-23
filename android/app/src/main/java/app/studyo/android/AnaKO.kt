package app.studyo.android

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

// 3.84: "Karanlık Oda" teması — Karanlık Oda ve Kareden Videoya'nın ortak arayüzünün yerel hâli: grafit zemin, kehribar vurgu,
// ışıldayan lamba imli başlık, eylem kartları (birincil kehribar, tam genişlik), hap sekmeler, açılır Ayarlar kutusu.
// Ana sayfanın önceki görünümü diğer temalarda aynen duruyor.

private val ARCHIVO = FontFamily(Font(R.font.archivo_800, FontWeight.ExtraBold), Font(R.font.archivo_600, FontWeight.SemiBold))

/** Kehribar, çevresine ışık saçan lamba (Karanlık Oda'nın imi). */
@Composable
private fun Lamba(boy: androidx.compose.ui.unit.Dp = 30.dp) {
    Canvas(Modifier.size(boy)) {
        val c = center; val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(Renk.turuncu.copy(alpha = 0.55f), Renk.turuncu.copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
        drawCircle(Renk.turuncu.copy(alpha = 0.16f), r * 0.55f, c)
        drawCircle(Renk.turuncu, r * 0.34f, c)
    }
}

/** Eylem kartı: simge üstte, kalın başlık, altında soluk açıklama. Birincil = kehribar dolgu + ışık gölgesi. */
@Composable
private fun EylemKarti(simge: String, baslik: String, aciklama: String, modifier: Modifier, birincil: Boolean = false, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(18.dp)
    val zem = if (birincil) Renk.turuncu else Renk.kart
    val ink = if (birincil) Renk.turuncuM else Renk.yazi
    Column(modifier
        .then(if (birincil) Modifier.shadow(18.dp, sekil, ambientColor = Renk.turuncu, spotColor = Renk.turuncu) else Modifier)
        .clip(sekil).background(zem).border(1.dp, if (birincil) Renk.turuncu else Renk.cizgi, sekil)
        .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (birincil) Renk.turuncuM.copy(alpha = 0.12f) else Renk.turuncu.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center) { Simge(simge, if (birincil) ink else Renk.turuncu, 22.dp) }
        Column(Modifier.padding(top = 14.dp)) {
            Text(baslik, color = ink, fontSize = if (birincil) 19.sp else 15.5.sp, fontFamily = ARCHIVO, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(aciklama, color = if (birincil) ink.copy(alpha = 0.78f) else Renk.soluk, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
        }
    }
}

/** Hap sekme grubu (Karanlık Oda'daki "seg"). */
@Composable
private fun HapSekme(secenekler: List<String>, secili: Int, sec: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Renk.kart2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        secenekler.forEachIndexed { i, s ->
            val on = i == secili
            Text(s, color = if (on) Renk.yazi else Renk.soluk, fontSize = 13.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(if (on) Renk.kart else Color.Transparent).clickable { sec(i) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

/** Açılır Ayarlar kutusu: tema, düzen, yedek, pil, hata kaydı, sürüm (⋯ menüsünün yerine; başlığa dokun = aç/kapat). */
@Composable
private fun AyarKutusu(geriYukle: () -> Unit) {
    val ctx = LocalContext.current
    var acik by remember { mutableStateOf(false) }
    val sekil = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(sekil).background(Renk.kart).border(1.dp, Renk.cizgi, sekil).animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable { acik = !acik }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Ayarlar", color = Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("tema · düzen · yedek", color = Renk.soluk, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            Simge("asagi", Renk.soluk, 16.dp, Modifier.rotate(if (acik) 180f else 0f))
        }
        if (acik) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
            Text("TEMA", color = Renk.soluk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4 to "InShot", 3 to "Karanlık Oda", 0 to "Gece", 1 to "Gündüz", 2 to "Siyah-beyaz").forEach { (t, ad) ->
                    val on = Renk.tema == t
                    Text(ad, color = if (on) Renk.turuncuM else Renk.yazi, fontSize = 12.5.sp, modifier = Modifier.clip(CircleShape)
                        .background(if (on) Renk.turuncu else Renk.kart2).border(1.dp, if (on) Renk.turuncu else Renk.cizgi, CircleShape)
                        .clickable { Renk.sec(ctx, t) }.padding(horizontal = 13.dp, vertical = 7.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Renk.cizgi))
            @Composable fun Satir(simge: String, ad: String, not: String = "", tik: () -> Unit) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = tik).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Simge(simge, Renk.soluk, 19.dp); Spacer(Modifier.width(12.dp))
                    Text(ad, color = Renk.yazi, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    if (not.isNotEmpty()) Text(not, color = Renk.soluk, fontSize = 11.5.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Satir("kapak", "Stüdyo ana sayfası (açık, mavi-turuncu)") { Duzen.anaStudyoSec(ctx, true) }
            Satir("menu", if (Duzen.menuStil == "renkli") "Alt başlıklar: simgeli" else "Alt başlıklar: yazılı renkli") { Duzen.degistir(ctx) }
            Satir("yukle", "Yedekten geri yükle", "ayarlar · logolar", geriYukle)
            Satir("pil", "Pil kısıtlamasını kaldır") { pilAyari(ctx) }
            Satir("degistir", "Hata kaydını paylaş") { kayitPaylas(ctx) }
            Satir("yildiz", "Sürüm", BuildConfigBilgi.surum(ctx)) { }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnaSayfaKO(
    projeler: List<Proje>, gorunen: List<Proje>, arsivde: Boolean, arsivSayisi: Int, secim: Set<String>?, disa: DisaDurum,
    arsivDegis: (Boolean) -> Unit, secimDegis: (Set<String>?) -> Unit, yeni: () -> Unit, webAc: () -> Unit, ac: (String) -> Unit,
    yenile: () -> Unit, topluSil: () -> Unit, geriYukle: () -> Unit, silIste: (Proje) -> Unit, adIste: (Proje) -> Unit, tvIste: (Pair<String, String>) -> Unit,
) {
    val ctx = LocalContext.current
    val seciliProjeler = secim?.let { s -> gorunen.filter { it.id in s } } ?: emptyList()
    LazyColumn(Modifier.fillMaxSize().background(Renk.zemin)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // başlık: lamba imi + Stüdyo (kamera bölgesi de aynı zeminde — ayrı şerit yok)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Lamba()
                Spacer(Modifier.width(6.dp))
                Column {
                    Text("Stüdyo", color = Renk.yazi, fontSize = 28.sp, fontFamily = ARCHIVO, fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(letterSpacing = (-0.5).sp))
                    Text("Fotoğraf ve video · her şey cihazında · sürüm ${BuildConfigBilgi.surum(ctx)}", color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { AyarKutusu(geriYukle) }
        // eylemler: birincil tam genişlik, altında ikili
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EylemKarti("ekle", "Yeni video", "Fotoğraf ve videolarını seç; giriş, müzik, geçiş ve logo hazır gelir", Modifier.fillMaxWidth().height(132.dp), birincil = true, onClick = yeni)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EylemKarti("palet", "Fotoğraf", "Fotoğraf editörü ve önceki Stüdyo (web)", Modifier.weight(1f).height(128.dp), onClick = webAc)
                    EylemKarti("asagi", if (arsivde) "Çalışmalarım" else "Arşiv", if (arsivde) "Son çalışmalarına dön" else "$arsivSayisi arşivlenmiş proje",
                        Modifier.weight(1f).height(128.dp)) { arsivDegis(!arsivde) }
                }
            }
        }
        if (disa != DisaDurum.Bos) item { DisaSerit(disa) { DisaServisi.sonProje?.let { id -> if (Depo.yukle(id) != null) ac(id) } } }
        stickyHeader {
            val s = secim
            Column(Modifier.fillMaxWidth().background(Renk.zemin).padding(top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s != null) Text("${s.size} seçili", color = Renk.yazi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    else {
                        HapSekme(listOf("Son çalışmalarım", "Arşiv" + if (arsivSayisi > 0) " · $arsivSayisi" else ""), if (arsivde) 1 else 0) { arsivDegis(it == 1) }
                        Spacer(Modifier.weight(1f))
                        if (gorunen.isNotEmpty()) BaslikDugmesi("Seç") { secimDegis(emptySet()) }
                    }
                }
                if (s != null) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SimgeDugme("uygula", "Tümünü seç", Modifier.weight(1f), renk = Renk.turkuaz, etkin = s.size < gorunen.size) { secimDegis(gorunen.map { it.id }.toSet()) }
                    SimgeDugme("gerial", "Seçimi kaldır", Modifier.weight(1f), renk = Renk.soluk, etkin = s.isNotEmpty()) { secimDegis(emptySet()) }
                    SimgeDugme(if (arsivde) "yinele" else "asagi", if (arsivde) "Arşivden çıkar" else "Arşivle", Modifier.weight(1f), renk = Renk.turuncu, etkin = s.isNotEmpty()) {
                        Depo.arsivle(seciliProjeler, !arsivde); yenile(); secimDegis(null)
                    }
                    SimgeDugme("sil", "Sil", Modifier.weight(1f), renk = Renk.kirmizi, etkin = s.isNotEmpty()) { topluSil() }
                    SimgeDugme("kapat", "Bitti", Modifier.weight(1f)) { secimDegis(null) }
                }
            }
        }
        if (gorunen.isEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Renk.cizgi, RoundedCornerShape(16.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (arsivde) "Arşiv boş." else "Henüz proje yok", color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (!arsivde) Text("“Yeni video” ile başla.", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                if (!arsivde && projeler.isEmpty()) Text("Uygulamayı yeniden mi kurdun? Yedekten geri yükle", color = Renk.turuncu, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = geriYukle).padding(6.dp))
            }
        }
        items(gorunen, key = { it.id }) { p ->
            val s = secim
            ProjeKartiKO(p,
                ac = { if (s != null) secimDegis(if (p.id in s) s - p.id else s + p.id) else ac(p.id) },
                cogalt = { Depo.cogalt(p); yenile() }, adDegistir = { adIste(p) }, sil = { silIste(p) },
                arsivle = { Depo.arsivle(listOf(p), !p.arsiv); yenile() },
                secimde = s != null, secili = s?.contains(p.id) == true, uzunBas = { secimDegis((s ?: emptySet()) + p.id) },
                tv = remember(p.id, disa) { TvYayin.cikti(ctx, p.id) }?.let { c -> { tvIste(c) } })
        }
    }
}

/** Proje kartı (Karanlık Oda teması): büyük 16:9 küçük resim, üstünde süre rozeti; ad, biçim · klip sayısı, tarih. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjeKartiKO(p: Proje, ac: () -> Unit, cogalt: () -> Unit, adDegistir: () -> Unit, sil: () -> Unit, arsivle: () -> Unit,
                         secimde: Boolean, secili: Boolean, uzunBas: () -> Unit, tv: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    val sekil = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().clip(sekil).background(if (secili) Renk.turuncu.copy(alpha = 0.12f) else Renk.kart)
        .border(1.dp, if (secili) Renk.turuncu else Renk.cizgi, sekil)
        .combinedClickable(onClick = ac, onLongClick = uzunBas).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (secimde) Box(Modifier.padding(end = 10.dp).size(24.dp).clip(CircleShape).background(if (secili) Renk.turuncu else Color.Transparent)
            .border(1.5.dp, if (secili) Renk.turuncu else Renk.soluk, CircleShape), contentAlignment = Alignment.Center) {
            if (secili) Text("✓", color = Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(11.dp)).background(Renk.kart2)) {
            KlipResmi(p.klipler.firstOrNull(), Modifier.fillMaxSize())
            Text(sureYaz(p.toplamMs), color = Color.White, fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xB3000000)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.ad, color = Renk.yazi, fontSize = 15.sp, fontFamily = ARCHIVO, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.bicim, color = Renk.turuncu, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(Renk.turuncu.copy(alpha = 0.12f)).padding(horizontal = 5.dp, vertical = 1.dp))
                Text("  ${p.klipler.size} klip", color = Renk.soluk, fontSize = 12.sp)
            }
            Text(SimpleDateFormat("d MMM yyyy · HH:mm", TR).format(Date(p.guncelleme)), color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (!secimde && tv != null) Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = tv), contentAlignment = Alignment.Center) { Simge("tv", Renk.turuncu, 22.dp) }
        if (!secimde) Box {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) { Simge("menu", Renk.soluk, 20.dp) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Aç") }, onClick = { menu = false; ac() })
                if (tv != null) DropdownMenuItem(text = { Text("Televizyonda izle") }, onClick = { menu = false; tv() })
                DropdownMenuItem(text = { Text("Çoğalt") }, onClick = { menu = false; cogalt() })
                DropdownMenuItem(text = { Text("Adını değiştir") }, onClick = { menu = false; adDegistir() })
                DropdownMenuItem(text = { Text(if (p.arsiv) "Arşivden çıkar" else "Arşivle") }, onClick = { menu = false; arsivle() })
                DropdownMenuItem(text = { Text("Seç") }, onClick = { menu = false; uzunBas() })
                DropdownMenuItem(text = { Text("Sil", color = Renk.kirmizi) }, onClick = { menu = false; sil() })
            }
        }
    }
}

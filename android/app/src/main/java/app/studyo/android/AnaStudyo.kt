package app.studyo.android

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// 3.85: "Stüdyo" ana sayfası — web Stüdyo'nun (2.16) ana ekranının yerel hâli: açık zemin, STÜDYO başlığı, ay düğmesi,
// "Yaratıcılığını keşfet." başlığı, Fotoğraf/Video kartları, mavi Karanlık Oda ve turuncu Kareden Videoya kartları,
// Son çalışmalar şeridi, alt gezinme (Ana sayfa · Projelerim · Taslaklar · Ayarlar).
// Yalnız ana sayfa: editör kendi temasında (Renk.tema) kalır. Önceki ana sayfalar seçenek olarak duruyor.

/** Ana sayfa renkleri — web Stüdyo'nun gündüz/gece değişkenleriyle aynı. Editör temasından bağımsız. */
object AS {
    var gece by mutableStateOf(false)
    private fun s(gun: Long, gec: Long) = Color(if (gece) gec else gun)
    val zemin get() = s(0xFFF3F5F7, 0xFF121A21)
    val kart get() = s(0xFFFFFFFF, 0xFF1F2B35)
    val kart2 get() = s(0xFFEEF1F4, 0xFF283844)
    val cizgi get() = s(0xFFDFE4E8, 0xFF2F3F4B)
    val yazi get() = s(0xFF141A1F, 0xFFEEF2F5)
    val soluk get() = s(0xFF5F6C75, 0xFF93A3AE)
    val vurgu get() = s(0xFFE8681C, 0xFFFF8A3D)
    val vurgu2 get() = s(0xFFF39A4C, 0xFFFFB26B)
    val alt get() = s(0xFFFFFFFF, 0xFF0E151B)
    val mavi = Color(0xFF5AA9FF)
    fun yukle(ctx: Context) { gece = ctx.getSharedPreferences("ayar", 0).getBoolean("anaGece", false) }
    fun degistir(ctx: Context) { gece = !gece; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("anaGece", gece).apply() }
}

/** Web Stüdyo'daki SVG simgeler (24×24, 1.8 çizgi, yuvarlak uç). */
private val YOL = mapOf(
    "foto" to listOf("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M7 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M4 18l5-5 4 3 3-2 4 4"),
    "vid" to listOf("M5.5 6h7.5a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H5.5a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", "M16 10.5l5.5-3v9l-5.5-3z"),
    "klaket" to listOf("M4 10h16v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M4 10l1.5-5.5 15 3.5-.5 2", "M8.5 5.5l2.5 4M13.5 6.6l2.5 4"),
    "ev" to listOf("M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"),
    "klasor" to listOf("M3.5 7a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z"),
    "taslak" to listOf("M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z", "M14 3v6h6M8 13h8M8 17h5"),
    "ayar" to listOf("M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"),
    "ay" to listOf("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"),
    "gunes" to listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"),
    "ok" to listOf("M9 6l6 6-6 6"),
    "kam" to listOf("M4 8h3l2-3h6l2 3h3a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0"),
)
private val YOL_ON = HashMap<String, List<androidx.compose.ui.graphics.Path>>()

@Composable
private fun WSimge(ad: String, renk: Color, boy: Dp = 24.dp, kalin: Float = 1.8f) {
    val yollar = remember(ad) { YOL_ON.getOrPut(ad) { (YOL[ad] ?: emptyList()).map { PathParser().parsePathString(it).toPath() } } }
    Canvas(Modifier.size(boy)) {
        val k = size.minDimension / 24f
        scale(k, k, Offset.Zero) {
            yollar.forEach { drawPath(it, renk, style = Stroke(width = kalin, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

private fun neZaman(t: Long): String {
    val c = Calendar.getInstance(); val bugun = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    c.timeInMillis = t; val o = c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR)
    if (o == bugun) return "Bugün " + SimpleDateFormat("HH:mm", TR).format(Date(t))
    c.timeInMillis = System.currentTimeMillis(); c.add(Calendar.DAY_OF_YEAR, -1)
    if (o == (c.get(Calendar.DAY_OF_YEAR) to c.get(Calendar.YEAR))) return "Dün"
    return SimpleDateFormat("d MMM", TR).format(Date(t))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnaSayfaStudyo(
    projeler: List<Proje>, gorunen: List<Proje>, arsivde: Boolean, arsivSayisi: Int, secim: Set<String>?, disa: DisaDurum,
    arsivDegis: (Boolean) -> Unit, secimDegis: (Set<String>?) -> Unit, yeni: () -> Unit, yeniSecimle: (List<Uri>) -> Unit,
    webAc: () -> Unit, webUrl: (String) -> Unit, ac: (String) -> Unit,
    yenile: () -> Unit, topluSil: () -> Unit, geriYukle: () -> Unit, silIste: (Proje) -> Unit, adIste: (Proje) -> Unit, tvIste: (Pair<String, String>) -> Unit,
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { AS.yukle(ctx) }
    var sekme by rememberSaveable { mutableIntStateOf(0) }     // 0 ana · 1 projelerim · 2 taslaklar · 3 ayarlar
    BackHandler(enabled = sekme != 0 && secim == null && !arsivde) { sekme = 0 }
    val secici = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) yeniSecimle(it) }

    Column(Modifier.fillMaxSize().background(AS.zemin)) {
        // kamera bölgesi de aynı zeminde (ayrı şerit yok)
        Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            when (sekme) {
                0 -> AnaSekme(projeler, disa, ac, yeni = { secici.launch(arrayOf("image/*", "video/*")) }, kv = yeni, webAc = webAc, webUrl = webUrl) { sekme = 1 }
                1, 2 -> ProjeSekme(sekme == 2, gorunen, arsivde, arsivSayisi, secim, disa, arsivDegis, secimDegis, ac, yenile, topluSil, silIste, adIste, tvIste)
                else -> AyarSekme(geriYukle)
            }
        }
        // alt gezinme
        Box(Modifier.fillMaxWidth().height(0.8.dp).background(AS.cizgi))
        Row(Modifier.fillMaxWidth().background(AS.alt).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 6.dp, vertical = 6.dp)) {
            listOf("ev" to "Ana sayfa", "klasor" to "Projelerim", "taslak" to "Taslaklar", "ayar" to "Ayarlar").forEachIndexed { i, (s, ad) ->
                val on = i == sekme
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { sekme = i; if (secim != null) secimDegis(null); if (i != 1 && arsivde) arsivDegis(false) }
                    .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WSimge(s, if (on) AS.vurgu else AS.soluk, 27.dp)
                    Text(ad, color = if (on) AS.yazi else AS.soluk, fontSize = 12.5.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun AnaSekme(projeler: List<Proje>, disa: DisaDurum, ac: (String) -> Unit, yeni: () -> Unit, kv: () -> Unit, webAc: () -> Unit, webUrl: (String) -> Unit, tumu: () -> Unit) {
    val ctx = LocalContext.current
    val son = remember(projeler) { projeler.filter { !it.arsiv }.sortedByDescending { it.guncelleme }.take(12) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)))
                Spacer(Modifier.width(14.dp))
                Text("STÜDYO", color = AS.yazi, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.18.em, modifier = Modifier.weight(1f))
                Box(Modifier.size(52.dp).clip(CircleShape).background(AS.kart).border(1.dp, AS.cizgi, CircleShape).clickable { AS.degistir(ctx) },
                    contentAlignment = Alignment.Center) { WSimge(if (AS.gece) "gunes" else "ay", AS.yazi, 25.dp) }
            }
        }
        item {
            Column(Modifier.padding(top = 26.dp, bottom = 20.dp)) {
                Text("Yaratıcılığını keşfet.", color = AS.yazi, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
                Text("Fotoğraf ve video, tek bir yerde.", color = AS.soluk, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                KucukKart("foto", AS.mavi, "Fotoğraf", "Düzenlemeye başla · filtre, ışık, HSL, kırp", Modifier.weight(1f).fillMaxHeight(), webAc)
                KucukKart("vid", AS.vurgu, "Video", "Düzenlemeye başla · kes, müzik, metin, geçiş", Modifier.weight(1f).fillMaxHeight(), yeni)
            }
        }
        item {
            GenisKart("kam", "Karanlık Oda", "Fotoğraf stüdyosu · kitaplık, hazır ayarlar, haber",
                Brush.linearGradient(listOf(Color(0xFF3D7BE8), Color(0xFF24467F))), Color(0xFFEEF4FF), Color(0xFF3D7BE8), 0.18f) { webUrl(KO_ADRES) }
        }
        item {
            GenisKart("klaket", "Kareden Videoya", "Fotoğraflarından YouTube videosu · video türleri",
                Brush.linearGradient(listOf(AS.vurgu2, AS.vurgu)), Color(0xFF1B0F05), AS.vurgu, 0.3f, kv)
        }
        if (disa != DisaDurum.Bos) item { Box(Modifier.padding(top = 14.dp)) { DisaSerit(disa) { DisaServisi.sonProje?.let { id -> if (Depo.yukle(id) != null) ac(id) } } } }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Son çalışmalar", color = AS.yazi, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Tümünü gör", color = AS.vurgu, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = tumu).padding(horizontal = 4.dp, vertical = 4.dp))
            }
        }
        item {
            if (son.isEmpty()) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, AS.cizgi, RoundedCornerShape(18.dp)).padding(18.dp),
                contentAlignment = Alignment.Center) { Text("Henüz çalışma yok. Yukarıdan video seçerek başla.", color = AS.soluk, fontSize = 14.sp) }
            else LazyRow(Modifier.fillMaxWidth().layout { m, c ->      // web'deki gibi kenardan kenara kayar
                val ek = 20.dp.roundToPx() * 2
                val p = m.measure(c.copy(maxWidth = c.maxWidth + ek, minWidth = c.minWidth + ek)); layout(c.maxWidth, p.height) { p.place(-ek / 2, 0) }
            }, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(son, key = { it.id }) { p ->
                    Column(Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).clickable { ac(p.id) }) {
                        Box(Modifier.size(150.dp, 190.dp).clip(RoundedCornerShape(18.dp)).background(AS.kart2).border(1.dp, AS.cizgi, RoundedCornerShape(18.dp))) {
                            if (p.klipler.isNotEmpty()) KlipResmi(p.klipler.first(), Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WSimge("vid", AS.soluk, 30.dp) }
                            Rozet(Modifier.padding(9.dp))
                        }
                        Text(p.ad, color = AS.yazi, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp))
                        Text(neZaman(p.guncelleme), color = AS.soluk, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

const val KO_ADRES = "https://aaskinkk-cloud.github.io/karanlik-oda/"

@Composable
private fun Rozet(modifier: Modifier = Modifier, sure: String? = null) {
    Row(modifier.clip(CircleShape).background(Color(0x8C000000)).padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        WSimge("vid", Color.White, 14.dp, 2.2f); Spacer(Modifier.width(5.dp))
        Text(sure ?: "Video", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KucukKart(simge: String, ton: Color, baslik: String, alt: String, modifier: Modifier, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(26.dp)
    Box(modifier.heightIn(min = 220.dp).clip(sekil).background(AS.kart).border(1.dp, AS.cizgi, sekil).clickable(onClick = onClick)) {
        // sağ üstteki soluk daire (web'deki ::after)
        Box(Modifier.align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp).size(130.dp).clip(CircleShape).background(ton.copy(alpha = 0.12f)))
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 22.dp)) {
            Box(Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(ton.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { WSimge(simge, ton, 34.dp) }
            Spacer(Modifier.weight(1f).heightIn(min = 18.dp))
            Text(baslik, color = AS.yazi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(alt, color = AS.soluk, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun GenisKart(simge: String, baslik: String, alt: String, zemin: Brush, yazi: Color, golge: Color, ikZemin: Float, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(26.dp)
    Row(Modifier.padding(top = 16.dp).fillMaxWidth().shadow(14.dp, sekil, ambientColor = golge, spotColor = golge).clip(sekil).background(zemin)
        .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(66.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = ikZemin)), contentAlignment = Alignment.Center) { WSimge(simge, yazi, 30.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
            Text(baslik, color = yazi, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(alt, color = yazi.copy(alpha = 0.85f), fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 3.dp))
        }
        WSimge("ok", yazi, 26.dp, 2f)
    }
}

/** Projelerim / Taslaklar: iki sütunlu ızgara, web'deki proje kartları. Seçim (uzun bas), arşiv, TV, ⋯ menüsü korunur. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjeSekme(
    taslak: Boolean, gorunen: List<Proje>, arsivde: Boolean, arsivSayisi: Int, secim: Set<String>?, disa: DisaDurum,
    arsivDegis: (Boolean) -> Unit, secimDegis: (Set<String>?) -> Unit, ac: (String) -> Unit, yenile: () -> Unit, topluSil: () -> Unit,
    silIste: (Proje) -> Unit, adIste: (Proje) -> Unit, tvIste: (Pair<String, String>) -> Unit,
) {
    val ctx = LocalContext.current
    var filtre by rememberSaveable { mutableIntStateOf(0) }    // 0 tümü · 1 yatay · 2 dikey · 3 kare
    val bicimler = listOf("", "16:9", "9:16", "1:1")
    val ciktilar = remember(gorunen, disa) { gorunen.associate { it.id to TvYayin.cikti(ctx, it.id) } }
    val liste = gorunen.filter { (!taslak || ciktilar[it.id] == null) && (filtre == 0 || it.bicim == bicimler[filtre]) }
    val seciliProjeler = secim?.let { s -> liste.filter { it.id in s } } ?: emptyList()
    LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Text(if (taslak) "TASLAKLAR" else if (arsivde) "ARŞİV" else "PROJELERİM", color = AS.yazi, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em)
                Text(if (taslak) "Yarım kalanlar: henüz dışa aktarılmamış videolar." else if (arsivde) "Arşivlenen projeler ana listede görünmez." else "Video projelerinin hepsi burada.",
                    color = AS.soluk, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                val s = secim
                if (s == null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Tümü", "YouTube", "Reels", "Kare").forEachIndexed { i, ad -> Cip(ad, filtre == i) { filtre = i } }
                    if (!taslak) Cip("Arşiv" + if (arsivSayisi > 0) " · $arsivSayisi" else "", arsivde) { arsivDegis(!arsivde) }
                    if (liste.isNotEmpty()) Cip("Seç", false) { secimDegis(emptySet()) }
                } else {
                    Text("${s.size} seçili", color = AS.yazi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SimgeDugme("uygula", "Tümünü seç", Modifier.weight(1f), renk = AS.mavi, etkin = s.size < liste.size) { secimDegis(liste.map { it.id }.toSet()) }
                        SimgeDugme("gerial", "Seçimi kaldır", Modifier.weight(1f), renk = AS.soluk, etkin = s.isNotEmpty()) { secimDegis(emptySet()) }
                        SimgeDugme(if (arsivde) "yinele" else "asagi", if (arsivde) "Arşivden çıkar" else "Arşivle", Modifier.weight(1f), renk = AS.vurgu, etkin = s.isNotEmpty()) {
                            Depo.arsivle(seciliProjeler, !arsivde); yenile(); secimDegis(null)
                        }
                        SimgeDugme("sil", "Sil", Modifier.weight(1f), renk = Color(0xFFD93A36), etkin = s.isNotEmpty(), onClick = topluSil)
                        SimgeDugme("kapat", "Bitti", Modifier.weight(1f), renk = AS.yazi) { secimDegis(null) }
                    }
                }
            }
        }
        if (liste.isEmpty()) item(span = { GridItemSpan(2) }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, AS.cizgi, RoundedCornerShape(18.dp)).padding(18.dp), contentAlignment = Alignment.Center) {
                Text(if (taslak) "Yarım kalan çalışma yok." else if (arsivde) "Arşiv boş." else "Bu bölümde proje yok.", color = AS.soluk, fontSize = 14.sp)
            }
        }
        items(liste, key = { it.id }) { p ->
            val s = secim
            val secili = s?.contains(p.id) == true
            val tv = ciktilar[p.id]
            var menu by remember { mutableStateOf(false) }
            val sekil = RoundedCornerShape(18.dp)
            Column(Modifier.clip(sekil).background(AS.kart).border(if (secili) 2.dp else 1.dp, if (secili) AS.vurgu else AS.cizgi, sekil)
                .combinedClickable(onClick = { if (s != null) secimDegis(if (secili) s - p.id else s + p.id) else ac(p.id) },
                    onLongClick = { secimDegis((s ?: emptySet()) + p.id) })) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(AS.kart2)) {
                    if (p.klipler.isNotEmpty()) KlipResmi(p.klipler.first(), Modifier.fillMaxSize())
                    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WSimge("vid", AS.soluk, 28.dp) }
                    Rozet(Modifier.padding(8.dp), sureYaz(p.toplamMs))
                    if (s != null) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(26.dp).clip(CircleShape)
                        .background(if (secili) AS.vurgu else Color(0x8C000000)).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        if (secili) Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    } else Box(Modifier.align(Alignment.TopEnd)) {
                        Box(Modifier.padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x8C000000)).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            Text("⋯", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Aç") }, onClick = { menu = false; ac(p.id) })
                            if (tv != null) DropdownMenuItem(text = { Text("Televizyonda izle") }, onClick = { menu = false; tvIste(tv) })
                            DropdownMenuItem(text = { Text("Çoğalt") }, onClick = { menu = false; Depo.cogalt(p); yenile() })
                            DropdownMenuItem(text = { Text("Adını değiştir") }, onClick = { menu = false; adIste(p) })
                            DropdownMenuItem(text = { Text(if (p.arsiv) "Arşivden çıkar" else "Arşivle") }, onClick = { menu = false; Depo.arsivle(listOf(p), !p.arsiv); yenile() })
                            DropdownMenuItem(text = { Text("Seç") }, onClick = { menu = false; secimDegis(setOf(p.id)) })
                            DropdownMenuItem(text = { Text("Sil", color = Color(0xFFD93A36)) }, onClick = { menu = false; silIste(p) })
                        }
                    }
                    if (tv != null && s == null) Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(34.dp).clip(CircleShape).background(Color(0x8C000000))
                        .clickable { tvIste(tv) }, contentAlignment = Alignment.Center) { Simge("tv", Color.White, 19.dp) }
                }
                Column(Modifier.padding(start = 11.dp, end = 11.dp, top = 9.dp, bottom = 11.dp)) {
                    Text(p.ad, color = AS.yazi, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.klipler.size} klip · ${p.bicim} · ${neZaman(p.guncelleme)}" + if (tv != null) " · aktarıldı" else "", color = AS.soluk, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Cip(ad: String, on: Boolean, tik: () -> Unit) {
    Text(ad, color = if (on) AS.zemin else AS.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(if (on) AS.yazi else AS.kart).border(1.5.dp, if (on) AS.yazi else AS.cizgi, CircleShape)
            .clickable(onClick = tik).padding(horizontal = 16.dp, vertical = 9.dp))
}

@Composable
private fun Seg(secenekler: List<String>, secili: Int, sec: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(AS.kart2).padding(3.dp).horizontalScroll(rememberScrollState())) {
        secenekler.forEachIndexed { i, s ->
            val on = i == secili
            Text(s, color = if (on) AS.yazi else AS.soluk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.clip(CircleShape).background(if (on) AS.kart else Color.Transparent).clickable { sec(i) }.padding(horizontal = 12.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun AyarSekme(geriYukle: () -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp)) {
        item { Text("AYARLAR", color = AS.yazi, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.16.em, modifier = Modifier.padding(bottom = 14.dp)) }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AS.kart).border(1.dp, AS.cizgi, RoundedCornerShape(18.dp))) {
                @Composable fun Satir(ad: String, not: String = "", son: Boolean = false, tik: (() -> Unit)? = null, sag: @Composable () -> Unit = {}) {
                    Row(Modifier.fillMaxWidth().then(if (tik != null) Modifier.clickable(onClick = tik) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ad, color = AS.yazi, fontSize = 15.sp)
                            if (not.isNotEmpty()) Text(not, color = AS.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        sag()
                    }
                    if (!son) Box(Modifier.fillMaxWidth().height(1.dp).background(AS.cizgi))
                }
                Satir("Ana sayfa teması", "Yalnız bu ekranlar") { Seg(listOf("Gündüz", "Gece"), if (AS.gece) 1 else 0) { if ((it == 1) != AS.gece) AS.degistir(ctx) } }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Editör teması", color = AS.yazi, fontSize = 15.sp)
                    Text("Video düzenleme ekranları", color = AS.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                    val sira = listOf(2, 0, 1, 3)
                    Seg(listOf("Siyah-beyaz", "Gece", "Gündüz", "Karanlık Oda"), sira.indexOf(Renk.tema).coerceAtLeast(0)) { Renk.sec(ctx, sira[it]) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(AS.cizgi))
                Satir("Ana sayfa görünümü", "Önceki görünüme dön (geri gelmek için oradaki menü/Ayarlar)", tik = { Duzen.anaStudyoSec(ctx, false) }) { WSimge("ok", AS.soluk, 20.dp) }
                Satir(if (Duzen.yaziliMenu) "Alt başlıklar: simgeli" else "Alt başlıklar: yazılı (önceki)", tik = { Duzen.degistir(ctx) })
                Satir("Yedekten geri yükle", "Ayarlar ve logolar", tik = geriYukle)
                Satir("Pil kısıtlamasını kaldır", tik = { pilAyari(ctx) })
                Satir("Hata kaydını paylaş", tik = { kayitPaylas(ctx) })
                Satir("Sürüm", BuildConfigBilgi.surum(ctx), son = true)
            }
        }
        item {
            Text("Fotoğraf ve Karanlık Oda kartları web uygulamasını açar; video projeleri bu cihazda yerel olarak düzenlenir.",
                color = AS.soluk, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp, start = 2.dp, end = 2.dp))
        }
    }
}

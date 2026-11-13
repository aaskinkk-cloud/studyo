package app.studyo.android

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renkler. Gece (koyu) teması varsayılan; gündüz teması ⋯ menüsünden seçilir ve hatırlanır. Gece görünümü silinmedi. */
object Renk {
    var gunduz by mutableStateOf(false)
    private fun s(gece: Long, gun: Long) = Color(if (gunduz) gun else gece)
    val zemin get() = s(0xFF121A21, 0xFFF2F5F8)
    val kart get() = s(0xFF1B2833, 0xFFFFFFFF)
    val kart2 get() = s(0xFF243444, 0xFFE6ECF1)
    val cizgi get() = s(0xFF2E4153, 0xFFC5D1DB)
    val yazi get() = s(0xFFE6EDF3, 0xFF15212C)
    val soluk get() = s(0xFF8AA0B2, 0xFF5A6E80)
    val turuncu get() = s(0xFFF5A524, 0xFFEE9A0C)
    val turuncuM get() = s(0xFF1B1204, 0xFF1B1204)
    val turkuaz get() = s(0xFF2EC5D3, 0xFF0E9FAD)
    val kirmizi get() = s(0xFFEF5350, 0xFFD93A36)
    val yesil get() = s(0xFF4CC38A, 0xFF16915A)

    fun yukle(ctx: Context) { gunduz = ctx.getSharedPreferences("ayar", 0).getBoolean("gunduz", false) }
    fun degistir(ctx: Context) { gunduz = !gunduz; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("gunduz", gunduz).apply() }
}

/** Editör düzeni: sade (önizleme hep büyük, InShot gibi) varsayılan; önceki düzen ⋯ menüsünden seçilebilir. */
object Duzen {
    /** Alt başlıklar: false = simge üstte + küçük yazı (varsayılan), true = önceki yazılı haplar. */
    var yaziliMenu by mutableStateOf(false)
    /** Önizlemenin ekran yüksekliğine oranı (tutamaçla 0,25–0,70; çift dokunuş = 0,42). */
    var onizlemeOran by mutableFloatStateOf(0.5f)
    fun oranKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putFloat("onizlemeOran2", onizlemeOran).apply() }
    fun yukle(ctx: Context) {
        val sp = ctx.getSharedPreferences("ayar", 0)
        yaziliMenu = sp.getBoolean("yaziliMenu", false)
        onizlemeOran = sp.getFloat("onizlemeOran2", 0.5f).coerceIn(0.25f, 0.75f)
    }
    fun degistir(ctx: Context) { yaziliMenu = !yaziliMenu; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("yaziliMenu", yaziliMenu).apply() }
}

@Composable
fun StudyoTema(icerik: @Composable () -> Unit) {
    val renkler = if (Renk.gunduz) lightColorScheme(
        primary = Renk.turuncu, onPrimary = Renk.turuncuM, secondary = Renk.turkuaz, onSecondary = Color.White,
        background = Renk.zemin, onBackground = Renk.yazi, surface = Renk.kart, onSurface = Renk.yazi,
        surfaceVariant = Renk.kart2, onSurfaceVariant = Renk.soluk, error = Renk.kirmizi,
        surfaceContainerHigh = Renk.kart, surfaceContainer = Renk.kart, outline = Renk.cizgi,
    ) else darkColorScheme(
        primary = Renk.turuncu, onPrimary = Renk.turuncuM, secondary = Renk.turkuaz, onSecondary = Color(0xFF04191B),
        background = Renk.zemin, onBackground = Renk.yazi, surface = Renk.kart, onSurface = Renk.yazi,
        surfaceVariant = Renk.kart2, onSurfaceVariant = Renk.soluk, error = Renk.kirmizi,
        surfaceContainerHigh = Renk.kart, surfaceContainer = Renk.kart,
    )
    MaterialTheme(colorScheme = renkler, content = icerik)
}

/** Hap düğme (bölümün alt başlıkları): simge + ad; seçili olan turuncu dolgulu ve altı çizgili. */
@Composable
fun Hap(metin: String, secili: Boolean, simge: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(22.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).clip(sekil)
                .background(if (secili) Brush.verticalGradient(listOf(Renk.turuncu, Renk.turuncu.copy(alpha = 0.85f))) else SolidColor(Renk.kart2))
                .border(1.dp, if (secili) Renk.turuncu else Renk.cizgi, sekil)
                .clickable(onClick = onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            val uzun = metin.length > 9
            simge?.let { Simge(it, if (secili) Renk.turuncuM else Renk.yazi, if (uzun) 16.dp else 19.dp); Spacer(Modifier.width(if (uzun) 4.dp else 7.dp)) }
            Text(metin, color = if (secili) Renk.turuncuM else Renk.yazi, fontSize = if (uzun) 12.5.sp else 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(top = 4.dp).width(28.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (secili) Renk.turuncu else Color.Transparent))
    }
}

/** Geniş hap düğme (ör. "Tüm kliplere uygula ›"). */
@Composable
fun GenisDugme(metin: String, simge: String? = null, modifier: Modifier = Modifier, vurgulu: Boolean = false, onClick: () -> Unit) {
    if (!vurgulu && !Duzen.yaziliMenu) {
        val r = tonRenk(-1, metin)
        Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Simge(simge ?: otoSimge(metin), r, 18.dp); Spacer(Modifier.width(6.dp))
            Text(metin, color = r, fontSize = 12.sp)
        }
        return
    }
    val sekil = RoundedCornerShape(26.dp)
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(sekil).background(if (vurgulu) Renk.turuncu else Renk.kart2)
            .border(1.dp, if (vurgulu) Renk.turuncu else Renk.cizgi, sekil).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        val r = if (vurgulu) Renk.turuncuM else Renk.yazi
        simge?.let { Simge(it, r, 20.dp); Spacer(Modifier.width(10.dp)) }
        Text(metin, color = r, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Simge("ileri", r, 18.dp)
    }
}

/** Açılır-kapanır kart (başlığa dokun = aç/kapat; ad değiştirme menüsü açmaz). */
@Composable
fun Kart(baslik: String, baslangicAcik: Boolean = true, icerik: @Composable ColumnScope.() -> Unit) {
    var acik by rememberSaveable(baslik) { mutableStateOf(baslangicAcik) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Renk.kart)) {
        Row(
            Modifier.fillMaxWidth().clickable { acik = !acik }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (acik) Box(Modifier.width(3.dp).height(18.dp).background(Renk.turuncu))
            if (acik) Spacer(Modifier.width(8.dp))
            Text(baslik, color = if (acik) Renk.turuncu else Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (acik) "▴" else "▾", color = Renk.soluk, fontSize = 16.sp)
        }
        if (acik) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = icerik)
    }
}

/** Ortak renk tonları (alt menüyle aynı aile). */
val TON = longArrayOf(0xFFD9CF5A, 0xFF86C06E, 0xFF3FBFAE, 0xFFE8A06A, 0xFFDB7F93)
fun tonRenk(sira: Int, metin: String): Color = Color(TON[(if (sira >= 0) sira else (metin.hashCode() and 0x7fffffff)) % TON.size])

/** Başındaki ◀ ▶ ✂ ♪ gibi işaretleri atar. */
fun temizYazi(m: String): String = m.trimStart { !it.isLetterOrDigit() && it != '(' }.trimEnd { it == '▶' || it == ' ' || it == '›' }

/** Yazıdan uygun simge. */
fun otoSimge(m: String): String {
    val t = m.lowercase(java.util.Locale("tr"))
    fun h(vararg k: String) = k.any { t.contains(it) }
    return when {
        h("sola") -> "geri"; h("sağa") -> "sag"; h("çoğalt") -> "cogalt"
        t == "sil" || h("kaldır", "emin misin") -> "sil"
        h("iptal", "vazgeç", "kapat") -> "kapat"; h("değiştir") -> "degistir"
        h("yükle") -> "yukle"; h("kaydet") -> "kaydet"
        h("duraklat", "durdur") -> "durdur"; h("dinle", "oynat") -> "oynat"
        h("kayıt", "kaydı") -> "mikrofon"; h("müzik") -> "nota"; h("nesne", "cam", "öneri") -> "sihir"
        h("kes") -> "makas"; h("takip", "nokta") -> "takip"; h("logo") -> "logo"; h("kapak", "kare") -> "kapak"
        h("altyazı", "satır", "srt") -> "altyazi"; h("oku") -> "prompter"; h("pil") -> "pil"
        h("başı") -> "basa"; h("sonu") -> "sonraki"; h("uygula") -> "uygula"
        h("oluştur", "dışa", "paylaş") -> "disa"; h("geri") -> "geri"; h("ekle", "seç") -> "ekle"
        h("sığdır") -> "tuval"; h("doldur") -> "kadraj"; h("youtube") -> "oynat"; h("reels") -> "katman"; h("sinema") -> "klip"
        h("yüksek") -> "yildiz"; h("normal") -> "uygula"
        else -> "ileri"
    }
}

/** Sade düğme: üstte simge, altta küçük ince yazı, arka plan yok (renk = ton). Dolgulu renk verilirse eski vurgulu kutu. */
@Composable
fun Dugme(metin: String, modifier: Modifier = Modifier, renk: Color = Renk.kart2, yaziRenk: Color = Renk.yazi, etkin: Boolean = true, sira: Int = -1, onClick: () -> Unit) {
    if (renk == Renk.kart2 && !Duzen.yaziliMenu) {
        val r = tonRenk(sira, metin).let { if (etkin) it else it.copy(alpha = 0.3f) }
        Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = etkin, onClick = onClick).padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Simge(otoSimge(metin), r, 20.dp)
            Text(temizYazi(metin), color = r, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp))
        }
        return
    }
    Box(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (etkin) renk else renk.copy(alpha = 0.35f))
            .clickable(enabled = etkin, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(metin, color = if (etkin) yaziRenk else yaziRenk.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Eşit genişlikte düğme sırası (boş hücre bırakmaz), her düğme ayrı ton. */
@Composable
fun DugmeSirasi(vararg d: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.forEachIndexed { i, (m, f) -> Dugme(m, Modifier.weight(1f), sira = i, onClick = f) }
    }
}

/** Seçim: simge + küçük ad + çok küçük açıklama, arka plan yok; seçili olan tam renk, kalın ve altı noktalı. "16:9" gibi oranlar kendi şeklini çizer. */
@Composable
fun SecimKarti(baslik: String, alt: String, secili: Boolean, modifier: Modifier = Modifier, etkin: Boolean = true, sira: Int = -1, onClick: () -> Unit) {
    if (Duzen.yaziliMenu) { SecimKartiEski(baslik, alt, secili, modifier, etkin, onClick); return }
    val ton = tonRenk(sira, baslik)
    val r = if (!etkin) Renk.soluk.copy(alpha = 0.4f) else if (secili) ton else ton.copy(alpha = 0.55f)
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = etkin, onClick = onClick).padding(vertical = 5.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val oran = Regex("""^(\d+):(\d+)$""").find(baslik)
        if (oran != null) {
            val (a, b) = oran.destructured; val w = a.toFloat(); val h = b.toFloat(); val k = 20f / maxOf(w, h)
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size((w * k).dp, (h * k).dp).border(1.8.dp, r, RoundedCornerShape(2.dp)))
            }
        } else Simge(otoSimge(baslik), r, 20.dp)
        Text(baslik, color = r, fontSize = 11.5.sp, fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
        val a = alt.trim().replace("\n", " ")
        if (a.isNotEmpty()) Text(a, color = Renk.soluk, fontSize = 9.5.sp, textAlign = TextAlign.Center, lineHeight = 11.sp, maxLines = 2)
        Box(Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape).background(if (secili) ton else Color.Transparent))
    }
}

@Composable
private fun SecimKartiEski(baslik: String, alt: String, secili: Boolean, modifier: Modifier, etkin: Boolean, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(sekil).background(if (secili) Renk.turuncu.copy(alpha = 0.16f) else Renk.kart2)
            .border(2.dp, if (secili) Renk.turuncu else Color.Transparent, sekil)
            .clickable(enabled = etkin, onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(baslik, color = if (!etkin) Renk.soluk else if (secili) Renk.turuncu else Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(alt, color = Renk.soluk, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun Etiketli(etiket: String, deger: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(etiket, color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(deger, color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Küçük açma-kapama: 32×18, açıkken ton renginde. */
@Composable
fun AnahtarSatiri(metin: String, deger: Boolean, degis: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { degis(!deger) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(metin, color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (Duzen.yaziliMenu) { Switch(checked = deger, onCheckedChange = degis, colors = SwitchDefaults.colors(checkedTrackColor = Renk.turuncu, checkedThumbColor = Renk.turuncuM)); return@Row }
        val ton = tonRenk(-1, metin)
        Box(Modifier.size(32.dp, 18.dp).clip(CircleShape).background(if (deger) ton else Renk.cizgi), contentAlignment = if (deger) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(2.dp).size(14.dp).clip(CircleShape).background(if (deger) Color.White else Renk.soluk))
        }
    }
}

/** İnce kaydırıcı: 3 dp çizgi + 14 dp yuvarlak tutamaç (dokunma alanı Material ile aynı). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InceSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
               steps: Int = 0, onValueChangeFinished: (() -> Unit)? = null, colors: SliderColors? = null, renk: Color = Renk.turuncu, enabled: Boolean = true) {
    if (Duzen.yaziliMenu) {
        Slider(value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange, steps = steps, enabled = enabled,
            onValueChangeFinished = onValueChangeFinished, colors = colors ?: SliderDefaults.colors(thumbColor = renk, activeTrackColor = renk))
        return
    }
    val rk = if (enabled) renk else Renk.soluk.copy(alpha = 0.4f)
    Slider(value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished, enabled = enabled,
        thumb = { Box(Modifier.size(14.dp).clip(CircleShape).background(rk)) },
        track = { st ->
            val span = (st.valueRange.endInclusive - st.valueRange.start).takeIf { it > 0f } ?: 1f
            val f = ((st.value - st.valueRange.start) / span).coerceIn(0f, 1f)
            val pasif = Renk.cizgi
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val y = size.height / 2
                drawLine(pasif, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(rk, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * f, y), size.height, androidx.compose.ui.graphics.StrokeCap.Round)
            }
        })
}

@Composable
fun KlipResmi(k: Klip?, modifier: Modifier) {
    val ctx = LocalContext.current
    val b by produceState<Bitmap?>(null, k?.uri, k?.basMs) { value = k?.let { Medya.kucukResim(ctx, it) } }
    Box(modifier.background(Color.Black)) {
        b?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

fun kayitPaylas(ctx: Context) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Stüdyo hata kaydı")
        .putExtra(Intent.EXTRA_TEXT, "Stüdyo ${BuildConfigBilgi.surum(ctx)}\n\n" + Kayit.metin())
    ctx.startActivity(Intent.createChooser(i, "Hata kaydını paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

object BuildConfigBilgi {
    fun surum(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }
}

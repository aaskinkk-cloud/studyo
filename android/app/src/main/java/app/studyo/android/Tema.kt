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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var onceki by mutableStateOf(false)
    fun yukle(ctx: Context) { onceki = ctx.getSharedPreferences("ayar", 0).getBoolean("oncekiDuzen", false) }
    fun degistir(ctx: Context) { onceki = !onceki; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("oncekiDuzen", onceki).apply() }
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

/** Hap düğme (bölümün alt başlıkları). */
@Composable
fun Hap(metin: String, secili: Boolean, onClick: () -> Unit) {
    val sekil = RoundedCornerShape(17.dp)
    Box(
        Modifier.height(34.dp).clip(sekil)
            .background(if (secili) Renk.turuncu else Color.Transparent)
            .border(1.5.dp, if (secili) Renk.turuncu else Renk.cizgi, sekil)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(metin, color = if (secili) Renk.turuncuM else Renk.yazi, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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

@Composable
fun Dugme(metin: String, modifier: Modifier = Modifier, renk: Color = Renk.kart2, yaziRenk: Color = Renk.yazi, etkin: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp))
            .background(if (etkin) renk else renk.copy(alpha = 0.35f))
            .clickable(enabled = etkin, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(metin, color = if (etkin) yaziRenk else yaziRenk.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Eşit genişlikte düğme sırası (boş hücre bırakmaz). */
@Composable
fun DugmeSirasi(vararg d: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.forEach { (m, f) -> Dugme(m, Modifier.weight(1f), onClick = f) }
    }
}

@Composable
fun SecimKarti(baslik: String, alt: String, secili: Boolean, modifier: Modifier = Modifier, etkin: Boolean = true, onClick: () -> Unit) {
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

@Composable
fun AnahtarSatiri(metin: String, deger: Boolean, degis: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { degis(!deger) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(metin, color = Renk.yazi, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = deger, onCheckedChange = degis, colors = SwitchDefaults.colors(checkedTrackColor = Renk.turuncu, checkedThumbColor = Renk.turuncuM))
    }
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

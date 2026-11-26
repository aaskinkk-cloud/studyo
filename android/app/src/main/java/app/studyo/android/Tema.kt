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
import androidx.compose.ui.draw.scale
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

/** Renkler. Gece (koyu) teması varsayılan; gündüz ve 3.63 siyah-beyaz (VITA tarzı) temalar ⋯ menüsünden seçilir ve hatırlanır. Önceki temalar silinmedi. */
object Renk {
    /** 0 = gece (varsayılan), 1 = gündüz, 2 = siyah-beyaz, 3 = Karanlık Oda (3.84: Karanlık Oda + Kareden Videoya'nın ortak görünümü:
     *  grafit zemin, kehribar vurgu, sıcak kırık beyaz yazı; ana sayfa o uygulamaların düzeninde). */
    var tema by mutableIntStateOf(0)
    val gunduz get() = tema == 1
    /** 3.91: 4 = InShot (koyu grafit, gri/beyaz simgeler, turkuaz vurgu) — VITA düzeniyle (sb) aynı yerleşim. */
    val sb get() = tema == 2 || tema == 4
    val ko get() = tema == 3
    val ins get() = tema == 4
    private fun s(gece: Long, gun: Long, sbr: Long, kor: Long, ins: Long) = Color(when (tema) { 1 -> gun; 2 -> sbr; 3 -> kor; 4 -> ins; else -> gece })
    val zemin get() = s(0xFF121A21, 0xFFF2F5F8, 0xFF000000, 0xFF0F1213, 0xFF1C1C1C)
    val kart get() = s(0xFF1B2833, 0xFFFFFFFF, 0xFF141414, 0xFF171B1D, 0xFF252525)
    val kart2 get() = s(0xFF243444, 0xFFE6ECF1, 0xFF242424, 0xFF21262A, 0xFF2F2F2F)
    val cizgi get() = s(0xFF2E4153, 0xFFC5D1DB, 0xFF343434, 0xFF2D3337, 0xFF3A3A3A)
    val yazi get() = s(0xFFE6EDF3, 0xFF15212C, 0xFFFFFFFF, 0xFFE8E6E0, 0xFFFFFFFF)
    val soluk get() = s(0xFF8AA0B2, 0xFF5A6E80, 0xFF9E9E9E, 0xFF8A939A, 0xFF9A9A9A)
    /** 4.01: InShot temasında vurgu rengi (Ayarlar › Tema rengi; varsayılan turkuaz). Yalnız turkuaz olan yerler değişir. */
    var vurgu by mutableStateOf(0xFF3FC1B8L)
    val VURGULAR = listOf(0xFF3FC1B8L to "Turkuaz", 0xFF4C8DF6L to "Mavi", 0xFF9B7BF7L to "Mor", 0xFF4CC38AL to "Yeşil",
        0xFFF5A524L to "Turuncu", 0xFFEF5350L to "Kırmızı", 0xFFEC6FA5L to "Pembe", 0xFFF2C94CL to "Sarı", 0xFFE6E6E6L to "Beyaz")
    /** 4.26 KÖK NEDEN ("tema rengine tıklıyorum, değişmiyor"): seçilen renk yalnız InShot temasında kullanılıyordu. Artık her temada:
     *  elle renk seçilince o renk geçerli; "Tema" seçeneği temanın kendi rengine döndürür. */
    var vurguElle by mutableStateOf(false)
    fun vurguSec(ctx: Context, v: Long) { vurgu = v; vurguElle = true; ctx.getSharedPreferences("ayar", 0).edit().putLong("vurguIns", v).putBoolean("vurguElle", true).apply() }
    fun vurguTema(ctx: Context) { vurguElle = false; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("vurguElle", false).apply() }
    private val vurguKoyu get() = (androidx.compose.ui.graphics.Color(vurgu).let { 0.299f * it.red + 0.587f * it.green + 0.114f * it.blue }) > 0.55f
    val turuncu get() = if (ins || vurguElle) Color(vurgu) else s(0xFFF5A524, 0xFFEE9A0C, 0xFFFFFFFF, 0xFFF0993C, 0xFF3FC1B8)
    val turuncuM get() = if (ins || vurguElle) Color(if (vurguKoyu) 0xFF0E1414 else 0xFFFFFFFF) else s(0xFF1B1204, 0xFF1B1204, 0xFF000000, 0xFF1B1206, 0xFF06201E)
    val turkuaz get() = if (ins || vurguElle) Color(vurgu) else s(0xFF2EC5D3, 0xFF0E9FAD, 0xFFD6D6D6, 0xFF6CB8AC, 0xFF3FC1B8)
    val kirmizi get() = s(0xFFEF5350, 0xFFD93A36, 0xFFEF5350, 0xFFE0645A, 0xFFEF5350)
    val yesil get() = s(0xFF4CC38A, 0xFF16915A, 0xFF4CC38A, 0xFF7FD3A1, 0xFF4CC38A)

    /** Siyah-beyaz temada renkli simge tonları beyaza döner; diğer temalarda renk aynen kalır. */
    fun tonla(c: Color): Color = if (sb) Color(if (ins) 0xFFDADADA else 0xFFEDEDED) else c

    fun yukle(ctx: Context) {
        val sp = ctx.getSharedPreferences("ayar", 0)
        tema = sp.getInt("tema", if (sp.getBoolean("gunduz", false)) 1 else 0).coerceIn(0, 4)
        vurgu = sp.getLong("vurguIns", 0xFF3FC1B8L)
        vurguElle = sp.getBoolean("vurguElle", false)
        // 3.91 (Ahmet'in isteği): InShot renkleri ve düğmeleri temel tema — bir kez geçilir; önceki temalar seçilebilir
        if (!sp.getBoolean("temaIns1", false)) { tema = 4; sp.edit().putInt("tema", 4).putBoolean("gunduz", false).putBoolean("temaIns1", true).apply() }
    }
    fun sec(ctx: Context, t: Int) { tema = t.coerceIn(0, 4); ctx.getSharedPreferences("ayar", 0).edit().putInt("tema", tema).putBoolean("gunduz", tema == 1).apply() }
    fun degistir(ctx: Context) = sec(ctx, if (tema == 1) 0 else 1)
}

/** Editör düzeni: sade (önizleme hep büyük, InShot gibi) varsayılan; önceki düzen ⋯ menüsünden seçilebilir. */
object Duzen {
    /** Alt başlıklar: false = simge üstte + küçük yazı (varsayılan), true = önceki yazılı haplar. */
    var yaziliMenu by mutableStateOf(false)
    /** Önizlemenin ekran yüksekliğine oranı (tutamaçla %10–75; çift dokunuş = %50). Yukarı sürükleyerek video küçültülür, ayar alanı büyür. */
    var onizlemeOran by mutableFloatStateOf(0.5f)
    const val ORAN_EN_AZ = 0.10f
    const val ORAN_EN_COK = 0.85f   // 4.11: daha esnek (önceden %75)
    /** 4.11: tutamaçla elle boy verildiyse proje açılışında / ekrana dönüşte videonun oranına yeniden oturtulmaz (çift dokunuş = yeniden oturt). */
    var elleOran by mutableStateOf(false)
    fun oranKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putFloat("onizlemeOran2", onizlemeOran).putBoolean("elleOran", elleOran).apply() }
    fun yukle(ctx: Context) {
        val sp = ctx.getSharedPreferences("ayar", 0)
        // 4.23: 4.20'ye dönüş. Menüleri bozan eski "yazılı" düzen seçimi açık kaldıysa bir kez kapatılır (4.21/4.22'nin "renkli" ayarı da sıfırlanır)
        if (sp.getBoolean("yaziliMenu", false)) sp.edit().putBoolean("yaziliMenu", false).putString("menuStil", "renkli").apply()   // 4.24: yazılı isteği = renkli görünüm
        yaziliMenu = false
        onizlemeOran = sp.getFloat("onizlemeOran2", 0.5f).coerceIn(ORAN_EN_AZ, ORAN_EN_COK)
        elleOran = sp.getBoolean("elleOran", false)
        katmanYuk = sp.getFloat("katmanYuk", 240f).coerceIn(120f, 560f)
        cizelgePps = sp.getFloat("cizelgePps", 48f).coerceIn(4f, 400f)
        seritGizli = sp.getBoolean("seritGizli", false)
        kontrolUstte = sp.getBoolean("kontrolUstte3", true)   // 3.72: kontroller varsayılan olarak videonun üstünde
        anaStudyo = sp.getBoolean("anaStudyo", true)          // 3.85: web Stüdyo görünümlü ana sayfa varsayılan
        altUstte = sp.getBoolean("altUstte", true)
        kesilenEski = sp.getBoolean("kesilenEski", false)
        cipTekSatir = sp.getBoolean("cipTekSatir", false)
        sp.getString("yuzBekle", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 5 }?.forEachIndexed { i, v -> yuzBekle[i] = v }
        isikTeshis = sp.getBoolean("isikTeshis2", false)   // 4.31: kök neden bulundu, ölçüm varsayılan kapalı
        menuOlcek = sp.getFloat("menuOlcek", 0.92f).coerceIn(0.7f, 1.2f)
        menuStil = sp.getString("menuStil", "ikisi") ?: "ikisi"
        oynaticiTema = sp.getBoolean("oynaticiTema2", true)   // 4.19: varsayılan tema renginde
        oynaticiSade = sp.getBoolean("oynaticiSade", false)
        hizliGizli = sp.getBoolean("hizliGizli", false); hizliYer = sp.getString("hizliYer", "oto") ?: "oto"
        duzenleSade = sp.getBoolean("duzenleSade", true)   // 4.06: sabit Düzenle sade (çerçevesiz); eski kutulu görünüm Ayarlar'da seçenek
        duzenleSabit = true   // 4.01: Sabitle düğmesi kaldırıldı — Düzenle hep sabit   // 3.89: Düzenle düğmesi varsayılan sabit (yalnız düğme)
    }
    fun degistir(ctx: Context) { yaziliMenu = !yaziliMenu; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("yaziliMenu", yaziliMenu).apply() }
    /** Katman bölümündeki çok satırlı çizelgenin yüksekliği (dp; tutamaçla 120–560, çift dokunuş = 240). */
    var katmanYuk by mutableFloatStateOf(240f)
    fun katmanYukKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putFloat("katmanYuk", katmanYuk).apply() }
    /** Çok satırlı çizelgenin yakınlığı (dp / saniye; iki parmakla değişir). */
    var cizelgePps by mutableFloatStateOf(48f)
    fun ppsKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putFloat("cizelgePps", cizelgePps).apply() }
    /** 3.71: siyah-beyaz temada oynatma kontrolleri videonun üstünde mi (şeffaf) yoksa altında mı (VITA satırı). */
    var kontrolUstte by mutableStateOf(true)
    fun kontrolDegis(ctx: Context) { kontrolUstte = !kontrolUstte; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("kontrolUstte3", kontrolUstte).apply() }
    /** 3.85: ana sayfa web Stüdyo görünümünde mi (açık zemin, mavi/turuncu kartlar, alt gezinme). false = önceki ana sayfalar. */
    var anaStudyo by mutableStateOf(true)
    /** 4.06: sabit Düzenle düğmesi — true: sade (diğer menüler gibi, ayırıcı çizgiyle), false: önceki kutulu, renkli çerçeveli görünüm. */
    var duzenleSade by mutableStateOf(true)
    /** 4.14: alt menüler küçük satır olarak üstte (başında bölüm adı sabit), ana menü altta serbest ve seçili bölüm renkli. false = önceki düzen (alt menüler ana menünün yerinde). */
    var altUstte by mutableStateOf(true)
    /** 4.15: Kesilenler'in önceki liste görünümü (seçenek). */
    var kesilenEski by mutableStateOf(false)
    /** 4.16: sayfa çipleri — false (varsayılan) = ayrı satır (4.14 düzeni), true = alt menü satırında tek satır (4.15). */
    var cipTekSatir by mutableStateOf(false)
    /** 4.39: yüz turunda durakta bekleme (sn) — 1…5 kişi (5 = 5 ve fazlası). Varsayılan 2 · 2 · 3 · 4 · 5. */
    val yuzBekle = mutableStateListOf(2f, 2f, 3f, 4f, 5f)
    fun yuzBekleSn(kisi: Int): Float = yuzBekle[(kisi.coerceIn(1, 5)) - 1]
    fun yuzBekleKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putString("yuzBekle", yuzBekle.joinToString(",")).apply() }
    /** 4.30: geçiş aydınlanması teşhisi — öğe sınırlarında parlaklığı Kayıt'a yazar (sorun çözülünce kaldırılacak). */
    var isikTeshis by mutableStateOf(false)
    /** 4.17: menü boyutu (ana menü, alt menü, sayfa düğmeleri): 0,85 küçük · 0,92 orta (varsayılan, bir tık küçük) · 1 büyük · 1,1 daha büyük. */
    var menuOlcek by mutableFloatStateOf(0.92f)
    /** 4.18: menü görünümü — "ikisi" (simge + yazı) · "simge" · "yazi"; bütün menülerde (ana, alt, sayfa düğmeleri). */
    var menuStil by mutableStateOf("ikisi")
    fun menuStilSec(ctx: Context, v: String) { menuStil = v; ctx.getSharedPreferences("ayar", 0).edit().putString("menuStil", v).apply() }
    /** 4.18: önizlemedeki oynatma düğmeleri ve kare çizgisi tema renginde. */
    var oynaticiTema by mutableStateOf(true)
    /** 4.19: oynatıcı sade — Başa / Önceki / Sonraki gizli (oynat, süre, geri al, tam ekran kalır). */
    var oynaticiSade by mutableStateOf(false)
    fun oynaticiSadeSec(ctx: Context, b: Boolean) { oynaticiSade = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("oynaticiSade", b).apply() }
    fun oynaticiTemaSec(ctx: Context, b: Boolean) { oynaticiTema = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("oynaticiTema2", b).apply() }
    /** 4.18: önizlemenin üstündeki hızlı düğmeler (katman seçiliyken): gizli mi, yeri ("oto" · "ust" · "alt" · "sol" · "sag"). */
    var hizliGizli by mutableStateOf(false)
    var hizliYer by mutableStateOf("oto")
    fun hizliKaydet(ctx: Context) { ctx.getSharedPreferences("ayar", 0).edit().putBoolean("hizliGizli", hizliGizli).putString("hizliYer", hizliYer).apply() }
    fun menuOlcekSec(ctx: Context, v: Float) { menuOlcek = v; ctx.getSharedPreferences("ayar", 0).edit().putFloat("menuOlcek", v).apply() }
    fun cipTekSatirSec(ctx: Context, b: Boolean) { cipTekSatir = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("cipTekSatir", b).apply() }
    fun kesilenEskiSec(ctx: Context, b: Boolean) { kesilenEski = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("kesilenEski", b).apply() }
    fun altUstteSec(ctx: Context, b: Boolean) { altUstte = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("altUstte", b).apply() }
    fun duzenleSadeSec(ctx: Context, b: Boolean) { duzenleSade = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("duzenleSade", b).apply() }
    fun anaStudyoSec(ctx: Context, b: Boolean) { anaStudyo = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("anaStudyo", b).apply() }
    /** 3.87: Düzenle menüsü sabit — başka bölümdeyken de Düzenle'nin menüsü altta durur. */
    var duzenleSabit by mutableStateOf(true)
    fun duzenleSabitSec(ctx: Context, b: Boolean) { duzenleSabit = b; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("duzenleSabit2", b).apply() }
    /** 3.35: büyük klip şeridi gizli (izlerken göz yormasın); seçim hatırlanır. */
    var seritGizli by mutableStateOf(false)
    fun seritDegis(ctx: Context) { seritGizli = !seritGizli; ctx.getSharedPreferences("ayar", 0).edit().putBoolean("seritGizli", seritGizli).apply() }
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
fun Kart(baslik: String, baslangicAcik: Boolean = true, hepsine: (() -> Unit)? = null, buna: (() -> Unit)? = null,
         ton: Color? = null, anahtar: Boolean? = null, anahtarDegis: ((Boolean) -> Unit)? = null, icerik: @Composable ColumnScope.() -> Unit) {
    val vurgu = ton ?: Renk.turuncu
    var acik by rememberSaveable(baslik) { mutableStateOf(baslangicAcik) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(14.dp)).background(Renk.kart)) {
        Row(
            Modifier.fillMaxWidth().clickable { acik = !acik }.padding(horizontal = 14.dp, vertical = 18.dp),   // 3.68: bant dikeyde daha kalın (12 → 18)
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (acik || ton != null) Box(Modifier.width(3.dp).height(18.dp).background(if (acik) vurgu else vurgu.copy(alpha = 0.5f)))
            if (acik || ton != null) Spacer(Modifier.width(8.dp))
            // ✓✓ başlığın başında: tüm kliplere uygula
            if (hepsine != null) Box(Modifier.padding(end = 6.dp).size(30.dp).clip(CircleShape).clickable { hepsine() }, contentAlignment = Alignment.Center) {
                Simge("cift", Renk.turuncu, 20.dp) }   // 4.14: tema renginde
            Text(baslik, color = if (acik || ton != null) vurgu else Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            // renkli anahtar: özelliği aç/kapat (kartı açıp kapatmaz)
            if (anahtar != null && anahtarDegis != null) Switch(checked = anahtar, onCheckedChange = anahtarDegis, modifier = Modifier.padding(end = 6.dp).scale(0.8f),
                colors = SwitchDefaults.colors(checkedTrackColor = vurgu, checkedThumbColor = Renk.zemin, uncheckedTrackColor = Renk.kart2, uncheckedThumbColor = Renk.soluk, uncheckedBorderColor = Renk.cizgi))
            // ✓ sağda: yalnız bu klip (seçim zaten bu klibe uygulanır) — onaylar ve kartı kapatır
            if (buna != null) Box(Modifier.padding(end = 4.dp).size(30.dp).clip(CircleShape).clickable { buna(); acik = false }, contentAlignment = Alignment.Center) {
                Simge("uygula", Renk.turuncu, 20.dp) }
            GizleGosterDugmesi(acik, vurgu) { acik = !acik }   // 3.57: belirgin düğme (önce yalnız küçük ▴/▾ işareti vardı)
        }
        if (acik) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = icerik)
    }
}

/** 3.71: uzun açıklama kapalı durur — "Nasıl çalışır?" satırına dokununca açılır. */
@Composable
fun AcilirNot(metin: String, baslik: String = "Nasıl çalışır?") {
    var acik by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable { acik = !acik }.padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(baslik, color = Renk.soluk.copy(alpha = 0.8f), fontSize = 11.5.sp)
            Spacer(Modifier.width(4.dp))
            Simge(if (acik) "h_yukari" else "h_asagi", Renk.soluk.copy(alpha = 0.55f), 11.dp)
        }
        if (acik) Text(metin, color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 3.57: bölüm başlıklarındaki gizle/göster düğmesi — renk tonlu çerçeveli hap, ok + yazı ("Gizle" / "Göster"). */
@Composable
fun GizleGosterDugmesi(acik: Boolean, ton: Color, tik: () -> Unit) {
    // 3.68: yazısız yuvarlak simge (▴ açık · ▾ kapalı); önceki yazılı hap ⋯ › "Alt başlıklar: yazılı (önceki)" seçiliyken kalır
    if (!Duzen.yaziliMenu) {
        // 3.71: yarı boyda ve silik (zeminsiz, çerçevesiz ok)
        Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = tik), contentAlignment = Alignment.Center) {
            Simge(if (acik) "h_yukari" else "h_asagi", Renk.soluk.copy(alpha = 0.55f), 13.dp) }
        return
    }
    val sekil = RoundedCornerShape(14.dp)
    Row(Modifier.height(28.dp).clip(sekil).background(ton.copy(alpha = if (acik) 0.10f else 0.20f)).border(1.dp, ton.copy(alpha = 0.7f), sekil)
        .clickable(onClick = tik).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Simge(if (acik) "h_yukari" else "h_asagi", ton, 14.dp)
        Spacer(Modifier.width(4.dp))
        Text(if (acik) "Gizle" else "Göster", color = ton, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Ortak renk tonları (alt menüyle aynı aile). 3.63: siyah-beyaz temada beyaz/gri tonlar. */
private val TON_RENKLI = longArrayOf(0xFFD9CF5A, 0xFF86C06E, 0xFF3FBFAE, 0xFFE8A06A, 0xFFDB7F93)
private val TON_SB = longArrayOf(0xFFFFFFFF, 0xFFE6E6E6, 0xFFF0F0F0, 0xFFDADADA, 0xFFEBEBEB)
val TON: LongArray get() = if (Renk.sb) TON_SB else TON_RENKLI
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
        h("kes", "böl") -> "makas"; h("takip", "nokta") -> "takip"; h("logo") -> "logo"; h("kapak", "kare") -> "kapak"
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

/** Sade düğme, simgesi açıkça verilir (Dugme gibi: arka plan yok, simge + küçük ince yazı). */
@Composable
fun SimgeDugme(simge: String, metin: String, modifier: Modifier = Modifier, renk: Color = Renk.yazi, etkin: Boolean = true, onClick: () -> Unit) {
    val r = if (etkin) renk else Renk.soluk.copy(alpha = 0.35f)
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = etkin, onClick = onClick).padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Simge(simge, r, 20.dp)
        Text(metin, color = r, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
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
fun SecimKarti(baslik: String, alt: String, secili: Boolean, modifier: Modifier = Modifier, etkin: Boolean = true, sira: Int = -1, simge: String? = null, onClick: () -> Unit) {
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
        } else Simge(simge ?: otoSimge(baslik), r, 20.dp)
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
        // Paylaşılan uygulamalar uzun metni ~20 KB'de kesiyor (en yeni satırlar kayboluyordu): yalnız son 16 KB gönderilir
        .putExtra(Intent.EXTRA_TEXT, "Stüdyo ${BuildConfigBilgi.surum(ctx)}\n\n" + Kayit.metin().takeLast(16_000))
    ctx.startActivity(Intent.createChooser(i, "Hata kaydını paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

object BuildConfigBilgi {
    fun surum(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }
}

/** 4.39: "BÜYÜK" menü görünümü (YouTube düzeni gibi) — başlıklar büyük harf, daha iri; simgeler gizli. */
fun menuAd(s: String): String = if (Duzen.menuStil == "buyuk") s.uppercase(java.util.Locale("tr")) else s
val menuYaziK: Float get() = if (Duzen.menuStil == "buyuk") 1.2f else 1f

package app.studyo.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Cam fırçası: kesilmiş nesnede cam (bardak vb.) bölgeleri yarı saydam yapar.
 *  Cam = özgün görüntü, parlaklığa göre saydamlık (parlak yansımalar belirgin, koyu yerler saydam) · Geri getir = özgün, tam opak · Sil = tamamen saydam.
 *  Fırça izleri ayrı maskelerde tutulur; birleşim her darbe sonunda yeniden hesaplanır. */
private class FircaDurumu(val taban: Bitmap, val orj: Bitmap) {
    val w = taban.width; val h = taban.height
    val maskeler = Array(3) { Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8) }   // 0 cam, 1 geri getir, 2 sil
    private val tuval = maskeler.map { AndroidCanvas(it) }
    private val geri = ArrayDeque<Array<Bitmap>>()
    val sonuc: Bitmap = taban.copy(Bitmap.Config.ARGB_8888, true)
    private val tb = IntArray(w * h).also { taban.getPixels(it, 0, w, 0, 0, w, h) }
    private val ob = IntArray(w * h).also { orj.getPixels(it, 0, w, 0, 0, w, h) }

    fun kaydetGeriAl() { geri.addLast(Array(3) { maskeler[it].copy(Bitmap.Config.ALPHA_8, true) }); if (geri.size > 8) geri.removeFirst() }
    fun geriAl(): Boolean {
        val s = geri.removeLastOrNull() ?: return false
        for (i in 0..2) { maskeler[i].eraseColor(0); tuval[i].drawBitmap(s[i], 0f, 0f, null) }
        return true
    }

    fun ciz(kip: Int, x0: Float, y0: Float, x1: Float, y1: Float, cap: Float) {
        val yumusak = (cap * 0.25f).coerceAtLeast(1f)
        val boya = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = cap; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE; maskFilter = BlurMaskFilter(yumusak, BlurMaskFilter.Blur.NORMAL) }
        val sil = Paint(boya).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        for (i in 0..2) tuval[i].drawLine(x0, y0, x1, y1, if (i == kip) boya else sil)
    }

    fun birlestir() {
        val m = Array(3) { IntArray(w * h) }
        for (i in 0..2) maskeler[i].getPixels(m[i], 0, w, 0, 0, w, h)
        val o = IntArray(w * h)
        for (p in 0 until w * h) {
            var a = tb[p] ushr 24; var r = tb[p] shr 16 and 255; var g = tb[p] shr 8 and 255; var b = tb[p] and 255
            val q = ob[p]; val qr = q shr 16 and 255; val qg = q shr 8 and 255; val qb = q and 255
            val c = (m[0][p] ushr 24) / 255f
            if (c > 0f) {
                val parlak = (qr * 0.299f + qg * 0.587f + qb * 0.114f) / 255f
                val ca = ((0.22f + 0.63f * parlak) * 255f)
                a = (a + (ca - a) * c).roundToInt(); r = (r + (qr - r) * c).roundToInt(); g = (g + (qg - g) * c).roundToInt(); b = (b + (qb - b) * c).roundToInt()
            }
            val gg = (m[1][p] ushr 24) / 255f
            if (gg > 0f) { a = (a + (255 - a) * gg).roundToInt(); r = (r + (qr - r) * gg).roundToInt(); g = (g + (qg - g) * gg).roundToInt(); b = (b + (qb - b) * gg).roundToInt() }
            val s = (m[2][p] ushr 24) / 255f
            if (s > 0f) a = (a * (1f - s)).roundToInt()
            o[p] = (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        sonuc.setPixels(o, 0, w, 0, 0, w, h)
    }
}

@Composable
fun CamFircasi(vm: EditorDurum, n: Nesne, kapat: () -> Unit) {
    var durum by remember { mutableStateOf<FircaDurumu?>(null) }
    var surum by remember { mutableIntStateOf(0) }
    var kip by remember { mutableIntStateOf(0) }
    var cap by remember { mutableFloatStateOf(0.06f) }
    var kaydediliyor by remember { mutableStateOf(false) }
    LaunchedEffect(n.yol) {
        durum = withContext(Dispatchers.IO) {
            val t = BitmapFactory.decodeFile(n.yol)?.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
            val o0 = if (n.orjYol.isNotEmpty()) BitmapFactory.decodeFile(n.orjYol) else null
            val o = if (o0 != null && (o0.width != t.width || o0.height != t.height)) Bitmap.createScaledBitmap(o0, t.width, t.height, true) else (o0 ?: t)
            FircaDurumu(t, o.copy(Bitmap.Config.ARGB_8888, false))
        }
        if (durum == null) { vm.uyari.value = "Nesne açılamadı."; kapat() }
    }
    Dialog(onDismissRequest = kapat, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(Renk.zemin)) {
            Row(Modifier.fillMaxWidth().background(Renk.kart).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = kapat) { Text("Vazgeç", color = Renk.yazi, fontSize = 13.sp) }
                Text("Cam fırçası", color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (durum?.geriAl() == true) { durum?.birlestir(); surum++ } }) { Text("Geri al", color = Renk.yazi, fontSize = 13.sp) }
                TextButton(enabled = durum != null && !kaydediliyor, onClick = {
                    val d = durum ?: return@TextButton
                    kaydediliyor = true
                    vm.kapsam.launch {
                        val yeni = withContext(Dispatchers.IO) {
                            runCatching {
                                val f = File(File(n.yol).parentFile, yeniId() + ".png")
                                f.outputStream().use { d.sonuc.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                f
                            }.getOrNull()
                        }
                        if (yeni != null) { vm.nesneDegis(n.id) { it.copy(yol = yeni.path) }; vm.kaydetSimdi(false); vm.mesaj.value = "Cam fırçası uygulandı" }
                        else vm.uyari.value = "Kaydedilemedi."
                        kapat()
                    }
                }) { Text("Kaydet", color = Renk.turuncu, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
            val d = durum
            Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp), contentAlignment = Alignment.Center) {
                if (d == null) { CircularProgressIndicator(color = Renk.turuncu); return@Box }
                var alan by remember { mutableStateOf(IntSize(1, 1)) }
                val olc = min(alan.width.toFloat() / d.w, alan.height.toFloat() / d.h)
                val ox = (alan.width - d.w * olc) / 2f; val oy = (alan.height - d.h * olc) / 2f
                Box(Modifier.fillMaxSize().onSizeChanged { alan = it }.pointerInput(d, kip, cap, olc) {
                    detectDragGestures(onDragStart = { d.kaydetGeriAl() }, onDragEnd = { d.birlestir(); surum++ }) { ch, _ ->
                        ch.consume()
                        val a = ch.previousPosition; val b = ch.position
                        d.ciz(kip, (a.x - ox) / olc, (a.y - oy) / olc, (b.x - ox) / olc, (b.y - oy) / olc, cap * maxOf(d.w, d.h))
                        if ((ch.uptimeMillis / 120) != (ch.previousUptimeMillis / 120)) { d.birlestir(); surum++ }
                    }
                }) {
                    // dama zemin: saydam yerler görünsün
                    Canvas(Modifier.fillMaxSize()) {
                        val k = 16.dp.toPx()
                        var y = 0f; var sira = 0
                        while (y < size.height) { var x = if (sira % 2 == 0) 0f else k; while (x < size.width) { drawRect(Color(0xFF3A4A5A), Offset(x, y), Size(k, k)); x += 2 * k }; y += k; sira++ }
                    }
                    key(surum) { Image(d.sonuc.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
            }
            Column(Modifier.fillMaxWidth().background(Renk.kart).padding(12.dp)) {
                Secenekler(listOf("0" to "Cam (bardak)", "1" to "Geri getir", "2" to "Sil"), kip.toString()) { kip = it.toInt() }
                Etiketli("Fırça boyutu", "%${(cap * 100).roundToInt()}")
                Slider(value = cap, onValueChange = { cap = it }, valueRange = 0.01f..0.2f, colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu, inactiveTrackColor = Renk.cizgi))
                Text("Bardağın cam kısmını boya: parlak yansımalar kalır, gerisi yarı saydam olur; arkadaki video camın içinden görünür. Kesimde kaybolan cam kenarlarını “Geri getir”, fazlalıkları “Sil” ile düzelt.",
                    color = Renk.soluk, fontSize = 12.sp)
            }
        }
    }
}

package app.studyo.android

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 3.93: kamerayla prompter çekimi (Ahmet'in isteği). Tam ekran kamera (ön/arka), metin üstte merceğe yakın yarı saydam bantta akar.
 * Metne basılı tut = durur, bırak = kaldığı yerden devam; sürükle = geri/ileri çek (kayma sürüklerken bekler).
 * 3 sn geri sayım → kayıt. Bitince video projeye klip olarak eklenir (uygulama içi dosya) ve bir kopyası galeriye (Movies/Stüdyo) kaydedilir.
 * CameraX (ücretsiz). Hız ve yazı boyu Prompter'la ortak (okumaHiz, okumaBoy).
 */
@SuppressLint("MissingPermission")
@Composable
fun KameraPrompter(vm: EditorDurum, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val p by vm.proje.collectAsState()
    val gorunum = LocalView.current
    @Suppress("DEPRECATION") val sahip: LifecycleOwner? = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var onKamera by remember { mutableStateOf(true) }
    var kayit by remember { mutableStateOf<Recording?>(null) }
    var sayac by remember { mutableIntStateOf(0) }        // geri sayım (3,2,1)
    var sure by remember { mutableLongStateOf(0L) }
    var akiyor by remember { mutableStateOf(false) }
    var tutuyor by remember { mutableStateOf(false) }      // metne basılı tutuluyor / sürükleniyor
    var hiz by remember { mutableFloatStateOf(p.okumaHiz) }
    val boy = (p.okumaBoy * 0.8f).coerceIn(18f, 44f)
    val kaydir = rememberScrollState()
    val kapsam = rememberCoroutineScope()
    val yog = androidx.compose.ui.platform.LocalDensity.current
    val videoCap = remember { VideoCapture.withOutput(Recorder.Builder()
        .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD))).build()) }
    val onizleme = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var izinVar by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        izinVar = r.values.all { it }; if (!izinVar) vm.uyari.value = "Kamerayla çekim için kamera ve mikrofon izni gerekiyor."
    }
    LaunchedEffect(Unit) { if (!izinVar) izin.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
    DisposableEffect(Unit) { gorunum.keepScreenOn = true; vm.player.pause(); onDispose { gorunum.keepScreenOn = false; kayit?.stop() } }

    // kamerayı bağla (ön/arka değişince yeniden)
    LaunchedEffect(izinVar, onKamera) {
        if (!izinVar || sahip == null) return@LaunchedEffect
        try {
            val sag = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(ctx).get() }
            sag.unbindAll()
            val pv = Preview.Builder().build().also { it.setSurfaceProvider(onizleme.surfaceProvider) }
            sag.bindToLifecycle(sahip, if (onKamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, pv, videoCap)
        } catch (e: Exception) { Kayit.hata("kamera", e); vm.uyari.value = "Kamera açılamadı: ${e.message}" }
    }
    DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } } }

    // kendiliğinden kayma (basılı tutarken durur)
    LaunchedEffect(akiyor, tutuyor, hiz) {
        var once = 0L
        while (akiyor && !tutuyor) {
            withFrameNanos { t -> if (once != 0L) kaydir.dispatchRawDelta(with(yog) { (hiz * (t - once) / 1e9f).dp.toPx() }); once = t }
        }
    }
    LaunchedEffect(kayit) { val b = System.currentTimeMillis(); while (kayit != null) { sure = System.currentTimeMillis() - b; delay(200) } }

    fun baslat() {
        if (kayit != null || sayac > 0) return
        kapsam.launch {
            for (s in 3 downTo 1) { sayac = s; delay(1000) }
            sayac = 0
            val f = File(Depo.dizin("medya"), "kamera_${System.currentTimeMillis()}.mp4")
            kayit = videoCap.output.prepareRecording(ctx, FileOutputOptions.Builder(f).build()).withAudioEnabled()
                .start(ContextCompat.getMainExecutor(ctx)) { e ->
                    if (e is VideoRecordEvent.Finalize) {
                        kayit = null; akiyor = false
                        if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_NONE && (!f.exists() || f.length() < 1000)) {
                            Kayit.yaz("Kamera kaydı hatası: ${e.error} ${e.cause?.message}"); vm.uyari.value = "Kayıt alınamadı (${e.error})."; f.delete()
                        } else {
                            vm.ekle(listOf(Uri.fromFile(f)))
                            vm.mesaj.value = "Çekim projeye eklendi · galeriye kaydediliyor"
                            kapsam.launch(Dispatchers.IO) { galeriyeKopyala(ctx, f) }
                        }
                    }
                }
            akiyor = true
        }
    }

    Dialog(onDismissRequest = { if (kayit == null) kapat() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (izinVar) AndroidView({ onizleme }, Modifier.fillMaxSize())
            // üst bant: okunacak metin (merceğe yakın) — basılı tut = dur, sürükle = geri/ileri
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.36f).background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x99000000), Color(0x00000000))))
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); tutuyor = true
                    do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed }); tutuyor = false } }) {
                Column(Modifier.fillMaxSize().verticalScroll(kaydir).padding(start = 24.dp, end = 24.dp, top = 44.dp)) {
                    Spacer(Modifier.height(40.dp))
                    Text(p.okumaMetni.ifBlank { "Okunacak metni Ses › Seslendirme › Prompter'a yaz." }, color = Color.White, fontSize = boy.sp,
                        lineHeight = (boy * 1.35f).sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(400.dp))
                }
                Box(Modifier.align(Alignment.TopCenter).padding(top = 84.dp).fillMaxWidth().height(2.dp).background(Renk.turuncu.copy(alpha = 0.6f)))
                if (tutuyor && akiyor) Text("Durdu · bırak = devam", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            if (sayac > 0) Text("$sayac", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            // alt denetimler
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000))))
                .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (kayit != null) Text("● ${sureYaz(sure)}", color = Renk.kirmizi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hız", color = Color(0xFFCCCCCC), fontSize = 12.sp, modifier = Modifier.width(40.dp))
                    InceSlider(value = hiz, onValueChange = { hiz = it }, valueRange = 10f..120f, modifier = Modifier.weight(1f),
                        onValueChangeFinished = { val h = hiz; vm.okumaDegis { it.copy(okumaHiz = h) } })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    SimgeDugme("kapat", "Kapat", renk = Color.White, etkin = kayit == null) { kapat() }
                    SimgeDugme("basa", "Baştan", renk = Color.White) { kapsam.launch { kaydir.scrollTo(0) } }
                    Box(Modifier.size(72.dp).clip(CircleShape).background(if (kayit != null) Color.White else Renk.kirmizi).clickable {
                        if (kayit != null) kayit?.stop() else if (izinVar) baslat() else izin.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                    }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (kayit != null) 24.dp else 30.dp).clip(if (kayit != null) RoundedCornerShape(5.dp) else CircleShape).background(if (kayit != null) Renk.kirmizi else Color.White))
                    }
                    SimgeDugme(if (akiyor) "durdur" else "oynat", if (akiyor) "Kaymayı durdur" else "Kaydır", renk = Color.White) { akiyor = !akiyor }
                    SimgeDugme("degistir", if (onKamera) "Arka kamera" else "Ön kamera", renk = Color.White, etkin = kayit == null) { onKamera = !onKamera }
                }
                Text("Metne basılı tut: durur · bırak: devam eder · sürükle: geri/ileri", color = Color(0xFF999999), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Çekimin bir kopyasını galeriye (Movies/Stüdyo) yazar. */
private fun galeriyeKopyala(ctx: android.content.Context, f: File) {
    try {
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, f.name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Stüdyo"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val cr = ctx.contentResolver
        val u = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return
        cr.openOutputStream(u)?.use { o -> f.inputStream().use { it.copyTo(o) } }
        if (Build.VERSION.SDK_INT >= 29) { cv.clear(); cv.put(MediaStore.Video.Media.IS_PENDING, 0); cr.update(u, cv, null, null) }
        Kayit.yaz("Kamera çekimi galeriye kaydedildi: ${f.name}")
    } catch (e: Exception) { Kayit.hata("galeriye kaydet", e) }
}

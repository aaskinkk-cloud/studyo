package app.studyo.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** Tam ekran prompter: metin kendiliğinden kayar, sen okursun; ses o anki video konumundan itibaren seslendirme olarak eklenir. */
@Composable
fun Prompter(vm: EditorDurum, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val p by vm.proje.collectAsState()
    val kaydediyor by vm.kaydediyor.collectAsState()
    val konum by vm.konum.collectAsState()
    val bas by vm.kayitBas.collectAsState()
    val kaydir = rememberScrollState()
    var hiz by remember { mutableFloatStateOf(p.okumaHiz) }
    var boy by remember { mutableFloatStateOf(p.okumaBoy) }
    var akiyor by remember { mutableStateOf(false) }
    val gorunum = LocalView.current
    DisposableEffect(Unit) { gorunum.keepScreenOn = true; onDispose { gorunum.keepScreenOn = false; if (vm.kaydediyor.value) vm.kayitDurdur() } }
    val izin = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.kayitBaslat(); akiyor = true } else vm.uyari.value = "Kayıt için mikrofon izni gerekiyor."
    }
    // kendiliğinden kaydırma: saniyede "hiz" dp
    val yog = androidx.compose.ui.platform.LocalDensity.current
    LaunchedEffect(akiyor, hiz) {
        var once = 0L
        while (akiyor) {
            withFrameNanos { t ->
                if (once != 0L) {
                    val dt = (t - once) / 1e9f
                    val px = with(yog) { (hiz * dt).dp.toPx() }
                    kaydir.dispatchRawDelta(px)
                }
                once = t
            }
        }
    }
    LaunchedEffect(kaydediyor) { if (!kaydediyor) akiyor = false }

    Dialog(onDismissRequest = kapat, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = kapat) { Text("Kapat", color = Color.White, fontSize = 13.sp) }
                    Text(if (kaydediyor) "● Kayıt ${sureYaz((konum - bas).coerceAtLeast(0))} · video ${sureYaz(konum)}" else "Video ${sureYaz(konum)} anından kaydedilir",
                        color = if (kaydediyor) Renk.kirmizi else Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { akiyor = false; vm.kapsam.launch { kaydir.scrollTo(0) } }) { Text("Baştan", color = Color.White, fontSize = 13.sp) }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Column(Modifier.fillMaxSize().verticalScroll(kaydir).padding(horizontal = 22.dp)) {
                        Spacer(Modifier.height(260.dp))
                        Text(p.okumaMetni.ifBlank { "Seslendirme panelinde okuyacağın metni yaz." }, color = Color.White,
                            fontSize = boy.sp, lineHeight = (boy * 1.35f).sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(600.dp))
                    }
                    // okuma çizgisi
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 250.dp).fillMaxWidth().height(2.dp).background(Renk.turuncu.copy(alpha = 0.7f)))
                }
                Column(Modifier.fillMaxWidth().background(Color(0xFF151515)).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hız", color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        Slider(value = hiz, onValueChange = { hiz = it }, valueRange = 10f..120f, modifier = Modifier.weight(1f),
                            onValueChangeFinished = { val h = hiz; vm.okumaDegis { it.copy(okumaHiz = h) } },
                            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Yazı", color = Color(0xFFAAAAAA), fontSize = 12.sp, modifier = Modifier.width(56.dp))
                        Slider(value = boy, onValueChange = { boy = it }, valueRange = 20f..60f, modifier = Modifier.weight(1f),
                            onValueChangeFinished = { val b = boy; vm.okumaDegis { it.copy(okumaBoy = b) } },
                            colors = SliderDefaults.colors(thumbColor = Renk.turuncu, activeTrackColor = Renk.turuncu))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF2A2A2A)).clickable { akiyor = !akiyor }, contentAlignment = Alignment.Center) {
                            Text(if (akiyor) "Kaydırmayı durdur" else "Yalnız kaydır", color = Color.White, fontSize = 13.sp)
                        }
                        Box(Modifier.size(64.dp).clip(CircleShape).background(if (kaydediyor) Color.White else Renk.kirmizi).clickable {
                            if (kaydediyor) vm.kayitDurdur()
                            else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { vm.kayitBaslat(); akiyor = true }
                            else izin.launch(Manifest.permission.RECORD_AUDIO)
                        }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size(if (kaydediyor) 22.dp else 26.dp).clip(if (kaydediyor) RoundedCornerShape(4.dp) else CircleShape).background(if (kaydediyor) Renk.kirmizi else Color.White))
                        }
                        Text("Kırmızı düğme: kayıt + kaydırma başlar. Ses, video o andan itibaren seslendirme olarak eklenir.", color = Color(0xFF888888), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

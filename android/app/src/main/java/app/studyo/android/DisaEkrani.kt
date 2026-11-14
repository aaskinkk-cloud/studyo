package app.studyo.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun DisaEkrani(vm: EditorDurum, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val p by vm.proje.collectAsState()
    val d by DisaServisi.durum.collectAsState()
    var kisa by rememberSaveable { mutableIntStateOf(1080) }
    var kalite by rememberSaveable { mutableFloatStateOf(1f) }
    var iptalOnay by remember { mutableStateOf(false) }
    val baskaProje = DisaServisi.sonProje != null && DisaServisi.sonProje != p.id && d != DisaDurum.Bos

    LaunchedEffect(Unit) {
        val s = DisaServisi.durum.value
        if ((s is DisaDurum.Tamam || s is DisaDurum.Hata) && DisaServisi.sonProje != p.id) DisaServisi.durum.value = DisaDurum.Bos
    }
    DisposableEffect(Unit) {
        vm.onizlemeBirak()
        onDispose { vm.onizlemeGeri() }
    }
    BackHandler { kapat() }

    fun basla() {
        vm.kaydetSimdi(false)
        DisaServisi.baslat(ctx, p.id, kisa, kalite)
    }
    val izinler = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { basla() }
    val baslatIstek = {
        val gerek = buildList {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (gerek.isEmpty()) basla() else izinler.launch(gerek.toTypedArray())
    }

    // 3.28: Şablon sayfasıyla aynı TON renk ailesi
    val tSari = Color(TON[0]); val tYesil = Color(TON[1]); val tTurkuaz = Color(TON[2]); val tTuruncu = Color(TON[3]); val tPembe = Color(TON[4])
    Column(Modifier.fillMaxSize().background(Renk.zemin)) {
        Row(
            Modifier.fillMaxWidth().background(Renk.kart)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = kapat) { Text("←", color = Renk.yazi, fontSize = 22.sp) }
            Text("Dışa aktar", color = Renk.yazi, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            when (val s = d) {
                is DisaDurum.Calisiyor -> Kart("Video oluşturuluyor", ton = tTurkuaz) {
                    Text("%${s.yuzde}", color = tTurkuaz, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { s.yuzde / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = tTurkuaz, trackColor = Renk.cizgi)
                    Etiketli("Kalan süre", if (s.kalanSn >= 0) "~" + sureYaz(s.kalanSn * 1000) else "hesaplanıyor…")
                    Etiketli("Aşama", s.asama)
                    Text("Ekranı kapatabilir ya da başka uygulamaya geçebilirsin; işlem bildirimde sürer. Bitmeden ve dosya doğrulanmadan “kaydedildi” denmez.",
                        color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dugme("Duraklat", Modifier.weight(1f)) { DisaServisi.komut(ctx, "duraklat") }
                        Dugme(if (iptalOnay) "Emin misin?" else "İptal", Modifier.weight(1f), renk = if (iptalOnay) Renk.kirmizi else Renk.kart2) {
                            if (iptalOnay) { iptalOnay = false; DisaServisi.komut(ctx, "iptal") } else iptalOnay = true
                        }
                    }
                }
                is DisaDurum.Dogrulaniyor -> Kart("Doğrulanıyor", ton = tTurkuaz) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = tTurkuaz, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.asama, color = Renk.yazi)
                    }
                }
                is DisaDurum.Tamam -> Kart("Kaydedildi ve doğrulandı", ton = tYesil) {
                    Text(s.ad, color = tYesil, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    MetrikIzgara(listOf("Boyut" to Medya.mb(s.boyut), "Süre" to sureYaz(s.sureMs), "İşlem" to sureYaz(s.sn * 1000)))
                    if (s.not.isNotEmpty()) Text(s.not, color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(8.dp))
                    val oynat = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(s.uri), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (_: Exception) {}
                    }
                    val paylas = {
                        val i = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, Uri.parse(s.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        ctx.startActivity(Intent.createChooser(i, "Videoyu paylaş"))
                    }
                    if (Duzen.yaziliMenu) DugmeSirasi("▶ Oynat" to oynat, "Paylaş" to paylas, "Yeni" to { DisaServisi.durum.value = DisaDurum.Bos })
                    else TonSecici(listOf(Triple("oynat", "Oynat", "oynat"), Triple("paylas", "Paylaş", "disa"), Triple("yeni", "Yeni", "ekle")), "", ilkSira = 1) { k ->
                        when (k) { "oynat" -> oynat(); "paylas" -> paylas(); else -> DisaServisi.durum.value = DisaDurum.Bos }
                    }
                }
                is DisaDurum.Hata -> Kart("Hata") {
                    Text(s.mesaj, color = Renk.kirmizi, fontWeight = FontWeight.SemiBold)
                    if (s.ayrinti.isNotBlank()) Text(s.ayrinti, color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    DugmeSirasi("Kapat" to { DisaServisi.durum.value = DisaDurum.Bos }, "Hata kaydını paylaş" to { kayitPaylas(ctx) })
                }
                is DisaDurum.Duraklatildi -> Kart("Duraklatıldı", ton = tSari) {
                    Text(s.mesaj, color = Renk.yazi)
                    Spacer(Modifier.height(8.dp))
                    DugmeSirasi(
                        (if (s.devamEdebilir) "Kaldığı yerden devam" else "Yeniden başlat") to { DisaServisi.devam(ctx) },
                        "Vazgeç" to { DisaServisi.sifirla(ctx) },
                    )
                }
                else -> {}
            }

            if (d == DisaDurum.Bos || d is DisaDurum.Hata) {
                Kart("Çözünürlük", ton = tSari) {
                    if (Duzen.yaziliMenu) COZUNURLUKLER.chunked(2).forEach { sira ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            sira.forEach { c ->
                                val (w, h) = Disa.boyut(p.bicim, c.kisa)
                                val destek = remember(w, h) { Medya.kodlayiciVar(w, h) }
                                SecimKarti(c.ad, if (destek) "$w×$h" else "Bu telefon\ndesteklemiyor", kisa == c.kisa && destek,
                                    Modifier.weight(1f).height(72.dp), etkin = destek) { kisa = c.kisa }
                            }
                        }
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Varsayılan ayarlar biçimi: renk tonlu büyük yazı + boyut; seçilende renkli ve altında nokta
                        COZUNURLUKLER.forEachIndexed { n, c ->
                            val (w, h) = Disa.boyut(p.bicim, c.kisa)
                            val destek = remember(w, h) { Medya.kodlayiciVar(w, h) }
                            val on = kisa == c.kisa && destek
                            val ton = Color(TON[n % TON.size])
                            val r = if (!destek) Renk.soluk.copy(alpha = 0.4f) else if (on) ton else ton.copy(alpha = 0.55f)
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(enabled = destek) { kisa = c.kisa }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(c.ad, color = r, fontSize = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
                                Text(if (destek) "$w×$h" else "desteklemiyor", color = Renk.soluk.copy(alpha = if (destek) 0.9f else 0.5f), fontSize = 9.5.sp, maxLines = 1)
                                Box(Modifier.padding(top = 4.dp).size(4.dp).clip(CircleShape).background(if (on) ton else Color.Transparent))
                            }
                        }
                    }
                }
                Kart("Kalite", ton = tYesil) {
                    if (Duzen.yaziliMenu) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecimKarti("Normal", "${Disa.bitHizi(kisa, 1f) / 1_000_000} Mbit/sn", kalite == 1f, Modifier.weight(1f)) { kalite = 1f }
                        SecimKarti("Yüksek", "${Disa.bitHizi(kisa, 1.6f) / 1_000_000} Mbit/sn", kalite == 1.6f, Modifier.weight(1f)) { kalite = 1.6f }
                    } else TonSecici(listOf(Triple("1", "Normal", "uygula"), Triple("1.6", "Yüksek", "yildiz")), if (kalite == 1f) "1" else "1.6", ilkSira = 1,
                        alt = listOf("${Disa.bitHizi(kisa, 1f) / 1_000_000} Mbit/sn · küçük dosya", "${Disa.bitHizi(kisa, 1.6f) / 1_000_000} Mbit/sn · en net")) { k -> kalite = if (k == "1") 1f else 1.6f }
                }
                Kart("Video oluştur", ton = tTuruncu) {
                    val tahmin = Disa.tahminiBayt(p, kisa, kalite)
                    val gerek = Disa.gerekenBos(tahmin)
                    val bos = remember(kisa, kalite) { Disa.bosAlan(ctx) }
                    val yeter = bos < 0 || bos >= gerek
                    val (w, h) = Disa.boyut(p.bicim, kisa)
                    if (Duzen.yaziliMenu) {
                        Etiketli("Süre · klip", "${sureYaz(p.toplamMs)} · ${p.klipler.size} klip")
                        Etiketli("Çıktı", "$w×$h · H.264 + AAC · MP4")
                        Etiketli("Tahmini boyut", "~" + Medya.mb(tahmin))
                        Etiketli("Boş alan", Medya.mb(bos) + if (yeter) "" else " (yetersiz)")
                    } else MetrikIzgara(listOf("Süre" to "${sureYaz(p.toplamMs)} · ${p.klipler.size} klip", "Çıktı" to "$w×$h", "Tahmini boyut" to "~" + Medya.mb(tahmin),
                        "Boş alan" to (Medya.mb(bos) + if (yeter) "" else " · yetersiz")), uyari = if (yeter) -1 else 3)
                    if (p.klipler.any { it.hdr }) Text("HDR klipler SDR'ye dönüştürülür (renkler her telefonda doğru görünür).", color = Renk.soluk, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
                    if (!yeter) Text("Yer yetersiz: yaklaşık ${Medya.mb(gerek)} boş alan gerekir (geçici dosya + galeriye kopya).", color = Renk.kirmizi, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    if (baskaProje) Text("Başka bir projenin dışa aktarması sürüyor ya da yarım kaldı. Önce onu bitir ya da vazgeç.", color = Renk.kirmizi, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                    Dugme("Videoyu oluştur", Modifier.fillMaxWidth().height(54.dp), renk = Renk.turuncu, yaziRenk = Renk.turuncuM,
                        etkin = yeter && p.klipler.isNotEmpty() && (d == DisaDurum.Bos || d is DisaDurum.Hata)) {
                        DisaServisi.durum.value = DisaDurum.Bos
                        baslatIstek()
                    }
                    Text("Kayıt yeri: Galeri › Movies/Stüdyo", color = Renk.soluk, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Kart("Kesintisiz çalışma", baslangicAcik = false, ton = tPembe) {
                    Text("Uzun videolarda Samsung pil tasarrufu işlemi durdurabilir. Stüdyo'yu “Kısıtlamasız” yaparsan ekran kapalıyken de güvenle sürer.",
                        color = Renk.soluk, fontSize = 12.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Dugme("Pil ayarını aç", Modifier.fillMaxWidth()) { pilAyari(ctx) }
                }
            }
            Spacer(Modifier.height(24.dp).background(Color.Transparent))
        }
    }
}


/** Özet kutuları (2 sütun): renk tonlu değer, altında küçük etiket. uyari: kırmızı gösterilecek kutunun sırası. */
@Composable
private fun MetrikIzgara(liste: List<Pair<String, String>>, uyari: Int = -1) {
    liste.chunked(2).forEachIndexed { s, sira ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sira.forEachIndexed { j, (etiket, deger) ->
                val n = s * 2 + j
                val ton = if (n == uyari) Renk.kirmizi else Color(TON[n % TON.size])
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Renk.kart2).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(deger, color = ton, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(etiket, color = Renk.soluk, fontSize = 11.sp)
                }
            }
            if (sira.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

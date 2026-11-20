package app.studyo.android

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import kotlinx.coroutines.delay
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** 3.73: Google Cast ayarı — Google'ın ücretsiz "Varsayılan medya alıcısı" (Default Media Receiver); hesap/ücret gerekmez. */
class CastAyar : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/** 3.73: dışa aktarılan videoyu YouTube'a yüklemeden televizyonda (Google TV / Chromecast) tam ekran oynatma.
 *  Telefon, videoyu ev Wi-Fi'ında kendi küçük sunucusundan verir (yalnız o video, rastgele adresle); TV doğrudan telefondan okur.
 *  İnternet kotası harcanmaz; telefon ve TV aynı Wi-Fi'da olmalı. */
object TvYayin {
    private var sunucu: ServerSocket? = null
    @Volatile private var yayinUri: Uri? = null
    @Volatile private var anahtar: String = ""
    private var dinleyiciKuruldu = false

    /** Son dışa aktarılan video (proje başına): "uri|ad". */
    fun ciktiKaydet(ctx: Context, projeId: String, uri: String, ad: String) {
        ctx.getSharedPreferences("ciktilar", 0).edit().putString(projeId, "$uri|$ad").apply()
    }
    fun cikti(ctx: Context, projeId: String): Pair<String, String>? =
        ctx.getSharedPreferences("ciktilar", 0).getString(projeId, null)?.let { s -> s.substringBefore('|') to s.substringAfter('|', "Video") }

    fun ip(): String? = runCatching {
        val hepsi = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        val sirali = hepsi.sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
        sirali.flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    /** Videoyu yerel ağda yayınlar; TV'nin açacağı adres. */
    @Synchronized
    fun yayinla(ctx: Context, uri: Uri): String? {
        val ip = ip() ?: return null
        yayinUri = uri
        anahtar = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        if (sunucu == null || sunucu?.isClosed == true) {
            val s = ServerSocket(0)
            sunucu = s
            val app = ctx.applicationContext
            thread(isDaemon = true, name = "tv-sunucu") {
                while (!s.isClosed) {
                    val c = try { s.accept() } catch (_: Exception) { break }
                    thread(isDaemon = true) { try { hizmet(app, c) } catch (_: Exception) {} finally { try { c.close() } catch (_: Exception) {} } }
                }
            }
        }
        return "http://$ip:${sunucu!!.localPort}/$anahtar.mp4"
    }

    @Synchronized
    fun durdur() {
        try { sunucu?.close() } catch (_: Exception) {}
        sunucu = null; yayinUri = null
    }

    /** HTTP: GET/HEAD, Range (TV ileri/geri sarabilsin). Yalnız rastgele adresteki tek video verilir. */
    private fun hizmet(ctx: Context, s: Socket) {
        s.soTimeout = 30_000
        val gir = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
        val ilk = gir.readLine() ?: return
        val p = ilk.split(" ")
        val yontem = p.getOrNull(0) ?: return
        val yol = p.getOrNull(1) ?: ""
        var aralik: String? = null
        while (true) {
            val l = gir.readLine() ?: break
            if (l.isEmpty()) break
            if (l.startsWith("Range:", ignoreCase = true)) aralik = l.substringAfter(':').trim()
        }
        val out = BufferedOutputStream(s.getOutputStream(), 64 * 1024)
        fun yanit(kod: String) { out.write("HTTP/1.1 $kod\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1)); out.flush() }
        val u = yayinUri
        if (u == null || yol.substringBefore('?') != "/$anahtar.mp4") { yanit("404 Not Found"); return }
        val pfd = try { ctx.contentResolver.openFileDescriptor(u, "r") } catch (_: Exception) { null } ?: run { yanit("404 Not Found"); return }
        pfd.use {
            val boy = pfd.statSize.takeIf { it > 0 } ?: run { yanit("500 Internal Server Error"); return }
            var a = 0L; var b = boy - 1; var kismi = false
            aralik?.takeIf { it.startsWith("bytes=") }?.removePrefix("bytes=")?.split(",")?.firstOrNull()?.trim()?.let { r ->
                val x = r.substringBefore('-').trim(); val y = r.substringAfter('-', "").trim()
                if (x.isNotEmpty()) { a = x.toLongOrNull() ?: 0L; if (y.isNotEmpty()) b = minOf(y.toLongOrNull() ?: (boy - 1), boy - 1) }
                else if (y.isNotEmpty()) a = (boy - (y.toLongOrNull() ?: boy)).coerceAtLeast(0)
                kismi = true
            }
            if (a >= boy || a > b) {
                out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$boy\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1)); out.flush(); return
            }
            val n = b - a + 1
            val bas = StringBuilder()
                .append(if (kismi) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                .append("Content-Type: video/mp4\r\nAccept-Ranges: bytes\r\nAccess-Control-Allow-Origin: *\r\n")
                .append("Content-Length: $n\r\n")
            if (kismi) bas.append("Content-Range: bytes $a-$b/$boy\r\n")
            bas.append("Connection: close\r\n\r\n")
            out.write(bas.toString().toByteArray(Charsets.ISO_8859_1))
            if (yontem.equals("GET", ignoreCase = true)) FileInputStream(pfd.fileDescriptor).use { fis ->
                fis.channel.position(a)
                val tampon = ByteArray(64 * 1024); var kalan = n
                while (kalan > 0) {
                    val r = fis.read(tampon, 0, minOf(tampon.size.toLong(), kalan).toInt())
                    if (r <= 0) break
                    out.write(tampon, 0, r); kalan -= r
                }
            }
            out.flush()
        }
    }

    fun castBaglami(ctx: Context): CastContext? = runCatching {
        @Suppress("DEPRECATION") CastContext.getSharedInstance(ctx.applicationContext)
    }.onFailure { Kayit.hata("Cast başlatılamadı", it) }.getOrNull()

    /** Oturum biterse (TV'den ya da telefondan) yerel sunucu kapanır. */
    fun dinleyiciKur(c: CastContext) {
        if (dinleyiciKuruldu) return
        dinleyiciKuruldu = true
        c.sessionManager.addSessionManagerListener(object : OturumDinleyici() {
            override fun onSessionEnded(session: CastSession, error: Int) { durdur() }
        }, CastSession::class.java)
    }
}

/** SessionManagerListener'ın bütün yöntemleri boş; gerekeni ezilir. */
open class OturumDinleyici : SessionManagerListener<CastSession> {
    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionStarted(session: CastSession, sessionId: String) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionEnding(session: CastSession) {}
    override fun onSessionEnded(session: CastSession, error: Int) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
}

/** 3.73: "Televizyonda izle" penceresi — cihaz seç (Google TV / Chromecast), video TV'de tam ekran oynar;
 *  telefondan oynat/duraklat, 10 sn geri/ileri, sarma çubuğu, bağlantıyı kes. Pencere kapansa da TV oynamaya devam eder. */
@Composable
fun TvPenceresi(uri: String, ad: String, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val cast = remember { TvYayin.castBaglami(ctx) }
    var durum by remember { mutableStateOf("Televizyon aranıyor… (telefon ve TV aynı Wi-Fi'da olmalı)") }
    var bagli by remember { mutableStateOf(cast?.sessionManager?.currentCastSession?.isConnected == true) }
    var cihazAdi by remember { mutableStateOf(cast?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "") }
    var cihazlar by remember { mutableStateOf(listOf<MediaRouter.RouteInfo>()) }
    var oynuyor by remember { mutableStateOf(false) }
    var konum by remember { mutableLongStateOf(0L) }
    var sure by remember { mutableLongStateOf(0L) }
    var yuklendi by remember { mutableStateOf(false) }

    fun yukle(s: CastSession) {
        val url = TvYayin.yayinla(ctx, Uri.parse(uri))
        if (url == null) { durum = "Telefon Wi-Fi'ye bağlı değil. TV ile aynı Wi-Fi'a bağlan."; return }
        val md = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply { putString(MediaMetadata.KEY_TITLE, ad) }
        val mi = MediaInfo.Builder(url).setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType("video/mp4").setMetadata(md).build()
        val rmc = s.remoteMediaClient
        if (rmc == null) { durum = "TV oynatıcısı hazır değil, yeniden dene."; return }
        rmc.load(MediaLoadRequestData.Builder().setMediaInfo(mi).setAutoplay(true).build())
        yuklendi = true; durum = "TV'de oynatılıyor"
        Kayit.yaz("TV'ye gönderildi: $ad → ${s.castDevice?.friendlyName}")
    }

    if (cast != null) {
        val router = remember { MediaRouter.getInstance(ctx) }
        val secici = remember {
            cast.mergedSelector ?: MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()
        }
        DisposableEffect(Unit) {
            TvYayin.dinleyiciKur(cast)
            fun yenile() { cihazlar = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(secici) } }
            val cb = object : MediaRouter.Callback() {
                override fun onRouteAdded(r: MediaRouter, route: MediaRouter.RouteInfo) = yenile()
                override fun onRouteRemoved(r: MediaRouter, route: MediaRouter.RouteInfo) = yenile()
                override fun onRouteChanged(r: MediaRouter, route: MediaRouter.RouteInfo) = yenile()
            }
            router.addCallback(secici, cb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            yenile()
            val dinle = object : OturumDinleyici() {
                override fun onSessionStarted(session: CastSession, sessionId: String) { bagli = true; cihazAdi = session.castDevice?.friendlyName ?: ""; yukle(session) }
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) { bagli = true; cihazAdi = session.castDevice?.friendlyName ?: "" }
                override fun onSessionStartFailed(session: CastSession, error: Int) { bagli = false; durum = "TV'ye bağlanılamadı (kod $error). Yeniden dene." }
                override fun onSessionEnded(session: CastSession, error: Int) { bagli = false; yuklendi = false; durum = "Bağlantı kesildi" }
            }
            cast.sessionManager.addSessionManagerListener(dinle, CastSession::class.java)
            onDispose {
                router.removeCallback(cb)
                cast.sessionManager.removeSessionManagerListener(dinle, CastSession::class.java)
            }
        }
        // zaten bağlıysa doğrudan gönder
        LaunchedEffect(Unit) { cast.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { yukle(it) } }
        // TV'deki oynatma durumu
        LaunchedEffect(bagli) {
            while (bagli) {
                val rmc = cast.sessionManager.currentCastSession?.remoteMediaClient
                oynuyor = rmc?.isPlaying == true
                konum = rmc?.approximateStreamPosition ?: 0L
                sure = rmc?.streamDuration?.takeIf { it > 0 } ?: sure
                delay(500)
            }
        }
    }

    Dialog(onDismissRequest = kapat) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Renk.kart).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Simge("tv", Renk.yazi, 22.dp)
                Text("Televizyonda izle", color = Renk.yazi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = kapat), contentAlignment = Alignment.Center) { Simge("kapat", Renk.soluk, 18.dp) }
            }
            Text(ad, color = Renk.soluk, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            if (cast == null) {
                Text("Google Cast bu telefonda başlatılamadı (Google Play Hizmetleri gerekli).", color = Renk.kirmizi, fontSize = 13.sp)
                return@Column
            }
            if (!bagli) {
                Text(durum, color = Renk.soluk, fontSize = 12.5.sp)
                Spacer(Modifier.height(8.dp))
                if (cihazlar.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Renk.turuncu, strokeWidth = 2.dp)
                    Text("Aynı Wi-Fi'daki Google TV / Chromecast aranıyor…", color = Renk.yazi, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
                }
                cihazlar.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Renk.kart2)
                        .clickable { durum = "${r.name} bağlanıyor…"; MediaRouter.getInstance(ctx).selectRoute(r) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Simge("tv", Renk.turuncu, 20.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(r.name, color = Renk.yazi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            r.description?.let { Text(it, color = Renk.soluk, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Simge("oynat", Renk.turuncu, 18.dp)
                    }
                }
                AcilirNot("Video internetten değil, telefondan ev Wi-Fi'ı üzerinden TV'ye gider (kota harcanmaz). Telefon ve TV aynı Wi-Fi'da olmalı. " +
                    "TV listede yoksa: TV açık mı, aynı ağda mı, kontrol et.")
            } else {
                Text(if (cihazAdi.isNotEmpty()) "$cihazAdi · $durum" else durum, color = Renk.yesil, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (!yuklendi) TonHap("Bu videoyu TV'de oynat", "oynat", true, Renk.turuncu) { cast.sessionManager.currentCastSession?.let { yukle(it) } }
                val rmc = cast.sessionManager.currentCastSession?.remoteMediaClient
                if (sure > 0) {
                    var surukle by remember { mutableStateOf<Float?>(null) }
                    androidx.compose.material3.Slider(value = surukle ?: (konum.toFloat() / sure).coerceIn(0f, 1f), onValueChange = { surukle = it },
                        onValueChangeFinished = { surukle?.let { f -> rmc?.seek(MediaSeekOptions.Builder().setPosition((f * sure).toLong()).build()) }; surukle = null },
                        modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(sureYaz(konum), color = Renk.soluk, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                        Text(sureYaz(sure), color = Renk.soluk, fontSize = 11.5.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).clickable { rmc?.seek(MediaSeekOptions.Builder().setPosition((konum - 10_000).coerceAtLeast(0)).build()) },
                        contentAlignment = Alignment.Center) { Text("−10", color = Renk.yazi, fontSize = 14.sp) }
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Renk.turuncu.copy(alpha = 0.16f)).clickable { if (oynuyor) rmc?.pause() else rmc?.play() },
                        contentAlignment = Alignment.Center) { Simge(if (oynuyor) "durdur" else "oynat", Renk.turuncu, 28.dp) }
                    Box(Modifier.size(46.dp).clip(CircleShape).clickable { rmc?.seek(MediaSeekOptions.Builder().setPosition(konum + 10_000).build()) },
                        contentAlignment = Alignment.Center) { Text("+10", color = Renk.yazi, fontSize = 14.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimgeDugme("kapat", "Bağlantıyı kes", Modifier.weight(1f), renk = Renk.kirmizi) { cast.sessionManager.endCurrentSession(true); TvYayin.durdur(); bagli = false }
                    SimgeDugme("uygula", "Pencereyi kapat", Modifier.weight(1f)) { kapat() }
                }
                AcilirNot("Pencereyi kapatınca TV oynamaya devam eder. Video telefondan okunduğu için Stüdyo'yu kapatma; telefonu şarjda tutman iyi olur.")
            }
        }
    }
}

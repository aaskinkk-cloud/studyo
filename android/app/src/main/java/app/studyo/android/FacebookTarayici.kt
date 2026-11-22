package app.studyo.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 3.97: Stüdyo içinde Facebook (Ahmet'in seçimi: kurulumsuz). m.facebook.com uygulama içinde açılır; giriş bir kez yapılır (çerezler saklanır).
 * Fotoğraflar KULLANICI seçer — uygulama sayfaları kendi kendine taramaz:
 *  - fotoğrafa basılı tut = seç / seçimi kaldır (açık fotoğraf sayfasında büyük boy gelir),
 *  - "Sayfadakileri seç" = o an sayfada yüklü büyük fotoğrafların hepsi (küçük simgeler ve profil resimleri alınmaz).
 * "Videoya ekle": seçilenler uygulama içine indirilip klip olarak eklenir. Facebook sayfa yapısını değiştirirse seçim yolu etkilenebilir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FacebookTarayici(vm: EditorDurum, kapat: () -> Unit) {
    val ctx = LocalContext.current
    val kapsam = rememberCoroutineScope()
    val secilen = remember { mutableStateListOf<String>() }
    var yukleniyor by remember { mutableIntStateOf(0) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var indiriliyor by remember { mutableStateOf<String?>(null) }
    var hataYazi by remember { mutableStateOf<String?>(null) }
    BackHandler { val w = webRef; if (w != null && w.canGoBack()) w.goBack() else kapat() }

    fun secDegis(u: String) {
        if (u.isBlank() || !u.startsWith("http")) return
        if (u in secilen) secilen.remove(u) else secilen.add(u)
        vm.mesaj.value = if (u in secilen) "Seçildi · ${secilen.size}" else "Seçim kaldırıldı · ${secilen.size}"
    }
    val kopru = remember { object {
        @JavascriptInterface fun hepsi(s: String) { android.os.Handler(android.os.Looper.getMainLooper()).post {
            val l = s.split('\n').map { it.trim() }.filter { it.startsWith("http") }
            var n = 0; for (u in l) if (u !in secilen) { secilen.add(u); n++ }
            vm.mesaj.value = if (l.isEmpty()) "Bu sayfada büyük fotoğraf bulunamadı · fotoğrafı açıp basılı tut" else "$n fotoğraf seçildi · toplam ${secilen.size}"
        } }
    } }

    fun ekle() {
        val l = secilen.toList(); if (l.isEmpty()) { vm.mesaj.value = "Önce fotoğrafa basılı tutarak seç"; return }
        kapsam.launch {
            val dizin = File(Depo.dizin("albumler"), "facebook").apply { mkdirs() }
            val cerez = CookieManager.getInstance()
            val inen = ArrayList<Uri>(); var hata = 0
            for ((i, u) in l.withIndex()) {
                indiriliyor = "İndiriliyor ${i + 1}/${l.size}…"
                val r = withContext(Dispatchers.IO) { runCatching {
                    val ad = "fb_" + Integer.toHexString(u.substringBefore('?').hashCode()) + ".jpg"
                    val f = File(dizin, ad)
                    if (!f.exists() || f.length() < 1000) {
                        val c = URL(u).openConnection() as HttpURLConnection
                        c.connectTimeout = 15000; c.readTimeout = 30000
                        c.setRequestProperty("User-Agent", WebViewUa.ua)
                        cerez.getCookie(u)?.let { c.setRequestProperty("Cookie", it) }
                        c.inputStream.use { inp -> f.outputStream().use { inp.copyTo(it) } }
                        if (f.length() < 1000) { f.delete(); error("boş dosya") }
                    }
                    Uri.fromFile(f)
                } }
                r.onSuccess { inen += it }.onFailure { hata++; Kayit.hata("facebook indir", it) }
            }
            indiriliyor = null
            if (inen.isEmpty()) { vm.uyari.value = "Fotoğraflar indirilemedi (hata kaydında)."; return@launch }
            vm.ekle(inen)
            vm.mesaj.value = "${inen.size} fotoğraf eklendi" + if (hata > 0) " · $hata indirilemedi" else ""
            secilen.clear(); kapat()
        }
    }

    Dialog(onDismissRequest = kapat, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Renk.zemin)) {
            Row(Modifier.fillMaxWidth().background(Renk.kart).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                .padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SimgeDugme("kapat", "Kapat", renk = Renk.yazi) { kapat() }
                SimgeDugme("geri", "Geri", renk = Renk.yazi) { webRef?.let { if (it.canGoBack()) it.goBack() } }
                SimgeDugme("kisi", "Fotoğraflarım", renk = Renk.yazi) { webRef?.loadUrl("https://www.facebook.com/me/photos") }
                SimgeDugme("katman", "Albümlerim", renk = Renk.yazi) { webRef?.loadUrl("https://www.facebook.com/me/photos_albums") }
                Spacer(Modifier.weight(1f))
                // 3.99: kaçış yolu — açık sayfayı Chrome'da aç (oradan kaydedip Paylaş › Stüdyo)
                SimgeDugme("disa", "Chrome'da", renk = Renk.yazi) {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(webRef?.url ?: "https://www.facebook.com/")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                SimgeDugme("uygula", "Sayfadakileri seç", renk = Renk.turuncu) { webRef?.evaluateJavascript(SAYFA_JS, null) }
            }
            if (yukleniyor in 1..99) LinearProgressIndicator(progress = { yukleniyor / 100f }, Modifier.fillMaxWidth().height(2.dp), color = Renk.turuncu)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                        settings.builtInZoomControls = false; settings.setSupportZoom(false)
                        settings.javaScriptCanOpenWindowsAutomatically = false; settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        // 3.98 KÖK DÜZELTME: Facebook uygulama içi tarayıcıyı kimlikteki "; wv" ve "Version/4.0" işaretlerinden tanıyıp sayfa vermiyordu
                        settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("Version/[\\d.]+ "), "")
                        WebViewUa.ua = settings.userAgentString
                        // 3.99 KÖK DÜZELTME (giriş sayfası beyaz): uygulama içi tarayıcı her istekte "X-Requested-With: app.studyo.android" gönderiyordu;
                        // Facebook gömülü tarayıcıyı bundan tanıyıp giriş formunu çizmiyor. Başlık kapatılır (WebView 110+).
                        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
                            runCatching { androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet()) }
                                .onFailure { Kayit.hata("X-Requested-With", it) }
                        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        addJavascriptInterface(kopru, "Studyo")
                        webViewClient = object : WebViewClient() {
                            // 3.98 KÖK DÜZELTME: giriş artık meta.com vb. alan adlarından da geçiyor — yalnız facebook.com'a izin vermek girişi engelliyordu.
                            // Bütün http(s) sayfaları içeride açılır; yalnız Facebook uygulamasına atan intent:// fb:// gibi bağlantılar açılmaz.
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                val sema = r.url.scheme ?: return true
                                if (sema != "https" && sema != "http") { Kayit.yaz("Facebook: uygulama bağlantısı açılmadı ($sema)"); return true }
                                return false
                            }
                            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: android.webkit.WebResourceError) {
                                if (r.isForMainFrame) { hataYazi = "Sayfa açılamadı: ${e.description} (${e.errorCode})"; Kayit.yaz("Facebook hata: ${e.errorCode} ${e.description} ${r.url}") }
                            }
                            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: android.webkit.WebResourceResponse) {
                                if (r.isForMainFrame) { hataYazi = "Facebook yanıtı: ${e.statusCode}"; Kayit.yaz("Facebook HTTP ${e.statusCode} ${r.url}") }
                            }
                            override fun onPageStarted(v: WebView, url: String?, f: Bitmap?) { yukleniyor = 5; hataYazi = null; Kayit.yaz("Facebook sayfa: ${url?.take(120)}") }
                            override fun onPageFinished(v: WebView, url: String?) {
                                yukleniyor = 100; CookieManager.getInstance().flush()
                                // 3.99: tanı — sayfanın başlığı ve görünen yazı miktarı kayda (boş sayfanın nedeni görülsün)
                                v.evaluateJavascript("(function(){return document.title+' | yazı='+(document.body?document.body.innerText.length:-1)+' | form='+document.forms.length})()") { r ->
                                    Kayit.yaz("Facebook yüklendi: ${url?.take(70)} → $r")
                                }
                            }
                        }
                        webChromeClient = object : WebChromeClient() { override fun onProgressChanged(v: WebView, p: Int) { yukleniyor = p } }
                        // basılı tut: fotoğrafı seç / seçimi kaldır
                        setOnLongClickListener {
                            val h = hitTestResult
                            if (h.type == WebView.HitTestResult.IMAGE_TYPE || h.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                                h.extra?.let { u -> secDegis(u) }; true
                            } else false
                        }
                        loadUrl("https://www.facebook.com/")   // 3.98: ana sayfa (girişliyse akış, değilse giriş formu) — fotoğraflara üstteki düğmelerle
                        webRef = this
                    }
                }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy(); webRef = null })
                hataYazi?.let { h -> Column(Modifier.align(Alignment.Center).padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xE6000000)).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(h, color = Color.White, fontSize = 13.sp)
                    Text("Yeniden dene", color = Renk.turuncu, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
                        .clip(RoundedCornerShape(8.dp)).clickable { hataYazi = null; webRef?.loadUrl("https://www.facebook.com/") }.padding(8.dp))
                } }
                indiriliyor?.let { Text(it, color = Color.White, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp)).background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 12.dp)) }
            }
            Row(Modifier.fillMaxWidth().background(Renk.kart).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (secilen.isEmpty()) "Fotoğrafa basılı tut = seç" else "${secilen.size} fotoğraf seçili", color = Renk.yazi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Giriş bir kez yapılır · fotoğrafı açıp basılı tutarsan büyük boyu gelir", color = Renk.soluk, fontSize = 10.5.sp)
                }
                if (secilen.isNotEmpty()) Text("Temizle", color = Renk.soluk, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { secilen.clear() }.padding(8.dp))
                Text("Videoya ekle", color = if (secilen.isEmpty()) Renk.soluk else Renk.turuncuM, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (secilen.isEmpty()) Renk.kart2 else Renk.turuncu)
                        .clickable(enabled = secilen.isNotEmpty() && indiriliyor == null) { ekle() }.padding(horizontal = 16.dp, vertical = 9.dp))
            }
        }
    }
}

private object WebViewUa { var ua: String = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36" }

/** Sayfada o an yüklü, büyük (≥ 400 px) fotoğrafların adresleri (en büyük srcset adayı); profil resmi/simge gibi küçükler alınmaz. */
private const val SAYFA_JS = """(function(){var s={},o=[];document.querySelectorAll('img').forEach(function(i){
 var w=i.naturalWidth||0,h=i.naturalHeight||0;if(Math.max(w,h)<400)return;var u=i.currentSrc||i.src;
 if(i.srcset){var b=0;i.srcset.split(',').forEach(function(p){var a=p.trim().split(/\s+/),x=parseInt(a[1])||0;if(x>b){b=x;u=a[0];}});}
 if(u&&u.indexOf('http')==0&&!s[u]){s[u]=1;o.push(u);}});
 document.querySelectorAll('[style*="background-image"]').forEach(function(e){var m=(e.style.backgroundImage||'').match(/url\(["']?(https[^"')]+)/);
 if(m&&e.offsetWidth>=300&&!s[m[1]]){s[m[1]]=1;o.push(m[1]);}});Studyo.hepsi(o.join('\n'));})();"""

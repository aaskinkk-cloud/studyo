package app.studyo.android;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Stüdyo — Android kabuğu (Karanlık Oda kabuğunun aynısı: kalıcı tam ekran, büyük dosya kaydı, Cloudflare yedeği).
 * Sayfa: https://aaskinkk-cloud.github.io/studyo/ (GitHub'a gönderilen her güncelleme kendiliğinden gelir, çevrimdışı da açılır).
 * Ekran: kalıcı tam ekran (immersive). Alttaki tuşlar kaydırılınca sayfanın ÜSTÜNE biner, sayfa yeniden boyutlanmaz,
 * birkaç saniye sonra kendiliğinden gizlenir. Chrome'un "Tam ekrandan çıkmak için…" uyarısı ve "Dokunarak ara" paneli bu uygulamada yoktur.
 */
public class MainActivity extends Activity {
    private static final String ADRES = "https://aaskinkk-cloud.github.io/studyo/";
    /** GitHub açılmazsa (404, sunucu hatası, bağlantı yok) sayfa buradan açılır. */
    private static final String YEDEK = "https://ahmet-uygulamalar.pages.dev/studyo/";
    private boolean yedekte = false;
    private static final int DOSYA_SEC = 41;
    private WebView web;
    private ValueCallback<Uri[]> dosyaCb;
    private android.widget.FrameLayout kap;
    private int kameraPx = 0;

    /** Ön kamera yüksekliğini sayfaya bildirir (CSS: --kamera). Üst menü en üstte açılır, yazı kameranın altından başlar. */
    private void kameraYaz() {
        if (web == null) return;
        float d = getResources().getDisplayMetrics().density;
        int css = Math.round(kameraPx / d);
        web.evaluateJavascript("document.documentElement.style.setProperty('--kamera','" + css + "px');try{RUI&&RUI.open&&RUI.vfit()}catch(e){}", null);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES; // ekranın tamamı (kamera bölgesi dahil) kullanılır
            w.setAttributes(lp);
        }
        web = new WebView(this);
        web.setBackgroundColor(0xFF121A21);
        /* Kamera bölgesi siyah şerit olarak kalmasın: kap, sayfanın renginde boyanır; yazı kameranın altından başlar. */
        kap = new android.widget.FrameLayout(this);
        kap.setBackgroundColor(0xFF121A21);
        kap.addView(web, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(kap);
        kap.setOnApplyWindowInsetsListener((v, ins) -> {
            int ust = 0, sol = 0, sag = 0;
            if (Build.VERSION.SDK_INT >= 28 && ins.getDisplayCutout() != null) {
                ust = ins.getDisplayCutout().getSafeInsetTop();
                sol = ins.getDisplayCutout().getSafeInsetLeft();
                sag = ins.getDisplayCutout().getSafeInsetRight();
            }
            kameraPx = ust;
            kameraYaz();
            return ins;
        });

        WebView.setWebContentsDebuggingEnabled(true); // 1.3: hata kaydı (bilgisayarda chrome://inspect)
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " OkurAndroid/1");
        web.addJavascriptInterface(new Kopru(), "OkurAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { v.evaluateJavascript(INDIR_JS, null); kameraYaz(); tamEkran(); }
            @Override public void onReceivedHttpError(WebView v, android.webkit.WebResourceRequest r, android.webkit.WebResourceResponse res) {
                if (r.isForMainFrame() && res != null && (res.getStatusCode() == 404 || res.getStatusCode() >= 500)) yedegeGec(v, "HTTP " + res.getStatusCode());
            }
            /* 1.3: sayfa işlemi çökerse uygulama kapanmasın, sayfa yeniden yüklensin */
            @Override public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                runOnUiThread(() -> { Toast.makeText(MainActivity.this, "Sayfa çöktü, yeniden açılıyor", Toast.LENGTH_LONG).show(); recreate(); });
                return true;
            }
            @Override public void onReceivedError(WebView v, android.webkit.WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) yedegeGec(v, e.getErrorCode() + " " + e.getDescription());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (dosyaCb != null) dosyaCb.onReceiveValue(null);
                dosyaCb = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(i, DOSYA_SEC); } catch (Exception e) { dosyaCb = null; return false; }
                return true;
            }
        });
        if (b != null) web.restoreState(b); else web.loadUrl(ADRES);
        tamEkran();
    }

    /** Kalıcı tam ekran: sistem çubukları gizli; kaydırınca geçici görünür ve sayfanın üstüne biner.
     *  Hem yeni (Android 11+) hem eski yöntem birlikte uygulanır; pencere hazır olmadan çağrılırsa bir sonraki karede yinelenir. */
    private void tamEkran() {
        final Window w = getWindow();
        final View d = w.getDecorView();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 30) {
            Runnable r = () -> {
                WindowInsetsController c = d.getWindowInsetsController();
                if (c == null) c = w.getInsetsController();
                if (c != null) {
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            };
            r.run();
            d.post(r);
            d.postDelayed(r, 300);
        }
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) tamEkran(); }
    @Override protected void onResume() { super.onResume(); tamEkran(); if (web != null) web.onResume(); }
    @Override protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); if (web != null) web.saveState(o); }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onActivityResult(int req, int res, Intent d) {
        if (req == DOSYA_SEC && dosyaCb != null) {
            Uri[] r = null;
            if (res == RESULT_OK && d != null) {
                if (d.getClipData() != null) {
                    int n = d.getClipData().getItemCount(); r = new Uri[n];
                    for (int i = 0; i < n; i++) r[i] = d.getClipData().getItemAt(i).getUri();
                } else if (d.getData() != null) r = new Uri[]{ d.getData() };
            }
            dosyaCb.onReceiveValue(r); dosyaCb = null;
            return;
        }
        super.onActivityResult(req, res, d);
    }

    /** GitHub sayfası açılmazsa bir kez Cloudflare kopyasına geçer; o da açılmazsa yeniden deneme ekranı gösterir. */
    private int deneme = 0;
    private void yedegeGec(WebView v, String neden) {
        /* geçici aksaklıkta önce GitHub bir kez daha denenir */
        if (!yedekte && deneme == 0) { deneme = 1; v.postDelayed(() -> v.loadUrl(ADRES), 1500); return; }
        if (!yedekte) {
            yedekte = true;
            runOnUiThread(() -> { Toast.makeText(MainActivity.this, "GitHub açılmadı (" + neden + ") — yedek adresten (Cloudflare) açılıyor", Toast.LENGTH_LONG).show(); v.loadUrl(YEDEK); });
        } else {
            runOnUiThread(() -> v.loadDataWithBaseURL(null,
                "<html><body style='font:18px sans-serif;background:#121a21;color:#e8e8e8;padding:40px 24px;text-align:center'>"
                + "<p>Stüdyo şu an açılamıyor.<br>İnternet bağlantını kontrol et.</p>"
                + "<p><a href='" + ADRES + "' style='display:inline-block;margin-top:16px;padding:14px 22px;border-radius:12px;background:#c2410c;color:#fff;text-decoration:none'>Yeniden dene</a></p></body></html>",
                "text/html", "utf-8", null));
        }
    }

    private void renkUygula(String c) {
        try {
            int r = android.graphics.Color.parseColor(c.trim());
            kap.setBackgroundColor(r); web.setBackgroundColor(r); getWindow().getDecorView().setBackgroundColor(r);
        } catch (Exception e) { }
    }

    /** Sayfadan gelen dosyaları (yedek, dışa aktarım) İndirilenler klasörüne yazar. */
    /* Büyük dosyalar (video) için parça parça yazma: sayfa 1 MB'lık parçalar gönderir, sonda tek dosya olarak İndirilenler'e kaydedilir. */
    private final java.util.HashMap<String, File> parcalar = new java.util.HashMap<>();
    class Kopru {
        @JavascriptInterface public void parcaBasla(String id) {
            try { File f = File.createTempFile("st-", ".part", getCacheDir()); parcalar.put(id, f); } catch (Exception e) { }
        }
        @JavascriptInterface public void parca(String id, String b64) {
            File f = parcalar.get(id); if (f == null) return;
            try (FileOutputStream o = new FileOutputStream(f, true)) { o.write(Base64.decode(b64, Base64.DEFAULT)); } catch (Exception e) { }
        }
        @JavascriptInterface public void parcaBitir(String id, String ad, String tur) {
            File f = parcalar.remove(id); if (f == null) return;
            try {
                if (ad == null || ad.trim().isEmpty()) ad = "studyo";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, ad);
                    cv.put(MediaStore.Downloads.MIME_TYPE, (tur == null || tur.isEmpty()) ? "application/octet-stream" : tur);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream o = getContentResolver().openOutputStream(u); java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                    }
                } else {
                    File hedef = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ad);
                    try (FileOutputStream o = new FileOutputStream(hedef); java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                    }
                }
                final String m = ad;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "İndirilenler'e kaydedildi: " + m, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally { f.delete(); }
        }
        /** 1.3: aynı sayfayı Chrome'da açar (önizleme bu uygulamada çökerse kaçış yolu). */
        @JavascriptInterface public void disAc(String url) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { i.setPackage("com.android.chrome"); startActivity(i); }
                catch (Exception e) { try { i.setPackage(null); startActivity(i); } catch (Exception e2) { Toast.makeText(MainActivity.this, "Tarayıcı açılamadı", Toast.LENGTH_LONG).show(); } }
            });
        }
        @JavascriptInterface public void renk(String c) { runOnUiThread(() -> renkUygula(c)); }
        @JavascriptInterface public void kaydet(String ad, String tur, String b64) {
            try {
                byte[] veri = Base64.decode(b64, Base64.DEFAULT);
                if (ad == null || ad.trim().isEmpty()) ad = "studyo";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Downloads.DISPLAY_NAME, ad);
                    cv.put(MediaStore.Downloads.MIME_TYPE, (tur == null || tur.isEmpty()) ? "application/octet-stream" : tur);
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(veri); }
                } else {
                    File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ad);
                    try (FileOutputStream o = new FileOutputStream(f)) { o.write(veri); }
                }
                final String m = ad;
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "İndirilenler'e kaydedildi: " + m, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    /** <a download href="blob:..."> tıklamalarını yakalayıp dosyayı Kopru.kaydet'e gönderir. */
    private static final String INDIR_JS =
        "(function(){try{var m=function(){var x=document.querySelector('meta[name=theme-color]');if(x&&window.OkurAndroid)OkurAndroid.renk(x.content)};m();" +
        "new MutationObserver(m).observe(document.head,{subtree:true,attributes:true,childList:true,attributeFilter:['content']})}catch(e){}})();" +
        "(function(){if(window.__okurIndir)return;window.__okurIndir=1;var o=HTMLAnchorElement.prototype.click;" +
        "HTMLAnchorElement.prototype.click=function(){var h=this.href||'',ad=this.getAttribute('download');" +
        "if(ad!==null&&/^(blob:|data:)/.test(h)&&window.OkurAndroid){fetch(h).then(function(r){return r.blob()}).then(function(b){" +
        "var id=Date.now()+''+Math.random(),P=1048576,ofs=0;OkurAndroid.parcaBasla(id);" +
        "var git=function(){if(ofs>=b.size){OkurAndroid.parcaBitir(id,ad||'studyo',b.type||'');return}var fr=new FileReader();" +
        "fr.onload=function(){var s=String(fr.result);OkurAndroid.parca(id,s.slice(s.indexOf(',')+1));ofs+=P;setTimeout(git,0)};fr.readAsDataURL(b.slice(ofs,ofs+P))};git()});return}" +
        "return o.apply(this,arguments)}})();";
}

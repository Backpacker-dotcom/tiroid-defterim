package com.backpacker.tiroiddefterim;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Uygulama sayfasını sabit bir adresten (https://tiroid.local/) sunar; böylece kayıtlar
 *  güncellemelerden sonra da aynı yerde kalır. Yeni sürüm GitHub'dan arka planda indirilir. */
public class MainActivity extends Activity {
    static final String HOST = "tiroid.local";
    static final String START = "https://" + HOST + "/index.html";
    static final String[] REMOTE = {
            "https://raw.githubusercontent.com/Backpacker-dotcom/tiroid-defterim/main/web/index.html",
            "https://cdn.jsdelivr.net/gh/Backpacker-dotcom/tiroid-defterim@main/web/index.html"
    };
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(this), "Android");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String path = u.getPath() == null ? "/" : u.getPath();
                if (path.equals("/") || path.equals("/index.html")) {
                    WebResourceResponse r = new WebResourceResponse("text/html", "utf-8", openPage());
                    HashMap<String, String> h = new HashMap<>();
                    h.put("Cache-Control", "no-store");
                    r.setResponseHeaders(h);
                    return r;
                }
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                        new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        readOpen(getIntent());
        web.loadUrl(START);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        Reminder.schedule(this);
        new Thread(this::fetchUpdate).start();
    }

    private InputStream openPage() {
        try {
            File f = new File(getFilesDir(), "index.html");
            if (f.exists() && f.length() > 2000) return new FileInputStream(f);
            return getAssets().open("index.html");
        } catch (Exception e) {
            return new ByteArrayInputStream("<p>Sayfa açılamadı.</p>".getBytes(StandardCharsets.UTF_8));
        }
    }

    /** GitHub'daki son sürümü indirir; bir sonraki açılışta kullanılır. */
    private void fetchUpdate() {
        for (String src : REMOTE) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(src + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) { c.disconnect(); continue; }
                InputStream in = c.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close();
                c.disconnect();
                byte[] data = out.toByteArray();
                String txt = new String(data, StandardCharsets.UTF_8);
                if (data.length < 2000 || !(txt.contains("FlutterThyro") || txt.contains("Tiroid Defterim")) || !txt.contains("</script>")) continue;
                File tmp = new File(getFilesDir(), "index.tmp");
                FileOutputStream fo = new FileOutputStream(tmp);
                fo.write(data);
                fo.close();
                File dst = new File(getFilesDir(), "index.html");
                if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst); }
                return;
            } catch (Exception ignored) { }
        }
    }

    private void readOpen(Intent i) {
        if (i == null) return;
        String o = i.getStringExtra("open");
        if (o != null && !o.isEmpty()) Bridge.prefs(this).edit().putString("open", o).apply();
        i.removeExtra("open");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readOpen(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onNativeResume&&window.onNativeResume()", null);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}

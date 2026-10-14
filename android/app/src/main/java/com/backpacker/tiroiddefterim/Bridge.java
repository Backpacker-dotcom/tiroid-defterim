package com.backpacker.tiroiddefterim;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Sayfanın telefonla konuştuğu köprü: window.Android */
public class Bridge {
    private final Context ctx;
    private final Activity act;
    private static WebView printView;
    Bridge(Activity a) { act = a; ctx = a.getApplicationContext(); }

    /** Raporu Android'in yazdırma ekranında açar; oradan "PDF olarak kaydet" seçilebilir. */
    @JavascriptInterface
    public void printHtml(final String html, final String name) {
        act.runOnUiThread(() -> {
            final WebView pv = new WebView(act);
            printView = pv;
            pv.getSettings().setJavaScriptEnabled(false);
            pv.setWebViewClient(new WebViewClient() {
                boolean done = false;
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (done) return;
                    done = true;
                    PrintManager pm = (PrintManager) act.getSystemService(Context.PRINT_SERVICE);
                    if (pm == null) return;
                    String job = (name == null || name.isEmpty()) ? "Tiroid-raporu" : name;
                    PrintDocumentAdapter ad = view.createPrintDocumentAdapter(job);
                    PrintAttributes attrs = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                    pm.print(job, ad, attrs);
                }
            });
            pv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        });
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("tiroid", Context.MODE_PRIVATE);
    }

    @JavascriptInterface
    public void setReminder(String hhmm, String name) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return;
        prefs(ctx).edit().putString("time", hhmm).putString("name", name == null ? "İlacım" : name).apply();
        Reminder.schedule(ctx);
    }

    @JavascriptInterface
    public void setWeightReminder(String hhmm, boolean on) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return;
        prefs(ctx).edit().putString("wtime", hhmm).putBoolean("won", on).apply();
        Reminder.schedule(ctx);
    }

    /** Uygulama bir bildirimden açıldıysa hedef ekranı döner ("weight"), yoksa boş. */
    @JavascriptInterface
    public String openTarget() {
        SharedPreferences p = prefs(ctx);
        String v = p.getString("open", "");
        if (!v.isEmpty()) p.edit().remove("open").apply();
        return v;
    }

    /** Bildirimdeki "Aldım" düğmesine basıldıysa "YYYY-MM-DD|HH:MM" döner, yoksa boş. */
    @JavascriptInterface
    public String takenFromNotification() {
        SharedPreferences p = prefs(ctx);
        String v = p.getString("taken", "");
        if (!v.isEmpty()) p.edit().remove("taken").apply();
        return v;
    }

    @JavascriptInterface
    public void copy(String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Tiroid Defterim yedeği", text));
    }

    @JavascriptInterface
    public String version() { return BuildConfig.VERSION_NAME; }
}

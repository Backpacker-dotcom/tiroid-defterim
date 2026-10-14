package com.backpacker.tiroiddefterim;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
                    String job = (name == null || name.isEmpty()) ? "FlutterThyro-rapor" : name;
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
        prefs(ctx).edit().putString("time", hhmm).putString("name", name == null ? "İlacım" : name).putBoolean("mon", true).apply();
        Reminder.schedule(ctx);
    }

    /** İlaç hatırlatıcısını kurar ya da (on=false, "İlaç kullanmıyorum") tamamen kapatır. */
    @JavascriptInterface
    public void setMedReminder(String hhmm, String name, boolean on) {
        SharedPreferences.Editor e = prefs(ctx).edit().putBoolean("mon", on);
        if (hhmm != null && hhmm.matches("\\d{1,2}:\\d{2}")) e.putString("time", hhmm);
        if (name != null && !name.isEmpty()) e.putString("name", name);
        e.apply();
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
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FlutterThyro yedeği", text));
    }

    @JavascriptInterface
    public String version() { return BuildConfig.VERSION_NAME; }

    // ---------- Takviyeler ----------
    @JavascriptInterface
    public void setSupplements(String json) {
        try { new JSONArray(json); } catch (Exception e) { return; }
        prefs(ctx).edit().putString("supps", json).apply();
        Reminder.schedule(ctx);
    }

    /** Bildirimden "Aldım" denen takviyeler: JSON dizi ["YYYY-MM-DD|id|HH:MM", ...] */
    @JavascriptInterface
    public String suppTaken() {
        SharedPreferences p = prefs(ctx);
        String v = p.getString("suppTaken", "[]");
        p.edit().remove("suppTaken").apply();
        return v;
    }

    // ---------- Yedek dosyası ----------
    static final String FOLDER = "FlutterThyro";
    private Uri lastUri;

    /** Yedeği İndirilenler/FlutterThyro klasörüne yazar. "ok|yol", "perm" veya "err|mesaj" döner. */
    @JavascriptInterface
    public String saveBackup(String json, String name) {
        try {
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = ctx.getContentResolver();
                Uri col = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String rel = Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/";
                try (Cursor c = cr.query(col, new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{name, rel}, null)) {
                    while (c != null && c.moveToNext()) cr.delete(Uri.withAppendedPath(col, String.valueOf(c.getLong(0))), null, null);
                } catch (Exception ignored) { }
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                Uri uri = cr.insert(col, cv);
                if (uri == null) return "err|Dosya oluşturulamadı";
                try (OutputStream os = cr.openOutputStream(uri)) { os.write(data); }
                lastUri = uri;
                prune(cr, col, rel);
                return "ok|İndirilenler/" + FOLDER + "/" + name;
            } else {
                if (act.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    act.runOnUiThread(() -> act.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 9));
                    return "perm";
                }
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(data); }
                lastUri = null;
                return "ok|İndirilenler/" + FOLDER + "/" + name;
            }
        } catch (Exception e) {
            return "err|" + e.getMessage();
        }
    }

    /** En yeni 12 yedeği tutar, eskileri siler (sadece uygulamanın kendi dosyaları). */
    private void prune(ContentResolver cr, Uri col, String rel) {
        try (Cursor c = cr.query(col, new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + " LIKE 'FlutterThyro-yedek-%'",
                new String[]{rel}, MediaStore.MediaColumns.DATE_ADDED + " DESC")) {
            int i = 0;
            while (c != null && c.moveToNext()) {
                if (++i > 12) cr.delete(Uri.withAppendedPath(col, String.valueOf(c.getLong(0))), null, null);
            }
        } catch (Exception ignored) { }
    }

    /** Yedeği kaydedip paylaşma ekranını açar (Drive, e-posta, WhatsApp...). */
    @JavascriptInterface
    public void shareBackup(String json, String name) {
        String r = saveBackup(json, name);
        final Uri uri = lastUri;
        act.runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            if (r.startsWith("ok") && uri != null) {
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, json);
            }
            i.putExtra(Intent.EXTRA_SUBJECT, name);
            try { act.startActivity(Intent.createChooser(i, "Yedeği paylaş")); } catch (Exception ignored) { }
        });
    }
}

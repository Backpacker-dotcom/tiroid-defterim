package com.backpacker.tiroiddefterim;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;

/** Sayfanın telefonla konuştuğu köprü: window.Android */
public class Bridge {
    private final Context ctx;
    Bridge(Context c) { ctx = c.getApplicationContext(); }

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

package com.backpacker.tiroiddefterim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Her gün ilaç, kilo ölçümü ve takviye bildirimlerini kurar. */
public class Reminder {
    static final String ACTION_REMIND = "com.backpacker.tiroiddefterim.REMIND";
    static final String ACTION_TAKEN = "com.backpacker.tiroiddefterim.TAKEN";
    static final String ACTION_WEIGHT = "com.backpacker.tiroiddefterim.WEIGHT";
    static final String ACTION_SUPP = "com.backpacker.tiroiddefterim.SUPP";
    static final String ACTION_SUPP_TAKEN = "com.backpacker.tiroiddefterim.SUPP_TAKEN";
    static final int SUPP_BASE = 100, SUPP_TAKEN_BASE = 300, MAX_SUPP = 40;

    static PendingIntent pending(Context c, String action, int code) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent suppPending(Context c, String action, int code, int idx, String id, String name) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action)
                .putExtra("idx", idx).putExtra("id", id).putExtra("name", name);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        SharedPreferences p = Bridge.prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        scheduleAt(c, am, pending(c, ACTION_REMIND, 1), p.getString("time", "08:45"));
        if (p.getBoolean("won", true)) scheduleAt(c, am, pending(c, ACTION_WEIGHT, 4), p.getString("wtime", "08:50"));
        else am.cancel(pending(c, ACTION_WEIGHT, 4));
        // Takviyeler
        int old = p.getInt("suppCount", 0);
        for (int i = 0; i < old && i < MAX_SUPP; i++) am.cancel(suppPending(c, ACTION_SUPP, SUPP_BASE + i, i, "", ""));
        int n = 0;
        try {
            JSONArray arr = new JSONArray(p.getString("supps", "[]"));
            for (int i = 0; i < arr.length() && i < MAX_SUPP; i++) {
                JSONObject o = arr.getJSONObject(i);
                scheduleAt(c, am, suppPending(c, ACTION_SUPP, SUPP_BASE + i, i, o.optString("id"), o.optString("name")), o.optString("time", "13:00"));
                n++;
            }
        } catch (Exception ignored) { }
        p.edit().putInt("suppCount", n).apply();
    }

    private static void scheduleAt(Context c, AlarmManager am, PendingIntent pi, String t) {
        int h = 8, m = 0;
        try {
            String[] parts = t.split(":");
            h = Integer.parseInt(parts[0]);
            m = Integer.parseInt(parts[1]);
        } catch (Exception ignored) { }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.cancel(pi);
        long when = cal.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}

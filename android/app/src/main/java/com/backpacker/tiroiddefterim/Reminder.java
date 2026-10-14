package com.backpacker.tiroiddefterim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** Her gün ilaç ve kilo ölçümü bildirimlerini kurar. */
public class Reminder {
    static final String ACTION_REMIND = "com.backpacker.tiroiddefterim.REMIND";
    static final String ACTION_TAKEN = "com.backpacker.tiroiddefterim.TAKEN";
    static final String ACTION_WEIGHT = "com.backpacker.tiroiddefterim.WEIGHT";

    static PendingIntent pending(Context c, String action, int code) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        SharedPreferences p = Bridge.prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        scheduleOne(c, am, ACTION_REMIND, 1, p.getString("time", "08:45"));
        PendingIntent w = pending(c, ACTION_WEIGHT, 4);
        if (p.getBoolean("won", true)) scheduleOne(c, am, ACTION_WEIGHT, 4, p.getString("wtime", "08:50"));
        else am.cancel(w);
    }

    private static void scheduleOne(Context c, AlarmManager am, String action, int code, String t) {
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
        PendingIntent pi = pending(c, action, code);
        am.cancel(pi);
        long when = cal.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}

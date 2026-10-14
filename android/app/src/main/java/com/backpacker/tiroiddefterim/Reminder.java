package com.backpacker.tiroiddefterim;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Her gün seçilen saatte ilaç bildirimi kurar. */
public class Reminder {
    static final String ACTION_REMIND = "com.backpacker.tiroiddefterim.REMIND";
    static final String ACTION_TAKEN = "com.backpacker.tiroiddefterim.TAKEN";

    static PendingIntent pending(Context c, String action, int code) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        String t = Bridge.prefs(c).getString("time", "08:45");
        int h = 8, m = 45;
        try {
            String[] p = t.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) { }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c, ACTION_REMIND, 1);
        am.cancel(pi);
        long when = cal.getTimeInMillis();
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }
}

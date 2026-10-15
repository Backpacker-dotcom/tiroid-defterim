package com.backpacker.tiroiddefterim;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Telefonun donanım adımsayarını (TYPE_STEP_COUNTER) okur.
 * Sayaç telefon açıldığından beri toplam adımı verir; yarım saatte bir ve gece yarısından
 * hemen önce okunur, aradaki fark o günün adımına eklenir. Pil dostudur: sayma işini
 * telefonun kendi çipi yapar, uygulama sadece kısa süreliğine değeri okur.
 */
public class Steps extends BroadcastReceiver {
    static final String ACTION = "com.backpacker.tiroiddefterim.STEPS";
    static final String ACTION_MID = "com.backpacker.tiroiddefterim.STEPS_MID";

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("steps", Context.MODE_PRIVATE);
    }

    static boolean available(Context c) {
        SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        return sm != null && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null;
    }

    static boolean permitted(Context c) {
        if (Build.VERSION.SDK_INT < 29) return true;
        return c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean enabled(Context c) {
        return sp(c).getBoolean("on", false);
    }

    static boolean active(Context c) {
        return enabled(c) && available(c) && permitted(c);
    }

    private static PendingIntent pi(Context c, String action, int code) {
        Intent i = new Intent(c, Steps.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent rep = pi(c, ACTION, 500), mid = pi(c, ACTION_MID, 501);
        if (!active(c)) { am.cancel(rep); am.cancel(mid); return; }
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 60_000,
                AlarmManager.INTERVAL_HALF_HOUR, rep);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 57);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 30_000) cal.add(Calendar.DAY_OF_MONTH, 1);
        if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), mid);
        else am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), mid);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (!active(c)) { schedule(c); return; }
        if (ACTION_MID.equals(intent.getAction())) schedule(c);
        final PendingResult pr = goAsync();
        sample(c, pr::finish);
    }

    /** Sayaç değerini bir kez okur, kaydeder ve done'ı çağırır (en geç 8 sn). */
    static void sample(final Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        final SensorManager sm = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        final Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null || !permitted(c)) { if (done != null) done.run(); return; }
        final boolean[] fin = {false};
        final Handler h = new Handler(Looper.getMainLooper());
        final SensorEventListener l = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent e) {
                if (fin[0]) return;
                fin[0] = true;
                sm.unregisterListener(this);
                record(c, (long) e.values[0]);
                if (done != null) done.run();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) { }
        };
        h.post(() -> {
            try { sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL); }
            catch (Exception e) { fin[0] = true; if (done != null) done.run(); return; }
            h.postDelayed(() -> {
                if (!fin[0]) { fin[0] = true; sm.unregisterListener(l); if (done != null) done.run(); }
            }, 8000);
        });
    }

    static String key(Date d) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
    }

    static synchronized void record(Context c, long raw) {
        SharedPreferences p = sp(c);
        long last = p.getLong("raw", -1);
        // Telefon yeniden başladıysa sayaç sıfırdan başlar
        long el = SystemClock.elapsedRealtime();
        if (last >= 0 && el < p.getLong("el", 0)) last = 0;
        long delta;
        if (last < 0) delta = 0;            // ilk okuma: bundan sonrası sayılır
        else if (raw >= last) delta = raw - last;
        else delta = raw;                   // sayaç sıfırlanmış
        if (delta > 80_000) delta = 0;      // anlamsız sıçrama
        try {
            JSONObject days = new JSONObject(p.getString("days", "{}"));
            String today = key(new Date());
            days.put(today, days.optLong(today, 0) + delta);
            // 400 günden eskisini sil
            List<String> ks = new ArrayList<>();
            for (Iterator<String> it = days.keys(); it.hasNext(); ) ks.add(it.next());
            Collections.sort(ks);
            for (int i = 0; i < ks.size() - 400; i++) days.remove(ks.get(i));
            p.edit().putLong("raw", raw).putLong("el", el).putString("days", days.toString())
                    .putLong("at", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    static String info(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("avail", available(c));
            o.put("perm", permitted(c));
            o.put("on", enabled(c));
            o.put("at", sp(c).getLong("at", 0));
            o.put("days", new JSONObject(sp(c).getString("days", "{}")));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}

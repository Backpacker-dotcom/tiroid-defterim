package com.backpacker.tiroiddefterim;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL = "ilac";
    static final int NOTIF_ID = 845;

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction() == null ? "" : intent.getAction();
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Reminder.ACTION_TAKEN.equals(a)) {
            Date now = new Date();
            String v = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now) + "|"
                    + new SimpleDateFormat("HH:mm", Locale.US).format(now);
            Bridge.prefs(c).edit().putString("taken", v).apply();
            if (nm != null) nm.cancel(NOTIF_ID);
            return;
        }
        if (Reminder.ACTION_REMIND.equals(a) && nm != null) show(c, nm);
        Reminder.schedule(c);
    }

    private void show(Context c, NotificationManager nm) {
        String name = Bridge.prefs(c).getString("name", "İlacım");
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "İlaç hatırlatıcı", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Her sabah ilaç saatinde hatırlatır");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(name + " zamanı")
                .setContentText("Aç karnına, sadece suyla. Kahvaltı için 30-60 dk bekle.")
                .setStyle(new Notification.BigTextStyle().bigText("Aç karnına, sadece bir bardak suyla al. Kahvaltı ve kahve için 30-60 dk, süt ürünleri için 4 saat bekle."))
                .setContentIntent(openPi)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .addAction(R.drawable.ic_notify, "Aldım", Reminder.pending(c, Reminder.ACTION_TAKEN, 3));
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        try { nm.notify(NOTIF_ID, b.build()); } catch (SecurityException ignored) { }
    }
}

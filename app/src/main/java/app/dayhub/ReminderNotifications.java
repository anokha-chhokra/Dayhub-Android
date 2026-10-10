package app.dayhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import app.dayhub.data.ReminderPlan;

/** Feature 29: shows a reminder as a notification. Tapping it opens Day Hub on the journal or the habits. */
final class ReminderNotifications {
    private static final String CHANNEL = "reminders";

    private ReminderNotifications() { }

    static void post(Context c, ReminderPlan.Reminder r) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, c.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.notify(r.key.hashCode(), new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(r.title)
                .setContentText(r.body)
                .setContentIntent(Intents.openPending(c, r.isJournal() ? "note" : "habits"))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .build());
    }
}

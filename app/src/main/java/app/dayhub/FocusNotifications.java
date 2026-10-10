package app.dayhub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import app.dayhub.data.FocusSession;

/** Feature 24: the two notifications a focus timer uses: one while it runs, one when time is up. */
final class FocusNotifications {
    private static final String CH_RUNNING = "focus";
    private static final String CH_DONE = "focus_done";
    private static final int ID_RUNNING = 1;
    private static final int ID_DONE = 2;

    private FocusNotifications() { }

    private static void channels(Context c, NotificationManager nm) {
        nm.createNotificationChannel(new NotificationChannel(CH_RUNNING, c.getString(R.string.channel_focus),
                NotificationManager.IMPORTANCE_LOW));
        NotificationChannel done = new NotificationChannel(CH_DONE, c.getString(R.string.channel_focus_done),
                NotificationManager.IMPORTANCE_DEFAULT);
        done.enableVibration(true);
        done.setVibrationPattern(new long[] {0, 160, 100, 160, 100, 300});
        nm.createNotificationChannel(done);
    }

    /** True if Android will show our notifications (from Android 13 the person has to allow them). */
    static boolean allowed(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    /** The ongoing notification with the time left counting down. Posting it again just updates it. */
    static void running(Context c, FocusSession s) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || !nm.areNotificationsEnabled()) return;
        channels(c, nm);
        Notification.Builder b = new Notification.Builder(c, CH_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(c.getString(R.string.focus_running))
                .setContentText(s.label.isEmpty() ? c.getString(R.string.focus_running_text) : s.label)
                .setContentIntent(Intents.openPending(c))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(s.endsAt)
                .setUsesChronometer(true)
                .setChronometerCountDown(true);
        nm.notify(ID_RUNNING, b.build());
    }

    /** Posts the running notification if it is not showing (it is gone after a restart), without re-alerting if it is. */
    static void ensureRunning(Context c, FocusSession s) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) {
            if (n.getId() == ID_RUNNING) return;
        }
        running(c, s);
    }

    /** Takes the running notification away, and if the timer ran to its end, says so. */
    static void finished(Context c, boolean completed) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(ID_RUNNING);
        if (!completed || !nm.areNotificationsEnabled()) return;
        channels(c, nm);
        nm.notify(ID_DONE, new Notification.Builder(c, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(c.getString(R.string.focus_done))
                .setContentText(c.getString(R.string.focus_done_text))
                .setContentIntent(Intents.openPending(c))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build());
    }
}

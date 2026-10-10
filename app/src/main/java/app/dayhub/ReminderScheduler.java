package app.dayhub;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import app.dayhub.data.DayHubData;
import app.dayhub.data.ReminderPlan;
import app.dayhub.data.Validate;

/**
 * Feature 29: keeps one alarm set for the next reminder. When it goes off, {@link ReminderReceiver} shows what
 * is due and sets the next one. Another alarm is always set for just after midnight, so a new day starts with
 * a fresh list, and everything is set again after a restart, an app update, or a change of date, time or time
 * zone. Nothing is set while system notifications are off.
 */
final class ReminderScheduler {
    private static final String ACTION = "app.dayhub.REMINDER";
    private static final int REQUEST_CODE = 9;
    /** Just after midnight, so the alarm cannot land on the old day. */
    private static final long AFTER_MIDNIGHT_MS = 5000;
    private static final long QUIET_MS = 600;
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pending;

    private ReminderScheduler() { }

    /** Sets the alarm again soon; a burst of saves is handled once. Main thread only. */
    static void request(Context context) {
        Context c = context.getApplicationContext();
        if (pending != null) HANDLER.removeCallbacks(pending);
        pending = () -> {
            pending = null;
            reschedule(c);
        };
        HANDLER.postDelayed(pending, QUIET_MS);
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, REQUEST_CODE, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static long epochOf(String day, String hhmm) {
        return LocalDate.parse(day).atTime(LocalTime.parse(hhmm)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** Works out the next time something needs to happen and sets (or cancels) the alarm. */
    static void reschedule(Context context) {
        Context c = context.getApplicationContext();
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        DayHubData data = ReminderData.read(c);
        if (data == null || !data.getSettings().notifications) {
            am.cancel(alarmIntent(c));
            return;
        }
        String today = Validate.localDate();
        long at = epochOf(Validate.addDays(today, 1), "00:00") + AFTER_MIDNIGHT_MS;
        String next = ReminderPlan.next(data, today, DateLabels.nowHHMM());
        if (next != null) at = Math.min(at, epochOf(today, next));
        set(am, at, alarmIntent(c));
    }

    private static void set(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                return;
            }
        } catch (SecurityException e) {
            // exact alarms are not allowed here: fall back to the inexact one below
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); // may come a few minutes late
    }
}

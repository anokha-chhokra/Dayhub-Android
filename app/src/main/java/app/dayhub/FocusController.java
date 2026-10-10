package app.dayhub;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

import app.dayhub.data.FocusSession;
import app.dayhub.widgets.WidgetUpdater;

/**
 * Feature 24: starts and ends a focus timer. It keeps the timer on disk, sets an alarm for the moment it
 * ends and shows the notifications, so the timer carries on, and rings, with the app closed.
 */
final class FocusController {
    private static final String ACTION_END = "app.dayhub.FOCUS_END";
    private static final long ALARM_SLACK_MS = 2000;

    private FocusController() { }

    /** True if Day Hub's focus guard is switched on in Android's Accessibility settings. */
    static boolean guardEnabled(Context c) {
        AccessibilityManager am = c.getSystemService(AccessibilityManager.class);
        if (am == null) return false;
        List<AccessibilityServiceInfo> on = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo i : on) {
            ServiceInfo si = i.getResolveInfo() == null ? null : i.getResolveInfo().serviceInfo;
            if (si != null && c.getPackageName().equals(si.packageName) && FocusGuardService.class.getName().equals(si.name)) return true;
        }
        return false;
    }

    /** True if Day Hub may "Display over other apps", which lets the guard bring it back to the front most reliably. */
    static boolean overlayGranted(Context c) {
        return Settings.canDrawOverlays(c);
    }

    static FocusSession start(Context c, int minutes, String label) {
        FocusSession s = FocusSession.start(System.currentTimeMillis(), minutes, label);
        FocusState.save(c, s);
        scheduleEnd(c, s.endsAt);
        FocusNotifications.running(c, s);
        WidgetUpdater.updateAll(c);
        FocusEvents.changed();
        return s;
    }

    /** The person ended it early: no "complete" notification. */
    static void end(Context c) {
        finish(c, false);
    }

    /** Called when the alarm fires or the app notices time is up. Does nothing if nothing has run out. */
    static void expireIfNeeded(Context c) {
        FocusSession s = FocusState.get(c);
        if (s.hasExpired(System.currentTimeMillis())) finish(c, true);
    }

    /**
     * The end-of-timer alarm went off. If it fired a little early (the phone's clock was changed), a timer
     * within two seconds of its end is finished now, and a timer further off gets its alarm set again.
     */
    static void onAlarm(Context c) {
        FocusSession s = FocusState.get(c);
        if (!s.active) return;
        if (s.endsAt - System.currentTimeMillis() <= ALARM_SLACK_MS) finish(c, true);
        else scheduleEnd(c, s.endsAt);
    }

    /**
     * Run whenever the app comes to the front (and after a restart): finishes a timer whose time ran out,
     * and for one still running makes sure the alarm and notification are in place, e.g. after the phone
     * was restarted, which forgets alarms and notifications.
     */
    static void resume(Context c) {
        expireIfNeeded(c);
        FocusSession s = FocusState.get(c);
        if (s.isActive(System.currentTimeMillis())) {
            scheduleEnd(c, s.endsAt);
            FocusNotifications.ensureRunning(c, s);
        }
    }

    private static void finish(Context c, boolean completed) {
        boolean was = FocusState.get(c).active;
        FocusState.clear(c);
        cancelEnd(c);
        FocusNotifications.finished(c, completed && was);
        WidgetUpdater.updateAll(c);
        FocusEvents.changed();
    }

    private static PendingIntent endIntent(Context c) {
        Intent i = new Intent(c, FocusEndReceiver.class).setAction(ACTION_END);
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void scheduleEnd(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        // Inexact is fine: the timer itself follows the clock, the alarm only tells you.
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, endIntent(c));
    }

    private static void cancelEnd(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(endIntent(c));
    }
}

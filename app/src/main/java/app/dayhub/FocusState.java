package app.dayhub;

import android.content.Context;
import android.content.SharedPreferences;

import app.dayhub.data.FocusSession;

/**
 * Feature 24: the running focus timer, kept on disk. Because it lives here and not in a screen, it
 * survives the app being closed or killed, and the alarm, the notification and the Home tile all see the
 * same timer.
 */
public final class FocusState {
    private FocusState() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    private static SharedPreferences uiPrefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("focus_ui", Context.MODE_PRIVATE);
    }

    public static synchronized FocusSession get(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("active", false)) return FocusSession.idle();
        return new FocusSession(true, p.getLong("startedAt", 0), p.getLong("endsAt", 0),
                p.getInt("minutes", 0), p.getString("label", ""));
    }

    static synchronized void save(Context c, FocusSession s) {
        prefs(c).edit().putBoolean("active", s.active).putLong("startedAt", s.startedAt).putLong("endsAt", s.endsAt)
                .putInt("minutes", s.minutes).putString("label", s.label).commit();
    }

    static synchronized void clear(Context c) {
        prefs(c).edit().clear().commit();
    }

    /** True while a timer is running. */
    public static boolean isActive(Context c) {
        return get(c).isActive(System.currentTimeMillis());
    }

    /** Whether Android's notification question has already been asked once (it is only asked once). */
    static boolean askedNotifications(Context c) {
        return uiPrefs(c).getBoolean("askedNotifications", false);
    }

    static void setAskedNotifications(Context c) {
        uiPrefs(c).edit().putBoolean("askedNotifications", true).apply();
    }
}

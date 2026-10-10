package app.dayhub;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Feature 29: which reminders have already been shown today, so each is announced once a day. */
final class ReminderState {
    private ReminderState() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("reminders", Context.MODE_PRIVATE);
    }

    static boolean shown(Context c, String day, String key) {
        SharedPreferences p = prefs(c);
        return day.equals(p.getString("day", "")) && p.getStringSet("shown", new HashSet<>()).contains(key);
    }

    static void markShown(Context c, String day, String key) {
        SharedPreferences p = prefs(c);
        Set<String> keys = day.equals(p.getString("day", "")) ? new HashSet<>(p.getStringSet("shown", new HashSet<>())) : new HashSet<>();
        keys.add(key);
        p.edit().putString("day", day).putStringSet("shown", keys).apply();
    }
}

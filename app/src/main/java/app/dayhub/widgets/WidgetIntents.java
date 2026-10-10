package app.dayhub.widgets;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import app.dayhub.WidgetActionActivity;

/** Features 27 and 28: the taps on widgets. All of them go through WidgetActionActivity, which decides what to do. */
public final class WidgetIntents {
    public static final String ACTION = "app.dayhub.WIDGET_ACTION";
    public static final String EXTRA_ACT = "act";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_ARG = "arg";

    public static final String ACT_TASK_DONE = "task.done";
    public static final String ACT_HABIT_UP = "habit.up";
    public static final String ACT_HABIT_DOWN = "habit.down";
    public static final String ACT_MOOD = "mood";
    public static final String ACT_OPEN = "open";
    public static final String ACT_FOCUS = "focus.start";

    private WidgetIntents() { }

    private static Intent base(Context c, String act) {
        return new Intent(c, WidgetActionActivity.class).setAction(ACTION).putExtra(EXTRA_ACT, act);
    }

    /** A button that does one fixed thing (open a screen, start a focus). */
    public static PendingIntent button(Context c, String act, String arg) {
        Intent i = base(c, act).putExtra(EXTRA_ARG, arg);
        i.setData(Uri.parse("dayhub://widget/" + act + "/" + arg)); // makes each button its own PendingIntent
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** The template a list widget's rows fill in (each row says what it does). Must be mutable for that. */
    public static PendingIntent template(Context c) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getActivity(c, 1, new Intent(c, WidgetActionActivity.class).setAction(ACTION), flags);
    }

    /** What one row of a list does when tapped. */
    public static Intent fill(String act, int id, String arg) {
        Intent i = new Intent().putExtra(EXTRA_ACT, act).putExtra(EXTRA_ID, id);
        if (arg != null) i.putExtra(EXTRA_ARG, arg);
        return i;
    }
}

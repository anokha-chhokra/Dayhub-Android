package app.dayhub.widgets;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import app.dayhub.FocusState;
import app.dayhub.R;
import app.dayhub.data.FocusSession;

/** Feature 27: start a focus with one tap, or watch the countdown while one runs. */
public class FocusWidget extends BaseWidget {
    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_focus);
        long now = System.currentTimeMillis();
        FocusSession s = FocusState.get(c);
        boolean on = s.isActive(now);
        rv.setViewVisibility(R.id.focus_idle, on ? View.GONE : View.VISIBLE);
        rv.setViewVisibility(R.id.focus_running, on ? View.VISIBLE : View.GONE);
        rv.setOnClickPendingIntent(R.id.root, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "focus"));
        if (on) {
            long base = SystemClock.elapsedRealtime() + (s.endsAt - now);
            rv.setChronometerCountDown(R.id.focus_timer, true);
            rv.setChronometer(R.id.focus_timer, base, null, true);
            rv.setTextViewText(R.id.focus_label, s.label.isEmpty() ? c.getString(R.string.focus_running_text) : s.label);
        } else {
            rv.setOnClickPendingIntent(R.id.focus_15, WidgetIntents.button(c, WidgetIntents.ACT_FOCUS, "15"));
            rv.setOnClickPendingIntent(R.id.focus_25, WidgetIntents.button(c, WidgetIntents.ACT_FOCUS, "25"));
            rv.setOnClickPendingIntent(R.id.focus_45, WidgetIntents.button(c, WidgetIntents.ACT_FOCUS, "45"));
        }
        return rv;
    }
}

package app.dayhub.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import app.dayhub.R;

/** Feature 27: one row of buttons that open Day Hub straight on the job: task, expense, note, habits, focus. */
public class QuickWidget extends BaseWidget {
    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_quick);
        rv.setOnClickPendingIntent(R.id.q_task, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "task"));
        rv.setOnClickPendingIntent(R.id.q_expense, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "expense"));
        rv.setOnClickPendingIntent(R.id.q_note, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "note"));
        rv.setOnClickPendingIntent(R.id.q_habits, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "habits"));
        rv.setOnClickPendingIntent(R.id.q_focus, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "focus"));
        return rv;
    }
}

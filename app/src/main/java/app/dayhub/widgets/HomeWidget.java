package app.dayhub.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import app.dayhub.R;
import app.dayhub.data.WidgetSnapshot;

/** Feature 27: the Home screen as a widget: attention, tasks, habits, spending and mood, with quick-add buttons on top. */
public class HomeWidget extends BaseWidget {
    @Override
    int listId() { return R.id.list; }

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_home);
        WidgetSnapshot s = WidgetData.load(c);
        boolean stale = s != null && s.isStale(java.time.LocalDate.now().toString());
        rv.setTextViewText(R.id.greeting, s == null ? c.getString(R.string.app_name) : s.greeting);
        rv.setTextViewText(R.id.stats, s == null ? "" : "⭐ " + s.points + "   🔥 " + s.streak + (stale ? "   ↻" : ""));
        rv.setOnClickPendingIntent(R.id.header, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "home"));
        rv.setOnClickPendingIntent(R.id.btn_task, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "task"));
        rv.setOnClickPendingIntent(R.id.btn_expense, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "expense"));
        rv.setOnClickPendingIntent(R.id.btn_note, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "note"));
        rv.setOnClickPendingIntent(R.id.btn_focus, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "focus"));
        rv.setOnClickPendingIntent(R.id.empty, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "home"));
        attachList(c, rv, id, "home");
        return rv;
    }
}

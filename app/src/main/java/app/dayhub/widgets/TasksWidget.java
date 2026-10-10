package app.dayhub.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import app.dayhub.R;
import app.dayhub.data.WidgetSnapshot;

/** Feature 27: today's open tasks. Tap one to open Tasks. */
public class TasksWidget extends BaseWidget {
    @Override
    int listId() { return R.id.list; }

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_tasks);
        WidgetSnapshot s = WidgetData.load(c);
        rv.setTextViewText(R.id.title, c.getString(R.string.widget_tasks_title));
        rv.setTextViewText(R.id.count, s == null ? "" : s.taskCount + " open");
        rv.setOnClickPendingIntent(R.id.header, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "tasks"));
        rv.setOnClickPendingIntent(R.id.btn_add, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "task"));
        rv.setOnClickPendingIntent(R.id.empty, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "home"));
        attachList(c, rv, id, "tasks");
        return rv;
    }
}

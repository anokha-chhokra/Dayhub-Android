package app.dayhub.widgets;

import android.content.Context;
import android.widget.RemoteViews;

import app.dayhub.R;
import app.dayhub.data.WidgetSnapshot;

/** Features 27 and 28: today's habits with plus and minus buttons (a check habit switches on and off). */
public class HabitsWidget extends BaseWidget {
    @Override
    int listId() { return R.id.list; }

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_habits);
        WidgetSnapshot s = WidgetData.load(c);
        boolean fresh = s != null && !s.isStale(java.time.LocalDate.now().toString());
        rv.setTextViewText(R.id.title, c.getString(R.string.widget_habits_title));
        rv.setTextViewText(R.id.count, fresh ? s.habitsDone + " / " + s.habitsTotal : "");
        rv.setOnClickPendingIntent(R.id.header, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "habits"));
        rv.setOnClickPendingIntent(R.id.empty, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "home"));
        attachList(c, rv, id, "habits");
        return rv;
    }
}

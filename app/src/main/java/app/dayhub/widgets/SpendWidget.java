package app.dayhub.widgets;

import android.content.Context;
import android.graphics.Color;
import android.widget.RemoteViews;

import app.dayhub.R;
import app.dayhub.data.WidgetSnapshot;

/** Feature 27: this month's spending against the budget, and a button to add an expense. */
public class SpendWidget extends BaseWidget {
    private static final int INK = 0xFF1B1A17;
    private static final int RED = 0xFFB3261E;

    @Override
    RemoteViews build(Context c, int id) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_spend);
        WidgetSnapshot s = WidgetData.load(c);
        if (s == null) {
            rv.setTextViewText(R.id.spend_total, c.getString(R.string.app_name));
            rv.setTextViewText(R.id.spend_line, c.getString(R.string.widget_open_once));
            rv.setProgressBar(R.id.spend_bar, 100, 0, false);
            rv.setTextViewText(R.id.spend_today, "");
        } else {
            rv.setTextViewText(R.id.spend_total, s.spend.total);
            rv.setTextViewText(R.id.spend_line, s.spend.line);
            rv.setTextColor(R.id.spend_line, s.spend.over ? RED : INK);
            rv.setProgressBar(R.id.spend_bar, 100, s.spend.pct, false);
            rv.setViewVisibility(R.id.spend_bar, s.spend.hasBudget ? android.view.View.VISIBLE : android.view.View.GONE);
            // After midnight "today" is yesterday's figure, and on the 1st the month is last month's: say so.
            boolean stale = s.isStale(java.time.LocalDate.now().toString());
            rv.setTextViewText(R.id.spend_today, stale ? "↻ Open Day Hub to refresh" : s.spend.today.isEmpty() ? "" : "Today " + s.spend.today);
        }
        rv.setOnClickPendingIntent(R.id.root, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "spend"));
        rv.setOnClickPendingIntent(R.id.btn_expense, WidgetIntents.button(c, WidgetIntents.ACT_OPEN, "expense"));
        return rv;
    }
}

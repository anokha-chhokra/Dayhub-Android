package app.dayhub.widgets;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

import app.dayhub.R;
import app.dayhub.data.Moods;
import app.dayhub.data.WidgetRows;
import app.dayhub.data.WidgetSnapshot;

/** Feature 27: supplies the rows of the three list widgets (Home, Tasks, Habits). */
public class WidgetListService extends RemoteViewsService {
    static final String EXTRA_KIND = "kind";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext(), intent.getStringExtra(EXTRA_KIND));
    }

    private static final class Factory implements RemoteViewsFactory {
        private static final int RED = 0xFFB3261E;
        private static final int INK = 0xFF1B1A17;
        private static final int MUTED = 0xFF5A564C;

        private final Context c;
        private final String kind;
        private List<WidgetRows.Row> rows = new ArrayList<>();

        Factory(Context c, String kind) {
            this.c = c;
            this.kind = kind == null ? "home" : kind;
        }

        @Override
        public void onCreate() { }

        @Override
        public void onDataSetChanged() {
            WidgetSnapshot s = WidgetData.load(c);
            String today = java.time.LocalDate.now().toString();
            switch (kind) {
                case "tasks": rows = WidgetRows.tasks(s, 15); break;
                case "habits": rows = WidgetRows.habits(s, today, 15); break;
                default: rows = WidgetRows.home(s, today, 5, 5); break;
            }
        }

        @Override
        public void onDestroy() { rows = new ArrayList<>(); }

        @Override
        public int getCount() { return rows.size(); }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= rows.size()) return null;
            WidgetRows.Row r = rows.get(position);
            switch (r.type) {
                case SECTION: return section(r);
                case ATTENTION: return attention(r);
                case TASK: return task(r);
                case HABIT: return habit(r);
                case SPEND: return spend(r);
                case MOOD: return mood();
                default: return note(r); // EMPTY and MORE
            }
        }

        private RemoteViews section(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_section);
            v.setTextViewText(R.id.section_title, r.title);
            v.setTextViewText(R.id.section_right, r.right);
            return v;
        }

        private RemoteViews attention(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_attention);
            v.setTextViewText(R.id.attn_title, r.title);
            v.setTextViewText(R.id.attn_sub, r.sub);
            v.setTextColor(R.id.attn_sub, r.flag ? RED : MUTED);
            v.setOnClickFillInIntent(R.id.row_root, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, r.go.isEmpty() ? "home" : r.go));
            return v;
        }

        private RemoteViews task(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_task);
            v.setTextViewText(R.id.task_title, (r.icon.isEmpty() ? "" : r.icon + " ") + r.title);
            v.setTextViewText(R.id.task_sub, r.sub);
            v.setViewVisibility(R.id.task_sub, r.sub.isEmpty() ? View.GONE : View.VISIBLE);
            v.setTextColor(R.id.task_sub, r.flag ? RED : MUTED);
            v.setOnClickFillInIntent(R.id.task_check, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "tasks"));
            v.setOnClickFillInIntent(R.id.task_text, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "tasks"));
            return v;
        }

        private RemoteViews habit(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_habit);
            boolean check = "check".equals(r.habitKind);
            v.setTextViewText(R.id.habit_icon, r.icon);
            v.setTextViewText(R.id.habit_title, r.title);
            v.setTextViewText(R.id.habit_sub, r.sub);
            v.setTextColor(R.id.habit_sub, r.flag ? RED : MUTED);
            v.setProgressBar(R.id.habit_bar, 100, r.progress, false);
            v.setViewVisibility(R.id.habit_bar, check ? View.GONE : View.VISIBLE);
            v.setViewVisibility(R.id.habit_minus, check ? View.GONE : View.VISIBLE);
            v.setTextViewText(R.id.habit_plus, check ? (r.done ? "✓" : "○") : "+");
            v.setOnClickFillInIntent(R.id.habit_minus, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "habits"));
            v.setOnClickFillInIntent(R.id.habit_plus, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "habits"));
            v.setOnClickFillInIntent(R.id.habit_text, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "habits"));
            return v;
        }

        private RemoteViews spend(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_spend);
            v.setTextViewText(R.id.spend_total, r.title);
            v.setTextViewText(R.id.spend_line, r.sub);
            v.setTextColor(R.id.spend_line, r.flag ? RED : INK);
            v.setTextViewText(R.id.spend_today, r.right);
            v.setProgressBar(R.id.spend_bar, 100, r.progress, false);
            v.setOnClickFillInIntent(R.id.row_root, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "spend"));
            return v;
        }

        private RemoteViews mood() {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_mood);
            int[] ids = { R.id.mood_1, R.id.mood_2, R.id.mood_3, R.id.mood_4, R.id.mood_5 };
            for (int i = 0; i < ids.length; i++) {
                v.setTextViewText(ids[i], Moods.emoji(i + 1));
                v.setOnClickFillInIntent(ids[i], WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, "home"));
            }
            return v;
        }

        private RemoteViews note(WidgetRows.Row r) {
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.row_empty);
            v.setTextViewText(R.id.empty_text, r.title);
            v.setOnClickFillInIntent(R.id.row_root, WidgetIntents.fill(WidgetIntents.ACT_OPEN, 0, r.go.isEmpty() ? "home" : r.go));
            return v;
        }

        @Override
        public RemoteViews getLoadingView() { return null; }

        @Override
        public int getViewTypeCount() { return 7; }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public boolean hasStableIds() { return false; }
    }
}

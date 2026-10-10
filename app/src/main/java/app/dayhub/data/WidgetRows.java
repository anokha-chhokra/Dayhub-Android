package app.dayhub.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 27: the lines a list widget shows, worked out from the widget snapshot. The same rows as the web
 * app's widgets. No Android code, so it can be tested on a plain JVM.
 *
 * Because the widgets read the app's data directly, the snapshot is always today's; the "new day" notes the
 * web app needed for a picture made yesterday are kept for the case where a widget is drawn from an old one.
 */
public final class WidgetRows {
    private WidgetRows() { }

    public enum Type { SECTION, ATTENTION, TASK, HABIT, SPEND, MOOD, EMPTY, MORE }

    public static final class Row {
        public Type type;
        public int id;
        public String title = "", sub = "", icon = "", right = "", go = "", habitKind = "";
        public int progress;
        public boolean flag;   // overdue / over budget / starred
        public boolean done;

        Row(Type type) { this.type = type; }
    }

    private static Row section(String title, String right) {
        Row r = new Row(Type.SECTION);
        r.title = title;
        r.right = right;
        return r;
    }

    private static Row empty(String text, String go) {
        Row r = new Row(Type.EMPTY);
        r.title = text;
        r.go = go;
        return r;
    }

    private static Row more(int n, String go) {
        Row r = new Row(Type.MORE);
        r.title = "+" + n + " more";
        r.go = go;
        return r;
    }

    private static Row task(WidgetSnapshot.TaskRow t) {
        Row r = new Row(Type.TASK);
        r.id = t.id;
        r.title = t.title;
        r.sub = t.sub;
        r.flag = t.overdue;
        r.done = false;
        r.icon = t.star ? "★" : "";
        r.go = "tasks";
        return r;
    }

    static Row habit(WidgetSnapshot.Habit h) {
        Row r = new Row(Type.HABIT);
        r.id = h.id;
        r.title = h.title;
        r.icon = h.icon;
        r.habitKind = h.kind;
        r.done = h.done;
        r.go = "habits";
        String unit = h.unit.isEmpty() ? "" : " " + h.unit;
        if ("check".equals(h.kind)) {
            r.sub = h.done ? "Done" : "Not yet";
            r.progress = h.done ? 100 : 0;
        } else if ("goal".equals(h.kind)) {
            r.sub = h.value + " / " + h.target + unit;
            r.progress = Math.min(100, (int) ((100L * h.value) / Math.max(1, h.target)));
        } else {
            r.sub = h.value + " of " + h.target + unit + " limit";
            r.progress = Math.min(100, (int) ((100L * h.value) / Math.max(1, h.target)));
            r.flag = h.value > h.target;
        }
        return r;
    }

    private static Row spend(WidgetSnapshot s) {
        Row r = new Row(Type.SPEND);
        r.title = s.spend.total;
        r.sub = s.spend.line;
        r.right = s.spend.today.isEmpty() ? "" : "Today " + s.spend.today;
        r.progress = s.spend.pct;
        r.flag = s.spend.over;
        r.go = "spend";
        return r;
    }

    private static final String NO_DATA = "Open Day Hub once to fill this widget";

    /** The replica of the Home screen. */
    public static List<Row> home(WidgetSnapshot s, String today, int maxTasks, int maxHabits) {
        List<Row> out = new ArrayList<>();
        if (s == null) { out.add(empty(NO_DATA, "home")); return out; }
        boolean stale = s.isStale(today);
        if (stale) out.add(empty("New day. Tap to open Day Hub and refresh.", "home"));

        if (!s.attention.isEmpty()) {
            out.add(section("Needs attention", ""));
            int n = 0;
            for (WidgetSnapshot.Attention a : s.attention) {
                if (n++ >= 3) break;
                Row r = new Row(Type.ATTENTION);
                r.title = a.title;
                r.sub = a.sub;
                r.flag = a.bad;
                r.go = a.go;
                out.add(r);
            }
        }

        out.add(section("Tasks", s.taskCount + " open"));
        if (s.tasks.isEmpty()) out.add(empty("Nothing open. Enjoy the day.", "tasks"));
        for (int i = 0; i < s.tasks.size() && i < maxTasks; i++) out.add(task(s.tasks.get(i)));
        if (s.taskCount > Math.min(maxTasks, s.tasks.size())) out.add(more(s.taskCount - Math.min(maxTasks, s.tasks.size()), "tasks"));

        if (!stale) {
            out.add(section("Habits", s.habitsDone + " / " + s.habitsTotal));
            if (s.habits.isEmpty()) out.add(empty("No habits yet. Tap to add some.", "habits"));
            for (int i = 0; i < s.habits.size() && i < maxHabits; i++) out.add(habit(s.habits.get(i)));
            if (s.habitsTotal > Math.min(maxHabits, s.habits.size())) out.add(more(s.habitsTotal - Math.min(maxHabits, s.habits.size()), "habits"));
        }

        out.add(section("Spending", ""));
        out.add(spend(s));

        out.add(section("How do you feel?", s.moodEmoji));
        out.add(new Row(Type.MOOD));
        return out;
    }

    /** The Tasks widget: every open task that fits. */
    public static List<Row> tasks(WidgetSnapshot s, int max) {
        List<Row> out = new ArrayList<>();
        if (s == null) { out.add(empty(NO_DATA, "home")); return out; }
        if (s.tasks.isEmpty()) out.add(empty("Nothing open. Enjoy the day.", "tasks"));
        for (int i = 0; i < s.tasks.size() && i < max; i++) out.add(task(s.tasks.get(i)));
        if (s.taskCount > Math.min(max, s.tasks.size())) out.add(more(s.taskCount - Math.min(max, s.tasks.size()), "tasks"));
        return out;
    }

    /** The Habits widget. Counts from an earlier day are not shown as today's. */
    public static List<Row> habits(WidgetSnapshot s, String today, int max) {
        List<Row> out = new ArrayList<>();
        if (s == null) { out.add(empty(NO_DATA, "home")); return out; }
        if (s.isStale(today)) { out.add(empty("New day. Tap to open Day Hub and refresh habits.", "habits")); return out; }
        if (s.habits.isEmpty()) out.add(empty("No habits yet. Tap to add some.", "habits"));
        for (int i = 0; i < s.habits.size() && i < max; i++) out.add(habit(s.habits.get(i)));
        if (s.habitsTotal > Math.min(max, s.habits.size())) out.add(more(s.habitsTotal - Math.min(max, s.habits.size()), "habits"));
        return out;
    }
}

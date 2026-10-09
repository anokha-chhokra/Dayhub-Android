package app.dayhub;

import android.app.Activity;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Moods;
import app.dayhub.data.Timeline;
import app.dayhub.data.Validate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature 16: the Journal tab. Day shows one day's timeline in time order (entries with mood and
 * tags, habits, spending, finished tasks); Calendar shows the month with each day's mood and habit
 * progress; Entries lists the month's entries. Tap an entry to edit it; Write adds one.
 */
public final class JournalScreen extends ScrollView {
    private static final int TAB_DAY = 0;
    private static final int TAB_CALENDAR = 1;
    private static final int TAB_ENTRIES = 2;

    private final Activity activity;
    private final DayHubData data;
    private final HabitActions habitActions;
    private final JournalActions journalActions;
    private final ExpenseActions expenseActions;
    private final LinearLayout tabs;
    private final LinearLayout nav;
    private final LinearLayout body;
    private int tab = TAB_DAY;
    private String day = Validate.localDate();
    private String ym = day.substring(0, 7);

    public JournalScreen(Activity activity, DayHubData data, HabitActions habitActions, JournalActions journalActions,
                         ExpenseActions expenseActions) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.habitActions = habitActions;
        this.journalActions = journalActions;
        this.expenseActions = expenseActions;
        setFillViewport(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Sketch.label(activity, "Journal", 32, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton write = new HandDrawnButton(activity, "+ Write", true);
        write.setOnClickListener(v -> journalActions.openSheet(null, tab == TAB_DAY ? day : Validate.localDate(), null, null));
        head.addView(write);
        column.addView(head);

        tabs = new LinearLayout(activity);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        column.addView(tabs, rowParams(14));
        nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(nav, rowParams(14));
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        column.addView(body, rowParams(4));
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Leaving the Journal resets it, so coming back lands on today. */
    public void resetToToday() {
        tab = TAB_DAY;
        day = Validate.localDate();
        ym = day.substring(0, 7);
    }

    /** Redraws the tabs, the day or month heading, and the view from the stored data. */
    public void refresh() {
        drawTabs();
        drawNav();
        body.removeAllViews();
        if (tab == TAB_DAY) drawDay();
        else if (tab == TAB_CALENDAR) drawCalendar();
        else drawEntries();
    }

    // ---------- tabs and navigation ----------

    private void drawTabs() {
        tabs.removeAllViews();
        String[] labels = {"Day", "Calendar", "Entries"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            HandDrawnButton b = new HandDrawnButton(activity, labels[i], tab == i);
            b.setOnClickListener(v -> {
                tab = index;
                if (index != TAB_DAY) ym = day.substring(0, 7);
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Sketch.dp(activity, 12);
            tabs.addView(b, lp);
        }
    }

    private long millis(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void drawNav() {
        nav.removeAllViews();
        String today = Validate.localDate();
        String title;
        String sub = null;
        boolean atEnd;
        if (tab == TAB_DAY) {
            title = DateLabels.dueLabel(activity, day, today);
            if (!day.equals(today)) {
                sub = DateUtils.formatDateTime(activity, millis(LocalDate.parse(day)),
                        DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR);
            }
            atEnd = day.compareTo(today) >= 0;
        } else {
            title = DateUtils.formatDateTime(activity, millis(LocalDate.parse(ym + "-01")),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_SHOW_YEAR);
            atEnd = ym.compareTo(today.substring(0, 7)) >= 0;
        }
        nav.addView(arrow("‹", "Previous", true, () -> step(-1)));
        LinearLayout middle = new LinearLayout(activity);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        middle.addView(Sketch.label(activity, title, 22, true, R.color.ink));
        if (sub != null) middle.addView(Sketch.label(activity, sub, 14, false, R.color.muted));
        nav.addView(middle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(arrow("›", "Next", !atEnd, () -> step(1)));
    }

    private HandDrawnButton arrow(String glyph, String description, boolean enabled, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, glyph, false);
        b.setContentDescription(description + (tab == TAB_DAY ? " day" : " month"));
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.35f);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void step(int delta) {
        String today = Validate.localDate();
        if (tab == TAB_DAY) {
            String next = Validate.addDays(day, delta);
            if (next.compareTo(today) > 0) return;
            day = next;
        } else {
            String next = YearMonth.parse(ym).plusMonths(delta).toString();
            if (next.compareTo(today.substring(0, 7)) > 0) return;
            ym = next;
        }
        refresh();
    }

    // ---------- an entry ----------

    private View entryBlock(Entry e) {
        LinearLayout block = new LinearLayout(activity);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClickable(true);
        block.setContentDescription("Edit entry");
        block.setOnClickListener(v -> journalActions.openSheet(e, e.day, null, null));

        String meta = (e.time == null ? "" : e.time) + (e.wordCount > 0 ? " · " + e.wordCount + " words" : "");
        LinearLayout headRow = new LinearLayout(activity);
        headRow.setOrientation(LinearLayout.HORIZONTAL);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        if (e.mood != null) {
            TextView face = new TextView(activity);
            face.setText(Moods.emoji(e.mood));
            face.setTextSize(22);
            headRow.addView(face);
        }
        TextView metaView = Sketch.label(activity, meta, 14, false, R.color.muted);
        metaView.setPadding(Sketch.dp(activity, e.mood != null ? 8 : 0), 0, 0, 0);
        headRow.addView(metaView);
        block.addView(headRow);

        block.addView(e.text.isEmpty() ? Sketch.label(activity, "Mood check-in", 14, false, R.color.muted)
                : Sketch.label(activity, e.text, 16, false, R.color.ink), rowParams(2));
        if (!e.tags.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String t : e.tags) sb.append('#').append(t).append(' ');
            block.addView(Sketch.label(activity, sb.toString().trim(), 14, true, R.color.muted), rowParams(2));
        }
        return block;
    }

    // ---------- Day ----------

    private View stat(String value, String label) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(Sketch.label(activity, value, value.length() > 6 ? 18 : 26, true, R.color.ink));
        box.addView(Sketch.label(activity, label, 13, false, R.color.muted));
        return box;
    }

    private void drawDay() {
        String today = Validate.localDate();
        String now = day.equals(today) ? DateLabels.nowHHMM() : null;
        Timeline.Day t = new Timeline(data).day(day, today, now);
        String currency = data.getSettings().currency;

        Timeline.Summary s = t.summary;
        HandDrawnCard summary = new HandDrawnCard(activity);
        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        String[] values = {(s.points > 0 ? "+" : "") + s.points,
            s.habitsScheduled > 0 ? s.habitsDone + "/" + s.habitsScheduled : "–",
            String.valueOf(s.entries), s.spentMinor > 0 ? MoneyFormat.money(s.spentMinor, currency) : "–"};
        String[] labels = {"points", "habits", s.entries == 1 ? "entry" : "entries", "spent"};
        for (int i = 0; i < values.length; i++) {
            stats.addView(stat(values[i], labels[i]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        summary.addView(stats);
        if (s.missed > 0) {
            summary.addView(Sketch.label(activity, s.missed + " planned habit" + (s.missed == 1 ? " was" : "s were") + " not done.",
                    14, false, R.color.red), rowParams(8));
        }
        body.addView(summary, rowParams(0));

        if (!t.goals.isEmpty()) body.addView(habitCard("Limits & goals", t.goals), rowParams(14));
        if (!t.anytime.isEmpty()) body.addView(habitCard("Anytime today", t.anytime), rowParams(14));

        HandDrawnCard timeline = new HandDrawnCard(activity);
        timeline.addView(Sketch.label(activity, "Timeline", 22, true, R.color.ink));
        if (t.items.isEmpty()) {
            timeline.addView(Sketch.label(activity, day.equals(today)
                    ? "Nothing logged yet. Write a line or tick a habit and it lands here with the time."
                    : "Nothing was recorded for this day.", 16, false, R.color.muted), rowParams(10));
        } else {
            String lastPart = null;
            boolean severalParts = false;
            for (Timeline.Item it : t.items) {
                if (!java.util.Objects.equals(it.part, t.items.get(0).part)) severalParts = true;
            }
            for (Timeline.Item it : t.items) {
                if (severalParts && !java.util.Objects.equals(it.part, lastPart)) {
                    timeline.addView(Sketch.label(activity, it.part == null ? "No time" : it.part, 14, true, R.color.muted), rowParams(12));
                }
                lastPart = it.part;
                timeline.addView(timelineRow(it, currency), rowParams(8));
            }
        }
        HandDrawnButton write = new HandDrawnButton(activity, "Write entry", true);
        write.setOnClickListener(v -> journalActions.openSheet(null, day, null, null));
        HandDrawnButton expense = new HandDrawnButton(activity, "+ Expense", false);
        expense.setOnClickListener(v -> expenseActions.openSheet(day));
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        actionRow.addView(write, gap);
        actionRow.addView(expense);
        timeline.addView(actionRow, rowParams(14));
        body.addView(timeline, rowParams(14));
    }

    private View habitCard(String title, List<Timeline.DayHabit> habits) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, title, 20, true, R.color.ink));
        for (Timeline.DayHabit h : habits) {
            card.addView(HabitRowView.build(activity, new HabitDay(h.habit, day, h.value), day, habitActions), rowParams(4));
        }
        return card;
    }

    private View timelineRow(Timeline.Item it, String currency) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView time = Sketch.label(activity, it.time == null ? "·" : it.time, 14, false, R.color.muted);
        time.setGravity(Gravity.END);
        time.setPadding(0, Sketch.dp(activity, 10), Sketch.dp(activity, 10), 0);
        row.addView(time, new LinearLayout.LayoutParams(Sketch.dp(activity, 58), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new HandDrawnDrawable(activity, activity.getColor(R.color.paper), 0f, 8400 + (it.time == null ? 0 : it.time.hashCode() & 0xff)));
        int p = Sketch.dp(activity, 12);
        card.setPadding(p, p, p, p);
        switch (it.type) {
            case "entry":
                card.addView(entryBlock(it.entry));
                break;
            case "habit":
                card.addView(habitItem(it));
                break;
            case "expense":
                Expense x = it.expense;
                card.addView(Sketch.label(activity, "💸 " + MoneyFormat.money(x.amountMinor, currency) + " · " + x.category, 16, true, R.color.ink));
                String note = (x.note == null ? "" : x.note) + (x.entryId != null ? (x.note != null ? " · " : "") + "from your journal" : "");
                if (!note.isEmpty()) card.addView(Sketch.label(activity, note, 14, false, R.color.muted));
                break;
            default:
                card.addView(Sketch.label(activity, "✓ " + it.task.title, 16, true, R.color.ink));
                card.addView(Sketch.label(activity, "task finished", 14, false, R.color.muted));
        }
        row.addView(card, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View habitItem(Timeline.Item it) {
        Timeline.DayHabit h = it.habit;
        boolean pending = it.pending;
        boolean missed = pending && it.missed;
        boolean done = !pending && (h.done || h.value > 0);
        String sub;
        if (pending) {
            sub = missed ? "not done · planned for " + it.time : "planned";
        } else {
            sub = h.habit.kind.equals("check") ? "done" : h.value + (h.habit.unit.isEmpty() ? "" : " " + h.habit.unit);
            if (h.pointsToday != 0) sub += " · " + (h.pointsToday > 0 ? "+" : "") + h.pointsToday + " pts";
        }
        HandDrawnCheckRow row = new HandDrawnCheckRow(activity, h.habit.icon + " " + h.habit.title)
                .setSubtitle(sub, missed)
                .onLabelClick(() -> habitActions.openSheet(h.habit));
        row.setChecked(done);
        row.setOnChange(() -> habitActions.log(new HabitDay(h.habit, day, h.value), day, row.isChecked() ? 1 : 0));
        return row;
    }

    // ---------- Calendar ----------

    private void drawCalendar() {
        Timeline.Month month = new Timeline(data).month(ym);
        String today = Validate.localDate();
        YearMonth y = YearMonth.parse(ym);
        int first = y.atDay(1).getDayOfWeek().getValue() % 7; // Sunday first, like the web app
        int count = y.lengthOfMonth();

        HandDrawnCard card = new HandDrawnCard(activity);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        for (String n : new String[] {"S", "M", "T", "W", "T", "F", "S"}) {
            TextView t = Sketch.label(activity, n, 14, true, R.color.muted);
            t.setGravity(Gravity.CENTER);
            header.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(header);

        LinearLayout week = null;
        for (int cell = 0; cell < first + count; cell++) {
            if (cell % 7 == 0) {
                week = new LinearLayout(activity);
                week.setOrientation(LinearLayout.HORIZONTAL);
                card.addView(week, rowParams(4));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (cell < first) {
                week.addView(new View(activity), lp);
                continue;
            }
            int d = cell - first + 1;
            String date = String.format(java.util.Locale.ROOT, "%s-%02d", ym, d);
            week.addView(calendarCell(d, date, month.days.get(date), today), lp);
        }
        while (week != null && week.getChildCount() < 7) {
            week.addView(new View(activity), new LinearLayout.LayoutParams(0, 1, 1f));
        }
        card.addView(Sketch.label(activity, "Tap a day to open its timeline. The face is the day’s average mood; the bar shows habits finished.",
                14, false, R.color.muted), rowParams(10));
        body.addView(card, rowParams(0));
    }

    private View calendarCell(int d, String date, Timeline.Cell info, String today) {
        boolean future = date.compareTo(today) > 0;
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setPadding(0, Sketch.dp(activity, 4), 0, Sketch.dp(activity, 4));
        if (date.equals(day)) {
            cell.setBackground(new HandDrawnDrawable(activity, activity.getColor(R.color.hi), 0f, 8500 + d));
        }
        cell.addView(Sketch.label(activity, String.valueOf(d), 16, date.equals(today), R.color.ink));
        String dot = info != null && info.entries > 0 ? (info.mood != null ? Moods.emoji(info.mood) : "•") : " ";
        TextView dots = Sketch.label(activity, dot, 14, false, R.color.ink);
        cell.addView(dots);
        if (info != null && info.habitsScheduled > 0) {
            int pct = Math.round(100f * info.habitsDone / info.habitsScheduled);
            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setWeightSum(100f);
            bar.setBackgroundColor(activity.getColor(R.color.track));
            if (pct > 0) {
                View fill = new View(activity);
                fill.setBackgroundColor(activity.getColor(R.color.ink));
                bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pct));
            }
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Sketch.dp(activity, 28), Sketch.dp(activity, 4));
            cell.addView(bar, bp);
        }
        cell.setAlpha(future ? 0.3f : 1f);
        cell.setEnabled(!future);
        cell.setContentDescription(date + (info != null && info.entries > 0 ? ", " + info.entries + " entries" : ""));
        if (!future) {
            cell.setOnClickListener(v -> {
                day = date;
                tab = TAB_DAY;
                refresh();
            });
        }
        return cell;
    }

    // ---------- Entries ----------

    private void drawEntries() {
        String[] range = Validate.monthRange(ym);
        List<Entry> entries = data.listEntries(range[0], range[1]);
        HandDrawnCard card = new HandDrawnCard(activity);
        if (entries.isEmpty()) {
            card.addView(Sketch.label(activity, "No entries this month.", 18, true, R.color.ink));
            card.addView(Sketch.label(activity, "Tap Write. A one-line mood check-in is enough.", 16, false, R.color.muted));
            body.addView(card, rowParams(0));
            return;
        }
        Map<String, java.util.List<Entry>> byDay = new LinkedHashMap<>();
        for (Entry e : entries) byDay.computeIfAbsent(e.day, k -> new java.util.ArrayList<>()).add(e);
        boolean firstGroup = true;
        for (Map.Entry<String, java.util.List<Entry>> g : byDay.entrySet()) {
            card.addView(Sketch.label(activity, DateLabels.dueLabel(activity, g.getKey(), Validate.localDate()), 18, true, R.color.ink),
                    rowParams(firstGroup ? 0 : 14));
            firstGroup = false;
            for (Entry e : g.getValue()) card.addView(entryBlock(e), rowParams(8));
        }
        body.addView(card, rowParams(0));
    }
}

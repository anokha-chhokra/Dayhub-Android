package app.dayhub;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.dayhub.data.Badges;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitProgress;
import app.dayhub.data.HabitProgress.DaySummary;
import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Validate;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Feature 15: the Habits tab. Points and streak at the top, a week strip of the last seven days
 * (tap a day to log or fix it), that day's habits with their controls (tick a check habit, step a
 * goal or limit up and down), the habits that do not run that day, and the badges.
 */
public final class HabitsScreen extends ScrollView {
    private static final int WEEK_DAYS = 7;

    private final Activity activity;
    private final DayHubData data;
    private final HabitActions actions;
    private final LinearLayout column;
    private String selectedDay;

    public HabitsScreen(Activity activity, DayHubData data, HabitActions actions) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.actions = actions;
        setFillViewport(true);

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Redraws everything from the stored data. */
    public void refresh() {
        String today = Validate.localDate();
        if (selectedDay == null || selectedDay.compareTo(today) > 0) selectedDay = today;
        HabitProgress progress = new HabitProgress(data);
        HabitProgress.StatsView stats = progress.statsView(today);
        try {
            data.commit(); // the Stats screen may have earned a badge
        } catch (RuntimeException e) {
            // Nothing to tell the person about: the badge will be recorded next time.
        }

        column.removeAllViews();
        drawHead();
        column.addView(statsCard(stats), rowParams(14));
        column.addView(weekCard(progress.recentDays(today, WEEK_DAYS), today), rowParams(14));

        List<HabitDay> all = progress.habitsForDay(selectedDay, false);
        List<HabitDay> scheduled = new ArrayList<>();
        List<HabitDay> other = new ArrayList<>();
        for (HabitDay h : all) (h.scheduled ? scheduled : other).add(h);
        column.addView(dayCard(scheduled, !all.isEmpty(), today), rowParams(14));
        if (!other.isEmpty()) column.addView(otherCard(other), rowParams(14));
        column.addView(badgesCard(stats.badges), rowParams(14));
    }

    // ---------- header and stats ----------

    private void drawHead() {
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Sketch.label(activity, "Habits", 32, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton add = new HandDrawnButton(activity, "+ Add", true);
        add.setOnClickListener(v -> actions.openSheet(null));
        head.addView(add);
        column.addView(head);
    }

    private View stat(int value, String label) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.addView(Sketch.label(activity, String.valueOf(value), 30, true, R.color.ink));
        box.addView(Sketch.label(activity, label, 14, false, R.color.muted));
        return box;
    }

    private View statsCard(HabitProgress.StatsView v) {
        HandDrawnCard card = new HandDrawnCard(activity);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] values = {v.stats.totalPoints, v.stats.streak, v.stats.longestStreak};
        String[] labels = {"points", "day streak", "best streak"};
        for (int i = 0; i < values.length; i++) {
            row.addView(stat(values[i], labels[i]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(row);
        card.addView(Sketch.label(activity, "Today: " + (v.stats.todayPoints >= 0 ? "+" : "") + v.stats.todayPoints
                + " points. A day counts for your streak when you finish a habit or write a journal entry.",
                14, false, R.color.muted), rowParams(10));
        return card;
    }

    // ---------- the week strip ----------

    private View weekCard(List<DaySummary> days, String today) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "This week", 20, true, R.color.ink));
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (DaySummary d : days) {
            LocalDate date = LocalDate.parse(d.day);
            boolean selected = d.day.equals(selectedDay);
            TextView cell = Sketch.label(activity, date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    + "\n" + date.getDayOfMonth() + "\n" + (d.scheduled == 0 ? "–" : d.done + "/" + d.scheduled),
                    13, d.day.equals(today), R.color.ink);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, Sketch.dp(activity, 8), 0, Sketch.dp(activity, 8));
            if (selected) {
                cell.setBackground(new HandDrawnDrawable(activity, activity.getColor(R.color.hi), 0f, 7400 + date.getDayOfMonth()));
            }
            cell.setContentDescription(d.day + ", " + d.done + " of " + d.scheduled + " habits done");
            cell.setOnClickListener(v -> {
                selectedDay = d.day;
                refresh();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = Sketch.dp(activity, 2);
            row.addView(cell, lp);
        }
        card.addView(row, rowParams(8));
        return card;
    }

    // ---------- the selected day ----------

    private View dayCard(List<HabitDay> scheduled, boolean anyHabits, String today) {
        HandDrawnCard card = new HandDrawnCard(activity);
        int done = 0;
        for (HabitDay h : scheduled) if (h.done) done++;

        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, DateLabels.dueLabel(activity, selectedDay, today), 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!scheduled.isEmpty()) {
            title.addView(Sketch.label(activity, done + " of " + scheduled.size() + " done", 14, false, R.color.muted));
        }
        card.addView(title);

        if (scheduled.isEmpty()) {
            card.addView(Sketch.label(activity,
                    anyHabits ? "Nothing scheduled this day."
                            : "No habits yet. Tap Add to start with water, steps, a workout and more.",
                    16, false, R.color.muted), rowParams(10));
            return card;
        }
        for (HabitDay h : scheduled) card.addView(HabitRowView.build(activity, h, selectedDay, actions), rowParams(4));
        return card;
    }

    private View otherCard(List<HabitDay> other) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Not this day", 20, true, R.color.ink));
        for (HabitDay h : other) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Sketch.dp(activity, 8), 0, Sketch.dp(activity, 8));
            row.setClickable(true);
            row.setOnClickListener(v -> actions.openSheet(h.habit));
            row.addView(Sketch.label(activity, h.habit.icon + " " + h.habit.title, 17, false, R.color.ink));
            row.addView(Sketch.label(activity, HabitProgressText.schedule(h.habit), 14, false, R.color.muted));
            card.addView(row, rowParams(2));
        }
        return card;
    }

    // ---------- badges ----------

    private View badgesCard(List<Badges.Status> badges) {
        HandDrawnCard card = new HandDrawnCard(activity);
        int unlocked = 0;
        for (Badges.Status b : badges) if (b.unlockedOn != null) unlocked++;

        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, "Badges", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.addView(Sketch.label(activity, unlocked + " of " + badges.size(), 14, false, R.color.muted));
        card.addView(title);

        for (int i = 0; i < badges.size(); i += 2) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + 2, badges.size()); j++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (j == i) lp.rightMargin = Sketch.dp(activity, 10);
                row.addView(badge(badges.get(j)), lp);
            }
            card.addView(row, rowParams(10));
        }
        return card;
    }

    private View badge(Badges.Status s) {
        boolean earned = s.unlockedOn != null;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Sketch.dp(activity, 6), Sketch.dp(activity, 10), Sketch.dp(activity, 6), Sketch.dp(activity, 10));
        box.setBackground(new HandDrawnDrawable(activity, activity.getColor(earned ? R.color.hi : R.color.track), 0f, 7500 + s.badge.id.length()));
        box.setAlpha(earned ? 1f : 0.7f);
        TextView icon = Sketch.label(activity, s.badge.icon, 28, false, R.color.ink);
        icon.setAlpha(earned ? 1f : 0.45f);
        box.addView(icon);
        box.addView(Sketch.label(activity, s.badge.name, 15, true, R.color.ink));
        TextView sub = Sketch.label(activity, earned ? s.unlockedOn : s.badge.desc, 12, false, R.color.muted);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);
        return box;
    }
}

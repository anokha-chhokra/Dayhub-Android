package app.dayhub.data;

import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Model.HabitLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature 7: points, streaks and the counts that badges are earned from, ported from the web app's
 * computeStats. Worked out fresh from the habits, their logs and the journal entries each time.
 */
public final class HabitStats {
    public int totalPoints;
    public int todayPoints;
    public int totalEntries;
    public int totalWords;
    public int habitDoneCount;
    public int perfectDays;
    public int streak;
    public int longestStreak;
    public boolean nightOwl;
    public boolean earlyBird;

    private HabitStats() {}

    /**
     * @param habits  all habits, archived ones too (their logs still count)
     * @param logs    every habit log
     * @param entries every journal entry (only day, time and wordCount are used)
     * @param today   "YYYY-MM-DD"
     */
    public static HabitStats compute(List<Habit> habits, List<HabitLog> logs, List<Entry> entries, String today) {
        Map<Integer, Habit> byId = new HashMap<>();
        for (Habit h : habits) byId.put(h.id, h);
        Map<String, Integer> pointsByDay = new HashMap<>();
        Set<String> activeDays = new HashSet<>();
        Map<String, Integer> valueByDayHabit = new HashMap<>(); // "day|id" -> value
        HabitStats s = new HabitStats();

        for (HabitLog log : logs) {
            Habit habit = byId.get(log.habitId);
            if (habit == null) continue;
            valueByDayHabit.put(log.day + "|" + habit.id, log.value);
            pointsByDay.merge(log.day, HabitRules.dayPoints(habit, log.value), Integer::sum);
            if (HabitRules.isActivity(habit, log.value)) activeDays.add(log.day);
            if (HabitRules.isDone(habit, log.value)) s.habitDoneCount++;
        }

        Set<String> entryDays = new HashSet<>();
        for (Entry e : entries) {
            s.totalWords += e.wordCount;
            if (entryDays.add(e.day)) pointsByDay.merge(e.day, HabitRules.JOURNAL_DAY_POINTS, Integer::sum);
            activeDays.add(e.day);
            if (e.time != null && !e.time.isEmpty()) {
                if (e.time.compareTo("21:00") >= 0) s.nightOwl = true;
                if (e.time.compareTo("07:00") < 0) s.earlyBird = true;
            }
        }

        // Days on which every scheduled habit was fine (needs at least 3 scheduled).
        Set<String> logDays = new HashSet<>();
        for (HabitLog l : logs) logDays.add(l.day);
        for (String day : logDays) {
            if (day.compareTo(today) > 0) continue;
            List<Habit> scheduled = new ArrayList<>();
            for (Habit h : habits) if (!h.archived && HabitRules.isScheduled(h, day)) scheduled.add(h);
            if (scheduled.size() < 3) continue;
            boolean allFine = true;
            int real = 0;
            for (Habit h : scheduled) {
                Integer v = valueByDayHabit.get(day + "|" + h.id);
                if (!HabitRules.isOk(h, v)) allFine = false;
                // an untouched limit is "fine", but a perfect day needs real work: two checks or goals done
                if (!h.kind.equals("limit") && HabitRules.isDone(h, v)) real++;
            }
            if (allFine && real >= 2) s.perfectDays++;
        }

        computeStreaks(s, habits, activeDays, today);

        long total = 0;
        for (int n : pointsByDay.values()) total += n;
        s.totalPoints = (int) Math.max(0, total);
        Integer todayPoints = pointsByDay.get(today);
        s.todayPoints = todayPoints == null ? 0 : todayPoints;
        s.totalEntries = entries.size();
        return s;
    }

    /**
     * Streaks are consecutive days with any activity. Two kinds of day never break one: today (it
     * is not over yet) and rest days (no habit is scheduled that weekday).
     */
    private static void computeStreaks(HabitStats s, List<Habit> habits, Set<String> activeDays, String today) {
        List<Habit> live = new ArrayList<>();
        for (Habit h : habits) if (!h.archived) live.add(h);

        List<String> sorted = new ArrayList<>();
        for (String d : activeDays) if (d.compareTo(today) <= 0) sorted.add(d);
        Collections.sort(sorted);

        int run = 0;
        String prev = null;
        for (String d : sorted) {
            run = prev != null && gapIsRest(live, prev, d) ? run + 1 : 1;
            if (run > s.longestStreak) s.longestStreak = run;
            prev = d;
        }

        if (sorted.isEmpty()) return;
        String first = sorted.get(0);
        for (String d = today; d.compareTo(first) >= 0; d = Validate.addDays(d, -1)) {
            if (activeDays.contains(d)) s.streak++;
            else if (d.equals(today) || isRest(live, d)) continue;
            else break;
        }
    }

    private static boolean isRest(List<Habit> live, String day) {
        if (live.isEmpty()) return false;
        int weekday = HabitRules.weekday(day);
        for (Habit h : live) if (h.days.contains(weekday)) return false;
        return true;
    }

    private static boolean gapIsRest(List<Habit> live, String from, String to) {
        for (String d = Validate.addDays(from, 1); d.compareTo(to) < 0; d = Validate.addDays(d, 1)) {
            if (!isRest(live, d)) return false;
        }
        return true;
    }
}

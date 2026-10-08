package app.dayhub.data;

import app.dayhub.data.Badges.Badge;
import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Model.HabitLog;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature 7: ties the stored data to the habit rules, like the web app's commit.js. It works out
 * points and streaks, records badges as they are earned, and describes each habit for a day.
 * Like {@link DayHubData}, it changes data in memory only; call {@code data.commit()} to save.
 */
public final class HabitProgress {
    /** A habit as shown on one day. */
    public static final class HabitDay {
        public final Habit habit;
        public final boolean scheduled;
        public final int value;
        public final boolean done;
        public final int pointsToday;

        HabitDay(Habit habit, String day, int value) {
            this.habit = habit;
            this.scheduled = HabitRules.isScheduled(habit, day);
            this.value = value;
            this.done = HabitRules.isDone(habit, value);
            this.pointsToday = HabitRules.dayPoints(habit, value);
        }
    }

    /** Fresh stats and the badges that were earned by the change that was just made. */
    public static final class Synced {
        public final HabitStats stats;
        public final List<Badge> newBadges;

        Synced(HabitStats stats, List<Badge> newBadges) {
            this.stats = stats;
            this.newBadges = newBadges;
        }
    }

    /** What logging a habit produced. */
    public static final class LogResult {
        public final HabitDay habit;
        public final Synced progress;

        LogResult(HabitDay habit, Synced progress) {
            this.habit = habit;
            this.progress = progress;
        }
    }

    /** What saving a journal entry produced. */
    public static final class EntryResult {
        public final Entry entry;
        public final Synced progress;

        EntryResult(Entry entry, Synced progress) {
            this.entry = entry;
            this.progress = progress;
        }
    }

    /** The Stats screen: the numbers and every badge with the day it was earned. */
    public static final class StatsView {
        public final HabitStats stats;
        public final List<Badges.Status> badges;

        StatsView(HabitStats stats, List<Badges.Status> badges) {
            this.stats = stats;
            this.badges = badges;
        }
    }

    private final DayHubData data;

    public HabitProgress(DayHubData data) {
        this.data = data;
    }

    /** Points, streaks and counts as of {@code today}. */
    public HabitStats statsFor(String today) {
        return HabitStats.compute(data.listHabits(true), data.listLogs(null, null),
                data.listEntries(null, null), today);
    }

    /** Recomputes stats and records any badge earned since last time. */
    public Synced syncBadges(String today) {
        HabitStats stats = statsFor(today);
        List<Badge> fresh = new ArrayList<>();
        for (String id : data.unlockBadges(Badges.earnedIds(stats), today)) fresh.add(Badges.byId(id));
        return new Synced(stats, fresh);
    }

    /** Every badge, with the day it was earned or null. */
    public List<Badges.Status> badgeList() {
        Map<String, String> have = data.listBadges();
        List<Badges.Status> out = new ArrayList<>();
        for (Badge b : Badges.ALL) out.add(new Badges.Status(b, have.get(b.id)));
        return out;
    }

    /** Brings the badges up to date, then returns stats and badges for the Stats screen. */
    public StatsView statsView(String today) {
        Synced synced = syncBadges(today);
        return new StatsView(synced.stats, badgeList());
    }

    private Map<Integer, Integer> valuesOn(String day) {
        Map<Integer, Integer> values = new HashMap<>();
        for (HabitLog l : data.listLogs(day, Validate.addDays(day, 1))) values.put(l.habitId, l.value);
        return values;
    }

    /** The (not archived) habits as they stand on a day, optionally only those scheduled that day. */
    public List<HabitDay> habitsForDay(String day, boolean onlyScheduled) {
        Map<Integer, Integer> values = valuesOn(day);
        List<HabitDay> out = new ArrayList<>();
        for (Habit h : data.listHabits(false)) {
            Integer v = values.get(h.id);
            HabitDay d = new HabitDay(h, day, v == null ? 0 : v);
            if (!onlyScheduled || d.scheduled) out.add(d);
        }
        return out;
    }

    /** One habit as it stands on a day, or null if it does not exist. */
    public HabitDay oneHabit(int habitId, String day) {
        Habit h = data.getHabit(habitId);
        if (h == null) return null;
        Integer v = valuesOn(day).get(habitId);
        return new HabitDay(h, day, v == null ? 0 : v);
    }

    /** Logs a habit (see {@link DayHubData#logHabit}) and records any badge it earns. */
    public LogResult logHabit(int habitId, JSONObject body) {
        data.logHabit(habitId, body);
        String today = Validate.todayOf(body.opt("today"));
        Synced progress = syncBadges(today);
        return new LogResult(oneHabit(habitId, Validate.isoDate(body.opt("day"), "day")), progress);
    }

    /** Writes a journal entry (see {@link DayHubData#createEntry}) and records any badge it earns. */
    public EntryResult createEntry(JSONObject body) {
        Entry entry = data.createEntry(body);
        return new EntryResult(entry, syncBadges(Validate.todayOf(body.opt("today"))));
    }

    /** Edits a journal entry (see {@link DayHubData#updateEntry}) and records any badge it earns. */
    public EntryResult updateEntry(int id, JSONObject body) {
        Entry entry = data.updateEntry(id, body);
        return new EntryResult(entry, syncBadges(Validate.todayOf(body.opt("today"))));
    }
}

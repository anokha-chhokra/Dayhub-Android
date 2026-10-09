package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Music;
import app.dayhub.data.Model.Settings;
import app.dayhub.data.Model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 13: everything the Home dashboard shows, worked out in one go from the stored data, the
 * way the web app's dashboard route does it: what needs attention, today's tasks and habits,
 * points and streak, the journal prompt and mood, the month's spending and the current music link.
 */
public final class HomeData {
    /** Today's tasks: open ones that are undated or due by today, plus those finished today. */
    public static final class Tasks {
        public final List<Task> list;
        public final int total;
        public final int doneToday;

        Tasks(List<Task> list, int doneToday) {
            this.list = list;
            this.total = list.size();
            this.doneToday = doneToday;
        }
    }

    /** Habits scheduled today. */
    public static final class Habits {
        public final List<HabitDay> list;
        public final int doneCount;
        public final int total;

        Habits(List<HabitDay> list) {
            this.list = list;
            int done = 0;
            for (HabitDay h : list) if (h.done) done++;
            this.doneCount = done;
            this.total = list.size();
        }
    }

    public static final class Stats {
        public final int totalPoints;
        public final int todayPoints;
        public final int streak;
        public final int longestStreak;

        Stats(HabitStats s) {
            this.totalPoints = s.totalPoints;
            this.todayPoints = s.todayPoints;
            this.streak = s.streak;
            this.longestStreak = s.longestStreak;
        }
    }

    public static final class Journal {
        public final Prompts.Prompt prompt;
        public final int todayCount;
        /** The mood of today's newest entry that has one, or null. */
        public final Integer latestMood;
        /**
         * A mood-only check-in made in the last 30 minutes: tapping a face again corrects it instead
         * of adding another. Null when there is none (or no clock).
         */
        public final Integer quickMoodId;

        Journal(Prompts.Prompt prompt, int todayCount, Integer latestMood, Integer quickMoodId) {
            this.prompt = prompt;
            this.todayCount = todayCount;
            this.latestMood = latestMood;
            this.quickMoodId = quickMoodId;
        }
    }

    public final String today;
    public final String name;
    public final List<Insights.Item> attention;
    public final Tasks tasks;
    public final DashboardInsights.Spend spend;
    public final Habits habits;
    public final Stats stats;
    public final Journal journal;
    /** The link Home plays, or null. */
    public final Music music;

    private HomeData(String today, String name, DashboardInsights insights, Tasks tasks, Habits habits,
                     Stats stats, Journal journal, Music music) {
        this.today = today;
        this.name = name;
        this.attention = insights.attention;
        this.spend = insights.spend;
        this.tasks = tasks;
        this.habits = habits;
        this.stats = stats;
        this.journal = journal;
        this.music = music;
    }

    /**
     * @param today "YYYY-MM-DD"
     * @param now   "HH:MM", or null; without it, time-based nudges are skipped
     */
    public static HomeData compute(DayHubData data, String today, String now) {
        Settings settings = data.getSettings();
        List<Task> taskRows = data.dashboardTasks(today);
        int doneToday = 0;
        for (Task t : taskRows) if (t.done) doneToday++;

        HabitProgress progress = new HabitProgress(data);
        Habits habits = new Habits(progress.habitsForDay(today, true));
        Stats stats = new Stats(progress.statsFor(today));

        List<Entry> todays = data.listEntries(today, Validate.addDays(today, 1));
        Integer latestMood = null;
        for (Entry e : todays) {
            if (e.mood != null) {
                latestMood = e.mood;
                break;
            }
        }
        Integer quickMoodId = null;
        if (now != null && !now.isEmpty()) {
            for (Entry e : todays) {
                if (!e.text.isEmpty() || e.mood == null || e.time == null || e.time.isEmpty()) continue;
                Integer since = Insights.minutesBetween(e.time, now);
                if (since != null && since >= 0 && since <= 30) {
                    quickMoodId = e.id;
                    break;
                }
            }
        }
        Journal journal = new Journal(Prompts.forDay(today), todays.size(), latestMood, quickMoodId);

        Music current = settings.currentMusicId == null ? null : data.getMusic(settings.currentMusicId);
        return new HomeData(today, settings.name, DashboardInsights.compute(data, today, now),
                new Tasks(taskRows, doneToday), habits, stats, journal, current);
    }

    /** Open tasks only, in display order. */
    public List<Task> openTasks() {
        List<Task> open = new ArrayList<>();
        for (Task t : tasks.list) if (!t.done) open.add(t);
        return open;
    }
}

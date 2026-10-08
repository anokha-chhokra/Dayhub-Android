package app.dayhub.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shapes of Day Hub's data, the same as the web app's rows. Money is whole minor units
 * (paise/cents) so totals never drift; dates are "YYYY-MM-DD" text and times "HH:MM" text, so a
 * backup file reads the same on both. Fields are public for easy reading; change data through
 * {@link DayHubData}, which validates it.
 */
public final class Model {
    private Model() {}

    public static final List<String> KINDS = Arrays.asList("check", "goal", "limit");
    public static final List<String> CATEGORIES =
            Arrays.asList("Food", "Transport", "Bills", "Shopping", "Health", "Fun", "Other");
    public static final List<String> PROMPT_IDS =
            Arrays.asList("gratitude", "reflection", "highlight", "challenge", "growth");

    public static final class Task {
        public int id;
        public String title;
        public String dueOn;
        public int priority; // 0 normal, 1 starred
        public boolean done;
        public String doneOn;
        public String doneTime;
        public String createdAt;

        public Task copy() {
            Task t = new Task();
            t.id = id; t.title = title; t.dueOn = dueOn; t.priority = priority; t.done = done;
            t.doneOn = doneOn; t.doneTime = doneTime; t.createdAt = createdAt;
            return t;
        }
    }

    public static final class Expense {
        public int id;
        public long amountMinor;
        public String category;
        public String note;
        public String spentOn;
        public Integer entryId; // the journal entry it was written in, if any
        public String time;

        public Expense copy() {
            Expense e = new Expense();
            e.id = id; e.amountMinor = amountMinor; e.category = category; e.note = note;
            e.spentOn = spentOn; e.entryId = entryId; e.time = time;
            return e;
        }
    }

    public static final class Habit {
        public int id;
        public String title;
        public String icon;
        public String kind; // check, goal or limit
        public String unit;
        public int target;
        public int step;
        public int points;
        public List<Integer> days = new ArrayList<>(); // 0 = Sunday
        public String remindAt;
        public boolean archived;
        public String createdOn;

        public Habit copy() {
            Habit h = new Habit();
            h.id = id; h.title = title; h.icon = icon; h.kind = kind; h.unit = unit; h.target = target;
            h.step = step; h.points = points; h.days = new ArrayList<>(days); h.remindAt = remindAt;
            h.archived = archived; h.createdOn = createdOn;
            return h;
        }
    }

    public static final class HabitLog {
        public int habitId;
        public String day;
        public int value;
        public String time;

        public HabitLog copy() {
            HabitLog l = new HabitLog();
            l.habitId = habitId; l.day = day; l.value = value; l.time = time;
            return l;
        }
    }

    public static final class Entry {
        public int id;
        public String day;
        public String time;
        public String text = "";
        public Integer mood; // 1 to 5
        public List<String> tags = new ArrayList<>();
        public String promptId;
        public int wordCount;
        public String createdAt;
        public String updatedAt;

        public Entry copy() {
            Entry e = new Entry();
            e.id = id; e.day = day; e.time = time; e.text = text; e.mood = mood;
            e.tags = new ArrayList<>(tags); e.promptId = promptId; e.wordCount = wordCount;
            e.createdAt = createdAt; e.updatedAt = updatedAt;
            return e;
        }
    }

    public static final class Music {
        public int id;
        public String provider;
        public String kind;
        public String url;
        public String embedUrl;
        public String label;

        public Music copy() {
            Music m = new Music();
            m.id = id; m.provider = provider; m.kind = kind; m.url = url; m.embedUrl = embedUrl;
            m.label = label;
            return m;
        }
    }

    public static final class Settings {
        public String name = "";
        public String currency = "INR";
        public long monthlyBudgetMinor;
        public boolean setupDone;
        public Integer currentMusicId;
        public boolean notifications;
        public String journalReminder = "";

        public Settings copy() {
            Settings s = new Settings();
            s.name = name; s.currency = currency; s.monthlyBudgetMinor = monthlyBudgetMinor;
            s.setupDone = setupDone; s.currentMusicId = currentMusicId;
            s.notifications = notifications; s.journalReminder = journalReminder;
            return s;
        }
    }

    /** Everything Day Hub keeps. Ids keep counting up, so a deleted row's number is never reused. */
    public static final class State {
        public Settings settings = new Settings();
        public List<Task> tasks = new ArrayList<>();
        public List<Expense> expenses = new ArrayList<>();
        public List<Habit> habits = new ArrayList<>();
        public List<HabitLog> habitLogs = new ArrayList<>();
        public List<Entry> journal = new ArrayList<>();
        public Map<String, String> badges = new LinkedHashMap<>(); // badge id -> day unlocked
        public List<Music> music = new ArrayList<>();
        public int seqTasks;
        public int seqExpenses;
        public int seqHabits;
        public int seqJournal;
        public int seqMusic;
    }
}

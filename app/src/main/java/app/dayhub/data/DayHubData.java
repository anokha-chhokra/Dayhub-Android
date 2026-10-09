package app.dayhub.data;

import static app.dayhub.data.DataError.bad;
import static app.dayhub.data.DataError.notFound;
import static app.dayhub.data.Validate.missing;

import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Model.HabitLog;
import app.dayhub.data.Model.Music;
import app.dayhub.data.Model.Settings;
import app.dayhub.data.Model.State;
import app.dayhub.data.Model.Task;

import app.dayhub.DataStore;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Feature 5: Day Hub's data engine, the Java twin of the web app's store plus the validation its
 * routes apply. It holds tasks, habits (and their daily logs), journal entries, expenses, music
 * links, badges and settings, and saves them as one JSON document through {@link DataStore}.
 *
 * Mutating methods that take a {@link JSONObject} body validate it exactly as the web app's API
 * does and throw a {@link DataError} on bad input. Nothing is written until {@link #commit()}.
 * Rows handed out are copies, so changing one never changes the stored data.
 */
public final class DayHubData {
    private final DataStore file; // null keeps everything in memory (tests)
    private State state;
    private boolean dirty;
    private String lastRaw; // exactly what is saved right now; null when nothing is

    private DayHubData(DataStore file, State state, String lastRaw) {
        this.file = file;
        this.state = state;
        this.lastRaw = lastRaw;
    }

    /** An engine with nothing saved anywhere. */
    public static DayHubData inMemory() {
        return new DayHubData(null, new State(), null);
    }

    /**
     * Opens the saved data (empty on first run). Throws {@link DamagedDataException} when it cannot
     * be used; the file is left exactly as it was.
     */
    public static DayHubData open(DataStore file) throws IOException, DamagedDataException {
        String raw = file.read();
        return new DayHubData(file, load(raw), raw);
    }

    /**
     * An empty engine that saves to {@code file}, for when the saved data is unusable and the
     * person chose to carry on without it. Call only after the old file has been moved aside.
     */
    public static DayHubData startFresh(DataStore file) {
        return new DayHubData(file, new State(), null);
    }

    static State load(String raw) throws DamagedDataException {
        if (raw == null) return new State();
        Object parsed;
        try {
            JSONTokener tokener = new JSONTokener(raw);
            parsed = tokener.nextValue();
            if (tokener.nextClean() != 0) throw new JSONException("Unexpected text after the data");
        } catch (JSONException e) {
            throw new DamagedDataException("The saved data cannot be read.", raw);
        }
        if (parsed instanceof JSONObject) {
            Object v = ((JSONObject) parsed).opt("v");
            if (v instanceof Number && ((Number) v).doubleValue() > DocumentCodec.FORMAT) {
                throw new DamagedDataException("This data was saved by a newer version of Day Hub.", raw);
            }
        }
        try {
            return DocumentCodec.read(parsed);
        } catch (DataError e) {
            throw new DamagedDataException("The saved data does not look right (" + e.getMessage() + ").", raw);
        }
    }

    // ---------- saving ----------

    private void touch() {
        dirty = true;
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    /**
     * Saves if anything changed. The write is atomic (see {@link DataStore}). If it fails, every
     * change since the last save is undone, so memory always matches what is on disk, and a 507
     * {@link DataError} is thrown.
     */
    public void commit() {
        if (!dirty) return;
        String text;
        try {
            text = DocumentCodec.write(state);
            if (file != null) file.write(text);
        } catch (IOException | RuntimeException e) {
            rollback();
            throw DataError.notSaved(e);
        }
        lastRaw = text;
        dirty = false;
    }

    /**
     * Forgets everything done since the last save, including the id counters, so a half-finished
     * operation leaves no trace.
     */
    public void rollback() {
        try {
            state = load(lastRaw);
        } catch (DamagedDataException e) {
            // lastRaw was read or written by this engine, so this cannot happen; keep going as is.
        }
        dirty = false;
    }

    // ---------- settings ----------

    public Settings getSettings() {
        return state.settings.copy();
    }

    /** Applies the fields present in a settings body (name, currency, monthlyBudget, ...). */
    public Settings updateSettings(JSONObject b) {
        Settings next = state.settings.copy();
        if (b.has("name")) {
            String name = Validate.optStr(b.opt("name"), "name", 40);
            next.name = name == null ? "" : name;
        }
        if (b.has("currency")) next.currency = Validate.currency(b.opt("currency"));
        if (b.has("monthlyBudget")) {
            Object v = b.opt("monthlyBudget");
            boolean none = missing(v) || "".equals(v) || (v instanceof Number && ((Number) v).doubleValue() == 0);
            next.monthlyBudgetMinor = none ? 0 : Validate.moneyToMinor(v, "monthly budget", true);
        }
        if (b.has("notifications")) {
            if (!(b.opt("notifications") instanceof Boolean)) throw bad("notifications must be true or false");
            next.notifications = (Boolean) b.opt("notifications");
        }
        if (b.has("journalReminder")) {
            String r = Validate.hhmm(b.opt("journalReminder"), "journal reminder");
            next.journalReminder = r == null ? "" : r;
        }
        state.settings = next;
        touch();
        return next.copy();
    }

    /** Marks the first-run setup as finished. */
    public void setSetupDone(boolean done) {
        state.settings.setupDone = done;
        touch();
    }

    /** True for a value JavaScript would call truthy: not null, false, 0 or an empty string. */
    private static boolean truthy(Object v) {
        if (missing(v) || Boolean.FALSE.equals(v) || "".equals(v)) return false;
        return !(v instanceof Number && ((Number) v).doubleValue() == 0);
    }

    /**
     * The first-run setup, all in one go so a half-finished setup never sticks: every answer is
     * checked first, and if any is wrong nothing is saved. The body may hold name, currency,
     * monthlyBudget, musicUrl, habits (a list of {@link HabitPresets} ids) and today.
     */
    public Settings setup(JSONObject b) {
        String name = Validate.optStr(b.opt("name"), "name", 40);
        String currency = b.has("currency") ? Validate.currency(b.opt("currency")) : null;
        Object budget = b.opt("monthlyBudget");
        Long budgetMinor = missing(budget) || "".equals(budget)
                ? null : Long.valueOf(Validate.moneyToMinor(budget, "monthly budget", true));
        MusicLinkParser.Parsed music = truthy(b.opt("musicUrl")) ? MusicLinkParser.parse(b.opt("musicUrl")) : null;
        List<HabitPresets.Preset> presets = new ArrayList<>();
        if (b.opt("habits") instanceof org.json.JSONArray) {
            org.json.JSONArray ids = (org.json.JSONArray) b.opt("habits");
            for (int i = 0; i < ids.length(); i++) {
                HabitPresets.Preset p = HabitPresets.byId(ids.opt(i));
                if (p == null) throw bad("Unknown habit \"" + ids.opt(i) + "\"");
                presets.add(p);
            }
        }
        String today = Validate.todayOf(b.opt("today"));

        // Everything is valid: now change things.
        Settings s = state.settings;
        s.setupDone = true;
        s.name = name == null ? "" : name;
        if (currency != null) s.currency = currency;
        if (budgetMinor != null) s.monthlyBudgetMinor = budgetMinor;
        for (HabitPresets.Preset p : presets) {
            Habit h = new Habit();
            h.id = ++state.seqHabits;
            h.title = p.title;
            h.icon = p.icon;
            h.kind = p.kind;
            h.unit = p.unit;
            h.target = p.target;
            h.step = p.step;
            h.points = p.points;
            h.days = new ArrayList<>(java.util.Arrays.asList(0, 1, 2, 3, 4, 5, 6));
            h.remindAt = null;
            h.archived = false;
            h.createdOn = today;
            state.habits.add(h);
        }
        touch();
        if (music != null) addMusic(music.url, music.defaultLabel, true);
        return s.copy();
    }

    // ---------- tasks ----------

    private static int byDue(Task a, Task b) {
        int c = Boolean.compare(a.done, b.done);
        if (c != 0) return c;
        c = Boolean.compare(a.dueOn == null, b.dueOn == null);
        if (c != 0) return c;
        if (a.dueOn != null) {
            c = a.dueOn.compareTo(b.dueOn);
            if (c != 0) return c;
        }
        c = Integer.compare(b.priority, a.priority);
        return c != 0 ? c : Integer.compare(a.id, b.id);
    }

    private Task taskRow(int id) {
        for (Task t : state.tasks) if (t.id == id) return t;
        return null;
    }

    private static <T> List<T> take(List<T> list, int limit) {
        return new ArrayList<>(list.subList(0, Math.min(limit, list.size())));
    }

    /** Tasks by status: "open" (default), "done" (newest 100) or "all". */
    public List<Task> listTasks(String status) {
        String s = Validate.oneOf(status, java.util.Arrays.asList("open", "done", "all"), "status", "open");
        List<Task> out = new ArrayList<>();
        for (Task t : state.tasks) if (s.equals("all") || t.done == s.equals("done")) out.add(t.copy());
        if (s.equals("done")) {
            out.sort((a, b) -> {
                int c = (b.doneOn == null ? "" : b.doneOn).compareTo(a.doneOn == null ? "" : a.doneOn);
                return c != 0 ? c : Integer.compare(b.id, a.id);
            });
            return take(out, 100);
        }
        out.sort(DayHubData::byDue);
        return take(out, 500);
    }

    public List<Task> allTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : state.tasks) out.add(t.copy());
        out.sort(DayHubData::byDue);
        return out;
    }

    public Task getTask(int id) {
        Task t = taskRow(id);
        return t == null ? null : t.copy();
    }

    public Task createTask(JSONObject b) {
        Task t = new Task();
        t.title = Validate.str(b.opt("title"), "title", 200);
        t.dueOn = Validate.optIsoDate(b.opt("dueOn"), "due date");
        t.priority = Validate.oneOf(b.opt("priority"), new int[] {0, 1}, "priority", 0);
        t.id = ++state.seqTasks;
        t.createdAt = DocumentCodec.nowIso();
        state.tasks.add(t);
        touch();
        return t.copy();
    }

    /** Edits title, dueOn, priority or done. Marking done records today's date and the time. */
    public Task updateTask(int id, JSONObject b) {
        String title = null;
        String dueOn = null;
        int priority = 0;
        Boolean done = null;
        String doneOn = null;
        String doneTime = null;
        if (b.has("title")) title = Validate.str(b.opt("title"), "title", 200);
        if (b.has("dueOn")) dueOn = Validate.optIsoDate(b.opt("dueOn"), "due date");
        if (b.has("priority")) priority = Validate.oneOf(b.opt("priority"), new int[] {0, 1}, "priority", null);
        if (b.has("done")) {
            if (!(b.opt("done") instanceof Boolean)) throw bad("done must be true or false");
            done = (Boolean) b.opt("done");
            doneOn = done ? Validate.todayOf(b.opt("today")) : null;
            doneTime = done ? Validate.hhmm(b.opt("time")) : null;
        }
        Task row = taskRow(id);
        if (row == null) throw notFound("Task not found");
        if (b.has("title")) row.title = title;
        if (b.has("dueOn")) row.dueOn = dueOn;
        if (b.has("priority")) row.priority = priority;
        if (done != null) {
            row.done = done;
            row.doneOn = doneOn;
            row.doneTime = doneTime;
        }
        touch();
        return row.copy();
    }

    public void deleteTask(int id) {
        Task row = taskRow(id);
        if (row == null) throw notFound("Task not found");
        state.tasks.remove(row);
        touch();
    }

    /** What Home shows: open tasks that are undated or due by today, plus those finished today. */
    public List<Task> dashboardTasks(String today) {
        List<Task> out = new ArrayList<>();
        for (Task t : state.tasks) {
            boolean open = !t.done && (t.dueOn == null || t.dueOn.compareTo(today) <= 0);
            if (open || (t.done && today.equals(t.doneOn))) out.add(t.copy());
        }
        out.sort(DayHubData::byDue);
        return out;
    }

    public List<Task> doneTasksOn(String day) {
        List<Task> out = new ArrayList<>();
        for (Task t : state.tasks) if (t.done && day.equals(t.doneOn)) out.add(t.copy());
        out.sort((a, b) -> {
            int c = (a.doneTime == null ? "" : a.doneTime).compareTo(b.doneTime == null ? "" : b.doneTime);
            return c != 0 ? c : Integer.compare(a.id, b.id);
        });
        return out;
    }

    // ---------- expenses ----------

    private static int newestFirst(Expense a, Expense b) {
        int c = b.spentOn.compareTo(a.spentOn);
        return c != 0 ? c : Integer.compare(b.id, a.id);
    }

    /** Adds an expense from a body (amount, category, note, spentOn, time). */
    public Expense addExpense(JSONObject b) {
        Object spentOn = b.opt("spentOn");
        boolean hasDate = !missing(spentOn) && !"".equals(spentOn);
        return createExpense(
                Validate.moneyToMinor(b.opt("amount"), "amount"),
                Validate.oneOf(b.opt("category"), Model.CATEGORIES, "category", "Other"),
                Validate.optStr(b.opt("note"), "note", 120),
                hasDate ? Validate.isoDate(spentOn, "date") : Validate.todayOf(b.opt("today")),
                null,
                Validate.hhmm(b.opt("time")));
    }

    /** Adds an already-clean expense, optionally linked to the journal entry it came from. */
    public Expense createExpense(long amountMinor, String category, String note, String spentOn,
                                 Integer entryId, String time) {
        Expense e = new Expense();
        e.id = ++state.seqExpenses;
        e.amountMinor = amountMinor;
        e.category = category;
        e.note = note;
        e.spentOn = spentOn;
        e.entryId = entryId;
        e.time = time;
        state.expenses.add(e);
        touch();
        return e.copy();
    }

    public void deleteExpense(int id) {
        for (Expense e : state.expenses) {
            if (e.id == id) {
                state.expenses.remove(e);
                touch();
                return;
            }
        }
        throw notFound("Expense not found");
    }

    /** Expenses newest first; with {@code from}/{@code to} (to is exclusive) only that range. */
    public List<Expense> listExpenses(String from, String to) {
        List<Expense> out = new ArrayList<>();
        for (Expense e : state.expenses) {
            if (from == null || to == null || (e.spentOn.compareTo(from) >= 0 && e.spentOn.compareTo(to) < 0)) {
                out.add(e.copy());
            }
        }
        out.sort(DayHubData::newestFirst);
        return out;
    }

    /** The most recent expenses that have a note, newest first; used to learn what words mean. */
    public List<Expense> recentExpensesWithNotes(int limit) {
        List<Expense> out = new ArrayList<>();
        for (Expense e : state.expenses) if (e.note != null && !e.note.isEmpty()) out.add(e.copy());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return take(out, limit);
    }

    public List<Expense> listEntryExpenses(int entryId) {
        List<Expense> out = new ArrayList<>();
        for (Expense e : state.expenses) if (e.entryId != null && e.entryId == entryId) out.add(e.copy());
        out.sort(Comparator.comparingInt(e -> e.id));
        return out;
    }

    /** Keeps spending that came from a journal entry on the entry's day and time. */
    public int moveEntryExpenses(int entryId, String fromDay, String spentOn, String time) {
        int n = 0;
        for (Expense e : state.expenses) {
            if (e.entryId != null && e.entryId == entryId && e.spentOn.equals(fromDay)) {
                e.spentOn = spentOn;
                e.time = time;
                n++;
            }
        }
        if (n > 0) touch();
        return n;
    }

    public static final class CategoryTotal {
        public final String category;
        public long totalMinor;
        public int count;

        CategoryTotal(String category) {
            this.category = category;
        }
    }

    public static final class Summary {
        public long totalMinor;
        public int count;
        public List<CategoryTotal> byCategory = new ArrayList<>(); // biggest first
    }

    /** Totals for expenses from {@code from} up to (not including) {@code to}. */
    public Summary summarizeExpenses(String from, String to) {
        Summary sum = new Summary();
        Map<String, CategoryTotal> groups = new LinkedHashMap<>();
        for (Expense e : state.expenses) {
            if (e.spentOn.compareTo(from) < 0 || e.spentOn.compareTo(to) >= 0) continue;
            sum.totalMinor += e.amountMinor;
            sum.count++;
            CategoryTotal g = groups.get(e.category);
            if (g == null) {
                g = new CategoryTotal(e.category);
                groups.put(e.category, g);
            }
            g.totalMinor += e.amountMinor;
            g.count++;
        }
        sum.byCategory = new ArrayList<>(groups.values());
        sum.byCategory.sort((a, b) -> {
            int c = Long.compare(b.totalMinor, a.totalMinor);
            return c != 0 ? c : a.category.compareTo(b.category);
        });
        return sum;
    }

    // ---------- habits ----------

    private Habit habitRow(int id) {
        for (Habit h : state.habits) if (h.id == id) return h;
        return null;
    }

    /** Merges a body over {@code base} (null for a new habit) and validates the result. */
    private static Habit habitFields(JSONObject b, Habit base) {
        Habit h = new Habit();
        String baseKind = base == null ? null : base.kind;
        h.kind = b.has("kind") || baseKind == null
                ? Validate.oneOf(b.opt("kind"), Model.KINDS, "type", baseKind == null ? "check" : baseKind)
                : baseKind;
        boolean check = h.kind.equals("check");
        h.target = check ? 1 : Validate.intIn(pick(b, "target", base == null ? null : base.target),
                "target", 1, 1_000_000, 1);
        h.title = Validate.str(pick(b, "title", base == null ? null : base.title), "title", 60);
        String icon = Validate.optStr(pick(b, "icon", base == null ? null : base.icon), "icon", 8);
        h.icon = icon == null ? "📌" : icon;
        String unit = check ? "" : Validate.optStr(pick(b, "unit", base == null ? null : base.unit), "unit", 20);
        h.unit = unit == null ? "" : unit;
        h.step = Math.min(Validate.intIn(pick(b, "step", base == null ? null : base.step),
                "step", 1, 1_000_000, 1), h.target);
        h.points = Validate.intIn(pick(b, "points", base == null ? null : base.points), "points", 1, 100, 10);
        h.days = Validate.weekdays(pick(b, "days", base == null ? null : base.days));
        h.remindAt = Validate.hhmm(pick(b, "remindAt", base == null ? null : base.remindAt), "reminder time");
        return h;
    }

    /** {@code b[key] ?? fallback}: the body's value unless it is missing or null. */
    private static Object pick(JSONObject b, String key, Object fallback) {
        Object v = b.opt(key);
        return missing(v) ? fallback : v;
    }

    public List<Habit> listHabits(boolean includeArchived) {
        List<Habit> out = new ArrayList<>();
        for (Habit h : state.habits) if (includeArchived || !h.archived) out.add(h.copy());
        out.sort(Comparator.comparingInt(h -> h.id));
        return out;
    }

    public Habit getHabit(int id) {
        Habit h = habitRow(id);
        return h == null ? null : h.copy();
    }

    /** Adds a habit from a body (title, kind, unit, target, step, points, days, remindAt, icon). */
    public Habit createHabit(JSONObject b) {
        Habit h = habitFields(b, null);
        h.createdOn = Validate.todayOf(b.opt("today"));
        h.id = ++state.seqHabits;
        state.habits.add(h);
        touch();
        return h.copy();
    }

    public Habit updateHabit(int id, JSONObject b) {
        Habit row = habitRow(id);
        if (row == null) throw notFound("Habit not found");
        Habit merged = habitFields(b, row);
        row.title = merged.title;
        row.icon = merged.icon;
        row.kind = merged.kind;
        row.unit = merged.unit;
        row.target = merged.target;
        row.step = merged.step;
        row.points = merged.points;
        row.days = merged.days;
        row.remindAt = merged.remindAt;
        touch();
        return row.copy();
    }

    /** "Deleting" a habit hides it and keeps its history, so points and streaks stay. */
    public void archiveHabit(int id) {
        Habit row = habitRow(id);
        if (row == null) throw notFound("Habit not found");
        row.archived = true;
        touch();
    }

    private static void noFuture(String day, String today, String what) {
        if (day.compareTo(today) > 0) throw bad("You can't " + what + " a day that hasn't happened yet");
    }

    /** Records a habit's progress for a day from a body (day, value, time). Zero clears the day. */
    public void logHabit(int habitId, JSONObject b) {
        Habit habit = habitRow(habitId);
        if (habit == null || habit.archived) throw notFound("Habit not found");
        String day = Validate.isoDate(b.opt("day"), "day");
        noFuture(day, Validate.todayOf(b.opt("today")), "log");
        int value = Validate.intIn(b.opt("value"), "value", 0, 1_000_000, null);
        if (habit.kind.equals("check")) value = Math.min(value, 1);
        setHabitLog(habitId, day, value, Validate.hhmm(b.opt("time")));
    }

    /** Sets (or with a value of 0 or less, removes) a habit's log for a day. */
    public void setHabitLog(int habitId, String day, int value, String time) {
        HabitLog existing = null;
        for (HabitLog l : state.habitLogs) if (l.habitId == habitId && l.day.equals(day)) existing = l;
        if (value <= 0) {
            if (existing != null) state.habitLogs.remove(existing);
        } else if (existing != null) {
            existing.value = value;
            existing.time = time;
        } else {
            HabitLog l = new HabitLog();
            l.habitId = habitId;
            l.day = day;
            l.value = value;
            l.time = time;
            state.habitLogs.add(l);
        }
        touch();
    }

    /** Logs sorted by day; with {@code from}/{@code to} (to exclusive) only that range. */
    public List<HabitLog> listLogs(String from, String to) {
        List<HabitLog> out = new ArrayList<>();
        for (HabitLog l : state.habitLogs) {
            if (from == null || to == null || (l.day.compareTo(from) >= 0 && l.day.compareTo(to) < 0)) {
                out.add(l.copy());
            }
        }
        out.sort(Comparator.comparing(l -> l.day));
        return out;
    }

    // ---------- journal ----------

    private Entry entryRow(int id) {
        for (Entry e : state.journal) if (e.id == id) return e;
        return null;
    }

    private static int entryNewestFirst(Entry a, Entry b) {
        int c = b.day.compareTo(a.day);
        if (c != 0) return c;
        c = (b.time == null ? "" : b.time).compareTo(a.time == null ? "" : a.time);
        return c != 0 ? c : Integer.compare(b.id, a.id);
    }

    public Entry getEntry(int id) {
        Entry e = entryRow(id);
        return e == null ? null : e.copy();
    }

    /** Entries newest first; with {@code from}/{@code to} (to exclusive) only that range. */
    public List<Entry> listEntries(String from, String to) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : state.journal) {
            if (from == null || to == null || (e.day.compareTo(from) >= 0 && e.day.compareTo(to) < 0)) {
                out.add(e.copy());
            }
        }
        out.sort(DayHubData::entryNewestFirst);
        return out;
    }

    /** Spending typed or confirmed alongside a journal entry, already validated. */
    private static final class EntrySpend {
        long amountMinor;
        String category;
        String note;
        int daysAgo;
    }

    private static List<EntrySpend> entrySpending(Object list) {
        List<EntrySpend> out = new ArrayList<>();
        if (!(list instanceof org.json.JSONArray)) return out;
        org.json.JSONArray inputs = (org.json.JSONArray) list;
        if (inputs.length() > 5) throw bad("At most 5 expenses per entry");
        for (int i = 0; i < inputs.length(); i++) {
            Object raw = inputs.opt(i);
            if (!(raw instanceof JSONObject)) throw bad("Expected a JSON object");
            JSONObject o = (JSONObject) raw;
            EntrySpend x = new EntrySpend();
            x.amountMinor = Validate.moneyToMinor(o.opt("amount"), "amount");
            x.category = Validate.oneOf(o.opt("category"), Model.CATEGORIES, "category", "Other");
            x.note = Validate.optStr(o.opt("note"), "note", 120);
            x.daysAgo = Validate.intIn(o.opt("daysAgo"), "daysAgo", 0, 30, 0);
            out.add(x);
        }
        return out;
    }

    /** Saves spending with an entry, on the entry's day (or some days earlier, as written). */
    private void saveEntrySpending(List<EntrySpend> spending, Entry entry) {
        for (EntrySpend x : spending) {
            createExpense(x.amountMinor, x.category, x.note,
                    x.daysAgo > 0 ? Validate.addDays(entry.day, -x.daysAgo) : entry.day,
                    entry.id, x.daysAgo > 0 ? null : entry.time);
        }
    }

    private static Integer moodOf(Object v) {
        return missing(v) || "".equals(v) ? null : Validate.intIn(v, "mood", 1, 5, null);
    }

    /**
     * Adds a journal entry from a body (text, mood, tags, day, time, promptId). An optional
     * {@code expenses} list (amount, category, note, daysAgo; at most 5) is saved with it; read
     * them back with {@link #listEntryExpenses}.
     */
    public Entry createEntry(JSONObject b) {
        String today = Validate.todayOf(b.opt("today"));
        Object dayRaw = b.opt("day");
        String day = missing(dayRaw) || "".equals(dayRaw) ? today : Validate.isoDate(dayRaw, "day");
        String text = Validate.optMultiline(b.opt("text"), "entry", 10_000);
        if (text == null) text = "";
        Integer mood = moodOf(b.opt("mood"));
        if (text.isEmpty() && mood == null) throw bad("Write something or pick a mood");
        Object prompt = b.opt("promptId");
        String promptId = missing(prompt) || "".equals(prompt)
                ? null : Validate.oneOf(prompt, Model.PROMPT_IDS, "prompt", null);
        List<EntrySpend> spending = entrySpending(b.opt("expenses"));
        noFuture(day, today, "write on");
        String time = Validate.hhmm(b.opt("time"));
        List<String> tags = Validate.tags(b.opt("tags"));

        Entry e = new Entry();
        e.id = ++state.seqJournal;
        e.day = day;
        e.time = time;
        e.text = text;
        e.mood = mood;
        e.tags = tags;
        e.promptId = promptId;
        e.wordCount = Validate.wordCount(text);
        e.createdAt = DocumentCodec.nowIso();
        e.updatedAt = e.createdAt;
        state.journal.add(e);
        touch();
        saveEntrySpending(spending, e);
        return e.copy();
    }

    /**
     * Edits an entry (text, mood, tags, day, time). Spending written with the entry follows it
     * when its day or time changes.
     */
    public Entry updateEntry(int id, JSONObject b) {
        Entry row = entryRow(id);
        if (row == null) throw notFound("Entry not found");
        String text = row.text;
        Integer mood = row.mood;
        List<String> tags = row.tags;
        String day = row.day;
        String time = row.time;
        String today = Validate.todayOf(b.opt("today"));
        if (b.has("text")) {
            text = Validate.optMultiline(b.opt("text"), "entry", 10_000);
            if (text == null) text = "";
        }
        if (b.has("mood")) mood = moodOf(b.opt("mood"));
        if (b.has("tags")) tags = Validate.tags(b.opt("tags"));
        if (b.has("day")) {
            day = Validate.isoDate(b.opt("day"), "day");
            noFuture(day, today, "move an entry to");
        }
        if (b.has("time")) time = Validate.hhmm(b.opt("time"));
        if (text.isEmpty() && mood == null) throw bad("Write something or pick a mood");
        List<EntrySpend> spending = entrySpending(b.opt("expenses"));

        String oldDay = row.day;
        String oldTime = row.time;
        row.text = text;
        row.wordCount = Validate.wordCount(text);
        row.mood = mood;
        row.tags = new ArrayList<>(tags);
        row.day = day;
        row.time = time;
        row.updatedAt = DocumentCodec.nowIso();
        touch();
        boolean sameTime = oldTime == null ? time == null : oldTime.equals(time);
        if (!day.equals(oldDay) || !sameTime) moveEntryExpenses(id, oldDay, day, time);
        saveEntrySpending(spending, row);
        return row.copy();
    }

    /** Deletes an entry. Spending written with it stays, just unlinked. */
    public void deleteEntry(int id) {
        Entry row = entryRow(id);
        if (row == null) throw notFound("Entry not found");
        state.journal.remove(row);
        for (Expense e : state.expenses) if (e.entryId != null && e.entryId == id) e.entryId = null;
        touch();
    }

    // ---------- badges ----------

    public Map<String, String> listBadges() {
        return new LinkedHashMap<>(state.badges);
    }

    /** Records badges not earned before and returns the new ones. */
    public List<String> unlockBadges(List<String> ids, String day) {
        List<String> fresh = new ArrayList<>();
        for (String id : ids) if (!state.badges.containsKey(id)) fresh.add(id);
        for (String id : fresh) state.badges.put(id, day);
        if (!fresh.isEmpty()) touch();
        return fresh;
    }

    // ---------- music ----------

    /** Saved links, newest first (up to 200). */
    public List<Music> listMusic() {
        List<Music> out = new ArrayList<>();
        for (Music m : state.music) out.add(m.copy());
        out.sort((a, b) -> Integer.compare(b.id, a.id));
        return take(out, 200);
    }

    public Music getMusic(int id) {
        for (Music m : state.music) if (m.id == id) return m.copy();
        return null;
    }

    /**
     * Adds a pasted YouTube link. Saving the same link again just renames it. Unless
     * {@code makeCurrent} is false it becomes the link Home plays.
     */
    public Music addMusic(Object url, Object label, boolean makeCurrent) {
        MusicLinkParser.Parsed parsed = MusicLinkParser.parse(url);
        String name = Validate.optStr(label, "label", 60);
        if (name == null) name = parsed.defaultLabel;

        Music link = null;
        for (Music m : state.music) if (m.embedUrl.equals(parsed.embedUrl)) link = m;
        if (link != null) {
            link.label = name; // same link again: just rename it
        } else {
            link = new Music();
            link.id = ++state.seqMusic;
            link.provider = parsed.provider;
            link.kind = parsed.kind;
            link.url = parsed.url;
            link.embedUrl = parsed.embedUrl;
            link.label = name;
            state.music.add(link);
        }
        if (makeCurrent) state.settings.currentMusicId = link.id;
        touch();
        return link.copy();
    }

    /**
     * Chooses the link Home plays from an id as it arrives in a request: missing or null clears it,
     * anything else must be a valid id of a saved link.
     */
    public void chooseMusic(Object idInput) {
        if (missing(idInput)) {
            setCurrentMusic(null);
            return;
        }
        setCurrentMusic(Validate.id(idInput));
    }

    /** Chooses the link Home plays; null clears it. */
    public void setCurrentMusic(Integer id) {
        if (id != null && getMusic(id) == null) throw notFound("Link not found");
        state.settings.currentMusicId = id;
        touch();
    }

    public void deleteMusic(int id) {
        for (Music m : state.music) {
            if (m.id == id) {
                state.music.remove(m);
                if (state.settings.currentMusicId != null && state.settings.currentMusicId == id) {
                    state.settings.currentMusicId = null;
                }
                touch();
                return;
            }
        }
        throw notFound("Link not found");
    }

    // ---------- backup and restore ----------

    /**
     * True when Day Hub holds no tasks, expenses, habits or journal entries (settings, music and
     * badges do not count), as in a fresh install. Overwriting a backup with this would lose it.
     */
    public boolean isBlank() {
        return state.tasks.isEmpty() && state.expenses.isEmpty() && state.habits.isEmpty() && state.journal.isEmpty();
    }

    /** Everything in display order (tasks by due date, newest first, ...), as the backup file lists it. */
    State orderedState() {
        State o = new State();
        o.settings = state.settings.copy();
        o.tasks = allTasks();
        o.expenses = listExpenses(null, null);
        o.habits = listHabits(true);
        o.habitLogs = listLogs(null, null);
        o.journal = listEntries(null, null);
        o.badges = listBadges();
        o.music = new ArrayList<>();
        for (Music m : state.music) o.music.add(m.copy());
        o.music.sort((a, b) -> Integer.compare(b.id, a.id));
        return o;
    }

    /** Everything as a backup file in the web app's format, in display order. */
    public JSONObject exportAll() {
        return DocumentCodec.export(orderedState());
    }

    /**
     * Replaces everything with a backup. Throws (and changes nothing) if the document is not a
     * good backup. Returns how many rows of each kind were restored.
     */
    public Map<String, Integer> importAll(Object document) {
        State next = DocumentCodec.read(document);
        state = next;
        touch();
        return DocumentCodec.counts(next);
    }

    /** Restores from the text of a backup file. A file that is not valid JSON changes nothing. */
    public Map<String, Integer> importAll(String text) {
        Object parsed;
        try {
            JSONTokener tokener = new JSONTokener(text);
            parsed = tokener.nextValue();
            if (tokener.nextClean() != 0) throw new JSONException("Unexpected text after the data");
        } catch (JSONException e) {
            throw bad("That file is not valid JSON");
        }
        return importAll(parsed);
    }
}

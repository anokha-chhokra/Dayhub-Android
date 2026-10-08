package app.dayhub.data;

import static app.dayhub.data.DataError.bad;
import static app.dayhub.data.Validate.missing;

import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Model.Habit;
import app.dayhub.data.Model.HabitLog;
import app.dayhub.data.Model.Music;
import app.dayhub.data.Model.Settings;
import app.dayhub.data.Model.State;
import app.dayhub.data.Model.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Reads and writes Day Hub's JSON document, in the web app's format, so a backup made by either
 * app restores in the other. Reading is strict: a bad document is refused with a message that says
 * which row is wrong, and nothing is guessed at.
 */
public final class DocumentCodec {
    /** Version number written into saved documents. */
    public static final int FORMAT = 1;

    private static final int MAX_ROWS = 200_000;
    private static final int MAX_ID = 2_000_000_000;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private DocumentCodec() {}

    /** The current time as a UTC timestamp like 2026-10-05T10:00:00.000Z. */
    public static String nowIso() {
        return STAMP.format(Instant.now());
    }

    private interface Cleaner<T> {
        T clean(JSONObject row);
    }

    // ---------- reading ----------

    private static boolean bool(Object v, String field) {
        if (Boolean.TRUE.equals(v)) return true;
        if (v instanceof Number && ((Number) v).doubleValue() == 1.0) return true;
        if (Boolean.FALSE.equals(v) || missing(v)) return false;
        if (v instanceof Number && ((Number) v).doubleValue() == 0.0) return false;
        throw bad(field + " must be true or false");
    }

    private static String optText(Object v, String field, int max) {
        return missing(v) || "".equals(v) ? null : Validate.optStr(v, field, max);
    }

    private static String stamp(Object v) {
        if (v instanceof String) {
            try {
                Instant.parse((String) v);
                return (String) v;
            } catch (DateTimeParseException ignored) {
                // fall through to now
            }
        }
        return nowIso();
    }

    private static int rowId(Object v) {
        return Validate.intIn(v, "id", 1, MAX_ID, null);
    }

    private static Integer nullableId(Object v, String field) {
        return missing(v) ? null : Validate.intIn(v, field, 1, MAX_ID, null);
    }

    /** Journal text keeps its line breaks. */
    private static String longText(Object v, String field) {
        if (missing(v)) return "";
        if (!(v instanceof String)) throw bad(field + " must be text");
        if (((String) v).length() > 10_000) throw bad(field + " is too long");
        return (String) v;
    }

    private static Task cleanTask(JSONObject r) {
        Task t = new Task();
        t.id = rowId(r.opt("id"));
        t.title = Validate.str(r.opt("title"), "title", 200);
        t.dueOn = Validate.optIsoDate(r.opt("dueOn"), "due date");
        t.priority = Validate.oneOf(r.opt("priority"), new int[] {0, 1}, "priority", 0);
        t.done = bool(r.opt("done"), "done");
        t.doneOn = Validate.optIsoDate(r.opt("doneOn"), "done date");
        t.doneTime = Validate.hhmm(r.opt("doneTime"), "done time");
        t.createdAt = stamp(r.opt("createdAt"));
        return t;
    }

    private static Expense cleanExpense(JSONObject r) {
        Expense e = new Expense();
        e.id = rowId(r.opt("id"));
        e.amountMinor = Validate.longIn(r.opt("amountMinor"), "amount", 1, 100_000_000_000L, null);
        e.category = Validate.str(missing(r.opt("category")) ? "Other" : r.opt("category"), "category", 30);
        e.note = optText(r.opt("note"), "note", 120);
        e.spentOn = Validate.isoDate(r.opt("spentOn"), "date");
        e.entryId = nullableId(r.opt("entryId"), "entry");
        e.time = Validate.hhmm(r.opt("time"));
        return e;
    }

    private static Habit cleanHabit(JSONObject r) {
        Habit h = new Habit();
        h.id = rowId(r.opt("id"));
        h.title = Validate.str(r.opt("title"), "title", 60);
        h.icon = Validate.str(missing(r.opt("icon")) ? "📌" : r.opt("icon"), "icon", 16);
        h.kind = Validate.oneOf(r.opt("kind"), Model.KINDS, "type", "check");
        String unit = optText(r.opt("unit"), "unit", 20);
        h.unit = unit == null ? "" : unit;
        h.target = Validate.intIn(r.opt("target"), "target", 1, 1_000_000, 1);
        h.step = Validate.intIn(r.opt("step"), "step", 1, 1_000_000, 1);
        h.points = Validate.intIn(r.opt("points"), "points", 1, 100, 10);
        h.days = Validate.weekdays(r.opt("days"));
        h.remindAt = Validate.hhmm(r.opt("remindAt"), "reminder time");
        h.archived = bool(r.opt("archived"), "archived");
        h.createdOn = Validate.isoDate(r.opt("createdOn"), "created date");
        return h;
    }

    private static HabitLog cleanLog(JSONObject r) {
        HabitLog l = new HabitLog();
        l.habitId = rowId(r.opt("habitId"));
        l.day = Validate.isoDate(r.opt("day"), "day");
        l.value = Validate.intIn(r.opt("value"), "value", 1, 1_000_000, null);
        l.time = Validate.hhmm(r.opt("time"));
        return l;
    }

    private static Entry cleanEntry(JSONObject r) {
        Entry e = new Entry();
        e.text = longText(r.opt("text"), "entry");
        e.id = rowId(r.opt("id"));
        e.day = Validate.isoDate(r.opt("day"), "day");
        e.time = Validate.hhmm(r.opt("time"));
        e.mood = missing(r.opt("mood")) ? null : Validate.intIn(r.opt("mood"), "mood", 1, 5, null);
        e.tags = Validate.tags(r.opt("tags"));
        e.promptId = optText(r.opt("promptId"), "prompt", 40);
        Integer wc = wholeNumber(r.opt("wordCount"));
        e.wordCount = wc != null && wc >= 0 ? wc : Validate.wordCount(e.text);
        e.createdAt = stamp(r.opt("createdAt"));
        e.updatedAt = stamp(r.opt("updatedAt"));
        return e;
    }

    private static Music cleanMusic(JSONObject r) {
        Music m = new Music();
        m.id = rowId(r.opt("id"));
        m.provider = Validate.str(r.opt("provider"), "provider", 20);
        m.kind = Validate.str(r.opt("kind"), "kind", 20);
        m.url = Validate.str(r.opt("url"), "link", 2048);
        m.embedUrl = Validate.str(r.opt("embedUrl"), "embed link", 2048);
        m.label = Validate.str(r.opt("label"), "label", 60);
        return m;
    }

    private static Settings cleanSettings(Object raw) {
        Settings out = new Settings();
        if (missing(raw)) return out;
        JSONObject s = obj(raw);
        if (s.has("name")) {
            String name = Validate.optStr(s.opt("name"), "name", 40);
            out.name = name == null ? "" : name;
        }
        if (s.has("currency")) out.currency = Validate.currency(s.opt("currency"));
        if (s.has("monthlyBudgetMinor")) {
            out.monthlyBudgetMinor = Validate.longIn(s.opt("monthlyBudgetMinor"), "budget", 0, 100_000_000_000L, null);
        }
        if (s.has("setupDone")) out.setupDone = bool(s.opt("setupDone"), "setupDone");
        if (s.has("currentMusicId")) out.currentMusicId = nullableId(s.opt("currentMusicId"), "current music");
        if (s.has("notifications")) out.notifications = bool(s.opt("notifications"), "notifications");
        if (s.has("journalReminder")) {
            String r = Validate.hhmm(s.opt("journalReminder"), "journal reminder");
            out.journalReminder = r == null ? "" : r;
        }
        return out;
    }

    private static JSONObject obj(Object value) {
        if (!(value instanceof JSONObject)) throw bad("Expected a JSON object");
        return (JSONObject) value;
    }

    private static JSONArray rows(JSONObject data, String key) {
        Object v = data.opt(key);
        if (missing(v)) return new JSONArray();
        if (!(v instanceof JSONArray)) throw bad("\"" + key + "\" should be a list");
        if (((JSONArray) v).length() > MAX_ROWS) throw bad("\"" + key + "\" has too many rows");
        return (JSONArray) v;
    }

    private static <T> List<T> readTable(JSONObject data, String key, String label, Cleaner<T> cleaner,
                                         Function<T, Integer> idOf) {
        List<T> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        JSONArray list = rows(data, key);
        for (int i = 0; i < list.length(); i++) {
            try {
                T row = cleaner.clean(obj(list.opt(i)));
                if (idOf != null) {
                    int id = idOf.apply(row);
                    if (!seen.add(id)) throw bad("id " + id + " appears twice");
                }
                out.add(row);
            } catch (DataError e) {
                throw bad(label + " #" + (i + 1) + ": " + e.getMessage());
            }
        }
        return out;
    }

    /**
     * Builds a clean state from a backup file or from what was saved here. Throws a 400
     * {@link DataError} that says which row is wrong; the caller keeps its old data.
     */
    public static State read(Object document) {
        if (!(document instanceof JSONObject)) throw bad("That is not a Day Hub backup");
        JSONObject data = (JSONObject) document;
        boolean looksRight = false;
        for (String k : new String[] {"settings", "tasks", "expenses", "habits", "habitLogs", "journal", "badges", "music"}) {
            if (data.has(k)) looksRight = true;
        }
        if (!looksRight) throw bad("That is not a Day Hub backup");

        State s = new State();
        s.tasks = readTable(data, "tasks", "Task", DocumentCodec::cleanTask, t -> t.id);
        s.expenses = readTable(data, "expenses", "Expense", DocumentCodec::cleanExpense, e -> e.id);
        s.habits = readTable(data, "habits", "Habit", DocumentCodec::cleanHabit, h -> h.id);
        s.journal = readTable(data, "journal", "Journal entry", DocumentCodec::cleanEntry, e -> e.id);
        s.music = readTable(data, "music", "Music link", DocumentCodec::cleanMusic, m -> m.id);
        s.habitLogs = readTable(data, "habitLogs", "Habit log", DocumentCodec::cleanLog, null);
        s.settings = cleanSettings(data.opt("settings"));

        Object badges = data.opt("badges");
        if (!missing(badges)) {
            JSONObject b = obj(badges);
            Iterator<String> keys = b.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                if (id.isEmpty() || id.length() > 40) throw bad("Badge: bad name");
                s.badges.put(id, Validate.isoDate(b.opt(id), "badge \"" + id + "\" date"));
            }
        }

        tidyLinks(s);

        // Ids keep counting up, so a deleted row's number is never reused.
        Object seqRaw = data.opt("seq");
        JSONObject saved = seqRaw instanceof JSONObject ? (JSONObject) seqRaw : new JSONObject();
        s.seqTasks = Math.max(topId(s.tasks, t -> t.id), savedSeq(saved, "tasks"));
        s.seqExpenses = Math.max(topId(s.expenses, e -> e.id), savedSeq(saved, "expenses"));
        s.seqHabits = Math.max(topId(s.habits, h -> h.id), savedSeq(saved, "habits"));
        s.seqJournal = Math.max(topId(s.journal, e -> e.id), savedSeq(saved, "journal"));
        s.seqMusic = Math.max(topId(s.music, m -> m.id), savedSeq(saved, "music"));
        return s;
    }

    /** Tidies links between tables, the way the database used to by itself. */
    private static void tidyLinks(State s) {
        Set<Integer> habitIds = new HashSet<>();
        for (Habit h : s.habits) habitIds.add(h.id);
        Set<Integer> entryIds = new HashSet<>();
        for (Entry e : s.journal) entryIds.add(e.id);

        Set<String> musicUrls = new HashSet<>();
        List<Music> uniqueMusic = new ArrayList<>();
        for (Music m : s.music) if (musicUrls.add(m.embedUrl)) uniqueMusic.add(m);
        s.music = uniqueMusic;

        // One log per habit and day (the last one wins); logs for unknown habits are dropped.
        Map<String, HabitLog> lastLog = new LinkedHashMap<>();
        for (HabitLog l : s.habitLogs) {
            if (habitIds.contains(l.habitId)) {
                lastLog.put(l.habitId + "|" + l.day, l);
            }
        }
        s.habitLogs = new ArrayList<>(lastLog.values());

        for (Expense e : s.expenses) if (e.entryId != null && !entryIds.contains(e.entryId)) e.entryId = null;
        Integer current = s.settings.currentMusicId;
        if (current != null) {
            boolean found = false;
            for (Music m : s.music) if (m.id == current) found = true;
            if (!found) s.settings.currentMusicId = null;
        }
    }

    private static int savedSeq(JSONObject saved, String name) {
        Integer v = wholeNumber(saved.opt(name));
        return v == null ? 0 : v;
    }

    /** The value as an int if it is a whole number that fits, otherwise null. */
    private static Integer wholeNumber(Object v) {
        if (!(v instanceof Number)) return null;
        double d = ((Number) v).doubleValue();
        return d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE ? Integer.valueOf((int) d) : null;
    }

    private static <T> int topId(List<T> list, Function<T, Integer> idOf) {
        int top = 0;
        for (T row : list) top = Math.max(top, idOf.apply(row));
        return top;
    }

    // ---------- writing ----------
    //
    // Documents are built as ordered maps, so the text comes out in the same key order as the web
    // app's no matter which JSON library is underneath.

    private static Map<String, Object> tree(Task t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("title", t.title);
        m.put("dueOn", t.dueOn);
        m.put("priority", t.priority);
        m.put("done", t.done);
        m.put("doneOn", t.doneOn);
        m.put("doneTime", t.doneTime);
        m.put("createdAt", t.createdAt);
        return m;
    }

    private static Map<String, Object> tree(Expense e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("amountMinor", e.amountMinor);
        m.put("category", e.category);
        m.put("note", e.note);
        m.put("spentOn", e.spentOn);
        m.put("entryId", e.entryId);
        m.put("time", e.time);
        return m;
    }

    private static Map<String, Object> tree(Habit h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.id);
        m.put("title", h.title);
        m.put("icon", h.icon);
        m.put("kind", h.kind);
        m.put("unit", h.unit);
        m.put("target", h.target);
        m.put("step", h.step);
        m.put("points", h.points);
        m.put("days", new ArrayList<>(h.days));
        m.put("remindAt", h.remindAt);
        m.put("archived", h.archived);
        m.put("createdOn", h.createdOn);
        return m;
    }

    private static Map<String, Object> tree(HabitLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("habitId", l.habitId);
        m.put("day", l.day);
        m.put("value", l.value);
        m.put("time", l.time);
        return m;
    }

    private static Map<String, Object> tree(Entry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id);
        m.put("day", e.day);
        m.put("time", e.time);
        m.put("text", e.text);
        m.put("mood", e.mood);
        m.put("tags", new ArrayList<>(e.tags));
        m.put("promptId", e.promptId);
        m.put("wordCount", e.wordCount);
        m.put("createdAt", e.createdAt);
        m.put("updatedAt", e.updatedAt);
        return m;
    }

    private static Map<String, Object> tree(Music m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.id);
        out.put("provider", m.provider);
        out.put("kind", m.kind);
        out.put("url", m.url);
        out.put("embedUrl", m.embedUrl);
        out.put("label", m.label);
        return out;
    }

    private static Map<String, Object> tree(Settings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name);
        m.put("currency", s.currency);
        m.put("monthlyBudgetMinor", s.monthlyBudgetMinor);
        m.put("setupDone", s.setupDone);
        m.put("currentMusicId", s.currentMusicId);
        m.put("notifications", s.notifications);
        m.put("journalReminder", s.journalReminder);
        return m;
    }

    private static <T> List<Object> rows(List<T> list, Function<T, Map<String, Object>> row) {
        List<Object> out = new ArrayList<>(list.size());
        for (T item : list) out.add(row.apply(item));
        return out;
    }

    private static void putTables(Map<String, Object> doc, State s) {
        doc.put("settings", tree(s.settings));
        doc.put("tasks", rows(s.tasks, DocumentCodec::tree));
        doc.put("expenses", rows(s.expenses, DocumentCodec::tree));
        doc.put("habits", rows(s.habits, DocumentCodec::tree));
        doc.put("habitLogs", rows(s.habitLogs, DocumentCodec::tree));
        doc.put("journal", rows(s.journal, DocumentCodec::tree));
        doc.put("badges", new LinkedHashMap<>(s.badges));
        doc.put("music", rows(s.music, DocumentCodec::tree));
    }

    /** The document saved in app storage: a version, every table, and the id counters. */
    public static String write(State s) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("v", FORMAT);
        putTables(doc, s);
        Map<String, Object> seq = new LinkedHashMap<>();
        seq.put("tasks", s.seqTasks);
        seq.put("expenses", s.seqExpenses);
        seq.put("habits", s.seqHabits);
        seq.put("journal", s.seqJournal);
        seq.put("music", s.seqMusic);
        doc.put("seq", seq);
        return JsonText.compact(doc);
    }

    /** A backup file as an ordered tree: an export time, then the tables (already in display order). */
    public static Map<String, Object> exportTree(State ordered) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("exportedAt", nowIso());
        putTables(doc, ordered);
        return doc;
    }

    /** A backup file as a JSONObject. Its key order depends on the JSON library; use {@link #exportTree} for text. */
    public static JSONObject export(State ordered) {
        return new JSONObject(exportTree(ordered));
    }

    /** How many rows of each kind a state holds, for the "restored" summary. */
    static Map<String, Integer> counts(State s) {
        Map<String, Integer> m = new HashMap<>();
        m.put("tasks", s.tasks.size());
        m.put("expenses", s.expenses.size());
        m.put("habits", s.habits.size());
        m.put("entries", s.journal.size());
        m.put("music", s.music.size());
        return m;
    }
}

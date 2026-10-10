import app.dayhub.DataStore;
import app.dayhub.data.*;
import app.dayhub.data.Model.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class EngineTest {
    static int passed = 0;

    static void check(boolean ok, String what) {
        if (!ok) { System.out.println("FAIL " + what); System.exit(1); }
        passed++;
    }

    static void eq(Object a, Object b, String what) {
        check(java.util.Objects.equals(a, b), what + " (got " + a + ", wanted " + b + ")");
    }

    interface Thrower { void run() throws Exception; }

    static void rejects(Thrower t, String regex, String what) {
        try { t.run(); } catch (DataError e) {
            check(e.getMessage().matches("(?s).*" + regex + ".*"), what + " message: " + e.getMessage());
            check(e.status == 400 || e.status == 404 || e.status == 507, what + " status");
            return;
        } catch (Exception e) { check(false, what + " threw " + e); }
        check(false, what + " was accepted");
    }

    static JSONObject j(String s) throws Exception { return new JSONObject(s); }

    static final String TODAY = "2026-10-05";

    public static void main(String[] a) throws Exception {
        validation();
        music();
        tasks();
        expensesAndJournal();
        habits();
        settings();
        persistence();
        backup();
        damaged();
        rollback();
        recovery();
        progress();
        detection();
        System.out.println("ALL OK (" + passed + " checks)");
    }

    static void validation() throws Exception {
        eq(Validate.moneyToMinor("123.45", "amount"), 12345L, "money 123.45");
        eq(Validate.moneyToMinor("1,234.5", "amount"), 123450L, "money with comma");
        eq(Validate.moneyToMinor(0.1, "amount"), 10L, "money 0.1");
        eq(Validate.moneyToMinor(250.0, "amount"), 25000L, "money 250.0");
        eq(Validate.moneyToMinor("0", "amount", true), 0L, "money zero allowed");
        for (Object bad : new Object[] {"0", "-5", "1.234", "abc", "", null, 1e21, "99999999999", true}) {
            rejects(() -> Validate.moneyToMinor(bad, "amount"), "amount", "money " + bad);
        }
        eq(Validate.isoDate("2026-02-28", "d"), "2026-02-28", "iso date");
        rejects(() -> Validate.isoDate("2026-02-30", "d"), "real date", "feb 30");
        rejects(() -> Validate.isoDate("05/10/2026", "d"), "must look like", "bad format");
        rejects(() -> Validate.isoDate("2026-10-05\n", "d"), "must look like", "trailing newline");
        rejects(() -> Validate.isoDate("0050-01-01", "d"), "real date", "year under 100 (as the web app)");
        eq(Validate.isoDate("2024-02-29", "d"), "2024-02-29", "leap day");
        eq(Validate.monthRange("2026-12")[1], "2027-01-01", "month range december");
        eq(Validate.monthRange("2026-01")[1], "2026-02-01", "month range january");
        eq(Validate.str("  a\tb\u0000c  ", "t", 20), "a b c", "single-line control chars become spaces");
        eq(Validate.optMultiline("  First\r\n\r\nThird\tline \u0001x ", "e", 100), "First\n\nThird\tline  x", "multiline keeps breaks");
        eq(Validate.optStr("   ", "n", 10), null, "blank optional is null");
        rejects(() -> Validate.str("", "title", 10), "title is required", "required");
        rejects(() -> Validate.str(5, "title", 10), "title must be text", "non-text");
        rejects(() -> Validate.str("abcdef", "title", 5), "title is too long \\(max 5 characters\\)", "too long");
        eq(Validate.str("  x ﻿", "t", 5), "x", "js-style trim");
        eq(Validate.currency("inr"), "INR", "currency upper");
        rejects(() -> Validate.currency("ZZZ9"), "currency", "bad currency");
        rejects(() -> Validate.currency("US"), "currency", "short currency");
        eq(Validate.hhmm("07:30"), "07:30", "hhmm");
        eq(Validate.hhmm(""), null, "hhmm empty");
        rejects(() -> Validate.hhmm("24:00"), "07:30", "hhmm 24");
        eq(Validate.weekdays(new JSONArray("[3,1,1,6]")), java.util.Arrays.asList(1, 3, 6), "weekdays sorted unique");
        rejects(() -> Validate.weekdays(new JSONArray("[]")), "at least one day", "empty weekdays");
        rejects(() -> Validate.weekdays(new JSONArray("[7]")), "day must be a whole number from 0 to 6", "weekday 7");
        eq(Validate.tags(new JSONArray("[\"#Work\",\" work \",\"\",\"Family\"]")), java.util.Arrays.asList("work", "family"), "tags");
        rejects(() -> Validate.tags(new JSONArray("[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]")), "at most 8", "nine tags");
        eq(Validate.intIn("42", "n", 0, 100, null), 42, "int from text");
        rejects(() -> Validate.intIn(1.5, "n", 0, 100, null), "whole number", "int fraction");
        rejects(() -> Validate.intIn(true, "n", 0, 100, null), "whole number", "int bool");
        eq(Validate.intIn(null, "n", 0, 100, 7), 7, "int fallback");
        eq(Validate.id("12"), 12, "id text");
        rejects(() -> Validate.id(0), "Invalid id", "id zero");
        rejects(() -> Validate.id("x"), "Invalid id", "id junk");
        eq(Validate.wordCount("  one two\nthree  "), 3, "word count");
        eq(Validate.wordCount("   "), 0, "word count blank");
        eq(Validate.todayOf("nope").length(), 10, "todayOf falls back");
        eq(Validate.addDays("2026-03-01", -1), "2026-02-28", "add days");
    }

    static void music() throws Exception {
        MusicLinkParser.Parsed v = MusicLinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        eq(v.provider, "youtube", "provider");
        eq(v.embedUrl, "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ", "embed url");
        eq(MusicLinkParser.parse("https://youtu.be/dQw4w9WgXcQ?t=3").embedUrl, v.embedUrl, "youtu.be");
        eq(MusicLinkParser.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ").provider, "youtube", "music host");
        MusicLinkParser.Parsed p = MusicLinkParser.parse("https://www.youtube.com/playlist?list=PLabcdefghij1234567");
        eq(p.kind, "playlist", "playlist kind");
        eq(p.embedUrl, "https://www.youtube-nocookie.com/embed/videoseries?list=PLabcdefghij1234567", "playlist embed");
        check(MusicLinkParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij1234567")
                .embedUrl.endsWith("embed/dQw4w9WgXcQ?list=PLabcdefghij1234567"), "video + playlist");
        eq(MusicLinkParser.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ").kind, "video", "shorts");
        for (String bad : new String[] {"", "hello", "javascript:alert(1)", "https://evil.example/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/watch?v=short", "https://youtube.com.evil.example/watch?v=dQw4w9WgXcQ",
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "spotify:track:37i9dQZF1DXcBWIGoYBM5M",
                "ftp://youtu.be/dQw4w9WgXcQ"}) {
            rejects(() -> MusicLinkParser.parse(bad), ".", "music " + bad);
        }
    }

    static void tasks() throws Exception {
        DayHubData d = DayHubData.inMemory();
        Task late = d.createTask(j("{\"title\":\"  Send invoice \",\"dueOn\":\"2026-10-01\",\"priority\":1}"));
        eq(late.title, "Send invoice", "task title trimmed");
        eq(late.id, 1, "first id");
        Task none = d.createTask(j("{\"title\":\"one\\ntwo\"}"));
        eq(none.title, "one two", "task title is one line");
        eq(none.priority, 0, "default priority");
        Task soon = d.createTask(j("{\"title\":\"soon\",\"dueOn\":\"2026-10-09\"}"));
        List<Task> open = d.listTasks("open");
        eq(open.get(0).id, late.id, "earliest due first");
        eq(open.get(2).id, none.id, "undated last");
        rejects(() -> d.createTask(j("{\"title\":\"\"}")), "title is required", "empty title");
        rejects(() -> d.createTask(j("{\"title\":\"x\",\"priority\":2}")), "priority must be one of: 0, 1", "priority 2");
        rejects(() -> d.createTask(j("{\"title\":\"x\",\"dueOn\":\"soon\"}")), "due date must look like", "bad due");

        Task done = d.updateTask(soon.id, j("{\"done\":true,\"today\":\"" + TODAY + "\",\"time\":\"09:15\"}"));
        check(done.done, "marked done");
        eq(done.doneOn, TODAY, "done on");
        eq(done.doneTime, "09:15", "done time");
        eq(d.listTasks("done").size(), 1, "done list");
        eq(d.listTasks("all").size(), 3, "all list");
        eq(d.dashboardTasks(TODAY).size(), 3, "dashboard: overdue + undated + done today");
        eq(d.doneTasksOn(TODAY).get(0).id, soon.id, "done on a day");
        Task undone = d.updateTask(soon.id, j("{\"done\":false}"));
        check(!undone.done && undone.doneOn == null && undone.doneTime == null, "undone clears done fields");
        rejects(() -> d.updateTask(soon.id, j("{\"done\":\"yes\"}")), "done must be true or false", "done text");
        rejects(() -> d.updateTask(99, j("{\"title\":\"x\"}")), "Task not found", "missing task");
        rejects(() -> d.listTasks("weird"), "status must be one of", "bad status");

        // Rows are copies.
        Task copy = d.getTask(late.id);
        copy.title = "changed outside";
        eq(d.getTask(late.id).title, "Send invoice", "returned rows are copies");

        d.deleteTask(none.id);
        rejects(() -> d.deleteTask(none.id), "Task not found", "delete twice");
        eq(d.createTask(j("{\"title\":\"after\"}")).id, 4, "deleted id is not reused");
    }

    static void expensesAndJournal() throws Exception {
        DayHubData d = DayHubData.inMemory();
        Entry e = d.createEntry(j("{\"text\":\"Good day.\\nSpent 250 on lunch\",\"today\":\"" + TODAY + "\",\"time\":\"13:10\",\"mood\":4,\"tags\":[\"Work\"]}"));
        eq(e.text, "Good day.\nSpent 250 on lunch", "journal keeps line break");
        eq(e.wordCount, 6, "word count");
        eq(e.day, TODAY, "defaults to today");
        eq(e.tags.get(0), "work", "tag lowercased");
        Expense lunch = d.createExpense(25000, "Food", "lunch", TODAY, e.id, "13:10");
        Expense bills = d.addExpense(j("{\"amount\":\"99.50\",\"category\":\"Bills\",\"spentOn\":\"" + TODAY + "\"}"));
        eq(bills.amountMinor, 9950L, "amount in minor units");
        Expense dflt = d.addExpense(j("{\"amount\":10,\"today\":\"" + TODAY + "\"}"));
        eq(dflt.category, "Other", "default category");
        eq(dflt.spentOn, TODAY, "default date from today");
        rejects(() -> d.addExpense(j("{\"amount\":5,\"category\":\"Pets\"}")), "category must be one of", "bad category");
        rejects(() -> d.addExpense(j("{\"amount\":-5}")), "amount", "negative");

        DayHubData.Summary sum = d.summarizeExpenses("2026-10-01", "2026-11-01");
        eq(sum.totalMinor, 25000L + 9950L + 1000L, "month total");
        eq(sum.count, 3, "count");
        eq(sum.byCategory.get(0).category, "Food", "biggest category first");
        eq(d.listExpenses("2026-10-01", "2026-11-01").size(), 3, "range list");
        eq(d.listExpenses("2026-11-01", "2026-12-01").size(), 0, "other month empty");
        eq(d.listEntryExpenses(e.id).size(), 1, "entry spending");

        // Editing the entry's time moves its spending; deleting it keeps the spending, unlinked.
        d.updateEntry(e.id, j("{\"day\":\"2026-10-04\",\"time\":\"08:00\",\"today\":\"" + TODAY + "\"}"));
        eq(d.listEntryExpenses(e.id).get(0).spentOn, "2026-10-04", "spending follows entry day");
        eq(d.listEntryExpenses(e.id).get(0).time, "08:00", "spending follows entry time");
        Entry edited = d.updateEntry(e.id, j("{\"text\":\"a\\nb\",\"today\":\"" + TODAY + "\"}"));
        eq(edited.wordCount, 2, "word count updates");
        rejects(() -> d.updateEntry(e.id, j("{\"text\":\"\",\"mood\":null}")), "Write something or pick a mood", "empty entry");
        Entry moodOnly = d.createEntry(j("{\"mood\":3,\"today\":\"" + TODAY + "\"}"));
        eq(moodOnly.text, "", "mood-only entry");
        rejects(() -> d.createEntry(j("{\"text\":\"\",\"today\":\"" + TODAY + "\"}")), "Write something or pick a mood", "nothing to save");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"day\":\"2026-10-06\",\"today\":\"" + TODAY + "\"}")), "hasn't happened yet", "future day");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"mood\":6}")), "mood must be a whole number from 1 to 5", "mood 6");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"promptId\":\"zzz\"}")), "prompt must be one of", "bad prompt");
        rejects(() -> d.createEntry(j("{\"text\":\"" + "x".repeat(10001) + "\"}")), "entry is too long", "10001 chars");

        d.deleteEntry(e.id);
        eq(d.listExpenses(null, null).size(), 3, "spending stays after entry delete");
        check(d.listExpenses(null, null).stream().allMatch(x -> x.entryId == null), "unlinked after entry delete");
        rejects(() -> d.deleteEntry(e.id), "Entry not found", "delete entry twice");
        d.deleteExpense(lunch.id);
        rejects(() -> d.deleteExpense(lunch.id), "Expense not found", "delete expense twice");
        List<Entry> list = d.listEntries(null, null);
        eq(list.size(), 1, "one entry left");
    }

    static void habits() throws Exception {
        DayHubData d = DayHubData.inMemory();
        Habit water = d.createHabit(j("{\"title\":\"Drink water\",\"icon\":\"\\ud83d\\udca7\",\"kind\":\"goal\",\"unit\":\"glasses\",\"target\":8,\"step\":20,\"points\":10,\"today\":\"" + TODAY + "\"}"));
        eq(water.step, 8, "step capped at target");
        eq(water.createdOn, TODAY, "created on");
        eq(water.days.size(), 7, "every day by default");
        Habit workout = d.createHabit(j("{\"title\":\"Workout\",\"target\":5,\"unit\":\"x\",\"days\":[1,3,5],\"remindAt\":\"07:00\"}"));
        eq(workout.kind, "check", "kind defaults to check");
        eq(workout.target, 1, "check target is 1");
        eq(workout.unit, "", "check has no unit");
        eq(workout.icon, "📌", "default icon");
        rejects(() -> d.createHabit(j("{\"title\":\"x\",\"kind\":\"weird\"}")), "type must be one of: check, goal, limit", "bad kind");
        rejects(() -> d.createHabit(j("{\"title\":\"x\",\"days\":[]}")), "at least one day", "no days");
        rejects(() -> d.createHabit(j("{\"title\":\"x\",\"points\":101}")), "points", "too many points");

        Habit patched = d.updateHabit(workout.id, j("{\"title\":\"Gym\",\"kind\":\"limit\",\"target\":3,\"unit\":\"hrs\"}"));
        eq(patched.title, "Gym", "patch title");
        eq(patched.target, 3, "patch to limit keeps target");
        eq(patched.days.size(), 3, "patch keeps days");
        eq(patched.remindAt, "07:00", "patch keeps reminder");
        rejects(() -> d.updateHabit(99, j("{}")), "Habit not found", "patch missing");

        d.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":3,\"today\":\"" + TODAY + "\",\"time\":\"09:00\"}"));
        d.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":5,\"today\":\"" + TODAY + "\"}"));
        eq(d.listLogs(null, null).size(), 1, "one log per habit and day");
        eq(d.listLogs(null, null).get(0).value, 5, "last value wins");
        d.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":0,\"today\":\"" + TODAY + "\"}"));
        eq(d.listLogs(null, null).size(), 0, "zero clears the log");
        d.logHabit(patched.id, j("{\"day\":\"" + TODAY + "\",\"value\":2,\"today\":\"" + TODAY + "\"}"));
        rejects(() -> d.logHabit(water.id, j("{\"day\":\"2026-10-06\",\"value\":1,\"today\":\"" + TODAY + "\"}")), "hasn't happened yet", "future log");
        rejects(() -> d.logHabit(99, j("{\"day\":\"" + TODAY + "\",\"value\":1}")), "Habit not found", "log missing");

        d.archiveHabit(water.id);
        eq(d.listHabits(false).size(), 1, "archived hidden");
        eq(d.listHabits(true).size(), 2, "archived kept");
        rejects(() -> d.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":1,\"today\":\"" + TODAY + "\"}")), "Habit not found", "log archived");

        eq(d.unlockBadges(java.util.Arrays.asList("first", "streak3"), TODAY).size(), 2, "new badges");
        eq(d.unlockBadges(java.util.Arrays.asList("first", "streak7"), "2026-10-06").size(), 1, "only unseen badges");
        eq(d.listBadges().get("first"), TODAY, "badge keeps first day");
    }

    static void settings() throws Exception {
        DayHubData d = DayHubData.inMemory();
        Settings s = d.getSettings();
        eq(s.currency, "INR", "default currency");
        eq(s.monthlyBudgetMinor, 0L, "default budget");
        s = d.updateSettings(j("{\"name\":\"  Asha \",\"currency\":\"usd\",\"monthlyBudget\":\"30,000\",\"notifications\":true,\"journalReminder\":\"21:30\"}"));
        eq(s.name, "Asha", "name");
        eq(s.currency, "USD", "currency");
        eq(s.monthlyBudgetMinor, 3000000L, "budget minor");
        check(s.notifications, "notifications");
        eq(s.journalReminder, "21:30", "journal reminder");
        eq(d.updateSettings(j("{\"monthlyBudget\":0}")).monthlyBudgetMinor, 0L, "budget 0 clears");
        eq(d.updateSettings(j("{\"monthlyBudget\":\"\"}")).monthlyBudgetMinor, 0L, "budget blank clears");
        eq(d.updateSettings(j("{\"journalReminder\":\"\"}")).journalReminder, "", "reminder cleared");
        rejects(() -> d.updateSettings(j("{\"notifications\":\"yes\"}")), "notifications must be true or false", "notifications text");
        rejects(() -> d.updateSettings(j("{\"currency\":\"ZZZ9\"}")), "currency", "bad currency");
        eq(d.getSettings().currency, "USD", "failed update changes nothing");

        // Music: a repeated link is renamed, not duplicated; deleting the current link clears it.
        Music m = d.addMusic("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null, true);
        eq(m.label, "YouTube video", "default label");
        eq(d.getSettings().currentMusicId, m.id, "made current");
        Music again = d.addMusic("https://youtu.be/dQw4w9WgXcQ", "Renamed", false);
        eq(again.id, m.id, "same link, same row");
        eq(d.listMusic().size(), 1, "no duplicate");
        eq(d.listMusic().get(0).label, "Renamed", "renamed");
        Music second = d.addMusic("https://www.youtube.com/playlist?list=PLabcdefghij1234567", "Mix", false);
        eq(d.getSettings().currentMusicId, m.id, "makeCurrent=false keeps current");
        eq(d.listMusic().get(0).id, second.id, "newest first");
        d.setCurrentMusic(second.id);
        d.deleteMusic(second.id);
        eq(d.getSettings().currentMusicId, null, "deleting current clears it");
        rejects(() -> d.setCurrentMusic(77), "Link not found", "current missing");
        rejects(() -> d.deleteMusic(77), "Link not found", "delete missing link");
        rejects(() -> d.addMusic("https://example.com", null, true), "Only YouTube", "other site");
        rejects(() -> d.addMusic("https://youtu.be/dQw4w9WgXcQ", "x".repeat(61), true), "label is too long", "long label");
    }

    static File tmp() throws Exception { return Files.createTempDirectory("dh").toFile(); }

    static void persistence() throws Exception {
        File dir = tmp();
        DataStore store = new DataStore(new File(dir, "dayhub.json"));
        DayHubData first = DayHubData.open(store);
        check(!store.exists(), "nothing saved on first run");
        first.createTask(j("{\"title\":\"kept\"}"));
        first.commit();
        check(store.exists(), "saved on commit");
        check(!first.hasUnsavedChanges(), "clean after commit");
        Task gone = first.createTask(j("{\"title\":\"temporary\"}"));
        first.deleteTask(gone.id);
        first.addMusic("https://youtu.be/dQw4w9WgXcQ", null, true);
        first.createEntry(j("{\"text\":\"Line one\\nLine two\",\"today\":\"" + TODAY + "\"}"));
        first.commit();

        DayHubData second = DayHubData.open(store); // a fresh app start
        eq(second.listTasks("open").get(0).title, "kept", "task survives restart");
        eq(second.listEntries(null, null).get(0).text, "Line one\nLine two", "line break survives restart");
        eq(second.getSettings().currentMusicId, 1, "current music survives restart");
        eq(second.createTask(j("{\"title\":\"after reload\"}")).id, gone.id + 1, "ids keep counting after restart");

        // No commit, no write.
        long before = store.read().length();
        second.createTask(j("{\"title\":\"not committed\"}"));
        eq((long) store.read().length(), before, "uncommitted changes are not saved");
    }

    static DayHubData filled() throws Exception {
        DayHubData d = DayHubData.inMemory();
        d.updateSettings(j("{\"name\":\"Asha\",\"currency\":\"inr\",\"monthlyBudget\":30000}"));
        d.setSetupDone(true);
        d.createTask(j("{\"title\":\"Send invoice\",\"dueOn\":\"" + TODAY + "\",\"priority\":1}"));
        Entry e = d.createEntry(j("{\"text\":\"Good day.\\nSpent 250\",\"today\":\"" + TODAY + "\",\"time\":\"13:10\",\"mood\":4,\"tags\":[\"work\"]}"));
        d.createExpense(25000, "Food", "lunch", TODAY, e.id, "13:10");
        d.addExpense(j("{\"amount\":\"99.50\",\"category\":\"Bills\",\"spentOn\":\"" + TODAY + "\"}"));
        Habit h = d.createHabit(j("{\"title\":\"Drink water\",\"kind\":\"goal\",\"target\":8,\"unit\":\"glasses\",\"today\":\"" + TODAY + "\"}"));
        d.logHabit(h.id, j("{\"day\":\"" + TODAY + "\",\"value\":3,\"today\":\"" + TODAY + "\",\"time\":\"09:00\"}"));
        d.addMusic("https://www.youtube.com/watch?v=dQw4w9WgXcQ", null, true);
        d.unlockBadges(java.util.Arrays.asList("first"), TODAY);
        return d;
    }

    static JSONObject norm(JSONObject o) throws Exception {
        JSONObject c = new JSONObject(o.toString());
        c.remove("exportedAt");
        return c;
    }

    static void backup() throws Exception {
        DayHubData a = filled();
        JSONObject backup = a.exportAll();
        DayHubData b = DayHubData.inMemory();
        Map<String, Integer> restored = b.importAll(new JSONObject(backup.toString()));
        eq(restored.get("entries"), 1, "restored entries");
        eq(restored.get("tasks"), 1, "restored tasks");
        eq(norm(b.exportAll()).toString(), norm(backup).toString(), "backup then restore gives the same data");
        eq(b.createTask(j("{\"title\":\"next\"}")).id, 2, "ids continue after import");

        // The text form works too, and a file that is not JSON changes nothing.
        DayHubData c = DayHubData.inMemory();
        c.importAll(backup.toString());
        eq(c.listEntries(null, null).size(), 1, "text import");
        rejects(() -> c.importAll("{not json"), "not valid JSON", "text not json");

        // A bad file changes nothing; the message names the row.
        DayHubData app = DayHubData.inMemory();
        app.createTask(j("{\"title\":\"mine\"}"));
        String good = a.exportAll().toString();
        String[][] cases = {
            {"{}", "not a Day Hub backup"},
            {"{\"hello\":\"world\"}", "not a Day Hub backup"},
            {"[1,2]", "not a Day Hub backup"},
            {"{\"tasks\":\"nope\"}", "\"tasks\" should be a list"},
            {"{\"tasks\":[{\"id\":1,\"title\":\"a\"},{\"id\":1,\"title\":\"b\"}]}", "id 1 appears twice"},
            {"{\"expenses\":[{\"id\":1,\"amountMinor\":-5,\"category\":\"Food\",\"spentOn\":\"" + TODAY + "\"}]}", "Expense #1"},
            {"{\"journal\":[{\"id\":1,\"day\":\"yesterday\",\"text\":\"\"}]}", "Journal entry #1"},
            {"{\"habits\":[{\"id\":1,\"title\":\"x\",\"kind\":\"weird\",\"createdOn\":\"" + TODAY + "\"}]}", "Habit #1"},
            {"{\"journal\":[{\"id\":1,\"day\":\"" + TODAY + "\",\"text\":\"" + "x".repeat(10001) + "\"}]}", "too long"},
            {"{\"settings\":{\"currency\":\"ZZZ9\"}}", "currency"},
            {"{\"tasks\":[5]}", "Task #1: Expected a JSON object"},
            {"{\"badges\":{\"x\":\"nope\"}}", "badge \"x\" date"},
        };
        for (String[] c2 : cases) {
            rejects(() -> app.importAll(c2[0]), c2[1], "import " + c2[0].substring(0, Math.min(40, c2[0].length())));
        }
        eq(app.listTasks("open").get(0).title, "mine", "bad imports changed nothing");
        app.importAll("{\"settings\":{\"name\":\"New\",\"setupDone\":true},\"tasks\":[{\"id\":7,\"title\":\"imported\",\"priority\":1}]}");
        eq(app.listTasks("open").get(0).title, "imported", "restore replaces what was there");
        eq(app.createTask(j("{\"title\":\"next\"}")).id, 8, "ids continue after the highest imported id");

        // Restore tidies broken links instead of keeping them.
        DayHubData t = DayHubData.inMemory();
        t.importAll("{\"settings\":{\"currentMusicId\":99},"
                + "\"habits\":[{\"id\":1,\"title\":\"Water\",\"kind\":\"goal\",\"target\":8,\"createdOn\":\"" + TODAY + "\"}],"
                + "\"habitLogs\":[{\"habitId\":1,\"day\":\"" + TODAY + "\",\"value\":2},{\"habitId\":1,\"day\":\"" + TODAY + "\",\"value\":5},{\"habitId\":42,\"day\":\"" + TODAY + "\",\"value\":1}],"
                + "\"expenses\":[{\"id\":1,\"amountMinor\":500,\"category\":\"Food\",\"spentOn\":\"" + TODAY + "\",\"entryId\":77}]}");
        JSONObject out = t.exportAll();
        eq(out.getJSONArray("habitLogs").length(), 1, "unknown habit log dropped, duplicate day collapsed");
        eq(out.getJSONArray("habitLogs").getJSONObject(0).getInt("value"), 5, "duplicate day: last wins");
        check(out.getJSONArray("expenses").getJSONObject(0).isNull("entryId"), "dangling entry link cleared");
        check(out.getJSONObject("settings").isNull("currentMusicId"), "dangling current music cleared");

        // Web-app compatibility: the export has the same keys and field names a web backup has.
        for (String k : new String[] {"exportedAt", "settings", "tasks", "expenses", "habits", "habitLogs", "journal", "badges", "music"}) {
            check(backup.has(k), "export has " + k);
        }
        JSONObject task = backup.getJSONArray("tasks").getJSONObject(0);
        for (String k : new String[] {"id", "title", "dueOn", "priority", "done", "doneOn", "doneTime", "createdAt"}) {
            check(task.has(k), "task has " + k);
        }
        JSONObject exp = backup.getJSONArray("expenses").getJSONObject(0);
        for (String k : new String[] {"id", "amountMinor", "category", "note", "spentOn", "entryId", "time"}) {
            check(exp.has(k), "expense has " + k);
        }
        // Order: newest expense first; tasks by due date.
        eq(backup.getJSONArray("expenses").getJSONObject(0).getInt("id"), 2, "export expenses newest first");

        // A document in the web app's own stored format (with version and counters) opens as is.
        String webDoc = "{\"v\":1,\"settings\":{\"name\":\"Web\"},\"tasks\":[{\"id\":3,\"title\":\"from web\",\"createdAt\":\"2026-10-05T10:00:00.000Z\"}],"
                + "\"seq\":{\"tasks\":9,\"expenses\":0,\"habits\":0,\"journal\":0,\"music\":0}}";
        DayHubData w = DayHubData.inMemory();
        w.importAll(webDoc);
        eq(w.createTask(j("{\"title\":\"x\"}")).id, 10, "web counters honoured");
    }

    static void damaged() throws Exception {
        String[][] cases = {
            {"{not json", "cannot be read"},
            {"{\"hello\": 1}", "does not look right"},
            {"[1, 2, 3]", "does not look right"},
            {"{\"v\": 99, \"tasks\": []}", "newer version"},
            {"{\"v\": 1, \"tasks\": [{\"id\": 1, \"title\": \"\"}]}", "Task #1"},
            {"{\"v\":1,\"tasks\":[]} trailing", "cannot be read"},
            {"", "cannot be read"},
        };
        for (String[] c : cases) {
            File dir = tmp();
            File f = new File(dir, "dayhub.json");
            Files.writeString(f.toPath(), c[0]);
            DataStore store = new DataStore(f);
            try {
                DayHubData.open(store);
                check(false, "damaged data was accepted: " + c[0]);
            } catch (DamagedDataException e) {
                check(e.getMessage().contains(c[1]), "damaged message for " + c[0] + ": " + e.getMessage());
                eq(e.raw, c[0], "raw text kept for " + c[0]);
            }
            eq(Files.readString(f.toPath()), c[0], "damaged file left untouched: " + c[0]);
        }
    }

    static void rollback() throws Exception {
        // A save that fails is undone: memory matches the file, ids too, and the error says so.
        File dir = tmp();
        File f = new File(dir, "dayhub.json");
        DataStore store = new DataStore(f);
        DayHubData d = DayHubData.open(store);
        d.createTask(j("{\"title\":\"saved\"}"));
        d.commit();
        String onDisk = store.read();

        File blocker = new File(dir, "dayhub.json.tmp"); // a non-empty folder here makes every save fail
        check(blocker.mkdir() && new File(blocker, "x").createNewFile(), "setup blocked saves");
        d.createTask(j("{\"title\":\"does not fit\"}"));
        d.updateSettings(j("{\"name\":\"Nope\"}"));
        try {
            d.commit();
            check(false, "commit should have failed");
        } catch (DataError e) {
            eq(e.status, 507, "failed save status");
            check(e.getMessage().contains("undone"), "failed save message: " + e.getMessage());
            check(e.getCause() != null, "failed save keeps its cause");
        }
        check(!d.hasUnsavedChanges(), "nothing pending after rollback");
        eq(d.listTasks("all").size(), 1, "failed save undone: tasks");
        eq(d.getSettings().name, "", "failed save undone: settings");
        eq(store.read(), onDisk, "failed save left the file alone");
        eq(d.createTask(j("{\"title\":\"next\"}")).id, 2, "ids undone too");

        blocker.listFiles()[0].delete();
        blocker.delete();
        d.commit();
        eq(DayHubData.open(store).listTasks("all").size(), 2, "saves work again once there is room");

        // A half-finished operation can be undone by hand.
        DayHubData m = DayHubData.inMemory();
        m.createTask(j("{\"title\":\"kept\"}"));
        m.commit();
        m.createTask(j("{\"title\":\"half done\"}"));
        m.setSetupDone(true);
        m.rollback();
        eq(m.listTasks("all").size(), 1, "manual rollback: tasks");
        check(!m.getSettings().setupDone, "manual rollback: settings");
        eq(m.createTask(j("{\"title\":\"x\"}")).id, 2, "manual rollback: ids");
        DayHubData fresh = DayHubData.inMemory();
        fresh.createTask(j("{\"title\":\"never saved\"}"));
        fresh.rollback();
        eq(fresh.listTasks("all").size(), 0, "rollback before any save empties");
    }

    static void recovery() throws Exception {
        String goodBackup = filled().exportAll().toString();

        // Start fresh: the unreadable copy is kept aside, byte for byte, and an empty app opens.
        File dir = tmp();
        File f = new File(dir, "dayhub.json");
        Files.writeString(f.toPath(), "{broken");
        DataStore store = new DataStore(f);
        DamagedDataRecovery.Result r = DamagedDataRecovery.startFresh(store);
        check(!f.exists(), "damaged file moved out of the way");
        eq(Files.readString(r.keptCopy.toPath()), "{broken", "damaged copy kept intact");
        check(r.keptCopy.getName().startsWith("dayhub-damaged-") && r.keptCopy.getName().endsWith(".json"), "kept copy name: " + r.keptCopy.getName());
        eq(r.data.listTasks("all").size(), 0, "fresh start is empty");
        r.data.createTask(j("{\"title\":\"new\"}"));
        r.data.commit();
        eq(DayHubData.open(store).listTasks("all").get(0).title, "new", "fresh data saves and reopens");
        check(r.keptCopy.exists(), "kept copy still there after saving");

        // Starting fresh with nothing saved keeps nothing.
        File dir2 = tmp();
        DamagedDataRecovery.Result none = DamagedDataRecovery.startFresh(new DataStore(new File(dir2, "dayhub.json")));
        eq(none.keptCopy, null, "nothing to keep");

        // Restore from a backup: checked first, then the damaged copy is kept and the backup saved.
        File dir3 = tmp();
        File f3 = new File(dir3, "dayhub.json");
        Files.writeString(f3.toPath(), "garbage");
        DataStore s3 = new DataStore(f3);
        eq(DamagedDataRecovery.inspect(goodBackup), "1 journal entries, 1 tasks, 2 expenses and 1 habits", "summary wording");
        DamagedDataRecovery.Result restored = DamagedDataRecovery.restore(s3, goodBackup);
        eq(restored.data.listEntries(null, null).size(), 1, "restored entries");
        eq(Files.readString(restored.keptCopy.toPath()), "garbage", "damaged copy kept after restore");
        eq(DayHubData.open(s3).listTasks("all").get(0).title, "Send invoice", "restore was saved");

        // A bad backup changes nothing at all.
        File dir4 = tmp();
        File f4 = new File(dir4, "dayhub.json");
        Files.writeString(f4.toPath(), "garbage");
        DataStore s4 = new DataStore(f4);
        for (String bad : new String[] {"{not json", "{}", "{\"tasks\":[{\"id\":1,\"title\":\"\"}]}"}) {
            rejects(() -> DamagedDataRecovery.restore(s4, bad), ".", "bad backup " + bad);
            eq(Files.readString(f4.toPath()), "garbage", "bad backup left the damaged file in place: " + bad);
        }
        eq(dir4.list().length, 1, "no stray files after bad backups");

        // If the restored data cannot be saved, the damaged copy is put back.
        File dir5 = tmp();
        File f5 = new File(dir5, "dayhub.json");
        Files.writeString(f5.toPath(), "garbage");
        File blocker = new File(dir5, "dayhub.json.tmp");
        check(blocker.mkdir() && new File(blocker, "x").createNewFile(), "setup blocked restore");
        rejects(() -> DamagedDataRecovery.restore(new DataStore(f5), goodBackup), "could not save", "restore that cannot save");
        eq(Files.readString(f5.toPath()), "garbage", "damaged copy put back when restore cannot save");
    }

    static void progress() throws Exception {
        DayHubData d = DayHubData.inMemory();
        HabitProgress p = new HabitProgress(d);
        Habit water = d.createHabit(j("{\"title\":\"Water\",\"kind\":\"goal\",\"target\":8,\"points\":20,\"today\":\"" + TODAY + "\"}"));
        Habit gym = d.createHabit(j("{\"title\":\"Gym\",\"days\":[2],\"today\":\"" + TODAY + "\"}")); // Tuesday only; TODAY is a Monday

        eq(p.habitsForDay(TODAY, false).size(), 2, "both habits listed");
        eq(p.habitsForDay(TODAY, true).size(), 1, "only the scheduled one");
        check(!p.habitsForDay(TODAY, false).get(1).scheduled, "gym not scheduled on Monday");

        HabitProgress.LogResult r = p.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":4,\"today\":\"" + TODAY + "\"}"));
        eq(r.habit.value, 4, "logged value shown");
        check(!r.habit.done, "half way is not done");
        eq(r.habit.pointsToday, 10, "proportional points");
        eq(r.progress.stats.totalPoints, 10, "total points");
        eq(r.progress.newBadges.size(), 0, "no badge for half a goal");
        r = p.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":8,\"today\":\"" + TODAY + "\"}"));
        check(r.habit.done, "goal reached");
        eq(r.progress.stats.totalPoints, 20, "points for the full goal");
        eq(r.progress.stats.streak, 1, "streak starts");
        eq(r.progress.newBadges.size(), 1, "first badge");
        eq(r.progress.newBadges.get(0).id, "first-step", "first step badge");
        eq(p.logHabit(water.id, j("{\"day\":\"" + TODAY + "\",\"value\":8,\"today\":\"" + TODAY + "\"}")).progress.newBadges.size(), 0, "a badge is only announced once");

        HabitProgress.EntryResult e = p.createEntry(j("{\"text\":\"late thoughts\",\"time\":\"22:10\",\"today\":\"" + TODAY + "\"}"));
        eq(e.progress.stats.totalPoints, 30, "journal day points added");
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (Badges.Badge b : e.progress.newBadges) ids.add(b.id);
        eq(ids, java.util.Arrays.asList("first-entry", "night-owl"), "entry badges");
        eq(p.createEntry(j("{\"text\":\"second\",\"time\":\"06:00\",\"today\":\"" + TODAY + "\"}")).progress.stats.totalPoints, 30, "second entry earns no extra points");

        int unlocked = 0;
        for (Badges.Status s : p.badgeList()) if (s.unlockedOn != null) unlocked++;
        eq(unlocked, 4, "badges unlocked so far (first-step, first-entry, night-owl, early-bird)");
        eq(p.badgeList().size(), 13, "all badges listed");
        eq(p.badgeList().get(0).unlockedOn, TODAY, "unlock day recorded");

        // An archived habit still counts toward history but is hidden from the day's list.
        d.archiveHabit(gym.id);
        eq(p.habitsForDay(TODAY, false).size(), 1, "archived habit hidden");
        eq(p.statsFor(TODAY).totalPoints, 30, "archiving keeps points");
        check(p.oneHabit(99, TODAY) == null, "unknown habit");

        // Badges survive a save and reopen.
        File dir = tmp();
        DataStore store = new DataStore(new File(dir, "dayhub.json"));
        DayHubData saved = DayHubData.open(store);
        Habit h = saved.createHabit(j("{\"title\":\"x\",\"today\":\"" + TODAY + "\"}"));
        new HabitProgress(saved).logHabit(h.id, j("{\"day\":\"" + TODAY + "\",\"value\":1,\"today\":\"" + TODAY + "\"}"));
        saved.commit();
        eq(DayHubData.open(store).listBadges().get("first-step"), TODAY, "badge saved");
    }

    static void detection() throws Exception {
        DayHubData d = DayHubData.inMemory();

        // The suggestions the web app's own tests expect.
        java.util.List<ExpenseDetector.Suggestion> one = ExpenseDetector.detect("Spent ₹250 on lunch with Ravi.", null, null);
        eq(one.size(), 1, "one suggestion");
        eq(one.get(0).amountMinor, 25000L, "amount");
        eq(one.get(0).category, "Food", "category");
        eq(one.get(0).note, "lunch", "note");
        eq(one.get(0).daysAgo, 0, "same day");
        eq(ExpenseDetector.detect("I walked 8000 steps and bought 3 apples", null, null).size(), 0, "counts are not spending");
        eq(ExpenseDetector.detect("Got paid 50000 for the project.", null, null).size(), 0, "income is not spending");
        eq(ExpenseDetector.detect("", null, null).size(), 0, "empty text");
        eq(ExpenseDetector.detect("Spent 300 on dosa on Friday", "2026-10-05", null).get(0).daysAgo, 3, "named weekday");
        eq(ExpenseDetector.detect("Spent 3 lakhs on a car", null, null).get(0).amountMinor, 30_000_000L, "lakhs");
        eq(ExpenseDetector.detect("a. Spent 10 on x. Spent 11 on x. Spent 12 on x. Spent 13 on x. Spent 14 on x. Spent 15 on x.", null, null).size(), 5, "at most five");

        // The route: history from saved expenses decides categories.
        d.createExpense(70000, "Fun", "zorbing", TODAY, null, null);
        d.createExpense(70000, "Fun", "zorbing ride", TODAY, null, null);
        d.createExpense(5000, "Food", null, TODAY, null, null); // no note: not used for learning
        eq(d.recentExpensesWithNotes(500).size(), 2, "only expenses with notes");
        eq(ExpenseDetector.suggest(d, "paid 700 for zorbing", TODAY, null).get(0).category, "Fun", "learned from history");
        eq(ExpenseDetector.detect("paid 700 for zorbing", null, null).get(0).category, "Other", "without history");

        // Entries save confirmed spending with them.
        Entry e = d.createEntry(j("{\"text\":\"Lunch out\",\"time\":\"13:00\",\"today\":\"" + TODAY + "\",\"expenses\":["
                + "{\"amount\":250,\"category\":\"Food\",\"note\":\"lunch\"},"
                + "{\"amount\":\"99.50\",\"note\":\"phone\",\"daysAgo\":2}]}"));
        java.util.List<Expense> saved = d.listEntryExpenses(e.id);
        eq(saved.size(), 2, "two expenses saved with the entry");
        eq(saved.get(0).spentOn, TODAY, "same-day expense date");
        eq(saved.get(0).time, "13:00", "same-day expense takes the entry time");
        eq(saved.get(1).spentOn, "2026-10-03", "daysAgo moves the date");
        eq(saved.get(1).time, null, "earlier expense has no time");
        eq(saved.get(1).category, "Other", "default category");

        // Editing: spending already saved with the entry is not suggested again.
        java.util.List<ExpenseDetector.Suggestion> again = ExpenseDetector.suggest(d, "Lunch out, spent 250 on lunch and paid 80 for tea", TODAY, e.id);
        eq(again.size(), 1, "saved amount left out");
        eq(again.get(0).amountMinor, 8000L, "new amount kept");
        eq(ExpenseDetector.suggest(d, "spent 250 on lunch", TODAY, null).size(), 1, "new entry still sees it");
        eq(ExpenseDetector.suggest(d, "spent 250 on lunch", "", null).size(), 1, "blank day is fine");
        rejects(() -> ExpenseDetector.suggest(d, "x", "tomorrowish", null), "day must look like", "bad day");

        d.updateEntry(e.id, j("{\"expenses\":[{\"amount\":80,\"category\":\"Food\",\"note\":\"tea\"}],\"today\":\"" + TODAY + "\"}"));
        eq(d.listEntryExpenses(e.id).size(), 3, "edit adds spending");
        d.updateEntry(e.id, j("{\"time\":\"09:30\",\"today\":\"" + TODAY + "\"}"));
        eq(d.listEntryExpenses(e.id).get(0).time, "09:30", "spending follows the entry's time");

        // Validation and atomicity: a bad expense stops the whole entry.
        int before = d.listEntries(null, null).size();
        int spendBefore = d.listExpenses(null, null).size();
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"today\":\"" + TODAY + "\",\"expenses\":[{\"amount\":5},{\"amount\":-1}]}")), "amount", "bad expense");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"today\":\"" + TODAY + "\",\"expenses\":[{\"amount\":5,\"category\":\"Pets\"}]}")), "category must be one of", "bad expense category");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"today\":\"" + TODAY + "\",\"expenses\":[{\"amount\":5,\"daysAgo\":31}]}")), "daysAgo must be a whole number from 0 to 30", "daysAgo 31");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"today\":\"" + TODAY + "\",\"expenses\":[{\"amount\":1},{\"amount\":1},{\"amount\":1},{\"amount\":1},{\"amount\":1},{\"amount\":1}]}")), "At most 5 expenses", "six expenses");
        rejects(() -> d.createEntry(j("{\"text\":\"x\",\"today\":\"" + TODAY + "\",\"expenses\":[7]}")), "Expected a JSON object", "expense not an object");
        eq(d.listEntries(null, null).size(), before, "failed entries leave no entry");
        eq(d.listExpenses(null, null).size(), spendBefore, "failed entries leave no spending");
    }
}

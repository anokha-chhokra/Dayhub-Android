package app.dayhub.data;

import app.dayhub.data.Model.Entry;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Model.Habit;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 11: the files Day Hub can hand out, ported from the web app: the full JSON backup, the
 * expenses CSV and the Markdown journal. Each is built as text here; saving it somewhere is up to
 * the caller. Restoring a backup is {@link DayHubData#importAll(String)}.
 */
public final class DataExport {
    private DataExport() {}

    /** A file ready to be saved. */
    public static final class Export {
        public final String name;
        public final String mimeType;
        public final String text;

        Export(String name, String mimeType, String text) {
            this.name = name;
            this.mimeType = mimeType;
            this.text = text;
        }
    }

    /** Everything, in the web app's backup format, pretty-printed. */
    public static Export backup(DayHubData data) {
        String text = JsonText.pretty(DocumentCodec.exportTree(data.orderedState())) + "\n";
        return new Export("day-hub-backup.json", "application/json", text);
    }

    // ---------- CSV ----------

    /** One CSV cell. Text that starts like a spreadsheet formula gets a leading ' so it stays text. */
    static String csvCell(String value) {
        String s = value == null ? "" : value;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.indexOf('"') >= 0 || s.indexOf(',') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** 12345 becomes "123.45". */
    static String minorToDecimal(long minor) {
        long abs = Math.abs(minor);
        return (minor < 0 ? "-" : "") + (abs / 100) + "." + (abs % 100 < 10 ? "0" : "") + (abs % 100);
    }

    /**
     * Expenses as a spreadsheet file, newest first.
     *
     * @param month "YYYY-MM" for one month, or null for every expense
     */
    public static Export expensesCsv(DayHubData data, String month) {
        List<Expense> rows;
        String name;
        if (month != null) {
            String ym = Validate.month(month, "month");
            String[] range = Validate.monthRange(ym);
            rows = data.listExpenses(range[0], range[1]);
            name = "expenses-" + ym + ".csv";
        } else {
            rows = data.listExpenses(null, null);
            name = "expenses-all.csv";
        }
        StringBuilder sb = new StringBuilder("Date,Amount,Category,Note\n");
        for (Expense e : rows) {
            sb.append(csvCell(e.spentOn)).append(',')
                    .append(csvCell(minorToDecimal(e.amountMinor))).append(',')
                    .append(csvCell(e.category)).append(',')
                    .append(csvCell(e.note)).append('\n');
        }
        return new Export(name, "text/csv", sb.toString());
    }

    // ---------- Markdown ----------

    /** The journal, habits, stats and badges as a readable Markdown document, oldest entry first. */
    public static Export journalMarkdown(DayHubData data, String today) {
        HabitProgress progress = new HabitProgress(data);
        HabitStats stats = progress.statsFor(today);
        List<Badges.Status> badges = progress.badgeList();
        List<Habit> habits = data.listHabits(false);
        List<Entry> entries = new ArrayList<>(data.listEntries(null, null));
        java.util.Collections.reverse(entries);
        String name = data.getSettings().name;

        List<String> l = new ArrayList<>();
        l.add("# Day Hub journal" + (name.isEmpty() ? "" : " — " + name));
        l.add("");
        l.add("_Exported " + today + "_");
        l.add("");
        l.add("## Stats");
        l.add("");
        l.add("- **Total points:** " + stats.totalPoints);
        l.add("- **Current streak:** " + stats.streak + " day" + (stats.streak == 1 ? "" : "s")
                + " (longest " + stats.longestStreak + ")");
        l.add("- **Journal entries:** " + stats.totalEntries);
        l.add("- **Words written:** " + stats.totalWords);
        int unlocked = 0;
        for (Badges.Status b : badges) if (b.unlockedOn != null) unlocked++;
        l.add("- **Badges unlocked:** " + unlocked + " / " + badges.size());
        l.add("");
        l.add("## Habits");
        l.add("");
        if (!habits.isEmpty()) {
            l.add("| Habit | Type | Target | Points |");
            l.add("|---|---|---|---|");
            for (Habit h : habits) {
                String type = h.kind.equals("check") ? "Daily check" : h.kind.equals("goal") ? "Goal" : "Limit";
                String target = h.kind.equals("check") ? "" : (h.target + " " + h.unit).trim();
                l.add("| " + h.icon + " " + h.title + " | " + type + " | " + target + " | " + h.points + " |");
            }
        } else {
            l.add("_No habits yet._");
        }
        l.add("");
        l.add("## Badges");
        l.add("");
        for (Badges.Status s : badges) {
            Badges.Badge b = s.badge;
            l.add(s.unlockedOn != null
                    ? "- [x] " + b.icon + " **" + b.name + "** — " + b.desc + " _(" + s.unlockedOn + ")_"
                    : "- [ ] " + b.icon + " " + b.name + " — " + b.desc);
        }
        l.add("");
        l.add("## Journal");
        l.add("");
        if (entries.isEmpty()) l.add("_No entries yet._");
        String lastDay = "";
        for (Entry e : entries) {
            if (!e.day.equals(lastDay)) {
                l.add("### " + e.day);
                l.add("");
                lastDay = e.day;
            }
            List<String> meta = new ArrayList<>();
            if (e.time != null && !e.time.isEmpty()) meta.add(e.time);
            if (e.mood != null) meta.add("mood " + e.mood + "/5");
            if (!e.tags.isEmpty()) {
                StringBuilder tags = new StringBuilder();
                for (String t : e.tags) tags.append(tags.length() == 0 ? "" : " ").append('#').append(t);
                meta.add(tags.toString());
            }
            if (!meta.isEmpty()) {
                l.add("_" + String.join(" · ", meta) + "_");
                l.add("");
            }
            if (!e.text.isEmpty()) {
                l.add(e.text);
                l.add("");
            }
        }
        return new Export("day-hub-journal.md", "text/markdown", String.join("\n", l) + "\n");
    }
}

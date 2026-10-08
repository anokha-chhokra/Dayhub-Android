package app.dayhub.data;

import app.dayhub.data.Model.Expense;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 8: spots spending in free text ("spent Rs 250 on lunch") so a journal entry can offer to
 * log it as an expense. It only suggests; nothing is saved from here. Ported from the web app's
 * detect.js.
 *
 * The patterns are written with explicit character classes instead of \b, \s and \d, because those
 * mean slightly different things in JavaScript, on a JVM and on Android, and the suggestions must
 * come out the same as the web app's.
 */
public final class ExpenseDetector {
    private static final String W = "[A-Za-z0-9_]";
    /** JavaScript's \b: a boundary between an ASCII word character and anything else. */
    private static final String B = "(?:(?<=" + W + ")(?!" + W + ")|(?<!" + W + ")(?=" + W + "))";
    /** JavaScript's \s. */
    private static final String S = "[\\s\\u00a0\\ufeff\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]";

    private static final String[][] CATEGORY_WORDS = {
        {"Bills", "rent|bill|bills|electricity|internet|wifi|recharged?|emi|insurance|subscription|broadband|water bill|gas|dth|maintenance|tuition|fees?|loan|mobile bill|phone bill"},
        {"Health", "doctor|medicine|medicines|pharmacy|gym|hospital|medical|checkup|dentist|clinic|physio|tablets|vitamins|lab test"},
        {"Transport", "uber|ola|rapido|auto|cab|taxi|bus|metro|train|fuel|petrol|diesel|parking|toll|flight|ticket|bike|scooter|bmtc|ride|airport|ferry"},
        {"Food", "lunch|dinner|breakfast|food|coffee|tea|chai|snack|snacks|groceries|grocery|restaurant|zomato|swiggy|biryani|pizza|cafe|meal|juice|dosa|idli|thali|burger|sandwich|dessert|tiffin|mess|canteen|bakery|chicken|eggs|milk|vegetables|fruits|ice cream"},
        {"Fun", "movie|movies|netflix|game|games|gaming|concert|party|trip|drinks|bar|pub|outing|show|cinema|ott|spotify|hotstar|prime"},
        {"Shopping", "shirt|shoes?|amazon|flipkart|clothes|mall|headphones|gift|bought|jeans|bag|watch|shopping|kurta|t-shirt|tshirt|laptop|charger|books?"},
    };
    private static final List<Pattern> CATEGORY_PATTERNS = new ArrayList<>();

    static {
        for (String[] c : CATEGORY_WORDS) CATEGORY_PATTERNS.add(Pattern.compile(B + "(" + c[1] + ")" + B));
    }

    private static final Map<String, Double> MULTIPLIER = new HashMap<>();

    static {
        MULTIPLIER.put("k", 1e3);
        MULTIPLIER.put("lakh", 1e5);
        MULTIPLIER.put("lakhs", 1e5);
        MULTIPLIER.put("lac", 1e5);
        MULTIPLIER.put("lacs", 1e5);
        MULTIPLIER.put("crore", 1e7);
        MULTIPLIER.put("crores", 1e7);
    }

    private static final int CI = Pattern.CASE_INSENSITIVE;
    private static final String SUFFIX = "(?:(k|lakhs?|lacs?|crores?)" + B + ")?";
    private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)" + S + "*" + SUFFIX;
    private static final Pattern MARKED_BEFORE = Pattern.compile(
            "(?:\\u20B9|" + B + "rs\\.?|" + B + "inr" + B + "|\\$|\\u20AC|\\u00A3)" + S + "*" + NUM, CI);
    private static final Pattern MARKED_AFTER = Pattern.compile(
            B + NUM + S + "*(?:rs\\.?" + B + "|rupees?" + B + "|inr" + B + "|\\u20B9|bucks" + B + ")", CI);
    private static final Pattern VERB = Pattern.compile(
            B + "(spent|paid|pay|bought|buy|cost|costs|ordered|recharged|booked)" + B
                    + "([^0-9.!?;\\n]{0,40}?)" + B + NUM, CI);
    private static final Pattern ALLOWED_AFTER_VERB_AMOUNT = Pattern.compile(
            "^" + S + "*(?:\\z|[,.;!?]|(?:on|for|at|towards|in|to|via|using|by|only|rupees?|rs|inr|bucks|and|but|then|today|yesterday)"
                    + B + ")", CI);
    private static final Pattern NOTE_AFTER = Pattern.compile(
            "^" + S + "*(?:on|for|at|towards)" + S + "+([^\\n\\r\\u2028\\u2029]{1,40}?)(?=\\z|[,.;!?]|" + S
                    + "+(?:and|but|then|today|yesterday|with)" + B + ")", CI);

    // Money coming in, or numbers that are not spending ("my budget is Rs 30000"), are not expenses.
    private static final Pattern NOT_SPENDING = Pattern.compile(
            B + "(earn(?:ed|ing)?|receiv(?:ed|e)|salary|credited|refund(?:ed)?|cashback|income|reimburs" + W
                    + "*|budget|saved|savings|balance|target|goal|limit|won|invoice[d]?|payment received)" + B, CI);
    private static final Pattern SPEND_VERB = Pattern.compile(
            B + "(spent|bought|ordered|booked|recharged|paid|pay|cost|costs)" + B, CI);
    private static final Pattern BEING_PAID = Pattern.compile(
            B + "(?:got|get|getting|was|were|am|been|will be|be)" + S + "+paid" + B, CI);

    private static final Pattern SENTENCES = Pattern.compile("(?<=[.!?\\n;])" + S + "+|\\n");
    private static final Pattern SEPARATOR = Pattern.compile(",|" + B + "and" + B + "|\\+", CI);
    private static final Pattern WHITESPACE = Pattern.compile(S + "+");

    private static final List<String> WEEKDAYS =
            Arrays.asList("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday");
    private static final Pattern DAY_BEFORE_YESTERDAY = Pattern.compile(B + "day before yesterday" + B);
    private static final Pattern YESTERDAY = Pattern.compile(B + "yesterday" + B + "|" + B + "last night" + B);
    private static final Pattern DAYS_AGO = Pattern.compile(B + "([0-9]{1,2}) days ago" + B);
    private static final Pattern NAMED_DAY = Pattern.compile(
            B + "(?:last|on)" + S + "+(sunday|monday|tuesday|wednesday|thursday|friday|saturday)" + B);

    private static final Set<String> STOP = new HashSet<>(
            Arrays.asList("the", "and", "for", "with", "from", "some", "my", "was", "had", "got", "new", "one"));

    private static final int MAX_SUGGESTIONS = 5;

    private ExpenseDetector() {}

    /** One thing that looks like spending. {@code daysAgo} is how many days before the entry's day. */
    public static final class Suggestion {
        public final long amountMinor;
        public final String category;
        public final String note;
        public final int daysAgo;

        Suggestion(long amountMinor, String category, String note, int daysAgo) {
            this.amountMinor = amountMinor;
            this.category = category;
            this.note = note;
            this.daysAgo = daysAgo;
        }
    }

    /** What past expenses say a word usually means. */
    public static final class Hint {
        public final String category;
        public final int n;
        public final double share;

        Hint(String category, int n, double share) {
            this.category = category;
            this.n = n;
            this.share = share;
        }
    }

    // ---------- learning from history ----------

    private static List<String> wordsOf(String text) {
        List<String> out = new ArrayList<>();
        String s = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < s.length()) {
            if (!isAsciiLetter(s.charAt(i))) {
                i++;
                continue;
            }
            int j = i;
            while (j < s.length() && isAsciiLetter(s.charAt(j))) j++;
            String w = s.substring(i, j);
            if (w.length() >= 3 && !STOP.contains(w)) out.add(w);
            i = j;
        }
        return out;
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    /** Learns "this word usually means this category" from past expenses that have notes. */
    public static Map<String, Hint> buildHints(List<Expense> expenses) {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (Expense e : expenses) {
            if (e.note == null || e.note.isEmpty() || e.category == null || e.category.isEmpty()) continue;
            for (String w : new HashSet<>(wordsOf(e.note))) {
                counts.computeIfAbsent(w, k -> new LinkedHashMap<>()).merge(e.category, 1, Integer::sum);
            }
        }
        Map<String, Hint> hints = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> word : counts.entrySet()) {
            int total = 0;
            for (int c : word.getValue().values()) total += c;
            String bestCategory = null;
            int bestN = 0;
            for (Map.Entry<String, Integer> c : word.getValue().entrySet()) {
                if (bestCategory == null || c.getValue() > bestN) { // ties keep the first seen
                    bestCategory = c.getKey();
                    bestN = c.getValue();
                }
            }
            hints.put(word.getKey(), new Hint(bestCategory, bestN, (double) bestN / total));
        }
        return hints;
    }

    private static String categoryFor(String text, Map<String, Hint> hints) {
        String s = text.toLowerCase(Locale.ROOT);
        String byKeyword = "Other";
        for (int i = 0; i < CATEGORY_WORDS.length; i++) {
            if (CATEGORY_PATTERNS.get(i).matcher(s).find()) {
                byKeyword = CATEGORY_WORDS[i][0];
                break;
            }
        }
        if (hints == null) return byKeyword;
        Hint best = null;
        for (String w : wordsOf(s)) {
            Hint hint = hints.get(w);
            if (hint != null && hint.share >= 0.6 && (best == null || hint.n > best.n)) best = hint;
        }
        if (best == null) return byKeyword;
        // Your own history beats the built-in word list once it has been seen twice; a single past
        // expense only fills in when the word list had no idea.
        if (best.n >= 2 || byKeyword.equals("Other")) return best.category;
        return byKeyword;
    }

    // ---------- reading the text ----------

    /** How many days before {@code refDay} the sentence says this happened (0 = same day). */
    private static int daysAgoIn(String sentence, String refDay) {
        String s = sentence.toLowerCase(Locale.ROOT);
        if (DAY_BEFORE_YESTERDAY.matcher(s).find()) return 2;
        if (YESTERDAY.matcher(s).find()) return 1;
        Matcher n = DAYS_AGO.matcher(s);
        if (n.find()) return Math.min(30, Integer.parseInt(n.group(1)));
        if (refDay != null && refDay.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            Matcher named = NAMED_DAY.matcher(s);
            if (named.find()) {
                int ref;
                try {
                    ref = HabitRules.weekday(refDay);
                } catch (RuntimeException e) {
                    return 0; // not a real date
                }
                int diff = (ref - WEEKDAYS.indexOf(named.group(1)) + 7) % 7;
                return diff == 0 ? 7 : diff;
            }
        }
        return 0;
    }

    private static final class Clause {
        final int start;
        final String text;

        Clause(int start, String text) {
            this.start = start;
            this.text = text;
        }
    }

    /** The part of a sentence between the nearest separators (comma, " and ", "+") around an amount. */
    private static Clause clauseAround(String sentence, int from, int to) {
        int start = 0;
        int end = sentence.length();
        Matcher m = SEPARATOR.matcher(sentence);
        while (m.find()) {
            if (m.end() <= from) start = m.end();
            else if (m.start() >= to) {
                end = m.start();
                break;
            }
        }
        return new Clause(start, sentence.substring(start, end));
    }

    private static boolean notSpending(String clauseText) {
        String t = BEING_PAID.matcher(clauseText).replaceAll(" <income> ");
        if (SPEND_VERB.matcher(t).find()) return false;
        return NOT_SPENDING.matcher(t).find() || t.contains("<income>");
    }

    private static String cleanNote(String text) {
        String s = text
                .replaceFirst("(?i)" + B + "(a|an|the|my|some|of|for|on|at|is|was|to)" + S + "*\\z", "")
                .replaceFirst("(?i)^" + S + "*(a|an|the|my|some)" + S + "+", "")
                .replaceFirst("(?i)" + S + "+(?:on|last)" + S
                        + "+(?:sunday|monday|tuesday|wednesday|thursday|friday|saturday)" + B, "")
                .replaceFirst("(?i)" + S + "+(?:yesterday|today|last night)" + B, "");
        s = Validate.trim(WHITESPACE.matcher(s).replaceAll(" "));
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static Long parseAmount(String numText, String suffix) {
        double n;
        try {
            n = Double.parseDouble(numText.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (Double.isNaN(n) || Double.isInfinite(n)) return null;
        if (suffix != null) {
            Double times = MULTIPLIER.get(suffix.toLowerCase(Locale.ROOT));
            n *= times == null ? 1.0 : times;
        }
        long minor = Math.round(n * 100);
        if (minor <= 0 || minor > 1_000_000_000L * 100) return null;
        return minor;
    }

    private static String slice(String s, int from) {
        return s.substring(Math.max(0, Math.min(from, s.length())));
    }

    private static String lastWords(String text, int count) {
        String t = Validate.trim(text);
        if (t.isEmpty()) return "";
        String[] words = WHITESPACE.split(t);
        int from = Math.max(0, words.length - count);
        return String.join(" ", Arrays.copyOfRange(words, from, words.length));
    }

    private static String firstGroup(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Up to 5 suggestions for spending found in {@code text}.
     *
     * @param refDay the entry's day ("YYYY-MM-DD"), needed to understand "on Monday"; may be null
     * @param hints  category memory from {@link #buildHints}, so your own history wins; may be null
     */
    public static List<Suggestion> detect(String text, String refDay, Map<String, Hint> hints) {
        List<Suggestion> found = new ArrayList<>();
        if (text == null || Validate.trim(text).isEmpty()) return found;
        Set<String> seen = new HashSet<>();

        for (String sentence : SENTENCES.split(text)) {
            if (Validate.trim(sentence).isEmpty()) continue;
            Set<Long> marked = new HashSet<>();

            for (Pattern re : new Pattern[] {MARKED_BEFORE, MARKED_AFTER}) {
                Matcher m = re.matcher(sentence);
                while (m.find()) {
                    Clause clause = clauseAround(sentence, m.start(), m.end());
                    int at = m.start() - clause.start;
                    String after = slice(clause.text, at + m.group().length());
                    String before = clause.text.substring(0, Math.max(0, Math.min(at, clause.text.length())));
                    String note = firstGroup(NOTE_AFTER, after);
                    if (note.isEmpty()) {
                        // "lunch Rs 250" or "paid rent of Rs 12000": use the words before the amount
                        String b = before.replaceFirst("(?i)" + B + "(spent|paid|pay|bought|buy|cost|costs|ordered)" + B, " ")
                                .replaceAll("[\\u20B9$\\u20AC\\u00A3]", " ");
                        note = lastWords(b, 3);
                    }
                    Long minor = parseAmount(m.group(1), m.group(2));
                    if (minor != null) marked.add(minor);
                    push(found, seen, hints, refDay, sentence, clause.text, minor, cleanNote(note));
                }
            }

            Matcher m = VERB.matcher(sentence);
            while (m.find()) {
                Long minor = parseAmount(m.group(3), m.group(4));
                if (minor != null && marked.contains(minor)) continue; // already found with its currency symbol
                String after = slice(sentence, m.end());
                double bare = Double.parseDouble(m.group(3).replace(",", ""));
                if (!ALLOWED_AFTER_VERB_AMOUNT.matcher(after).find()) continue; // "bought 3 apples" is not a price
                if (bare < 10 && m.group(4) == null) continue; // small bare numbers are usually counts
                String note = firstGroup(NOTE_AFTER, after);
                if (note.isEmpty()) note = cleanNote(m.group(2));
                Clause clause = clauseAround(sentence, m.start(), m.end());
                push(found, seen, hints, refDay, sentence, clause.text, minor, cleanNote(note));
            }
        }
        return found.size() > MAX_SUGGESTIONS ? new ArrayList<>(found.subList(0, MAX_SUGGESTIONS)) : found;
    }

    private static void push(List<Suggestion> found, Set<String> seen, Map<String, Hint> hints, String refDay,
                             String sentence, String clauseText, Long minor, String note) {
        if (minor == null) return;
        String key = sentence + "|" + minor;
        if (seen.contains(key)) return;
        if (notSpending(clauseText)) return;
        seen.add(key);
        found.add(new Suggestion(minor, categoryFor(clauseText + " " + note, hints), note, daysAgoIn(sentence, refDay)));
    }

    /**
     * What the web app's detect route does: suggestions for a journal entry's text, using the
     * person's own spending history, and (when editing) leaving out spending already saved with
     * the entry.
     *
     * @param day     the entry's day, or null/blank for none
     * @param entryId the entry being edited, or null for a new one
     */
    public static List<Suggestion> suggest(DayHubData data, Object text, Object day, Integer entryId) {
        String t = text instanceof String ? (String) text : "";
        if (t.length() > 10_000) t = t.substring(0, 10_000);
        boolean hasDay = !Validate.missing(day) && !"".equals(day);
        String refDay = hasDay ? Validate.isoDate(day, "day") : null;
        List<Suggestion> items = detect(t, refDay, buildHints(data.recentExpensesWithNotes(500)));
        if (entryId == null) return items;

        List<Long> saved = new ArrayList<>();
        for (Expense e : data.listEntryExpenses(entryId)) saved.add(e.amountMinor);
        List<Suggestion> fresh = new ArrayList<>();
        for (Suggestion s : items) {
            int at = saved.indexOf(s.amountMinor);
            if (at == -1) fresh.add(s);
            else saved.remove(at);
        }
        return fresh;
    }
}

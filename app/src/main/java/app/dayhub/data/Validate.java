package app.dayhub.data;

import static app.dayhub.data.DataError.bad;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Input validation, ported from the web app's validate.js. Everything the user types or imports
 * goes through here. Values are the loose objects a JSON document gives (String, Number, Boolean,
 * JSONArray, null / JSONObject.NULL); a bad value throws a 400 {@link DataError} with the same
 * wording as the web app.
 */
public final class Validate {
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final Pattern HHMM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern MONEY = Pattern.compile("\\d+(\\.\\d{1,2})?");
    private static final Pattern WHOLE = Pattern.compile("-?\\d+");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private Validate() {}

    /** True for an absent value or a JSON null. */
    public static boolean missing(Object v) {
        return v == null || v == JSONObject.NULL;
    }

    private static boolean blank(Object v) {
        return missing(v) || "".equals(v);
    }

    // ---------- text ----------

    /** Same set of characters JavaScript's trim() removes. */
    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r'
                || c == ' ' || c == '﻿' || c == ' ' || (c >= ' ' && c <= ' ')
                || c == ' ' || c == ' ' || c == ' ' || c == ' ' || c == '　';
    }

    private static String trim(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && isSpace(s.charAt(a))) a++;
        while (b > a && isSpace(s.charAt(b - 1))) b--;
        return s.substring(a, b);
    }

    private static String clean(String raw, boolean multiline) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (multiline) {
                if (c == '\r') { // CRLF and lone CR both become one line break
                    sb.append('\n');
                    if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') i++;
                    continue;
                }
                // Line breaks and tabs stay; other control characters become spaces.
                if (c <= 8 || c == 0x0b || c == 0x0c || (c >= 0x0e && c <= 0x1f) || c == 0x7f) c = ' ';
            } else if (c <= 0x1f || c == 0x7f) {
                c = ' ';
            }
            sb.append(c);
        }
        return trim(sb.toString());
    }

    /**
     * Trimmed text with control characters removed. Empty gives null when optional.
     * {@code multiline} keeps line breaks and tabs (journal entries); otherwise one line.
     */
    public static String str(Object value, String field, int max, boolean optional, boolean multiline) {
        if (missing(value)) {
            if (optional) return null;
            throw bad(field + " is required");
        }
        if (!(value instanceof String)) throw bad(field + " must be text");
        String s = clean((String) value, multiline);
        if (s.isEmpty()) {
            if (optional) return null;
            throw bad(field + " is required");
        }
        if (s.length() > max) throw bad(field + " is too long (max " + max + " characters)");
        return s;
    }

    /** Required single-line text. */
    public static String str(Object value, String field, int max) {
        return str(value, field, max, false, false);
    }

    /** Optional single-line text: null when missing or blank. */
    public static String optStr(Object value, String field, int max) {
        return str(value, field, max, true, false);
    }

    /** Optional text that keeps its line breaks: null when missing or blank. */
    public static String optMultiline(Object value, String field, int max) {
        return str(value, field, max, true, true);
    }

    /** Number of whitespace-separated words. */
    public static int wordCount(String text) {
        int n = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            boolean space = isSpace(text.charAt(i));
            if (!space && !inWord) n++;
            inWord = !space;
        }
        return n;
    }

    // ---------- dates and times ----------

    private static boolean realDate(int y, int m, int d) {
        // JavaScript's Date.UTC maps years 0-99 to 1900-1999, so the web app rejects them too.
        if (y < 100) return false;
        try {
            LocalDate.of(y, m, d);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    /** "2026-10-05" for a real calendar date. Missing gives null when optional. */
    public static String isoDate(Object value, String field, boolean optional) {
        if (blank(value)) {
            if (optional) return null;
            throw bad(field + " is required");
        }
        if (!(value instanceof String) || !ISO_DATE.matcher((String) value).matches()) {
            throw bad(field + " must look like 2026-10-05");
        }
        String s = (String) value;
        int y = Integer.parseInt(s.substring(0, 4));
        int m = Integer.parseInt(s.substring(5, 7));
        int d = Integer.parseInt(s.substring(8, 10));
        if (!realDate(y, m, d)) throw bad(field + " is not a real date");
        return s;
    }

    public static String isoDate(Object value, String field) {
        return isoDate(value, field, false);
    }

    public static String optIsoDate(Object value, String field) {
        return isoDate(value, field, true);
    }

    /** "2026-10". */
    public static String month(Object value, String field) {
        if (!(value instanceof String) || !MONTH.matcher((String) value).matches()) {
            throw bad(field + " must look like 2026-10");
        }
        return (String) value;
    }

    /** "07:30" style time, or null when empty. */
    public static String hhmm(Object value, String field) {
        if (blank(value)) return null;
        if (!(value instanceof String) || !HHMM.matcher((String) value).matches()) {
            throw bad(field + " must look like 07:30");
        }
        return (String) value;
    }

    public static String hhmm(Object value) {
        return hhmm(value, "time");
    }

    /** Today's date on this phone, "2026-10-05". */
    public static String localDate() {
        return LocalDate.now().toString();
    }

    /** The caller's "today" when it is a valid date, otherwise this phone's date. */
    public static String todayOf(Object candidate) {
        try {
            return isoDate(candidate, "today");
        } catch (DataError e) {
            return localDate();
        }
    }

    /** {first day, first day of the next month} for a "2026-10" month, for from/to ranges. */
    public static String[] monthRange(String ym) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(5, 7));
        String next = m == 12 ? (y + 1) + "-01" : String.format(Locale.ROOT, "%d-%02d", y, m + 1);
        return new String[] {ym + "-01", next + "-01"};
    }

    public static String addDays(String ymd, int n) {
        return LocalDate.parse(ymd).plusDays(n).toString();
    }

    // ---------- numbers ----------

    /** "123.45" or 123.45 gives 12345 (whole minor units). No float rounding surprises. */
    public static long moneyToMinor(Object value, String field, boolean allowZero) {
        String s;
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) throw bad(field + " must be a number");
            s = d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d);
        } else if (value instanceof Number) {
            s = value.toString();
        } else if (value instanceof String) {
            s = trim((String) value).replace(",", "");
        } else {
            throw bad(field + " must be a number");
        }
        if (!MONEY.matcher(s).matches()) {
            throw bad(field + " must be a positive amount with up to 2 decimals");
        }
        int dot = s.indexOf('.');
        String whole = dot < 0 ? s : s.substring(0, dot);
        String frac = dot < 0 ? "" : s.substring(dot + 1);
        if (whole.length() > 13) throw bad(field + " is too large");
        long minor = Long.parseLong(whole) * 100 + Long.parseLong((frac + "00").substring(0, 2));
        if (!allowZero && minor <= 0) throw bad(field + " must be greater than zero");
        if (minor > 1_000_000_000L * 100) throw bad(field + " is too large");
        return minor;
    }

    public static long moneyToMinor(Object value, String field) {
        return moneyToMinor(value, field, false);
    }

    private static Long asInteger(Object v) {
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return ((Number) v).longValue();
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d) || Math.abs(d) > 9e18) return null;
            return (long) d;
        }
        if (v instanceof String) {
            String s = trim((String) v);
            if (!WHOLE.matcher(s).matches()) return null;
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Whole number within [min, max]. Missing gives {@code fallback}, or an error when that is null. */
    public static long longIn(Object value, String field, long min, long max, Long fallback) {
        if (blank(value)) {
            if (fallback != null) return fallback;
            throw bad(field + " is required");
        }
        Long n = asInteger(value);
        if (n == null || n < min || n > max) {
            throw bad(field + " must be a whole number from " + min + " to " + max);
        }
        return n;
    }

    public static int intIn(Object value, String field, int min, int max, Integer fallback) {
        return (int) longIn(value, field, min, max, fallback == null ? null : Long.valueOf(fallback));
    }

    /** A row id: a positive whole number, as a number or digits in text. */
    public static int id(Object value) {
        Long n = asInteger(value);
        if (n == null || n <= 0 || n > Integer.MAX_VALUE) throw bad("Invalid id");
        return (int) (long) n;
    }

    // ---------- choices ----------

    /** One of {@code list}. Missing gives {@code fallback}, or an error when that is null. */
    public static String oneOf(Object value, List<String> list, String field, String fallback) {
        if (blank(value)) {
            if (fallback != null) return fallback;
            throw bad(field + " is required");
        }
        if (!(value instanceof String) || !list.contains(value)) {
            throw bad(field + " must be one of: " + String.join(", ", list));
        }
        return (String) value;
    }

    /** One of the whole numbers in {@code list}. Missing gives {@code fallback}, or an error when null. */
    public static int oneOf(Object value, int[] list, String field, Integer fallback) {
        if (blank(value)) {
            if (fallback != null) return fallback;
            throw bad(field + " is required");
        }
        Long n = value instanceof String ? null : asInteger(value);
        if (n != null) {
            for (int allowed : list) if (allowed == n) return allowed;
        }
        StringBuilder names = new StringBuilder();
        for (int allowed : list) names.append(names.length() == 0 ? "" : ", ").append(allowed);
        throw bad(field + " must be one of: " + names);
    }

    /** A 3-letter currency code like "INR", upper-cased. */
    public static String currency(Object value) {
        String code = str(value, "currency", 3).toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(code).matches()) throw bad("currency must be a 3-letter code like INR");
        return code;
    }

    // ---------- lists ----------

    private static List<?> asList(Object v) {
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> out = new ArrayList<>(a.length());
            for (int i = 0; i < a.length(); i++) out.add(a.opt(i));
            return out;
        }
        return v instanceof List ? (List<?>) v : null;
    }

    /** Weekday list like [0,1,2,3,4,5,6] (0 = Sunday), sorted and without repeats. */
    public static List<Integer> weekdays(Object value) {
        if (missing(value)) return new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6));
        List<?> items = asList(value);
        if (items == null || items.isEmpty()) throw bad("Pick at least one day");
        TreeSet<Integer> days = new TreeSet<>();
        for (Object d : items) days.add(intIn(d, "day", 0, 6, null));
        return new ArrayList<>(days);
    }

    /** Up to 8 short lowercase tags, without repeats. */
    public static List<String> tags(Object value) {
        List<String> out = new ArrayList<>();
        if (missing(value)) return out;
        List<?> items = asList(value);
        if (items == null) throw bad("tags must be a list");
        for (Object raw : items) {
            if (!(raw instanceof String)) throw bad("tags must be text");
            StringBuilder sb = new StringBuilder();
            for (char c : ((String) raw).toCharArray()) sb.append(c <= 0x1f || c == 0x7f || c == '#' ? ' ' : c);
            String t = trim(sb.toString()).toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (t.length() > 24) throw bad("each tag must be 24 characters or fewer");
            if (!out.contains(t)) out.add(t);
        }
        if (out.size() > 8) throw bad("at most 8 tags");
        return out;
    }
}

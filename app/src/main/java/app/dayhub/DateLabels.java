package app.dayhub;

import android.content.Context;
import android.text.format.DateUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Greetings and friendly dates for the screens, in the phone's language. */
public final class DateLabels {
    private DateLabels() {}

    /** The clock time now as "HH:MM". */
    public static String nowHHMM() {
        return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    /** "Good morning, Asha" by the hour of the day. */
    public static String greeting(String name) {
        int hour = LocalTime.now().getHour();
        String part = hour < 5 ? "Still up" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon"
                : hour < 22 ? "Good evening" : "Late night";
        return name == null || name.isEmpty() ? part : part + ", " + name;
    }

    private static long millis(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** "Thursday, October 8". */
    public static String weekdayDate(Context c) {
        return DateUtils.formatDateTime(c, millis(LocalDate.now()),
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR);
    }

    /** "Today", "Tomorrow", "Yesterday" or a short date like "Mon, Oct 12". */
    public static String dueLabel(Context c, String dueOn, String today) {
        if (dueOn == null || dueOn.isEmpty()) return "";
        LocalDate due = LocalDate.parse(dueOn);
        LocalDate t = LocalDate.parse(today);
        if (due.equals(t)) return "Today";
        if (due.equals(t.plusDays(1))) return "Tomorrow";
        if (due.equals(t.minusDays(1))) return "Yesterday";
        return DateUtils.formatDateTime(c, millis(due), DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY
                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
    }
}

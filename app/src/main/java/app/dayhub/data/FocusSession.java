package app.dayhub.data;

import java.util.Locale;

/**
 * Feature 24: one focus timer. Immutable, and "now" is always passed in so it can be checked without a
 * clock. This is the web app's Android FocusSession, so the same rules hold: a timer is at least a minute
 * and never longer than eight hours (a lock must always end), a label is one short line, and a session
 * whose end time has passed is over whatever was stored.
 */
public final class FocusSession {
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 480;
    public static final int MAX_LABEL = 60;

    private static final FocusSession IDLE = new FocusSession(false, 0, 0, 0, "");

    public final boolean active;
    public final long startedAt;
    public final long endsAt;
    public final int minutes;
    public final String label;

    public FocusSession(boolean active, long startedAt, long endsAt, int minutes, String label) {
        this.active = active;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.minutes = minutes;
        this.label = label == null ? "" : label;
    }

    public static FocusSession idle() {
        return IDLE;
    }

    public static int clampMinutes(int minutes) {
        return Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, minutes));
    }

    /** Keeps a label short and on one line. */
    public static String cleanLabel(String label) {
        if (label == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < label.length() && b.length() < MAX_LABEL; i++) {
            char c = label.charAt(i);
            b.append(c < 0x20 || c == 0x7f ? ' ' : c);
        }
        // Cutting at the limit must not leave half of an emoji behind.
        if (b.length() > 0 && Character.isHighSurrogate(b.charAt(b.length() - 1))) b.setLength(b.length() - 1);
        return b.toString().trim();
    }

    public static FocusSession start(long now, int minutes, String label) {
        int m = clampMinutes(minutes);
        return new FocusSession(true, now, now + m * 60_000L, m, cleanLabel(label));
    }

    /** True while the timer is running. A session whose end time has passed is over, whatever was stored. */
    public boolean isActive(long now) {
        return active && endsAt > now;
    }

    public long remainingMs(long now) {
        return isActive(now) ? endsAt - now : 0;
    }

    /** True if this was running when last saved but its time has run out. */
    public boolean hasExpired(long now) {
        return active && endsAt <= now;
    }

    /** "1:05:09" or "04:09": what a timer shows. A part of a second counts as a whole one. */
    public static String clock(long ms) {
        long total = Math.max(0, (ms + 999) / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }
}

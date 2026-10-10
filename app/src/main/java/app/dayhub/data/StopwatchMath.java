package app.dayhub.data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Feature 23: the maths of the focus stopwatch, ported from the web app's stopwatch.js.
 *
 * The dial is re-engraved for whatever length is set. A 25 minute focus gets numerals every 5 minutes
 * and a tick for each minute; 2 hours gets numerals every 20 minutes. The red hand starts at 12 and
 * makes one turn over the whole length, and the yellow wedge is the time that is left. Plain functions,
 * so they can be tested without a screen; the drawing is in StopwatchView.
 */
public final class StopwatchMath {
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 480; // eight hours

    private static final int[] NICE_STEPS = {1, 2, 3, 5, 10, 15, 20, 30, 60, 120};
    private static final int TARGET_LABELS = 7;
    private static final double NEAR_TOP_DEG = 346; // a numeral closer to 12 than this would sit on top of the 0
    private static final int LONG_DIAL_MIN = 120; // above this, numerals are whole hours so they fit
    private static final Pattern LEADING_INTEGER = Pattern.compile("^[+-]?[0-9]+");

    private StopwatchMath() {}

    // ---------- lengths ----------

    /**
     * A whole number of minutes inside the allowed range, or null if the value is not a number at all.
     * Numbers are rounded; text is read like the start of "25 minutes" (the leading whole number).
     */
    public static Integer clampMinutes(Object value) {
        double n;
        if (value instanceof Number) {
            n = ((Number) value).doubleValue();
        } else {
            Matcher m = LEADING_INTEGER.matcher(jsTrim(String.valueOf(value)));
            if (!m.find()) return null;
            n = new BigInteger(m.group().startsWith("+") ? m.group().substring(1) : m.group()).doubleValue();
        }
        if (Double.isNaN(n) || Double.isInfinite(n)) return null;
        return (int) Math.min(MAX_MINUTES, Math.max(MIN_MINUTES, Math.round(n)));
    }

    private static String jsTrim(String s) {
        return Validate.trim(s);
    }

    /** "25 min", "1 h", "1 h 30 min". */
    public static String minutesLabel(double minutes) {
        long m = Math.round(minutes);
        long h = m / 60;
        long rest = m % 60;
        if (h == 0) return m + " min";
        return rest != 0 ? h + " h " + rest + " min" : h + " h";
    }

    /** What is printed on the face under the hub: short, so it fits between the numerals. */
    public static String faceLength(double minutes) {
        long m = Math.round(minutes);
        if (m < 100) return m + " min";
        long h = m / 60;
        long rest = m % 60;
        return rest != 0 ? h + "h " + rest + "m" : h + " h";
    }

    // ---------- the dial ----------

    /** A small mark on the dial. {@code deg} is clockwise from 12 o'clock. */
    public static final class Tick {
        public final int at;
        public final double deg;
        public final boolean major;

        Tick(int at, double deg, boolean major) {
            this.at = at;
            this.deg = deg;
            this.major = major;
        }
    }

    /** A numeral printed on the dial. */
    public static final class Label {
        public final int at;
        public final double deg;
        public final String text;

        Label(int at, double deg, String text) {
            this.at = at;
            this.deg = deg;
            this.text = text;
        }
    }

    /** Which ticks and numerals a dial spanning some minutes gets. */
    public static final class Plan {
        public final int span;
        public final int step;
        public final int minor;
        public final List<Tick> ticks;
        public final List<Label> labels;

        Plan(int span, int step, int minor, List<Tick> ticks, List<Label> labels) {
            this.span = span;
            this.step = step;
            this.minor = minor;
            this.ticks = ticks;
            this.labels = labels;
        }
    }

    private static String dialText(int v, int span) {
        return span > LONG_DIAL_MIN ? (v != 0 ? (v / 60) + "h" : "0") : String.valueOf(v);
    }

    /**
     * Numerals go to the step that gives about seven of them (preferring a step that divides the length
     * evenly), and the small ticks split each numeral gap into 5, 4, 3 or 2.
     */
    public static Plan dialPlan(int minutes) {
        Integer clamped = clampMinutes(minutes);
        int span = clamped == null ? MIN_MINUTES : clamped;
        int step = 1;
        double best = Double.POSITIVE_INFINITY;
        for (int s : NICE_STEPS) {
            if (s > span && s != 1) continue;
            if (span > LONG_DIAL_MIN && s % 60 != 0) continue; // long dials are numbered in whole hours
            double cost = Math.abs(Math.ceil((double) span / s) - TARGET_LABELS) + (span % s != 0 ? 2 : 0);
            if (cost <= best) { // later (larger) steps win a tie
                best = cost;
                step = s;
            }
        }
        int minor = step;
        for (int k : new int[] {5, 4, 3, 2}) {
            if (step % k == 0) {
                minor = step / k;
                break;
            }
        }
        List<Tick> ticks = new ArrayList<>();
        for (int at = 0; at < span; at += minor) ticks.add(new Tick(at, ((double) at / span) * 360, at % step == 0));
        List<Label> labels = new ArrayList<>();
        for (int at = 0; at < span; at += step) {
            double deg = ((double) at / span) * 360;
            if (at > 0 && deg > NEAR_TOP_DEG) continue;
            labels.add(new Label(at, deg, dialText(at, span)));
        }
        return new Plan(span, step, minor, ticks, labels);
    }

    // ---------- geometry ----------

    /** A point on a circle, with 0 degrees at 12 o'clock and angles growing clockwise: {x, y}. */
    public static double[] polar(double cx, double cy, double r, double deg) {
        double rad = (deg * Math.PI) / 180;
        return new double[] {cx + r * Math.sin(rad), cy - r * Math.cos(rad)};
    }

    private static double f(double n) {
        return Math.round(n * 100) / 100.0;
    }

    /** A number the way JavaScript would print it: no trailing ".0", and never "-0". */
    private static String num(double v) {
        if (v == 0) return "0";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    /**
     * The slice of a circle from one angle clockwise to another, as an SVG path (the same text the web
     * app draws). Empty if there is nothing to draw.
     */
    public static String wedgePath(double cx, double cy, double r, double fromDeg, double toDeg) {
        double sweep = toDeg - fromDeg;
        if (!(sweep > 0.05)) return "";
        if (sweep >= 359.95) {
            return "M" + num(f(cx - r)) + " " + num(f(cy)) + "A" + num(r) + " " + num(r) + " 0 1 1 " + num(f(cx + r)) + " " + num(f(cy))
                    + "A" + num(r) + " " + num(r) + " 0 1 1 " + num(f(cx - r)) + " " + num(f(cy)) + "Z";
        }
        double[] a = polar(cx, cy, r, fromDeg);
        double[] b = polar(cx, cy, r, toDeg);
        return "M" + num(f(cx)) + " " + num(f(cy)) + "L" + num(f(a[0])) + " " + num(f(a[1])) + "A" + num(r) + " " + num(r) + " 0 "
                + (sweep > 180 ? 1 : 0) + " 1 " + num(f(b[0])) + " " + num(f(b[1])) + "Z";
    }

    /** Where the hand points after {@code elapsedMs} of a {@code totalMs} focus. */
    public static double handDegrees(double elapsedMs, double totalMs) {
        if (!(totalMs > 0)) return 0;
        return Math.min(1, Math.max(0, elapsedMs / totalMs)) * 360;
    }
}

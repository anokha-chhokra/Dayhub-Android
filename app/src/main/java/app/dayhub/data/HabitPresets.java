package app.dayhub.data;

import java.util.Arrays;
import java.util.List;

/** Ready-made habits offered in setup and in the "add habit" sheet, ported from the web app's habits.js. */
public final class HabitPresets {
    private HabitPresets() {}

    public static final class Preset {
        public final String id;
        public final String title;
        public final String icon;
        public final String kind; // check, goal or limit
        public final String unit;
        public final int target;
        public final int step;
        public final int points;

        private Preset(String id, String title, String icon, String kind, String unit, int target, int step, int points) {
            this.id = id;
            this.title = title;
            this.icon = icon;
            this.kind = kind;
            this.unit = unit;
            this.target = target;
            this.step = step;
            this.points = points;
        }
    }

    public static final List<Preset> ALL = Arrays.asList(
            new Preset("water", "Drink water", "\uD83D\uDCA7", "goal", "glasses", 8, 1, 10),
            new Preset("steps", "Steps", "\uD83D\uDEB6", "goal", "steps", 8000, 500, 20),
            new Preset("workout", "Workout", "\uD83C\uDFCB\uFE0F", "check", "", 1, 1, 20),
            new Preset("read", "Read 20 pages", "\uD83D\uDCD6", "check", "", 1, 1, 10),
            new Preset("meditate", "Meditate", "\uD83E\uDDD8", "check", "", 1, 1, 10),
            new Preset("sleep", "Sleep by 11 pm", "\uD83D\uDE34", "check", "", 1, 1, 10),
            new Preset("nophone", "No screens before bed", "\uD83D\uDCF5", "check", "", 1, 1, 10),
            new Preset("coffee", "Coffee / tea", "\u2615", "limit", "cups", 3, 1, 5),
            new Preset("screen", "Screen time", "\uD83D\uDCF1", "limit", "hours", 3, 1, 5));

    public static Preset byId(Object id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return null;
    }
}

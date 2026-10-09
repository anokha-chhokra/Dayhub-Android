package app.dayhub.data;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/** Journal prompts and tag ideas, ported from the web app's prompts.js. A different prompt is suggested each day. */
public final class Prompts {
    private Prompts() {}

    public static final class Prompt {
        public final String id;
        public final String title;
        public final String text;

        private Prompt(String id, String title, String text) {
            this.id = id;
            this.title = title;
            this.text = text;
        }
    }

    public static final List<Prompt> ALL = Arrays.asList(
            new Prompt("gratitude", "Gratitude", "Three things you are grateful for today"),
            new Prompt("reflection", "Reflection", "One thing you would do differently"),
            new Prompt("highlight", "Highlight", "The best part of your day"),
            new Prompt("challenge", "Challenge", "Something that tested you today"),
            new Prompt("growth", "Growth", "Something you learned"));

    public static final List<String> TAG_SUGGESTIONS =
            Arrays.asList("gratitude", "work", "family", "health", "growth");

    /** The prompt for a day ("YYYY-MM-DD"): the same one all day, a different one tomorrow. */
    public static Prompt forDay(String ymd) {
        long dayNumber = LocalDate.parse(ymd).toEpochDay();
        return ALL.get((int) Math.floorMod(dayNumber, (long) ALL.size()));
    }
}

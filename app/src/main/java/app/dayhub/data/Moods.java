package app.dayhub.data;

import java.util.Arrays;
import java.util.List;

/** The five moods a journal entry can carry, from rough to great, as in the web app. */
public final class Moods {
    private Moods() {}

    public static final class Mood {
        public final int value; // 1 to 5
        public final String emoji;
        public final String label;

        private Mood(int value, String emoji, String label) {
            this.value = value;
            this.emoji = emoji;
            this.label = label;
        }
    }

    public static final List<Mood> ALL = Arrays.asList(
            new Mood(1, "😞", "Rough"),
            new Mood(2, "😕", "Low"),
            new Mood(3, "😐", "Okay"),
            new Mood(4, "🙂", "Good"),
            new Mood(5, "😄", "Great"));

    /** The emoji for a mood value, or an empty string for none. */
    public static String emoji(Integer value) {
        if (value == null) return "";
        for (Mood m : ALL) if (m.value == value) return m.emoji;
        return "";
    }
}

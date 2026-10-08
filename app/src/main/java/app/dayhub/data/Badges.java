package app.dayhub.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The badges that can be earned, ported from the web app's habits.js, in the same order. */
public final class Badges {
    private Badges() {}

    private interface Test {
        boolean earned(HabitStats s);
    }

    /** One badge: what it looks like and what earns it. */
    public static final class Badge {
        public final String id;
        public final String icon;
        public final String name;
        public final String desc;
        private final Test test;

        private Badge(String id, String icon, String name, String desc, Test test) {
            this.id = id;
            this.icon = icon;
            this.name = name;
            this.desc = desc;
            this.test = test;
        }
    }

    /** A badge and the day it was earned, or null if it has not been earned yet. */
    public static final class Status {
        public final Badge badge;
        public final String unlockedOn;

        Status(Badge badge, String unlockedOn) {
            this.badge = badge;
            this.unlockedOn = unlockedOn;
        }
    }

    public static final List<Badge> ALL = Arrays.asList(
            new Badge("first-step", "\uD83D\uDC63", "First step", "Complete a habit for the first time", s -> s.habitDoneCount >= 1),
            new Badge("first-entry", "\u270D\uFE0F", "First entry", "Write your first journal entry", s -> s.totalEntries >= 1),
            new Badge("streak-3", "\uD83D\uDD25", "3-day streak", "Show up 3 days in a row", s -> s.longestStreak >= 3),
            new Badge("streak-7", "\uD83D\uDD25", "7-day streak", "Show up 7 days in a row", s -> s.longestStreak >= 7),
            new Badge("streak-30", "\uD83C\uDFC6", "30-day streak", "Show up 30 days in a row", s -> s.longestStreak >= 30),
            new Badge("points-100", "\u2B50", "100 points", "Earn 100 points", s -> s.totalPoints >= 100),
            new Badge("points-1000", "\uD83C\uDF1F", "1,000 points", "Earn 1,000 points", s -> s.totalPoints >= 1000),
            new Badge("entries-10", "\uD83D\uDCD3", "10 entries", "Write 10 journal entries", s -> s.totalEntries >= 10),
            new Badge("entries-100", "\uD83D\uDCDA", "100 entries", "Write 100 journal entries", s -> s.totalEntries >= 100),
            new Badge("words-10000", "\uD83D\uDD8B\uFE0F", "10,000 words", "Write 10,000 words in total", s -> s.totalWords >= 10000),
            new Badge("night-owl", "\uD83E\uDD89", "Night owl", "Write an entry after 9 pm", s -> s.nightOwl),
            new Badge("early-bird", "\uD83D\uDC26", "Early bird", "Write an entry before 7 am", s -> s.earlyBird),
            new Badge("clean-build", "\u2705", "Clean build", "Finish every habit in a day (3 or more habits)", s -> s.perfectDays >= 1));

    public static Badge byId(String id) {
        for (Badge b : ALL) if (b.id.equals(id)) return b;
        return null;
    }

    /** Ids of every badge these stats have earned. */
    public static List<String> earnedIds(HabitStats stats) {
        List<String> ids = new ArrayList<>();
        for (Badge b : ALL) if (b.test.earned(stats)) ids.add(b.id);
        return ids;
    }
}

package app.dayhub.data;

import app.dayhub.data.HabitProgress.HabitDay;
import app.dayhub.data.Model.Settings;
import app.dayhub.data.Model.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature 9: what the Home screen's spending tile and "Needs you now" list show, worked out from
 * the stored data the way the web app's dashboard route does.
 */
public final class DashboardInsights {
    /** This month's spending against the budget. */
    public static final class Spend {
        public final String month;
        public final String currency;
        public final long totalMinor;
        public final long budgetMinor;
        public final long todayMinor;
        public final Insights.Pace pace;

        Spend(String month, String currency, long totalMinor, long budgetMinor, long todayMinor, Insights.Pace pace) {
            this.month = month;
            this.currency = currency;
            this.totalMinor = totalMinor;
            this.budgetMinor = budgetMinor;
            this.todayMinor = todayMinor;
            this.pace = pace;
        }
    }

    public final Spend spend;
    public final List<Insights.Item> attention;

    private DashboardInsights(Spend spend, List<Insights.Item> attention) {
        this.spend = spend;
        this.attention = attention;
    }

    /**
     * @param today "YYYY-MM-DD"
     * @param now   "HH:MM" or null; without it, time-based nudges (late habits, the journal) are skipped
     */
    public static DashboardInsights compute(DayHubData data, String today, String now) {
        Settings settings = data.getSettings();
        String ym = today.substring(0, 7);
        String[] range = Validate.monthRange(ym);
        DayHubData.Summary month = data.summarizeExpenses(range[0], range[1]);
        DayHubData.Summary day = data.summarizeExpenses(today, Validate.addDays(today, 1));
        Insights.Pace pace = Insights.spendPace(ym, today, month.totalMinor, settings.monthlyBudgetMinor);

        List<Task> open = new ArrayList<>();
        for (Task t : data.dashboardTasks(today)) if (!t.done) open.add(t);
        List<Insights.HabitNudge> habits = new ArrayList<>();
        for (HabitDay d : new HabitProgress(data).habitsForDay(today, true)) habits.add(Insights.HabitNudge.of(d));
        int entriesToday = data.listEntries(today, Validate.addDays(today, 1)).size();

        List<Insights.Item> attention = Insights.buildAttention(today, now, open, habits, entriesToday,
                settings.journalReminder, pace.status, month.totalMinor - settings.monthlyBudgetMinor);
        return new DashboardInsights(
                new Spend(ym, settings.currency, month.totalMinor, settings.monthlyBudgetMinor, day.totalMinor, pace),
                attention);
    }
}

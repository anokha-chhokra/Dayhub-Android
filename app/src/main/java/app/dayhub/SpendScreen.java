package app.dayhub;

import android.app.Activity;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import app.dayhub.data.DayHubData;
import app.dayhub.data.Insights;
import app.dayhub.data.Model.Expense;
import app.dayhub.data.Model.Settings;
import app.dayhub.data.Validate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

/**
 * Feature 17: the Spend tab. A month at a time: how much was spent against the budget (with the pace
 * for the current month), a bar for each category, then every expense newest first with a bin to
 * delete it (Undo brings it back). Add records a new one; CSV saves the month as a spreadsheet.
 */
public final class SpendScreen extends ScrollView {
    private final Activity activity;
    private final DayHubData data;
    private final ExpenseActions actions;
    private final DataTransfer transfer;
    private final LinearLayout nav;
    private final LinearLayout body;
    private String ym = Validate.localDate().substring(0, 7);

    public SpendScreen(Activity activity, DayHubData data, ExpenseActions actions, DataTransfer transfer) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.actions = actions;
        this.transfer = transfer;
        setFillViewport(true);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Sketch.label(activity, "Spending", 32, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton add = new HandDrawnButton(activity, "+ Add", true);
        add.setOnClickListener(v -> actions.openSheet(Validate.localDate()));
        head.addView(add);
        column.addView(head);

        nav = new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(nav, rowParams(14));
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        column.addView(body, rowParams(4));
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Redraws the month heading and everything under it from the stored data. */
    public void refresh() {
        String thisMonth = Validate.localDate().substring(0, 7);
        if (ym.compareTo(thisMonth) > 0) ym = thisMonth;
        drawNav(thisMonth);
        body.removeAllViews();

        Settings settings = data.getSettings();
        String[] range = Validate.monthRange(ym);
        DayHubData.Summary sum = data.summarizeExpenses(range[0], range[1]);
        List<Expense> expenses = data.listExpenses(range[0], range[1]);
        body.addView(summaryCard(sum, settings, thisMonth), rowParams(0));
        body.addView(listCard(expenses, settings.currency), rowParams(14));
    }

    // ---------- month navigation ----------

    private HandDrawnButton arrow(String glyph, String description, boolean enabled, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, glyph, false);
        b.setContentDescription(description);
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.35f);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void drawNav(String thisMonth) {
        nav.removeAllViews();
        long first = YearMonth.parse(ym).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        String title = DateUtils.formatDateTime(activity, first,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_SHOW_YEAR);
        nav.addView(arrow("‹", "Previous month", true, () -> {
            ym = YearMonth.parse(ym).minusMonths(1).toString();
            refresh();
        }));
        TextViewHolder.centered(nav, Sketch.label(activity, title, 22, true, R.color.ink));
        nav.addView(arrow("›", "Next month", ym.compareTo(thisMonth) < 0, () -> {
            ym = YearMonth.parse(ym).plusMonths(1).toString();
            refresh();
        })); // no future months
    }

    // ---------- the month at a glance ----------

    private View summaryCard(DayHubData.Summary sum, Settings settings, String thisMonth) {
        String cur = settings.currency;
        long budget = settings.monthlyBudgetMinor;
        boolean over = budget > 0 && sum.totalMinor > budget;
        int pct = budget > 0 ? (int) Math.min(100, Math.round((double) sum.totalMinor / budget * 100)) : 0;

        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Spent this month", 14, true, R.color.muted));
        card.addView(Sketch.label(activity, MoneyFormat.money(sum.totalMinor, cur), 34, true, R.color.ink), rowParams(2));
        if (budget > 0) {
            card.addView(Sketch.label(activity, over
                    ? "Over budget by " + MoneyFormat.money(sum.totalMinor - budget, cur)
                    : MoneyFormat.money(budget - sum.totalMinor, cur) + " left of " + MoneyFormat.money(budget, cur),
                    15, false, over ? R.color.red : R.color.muted));
            card.addView(new HandDrawnBar(activity, pct, over), rowParams(10));
        } else {
            card.addView(Sketch.label(activity, "Set a monthly budget in Settings to see progress.", 15, false, R.color.muted));
        }
        if (ym.equals(thisMonth)) {
            Insights.Pace pace = Insights.spendPace(ym, Validate.localDate(), sum.totalMinor, budget);
            String text = MoneyFormat.paceText(pace, cur);
            if (!text.isEmpty()) {
                card.addView(Sketch.label(activity, text, 14, false, pace.status.equals("watch") ? R.color.red : R.color.muted), rowParams(8));
            }
        }
        if (!sum.byCategory.isEmpty()) {
            long top = 1;
            for (DayHubData.CategoryTotal c : sum.byCategory) top = Math.max(top, c.totalMinor);
            for (DayHubData.CategoryTotal c : sum.byCategory) {
                LinearLayout line = new LinearLayout(activity);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.addView(Sketch.label(activity, c.category, 16, false, R.color.ink),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                line.addView(Sketch.label(activity, MoneyFormat.money(c.totalMinor, cur), 16, true, R.color.ink));
                card.addView(line, rowParams(12));
                card.addView(new HandDrawnBar(activity, (int) Math.round((double) c.totalMinor / top * 100), false), rowParams(4));
            }
        }
        HandDrawnButton csv = new HandDrawnButton(activity, "Save as CSV", false);
        csv.setOnClickListener(v -> transfer.exportExpenses(ym));
        card.addView(csv, rowParams(14));
        return card;
    }

    // ---------- the expenses ----------

    private View listCard(List<Expense> expenses, String cur) {
        HandDrawnCard card = new HandDrawnCard(activity);
        if (expenses.isEmpty()) {
            card.addView(Sketch.label(activity, "No expenses this month.", 18, true, R.color.ink));
            card.addView(Sketch.label(activity, "Tap Add when you spend something.", 16, false, R.color.muted));
            return card;
        }
        String today = Validate.localDate();
        String lastDay = null;
        for (Expense e : expenses) {
            if (!e.spentOn.equals(lastDay)) {
                card.addView(Sketch.label(activity, DateLabels.dueLabel(activity, e.spentOn, today), 18, true, R.color.ink),
                        rowParams(lastDay == null ? 0 : 14));
                lastDay = e.spentOn;
            }
            card.addView(expenseRow(e, cur), rowParams(4));
        }
        return card;
    }

    private View expenseRow(Expense e, String cur) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Sketch.label(activity, e.note != null ? e.note : e.category, 16, false, R.color.ink));
        if (e.note != null) text.addView(Sketch.label(activity, e.category, 14, false, R.color.muted));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(Sketch.label(activity, MoneyFormat.money(e.amountMinor, cur), 16, true, R.color.ink));
        android.widget.TextView bin = Sketch.label(activity, "🗑️", 22, false, R.color.ink);
        bin.setContentDescription("Delete " + (e.note != null ? e.note : e.category) + " " + MoneyFormat.money(e.amountMinor, cur));
        bin.setPadding(Sketch.dp(activity, 12), Sketch.dp(activity, 8), Sketch.dp(activity, 6), Sketch.dp(activity, 8));
        bin.setOnClickListener(v -> actions.deleteWithUndo(e));
        row.addView(bin);
        return row;
    }

    /** Small helper so the month title can sit in the middle of the arrows. */
    private static final class TextViewHolder {
        static void centered(LinearLayout parent, android.widget.TextView t) {
            t.setGravity(Gravity.CENTER);
            parent.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }
}

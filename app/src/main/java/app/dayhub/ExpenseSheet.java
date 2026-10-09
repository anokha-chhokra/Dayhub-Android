package app.dayhub;

import android.app.Activity;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.dayhub.data.DayHubData;
import app.dayhub.data.Model;
import app.dayhub.data.Validate;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Currency;
import java.util.Locale;

/** The sheet for adding an expense: amount, category, an optional note, and the date and time. */
public final class ExpenseSheet {
    private final Activity activity;
    private final Overlays overlays;
    private final ExpenseActions actions;
    private final String today = Validate.localDate();

    private final HandDrawnField amount;
    private final HandDrawnField note;
    private final LinearLayout categoryRow;
    private final TextView dateText;
    private final TextView timeText;
    private final TextView error;
    private final String symbol;
    private String category = "Food";
    private String date;
    private String time;
    private BottomSheet sheet;

    public static void open(Activity activity, DayHubData data, Overlays overlays, ExpenseActions actions, String day) {
        new ExpenseSheet(activity, data, overlays, actions, day).show();
    }

    private ExpenseSheet(Activity activity, DayHubData data, Overlays overlays, ExpenseActions actions, String day) {
        this.activity = activity;
        this.overlays = overlays;
        this.actions = actions;
        this.date = day;
        this.time = day.equals(today) ? DateLabels.nowHHMM() : null;

        String code = data.getSettings().currency;
        String sym;
        try {
            sym = Currency.getInstance(code).getSymbol(code.equals("INR") ? Locale.forLanguageTag("en-IN") : Locale.getDefault());
        } catch (IllegalArgumentException e) {
            sym = code;
        }
        symbol = sym;

        amount = new HandDrawnField(activity, "0");
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setFilters(new InputFilter[] {new InputFilter.LengthFilter(16)});
        note = new HandDrawnField(activity, "Optional");
        note.setSingleLine(true);
        note.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        note.setFilters(new InputFilter[] {new InputFilter.LengthFilter(120)});
        categoryRow = new LinearLayout(activity);
        categoryRow.setOrientation(LinearLayout.HORIZONTAL);
        dateText = Sketch.label(activity, "", 16, false, R.color.ink);
        timeText = Sketch.label(activity, "", 16, false, R.color.ink);
        error = Sketch.label(activity, "", 15, false, R.color.red);
        error.setVisibility(View.GONE);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private TextView heading(String s) {
        return Sketch.label(activity, s, 17, true, R.color.ink);
    }

    private void show() {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(heading("Amount (" + symbol + ")"));
        body.addView(amount, rowParams(6));

        body.addView(heading("Category"), rowParams(14));
        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(categoryRow);
        body.addView(scroll, rowParams(6));
        drawCategories();

        body.addView(heading("Note"), rowParams(14));
        body.addView(note, rowParams(6));

        body.addView(heading("Date"), rowParams(14));
        body.addView(dateText, rowParams(4));
        HandDrawnButton changeDate = new HandDrawnButton(activity, "Change date", false);
        changeDate.setOnClickListener(v -> DateTimePickers.pickDate(overlays, activity, LocalDate.parse(date), d -> {
            date = d.toString();
            time = date.equals(today) ? DateLabels.nowHHMM() : null; // we only know the time for today
            drawWhen();
        }));
        body.addView(changeDate, rowParams(6));

        body.addView(heading("Time (optional)"), rowParams(14));
        body.addView(timeText, rowParams(4));
        LinearLayout timeButtons = new LinearLayout(activity);
        timeButtons.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton setTime = new HandDrawnButton(activity, "Set time", false);
        setTime.setOnClickListener(v -> DateTimePickers.pickTime(overlays, activity,
                time != null ? LocalTime.parse(time) : LocalTime.now(), t -> {
                    time = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
                    drawWhen();
                }));
        HandDrawnButton noTime = new HandDrawnButton(activity, "No time", false);
        noTime.setOnClickListener(v -> {
            time = null;
            drawWhen();
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        timeButtons.addView(setTime, gap);
        timeButtons.addView(noTime);
        body.addView(timeButtons, rowParams(6));

        body.addView(error, rowParams(10));
        HandDrawnButton save = new HandDrawnButton(activity, "Add expense", true);
        save.setOnClickListener(v -> save());
        body.addView(save, rowParams(16));

        drawWhen();
        sheet = overlays.sheet("Add expense", body);
        Keyboard.showFor(amount);
    }

    private void drawCategories() {
        categoryRow.removeAllViews();
        for (String c : Model.CATEGORIES) {
            HandDrawnButton chip = new HandDrawnButton(activity, c, c.equals(category));
            chip.setOnClickListener(v -> {
                category = c;
                drawCategories();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Sketch.dp(activity, 10);
            categoryRow.addView(chip, lp);
        }
    }

    private void drawWhen() {
        dateText.setText(DateLabels.dueLabel(activity, date, today));
        timeText.setText(time == null ? "No time" : time);
    }

    private void save() {
        error.setVisibility(View.GONE);
        String message;
        try {
            JSONObject body = new JSONObject().put("amount", amount.getText().toString()).put("category", category)
                    .put("note", note.getText().toString()).put("spentOn", date)
                    .put("time", time == null ? JSONObject.NULL : time).put("today", today);
            message = actions.add(body);
        } catch (JSONException e) {
            message = "Could not save the expense";
        }
        if (message != null) {
            error.setText(message);
            error.setVisibility(View.VISIBLE);
            return;
        }
        sheet.dismiss();
    }
}

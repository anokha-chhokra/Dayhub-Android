package app.dayhub;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.ExpenseDetector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Looks like spending" box shown under a journal note: as the text changes, amounts found in it
 * are offered as ticked "Add as expense" lines. Call {@link #schedule} as the person types;
 * {@link #selected} gives the ticked ones in the shape the journal expects.
 */
public final class ExpenseSuggestionsView extends LinearLayout {
    private static final long DELAY_MS = 450;

    private final Activity activity;
    private final DayHubData data;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<String> skipped = new HashSet<>();
    private List<ExpenseDetector.Suggestion> suggestions = new ArrayList<>();
    private String text = "";
    private String day;
    private String currency = "INR";
    private Integer entryId;
    private final Runnable detect = this::detectNow;

    public ExpenseSuggestionsView(Activity activity, DayHubData data) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.day = app.dayhub.data.Validate.localDate();
        setOrientation(VERTICAL);
    }

    /** The day the note is for (for "on Friday"), the currency to show, and the entry being edited, if any. */
    public void configure(String day, String currency, Integer entryId) {
        this.day = day;
        this.currency = currency;
        this.entryId = entryId;
    }

    public void schedule(String newText) {
        text = newText;
        handler.removeCallbacks(detect);
        if (newText.trim().isEmpty()) {
            reset();
            return;
        }
        handler.postDelayed(detect, DELAY_MS);
    }

    public void reset() {
        handler.removeCallbacks(detect);
        suggestions = new ArrayList<>();
        skipped.clear();
        draw();
    }

    private void detectNow() {
        try {
            suggestions = ExpenseDetector.suggest(data, text, day, entryId);
        } catch (DataError e) {
            return; // detection is a bonus; ignore failures
        }
        Set<String> keys = new HashSet<>();
        for (ExpenseDetector.Suggestion s : suggestions) keys.add(key(s));
        skipped.retainAll(keys);
        draw();
    }

    private static String key(ExpenseDetector.Suggestion s) {
        return s.amountMinor + "|" + s.note + "|" + s.daysAgo;
    }

    private static String whenLabel(int daysAgo) {
        return daysAgo == 0 ? "" : daysAgo == 1 ? " · yesterday" : " · " + daysAgo + " days earlier";
    }

    private void draw() {
        removeAllViews();
        if (suggestions.isEmpty()) return;
        addView(Sketch.label(activity, "Looks like spending", 14, true, R.color.muted));
        for (ExpenseDetector.Suggestion s : suggestions) {
            String line = "Add as expense: " + MoneyFormat.money(s.amountMinor, currency) + " · " + s.category
                    + (s.note.isEmpty() ? "" : " · " + s.note) + whenLabel(s.daysAgo);
            HandDrawnCheckRow row = new HandDrawnCheckRow(activity, line);
            row.setChecked(!skipped.contains(key(s)));
            row.setOnChange(() -> {
                if (row.isChecked()) skipped.remove(key(s));
                else skipped.add(key(s));
            });
            addView(row);
        }
    }

    /** The ticked suggestions as [{amount, category, note, daysAgo}]. */
    public JSONArray selected() throws JSONException {
        JSONArray out = new JSONArray();
        for (ExpenseDetector.Suggestion s : suggestions) {
            if (skipped.contains(key(s))) continue;
            out.put(new JSONObject().put("amount", MoneyFormat.decimal(s.amountMinor))
                    .put("category", s.category).put("note", s.note).put("daysAgo", s.daysAgo));
        }
        return out;
    }
}

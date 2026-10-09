package app.dayhub;

import android.app.Activity;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Expense;

import org.json.JSONException;
import org.json.JSONObject;

/** Feature 17: adding and deleting expenses. Each change is saved at once and the screen redraws. */
public final class ExpenseActions {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;

    public ExpenseActions(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged) {
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
    }

    /** Adds an expense from a body (amount, category, note, spentOn, time). Returns an error message, or null. */
    public String add(JSONObject body) {
        try {
            data.addExpense(body);
            data.commit();
        } catch (DataError e) {
            return e.getMessage();
        }
        overlays.toast("Expense added");
        onChanged.run();
        return null;
    }

    /** Deletes an expense and offers Undo, which adds it back. */
    public void deleteWithUndo(Expense expense) {
        try {
            data.deleteExpense(expense.id);
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            onChanged.run();
            return;
        }
        onChanged.run();
        overlays.toast("Expense deleted", () -> restore(expense));
    }

    private void restore(Expense e) {
        try {
            JSONObject body = new JSONObject().put("amount", MoneyFormat.decimal(e.amountMinor)).put("category", e.category)
                    .put("note", e.note == null ? JSONObject.NULL : e.note).put("spentOn", e.spentOn)
                    .put("time", e.time == null ? JSONObject.NULL : e.time);
            data.addExpense(body);
            data.commit();
        } catch (DataError err) {
            overlays.toast(err.getMessage());
        } catch (JSONException err) {
            overlays.toast("Could not bring the expense back");
        }
        onChanged.run();
    }

    /** Opens the add-expense sheet, starting on {@code day}. */
    public void openSheet(String day) {
        ExpenseSheet.open(activity, data, overlays, this, day);
    }
}

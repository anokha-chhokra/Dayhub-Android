package app.dayhub;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Feature 19: the Settings screen. You (name, currency, monthly budget), Reminders (the daily
 * journal reminder and whether to show system notifications), Focus, and Your data (backup, restore,
 * exports, and running the first-run setup again).
 */
public final class SettingsScreen extends ScrollView {
    private final MainActivity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final ActivityResults results;
    private final DataTransfer transfer;
    private final Runnable onChanged;
    private final LinearLayout column;
    private final BackupFile backupFile;

    // What is being edited in the You card (kept between redraws, so a half-edited form survives).
    private HandDrawnField name;
    private HandDrawnField budget;
    private String currency;
    private String reminder;

    public SettingsScreen(MainActivity activity, DayHubData data, Overlays overlays, ActivityResults results,
                          DataTransfer transfer, Runnable onChanged) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.results = results;
        this.transfer = transfer;
        this.onChanged = onChanged;
        this.backupFile = new BackupFile(activity, new BackupPrefs(activity));
        setFillViewport(true);
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private View heading(String s) {
        return Sketch.label(activity, s, 17, true, R.color.ink);
    }

    private View hint(String s) {
        return Sketch.label(activity, s, 14, false, R.color.muted);
    }

    /** Redraws every card from the stored settings. */
    public void refresh() {
        Settings s = data.getSettings();
        currency = s.currency;
        reminder = s.journalReminder.isEmpty() ? null : s.journalReminder;
        column.removeAllViews();
        column.addView(Sketch.label(activity, "Settings", 32, true, R.color.ink));
        column.addView(youCard(s), rowParams(14));
        column.addView(remindersCard(s), rowParams(14));
        column.addView(new BackupCard(activity, data, overlays, results, transfer, backupFile, onChanged), rowParams(14));
        column.addView(focusCard(), rowParams(14));
        column.addView(dataCard(), rowParams(14));
        column.addView(Sketch.label(activity, "Day Hub for Android " + versionName(), 14, false, R.color.muted), rowParams(14));
    }

    private String versionName() {
        try {
            return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    // ---------- You ----------

    private View youCard(Settings s) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "You", 22, true, R.color.ink));

        card.addView(heading("Name"), rowParams(12));
        name = new HandDrawnField(activity, "Your name");
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        name.setFilters(new InputFilter[] {new InputFilter.LengthFilter(40)});
        name.setText(s.name);
        card.addView(name, rowParams(6));

        card.addView(heading("Currency"), rowParams(14));
        List<String[]> options = new ArrayList<>();
        boolean known = false;
        for (String[] c : SetupWizard.CURRENCIES) if (c[0].equals(s.currency)) known = true;
        if (!known) options.add(new String[] {s.currency, s.currency});
        for (String[] c : SetupWizard.CURRENCIES) options.add(c);
        List<HandDrawnCheckRow> rows = new ArrayList<>();
        for (String[] c : options) {
            HandDrawnCheckRow row = new HandDrawnCheckRow(activity, c[1]);
            row.setChecked(c[0].equals(currency));
            row.setOnChange(() -> {
                currency = c[0];
                for (int i = 0; i < rows.size(); i++) rows.get(i).setChecked(options.get(i)[0].equals(currency));
            });
            rows.add(row);
            card.addView(row, rowParams(0));
        }

        card.addView(heading("Monthly budget"), rowParams(14));
        budget = new HandDrawnField(activity, "No budget");
        budget.setSingleLine(true);
        budget.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        long m = s.monthlyBudgetMinor;
        budget.setText(m == 0 ? "" : m % 100 == 0 ? String.valueOf(m / 100) : MoneyFormat.decimal(m));
        card.addView(budget, rowParams(6));

        HandDrawnButton save = new HandDrawnButton(activity, "Save", true);
        save.setOnClickListener(v -> saveYou());
        card.addView(save, rowParams(14));
        return card;
    }

    private void saveYou() {
        try {
            JSONObject body = new JSONObject().put("name", name.getText().toString()).put("currency", currency)
                    .put("monthlyBudget", budget.getText().toString().trim());
            data.updateSettings(body);
            data.commit();
            overlays.toast("Saved");
            onChanged.run();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            refresh(); // show what is really saved
        } catch (JSONException e) {
            overlays.toast("Could not save");
        }
    }

    // ---------- Reminders ----------

    private View remindersCard(Settings s) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Reminders", 22, true, R.color.ink));

        card.addView(heading("Daily journal reminder"), rowParams(12));
        android.widget.TextView time = Sketch.label(activity, reminder == null ? "None" : reminder, 16, false, R.color.ink);
        card.addView(time, rowParams(4));
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton set = new HandDrawnButton(activity, "Set time", false);
        set.setOnClickListener(v -> DateTimePickers.pickTime(overlays, activity,
                reminder != null ? LocalTime.parse(reminder) : LocalTime.of(20, 0), t -> {
                    reminder = String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
                    time.setText(reminder);
                }));
        HandDrawnButton none = new HandDrawnButton(activity, "None", false);
        none.setOnClickListener(v -> {
            reminder = null;
            time.setText("None");
        });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        buttons.addView(set, gap);
        buttons.addView(none);
        card.addView(buttons, rowParams(8));
        card.addView(hint("Leave it as None for no reminder. Habit reminders are set on each habit."), rowParams(6));

        HandDrawnCheckRow notifications = new HandDrawnCheckRow(activity, "Also show system notifications");
        notifications.setChecked(s.notifications);
        notifications.setOnChange(() -> setNotifications(notifications));
        card.addView(notifications, rowParams(10));

        HandDrawnButton save = new HandDrawnButton(activity, "Save", true);
        save.setOnClickListener(v -> saveReminder());
        card.addView(save, rowParams(12));
        return card;
    }

    private void saveReminder() {
        try {
            data.updateSettings(new JSONObject().put("journalReminder", reminder == null ? "" : reminder));
            data.commit();
            overlays.toast("Saved");
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not save");
        }
    }

    /** Turning notifications on asks Android's permission first (needed from Android 13). */
    private void setNotifications(HandDrawnCheckRow row) {
        if (!row.isChecked()) {
            storeNotifications(false);
            return;
        }
        if (Build.VERSION.SDK_INT < 33) {
            storeNotifications(true);
            return;
        }
        results.requestPermission(Manifest.permission.POST_NOTIFICATIONS, granted -> {
            if (granted) {
                storeNotifications(true);
            } else {
                row.setChecked(false);
                overlays.toast("Notifications are blocked for Day Hub. You can allow them in the phone’s settings.");
            }
        });
    }

    private void storeNotifications(boolean on) {
        try {
            data.updateSettings(new JSONObject().put("notifications", on));
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (JSONException e) {
            overlays.toast("Could not save");
        }
    }

    // ---------- Focus ----------

    private View focusCard() {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Focus mode", 22, true, R.color.ink));
        card.addView(hint("While a focus timer runs, only Day Hub, Messages and WhatsApp can be used. Anything else you "
                + "open is sent straight back to Day Hub. Incoming calls and the notification shade still work. "
                + "If your phone will not let Day Hub jump back, a Focus mode page covers the other app instead, with "
                + "a Back to Day Hub button. The timer always ends by itself, and holding the end button for 8 seconds "
                + "ends it early."), rowParams(8));
        boolean guard = FocusController.guardEnabled(activity);
        card.addView(Sketch.label(activity, guard ? "✅ Focus guard is on." : "⚠️ Focus guard is off, so focus mode cannot start.",
                16, false, R.color.ink), rowParams(12));
        boolean overlay = FocusController.overlayGranted(activity);
        card.addView(hint(overlay ? "✅ Display over other apps is allowed (most reliable)."
                : "Optional: allow Display over other apps so Day Hub can come back faster on phones that delay it."), rowParams(6));

        HandDrawnButton accessibility = new HandDrawnButton(activity, "Accessibility settings", false);
        accessibility.setOnClickListener(v -> openSystemScreen(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        card.addView(accessibility, rowParams(12));
        if (!overlay) {
            HandDrawnButton display = new HandDrawnButton(activity, "Allow display over apps", false);
            display.setOnClickListener(v -> openSystemScreen(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
            card.addView(display, rowParams(10));
        }
        HandDrawnButton again = new HandDrawnButton(activity, "Check again", false);
        again.setOnClickListener(v -> refresh());
        card.addView(again, rowParams(10));
        card.addView(hint("If Android will not let you turn the guard on: on Android 13 and newer, an app installed from "
                + "a file is blocked from accessibility until you allow it. Settings, Apps, Day Hub, the three-dot menu "
                + "at the top right, Allow restricted settings. Then open Accessibility, find Day Hub focus guard and "
                + "turn it on."), rowParams(12));
        return card;
    }

    private void openSystemScreen(String action) {
        openSystemScreen(action, null);
    }

    private void openSystemScreen(String action, Uri data) {
        try {
            Intent i = new Intent(action);
            if (data != null) i.setData(data);
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            overlays.toast("This phone has no screen for that.");
        }
    }

    // ---------- Your data ----------

    private View dataCard() {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Your data", 22, true, R.color.ink));
        card.addView(hint("Your data is saved inside this app on this phone and is never uploaded. "
                + "Save a backup now and then, somewhere safe."), rowParams(8));
        card.addView(buttonRow("Backup (JSON)", true, transfer::exportBackup,
                "Restore…", false, () -> transfer.restoreFromFile(onChanged)), rowParams(14));
        card.addView(buttonRow("Expenses (CSV)", false, () -> transfer.exportExpenses(null),
                "Journal (Markdown)", false, transfer::exportJournal), rowParams(10));
        HandDrawnButton again = new HandDrawnButton(activity, "Run setup again", false);
        again.setOnClickListener(v -> SetupWizard.run(activity, activity.content(), transfer, data, onChanged));
        card.addView(again, rowParams(10));
        return card;
    }

    private View buttonRow(String firstLabel, boolean firstPrimary, Runnable first,
                           String secondLabel, boolean secondPrimary, Runnable second) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        HandDrawnButton a = new HandDrawnButton(activity, firstLabel, firstPrimary);
        a.setOnClickListener(v -> first.run());
        HandDrawnButton b = new HandDrawnButton(activity, secondLabel, secondPrimary);
        b.setOnClickListener(v -> second.run());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        row.addView(a, gap);
        row.addView(b);
        return row;
    }
}

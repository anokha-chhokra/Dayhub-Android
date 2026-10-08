package app.dayhub;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import app.dayhub.data.DamagedDataException;
import app.dayhub.data.DamagedDataRecovery;
import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;

import java.io.IOException;

/**
 * Feature 6: shown instead of the app when the saved data cannot be used. Nothing is deleted or
 * overwritten until the person chooses: they can save a copy of the unreadable data, restore a
 * backup file, or start fresh (the unreadable copy is kept under another name).
 */
public final class DamagedDataScreen extends ScrollView {
    public interface Listener {
        /** The person recovered; carry on with this data. */
        void onRecovered(DayHubData data);
    }

    private final Activity activity;
    private final Overlays overlays;
    private final ActivityResults results;
    private final DataStore store;
    private final DamagedDataException error;
    private final Listener listener;

    public DamagedDataScreen(Activity activity, Overlays overlays, ActivityResults results,
                             DataStore store, DamagedDataException error, Listener listener) {
        super(activity);
        this.activity = activity;
        this.overlays = overlays;
        this.results = results;
        this.store = store;
        this.error = error;
        this.listener = listener;
        setFillViewport(true);

        int pad = Sketch.dp(activity, 20);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);
        column.addView(Sketch.label(activity, "Day Hub", 32, true, R.color.ink));

        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Your saved data could not be read", 22, true, R.color.ink));
        card.addView(spaced(Sketch.label(activity, error.getMessage(), 16, false, R.color.ink), 10));
        card.addView(spaced(Sketch.label(activity,
                "Nothing has been deleted. Save a copy first, so it can be repaired later.",
                15, false, R.color.muted), 10));
        if (error.raw != null && !error.raw.isEmpty()) {
            card.addView(button("Save a copy of the saved data", false, v -> saveCopy()));
        }
        card.addView(button("Restore from a backup file", false, v -> pickBackup()));
        card.addView(button("Start fresh", true, v -> startFresh()));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = Sketch.dp(activity, 16);
        column.addView(card, cardParams);

        column.addView(spaced(Sketch.label(activity,
                "Start fresh keeps the unreadable copy on this phone under another name and opens an "
                        + "empty Day Hub. You can restore a backup afterwards.",
                14, false, R.color.muted), 14));
        addView(column);
    }

    private View spaced(View v, int topDp) {
        v.setLayoutParams(rowParams(topDp));
        return v;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private HandDrawnButton button(String label, boolean primary, OnClickListener onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, label, primary);
        b.setLayoutParams(rowParams(14));
        b.setOnClickListener(onClick);
        return b;
    }

    // ---------- save a copy ----------

    private void saveCopy() {
        results.launch(DocumentFiles.createIntent("day-hub-damaged-data.json", "application/json"),
                (resultCode, data) -> {
                    Uri uri = pickedUri(resultCode, data);
                    if (uri == null) return;
                    try {
                        DocumentFiles.writeText(activity, uri, error.raw);
                        overlays.toast("Saved a copy");
                    } catch (IOException e) {
                        overlays.toast("Could not save the copy");
                    }
                });
    }

    // ---------- restore from a backup file ----------

    private void pickBackup() {
        results.launch(DocumentFiles.pickIntent(), (resultCode, data) -> {
            Uri uri = pickedUri(resultCode, data);
            if (uri == null) return;
            String text;
            try {
                text = DocumentFiles.readText(activity, uri);
            } catch (IOException e) {
                overlays.toast("Could not read that file");
                return;
            }
            String summary;
            try {
                summary = DamagedDataRecovery.inspect(text);
            } catch (DataError e) {
                overlays.toast(e.getMessage());
                return;
            }
            confirmRestore(text, summary);
        });
    }

    private void confirmRestore(String backupText, String summary) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Sketch.label(activity, "This backup has " + summary + ".", 16, false, R.color.ink));
        body.addView(spaced(Sketch.label(activity,
                "Restoring replaces everything now saved on this device. The unreadable copy is kept.",
                15, false, R.color.red), 10));

        BottomSheet[] sheet = new BottomSheet[1];
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        HandDrawnButton replace = new HandDrawnButton(activity, "Replace my data", true);
        replace.setOnClickListener(v -> {
            sheet[0].dismiss();
            restore(backupText);
        });
        HandDrawnButton cancel = new HandDrawnButton(activity, "Cancel", false);
        cancel.setOnClickListener(v -> sheet[0].dismiss());
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        row.addView(replace, gap);
        row.addView(cancel);
        body.addView(row, rowParams(14));
        sheet[0] = overlays.sheet("Restore from backup", body);
    }

    private void restore(String backupText) {
        try {
            DamagedDataRecovery.Result r = DamagedDataRecovery.restore(store, backupText);
            listener.onRecovered(r.data);
            overlays.toast("Backup restored");
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        } catch (IOException e) {
            overlays.toast("Could not restore. Nothing was changed.");
        }
    }

    // ---------- start fresh ----------

    private void startFresh() {
        try {
            DamagedDataRecovery.Result r = DamagedDataRecovery.startFresh(store);
            listener.onRecovered(r.data);
            overlays.toast(r.keptCopy == null
                    ? "Started fresh" : "Started fresh. The old copy was kept as " + r.keptCopy.getName());
        } catch (IOException e) {
            overlays.toast("Could not start fresh. Nothing was changed.");
        }
    }

    private static Uri pickedUri(int resultCode, Intent data) {
        return resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
    }
}

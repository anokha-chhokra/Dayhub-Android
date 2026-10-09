package app.dayhub;

import android.app.Activity;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import app.dayhub.data.DataExport;
import app.dayhub.data.DayHubData;

import java.io.IOException;

/**
 * Feature 20: the Backup file card in Settings. Choose one file (a new one, or an existing one) in
 * Downloads, Documents, Google Drive or anywhere else; Back up now overwrites that same file with
 * the whole of Day Hub, so there is only ever one file and it is always the latest.
 */
public final class BackupCard extends HandDrawnCard {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final ActivityResults results;
    private final DataTransfer transfer;
    private final BackupFile file;
    private final Runnable onChanged;

    public BackupCard(Activity activity, DayHubData data, Overlays overlays, ActivityResults results,
                      DataTransfer transfer, BackupFile file, Runnable onChanged) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.results = results;
        this.transfer = transfer;
        this.file = file;
        this.onChanged = onChanged;
        draw();
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    private static String kb(long bytes) {
        return Math.max(1, Math.round(bytes / 1024f)) + " KB";
    }

    private HandDrawnButton button(String label, boolean primary, Runnable onClick) {
        HandDrawnButton b = new HandDrawnButton(activity, label, primary);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private void draw() {
        removeAllViews();
        addView(Sketch.label(activity, "Backup file", 22, true, R.color.ink));
        addView(Sketch.label(activity,
                "Day Hub keeps your data inside this app. A backup file is a second copy in a place you choose "
                        + "(Downloads, Documents, Google Drive). Every backup overwrites the file, so there is always "
                        + "one file and it is always the latest.", 14, false, R.color.muted), rowParams(8));

        BackupPrefs prefs = file.prefs();
        if (!file.configured()) {
            addView(Sketch.label(activity, "No backup file chosen yet.", 16, false, R.color.ink), rowParams(12));
            addView(button("Create backup file…", true, () -> choose(true)), rowParams(12));
            addView(button("Use an existing file…", false, () -> choose(false)), rowParams(10));
            return;
        }

        addView(Sketch.label(activity, "Backing up to " + (prefs.name() == null ? "your backup file" : prefs.name()),
                16, true, R.color.ink), rowParams(12));
        if (!prefs.lastOk()) {
            addView(Sketch.label(activity, "Last backup failed: " + (prefs.lastError().isEmpty() ? "unknown error" : prefs.lastError())
                    + " Choose the file again.", 14, false, R.color.red), rowParams(4));
        } else if (prefs.lastAt() > 0) {
            addView(Sketch.label(activity, "Last backup " + DateUtils.formatDateTime(activity, prefs.lastAt(),
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH)
                    + " · " + kb(prefs.bytes()), 14, false, R.color.muted), rowParams(4));
        } else {
            addView(Sketch.label(activity, "Not backed up yet.", 14, false, R.color.muted), rowParams(4));
        }

        addView(button("Back up now", true, this::backUp), rowParams(12));
        addView(button("Restore from this file", false, () -> transfer.restoreFromUri(file.uri(), prefs.name(), onChanged)), rowParams(10));
        addView(button("New file…", false, () -> choose(true)), rowParams(10));
        addView(button("Use an existing file…", false, () -> choose(false)), rowParams(10));
        addView(button("Stop using this file", false, () -> {
            file.forget();
            overlays.toast("No longer backing up to that file");
            draw();
        }), rowParams(10));
    }

    // ---------- choosing the file ----------

    private void choose(boolean create) {
        results.launch(create ? DocumentFiles.createBackupIntent() : DocumentFiles.chooseExistingIntent(), (resultCode, intent) -> {
            if (resultCode != Activity.RESULT_OK || intent == null || intent.getData() == null) return;
            Uri uri = intent.getData();
            String name = file.displayName(uri);
            long existing = file.sizeOf(uri);
            try {
                file.adopt(uri, name, existing);
            } catch (IOException e) {
                overlays.toast(e.getMessage());
                return;
            }
            if (existing > 0) askAboutExisting(name, existing);
            else backUp();
        });
    }

    /** The chosen file already holds something: restore it, or knowingly replace it. */
    private void askAboutExisting(String name, long bytes) {
        boolean empty = data.isBlank();
        android.widget.LinearLayout body = new android.widget.LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Sketch.label(activity, "“" + name + "” is " + kb(bytes) + " big. Day Hub overwrites the backup "
                + "file with your current data each time it backs up.", 16, false, R.color.ink));
        body.addView(empty
                ? Sketch.label(activity, "Day Hub is empty right now, so overwriting would erase that backup. Restore it first.",
                        15, false, R.color.red)
                : Sketch.label(activity, "If it holds data you have not loaded into Day Hub yet, restore it first.",
                        15, false, R.color.muted), rowParams(10));

        boolean[] chosen = new boolean[1];
        BottomSheet[] sheet = new BottomSheet[1];
        HandDrawnButton restore = button("Restore it first", true, () -> {
            chosen[0] = true;
            sheet[0].dismiss();
            transfer.restoreFromUri(file.uri(), name, onChanged);
        });
        body.addView(restore, rowParams(14));
        if (!empty) {
            body.addView(button("Overwrite it", false, () -> {
                chosen[0] = true;
                sheet[0].dismiss();
                backUp();
            }), rowParams(10));
        }
        body.addView(button("Cancel", false, () -> sheet[0].dismiss()), rowParams(10));
        sheet[0] = overlays.sheet("This file already has a backup", body);
        sheet[0].setOnDismiss(() -> {
            if (!chosen[0]) { // cancelled: do not keep a file that was never agreed to
                file.forget();
                draw();
            }
        });
    }

    // ---------- backing up ----------

    private void backUp() {
        try {
            BackupFile.Written w = file.write(DataExport.backup(data).text);
            overlays.toast("Backed up (" + kb(w.bytes) + ")");
        } catch (IOException e) {
            overlays.toast(e.getMessage());
        }
        draw();
    }
}

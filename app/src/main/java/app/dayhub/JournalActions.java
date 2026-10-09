package app.dayhub;

import android.app.Activity;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HabitProgress;
import app.dayhub.data.Model.Entry;

import org.json.JSONObject;

/**
 * Feature 16: saving and deleting journal entries. Saving also records any badge the entry earns
 * and any spending that was ticked, and tells the screen to redraw.
 */
public final class JournalActions {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;

    public JournalActions(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged) {
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
    }

    static String badgeText(HabitProgress.Synced progress) {
        if (progress.newBadges.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(". New badge: ");
        for (int i = 0; i < progress.newBadges.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(progress.newBadges.get(i).icon).append(' ').append(progress.newBadges.get(i).name);
        }
        return sb.toString();
    }

    /**
     * Adds an entry ({@code editing == null}) or edits one, from a body (see
     * {@link DayHubData#createEntry}). Returns an error message to show in the sheet, or null on success.
     */
    public String save(Entry editing, JSONObject body) {
        try {
            HabitProgress progress = new HabitProgress(data);
            int before = editing == null ? 0 : data.listEntryExpenses(editing.id).size();
            HabitProgress.EntryResult result = editing == null
                    ? progress.createEntry(body) : progress.updateEntry(editing.id, body);
            data.commit();
            int added = data.listEntryExpenses(result.entry.id).size() - before;
            overlays.toast((added > 0 ? "Saved. " + added + " expense" + (added > 1 ? "s" : "") + " added too." : "Saved")
                    + badgeText(result.progress));
        } catch (DataError e) {
            return e.getMessage();
        }
        onChanged.run();
        return null;
    }

    public void delete(Entry entry) {
        try {
            data.deleteEntry(entry.id);
            data.commit();
            overlays.toast("Entry deleted");
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
        onChanged.run();
    }

    /** Opens the add (entry == null) or edit sheet. A draft pre-fills a new entry. */
    public void openSheet(Entry entry, String day, String draftText, Integer draftMood) {
        EntrySheet.open(activity, data, overlays, this, entry, day, draftText, draftMood);
    }
}

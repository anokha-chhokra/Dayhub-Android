package app.dayhub;

import android.app.Activity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * The "Restore from backup" sheet: says what is in the backup and asks before it replaces what is
 * on the phone. Shared by the damaged-data screen and the normal restore.
 */
public final class RestoreConfirm {
    private RestoreConfirm() {}

    /**
     * @param summary   what the backup holds, e.g. "3 journal entries, 5 tasks, ..."
     * @param warning   what restoring will do to the current data
     * @param onReplace runs when the person confirms
     */
    public static void show(Activity activity, Overlays overlays, String summary, String warning,
                            Runnable onReplace) {
        show(activity, overlays, "This backup", summary, warning, onReplace);
    }

    /** @param subject who "has" the summary: "This backup", or a file name in quotes */
    public static void show(Activity activity, Overlays overlays, String subject, String summary, String warning,
                            Runnable onReplace) {
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(Sketch.label(activity, subject + " has " + summary + ".", 16, false, R.color.ink));

        LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warningParams.topMargin = Sketch.dp(activity, 10);
        body.addView(Sketch.label(activity, warning, 15, false, R.color.red), warningParams);

        BottomSheet[] sheet = new BottomSheet[1];
        HandDrawnButton replace = new HandDrawnButton(activity, "Replace my data", true);
        replace.setOnClickListener(v -> {
            sheet[0].dismiss();
            onReplace.run();
        });
        HandDrawnButton cancel = new HandDrawnButton(activity, "Cancel", false);
        cancel.setOnClickListener(v -> sheet[0].dismiss());

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(activity, 12);
        row.addView(replace, gap);
        row.addView(cancel);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Sketch.dp(activity, 14);
        body.addView(row, rowParams);

        sheet[0] = overlays.sheet("Restore from backup", body);
    }
}

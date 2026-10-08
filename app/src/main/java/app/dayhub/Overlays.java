package app.dayhub;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/**
 * The layer above the screens: shows bottom sheets and Undo toasts over the full window (including
 * under the system bars) and handles back presses for them.
 */
public final class Overlays {
    private final ViewGroup host;
    private UndoToast currentToast;

    public Overlays(Activity activity) {
        this.host = activity.findViewById(android.R.id.content);
    }

    /** Shows a sheet with an optional title and the given body. */
    public BottomSheet sheet(CharSequence title, View body) {
        BottomSheet sheet = new BottomSheet(host.getContext(), title, body);
        sheet.show(host, bottomInset());
        return sheet;
    }

    public void toast(CharSequence message) {
        toast(message, null);
    }

    /** Shows a toast; if {@code undo} is non-null it gets an Undo button that runs it. */
    public void toast(CharSequence message, Runnable undo) {
        if (currentToast != null) currentToast.dismiss(false);
        UndoToast[] self = new UndoToast[1];
        self[0] = new UndoToast(host.getContext(), message, undo, () -> {
            if (currentToast == self[0]) currentToast = null;
        });
        currentToast = self[0];
        // Sit above the bottom navigation bar.
        self[0].show(host, bottomInset() + Sketch.dp(host.getContext(), 88));
    }

    /** Closes the top bottom sheet if one is open. Returns true when the back press was used. */
    public boolean handleBack() {
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            if (child instanceof BottomSheet) {
                ((BottomSheet) child).dismiss();
                return true;
            }
        }
        return false;
    }

    private int bottomInset() {
        WindowInsets insets = host.getRootWindowInsets();
        return insets == null ? 0 : EdgeToEdgeShell.insetsOf(insets)[3];
    }
}

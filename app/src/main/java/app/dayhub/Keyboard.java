package app.dayhub;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/** Brings up the on-screen keyboard for a field. */
public final class Keyboard {
    private Keyboard() {}

    /** Focuses the field and shows the keyboard once any opening animation has settled. */
    public static void showFor(View field) {
        field.postDelayed(() -> {
            if (!field.isAttachedToWindow()) return;
            field.requestFocus();
            InputMethodManager imm = (InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }, 300);
    }
}

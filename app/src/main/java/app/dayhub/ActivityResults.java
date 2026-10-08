package app.dayhub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.util.SparseArray;

/**
 * Launches system screens (the file picker, "save as") and hands each answer to the callback that
 * asked. The activity forwards its onActivityResult here.
 */
public final class ActivityResults {
    public interface Callback {
        void onResult(int resultCode, Intent data);
    }

    private final Activity activity;
    private final SparseArray<Callback> pending = new SparseArray<>();
    private int nextCode = 1000;

    public ActivityResults(Activity activity) {
        this.activity = activity;
    }

    @SuppressWarnings("deprecation")
    public void launch(Intent intent, Callback callback) {
        int code = nextCode++;
        pending.put(code, callback);
        try {
            activity.startActivityForResult(intent, code);
        } catch (ActivityNotFoundException e) {
            pending.remove(code);
            callback.onResult(Activity.RESULT_CANCELED, null);
        }
    }

    /** Returns true if the result was one this class asked for. */
    public boolean dispatch(int requestCode, int resultCode, Intent data) {
        Callback callback = pending.get(requestCode);
        if (callback == null) return false;
        pending.remove(requestCode);
        callback.onResult(resultCode, data);
        return true;
    }
}

package app.dayhub;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.SparseArray;

/**
 * Launches system screens (the file picker, "save as") and hands each answer to the callback that
 * asked. The activity forwards its onActivityResult here.
 */
public final class ActivityResults {
    public interface Callback {
        void onResult(int resultCode, Intent data);
    }

    /** The answer to a permission request. */
    public interface PermissionCallback {
        void onResult(boolean granted);
    }

    private final Activity activity;
    private final SparseArray<PermissionCallback> permissionPending = new SparseArray<>();
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

    /** Asks Android for a permission (or answers yes at once if it is already granted). */
    public void requestPermission(String permission, PermissionCallback callback) {
        if (Build.VERSION.SDK_INT < 23 || activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            callback.onResult(true);
            return;
        }
        int code = nextCode++;
        permissionPending.put(code, callback);
        activity.requestPermissions(new String[] {permission}, code);
    }

    /** Returns true if the answer was to a permission request this class made. */
    public boolean dispatchPermission(int requestCode, int[] grantResults) {
        PermissionCallback callback = permissionPending.get(requestCode);
        if (callback == null) return false;
        permissionPending.remove(requestCode);
        callback.onResult(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        return true;
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

package app.dayhub;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import app.dayhub.data.WriteVerifier;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Feature 20: the backup file. One file the person chose (Downloads, Documents, Google Drive...),
 * overwritten in place with the whole of Day Hub every time and read back to be sure, so there is always exactly one file
 * and it is always the latest. Android's file picker gives the app lasting access to that one file
 * only; nothing else on the phone is touched.
 */
public final class BackupFile {
    private final Context context;
    private final BackupPrefs prefs;

    public BackupFile(Context context, BackupPrefs prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
    }

    private ContentResolver resolver() {
        return context.getContentResolver();
    }

    public BackupPrefs prefs() {
        return prefs;
    }

    public boolean configured() {
        return prefs.uri() != null;
    }

    public Uri uri() {
        String s = prefs.uri();
        return s == null ? null : Uri.parse(s);
    }

    private static final int GRANT = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    /** Keeps lasting access to the chosen file and lets go of the previous one. */
    public void adopt(Uri uri, String name, long existingBytes) throws IOException {
        try {
            resolver().takePersistableUriPermission(uri, GRANT);
        } catch (SecurityException e) {
            throw new IOException("That place does not allow a lasting backup file. Choose one in Downloads or Documents.");
        }
        Uri old = uri();
        if (old != null && !old.equals(uri)) release(old);
        prefs.setFile(uri.toString(), name, existingBytes);
    }

    /** Stops using the file (the file itself is left alone). */
    public void forget() {
        Uri old = uri();
        if (old != null) release(old);
        prefs.clear();
    }

    private void release(Uri uri) {
        try {
            resolver().releasePersistableUriPermission(uri, GRANT);
        } catch (RuntimeException ignored) {
            // it was already gone
        }
    }

    public String displayName(Uri uri) {
        try (Cursor c = resolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {
            // some providers refuse; fall back to the last part of the address
        }
        String last = uri.getLastPathSegment();
        return last == null ? "backup file" : last.substring(last.lastIndexOf('/') + 1);
    }

    /** Size in bytes, or 0 if the provider does not say (a new file is empty). */
    public long sizeOf(Uri uri) {
        try (Cursor c = resolver().query(uri, new String[] {OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return Math.max(0, c.getLong(0));
        } catch (RuntimeException ignored) {
            // measured by reading instead
        }
        try (InputStream in = resolver().openInputStream(uri)) {
            if (in == null) return 0;
            long n = 0;
            byte[] buf = new byte[8192];
            for (int r; (r = in.read(buf)) > 0; ) {
                n += r;
                if (n > 50_000_000L) break;
            }
            return n;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    /** What a finished backup wrote. */
    public static final class Written {
        public final long bytes;
        public final long at;

        Written(long bytes, long at) {
            this.bytes = bytes;
            this.at = at;
        }
    }

    /**
     * Replaces the whole content of the backup file with {@code text} and remembers how it went. On
     * failure the reason is remembered too and an {@link IOException} says what to do.
     */
    public Written write(String text) throws IOException {
        Uri target = uri();
        if (target == null) throw new IOException("Choose a backup file first.");
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try {
            overwrite(target, bytes);
            if (!WriteVerifier.confirms(bytes, limit -> readBytes(target, limit), Thread::sleep)) {
                throw new IOException("Could not confirm that the backup file was saved. "
                        + "Try Back up now again, or choose the file again.");
            }
        } catch (IOException e) {
            prefs.failed(e.getMessage());
            throw e;
        }
        Written w = new Written(bytes.length, System.currentTimeMillis());
        prefs.succeeded(w.bytes, w.at);
        return w;
    }

    /** Reads up to {@code limit} bytes (plus one, so a caller can tell the file was cut). */
    public byte[] readBytes(Uri uri, long limit) throws IOException {
        InputStream in;
        try {
            in = resolver().openInputStream(uri);
        } catch (SecurityException e) {
            throw new IOException("Day Hub no longer has permission for that file. Choose it again.");
        } catch (FileNotFoundException e) {
            throw new IOException("That file is gone. Choose a file again.");
        }
        if (in == null) throw new IOException("Android could not open that file.");
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            long total = 0;
            for (int n; (n = src.read(buf)) > 0; ) {
                out.write(buf, 0, n);
                total += n;
                if (total > limit) break;
            }
            return out.toByteArray();
        }
    }

    /** Writes bytes over whatever the file held: "wt" truncates, and the length is set explicitly in case a provider ignores that. */
    private void overwrite(Uri uri, byte[] bytes) throws IOException {
        ParcelFileDescriptor pfd;
        try {
            pfd = resolver().openFileDescriptor(uri, "wt");
        } catch (SecurityException e) {
            throw new IOException("Day Hub no longer has permission for the backup file. Choose it again.");
        } catch (FileNotFoundException e) {
            throw new IOException("The backup file is gone. Choose a file again.");
        }
        if (pfd == null) throw new IOException("Android could not open the backup file.");
        try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pfd)) {
            out.write(bytes);
            out.flush();
            try {
                out.getChannel().truncate(bytes.length); // drop anything left from a longer old file
            } catch (IOException ignored) {
                // not seekable: the "wt" mode did the work
            }
            try {
                out.getFD().sync();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}

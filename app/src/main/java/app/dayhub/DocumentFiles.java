package app.dayhub;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Reads and writes text files the person chose through Android's system file screens. */
public final class DocumentFiles {
    /** Backups are far smaller than this; the cap just stops a wrong, huge file filling memory. */
    private static final int MAX_BYTES = 50 * 1024 * 1024;

    private DocumentFiles() {}

    /** The "open a file" screen. No storage permission is needed. */
    public static Intent pickIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"); // phones often label .json files as plain binary, so do not filter
    }

    /** The "save as" screen, suggesting a file name. */
    public static Intent createIntent(String fileName, String mimeType) {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType)
                .putExtra(Intent.EXTRA_TITLE, fileName);
    }

    private static final int KEEP = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;

    /** The "open a file" screen, asking for access that lasts, for choosing the backup file. */
    public static Intent chooseExistingIntent() {
        return pickIntent().addFlags(KEEP);
    }

    /** The "save as" screen for a new backup file, asking for access that lasts. */
    public static Intent createBackupIntent() {
        return createIntent("dayhub-backup.json", "application/json").addFlags(KEEP);
    }

    public static String readText(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open that file");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new IOException("That file is too big to be a Day Hub backup");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Writes the text, replacing anything already in the file. */
    public static void writeText(Context context, Uri uri, String text) throws IOException {
        try (OutputStream out = context.getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Could not open that file");
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}

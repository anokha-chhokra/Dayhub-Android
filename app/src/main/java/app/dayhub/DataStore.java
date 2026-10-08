package app.dayhub;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Feature 4: all app data lives in one JSON document in private app storage.
 *
 * Saves are atomic: the new text is written and synced to a temp file next to the document, then
 * renamed over it. A crash or a full disk mid-save leaves the previous document untouched, and a
 * stale temp file is simply overwritten by the next save.
 */
public final class DataStore {
    private static final String FILE_NAME = "dayhub.json";

    private final File file;
    private final File temp;

    /** The store in the app's private files directory. */
    public DataStore(Context context) {
        this(new File(context.getFilesDir(), FILE_NAME));
    }

    /** A store backed by the given file (its parent directory must exist). */
    public DataStore(File file) {
        this.file = file;
        this.temp = new File(file.getParentFile(), file.getName() + ".tmp");
    }

    public synchronized boolean exists() {
        return file.isFile();
    }

    /** The saved text, or null if nothing has been saved yet. */
    public synchronized String read() throws IOException {
        if (!file.isFile()) return null;
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** Atomically replaces the saved text. On failure the previous document is left as it was. */
    public synchronized void write(String text) throws IOException {
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            temp.delete();
            throw e;
        }
    }

    /**
     * The saved document, or an empty object on first run.
     * Throws {@link JSONException} if the file exists but is not valid JSON (damaged data).
     */
    public JSONObject load() throws IOException, JSONException {
        String text = read();
        return text == null ? new JSONObject() : new JSONObject(text);
    }

    public void save(JSONObject document) throws IOException {
        write(document.toString());
    }
}

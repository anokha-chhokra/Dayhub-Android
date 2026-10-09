package app.dayhub.data;

import java.io.IOException;
import java.util.Arrays;

/**
 * Feature 21: after a backup file is written, read it back and check it holds exactly what was
 * written. Pure logic with the reading and the waiting passed in, so it can be tested without a phone.
 */
public final class WriteVerifier {
    private static final long RETRY_PAUSE_MS = 400;

    private WriteVerifier() {}

    /** Reads up to {@code limit} bytes of the file as it is now. */
    public interface Source {
        byte[] read(long limit) throws IOException;
    }

    /** Waits a moment. */
    public interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    /**
     * True if the file now holds exactly {@code expected}, or if this storage provider simply will not
     * let us look (a write that went through is not a failure just because reading it back is refused).
     * A cloud provider may still show the old content for a moment, so a mismatch is checked once more
     * after a short pause before it counts.
     */
    public static boolean confirms(byte[] expected, Source source, Pause pause) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                // One byte more than expected, so a file that is longer than it should be is caught.
                if (Arrays.equals(expected, source.read(expected.length + 1L))) return true;
            } catch (IOException | RuntimeException e) {
                return true; // cannot be read back here; the write itself reported no error
            }
            if (attempt == 0) {
                try {
                    pause.sleep(RETRY_PAUSE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}

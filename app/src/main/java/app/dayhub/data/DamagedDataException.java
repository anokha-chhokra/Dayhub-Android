package app.dayhub.data;

/**
 * The saved data cannot be used. {@link #raw} keeps the original text so it can be handed back to
 * the person, and the file on disk is never overwritten while the data is in this state.
 */
public class DamagedDataException extends Exception {
    public final String raw;

    public DamagedDataException(String message, String raw) {
        super(message);
        this.raw = raw;
    }
}

package app.dayhub.data;

/**
 * A request the data engine refuses, with an HTTP-style status the screens can read
 * (400 bad input, 404 not found), the same as the web app's HttpError.
 */
public class DataError extends RuntimeException {
    public final int status;

    public DataError(int status, String message) {
        super(message);
        this.status = status;
    }

    public DataError(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** 507: the change could not be saved, so it was undone. */
    public static DataError notSaved(Throwable cause) {
        return new DataError(507, "Day Hub could not save, so your last change was undone. "
                + "Free up some space on the phone and try again.", cause);
    }

    /** 400: the input is not acceptable. */
    public static DataError bad(String message) {
        return new DataError(400, message);
    }

    /** 404: the thing asked for does not exist. */
    public static DataError notFound(String message) {
        return new DataError(404, message);
    }
}

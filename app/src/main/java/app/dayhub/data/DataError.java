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

    /** 400: the input is not acceptable. */
    public static DataError bad(String message) {
        return new DataError(400, message);
    }

    /** 404: the thing asked for does not exist. */
    public static DataError notFound(String message) {
        return new DataError(404, message);
    }
}

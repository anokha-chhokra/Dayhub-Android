package app.dayhub.data;

/**
 * Feature 26: the rule behind "hold to end early". Ending a focus session is a decision, not a slip, so it
 * only works if the finger stays down for the whole of {@link #HOLD_MS}. Lifting early starts over.
 * Time is passed in, so it can be checked without a clock.
 */
public final class HoldToEnd {
    /** Eight seconds, the same as the web app. */
    public static final long HOLD_MS = 8000;

    private long downAt = -1;

    /** The finger went down. A second press while already holding changes nothing. */
    public void press(long now) {
        if (downAt < 0) downAt = now;
    }

    /** The finger lifted, left the button, or the touch was taken over (a scroll): start over. */
    public void release() {
        downAt = -1;
    }

    public boolean holding() {
        return downAt >= 0;
    }

    /** How much of the hold is done, from 0 to 1. */
    public float progress(long now) {
        if (downAt < 0) return 0f;
        return Math.min(1f, Math.max(0f, (now - downAt) / (float) HOLD_MS));
    }

    /** True once the finger has been down for the whole time. */
    public boolean complete(long now) {
        return downAt >= 0 && now - downAt >= HOLD_MS;
    }

    /** How long is left to wait from {@code now}, in milliseconds. */
    public long remainingMs(long now) {
        return downAt < 0 ? HOLD_MS : Math.max(0, HOLD_MS - (now - downAt));
    }
}

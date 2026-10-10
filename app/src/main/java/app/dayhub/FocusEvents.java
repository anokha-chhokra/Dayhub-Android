package app.dayhub;

import java.util.concurrent.CopyOnWriteArraySet;

/** Lets parts of the app that run without a screen (the alarm, the guard) hear that the focus timer started or ended. */
final class FocusEvents {
    interface Listener {
        void focusChanged();
    }

    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    private FocusEvents() { }

    static void add(Listener l) {
        LISTENERS.add(l);
    }

    static void remove(Listener l) {
        LISTENERS.remove(l);
    }

    static void changed() {
        for (Listener l : LISTENERS) l.focusChanged();
    }
}

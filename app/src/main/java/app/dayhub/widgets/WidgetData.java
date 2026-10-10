package app.dayhub.widgets;

import android.content.Context;

import java.io.IOException;

import app.dayhub.DataStore;
import app.dayhub.DateLabels;
import app.dayhub.MoneyFormat;
import app.dayhub.data.DamagedDataException;
import app.dayhub.data.DayHubData;
import app.dayhub.data.HomeData;
import app.dayhub.data.Validate;
import app.dayhub.data.WidgetSnapshot;

/**
 * Feature 27: where the widgets get their data. They read Day Hub's own saved file directly, every time they
 * are drawn, and work out Home's numbers for today. Nothing is pushed to them, so they cannot be out of date
 * or disagree with the app. (The file is replaced whole when the app saves, so a read never sees half a save.)
 */
final class WidgetData {
    private WidgetData() { }

    /** Today's picture, or null if Day Hub has no saved data yet, or it cannot be read. */
    static WidgetSnapshot load(Context context) {
        Context c = context.getApplicationContext();
        try {
            DataStore store = new DataStore(c);
            if (!store.exists()) return null;
            DayHubData data = DayHubData.open(store);
            HomeData home = HomeData.compute(data, Validate.localDate(), DateLabels.nowHHMM());
            return WidgetSnapshot.build(home, new WidgetSnapshot.Labels() {
                @Override public String money(long minor, String currency) { return MoneyFormat.money(minor, currency); }
                @Override public String dueLabel(String dueOn, String today) { return DateLabels.dueLabel(c, dueOn, today); }
                @Override public String greeting(String name) { return DateLabels.greeting(name); }
            });
        } catch (IOException | DamagedDataException | RuntimeException e) {
            return null; // a widget shows "open Day Hub" rather than failing
        }
    }
}

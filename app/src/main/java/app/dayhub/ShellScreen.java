package app.dayhub;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The app's main layout: a page area above a hand-drawn bottom navigation bar.
 * Pages are placeholders until their features are built; Home shows the hand-drawn widgets.
 */
public final class ShellScreen extends LinearLayout {
    private static final String[] TABS = {"Home", "Tasks", "Habits", "Journal", "Spend"};

    private final Overlays overlays;
    private final FrameLayout pageHost;
    private final BottomNavBar nav;
    private int currentTab;

    public ShellScreen(Context c, Overlays overlays) {
        super(c);
        this.overlays = overlays;
        setOrientation(VERTICAL);

        pageHost = new FrameLayout(c);
        addView(pageHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new BottomNavBar(c, TABS);
        LayoutParams navParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.setMargins(Sketch.dp(c, 12), Sketch.dp(c, 4), Sketch.dp(c, 12), Sketch.dp(c, 8));
        addView(nav, navParams);

        nav.setListener(this::showPage);
        nav.select(0, true);
    }

    /** Back from any tab other than Home returns to Home. Returns true when it was used. */
    public boolean handleBack() {
        if (currentTab == 0) return false;
        nav.select(0, true);
        return true;
    }

    private void showPage(int index) {
        currentTab = index;
        pageHost.removeAllViews();
        pageHost.addView(page(index), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View page(int index) {
        Context c = getContext();
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        column.setPadding(Sketch.dp(c, 20), Sketch.dp(c, 20), Sketch.dp(c, 20), Sketch.dp(c, 20));

        column.addView(Sketch.label(c, TABS[index], 32, true, R.color.ink));
        column.addView(card(index), cardParams());

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.addView(column);
        return scroll;
    }

    private LayoutParams cardParams() {
        LayoutParams lp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(getContext(), 16);
        return lp;
    }

    private View card(int index) {
        Context c = getContext();
        HandDrawnCard card = new HandDrawnCard(c);
        if (index != 0) {
            card.addView(Sketch.label(c, "Coming soon", 20, true, R.color.ink));
            card.addView(Sketch.label(c, "This page is built in a later step.", 16, false, R.color.muted));
            return card;
        }

        card.addView(Sketch.label(c, "Hello there", 22, true, R.color.ink));
        card.addView(Sketch.label(c, "Everything here is drawn by hand.", 16, false, R.color.muted));

        LayoutParams fieldParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fieldParams.topMargin = Sketch.dp(c, 14);
        HandDrawnField field = new HandDrawnField(c, "Jot something down");
        card.addView(field, fieldParams);

        HandDrawnButton add = new HandDrawnButton(c, "Add", true);
        add.setOnClickListener(v -> overlays.toast(field.getText().length() == 0
                ? "Write something first" : "Saved"));
        HandDrawnButton clear = new HandDrawnButton(c, "Clear", false);
        clear.setOnClickListener(v -> {
            CharSequence old = field.getText().toString();
            if (old.length() == 0) return;
            field.setText("");
            overlays.toast("Cleared", () -> field.setText(old));
        });
        card.addView(buttonRow(c, add, clear), rowParams(c));

        HandDrawnButton date = new HandDrawnButton(c, "Date", false);
        date.setOnClickListener(v -> DateTimePickers.pickDate(overlays, c, LocalDate.now(),
                d -> overlays.toast(d.format(DateTimeFormatter.ofPattern("d MMM yyyy")))));
        HandDrawnButton time = new HandDrawnButton(c, "Time", false);
        time.setOnClickListener(v -> DateTimePickers.pickTime(overlays, c, LocalTime.now(),
                t -> overlays.toast(t.format(DateTimeFormatter.ofPattern("HH:mm")))));
        card.addView(buttonRow(c, date, time), rowParams(c));
        return card;
    }

    private LinearLayout buttonRow(Context c, View first, View second) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        LayoutParams gap = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gap.rightMargin = Sketch.dp(c, 12);
        row.addView(first, gap);
        row.addView(second);
        return row;
    }

    private LayoutParams rowParams(Context c) {
        LayoutParams lp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(c, 14);
        return lp;
    }
}

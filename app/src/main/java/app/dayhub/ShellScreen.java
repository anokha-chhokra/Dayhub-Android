package app.dayhub;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * The app's main layout: a page area above a hand-drawn bottom navigation bar.
 * Pages are placeholders until their features are built; Home shows the hand-drawn widgets.
 */
public final class ShellScreen extends LinearLayout {
    private static final String[] TABS = {"Home", "Tasks", "Habits", "Journal", "Spend"};

    private final FrameLayout pageHost;

    public ShellScreen(Context c) {
        super(c);
        setOrientation(VERTICAL);

        pageHost = new FrameLayout(c);
        addView(pageHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        BottomNavBar nav = new BottomNavBar(c, TABS);
        LayoutParams navParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.setMargins(Sketch.dp(c, 12), Sketch.dp(c, 4), Sketch.dp(c, 12), Sketch.dp(c, 8));
        addView(nav, navParams);

        nav.setListener(this::showPage);
        nav.select(0, true);
    }

    private void showPage(int index) {
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
        card.addView(new HandDrawnField(c, "Jot something down"), fieldParams);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        LayoutParams buttonParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.rightMargin = Sketch.dp(c, 12);
        row.addView(new HandDrawnButton(c, "Add", true), buttonParams);
        row.addView(new HandDrawnButton(c, "Clear", false));
        LayoutParams rowParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Sketch.dp(c, 14);
        card.addView(row, rowParams);
        return card;
    }
}

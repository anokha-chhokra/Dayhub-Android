package app.dayhub;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import app.dayhub.data.DayHubData;

/**
 * The app's main layout: a page area above a hand-drawn bottom navigation bar. Home is the real
 * dashboard; the other tabs are placeholders until their features are built.
 */
public final class ShellScreen extends LinearLayout implements HomeScreen.Navigator {
    private static final String[] TABS = {"Home", "Tasks", "Habits", "Journal", "Spend"};
    private static final String[] ROUTES = {"home", "tasks", "habits", "journal", "spend"};

    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final DataTransfer transfer;
    private final FrameLayout pageHost;
    private final BottomNavBar nav;
    private HomeScreen home;
    private int currentTab;

    public ShellScreen(Activity activity, DayHubData data, Overlays overlays, DataTransfer transfer) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.transfer = transfer;
        setOrientation(VERTICAL);

        pageHost = new FrameLayout(activity);
        addView(pageHost, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        nav = new BottomNavBar(activity, TABS);
        LayoutParams navParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navParams.setMargins(Sketch.dp(activity, 12), Sketch.dp(activity, 4), Sketch.dp(activity, 12), Sketch.dp(activity, 8));
        addView(nav, navParams);

        nav.setListener(this::showPage);
        nav.select(0, true);
    }

    /** Opens the tab for a route such as "tasks" or "spend". Routes without a tab yet say so. */
    @Override
    public void go(String route) {
        for (int i = 0; i < ROUTES.length; i++) {
            if (ROUTES[i].equals(route)) {
                nav.select(i, true);
                return;
            }
        }
        overlays.toast("That screen is coming soon");
    }

    /** Back from any tab other than Home returns to Home. Returns true when it was used. */
    public boolean handleBack() {
        if (currentTab == 0) return false;
        nav.select(0, true);
        return true;
    }

    /** Redraws the page being shown, e.g. after the app comes back to the front or data was restored. */
    public void refresh() {
        showPage(currentTab);
    }

    private void showPage(int index) {
        currentTab = index;
        pageHost.removeAllViews();
        if (index == 0) {
            // One Home for the whole session, so a half-typed note is still there when you come back.
            if (home == null) home = new HomeScreen(activity, data, overlays, transfer, this);
            pageHost.addView(home, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            home.refresh();
            return;
        }
        pageHost.addView(placeholder(index), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View placeholder(int index) {
        Context c = getContext();
        LinearLayout column = new LinearLayout(c);
        column.setOrientation(VERTICAL);
        column.setPadding(Sketch.dp(c, 20), Sketch.dp(c, 20), Sketch.dp(c, 20), Sketch.dp(c, 20));
        column.addView(Sketch.label(c, TABS[index], 32, true, R.color.ink));

        HandDrawnCard card = new HandDrawnCard(c);
        card.addView(Sketch.label(c, "Coming soon", 20, true, R.color.ink));
        card.addView(Sketch.label(c, "This page is built in a later step.", 16, false, R.color.muted));
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(c, 16);
        column.addView(card, lp);

        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        scroll.addView(column);
        return scroll;
    }
}

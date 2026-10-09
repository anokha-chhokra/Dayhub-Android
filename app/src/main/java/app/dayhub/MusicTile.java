package app.dayhub;

import android.app.Activity;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Music;

/**
 * The Music tile on Home: paste a YouTube link and it opens in the YouTube app (or the browser).
 * Day Hub has no player of its own and never goes online. The link field is built once so
 * whatever is typed in it survives screen refreshes.
 */
public final class MusicTile extends HandDrawnCard {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;
    private final Runnable openSaved;
    private final HandDrawnField link;
    private String shownKey; // what the tile currently shows, so an unchanged tile is not rebuilt

    public MusicTile(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged, Runnable openSaved) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
        this.openSaved = openSaved;
        link = new HandDrawnField(activity, "Paste a YouTube link");
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Shows the current link (or the empty state) above the paste box. */
    public void update(Music current) {
        // Rebuilding would drop the keyboard while a link is being typed, so only do it on a change.
        String key = current == null ? "none" : current.id + "|" + current.label + "|" + current.url;
        if (key.equals(shownKey)) return;
        shownKey = key;
        removeAllViews();
        LinearLayout title = new LinearLayout(activity);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(android.view.Gravity.CENTER_VERTICAL);
        title.addView(Sketch.label(activity, "Music", 22, true, R.color.ink),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        HandDrawnButton saved = new HandDrawnButton(activity, "Saved", false);
        saved.setOnClickListener(v -> openSaved.run());
        title.addView(saved);
        addView(title);
        if (current != null) {
            addView(Sketch.label(activity, current.label, 16, false, R.color.muted), rowParams(8));
            HandDrawnButton open = new HandDrawnButton(activity, "Open in YouTube", false);
            open.setOnClickListener(v -> openLink(current));
            addView(open, rowParams(12));
        } else {
            addView(Sketch.label(activity, "Paste a YouTube video or playlist link and it opens in YouTube.",
                    16, false, R.color.muted), rowParams(8));
        }
        if (link.getParent() != null) ((ViewGroup) link.getParent()).removeView(link);
        addView(link, rowParams(14));
        HandDrawnButton play = new HandDrawnButton(activity, "Play", true);
        play.setOnClickListener(v -> play());
        addView(play, rowParams(12));
    }

    private void openLink(Music m) {
        if (!ExternalLinks.open(activity, m.url)) overlays.toast("No app found to open the link");
    }

    private void play() {
        String url = link.getText().toString().trim();
        if (url.isEmpty()) return;
        try {
            Music saved = data.addMusic(url, null, true);
            data.commit();
            link.setText("");
            onChanged.run();
            openLink(saved);
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
    }
}

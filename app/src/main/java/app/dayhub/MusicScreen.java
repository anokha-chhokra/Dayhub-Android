package app.dayhub;

import android.app.Activity;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import app.dayhub.data.DataError;
import app.dayhub.data.DayHubData;
import app.dayhub.data.Model.Music;

import java.util.List;

/**
 * Feature 18: the Music tab. Paste a YouTube video or playlist link and it is saved and opened in the
 * YouTube app (or the browser): Day Hub has no player of its own and never goes online. Saved links
 * can be played again, and removed.
 */
public final class MusicScreen extends ScrollView {
    private final Activity activity;
    private final DayHubData data;
    private final Overlays overlays;
    private final Runnable onChanged;
    private final LinearLayout column;
    private final HandDrawnField link;

    public MusicScreen(Activity activity, DayHubData data, Overlays overlays, Runnable onChanged) {
        super(activity);
        this.activity = activity;
        this.data = data;
        this.overlays = overlays;
        this.onChanged = onChanged;
        setFillViewport(true);

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = Sketch.dp(activity, 20);
        column.setPadding(pad, pad, pad, pad);
        addView(column);

        // Built once, so what is typed survives a redraw.
        link = new HandDrawnField(activity, "https://www.youtube.com/watch?v=…");
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Sketch.dp(activity, topDp);
        return lp;
    }

    /** Redraws the current link, the paste box and the saved list from the stored data. */
    public void refresh() {
        column.removeAllViews();
        column.addView(Sketch.label(activity, "Music", 32, true, R.color.ink));

        Integer currentId = data.getSettings().currentMusicId;
        Music current = currentId == null ? null : data.getMusic(currentId);
        if (current != null) column.addView(currentCard(current), rowParams(14));
        column.addView(pasteCard(), rowParams(14));
        column.addView(savedCard(data.listMusic(), currentId), rowParams(14));
    }

    // ---------- opening a link ----------

    private void open(Music m) {
        if (ExternalLinks.blockedByFocus(activity)) {
            overlays.toast("Links are off while focus mode is on.");
            return;
        }
        if (!ExternalLinks.open(activity, m.url)) overlays.toast("No app found to open the link");
    }

    // ---------- what is current ----------

    private View currentCard(Music current) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Now", 14, true, R.color.muted));
        card.addView(Sketch.label(activity, current.label, 20, true, R.color.ink), rowParams(2));
        HandDrawnButton open = new HandDrawnButton(activity, "Open in YouTube", true);
        open.setOnClickListener(v -> open(current));
        card.addView(open, rowParams(12));
        return card;
    }

    // ---------- paste a link ----------

    private View pasteCard() {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Paste a YouTube link", 20, true, R.color.ink));
        card.addView(Sketch.label(activity, "A video or a playlist. It is saved here and opens in YouTube right away.",
                14, false, R.color.muted), rowParams(4));
        if (link.getParent() != null) ((ViewGroup) link.getParent()).removeView(link);
        card.addView(link, rowParams(10));
        HandDrawnButton play = new HandDrawnButton(activity, "Play", true);
        play.setOnClickListener(v -> addAndPlay());
        card.addView(play, rowParams(12));
        card.addView(Sketch.label(activity,
                "Day Hub has no player of its own, so links open in the YouTube app or your browser. "
                        + "Some videos block embedding in other apps; if one will not play, try another link.",
                14, false, R.color.muted), rowParams(12));
        return card;
    }

    private void addAndPlay() {
        String url = link.getText().toString().trim();
        if (url.isEmpty()) return;
        try {
            Music saved = data.addMusic(url, null, true);
            data.commit();
            link.setText("");
            onChanged.run();
            open(saved);
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
    }

    // ---------- saved links ----------

    private View savedCard(List<Music> links, Integer currentId) {
        HandDrawnCard card = new HandDrawnCard(activity);
        card.addView(Sketch.label(activity, "Saved", 22, true, R.color.ink));
        if (links.isEmpty()) {
            card.addView(Sketch.label(activity, "Links you play are saved here.", 16, false, R.color.muted), rowParams(10));
            return card;
        }
        for (Music m : links) card.addView(savedRow(m, currentId != null && currentId == m.id), rowParams(8));
        return card;
    }

    private View savedRow(Music m, boolean isCurrent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout text = new LinearLayout(activity);
        text.setOrientation(LinearLayout.VERTICAL);
        text.addView(Sketch.label(activity, m.label, 16, false, R.color.ink));
        text.addView(Sketch.label(activity, m.kind, 14, false, R.color.muted));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        HandDrawnButton play = new HandDrawnButton(activity, isCurrent ? "Open" : "Play", !isCurrent);
        play.setOnClickListener(v -> play(m));
        row.addView(play);

        TextView bin = Sketch.label(activity, "🗑️", 22, false, R.color.ink);
        bin.setContentDescription("Remove " + m.label);
        bin.setPadding(Sketch.dp(activity, 12), Sketch.dp(activity, 8), Sketch.dp(activity, 4), Sketch.dp(activity, 8));
        bin.setOnClickListener(v -> remove(m));
        row.addView(bin);
        return row;
    }

    private void play(Music m) {
        try {
            data.setCurrentMusic(m.id);
            data.commit();
        } catch (DataError e) {
            overlays.toast(e.getMessage());
            onChanged.run();
            return;
        }
        onChanged.run();
        open(m);
    }

    private void remove(Music m) {
        try {
            data.deleteMusic(m.id);
            data.commit();
            overlays.toast("Removed");
        } catch (DataError e) {
            overlays.toast(e.getMessage());
        }
        onChanged.run();
    }
}

package app.dayhub.widgets;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import app.dayhub.R;

/** Feature 27: shared parts of the six widgets. Each provider only says how to draw itself from Day Hub's data. */
public abstract class BaseWidget extends AppWidgetProvider {
    private static final String TAG = "DayHubWidget";

    /** Draws one widget. */
    abstract RemoteViews build(Context c, int widgetId);

    /** The ListView in the layout that needs refreshing when data changes, or 0 if there is none. */
    int listId() { return 0; }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        push(c, m, ids);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        push(c, m, new int[] { id });
    }

    /** Redraws every copy of this widget that is on a home screen. */
    final void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, getClass()));
        if (ids != null && ids.length > 0) push(c, m, ids);
    }

    private void push(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            try {
                m.updateAppWidget(id, build(c, id));
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not draw widget " + id, e);
            }
        }
        if (listId() != 0 && ids.length > 0) m.notifyAppWidgetViewDataChanged(ids, listId());
    }

    /** Points the widget's list at WidgetListService, one adapter per widget so each keeps its own rows. */
    static void attachList(Context c, RemoteViews rv, int widgetId, String kind) {
        Intent svc = new Intent(c, WidgetListService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.putExtra(WidgetListService.EXTRA_KIND, kind);
        svc.setData(Uri.parse("dayhub://list/" + kind + "/" + widgetId)); // Android caches adapters by this; it must differ
        rv.setRemoteAdapter(R.id.list, svc);
        rv.setEmptyView(R.id.list, R.id.empty);
        rv.setPendingIntentTemplate(R.id.list, WidgetIntents.template(c));
    }
}

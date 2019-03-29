package com.cliqz.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.mozilla.gecko.GeckoApp;
import org.mozilla.gecko.R;

public class Provider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (context == null || appWidgetManager == null || appWidgetIds == null || appWidgetIds.length == 0) {
            return;
        }

        for (int id: appWidgetIds) {
            final Intent intent = new Intent(context, GeckoApp.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.app_widget_search);
            views.setOnClickPendingIntent(R.id.app_widget_search_root, pendingIntent);
            appWidgetManager.updateAppWidget(id, views);
        }
    }
}

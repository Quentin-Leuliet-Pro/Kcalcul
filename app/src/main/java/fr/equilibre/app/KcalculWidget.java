package fr.equilibre.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Two shortcuts into the existing local entry forms; no background work is needed. */
public final class KcalculWidget extends AppWidgetProvider {
    static final String ADD_CALORIES="fr.equilibre.app.widget.ADD_CALORIES";
    static final String ADD_WEIGHT="fr.equilibre.app.widget.ADD_WEIGHT";

    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids) {
        for(int id:ids) manager.updateAppWidget(id,views(context));
    }

    static RemoteViews views(Context context) {
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.kcalcul_widget);
        views.setOnClickPendingIntent(R.id.widget_add_calories,launch(context,ADD_CALORIES,1));
        views.setOnClickPendingIntent(R.id.widget_add_weight,launch(context,ADD_WEIGHT,2));
        return views;
    }

    private static PendingIntent launch(Context context,String action,int requestCode) {
        Intent intent=new Intent(context,MainActivity.class).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context,requestCode,intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

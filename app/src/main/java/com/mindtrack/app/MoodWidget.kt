package com.mindtrack.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

const val EXTRA_QUICK_MOOD = "com.mindtrack.app.QUICK_MOOD"

/** One-tap mood entry point on the home screen. Tapping a face opens the check-in with that mood chosen. */
class MoodWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val faces = listOf(
            R.id.widget_mood_1 to 1, R.id.widget_mood_2 to 2, R.id.widget_mood_3 to 3,
            R.id.widget_mood_4 to 4, R.id.widget_mood_5 to 5
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_mood)
            faces.forEach { (viewId, score) ->
                val intent = Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_QUICK_MOOD, score)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(
                    viewId,
                    PendingIntent.getActivity(
                        context, 8000 + score, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
            manager.updateAppWidget(id, views)
        }
    }
}

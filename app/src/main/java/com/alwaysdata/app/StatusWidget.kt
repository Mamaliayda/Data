package com.alwaysdata.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/** Home-screen widget showing live mobile-data / protection status. */
class StatusWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, StatusWidget::class.java)
            )
            for (id in ids) {
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val protectionOn = Prefs.isProtectionEnabled(context)
            val dataOn = MobileDataController.isMobileDataEnabled(context)
            val text = when {
                !protectionOn -> "حفاظت خاموش است"
                dataOn -> "دیتا روشن است ✓"
                else -> "دیتا خاموش — در حال تلاش…"
            }
            views.setTextViewText(R.id.widget_status, text)
            views.setInt(
                R.id.widget_dot,
                "setBackgroundColor",
                if (protectionOn && dataOn) 0xFF4ADE80.toInt() else 0xFFF87171.toInt()
            )
            return views
        }
    }
}

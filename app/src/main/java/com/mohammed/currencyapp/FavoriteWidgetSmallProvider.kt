package com.mohammed.currencyapp

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * الودجت الصغير 4×1: يعرض أول مفضلة فقط. الرسم والتحديث كلهم مشتركين مع الودجت
 * الكبير (FavoriteWidgetProvider) حتى يبقون متطابقين: أي تحديث يرسم النوعين معاً،
 * والمنبه الساعي واحد لكلهم.
 */
class FavoriteWidgetSmallProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshScheduler.schedule(context)
        FavoriteWidgetProvider.renderFromCache(context)
        val pendingResult = goAsync()
        FavoriteWidgetProvider.refreshFavorite(context) { pendingResult.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == FavoriteWidgetProvider.ACTION_REFRESH) {
            val pendingResult = goAsync()
            FavoriteWidgetProvider.refreshFavorite(context, animate = true) { pendingResult.finish() }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (!FavoriteWidgetProvider.hasAnyWidget(context)) WidgetRefreshScheduler.cancel(context)
    }
}

package com.mohammed.currencyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/**
 * ودجت الشاشة الرئيسية: صف وحيد يعرض بورصة المفضلة (اسم + سعر + علم)، مع
 * زر تحديث يدوي أعلى يمين العلم يحدّث هذه البورصة وحدها (مو كل القائمة)،
 * وتحديث تلقائي إضافي كل نص ساعة عبر WidgetRefreshScheduler.
 *
 * لا يوجد أي عملية دائمة بالخلفية: أندرويد يشغّل هذا الـ Provider فقط لحظة
 * إضافة الودجت، أو الضغط على زر التحديث، أو عند المنبه الساعي — وبكل مرة
 * يرسم القيمة المحفوظة بالكاش فوراً (بدون انتظار شبكة)، ثم يسوي طلب شبكة
 * واحد مستهدف لبورصة وحدة بالخلفية ويحدّث الودجت بنتيجته.
 */
class FavoriteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mohammed.currencyapp.ACTION_WIDGET_REFRESH"

        private fun buildRemoteViews(context: Context, item: PriceItem?, favoriteKey: String): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_favorite_price)
            val meta = WidgetPriceProvider.metaFor(favoriteKey)

            views.setTextViewText(R.id.widgetName, item?.name ?: meta?.name ?: "—")
            views.setTextViewText(R.id.widgetPrice, item?.price ?: "—")
            views.setTextViewText(R.id.widgetUnit, item?.unit ?: "")

            val badgeRes = when (item?.trend) {
                Trend.UP -> R.drawable.bg_badge_up
                Trend.DOWN -> R.drawable.bg_badge_down
                else -> R.drawable.bg_badge
            }
            views.setInt(R.id.widgetPrice, "setBackgroundResource", badgeRes)

            val flagKey = item?.flagDrawable ?: meta?.flag
            val flagRes = flagKey?.let {
                context.resources.getIdentifier("row_bg_$it", "drawable", context.packageName)
            } ?: 0
            if (flagRes != 0) {
                views.setImageViewResource(R.id.widgetFlagBg, flagRes)
            }

            // فتح التطبيق عند الضغط على أي مكان بالودجت غير زر التحديث.
            val openAppPending = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPending)

            // زر التحديث اليدوي: يحدّث بورصة المفضلة وحدها.
            val refreshIntent = Intent(context, FavoriteWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRefreshButton, refreshPending)

            return views
        }

        private fun currentWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, FavoriteWidgetProvider::class.java))
        }

        /** يرسم كل الودجتات الحالية بآخر سعر محفوظ بالكاش فوراً (بدون شبكة). */
        fun renderFromCache(context: Context) {
            val ids = currentWidgetIds(context)
            if (ids.isEmpty()) return
            val manager = AppWidgetManager.getInstance(context)
            val favoriteKey = FavoriteCityStore.get(context)
            val cached = WidgetPriceProvider.buildCachedItem(context, favoriteKey)
            val views = buildRemoteViews(context, cached, favoriteKey)
            for (id in ids) manager.updateAppWidget(id, views)
        }

        /**
         * يجيب سعر جديد فعلي لبورصة المفضلة وحدها، ويحدّث كل الودجتات بنتيجته.
         * لو animate=true (زر التحديث اليدوي فقط)، يعرض تأثير "عداد تصاعدي
         * مبعثر" (أرقام عشوائية تستقر تدريجياً على الرقم الصحيح) قبل ما
         * يثبّت على السعر النهائي — نفس فكرة ملف الـ HTML المرفق، بس منفّذة
         * بتحديثات RemoteViews متكررة بدل JavaScript (الودجت ما تشغّل جافاسكربت).
         */
        fun refreshFavorite(context: Context, animate: Boolean = false, onDone: () -> Unit = {}) {
            val ids = currentWidgetIds(context)
            if (ids.isEmpty()) {
                onDone()
                return
            }
            val favoriteKey = FavoriteCityStore.get(context)
            WidgetPriceProvider.fetchOne(context, favoriteKey) { item ->
                if (animate) {
                    animateReveal(context, ids, item, favoriteKey, onDone)
                } else {
                    val manager = AppWidgetManager.getInstance(context)
                    val views = buildRemoteViews(context, item, favoriteKey)
                    for (id in ids) manager.updateAppWidget(id, views)
                    onDone()
                }
            }
        }

        /**
         * يعيد رسم الودجت عدة مرات متتالية (كل ~45ms، لمدة ~1.4 ثانية)، وبكل
         * مرة يستبدل خانات الأرقام فقط بسعر النص النهائي بأرقام عشوائية
         * تقترب تدريجياً من الرقم الحقيقي حتى تستقر عليه بآخر إطار — بالضبط
         * نفس منطق عداد ملف الـ HTML (قيمة "حقيقية" متصاعدة تُقارن رقماً
         * برقم مع الهدف، وبعد تجاوز 60% من المدة أي خانة تتطابق تثبت وتبقى).
         * الفواصل والحروف (مثل "," و"د.ع" أو "يورو") تبقى ثابتة بمكانها،
         * فقط الأرقام [0-9] بالنص هي اللي تتحرك.
         */
        private fun animateReveal(
            context: Context,
            ids: IntArray,
            item: PriceItem?,
            favoriteKey: String,
            onDone: () -> Unit
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val finalPrice = item?.price
            val plan = finalPrice?.let { DigitRevealAnimator.plan(it) }

            // لا يوجد سعر جديد فعلي (فشل الاتصال مثلاً) أو نص بدون أرقام —
            // نعرض النتيجة مباشرة بدون أنيميشن.
            if (finalPrice == null || plan == null) {
                val views = buildRemoteViews(context, item, favoriteKey)
                for (id in ids) manager.updateAppWidget(id, views)
                onDone()
                return
            }

            val duration = 1400L
            val frameRate = 45L
            val totalSteps = (duration / frameRate).toInt().coerceAtLeast(1)
            val handler = Handler(Looper.getMainLooper())
            var step = 0

            fun renderFrame() {
                step++
                val views = buildRemoteViews(context, item, favoriteKey)
                views.setTextViewText(R.id.widgetPrice, DigitRevealAnimator.frameText(finalPrice, plan, step, totalSteps))
                for (id in ids) manager.updateAppWidget(id, views)

                if (step < totalSteps) {
                    handler.postDelayed(::renderFrame, frameRate)
                } else {
                    onDone()
                }
            }
            renderFrame()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        renderFromCache(context)
        val pendingResult = goAsync()
        refreshFavorite(context) { pendingResult.finish() }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // نفس أنيميشن الأرقام المبعثرة تظهر بالحالتين: الضغط اليدوي على الزر،
        // وأيضاً التحديث الساعي التلقائي بالخلفية (بناءً على طلب المستخدم).
        when (intent.action) {
            ACTION_REFRESH -> {
                val pendingResult = goAsync()
                refreshFavorite(context, animate = true) { pendingResult.finish() }
            }
            WidgetRefreshScheduler.ACTION_HOURLY_REFRESH -> {
                val pendingResult = goAsync()
                refreshFavorite(context, animate = true) { pendingResult.finish() }
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context)
    }
}

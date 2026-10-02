package com.mohammed.currencyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * ودجت الشاشة الرئيسية 4×2: يعرض أول 3 مفضلات — الأولى ببطاقة كبيرة (علم + اسم +
 * سعر + مقدار التغيير)، والثانية والثالثة ببطاقتين صغيرتين تحتها. لون كل بطاقة
 * حسب اتجاه السعر (أخضر/أحمر/رصاصي فاتح). أعلى الودجت: اسم البورصة، وقت آخر
 * تحديث، وزر تحديث يدوي يحدّث المفضلات الظاهرة (مو كل القائمة)، وتحديث تلقائي
 * كل نص ساعة عبر WidgetRefreshScheduler (منبه واحد خفيف بدون إيقاظ الجهاز).
 *
 * لا يوجد أي عملية دائمة بالخلفية: أندرويد يشغّل هذا الـ Provider فقط لحظة
 * إضافة الودجت، أو الضغط على زر التحديث، أو عند المنبه الساعي — وبكل مرة
 * يرسم القيم المحفوظة بالكاش فوراً (بدون انتظار شبكة)، ثم يسوي طلب شبكة
 * مستهدف لكل مفضلة بالخلفية ويحدّث الودجت بنتيجتها.
 */
class FavoriteWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.mohammed.currencyapp.ACTION_WIDGET_REFRESH"

        private class CardIds(
            val card: Int, val flag: Int, val name: Int, val unit: Int,
            val price: Int, val deltaRow: Int, val arrow: Int, val delta: Int,
            val nameSp: Float
        )

        // الودجت الكبير 4×2: ثلاث بطاقات (أول 3 مفضلات).
        private val CARDS = listOf(
            CardIds(R.id.widgetCard1, R.id.widgetFlag1, R.id.widgetName1, R.id.widgetUnit1,
                R.id.widgetPrice1, R.id.widgetDeltaRow1, R.id.widgetDeltaArrow1, R.id.widgetDelta1, 19f),
            CardIds(R.id.widgetCard2, R.id.widgetFlag2, R.id.widgetName2, R.id.widgetUnit2,
                R.id.widgetPrice2, R.id.widgetDeltaRow2, R.id.widgetDeltaArrow2, R.id.widgetDelta2, 14f),
            CardIds(R.id.widgetCard3, R.id.widgetFlag3, R.id.widgetName3, R.id.widgetUnit3,
                R.id.widgetPrice3, R.id.widgetDeltaRow3, R.id.widgetDeltaArrow3, R.id.widgetDelta3, 14f)
        )

        // الودجت الصغير 4×1: بطاقة وحدة (أول مفضلة).
        private val SMALL_CARD = CardIds(
            R.id.smallCard, R.id.smallFlag, R.id.smallName, R.id.smallUnit,
            R.id.smallPrice, R.id.smallDeltaRow, R.id.smallDeltaArrow, R.id.smallDelta, 16f
        )

        private fun displayPrice(raw: String?): String = raw?.removeSuffix(" د.ع") ?: "—"

        private fun fillCard(
            context: Context, views: RemoteViews, ids: CardIds, key: String, item: PriceItem?
        ) {
            val meta = WidgetPriceProvider.metaFor(key)
            views.setViewVisibility(ids.card, View.VISIBLE)
            setName(context, views, ids, item?.name ?: meta?.name ?: "—")
            views.setTextViewText(ids.unit, item?.unit ?: "")
            views.setTextViewText(ids.price, displayPrice(item?.price))

            views.setInt(
                ids.card, "setBackgroundResource",
                when (item?.trend) {
                    Trend.UP -> R.drawable.bg_widget_card_up
                    Trend.DOWN -> R.drawable.bg_widget_card_down
                    else -> R.drawable.bg_widget_card_flat
                }
            )

            val flagKey = item?.flagDrawable ?: meta?.flag
            val flagRes = flagKey?.let {
                context.resources.getIdentifier("flag_$it", "drawable", context.packageName)
            } ?: 0
            if (flagRes != 0) views.setImageViewResource(ids.flag, flagRes)

            // مقدار التغيير + مثلث (نفس منطق الصفحة الرئيسية). المستقر/بدون تغيير = مخفي.
            val amount = if (item != null && item.trend != Trend.FLAT) {
                PriceDelta.absText(context, item.cityKey ?: item.name)
            } else null
            if (amount == null) {
                views.setViewVisibility(ids.deltaRow, View.INVISIBLE)
            } else {
                views.setViewVisibility(ids.deltaRow, View.VISIBLE)
                if (item?.trend == Trend.UP) {
                    views.setImageViewResource(ids.arrow, R.drawable.ic_tri_up)
                    views.setTextViewText(ids.delta, "+$amount")
                    views.setTextColor(ids.delta, Color.parseColor("#1E9E46"))
                } else {
                    views.setImageViewResource(ids.arrow, R.drawable.ic_tri_down)
                    views.setTextViewText(ids.delta, "-$amount")
                    views.setTextColor(ids.delta, Color.parseColor("#E0312D"))
                }
            }
        }

        /** اسم المدينة كصورة بخط Cairo العريض (الودجت ما يدعم الخطوط المخصصة كنص). */
        private fun setName(context: Context, views: RemoteViews, ids: CardIds, name: String) {
            views.setImageViewBitmap(
                ids.name, WidgetText.render(context, name, ids.nameSp, Color.parseColor("#212121"))
            )
        }

        private fun placeholderCard(context: Context, views: RemoteViews, ids: CardIds) {
            views.setViewVisibility(ids.card, View.VISIBLE)
            setName(context, views, ids, "اختر مفضلة")
            views.setTextViewText(ids.unit, "اضغط ☆ بالتطبيق")
            views.setTextViewText(ids.price, "—")
            views.setViewVisibility(ids.deltaRow, View.INVISIBLE)
            views.setInt(ids.card, "setBackgroundResource", R.drawable.bg_widget_card_flat)
        }

        private fun openAppPending(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun refreshPending(context: Context, target: Class<*>, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, requestCode,
                Intent(context, target).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun buildLargeViews(
            context: Context, keys: List<String>, items: List<PriceItem?>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_favorite_price)

            val last = LastUpdateStore.load(context)
            views.setTextViewText(R.id.widgetUpdateTime, if (last != null) UpdateTimeFormat.timeAndDate(last) else "—")

            if (keys.isEmpty()) {
                placeholderCard(context, views, CARDS[0])
                views.setViewVisibility(CARDS[1].card, View.GONE)
                views.setViewVisibility(CARDS[2].card, View.GONE)
            } else {
                for ((i, ids) in CARDS.withIndex()) {
                    if (i < keys.size) fillCard(context, views, ids, keys[i], items.getOrNull(i))
                    else views.setViewVisibility(ids.card, View.GONE)
                }
            }
            views.setViewVisibility(R.id.widgetSmallRow, if (keys.size >= 2) View.VISIBLE else View.GONE)

            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPending(context))
            // الضغط على الزر أو الوقت والتاريخ (كل منطقة التحديث) يسوي تحديث.
            val refresh = refreshPending(context, FavoriteWidgetProvider::class.java, 1)
            views.setOnClickPendingIntent(R.id.widgetUpdateArea, refresh)
            views.setOnClickPendingIntent(R.id.widgetRefreshButton, refresh)
            views.setOnClickPendingIntent(R.id.widgetUpdateTime, refresh)
            return views
        }

        private fun buildSmallViews(
            context: Context, keys: List<String>, items: List<PriceItem?>
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_favorite_small)
            val last = LastUpdateStore.load(context)
            views.setTextViewText(R.id.smallUpdateTime, if (last != null) UpdateTimeFormat.timeAndDate(last) else "—")
            if (keys.isEmpty()) placeholderCard(context, views, SMALL_CARD)
            else fillCard(context, views, SMALL_CARD, keys[0], items.getOrNull(0))

            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPending(context))
            val refresh = refreshPending(context, FavoriteWidgetSmallProvider::class.java, 2)
            views.setOnClickPendingIntent(R.id.smallUpdateArea, refresh)
            views.setOnClickPendingIntent(R.id.smallRefreshButton, refresh)
            views.setOnClickPendingIntent(R.id.smallUpdateTime, refresh)
            return views
        }

        private fun idsOf(context: Context, provider: Class<*>): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

        /** هل في أي ودجت (كبير أو صغير) موجود على الشاشة؟ */
        fun hasAnyWidget(context: Context): Boolean =
            idsOf(context, FavoriteWidgetProvider::class.java).isNotEmpty() ||
                idsOf(context, FavoriteWidgetSmallProvider::class.java).isNotEmpty()

        /** يرسم كل الودجتات (الكبيرة والصغيرة) بنفس البيانات. [firstPriceOverride]
         * يستبدل نص سعر أول مفضلة فقط (لأنيميشن الأرقام المبعثرة). */
        private fun pushAll(
            context: Context, keys: List<String>, items: List<PriceItem?>, firstPriceOverride: String? = null
        ) {
            val manager = AppWidgetManager.getInstance(context)

            val largeIds = idsOf(context, FavoriteWidgetProvider::class.java)
            if (largeIds.isNotEmpty()) {
                val v = buildLargeViews(context, keys, items)
                if (firstPriceOverride != null) v.setTextViewText(R.id.widgetPrice1, firstPriceOverride)
                for (id in largeIds) manager.updateAppWidget(id, v)
            }

            val smallIds = idsOf(context, FavoriteWidgetSmallProvider::class.java)
            if (smallIds.isNotEmpty()) {
                val v = buildSmallViews(context, keys, items)
                if (firstPriceOverride != null) v.setTextViewText(R.id.smallPrice, firstPriceOverride)
                for (id in smallIds) manager.updateAppWidget(id, v)
            }
        }

        /** يرسم كل الودجتات الحالية بآخر أسعار محفوظة بالكاش فوراً (بدون شبكة). */
        fun renderFromCache(context: Context) {
            if (!hasAnyWidget(context)) return
            val keys = FavoriteCityStore.getAll(context)
            val items = keys.map { WidgetPriceProvider.buildCachedItem(context, it) }
            pushAll(context, keys, items)
        }

        /**
         * يجيب أسعار جديدة فعلية للمفضلات (لحد 3)، ويحدّث كل الودجتات بنتيجتها.
         * لو animate=true (زر التحديث اليدوي والتحديث الساعي)، سعر أول مفضلة يطلع
         * بتأثير "عداد مبعثر" (أرقام عشوائية تستقر تدريجياً على الرقم الصحيح)
         * بتحديثات RemoteViews متكررة بدل JavaScript.
         */
        fun refreshFavorite(context: Context, animate: Boolean = false, onDone: () -> Unit = {}) {
            if (!hasAnyWidget(context)) {
                onDone()
                return
            }
            val keys = FavoriteCityStore.getAll(context)
            if (keys.isEmpty()) {
                renderFromCache(context)
                onDone()
                return
            }
            val results = arrayOfNulls<PriceItem>(keys.size)
            val remaining = AtomicInteger(keys.size)
            keys.forEachIndexed { i, key ->
                WidgetPriceProvider.fetchOne(context, key) { item ->
                    results[i] = item ?: WidgetPriceProvider.buildCachedItem(context, key)
                    if (item != null) {
                        WidgetRefreshScheduler.markRefreshed(context)
                        LastUpdateStore.save(context, System.currentTimeMillis())
                    }
                    if (remaining.decrementAndGet() == 0) {
                        val items = results.toList()
                        if (animate) animateReveal(context, keys, items, onDone)
                        else {
                            pushAll(context, keys, items)
                            onDone()
                        }
                    }
                }
            }
        }

        /**
         * يعيد رسم الودجت عدة مرات متتالية (كل ~45ms، لمدة ~1.4 ثانية)، وبكل مرة
         * يستبدل أرقام سعر أول مفضلة بأرقام عشوائية تقترب تدريجياً من الرقم
         * الحقيقي حتى تستقر عليه بآخر إطار. الفواصل والحروف تبقى ثابتة.
         */
        private fun animateReveal(
            context: Context,
            keys: List<String>,
            items: List<PriceItem?>,
            onDone: () -> Unit
        ) {
            val finalPrice = items.firstOrNull()?.price?.removeSuffix(" د.ع")
            val plan = finalPrice?.let { DigitRevealAnimator.plan(it) }

            // لا يوجد سعر جديد فعلي (فشل الاتصال مثلاً) أو نص بدون أرقام —
            // نعرض النتيجة مباشرة بدون أنيميشن.
            if (finalPrice == null || plan == null) {
                pushAll(context, keys, items)
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
                pushAll(context, keys, items, DigitRevealAnimator.frameText(finalPrice, plan, step, totalSteps))
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
        // أندرويد يستدعي onUpdate بعد إعادة تشغيل الهاتف وبعد تحديث التطبيق، والمنبهات
        // تنمسح بالحالتين — فنعيد الجدولة هنا (آمنة للتكرار، ما تسوي منبه ثاني).
        WidgetRefreshScheduler.schedule(context)
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
                // تحديث تلقائي: نتخطاه لو ماكو نت أو تحدّث قبل دقايق (توفير بطارية وشبكة).
                if (!WidgetRefreshScheduler.shouldAutoRefresh(context)) return
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
        // نوقف المنبه فقط لو ما بقى أي ودجت (كبير أو صغير) على الشاشة.
        if (!hasAnyWidget(context)) WidgetRefreshScheduler.cancel(context)
    }
}

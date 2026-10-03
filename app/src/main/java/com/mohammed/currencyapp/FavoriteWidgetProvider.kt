package com.mohammed.currencyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
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

        // بعض الـ Launchers تطلب onUpdate كل ما ترجع للشاشة الرئيسية. هالطلب ما يحتاج نبني
        // الودجت من جديد لو رسمناه قبل دقايق لنفس الودجتات (والنظام محتفظ بآخر رسم)، وكل بناء
        // = PendingIntents + صور + اتصالات Binder لحظة ما الـ Launcher مشغول. نتجاوز فقط لو:
        // نفس تشغيل الجهاز (BOOT_COUNT)، رسم قبل <5 دقايق، وكل المعرّفات مرسومة سابقاً.
        private const val RENDER_PREFS = "widget_render_state"

        private fun bootCount(context: Context): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0) }
                catch (_: Exception) { 0 }
            } else 0

        private fun markRendered(context: Context, ids: IntArray) {
            context.getSharedPreferences(RENDER_PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet("ids", ids.map { it.toString() }.toSet())
                .putInt("boot", bootCount(context))
                .putLong("at", System.currentTimeMillis())
                .apply()
        }

        fun renderedRecently(context: Context, requestedIds: IntArray): Boolean {
            val p = context.getSharedPreferences(RENDER_PREFS, Context.MODE_PRIVATE)
            if (p.getInt("boot", -1) != bootCount(context)) return false
            if (System.currentTimeMillis() - p.getLong("at", 0L) > 5 * 60_000L) return false
            val known = p.getStringSet("ids", emptySet()) ?: emptySet()
            return requestedIds.isNotEmpty() && requestedIds.all { known.contains(it.toString()) }
        }

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
            markRendered(context, largeIds + smallIds)
        }

        /** يرسم كل الودجتات الحالية بآخر أسعار محفوظة بالكاش فوراً (بدون شبكة). */
        fun renderFromCache(context: Context) {
            if (!hasAnyWidget(context)) return
            val keys = FavoriteCityStore.getAll(context)
            val items = keys.map { WidgetPriceProvider.buildCachedItem(context, it) }
            pushAll(context, keys, items)
        }

        // تحديث واحد فقط بنفس الوقت: لو المستخدم ضغط الزر كذا مرة، أو فتح الهاتف وصار
        // onUpdate لودجتين، ما نبدأ تحديث جديد فوق اللي شغال (كل تحديث = طلبات شبكة +
        // رسم ودجت، وتراكمها كان يسبب تأخير بالشاشة).
        private val refreshing = AtomicBoolean(false)
        @Volatile private var refreshStartedAt = 0L
        private const val REFRESH_STALE_MS = 40_000L
        private const val REFRESH_WATCHDOG_MS = 25_000L

        /**
         * يجيب أسعار جديدة فعلية للمفضلات (لحد 3)، ويحدّث كل الودجتات بنتيجتها.
         * لو animate=true، سعر أول مفضلة يطلع بتأثير "عداد مبعثر" (أرقام عشوائية تستقر
         * تدريجياً على الرقم الصحيح). [onDone] ينادى مرة وحدة فقط، حتى لو فشلت الشبكة أو
         * تأخرت (مؤقّت أمان 25 ثانية) — مهم لأن goAsync لازم ينتهي وإلا يصير ANR.
         */
        fun refreshFavorite(context: Context, animate: Boolean = false, onDone: () -> Unit = {}) {
            val finished = AtomicBoolean(false)
            val finish = {
                if (finished.compareAndSet(false, true)) {
                    refreshing.set(false)
                    try { onDone() } catch (_: Exception) {}
                }
            }
            try {
                if (!hasAnyWidget(context)) { finish(); return }

                val now = SystemClock.elapsedRealtime()
                if (!refreshing.compareAndSet(false, true)) {
                    if (now - refreshStartedAt < REFRESH_STALE_MS) {
                        // تحديث شغال هسه: نسكّر هذا الطلب بدون ما نلمس الحالة.
                        try { onDone() } catch (_: Exception) {}
                        return
                    }
                    refreshing.set(true) // القديم عالق (>40ث): نكمل
                }
                refreshStartedAt = now

                val keys = FavoriteCityStore.getAll(context)
                if (keys.isEmpty()) {
                    renderFromCache(context)
                    finish()
                    return
                }

                // مؤقّت أمان: لو الشبكة علّقت، نرسم من الكاش وننهي.
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!finished.get()) {
                        renderFromCache(context)
                        finish()
                    }
                }, REFRESH_WATCHDOG_MS)

                val results = arrayOfNulls<PriceItem>(keys.size)
                val handled = BooleanArray(keys.size)
                val remaining = AtomicInteger(keys.size)
                keys.forEachIndexed { i, key ->
                    WidgetPriceProvider.fetchOne(context, key) { item ->
                        if (handled[i]) return@fetchOne // حماية من نداء مكرر لنفس العنصر
                        handled[i] = true
                        results[i] = item ?: WidgetPriceProvider.buildCachedItem(context, key)
                        if (item != null) {
                            WidgetRefreshScheduler.markRefreshed(context)
                            LastUpdateStore.save(context, System.currentTimeMillis())
                        }
                        if (remaining.decrementAndGet() == 0 && !finished.get()) {
                            val items = results.toList()
                            try {
                                if (animate) animateReveal(context, keys, items, finish)
                                else {
                                    pushAll(context, keys, items)
                                    finish()
                                }
                            } catch (_: Exception) {
                                finish()
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                finish()
            }
        }

        /**
         * أنيميشن الأرقام: رسم كامل واحد بأول إطار، وبعدها تحديثات "جزئية" (partiallyUpdateAppWidget)
         * تغيّر نص السعر فقط (~12 إطار كل 75ms). قبل كان كل إطار يعيد بناء وإرسال الودجت كامل
         * (أيقونات، أعلام، أسماء، PendingIntents) ~30 مرة بثانية ونص، وهذا يحمّل الـ Launcher
         * والنظام ويسبب تأخير بالشاشة.
         */
        private fun animateReveal(
            context: Context,
            keys: List<String>,
            items: List<PriceItem?>,
            finish: () -> Unit
        ) {
            val finalPrice = items.firstOrNull()?.price?.removeSuffix(" د.ع")
            val plan = finalPrice?.let { DigitRevealAnimator.plan(it) }

            // لا يوجد سعر جديد فعلي (فشل الاتصال مثلاً) أو نص بدون أرقام — بدون أنيميشن.
            if (finalPrice == null || plan == null) {
                pushAll(context, keys, items)
                finish()
                return
            }

            val totalSteps = 12
            val frameMs = 75L
            val manager = AppWidgetManager.getInstance(context)
            val largeIds = idsOf(context, FavoriteWidgetProvider::class.java)
            val smallIds = idsOf(context, FavoriteWidgetSmallProvider::class.java)

            pushAll(context, keys, items, DigitRevealAnimator.frameText(finalPrice, plan, 1, totalSteps))

            val handler = Handler(Looper.getMainLooper())
            var step = 1
            fun nextFrame() {
                step++
                val text = if (step >= totalSteps) finalPrice
                else DigitRevealAnimator.frameText(finalPrice, plan, step, totalSteps)
                try {
                    if (largeIds.isNotEmpty()) {
                        manager.partiallyUpdateAppWidget(
                            largeIds,
                            RemoteViews(context.packageName, R.layout.widget_favorite_price)
                                .apply { setTextViewText(R.id.widgetPrice1, text) }
                        )
                    }
                    if (smallIds.isNotEmpty()) {
                        manager.partiallyUpdateAppWidget(
                            smallIds,
                            RemoteViews(context.packageName, R.layout.widget_favorite_small)
                                .apply { setTextViewText(R.id.smallPrice, text) }
                        )
                    }
                } catch (_: Exception) {
                    // لو فشل تحديث جزئي نرسم النتيجة النهائية كاملة وننهي.
                    pushAll(context, keys, items)
                    finish()
                    return
                }
                if (step < totalSteps) handler.postDelayed(::nextFrame, frameMs) else finish()
            }
            handler.postDelayed(::nextFrame, frameMs)
        }

        /** هل الشاشة شغالة؟ (لا نشغّل أنيميشن بالخلفية والشاشة مطفية). */
        fun isScreenOn(context: Context): Boolean = try {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        } catch (_: Exception) {
            true
        }

        /** تحديث من داخل BroadcastReceiver: goAsync ينتهي مرة وحدة مضمون حتى لو صار خطأ. */
        fun refreshAsync(receiver: BroadcastReceiver, context: Context, animate: Boolean) {
            val pending = receiver.goAsync() ?: return
            val done = AtomicBoolean(false)
            val finish = {
                if (done.compareAndSet(false, true)) {
                    try { pending.finish() } catch (_: Exception) {}
                }
            }
            try {
                refreshFavorite(context, animate) { finish() }
            } catch (_: Exception) {
                finish()
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // أندرويد يستدعي onUpdate بعد إعادة تشغيل الهاتف وبعد تحديث التطبيق، والمنبهات
        // تنمسح بالحالتين — فنعيد الجدولة هنا (آمنة للتكرار، ما تسوي منبه ثاني).
        WidgetRefreshScheduler.scheduleIfMissing(context)
        // رسمناه قبل دقايق لنفس الودجتات؟ ما نبنيه من جديد ولا نجلب (غالباً الـ Launcher رجع للشاشة).
        if (renderedRecently(context, appWidgetIds)) return
        renderFromCache(context)
        // طلب الشبكة فقط لو يستاهل (ماكو تحديث قبل دقايق + في نت)، حتى ما يصير تحديث
        // لكل onUpdate (مثلاً لودجتين بنفس الوقت أو تغيير حجم الودجت).
        if (WidgetRefreshScheduler.shouldAutoRefresh(context)) {
            refreshAsync(this, context, animate = false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // الضغط اليدوي: الأنيميشن يظهر دايماً (المستخدم يشوف الودجت).
            ACTION_REFRESH -> refreshAsync(this, context, animate = true)
            WidgetRefreshScheduler.ACTION_HOURLY_REFRESH -> {
                // تحديث تلقائي: نتخطاه لو ماكو نت أو تحدّث قبل دقايق (توفير بطارية وشبكة).
                if (!WidgetRefreshScheduler.shouldAutoRefresh(context)) return
                // الأنيميشن بس لو الشاشة شغالة؛ بالخلفية تحديث عادي بدون إطارات زايدة.
                refreshAsync(this, context, animate = isScreenOn(context))
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

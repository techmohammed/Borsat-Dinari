package com.mohammed.currencyapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class CalculatorWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val ACTION_KEY = "com.mohammed.currencyapp.ACTION_CALCULATOR_KEY"
        private const val ACTION_SELECT = "com.mohammed.currencyapp.ACTION_CALCULATOR_SELECT"
        private const val ACTION_REFRESH = "com.mohammed.currencyapp.ACTION_CALCULATOR_REFRESH"
        private const val EXTRA_KEY = "calculator_key"
        private const val PREFS = "calculator_widget_values"
        private const val VALUE_PREFIX = "value_"
        private const val INDEX_PREFIX = "index_"
        private var cachedItems: List<PriceItem>? = null
        private val calculatorKeys = listOf(
            "baghdad", "basra", "erbil", "kirkuk", "sulaymaniyah", "duhok", "mosul",
            "karbala", "najaf", "wasit", "muthanna", "salahaldin", "diyala", "dhiqar",
            "qadisiyyah", "babil", "maysan", "anbar", "USD_OFFICIAL", "USD_BAGHDAD",
            "FX_EUR", "FX_GBP", "TRY_INVESTING", "IRR_TELEGRAM"
        )

        private val keyIds = mapOf(
            "9" to R.id.calculatorKey9, "8" to R.id.calculatorKey8, "7" to R.id.calculatorKey7,
            "6" to R.id.calculatorKey6, "5" to R.id.calculatorKey5, "4" to R.id.calculatorKey4,
            "3" to R.id.calculatorKey3, "2" to R.id.calculatorKey2, "1" to R.id.calculatorKey1,
            "0" to R.id.calculatorKey0, "." to R.id.calculatorKeyPoint,
            "delete" to R.id.calculatorKeyDelete
        )

        fun refreshFromSharedCache(context: Context) {
            cachedItems = null
            CalculatorWidgetProvider().renderAllFromDiskCache(context)
        }

        /** يحفظ وقتاً مشتركاً ويرسم كل ويدجتات الحاسبة والمفضلات من الكاش. */
        fun syncAllWidgetsAt(context: Context, millis: Long) {
            LastUpdateStore.save(context, millis)
            LastUpdateStore.saveCalculator(context, millis)
            cachedItems = null
            CalculatorWidgetProvider().renderAllFromDiskCache(context)
            FavoriteWidgetProvider.renderFromCache(context)
        }

        /** مزامنة الحاسبة مع آخر تحديث مكتمل داخل التطبيق. */
        fun syncFromMainRefresh(context: Context, millis: Long) {
            syncAllWidgetsAt(context, millis)
        }

        fun refreshLanguage(context: Context) {
            cachedItems = null
            CalculatorWidgetProvider().renderAllFromSharedCache(context)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ids.forEach { renderWithItems(context, manager, it, emptyList()) }
        loadItems(context, force = true) { items ->
            try {
                ids.forEach { renderWithItems(context, manager, it, items) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        var finished = false
        fun finishAsync() {
            if (!finished) {
                finished = true
                pending.finish()
            }
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (intent.action) {
            ACTION_KEY -> {
                val key = intent.getStringExtra(EXTRA_KEY) ?: run { finishAsync(); return }
                val old = prefs.getString(VALUE_PREFIX + widgetId, "") ?: ""
                val value = when (key) {
                    "delete" -> old.dropLast(1)
                    "." -> if (!old.contains('.')) {
                        if (old.isEmpty()) "0." else "$old."
                    } else old
                    else -> appendDigit(old, key)
                }
                prefs.edit().putString(VALUE_PREFIX + widgetId, value).apply()
                // الإدخال يظهر فوراً؛ لا ننتظر شبكة أو تحميل القائمة لكل ضغطة.
                renderWithItems(context, AppWidgetManager.getInstance(context), widgetId,
                    cachedItems ?: cachedItemsFromDisk(context))
                finishAsync()
            }
            ACTION_SELECT -> {
                val chosen = intent.getIntExtra("calculator_index", -1)
                val delta = intent.getIntExtra("calculator_delta", 0)
                // السهم يغير الفهرس الآن، لا يخزن خطوة تنتظر زر التحديث.
                // نقرأ آخر أسعار القرص فوراً حتى يعمل الودجت والتطبيق مغلق.
                val items = cachedItems ?: cachedItemsFromDisk(context)
                if (items.isNotEmpty()) {
                    val old = prefs.getInt(INDEX_PREFIX + widgetId, 0)
                    val next = if (chosen >= 0) {
                        chosen.coerceIn(0, items.lastIndex)
                    } else {
                        ((old + delta) % items.size + items.size) % items.size
                    }
                    prefs.edit()
                        .putInt(INDEX_PREFIX + widgetId, next)
                        .putInt("step_$widgetId", 0)
                        .apply()
                }
                renderWithItems(context, AppWidgetManager.getInstance(context), widgetId, items)
                finishAsync()
            }
            ACTION_REFRESH -> {
                // تحديث مستقل للحاسبة؛ لا ننتظر تحديث ويدجت المفضلات ولا نقفل زره.
                renderWithItems(context, AppWidgetManager.getInstance(context), widgetId,
                    cachedItems ?: cachedItemsFromDisk(context))
                TelegramScraperRepository.invalidateSharedMessages()
                refreshOne(context, widgetId, force = true, onDone = {
                    val sharedTime = LastUpdateStore.loadCalculator(context)
                        ?: System.currentTimeMillis()
                    syncAllWidgetsAt(context, sharedTime)
                    finishAsync()
                })
            }
            else -> finishAsync()
        }
    }

    private fun renderAllFromSharedCache(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, CalculatorWidgetProvider::class.java))
        if (ids.isEmpty()) return
        loadItems(context, force = false) { items -> ids.forEach { renderWithItems(context, manager, it, items) } }
    }

    private fun renderAllFromDiskCache(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(android.content.ComponentName(context, CalculatorWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val items = cachedItemsFromDisk(context)
        cachedItems = items
        ids.forEach { renderWithItems(context, manager, it, items) }
    }

    private fun refreshOne(context: Context, widgetId: Int, force: Boolean, onDone: () -> Unit = {}) {
        val manager = AppWidgetManager.getInstance(context)
        loadItems(context, force) { items ->
            if (force) LastUpdateStore.saveCalculator(context, System.currentTimeMillis())
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val step = prefs.getInt("step_$widgetId", 0)
            if (step != 0 && items.isNotEmpty()) {
                val old = prefs.getInt(INDEX_PREFIX + widgetId, 0)
                val next = ((old + step) % items.size + items.size) % items.size
                prefs.edit()
                    .putInt(INDEX_PREFIX + widgetId, next)
                    .putInt("step_$widgetId", 0)
                    .apply()
            }
            renderWithItems(context, manager, widgetId, items)
            animateResult(context, manager, widgetId, items, onDone)
        }
    }

    private fun renderWithItems(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        items: List<PriceItem>
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_calculator)
        views.setImageViewBitmap(
            R.id.calculatorWidgetTitle,
            WidgetText.render(context, context.getString(R.string.app_name), 17f, Color.WHITE)
        )
        views.setContentDescription(R.id.calculatorWidgetTitle, context.getString(R.string.app_name))

        val last = LastUpdateStore.loadCalculator(context)
        views.setViewVisibility(R.id.calculatorWidgetUpdateLabel, View.GONE)
        views.setTextViewText(
            R.id.calculatorWidgetUpdateTime,
            if (last != null) UpdateTimeFormat.timeAboveDate(context, last) else "—"
        )

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(VALUE_PREFIX + widgetId, "") ?: ""
        views.setTextViewText(R.id.calculatorWidgetAmount, format(value))

        val item = items.getOrNull(prefs.getInt(INDEX_PREFIX + widgetId, 0))
        if (item == null) {
            views.setImageViewResource(R.id.calculatorWidgetExchangeFlag, R.drawable.flag_iq)
            views.setImageViewBitmap(R.id.calculatorWidgetExchangeName,
                WidgetText.render(context, context.getString(R.string.calculator_exchange_label), 14f, Color.DKGRAY))
            views.setTextViewText(R.id.calculatorWidgetResult, "—")
            views.setImageViewBitmap(
                R.id.calculatorWidgetUnit,
                WidgetText.render(context, "", 14f, Color.DKGRAY)
            )
        } else {
            views.setImageViewResource(R.id.calculatorWidgetExchangeFlag, flagResource(context, item.flagDrawable))
            views.setImageViewBitmap(R.id.calculatorWidgetExchangeName,
                WidgetText.render(context, item.name, 15f, Color.DKGRAY))
            val amount = value.toDoubleOrNull()
            val price = extractNumber(item.price)
            val result = if (amount != null && price != null && price != 0.0) {
                if (item.cityKey == "FX_EUR" || item.cityKey == "FX_GBP") amount * 100.0 / price
                else amount * price / 100.0
            } else null
            views.setTextViewText(
                R.id.calculatorWidgetResult,
                resultText(result)
            )
            views.setImageViewBitmap(
                R.id.calculatorWidgetUnit,
                WidgetText.render(context, outputUnit(context, item), 14f, Color.DKGRAY)
            )
        }

        keyIds.entries.forEachIndexed { index, (key, viewId) ->
            val intent = Intent(context, CalculatorWidgetProvider::class.java).apply {
                action = ACTION_KEY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_KEY, key)
            }
            views.setOnClickPendingIntent(viewId, broadcast(context, intent, widgetRequestCode(widgetId, index + 1)))
        }
        views.setOnClickPendingIntent(R.id.calculatorWidgetExchangeArrowUp,
            selectPending(context, widgetId, -1))
        views.setOnClickPendingIntent(R.id.calculatorWidgetExchangeArrowDown,
            selectPending(context, widgetId, 1))
        // الضغط على جسم خانة البورصة يبقى داخل الويدجت ولا يفتح التطبيق.
        views.setOnClickPendingIntent(R.id.calculatorWidgetExchange,
            selectPending(context, widgetId, 0))

        val refreshIntent = Intent(context, CalculatorWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(R.id.calculatorWidgetRefresh, broadcast(context, refreshIntent, widgetRequestCode(widgetId, 30)))
        views.setOnClickPendingIntent(R.id.calculatorWidgetUpdateArea, broadcast(context, refreshIntent, widgetRequestCode(widgetId, 31)))
        views.setOnClickPendingIntent(R.id.calculatorWidgetUpdateLabel, broadcast(context, refreshIntent, widgetRequestCode(widgetId, 32)))
        views.setOnClickPendingIntent(R.id.calculatorWidgetUpdateTime, broadcast(context, refreshIntent, widgetRequestCode(widgetId, 33)))
        manager.updateAppWidget(widgetId, views)
    }

    /** يعيد نتيجة الحاسبة كنص نهائي حتى تستخدمه واجهة الرسم والأنيميشن معاً. */
    private fun resultTextForWidget(context: Context, widgetId: Int, items: List<PriceItem>): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(VALUE_PREFIX + widgetId, "") ?: ""
        val item = items.getOrNull(prefs.getInt(INDEX_PREFIX + widgetId, 0)) ?: return "—"
        val amount = value.toDoubleOrNull()
        val price = extractNumber(item.price)
        val result = if (amount != null && price != null && price != 0.0) {
            if (item.cityKey == "FX_EUR" || item.cityKey == "FX_GBP") amount * 100.0 / price
            else amount * price / 100.0
        } else null
        return resultText(result)
    }

    private fun resultText(result: Double?): String =
        if (result != null && result.isFinite()) {
            DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US)).format(result)
        } else "—"

    /** حركة Matrix خفيفة لرقم الناتج بعد الضغط على تحديث الحاسبة. */
    private fun animateResult(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        items: List<PriceItem>,
        onDone: () -> Unit
    ) {
        val finalText = resultTextForWidget(context, widgetId, items)
        val plan = DigitRevealAnimator.plan(finalText)
        if (plan == null) {
            onDone()
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val totalSteps = 12
        val frameMs = 65L
        var step = 0
        fun nextFrame() {
            step++
            try {
                val frame = if (step >= totalSteps) finalText
                else DigitRevealAnimator.frameText(finalText, plan, step, totalSteps)
                manager.partiallyUpdateAppWidget(
                    widgetId,
                    RemoteViews(context.packageName, R.layout.widget_calculator).apply {
                        setTextViewText(R.id.calculatorWidgetResult, frame)
                    }
                )
            } catch (_: Exception) {
                onDone()
                return
            }
            if (step < totalSteps) handler.postDelayed({ nextFrame() }, frameMs) else onDone()
        }
        handler.post(::nextFrame)
    }

    private fun broadcast(context: Context, intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun selectPending(context: Context, widgetId: Int, delta: Int): PendingIntent {
        val intent = Intent(context, CalculatorWidgetProvider::class.java).apply {
            action = ACTION_SELECT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra("calculator_delta", delta)
        }
        val slot = when (delta) { -1 -> 20; 1 -> 21; else -> 22 }
        return broadcast(context, intent, widgetRequestCode(widgetId, slot))
    }

    private fun widgetRequestCode(widgetId: Int, slot: Int): Int = widgetId * 1000 + slot

    private fun loadItems(context: Context, force: Boolean, callback: (List<PriceItem>) -> Unit) {
        if (!force) {
            cachedItems?.let { callback(it); return }
        }
        var cities: List<PriceItem>? = null
        var currencies: List<PriceItem>? = null
        fun finishIfReady() {
            if (cities == null || currencies == null) return
            val favorites = FavoriteCityStore.getAll(context)
            val all = (cities!! + currencies!!).distinctBy {
                it.cityKey ?: "${it.flagDrawable}:${it.name}:${it.unit}"
            }.sortedWith(compareBy<PriceItem> {
                val index = favorites.indexOf(it.cityKey ?: "")
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy { it.name })
            cachedItems = all
            callback(all)
        }
        TelegramScraperRepository.fetch(context) { cities = it; finishIfReady() }
        CurrenciesRepository.fetch(context) { currencies = it; finishIfReady() }
    }

    /** قائمة فورية من الكاش الدائم، لا تبدأ اتصال شبكة عند لمس السهم أو الأرقام. */
    private fun cachedItemsFromDisk(context: Context): List<PriceItem> {
        val favorites = FavoriteCityStore.getAll(context)
        return calculatorKeys.mapNotNull { WidgetPriceProvider.buildCachedItem(context, it) }
            .sortedWith(compareBy<PriceItem> {
                val index = favorites.indexOf(it.cityKey ?: "")
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy { it.name })
    }

    private fun appendDigit(current: String, digit: String): String {
        if (current.isEmpty()) return digit
        if (!current.contains('.') && current == "0") return if (digit == "0") "0" else digit
        return current + digit
    }

    private fun flagResource(context: Context, flag: String): Int = context.resources.getIdentifier(
        "flag_${flag.lowercase(Locale.US)}", "drawable", context.packageName
    ).takeIf { it != 0 } ?: R.drawable.flag_iq

    private fun outputUnit(context: Context, item: PriceItem): String = when (item.cityKey) {
        "TRY_INVESTING" -> context.getString(R.string.currency_try_unit)
        "IRR_TELEGRAM" -> context.getString(R.string.currency_irr_unit)
        "FX_EUR" -> context.getString(R.string.currency_eur_unit)
        "FX_GBP" -> context.getString(R.string.currency_gbp_unit)
        else -> context.getString(R.string.currency_iqd_unit)
    }

    private fun extractNumber(value: String): Double? {
        val match = Regex("[0-9]+(?:[,.][0-9]+)*").find(normalizeDigits(value)) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    private fun normalizeDigits(value: String): String = buildString {
        value.forEach { c -> append(when (c) {
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            else -> c
        }) }
    }

    private fun format(value: String): String {
        if (value.isEmpty()) return "0"
        val parts = value.split('.', limit = 2)
        val digits = parts[0].filter { it.isDigit() }.ifEmpty { "0" }
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (parts.size == 2) "$grouped.${parts[1].filter { it.isDigit() }}" else grouped
    }
}

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
        private const val ACTION_SWAP = "com.mohammed.currencyapp.ACTION_CALCULATOR_SWAP"
        private const val ACTION_REFRESH = "com.mohammed.currencyapp.ACTION_CALCULATOR_REFRESH"
        private const val EXTRA_KEY = "calculator_key"
        private const val PREFS = "calculator_widget_values"
        private const val VALUE_PREFIX = "value_"
        private const val SOURCE_INDEX_PREFIX = "source_index_"
        private const val DESTINATION_INDEX_PREFIX = "destination_index_"
        private var cachedItems: List<PriceItem>? = null
        private val calculatorKeys = listOf(
            "baghdad", "basra", "erbil", "kirkuk", "sulaymaniyah", "duhok", "mosul",
            "karbala", "najaf", "wasit", "muthanna", "salahaldin", "diyala", "dhiqar",
            "qadisiyyah", "babil", "maysan", "anbar", "USD_OFFICIAL", "USD_BAGHDAD",
            "FX_EUR", "FX_GBP", "FX_JOD", "FX_AED", "FX_KWD", "FX_EGP",
            "TRY_INVESTING", "FX_SAR", "FX_SYP", "IRR_TELEGRAM"
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
                val isSource = intent.getBooleanExtra("calculator_source", false)
                val delta = intent.getIntExtra("calculator_delta", 1)
                val items = cachedItems ?: cachedItemsFromDisk(context)
                if (items.isNotEmpty()) {
                    val options = calculatorOptions(context, items)
                    val indexKey = (if (isSource) SOURCE_INDEX_PREFIX else DESTINATION_INDEX_PREFIX) + widgetId
                    val old = prefs.getInt(indexKey, if (isSource) 0 else 1.coerceAtMost(options.lastIndex))
                    val next = ((old + delta) % options.size + options.size) % options.size
                    prefs.edit().putInt(indexKey, next).apply()
                }
                renderWithItems(context, AppWidgetManager.getInstance(context), widgetId, items)
                finishAsync()
            }
            ACTION_SWAP -> {
                val source = prefs.getInt(SOURCE_INDEX_PREFIX + widgetId, 0)
                val options = calculatorOptions(context, cachedItems ?: cachedItemsFromDisk(context))
                val destination = prefs.getInt(DESTINATION_INDEX_PREFIX + widgetId, 1.coerceAtMost(options.lastIndex))
                prefs.edit()
                    .putInt(SOURCE_INDEX_PREFIX + widgetId, destination)
                    .putInt(DESTINATION_INDEX_PREFIX + widgetId, source)
                    .apply()
                renderWithItems(context, AppWidgetManager.getInstance(context), widgetId,
                    cachedItems ?: cachedItemsFromDisk(context))
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

        val options = calculatorOptions(context, items)
        val sourceIndex = prefs.getInt(SOURCE_INDEX_PREFIX + widgetId, 0).coerceIn(0, options.lastIndex)
        val destinationIndex = prefs.getInt(DESTINATION_INDEX_PREFIX + widgetId, 1.coerceAtMost(options.lastIndex))
            .coerceIn(0, options.lastIndex)
        val source = options[sourceIndex]
        val destination = options[destinationIndex]
        // أسماء البورصات بنفس حجم وحدة العملة، ثابتة وغير متكيفة مع عرض الحقل.
        views.setImageViewResource(R.id.calculatorWidgetSourceFlag, flagResource(context, source.flagDrawable))
        views.setImageViewBitmap(R.id.calculatorWidgetSourceName,
            WidgetText.render(context, source.name, 14f, Color.DKGRAY))
        views.setContentDescription(R.id.calculatorWidgetSource, source.name)
        views.setImageViewResource(R.id.calculatorWidgetExchangeFlag, flagResource(context, destination.flagDrawable))
        views.setImageViewBitmap(R.id.calculatorWidgetExchangeName,
            WidgetText.render(context, destination.name, 14f, Color.DKGRAY))
        views.setContentDescription(R.id.calculatorWidgetExchange, destination.name)
        val amount = value.toDoubleOrNull()
        val sourceRate = ratePer100Usd(source)
        val destinationRate = ratePer100Usd(destination)
        val result = if (amount != null && sourceRate != null && destinationRate != null && sourceRate > 0.0) {
            amount * destinationRate / sourceRate
        } else null
        views.setTextViewText(R.id.calculatorWidgetResult, resultText(result))
        views.setImageViewBitmap(R.id.calculatorWidgetUnit,
            WidgetText.render(context, outputUnit(context, destination), 14f, Color.DKGRAY))

        keyIds.entries.forEachIndexed { index, (key, viewId) ->
            val intent = Intent(context, CalculatorWidgetProvider::class.java).apply {
                action = ACTION_KEY
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_KEY, key)
            }
            views.setOnClickPendingIntent(viewId, broadcast(context, intent, widgetRequestCode(widgetId, index + 1)))
        }
        views.setOnClickPendingIntent(R.id.calculatorWidgetSourceArrowUp,
            selectPending(context, widgetId, true, -1))
        views.setOnClickPendingIntent(R.id.calculatorWidgetSourceArrowDown,
            selectPending(context, widgetId, true, 1))
        views.setOnClickPendingIntent(R.id.calculatorWidgetExchangeArrowUp,
            selectPending(context, widgetId, false, -1))
        views.setOnClickPendingIntent(R.id.calculatorWidgetExchangeArrowDown,
            selectPending(context, widgetId, false, 1))
        val swapIntent = Intent(context, CalculatorWidgetProvider::class.java).apply {
            action = ACTION_SWAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setOnClickPendingIntent(R.id.calculatorWidgetSwap,
            broadcast(context, swapIntent, widgetRequestCode(widgetId, 24)))

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
        val options = calculatorOptions(context, items)
        val source = options.getOrNull(prefs.getInt(SOURCE_INDEX_PREFIX + widgetId, 0)) ?: return "—"
        val destination = options.getOrNull(
            prefs.getInt(DESTINATION_INDEX_PREFIX + widgetId, 1.coerceAtMost(options.lastIndex))
        ) ?: return "—"
        val amount = value.toDoubleOrNull()
        val sourceRate = ratePer100Usd(source)
        val destinationRate = ratePer100Usd(destination)
        val result = if (amount != null && sourceRate != null && destinationRate != null && sourceRate > 0.0) {
            amount * destinationRate / sourceRate
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

    private fun selectPending(context: Context, widgetId: Int, isSource: Boolean, delta: Int): PendingIntent {
        val intent = Intent(context, CalculatorWidgetProvider::class.java).apply {
            action = ACTION_SELECT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra("calculator_source", isSource)
            putExtra("calculator_delta", delta)
        }
        val slot = when {
            isSource && delta < 0 -> 20
            isSource -> 21
            delta < 0 -> 22
            else -> 23
        }
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
            })
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
            })
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
        "CALC_USD" -> context.getString(R.string.currency_us_dollar)
        "TRY_INVESTING" -> context.getString(R.string.currency_try_unit)
        "IRR_TELEGRAM" -> context.getString(R.string.currency_irr_unit)
        "FX_EUR" -> context.getString(R.string.currency_eur_unit)
        "FX_GBP" -> context.getString(R.string.currency_gbp_unit)
        "FX_JOD" -> context.getString(R.string.currency_jod_unit)
        "FX_AED" -> context.getString(R.string.currency_aed_unit)
        "FX_KWD" -> context.getString(R.string.currency_kwd_unit)
        "FX_EGP" -> context.getString(R.string.currency_egp_unit)
        "FX_SAR" -> context.getString(R.string.currency_sar_unit)
        "FX_SYP" -> context.getString(R.string.currency_syp_unit)
        else -> context.getString(R.string.currency_iqd_unit)
    }

    private fun extractNumber(value: String): Double? {
        val match = Regex("[0-9]+(?:[,.][0-9]+)*").find(normalizeDigits(value)) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    private fun calculatorOptions(context: Context, items: List<PriceItem>): List<PriceItem> {
        val usd = PriceItem(
            name = context.getString(R.string.currency_us_dollar),
            flagDrawable = "us",
            price = "100",
            unit = "100$",
            cityKey = "CALC_USD"
        )
        return listOf(usd) + items
    }

    private fun ratePer100Usd(item: PriceItem): Double? {
        if (item.cityKey == "CALC_USD") return 100.0
        val price = extractNumber(item.price) ?: return null
        return if (item.cityKey == "FX_EUR" || item.cityKey == "FX_GBP") {
            if (price == 0.0) null else 10_000.0 / price
        } else price
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

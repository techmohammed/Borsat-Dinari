package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * كل العملات العالمية (الدولار الرسمي، اليورو، الپاوند، الليرة التركية،
 * التومان الإيراني) تُقرأ الآن حصراً من آخر منشور بقناتنا الخاصة "بورصة
 * ديناري" عبر TelegramScraperRepository — بدون أي API خارجي (open.er-api.com)
 * ولا كشط investing.com، بنفس مبدأ أسعار المدن.
 */
object CurrenciesRepository {

    // إذا مرت 3 ساعات وأكثر بدون سعر جديد فعلي، نلوّن العنصر رصاصي (نفس مبدأ
    // TelegramScraperRepository لأسعار المدن).
    private const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class CurrencyDef(
        val code: String,
        val name: String,
        val flag: String,
        val label: String,
        val dollarBase: Boolean,
        val unit: String
    )

    private val defs = listOf(
        CurrencyDef("EUR", "يورو", "eu", "$", false, "100€"),
        CurrencyDef("GBP", "پاوند", "gb", "$", false, "100£")
    )

    private val sample = listOf(
        PriceItem("الرسمي", "us", "—", "100$", Trend.FLAT, "USD_OFFICIAL"),
        PriceItem("يورو", "eu", "\$ 115.10", "100€", Trend.FLAT),
        PriceItem("پاوند", "gb", "\$ 134.00", "100£", Trend.FLAT),
        PriceItem("ليرة", "tr", "—", "100$", Trend.FLAT),
        PriceItem("تومان", "ir", "—", "100$", Trend.FLAT),
    )

    fun fetch(context: Context, onResult: (List<PriceItem>) -> Unit) {
        executor.execute {
            val borsatDinariMessages = TelegramScraperRepository.fetchTrustedBorsatDinariMessages()

            val items = mutableListOf<PriceItem>()
            fetchOfficialUsd(context, borsatDinariMessages)?.let { items.add(it) }
            items.addAll(fetchEuroAndPound(context, borsatDinariMessages))

            val tryItem = fetchTryFromBorsatDinari(context, borsatDinariMessages)
            items.add(tryItem ?: sample[3])

            val telegramRates = TelegramScraperRepository.fetchCurrencyRatesBlocking()
            val tomanItem = buildTelegramCurrencyItem(
                context = context,
                cacheKey = "IRR_TELEGRAM",
                name = "تومان",
                flag = "ir",
                value = telegramRates.irrPer100Usd,
                unit = "100$",
                suffix = ""
            )
            items.add(tomanItem ?: sample[4])

            mainHandler.post { onResult(items) }
        }
    }

    /**
     * نفس مبدأ TelegramScraperRepository.fetchOne — تحديث مستهدف لعملة وحدة
     * بس (يورو، پاوند، ليرة، أو تومان)، تُستخدم من ودجت الشاشة الرئيسية.
     */
    fun fetchOne(context: Context, key: String, onResult: (PriceItem?) -> Unit) {
        executor.execute {
            val result: PriceItem? = when {
                key == "USD_OFFICIAL" -> {
                    val messages = TelegramScraperRepository.fetchTrustedBorsatDinariMessages()
                    fetchOfficialUsd(context, messages)
                }
                key == "FX_EUR" || key == "FX_GBP" -> {
                    val messages = TelegramScraperRepository.fetchTrustedBorsatDinariMessages()
                    fetchEuroAndPound(context, messages).find { it.cityKey == key }
                }
                key == "TRY_INVESTING" -> {
                    val messages = TelegramScraperRepository.fetchTrustedBorsatDinariMessages()
                    fetchTryFromBorsatDinari(context, messages)
                }
                key == "IRR_TELEGRAM" -> {
                    val rates = TelegramScraperRepository.fetchCurrencyRatesBlocking()
                    buildTelegramCurrencyItem(context, "IRR_TELEGRAM", "تومان", "ir", rates.irrPer100Usd, "100$", "")
                }
                else -> null
            }
            mainHandler.post { onResult(result) }
        }
    }

    /** يبني PriceItem من الكاش المحلي فقط بدون أي اتصال شبكة — لعرض فوري بالودجت. */
    fun buildCachedItem(context: Context, key: String): PriceItem? {
        return when {
            key == "USD_OFFICIAL" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem("الرسمي", "us", "${formatLong(value)} د.ع", "100$", trend, key)
            }
            key == "FX_EUR" || key == "FX_GBP" -> {
                val code = key.removePrefix("FX_")
                val def = defs.find { it.code == code } ?: return null
                val cache = FxCache(context)
                val value = cache.load(code) ?: return null
                val trend = cache.loadTrend(code) ?: Trend.UP
                val priceText = if (def.dollarBase) {
                    "${formatNumber(value)} ${def.label}"
                } else {
                    "${def.label} ${String.format(Locale.US, "%.2f", value)}"
                }
                PriceItem(def.name, def.flag, priceText, def.unit, trend, key)
            }
            key == "TRY_INVESTING" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem("ليرة", "tr", formatLong(value), "100\$", trend, key)
            }
            key == "IRR_TELEGRAM" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem("تومان", "ir", formatLong(value), "100\$", trend, key)
            }
            key == "USD_BAGHDAD" -> {
                val cache = FxCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem("دولار", "us", "${String.format(Locale.US, "%,d", value.toLong())} د.ع", "100\$", trend, key)
            }
            else -> null
        }
    }

    /** أحدث سعر رسمي للدولار لكل 100$ من آخر منشور بقناة بورصة ديناري. */
    private fun fetchOfficialUsd(context: Context, messages: List<Pair<String, String>>): PriceItem? {
        val cache = FxTextCache(context)
        val cacheKey = "USD_OFFICIAL"

        var bestTimestamp: String? = null
        var current: Long? = null
        for ((ts, chunk) in messages) {
            if (bestTimestamp != null && ts <= bestTimestamp) continue
            val value = TelegramScraperRepository.extractBorsatDinariOfficialUsd(chunk) ?: continue
            bestTimestamp = ts
            current = value
        }

        val previous = cache.load(cacheKey)
        val effective = current ?: previous ?: return null
        val trend = when {
            current == null || previous == null -> cache.loadTrend(cacheKey) ?: Trend.UP
            current > previous -> Trend.UP
            current < previous -> Trend.DOWN
            else -> cache.loadTrend(cacheKey) ?: Trend.UP
        }

        val now = System.currentTimeMillis()
        val valueChanged = current != null && current != previous
        if (valueChanged) {
            cache.saveUpdateTime(cacheKey, now)
            HistoryStore.record(context, cacheKey, "${formatLong(current!!)} د.ع", trend)
        }
        if (current != null) cache.save(cacheKey, current)

        val lastChangeTime = cache.loadUpdateTime(cacheKey)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend
        cache.saveTrend(cacheKey, finalTrend)

        return PriceItem("الرسمي", "us", "${formatLong(effective)} د.ع", "100$", finalTrend, cacheKey)
    }

    /**
     * يستخرج سعر الليرة التركية من آخر منشور بقناة بورصة ديناري.
     * (سابقاً كان يجيب من investing.com مباشرة أو open.er-api.com — استغنينا
     * عن الاثنين، ونعتمد قناتنا حصراً الآن، نفس بقية الأسعار).
     */
    private fun fetchTryFromBorsatDinari(context: Context, messages: List<Pair<String, String>>): PriceItem? {
        val cache = FxTextCache(context)
        val cacheKey = "TRY_INVESTING"

        var bestTimestamp: String? = null
        var current: Long? = null
        for ((ts, chunk) in messages) {
            if (bestTimestamp != null && ts <= bestTimestamp) continue
            val value = TelegramScraperRepository.extractBorsatDinariTry(chunk) ?: continue
            bestTimestamp = ts
            current = value
        }

        val previous = cache.load(cacheKey)
        val effective = current ?: previous ?: return null
        val trend = when {
            current == null || previous == null -> cache.loadTrend(cacheKey) ?: Trend.UP
            current > previous -> Trend.UP
            current < previous -> Trend.DOWN
            else -> cache.loadTrend(cacheKey) ?: Trend.UP
        }
        if (current != null) cache.save(cacheKey, current)

        val now = System.currentTimeMillis()
        val valueChanged = current != null && current != previous
        if (valueChanged) {
            cache.saveUpdateTime(cacheKey, now)
            HistoryStore.record(context, cacheKey, formatLong(current!!), trend)
        }
        val lastChangeTime = cache.loadUpdateTime(cacheKey)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend
        cache.saveTrend(cacheKey, finalTrend)

        return PriceItem(
            name = "ليرة",
            flagDrawable = "tr",
            price = formatLong(effective),
            unit = "100\$",
            trend = finalTrend,
            cityKey = cacheKey
        )
    }

    /** يستخرج اليورو والپاوند من آخر منشور بقناة بورصة ديناري. */
    private fun fetchEuroAndPound(context: Context, messages: List<Pair<String, String>>): List<PriceItem> {
        val cache = FxCache(context)
        val items = mutableListOf<PriceItem>()

        for (def in defs) {
            var bestTimestamp: String? = null
            var current: Double? = null
            for ((ts, chunk) in messages) {
                if (bestTimestamp != null && ts <= bestTimestamp) continue
                val value = TelegramScraperRepository.extractBorsatDinariDollarBased(chunk, def.name) ?: continue
                bestTimestamp = ts
                current = value
            }

            val previous = cache.load(def.code)
            // لو ما لكينا سعر بآخر منشور، نرجع لآخر قيمة محفوظة بدل ما نسقط
            // العملة كلياً من القائمة.
            val effective = current ?: previous ?: continue

            val trend = if (current != null && previous != null) {
                when {
                    current > previous + 0.0001 -> Trend.UP
                    current < previous - 0.0001 -> Trend.DOWN
                    else -> cache.loadTrend(def.code) ?: Trend.UP
                }
            } else {
                cache.loadTrend(def.code) ?: Trend.UP
            }
            if (current != null) cache.save(def.code, current)

            val now = System.currentTimeMillis()
            // نقارن بفارق بسيط (0.0001) بدل المساواة التامة لأن هذي أرقام
            // عشرية (Double) وقد تختلف بجزء تافه بدون أي تغيير فعلي بالسعر المعروض.
            val valueChanged = current != null && (previous == null || kotlin.math.abs(current - previous) > 0.0001)
            if (valueChanged) {
                cache.saveUpdateTime(def.code, now)
            }
            val lastChangeTime = cache.loadUpdateTime(def.code)
            val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
            val finalTrend = if (isStale) Trend.FLAT else trend
            cache.saveTrend(def.code, finalTrend)

            val priceText = if (def.dollarBase) {
                "${formatNumber(effective)} ${def.label}"
            } else {
                "${def.label} ${String.format(Locale.US, "%.2f", effective)}"
            }
            if (valueChanged) {
                HistoryStore.record(context, "FX_${def.code}", priceText, trend)
            }
            items.add(PriceItem(def.name, def.flag, priceText, def.unit, finalTrend, "FX_${def.code}"))
        }
        return items
    }

    private fun buildTelegramCurrencyItem(
        context: Context,
        cacheKey: String,
        name: String,
        flag: String,
        value: Long?,
        unit: String,
        suffix: String
    ): PriceItem? {
        val cache = FxTextCache(context)
        val current = value?.takeIf { it > 0 }
        val previous = cache.load(cacheKey)
        val effective = current ?: previous ?: return null

        val trend = when {
            current == null || previous == null -> cache.loadTrend(cacheKey) ?: Trend.UP
            current > previous -> Trend.UP
            current < previous -> Trend.DOWN
            else -> cache.loadTrend(cacheKey) ?: Trend.UP
        }
        if (current != null) cache.save(cacheKey, current)

        val now = System.currentTimeMillis()
        val valueChanged = current != null && current != previous
        if (valueChanged) {
            cache.saveUpdateTime(cacheKey, now)
            val priceForHistory = if (suffix.isBlank()) formatLong(current!!) else "${formatLong(current!!)} $suffix"
            HistoryStore.record(context, cacheKey, priceForHistory, trend)
        }
        val lastChangeTime = cache.loadUpdateTime(cacheKey)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend
        cache.saveTrend(cacheKey, finalTrend)

        return PriceItem(
            name = name,
            flagDrawable = flag,
            price = if (suffix.isBlank()) formatLong(effective) else "${formatLong(effective)} $suffix",
            unit = unit,
            trend = finalTrend,
            cityKey = cacheKey
        )
    }

    private fun formatNumber(value: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.maximumFractionDigits = 0
        return nf.format(value)
    }

    private fun formatLong(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
}

class FxCache(context: Context) {
    private val prefs = context.getSharedPreferences("fx_cache", Context.MODE_PRIVATE)
    fun save(code: String, value: Double) {
        val old = if (prefs.contains(code)) prefs.getFloat(code, 0f) else null
        val newF = value.toFloat()
        val editor = prefs.edit().putFloat(code, newF)
        if (old != null && old != newF) editor.putFloat("${code}_delta", newF - old)
        editor.apply()
    }
    /** آخر فرق سعر مسجّل (موجب = صعود، سالب = نزول) أو null لو ما تغيّر بعد. */
    fun loadDelta(code: String): Double? =
        if (prefs.contains("${code}_delta")) prefs.getFloat("${code}_delta", 0f).toDouble() else null
    fun load(code: String): Double? {
        return if (prefs.contains(code)) prefs.getFloat(code, 0f).toDouble() else null
    }
    fun saveTrend(code: String, trend: Trend) {
        prefs.edit().putString("${code}_trend", trend.name).apply()
    }
    fun loadTrend(code: String): Trend? {
        return prefs.getString("${code}_trend", null)?.let {
            runCatching { Trend.valueOf(it) }.getOrNull()
        }
    }
    fun saveUpdateTime(code: String, timeMillis: Long) {
        prefs.edit().putLong("${code}_updated_at", timeMillis).apply()
    }
    fun loadUpdateTime(code: String): Long? {
        return if (prefs.contains("${code}_updated_at")) prefs.getLong("${code}_updated_at", 0L) else null
    }
}

class FxTextCache(context: Context) {
    private val prefs = context.getSharedPreferences("fx_text_cache", Context.MODE_PRIVATE)
    fun save(code: String, value: Long) {
        val old = if (prefs.contains(code)) prefs.getLong(code, 0L) else null
        val editor = prefs.edit().putLong(code, value)
        if (old != null && old != value) editor.putLong("${code}_delta", value - old)
        editor.apply()
    }
    /** آخر فرق سعر مسجّل (موجب = صعود، سالب = نزول) أو null لو ما تغيّر بعد. */
    fun loadDelta(code: String): Long? =
        if (prefs.contains("${code}_delta")) prefs.getLong("${code}_delta", 0L) else null
    fun load(code: String): Long? {
        return if (prefs.contains(code)) prefs.getLong(code, 0L) else null
    }
    fun saveTrend(code: String, trend: Trend) {
        prefs.edit().putString("${code}_trend", trend.name).apply()
    }
    fun loadTrend(code: String): Trend? {
        return prefs.getString("${code}_trend", null)?.let {
            runCatching { Trend.valueOf(it) }.getOrNull()
        }
    }
    fun saveUpdateTime(code: String, timeMillis: Long) {
        prefs.edit().putLong("${code}_updated_at", timeMillis).apply()
    }
    fun loadUpdateTime(code: String): Long? {
        return if (prefs.contains("${code}_updated_at")) prefs.getLong("${code}_updated_at", 0L) else null
    }
}

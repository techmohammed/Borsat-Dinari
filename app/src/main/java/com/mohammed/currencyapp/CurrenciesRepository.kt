package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * كل العملات العالمية تُقرأ حصراً من آخر منشور بقناتنا الخاصة "بورصة ديناري"
 * عبر TelegramScraperRepository، بنفس مبدأ أسعار المدن.
 */
object CurrenciesRepository {

    // إذا مرت 3 ساعات وأكثر بدون سعر جديد فعلي، نلوّن العنصر رصاصي (نفس مبدأ
    // TelegramScraperRepository لأسعار المدن).
    private const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("fx-repo"))
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class CurrencyDef(
        val code: String,
        val nameRes: Int,
        val flag: String,
        val label: String,
        val dollarBase: Boolean,
        val unit: String,
        val fractionDigits: Int = 2,
        val sourceNames: List<String> = emptyList()
    )

    private val defs = listOf(
        CurrencyDef("EUR", R.string.cur_eur, "eu", "$", false, "100€", 2, listOf("يورو")),
        CurrencyDef("GBP", R.string.cur_gbp, "gb", "$", false, "100£", 2, listOf("پاوند")),
        CurrencyDef("JOD", R.string.cur_jod, "jo", "", true, "100$", 2, listOf("دينار أردني", "دينار اردني")),
        CurrencyDef("AED", R.string.cur_aed, "ae", "", true, "100$", 2, listOf("درهم إماراتي", "درهم اماراتي")),
        CurrencyDef("KWD", R.string.cur_kwd, "kw", "", true, "100$", 2, listOf("دينار كويتي")),
        CurrencyDef("EGP", R.string.cur_egp, "eg", "", true, "100$", 0, listOf("جنيه مصري")),
        CurrencyDef("CNY", R.string.cur_cny, "cn", "", true, "100$", 2, listOf("يوان صيني", "يوان")),
        CurrencyDef("SAR", R.string.cur_sar, "sa", "", true, "100$", 2, listOf("ريال سعودي", "ريال سعودى")),
        CurrencyDef("SYP", R.string.cur_syp, "sy", "", true, "100$", 0, listOf("ليرة سورية", "ليرة سوريه", "ليرة سوريّة"))
    )

    private fun sample(context: Context) = listOf(
        PriceItem(context.getString(R.string.cur_usd_official), "us", "—", "100$", Trend.FLAT, "USD_OFFICIAL"),
        PriceItem(context.getString(R.string.cur_eur), "eu", "\$ 115.10", "100€", Trend.FLAT),
        PriceItem(context.getString(R.string.cur_gbp), "gb", "\$ 134.00", "100£", Trend.FLAT),
        PriceItem(context.getString(R.string.cur_try), "tr", "—", "100$", Trend.FLAT),
        PriceItem(context.getString(R.string.cur_irr), "ir", "—", "100$", Trend.FLAT),
        PriceItem(context.getString(R.string.cur_jod), "jo", "—", "100$", Trend.FLAT, "FX_JOD"),
        PriceItem(context.getString(R.string.cur_aed), "ae", "—", "100$", Trend.FLAT, "FX_AED"),
        PriceItem(context.getString(R.string.cur_kwd), "kw", "—", "100$", Trend.FLAT, "FX_KWD"),
        PriceItem(context.getString(R.string.cur_egp), "eg", "—", "100$", Trend.FLAT, "FX_EGP"),
        PriceItem(context.getString(R.string.cur_sar), "sa", "—", "100$", Trend.FLAT, "FX_SAR"),
        PriceItem(context.getString(R.string.cur_syp), "sy", "—", "100$", Trend.FLAT, "FX_SYP"),
        PriceItem(context.getString(R.string.cur_cny), "cn", "—", "100$", Trend.FLAT, "FX_CNY"),
    )

    fun fetch(context: Context, onResult: (List<PriceItem>) -> Unit) {
        executor.execute {
            val borsatDinariMessages = TelegramScraperRepository.fetchTrustedMessagesShared()
            val items = fetchItemsFromMessages(context, borsatDinariMessages)
            mainHandler.post { onResult(items) }
        }
    }

    /** Parses all currencies from an already-fetched Telegram page on a worker thread. */
    internal fun fetchItemsFromMessages(
        context: Context,
        borsatDinariMessages: List<Pair<String, String>>
    ): List<PriceItem> {
        val items = mutableListOf<PriceItem>()
        fetchOfficialUsd(context, borsatDinariMessages)?.let { items.add(it) }
        val parsedCurrencies = fetchCurrencyItems(context, borsatDinariMessages)
        val lateCurrencyKeys = setOf("FX_CNY", "FX_SAR", "FX_SYP")
        val alwaysListedKeys = setOf("FX_JOD", "FX_AED", "FX_KWD", "FX_EGP", "FX_CNY", "FX_SAR", "FX_SYP")
        val fallbackCurrencies = sample(context).filter {
            it.cityKey in alwaysListedKeys && parsedCurrencies.none { parsed -> parsed.cityKey == it.cityKey }
        }
        items.addAll(parsedCurrencies.filterNot { it.cityKey in lateCurrencyKeys })
        items.addAll(fallbackCurrencies.filterNot { it.cityKey in lateCurrencyKeys })

        val tryItem = fetchTryFromBorsatDinari(context, borsatDinariMessages)
        items.add(tryItem ?: sample(context)[3])
        items.addAll(parsedCurrencies.filter { it.cityKey in lateCurrencyKeys })
        items.addAll(fallbackCurrencies.filter { it.cityKey in lateCurrencyKeys })

        val telegramToman = TelegramScraperRepository.extractTomanFromMessages(borsatDinariMessages)
        val tomanItem = buildTelegramCurrencyItem(
            context = context,
            cacheKey = "IRR_TELEGRAM",
            name = context.getString(R.string.cur_irr),
            flag = "ir",
            value = telegramToman,
            unit = "100$",
            suffix = "",
            publishedAtMillis = TelegramScraperRepository.latestTomanTimestamp(borsatDinariMessages)
        )
        items.add(tomanItem ?: sample(context)[4])
        return items
    }

    /**
     * نفس مبدأ TelegramScraperRepository.fetchOne — تحديث مستهدف لعملة وحدة
     * بس لعملة واحدة، تُستخدم من ودجت الشاشة الرئيسية.
     */
    fun fetchOne(context: Context, key: String, onResult: (PriceItem?) -> Unit) {
        executor.execute {
            val result: PriceItem? = when {
                key == "USD_OFFICIAL" -> {
                    val messages = TelegramScraperRepository.fetchTrustedMessagesShared()
                    fetchOfficialUsd(context, messages)
                }
                key.startsWith("FX_") -> {
                    val messages = TelegramScraperRepository.fetchTrustedMessagesShared()
                    fetchCurrencyItems(context, messages).find { it.cityKey == key }
                }
                key == "TRY_INVESTING" -> {
                    val messages = TelegramScraperRepository.fetchTrustedMessagesShared()
                    fetchTryFromBorsatDinari(context, messages)
                }
                key == "IRR_TELEGRAM" -> {
                    val rates = TelegramScraperRepository.fetchCurrencyRatesBlocking()
                    buildTelegramCurrencyItem(context, "IRR_TELEGRAM", context.getString(R.string.cur_irr), "ir", rates.irrPer100Usd, "100$", "")
                }
                else -> null
            }
            mainHandler.post { onResult(result) }
        }
    }

    /** يحل مفتاح عملة وحدة من رسائل القناة الجاهزة — بدون طلب شبكة هنا
     * (الصفحة تجلب مرة وحدة خارجياً ويُمرر نصها لكل المفاتيح، نفس كاش
     * التطبيق المشترك). يحفظ بالكاش المشترك ويسجل هيستوري عند تغيّر السعر،
     * نفس دوال fetch العادية بالضبط. يُستخدم من تحديث الودجت الجماعي. */
    internal fun resolveBlocking(
        context: Context,
        key: String,
        messages: List<Pair<String, String>>
    ): PriceItem? = when (key) {
        "USD_OFFICIAL" -> fetchOfficialUsd(context, messages)
        "FX_EUR", "FX_GBP", "FX_JOD", "FX_AED", "FX_KWD", "FX_EGP", "FX_CNY", "FX_SAR", "FX_SYP" -> fetchCurrencyItems(context, messages).find { it.cityKey == key }
        "TRY_INVESTING" -> fetchTryFromBorsatDinari(context, messages)
        "IRR_TELEGRAM" -> buildTelegramCurrencyItem(
            context, "IRR_TELEGRAM", context.getString(R.string.cur_irr), "ir",
            TelegramScraperRepository.extractTomanFromMessages(messages), "100$", ""
        )
        else -> null
    }

    /** يبني PriceItem من الكاش المحلي فقط بدون أي اتصال شبكة — لعرض فوري بالودجت. */
    fun buildCachedItem(context: Context, key: String): PriceItem? {
        return when {
            key == "USD_OFFICIAL" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem(context.getString(R.string.cur_usd_official), "us", "${formatLong(value)} د.ع", "100$", trend, key, HistoryStore.latestTimestamp(context, key) ?: cache.loadUpdateTime(key))
            }
            key.startsWith("FX_") -> {
                val code = key.removePrefix("FX_")
                val def = defs.find { it.code == code } ?: return null
                val cache = FxCache(context)
                val value = cache.load(code) ?: return null
                val trend = cache.loadTrend(code) ?: Trend.UP
                val priceText = if (def.dollarBase) {
                    formatFxAmount(value, def.fractionDigits)
                } else {
                    "${def.label} ${String.format(Locale.US, "%.2f", value)}"
                }
                PriceItem(context.getString(def.nameRes), def.flag, priceText, def.unit, trend, key, HistoryStore.latestTimestamp(context, key) ?: cache.loadUpdateTime(def.code))
            }
            key == "TRY_INVESTING" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem(context.getString(R.string.cur_try), "tr", formatLong(value), "100\$", trend, key, HistoryStore.latestTimestamp(context, key) ?: cache.loadUpdateTime(key))
            }
            key == "IRR_TELEGRAM" -> {
                val cache = FxTextCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem(context.getString(R.string.cur_irr), "ir", formatLong(value), "100\$", trend, key, HistoryStore.latestTimestamp(context, key) ?: cache.loadUpdateTime(key))
            }
            key == "USD_BAGHDAD" -> {
                val cache = FxCache(context)
                val value = cache.load(key) ?: return null
                val trend = cache.loadTrend(key) ?: Trend.UP
                PriceItem(context.getString(R.string.cur_usd_avg), "us", "${String.format(Locale.US, "%,d", value.toLong())} د.ع", "100\$", trend, key, HistoryStore.latestTimestamp(context, key) ?: cache.loadUpdateTime(key))
            }
            else -> null
        }
    }

    /** يبني كل العملات من الكاش المحلي فوراً، بدون أي اتصال شبكة. */
    fun buildCachedItems(context: Context): List<PriceItem> = listOf(
        "USD_OFFICIAL",
        "FX_EUR",
        "FX_GBP",
        "FX_JOD", "FX_AED", "FX_KWD", "FX_EGP",
        "TRY_INVESTING",
        "FX_CNY", "FX_SAR", "FX_SYP",
        "IRR_TELEGRAM"
    ).mapNotNull { buildCachedItem(context, it) }

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
        val publishedAtMillis = bestTimestamp?.let { TelegramScraperRepository.telegramTimestampToMillis(it) }

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
            val priceTime = publishedAtMillis ?: now
            cache.saveUpdateTime(cacheKey, priceTime)
            HistoryStore.recordAt(context, cacheKey, "${formatLong(current!!)} د.ع", trend, priceTime)
        }
        if (current != null) cache.save(cacheKey, current)

        val lastChangeTime = cache.loadUpdateTime(cacheKey)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend
        cache.saveTrend(cacheKey, finalTrend)

        return PriceItem(context.getString(R.string.cur_usd_official), "us", "${formatLong(effective)} د.ع", "100$", finalTrend, cacheKey, HistoryStore.latestTimestamp(context, cacheKey) ?: cache.loadUpdateTime(cacheKey))
    }

    /** يستخرج سعر الليرة التركية من آخر منشور بقناة بورصة ديناري، وهي المصدر
     * الرئيسي والوحيد للأسعار في التطبيق. */
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
        val publishedAtMillis = bestTimestamp?.let { TelegramScraperRepository.telegramTimestampToMillis(it) }

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
            val priceTime = publishedAtMillis ?: now
            cache.saveUpdateTime(cacheKey, priceTime)
            HistoryStore.recordAt(context, cacheKey, formatLong(current!!), trend, priceTime)
        }
        val lastChangeTime = cache.loadUpdateTime(cacheKey)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend
        cache.saveTrend(cacheKey, finalTrend)

        return PriceItem(
            name = context.getString(R.string.cur_try),
            flagDrawable = "tr",
            price = formatLong(effective),
            unit = "100\$",
            trend = finalTrend,
            cityKey = cacheKey,
            updateTimeMillis = HistoryStore.latestTimestamp(context, cacheKey) ?: cache.loadUpdateTime(cacheKey)
        )
    }

    /** يستخرج أسعار العملات ذات التسعير مقابل الدولار من منشور بورصة ديناري. */
    private fun fetchCurrencyItems(context: Context, messages: List<Pair<String, String>>): List<PriceItem> {
        val cache = FxCache(context)
        val items = mutableListOf<PriceItem>()

        for (def in defs) {
            var bestTimestamp: String? = null
            var current: Double? = null
            for ((ts, chunk) in messages) {
                if (bestTimestamp != null && ts <= bestTimestamp) continue
                // أسماء المطابقة عربية لأنها تخص نص القناة؛ الاسم المعروض مترجم من nameRes.
                val value = if (def.code == "EUR" || def.code == "GBP") {
                    TelegramScraperRepository.extractBorsatDinariDollarBased(chunk, def.sourceNames.first())
                } else {
                    TelegramScraperRepository.extractBorsatDinariDollarToCurrency(chunk, def.sourceNames)
                } ?: continue
                bestTimestamp = ts
                current = value
            }
            val publishedAtMillis = bestTimestamp?.let { TelegramScraperRepository.telegramTimestampToMillis(it) }

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
                cache.saveUpdateTime(def.code, publishedAtMillis ?: now)
            }
            val lastChangeTime = cache.loadUpdateTime(def.code)
            val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
            val finalTrend = if (isStale) Trend.FLAT else trend
            cache.saveTrend(def.code, finalTrend)

            val priceText = if (def.dollarBase) {
                formatFxAmount(effective, def.fractionDigits)
            } else {
                "${def.label} ${String.format(Locale.US, "%.2f", effective)}"
            }
            if (valueChanged) {
                HistoryStore.recordAt(context, "FX_${def.code}", priceText, trend, publishedAtMillis ?: now)
            }
            val historyKey = "FX_${def.code}"
            items.add(PriceItem(context.getString(def.nameRes), def.flag, priceText, def.unit, finalTrend, historyKey, HistoryStore.latestTimestamp(context, historyKey) ?: cache.loadUpdateTime(def.code)))
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
        suffix: String,
        publishedAtMillis: Long? = null
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
            val priceTime = publishedAtMillis ?: now
            cache.saveUpdateTime(cacheKey, priceTime)
            val priceForHistory = if (suffix.isBlank()) formatLong(current!!) else "${formatLong(current!!)} $suffix"
            HistoryStore.recordAt(context, cacheKey, priceForHistory, trend, priceTime)
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
            cityKey = cacheKey,
            updateTimeMillis = HistoryStore.latestTimestamp(context, cacheKey) ?: cache.loadUpdateTime(cacheKey)
        )
    }

    private fun formatFxAmount(value: Double, fractionDigits: Int): String =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(value)

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

package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

/**
 * جسر بسيط بين ودجت الشاشة الرئيسية والمستودعات الموجودة (TelegramScraperRepository
 * وCurrenciesRepository). مسؤول عن: تحديد أي مستودع يملك مفتاح المفضلة
 * الحالي، وتوفير نسخة "كاش فقط" (عرض فوري بدون شبكة) ونسخة "تحديث مستهدف"
 * (طلب شبكة لعنصر وحدة بس، يستخدمها زر التحديث اليدوي والتحديث الساعي).
 */
object WidgetPriceProvider {

    /** بيانات وصفية (اسم + علم) لأي مفتاح معروف — تُستخدم لعرض اسم/علم
     * صحيحين بالودجت حتى لو الكاش فاضي كلياً (أول تشغيل مثلاً). */
    fun metaFor(key: String): TelegramScraperRepository.CityMeta? {
        TelegramScraperRepository.metaFor(key)?.let { return it }
        return when (key) {
            "FX_EUR" -> TelegramScraperRepository.CityMeta(key, R.string.cur_eur, "eu")
            "FX_GBP" -> TelegramScraperRepository.CityMeta(key, R.string.cur_gbp, "gb")
            "FX_JOD" -> TelegramScraperRepository.CityMeta(key, R.string.cur_jod, "jo")
            "FX_AED" -> TelegramScraperRepository.CityMeta(key, R.string.cur_aed, "ae")
            "FX_KWD" -> TelegramScraperRepository.CityMeta(key, R.string.cur_kwd, "kw")
            "FX_EGP" -> TelegramScraperRepository.CityMeta(key, R.string.cur_egp, "eg")
            "FX_SAR" -> TelegramScraperRepository.CityMeta(key, R.string.cur_sar, "sa")
            "FX_SYP" -> TelegramScraperRepository.CityMeta(key, R.string.cur_syp, "sy")
            "TRY_INVESTING" -> TelegramScraperRepository.CityMeta(key, R.string.cur_try, "tr")
            "IRR_TELEGRAM" -> TelegramScraperRepository.CityMeta(key, R.string.cur_irr, "ir")
            "USD_OFFICIAL" -> TelegramScraperRepository.CityMeta(key, R.string.cur_usd_official, "us")
            "USD_BAGHDAD" -> TelegramScraperRepository.CityMeta(key, R.string.cur_usd_avg, "us")
            else -> null
        }
    }

    /** يرجع آخر سعر محفوظ بالكاش فوراً وبدون أي اتصال شبكة. */
    fun buildCachedItem(context: Context, key: String): PriceItem? {
        return if (key == "USD_OFFICIAL" || key == "USD_BAGHDAD" || key.startsWith("FX_") || key == "TRY_INVESTING" || key == "IRR_TELEGRAM") {
            CurrenciesRepository.buildCachedItem(context, key)
        } else {
            TelegramScraperRepository.buildCachedItem(context, key)
        }
    }

    /** تحديث شبكة مستهدف لعنصر المفضلة وحده. */
    fun fetchOne(context: Context, key: String, onResult: (PriceItem?) -> Unit) {
        when {
            key == "USD_OFFICIAL" -> CurrenciesRepository.fetchOne(context, key, onResult)
            key == "USD_BAGHDAD" -> fetchUsdMirror(context, onResult)
            key.startsWith("FX_") || key == "TRY_INVESTING" || key == "IRR_TELEGRAM" ->
                CurrenciesRepository.fetchOne(context, key, onResult)
            else -> TelegramScraperRepository.fetchOne(context, key, onResult)
        }
    }

    /** سعر "دولار" بالقائمة الرئيسية مطابق تماماً لسعر بغداد (نفس منطق
     * MainActivity.buildUsdAverageItem)، فنحدّث بغداد أولاً ثم نبني عليه. */
    private fun fetchUsdMirror(context: Context, onResult: (PriceItem?) -> Unit) {
        TelegramScraperRepository.fetchOne(context, "baghdad") { baghdad ->
            onResult(usdFromBaghdad(context, baghdad))
        }
    }

    /** يحوّل عنصر بغداد المُحلّ لعنصر "دولار" (USD_BAGHDAD) مع حفظه بالكاش —
     * نفس قيمة بغداد تماماً. لو بغداد غير متوفرة نرجع لآخر قيمة محفوظة. */
    private fun usdFromBaghdad(context: Context, baghdad: PriceItem?): PriceItem? {
        val digits = baghdad?.price?.replace(",", "")?.replace("د.ع", "")?.trim()?.toLongOrNull()
        if (baghdad == null || digits == null) return buildCachedItem(context, "USD_BAGHDAD")
        val cache = FxCache(context)
        val key = "USD_BAGHDAD"
        val previous = cache.load(key)
        val trend = if (previous != null) {
            when {
                digits > previous -> Trend.UP
                digits < previous -> Trend.DOWN
                else -> cache.loadTrend(key) ?: Trend.UP
            }
        } else cache.loadTrend(key) ?: Trend.UP
        cache.save(key, digits.toDouble())
        cache.saveTrend(key, trend)
        return PriceItem(context.getString(R.string.cur_usd_avg), "us", "${String.format(Locale.US, "%,d", digits)} د.ع", "100\$", trend, key)
    }

    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("widget-batch"))
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * يحدّث كل المفضلات دفعة وحدة بأقل عدد ممكن من طلبات الشبكة: صفحة القناة
     * تجلب مرة وحدة وتُمرر لكل المفاتيح (نفس الكاش المشترك مع التطبيق، بفترة
     * صلاحية دقيقتين)، وكل سعر ينحفظ بالكاشات المشتركة فيقرأه التطبيق والودجت
     * بعدين بدون أي طلب إضافي.
     */
    fun refreshAll(context: Context, keys: List<String>, onResult: (List<PriceItem?>) -> Unit) {
        executor.execute {
            val messages = TelegramScraperRepository.fetchTrustedMessagesShared()
            val results = keys.map { key -> resolveFromMessages(context, key, messages) }
            mainHandler.post { onResult(results) }
        }
    }

    private fun resolveFromMessages(
        context: Context,
        key: String,
        messages: List<Pair<String, String>>
    ): PriceItem? = when {
        key == "USD_BAGHDAD" ->
            usdFromBaghdad(context, TelegramScraperRepository.resolveBlocking(context, "baghdad", messages))
        key == "USD_OFFICIAL" || key == "TRY_INVESTING" || key == "IRR_TELEGRAM" || key.startsWith("FX_") ->
            CurrenciesRepository.resolveBlocking(context, key, messages)
        else -> TelegramScraperRepository.resolveBlocking(context, key, messages)
    }
}

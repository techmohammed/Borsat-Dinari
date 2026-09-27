package com.mohammed.currencyapp

import android.content.Context
import java.util.Locale

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
            "FX_EUR" -> TelegramScraperRepository.CityMeta(key, "يورو", "eu")
            "FX_GBP" -> TelegramScraperRepository.CityMeta(key, "پاوند", "gb")
            "TRY_INVESTING" -> TelegramScraperRepository.CityMeta(key, "ليرة", "tr")
            "IRR_TELEGRAM" -> TelegramScraperRepository.CityMeta(key, "تومان", "ir")
            "USD_OFFICIAL" -> TelegramScraperRepository.CityMeta(key, "الرسمي", "us")
            "USD_BAGHDAD" -> TelegramScraperRepository.CityMeta(key, "دولار", "us")
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
            val digits = baghdad?.price?.replace(",", "")?.replace("د.ع", "")?.trim()?.toLongOrNull()
            if (baghdad == null || digits == null) {
                onResult(buildCachedItem(context, "USD_BAGHDAD"))
                return@fetchOne
            }
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
            onResult(PriceItem("دولار", "us", "${String.format(Locale.US, "%,d", digits)} د.ع", "100\$", trend, key))
        }
    }
}

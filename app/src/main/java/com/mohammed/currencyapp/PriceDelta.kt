package com.mohammed.currencyapp

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * مقدار آخر تغيير حصل بسعر صف معيّن (مثلاً السعر زاد 500 دينار → "500")،
 * يُعرض أسفل السعر بقائمة الصفحة الرئيسية مع مثلث أخضر/أحمر.
 *
 * القيمة تنحسب وتنحفظ تلقائياً داخل دوال save() بكاشات الأسعار (ScraperCache /
 * FxCache / FxTextCache) لحظة ما السعر يتغير فعلياً، وتبقى محفوظة (نفس منطق
 * الاتجاه Trend اللي يبقى "عالق" على آخر تغيير) حتى تجي حركة سعر جديدة.
 * هنا نقرأها فقط ونرجعها نص جاهز (بدون إشارة — الإشارة تحددها الواجهة من اتجاه السعر).
 */
object PriceDelta {
    fun absText(context: Context, key: String): String? = when {
        key.startsWith("FX_") ->
            FxCache(context).loadDelta(key.removePrefix("FX_"))?.let { decimal(it) }
        key == "USD_BAGHDAD" ->
            FxCache(context).loadDelta(key)?.let { whole(it.roundToLong()) }
        key == "USD_OFFICIAL" || key == "TRY_INVESTING" || key == "IRR_TELEGRAM" ->
            FxTextCache(context).loadDelta(key)?.let { whole(it) }
        else ->
            ScraperCache(context).loadDelta(key)?.let { whole(it) }
    }

    private fun whole(v: Long): String? =
        if (v == 0L) null else String.format(Locale.US, "%,d", abs(v))

    private fun decimal(v: Double): String? =
        if (abs(v) < 0.005) null else String.format(Locale.US, "%.2f", abs(v))
}

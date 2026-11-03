package com.mohammed.currencyapp

import android.content.Context

/** تفعيل/إخفاء مربع الأخبار بالصفحة الرئيسية (الافتراضي: ظاهر). */
object NewsPreferences {
    private const val PREFS = "news_prefs"
    private const val KEY_ENABLED = "home_news_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

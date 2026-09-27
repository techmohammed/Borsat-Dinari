package com.mohammed.currencyapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * يخزن اختيار المستخدم اليدوي بين النمط النهاري والليلي، ويطبقه فعلياً
 * عبر AppCompatDelegate.setDefaultNightMode. هذا تبديل يدوي مستقل عن إعداد
 * النظام (مو "اتبع نظام الجهاز") — المستخدم يختار بنفسه بزر بالتطبيق.
 */
object ThemePreferences {
    private const val PREFS = "theme_prefs"
    private const val KEY_NIGHT = "is_night_mode"

    /** يرجع true لو الوضع الليلي مفعّل حسب آخر اختيار محفوظ (النهاري افتراضياً). */
    fun isNightMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NIGHT, false)
    }

    /** يُستدعى مرة وحدة بأقرب نقطة ممكنة (Application.onCreate) قبل أي Activity،
     * حتى يطبّق آخر اختيار محفوظ بدون أي وميض بالنمط الافتراضي أول. */
    fun applySavedMode(context: Context) {
        val mode = if (isNightMode(context)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /** يبدّل الوضع الحالي، يحفظه، ويطبّقه فوراً — يسبب إعادة إنشاء (recreate)
     * تلقائية لكل Activity مفتوحة حالياً لأن الثيم DayNight. */
    fun toggle(context: Context) {
        val newValue = !isNightMode(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NIGHT, newValue)
            .apply()
        AppCompatDelegate.setDefaultNightMode(
            if (newValue) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}

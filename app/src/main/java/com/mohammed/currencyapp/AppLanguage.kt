package com.mohammed.currencyapp

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * لغة التطبيق: يخزّنها AppCompat تلقائياً (بدون SharedPreferences يدوي) عبر
 * setApplicationLocales — تشتغل من API 21 فما فوق وتعيد إنشاء الشاشات تلقائياً.
 * اللغة الافتراضية عربي لأول مرة (حتى لو لغة الجهاز مختلفة) — بعدها آخر اختيار
 * للمستخدم هو اللي يبقى محفوظاً حتى بعد إعادة تشغيل الهاتف أو تحديث التطبيق.
 */
object AppLanguage {
    const val DEFAULT_TAG = "ar"
    private const val PREFS = "app_language_preferences"
    private const val KEY_TAG = "selected_language"
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun currentTag(): String {
        val appCompatTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
        return appCompatTag
            ?: lastSavedTag()
            ?: DEFAULT_TAG
    }

    fun apply(tag: String) {
        val normalized = tag.ifBlank { DEFAULT_TAG }
        saveTag(normalized)
        if (AppCompatDelegate.getApplicationLocales().get(0)?.language != normalized) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(normalized))
        }
    }

    /** يضمن إن اللغة الافتراضية عربي أول تشغيل (قبل أي اختيار من المستخدم). */
    fun ensureDefault() {
        val appCompatLocales = AppCompatDelegate.getApplicationLocales()
        val saved = lastSavedTag()
        if (appCompatLocales.isEmpty) {
            apply(saved ?: DEFAULT_TAG)
        } else {
            saveTag(appCompatLocales.get(0)?.language ?: DEFAULT_TAG)
        }
    }

    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun lastSavedTag(): String? = prefs()?.getString(KEY_TAG, null)

    private fun saveTag(tag: String) {
        prefs()?.edit()?.putString(KEY_TAG, tag)?.apply()
    }
}

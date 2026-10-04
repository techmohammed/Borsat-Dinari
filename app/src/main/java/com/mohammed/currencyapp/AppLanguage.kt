package com.mohammed.currencyapp

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

    fun currentTag(): String =
        AppCompatDelegate.getApplicationLocales().get(0)?.language ?: DEFAULT_TAG

    fun apply(tag: String) {
        if (currentTag() != tag) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** يضمن إن اللغة الافتراضية عربي أول تشغيل (قبل أي اختيار من المستخدم). */
    fun ensureDefault() {
        if (AppCompatDelegate.getApplicationLocales().isEmpty) apply(DEFAULT_TAG)
    }
}

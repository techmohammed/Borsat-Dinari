package com.mohammed.currencyapp

import android.app.Application

class DinariApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // نطبّق آخر نمط (نهاري/ليلي) اختاره المستخدم يدوياً قبل أي Activity،
        // حتى ما يصير وميض بالنمط الافتراضي قبل ما يتحول للنمط المحفوظ.
        ThemePreferences.applySavedMode(this)
    }
}

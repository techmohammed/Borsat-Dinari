package com.mohammed.currencyapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View

class DinariApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // نفضل النمط المحفوظ قبل أي Activity يتصور — حتى أول شاشة ما تومض
        // بالنهاري لو المستخدم اختار الليلي سابقاً.
        ThemePreferences.applySavedMode(this)
        // اللغة الافتراضية عربي أول تشغيل (آخر اختيار للمستخدم يبقى محفوظاً).
        AppLanguage.ensureDefault()

        // المحاذاة RTL ثابتة مهما كانت اللغة المختارة: نفرض اتجاه الشاشة
        // يمين-لليسار على كل Activity تتصور، حتى لو لغة الواجهة إنجليزي/
        // تركي/كردي — الترتيب يبقى مطابقاً لتصميم التطبيق الحالي بالضبط
        // (العناوين يمين، أزرار الشريط يسار). تنسيق الـ XML اللي عنده
        // layoutDirection صريح (مثل الشريط ltr) ما يتأثر أصلاً.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
                KurdishFont.apply(activity.window.decorView, activity)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

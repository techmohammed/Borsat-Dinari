package com.mohammed.currencyapp

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * من أندرويد 15 (API 35) وفوق، أي تطبيق هدفه targetSdk 35+ يُرسم تلقائياً
 * "حافة لحافة" (edge-to-edge) تحت شريط الحالة وشريط التنقل. هذي الدالة
 * تُرجع الشكل القديم: شريط الأدوات (أبيض/داكن) يمتد خلف شريط الحالة
 * بنفس لونه، والمحتوى القابل للتمرير ما يختفي خلف شريط التنقل.
 *
 * على الأجهزة الأقدم (أقل من 35) ما نسوي أي شي — الثيم (statusBarColor)
 * يشتغل مثل قبل بدون تغيير.
 *
 * @param toolbar شريط الأدوات: نزيد ارتفاعه ونحط padding علوي بمقدار شريط الحالة.
 * @param scrollingContent القائمة/ScrollView: نزيد padding سفلي بمقدار شريط التنقل
 *        (لازم clipToPadding=false حتى المحتوى يمر تحت الشريط وقت التمرير).
 */
fun AppCompatActivity.applySystemBarsInsets(toolbar: View, scrollingContent: View?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

    val root = findViewById<ViewGroup>(android.R.id.content)
    val toolbarHeight = toolbar.layoutParams.height
    val toolbarPaddingTop = toolbar.paddingTop
    val scrollBottom = scrollingContent?.paddingBottom ?: 0

    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.updatePadding(left = bars.left, right = bars.right)

        toolbar.updatePadding(top = toolbarPaddingTop + bars.top)
        toolbar.layoutParams = toolbar.layoutParams.apply { height = toolbarHeight + bars.top }

        scrollingContent?.updatePadding(bottom = scrollBottom + bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(root)
}

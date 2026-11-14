package com.mohammed.currencyapp

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

/**
 * حجم الخط داخل التطبيق: صغير / وسط (الافتراضي = القياس الحالي) / كبير.
 * يُطبَّق بدل مقياس خط النظام فقط داخل التطبيق، فمن عنده خط نظام كبير جداً
 * ما تتخربط عنده الأرقام والصفوف.
 */
object FontScale {
    const val SMALL = 0
    const val MEDIUM = 1
    const val LARGE = 2
    private const val PREFS = "font_scale_preferences"
    private const val KEY = "level"

    fun level(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, MEDIUM).coerceIn(SMALL, LARGE)

    fun set(context: Context, level: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, level.coerceIn(SMALL, LARGE)).apply()
    }

    fun scale(level: Int): Float = when (level) {
        SMALL -> 0.9f
        LARGE -> 1.1f
        else -> 1.0f
    }

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.fontScale = scale(level(base))
        return base.createConfigurationContext(config)
    }
}

/** أساس كل الشاشات: يفرض حجم الخط المختار من الإعدادات. */
open class ScaledActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(FontScale.wrap(newBase))
    }
}

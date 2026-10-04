package com.mohammed.currencyapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat

/**
 * RemoteViews (الودجت) تنعرض داخل الـ Launcher وما تدعم الخطوط المخصصة، فأسماء
 * المدن ترسم كصور بخط Cairo العريض (نفس خط التطبيق) وتنحط بـ setImageViewBitmap.
 * الصورة مقصوصة على حدود الحروف الفعلية (بدون فراغ زايد) حتى ما تكبّر ارتفاع البطاقة.
 */
object WidgetText {
    private val cache = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > 24
    }

    @Synchronized
    fun render(context: Context, text: String, sizeSp: Float, color: Int): Bitmap {
        val dm = context.resources.displayMetrics
        val kurdish = isKurdish(text)
        val key = "$text|$sizeSp|$color|${dm.densityDpi}|$kurdish"
        cache[key]?.let { return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            // أسماء المدن/العنوان الكوردي تستخدم Peshang، أما الأرقام والأسعار
            // فهي TextViews منفصلة وتبقى على الخط الحالي كما هي.
            typeface = ResourcesCompat.getFont(
                context,
                if (kurdish) R.font.peshang_des_5_bold else R.font.cairo_bold
            )
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm)
            this.color = color
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val pad = 2
        val width = (bounds.width() + pad * 2).coerceAtLeast(1)
        val height = (bounds.height() + pad * 2).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // نرسم بحيث تنطبق حدود الحروف على الصورة (bounds.left/top قد تكون غير صفرية).
        Canvas(bmp).drawText(text, (pad - bounds.left).toFloat(), (pad - bounds.top).toFloat(), paint)
        bmp.density = dm.densityDpi
        cache[key] = bmp
        return bmp
    }

    /** محارف مميزة للنص السوراني؛ لا نغير خط النصوص العربية/الإنجليزية العادية. */
    private fun isKurdish(text: String): Boolean =
        text.any { it in "ەێۆڕڵڤگچپژ" }
}

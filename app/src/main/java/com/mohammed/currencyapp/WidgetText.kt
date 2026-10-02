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
        val key = "$text|$sizeSp|$color|${dm.densityDpi}"
        cache[key]?.let { return it }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.cairo_bold)
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
}

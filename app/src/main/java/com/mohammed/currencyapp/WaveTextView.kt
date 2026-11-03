package com.mohammed.currencyapp

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.SystemClock
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.PI
import kotlin.math.sin

/**
 * عنوان بحركة "موجة الملعب" مرة وحدة: كل حرف يرتفع ويكبر قليلاً ويتلوّن بالأخضر ثم يرجع
 * لمكانه، والموجة تنتقل من أول حرف لآخر حرف بتأخير متتابع. الحرف يُرسم من نفس النص الحقيقي
 * (مقصوصاً على خانته) فيبقى تشكيل الحروف العربية صحيحاً طول الحركة، وبعدها العرض عادي.
 */
class WaveTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var startAt = -1L
    private val argb = ArgbEvaluator()
    private val density = resources.displayMetrics.density

    /** يشغّل الموجة مرة وحدة (يتجاهل الطلب لو الموجة السابقة لسه شغالة). */
    fun startWave() {
        val now = SystemClock.uptimeMillis()
        if (startAt >= 0 && now - startAt < totalMs()) return
        startAt = now
        postInvalidateOnAnimation()
    }

    private fun totalMs(): Long = DURATION_MS + (text?.length ?: 0) * DELAY_MS

    override fun onDraw(canvas: Canvas) {
        val lay = layout
        val txt = text
        val elapsed = if (startAt >= 0) SystemClock.uptimeMillis() - startAt else -1L
        if (lay == null || txt.isNullOrEmpty() || elapsed < 0 || elapsed >= totalMs()) {
            if (startAt >= 0 && elapsed >= totalMs()) startAt = -1L
            super.onDraw(canvas)
            return
        }
        val baseColor = currentTextColor
        val left = compoundPaddingLeft.toFloat()
        val top0 = extendedPaddingTop.toFloat()
        val originalFilter = paint.colorFilter
        for (i in txt.indices) {
            val line = lay.getLineForOffset(i)
            val cellTop = top0 + lay.getLineTop(line)
            val h = (lay.getLineBottom(line) - lay.getLineTop(line)).toFloat()
            val h0 = lay.getPrimaryHorizontal(i)
            val h1 = lay.getPrimaryHorizontal(i + 1)
            val x0 = left + minOf(h0, h1) - 1f
            val x1 = left + maxOf(h0, h1) + 1f
            val p = ((elapsed - i * DELAY_MS).toFloat() / DURATION_MS).coerceIn(0f, 1f)
            val k = sin(p * PI).toFloat() // 0 → 1 → 0
            canvas.save()
            if (k > 0f) {
                val cx = (x0 + x1) / 2f
                val cy = cellTop + h / 2f
                canvas.translate(0f, -LIFT_DP * density * k)
                canvas.scale(1f + 0.15f * k, 1f + 0.15f * k, cx, cy)
                val color = argb.evaluate(k, baseColor, GREEN) as Int
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            }
            canvas.clipRect(x0, cellTop, x1, cellTop + h)
            super.onDraw(canvas)
            paint.colorFilter = originalFilter
            canvas.restore()
        }
        postInvalidateOnAnimation()
    }

    private companion object {
        const val DURATION_MS = 400L
        const val DELAY_MS = 80L
        const val LIFT_DP = 7f
        const val GREEN = 0xFF38EF7D.toInt()
    }
}

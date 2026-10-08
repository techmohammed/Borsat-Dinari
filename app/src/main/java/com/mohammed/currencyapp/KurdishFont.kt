package com.mohammed.currencyapp

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import java.util.WeakHashMap

/**
 * يطبق Peshang على الكلمات الكوردية، ويعيد الأرقام إلى Cairo الواضح داخل النص
 * المختلط، مثل السعر والوقت والتاريخ ورقم الإصدار.
 */
object KurdishFont {
    private val watchedViews = WeakHashMap<TextView, Boolean>()
    private val formattingViews = WeakHashMap<TextView, Boolean>()
    private val numberPattern = Regex("[0-9٠-٩۰-۹][0-9٠-٩۰-۹,./:-]*")

    fun apply(root: View, context: Context) {
        if (!isKurdish(context)) return
        val peshang = ResourcesCompat.getFont(context, R.font.peshang_des_5_bold) ?: return
        val cairo = ResourcesCompat.getFont(context, R.font.cairo_bold) ?: return
        applyToView(root, peshang, cairo, context)
    }

    private fun applyToView(
        view: View,
        peshang: Typeface,
        cairo: Typeface,
        context: Context
    ) {
        if (view is TextView) {
            view.typeface = peshang
            // تقوية الكلمات الكوردية، مع بقاء الأرقام بخط Cairo Bold الأوضح.
            view.paint.isFakeBoldText = true
            // EditText يحتاج أن يحتفظ بالمؤشر والنص أثناء الكتابة؛ إعادة وضع
            // Spannable بعد كل حرف كانت تجعل خانة المبلغ تقبل رقماً واحداً فقط.
            if (view !is EditText) {
                styleNumbers(view, cairo)
                watchForLaterText(view, cairo)
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyToView(view.getChildAt(index), peshang, cairo, context)
            }
        }
    }

    private fun watchForLaterText(view: TextView, cairo: Typeface) {
        if (watchedViews.put(view, true) == true) return
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (formattingViews[view] != true) styleNumbers(view, cairo)
            }
        })
    }

    private fun styleNumbers(view: TextView, cairo: Typeface) {
        val source = view.text ?: return
        if (source.isEmpty()) return
        if (source is Spanned && source.getSpans(0, source.length, NumberTypefaceSpan::class.java).isNotEmpty()) return

        val styled = SpannableString(source)
        numberPattern.findAll(source).forEach { match ->
            styled.setSpan(
                NumberTypefaceSpan(cairo),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (styled.getSpans(0, styled.length, NumberTypefaceSpan::class.java).isEmpty()) return
        formattingViews[view] = true
        view.text = styled
        formattingViews.remove(view)
    }

    private class NumberTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
        }
    }

    private fun isKurdish(context: Context): Boolean {
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
        return language == "ku"
    }
}

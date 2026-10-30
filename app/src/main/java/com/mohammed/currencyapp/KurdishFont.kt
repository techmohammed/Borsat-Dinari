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
        // الكلمات فقط تتبع لغة الواجهة؛ الأرقام تبقى دائماً على شكل العربية الثابت.
        val peshang = if (isKurdish(context))
            ResourcesCompat.getFont(context, R.font.peshang_des_5_bold) else null
        val cairo = ResourcesCompat.getFont(context, R.font.cairo_bold) ?: return
        applyToView(root, peshang, cairo, context)
    }

    // خط Cairo يُحمّل مرة وحدة فقط (ResourcesCompat.getFont كان يُستدعى بكل
    // إطار أنيميشن لكل صف، وهذا ثقيل على الخيط الرئيسي).
    private var cairoBoldCache: Typeface? = null
    private fun cairoBold(context: Context): Typeface? =
        cairoBoldCache ?: ResourcesCompat.getFont(context.applicationContext, R.font.cairo_bold)
            ?.also { cairoBoldCache = it }

    /** إبراز أرقام بطاقات الصفحة الرئيسية فقط؛ بقية الصفحات تبقى بالوزن الموحد. */
    fun emphasizeMainNumber(view: TextView) {
        val source = view.text?.toString() ?: return
        setMainNumberText(view, source)
    }

    /**
     * يضع نص رقم بطاقة الرئيسية بـ setText واحدة فقط بدون ما يشغّل الـTextWatcher
     * (الطريقة القديمة كانت تسوي 3 setText بكل إطار، وكل setText بـautoSize
     * تعيد قياس النص بعدة أحجام، فتثقل الشاشة وقت أنيميشن الأرقام).
     */
    fun setMainNumberText(view: TextView, text: CharSequence) {
        formattingViews[view] = true
        try {
            view.text = text.toString()
        } finally {
            formattingViews.remove(view)
        }
        cairoBold(view.context)?.let { if (view.typeface !== it) view.typeface = it }
        if (!view.paint.isFakeBoldText) view.paint.isFakeBoldText = true
    }

    private fun applyToView(
        view: View,
        peshang: Typeface?,
        cairo: Typeface,
        context: Context
    ) {
        if (view is TextView) {
            if (peshang != null) view.typeface = peshang
            // الأرقام تستخدم Cairo Bold الحقيقي المطابق للعربية، بدون fake bold.
            view.paint.isFakeBoldText = false
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
            textPaint.isFakeBoldText = false
        }

        override fun updateMeasureState(textPaint: TextPaint) {
            textPaint.typeface = typeface
            textPaint.isFakeBoldText = false
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

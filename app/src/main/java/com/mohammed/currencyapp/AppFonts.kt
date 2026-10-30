package com.mohammed.currencyapp

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/**
 * خطوط التطبيق الموحدة: Cairo لكل اللغات، وPeshang للكوردية (الأرقام ترجع Cairo
 * عبر KurdishFont). تُستخدم بالعناصر المبنية بالكود وبنوافذ الحوار.
 */
object AppFonts {
    private var cairoBold: Typeface? = null
    private var cairoRegular: Typeface? = null
    private var cairoSemi: Typeface? = null
    private var peshang: Typeface? = null

    private fun load(context: Context) {
        val c = context.applicationContext
        if (cairoBold == null) cairoBold = ResourcesCompat.getFont(c, R.font.cairo_bold)
        if (cairoRegular == null) cairoRegular = ResourcesCompat.getFont(c, R.font.cairo_regular)
        if (cairoSemi == null) cairoSemi = ResourcesCompat.getFont(c, R.font.cairo_semibold)
        if (peshang == null) peshang = ResourcesCompat.getFont(c, R.font.peshang_des_5_bold)
    }

    private fun isKu() = AppLanguage.currentTag() == "ku"

    fun bold(context: Context): Typeface {
        load(context)
        return (if (isKu()) peshang else cairoBold) ?: Typeface.DEFAULT_BOLD
    }

    /** Cairo العريض دائماً (حتى بالكوردية) — للنصوص اللاتينية مثل أسماء المواقع. */
    fun cairoBold(context: Context): Typeface {
        load(context)
        return cairoBold ?: Typeface.DEFAULT_BOLD
    }

    fun regular(context: Context): Typeface {
        load(context)
        return (if (isKu()) peshang else cairoRegular) ?: Typeface.DEFAULT
    }

    /** يطبق الخط على كل النصوص داخل [root] (يحافظ على كون النص عريضاً أو عادياً). */
    fun applyTree(root: View, context: Context) {
        load(context)
        if (isKu()) {
            KurdishFont.apply(root, context)
            return
        }
        applyCairo(root)
    }

    private fun applyCairo(view: View) {
        if (view is TextView) {
            val tf = view.typeface
            val bold = tf != null && (tf === cairoBold || tf === cairoSemi || tf.isBold)
            view.typeface = if (bold) cairoBold else cairoRegular
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) applyCairo(view.getChildAt(i))
    }

    /** لنوافذ الحوار: يُنادى بعد show() حتى تكون الـviews موجودة. */
    fun applyDialog(dialog: Dialog, context: Context) {
        dialog.window?.decorView?.let { applyTree(it, context) }
    }
}

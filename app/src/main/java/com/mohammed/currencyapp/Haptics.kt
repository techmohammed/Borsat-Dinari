package com.mohammed.currencyapp

import android.app.Activity
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window

/**
 * اهتزاز خفيف (هابتك) عند أي ضغطة على زر/عنصر قابل للضغط بكل شاشات التطبيق.
 * نغلّف Window.Callback لكل Activity ونفحص رفع الإصبع: لو كانت ضغطة حقيقية (مو سحب)
 * وتحتها عنصر قابل للضغط ومفعّل، نستدعي performHapticFeedback عليه. النظام نفسه يحترم
 * إعداد "الاهتزاز عند اللمس" بالجهاز، فلو المستخدم طافيه ما يهتز شي.
 */
object Haptics {
    fun install(activity: Activity) {
        val w = activity.window
        if (w.callback is HapticWindowCallback) return
        w.callback = HapticWindowCallback(activity, w.callback)
    }

    /** اهتزاز يدوي لحالات بدون ضغطة (بدء السحب، إكمال التحديث بالسحب...). */
    fun tick(view: View, strong: Boolean = false) {
        val c = if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        view.performHapticFeedback(c)
    }

    private class HapticWindowCallback(
        private val activity: Activity,
        private val base: Window.Callback
    ) : Window.Callback by base {
        private var downX = 0f
        private var downY = 0f
        private val slop = ViewConfiguration.get(activity).scaledTouchSlop

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY }
                MotionEvent.ACTION_UP -> {
                    val moved = Math.abs(event.rawX - downX) > slop || Math.abs(event.rawY - downY) > slop
                    if (!moved) {
                        val target = findClickable(activity.window.decorView, event.rawX.toInt(), event.rawY.toInt())
                        target?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
            return base.dispatchTouchEvent(event)
        }

        private val tmp = Rect()

        private fun findClickable(v: View, x: Int, y: Int): View? {
            if (v.visibility != View.VISIBLE || !v.isShown) return null
            if (!v.getGlobalVisibleRect(tmp) || !tmp.contains(x, y)) return null
            if (v is ViewGroup) {
                for (i in v.childCount - 1 downTo 0) {
                    findClickable(v.getChildAt(i), x, y)?.let { return it }
                }
            }
            return if (v.isClickable && v.isEnabled) v else null
        }
    }
}

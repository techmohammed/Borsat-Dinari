package com.mohammed.currencyapp

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** تنسيق الوقت والتاريخ بأرقام لاتينية ونظام 24 ساعة. */
object UpdateTimeFormat {

    /** مثال: 1/10/2026 (يوم/شهر/سنة بدون أصفار زائدة) */
    fun dateDMY(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.US, "%d/%d/%d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
        )
    }

    /** مثال: 22:45 - 1/10/2026، الوقت يساراً والتاريخ يميناً. */
    fun timeAndDate(context: Context, millis: Long): String =
        Calendar.getInstance().run {
            timeInMillis = millis
            String.format(Locale.US, "\u200E%02d:%02d\u200E - \u200E%s\u200E", get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE), dateDMY(millis))
        }

    /** للودجت: الوقت في سطر مستقل فوق التاريخ. */
    fun timeAboveDate(context: Context, millis: Long): String =
        Calendar.getInstance().run {
            timeInMillis = millis
            String.format(Locale.US, "%02d:%02d\n%s", get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE), dateDMY(millis))
        }

    /** نص الوقت والتاريخ؛ الأيقونتان الفعليتان تأتيان من تخطيط البطاقة. */
    fun cardTimeAndDate(context: Context, millis: Long): String =
        Calendar.getInstance().run {
            timeInMillis = millis
            String.format(Locale.US, "\u200E%02d:%02d\u200E - \u200E%s\u200E", get(Calendar.HOUR_OF_DAY), get(Calendar.MINUTE), dateDMY(millis))
        }
}

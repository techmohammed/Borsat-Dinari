package com.mohammed.currencyapp

import android.content.Context
import java.util.Calendar
import java.util.Locale

/** تنسيق وقت "آخر تحديث": ساعة بنظام 12 مع لاحقة ص/م حسب لغة الواجهة،
 * والتاريخ يوم/شهر/سنة بأرقام لاتينية (نفس الشكل الحالي). */
object UpdateTimeFormat {

    /** مثال: 10:45 م */
    fun time12(context: Context, millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val suffix = if (cal.get(Calendar.AM_PM) == Calendar.AM)
            context.getString(R.string.time_am) else context.getString(R.string.time_pm)
        return String.format(Locale.US, "%d:%02d %s", hour, cal.get(Calendar.MINUTE), suffix)
    }

    /** مثال: 1/10/2026 (يوم/شهر/سنة بدون أصفار زائدة) */
    fun dateDMY(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            Locale.US, "%d/%d/%d",
            cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR)
        )
    }

    /** مثال: 10:45 م  1/10/2026 */
    fun timeAndDate(context: Context, millis: Long): String =
        "${time12(context, millis)}  ${dateDMY(millis)}"
}

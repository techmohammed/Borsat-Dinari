package com.mohammed.currencyapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * تحديث تلقائي إضافي كل نص ساعة لودجت البورصة المفضلة، بجانب زر التحديث
 * اليدوي وبجانب android:updatePeriodMillis بملف الودجت (اللي أندرويد
 * أحياناً يؤخره بوضع Doze). نستخدم AlarmManager.setInexactRepeating حتى
 * ما نحتاج صلاحية "المنبهات الدقيقة" (SCHEDULE_EXACT_ALARM)، وحتى يبقى
 * التوقيت مرن وخفيف على البطارية (النظام يجمع منبهات التطبيقات المتقاربة
 * ببعضها بدل تنبيه كل تطبيق بالضبط بوقته).
 */
object WidgetRefreshScheduler {
    const val ACTION_HOURLY_REFRESH = "com.mohammed.currencyapp.ACTION_WIDGET_HOURLY_REFRESH"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FavoriteWidgetProvider::class.java).apply {
            action = ACTION_HOURLY_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** يبدأ (أو يجدد) الجدولة كل نص ساعة. آمن الاستدعاء أكثر من مرة. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR,
            pendingIntent(context)
        )
    }

    /** يوقف الجدولة — يُستدعى لما آخر ودجت ينحذف من الشاشة. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }
}

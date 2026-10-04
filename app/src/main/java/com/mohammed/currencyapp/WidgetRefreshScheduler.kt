package com.mohammed.currencyapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * تحديث تلقائي لودجت البورصة المفضلة كل 15 دقيقة، بأقل استهلاك ممكن للطاقة:
 *
 *  - منبه واحد فقط: AlarmManager.setInexactRepeating بنوع RTC (مو RTC_WAKEUP)
 *    وبفترة INTERVAL_FIFTEEN_MINUTES (أقصر فترة نظامية للمنبهات غير الدقيقة —
 *    بدون أي صلاحية SCHEDULE_EXACT_ALARM). "غير دقيق" عمداً: النظام يجمعه مع
 *    منبهات تطبيقات ثانية بدل ما يصحّي الشبكة لحاله، ولو الهاتف نايم وقت
 *    الموعد ينفذ أول ما يصحى (المستخدم يفتح الشاشة) — وهذا بالضبط الوقت
 *    اللي يحتاج فيه السعر.
 *  - أوقفنا android:updatePeriodMillis بملف الودجت (صار 0)، لأنه كان يشغّل
 *    تحديث ثاني كل نص ساعة ويصحّي الجهاز (حسب توثيق أندرويد).
 *  - الربط مع التطبيق: كل الطلبات تمر على الكاش المشترك (TelegramScraperRepository)
 *    بفترة صلاحية دقيقتين، والتطبيق نفسه يسجّل هنا markRefreshed كل ما حدّث —
 *    فلو منبه الودجت جاء بعد دقايق من تحديث التطبيق يتخطى طلب الشبكة ويكتفي
 *    بالرسم من الكاش المشترك (صفر تكرار).
 *  - المنبه يُعاد جدولته من onEnabled و onUpdate (أندرويد يستدعي onUpdate بعد
 *    إعادة تشغيل الهاتف وبعد تحديث التطبيق، والمنبهات تنمسح بالحالتين)، بدون
 *    أي BootReceiver ولا إذن RECEIVE_BOOT_COMPLETED.
 *  - قبل أي تحديث تلقائي: إذا ماكو إنترنت ما نسوي شي (نوفر انتظار مهلة الشبكة
 *    وتشغيل الراديو)، وإذا تم تحديث ناجح قبل أقل من 10 دقايق (تحديث يدوي أو
 *    من التطبيق) نتخطاه حتى ما نكرر الطلب.
 */
object WidgetRefreshScheduler {
    const val ACTION_HOURLY_REFRESH = "com.mohammed.currencyapp.ACTION_WIDGET_HOURLY_REFRESH"

    private const val PREFS = "widget_refresh"
    private const val KEY_LAST_OK = "last_ok_ms"
    private const val MIN_GAP_MS = 10 * 60 * 1000L

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, FavoriteWidgetProvider::class.java).apply {
            action = ACTION_HOURLY_REFRESH
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** يبدأ (أو يجدد) الجدولة كل 15 دقيقة. آمن الاستدعاء أكثر من مرة. */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent(context)
        )
    }

    /** يجدول فقط لو ما في منبه شغال (حتى onUpdate المتكرر ما يزيح موعد التحديث كل مرة). */
    fun scheduleIfMissing(context: Context) {
        val existing = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, FavoriteWidgetProvider::class.java).apply { action = ACTION_HOURLY_REFRESH },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing == null) schedule(context)
    }

    /** يوقف الجدولة — يُستدعى لما آخر ودجت ينحذف من الشاشة. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    /** يسجّل وقت آخر تحديث ناجح — يناديه التطبيق والودجت معاً (الكاش مشترك). */
    fun markRefreshed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_OK, System.currentTimeMillis()).apply()
    }

    /** للتحديث التلقائي فقط: هل يستاهل نسوي طلب شبكة هسه؟ */
    fun shouldAutoRefresh(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_OK, 0L)
        if (System.currentTimeMillis() - last < MIN_GAP_MS) return false
        return isOnline(context)
    }

    @Suppress("DEPRECATION")
    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                cm.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            true // لو فشل الفحص لأي سبب، نحاول التحديث بدل ما نوقفه
        }
    }
}

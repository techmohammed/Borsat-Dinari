package com.mohammed.currencyapp

import android.content.Context

/** يخزن توقيت آخر تحديث فعلي للقائمة الرئيسية، حتى يبين جنب زر التحديث
 * (وضمن ذلك يبقى معروض حتى لو المستخدم سكّر التطبيق وفتحه مرة ثانية). */
object LastUpdateStore {
    private const val PREFS = "last_update_prefs"
    private const val KEY_TIME = "last_update_millis"

    fun save(context: Context, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_TIME, millis)
            .apply()
    }

    fun load(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_TIME, -1L)
        return if (value == -1L) null else value
    }
}

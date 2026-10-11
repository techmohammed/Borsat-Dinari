package com.mohammed.currencyapp

import android.content.Context
import java.io.File

/** ينظف ملفات تقارير PDF المؤقتة فقط؛ لا يلمس SharedPreferences أو سجل الأسعار. */
object CacheCleanup {
    fun clearReportFiles(context: Context) {
        runCatching { File(context.cacheDir, "reports").deleteRecursively() }
    }
}

package com.mohammed.currencyapp

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val DB_NAME = "price_history.db"
private const val DB_VERSION = 1
private const val TABLE = "history"
private const val COL_ID = "id"
private const val COL_KEY = "item_key"
private const val COL_PRICE = "price"
private const val COL_TREND = "trend"
private const val COL_TIME = "timestamp"

// نحتفظ بالهيستوري 60 يوم بس، وننظف تلقائياً أي أقدم من هذا بكل مرة نسجل
// فيها نقطة جديدة، حتى ما تتراكم قاعدة البيانات وتصير ثقيلة مع الوقت.
private const val HISTORY_RETENTION_MS = 60L * 24 * 60 * 60 * 1000

data class HistoryEntry(val id: Long, val price: String, val trend: Trend, val timestampMillis: Long)

private class HistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_KEY TEXT NOT NULL,
                $COL_PRICE TEXT NOT NULL,
                $COL_TREND TEXT NOT NULL,
                $COL_TIME INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_history_key_time ON $TABLE($COL_KEY, $COL_TIME)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }
}

/**
 * سجل هيستوري بسيط لكل سعر: يخزن نقطة جديدة بس لما السعر يتغير فعلياً (مو
 * كل تحديث)، وهذا يخلي عدد السجلات صغير جداً حتى على مدى شهرين. يُستخدم من
 * صفحة "هيستوري" خاصة بكل بورصة/عملة، تفتح بالضغط على صفها بالقائمة الرئيسية.
 */
object HistoryStore {

    fun record(context: Context, key: String, price: String, trend: Trend) {
        record(context, key, price, trend, System.currentTimeMillis())
    }

    private fun record(context: Context, key: String, price: String, trend: Trend, timestampMillis: Long) {
        val db = HistoryDbHelper(context).writableDatabase
        val values = ContentValues().apply {
            put(COL_KEY, key)
            put(COL_PRICE, price)
            put(COL_TREND, trend.name)
            put(COL_TIME, timestampMillis)
        }
        db.insert(TABLE, null, values)
        val now = System.currentTimeMillis()
        db.delete(TABLE, "$COL_TIME < ?", arrayOf((now - HISTORY_RETENTION_MS).toString()))
        db.close()
    }

    // نتجاهل أي نقطة جديدة توصل ضمن هالمدى (بالميلي ثانية) من نقطة موجودة
    // أصلاً بنفس البورصة، حتى لو السعر نفسه — عشان "لا يكرر اللي موجودة
    // عنده بالسجل" لما نجيب نفس المدى مرتين (مثلاً منبه اليدوي + سنكرون
    // القناة عالنفس اليوم).
    private const val DEDUPE_WINDOW_MS = 2L * 60 * 1000

    /** يرجع أرقام السعر بس (بدون فواصل ولا "د.ع")، لمقارنة قيم مهما كان
     * شكل تنسيقها الظاهر. بدونها كانت مقارنة "157,800 د.ع" (شكل التخزين
     * العادي) مع "157800" (شكل استخراج القناة الخام) تفشل دايماً، فتعتبر
     * كل نقطة "تغيير جديد" حتى لو نفس السعر بالضبط — وهذا سبب التكرار
     * والشكل الغريب اللي طلع بالقائمة. */
    private fun digitsOf(priceText: String): String? =
        priceText.filter { it.isDigit() }.ifEmpty { null }

    /**
     * يدمج نقاط سعر جايه من القناة (بترتيب أي شي) بسجل بورصة/عملة معينة:
     * يتجاهل أي نقطة قريبة زمنياً من نقطة موجودة أصلاً (نفس اللحظة
     * تقريباً)، ويسجل بس النقاط اللي فيها تغيير سعر فعلي عن آخر قيمة
     * معروفة قبلها زمنياً (نفس فلسفة record() العادية: نقطة وحدة لكل
     * تغيير سعر حقيقي، مو لكل منشور بالقناة). النص المُرسل بكل [candidates]
     * لازم يوصل مُنسّق مسبقاً بنفس شكل عرضه العادي (نفس ما يسوي
     * TelegramScraperRepository) — نخزنه هنا كما هو بدون أي إعادة تنسيق،
     * لأن هذي الدالة تُستخدم الآن لكل من أسعار المدن (دينار) والعملات
     * العالمية (يورو/پاوند بفاصلة عشرية، دولار رسمي/ليرة/تومان بدون وحدة)،
     * وكل نوع له شكل تنسيق مختلف. digitsOf() تُستخدم فقط للمقارنة الرقمية
     * (هل تغيّر السعر فعلاً) وليس لإعادة بناء النص المعروض. يرجع عدد
     * النقاط المضافة فعلياً.
     */
    fun backfill(context: Context, key: String, candidates: List<Pair<Long, String>>): Int {
        if (candidates.isEmpty()) return 0
        val sorted = candidates.sortedBy { it.first }
        val fromMillis = sorted.first().first
        val toMillis = sorted.last().first
        val db = HistoryDbHelper(context).writableDatabase

        var lastDigits: String? = null
        db.query(
            TABLE, arrayOf(COL_PRICE),
            "$COL_KEY = ? AND $COL_TIME < ?", arrayOf(key, fromMillis.toString()),
            null, null, "$COL_TIME DESC", "1"
        ).use { if (it.moveToFirst()) lastDigits = digitsOf(it.getString(0)) }

        val existingTimes = mutableListOf<Long>()
        db.query(
            TABLE, arrayOf(COL_TIME),
            "$COL_KEY = ? AND $COL_TIME BETWEEN ? AND ?",
            arrayOf(key, (fromMillis - DEDUPE_WINDOW_MS).toString(), (toMillis + DEDUPE_WINDOW_MS).toString()),
            null, null, null
        ).use { while (it.moveToNext()) existingTimes.add(it.getLong(0)) }

        var inserted = 0
        for ((ts, priceText) in sorted) {
            val digits = digitsOf(priceText) ?: continue
            if (digits == lastDigits) { lastDigits = digits; continue }
            val nearDuplicate = existingTimes.any { kotlin.math.abs(it - ts) <= DEDUPE_WINDOW_MS }
            if (!nearDuplicate) {
                val oldVal = lastDigits?.toLongOrNull()
                val newVal = digits.toLongOrNull()
                val trend = when {
                    oldVal == null || newVal == null -> Trend.FLAT
                    newVal > oldVal -> Trend.UP
                    newVal < oldVal -> Trend.DOWN
                    else -> Trend.FLAT
                }
                val values = ContentValues().apply {
                    put(COL_KEY, key); put(COL_PRICE, priceText); put(COL_TREND, trend.name); put(COL_TIME, ts)
                }
                db.insert(TABLE, null, values)
                existingTimes.add(ts)
                inserted++
            }
            lastDigits = digits
        }
        val now = System.currentTimeMillis()
        db.delete(TABLE, "$COL_TIME < ?", arrayOf((now - HISTORY_RETENTION_MS).toString()))
        db.close()
        return inserted
    }

    /** يرجع سجلات مدينة/عملة معينة بين تاريخين (شامل الطرفين)، الأحدث أولاً. */
    fun query(context: Context, key: String, fromMillis: Long, toMillis: Long): List<HistoryEntry> {
        val db = HistoryDbHelper(context).readableDatabase
        val cursor = db.query(
            TABLE,
            arrayOf(COL_ID, COL_PRICE, COL_TREND, COL_TIME),
            "$COL_KEY = ? AND $COL_TIME BETWEEN ? AND ?",
            arrayOf(key, fromMillis.toString(), toMillis.toString()),
            null, null,
            "$COL_TIME DESC"
        )
        val result = mutableListOf<HistoryEntry>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val price = it.getString(1)
                val trend = runCatching { Trend.valueOf(it.getString(2)) }.getOrDefault(Trend.UP)
                val time = it.getLong(3)
                result.add(HistoryEntry(id, price, trend, time))
            }
        }
        db.close()
        return result
    }

    /** يحذف سجل هيستوري وحد بمعرفه (يُستخدم من زر سلة المهملات بكل صف — حذف
     * فوري بلا رسالة تأكيد، حسب طلب المستخدم). */
    fun delete(context: Context, id: Long) {
        val db = HistoryDbHelper(context).writableDatabase
        db.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    /** يحذف كل سجلات بورصة/عملة معينة ضمن فترة محددة (من التقويم بصفحة
     * الهيستوري) — زر الحذف بالترويسة يمسح الفترة المعروضة حالياً كاملة
     * بضغطة وحدة، بلا رسالة تأكيد، حسب طلب المستخدم. */
    fun deleteRange(context: Context, key: String, fromMillis: Long, toMillis: Long) {
        val db = HistoryDbHelper(context).writableDatabase
        db.delete(
            TABLE, "$COL_KEY = ? AND $COL_TIME BETWEEN ? AND ?",
            arrayOf(key, fromMillis.toString(), toMillis.toString())
        )
        db.close()
    }
}

package com.mohammed.currencyapp

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * عدّاد الزوار والمتواجدين الآن (Firebase Realtime Database عبر REST، بدون مكتبات).
 * نفس قاعدة بيانات صفحة الويب: stats/total (يزيد مرة لكل جهاز) و online/<id> (نبضة كل 30 ثانية).
 */
object VisitorStats {
    private const val BASE = "https://borsat-dinari-default-rtdb.europe-west1.firebasedatabase.app"
    private const val PREFS = "visitor_stats"
    private const val KEY_ID = "device_id"
    private const val KEY_COUNTED = "counted"
    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("visitor-stats"))

    @Volatile var online: Int? = null
    @Volatile var total: Long? = null
    @Volatile var ratingSum: Long = 0
    @Volatile var ratingCount: Long = 0
    private const val KEY_RATING = "my_rating"

    fun myRating(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_RATING, 0)

    /** جلب متوسط التقييم العام وتقييم هذا الجهاز (إن وجد). */
    fun loadRating(context: Context, onDone: () -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val raw = request("stats/rating", "GET")
            if (raw != null && raw != "null") runCatching {
                val o = JSONObject(raw)
                ratingSum = o.optLong("sum", 0)
                ratingCount = o.optLong("count", 0)
            }
            val mine = request("ratings/${deviceId(app)}", "GET")?.trim()?.toDoubleOrNull()?.toInt()
            if (mine != null && mine in 1..5) {
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_RATING, mine).apply()
            }
            onDone()
        }
    }

    /** إرسال التقييم: تقييم واحد لكل جهاز؛ إعادة التقييم تعدّل المجموع فقط ولا تزيد العدد. */
    fun submitRating(context: Context, stars: Int, onDone: (Boolean) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val id = deviceId(app)
            val oldRaw = request("ratings/$id", "GET")
            if (oldRaw == null) { onDone(false); return@execute }
            val old = oldRaw.trim().toDoubleOrNull()?.toInt() ?: 0
            val body = JSONObject().apply {
                put("ratings/$id", stars)
                if (stars != old) put("stats/rating/sum", JSONObject().put(".sv", JSONObject().put("increment", stars - old)))
                if (old == 0) put("stats/rating/count", JSONObject().put(".sv", JSONObject().put("increment", 1)))
            }
            val ok = request("", "PATCH", body.toString()) != null
            if (ok) app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_RATING, stars).apply()
            if (ok) {
                request("stats/rating", "GET")?.let { raw ->
                    runCatching {
                        val o = JSONObject(raw)
                        ratingSum = o.optLong("sum", 0)
                        ratingCount = o.optLong("count", 0)
                    }
                }
            }
            onDone(ok)
        }
    }

    fun deviceId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getString(KEY_ID, null)
        if (saved != null && saved.length in 8..24) return saved
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val rnd = java.security.SecureRandom()
        val id = "a" + (1..15).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        p.edit().putString(KEY_ID, id).apply()
        return id
    }

    private fun request(path: String, method: String, body: String? = null): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$BASE/$path.json").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8000
                readTimeout = 8000
                useCaches = false
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            if (conn.responseCode !in 200..299) null else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** نبضة حضور + (أول مرة فقط) زيادة إجمالي الزوار، ثم جلب الأرقام. */
    fun sync(context: Context, onUpdate: () -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!p.getBoolean(KEY_COUNTED, false)) {
                if (request("stats/total", "PUT", "{\".sv\":{\"increment\":1}}") != null) {
                    p.edit().putBoolean(KEY_COUNTED, true).apply()
                }
            }
            request("online/${deviceId(app)}", "PUT", "{\".sv\":\"timestamp\"}")
            val onlineRaw = request("online", "GET")
            val totalRaw = request("stats/total", "GET")
            if (onlineRaw != null && onlineRaw != "null") {
                runCatching {
                    val obj = JSONObject(onlineRaw)
                    val values = obj.keys().asSequence().mapNotNull { k -> if (obj.opt(k) is Number) k to obj.getLong(k) else null }.toList()
                    val max = values.maxOfOrNull { it.second } ?: 0L
                    online = values.count { it.second >= max - 70_000L }
                    val stale = values.filter { it.second < max - 300_000L }.take(25)
                    if (stale.isNotEmpty()) {
                        request("online", "PATCH", JSONObject().apply { stale.forEach { put(it.first, JSONObject.NULL) } }.toString())
                    }
                }
            } else if (onlineRaw == "null") {
                online = 0
            }
            if (totalRaw != null) total = totalRaw.trim().toDoubleOrNull()?.toLong() ?: total
            onUpdate()
        }
    }

    /** عند مغادرة التطبيق نشيل الحضور فوراً (ولو فشل ينتهي لحاله بعد دقيقة). */
    fun leave(context: Context) {
        val app = context.applicationContext
        executor.execute { request("online/${deviceId(app)}", "DELETE") }
    }
}

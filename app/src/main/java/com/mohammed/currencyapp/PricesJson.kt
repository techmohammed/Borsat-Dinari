package com.mohammed.currencyapp

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * الأسعار من ملفات JSON بالريبو العام (نفس مصدر الويب) بدل قراءة صفحة قناة تليغرام:
 *  currencies.json: {"local": {key: {price, delta, time}}, "world": {key: {...}}}
 *  crypto.json / metals.json: {"items": {KEY: {price, delta, time[, unit]}}}
 * إذا فشل الجلب (أو الملف فارغ) تبقى اللقطة null وكل المستودعات ترجع تلقائياً لقراءة القناة.
 */
object PricesJson {
    class Entry(val price: Double, val timeMillis: Long?)

    private class Snap(
        val at: Long,
        val local: Map<String, Entry>,
        val world: Map<String, Entry>,
        val crypto: Map<String, Entry>,
        val metals: Map<String, Entry>
    )

    private val BASES = listOf(
        "https://borsatdinari.github.io/data/",
        "https://techmohammed.github.io/Borsat-Dinari/data/"
    )
    private const val TTL_MS = 120_000L
    @Volatile private var snap: Snap? = null
    private val lock = Any()

    /** التحديث اليدوي: يجبر جلب جديد. */
    fun invalidate() { synchronized(lock) { snap = snap?.let { Snap(0L, it.local, it.world, it.crypto, it.metals) } } }

    /** يتأكد من وجود لقطة طازجة (blocking). true = نستخدم JSON، false = رجوع لتليغرام. */
    fun ensureFresh(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        snap?.let { if (it.at != 0L && now - it.at < TTL_MS) return true }
        synchronized(lock) {
            val again = snap
            if (again != null && again.at != 0L && android.os.SystemClock.elapsedRealtime() - again.at < TTL_MS) return true
            val bucket = System.currentTimeMillis() / 60_000L
            for (base in BASES) {
                try {
                    val cur = JSONObject(download("${base}currencies.json?t=$bucket"))
                    val local = entries(cur.optJSONObject("local"))
                    val world = entries(cur.optJSONObject("world"))
                    if (local.isEmpty() && world.isEmpty()) continue
                    val cry = try { entries(JSONObject(download("${base}crypto.json?t=$bucket")).optJSONObject("items")) } catch (_: Exception) { emptyMap() }
                    val met = try { entries(JSONObject(download("${base}metals.json?t=$bucket")).optJSONObject("items")) } catch (_: Exception) { emptyMap() }
                    snap = Snap(android.os.SystemClock.elapsedRealtime(), local, world, cry, met)
                    return true
                } catch (_: Exception) { }
            }
            snap = null
            return false
        }
    }

    fun local(key: String): Entry? = snap?.let { s -> s.local[key] ?: if (key == "qadisiyyah") s.local["qadisiyah"] else null }
    fun world(key: String): Entry? = snap?.world?.get(key)
    fun crypto(code: String): Entry? = snap?.crypto?.get(code)
    fun metal(id: String): Entry? = snap?.metals?.get(id.lowercase())
    fun active(): Boolean = snap != null

    /** نص السعر بنفس شكل قناة تليغرام: 80,192.32 أو رقم صغير بدون أصفار زايدة. */
    fun raw(price: Double): String =
        if (price >= 1) String.format(java.util.Locale.US, "%,.2f", price)
        else java.math.BigDecimal(price).setScale(6, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun entries(o: JSONObject?): Map<String, Entry> {
        if (o == null) return emptyMap()
        val out = HashMap<String, Entry>()
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            if (e.isNull("price")) continue
            val p = e.optDouble("price", Double.NaN)
            if (p.isNaN() || p <= 0.0) continue
            out[k] = Entry(p, parseTime(e.optString("time", "")))
        }
        return out
    }

    private fun parseTime(s: String): Long? {
        if (s.isBlank()) return null
        s.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
        return try {
            var t = s.trim().replace(" ", "T")
            var offset = 0L
            val m = Regex("""([+-])(\d\d):?(\d\d)$""").find(t)
            if (m != null && t.length - m.value.length > 10) {
                offset = (m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()) * 60_000L * (if (m.groupValues[1] == "+") 1 else -1)
                t = t.substring(0, m.range.first)
            }
            t = t.removeSuffix("Z").replace(Regex("""\.\d+$"""), "")
            val fmt = java.text.SimpleDateFormat(if (t.length > 10) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            (fmt.parse(t)?.time ?: return null) - offset
        } catch (_: Exception) { null }
    }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        return try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { c.disconnect() }
    }
}

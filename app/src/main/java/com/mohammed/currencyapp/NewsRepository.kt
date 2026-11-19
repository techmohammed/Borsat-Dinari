package com.mohammed.currencyapp

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * الأخبار من ملف news.json بالريبو العام (بدل قناة تليغرام):
 * {"updated": "...", "items": [{"id": 1, "time": "ISO", "translations": {"AR": "...", "EN": "...", "TR": "...", "KU": "..."}}]}
 * يُجلب مرة وحدة بطلب صغير؛ نص الخبر يُختار حسب لغة التطبيق وقت العرض (انظر NewsArchiveStore).
 */
object NewsRepository {
    private val URLS = listOf(
        "https://borsatdinari.github.io/data/news.json",
        "https://techmohammed.github.io/Borsat-Dinari/data/news.json"
    )
    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("news-json"))
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetch(onResult: (List<TelegramScraperRepository.ChannelNotice>) -> Unit) {
        executor.execute {
            var result = emptyList<TelegramScraperRepository.ChannelNotice>()
            // مفتاح يتغير كل 5 دقايق لتجاوز كاش الـCDN القديم بدون ما نضرب المصدر بكل طلب.
            val bucket = System.currentTimeMillis() / 300_000L
            for (base in URLS) {
                val parsed = try { parse(download("$base?t=$bucket")) } catch (_: Exception) { null }
                if (parsed != null) { result = parsed; break }
            }
            mainHandler.post { onResult(result) }
        }
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    internal fun parse(raw: String): List<TelegramScraperRepository.ChannelNotice> {
        val items = JSONObject(raw).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<TelegramScraperRepository.ChannelNotice>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            val tr = o.optJSONObject("translations") ?: continue
            val map = HashMap<String, String>()
            tr.keys().forEach { k -> tr.optString(k).trim().takeIf { it.isNotEmpty() }?.let { map[k.uppercase()] = it } }
            if (id < 0L || map.isEmpty()) continue
            out += TelegramScraperRepository.ChannelNotice(
                id = id,
                text = pick(map),
                publishedAtMillis = parseTime(o.optString("time")),
                imageUrl = null,
                postUrl = "",
                translations = map
            )
        }
        return out
    }

    /** نص الخبر بلغة التطبيق الحالية؛ إذا ناقصة يرجع للعربي ثم لأي لغة متوفرة. */
    fun pick(map: Map<String, String>): String =
        map[AppLanguage.currentTag().uppercase()] ?: map["AR"] ?: map.values.firstOrNull() ?: ""

    private fun parseTime(iso: String): Long = try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        fmt.parse(iso)?.time ?: 0L
    } catch (_: Exception) { 0L }
}

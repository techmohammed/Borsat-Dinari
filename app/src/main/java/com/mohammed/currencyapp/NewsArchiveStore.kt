package com.mohammed.currencyapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Locally keeps each notice observed from the public Borsat Dinari Telegram channel. */
object NewsArchiveStore {
    private const val PREFS = "channel_news_archive"
    private const val KEY_ITEMS = "items_json"
    private const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000 // 60 يوم

    private fun withinRetention(publishedAt: Long): Boolean =
        publishedAt <= 0L || publishedAt >= System.currentTimeMillis() - RETENTION_MS

    fun load(context: Context): List<TelegramScraperRepository.ChannelNotice> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optLong("id", -1L)
                    val text = item.optString("text").trim()
                    val timestamp = item.optLong("publishedAt", 0L)
                    if (id >= 0L && text.isNotEmpty()) {
                        add(TelegramScraperRepository.ChannelNotice(
                            id = id,
                            text = text,
                            publishedAtMillis = timestamp,
                            imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() },
                            postUrl = item.optString("postUrl")
                        ))
                    }
                }
            }.filter { withinRetention(it.publishedAtMillis) }.sortedByDescending { it.publishedAtMillis }
        }.getOrDefault(emptyList())
    }

    fun merge(context: Context, incoming: List<TelegramScraperRepository.ChannelNotice>) {
        if (incoming.isEmpty()) return
        val merged = (load(context) + incoming)
            .distinctBy { it.id }
            .filter { withinRetention(it.publishedAtMillis) }
            .sortedByDescending { it.publishedAtMillis }
        val json = JSONArray()
        merged.forEach { notice ->
            json.put(JSONObject().apply {
                put("id", notice.id)
                put("text", notice.text)
                put("publishedAt", notice.publishedAtMillis)
                put("imageUrl", notice.imageUrl ?: "")
                put("postUrl", notice.postUrl)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, json.toString()).apply()
    }
}

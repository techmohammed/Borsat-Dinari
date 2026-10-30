package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

/**
 * سوق المعادن: يُقرأ من آخر منشور بقناة بورصة ديناري يحتوي أسعار الذهب والفضة والمعادن.
 * المحلل مرن (صيغة المنشور لم تكن متوفرة وقت الكتابة): يمر على كل سطر، يتعرف على المعدن
 * من اسمه بالعربي أو الإنكليزي (والعيار للذهب 24/22/21/18) ويأخذ السعر الذي قبل "$"
 * (أو آخر رقم بالسطر). سطور الأونصة تُهمل.
 */
object MetalsRepository {
    private data class Metal(val id: String, val nameRes: Int, val icon: String, val perTon: Boolean)

    private val metals = listOf(
        Metal("GOLD24", R.string.metal_gold24, "gold", false),
        Metal("GOLD22", R.string.metal_gold22, "gold", false),
        Metal("GOLD21", R.string.metal_gold21, "gold", false),
        Metal("GOLD18", R.string.metal_gold18, "gold", false),
        Metal("SILVER", R.string.metal_silver, "silver", false),
        Metal("PLATINUM", R.string.metal_platinum, "platinum", false),
        Metal("PALLADIUM", R.string.metal_palladium, "palladium", false),
        Metal("COPPER", R.string.metal_copper, "copper", true),
        Metal("ALUMINUM", R.string.metal_aluminum, "aluminum", true),
        Metal("NICKEL", R.string.metal_nickel, "nickel", true)
    )

    fun isMetalKey(key: String) = key.startsWith("METAL_")
    fun nameResFor(key: String): Int? = metals.find { "METAL_${it.id}" == key }?.nameRes
    fun flagFor(key: String): String = "metal_" + (metals.find { "METAL_${it.id}" == key }?.icon ?: "gold")

    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("metals-repo"))
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val STALE_MS = 3 * 60 * 60 * 1000L

    private data class Parsed(val price: Double, val raw: String, val up: Boolean?)

    private val karatRegex = Regex("""(?<![\d.,])(24|22|21|18)(?![\d,]|\.\d)""")
    private val numberRegex = Regex("""(?<![\d.,])[0-9][0-9,]*(?:\.[0-9]+)?""")
    private val beforeDollar = Regex("""([0-9][0-9,]*(?:\.[0-9]+)?)\s*\$""")
    private val percentRegex = Regex("""[0-9][0-9,]*(?:\.[0-9]+)?\s*%""")

    fun fetch(context: Context, onResult: (List<PriceItem>) -> Unit) {
        executor.execute {
            val items = resolveAll(context, TelegramScraperRepository.fetchTrustedMessagesShared())
            mainHandler.post { onResult(items) }
        }
    }

    fun resolveAll(context: Context, messages: List<Pair<String, String>>): List<PriceItem> {
        var bestTs: String? = null
        var parsed: Map<String, Parsed> = emptyMap()
        val searched = if (!hasMetals(messages) && searchAllowed(context)) {
            TelegramScraperRepository.searchChannelMessages("ذهب") +
                TelegramScraperRepository.searchChannelMessages("Gold")
        } else emptyList()
        for ((ts, chunk) in messages + searched) {
            if (bestTs != null && ts <= bestTs) continue
            val text = TelegramScraperRepository.plainMessageText(chunk)
            if (text.contains("crypto currenc", ignoreCase = true)) continue
            val map = parseBody(text)
            if (map.size < 3) continue
            bestTs = ts
            parsed = map
        }
        val publishedAt = bestTs?.let { TelegramScraperRepository.telegramTimestampToMillis(it) }
        return metals.mapNotNull { buildItem(context, it, parsed[it.id], publishedAt) }
    }

    fun resolveBlocking(context: Context, key: String, messages: List<Pair<String, String>>): PriceItem? =
        resolveAll(context, messages).find { it.cityKey == key }

    fun fetchOne(context: Context, key: String, onResult: (PriceItem?) -> Unit) {
        executor.execute {
            val item = resolveBlocking(context, key, TelegramScraperRepository.fetchTrustedMessagesShared())
            mainHandler.post { onResult(item) }
        }
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT)
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')

    private fun metalIdFor(line: String): String? {
        val n = norm(line)
        return when {
            n.contains("gold") || n.contains("ذهب") || n.contains("xau") -> {
                val k = karatRegex.find(n)?.groupValues?.get(1) ?: return null
                "GOLD$k"
            }
            n.contains("silver") || n.contains("فضه") || n.contains("xag") -> "SILVER"
            n.contains("platin") || n.contains("بلاتين") || n.contains("xpt") -> "PLATINUM"
            n.contains("palladium") || n.contains("بلاديوم") || n.contains("xpd") -> "PALLADIUM"
            n.contains("copper") || n.contains("نحاس") -> "COPPER"
            n.contains("alumin") || n.contains("منيوم") || n.contains("مونيوم") -> "ALUMINUM"
            n.contains("nickel") || n.contains("نيكل") -> "NICKEL"
            else -> null
        }
    }

    /** للسجل القديم: نص رسالة المعادن → (معرّف المعدن → السعر الخام)، يحتاج 3 أسطر على الأقل. */
    internal fun parseRawPrices(text: String): Map<String, String> {
        if (text.contains("crypto currenc", ignoreCase = true)) return emptyMap()
        val m = parseBody(text)
        return if (m.size >= 3) m.mapValues { it.value.raw } else emptyMap()
    }

    /** الرمز الإنجليزي المعروض تحت اسم المعدن. */
    fun symbolFor(key: String): String = when (key.removePrefix("METAL_")) {
        "GOLD24", "GOLD22", "GOLD21", "GOLD18" -> "AU"
        "SILVER" -> "AG"
        "PLATINUM" -> "PT"
        "PALLADIUM" -> "PD"
        "COPPER" -> "CU"
        "ALUMINUM" -> "AL"
        "NICKEL" -> "NI"
        else -> ""
    }

    private fun parseBody(text: String): Map<String, Parsed> {
        val out = LinkedHashMap<String, Parsed>()
        for (line in text.lines()) {
            val n = norm(line)
            if (n.contains("ounce") || n.contains("اونص") || Regex("""\boz\b""").containsMatchIn(n)) continue
            val id = metalIdFor(line) ?: continue
            if (out.containsKey(id)) continue
            var work = line.replace(percentRegex, " ")
            if (id.startsWith("GOLD")) work = work.replaceFirst(karatRegex, " ")
            val raw = (beforeDollar.findAll(work).lastOrNull()?.groupValues?.get(1)
                ?: numberRegex.findAll(work).lastOrNull()?.value)?.trimEnd(',', '.') ?: continue
            val value = raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 } ?: continue
            val up = when {
                line.contains("🟢") -> true
                line.contains("🔴") -> false
                else -> null
            }
            out[id] = Parsed(value, raw, up)
        }
        return out
    }

    private fun hasMetals(messages: List<Pair<String, String>>) = messages.any {
        val t = TelegramScraperRepository.plainMessageText(it.second)
        !t.contains("crypto currenc", ignoreCase = true) && parseBody(t).size >= 3
    }

    /** البحث بالقناة مكلف نسبياً: نسمح به مرة كل 6 ساعات فقط عند غياب المنشور من آخر الصفحة. */
    private fun searchAllowed(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong("search_at", 0L) < 6 * 60 * 60 * 1000L) return false
        p.edit().putLong("search_at", now).apply()
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences("metals_cache", Context.MODE_PRIVATE)
    private fun priceText(raw: String) = "$ $raw"
    private fun unitOf(context: Context, m: Metal) =
        context.getString(if (m.perTon) R.string.metal_unit_ton else R.string.metal_unit_gram)

    private fun buildItem(context: Context, m: Metal, current: Parsed?, publishedAt: Long?): PriceItem? {
        val key = "METAL_${m.id}"
        val p = prefs(context)
        val prevRaw = p.getString(key, null)
        val previous = prevRaw?.replace(",", "")?.toDoubleOrNull()
        val rawNow = current?.raw ?: prevRaw ?: return null
        val now = System.currentTimeMillis()
        val storedTrend = p.getString("${key}_trend", null)?.let { runCatching { Trend.valueOf(it) }.getOrNull() }
        var trend = storedTrend ?: Trend.UP
        if (current != null) {
            val changed = previous == null || kotlin.math.abs(current.price - previous) > 1e-9
            trend = when {
                previous != null && current.price > previous -> Trend.UP
                previous != null && current.price < previous -> Trend.DOWN
                previous == null && current.up != null -> if (current.up) Trend.UP else Trend.DOWN
                else -> storedTrend ?: Trend.UP
            }
            if (changed) {
                val t = publishedAt ?: now
                val editor = p.edit().putString(key, current.raw).putLong("${key}_updated_at", t)
                if (previous != null) editor.putString("${key}_delta", (current.price - previous).toString())
                editor.apply()
                HistoryStore.recordAt(context, key, priceText(current.raw), trend, t)
            }
        }
        val last = p.getLong("${key}_updated_at", 0L).takeIf { it > 0 }
        val stale = last == null || now - last > STALE_MS
        p.edit().putString("${key}_trend", trend.name).apply()
        return PriceItem(
            context.getString(m.nameRes), "metal_${m.icon}", priceText(rawNow), unitOf(context, m),
            if (stale) Trend.FLAT else trend, key, HistoryStore.latestTimestamp(context, key) ?: last
        )
    }

    fun buildCachedItem(context: Context, key: String): PriceItem? {
        val m = metals.find { "METAL_${it.id}" == key } ?: return null
        val p = prefs(context)
        val raw = p.getString(key, null) ?: return null
        val last = p.getLong("${key}_updated_at", 0L).takeIf { it > 0 }
        val trend = p.getString("${key}_trend", null)?.let { runCatching { Trend.valueOf(it) }.getOrNull() } ?: Trend.UP
        val stale = last == null || System.currentTimeMillis() - last > STALE_MS
        return PriceItem(
            context.getString(m.nameRes), "metal_${m.icon}", priceText(raw), unitOf(context, m),
            if (stale) Trend.FLAT else trend, key, HistoryStore.latestTimestamp(context, key) ?: last
        )
    }

    fun buildCachedItems(context: Context): List<PriceItem> =
        metals.mapNotNull { buildCachedItem(context, "METAL_${it.id}") }

    fun deltaText(context: Context, key: String): String? {
        val d = prefs(context).getString("${key}_delta", null)?.toDoubleOrNull() ?: return null
        val a = kotlin.math.abs(d)
        if (a < 1e-9) return null
        return String.format(Locale.US, "%,.2f", a)
    }
}

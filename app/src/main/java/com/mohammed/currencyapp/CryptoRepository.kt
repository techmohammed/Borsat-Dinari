package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

/**
 * العملات الرقمية: تُقرأ من آخر منشور بقناة بورصة ديناري يبدأ بعبارة "Crypto currency"
 * وبكل سطر منه الصيغة:  🔴 Bitcoin - BTC = 80,192.32 $
 * (الدائرة 🟢 = صعود، 🔴 = نزول، تُستعمل فقط لو ما توفر سعر سابق للمقارنة).
 */
object CryptoRepository {
    private data class Coin(val code: String, val nameRes: Int)

    private val coins = listOf(
        Coin("BTC", R.string.cur_crypto_btc),
        Coin("ETH", R.string.cur_crypto_eth),
        Coin("USDT", R.string.cur_crypto_usdt),
        Coin("XRP", R.string.cur_crypto_xrp),
        Coin("BNB", R.string.cur_crypto_bnb),
        Coin("SOL", R.string.cur_crypto_sol),
        Coin("USDC", R.string.cur_crypto_usdc),
        Coin("DOGE", R.string.cur_crypto_doge),
        Coin("ZEC", R.string.cur_crypto_zec),
        Coin("HYPE", R.string.cur_crypto_hype)
    )

    fun isCryptoKey(key: String) = key.startsWith("CRYPTO_")
    fun nameResFor(key: String): Int? = coins.find { "CRYPTO_${it.code}" == key }?.nameRes
    fun flagFor(key: String) = "crypto_${key.removePrefix("CRYPTO_").lowercase(Locale.US)}"

    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("crypto-repo"))
    private val mainHandler = Handler(Looper.getMainLooper())

    private val lineRegex = Regex("""([🟢🔴⚪🟡🟠])?[^\n=]*?-\s*([A-Za-z]{2,6})\s*=\s*([0-9][0-9,]*(?:\.[0-9]+)?)\s*\$?""")
    private const val STALE_MS = 3 * 60 * 60 * 1000L

    private data class Parsed(val price: Double, val raw: String, val up: Boolean?)

    fun fetch(context: Context, onResult: (List<PriceItem>) -> Unit) {
        executor.execute {
            val items = resolveAll(context, TelegramScraperRepository.fetchTrustedMessagesShared())
            mainHandler.post { onResult(items) }
        }
    }

    fun resolveAll(context: Context, messages: List<Pair<String, String>>): List<PriceItem> {
        var bestTs: String? = null
        var parsed: Map<String, Parsed> = emptyMap()
        val searched = if (!hasCrypto(messages) && searchAllowed(context)) {
            TelegramScraperRepository.searchChannelMessages("Crypto currency")
        } else emptyList()
        for ((ts, chunk) in messages + searched) {
            if (bestTs != null && ts <= bestTs) continue
            val text = TelegramScraperRepository.plainMessageText(chunk)
            val at = text.indexOf("crypto currenc", ignoreCase = true).takeIf { it >= 0 }
                ?: text.indexOf("cryptocurrenc", ignoreCase = true).takeIf { it >= 0 } ?: continue
            val map = parseBody(text.substring(at))
            if (map.isEmpty()) continue
            bestTs = ts
            parsed = map
        }
        val publishedAt = bestTs?.let { TelegramScraperRepository.telegramTimestampToMillis(it) }
        return coins.mapNotNull { coin -> buildItem(context, coin, parsed[coin.code], publishedAt) }
    }

    fun resolveBlocking(context: Context, key: String, messages: List<Pair<String, String>>): PriceItem? =
        resolveAll(context, messages).find { it.cityKey == key }

    fun fetchOne(context: Context, key: String, onResult: (PriceItem?) -> Unit) {
        executor.execute {
            val item = resolveBlocking(context, key, TelegramScraperRepository.fetchTrustedMessagesShared())
            mainHandler.post { onResult(item) }
        }
    }

    /** للسجل القديم: نص رسالة العملات الرقمية → (الرمز → السعر الخام)، أو فارغة إن لم تكن رسالة كريبتو. */
    internal fun parseRawPrices(text: String): Map<String, String> {
        val at = text.indexOf("crypto currenc", ignoreCase = true).takeIf { it >= 0 }
            ?: text.indexOf("cryptocurrenc", ignoreCase = true).takeIf { it >= 0 } ?: return emptyMap()
        return parseBody(text.substring(at)).mapValues { it.value.raw }
    }

    private fun parseBody(text: String): Map<String, Parsed> {
        val out = LinkedHashMap<String, Parsed>()
        for (m in lineRegex.findAll(text)) {
            val code = m.groupValues[2].uppercase(Locale.US)
            if (coins.none { it.code == code } || out.containsKey(code)) continue
            val raw = m.groupValues[3].trimEnd(',', '.')
            val value = raw.replace(",", "").toDoubleOrNull() ?: continue
            val up = when {
                m.value.contains("🟢") -> true
                m.value.contains("🔴") -> false
                else -> null
            }
            out[code] = Parsed(value, raw, up)
        }
        return out
    }

    private fun hasCrypto(messages: List<Pair<String, String>>) =
        messages.any { TelegramScraperRepository.plainMessageText(it.second).contains("crypto currenc", ignoreCase = true) }

    /** البحث بالقناة مكلف نسبياً: نسمح به مرة كل 6 ساعات فقط عند غياب المنشور من آخر الصفحة. */
    private fun searchAllowed(context: Context): Boolean {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        if (now - p.getLong("search_at", 0L) < 6 * 60 * 60 * 1000L) return false
        p.edit().putLong("search_at", now).apply()
        return true
    }

    private fun prefs(context: Context) = context.getSharedPreferences("crypto_cache", Context.MODE_PRIVATE)

    private fun priceText(raw: String) = "$ $raw"

    private fun buildItem(context: Context, coin: Coin, current: Parsed?, publishedAt: Long?): PriceItem? {
        val key = "CRYPTO_${coin.code}"
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
        val finalTrend = if (stale) Trend.FLAT else trend
        p.edit().putString("${key}_trend", trend.name).apply()
        return PriceItem(
            context.getString(coin.nameRes), "crypto_${coin.code.lowercase(Locale.US)}", priceText(rawNow),
            "1 ${coin.code}", finalTrend, key, HistoryStore.latestTimestamp(context, key) ?: last
        )
    }

    fun buildCachedItem(context: Context, key: String): PriceItem? {
        val coin = coins.find { "CRYPTO_${it.code}" == key } ?: return null
        val p = prefs(context)
        val raw = p.getString(key, null) ?: return null
        val last = p.getLong("${key}_updated_at", 0L).takeIf { it > 0 }
        val trend = p.getString("${key}_trend", null)?.let { runCatching { Trend.valueOf(it) }.getOrNull() } ?: Trend.UP
        val stale = last == null || System.currentTimeMillis() - last > STALE_MS
        return PriceItem(
            context.getString(coin.nameRes), "crypto_${coin.code.lowercase(Locale.US)}", priceText(raw),
            "1 ${coin.code}", if (stale) Trend.FLAT else trend, key, HistoryStore.latestTimestamp(context, key) ?: last
        )
    }

    fun buildCachedItems(context: Context): List<PriceItem> =
        coins.mapNotNull { buildCachedItem(context, "CRYPTO_${it.code}") }

    /** فرق السعر الأخير للعرض (موجب/سالب) أو null. */
    fun deltaText(context: Context, key: String): String? {
        val d = prefs(context).getString("${key}_delta", null)?.toDoubleOrNull() ?: return null
        val a = kotlin.math.abs(d)
        if (a < 1e-9) return null
        return String.format(Locale.US, if (a >= 1) "%,.2f" else "%.4f", a)
    }
}

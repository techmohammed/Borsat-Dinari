package com.mohammed.currencyapp

import android.content.Context

/**
 * المفضلة: حتى 5، مرتبة (الأولى = الأعلى بالرئيسية). الافتراضي لو ما اختار المستخدم شي:
 * بغداد، أربيل، الدولار الرسمي، البصرة، اليورو. الضغط على نجمة مفضلة يشيلها،
 * ولو انشالت كلها ترجع الافتراضيات.
 * الودجتات واختصارات التطبيق تستخدم أول 3 فقط (بغداد، أربيل، الرسمي افتراضياً).
 */
object FavoriteCityStore {
    const val MAX_FAVORITES = 5

    val DEFAULTS = listOf("baghdad", "erbil", "USD_OFFICIAL", "basra", "FX_EUR")

    private const val PREFS_NAME = "favorite_prefs"
    private const val KEY_CITY = "favorite_city"      // النسخ القديمة: مفضلة وحدة فقط
    private const val KEY_LIST = "favorite_list"      // مفاتيح مفصولة بفاصلة

    enum class Result { REMOVED, ADDED }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True only after the user explicitly changed favorites. */
    fun hasExplicitSelection(context: Context): Boolean {
        val p = prefs(context)
        return p.contains(KEY_LIST) || p.contains(KEY_CITY)
    }

    /** المفضلة: المختار صراحةً (حتى 5)، ولو فارغ ترجع الافتراضيات. */
    fun getAll(context: Context): List<String> {
        val p = prefs(context)
        val raw = p.getString(KEY_LIST, null)
        val chosen = when {
            raw != null -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            p.contains(KEY_CITY) -> listOfNotNull(p.getString(KEY_CITY, null))
            else -> emptyList()
        }.distinct().take(MAX_FAVORITES)
        return chosen.ifEmpty { DEFAULTS }
    }

    private fun save(context: Context, keys: List<String>) {
        prefs(context).edit().putString(KEY_LIST, keys.joinToString(",")).remove(KEY_CITY).apply()
    }

    /**
     * الضغط على نجمة مفضلة يشيلها؛ على نجمة غير مفضلة يضيفها بالأول
     * (ولو صاروا أكثر من 5 تنحذف آخر وحدة).
     */
    fun toggle(context: Context, key: String): Result {
        val current = getAll(context)
        return if (key in current) {
            save(context, current.filter { it != key })
            Result.REMOVED
        } else {
            save(context, (listOf(key) + current).take(MAX_FAVORITES))
            Result.ADDED
        }
    }

    /** يحفظ ترتيب المفضلة بعد السحب (الرئيسية). */
    fun setOrder(context: Context, ordered: List<String>) {
        save(context, ordered.distinct().take(MAX_FAVORITES))
    }

    /** يرتب القائمة بحيث تكون المفضلة أول عناصر (بترتيب اختيارها) وباقي العناصر بترتيبها الأصلي. */
    fun sortWithFavoriteFirst(context: Context, items: List<PriceItem>): List<PriceItem> {
        val favorites = getAll(context)
        return items.sortedBy { item ->
            val i = favorites.indexOf(item.cityKey ?: item.name)
            if (i < 0) Int.MAX_VALUE else i
        }
    }
}

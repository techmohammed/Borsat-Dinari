package com.mohammed.currencyapp

import android.content.Context

/**
 * المفضلة: لحد 3 بورصات/عملات مرتبة حسب ترتيب اختيارها. الأولى هي "الرئيسية"
 * (صفها كبير بالصفحة الرئيسية وبطاقتها الكبيرة بالودجت)، والثانية والثالثة
 * تظهر فوق باقي الصفوف بدون تكبير وببطاقتين صغيرتين بالودجت.
 */
object FavoriteCityStore {
    const val MAX_FAVORITES = 3

    private const val PREFS_NAME = "favorite_prefs"
    private const val KEY_CITY = "favorite_city"      // النسخ القديمة: مفضلة وحدة فقط
    private const val KEY_LIST = "favorite_list"      // مفاتيح مفصولة بفاصلة
    private const val DEFAULT_CITY = "baghdad"

    enum class Result { ADDED, REMOVED, REPLACED }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** المفضلة بالترتيب (الأولى = الرئيسية). تنتقل تلقائياً من النسخ القديمة. */
    fun getAll(context: Context): List<String> {
        val p = prefs(context)
        val raw = p.getString(KEY_LIST, null)
        if (raw != null) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_FAVORITES)
        }
        return listOf(p.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY)
    }

    private fun save(context: Context, keys: List<String>) {
        prefs(context).edit().putString(KEY_LIST, keys.joinToString(",")).apply()
    }

    /**
     * الضغط على النجمة: يشيل لو موجود، ويضيف لو مو موجود. لو المفضلات امتلت (3)
     * تنمسح آخر وحدة انضافت (اللي قبلها) وتاخذ مكانها الجديدة — بدون أي رسالة.
     */
    fun toggle(context: Context, key: String): Result {
        val current = getAll(context)
        return when {
            key in current -> { save(context, current - key); Result.REMOVED }
            current.size >= MAX_FAVORITES -> {
                save(context, current.take(MAX_FAVORITES - 1) + key)
                Result.REPLACED
            }
            else -> { save(context, current + key); Result.ADDED }
        }
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

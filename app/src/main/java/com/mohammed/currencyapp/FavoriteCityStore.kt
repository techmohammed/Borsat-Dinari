package com.mohammed.currencyapp

import android.content.Context

object FavoriteCityStore {
    private const val PREFS_NAME = "favorite_prefs"
    private const val KEY_CITY = "favorite_city"
    private const val DEFAULT_CITY = "baghdad"

    fun get(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
    }

    fun set(context: Context, cityKey: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CITY, cityKey).apply()
    }

    /** يرتب قائمة البورصات المحلية بحيث تكون المفضلة أول عنصر. */
    fun sortWithFavoriteFirst(context: Context, items: List<PriceItem>): List<PriceItem> {
        val favorite = get(context)
        return items.sortedBy { if (it.cityKey == favorite) 0 else 1 }
    }
}

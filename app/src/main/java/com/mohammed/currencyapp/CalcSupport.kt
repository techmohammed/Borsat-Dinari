package com.mohammed.currencyapp

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** دعم العملات الرقمية والمعادن بالحاسبة (التطبيق والويدجت): سعرها بالدولار للوحدة الواحدة. */
object CalcSupport {
    fun isQuote(key: String?): Boolean =
        key != null && (key.startsWith("CRYPTO_") || key.startsWith("METAL_"))

    /** وحدات الخيار لكل 100 دولار: 100 / السعر بالدولار للوحدة. */
    fun ratePer100Usd(key: String?, usdPrice: Double): Double? =
        if (isQuote(key) && usdPrice > 0.0) 100.0 / usdPrice else null

    fun outputUnit(item: PriceItem): String? = when {
        item.cityKey?.startsWith("CRYPTO_") == true -> item.cityKey.removePrefix("CRYPTO_")
        item.cityKey?.startsWith("METAL_") == true -> "${item.unit} ${item.name}"
        else -> null
    }

    /** النواتج الصغيرة (مثل 0.00124 BTC) تحتاج خانات عشرية أكثر. */
    fun format(result: Double): String {
        val pattern = if (kotlin.math.abs(result) in 1e-12..0.99999) "#,##0.########" else "#,##0.##"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(result)
    }

    val calculatorKeys: List<String> =
        listOf("BTC", "ETH", "USDT", "XRP", "BNB", "SOL", "USDC", "DOGE", "ZEC", "HYPE").map { "CRYPTO_$it" } +
        listOf("GOLD24", "GOLD22", "GOLD21", "GOLD18", "SILVER", "PLATINUM", "PALLADIUM", "COPPER", "ALUMINUM", "NICKEL").map { "METAL_$it" }
}

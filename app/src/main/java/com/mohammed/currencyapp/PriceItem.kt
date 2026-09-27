package com.mohammed.currencyapp

enum class Trend { UP, DOWN, FLAT }

data class PriceItem(
    val name: String,
    val flagDrawable: String, // اسم ملف الدروابل بدون امتداد، مثال "iq"
    val price: String,
    val unit: String,
    val trend: Trend = Trend.FLAT,
    val cityKey: String? = null // غير null فقط لصفوف "البورصات المحلية" (لعرض نجمة المفضلة)
)

// عنصر يمثل صف بالقائمة: إما عنوان قسم أو بطاقة سعر
sealed class ListRow {
    data class Header(val title: String) : ListRow()
    data class Row(val item: PriceItem) : ListRow()
}

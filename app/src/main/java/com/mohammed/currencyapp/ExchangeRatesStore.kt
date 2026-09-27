package com.mohammed.currencyapp

data class CityDef(val key: String, val name: String, val flag: String)

/** تعريف المدن الثابت (الاسم بدون بادئة "البورصة"، والعلم المناسب لكل مدينة). */
object ExchangeRatesStore {
    val cities = listOf(
        CityDef("baghdad", "بغداد", "iq"),
        CityDef("basra", "بصرة", "iq"),
        CityDef("erbil", "أربيل", "kurdistan"),
        CityDef("kirkuk", "كركوك", "kurdistan"),
        CityDef("sulaymaniyah", "سليمانية", "kurdistan"),
        CityDef("duhok", "دهوك", "kurdistan"),
        CityDef("mosul", "الموصل", "iq"),
        CityDef("karbala", "كربلاء", "iq"),
        CityDef("najaf", "النجف", "iq"),
        CityDef("wasit", "واسط", "iq"),
        CityDef("muthanna", "المثنى", "iq"),
        CityDef("salahaldin", "صلاح الدين", "iq"),
        CityDef("diyala", "ديالى", "iq"),
        CityDef("dhiqar", "ذي قار", "iq"),
        CityDef("qadisiyyah", "القادسية", "iq"),
        CityDef("babil", "بابل", "iq"),
        CityDef("maysan", "ميسان", "iq"),
        CityDef("anbar", "الأنبار", "iq"),
    )
}

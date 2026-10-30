package com.mohammed.currencyapp

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

object HistoryPdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842

    fun export(context: Context, itemKey: String, itemName: String, flagName: String, unitText: String, entries: List<HistoryEntry>, from: Long, to: Long): Uri {
        val tag = AppLanguage.currentTag()
        val labels = Labels.forTag(tag, context)
        val rtl = tag == "ar" || tag == "ku"
        val sorted = entries.sortedBy { it.timestampMillis }
        val displayEntries = entries.sortedByDescending { it.timestampMillis }
        val pdf = android.graphics.pdf.PdfDocument()
        val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas
        val blue = Color.rgb(0, 48, 120)
        val green = Color.rgb(30, 158, 70)
        val red = Color.rgb(224, 49, 45)
        val grey = Color.rgb(91, 107, 140)
        val bg = Color.rgb(245, 248, 252)
        val cairo = ResourcesCompat.getFont(context, R.font.cairo_regular) ?: Typeface.DEFAULT
        val cairoBold = ResourcesCompat.getFont(context, R.font.cairo_bold) ?: Typeface.DEFAULT_BOLD
        val peshang = ResourcesCompat.getFont(context, R.font.peshang_des_5_bold) ?: cairoBold
        val textFont = if (tag == "ku") peshang else cairo

        canvas.drawColor(bg)
        val title = paint(Color.WHITE, 34f, textFont, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
        val label = paint(grey, 10f, textFont, Paint.Align.CENTER)
        val currentLabel = paint(blue, 13f, cairoBold, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
        val bold = paint(blue, 20f, textFont, Paint.Align.CENTER)
        val number = paint(blue, 18f, cairoBold, Paint.Align.CENTER)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

        fill.color = blue
        canvas.drawRoundRect(24f, 22f, 571f, 88f, 18f, 18f, fill)
        val icon = (context.getDrawable(R.drawable.report_app_icon_8) as? BitmapDrawable)?.bitmap
        val iconRect = if (rtl) RectF(34f, 12f, 120f, 98f) else RectF(475f, 12f, 561f, 98f)
        icon?.let { canvas.drawBitmap(it, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG)) }
        val textX = if (rtl) 556f else 39f
        title.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(labels.appName, textX, 62f, title)

        fun card(left: Float, top: Float, right: Float, bottom: Float) {
            fill.color = Color.WHITE
            canvas.drawRoundRect(left, top, right, bottom, 14f, 14f, fill)
        }
        // خانة تعريف البورصة أقصر، مع إبراز العلم خارج حدودها من الأعلى والأسفل.
        card(24f, 101f, 571f, 164f)
        val flagId = context.resources.getIdentifier("flag_${flagName.lowercase(Locale.US)}", "drawable", context.packageName)
        val flagBitmap = (context.getDrawable(if (flagId != 0) flagId else R.drawable.flag_iq) as? BitmapDrawable)?.bitmap
        val flagRect = RectF(if (rtl) 461f else 46f, 95f, if (rtl) 535f else 120f, 169f)
        flagBitmap?.let {
            drawCircularBitmap(canvas, it, flagRect)
            canvas.drawOval(flagRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.BLACK
                strokeWidth = 1f
            })
        }
        bold.textSize = 27f
        bold.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        // مسافة إضافية تقارب 2 سم عن العلم، مع محاذاة عمودية إلى مركزه.
        canvas.drawText(itemName, if (rtl) 395f else 182f, 143f, bold)
        label.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        // كتلة مستقلة للفترة: العنوان فوق التاريخين وفي منتصف علامة الفصل،
        // حتى لا تختلط كلمة "الفترة المختارة" مع السطر الزمني في RTL.
        val periodCenter = if (rtl) 125f else 470f
        val periodText = "\u202A${dateFmt.format(Date(from))}  -  ${dateFmt.format(Date(to))}\u202C"
        label.textAlign = Paint.Align.CENTER
        canvas.drawText(labels.period, periodCenter, 132f, label)
        canvas.drawText(periodText, periodCenter, 153f, label)

        val values = sorted.mapNotNull { parse(it.price) }
        val units = reportUnits(itemKey, unitText, tag)
        // بطاقة مستقلة للسعر الحالي، منفصلة عن بطاقات الإحصاءات الثلاث.
        card(24f, 181f, 571f, 220f)
        val last = values.lastOrNull() ?: 0.0
        val min = values.minOrNull() ?: 0.0
        val avg = if (values.isEmpty()) 0.0 else values.average()
        val currentPaint = paint(blue, 16f, cairoBold, Paint.Align.CENTER)
        canvas.drawText("${units.from} = ${formatValue(last, itemKey)} ${units.to}", PAGE_W / 2f, 207f, currentPaint)
        // في العربي والكوردي تكون التسمية يميناً، وفي الإنكليزي والتركي يساراً.
        currentLabel.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(labels.current, if (rtl) 547f else 48f, 207f, currentLabel)
        card(24f, 223f, 571f, 271f)
        listOf(labels.lowest to min, labels.highest to (values.maxOrNull() ?: 0.0), labels.average to avg).forEachIndexed { i, pair ->
            val x = 120f + i * 178f
            label.textAlign = Paint.Align.CENTER
            canvas.drawText(pair.first, x, 235f, label)
            number.color = if (i == 0) red else if (i == 1) green else blue
            canvas.drawText(formatValue(pair.second, itemKey) + " " + units.to, x, 259f, number)
        }

        card(24f, 276f, 571f, 420f)
        // نترك هامشاً أكبر يسار المحور حتى تظهر أرقام التومان الكبيرة كاملة.
        // تقليل عرض المخطط إلى قرابة ثلثي العرض السابق مع إبقاء مساحة واضحة للمحور.
        drawChart(canvas, sorted, itemKey, 40f, 297f, 555f, 400f, line, grey, green, red, textFont)
        card(24f, 430f, 571f, 815f)
        val detailsPaint = paint(blue, 20f, textFont, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
        canvas.drawText(labels.details, if (rtl) 555f else 40f, 455f, detailsPaint)
        drawTable(context, canvas, displayEntries.take(30), displayEntries, labels, 32f, 470f, 208f, 812f, dateFmt, cairo, textFont, blue, green, red, grey)
        drawTable(context, canvas, displayEntries.drop(30).take(30), displayEntries, labels, 213f, 470f, 389f, 812f, dateFmt, cairo, textFont, blue, green, red, grey)
        drawTable(context, canvas, displayEntries.drop(60).take(30), displayEntries, labels, 394f, 470f, 563f, 812f, dateFmt, cairo, textFont, blue, green, red, grey)
        val footer = paint(grey, 9f, textFont, Paint.Align.LEFT)
        footer.textAlign = Paint.Align.CENTER
        canvas.drawText(labels.footer, PAGE_W / 2f, 832f, footer)
        drawPageNumber(canvas, 1, rtl, grey, textFont)
        pdf.finishPage(page)

        var offset = 90
        // الصفحات التالية تستغل الورقة كاملة: كل عمود يسع عدد صفوف أكبر بكثير من الأولى.
        val extraRows = ((785f - 112f - 24f) / 10.5f).toInt()
        var pageNumber = 2
        while (offset < displayEntries.size) {
            val extraPage = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            val extraCanvas = extraPage.canvas
            extraCanvas.drawColor(bg)
            fill.color = blue
            extraCanvas.drawRoundRect(24f, 22f, 571f, 88f, 18f, 18f, fill)
            val pageTitle = paint(Color.WHITE, 27f, textFont, if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT)
            extraCanvas.drawText(labels.details, if (rtl) 555f else 40f, 60f, pageTitle)
            val left = displayEntries.drop(offset).take(extraRows)
            val middle = displayEntries.drop(offset + extraRows).take(extraRows)
            val right = displayEntries.drop(offset + 2 * extraRows).take(extraRows)
            drawTable(context, extraCanvas, left, displayEntries, labels, 32f, 112f, 208f, 785f, dateFmt, cairo, textFont, blue, green, red, grey)
            drawTable(context, extraCanvas, middle, displayEntries, labels, 213f, 112f, 389f, 785f, dateFmt, cairo, textFont, blue, green, red, grey)
            drawTable(context, extraCanvas, right, displayEntries, labels, 394f, 112f, 563f, 785f, dateFmt, cairo, textFont, blue, green, red, grey)
            val extraFooter = paint(grey, 9f, textFont, Paint.Align.CENTER)
            extraCanvas.drawText(labels.footer, PAGE_W / 2f, 815f, extraFooter)
            drawPageNumber(extraCanvas, pageNumber, rtl, grey, textFont)
            pdf.finishPage(extraPage)
            offset += 3 * extraRows
            pageNumber++
        }

        // نحتفظ بتقرير واحد فقط؛ مشاركة تقرير جديد تحذف بقايا التقارير السابقة.
        CacheCleanup.clearReportFiles(context)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val fromName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(from))
        val toName = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(to))
        val safeName = itemName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "history" }
        val file = File(dir, "${safeName}_${fromName}_${toName}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }

    private fun drawTable(context: Context, canvas: Canvas, entries: List<HistoryEntry>, allEntries: List<HistoryEntry>, labels: Labels, l: Float, t: Float, r: Float, b: Float, dateFmt: SimpleDateFormat, cairo: Typeface, cairoBold: Typeface, blue: Int, green: Int, red: Int, grey: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        fill.color = blue
        canvas.drawRoundRect(l, t, r, t + 22f, 7f, 7f, fill)
        val head = paint(Color.WHITE, 8f, cairoBold, Paint.Align.CENTER)
        canvas.drawText(labels.date, l + 23f, t + 15f, head)
        canvas.drawText(labels.time, l + 58f, t + 15f, head)
        canvas.drawText(labels.price, l + 95f, t + 15f, head)
        val rowH = 10.5f
        val usable = ((b - t - 24f) / rowH).toInt().coerceAtLeast(1)
        val shown = entries.take(usable)
        val iconUp = bitmap(context, R.drawable.ic_hist_up)
        val iconDown = bitmap(context, R.drawable.ic_hist_down)
        val iconFlat = bitmap(context, R.drawable.ic_hist_flat)
        val rowText = paint(Color.DKGRAY, 6.8f, cairo, Paint.Align.CENTER)
        shown.forEachIndexed { i, e ->
            val y = t + 22f + i * rowH
            fill.color = if (i % 2 == 0) Color.WHITE else Color.rgb(247, 250, 254)
            canvas.drawRect(l, y, r, y + rowH, fill)
            val dt = Date(e.timestampMillis)
            canvas.drawText(dateFmt.format(dt), l + 23f, y + 9f, rowText)
            canvas.drawText(SimpleDateFormat("HH:mm", Locale.US).format(dt), l + 58f, y + 9f, rowText)
            canvas.drawText(numericText(e.price), l + 95f, y + 9f, rowText)
            val bm = when (e.trend) { Trend.UP -> iconUp; Trend.DOWN -> iconDown; Trend.FLAT -> iconFlat }
            bm?.let { canvas.drawBitmap(it, null, RectF(r - 55f, y + 0.5f, r - 44f, y + 10.5f), Paint(Paint.ANTI_ALIAS_FLAG)) }
            rowText.color = when (e.trend) { Trend.UP -> green; Trend.DOWN -> red; Trend.FLAT -> grey }
            val older = allEntries.filter { it.timestampMillis < e.timestampMillis }.maxByOrNull { it.timestampMillis }
            val delta = parse(e.price)?.let { now -> older?.let { old -> parse(old.price)?.let { now - it } } }
            canvas.drawText(delta?.let { if (it > 0) "+${formatNumber(it)}" else if (it < 0) "−${formatNumber(-it)}" else "0" } ?: "—", r - 30f, y + 9f, rowText)
            rowText.color = Color.DKGRAY
        }
    }

    private fun drawChart(canvas: Canvas, entries: List<HistoryEntry>, itemKey: String, l: Float, t: Float, r: Float, b: Float, line: Paint, grey: Int, green: Int, red: Int, font: Typeface) {
        val raw = entries.mapNotNull { parse(it.price)?.let { v -> it.timestampMillis to v } }
        if (raw.size < 2) return
        val day = 24L * 60 * 60 * 1000
        val spanTime = raw.last().first - raw.first().first
        val points = if (spanTime > day) {
            raw.groupBy { startOfDay(it.first) }.map { it.key to it.value.map { p -> p.second }.average() }.sortedBy { it.first }
        } else raw
        if (points.size < 2) return
        val lo = points.minOf { it.second }; val hi = points.maxOf { it.second }; val span = max(hi - lo, 1.0)
        val axis = paint(grey, 8f, font, Paint.Align.RIGHT)
        val axisWidth = (0..4).maxOf { i ->
            val value = hi - (hi - lo) * i / 4.0
            axis.measureText(formatValue(value, itemKey))
        }
        // مساحة المحور تتوسع تلقائياً للتومان والأرقام الطويلة، ثم يستغل الرسم بقية الخانة.
        val plotLeft = max(l + axisWidth + 16f, l + 34f)
        val xs = points.mapIndexed { i, _ -> plotLeft + (r - plotLeft) * i / (points.size - 1).toFloat() }
        val ys = points.map { b - ((it.second - lo) / span * (b - t)).toFloat() }
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(32, Color.red(grey), Color.green(grey), Color.blue(grey)); strokeWidth = 0.8f }
        for (i in 0..4) { val y = t + (b - t) * i / 4f; canvas.drawLine(plotLeft, y, r, y, grid) }
        for (x in xs) canvas.drawLine(x, t, x, b, grid)
        for (i in 0..4) {
            val value = hi - (hi - lo) * i / 4.0
            val y = t + (b - t) * i / 4f
            canvas.drawText(formatValue(value, itemKey), plotLeft - 8f, y + 3f, axis)
        }
        val area = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (i in 0 until xs.size - 1) {
            line.color = when { points[i + 1].second > points[i].second -> green; points[i + 1].second < points[i].second -> red; else -> grey }
            val areaColor = line.color
            val segment = Path().apply {
                moveTo(xs[i], ys[i]); lineTo(xs[i + 1], ys[i + 1]); lineTo(xs[i + 1], b); lineTo(xs[i], b); close()
            }
            area.shader = LinearGradient(0f, t, 0f, b, Color.argb(80, Color.red(areaColor), Color.green(areaColor), Color.blue(areaColor)), Color.argb(5, Color.red(areaColor), Color.green(areaColor), Color.blue(areaColor)), Shader.TileMode.CLAMP)
            canvas.drawPath(segment, area)
            canvas.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], line)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(0,48,120) }
        xs.forEachIndexed { i, x -> canvas.drawCircle(x, ys[i], 3f, dot); canvas.drawCircle(x, ys[i], 3f, ring) }
        val datePaint = paint(grey, 7f, font, Paint.Align.CENTER)
        if (points.size <= 14) points.forEachIndexed { i, p -> canvas.drawText(SimpleDateFormat("dd/MM", Locale.US).format(Date(p.first)), xs[i], b + 18f, datePaint) }
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply { timeInMillis = millis; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun bitmap(context: Context, id: Int): Bitmap? {
        val drawable = context.getDrawable(id) ?: return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val w = max(1, drawable.intrinsicWidth)
        val h = max(1, drawable.intrinsicHeight)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val c = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(c)
        }
    }
    private fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, rect: RectF) { canvas.save(); val path = Path().apply { addOval(rect, Path.Direction.CW) }; canvas.clipPath(path); canvas.drawBitmap(bitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG)); canvas.restore() }
    private fun paint(color: Int, size: Float, typeface: Typeface, align: Paint.Align) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; this.typeface = typeface; textAlign = align }
    private fun parse(value: String): Double? = Regex("[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?").find(value)?.value?.replace(",", "")?.toDoubleOrNull()
    private fun formatNumber(value: Double): String = String.format(Locale.US, "%,.0f", value)
    private fun formatValue(value: Double, itemKey: String): String = if (itemKey.startsWith("CRYPTO_") || itemKey.startsWith("METAL_")) {
        String.format(Locale.US, if (value < 1.0) "%.4f" else "%,.2f", value)
    } else if (itemKey in setOf("FX_EUR", "FX_GBP", "FX_JOD", "FX_AED", "FX_KWD", "FX_EGP", "FX_CNY", "FX_SAR")) {
        String.format(Locale.US, "%,.2f", value)
    } else {
        formatNumber(value)
    }
    private fun numericText(value: String): String = Regex("[0-9][0-9,.]*").find(value)?.value ?: value
    private data class ReportUnits(val from: String, val to: String)

    private fun reportUnits(itemKey: String, unitText: String, tag: String): ReportUnits = when {
        itemKey.startsWith("CRYPTO_") || itemKey.startsWith("METAL_") ->
            ReportUnits(unitText.ifBlank { "1" }, "$")
        else -> reportUnitsFx(itemKey, unitText, tag)
    }

    private fun reportUnitsFx(itemKey: String, unitText: String, tag: String): ReportUnits = when (itemKey) {
        "FX_EUR" -> ReportUnits("100€", "$")
        "FX_GBP" -> ReportUnits("100£", "$")
        "FX_JOD" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "د.أ" else "JOD")
        "FX_AED" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "د.إ" else "AED")
        "FX_KWD" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "د.ك" else "KWD")
        "FX_EGP" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "ج.م" else "EGP")
        "FX_CNY" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "ي.ص" else "CNY")
        "FX_SAR" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "ر.س" else "SAR")
        "FX_SYP" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "ل.س" else "SYP")
        "TRY_INVESTING" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "ل.ت" else "TL")
        "IRR_TELEGRAM" -> ReportUnits("100$", when (tag) {
            "en" -> "Toman"
            "tr" -> "Tümen"
            "ku" -> "تۆمان"
            else -> "تومان"
        })
        "USD_OFFICIAL", "USD_BAGHDAD" -> ReportUnits("100$", if (tag == "ar" || tag == "ku") "د.ع" else "IQD")
        else -> {
            val source = if (unitText.contains("€")) "100€" else if (unitText.contains("£")) "100£" else "100$"
            ReportUnits(source, if (source == "100$") (if (tag == "ar" || tag == "ku") "د.ع" else "IQD") else "$")
        }
    }
    private fun compactUnit(unit: String): String {
        val u = unit.lowercase(Locale.ROOT)
        return when {
            u.contains("دينار") || u.contains("iqd") || u.contains("عراقي") -> "د.ع"
            u.contains("ليرة") || u.contains("try") || u.contains("تركية") -> "ل.ت"
            u.contains("تومان") || u.contains("irr") || u.contains("إيراني") -> "ت"
            u.contains("يورو") || u.contains("eur") -> "€"
            u.contains("باوند") || u.contains("gbp") -> "£"
            else -> unit
        }
    }
    private fun drawPageNumber(canvas: Canvas, page: Int, rtl: Boolean, color: Int, font: Typeface) {
        val p = paint(color, 8f, font, if (rtl) Paint.Align.LEFT else Paint.Align.RIGHT)
        canvas.drawText(page.toString(), if (rtl) 32f else 563f, 832f, p)
    }

    private data class Labels(val appName: String, val version: String, val exchange: String, val period: String, val current: String, val last: String, val highest: String, val average: String, val lowest: String, val unit: String, val details: String, val date: String, val time: String, val price: String, val footer: String) {
        companion object {
            fun forTag(tag: String, context: Context): Labels {
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
                return when (tag) {
                    "en" -> Labels("Borsat Dinari", version, "Exchange", "Selected period", "Current price", "Last price", "Highest price", "Average", "Lowest price", "IQD", "Details", "Date", "Time", "Price", "App: Borsat Dinari  •  Version: $version  •  Designed by engineer: Mohammed Mokhtar Faiq")
                    "tr" -> Labels("Borsat Dinari", version, "Borsa", "Seçilen dönem", "Güncel fiyat", "Son fiyat", "En yüksek", "Ortalama", "En düşük", "IQD", "Detaylar", "Tarih", "Saat", "Fiyat", "Uygulama: Borsat Dinari  •  Sürüm: $version  •  Tasarım mühendisi: Mohammed Mokhtar Faiq")
                    "ku" -> Labels("بورصە دیناری", version, "بۆرسە", "ماوەی هەڵبژێردراو", "نرخی ئێستا", "دواین نرخ", "بەرزترین نرخ", "ناوەند", "کەمترین نرخ", "د.ع", "وردەکاری", "بەروار", "کات", "نرخ", "ئەپ: بورصە دیناری  •  وەشان: $version  •  دیزاینەر، ئەندازیار: محمد مختار فائق")
                    else -> Labels("بورصة ديناري", version, "البورصة", "الفترة المختارة", "السعر الحالي", "آخر سعر", "أعلى سعر", "المتوسط", "أدنى سعر", "د.ع", "التفاصيل", "التاريخ", "الوقت", "السعر", "التطبيق: بورصة ديناري  •  الإصدار: $version  •  تصميم المهندس: محمد مختار فائق")
                }
            }
        }
    }
}

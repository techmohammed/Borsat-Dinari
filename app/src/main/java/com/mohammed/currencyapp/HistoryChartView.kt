package com.mohammed.currencyapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * مخطط بياني بسيط (خط متعرج) لتطور سعر بورصة/عملة عبر الوقت — مرسوم يدوياً
 * على Canvas مباشرة (بدون أي مكتبة رسوم بيانية خارجية، حتى ما نحتاج نضيف
 * تبعية جديدة بالمشروع). المحور الأفقي = الوقت، المحور العمودي = السعر.
 * كل قطعة من الخط بين نقطتين متتاليتين تلوّن أخضر لو السعر صاعد، أحمر لو
 * نازل، رصاصي لو ثابت — بنفس ألوان الاتجاه المستخدمة بباقي التطبيق
 * (trendUp / trendDown / trendFlat).
 */
class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // (توقيت، سعر) — دايماً بترتيب تصاعدي بالوقت (الأقدم أولاً يساراً، زي المخطط المرجعي).
    private var points: List<Pair<Long, Double>> = emptyList()

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)

    private val colorUp = ContextCompat.getColor(context, R.color.trendUp)
    private val colorDown = ContextCompat.getColor(context, R.color.trendDown)
    private val colorFlat = ContextCompat.getColor(context, R.color.trendFlat)
    private val colorAxisText = ContextCompat.getColor(context, R.color.textSecondary)
    private val labelTypeface = ResourcesCompat.getFont(context, R.font.cairo_regular)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
        strokeWidth = dp(1f)
        pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAxisText
        textSize = dp(11f)
        typeface = labelTypeface
    }
    private val avgLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
        color = Color.parseColor("#9575CD")
        pathEffect = DashPathEffect(floatArrayOf(dp(8f), dp(5f)), 0f)
    }
    private val avgLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9575CD")
        textSize = dp(10.5f)
        typeface = labelTypeface
    }
    // النص عبارة جملتين، فلازم TextPaint مع StaticLayout حتى يلتف
    // (wrap) تلقائياً بعرض المخطط المتاح بدل سطر وحد يطلع خارج الشاشة —
    // المحاذاة (توسيط) تُطبَّق عبر Layout.Alignment.ALIGN_CENTER مو
    // Paint.textAlign، حتى ما يتعارضون مع بعض بحساب StaticLayout للعرض.
    private val emptyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAxisText
        textSize = dp(13f)
        typeface = labelTypeface
    }

    /**
     * يستقبل سجلات الهيستوري (بأي ترتيب)، يرتبها تصاعدياً بالوقت، ويحلل
     * القيمة الرقمية من نص كل سعر. يدعم كل صيغ الأسعار المختلفة بالتطبيق
     * ("156,650 د.ع"، "115.10 دولار"، "يورو 115.10"، "4,859"...) لأنه بس
     * يلتقط أول رقم موجود بالنص، بغض النظر عن مكان النص المحيط به.
     */
    fun setEntries(entries: List<HistoryEntry>) {
        points = entries
            .sortedBy { it.timestampMillis }
            .mapNotNull { entry -> parsePriceValue(entry.price)?.let { entry.timestampMillis to it } }
        invalidate()
    }

    private fun parsePriceValue(priceText: String): Double? {
        val match = Regex("""[0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?""").find(priceText) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    /** رسالة الحالة الفاضية (أقل من نقطتين): توضح للمستخدم ليش المخطط
     * فاضي وشلون يحله (توسيع الفترة بالتقويم ثم التنزيل)، بدل رسالة قصيرة
     * غامضة. تُرسم بـStaticLayout حتى تلتف تلقائياً على عرض المخطط. */
    private fun drawEmptyMessage(canvas: Canvas, w: Float, h: Float) {
        val message = "لا توجد بيانات كافية لرسم المخطط التوضيحي يرجى الضغط على زر التقويم وتحديد فترة اطول ثم الضغط على زر التنزيل"
        val horizontalPadding = dp(24f)
        val layoutWidth = (w - horizontalPadding * 2).toInt().coerceAtLeast(dp(120f).toInt())

        @Suppress("DEPRECATION")
        val staticLayout = StaticLayout(
            message, emptyTextPaint, layoutWidth,
            Layout.Alignment.ALIGN_CENTER, 1.2f, 0f, false
        )

        canvas.save()
        canvas.translate((w - layoutWidth) / 2f, h / 2f - staticLayout.height / 2f)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        if (points.size < 2) {
            drawEmptyMessage(canvas, w, h)
            return
        }

        val prices = points.map { it.second }
        val minPrice = prices.min()
        val maxPrice = prices.max()
        val range = (maxPrice - minPrice).let { if (it < 1.0) 1.0 else it }
        // هامش 12% فوق وتحت حتى ما تلتصق النقاط بحواف المخطط.
        val paddedMin = minPrice - range * 0.12
        val paddedMax = maxPrice + range * 0.12
        val paddedRange = (paddedMax - paddedMin).let { if (it <= 0.0) 1.0 else it }
        // أسعار العملات (يورو/پاوند) صغيرة وبفاصلة عشرية، عكس أسعار المدن
        // بالدينار (أرقام كبيرة صحيحة) — نفرّق شكل تسمية المحور العمودي تبعاً لهذا.
        val useDecimalLabels = maxPrice < 1000.0

        val leftPad = dp(64f)
        val rightPad = dp(12f)
        val topPad = dp(12f)
        val bottomPad = dp(46f)

        val chartLeft = leftPad
        val chartRight = w - rightPad
        val chartTop = topPad
        val chartBottom = h - bottomPad
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        fun xFor(index: Int): Float =
            chartLeft + if (points.size == 1) chartWidth / 2f
            else chartWidth * index / (points.size - 1).toFloat()

        fun yFor(price: Double): Float =
            (chartBottom - ((price - paddedMin) / paddedRange * chartHeight)).toFloat()

        // خطوط شبكة أفقية متقطعة + تسميات السعر.
        val gridLines = 5
        axisTextPaint.textAlign = Paint.Align.LEFT
        for (i in 0..gridLines) {
            val ratio = i / gridLines.toFloat()
            val y = chartTop + chartHeight * ratio
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            val priceAtLine = paddedMax - paddedRange * ratio
            val label = if (useDecimalLabels) String.format(Locale.US, "%.2f", priceAtLine) else String.format(Locale.US, "%,d", priceAtLine.toLong())
            canvas.drawText(label, dp(4f), y + dp(4f), axisTextPaint)
        }

        // خط رفيع متقطع لمتوسط السعر خلال الفترة المعروضة كاملة — يساعد
        // المستخدم يشوف هل السعر الحالي أعلى أو أوطى من المعتاد بالفترة.
        val avgPrice = prices.average()
        val avgY = yFor(avgPrice)
        canvas.drawLine(chartLeft, avgY, chartRight, avgY, avgLinePaint)
        val avgLabel = "المتوسط " + if (useDecimalLabels) String.format(Locale.US, "%.2f", avgPrice) else String.format(Locale.US, "%,d", avgPrice.toLong())
        avgLabelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(avgLabel, chartRight, avgY - dp(4f), avgLabelPaint)

        // خط السعر المتعرج، مقسّم لقطع ملوّنة حسب الصعود/النزول بين كل نقطتين متتاليتين.
        for (i in 0 until points.size - 1) {
            val p1 = points[i].second
            val p2 = points[i + 1].second
            linePaint.color = when {
                p2 > p1 -> colorUp
                p2 < p1 -> colorDown
                else -> colorFlat
            }
            canvas.drawLine(xFor(i), yFor(p1), xFor(i + 1), yFor(p2), linePaint)
        }

        // نقطة دائرية بكل قيمة — لونها نفس لون القطعة الواصلة إليها (رمادي لأول نقطة، ما إلها سابقة).
        for (i in points.indices) {
            dotPaint.color = if (i == 0) {
                colorFlat
            } else {
                val prev = points[i - 1].second
                val cur = points[i].second
                when {
                    cur > prev -> colorUp
                    cur < prev -> colorDown
                    else -> colorFlat
                }
            }
            canvas.drawCircle(xFor(i), yFor(points[i].second), dp(4f), dotPaint)
        }

        // تسميات الوقت أسفل المحور، مائلة قليلاً حتى تتسع لعدد أكبر بدون تداخل.
        // لو النقاط كثيرة، نعرض تسمية كل نقطة N فقط حتى لا تتزاحم.
        val approxLabelWidth = dp(70f)
        val maxLabels = (chartWidth / approxLabelWidth).toInt().coerceAtLeast(2)
        val step = (points.size / maxLabels).coerceAtLeast(1)

        axisTextPaint.textAlign = Paint.Align.RIGHT
        for (i in points.indices) {
            if (i % step != 0 && i != points.size - 1) continue
            val label = timeFormat.format(points[i].first)
            val x = xFor(i)
            val y = chartBottom + dp(16f)
            canvas.save()
            canvas.rotate(-30f, x, y)
            canvas.drawText(label, x, y, axisTextPaint)
            canvas.restore()
        }
    }
}

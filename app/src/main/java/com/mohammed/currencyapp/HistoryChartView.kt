package com.mohammed.currencyapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * مخطط خطي لتطور سعر بورصة/عملة عبر الوقت، مرسوم يدوياً على Canvas (بدون
 * مكتبة رسوم خارجية). الشكل: شبكة خفيفة، تسميات سعر "مدوّرة" يسار، تسميات
 * وقت أسفل (ساعات لو الفترة يوم، وتواريخ يوم/شهر لو أكثر)، خط ونقاط بلون
 * اتجاه الفترة كلها (أخضر لو السعر بآخر الفترة أعلى من أولها، أحمر لو أوطى،
 * رصاصي لو نفسه) وتحته تعبئة متدرجة بنفس اللون.
 *
 * المحور الأفقي زمني حقيقي (مو متباعد بالتساوي بين النقاط): الموقع يتحدد
 * بوقت كل نقطة ضمن الفترة المعروضة، حتى تبين الفراغات الزمنية الفعلية.
 */
class HistoryChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // (توقيت، سعر) بترتيب تصاعدي بالوقت.
    private var points: List<Pair<Long, Double>> = emptyList()
    private var axisFrom = 0L
    private var axisTo = 1L
    // true لو الفترة أكثر من يوم: نعرض معدل كل يوم كنقطة وحدة (موضوعة على بداية اليوم).
    private var dailyMode = false

    private val dayMs = 24L * 60 * 60 * 1000
    private val hourMs = 60L * 60 * 1000
    private val dateFormat = SimpleDateFormat("d/M", Locale.US)
    private val hourFormat = SimpleDateFormat("HH:mm", Locale.US)

    private val colorUp = ContextCompat.getColor(context, R.color.deltaUp)
    private val colorDown = ContextCompat.getColor(context, R.color.deltaDown)
    private val colorFlat = ContextCompat.getColor(context, R.color.trendFlat)
    private val colorAxisText = ContextCompat.getColor(context, R.color.chartAxisText)
    private val colorGrid = ContextCompat.getColor(context, R.color.chartGrid)
    private val colorRing = ContextCompat.getColor(context, R.color.cardWhite)
    private val labelTypeface = ResourcesCompat.getFont(context, R.font.cairo_regular)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorGrid
        strokeWidth = dp(1f)
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAxisText
        textSize = dp(11.5f)
        typeface = labelTypeface
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // بدون anti-alias عمداً: تعبئة كل قطعة (أخضر/أحمر) مضلع منفصل، وبدون AA ما تبين
    // خطوط رفيعة بين القطع المتجاورة (حافة الخط العلوية يغطيها الخط نفسه).
    private val fillPaint = Paint().apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorRing
    }
    private val emptyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAxisText
        textSize = dp(13f)
        typeface = labelTypeface
    }

    private fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /**
     * يستقبل سجلات الهيستوري (بأي ترتيب) والفترة المعروضة [fromMillis]..[toMillis].
     * القيمة الرقمية تُلتقط من أول رقم بنص السعر ("156,650 د.ع"، "115.10"...).
     *
     * لو الفترة أكثر من يوم: نجمع أسعار كل يوم بمعدل واحد ونرسم نقطة وحدة لكل يوم
     * (على بداية اليوم، تحت تسمية تاريخه) بدل عشرات النقاط المتزاحمة. لو الفترة
     * يوم واحد نرسم كل النقاط بوقتها الفعلي.
     */
    fun setEntries(entries: List<HistoryEntry>, fromMillis: Long, toMillis: Long) {
        val raw = entries
            .sortedBy { it.timestampMillis }
            .mapNotNull { entry -> parsePriceValue(entry.price)?.let { entry.timestampMillis to it } }
        val now = System.currentTimeMillis()
        val fromDay = startOfDay(fromMillis)
        val toDay = startOfDay(max(min(toMillis, now), fromMillis))
        dailyMode = toDay > fromDay

        if (dailyMode) {
            val byDay = LinkedHashMap<Long, MutableList<Double>>()
            for ((t, v) in raw) byDay.getOrPut(startOfDay(t)) { mutableListOf() }.add(v)
            points = byDay.map { (day, values) -> day to values.average() }.sortedBy { it.first }
            axisFrom = fromDay
            axisTo = toDay
        } else {
            points = raw
            axisFrom = fromMillis
            axisTo = max(min(toMillis, now), raw.lastOrNull()?.first ?: toMillis)
        }
        if (axisTo <= axisFrom) axisTo = axisFrom + 1
        invalidate()
    }

    private fun parsePriceValue(priceText: String): Double? {
        val match = Regex("""[0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?""").find(priceText) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    private fun drawEmptyMessage(canvas: Canvas, w: Float, h: Float) {
        val message = "لا توجد بيانات كافية لرسم المخطط، جرّب فترة أطول أو اختر فترة من التقويم"
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

    /** يحدد حدود المحور العمودي وخطوته بأرقام "مدوّرة" (1، 2، 2.5، 5 × 10^n). */
    private fun niceScale(minV: Double, maxV: Double): Triple<Double, Double, Double> {
        val rawSpan = maxV - minV
        val span = if (rawSpan < 1e-9) max(abs(maxV) * 0.01, 1.0) else rawSpan
        val rawStep = span / 4.0
        val mag = 10.0.pow(floor(log10(rawStep)))
        val norm = rawStep / mag
        val step = mag * when {
            norm <= 1.0 -> 1.0
            norm <= 2.0 -> 2.0
            norm <= 2.5 -> 2.5
            norm <= 5.0 -> 5.0
            else -> 10.0
        }
        val axisMin = floor((minV - span * 0.05) / step) * step
        val axisMax = ceil((maxV + span * 0.05) / step) * step
        return Triple(axisMin, axisMax, step)
    }

    private fun formatTick(v: Double, step: Double): String = when {
        step >= 1.0 -> String.format(Locale.US, "%,d", Math.round(v))
        step >= 0.1 -> String.format(Locale.US, "%.1f", v)
        else -> String.format(Locale.US, "%.2f", v)
    }

    /** نقاط تسميات المحور الأفقي: ساعات لو الفترة يوم واحد، وإلا تواريخ بأيام مرتبة. */
    private fun buildXTicks(): List<Pair<Long, String>> {
        val span = axisTo - axisFrom
        val out = ArrayList<Pair<Long, String>>()
        if (!dailyMode) {
            val stepH = when {
                span <= 6 * hourMs -> 1
                span <= 12 * hourMs -> 2
                span <= 20 * hourMs -> 3
                else -> 4
            }
            val cal = Calendar.getInstance().apply {
                timeInMillis = axisFrom
                set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            // أول تسمية على أقرب ساعة من مضاعفات stepH تساوي أو تلي بداية المحور.
            while (cal.timeInMillis < axisFrom || cal.get(Calendar.HOUR_OF_DAY) % stepH != 0) {
                cal.add(Calendar.HOUR_OF_DAY, 1)
            }
            var guard = 0
            while (cal.timeInMillis <= axisTo && guard++ < 30) {
                out.add(cal.timeInMillis to hourFormat.format(cal.timeInMillis))
                cal.add(Calendar.HOUR_OF_DAY, stepH)
            }
            if (out.isEmpty()) {
                out.add(axisFrom to hourFormat.format(axisFrom))
                out.add(axisTo to hourFormat.format(axisTo))
            }
        } else {
            val days = Math.round(span / dayMs.toDouble()).toInt() + 1
            val k = when {
                days <= 7 -> 1
                days <= 14 -> 2
                days <= 35 -> 5
                days <= 70 -> 10
                else -> 15
            }
            val cal = Calendar.getInstance().apply {
                timeInMillis = axisFrom
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            var guard = 0
            while (cal.timeInMillis <= axisTo && guard++ < 40) {
                out.add(cal.timeInMillis to dateFormat.format(cal.timeInMillis))
                cal.add(Calendar.DAY_OF_YEAR, k)
            }
        }
        return out
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
        val (axisMin, axisMax, step) = niceScale(prices.min(), prices.max())
        val axisRange = (axisMax - axisMin).let { if (it <= 0.0) 1.0 else it }

        val chartLeft = dp(58f)
        val chartRight = w - dp(22f)
        val chartTop = dp(14f)
        val chartBottom = h - dp(34f)
        val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
        val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)

        fun xFor(t: Long): Float =
            chartLeft + (chartWidth * ((t - axisFrom).toDouble() / (axisTo - axisFrom).toDouble())).toFloat()

        fun yFor(price: Double): Float =
            (chartBottom - ((price - axisMin) / axisRange * chartHeight)).toFloat()

        // شبكة أفقية + تسميات السعر (يسار).
        axisTextPaint.textAlign = Paint.Align.LEFT
        val n = Math.round(axisRange / step).toInt().coerceIn(1, 12)
        for (i in 0..n) {
            val v = axisMin + i * step
            val y = yFor(v)
            canvas.drawLine(chartLeft, y, chartRight, y, gridPaint)
            canvas.drawText(formatTick(v, step), dp(6f), y + dp(4f), axisTextPaint)
        }

        // شبكة عمودية + تسميات الوقت (أسفل).
        axisTextPaint.textAlign = Paint.Align.CENTER
        for ((t, label) in buildXTicks()) {
            val x = xFor(t)
            canvas.drawLine(x, chartTop, x, chartBottom, gridPaint)
            canvas.drawText(label, x, chartBottom + dp(22f), axisTextPaint)
        }

        // لون كل قطعة حسب اتجاه السعر بين نقطتين متتاليتين (يوم لليوم اللي بعده بالفترات
        // الطويلة): أخضر صعود، أحمر نزول، رصاصي ثابت. التعبئة تحت القطعة بنفس لونها.
        val n2 = points.size - 1
        val segColors = IntArray(n2) { i ->
            val a = points[i].second
            val b = points[i + 1].second
            when {
                b > a -> colorUp
                b < a -> colorDown
                else -> colorFlat
            }
        }
        val xs = FloatArray(points.size) { xFor(points[it].first) }
        val ys = FloatArray(points.size) { yFor(points[it].second) }

        // تدرج لكل لون: يبدأ من أعلى نقطة بالخط وينتهي شفاف عند أسفل المخطط.
        val topY = ys.min()
        val shaders = HashMap<Int, LinearGradient>()
        fun shaderFor(color: Int): LinearGradient = shaders.getOrPut(color) {
            LinearGradient(
                0f, topY, 0f, chartBottom,
                ColorUtils.setAlphaComponent(color, 95),
                ColorUtils.setAlphaComponent(color, 6),
                Shader.TileMode.CLAMP
            )
        }

        val seg = Path()
        for (i in 0 until n2) {
            seg.rewind()
            seg.moveTo(xs[i], ys[i])
            seg.lineTo(xs[i + 1], ys[i + 1])
            seg.lineTo(xs[i + 1], chartBottom)
            seg.lineTo(xs[i], chartBottom)
            seg.close()
            fillPaint.shader = shaderFor(segColors[i])
            canvas.drawPath(seg, fillPaint)
        }

        for (i in 0 until n2) {
            linePaint.color = segColors[i]
            canvas.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], linePaint)
        }

        // نقاط الأسعار (لو أكثر من 31 نقطة نرسم آخر نقطة فقط حتى ما يزدحم المخطط).
        val dotIndices = if (points.size <= 31) points.indices else (points.size - 1)..(points.size - 1)
        val dotR = if (points.size > 14) 3.2f else 3.8f
        for (i in dotIndices) {
            dotPaint.color = if (i == 0) segColors[0] else segColors[i - 1]
            canvas.drawCircle(xs[i], ys[i], dp(dotR + 1.7f), ringPaint)
            canvas.drawCircle(xs[i], ys[i], dp(dotR), dotPaint)
        }
    }
}

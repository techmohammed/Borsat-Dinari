package com.mohammed.currencyapp

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class PriceListAdapter(
    private var rows: List<ListRow>,
    private var favoriteKeys: List<String> = listOf("baghdad"),
    private val onStarClick: (String) -> Unit = {},
    private val onRowClick: (PriceItem) -> Unit = {},
    private val dragMode: Boolean = false,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
        private const val TYPE_FAVORITE = 2
    }

    // مفاتيح الصفوف اللي سعرها فعلياً تغيّر بآخر submitList — تُستخدم لتشغيل
    // أنيميشن "الأرقام المبعثرة تستقر" على صفوف تغيّرت بس، مو كل الصفوف
    // (تحديث يدوي بالسحب للأسفل، أو تحديث تلقائي كل 15 دقيقة وهو مفتوح).
    private var changedKeys: MutableSet<String> = mutableSetOf()
    private val clearHandler = Handler(Looper.getMainLooper())
    private val clearChanged = Runnable { changedKeys = mutableSetOf() }

    fun submitList(newRows: List<ListRow>, newFavoriteKeys: List<String>) {
        val oldPriceByKey = rows.filterIsInstance<ListRow.Row>()
            .associate { (it.item.cityKey ?: it.item.name) to it.item.price }

        changedKeys = newRows.filterIsInstance<ListRow.Row>()
            .mapNotNull { row ->
                val key = row.item.cityKey ?: row.item.name
                val old = oldPriceByKey[key]
                if (old != null && old != row.item.price) key else null
            }
            .toMutableSet()

        rows = newRows
        favoriteKeys = newFavoriteKeys
        notifyDataSetChanged()

        // الصف يستهلك علامة "تغيّر" لحظة ربطه (فتشتغل الحركة مرة وحدة فقط)،
        // وإذا ما انربط (خارج الشاشة) نمسحها بعد ثانية ونصف حتى ما تتحرك لاحقاً بالسكرول.
        clearHandler.removeCallbacks(clearChanged)
        clearHandler.postDelayed(clearChanged, 1500L)
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ListRow.Header -> TYPE_HEADER
        is ListRow.Row -> TYPE_ROW // تظل هيئة البطاقة واحدة سواء كانت مفضلة أم لا.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_section_header, parent, false)
                KurdishFont.apply(view, parent.context)
                HeaderViewHolder(view)
            }
            TYPE_FAVORITE -> {
                val view = inflater.inflate(R.layout.item_price_favorite, parent, false)
                KurdishFont.apply(view, parent.context)
                RowViewHolder(view, onStarClick, onRowClick, dragMode, onStartDrag)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_price, parent, false)
                KurdishFont.apply(view, parent.context)
                RowViewHolder(view, onStarClick, onRowClick, dragMode, onStartDrag)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ListRow.Header -> (holder as HeaderViewHolder).bind(row.title)
            is ListRow.Row -> {
                (holder as RowViewHolder).bind(row.item, favoriteKeys, animatePrice = true)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? RowViewHolder)?.cancelPriceAnimation()
    }

    override fun getItemCount(): Int = rows.size

    /** إعادة ترتيب الصفوف أثناء السحب (صفحة الرئيسية). */
    fun moveItem(from: Int, to: Int) {
        if (from !in rows.indices || to !in rows.indices || from == to) return
        val mutable = rows.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        rows = mutable
        notifyItemMoved(from, to)
    }

    /** مفاتيح الصفوف بالترتيب الحالي. */
    fun currentKeys(): List<String> =
        rows.filterIsInstance<ListRow.Row>().map { it.item.cityKey ?: it.item.name }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: android.widget.TextView = view.findViewById(R.id.tvHeader)
        fun bind(title: String) { tvHeader.text = title }
    }

    class RowViewHolder(
        view: View,
        private val onStarClick: (String) -> Unit,
        private val onRowClick: (PriceItem) -> Unit,
        private val dragMode: Boolean = false,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit = {}
    ) : RecyclerView.ViewHolder(view) {
        private val tvPrice: android.widget.TextView = view.findViewById(R.id.tvPrice)
        private val tvResultUnit: android.widget.TextView = view.findViewById(R.id.tvResultUnit)
        private val tvUnit: android.widget.TextView = view.findViewById(R.id.tvUnit)
        private val tvCode: android.widget.TextView = view.findViewById(R.id.tvCode)
        private val tvUpdateTime: android.widget.TextView = view.findViewById(R.id.tvUpdateTime)
        private val tvUpdateDate: android.widget.TextView = view.findViewById(R.id.tvUpdateDate)
        private val timeRow: View = view.findViewById(R.id.timeRow)
        private val dateRow: View = view.findViewById(R.id.dateRow)
        private val tvName: android.widget.TextView = view.findViewById(R.id.tvName)
        private val ivFlag: android.widget.ImageView = view.findViewById(R.id.ivFlag)
        init {
            // كل الأيقونات (أعلام، عملات رقمية، معادن) تُقصّ دائرية بغض النظر عن شكل الصورة الأصلي.
            ivFlag.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) { outline.setOval(0, 0, v.width, v.height) }
            }
            ivFlag.clipToOutline = true
            // وحدة السعر تتحاذى مع منتصف سطر التاريخ تماماً (نفس الارتفاع) مهما كانت الشاشة.
            val priceGroup: View = view.findViewById(R.id.priceGroup)
            view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val dateParent = dateRow.parent as? View ?: return@addOnLayoutChangeListener
                val dateCenter = dateParent.top + dateRow.top + dateRow.height / 2f
                val unitCenterNow = priceGroup.top + tvResultUnit.top + tvResultUnit.height / 2f
                val shift = dateCenter - unitCenterNow
                if (kotlin.math.abs(tvResultUnit.translationY - shift) > 0.5f) tvResultUnit.translationY = shift
            }
        }
        private val deltaRow: View = view.findViewById(R.id.deltaRow)
        private val ivDeltaArrow: android.widget.ImageView = view.findViewById(R.id.ivDeltaArrow)
        private val tvDelta: android.widget.TextView = view.findViewById(R.id.tvDelta)
        private val btnStar: android.widget.ImageButton = view.findViewById(R.id.btnStar)

        private val animHandler = Handler(Looper.getMainLooper())
        private var animRunnable: Runnable? = null

        fun bind(item: PriceItem, favoriteKeys: List<String>, animatePrice: Boolean) {
            val context = itemView.context
            val rowKey = item.cityKey ?: item.name
            tvUnit.text = item.unit
            tvCode.text = currencyIsoCode(item)
            tvUnit.visibility = View.VISIBLE
            if (rowKey.startsWith("METAL_")) {
                // المعادن: الرمز الإنجليزي (AU...) تحت الاسم، وتحته الوحدة (غرام / طن متري).
                tvCode.text = MetalsRepository.symbolFor(rowKey).ifEmpty { item.unit }
            } else if (rowKey.startsWith("CRYPTO_")) {
                // العملات الرقمية: الاسم والرمز فقط.
                tvUnit.visibility = View.GONE
            }
            tvResultUnit.text = resultCurrencyCode(item, context)
            // وقت وتاريخ آخر تحديث فعلي لهذا السعر (نفس الصيغة بكل اللغات).
            val updatedAt = item.updateTimeMillis
            if (updatedAt != null && item.price != "—") {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = updatedAt }
                tvUpdateTime.text = String.format(java.util.Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                tvUpdateDate.text = UpdateTimeFormat.dateDMY(updatedAt)
                timeRow.visibility = View.VISIBLE
                dateRow.visibility = View.VISIBLE
            } else {
                timeRow.visibility = View.INVISIBLE
                dateRow.visibility = View.INVISIBLE
            }
            tvName.text = item.name
            val key = item.cityKey ?: item.name

            // الصف كله يتلون: أخضر فاتح = صعود، أحمر فاتح = نزول، أزرق فاتح = مستقر.
            // الظل الخفيف تحت الصف حتى يبرز.
            val rowBackground = (ContextCompat.getDrawable(context, R.drawable.bg_row_card)?.mutate()
                as? android.graphics.drawable.GradientDrawable)
            rowBackground?.setColor(
                ContextCompat.getColor(
                    context,
                    when (item.trend) {
                        Trend.UP -> R.color.rowUpBg
                        Trend.DOWN -> R.color.rowDownBg
                        Trend.FLAT -> R.color.currencyCard
                    }
                )
            )
            itemView.background = rowBackground
            itemView.elevation = 3f * context.resources.displayMetrics.density

            // علم صغير بدائرة بجانب الاسم.
            val flagResId = context.resources.getIdentifier(
                "flag_${item.flagDrawable}", "drawable", context.packageName
            )
            if (flagResId != 0) ivFlag.setImageResource(flagResId)
            else ivFlag.setImageDrawable(null)

            // مقدار التغيير أسفل السعر: مثلث أخضر + "+500" أو مثلث أحمر + "-300".
            if (item.price == "—") {
                deltaRow.visibility = View.INVISIBLE
            } else if (item.trend == Trend.FLAT) {
                // مستقر أو بدون تحديث: صف رصاصي بدون رقم تغيير.
                deltaRow.visibility = View.INVISIBLE
            } else {
                val amount = PriceDelta.absText(context, key)
                if (amount == null) {
                    deltaRow.visibility = View.INVISIBLE
                } else {
                    deltaRow.visibility = View.VISIBLE
                    ivDeltaArrow.visibility = View.VISIBLE
                    if (item.trend == Trend.UP) {
                        deltaRow.background = null
                        ivDeltaArrow.setImageResource(R.drawable.ic_tri_up)
                        KurdishFont.setMainNumberText(tvDelta, "+$amount")
                        tvDelta.setTextColor(ContextCompat.getColor(context, R.color.deltaUp))
                    } else {
                        deltaRow.background = null
                        ivDeltaArrow.setImageResource(R.drawable.ic_tri_down)
                        KurdishFont.setMainNumberText(tvDelta, "-$amount")
                        tvDelta.setTextColor(ContextCompat.getColor(context, R.color.deltaDown))
                    }
                }
            }

            if (dragMode) {
                // الرئيسية: ثلاث خطوط للسحب وتغيير ترتيب الصف (يُحفظ ويثبت مكانه).
                btnStar.setImageResource(R.drawable.ic_drag_handle)
                btnStar.setOnClickListener(null)
                btnStar.setOnTouchListener { _, event ->
                    if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                        // لا نمنع RecyclerView من الاعتراض (يحتاجه ItemTouchHelper)؛ الصفحة تمنع السكرول من onStartDrag.
                        onStartDrag(this)
                        true
                    } else false
                }
            } else {
                btnStar.setOnTouchListener(null)
                btnStar.setImageResource(
                    if (key in favoriteKeys) R.drawable.ic_star_filled else R.drawable.ic_star_outline
                )
                btnStar.setOnClickListener { onStarClick(key) }
            }
            btnStar.visibility = View.VISIBLE

            // الضغط على أي مكان ثاني بالصف (غير النجمة) يفتح هيستوري هذه البورصة.
            itemView.setOnClickListener { onRowClick(item) }

            cancelPriceAnimation()
            // السعر بالأسود وبدون "د.ع" (الوحدة تحت الاسم). نبقي item.price الأصلي
            // للمقارنة وللودجت والهيستوري، ونعرض بس النسخة المختصرة هنا.
            val displayPrice = item.price.removeSuffix(" د.ع").trimStart().removePrefix("$").trim()
            // حجم السعر ثابت (18dp) لكل الصفوف والأطوال والشاشات، من ملف الـlayout، بدون تكيّف.
            val priceKey = item.cityKey ?: item.name
            val startStep = if (animatePrice) PriceAnimState.stepFor(priceKey, item.price, System.currentTimeMillis()) else 0
            val plan = if (startStep > 0) DigitRevealAnimator.plan(displayPrice) else null
            if (plan == null) {
                KurdishFont.setMainNumberText(tvPrice, displayPrice)
                return
            }

            // نفس فكرة أنيميشن الودجت بالضبط: أرقام عشوائية تستقر تدريجياً
            // على الرقم الصحيح خلال ~900ms، بدل تغيير الرقم فجأة.
            val frameRate = PriceAnimState.FRAME_MS
            val totalSteps = (PriceAnimState.DURATION_MS / frameRate).toInt().coerceAtLeast(1)
            var step = startStep - 1
            val runnable = object : Runnable {
                override fun run() {
                    step++
                    KurdishFont.setMainNumberText(
                        tvPrice, DigitRevealAnimator.frameText(displayPrice, plan, step, totalSteps)
                    )
                    if (step < totalSteps) animHandler.postDelayed(this, frameRate)
                }
            }
            animRunnable = runnable
            animHandler.post(runnable)
        }

        fun cancelPriceAnimation() {
            animRunnable?.let { animHandler.removeCallbacks(it) }
            animRunnable = null
        }
    }
}

private fun currencyIsoCode(item: PriceItem): String {
    val key = item.cityKey ?: ""
    return when {
        key.startsWith("CRYPTO_") -> key.removePrefix("CRYPTO_")
        key.startsWith("METAL_") -> "USD"
        key.startsWith("FX_") -> key.removePrefix("FX_")
        key == "TRY_INVESTING" -> "TRY"
        key == "IRR_TELEGRAM" -> "IRR"
        key == "USD_OFFICIAL" || key == "USD_BAGHDAD" -> "USD"
        key in setOf("baghdad", "basra", "erbil", "kirkuk", "sulaymaniyah", "duhok", "mosul", "karbala", "najaf", "wasit", "muthanna", "salahaldin", "diyala", "dhiqar", "qadisiyyah", "babil", "maysan", "anbar", "halabja") -> "IQD"
        item.flagDrawable == "eu" -> "EUR"
        item.flagDrawable == "gb" -> "GBP"
        item.flagDrawable == "tr" -> "TRY"
        item.flagDrawable == "ir" -> "IRR"
        item.flagDrawable == "us" -> "USD"
        item.flagDrawable == "iq" -> "USD"
        else -> item.flagDrawable.uppercase(Locale.US)
    }
}

private fun resultCurrencyCode(item: PriceItem, context: android.content.Context): String {
    if (item.price.endsWith(" د.ع")) return context.getString(R.string.iqd_short)
    if (item.price.trimStart().startsWith("$")) return "USD"
    return currencyIsoCode(item)
}


/**
 * حالة أنيميشن الأرقام تعيش خارج الـAdapter: الصفحات تُعاد بناؤها كل ما وصل
 * جزء من التحديث (رقمية/معادن/أخبار)، فكان الـAdapter الجديد ينسى أن السعر تغيّر
 * ويعرض الرقم النهائي فوراً. هنا نتذكر آخر سعر معروض ووقت بدء حركة كل صف،
 * فإذا انبنى الصف من جديد أثناء الحركة يكمل من نفس الخطوة بدل ما يقفز.
 */
object PriceAnimState {
    const val DURATION_MS = 900L
    const val FRAME_MS = 75L
    private val lastShown = HashMap<String, String>()
    private val startedAt = HashMap<String, Long>()

    /** يرجع الخطوة اللي تبدأ منها الحركة (1..)، أو 0 لو ما أكو حركة لهذا الصف. */
    @Synchronized
    fun stepFor(key: String, price: String, now: Long): Int {
        val old = lastShown.put(key, price)
        if (old != null && old != price) startedAt[key] = now
        val start = startedAt[key] ?: return 0
        val elapsed = now - start
        if (elapsed >= DURATION_MS) { startedAt.remove(key); return 0 }
        return (elapsed / FRAME_MS).toInt() + 1
    }
}

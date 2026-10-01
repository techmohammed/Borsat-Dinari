package com.mohammed.currencyapp

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class PriceListAdapter(
    private var rows: List<ListRow>,
    private var favoriteKeys: List<String> = listOf("baghdad"),
    private val onStarClick: (String) -> Unit = {},
    private val onRowClick: (PriceItem) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
        private const val TYPE_FAVORITE = 2
    }

    // مفاتيح الصفوف اللي سعرها فعلياً تغيّر بآخر submitList — تُستخدم لتشغيل
    // أنيميشن "الأرقام المبعثرة تستقر" على صفوف تغيّرت بس، مو كل الصفوف
    // (تحديث يدوي بالسحب للأسفل، أو تحديث تلقائي كل 15 دقيقة وهو مفتوح).
    private var changedKeys: Set<String> = emptySet()

    fun submitList(newRows: List<ListRow>, newFavoriteKeys: List<String>) {
        val oldPriceByKey = rows.filterIsInstance<ListRow.Row>()
            .associate { (it.item.cityKey ?: it.item.name) to it.item.price }

        changedKeys = newRows.filterIsInstance<ListRow.Row>()
            .mapNotNull { row ->
                val key = row.item.cityKey ?: row.item.name
                val old = oldPriceByKey[key]
                if (old != null && old != row.item.price) key else null
            }
            .toSet()

        rows = newRows
        favoriteKeys = newFavoriteKeys
        notifyDataSetChanged()

        // نمسح علامة "تغيّر" بعد ما تخلص عملية إعادة الرسم الحالية، حتى لا
        // تتكرر الأنيميشن لاحقاً لمجرد إعادة تدوير/عرض نفس الصف بالسكرول
        // بدون أي بيانات جديدة فعلياً.
        Handler(Looper.getMainLooper()).post { changedKeys = emptySet() }
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ListRow.Header -> TYPE_HEADER
        is ListRow.Row -> {
            val item = (rows[position] as ListRow.Row).item
            // أول مفضلة فقط لها تصميم أكبر وأبرز؛ الثانية والثالثة صفوف عادية
            // (بس تجي فوق باقي الصفوف).
            if ((item.cityKey ?: item.name) == favoriteKeys.firstOrNull()) TYPE_FAVORITE else TYPE_ROW
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER ->
                HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false))
            TYPE_FAVORITE ->
                RowViewHolder(inflater.inflate(R.layout.item_price_favorite, parent, false), onStarClick, onRowClick)
            else ->
                RowViewHolder(inflater.inflate(R.layout.item_price, parent, false), onStarClick, onRowClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ListRow.Header -> (holder as HeaderViewHolder).bind(row.title)
            is ListRow.Row -> {
                val key = row.item.cityKey ?: row.item.name
                (holder as RowViewHolder).bind(row.item, favoriteKeys, animatePrice = changedKeys.contains(key))
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? RowViewHolder)?.cancelPriceAnimation()
    }

    override fun getItemCount(): Int = rows.size

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvHeader: android.widget.TextView = view.findViewById(R.id.tvHeader)
        fun bind(title: String) { tvHeader.text = title }
    }

    class RowViewHolder(
        view: View,
        private val onStarClick: (String) -> Unit,
        private val onRowClick: (PriceItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tvPrice: android.widget.TextView = view.findViewById(R.id.tvPrice)
        private val tvUnit: android.widget.TextView = view.findViewById(R.id.tvUnit)
        private val tvName: android.widget.TextView = view.findViewById(R.id.tvName)
        private val ivFlag: android.widget.ImageView = view.findViewById(R.id.ivFlag)
        private val deltaRow: View = view.findViewById(R.id.deltaRow)
        private val ivDeltaArrow: android.widget.ImageView = view.findViewById(R.id.ivDeltaArrow)
        private val tvDelta: android.widget.TextView = view.findViewById(R.id.tvDelta)
        private val btnStar: android.widget.ImageButton = view.findViewById(R.id.btnStar)

        private val animHandler = Handler(Looper.getMainLooper())
        private var animRunnable: Runnable? = null

        fun bind(item: PriceItem, favoriteKeys: List<String>, animatePrice: Boolean) {
            val context = itemView.context
            tvUnit.text = item.unit
            tvName.text = item.name

            // خلفية الصف كاملة: أخضر فاتح = صعود، أحمر فاتح = نزول.
            // FLAT رصاصي فاتح: السعر مستقر (ما تغيّر)، أو ما صار تحديث فعلي
            // من أي مصدر منذ فترة (انظر تعليق "3 ساعات" بمستودعات الأسعار).
            val bgColorRes = when (item.trend) {
                Trend.UP -> R.color.rowUpBg
                Trend.DOWN -> R.color.rowDownBg
                Trend.FLAT -> R.color.rowFlatBg
            }
            (itemView.background.mutate() as android.graphics.drawable.GradientDrawable)
                .setColor(ContextCompat.getColor(context, bgColorRes))

            // علم صغير بدائرة بجانب الاسم.
            val flagResId = context.resources.getIdentifier(
                "flag_${item.flagDrawable}", "drawable", context.packageName
            )
            if (flagResId != 0) ivFlag.setImageResource(flagResId)
            else ivFlag.setImageDrawable(null)

            // مقدار التغيير أسفل السعر: مثلث أخضر + "+500" أو مثلث أحمر + "-300".
            val key = item.cityKey ?: item.name
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
                        ivDeltaArrow.setImageResource(R.drawable.ic_tri_up)
                        tvDelta.text = "+$amount"
                        tvDelta.setTextColor(ContextCompat.getColor(context, R.color.deltaUp))
                    } else {
                        ivDeltaArrow.setImageResource(R.drawable.ic_tri_down)
                        tvDelta.text = "-$amount"
                        tvDelta.setTextColor(ContextCompat.getColor(context, R.color.deltaDown))
                    }
                }
            }

            // كل الصفوف أصبحت قابلة للمفضلة، بما فيها اليورو والجنيه والليرة والتومان والدولار.
            btnStar.visibility = View.VISIBLE
            btnStar.setImageResource(
                if (key in favoriteKeys) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            btnStar.setOnClickListener { onStarClick(key) }

            // الضغط على أي مكان ثاني بالصف (غير النجمة) يفتح هيستوري هذه البورصة.
            itemView.setOnClickListener { onRowClick(item) }

            cancelPriceAnimation()
            // السعر بالأسود وبدون "د.ع" (الوحدة تحت الاسم). نبقي item.price الأصلي
            // للمقارنة وللودجت والهيستوري، ونعرض بس النسخة المختصرة هنا.
            val displayPrice = item.price.removeSuffix(" د.ع")
            val plan = if (animatePrice) DigitRevealAnimator.plan(displayPrice) else null
            if (plan == null) {
                tvPrice.text = displayPrice
                return
            }

            // نفس فكرة أنيميشن الودجت بالضبط: أرقام عشوائية تستقر تدريجياً
            // على الرقم الصحيح خلال ~900ms، بدل تغيير الرقم فجأة.
            val duration = 900L
            val frameRate = 40L
            val totalSteps = (duration / frameRate).toInt().coerceAtLeast(1)
            var step = 0
            val runnable = object : Runnable {
                override fun run() {
                    step++
                    tvPrice.text = DigitRevealAnimator.frameText(displayPrice, plan, step, totalSteps)
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

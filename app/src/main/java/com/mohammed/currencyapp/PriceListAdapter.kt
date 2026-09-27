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
    private var favoriteKey: String = "baghdad",
    private val onStarClick: (String) -> Unit = {},
    private val onRowClick: (PriceItem) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }

    // مفاتيح الصفوف اللي سعرها فعلياً تغيّر بآخر submitList — تُستخدم لتشغيل
    // أنيميشن "الأرقام المبعثرة تستقر" على صفوف تغيّرت بس، مو كل الصفوف
    // (تحديث يدوي بالسحب للأسفل، أو تحديث تلقائي كل 15 دقيقة وهو مفتوح).
    private var changedKeys: Set<String> = emptySet()

    fun submitList(newRows: List<ListRow>, newFavoriteKey: String) {
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
        favoriteKey = newFavoriteKey
        notifyDataSetChanged()

        // نمسح علامة "تغيّر" بعد ما تخلص عملية إعادة الرسم الحالية، حتى لا
        // تتكرر الأنيميشن لاحقاً لمجرد إعادة تدوير/عرض نفس الصف بالسكرول
        // بدون أي بيانات جديدة فعلياً.
        Handler(Looper.getMainLooper()).post { changedKeys = emptySet() }
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is ListRow.Header -> TYPE_HEADER
        is ListRow.Row -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_section_header, parent, false))
        } else {
            RowViewHolder(inflater.inflate(R.layout.item_price, parent, false), onStarClick, onRowClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ListRow.Header -> (holder as HeaderViewHolder).bind(row.title)
            is ListRow.Row -> {
                val key = row.item.cityKey ?: row.item.name
                (holder as RowViewHolder).bind(row.item, favoriteKey, animatePrice = changedKeys.contains(key))
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
        private val ivFlagBg: android.widget.ImageView = view.findViewById(R.id.ivFlagBg)
        private val btnStar: android.widget.ImageButton = view.findViewById(R.id.btnStar)

        private val animHandler = Handler(Looper.getMainLooper())
        private var animRunnable: Runnable? = null

        fun bind(item: PriceItem, favoriteKey: String, animatePrice: Boolean) {
            val context = itemView.context
            tvUnit.text = item.unit
            tvName.text = item.name

            // صعود السعر = أخضر، نزول السعر = أحمر.
            // FLAT رصاصي: تعني السعر مستقر (ما تغيّر)، أو عدم توفر تحديث
            // فعلي من أي مصدر منذ فترة (انظر تعليق "3 ساعات" بمستودعات الأسعار).
            val colorRes = when (item.trend) {
                Trend.UP -> R.color.trendUp
                Trend.DOWN -> R.color.trendDown
                Trend.FLAT -> R.color.trendFlat
            }
            tvPrice.background.setTint(ContextCompat.getColor(context, colorRes))

            val bgResId = context.resources.getIdentifier(
                "row_bg_${item.flagDrawable}", "drawable", context.packageName
            )
            if (bgResId != 0) ivFlagBg.setImageResource(bgResId)
            else ivFlagBg.setImageDrawable(null)

            // كل الصفوف أصبحت قابلة للمفضلة، بما فيها اليورو والجنيه والليرة والتومان والدولار.
            val key = item.cityKey ?: item.name
            btnStar.visibility = View.VISIBLE
            btnStar.setImageResource(
                if (key == favoriteKey) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            btnStar.setOnClickListener { onStarClick(key) }

            // الضغط على أي مكان ثاني بالصف (غير النجمة) يفتح هيستوري هذه البورصة.
            itemView.setOnClickListener { onRowClick(item) }

            cancelPriceAnimation()
            val plan = if (animatePrice) DigitRevealAnimator.plan(item.price) else null
            if (plan == null) {
                tvPrice.text = item.price
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
                    tvPrice.text = DigitRevealAnimator.frameText(item.price, plan, step, totalSteps)
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

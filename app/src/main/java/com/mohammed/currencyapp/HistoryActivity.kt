package com.mohammed.currencyapp

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.mohammed.currencyapp.databinding.ActivityHistoryBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * صفحة هيستوري خاصة ببورصة/عملة وحدة. تفتح افتراضياً على سجل اليوم
 * الحالي، وتسمح باختيار فترة (من - إلى) عبر تقويم أندرويد القياسي
 * (MaterialDatePicker بوضع range)، محدود بآخر 60 يوم لأن هذا أقصى مدة
 * نحتفظ فيها بالهيستوري أصلاً (انظر HistoryStore).
 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_KEY = "extra_key"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_FLAG = "extra_flag"
        private const val RETENTION_DAYS = 60L
    }

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var itemKey: String
    private lateinit var itemName: String
    private val listAdapter = HistoryAdapter()
    private var currentFrom: Long = 0L
    private var currentTo: Long = 0L

    /** نفرض Locale.US كامل (مو "ar" بامتداد nu-latn زي المحاولة الأولى)
     * لصفحة الهيستوري كلها — وبالأخص تقويم اختيار الفترة
     * (MaterialDatePicker) — بغض النظر عن لغة الجهاز. جربنا أول لوكيل
     * "ar-u-nu-latn" (عربي بأرقام لاتينية) حتى يضل اسم الشهر عربي، بس
     * طلع فيه خلل معروف بمكتبة Material (اتجاه أسهم التنقل بين الأشهر
     * يتحدد من لغة الـLocale نفسها "ar" = RTL بغض النظر عن
     * layoutDirection المفروض) — راجع القضية #1716 بمستودع
     * material-components-android. الحل الوحيد الموثوق: لوكيل مو RTL
     * إطلاقاً بلغته (Locale.US)، فتضل الأسهم صحيحة مهما كانت لغة
     * الجهاز. نص التطبيق نفسه ثابت عربي بالكود (مو من موارد locale)
     * فما يتأثر، بس اسم الشهر وأيام الأسبوع بالتقويم نفسه بيصيروا
     * إنكليزي كنتيجة جانبية (مقبولة مقارنة بأسهم مقلوبة). */
    override fun attachBaseContext(newBase: Context) {
        val fixedLocale = Locale.US
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(fixedLocale)
        config.setLayoutDirection(fixedLocale)
        Locale.setDefault(fixedLocale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        itemKey = intent.getStringExtra(EXTRA_KEY) ?: run { finish(); return }
        itemName = intent.getStringExtra(EXTRA_NAME) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        binding.tvItemName.text = itemName

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = listAdapter

        // نطاق اليوم الحالي افتراضياً (00:00 لين 23:59 بتوقيت الجهاز).
        val now = System.currentTimeMillis()
        goToDay(now, autoSync = true)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPickRange.setOnClickListener { openRangePicker() }
        binding.btnSyncChannel.setOnClickListener { onSyncChannelClicked() }
        binding.btnDeleteRange.setOnClickListener { onDeleteRangeClicked() }
        binding.btnPrevDay.setOnClickListener {
            goToDay(currentFrom - 24 * 60 * 60 * 1000, autoSync = true)
        }
        binding.btnNextDay.setOnClickListener {
            goToDay(currentFrom + 24 * 60 * 60 * 1000, autoSync = true)
        }
    }

    private var syncCancelled = false
    private var syncRunning = false

    /** رسالة تنبيه مخصصة (بديل AlertDialog الافتراضي، اللي كانت أزراره
     * تختفي عملياً — لون التطبيق colorPrimary أبيض عمداً، فنص الأزرار
     * كان يطلع أبيض على خلفية بيضاء). تصميمها ثابت (عنوان + أيقونة،
     * فاصل، رسالة، فاصل، زرين) وتتلون حسب نوع الإجراء عبر [accentColor]
     * و[iconRes]. */
    private fun showConfirmDialog(
        title: String,
        message: String,
        iconRes: Int,
        accentColor: Int,
        confirmText: String,
        onConfirm: () -> Unit,
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val dialog = android.app.Dialog(this)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<android.widget.ImageView>(R.id.ivDialogIcon).setImageResource(iconRes)
        dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle).text = title
        dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage).text = message

        val confirmView = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogConfirm)
        confirmView.text = confirmText
        confirmView.background.setTint(accentColor)
        confirmView.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialogView.findViewById<android.widget.TextView>(R.id.tvDialogCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    /** زر التنزيل: يلف للخلف بصفحات قناة بورصة ديناري لين يغطي الفترة
     * المحددة حالياً بالتقويم (currentFrom..currentTo)، ويعبي أي فجوة
     * ناقصة بالسجل المحلي لهذي البورصة بس ضمن نفس الفترة (بدون تكرار
     * الموجود، وبدون أي منبه دوري بالخلفية — يشتغل بس لما المستخدم يضغط
     * الزر ويأكد من رسالة التنبيه). ضغطة ثانية أثناء الجلب تلغيه (بدون
     * رسالة تأكيد، لأنها إلغاء مو بدء عملية)، وأي نقطة توصل تنسجل أول
     * بأول قبل الإلغاء. */
    private fun onSyncChannelClicked() {
        if (syncRunning) {
            syncCancelled = true
            return
        }
        val message = "هل انت متأكد بالقيام بتحديث السجلات للفترة المحددة من " +
            "${formatDateOnly(currentFrom)} الى ${formatDateOnly(currentTo)}؟"
        showConfirmDialog(
            title = "تحديث",
            message = message,
            iconRes = R.drawable.ic_channel_sync,
            accentColor = "#00897B".toColorInt(),
            confirmText = "تحديث"
        ) { startSync() }
    }

    /** يتأكد اكو اتصال إنترنت فعلي قبل أي عملية تمسح بيانات محلية —
     * بدونها كان startSync يمسح سجل اليوم/الفترة المحددة أولاً (نفس
     * فكرة "امسح ثم حدّث") حتى لو ماكو نت، فتضل الصفحة فارغة لحد ما
     * يرجع النت، وتضيع بيانات كانت موجودة بلا داعي. */
    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    /** يبدأ مزامنة القناة للفترة المحددة حالياً. نمسح أولاً كل سجلات
     * هذي البورصة بنفس الفترة (currentFrom..currentTo) قبل الجلب —
     * بدونها، كل ضغطة على زر الجلب كانت تضيف نفس النقاط فوق الموجودة
     * أصلاً (HistoryStore.backfill يدرج نقاط جديدة، ما يستبدل)، فيصير
     * تكرار بالقائمة والمخطط كل ما تعيد تحميل نفس الفترة.
     *
     * قبل أي مسح، نتأكد اكو نت فعلاً — إذا ماكو، نوقف فوراً بدون ما
     * نلمس البيانات المحفوظة إطلاقاً (ولا مسح ولا محاولة جلب)، فتضل
     * القائمة والمخطط زي ما هم. هذا يشمل كل نقاط الدخول اللي تستدعي
     * startSync(): زر التحديث اليدوي، زري التنقل بين الأيام، اختيار
     * فترة من التقويم، والتحديث التلقائي عند فتح الصفحة أول مرة —
     * كلهم يمرون من هنا. */
    private fun startSync() {
        if (!isNetworkAvailable()) return
        syncRunning = true
        syncCancelled = false
        // نغيّر شفافية نفس الزر بدل ما نضيف View تحميل منفصل بالتولبار —
        // إضافة/إخفاء عنصر ثاني بالتولبار كانت تسبب إعادة قياس تخلي عنوان
        // البورصة (تدهوك) يختفي مؤقتاً لين يخلص التحميل.
        binding.btnSyncChannel.isEnabled = true // نخليه فعّال حتى تكدر تلغي بالضغط عليه ثانية
        binding.btnSyncChannel.alpha = 0.35f

        val since = currentFrom
        val until = currentTo
        HistoryStore.deleteRange(this, itemKey, since, until)
        TelegramScraperRepository.fetchHistoricalPrices(
            cityKey = itemKey,
            sinceMillis = since,
            untilMillis = until,
            onProgress = { _, _ -> },
            isCancelled = { syncCancelled }
        ) { points ->
            syncRunning = false
            binding.btnSyncChannel.alpha = 1f
            if (points.isNotEmpty()) HistoryStore.backfill(this, itemKey, points)
            loadHistory(currentFrom, currentTo)
        }
    }

    override fun onStop() {
        super.onStop()
        // نلغي أي جلب شغال إذا المستخدم غادر الصفحة، حتى ما يضل يشتغل
        // بالخلفية بلا داعي وهو مو شايفها.
        syncCancelled = true
        binding.btnSyncChannel.alpha = 1f
    }

    /** زر الحذف بالترويسة: يمسح كل سجلات هذي البورصة ضمن الفترة المحددة
     * حالياً بالتقويم (currentFrom..currentTo)، بعد رسالة تنبيه تأكيد
     * توضح الفترة بالضبط، ثم يعيد تحميل نفس الفترة حتى تتحدث القائمة
     * والمخطط سوا فوراً. */
    private fun onDeleteRangeClicked() {
        val message = "هل انت متأكد بالقيام بحذف السجلات للفترة المحددة من " +
            "${formatDateOnly(currentFrom)} الى ${formatDateOnly(currentTo)}؟"
        showConfirmDialog(
            title = "حذف",
            message = message,
            iconRes = R.drawable.ic_delete,
            accentColor = "#C0392B".toColorInt(),
            confirmText = "حذف"
        ) {
            HistoryStore.deleteRange(this, itemKey, currentFrom, currentTo)
            loadHistory(currentFrom, currentTo)
        }
    }

    /** يفتح صفحة الهيستوري على يوم واحد كامل (00:00 لين 23:59) يحتوي
     * [anyMillisInDay]، ويحدّث رقم اليوم بترويسة الصفحة. يُستخدم بزري
     * السهم (اليوم السابق/التالي) وبالفتح الأولي على يوم اليوم — وباختيار
     * [autoSync]=true (كل الحالات الحالية) يمسح سجلات هذا اليوم أولاً
     * ثم يجيبها من جديد من القناة، نفس فكرة زر التحديث بالضبط. */
    private fun goToDay(anyMillisInDay: Long, autoSync: Boolean) {
        loadHistory(startOfDay(anyMillisInDay), endOfDay(anyMillisInDay))
        if (autoSync) startSync()
    }

    private fun openRangePicker() {
        val now = System.currentTimeMillis()
        val earliest = now - (RETENTION_DAYS * 24 * 60 * 60 * 1000)

        // نحدد بداية ونهاية التقويم نفسه (Builder.setStart/setEnd) حتى ما
        // يعرض شهور بلا داعي قبل أول يوم عندنا فيه بيانات أو بعد اليوم
        // الحالي — مو بس نحدد الأيام القابلة للاختيار (DateValidator).
        val constraints = CalendarConstraints.Builder()
            .setStart(earliest)
            .setEnd(now)
            .setOpenAt(now)
            .setValidator(
                CompositeDateValidator(
                    DateValidatorPointBackward.now(),
                    DateValidatorPointForward.from(earliest)
                )
            )
            .build()

        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("اختيار الفترة")
            .setTheme(R.style.ThemeOverlay_App_DatePicker)
            .setCalendarConstraints(constraints)
            .setSelection(
                androidx.core.util.Pair(startOfDay(now), endOfDay(now))
            )
            .setPositiveButtonText("اختيار")
            .setNegativeButtonText("إلغاء")
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val from = startOfDay(selection.first ?: now)
            val to = endOfDay(selection.second ?: now)
            loadHistory(from, to)
            // نفس فكرة التحديث التلقائي عند فتح الصفحة: أي فترة يختارها
            // المستخدم يدوياً من التقويم تنعامل نفس معاملة زر "تحديث" —
            // تمسح الفترة المحددة أولاً ثم تجيب بياناتها من جديد من
            // القناة (startSync يعتمد على currentFrom/currentTo اللي
            // للتو ضبطناها بـloadHistory فوق).
            startSync()
        }

        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun loadHistory(fromMillis: Long, toMillis: Long) {
        currentFrom = fromMillis
        currentTo = toMillis
        binding.tvDayNumber.text = SimpleDateFormat("d", Locale.US).format(fromMillis)
        // زر اليوم التالي يوقف لما نكون على يوم اليوم الحالي أصلاً — ما
        // اكو بيانات ليوم "غد" لأنه لسا ما صار، فما فايدة نخلي المستخدم
        // يضغط عليه بلا نتيجة.
        val isAtToday = startOfDay(fromMillis) >= startOfDay(System.currentTimeMillis())
        binding.btnNextDay.isEnabled = !isAtToday
        binding.btnNextDay.alpha = if (isAtToday) 0.3f else 1f
        val entries = HistoryStore.query(this, itemKey, fromMillis, toMillis)
        listAdapter.submit(entries)
        binding.historyChart.setEntries(entries)
        val isEmpty = entries.isEmpty()
        binding.tvEmpty.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        binding.recyclerHistory.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
        binding.chartContainer.visibility = if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
    }

    /** تاريخ باليوم/الشهر/السنة بس (بدون وقت)، تُستخدم برسائل تأكيد الحذف
     * والتنزيل حتى توضح الفترة المحددة بالتقويم بشكل مختصر ومفهوم. */
    private fun formatDateOnly(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.US).format(millis)

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun endOfDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    /** يجمع شرطين تحقق بتقويم أندرويد (Parcelable مطلوب لأنه يُمرر مع الحوار). */
    private class CompositeDateValidator(
        private val a: CalendarConstraints.DateValidator,
        private val b: CalendarConstraints.DateValidator
    ) : CalendarConstraints.DateValidator {
        override fun isValid(date: Long): Boolean = a.isValid(date) && b.isValid(date)
        override fun describeContents(): Int = 0
        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(a, flags)
            dest.writeParcelable(b, flags)
        }

        companion object CREATOR : Parcelable.Creator<CompositeDateValidator> {
            override fun createFromParcel(source: Parcel): CompositeDateValidator {
                val loader = CalendarConstraints.DateValidator::class.java.classLoader
                val a = readValidator(source, loader)
                val b = readValidator(source, loader)
                return CompositeDateValidator(a, b)
            }
            override fun newArray(size: Int): Array<CompositeDateValidator?> = arrayOfNulls(size)

            // readParcelable(ClassLoader?) صارت deprecated بـ API 33 لصالح
            // النسخة اللي تاخذ Class<T> صراحةً؛ نستخدم النسخة الجديدة إذا
            // متوفرة وإلا نرجع للقديمة بأجهزة أقدم (minSdk 21).
            @Suppress("DEPRECATION")
            private fun readValidator(
                source: Parcel,
                loader: ClassLoader?
            ): CalendarConstraints.DateValidator {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    source.readParcelable(loader, CalendarConstraints.DateValidator::class.java)!!
                } else {
                    source.readParcelable(loader)!!
                }
            }
        }
    }

    private class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {
        private var entries: List<HistoryEntry> = emptyList()
        private val timeFormat = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.US)

        fun submit(newEntries: List<HistoryEntry>) {
            entries = newEntries
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            holder.time.text = timeFormat.format(entry.timestampMillis)
            holder.price.text = entry.price
            val colorRes = when (entry.trend) {
                Trend.UP -> R.color.trendUp
                Trend.DOWN -> R.color.trendDown
                Trend.FLAT -> R.color.trendFlat
            }
            holder.price.background.setTint(ContextCompat.getColor(holder.itemView.context, colorRes))
        }

        override fun getItemCount(): Int = entries.size

        class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val time: android.widget.TextView = view.findViewById(R.id.tvHistoryTime)
            val price: android.widget.TextView = view.findViewById(R.id.tvHistoryPrice)
        }
    }
}

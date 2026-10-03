package com.mohammed.currencyapp

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.core.widget.TextViewCompat
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
import kotlin.math.abs

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
        applySystemBarsInsets(binding.toolbar, binding.recyclerHistory)

        itemKey = intent.getStringExtra(EXTRA_KEY) ?: run { finish(); return }
        itemName = intent.getStringExtra(EXTRA_NAME) ?: ""

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = ""
        binding.tvItemName.text = itemName

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = listAdapter

        tabViews = listOf(binding.tabDay, binding.tab7, binding.tab30, binding.tab60)
        tabViews.forEachIndexed { index, tab -> tab.setOnClickListener { showTab(index) } }
        showTab(0) // اليوم الحالي افتراضياً

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnPickRange.setOnClickListener { openRangePicker() }
    }

    // أزرار الفترة: يوم / أسبوع / شهر / شهرين (الأيام تنتهي باليوم الحالي).
    private lateinit var tabViews: List<android.widget.TextView>
    private val tabDays = intArrayOf(1, 7, 30, 60)

    // الفترة المختارة حالياً (0=يوم، 1=أسبوع، 2=شهر، 3=شهرين)، أو -1 لو فترة مخصصة من التقويم.
    private var selectedTab = 0

    // اتجاه السحب بين الفترات. الترتيب بالشاشة من اليمين: يوم ← أسبوع ← شهر ← شهرين،
    // فالسحب لليمين (الإصبع يتحرك من اليسار لليمين) ينقل للفترة اللي على يسار الحالية
    // (الأطول)، والسحب لليسار يرجع للأقصر — نفس سلوك الصفحات بالواجهات العربية.
    // غيّر هذا الخيار لـ false لو تريد العكس.
    private val swipeRightGoesLonger = true

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

    private val syncListener = object : HistorySync.Listener {
        override fun onDataChanged() {
            loadHistory(currentFrom, currentTo)
        }

        override fun onFinished() {
            binding.syncProgress.visibility = View.INVISIBLE
            loadHistory(currentFrom, currentTo)
        }
    }

    /** يطلب من HistorySync يغطي الفترة المعروضة. المزامنة تراكمية: بعد أول مرة
     * السجل المحلي يكفي ويظهر فوراً، وما نجلب من القناة إلا المنشورات الجديدة أو
     * الفترة الأقدم اللي ما غطيناها قبل. شريط التقدم الرفيع يظهر بس لو في جلب فعلي. */
    private fun startSync() {
        if (HistorySync.isUpToDate(this, currentFrom)) return
        if (!isNetworkAvailable()) return
        binding.syncProgress.visibility = View.VISIBLE
        HistorySync.request(applicationContext, currentFrom, syncListener)
    }

    override fun onStart() {
        super.onStart()
        // لو المزامنة لسا شغالة (رجعنا للصفحة) نكمل نعرض تقدمها.
        if (HistorySync.attach(syncListener)) binding.syncProgress.visibility = View.VISIBLE
    }

    override fun onStop() {
        super.onStop()
        HistorySync.cancel()
        binding.syncProgress.visibility = View.INVISIBLE
    }

    // ───────────── السحب يمين/يسار للتنقل بين الفترات ─────────────
    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null || selectedTab < 0) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val minDistance = resources.displayMetrics.density * 70f
                if (abs(dx) < minDistance || abs(dx) < abs(dy) * 1.6f || abs(velocityX) < 500f) return false
                val goLonger = (dx > 0) == swipeRightGoesLonger
                val target = if (goLonger) selectedTab + 1 else selectedTab - 1
                if (target in tabDays.indices) {
                    showTab(target)
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** حركة انزلاق قصيرة للمخطط والقائمة عند تبديل الفترة. */
    private fun slideContent(enterFromRight: Boolean) {
        val distance = resources.displayMetrics.density * 56f * (if (enterFromRight) 1f else -1f)
        for (v in listOf<View>(binding.chartContainer, binding.recyclerHistory)) {
            v.animate().cancel()
            v.translationX = distance
            v.alpha = 0.25f
            v.animate().translationX(0f).alpha(1f).setDuration(200L).start()
        }
    }

    /** يلوّن زر الفترة المختار (أزرق + نص أبيض)؛ index = -1 يعني فترة مخصصة من التقويم. */
    private fun selectTab(index: Int) {
        tabViews.forEachIndexed { i, tab ->
            val selected = i == index
            tab.setBackgroundResource(if (selected) R.drawable.bg_tab_selected else 0)
            tab.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.white else R.color.textPrimary)
            )
        }
    }

    /** يعرض آخر N يوم (يوم = اليوم الحالي فقط، أسبوع = اليوم وآخر 6 قبله، وهكذا). */
    private fun showTab(index: Int) {
        val previous = selectedTab
        selectedTab = index
        selectTab(index)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -(tabDays[index] - 1))
        }
        loadHistory(startOfDay(cal.timeInMillis), endOfDay(now))
        startSync()
        // الفترة الأطول موقعها يسار الحالية فيدخل المحتوى من اليسار، والأقصر من اليمين.
        if (previous != index && previous >= 0) slideContent(enterFromRight = index < previous)
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
            selectedTab = -1
            selectTab(-1) // فترة مخصصة: ما يتلوّن أي زر فترة
            loadHistory(from, to)
            // أي فترة يختارها المستخدم من التقويم نجلب بياناتها من القناة
            // (startSync يعتمد على currentFrom/currentTo اللي للتو ضبطناها).
            startSync()
        }

        // نكبّر عنوان "اختيار الفترة" بالهيدر بالكود (حجم الستايل وحده ما كان يظهر).
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                // Material يطبّق "الحجم التلقائي" على عنوان الهيدر فيصغّره، ولذلك الحجم
                // بالستايل ما يبين. نوقف الحجم التلقائي ونفرض الحجم بعد ما ينعرض
                // الهيدر (وبعد أول رسم أيضاً حتى ما يرجعه Material).
                private fun enlarge(root: View?) {
                    val title = root?.findViewById<android.widget.TextView>(
                        com.google.android.material.R.id.mtrl_picker_title_text
                    ) ?: return
                    TextViewCompat.setAutoSizeTextTypeWithDefaults(
                        title, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
                    )
                    title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    title.maxLines = 2
                }

                override fun onFragmentViewCreated(
                    fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?
                ) {
                    if (f !== picker) return
                    enlarge(v)
                }

                override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                    if (f !== picker) return
                    enlarge(f.view)
                    f.view?.post { enlarge(f.view) }
                }

                override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                    if (f === picker) fm.unregisterFragmentLifecycleCallbacks(this)
                }
            },
            false
        )
        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun loadHistory(fromMillis: Long, toMillis: Long) {
        currentFrom = fromMillis
        currentTo = toMillis
        val entries = HistoryStore.query(this, itemKey, fromMillis, toMillis)
        listAdapter.submit(entries)
        binding.historyChart.setEntries(entries, fromMillis, toMillis)
        val isEmpty = entries.isEmpty()
        binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.chartContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

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
        private val dateFormat = SimpleDateFormat("d/M", Locale.US)
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

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
            holder.date.text = dateFormat.format(entry.timestampMillis)
            holder.time.text = timeFormat.format(entry.timestampMillis)
            holder.price.text = entry.price.removeSuffix(" د.ع")
            holder.trend.setImageResource(
                when (entry.trend) {
                    Trend.UP -> R.drawable.ic_hist_up
                    Trend.DOWN -> R.drawable.ic_hist_down
                    Trend.FLAT -> R.drawable.ic_hist_flat
                }
            )
        }

        override fun getItemCount(): Int = entries.size

        class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val trend: android.widget.ImageView = view.findViewById(R.id.ivHistoryTrend)
            val price: android.widget.TextView = view.findViewById(R.id.tvHistoryPrice)
            val date: android.widget.TextView = view.findViewById(R.id.tvHistoryDate)
            val time: android.widget.TextView = view.findViewById(R.id.tvHistoryTime)
        }
    }
}

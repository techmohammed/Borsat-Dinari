package com.mohammed.currencyapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mohammed.currencyapp.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHORTCUT_CITY_KEY = "shortcut_city_key"
        const val EXTRA_SHORTCUT_CITY_NAME = "shortcut_city_name"
        const val EXTRA_SHORTCUT_CITY_FLAG = "shortcut_city_flag"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PriceListAdapter
    private val lastUpdateTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

    private var latestExchanges: List<PriceItem> = emptyList()
    private var latestCurrencies: List<PriceItem> = emptyList()

    // تحديث الأسعار كل 15 دقيقة طالما التطبيق مفتوح بالمقدمة
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 15 * 60 * 1000L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadExchanges()
            loadCurrencies()
            refreshHandler.postDelayed(this, refreshIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarsInsets(binding.toolbar, binding.recyclerView)

        adapter = PriceListAdapter(
            rows = emptyList(),
            favoriteKeys = FavoriteCityStore.getAll(this),
            onStarClick = { cityKey ->
                FavoriteCityStore.toggle(this, cityKey)
                rebuildRows()
                // نحدث الودجت فوراً بالمفضلات الجديدة: عرض من الكاش فوراً، وبعدين
                // طلب سعر حقيقي لها بالخلفية.
                FavoriteWidgetProvider.renderFromCache(this)
                FavoriteWidgetProvider.refreshFavorite(this)
            },
            onRowClick = { item ->
                val intent = Intent(this, HistoryActivity::class.java).apply {
                    putExtra(HistoryActivity.EXTRA_KEY, item.cityKey ?: item.name)
                    putExtra(HistoryActivity.EXTRA_NAME, item.name)
                    putExtra(HistoryActivity.EXTRA_FLAG, item.flagDrawable)
                }
                startActivity(intent)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            loadExchanges()
            loadCurrencies()
        }

        binding.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // أيقونة القمر تعني "أنت بالنمط النهاري الآن، اضغط للتحويل لليلي"،
        // والعكس بالعكس — الأيقونة تمثل الوضع اللي رح تنتقل له لو ضغطت.
        binding.btnThemeToggle.setImageResource(
            if (ThemePreferences.isNightMode(this)) R.drawable.ic_theme_sun else R.drawable.ic_theme_moon
        )
        binding.btnThemeToggle.setOnClickListener {
            ThemePreferences.toggle(this)
            // setDefaultNightMode يعيد إنشاء الـ Activity تلقائياً (DayNight
            // theme)، فما نحتاج نحدث الأيقونة يدوياً هنا.
        }

        binding.btnRefreshNow.setOnClickListener {
            loadExchanges()
            loadCurrencies()
        }

        updateLastUpdateLabel(LastUpdateStore.load(this))

        // فتحنا من اختصار الشاشة الرئيسية (ضغطة مطولة على أيقونة التطبيق)؟
        // نفتح هيستوري المدينة المطلوبة مباشرة فوق الشاشة الرئيسية.
        intent?.getStringExtra(EXTRA_SHORTCUT_CITY_KEY)?.let { key ->
            val name = intent.getStringExtra(EXTRA_SHORTCUT_CITY_NAME) ?: ""
            val flag = intent.getStringExtra(EXTRA_SHORTCUT_CITY_FLAG) ?: ""
            startActivity(Intent(this, HistoryActivity::class.java).apply {
                putExtra(HistoryActivity.EXTRA_KEY, key)
                putExtra(HistoryActivity.EXTRA_NAME, name)
                putExtra(HistoryActivity.EXTRA_FLAG, flag)
            })
        }

        loadExchanges()
        loadCurrencies()
    }

    override fun onStart() {
        super.onStart()
        refreshHandler.postDelayed(refreshRunnable, refreshIntervalMs)
    }

    override fun onStop() {
        super.onStop()
        refreshHandler.removeCallbacks(refreshRunnable)
    }


    private fun loadExchanges() {
        binding.swipeRefresh.isRefreshing = true
        TelegramScraperRepository.fetch(this) { items ->
            latestExchanges = FavoriteCityStore.sortWithFavoriteFirst(this, items)
            binding.swipeRefresh.isRefreshing = false
            rebuildRows()
        }
    }

    private fun loadCurrencies() {
        CurrenciesRepository.fetch(this) { currencies ->
            latestCurrencies = currencies
            rebuildRows()
        }
    }

    private fun updateLastUpdateLabel(millis: Long?) {
        // شريط "آخر تحديث: 16:45" أعلى القائمة (وقت 24 ساعة بدون تاريخ).
        binding.tvLastUpdate.text = if (millis != null) {
            "آخر تحديث: ${lastUpdateTimeFormat.format(millis)}"
        } else "آخر تحديث: —"
    }

    private fun rebuildRows() {
        val allItems = mutableListOf<PriceItem>()
        allItems.addAll(latestExchanges)
        allItems.addAll(latestCurrencies)

        val favorites = FavoriteCityStore.getAll(this)
        val ordered = allItems.sortedBy { item ->
            val i = favorites.indexOf(item.cityKey ?: item.name)
            if (i < 0) Int.MAX_VALUE else i
        }
        adapter.submitList(ordered.map { ListRow.Row(it) }, favorites)

        // نسجل توقيت آخر تحديث فعلي للقائمة (يشمل التحديث اليدوي بالسحب أو
        // بزر الأيقونة، والتحديث التلقائي كل 15 دقيقة).
        val now = System.currentTimeMillis()
        LastUpdateStore.save(this, now)
        updateLastUpdateLabel(now)

        // التطبيق أصلاً يحدّث كل 15 دقيقة وهو مفتوح، فنفس الفرصة نحدث الودجت
        // من نفس الكاش (بدون أي طلب شبكة إضافي).
        FavoriteWidgetProvider.renderFromCache(this)
    }
}

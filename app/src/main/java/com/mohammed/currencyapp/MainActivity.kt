package com.mohammed.currencyapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.view.GestureDetector
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import kotlin.math.abs
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mohammed.currencyapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHORTCUT_CITY_KEY = "shortcut_city_key"
        const val EXTRA_SHORTCUT_CITY_NAME = "shortcut_city_name"
        const val EXTRA_SHORTCUT_CITY_FLAG = "shortcut_city_flag"
        private const val ANNOUNCEMENT_PREFS = "announcement_banner_prefs"
        private const val ANNOUNCEMENT_POST_ID = "announcement_post_id"
        private const val ANNOUNCEMENT_FIRST_SEEN = "announcement_first_seen"
        private const val ANNOUNCEMENT_VISIBLE_MS = 24 * 60 * 60 * 1000L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PriceListAdapter

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
        KurdishFont.apply(binding.root, this)
        applySystemBarsInsets(binding.toolbar, binding.recyclerView)

        adapter = PriceListAdapter(
            rows = emptyList(),
            favoriteKeys = FavoriteCityStore.getAll(this),
            onStarClick = { cityKey ->
                FavoriteCityStore.toggle(this, cityKey)
                FavoriteShortcuts.update(this)
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
            // تحديث يدوي: نتجاوز الكاش المشترك حتى يجيب بيانات حية فعلاً.
            TelegramScraperRepository.invalidateSharedMessages()
            loadExchanges()
            loadCurrencies()
        }

        binding.btnAbout.setOnClickListener { openAbout() }

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

        // زر الكرة الأرضية: ينزل مستطيل اختيار اللغة (4 لغات بأعلامها).
        binding.btnLanguage.setOnClickListener { showLanguagePopup(it) }
        binding.btnCalculator.setOnClickListener {
            startActivity(Intent(this, CalculatorActivity::class.java))
        }

        binding.btnRefreshNow.setOnClickListener {
            // تحديث يدوي: نتجاوز الكاش المشترك حتى يجيب بيانات حية فعلاً.
            TelegramScraperRepository.invalidateSharedMessages()
            loadExchanges()
            loadCurrencies()
        }

        updateLastUpdateLabel(LastUpdateStore.load(this))

        // اختصارات أيقونة التطبيق (ضغطة مطولة): المفضلات الثلاث، كل وحدة بعلمها.
        FavoriteShortcuts.update(this)

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

    /** يفتح "حول التطبيق" بانزلاق من اليسار (مثل سحب القائمة البرگر). */
    private fun openAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.drawer_open_enter, R.anim.drawer_open_exit)
        }
        // أندرويد 14+: الانزلاق يتحدد داخل AboutActivity (overrideActivityTransition).
    }

    // سحب وسط الشاشة لليمين = حول التطبيق، ولليسار = الحاسبة.
    private val drawerSwipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val minDistance = resources.displayMetrics.density * 80f
                if (abs(dx) > minDistance && abs(dx) > abs(dy) * 1.8f && abs(velocityX) > 600f) {
                    if (dx < 0) {
                        startActivity(Intent(this@MainActivity, CalculatorActivity::class.java))
                        return true
                    }
                    openAbout()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        drawerSwipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
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
        loadLatestAnnouncement()
        TelegramScraperRepository.fetch(this) { items ->
            latestExchanges = FavoriteCityStore.sortWithFavoriteFirst(this, items)
            binding.swipeRefresh.isRefreshing = false
            rebuildRows()
        }
    }

    /** يعرض أحدث منشور يبدأ بـ"تنويه" داخل شريط متحرك تحت الهيدر. */
    private fun loadLatestAnnouncement() {
        // التنويه مخصص للواجهة العربية فقط؛ لا نرسل طلباً للقناة أصلاً
        // عند اختيار الإنجليزية أو التركية أو الكوردية.
        if (AppLanguage.currentTag() != "ar") {
            binding.announcementBar.visibility = View.GONE
            return
        }
        TelegramScraperRepository.fetchLatestAnnouncement { announcement ->
            if (announcement == null) {
                binding.announcementBar.visibility = View.GONE
                return@fetchLatestAnnouncement
            }

            val prefs = getSharedPreferences(ANNOUNCEMENT_PREFS, MODE_PRIVATE)
            val savedPostId = prefs.getLong(ANNOUNCEMENT_POST_ID, -1L)
            val firstSeen = prefs.getLong(ANNOUNCEMENT_FIRST_SEEN, 0L)
            val now = System.currentTimeMillis()
            if (savedPostId == announcement.postId && now - firstSeen >= ANNOUNCEMENT_VISIBLE_MS) {
                binding.announcementBar.visibility = View.GONE
                return@fetchLatestAnnouncement
            }
            if (savedPostId != announcement.postId) {
                prefs.edit()
                    .putLong(ANNOUNCEMENT_POST_ID, announcement.postId)
                    .putLong(ANNOUNCEMENT_FIRST_SEEN, now)
                    .apply()
            }

            binding.tvAnnouncementText.text = announcement.text
            binding.tvAnnouncementText.isSelected = true
            speedUpAnnouncementMarquee()
            binding.announcementBar.visibility = View.VISIBLE
            KurdishFont.apply(binding.announcementBar, this)

            // الضغط على النص لا يفتح المنشور؛ الشريط مخصص للعرض فقط.
            binding.tvAnnouncementText.setOnClickListener(null)
            binding.btnCloseAnnouncement.setOnClickListener {
                binding.announcementBar.visibility = View.GONE
            }
        }
    }

    /** يزيد سرعة الـmarquee الافتراضية قليلاً مع إبقاء الحركة مستمرة. */
    private fun speedUpAnnouncementMarquee() {
        binding.tvAnnouncementText.postDelayed({
            runCatching {
                val marqueeField = TextView::class.java.getDeclaredField("mMarquee")
                marqueeField.isAccessible = true
                val marquee = marqueeField.get(binding.tvAnnouncementText) ?: return@runCatching
                val speedField = marquee.javaClass.getDeclaredField("mPixelsPerSecond")
                speedField.isAccessible = true
                speedField.setFloat(marquee, 72f)
            }
        }, 100L)
    }

    private fun loadCurrencies() {
        CurrenciesRepository.fetch(this) { currencies ->
            latestCurrencies = currencies
            rebuildRows()
        }
    }

    private fun updateLastUpdateLabel(millis: Long?) {
        binding.tvLastUpdate.text = if (millis != null) {
            getString(R.string.last_update_prefix, UpdateTimeFormat.timeAndDate(this, millis))
        } else getString(R.string.last_update_none)
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
        // نسجّل التحديث عند منبه الودجت حتى لو جاء بعد دقايق يتخطى طلب
        // الشبكة المكرر (الكاش المشترك نفسه صار محدث).
        WidgetRefreshScheduler.markRefreshed(this)
    }

    /** مستطيل اللغات: ينزل تحت زر الكرة الأرضية، والضغط على لغة يطبقها
     *  ويخزنها (AppCompat يتكفل بالحفظ وإعادة إنشاء الشاشات تلقائياً). */
    private fun showLanguagePopup(anchor: View) {
        val content = layoutInflater.inflate(R.layout.dialog_language, null)
        KurdishFont.apply(content, this)
        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = 16f
        fun select(viewId: Int, tag: String) {
            content.findViewById<View>(viewId).setOnClickListener {
                popup.dismiss()
                AppLanguage.apply(tag)
                CalculatorWidgetProvider.refreshLanguage(this)
            }
        }
        select(R.id.langAr, "ar")
        select(R.id.langEn, "en")
        select(R.id.langTr, "tr")
        select(R.id.langKu, "ku")
        popup.showAsDropDown(anchor, 0, (6 * resources.displayMetrics.density).toInt())
    }
}

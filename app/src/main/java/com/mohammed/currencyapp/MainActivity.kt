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
        private const val ANNOUNCEMENT_TEXT = "announcement_text"
        private const val ANNOUNCEMENT_PUBLISHED_AT = "announcement_published_at"
        private const val ANNOUNCEMENT_CLOSE_COUNT = "announcement_close_count"
        private const val ANNOUNCEMENT_VISIBLE_MS = 12 * 60 * 60 * 1000L
        private const val ANNOUNCEMENT_MAX_CLOSES = 3
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: PriceListAdapter

    private var latestExchanges: List<PriceItem> = emptyList()
    private var latestCurrencies: List<PriceItem> = emptyList()

    // رقم دورة التحديث الحالية حتى لا يؤثر رد شبكة قديم على دورة أحدث.
    private var refreshGeneration = 0
    private var exchangesLoadedGeneration = -1
    private var currenciesLoadedGeneration = -1
    private var updateTimeRecordedGeneration = -1
    private var announcementStartedGeneration = -1

    // تحديث الأسعار كل 15 دقيقة طالما التطبيق مفتوح بالمقدمة
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshIntervalMs = 15 * 60 * 1000L
    private val refreshRunnable = object : Runnable {
        override fun run() {
            startDataRefresh()
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
                    putExtra(HistoryActivity.EXTRA_UNIT, item.unit)
                }
                startActivity(intent)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // نبقي السحب للأسفل كتحديث، لكن نخفي دائرة SwipeRefreshLayout؛
        // شريط mainProgress الرفيع أسفل الهيدر هو مؤشر التحديث الجديد.
        binding.swipeRefresh.setColorSchemeColors(android.graphics.Color.TRANSPARENT)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(android.graphics.Color.TRANSPARENT)
        // نخفي CircleImageView الداخلي نفسه؛ السحب يبقى فعالاً لكن لا يظهر
        // أي سهم/دائرة، ويكون mainProgress الرفيع هو المؤشر الوحيد.
        for (index in 0 until binding.swipeRefresh.childCount) {
            val child = binding.swipeRefresh.getChildAt(index)
            if (child !is androidx.recyclerview.widget.RecyclerView) child.alpha = 0f
        }
        binding.swipeRefresh.setOnRefreshListener {
            startDataRefresh(invalidateNetworkCache = true)
            binding.swipeRefresh.post { binding.swipeRefresh.isRefreshing = false }
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
            startDataRefresh(invalidateNetworkCache = true)
        }

        updateLastUpdateLabel(LastUpdateStore.load(this))

        // اختصارات أيقونة التطبيق (ضغطة مطولة): المفضلات الثلاث، كل وحدة بعلمها.
        FavoriteShortcuts.update(this)

        // فتحنا من اختصار الشاشة الرئيسية (ضغطة مطولة على أيقونة التطبيق)؟
        // نفتح هيستوري المدينة المطلوبة مباشرة فوق الشاشة الرئيسية.
        openShortcutHistory(intent)

        // اعرض آخر أسعار محفوظة فوراً قبل بدء جلب الشبكة حتى لا تبدأ
        // الصفحة فارغة. عند وصول سعر جديد يعيد التحديث بناء القائمة
        // وتشغيل Matrix/ألوان التغيير كالمعتاد.
        latestExchanges = TelegramScraperRepository.buildCachedItems(this)
        latestCurrencies = CurrenciesRepository.buildCachedItems(this)
        rebuildRows()

        startDataRefresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openShortcutHistory(intent)
    }

    private fun openShortcutHistory(source: Intent?) {
        val key = source?.getStringExtra(EXTRA_SHORTCUT_CITY_KEY) ?: return
        val name = source.getStringExtra(EXTRA_SHORTCUT_CITY_NAME) ?: ""
        val flag = source.getStringExtra(EXTRA_SHORTCUT_CITY_FLAG) ?: ""
        source.removeExtra(EXTRA_SHORTCUT_CITY_KEY)
        source.removeExtra(EXTRA_SHORTCUT_CITY_NAME)
        source.removeExtra(EXTRA_SHORTCUT_CITY_FLAG)
        setIntent(source)
        startActivity(Intent(this, HistoryActivity::class.java).apply {
            putExtra(HistoryActivity.EXTRA_KEY, key)
            putExtra(HistoryActivity.EXTRA_NAME, name)
            putExtra(HistoryActivity.EXTRA_FLAG, flag)
        })
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


    private fun startDataRefresh(invalidateNetworkCache: Boolean = false) {
        if (invalidateNetworkCache) {
            TelegramScraperRepository.invalidateSharedMessages()
        }
        refreshGeneration++
        exchangesLoadedGeneration = -1
        currenciesLoadedGeneration = -1
        updateTimeRecordedGeneration = -1
        val generation = refreshGeneration
        binding.mainProgress.visibility = View.VISIBLE
        loadExchanges(generation)
        loadCurrencies(generation)
    }

    private fun loadExchanges(generation: Int) {
        TelegramScraperRepository.fetch(this) { items ->
            if (generation != refreshGeneration) return@fetch
            latestExchanges = FavoriteCityStore.sortWithFavoriteFirst(this, items)
            exchangesLoadedGeneration = generation
            rebuildRows(recordRefreshCompletion = true)
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
            val prefs = getSharedPreferences(ANNOUNCEMENT_PREFS, MODE_PRIVATE)
            val savedPostId = prefs.getLong(ANNOUNCEMENT_POST_ID, -1L)
            val savedText = prefs.getString(ANNOUNCEMENT_TEXT, null)
            val savedPublishedAt = prefs.getLong(ANNOUNCEMENT_PUBLISHED_AT, 0L)
            val savedAnnouncement = if (savedPostId >= 0L && !savedText.isNullOrBlank() && savedPublishedAt > 0L) {
                TelegramScraperRepository.Announcement(savedText, savedPostId, savedPublishedAt)
            } else null

            // إذا خرج التنويه من صفحة Telegram الحالية، نكمل عرض آخر تنويه محفوظ
            // بدل إخفائه قبل انتهاء الـ12 ساعة.
            val activeAnnouncement = when {
                announcement == null -> savedAnnouncement
                savedAnnouncement != null && announcement.postId < savedAnnouncement.postId -> savedAnnouncement
                else -> announcement
            }
            if (activeAnnouncement == null) {
                binding.announcementBar.visibility = View.GONE
                return@fetchLatestAnnouncement
            }

            val now = System.currentTimeMillis()
            val closeCount = if (savedPostId == activeAnnouncement.postId) {
                prefs.getInt(ANNOUNCEMENT_CLOSE_COUNT, 0)
            } else 0
            val publishedAt = activeAnnouncement.publishedAtMillis
            if (publishedAt <= 0L || now - publishedAt >= ANNOUNCEMENT_VISIBLE_MS || closeCount >= ANNOUNCEMENT_MAX_CLOSES) {
                binding.announcementBar.visibility = View.GONE
                return@fetchLatestAnnouncement
            }
            if (savedPostId != activeAnnouncement.postId) {
                prefs.edit()
                    .putLong(ANNOUNCEMENT_POST_ID, activeAnnouncement.postId)
                    .putString(ANNOUNCEMENT_TEXT, activeAnnouncement.text)
                    .putLong(ANNOUNCEMENT_PUBLISHED_AT, activeAnnouncement.publishedAtMillis)
                    .putInt(ANNOUNCEMENT_CLOSE_COUNT, 0)
                    .apply()
            } else if (savedText.isNullOrBlank() || savedPublishedAt <= 0L) {
                // توافق مع النسخ السابقة التي كانت تحفظ رقم المنشور فقط.
                prefs.edit()
                    .putString(ANNOUNCEMENT_TEXT, activeAnnouncement.text)
                    .putLong(ANNOUNCEMENT_PUBLISHED_AT, activeAnnouncement.publishedAtMillis)
                    .apply()
            }

            binding.tvAnnouncementText.text = activeAnnouncement.text
            binding.tvAnnouncementText.isSelected = true
            speedUpAnnouncementMarquee()
            binding.announcementBar.visibility = View.VISIBLE
            KurdishFont.apply(binding.announcementBar, this)

            // الضغط على النص لا يفتح المنشور؛ الشريط مخصص للعرض فقط.
            binding.tvAnnouncementText.setOnClickListener(null)
            binding.btnCloseAnnouncement.setOnClickListener {
                val currentId = prefs.getLong(ANNOUNCEMENT_POST_ID, -1L)
                if (currentId == activeAnnouncement.postId) {
                    val nextCount = prefs.getInt(ANNOUNCEMENT_CLOSE_COUNT, 0) + 1
                    prefs.edit().putInt(ANNOUNCEMENT_CLOSE_COUNT, nextCount).apply()
                }
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

    private fun loadCurrencies(generation: Int) {
        CurrenciesRepository.fetch(this) { currencies ->
            if (generation != refreshGeneration) return@fetch
            latestCurrencies = currencies
            currenciesLoadedGeneration = generation
            rebuildRows(recordRefreshCompletion = true)
        }
    }

    private fun updateLastUpdateLabel(millis: Long?) {
        binding.tvLastUpdate.text = if (millis != null) {
            getString(R.string.last_update_prefix, UpdateTimeFormat.timeAndDate(this, millis))
        } else getString(R.string.last_update_none)
    }

    private fun rebuildRows(recordRefreshCompletion: Boolean = false) {
        val allItems = mutableListOf<PriceItem>()
        allItems.addAll(latestExchanges)
        allItems.addAll(latestCurrencies)

        val favorites = FavoriteCityStore.getAll(this)
        val ordered = allItems.sortedBy { item ->
            val i = favorites.indexOf(item.cityKey ?: item.name)
            if (i < 0) Int.MAX_VALUE else i
        }
        adapter.submitList(ordered.map { ListRow.Row(it) }, favorites)

        // نسجل التوقيت مرة واحدة فقط بعد اكتمال المصدرين، لا عند وصول أول
        // مصدر. لا ننتظر هنا ولا نؤخر عرض القائمة؛ كل مصدر يُعرض فور وصوله.
        if (
            recordRefreshCompletion &&
            exchangesLoadedGeneration == refreshGeneration &&
            currenciesLoadedGeneration == refreshGeneration &&
            updateTimeRecordedGeneration != refreshGeneration
        ) {
            val now = System.currentTimeMillis()
            updateTimeRecordedGeneration = refreshGeneration
            LastUpdateStore.save(this, now)
            updateLastUpdateLabel(now)
            // نفس لحظة اكتمال تحديث التطبيق تُحفظ للحاسبة وتُعرض كل ويدجتاتها
            // فوراً من الكاش المحلي، فلا يبقى وقت الحاسبة قديماً.
            CalculatorWidgetProvider.syncFromMainRefresh(this, now)
        }
        if (exchangesLoadedGeneration == refreshGeneration && currenciesLoadedGeneration == refreshGeneration) {
            binding.mainProgress.visibility = View.INVISIBLE
            if (announcementStartedGeneration != refreshGeneration) {
                announcementStartedGeneration = refreshGeneration
                // التنويه مهمة ثانوية؛ لا يؤخر عرض أسعار المدن والعملات.
                loadLatestAnnouncement()
            }
        }

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

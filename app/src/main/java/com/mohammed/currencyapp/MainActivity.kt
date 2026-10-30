package com.mohammed.currencyapp

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.os.SystemClock
import androidx.core.widget.TextViewCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mohammed.currencyapp.databinding.ActivityMainBinding
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SHORTCUT_CITY_KEY = "shortcut_city_key"
        const val EXTRA_SHORTCUT_CITY_NAME = "shortcut_city_name"
        const val EXTRA_SHORTCUT_CITY_FLAG = "shortcut_city_flag"
        private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val TAB_CRYPTO = 0
        private const val TAB_GLOBAL = 1
        private const val TAB_HOME = 2
        private const val TAB_LOCAL = 3
        private const val TAB_METALS = 4
        private const val TAB_SETTINGS = 5 // خارج الشريط السفلي؛ تُفتح من مربع "الإعدادات" بالرئيسية
        private const val TAB_NEWS = 6 // خارج الشريط السفلي؛ تُفتح من "عرض الكل" بالرئيسية
    }

    private lateinit var binding: ActivityMainBinding
    private var currentTab = TAB_HOME
    private var latestExchanges: List<PriceItem> = emptyList()
    private var latestCurrencies: List<PriceItem> = emptyList()
    private var latestCrypto: List<PriceItem> = emptyList()
    private var latestMetals: List<PriceItem> = emptyList()
    private var dragging = false
    private var notices: List<TelegramScraperRepository.ChannelNotice> = emptyList()
    private var refreshGeneration = 0
    private var scrollTarget: View? = null
    private val expandedNews = mutableSetOf<Long>()
    private var progressShownAt = 0L
    private val hideProgressRunnable = Runnable { binding.mainProgress.visibility = View.INVISIBLE }
    private val cornerPx: Float by lazy { resources.getDimension(R.dimen.corner_radius) }
    private val swipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (currentTab > TAB_METALS || dragging) return false
                if (kotlin.math.abs(dx) > dp(70) && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.6f &&
                    kotlin.math.abs(velocityX) > dp(300)) {
                    // ترتيب الشريط من اليمين: رقمية، عالمي، رئيسية، محلي، معادن — والتنقل دائري.
                    val count = TAB_METALS + 1
                    val next = if (dx < 0) (currentTab + 1) % count else (currentTab + count - 1) % count
                    showTab(next)
                    return true
                }
                return false
            }
        })
    }
    private var lastUpdateTime: Long? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshData()
            mainHandler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        KurdishFont.apply(binding.root, this)
        applySystemBarsInsets(binding.toolbar, binding.bottomNavigation)
        // تقليل ارتفاع الشريط السفلي 2 ملم من الأعلى بدون تحريك الأيقونات: نقص الخلفية فقط من فوق.
        val trim = (11.3f * resources.displayMetrics.density).toInt()
        binding.bottomNavigation.background = android.graphics.drawable.InsetDrawable(
            android.graphics.drawable.ColorDrawable(ContextCompat.getColor(this, R.color.headerBlue)), 0, trim, 0, 0
        )
        (binding.bottomNavigation.layoutParams as ViewGroup.MarginLayoutParams).topMargin = -trim
        currentTab = savedInstanceState?.getInt("selected_main_tab", TAB_HOME) ?: TAB_HOME
        languageExpanded = savedInstanceState?.getBoolean("lang_expanded", false) ?: false
        lastUpdateTime = LastUpdateStore.load(this)
        latestExchanges = TelegramScraperRepository.buildCachedItems(this)
        latestCurrencies = CurrenciesRepository.buildCachedItems(this)
        latestCrypto = CryptoRepository.buildCachedItems(this)
        latestMetals = MetalsRepository.buildCachedItems(this)
        notices = NewsArchiveStore.load(this)
        setupBottomNavigation()
        showTab(currentTab, animate = false)
        openShortcutHistory(intent)
        // عند إعادة الإنشاء (تغيير اللغة/المظهر) نعرض الكاش فوراً بدون تحديث شبكة.
        if (savedInstanceState == null) refreshData()
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab == TAB_NEWS || currentTab == TAB_SETTINGS) showTab(TAB_HOME) else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) dragging = false
        return handled
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_main_tab", currentTab)
        outState.putBoolean("lang_expanded", languageExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(refreshRunnable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        openShortcutHistory(intent)
    }

    private data class NavSpec(val label: Int, val icon: Int, val tab: Int)
    private val navSpecs = listOf(
        NavSpec(R.string.navbar_crypto, R.drawable.ic_nav_bitcoin, TAB_CRYPTO),
        NavSpec(R.string.navbar_global, R.drawable.ic_globe, TAB_GLOBAL),
        NavSpec(R.string.nav_home, R.drawable.ic_nav_home, TAB_HOME),
        NavSpec(R.string.navbar_local, R.drawable.ic_nav_bars, TAB_LOCAL),
        NavSpec(R.string.navbar_metals, R.drawable.ic_nav_metals, TAB_METALS)
    )

    private fun setupBottomNavigation() {
        binding.bottomNavigation.layoutDirection = View.LAYOUT_DIRECTION_RTL
        binding.bottomNavigation.removeAllViews()
        navSpecs.forEach { spec ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                clipChildren = false
                clipToPadding = false
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                setPadding(dp(2), dp(4), dp(2), dp(2))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { showTab(spec.tab) }
                tag = "nav_${spec.tab}"
            }
            val iconHolder = FrameLayout(this).apply {
                background = rounded(if (spec.tab == currentTab) 0xFF1769E0.toInt() else Color.TRANSPARENT, 100)
                val holderSize = if (spec.tab == currentTab) 52 else 36
                layoutParams = LinearLayout.LayoutParams(dp(holderSize), dp(holderSize)).apply { gravity = Gravity.CENTER }
                if (spec.tab == currentTab) translationY = -dp(15).toFloat()
            }
            val icon = ImageView(this).apply {
                setImageResource(spec.icon)
                imageTintList = android.content.res.ColorStateList.valueOf(if (spec.tab == currentTab) Color.WHITE else 0xFFBCCBE2.toInt())
                layoutParams = FrameLayout.LayoutParams(dp(if (spec.tab == currentTab) 27 else 22), dp(if (spec.tab == currentTab) 27 else 22), Gravity.CENTER)
                contentDescription = getString(spec.label)
            }
            iconHolder.addView(icon)
            val label = text(getString(spec.label), 10, if (spec.tab == currentTab) Color.WHITE else 0xFFBCCBE2.toInt(), true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22))
                if (spec.tab == currentTab) translationY = -dp(7).toFloat()
            }
            item.addView(iconHolder)
            item.addView(label)
            val underline = View(this).apply {
                background = rounded(if (spec.tab == currentTab) 0xFF3E8BFF.toInt() else Color.TRANSPARENT, 30)
                layoutParams = LinearLayout.LayoutParams(dp(if (spec.tab == currentTab) 36 else 12), dp(3)).apply {
                    gravity = Gravity.CENTER
                    topMargin = dp(1)
                }
                if (spec.tab == currentTab) translationY = -dp(4).toFloat()
            }
            item.addView(underline)
            binding.bottomNavigation.addView(item)
        }
    }

    private fun titleFor(tab: Int): String = getString(
        when (tab) {
            TAB_NEWS -> R.string.news_page_title
            TAB_CRYPTO -> R.string.nav_crypto
            TAB_METALS -> R.string.nav_metals
            TAB_GLOBAL -> R.string.nav_global
            TAB_LOCAL -> R.string.nav_local
            TAB_SETTINGS -> R.string.nav_settings
            else -> R.string.app_name
        }
    )

    private fun showTab(tab: Int, animate: Boolean = true) {
        if (tab !in TAB_CRYPTO..TAB_NEWS) return
        currentTab = tab
        setupBottomNavigation()
        scrollTarget = null
        binding.tvAppTitle.text = titleFor(tab)
        val page = when (tab) {
            TAB_HOME -> buildHomePage()
            TAB_NEWS -> buildNewsPage()
            TAB_GLOBAL -> buildMarketPage(MarketKind.GLOBAL)
            TAB_LOCAL -> buildMarketPage(MarketKind.LOCAL)
            TAB_CRYPTO -> buildMarketPage(MarketKind.CRYPTO)
            TAB_METALS -> buildMarketPage(MarketKind.METALS)
            else -> buildSettingsPage()
        }
        KurdishFont.apply(page, this)
        val shown = if (tab == TAB_SETTINGS) page else wrapWithRefresh(page)
        binding.pageContainer.removeAllViews()
        shown.alpha = if (animate) 0f else 1f
        binding.pageContainer.addView(shown, FrameLayout.LayoutParams(-1, -1))
        if (animate) shown.animate().alpha(1f).setDuration(180).start()
    }

    /** السحب للأسفل يحدّث ويستعمل الخط تحت الهيدر؛ دائرة التحديث الافتراضية مخفية تماماً. */
    private fun wrapWithRefresh(page: View): View {
        val refresh = SwipeRefreshLayout(this)
        refresh.setProgressViewOffset(false, -dp(300), -dp(300))
        refresh.setColorSchemeColors(Color.TRANSPARENT)
        refresh.setProgressBackgroundColorSchemeColor(Color.TRANSPARENT)
        refresh.setOnChildScrollUpCallback { _, _ -> dragging || (scrollTarget?.canScrollVertically(-1) ?: false) }
        refresh.setOnRefreshListener {
            refresh.isRefreshing = false
            Haptics.tick(refresh, strong = true)
            refreshData(invalidate = true)
        }
        refresh.addView(page, ViewGroup.LayoutParams(-1, -1))
        return refresh
    }

    private fun showProgress() {
        progressShownAt = SystemClock.uptimeMillis()
        mainHandler.removeCallbacks(hideProgressRunnable)
        binding.mainProgress.visibility = View.VISIBLE
        mainHandler.postDelayed(hideProgressRunnable, 20_000L) // مؤقت أمان لو الشبكة علّقت
    }

    /** يخفي الخط بعد ما يبقى ظاهر وقت كافي (~0.9ث) حتى لو التحديث من الكاش وسريع. */
    private fun hideProgress() {
        val wait = (900L - (SystemClock.uptimeMillis() - progressShownAt)).coerceAtLeast(0L)
        mainHandler.removeCallbacks(hideProgressRunnable)
        mainHandler.postDelayed(hideProgressRunnable, wait)
    }

    private fun refreshData(invalidate: Boolean = false) {
        if (invalidate) TelegramScraperRepository.invalidateSharedMessages()
        val generation = ++refreshGeneration
        showProgress()
        TelegramScraperRepository.fetchMainPage(this) { exchanges, currencies ->
            if (generation != refreshGeneration || isFinishing) return@fetchMainPage
            latestExchanges = FavoriteCityStore.sortWithFavoriteFirst(this, exchanges)
            latestCurrencies = currencies
            CryptoRepository.fetch(this) { crypto ->
                if (isFinishing) return@fetch
                latestCrypto = crypto
                if (currentTab == TAB_CRYPTO || currentTab == TAB_HOME) renderCurrentTab()
            }
            MetalsRepository.fetch(this) { metals ->
                if (isFinishing) return@fetch
                latestMetals = metals
                if (currentTab == TAB_METALS || currentTab == TAB_HOME) renderCurrentTab()
            }
            val now = System.currentTimeMillis()
            lastUpdateTime = now
            LastUpdateStore.save(this, now)
            CalculatorWidgetProvider.syncFromMainRefresh(this, now)
            FavoriteWidgetProvider.renderFromCache(this)
            WidgetRefreshScheduler.markRefreshed(this)
            renderCurrentTab()
            TelegramScraperRepository.fetchLatestNotices { latest ->
                if (isFinishing) return@fetchLatestNotices
                if (generation == refreshGeneration) hideProgress()
                NewsArchiveStore.merge(this, latest)
                notices = NewsArchiveStore.load(this)
                if (currentTab == TAB_HOME || currentTab == TAB_NEWS) renderCurrentTab()
            }
        }
    }

    private fun renderCurrentTab() = showTab(currentTab, animate = false)

    private fun buildHomePage(): View {
        val scroll = ScrollView(this).apply {
            clipToPadding = false; clipChildren = false; isFillViewport = false
            isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER
        }
        scrollTarget = scroll
        val content = vertical().apply { clipChildren = false; clipToPadding = false; setPadding(dp(14), dp(0), dp(14), dp(14)) }
        content.addView(buildUpdateStrip(getString(R.string.favorites_title)))
        // المفضلة حتى 5 (الافتراضي: بغداد، أربيل، الرسمي، البصرة، اليورو).
        val favoriteKeys = FavoriteCityStore.getAll(this)
        val allItems = latestCurrencies + latestExchanges + latestCrypto + latestMetals
        val favoriteItems = favoriteKeys.mapNotNull { key -> allItems.find { (it.cityKey ?: it.name) == key } }
        if (favoriteItems.isEmpty()) {
            content.addView(emptyCard(getString(R.string.favorites_empty), getString(R.string.favorites_hint)))
        } else {
            content.addView(buildFavoritesList(favoriteItems, favoriteKeys))
        }
        content.addView(buildActionCards())
        content.addView(buildNewsPreview())
        scroll.addView(content)
        return scroll
    }

    /** مربع حقيقي: الارتفاع = العرض. */
    private class SquareLayout(context: Context) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            super.onMeasure(exact, exact)
        }
    }

    private fun fitText(view: TextView, minSp: Int, maxSp: Int) {
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            view, minSp, maxSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP
        )
    }

    private fun buildActionCards(): View {
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(7) }
        }
        val actionSpecs = listOf(
            Triple(R.string.quick_refresh, R.drawable.ic_refresh_history) { refreshData(invalidate = true) },
            Triple(R.string.quick_calculator, R.drawable.ic_calculator) { startActivity(Intent(this, CalculatorActivity::class.java)) },
            Triple(R.string.quick_about, R.drawable.ic_menu_burger) { openAbout() },
            Triple(R.string.nav_settings, R.drawable.ic_nav_settings) { showTab(TAB_SETTINGS) }
        )
        actionSpecs.forEachIndexed { index, action ->
            val card = SquareLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(0xFF092E5A.toInt(), 0)
                setPadding(dp(3), dp(4), dp(3), dp(4))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index < actionSpecs.lastIndex) marginEnd = dp(7)
                }
                setOnClickListener { action.third.invoke() }
            }
            val circleColor = listOf(0xFF2878F0.toInt(), 0xFF0BA77D.toInt(), 0xFFE89A20.toInt(), 0xFF6653D8.toInt())[index]
            val circle = FrameLayout(this).apply {
                background = rounded(circleColor, 100)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { gravity = Gravity.CENTER }
            }
            val icon = ImageView(this).apply {
                setImageResource(action.second)
                imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER)
            }
            circle.addView(icon)
            card.addView(circle)
            card.addView(text(getString(action.first), 11, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(4), 0, 0)
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                fitText(this, 7, 11)
            })
            val subtitle = when (action.first) {
                R.string.quick_refresh -> lastUpdateTime?.let { UpdateTimeFormat.stamp(it) } ?: getString(R.string.last_update_none)
                R.string.quick_calculator -> getString(R.string.quick_convert)
                R.string.quick_about -> getString(R.string.app_name)
                else -> getString(R.string.app_name)
            }
            card.addView(text(subtitle, 8, 0xFFB9CBE4.toInt(), false).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
                fitText(this, 5, 8)
            })
            actions.addView(card)
        }
        return actions
    }

    private fun buildNewsPreview(): View {
        val card = vertical().apply {
            background = rounded(0xFF082F60.toInt(), 0)
            setPadding(dp(12), dp(9), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val titleGroup = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        titleGroup.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_nav_news)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(8) })
        titleGroup.addView(text(getString(R.string.latest_news), 17, Color.WHITE, true))
        header.addView(titleGroup, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text(getString(R.string.view_all) + "  ›", 12, 0xFF87B8FF.toInt(), true).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { showTab(TAB_NEWS) }
        })
        card.addView(header)
        card.addView(separator().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) })
        val latest = notices.take(2)
        if (latest.isEmpty()) {
            card.addView(text(getString(R.string.news_empty), 13, 0xFFB9CBE4.toInt(), false).apply { setPadding(0, dp(14), 0, dp(12)) })
        } else {
            latest.forEachIndexed { index, notice ->
                if (index > 0) card.addView(separator())
                // الأخبار بالرئيسية للعرض فقط (غير قابلة للضغط)، والنص أبيض بالوضعين.
                card.addView(buildNewsRow(notice, compact = true, onDark = true).first)
            }
        }
        return card
    }

    private fun buildNewsPage(): View {
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        scrollTarget = scroll
        val content = vertical().apply { setPadding(dp(14), dp(10), dp(14), dp(18)) }
        if (notices.isEmpty()) content.addView(emptyCard(getString(R.string.news_empty), getString(R.string.news_fallback_title)))
        else notices.sortedByDescending { it.publishedAtMillis }.forEach { notice ->
            val card = vertical().apply {
                background = rounded(0xFF082F60.toInt(), 0)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                isClickable = true
            }
            val (row, body) = buildNewsRow(notice, compact = false, onDark = true)
            card.addView(row)
            val openLink = text(getString(R.string.news_open_channel), 12, 0xFF3684F5.toInt(), true).apply {
                gravity = Gravity.RIGHT
                setPadding(0, dp(4), 0, dp(2))
                visibility = if (notice.id in expandedNews) View.VISIBLE else View.GONE
                setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(notice.postUrl))) }
                }
            }
            card.addView(openLink)
            // الضغط يوسّع الخبر مكانه ويدفع الأخبار اللي تحته للأسفل (بدون فتح أي شي).
            card.setOnClickListener {
                TransitionManager.beginDelayedTransition(content, AutoTransition().setDuration(200))
                if (notice.id in expandedNews) expandedNews.remove(notice.id) else expandedNews.add(notice.id)
                val expanded = notice.id in expandedNews
                body.maxLines = if (expanded) Int.MAX_VALUE else 2
                body.ellipsize = if (expanded) null else android.text.TextUtils.TruncateAt.END
                openLink.visibility = if (expanded) View.VISIBLE else View.GONE
            }
            if (notice.id in expandedNews) { body.maxLines = Int.MAX_VALUE; body.ellipsize = null }
            content.addView(card)
        }
        scroll.addView(content)
        return scroll
    }

    /** يرجع الصف + نص الخبر (لتوسيعه). */
    private fun buildNewsRow(notice: TelegramScraperRepository.ChannelNotice, compact: Boolean, onDark: Boolean): Pair<View, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            setPadding(0, dp(if (compact) 7 else 4), 0, dp(if (compact) 7 else 4))
        }
        row.addView(newsImage(notice.imageUrl, compact, trailingDots(notice.text)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(if (compact) 62 else 78), dp(if (compact) 62 else 78))
        })
        // خط عمودي أبيض (يخفت من الأطراف) يفصل الصورة عن الخبر.
        row.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF)
            )
        }, LinearLayout.LayoutParams(dp(2), dp(if (compact) 62 else 78)).apply { marginStart = dp(9); marginEnd = dp(9) })
        val texts = vertical().apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val body = text(notice.text, if (compact) 12 else 14, if (onDark) Color.WHITE else textPrimary(), true).apply {
            gravity = Gravity.RIGHT
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        texts.addView(body)
        texts.addView(text(formatNoticeDate(notice.publishedAtMillis), 10, if (onDark) 0xFFB9CBE4.toInt() else textSecondary(), false).apply {
            gravity = Gravity.RIGHT; setPadding(0, dp(3), 0, 0)
        })
        row.addView(texts)
        return row to body
    }

    /** عدد النقاط بآخر نص الخبر (1–4) يحدد صورة الفئة بدل علامة الدولار. */
    private fun trailingDots(text: String): Int =
        text.trimEnd().takeLastWhile { it == '.' }.length.coerceAtMost(5)

    @Suppress("UNUSED_PARAMETER")
    private fun newsImage(url: String?, compact: Boolean, dots: Int = 0): FrameLayout {
        val frame = FrameLayout(this).apply { background = rounded(0xFF315D8A.toInt(), 12); clipToOutline = true }
        // بدون نقاط بنهاية الخبر: أيقونة التطبيق كصورة افتراضية.
        val fallback = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.mipmap.ic_launcher)
        }
        val categoryRes = when (dots) {
            1 -> R.drawable.news_cat_1
            2 -> R.drawable.news_cat_2
            3 -> R.drawable.news_cat_3
            4 -> R.drawable.news_cat_4
            5 -> R.drawable.news_cat_5
            else -> 0
        }
        if (categoryRes != 0) {
            frame.addView(ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(categoryRes)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            frame.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        }
        if (!url.isNullOrBlank() && url.startsWith("https://")) NewsImageLoader.load(url) { bitmap ->
            if (bitmap != null && !isFinishing) {
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setImageBitmap(bitmap) }
                frame.removeAllViews()
                frame.addView(image, FrameLayout.LayoutParams(-1, -1))
            }
        }
        return frame
    }

    private enum class MarketKind { GLOBAL, LOCAL, CRYPTO, METALS }

    private fun buildMarketPage(kind: MarketKind): View {
        val items = when (kind) {
            MarketKind.GLOBAL -> latestCurrencies
            MarketKind.LOCAL -> latestExchanges
            MarketKind.CRYPTO -> latestCrypto
            MarketKind.METALS -> latestMetals
        }
        val title = getString(when (kind) {
            MarketKind.GLOBAL -> R.string.market_global_title
            MarketKind.LOCAL -> R.string.market_local_title
            MarketKind.CRYPTO -> R.string.market_crypto_title
            MarketKind.METALS -> R.string.market_metals_title
        })
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(buildUpdateStrip(title, dp(14)))
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(14), 0, dp(14), dp(8))
        }
        scrollTarget = list
        val adapter = PriceListAdapter(
            rows = items.map { ListRow.Row(it) },
            favoriteKeys = FavoriteCityStore.getAll(this),
            onStarClick = { key -> onStarClicked(key) },
            onRowClick = { item -> openHistory(item) }
        )
        list.adapter = adapter
        if (items.isEmpty()) {
            root.addView(emptyCard(getString(if (kind == MarketKind.METALS) R.string.metals_empty else R.string.crypto_empty), getString(R.string.favorites_empty)).apply {
                layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(14), dp(8), dp(14), 0) }
            })
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun onStarClicked(key: String) {
        FavoriteCityStore.toggle(this, key)
        FavoriteShortcuts.update(this)
        FavoriteWidgetProvider.renderFromCache(this)
        renderCurrentTab()
    }

    /** مفضلة الرئيسية: صفوف قابلة للسحب بالمقبض (3 خطوط) والترتيب يُحفظ ويثبت. */
    private fun buildFavoritesList(items: List<PriceItem>, favoriteKeys: List<String>): View {
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            clipChildren = false
            isVerticalScrollBarEnabled = false
            // مساحة جانبية للظل حتى ما تنقص حواف الصفوف؛ الهامش السالب يبقي العرض مثل العالمي/المحلي.
            setPadding(dp(4), 0, dp(4), 0)
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(-dp(4), 0, -dp(4), 0) }
        }
        lateinit var touchHelper: ItemTouchHelper
        val adapter = PriceListAdapter(
            rows = items.map { ListRow.Row(it) },
            favoriteKeys = favoriteKeys,
            onRowClick = { openHistory(it) },
            dragMode = true,
            onStartDrag = { holder ->
                dragging = true
                // نمنع ScrollView من سرقة اللمس أثناء السحب (بدون منع RecyclerView نفسه).
                (scrollTarget as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                Haptics.tick(holder.itemView, strong = true)
                touchHelper.startDrag(holder)
            }
        )
        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(vh.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                dragging = false
                FavoriteCityStore.setOrder(this@MainActivity, adapter.currentKeys())
                FavoriteShortcuts.update(this@MainActivity)
                FavoriteWidgetProvider.renderFromCache(this@MainActivity)
            }
        })
        list.adapter = adapter
        touchHelper.attachToRecyclerView(list)
        return list
    }

    private var languageExpanded = false

    private fun isRtlLanguage() = AppLanguage.currentTag().let { it == "ar" || it == "ku" }

    private fun buildSettingsPage(): View {
        val scroll = ScrollView(this).apply { clipToPadding = false; isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        // عربي/كوردي: يمين لليسار؛ إنكليزي/تركي: يسار لليمين (الأيقونات والنصوص والأسهم تتبع الاتجاه).
        val rtl = isRtlLanguage()
        val content = vertical().apply {
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            textDirection = if (rtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            setPadding(dp(14), 0, dp(14), dp(18))
        }
        val blue = 0xFF1769E0.toInt()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.6" }.getOrDefault("1.1.6")

        // ── تفضيلات التطبيق ──
        content.addView(settingsSectionTitle(getString(R.string.settings_section_prefs)))
        val prefs = settingsCard()
        val langRow = settingsRow(
            R.drawable.ic_globe, getString(R.string.settings_language), currentLanguageLabel(),
            trailing = settingsChevron()
        )
        prefs.addView(langRow)
        val langList = vertical().apply { visibility = if (languageExpanded) View.VISIBLE else View.GONE }
        val currentTag = AppLanguage.currentTag()
        listOf(
            Triple("ar", R.drawable.flag_iq, R.string.language_ar),
            Triple("en", R.drawable.flag_us, R.string.language_en),
            Triple("tr", R.drawable.flag_tr, R.string.language_tr),
            Triple("ku", R.drawable.flag_kurdistan, R.string.language_ku)
        ).forEach { (tag, flag, label) ->
            val option = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(7), dp(14), dp(7))
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                setOnClickListener { if (tag != currentTag) { AppLanguage.apply(tag); CalculatorWidgetProvider.refreshLanguage(this@MainActivity) } }
            }
            option.addView(ImageView(this).apply {
                setImageResource(flag)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.bg_flag_ring)
                clipToOutline = true
            }, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginStart = dp(48) })
            option.addView(text(getString(label), 14, textPrimary(), tag == currentTag).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
            })
            if (tag == currentTag) option.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_set_check)
                imageTintList = android.content.res.ColorStateList.valueOf(blue)
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
            langList.addView(option)
        }
        prefs.addView(langList)
        langRow.setOnClickListener {
            TransitionManager.beginDelayedTransition(content, AutoTransition().setDuration(200))
            languageExpanded = !languageExpanded
            langList.visibility = if (languageExpanded) View.VISIBLE else View.GONE
        }
        prefs.addView(settingsDivider())
        val night = ThemePreferences.isNightMode(this)
        val themeSwitch = androidx.appcompat.widget.SwitchCompat(this).apply {
            isChecked = night
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(blue, 0xFFBDBDBD.toInt()))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(0x661769E0, 0x44808080))
        }
        val themeRow = settingsRow(R.drawable.ic_set_theme, getString(R.string.settings_appearance), getString(R.string.settings_appearance_sub), trailing = themeSwitch)
        themeSwitch.setOnCheckedChangeListener { _, _ -> ThemePreferences.toggle(this) }
        themeRow.setOnClickListener { themeSwitch.toggle() }
        prefs.addView(themeRow)
        content.addView(prefs)

        // ── عام ──
        content.addView(settingsSectionTitle(getString(R.string.settings_section_general)))
        val general = settingsCard()
        general.addView(settingsRow(R.drawable.ic_set_help, getString(R.string.settings_guide), getString(R.string.settings_guide_sub), trailing = settingsChevron()) {
            InfoPageActivity.start(this, InfoPageActivity.PAGE_GUIDE)
        })
        general.addView(settingsDivider())
        general.addView(settingsRow(R.drawable.ic_set_doc, getString(R.string.settings_terms), getString(R.string.settings_terms_sub), trailing = settingsChevron()) {
            InfoPageActivity.start(this, InfoPageActivity.PAGE_TERMS)
        })
        general.addView(settingsDivider())
        general.addView(settingsRow(R.drawable.ic_set_mail, getString(R.string.settings_support), getString(R.string.settings_support_sub), trailing = settingsChevron()) {
            InfoPageActivity.start(this, InfoPageActivity.PAGE_SUPPORT)
        })
        general.addView(settingsDivider())
        general.addView(settingsRow(R.drawable.ic_set_people, getString(R.string.settings_social), getString(R.string.settings_social_sub), trailing = settingsChevron()) {
            InfoPageActivity.start(this, InfoPageActivity.PAGE_SOCIAL)
        })
        general.addView(settingsDivider())
        val versionText = text(version, 13, textSecondary(), true).apply { textDirection = View.TEXT_DIRECTION_LTR }
        general.addView(settingsRow(R.drawable.ic_set_info, getString(R.string.settings_about), getString(R.string.settings_about_sub), trailing = versionText) { openAbout() })
        content.addView(general)

        // ── الإضافات ──
        content.addView(settingsSectionTitle(getString(R.string.settings_section_extras)))
        val extras = settingsCard()
        extras.addView(settingsRow(R.drawable.ic_set_share, getString(R.string.settings_share), getString(R.string.settings_share_sub), trailing = settingsChevron()) { shareApp() })
        extras.addView(settingsDivider())
        // قيّم التطبيق: غير فعال حالياً لحين رفع التطبيق على منصة قوية.
        extras.addView(settingsRow(R.drawable.ic_set_star, getString(R.string.settings_rate), getString(R.string.settings_rate_sub), trailing = null).apply { alpha = 0.45f })
        content.addView(extras)

        scroll.addView(content)
        return scroll
    }

    private fun settingsSectionTitle(label: String): View = text(label, 15, textPrimary(), true).apply {
        gravity = Gravity.START
        setPadding(dp(4), dp(8), dp(4), dp(6))
    }

    private fun settingsCard(): LinearLayout = vertical().apply {
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@MainActivity, R.color.cardWhite))
            cornerRadius = cornerPx
        }
        elevation = dp(2).toFloat()
    }

    private fun settingsDivider(): View = View(this).apply {
        setBackgroundColor(0x1F808080)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(62); marginEnd = dp(14) }
    }

    private fun settingsChevron(): View = ImageView(this).apply {
        setImageResource(R.drawable.ic_set_chevron)
        imageTintList = android.content.res.ColorStateList.valueOf(textSecondary())
        // السهم يشير لليسار بالأصل (للعربي)؛ نعكسه بالإنكليزي/التركي.
        if (!isRtlLanguage()) scaleX = -1f
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
    }

    /** صف إعدادات: أيقونة ملونة بدائرة (يمين)، عنوان وسطر ثانوي، والعنصر اللاحق (سهم/زر/رقم) باليسار. */
    private fun settingsRow(iconRes: Int, title: String, subtitle: String?, trailing: View?, onClick: (() -> Unit)? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(58)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                background = selectableBackground()
                setOnClickListener { onClick() }
            }
        }
        val circle = FrameLayout(this).apply { background = rounded(0xFF1769E0.toInt(), 100) }
        circle.addView(ImageView(this).apply {
            setImageResource(iconRes)
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        row.addView(circle, LinearLayout.LayoutParams(dp(36), dp(36)))
        val texts = vertical().apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12); marginEnd = dp(8) }
        }
        texts.addView(text(title, 14, textPrimary(), true).apply { gravity = Gravity.START })
        if (subtitle != null) texts.addView(text(subtitle, 11, textSecondary(), false).apply { gravity = Gravity.START; setPadding(0, dp(2), 0, 0) })
        row.addView(texts)
        if (trailing != null) row.addView(trailing)
        return row
    }

    /** مشاركة التطبيق: نفس ملف الـAPK المثبّت يُرسل عبر قائمة المشاركة (تليغرام/واتساب/غيرهما)، ولو فشل يُرسل رابط القناة نصاً. */
    private fun shareApp() {
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "app"
        val message = getString(R.string.share_text)
        try {
            val dir = java.io.File(cacheDir, "shared_apk").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = java.io.File(dir, "BorsatDinari-$version.apk")
            java.io.File(applicationInfo.sourceDir).copyTo(out, overwrite = true)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", out)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.settings_share)))
        } catch (_: Exception) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            runCatching { startActivity(Intent.createChooser(send, getString(R.string.settings_share))) }
        }
    }

    private fun currentLanguageLabel(): String = when (AppLanguage.currentTag()) {
        "en" -> getString(R.string.language_en)
        "tr" -> getString(R.string.language_tr)
        "ku" -> getString(R.string.language_ku)
        else -> getString(R.string.language_ar)
    }

    private fun openHistory(item: PriceItem) {
        startActivity(Intent(this, HistoryActivity::class.java).apply {
            putExtra(HistoryActivity.EXTRA_KEY, item.cityKey ?: item.name)
            putExtra(HistoryActivity.EXTRA_NAME, item.name)
            putExtra(HistoryActivity.EXTRA_FLAG, item.flagDrawable)
            putExtra(HistoryActivity.EXTRA_UNIT, item.unit)
        })
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

    private fun openAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.drawer_open_enter, R.anim.drawer_open_exit)
        }
    }

    /**
     * شريط التحديث المضغوط (بنفس عرض البانر): العنوان يسار، وعلى اليمين
     * التاريخ والوقت (22/9/2026 - 17:36) ثم خط فاصل ثم أيقونة الساعة.
     */
    private fun buildUpdateStrip(label: String, horizontalMargin: Int = 0): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(ContextCompat.getColor(this@MainActivity, R.color.headerBlue), 0)
            setPadding(dp(12), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(-1, dp(32)).apply {
                topMargin = dp(4); bottomMargin = dp(4); marginStart = horizontalMargin; marginEnd = horizontalMargin
            }
        }
        strip.addView(text(label, 12, Color.WHITE, true).apply {
            gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val stamp = lastUpdateTime?.let { UpdateTimeFormat.stamp(it) } ?: getString(R.string.last_update_none)
        strip.addView(text(stamp, 11, 0xFFE5EEFC.toInt(), true).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            maxLines = 1
        })
        strip.addView(View(this).apply { setBackgroundColor(0x66FFFFFF) },
            LinearLayout.LayoutParams(dp(1), dp(16)).apply { marginStart = dp(8); marginEnd = dp(8) })
        strip.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_clock_outline)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(18), dp(18)))
        return strip
    }

    private fun emptyCard(title: String, subtitle: String): View = vertical().apply {
        background = rounded(surfaceColor(), 16)
        setPadding(dp(15), dp(15), dp(15), dp(15))
        addView(text(title, 14, textPrimary(), true))
        addView(text(subtitle, 12, textSecondary(), false).apply { setPadding(0, dp(4), 0, 0) })
    }

    private fun separator(): View = View(this).apply {
        setBackgroundColor(0x333F8DC9)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(3); bottomMargin = dp(3) }
    }

    private fun vertical(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun text(value: String, size: Int, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = if (bold) AppFonts.bold(this@MainActivity) else AppFonts.regular(this@MainActivity)
        includeFontPadding = false
    }

    /** radiusDp = 0 يعني التقويس الموحد (corner_radius)؛ أرقام كبيرة (دوائر/أقراص) تبقى كما هي. */
    private fun rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = if (radiusDp >= 30) dp(radiusDp).toFloat() else cornerPx
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? =
        android.util.TypedValue().let { value ->
            theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            ContextCompat.getDrawable(this, value.resourceId)
        }

    private fun surfaceColor(): Int = ContextCompat.getColor(this, R.color.rowFlatBg)
    private fun textPrimary(): Int = ContextCompat.getColor(this, R.color.textPrimary)
    private fun textSecondary(): Int = ContextCompat.getColor(this, R.color.textSecondary)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun formatNoticeDate(millis: Long): String =
        if (millis > 0L) UpdateTimeFormat.stamp(millis) else "—"
}

private object NewsImageLoader {
    private val executor = Executors.newFixedThreadPool(2)
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(12) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount / 1024
    }
    private val main = Handler(Looper.getMainLooper())

    fun load(url: String, callback: (android.graphics.Bitmap?) -> Unit) {
        cache.get(url)?.let { callback(it); return }
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                try { connection.inputStream.use { BitmapFactory.decodeStream(it) } }
                finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) cache.put(url, bitmap)
            main.post { callback(bitmap) }
        }
    }
}

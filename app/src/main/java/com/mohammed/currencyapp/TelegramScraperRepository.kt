package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * يجيب كل أسعار المدن (وسعر التومان الإيراني) من مصدر واحد فقط: قناتنا
 * الخاصة "بورصة ديناري" (https://t.me/s/BorsatDinari)، اللي ينشر فيها
 * بوتنا (Dinari_Telegram_Bot) ملخص جاهز لكل الأسعار كل فترة عبر GitHub
 * Actions — هو المسؤول عن تجميع البيانات من مصادرها الأصلية، والتطبيق هنا
 * يكتفي بقراءة آخر منشور بهذي القناة وتحليل أرقامه مباشرة، بدون أي اتصال
 * لأي قناة أخرى. نعتمد كل رسالة بالقناة كما هي، بغض النظر عن أي دائرة
 * زرقاء أو حمراء بآخرها.
 *
 * صيغة منشور البوت (سطر لكل مدينة، ثم قسم العملات العالمية):
 *   🇮🇶 أسعار صرف 100$ مقابل الدينار العراقي
 *   🟢 155,650 د.ع | بغداد
 *   ...
 *   ━━━━━━━━━━━━━━━━━━━━
 *   العملات العالمية
 *   🟢 100 دولار = 155,650 د.ع (السعر الرسمي)
 *   🟢 100 يورو = 108.23 دولار
 *   🟢 100 پاوند = 126.45 دولار
 *   🟢 100 دولار = 4,859 ليرة تركية
 *   🟢 100 دولار = 23,600,000 تومان
 *   🟢 100 دولار = 71.00 دينار أردني
 *   🟢 100 دولار = 367.30 درهم إماراتي
     *   🟢 100 دولار = 31.09 دينار كويتي
     *   🟢 100 دولار = 5,391 جنيه مصري
     *   🟢 100 دولار = 375.00 ريال سعودي
     *   🟢 100 دولار = 12,188 ليرة سورية
 */
object TelegramScraperRepository {

    private const val BORSAT_DINARI_CHANNEL = "BorsatDinari"
    // إذا مرت 3 ساعات وأكثر بدون ما نحصل فعلياً على سعر جديد صالح لمدينة
    // معينة، نعتبرها "بدون تحديث حالياً" ونلوّنها رصاصي بدل الأخضر/الأحمر،
    // بغض النظر عن آخر اتجاه معروف.
    private const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor(BackgroundThreads.factory("tg-scraper"))
    private val requestTimeoutExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "tg-request-timeout").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class CityPattern(
        val key: String,
        val nameRes: Int,
        val flag: String,
        // اسم المدينة كما يكتبه بوتنا بمنشوره (قد يكون بأكثر من صيغة).
        val keywords: List<String>
    )

    data class CurrencyTelegramRates(
        val irrPer100Usd: Long?
    )

    /** بيانات وصفية بسيطة (اسم + علم) لأي مدينة — تُستخدم بالودجت لعرض
     * الاسم/العلم الصحيحين حتى قبل ما يتوفر أي سعر بالكاش. */
    data class CityMeta(val key: String, val nameRes: Int, val flag: String) {
        fun name(context: Context): String = context.getString(nameRes)
    }

    fun metaFor(cityKey: String): CityMeta? =
        cityPatterns.find { it.key == cityKey }?.let { CityMeta(it.key, it.nameRes, it.flag) }

    private val cityPatterns = listOf(
        CityPattern("baghdad", R.string.city_baghdad, "iq", listOf("بغداد")),
        CityPattern("basra", R.string.city_basra, "iq", listOf("البصرة", "بصرة")),
        CityPattern("erbil", R.string.city_erbil, "kurdistan", listOf("أربيل", "اربيل", "هەولێر")),
        CityPattern("kirkuk", R.string.city_kirkuk, "kurdistan", listOf("كركوك")),
        CityPattern("sulaymaniyah", R.string.city_sulaymaniyah, "kurdistan", listOf("السليمانية", "سليمانية", "سليمانی", "سليماني", "سلێمانی")),
        CityPattern("duhok", R.string.city_duhok, "kurdistan", listOf("دهوك")),
        CityPattern("mosul", R.string.city_mosul, "iq", listOf("الموصل", "موصل")),
        CityPattern("karbala", R.string.city_karbala, "iq", listOf("كربلاء")),
        CityPattern("najaf", R.string.city_najaf, "iq", listOf("النجف", "نجف")),
        // 9 محافظات جديدة ضافتها القناة — كلهن بعلم العراق (iq) نفس بغداد.
        CityPattern("wasit", R.string.city_wasit, "iq", listOf("واسط")),
        CityPattern("muthanna", R.string.city_muthanna, "iq", listOf("المثنى", "مثنى")),
        CityPattern("salahaldin", R.string.city_salahaldin, "iq", listOf("صلاح الدين")),
        CityPattern("diyala", R.string.city_diyala, "iq", listOf("ديالى", "ديالي")),
        CityPattern("dhiqar", R.string.city_dhiqar, "iq", listOf("ذي قار")),
        CityPattern("qadisiyyah", R.string.city_qadisiyyah, "iq", listOf("القادسية", "قادسية")),
        CityPattern("babil", R.string.city_babil, "iq", listOf("بابل")),
        CityPattern("maysan", R.string.city_maysan, "iq", listOf("ميسان")),
        CityPattern("anbar", R.string.city_anbar, "iq", listOf("الأنبار", "أنبار")),
    )

    /** يجيب سعر التومان الإيراني من آخر منشور بقناة بورصة ديناري. */
    fun fetchCurrencyRatesBlocking(): CurrencyTelegramRates =
        CurrencyTelegramRates(extractTomanFromMessages(fetchTrustedMessagesShared()))

    /** يستخرج سعر التومان (لكل 100$) من قائمة رسائل جاهزة — نفس منطق
     * fetchCurrencyRatesBlocking بس يشتغل على رسائل ممرّرة، حتى يشارك نفس
     * طلب الشبكة مع باقي العملات بالتحديث الجماعي (ودجت/تطبيق). */
    internal fun extractTomanFromMessages(messages: List<Pair<String, String>>): Long? {
        var bestTimestamp: String? = null
        var bestValue: Long? = null
        for ((ts, chunk) in messages) {
            if (bestTimestamp != null && ts <= bestTimestamp) continue
            val value = extractBorsatDinariTomanRate(chunk) ?: continue
            bestTimestamp = ts
            bestValue = value
        }
        return bestValue
    }

    internal fun latestTomanTimestamp(messages: List<Pair<String, String>>): Long? {
        var bestTimestamp: String? = null
        for ((ts, chunk) in messages) {
            if (extractBorsatDinariTomanRate(chunk) != null && (bestTimestamp == null || ts > bestTimestamp)) {
                bestTimestamp = ts
            }
        }
        return bestTimestamp?.let { telegramTimestampToMillis(it) }
    }

    /** يرجع كل رسائل قناة بورصة ديناري بدون أي فلترة — تُستخدم هنا لأسعار
     * المدن والتومان، ومن CurrenciesRepository لليورو والپاوند والليرة
     * والسعر الرسمي للدولار. */
    fun fetchTrustedBorsatDinariMessages(): List<Pair<String, String>> {
        val html = try { fetchChannelHtml(BORSAT_DINARI_CHANNEL) } catch (_: Exception) { "" }
        val live = parseMessages(html)
        if (live.isEmpty()) return live
        return fillGap(live)
    }

    private val postIdRegex = Regex("""data-post="[^/"]+/(\d+)"""")
    private fun postId(chunk: String): Long? = postIdRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull()

    private const val GAP_PREFS = "channel_gap"
    private const val GAP_KEY_NEWS = "news_gap"
    private const val GAP_KEY_NEWEST = "last_newest_id"
    private const val GAP_MAX_PAGES = 10 // ~200 منشور

    /**
     * صفحة القناة الحية تعرض آخر ~20 منشور فقط. لو غاب المستخدم وانكتبت منشورات أكثر من هذا،
     * نلف للخلف (?before=) صفحة صفحة لحد ما نوصل آخر منشور شفناه سابقاً، حتى ما تضيع
     * تحديثات العملات الرقمية/المعادن/التنويهات. نتقدم بعلامة "آخر منشور" بس لو نجح الجلب
     * (أو وصلنا الحد الأعلى)، ولو انقطعت الشبكة نبقيها حتى نعيد المحاولة.
     */
    private fun fillGap(live: List<Pair<String, String>>): List<Pair<String, String>> {
        val prefs = DinariApplication.instance.getSharedPreferences(GAP_PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong(GAP_KEY_NEWEST, 0L)
        val liveIds = live.mapNotNull { postId(it.second) }
        if (liveIds.isEmpty()) return live
        val newest = liveIds.maxOrNull()!!
        val collected = ArrayList<Pair<String, String>>(live)
        var oldest = liveIds.minOrNull()!!
        var advance = true
        if (lastSeen > 0L && oldest > lastSeen + 1) {
            var pages = 0
            while (oldest > lastSeen + 1 && pages < GAP_MAX_PAGES) {
                pages++
                val older = try {
                    parseMessages(fetchChannelHtml(BORSAT_DINARI_CHANNEL, beforeId = oldest))
                } catch (_: Exception) { advance = false; break }
                val ids = older.mapNotNull { postId(it.second) }
                if (ids.isEmpty() || ids.minOrNull()!! >= oldest) break
                collected.addAll(older)
                oldest = ids.minOrNull()!!
            }
        }
        // فجوة ما كدر الجلب العادي يغطيها (أكبر من الحد الأقصى): نعلّم حتى تُكمَّل الأخبار ببحث واحد.
        if (advance && lastSeen > 0L && oldest > lastSeen + 1) prefs.edit().putBoolean(GAP_KEY_NEWS, true).apply()
        if (advance && newest > lastSeen) prefs.edit().putLong(GAP_KEY_NEWEST, newest).apply()
        return collected.distinctBy { postId(it.second) ?: it.second.hashCode().toLong() }
            .sortedBy { postId(it.second) ?: 0L }
    }

    /** بحث نصي بالقناة (t.me/s/…?q=) لأنواع نادرة النشر مثل العملات الرقمية والمعادن. */
    internal fun searchChannelMessages(query: String): List<Pair<String, String>> =
        try { parseMessages(fetchChannelHtml(BORSAT_DINARI_CHANNEL, query = query)) } catch (_: Exception) { emptyList() }

    /** آخر سعر رسمي للدولار لكل 100$ من منشور بورصة ديناري. */
    fun extractBorsatDinariOfficialUsd(text: String): Long? {
        val t = normalizeArabicText(text)
        val patterns = listOf(
            Regex("""100\s*دولار[^0-9]{0,30}(?:=|:|\|)\s*([0-9]{2,3},[0-9]{3})\s*د\.ع[^\n]{0,40}(?:السعر\s*الرسمي|الرسمي)""", RegexOption.IGNORE_CASE),
            Regex("""(?:دولار|USD)[^0-9]{0,30}([0-9]{2,3},[0-9]{3})\s*د\.ع[^\n]{0,40}(?:السعر\s*الرسمي|الرسمي)""", RegexOption.IGNORE_CASE),
            Regex("""100\s*دولار[^0-9]{0,30}([0-9]{2,3},[0-9]{3})\s*د\.ع""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.findAll(t).lastOrNull() ?: continue
            val n = m.groupValues[1].replace(",", "").toLongOrNull()
            if (n != null && n in 100_000..999_999) return n
        }
        return null
    }

    /** يرجع قائمة PriceItem لكل المدن، مع الاعتماد على آخر قيمة معروفة (كاش) لو الفحص الحالي فشل،
     * ويحسب اتجاه السعر (أخضر=صعود، أحمر=نزول) بالمقارنة مع آخر سعر محفوظ. */
    fun fetch(context: Context, onResult: (List<PriceItem>) -> Unit) {
        executor.execute {
            val cache = ScraperCache(context)
            val messages = fetchTrustedMessagesShared()
            val items = cityPatterns.map { cp -> resolveCityItem(cp, messages, cache, context) }
            mainHandler.post { onResult(items) }
        }
    }

    /** Fetches once, resolves both main-page groups on a worker, then posts one UI result. */
    fun fetchMainPage(
        context: Context,
        onResult: (exchanges: List<PriceItem>, currencies: List<PriceItem>) -> Unit
    ) {
        executor.execute {
            val messages = fetchTrustedMessagesShared()
            val cache = ScraperCache(context)
            val exchanges = cityPatterns.map { cp -> resolveCityItem(cp, messages, cache, context) }
            val currencies = CurrenciesRepository.fetchItemsFromMessages(context, messages)
            mainHandler.post { onResult(exchanges, currencies) }
        }
    }

    /**
     * نفس منطق fetch() بالضبط، بس لمدينة وحدة فقط بدل كل المدن — تُستخدم من
     * ودجت الشاشة الرئيسية (زر التحديث اليدوي + التحديث التلقائي)
     * حتى ما تحتاج تشتغل على كل القائمة عشان تحدث بورصة وحدة بس.
     */
    fun fetchOne(context: Context, cityKey: String, onResult: (PriceItem?) -> Unit) {
        executor.execute {
            val cp = cityPatterns.find { it.key == cityKey }
            if (cp == null) {
                mainHandler.post { onResult(null) }
                return@execute
            }
            val cache = ScraperCache(context)
            val messages = fetchTrustedMessagesShared()
            val item = resolveCityItem(cp, messages, cache, context)
            mainHandler.post { onResult(item) }
        }
    }

    // الكاش المشترك بين التطبيق والودجت: أول من يحدّث يجيب صفحة القناة مرة وحدة،
    // وأي تحديث ثاني خلال دقيقتين يستخدم نفس النتيجة بدل ما يكرر طلب الشبكة.
    // دقيقتين كافية لأن التطبيق والودجت غالباً يتحدثون بفارق ثوانٍ من نفس
    // الدورة (15 دقيقة)، وقصيرة بما يكفي حتى ما يبين للمستخدم سعر قديم لو
    // حدّث يدوياً بعد دقيقة — والتحديث اليدوي أصلاً يتجاوز الكاش عبر
    // invalidateSharedMessages(). محمي بـ synchronized حتى لو طلبوا بنفس
    // الوقت من خيطين (المستودعين عندهم executor منفصل).
    private var sharedMessages: List<Pair<String, String>> = emptyList()
    private var sharedMessagesAt = 0L
    private const val SHARED_TTL_MS = 120_000L
    private const val ANNOUNCEMENT_TTL_MS = 15 * 60 * 1000L
    private const val RELEASE_TTL_MS = 6 * 60 * 60 * 1000L
    private var announcementCacheAt = 0L
    private var announcementCacheReady = false
    private var announcementCache: Announcement? = null
    private var releaseCacheAt = 0L
    private var releaseCacheReady = false
    private var releaseCache: AppRelease? = null

    // قفلان منفصلان عمداً: stateLock للحالة فقط (لحظي، ما نسوي فيه شبكة أبداً)
    // وfetchLock لتجميع طلبات الشبكة. قبل كان @Synchronized على الكائن كله
    // يمسك القفل طوال طلب الشبكة (لحد 16+ ثانية بشبكة بطيئة)، فأي استدعاء من
    // الخيط الرئيسي (زر التحديث، شريط التنويه، فحص التحديث) كان يتجمد.
    private val stateLock = Any()
    private val fetchLock = Any()

    fun fetchTrustedMessagesShared(): List<Pair<String, String>> {
        readFreshShared()?.let { return it }
        synchronized(fetchLock) {
            // خيط ثاني ممكن يكون جلبها أثناء انتظارنا للقفل.
            readFreshShared()?.let { return it }
            val fresh = fetchTrustedBorsatDinariMessages()
            if (fresh.isNotEmpty()) {
                synchronized(stateLock) {
                    sharedMessages = fresh
                    sharedMessagesAt = android.os.SystemClock.elapsedRealtime()
                }
            }
            return fresh
        }
    }

    private fun readFreshShared(): List<Pair<String, String>>? = synchronized(stateLock) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (sharedMessages.isNotEmpty() && now - sharedMessagesAt < SHARED_TTL_MS) sharedMessages else null
    }

    /** يفرّغ الكاش المشترك — يناديه التحديث اليدوي (سحب للأسفل بالتطبيق أو
     * زر تحديث الودجت) حتى يجيب بيانات حية فعلاً بدل ما يرجع نفس صفحة
     * جلبها قبل دقيقة تحديث تلقائي. */
    fun invalidateSharedMessages() {
        synchronized(stateLock) { sharedMessagesAt = 0L }
    }

    /** يبني PriceItem من الكاش المحلي فقط، بدون أي اتصال شبكة — يُستخدم لعرض
     * فوري بالودجت لحظة إضافتها للشاشة، قبل ما يوصل أي رد شبكة. */
    fun buildCachedItem(context: Context, cityKey: String): PriceItem? {
        val cp = cityPatterns.find { it.key == cityKey } ?: return null
        val cache = ScraperCache(context)
        val digitsOnly = cache.load(cp.key) ?: return null
        val trend = cache.loadTrend(cp.key) ?: Trend.UP
        return PriceItem(context.getString(cp.nameRes), cp.flag, formatIqd(digitsOnly), "100\$", trend, cp.key, HistoryStore.latestTimestamp(context, cp.key) ?: cache.loadUpdateTime(cp.key))
    }

    /** يبني كل أسعار المدن من الكاش المحلي فوراً، بدون انتظار الشبكة. */
    fun buildCachedItems(context: Context): List<PriceItem> =
        cityPatterns.mapNotNull { buildCachedItem(context, it.key) }

    /** يحل سعر مدينة وحدة من رسائل القناة الجاهزة — بدون أي طلب شبكة هنا
     * (الصفحة تجلب مرة وحدة خارجياً ويُمرر نصها لكل المفاتيح). يحفظ بالكاش
     * المشترك ويسجل هيستوري إذا تغيّر السعر، نفس resolveCityItem بالضبط.
     * يُستخدم من تحديث الودجت الجماعي. */
    internal fun resolveBlocking(
        context: Context,
        cityKey: String,
        messages: List<Pair<String, String>>
    ): PriceItem? {
        val cp = cityPatterns.find { it.key == cityKey } ?: return null
        return resolveCityItem(cp, messages, ScraperCache(context), context)
    }

    private fun resolveCityItem(
        cp: CityPattern,
        messages: List<Pair<String, String>>,
        cache: ScraperCache,
        context: Context
    ): PriceItem {
        var bestTimestamp: String? = null
        var bestValue: String? = null
        for ((ts, chunk) in messages) {
            if (bestTimestamp != null && ts <= bestTimestamp) continue
            val value = extractFlexiblePrice(chunk, cp.keywords) ?: continue
            bestTimestamp = ts
            bestValue = value
        }
        val publishedAtMillis = bestTimestamp?.let { telegramTimestampToMillis(it) }

        val extracted = bestValue
        val previous = cache.load(cp.key)
        val digitsOnly = extracted ?: previous

        val trend = if (extracted != null && previous != null) {
            val newVal = extracted.toLongOrNull()
            val oldVal = previous.toLongOrNull()
            when {
                newVal == null || oldVal == null -> cache.loadTrend(cp.key) ?: Trend.UP
                newVal > oldVal -> Trend.UP
                newVal < oldVal -> Trend.DOWN
                else -> cache.loadTrend(cp.key) ?: Trend.UP
            }
        } else {
            cache.loadTrend(cp.key) ?: Trend.UP
        }

        // المهم هو: من متى السعر ثابت على نفس الرقم (مو من متى آخر مرة
        // نجحنا نجيب سعر). القناة قد "تنجح" بإرجاع نفس الرقم القديم كل
        // مرة لأنها بكل بساطة ما نشرت شي جديد، وهذا لازم يُحتسب كـ"بدون
        // تحديث" مهما طالت مدته، لا أن يصفّر العداد كل مرة تنجح فيها القراءة.
        val now = System.currentTimeMillis()
        val valueChanged = extracted != null && extracted != previous
        if (valueChanged) {
            val priceTime = publishedAtMillis ?: now
            cache.saveUpdateTime(cp.key, priceTime)
            HistoryStore.recordAt(context, cp.key, formatIqd(extracted!!), trend, priceTime)
        }
        val lastChangeTime = cache.loadUpdateTime(cp.key)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend

        if (extracted != null) cache.save(cp.key, extracted)
        cache.saveTrend(cp.key, finalTrend)
        val priceText = if (digitsOnly != null) formatIqd(digitsOnly) else "—"
        return PriceItem(
            name = context.getString(cp.nameRes),
            flagDrawable = cp.flag,
            price = priceText,
            unit = "100\$",
            trend = finalTrend,
            cityKey = cp.key,
            updateTimeMillis = HistoryStore.latestTimestamp(context, cp.key) ?: cache.loadUpdateTime(cp.key)
        )
    }

    private fun fetchChannelHtml(
        username: String,
        beforeId: Long? = null,
        query: String? = null
    ): String {
        val params = mutableListOf<String>()
        if (query != null) params += "q=" + java.net.URLEncoder.encode(query, "UTF-8")
        if (beforeId != null) params += "before=$beforeId"
        // كسر التخزين المؤقت بس للصفحة الحالية/الحية (آخر الأسعار، وبحث
        // فحص التحديث) — هذا المحتوى يتغير باستمرار ولازم يوصلنا طازة.
        // صفحات الهيستوري القديمة (beforeId != null) ما نلمسها: محتواها
        // ثابت ما يتغير أبداً، فكسر الكاش هناك يبطّئ الباكفيل من غير أي
        // فايدة (يجبر تليگرام يعيد نفس الصفحة من الأصل كل مرة بدل ما
        // ياخذها من كاش سريع جاهز).
        val isLivePage = beforeId == null
        if (isLivePage) params += "_=" + System.currentTimeMillis()
        val suffix = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        val url = URL("https://t.me/s/$username$suffix")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        if (isLivePage) {
            conn.useCaches = false
            conn.setRequestProperty("Cache-Control", "no-cache, no-store")
            conn.setRequestProperty("Pragma", "no-cache")
        }
        // مهلة إجمالية للطلب: readTimeout وحده يتجدد مع كل بايت يوصل، فشبكة
        // بطيئة تقطّر الرد ممكن تبقي المحمّل (وخيط الجلب الوحيد) عالقاً دقايق.
        // نقطع الاتصال من مجدول مستقل كي لا ينتظر انتهاء read() نفسه.
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000L
        val timeoutTask = requestTimeoutExecutor.schedule(
            { conn.disconnect() },
            20_000L,
            TimeUnit.MILLISECONDS
        )
        val html = try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (true) {
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                        throw java.io.IOException("channel fetch deadline exceeded")
                    }
                    val n = reader.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                        throw java.io.IOException("channel fetch deadline exceeded")
                    }
                }
                sb.toString()
            }
        } finally {
            timeoutTask.cancel(false)
            conn.disconnect()
        }
        return html
    }

    /** آخر إصدار للتطبيق منشور بالقناة: رقم النسخة + رابط المنشور نفسه. */
    data class AppRelease(val version: String, val postUrl: String)

    /** تنويه القناة لعرضه في الشريط العلوي، مع وقت نشره الحقيقي في Telegram. */
    data class Announcement(val text: String, val postId: Long, val publishedAtMillis: Long)

    data class ChannelNotice(
        val id: Long,
        val text: String,
        val publishedAtMillis: Long,
        val imageUrl: String?,
        val postUrl: String
    )

    /** Fetches notices from the already-shared public channel page on a worker thread. */
    fun fetchLatestNotices(onResult: (List<ChannelNotice>) -> Unit) {
        executor.execute {
            val notices = try {
                parseLatestNotices(fetchTrustedMessagesShared())
            } catch (_: Exception) {
                emptyList()
            }
            // الصفحة الحية (مع تعبئة الفجوة) تغطي أغلب الحالات. نضيف بحث "تنويه" بالقناة بس لما نحتاجه:
            // (1) الأرشيف فارغ (أول تشغيل)، أو (2) فجوة أكبر من حد الجلب العادي. وبحد أدنى 30 دقيقة بين بحثين
            // حتى ما نكلف الجهاز والبطارية والشبكة.
            val searched = try {
                val prefs = DinariApplication.instance.getSharedPreferences(GAP_PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                val archiveEmpty = NewsArchiveStore.load(DinariApplication.instance).isEmpty()
                val gap = prefs.getBoolean(GAP_KEY_NEWS, false)
                val cooled = now - prefs.getLong("news_search_at", 0L) > 30 * 60 * 1000L
                if ((archiveEmpty || gap) && cooled) {
                    prefs.edit().putLong("news_search_at", now).apply()
                    val found = parseLatestNotices(searchChannelMessages("تنويه"))
                    if (found.isNotEmpty()) prefs.edit().putBoolean(GAP_KEY_NEWS, false).apply()
                    found
                } else emptyList()
            } catch (_: Exception) { emptyList() }
            mainHandler.post { onResult((notices + searched).distinctBy { it.id }.sortedByDescending { it.publishedAtMillis }) }
        }
    }

    private fun parseLatestNotices(messages: List<Pair<String, String>>): List<ChannelNotice> {
        val idRegex = Regex("""data-post="[^/"]+/(\d+)""")
        val timeRegex = Regex("""<time[^>]*datetime="([^"]+)"""")
        val textRegex = Regex(
            """<div[^>]*class="[^"]*tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
        val photoRegex = Regex("""background-image\s*:\s*url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
        return messages.mapNotNull { (_, chunk) ->
            val id = idRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val rawText = textRegex.find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
            val text = stripHtml(rawText).replace(bidiRegex, "").replace(Regex("\\s+"), " ").trim()
            val noticeIndex = text.indexOf("تنويه")
            if (noticeIndex !in 0..120) return@mapNotNull null
            val displayText = text.substring(noticeIndex + "تنويه".length)
                .replaceFirst(Regex("^[\\s:：،,\\-–—|]+"), "").trim()
                .ifEmpty { text }
            val publishedAt = timeRegex.find(chunk)?.groupValues?.get(1)?.let(::parseIsoToMillis) ?: 0L
            val imageUrl = photoRegex.find(chunk)?.groupValues?.get(1)
                ?.replace("&amp;", "&")
            ChannelNotice(
                id = id,
                text = displayText,
                publishedAtMillis = publishedAt,
                imageUrl = imageUrl,
                postUrl = "https://t.me/$BORSAT_DINARI_CHANNEL/$id"
            )
        }.sortedByDescending { it.publishedAtMillis }
    }

    /**
     * يبحث في نفس رسائل القناة المشتركة عن أحدث منشور يبدأ بكلمة "تنويه".
     * لا يفتح اتصالاً منفصلاً إذا كانت الأسعار قد جلبت الصفحة قبلَه، لأن
     * fetchTrustedMessagesShared() يعيد نفس نتيجة الكاش لمدة دقيقتين.
     */
    fun fetchLatestAnnouncement(onResult: (Announcement?) -> Unit) {
        synchronized(stateLock) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (announcementCacheReady && now - announcementCacheAt < ANNOUNCEMENT_TTL_MS) {
                mainHandler.post { onResult(announcementCache) }
                return
            }
        }
        executor.execute {
            val announcement = try {
                parseLatestAnnouncement(fetchTrustedMessagesShared())
            } catch (_: Exception) {
                null
            }
            synchronized(stateLock) {
                announcementCache = announcement
                announcementCacheAt = android.os.SystemClock.elapsedRealtime()
                announcementCacheReady = true
            }
            mainHandler.post { onResult(announcement) }
        }
    }

    private fun parseLatestAnnouncement(messages: List<Pair<String, String>>): Announcement? {
        val idRegex = Regex("""data-post="[^/"]+/(\d+)""")
        // نأخذ محتوى عنصر نص الرسالة فقط، وليس كامل حاوية Telegram التي
        // تحتوي أحياناً على بيانات داخلية مثل view و TWeb.init.
        val messageTextRegex = Regex(
            """<div[^>]*class="[^"]*tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
        var best: Triple<String, Long, String>? = null
        for ((timestamp, chunk) in messages) {
            val id = idRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val rawMessageText = messageTextRegex.find(chunk)?.groupValues?.get(1) ?: continue
            val text = stripHtml(rawMessageText)
                .replace(bidiRegex, "")
                .replace(Regex("\\s+"), " ")
                .trim()
            // صفحة t.me/s قد تضع اسم القناة قبل نص المنشور، مثل:
            // "بورصة ديناري تنويه: الأسعار تقريبية..."؛ لذلك لا نشترط
            // أن تكون كلمة تنويه أول كلمة حرفياً، بل نبحث عنها قرب البداية.
            val noticeIndex = text.indexOf("تنويه")
            if (noticeIndex !in 0..120) continue
            if (best == null || timestamp > best.first) {
                best = Triple(timestamp, id, text)
            }
        }
        return best?.let { (timestamp, id, text) ->
            val noticeIndex = text.indexOf("تنويه")
            val displayText = text
                .substring(noticeIndex + "تنويه".length)
                .replaceFirst(Regex("^[\\s:：،,\\-–—|]+"), "")
                .trim()
                .ifEmpty { "تنويه" }
            Announcement(displayText, id, parseIsoToMillis(timestamp) ?: 0L)
        }
    }

    /**
     * يدور بالقناة على منشور الـAPK (نصه "تطبيق بورصة ديناري 1.0.7") ويرجع
     * أعلى رقم نسخة لقاه مع رابط منشوره — المقارنة برقم النسخة نفسه مو
     * بتاريخ النشر أو التعديل. نستخدم بحث تليگرام الداخلي (?q=) لأن
     * الأسعار تنزل كمنشورات جديدة كل فترة فمنشور الـAPK يطلع بسرعة من
     * الصفحة الأولى. إذا البحث ما رجع شي نفحص آخر صفحة كاحتياط. أي فشل
     * (نت/تغيير صيغة) يرجع null بصمت.
     */
    fun fetchLatestAppRelease(onResult: (AppRelease?) -> Unit) {
        synchronized(stateLock) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (releaseCacheReady && now - releaseCacheAt < RELEASE_TTL_MS) {
                mainHandler.post { onResult(releaseCache) }
                return
            }
        }
        executor.execute {
            val release = try {
                val fromSearch = parseAppRelease(
                    fetchChannelHtml(BORSAT_DINARI_CHANNEL, query = "تطبيق بورصة ديناري")
                )
                fromSearch ?: parseAppRelease(fetchChannelHtml(BORSAT_DINARI_CHANNEL))
            } catch (_: Exception) {
                null
            }
            synchronized(stateLock) {
                releaseCache = release
                releaseCacheAt = android.os.SystemClock.elapsedRealtime()
                releaseCacheReady = true
            }
            mainHandler.post { onResult(release) }
        }
    }

    private fun parseAppRelease(html: String): AppRelease? {
        val idRegex = Regex("""data-post="[^/"]+/(\d+)"""")
        // الرقم صيغته x.y.z (ثلاث خانات) حتى ما نلخبط مع حجم الملف "4 MB".
        // كابشن المنشورات اختلفت بين الإصدارات ("تطبيق بورصة ديناري 1.0.7"،
        // و"تطبيق بورصة ديناري للاندرويد النسخة الجديدة 1.0.6")، فنقبل
        // كلمات بين العبارة والرقم (بدون أرقام بالوسط)، وبالترتيبين لأن
        // اتجاه النص RTL. اسم الملف المكتوب بشرطات سفلية (تطبيق_بورصة_...)
        // ما يطابق لأن العبارة بالكابشن بمسافات.
        val afterPhrase = Regex("""تطبيق\s+بورصة\s+ديناري[^0-9]{0,80}?(\d+\.\d+\.\d+)""")
        val beforePhrase = Regex("""(\d+\.\d+\.\d+)[^0-9]{0,80}?تطبيق\s+بورصة\s+ديناري""")
        var best: Pair<String, Long>? = null
        for (chunk in splitMessages(html)) {
            val id = idRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val text = normalizeArabicText(chunk).replace(Regex("\\s+"), " ")
            val version = afterPhrase.find(text)?.groupValues?.get(1)
                ?: beforePhrase.find(text)?.groupValues?.get(1)
                ?: continue
            if (best == null || compareVersions(version, best.first) > 0) best = version to id
        }
        return best?.let { AppRelease(it.first, "https://t.me/$BORSAT_DINARI_CHANNEL/${it.second}") }
    }

    /** مقارنة رقمية خانة بخانة (1.0.10 أكبر من 1.0.9). يرجع موجب لو a أحدث. */
    fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }

    private data class ChannelMessage(val id: Long, val timestampMillis: Long, val raw: String)

    /** يحول توقيت تليگرام (data-post وdatetime) إلى ملي ثانية، ويقرأ رقم
     * المنشور (id) من data-post="channel/id" حتى نكدر نلف للخلف بصفحات
     * أقدم عبر ?before=<id>. متوافق مع minSdk 21 (بدون java.time). */
    private fun parseChannelMessages(html: String): List<ChannelMessage> {
        val idRegex = Regex("""data-post="[^/"]+/(\d+)"""")
        val timeRegex = Regex("""<time[^>]*datetime="([^"]+)"""")
        return splitMessages(html).mapNotNull { chunk ->
            val id = idRegex.find(chunk)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
            val isoTs = timeRegex.find(chunk)?.groupValues?.get(1) ?: return@mapNotNull null
            val millis = parseIsoToMillis(isoTs) ?: return@mapNotNull null
            ChannelMessage(id, millis, chunk)
        }
    }

    internal fun telegramTimestampToMillis(iso: String): Long? = parseIsoToMillis(iso)

    private fun parseIsoToMillis(iso: String): Long? {
        return try {
            // "2026-09-20T13:38:00+00:00" -> "2026-09-20T13:38:00+0000" (صيغة
            // RFC-822 اللي يدعمها حرف Z من SimpleDateFormat بكل مستويات API).
            val normalized = if (iso.length >= 6 && iso[iso.length - 3] == ':') {
                iso.substring(0, iso.length - 3) + iso.substring(iso.length - 2)
            } else iso
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", java.util.Locale.US)
            fmt.parse(normalized)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** نفس صيغة رقم مضبب بفواصل بدون أي وحدة/لاحقة — تُستخدم لتاريخ الدولار
     * الرسمي والليرة والتومان (نفس منطق CurrenciesRepository.formatLong،
     * مكرر هنا محلياً لأنه private بالملف الثاني). */
    private fun formatLongPlain(value: Long): String =
        String.format(java.util.Locale.US, "%,d", value)

    /** محلل تاريخي لكل عملة عالمية (غير المدن): يرجع نص السعر بنفس شكله
     * المعروض بالضبط (نفس صيغة CurrenciesRepository)، حتى تنسجم النقاط
     * القديمة المجلوبة من القناة مع النقاط العادية المسجّلة أول بأول. */
    private data class CurrencyHistoryDef(val key: String, val extract: (String) -> String?)

    private val currencyHistoryDefs: List<CurrencyHistoryDef> = listOf(
        CurrencyHistoryDef("USD_OFFICIAL") { chunk ->
            extractBorsatDinariOfficialUsd(chunk)?.let { "${formatLongPlain(it)} د.ع" }
        },
        CurrencyHistoryDef("TRY_INVESTING") { chunk ->
            extractBorsatDinariTry(chunk)?.let { formatLongPlain(it) }
        },
        CurrencyHistoryDef("IRR_TELEGRAM") { chunk ->
            extractBorsatDinariTomanRate(chunk)?.let { formatLongPlain(it) }
        },
        CurrencyHistoryDef("FX_EUR") { chunk ->
            extractBorsatDinariDollarBased(chunk, "يورو")
                ?.let { "$ ${String.format(java.util.Locale.US, "%.2f", it)}" }
        },
        CurrencyHistoryDef("FX_GBP") { chunk ->
            extractBorsatDinariDollarBased(chunk, "پاوند")
                ?.let { "$ ${String.format(java.util.Locale.US, "%.2f", it)}" }
        },
        CurrencyHistoryDef("FX_JOD") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("دينار أردني", "دينار اردني"))?.let { formatCurrencyRate(it, 2) }
        },
        CurrencyHistoryDef("FX_AED") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("درهم إماراتي", "درهم اماراتي"))?.let { formatCurrencyRate(it, 2) }
        },
        CurrencyHistoryDef("FX_KWD") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("دينار كويتي"))?.let { formatCurrencyRate(it, 2) }
        },
        CurrencyHistoryDef("FX_EGP") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("جنيه مصري"))?.let { formatCurrencyRate(it, 0) }
        },
        CurrencyHistoryDef("FX_SAR") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("ريال سعودي", "ريال سعودى"))?.let { formatCurrencyRate(it, 2) }
        },
        CurrencyHistoryDef("FX_CNY") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("يوان صيني", "يوان"))?.let { formatCurrencyRate(it, 2) }
        },
        CurrencyHistoryDef("FX_SYP") { chunk ->
            extractBorsatDinariDollarToCurrency(chunk, listOf("ليرة سورية", "ليرة سوريه", "ليرة سوريّة"))?.let { formatCurrencyRate(it, 0) }
        }
    )

    /** منشور واحد من القناة بعد استخراج سعر كل مدينة وعملة منه (key → نص السعر
     * بنفس صيغة العرض). تستخدمه HistorySync لبناء سجل الأسعار. */
    internal data class HistoryMessage(
        val id: Long,
        val timestampMillis: Long,
        val prices: Map<String, String>
    )

    /**
     * يجيب صفحة وحدة من القناة (أحدث ~20 منشور قبل [beforeId]، أو آخر صفحة لو
     * null) ويستخرج من كل منشور أسعار كل المدن والعملات مرة وحدة. يشتغل على الخيط
     * اللي ينادي عليه (HistorySync ينادي عليه من عدة خيوط بالتوازي) ويرمي
     * استثناء لو فشل الاتصال.
     */
    internal fun fetchHistoryPage(beforeId: Long?): List<HistoryMessage> {
        val html = fetchChannelHtml(BORSAT_DINARI_CHANNEL, beforeId)
        return parseChannelMessages(html).map { m ->
            HistoryMessage(m.id, m.timestampMillis, extractAllPrices(m.raw))
        }
    }

    /** يستخرج أسعار كل المدن والعملات من نص منشور واحد. التطبيع (إزالة HTML
     * وتوحيد الأرقام) يتم مرة وحدة للمنشور، مو لكل مدينة. */
    private fun extractAllPrices(raw: String): Map<String, String> {
        val out = HashMap<String, String>()
        val normalized = normalizeArabicText(raw)
        for (cp in cityPatterns) {
            extractFlexiblePriceNormalized(normalized, cp.keywords)?.let { out[cp.key] = formatIqd(it) }
        }
        for (def in currencyHistoryDefs) {
            def.extract(normalized)?.let { out[def.key] = it }
        }
        val plain = plainMessageText(raw)
        CryptoRepository.parseRawPrices(plain).forEach { (code, v) -> out["CRYPTO_$code"] = "$ $v" }
        MetalsRepository.parseRawPrices(plain).forEach { (id, v) -> out["METAL_$id"] = "$ $v" }
        return out
    }

    /**
     * يقسم صفحة القناة إلى رسائلها المنفردة، ويرجع لكل رسالة (توقيتها، نصها
     * الخام). نعتمد على خاصية data-post="channel/id" اللي يضعها تليجرام
     * ببداية كل حاوية رسالة كنقطة تقسيم — أوثق من محاولة مطابقة إغلاق كل
     * وسم HTML متداخل بالگريغز.
     */
    private fun splitMessages(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val marker = Regex("""<div[^>]*\sdata-post="[^"]*"""")
        val starts = marker.findAll(html).map { it.range.first }.toList()
        if (starts.isEmpty()) return listOf(html)
        return starts.indices.map { i ->
            val begin = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else html.length
            html.substring(begin, end)
        }
    }

    /**
     * يرجع لكل رسالة توقيتها (من خاصية datetime، صيغة ISO-8601 موحدة
     * المنطقة الزمنية دوماً بصفحات t.me/s/) مع نصها الخام، لمقارنة الأحدث
     * فعلياً بين رسائل القناة بمقارنة نصية بسيطة للتوقيت.
     */
    private fun parseMessages(html: String): List<Pair<String, String>> {
        return splitMessages(html).mapNotNull { chunk ->
            val ts = Regex("""<time[^>]*datetime="([^"]+)"""").find(chunk)?.groupValues?.get(1)
            ts?.let { it to chunk }
        }
    }

    /**
     * استخراج عام مرن يدور على اسم المدينة قريب من رقم بصيغة "XXX,XXX"،
     * بالاتجاهين (المدينة قبل الرقم أو بعده)، ضمن نافذة صغيرة حتى ما يقفز
     * لمدينة ثانية بنفس الرسالة. تُستخدم مع صيغة منشور بورصة ديناري:
     * "155,650 د.ع | بغداد".
     */
    private fun extractFlexiblePrice(text: String, keywords: List<String>): String? {
        if (text.isBlank()) return null
        return extractFlexiblePriceNormalized(normalizeArabicText(text), keywords)
    }

    // الگريغز تنبني مرة وحدة لكل كلمة مفتاحية (بدل ما تنبني من جديد لكل منشور
    // ولكل مدينة — كانت أكبر سبب لبطء جلب السجل القديم).
    private val flexRegexCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Regex, Regex>>()

    private fun flexRegexes(keyword: String): Pair<Regex, Regex> = flexRegexCache.getOrPut(keyword) {
        val escaped = Regex.escape(keyword)
        Regex("""$escaped[^\n]{0,35}?([0-9]{2,3},[0-9]{3})""", RegexOption.IGNORE_CASE) to
            Regex("""([0-9]{2,3},[0-9]{3})[^\n]{0,35}?$escaped""", RegexOption.IGNORE_CASE)
    }

    private fun extractFlexiblePriceNormalized(normalized: String, keywords: List<String>): String? {
        for (keyword in keywords) {
            val (cityFirst, numberFirst) = flexRegexes(keyword)

            val m1 = cityFirst.findAll(normalized).lastOrNull()
            if (m1 != null) {
                val candidate = m1.groupValues[1].replace(",", "")
                if (candidate.toLongOrNull()?.let { it >= 10000 } == true) return candidate
            }

            // بورصة ديناري تكتب الرقم قبل اسم المدينة: "155,650 د.ع | بغداد".
            val m2 = numberFirst.findAll(normalized).lastOrNull()
            if (m2 != null) {
                val candidate = m2.groupValues[1].replace(",", "")
                if (candidate.toLongOrNull()?.let { it >= 10000 } == true) return candidate
            }
        }
        return null
    }

    /** تُستخدم مع منشور بورصة ديناري: "100 دولار = 23,600,000 تومان" —
     * رقم مباشر بنفس المقياس النهائي، بدون حاجة لضرب ×1000. */
    private fun extractBorsatDinariTomanRate(text: String): Long? {
        val t = normalizeArabicText(text)
        val regex = Regex(
            """100\s*دولار[^0-9]{0,15}([0-9]{2,3},[0-9]{3},[0-9]{3})\s*تومان""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.findAll(t).lastOrNull() ?: return null
        return normalizeDigits(match.groupValues[1])?.toLongOrNull()
    }

    /**
     * يستخرج من منشور بورصة ديناري سعر عملة "دولارية القاعدة" (يورو/پاوند)
     * بصيغة "100 يورو = 116.18 دولار" — يرجع الرقم العشري مباشرة (دولار
     * لكل 100 وحدة)، بنفس مقياس CurrenciesRepository تماماً.
     */
    fun extractBorsatDinariDollarBased(text: String, currencyName: String): Double? {
        val t = normalizeArabicText(text)
        val escaped = Regex.escape(currencyName)
        val regex = Regex(
            """100\s*$escaped[^0-9]{0,10}([0-9]{2,3}\.[0-9]{1,2})\s*دولار""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.findAll(t).lastOrNull() ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /** يستخرج قيمة العملة المحلية لكل 100 دولار، مثل
     * "100 دولار = 71.00 دينار أردني" أو "5,391 جنيه مصري". */
    fun extractBorsatDinariDollarToCurrency(text: String, currencyNames: List<String>): Double? {
        if (currencyNames.isEmpty()) return null
        val t = normalizeArabicText(text)
        val names = currencyNames.joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            """100\s*دولار[^0-9]{0,20}([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(?:$names)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.findAll(t).lastOrNull() ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    private fun formatCurrencyRate(value: Double, fractionDigits: Int): String =
        String.format(java.util.Locale.US, "%,.${fractionDigits}f", value)

    /**
     * يستخرج من منشور بورصة ديناري سعر الليرة التركية فقط: "100 دولار = 4,859
     * ليرة تركية". اشتراط كلمة "تركية" يمنع التقاط سعر الليرة السورية.
     */
    fun extractBorsatDinariTry(text: String): Long? {
        val t = normalizeArabicText(text)
        val regex = Regex(
            """100\s*دولار[^0-9]{0,15}([0-9]{1,3},[0-9]{3})\s*(?:ليرة\s+تركية|الليرة\s+التركية)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.findAll(t).lastOrNull() ?: return null
        return normalizeDigits(match.groupValues[1])?.toLongOrNull()
    }

    /** نص الرسالة الصافي (أسطر محفوظة، أرقام لاتينية) لاستعماله بمحلل العملات الرقمية. */
    internal fun plainMessageText(chunk: String): String {
        // كل كتلة الرسالة (حتى لو النص داخل <pre><code>)، مع توحيد المسافات الخاصة ورمز الدولار.
        return normalizeArabicText(chunk)
            .replace('\u00a0', ' ').replace('\u202f', ' ').replace('\u2009', ' ')
            .replace("&#036;", "$").replace("&#36;", "$")
    }

    private val replyPreviewRegex = Regex("""tgme_widget_message_reply[\s\S]*?(?=js-message_text)""")
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val blockEndRegex = Regex("""</p>|</div>|</span>|</a>""", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex("<[^>]+>")
    private val bidiRegex = Regex("[\u200B-\u200F\u202A-\u202E\u2060\uFEFF]")

    private fun stripHtml(text: String): String {
        // نشيل صندوق "الرد على رسالة قديمة" (Reply Preview) قبل أي شي، حتى ما
        // نقرا غلط أرقام قديمة من رسالة ماضية. صندوق الرد يبدأ بعلامة
        // "tgme_widget_message_reply" وينتهي عملياً عند بداية نص الرسالة
        // الفعلية الجديدة، المعلّمة دايماً بـ"js-message_text" (مو الرد
        // المقتبس)، فنشيل كل شي بينهم بغض النظر عن تداخل الوسوم بالداخل.
        val withoutReplyPreview = text.replace(replyPreviewRegex, "")
        return withoutReplyPreview
            .replace(brRegex, "\n")
            .replace(blockEndRegex, "\n")
            .replace(tagRegex, " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
    }

    /** إزالة وسوم HTML، محارف التحكم بالاتجاه، وتوحيد الأرقام العربية/الفارسية إلى لاتينية. */
    private fun normalizeArabicText(text: String): String {
        return stripHtml(text)
            .replace(bidiRegex, "")
            .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3')
            .replace('٤', '4').replace('٥', '5').replace('٦', '6').replace('٧', '7')
            .replace('٨', '8').replace('٩', '9')
    }

    private fun normalizeDigits(value: String?): String? {
        if (value == null) return null
        val out = buildString {
            for (c in value) {
                append(
                    when (c) {
                        in '٠'..'٩' -> ('0'.code + (c.code - '٠'.code)).toChar()
                        in '۰'..'۹' -> ('0'.code + (c.code - '۰'.code)).toChar()
                        else -> c
                    }
                )
            }
        }
        return out.replace(",", "").replace(".", "").trim().ifEmpty { null }
    }

    private fun formatIqd(digitsOnly: String): String {
        val number = digitsOnly.toLongOrNull() ?: return "$digitsOnly د.ع"
        return "${String.format(java.util.Locale.US, "%,d", number)} د.ع"
    }
}

/** كاش بسيط لآخر سعر معروف لكل مدينة (يستخدم إذا فشل الاتصال أو تغير شكل القناة). */
class ScraperCache(private val context: Context) {
    private val prefs = context.getSharedPreferences("scraper_cache", Context.MODE_PRIVATE)

    fun save(cityKey: String, digitsOnly: String) {
        // نحسب ونحفظ مقدار التغيير (الجديد - القديم) لحظة ما السعر يتغير فعلياً،
        // حتى يعرضه التطبيق أسفل السعر (PriceDelta). إذا نفس السعر نترك آخر فرق محفوظ.
        val old = prefs.getString(cityKey, null)?.toLongOrNull()
        val new = digitsOnly.toLongOrNull()
        val editor = prefs.edit().putString(cityKey, digitsOnly)
        if (old != null && new != null && old != new) editor.putLong("${cityKey}_delta", new - old)
        editor.apply()
    }

    /** آخر فرق سعر مسجّل (موجب = صعود، سالب = نزول) أو null لو ما تغيّر بعد. */
    fun loadDelta(cityKey: String): Long? =
        if (prefs.contains("${cityKey}_delta")) prefs.getLong("${cityKey}_delta", 0L) else null

    fun load(cityKey: String): String? = prefs.getString(cityKey, null)

    fun saveTrend(cityKey: String, trend: Trend) {
        prefs.edit().putString("${cityKey}_trend", trend.name).apply()
    }

    fun loadTrend(cityKey: String): Trend? {
        return prefs.getString("${cityKey}_trend", null)?.let {
            runCatching { Trend.valueOf(it) }.getOrNull()
        }
    }

    /** توقيت آخر مرة حصلنا فيها فعلياً على سعر جديد صالح لهذه المدينة (بساعة الجهاز). */
    fun saveUpdateTime(cityKey: String, timeMillis: Long) {
        prefs.edit().putLong("${cityKey}_updated_at", timeMillis).apply()
    }

    fun loadUpdateTime(cityKey: String): Long? {
        return if (prefs.contains("${cityKey}_updated_at")) prefs.getLong("${cityKey}_updated_at", 0L) else null
    }
}

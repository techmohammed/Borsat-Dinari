package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

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
 */
object TelegramScraperRepository {

    private const val BORSAT_DINARI_CHANNEL = "BorsatDinari"
    // إذا مرت 3 ساعات وأكثر بدون ما نحصل فعلياً على سعر جديد صالح لمدينة
    // معينة، نعتبرها "بدون تحديث حالياً" ونلوّنها رصاصي بدل الأخضر/الأحمر،
    // بغض النظر عن آخر اتجاه معروف.
    private const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private data class CityPattern(
        val key: String,
        val name: String,
        val flag: String,
        // اسم المدينة كما يكتبه بوتنا بمنشوره (قد يكون بأكثر من صيغة).
        val keywords: List<String>
    )

    data class CurrencyTelegramRates(
        val irrPer100Usd: Long?
    )

    /** بيانات وصفية بسيطة (اسم + علم) لأي مدينة — تُستخدم بالودجت لعرض
     * الاسم/العلم الصحيحين حتى قبل ما يتوفر أي سعر بالكاش. */
    data class CityMeta(val key: String, val name: String, val flag: String)

    fun metaFor(cityKey: String): CityMeta? =
        cityPatterns.find { it.key == cityKey }?.let { CityMeta(it.key, it.name, it.flag) }

    private val cityPatterns = listOf(
        CityPattern("baghdad", "بغداد", "iq", listOf("بغداد")),
        CityPattern("basra", "بصرة", "iq", listOf("البصرة", "بصرة")),
        CityPattern("erbil", "أربيل", "kurdistan", listOf("أربيل", "اربيل", "هەولێر")),
        CityPattern("kirkuk", "كركوك", "kurdistan", listOf("كركوك")),
        CityPattern("sulaymaniyah", "سليمانية", "kurdistan", listOf("السليمانية", "سليمانية", "سليمانی", "سليماني", "سلێمانی")),
        CityPattern("duhok", "دهوك", "kurdistan", listOf("دهوك")),
        CityPattern("mosul", "الموصل", "iq", listOf("الموصل", "موصل")),
        CityPattern("karbala", "كربلاء", "iq", listOf("كربلاء")),
        CityPattern("najaf", "النجف", "iq", listOf("النجف", "نجف")),
        // 9 محافظات جديدة ضافتها القناة — كلهن بعلم العراق (iq) نفس بغداد.
        CityPattern("wasit", "واسط", "iq", listOf("واسط")),
        CityPattern("muthanna", "المثنى", "iq", listOf("المثنى", "مثنى")),
        CityPattern("salahaldin", "صلاح الدين", "iq", listOf("صلاح الدين")),
        CityPattern("diyala", "ديالى", "iq", listOf("ديالى", "ديالي")),
        CityPattern("dhiqar", "ذي قار", "iq", listOf("ذي قار")),
        CityPattern("qadisiyyah", "القادسية", "iq", listOf("القادسية", "قادسية")),
        CityPattern("babil", "بابل", "iq", listOf("بابل")),
        CityPattern("maysan", "ميسان", "iq", listOf("ميسان")),
        CityPattern("anbar", "الأنبار", "iq", listOf("الأنبار", "أنبار")),
    )

    /** يجيب سعر التومان الإيراني من آخر منشور بقناة بورصة ديناري. */
    fun fetchCurrencyRatesBlocking(): CurrencyTelegramRates {
        val borsatDinariMessages = fetchTrustedBorsatDinariMessages()

        var bestTimestamp: String? = null
        var bestValue: Long? = null
        for ((ts, chunk) in borsatDinariMessages) {
            if (bestTimestamp != null && ts <= bestTimestamp) continue
            val value = extractBorsatDinariTomanRate(chunk) ?: continue
            bestTimestamp = ts
            bestValue = value
        }

        return CurrencyTelegramRates(bestValue)
    }

    /** يرجع كل رسائل قناة بورصة ديناري بدون أي فلترة — تُستخدم هنا لأسعار
     * المدن والتومان، ومن CurrenciesRepository لليورو والپاوند والليرة
     * والسعر الرسمي للدولار. */
    fun fetchTrustedBorsatDinariMessages(): List<Pair<String, String>> {
        val html = try { fetchChannelHtml(BORSAT_DINARI_CHANNEL) } catch (_: Exception) { "" }
        return parseMessages(html)
    }

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
            val messages = fetchTrustedBorsatDinariMessages()
            val items = cityPatterns.map { cp -> resolveCityItem(cp, messages, cache, context) }
            mainHandler.post { onResult(items) }
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
            val messages = fetchTrustedBorsatDinariMessages()
            val item = resolveCityItem(cp, messages, cache, context)
            mainHandler.post { onResult(item) }
        }
    }

    /** يبني PriceItem من الكاش المحلي فقط، بدون أي اتصال شبكة — يُستخدم لعرض
     * فوري بالودجت لحظة إضافتها للشاشة، قبل ما يوصل أي رد شبكة. */
    fun buildCachedItem(context: Context, cityKey: String): PriceItem? {
        val cp = cityPatterns.find { it.key == cityKey } ?: return null
        val cache = ScraperCache(context)
        val digitsOnly = cache.load(cp.key) ?: return null
        val trend = cache.loadTrend(cp.key) ?: Trend.UP
        return PriceItem(cp.name, cp.flag, formatIqd(digitsOnly), "100\$", trend, cp.key)
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
            cache.saveUpdateTime(cp.key, now)
            HistoryStore.record(context, cp.key, formatIqd(extracted!!), trend)
        }
        val lastChangeTime = cache.loadUpdateTime(cp.key)
        val isStale = lastChangeTime == null || (now - lastChangeTime) > STALE_THRESHOLD_MS
        val finalTrend = if (isStale) Trend.FLAT else trend

        if (extracted != null) cache.save(cp.key, extracted)
        cache.saveTrend(cp.key, finalTrend)
        val priceText = if (digitsOnly != null) formatIqd(digitsOnly) else "—"
        return PriceItem(
            name = cp.name,
            flagDrawable = cp.flag,
            price = priceText,
            unit = "100\$",
            trend = finalTrend,
            cityKey = cp.key
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
        val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        conn.disconnect()
        return html
    }

    /** آخر إصدار للتطبيق منشور بالقناة: رقم النسخة + رابط المنشور نفسه. */
    data class AppRelease(val version: String, val postUrl: String)

    /**
     * يدور بالقناة على منشور الـAPK (نصه "تطبيق بورصة ديناري 1.0.7") ويرجع
     * أعلى رقم نسخة لقاه مع رابط منشوره — المقارنة برقم النسخة نفسه مو
     * بتاريخ النشر أو التعديل. نستخدم بحث تليگرام الداخلي (?q=) لأن
     * الأسعار تنزل كمنشورات جديدة كل فترة فمنشور الـAPK يطلع بسرعة من
     * الصفحة الأولى. إذا البحث ما رجع شي نفحص آخر صفحة كاحتياط. أي فشل
     * (نت/تغيير صيغة) يرجع null بصمت.
     */
    fun fetchLatestAppRelease(onResult: (AppRelease?) -> Unit) {
        executor.execute {
            val release = try {
                val fromSearch = parseAppRelease(
                    fetchChannelHtml(BORSAT_DINARI_CHANNEL, query = "تطبيق بورصة ديناري")
                )
                fromSearch ?: parseAppRelease(fetchChannelHtml(BORSAT_DINARI_CHANNEL))
            } catch (_: Exception) {
                null
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
        }
    )

    /**
     * يلف للخلف بصفحات قناة بورصة ديناري (20 منشور تقريباً بكل صفحة) لين
     * يوصل لـ[sinceMillis] أو حد أقصى من الصفحات (حماية من حلقة بلا
     * نهاية)، ويرجع نقاط سعر (توقيت، قيمة) ضمن الفترة [sinceMillis]..
     * [untilMillis] بس، لمدينة أو عملة عالمية وحدة — تُستخدم من صفحة
     * الهيستوري لتعبئة أي فجوة بالسجل المحلي للفترة المحددة حالياً
     * بالتقويم، دون تكرار ما هو موجود أصلاً (التكرار يُفلتر بـ
     * HistoryStore.backfill). يدعم مفتاح أي مدينة من [cityPatterns] أو أي
     * عملة عالمية (الرسمي/يورو/پاوند/ليرة/تومان) — سابقاً كان مقيداً
     * بالمدن فقط فما كان يجيب شي لهذي العملات (كان يرجع دايماً قائمة
     * فاضية بصمت). ملاحظة: اللف للخلف يبدأ دوماً من آخر منشور بالقناة
     * (مهما كان [untilMillis])، لأن صفحات تليگرام تُقرأ من الأحدث للأقدم
     * فقط؛ [untilMillis] يُستخدم فلترة على النتيجة النهائية بس، وهذا مقبول
     * لأن أقصى عمق سجل بالتطبيق أصلاً 60 يوم (RETENTION_DAYS بالهيستوري).
     */
    fun fetchHistoricalPrices(
        cityKey: String,
        sinceMillis: Long,
        untilMillis: Long = Long.MAX_VALUE,
        onProgress: (pagesFetched: Int, oldestReachedMillis: Long) -> Unit,
        isCancelled: () -> Boolean,
        onResult: (List<Pair<Long, String>>) -> Unit
    ) {
        executor.execute {
            val cp = cityPatterns.find { it.key == cityKey }
            val currencyDef = if (cp == null) currencyHistoryDefs.find { it.key == cityKey } else null
            val extractor: ((String) -> String?)? = when {
                cp != null -> { chunk -> extractFlexiblePrice(chunk, cp.keywords)?.let { formatIqd(it) } }
                currencyDef != null -> currencyDef.extract
                else -> null
            }
            val collected = mutableListOf<ChannelMessage>()
            var beforeId: Long? = null
            var lastOldestId: Long? = null
            var pages = 0
            while (extractor != null && !isCancelled() && pages < 400) {
                val html = try { fetchChannelHtml(BORSAT_DINARI_CHANNEL, beforeId) } catch (_: Exception) { "" }
                val page = parseChannelMessages(html)
                if (page.isEmpty()) break
                collected += page
                val oldest = page.minByOrNull { it.id } ?: break
                if (oldest.id == lastOldestId) break
                lastOldestId = oldest.id
                pages++
                mainHandler.post { onProgress(pages, oldest.timestampMillis) }
                if (oldest.timestampMillis <= sinceMillis) break
                beforeId = oldest.id
            }
            val points = if (extractor != null) {
                collected.asSequence()
                    .filter { it.timestampMillis in sinceMillis..untilMillis }
                    .sortedBy { it.timestampMillis }
                    .mapNotNull { m -> extractor(m.raw)?.let { m.timestampMillis to it } }
                    .toList()
            } else emptyList()
            mainHandler.post { onResult(points) }
        }
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
        val normalized = normalizeArabicText(text)

        for (keyword in keywords) {
            val escaped = Regex.escape(keyword)

            val cityFirst = Regex(
                """$escaped[^\n]{0,35}?([0-9]{2,3},[0-9]{3})""",
                RegexOption.IGNORE_CASE
            )
            val m1 = cityFirst.findAll(normalized).lastOrNull()
            if (m1 != null) {
                val candidate = m1.groupValues[1].replace(",", "")
                if (candidate.toLongOrNull()?.let { it >= 10000 } == true) return candidate
            }

            // بورصة ديناري تكتب الرقم قبل اسم المدينة: "155,650 د.ع | بغداد".
            val numberFirst = Regex(
                """([0-9]{2,3},[0-9]{3})[^\n]{0,35}?$escaped""",
                RegexOption.IGNORE_CASE
            )
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

    /**
     * يستخرج من منشور بورصة ديناري سعر الليرة التركية: "100 دولار = 4,859
     * ليرة تركية" — رقم مباشر بمقياس "ليرة لكل 100 دولار".
     */
    fun extractBorsatDinariTry(text: String): Long? {
        val t = normalizeArabicText(text)
        val regex = Regex(
            """100\s*دولار[^0-9]{0,15}([0-9]{1,3},[0-9]{3})\s*ليرة""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.findAll(t).lastOrNull() ?: return null
        return normalizeDigits(match.groupValues[1])?.toLongOrNull()
    }

    private fun stripHtml(text: String): String {
        // نشيل صندوق "الرد على رسالة قديمة" (Reply Preview) قبل أي شي، حتى ما
        // نقرا غلط أرقام قديمة من رسالة ماضية. صندوق الرد يبدأ بعلامة
        // "tgme_widget_message_reply" وينتهي عملياً عند بداية نص الرسالة
        // الفعلية الجديدة، المعلّمة دايماً بـ"js-message_text" (مو الرد
        // المقتبس)، فنشيل كل شي بينهم بغض النظر عن تداخل الوسوم بالداخل.
        val withoutReplyPreview = text.replace(
            Regex("""tgme_widget_message_reply[\s\S]*?(?=js-message_text)"""),
            ""
        )
        return withoutReplyPreview
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""</p>|</div>|</span>|</a>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
    }

    /** إزالة وسوم HTML، محارف التحكم بالاتجاه، وتوحيد الأرقام العربية/الفارسية إلى لاتينية. */
    private fun normalizeArabicText(text: String): String {
        return stripHtml(text)
            .replace(Regex("[\u200B-\u200F\u202A-\u202E\u2060\uFEFF]"), "")
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
        prefs.edit().putString(cityKey, digitsOnly).apply()
    }

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

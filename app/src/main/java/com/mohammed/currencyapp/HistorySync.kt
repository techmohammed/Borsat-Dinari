package com.mohammed.currencyapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * مزامنة سجل الأسعار من قناة بورصة ديناري — مصممة تكون فورية بعد أول مرة:
 *
 *  1. **كل المدن والعملات دفعة وحدة**: كل منشور بالقناة فيه أسعار كل المدن
 *     والعملات، فنقرأ كل صفحة مرة وحدة ونستخرج منها كل شي ونحفظه. (قبل، كل
 *     مدينة كانت تعيد جلب نفس الصفحات من الصفر.)
 *  2. **صفحات بالتوازي**: أرقام منشورات تليگرام متسلسلة، فنقدر نطلب عدة
 *     صفحات متباعدة بنفس الوقت (?before=N، N-20، N-40…) بدل صفحة صفحة. إذا
 *     طلعت فجوة بين الصفحات (منشورات محذوفة مثلاً) نرجع نكمل من آخر نقطة
 *     متصلة، فما يضيع منشور.
 *  3. **تراكمي**: نحفظ الفترة اللي غطيناها (أقدم/أحدث منشور). المرات الجاية
 *     نجلب بس المنشورات الجديدة (غالباً صفحة وحدة) وبس الجزء الأقدم لو
 *     المستخدم طلب فترة أطول (أسبوع ثم شهر ثم شهرين) — ما نعيد جلب شي موجود.
 *  4. **حفظ دفعات + عرض تدريجي**: الكتابة بعملية وحدة لكل دفعة (بدل كتابة كل
 *     نقطة لحالها)، ونخبر الشاشة بعد كل دفعة حتى تظهر البيانات أول بأول
 *     (الأحدث أولاً: اليوم يظهر خلال ثواني، بعدين الأسبوع، وهكذا).
 *
 * الجلب غير مدمّر: ما نمسح شي من السجل المحلي إلا مع حفظ بدائله من القناة
 * بنفس العملية، وإذا فشل الجلب بالنص نحتفظ بالجزء المتصل اللي وصلنا له.
 */
object HistorySync {

    interface Listener {
        /** انحفظت دفعة جديدة بالسجل — حدّث العرض. (على الخيط الرئيسي) */
        fun onDataChanged()
        /** خلصت المزامنة (نجحت أو توقفت). (على الخيط الرئيسي) */
        fun onFinished()
    }

    private const val PREFS = "history_sync"
    private const val WAVE = 6                       // عدد الصفحات المطلوبة بنفس الوقت
    private const val COMMIT_EVERY = 160             // نحفظ كل ~160 منشور (ثواني قليلة بكل دفعة)
    private const val MAX_PAGES = 450
    private const val TAIL_FRESH_MS = 45_000L        // لا نعيد فحص "الجديد" لو انفحص قبل أقل من هالمدة
    private const val RETENTION_MS = 60L * 24 * 60 * 60 * 1000

    private val coordinator = Executors.newSingleThreadExecutor()
    private val pool = Executors.newFixedThreadPool(WAVE)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var wantedSince = Long.MAX_VALUE
    @Volatile private var listener: Listener? = null
    @Volatile private var lastTailSyncAt = 0L

    private data class Coverage(
        val oldestId: Long,
        val oldestTime: Long,
        val newestId: Long,
        val newestTime: Long,
        val reachedStart: Boolean
    )

    // ───────────── واجهة الاستخدام ─────────────

    /** يربط شاشة بالمزامنة؛ يرجع true لو المزامنة شغالة هسه. */
    fun attach(l: Listener): Boolean {
        listener = l
        return running
    }

    fun detach() {
        listener = null
    }

    /** هل السجل المحلي يغطي من [sinceMillis] لليوم وحديث بما فيه الكفاية (بدون أي شبكة)؟ */
    fun isUpToDate(context: Context, sinceMillis: Long): Boolean {
        if (running) return false
        val cov = loadCoverage(context) ?: return false
        val since = max(sinceMillis, System.currentTimeMillis() - RETENTION_MS)
        val coversOlder = since >= cov.oldestTime || cov.reachedStart
        return coversOlder && System.currentTimeMillis() - lastTailSyncAt < TAIL_FRESH_MS
    }

    /**
     * يطلب مزامنة تغطي من [sinceMillis] لحد اليوم. لو في مزامنة شغالة نوسّع
     * هدفها (مثلاً المستخدم انتقل من أسبوع لشهر) بدل ما نبدأ ثانية.
     */
    fun request(context: Context, sinceMillis: Long, l: Listener) {
        listener = l
        val since = max(sinceMillis, System.currentTimeMillis() - RETENTION_MS)
        synchronized(this) {
            if (running) {
                wantedSince = min(wantedSince, since)
                return
            }
            wantedSince = since
            running = true
        }
        val app = context.applicationContext
        coordinator.execute {
            try {
                runSync(app)
            } catch (_: Exception) {
                // أي خطأ غير متوقع: نوقف بهدوء، السجل المحلي يبقى سليم.
            } finally {
                running = false
                mainHandler.post { listener?.onFinished() }
            }
        }
    }

    // ───────────── المنطق الداخلي ─────────────

    private fun runSync(ctx: Context) {
        val cov = loadCoverage(ctx)
        if (cov == null) {
            if (fetchChunk(ctx, startBefore = null, stopId = null, tail = false)) {
                lastTailSyncAt = System.currentTimeMillis()
            }
        } else if (System.currentTimeMillis() - lastTailSyncAt > TAIL_FRESH_MS) {
            if (fetchChunk(ctx, startBefore = null, stopId = cov.newestId, tail = true)) {
                lastTailSyncAt = System.currentTimeMillis()
            }
        }

        // الجزء الأقدم لو المستخدم طلب فترة أطول مما غطيناه (وممكن يتوسع أثناء الجلب).
        var guard = 0
        while (guard++ < 4) {
            val c = loadCoverage(ctx) ?: break
            val needOlder = wantedSince < c.oldestTime && !c.reachedStart
            if (!needOlder) break
            if (!fetchChunk(ctx, startBefore = c.oldestId, stopId = null, tail = false)) break
        }
    }

    private fun fetchPageWithRetry(before: Long?): List<TelegramScraperRepository.HistoryMessage>? {
        for (attempt in 0..1) {
            try {
                return TelegramScraperRepository.fetchHistoryPage(before)
            } catch (_: Exception) {
                if (attempt == 0) Thread.sleep(350)
            }
        }
        return null
    }

    private fun fetchWave(befores: List<Long>): List<List<TelegramScraperRepository.HistoryMessage>?> {
        val futures = befores.map { b -> pool.submit(Callable { fetchPageWithRetry(b) }) }
        return futures.map { f -> try { f.get() } catch (_: Exception) { null } }
    }

    /**
     * يجلب سلسلة صفحات متصلة من القناة بالتوازي ويحفظها.
     *  - [startBefore] = null: يبدأ من آخر منشور؛ غير هيج: يكمل للخلف من هذا الرقم.
     *  - [stopId]: لو موجود (جلب "الجديد" tail) نوقف عند أول منشور مغطّى مسبقاً.
     *  - [tail]: دفعة وحدة تنحفظ بالنهاية بس لو وصلنا للتغطية السابقة (حتى ما تصير فجوة).
     * يرجع true لو خلص بدون فشل شبكة.
     */
    private fun fetchChunk(ctx: Context, startBefore: Long?, stopId: Long?, tail: Boolean): Boolean {
        val seen = HashSet<Long>()
        var pending = ArrayList<TelegramScraperRepository.HistoryMessage>()
        var nextBefore: Long? = startBefore
        var step = 20L
        var pages = 0
        var ok = true
        var done = false
        var reachedStart = false
        var reachedStop = false
        var firstCommit = (startBefore == null)   // الدفعة الأحدث (جديد/أول مرة): تبدّل نقاط التطبيق اللحظية من أول نقطة بالدفعة وللآخر
        val retentionLimit = System.currentTimeMillis() - RETENTION_MS

        fun commit() {
            if (pending.isEmpty()) return
            commitMessages(ctx, pending, replaceToEnd = firstCommit, isTail = tail)
            firstCommit = false
            pending = ArrayList()
        }

        while (!done && ok && pages < MAX_PAGES) {
            val before = nextBefore
            val befores: List<Long> = if (before == null) emptyList()
            else (0 until WAVE).map { before - step * it }.filter { it > 1 }

            val results: List<List<TelegramScraperRepository.HistoryMessage>?> =
                if (before == null) listOf(fetchPageWithRetry(null))
                else if (befores.isEmpty()) listOf(emptyList())
                else fetchWave(befores)

            var newNextBefore = nextBefore
            for ((j, page) in results.withIndex()) {
                if (page == null) { ok = false; break }
                pages++
                if (page.isEmpty()) { reachedStart = true; done = true; break }

                if (before == null && j == 0) step = max(8, page.size).toLong()
                else if (page.size < step) step = max(5, page.size).toLong()

                for (m in page) {
                    if (seen.add(m.id) && (stopId == null || m.id > stopId)) pending.add(m)
                }
                val minMsg = page.minByOrNull { it.id } ?: break
                newNextBefore = minMsg.id

                if (stopId != null && minMsg.id <= stopId) { reachedStop = true; done = true; break }
                val limitTime = if (tail) retentionLimit else max(wantedSince, retentionLimit)
                if (minMsg.timestampMillis <= limitTime) { done = true; break }

                // اتصال الصفحة بالتي بعدها داخل نفس الموجة (وإلا فجوة: نكمل من هنا).
                if (before != null && j + 1 < befores.size && minMsg.id > befores[j + 1]) break
            }
            if (before != null && newNextBefore == before) done = true   // ما تقدمنا: نوقف حتى ما نلف بلا نهاية
            nextBefore = newNextBefore

            if (!tail && pending.size >= COMMIT_EVERY) commit()
        }

        if (tail) {
            // "الجديد": نحفظه بس لو وصلنا للتغطية القديمة بدون فجوة. لو القديمة أقدم من
            // 60 يوم وما وصلناها، نعتبر الجلب هذا تغطية جديدة بالكامل.
            if (reachedStop && ok) {
                commit()
            } else if (ok && done && !reachedStop) {
                resetCoverage(ctx)
                firstCommit = true
                commit()
            } else {
                pending = ArrayList() // فشل بالنص: ما نحفظ شي حتى ما تصير فجوة
            }
        } else {
            commit() // نحفظ الجزء المتصل اللي وصلنا له حتى لو توقف الجلب بالنص
        }

        if (reachedStart && !tail) markReachedStart(ctx)
        return ok
    }

    private fun commitMessages(
        ctx: Context,
        msgs: List<TelegramScraperRepository.HistoryMessage>,
        replaceToEnd: Boolean,
        isTail: Boolean
    ) {
        val sorted = msgs.sortedBy { it.timestampMillis }
        val byKey = HashMap<String, MutableList<Pair<Long, String>>>()
        for (m in sorted) {
            for ((key, price) in m.prices) {
                byKey.getOrPut(key) { ArrayList() }.add(m.timestampMillis to price)
            }
        }
        val tMax = sorted.last().timestampMillis
        HistoryStore.mergeChunk(ctx, byKey, tMax, replaceToEnd)

        val oldest = msgs.minByOrNull { it.id }!!
        val newest = msgs.maxByOrNull { it.id }!!
        val cov = loadCoverage(ctx)
        val updated = when {
            cov == null -> Coverage(oldest.id, oldest.timestampMillis, newest.id, newest.timestampMillis, false)
            isTail -> cov.copy(newestId = max(cov.newestId, newest.id), newestTime = max(cov.newestTime, newest.timestampMillis))
            oldest.id < cov.oldestId -> cov.copy(oldestId = oldest.id, oldestTime = oldest.timestampMillis)
            else -> cov
        }
        saveCoverage(ctx, updated)
        mainHandler.post { listener?.onDataChanged() }
    }

    // ───────────── تخزين الفترة المغطاة ─────────────

    private fun loadCoverage(ctx: Context): Coverage? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("oldest_id")) return null
        return Coverage(
            p.getLong("oldest_id", 0L), p.getLong("oldest_time", 0L),
            p.getLong("newest_id", 0L), p.getLong("newest_time", 0L),
            p.getBoolean("reached_start", false)
        )
    }

    private fun saveCoverage(ctx: Context, c: Coverage) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("oldest_id", c.oldestId).putLong("oldest_time", c.oldestTime)
            .putLong("newest_id", c.newestId).putLong("newest_time", c.newestTime)
            .putBoolean("reached_start", c.reachedStart)
            .apply()
    }

    private fun resetCoverage(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun markReachedStart(ctx: Context) {
        loadCoverage(ctx)?.let { saveCoverage(ctx, it.copy(reachedStart = true)) }
    }
}

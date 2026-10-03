package com.mohammed.currencyapp

import android.os.Process
import java.util.concurrent.ThreadFactory

/** خيوط الشبكة والتحليل بأولوية خلفية: ما تزاحم رسم الشاشة والـ Launcher حتى لو كانت شغالة. */
object BackgroundThreads {
    fun factory(name: String): ThreadFactory = ThreadFactory { task ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            task.run()
        }, name).apply { isDaemon = true }
    }
}

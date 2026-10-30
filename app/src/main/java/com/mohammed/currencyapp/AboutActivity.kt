package com.mohammed.currencyapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mohammed.currencyapp.databinding.ActivityAboutBinding
import kotlin.math.abs

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.drawer_open_enter, R.anim.drawer_open_exit)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.drawer_close_enter, R.anim.drawer_close_exit)
        }
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        KurdishFont.apply(binding.root, this)

        // رقم الإصدار الحقيقي من النظام (يظهر حسب لغة الواجهة حوله).
        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { null }
        binding.tvVersion.text = getString(R.string.about_version_format, versionName ?: "—")
        val language = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale.language
        }
        val isRtl = language == "ar" || language == "ku"
        binding.aboutVersionRow.layoutDirection =
            if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        binding.aboutVersionRow.gravity =
            Gravity.CENTER_VERTICAL or if (isRtl) Gravity.RIGHT else Gravity.LEFT
        applySystemBarsInsets(binding.toolbar, binding.scrollAbout)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        binding.ivHeaderLogo.setOnClickListener { finish() }
        binding.toolbarTitle.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_bold
        )

        checkForUpdate(binding)
    }

    /** يدور بالقناة على أحدث رقم نسخة منشور (التطبيق نفسه ينشر كـAPK
     * بنص "تطبيق بورصة ديناري 1.0.7") ويقارنه برقم نسخة التطبيق الحالية،
     * مو بتاريخ التعديل. أحمر بجانب رقم النسخة: "يوجد تحديث جديد" (رابط
     * لمنشور القناة) لو الرقم أحدث، أو "لديك اخر اصدار" لو نفس الرقم.
     * لو ماكو نت أو فشل الفحص ما يطلع أي شي. */
    private fun checkForUpdate(binding: ActivityAboutBinding) {
        @Suppress("DEPRECATION")
        val current = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: return

        TelegramScraperRepository.fetchLatestAppRelease { release ->
            if (release == null || isFinishing || isDestroyed) return@fetchLatestAppRelease
            val status = binding.tvUpdateStatus
            if (TelegramScraperRepository.compareVersions(release.version, current) > 0) {
                status.text = getString(R.string.update_available)
                status.paintFlags = status.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                status.setOnClickListener { openLink(release.postUrl) }
            } else {
                status.text = getString(R.string.update_latest)
                status.paintFlags = status.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                status.isClickable = false
            }
            status.visibility = View.VISIBLE
        }
    }

    private fun openLink(url: String, action: String = Intent.ACTION_VIEW) {
        try {
            startActivity(Intent(action, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            // ماكو تطبيق يفتح هذا الرابط (مثلاً ماكو تطبيق بريد) — نتجاهل بدل الكراش.
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.drawer_close_enter, R.anim.drawer_close_exit)
        }
    }

    // سحب من اليمين لليسار = يسكّر الصفحة (عكس فتحها).
    private val closeSwipeDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val minDistance = resources.displayMetrics.density * 80f
                if (-dx > minDistance && -dx > abs(dy) * 1.8f && -velocityX > 600f) {
                    finish()
                    return true
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        closeSwipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

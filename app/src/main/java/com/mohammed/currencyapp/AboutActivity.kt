package com.mohammed.currencyapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.mohammed.currencyapp.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    companion object {
        private const val TELEGRAM_CHANNEL_URL = "https://t.me/BorsatDinari"
        private const val X_URL = "https://x.com/Borsatdinari"
        private const val FACEBOOK_URL = "https://www.facebook.com/share/1Bv8F5CUZE/"
        private const val INSTAGRAM_URL = "https://www.instagram.com/borsatdinari"
        private const val WHATSAPP_CHANNEL_URL = "https://whatsapp.com/channel/0029VbDz68h7YSd54EV6rw0R"
        private const val TIKTOK_URL = "https://tiktok.com/@borsatdinari"
        private const val GITHUB_URL = "https://github.com/techmohammed/Borsat-Dinari"
        private const val EMAIL_URI = "mailto:BorsatDinari@gmail.com"
        private const val THREADS_URL = "https://www.threads.com/@borsatdinari"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarsInsets(binding.toolbar, binding.scrollAbout)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // إزالة عنوان التطبيق الصغير من شريط الصفحة، والإبقاء على اسم بورصة ديناري الكبير داخل المحتوى.
        supportActionBar?.title = ""

        // أزرار قسم "تابعنا" أسفل الصفحة.
        binding.btnFollowTelegram.setOnClickListener { openLink(TELEGRAM_CHANNEL_URL) }
        binding.btnFollowX.setOnClickListener { openLink(X_URL) }
        binding.btnFollowFacebook.setOnClickListener { openLink(FACEBOOK_URL) }
        binding.btnFollowInstagram.setOnClickListener { openLink(INSTAGRAM_URL) }
        binding.btnFollowWhatsapp.setOnClickListener { openLink(WHATSAPP_CHANNEL_URL) }
        binding.btnFollowTiktok.setOnClickListener { openLink(TIKTOK_URL) }
        binding.btnFollowGithub.setOnClickListener { openLink(GITHUB_URL) }
        binding.btnFollowEmail.setOnClickListener {
            openLink(EMAIL_URI, Intent.ACTION_SENDTO)
        }
        // F-Droid: بدون رابط حالياً (لسا التطبيق مو منشور هناك)، فبدون click listener عمداً.
        binding.btnFollowThreads.setOnClickListener { openLink(THREADS_URL) }

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
                status.text = "يوجد تحديث جديد"
                status.paintFlags = status.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                status.setOnClickListener { openLink(release.postUrl) }
            } else {
                status.text = "لديك اخر اصدار"
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

package com.mohammed.currencyapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
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
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

package com.mohammed.currencyapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * الصفحات الفرعية للإعدادات (كلها بنفس الهيدر الموحد: أيقونة التطبيق يسار والعنوان بالوسط):
 * شرح التطبيق، شروط الاستخدام، المساعدة والدعم، قنوات التواصل.
 */
class InfoPageActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_GUIDE = "guide"
        const val PAGE_TERMS = "terms"
        const val PAGE_SUPPORT = "support"
        const val PAGE_SOCIAL = "social"
        private const val SUPPORT_EMAIL = "BorsatDinari@gmail.com"

        fun start(context: Context, page: String) {
            context.startActivity(Intent(context, InfoPageActivity::class.java).putExtra(EXTRA_PAGE, page))
        }
    }

    private data class Social(val label: String, val icon: Int, val url: String?)

    private val corner: Float by lazy { resources.getDimension(R.dimen.corner_radius) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun isRtlLang() = AppLanguage.currentTag().let { it == "ar" || it == "ku" }
    private fun startGravity() = if (isRtlLang()) Gravity.RIGHT else Gravity.LEFT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(EXTRA_PAGE) ?: PAGE_GUIDE
        val title = getString(
            when (page) {
                PAGE_TERMS -> R.string.settings_terms
                PAGE_SUPPORT -> R.string.settings_support
                PAGE_SOCIAL -> R.string.settings_social
                else -> R.string.settings_guide
            }
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@InfoPageActivity, R.color.background))
        }
        val header = FrameLayout(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@InfoPageActivity, R.color.brandBlue))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            elevation = dp(2).toFloat()
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = getString(R.string.cd_back)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.LEFT or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(8) })
        header.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 22f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            typeface = AppFonts.bold(this@InfoPageActivity)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            marginStart = dp(56); marginEnd = dp(56)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(56)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(20))
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(content)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        applySystemBarsInsets(header, scroll)

        if (page == PAGE_SOCIAL) {
            root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF050B33.toInt(), 0xFF0B2A8C.toInt()))
        }
        when (page) {
            PAGE_TERMS -> buildTerms(content)
            PAGE_SUPPORT -> buildSupport(content)
            PAGE_SOCIAL -> buildSocial(content)
            else -> buildGuide(content)
        }
        AppFonts.applyTree(root, this)
    }

    // ───── بناء عام ─────

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(this@InfoPageActivity, R.color.cardWhite))
            cornerRadius = corner
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        elevation = dp(2).toFloat()
    }

    private fun body(text: String, bold: Boolean = false, size: Int = 14, secondary: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        gravity = startGravity()
        setLineSpacing(dp(4).toFloat(), 1f)
        setTextColor(ContextCompat.getColor(this@InfoPageActivity, if (secondary) R.color.textSecondary else R.color.textPrimary))
        typeface = if (bold) AppFonts.bold(this@InfoPageActivity) else AppFonts.regular(this@InfoPageActivity)
    }

    private fun section(content: LinearLayout, titleRes: Int, textRes: Int) {
        val c = card()
        c.addView(body(getString(titleRes), bold = true, size = 16).apply {
            setTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.accentBlue))
        })
        c.addView(body(getString(textRes)).apply { setPadding(0, dp(6), 0, 0) })
        content.addView(c)
    }

    private fun buildGuide(content: LinearLayout) {
        section(content, R.string.guide_home_title, R.string.guide_home_text)
        section(content, R.string.guide_widget_title, R.string.guide_widget_text)
        section(content, R.string.guide_colors_title, R.string.guide_colors_text)
        section(content, R.string.guide_update_title, R.string.guide_update_text)
    }

    private fun buildTerms(content: LinearLayout) {
        section(content, R.string.terms_prices_title, R.string.terms_prices_text)
        section(content, R.string.terms_accuracy_title, R.string.terms_accuracy_text)
        section(content, R.string.terms_liability_title, R.string.terms_liability_text)
        section(content, R.string.terms_advice_title, R.string.terms_advice_text)
        section(content, R.string.terms_privacy_title, R.string.terms_privacy_text)
        section(content, R.string.terms_rights_title, R.string.terms_rights_text)
        section(content, R.string.terms_changes_title, R.string.terms_changes_text)
    }

    // ───── المساعدة والدعم ─────

    private fun inputBackground(error: Boolean = false) = GradientDrawable().apply {
        setColor(ContextCompat.getColor(this@InfoPageActivity, R.color.cardWhite))
        cornerRadius = corner
        if (error) setStroke(dp(2), 0xFFE53935.toInt()) else setStroke(dp(1), 0x33808080)
    }

    private fun buildSupport(content: LinearLayout) {
        content.addView(body(getString(R.string.support_intro), secondary = true).apply { setPadding(dp(2), 0, dp(2), dp(10)) })

        // المرسل إليه (ثابت)
        content.addView(body(getString(R.string.support_to_label), bold = true).apply { setPadding(dp(2), 0, dp(2), dp(4)) })
        content.addView(TextView(this).apply {
            text = SUPPORT_EMAIL
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            textDirection = View.TEXT_DIRECTION_LTR
            setTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.accentBlue))
            typeface = AppFonts.bold(this@InfoPageActivity)
            background = inputBackground()
            setPadding(dp(12), 0, dp(12), 0)
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(10) })

        // نوع الرسالة
        content.addView(body(getString(R.string.support_type_label), bold = true).apply { setPadding(dp(2), 0, dp(2), dp(4)) })
        val types = listOf(
            R.string.support_type_idea, R.string.support_type_suggestion, R.string.support_type_problem,
            R.string.support_type_question, R.string.support_type_other
        )
        var selectedType = types[0]
        val group = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        val blue = ContextCompat.getColor(this, R.color.brandBlue)
        val surface = ContextCompat.getColor(this, R.color.cardWhite)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        types.forEachIndexed { index, res ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = getString(res)
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = ColorStateList(states, intArrayOf(blue, surface))
                setTextColor(ColorStateList(states, intArrayOf(Color.WHITE, ContextCompat.getColor(this@InfoPageActivity, R.color.textPrimary))))
                chipStrokeWidth = dp(1).toFloat()
                chipStrokeColor = ColorStateList.valueOf(0x33808080)
                typeface = AppFonts.bold(this@InfoPageActivity)
                setOnCheckedChangeListener { _, checked -> if (checked) selectedType = res }
            }
            group.addView(chip)
            if (index == 0) chip.isChecked = true
        }
        content.addView(group, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        // العنوان (اختياري)
        val subject = EditText(this).apply {
            hint = getString(R.string.support_subject_hint)
            textSize = 14f
            maxLines = 1
            inputType = InputType.TYPE_CLASS_TEXT
            gravity = Gravity.CENTER_VERTICAL or startGravity()
            background = inputBackground()
            setPadding(dp(12), 0, dp(12), 0)
            typeface = AppFonts.regular(this@InfoPageActivity)
            setTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.textPrimary))
            setHintTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.textSecondary))
        }
        content.addView(subject, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(10) })

        // نص الرسالة (مربع كبير)
        val message = EditText(this).apply {
            hint = getString(R.string.support_message_hint)
            textSize = 14f
            minLines = 8
            gravity = Gravity.TOP or startGravity()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = inputBackground()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            typeface = AppFonts.regular(this@InfoPageActivity)
            setTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.textPrimary))
            setHintTextColor(ContextCompat.getColor(this@InfoPageActivity, R.color.textSecondary))
        }
        content.addView(message, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // زر الإرسال
        content.addView(TextView(this).apply {
            text = getString(R.string.support_send)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = AppFonts.bold(this@InfoPageActivity)
            background = GradientDrawable().apply { setColor(blue); cornerRadius = corner }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val text = message.text?.toString()?.trim().orEmpty()
                if (text.isEmpty()) {
                    // بدون Toast: نبرز المربع بإطار أحمر ونحط المؤشر فيه.
                    message.background = inputBackground(error = true)
                    message.requestFocus()
                } else {
                    message.background = inputBackground()
                    sendEmail(getString(selectedType), subject.text?.toString()?.trim().orEmpty(), text)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(50)))
    }

    private fun sendEmail(type: String, subject: String, message: String) {
        val versionName = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { "?" }
        val fullSubject = if (subject.isEmpty()) "[$type] ${getString(R.string.app_name)}" else "[$type] $subject"
        val footer = "\n\n—\n${getString(R.string.app_name)} $versionName · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, fullSubject)
            putExtra(Intent.EXTRA_TEXT, message + footer)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    // ───── قنوات التواصل ─────

    private fun buildSocial(content: LinearLayout) {
        content.addView(body(getString(R.string.social_intro)).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        fun s(label: String, icon: Int, url: String?) = Social(label, icon, url)
        fun rowOf(vararg items: Social) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            items.forEach { addView(socialCell(it)) }
        }
        fun add(v: View, top: Int = 6) = content.addView(v, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })

        add(rowOf(
            s("WHATSAPP", R.drawable.soc_whatsapp, "https://whatsapp.com/channel/0029VbDz68h7YSd54EV6rw0R"),
            s("INSTAGRAM", R.drawable.soc_instagram, "https://www.instagram.com/borsatdinari")
        ), 0)
        add(rowOf(
            s("TELEGRAM", R.drawable.soc_telegram, "https://t.me/BorsatDinari"),
            s("FACEBOOK", R.drawable.soc_facebook, "https://www.facebook.com/share/1Bv8F5CUZE/"),
            s("X", R.drawable.soc_x, "https://x.com/Borsatdinari"),
            s("THREADS", R.drawable.soc_threads, "https://www.threads.com/@borsatdinari")
        ))
        // وسط: GitHub/GitLab يسار، أيقونة التطبيق بالمنتصف، Gmail/Pinterest يمين.
        val middle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        fun column(vararg items: Social) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            items.forEach { addView(socialCell(it)) }
        }
        middle.addView(column(
            s("GITHUB", R.drawable.soc_github, "https://github.com/techmohammed/Borsat-Dinari"),
            s("GITLAB", R.drawable.soc_gitlab, null)
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        middle.addView(ImageView(this).apply {
            setImageResource(R.drawable.report_app_icon_8)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.app_name)
        }, LinearLayout.LayoutParams(dp(132), dp(132)))
        middle.addView(column(
            s("GMAIL", R.drawable.soc_gmail, "mailto:$SUPPORT_EMAIL"),
            s("PINTEREST", R.drawable.soc_pinterest, "https://www.pinterest.com/borsatdinari/")
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        add(middle, 10)
        add(rowOf(
            s("TIKTOK", R.drawable.soc_tiktok, "https://tiktok.com/@borsatdinari"),
            s("APKPURE", R.drawable.soc_apkpure, "https://apkpure.com/dp/com.mokhtar.com"),
            s("F-DROID", R.drawable.soc_fdroid, null),
            s("PLAY STORE", R.drawable.soc_playstore, null)
        ), 10)
        add(rowOf(
            s("APTOIDE", R.drawable.soc_aptoide, null),
            s("UPTODOWN", R.drawable.soc_uptodown, null)
        ))
    }

    private fun socialCell(item: Social): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(3), dp(2), dp(3))
            isClickable = item.url != null
            isFocusable = item.url != null
            alpha = if (item.url == null) 0.55f else 1f
            item.url?.let { url -> setOnClickListener { openLink(url) } }
        }
        cell.addView(ImageView(this).apply {
            setImageResource(item.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = item.label
        }, LinearLayout.LayoutParams(dp(60), dp(60)))
        cell.addView(TextView(this).apply {
            text = item.label
            textSize = 10f
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            // أسماء المواقع بالإنكليزي دائماً، فنثبّت Cairo حتى بالكوردية.
            typeface = AppFonts.cairoBold(this@InfoPageActivity)
        }, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        return cell
    }

    private fun openLink(url: String) {
        try {
            val action = if (url.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW
            startActivity(Intent(action, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }
}

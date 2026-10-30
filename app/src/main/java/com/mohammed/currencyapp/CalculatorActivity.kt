package com.mohammed.currencyapp

import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.mohammed.currencyapp.databinding.ActivityCalculatorBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class CalculatorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCalculatorBinding
    private var items: List<PriceItem> = emptyList()
    private var calculatorOptions: List<PriceItem> = emptyList()
    private var sourceIndex = 0
    private var destinationIndex = 1
    private val usdBaseKey = "CALC_USD"
    private var citiesLoaded = false
    private var currenciesLoaded = false
    private var cityItems: List<PriceItem> = emptyList()
    private var currencyItems: List<PriceItem> = emptyList()
    private var cryptoLoaded = false
    private var metalsLoaded = false
    private var cryptoItems: List<PriceItem> = emptyList()
    private var metalItems: List<PriceItem> = emptyList()
    private var amountDigits = ""
    private val numberFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale.US))
    private var swipeStartX = 0f
    private var swipeStartY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - swipeStartX
                val dy = event.y - swipeStartY
                val edge = 180f * resources.displayMetrics.density
                if (swipeStartX <= edge && dx >= edge && kotlin.math.abs(dy) < edge) {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        KurdishFont.apply(binding.root, this)
        // الأرقام تبقى Cairo الواضح حتى عند تفعيل الكوردية.
        binding.amountInput.typeface = resources.getFont(R.font.cairo_bold)
        binding.amountInput.showSoftInputOnFocus = false
        binding.toolbarTitle.text = getString(R.string.calculator_title)
        binding.toolbarTitle.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_bold
        )
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = null
        binding.ivHeaderLogo.setOnClickListener { finish() }

        setupNumberPad()
        binding.exchangeSelector.setOnClickListener {
            hideKeyboardForExchange(binding.exchangeSelector)
            showExchangePopup(isSource = false)
        }
        binding.sourceSelector.setOnClickListener {
            hideKeyboardForExchange(binding.sourceSelector)
            showExchangePopup(isSource = true)
        }
        binding.swapButton.setOnClickListener {
            val previousSource = sourceIndex
            sourceIndex = destinationIndex
            destinationIndex = previousSource
            updateSelectedViews()
            updateResult()
        }

        TelegramScraperRepository.fetch(this) { fetched ->
            cityItems = fetched
            citiesLoaded = true
            rebuildExchangeItems()
        }
        CurrenciesRepository.fetch(this) { fetched ->
            currencyItems = fetched
            currenciesLoaded = true
            rebuildExchangeItems()
        }
        CryptoRepository.fetch(this) { fetched ->
            cryptoItems = fetched
            cryptoLoaded = true
            rebuildExchangeItems()
        }
        MetalsRepository.fetch(this) { fetched ->
            metalItems = fetched
            metalsLoaded = true
            rebuildExchangeItems()
        }
    }

    private fun rebuildExchangeItems() {
        if (!citiesLoaded || !currenciesLoaded || !cryptoLoaded || !metalsLoaded) return
        val favorites = FavoriteCityStore.getAll(this)
        // لا نعتمد على اسم مترجم كمفتاح؛ حتى لا تختفي عناصر عند تغيير اللغة.
        // الترتيب الثابت يأتي من مصادر البيانات، فتظهر العملات الجديدة بعد التركية بكل اللغات.
        items = (cityItems + currencyItems + cryptoItems + metalItems).distinctBy {
            it.cityKey ?: "${it.flagDrawable}:${it.name}:${it.unit}"
        }
            .sortedWith(compareBy<PriceItem> {
                val key = it.cityKey ?: ""
                val favoriteIndex = favorites.indexOf(key)
                if (favoriteIndex >= 0) favoriteIndex else Int.MAX_VALUE
            })
        val usd = PriceItem(
            name = getString(R.string.currency_us_dollar),
            flagDrawable = "us",
            price = "100",
            unit = "100$",
            cityKey = usdBaseKey
        )
        calculatorOptions = listOf(usd) + items
        sourceIndex = 0
        destinationIndex = if (items.isEmpty()) 0 else 1
        updateSelectedViews()
        updateResult()
    }

    private fun showExchangePopup(isSource: Boolean) {
        if (calculatorOptions.isEmpty()) return
        val content = layoutInflater.inflate(R.layout.dialog_calculator_exchange, null) as LinearLayout
        val isKurdish = AppLanguage.currentTag() == "ku"
        val itemFont = resources.getFont(if (isKurdish) R.font.peshang_des_5_bold else R.font.cairo_semibold)
        calculatorOptions.forEachIndexed { _, item ->
            val row = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(58)
                )
                minimumHeight = dp(58)
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(12), 0, dp(12), 0)
                isClickable = true
                isFocusable = true
                // لا نستخدم attr كخلفية مباشرة؛ ذلك كان سبب الكراش عند فتح القائمة.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            val flag = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(36),
                    dp(36)
                ).apply { marginEnd = dp(10) }
                setImageResource(flagResource(item.flagDrawable))
            }
            val name = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTextColor(getColor(R.color.textPrimary))
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                typeface = itemFont
                text = item.name
            }
            row.addView(flag)
            row.addView(name)
            content.addView(row)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            setPadding(dp(2), 0, dp(10), 0)
            clipToPadding = true
            clipToOutline = true
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundResource(R.drawable.bg_calculator_popup)
        }
        val popupHeight = minOf(dp(5 * 58 + 12), (resources.displayMetrics.heightPixels * 0.48f).toInt())
        val anchor = if (isSource) binding.sourceSelector else binding.exchangeSelector
        val popup = PopupWindow(
            scroll,
            anchor.width,
            popupHeight,
            true
        )
        popup.elevation = 16f
        // نحتاج مرجع الإغلاق داخل مستمع الصفوف بعد إنشائه.
        for (i in 0 until content.childCount) {
            val row = content.getChildAt(i)
            row.setOnClickListener {
                if (isSource) sourceIndex = i else destinationIndex = i
                updateSelectedViews()
                updateResult()
                popup.dismiss()
            }
        }
        popup.showAsDropDown(anchor, 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun hideKeyboardForExchange(target: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.amountInput.windowToken, 0)
        binding.amountInput.clearFocus()
        target.requestFocus()
    }

    private fun setupNumberPad() {
        val keys = listOf(
            binding.key9 to "9", binding.key8 to "8", binding.key7 to "7",
            binding.key6 to "6", binding.key5 to "5", binding.key4 to "4",
            binding.key3 to "3", binding.key2 to "2", binding.key1 to "1",
            binding.key0 to "0"
        )
        keys.forEach { (button, digit) ->
            button.setOnClickListener {
                amountDigits = appendDigit(amountDigits, digit)
                refreshAmount()
            }
        }
        binding.keyPoint.setOnClickListener {
            if (!amountDigits.contains('.')) {
                amountDigits = if (amountDigits.isEmpty()) "0." else "$amountDigits."
                refreshAmount()
            }
        }
        binding.keyDelete.setOnClickListener {
            if (amountDigits.isNotEmpty()) {
                amountDigits = amountDigits.dropLast(1)
                refreshAmount()
            }
        }
        binding.keyDelete.setOnLongClickListener {
            amountDigits = ""
            refreshAmount()
            true
        }
    }

    private fun refreshAmount() {
        binding.amountInput.setText(formatInput(amountDigits))
        updateResult()
    }

    private fun appendDigit(current: String, digit: String): String {
        if (current.isEmpty()) return digit
        if (!current.contains('.') && current == "0") return if (digit == "0") "0" else digit
        return current + digit
    }

    private fun formatInput(value: String): String {
        val normalized = normalizeDigits(value).replace(",", "")
        if (normalized.isEmpty()) return ""
        val parts = normalized.split('.', limit = 2)
        val digits = parts[0].filter { it in '0'..'9' }
        if (digits.isEmpty()) return if (parts.size > 1) "0." else ""
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return if (parts.size > 1) grouped + "." + parts[1].filter { it in '0'..'9' } else grouped
    }

    private fun updateSelectedViews() {
        val source = calculatorOptions.getOrNull(sourceIndex) ?: return
        val destination = calculatorOptions.getOrNull(destinationIndex) ?: return
        val itemFont = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_semibold
        )
        binding.sourceName.text = source.name
        binding.sourceName.typeface = itemFont
        binding.sourceFlag.setImageResource(flagResource(source.flagDrawable))
        binding.sourceSelector.contentDescription = "${getString(R.string.calculator_exchange_label)}: ${source.name}"
        binding.exchangeName.text = destination.name
        binding.exchangeName.typeface = itemFont
        binding.exchangeFlag.setImageResource(flagResource(destination.flagDrawable))
        binding.exchangeSelector.contentDescription = "${getString(R.string.calculator_exchange_label)}: ${destination.name}"
    }

    private fun updateResult() {
        val amount = amountDigits.toDoubleOrNull()
        val source = calculatorOptions.getOrNull(sourceIndex)
        val destination = calculatorOptions.getOrNull(destinationIndex)
        val sourceRate = source?.let { ratePer100Usd(it) }
        val destinationRate = destination?.let { ratePer100Usd(it) }
        if (amount == null || sourceRate == null || destinationRate == null || sourceRate <= 0.0 || destinationRate <= 0.0) {
            binding.resultNumber.text = "—"
            binding.resultUnit.text = ""
            return
        }
        val result = amount * destinationRate / sourceRate
        if (!result.isFinite()) {
            binding.resultNumber.text = "—"
            binding.resultUnit.text = ""
            return
        }
        binding.resultNumber.text = if (CalcSupport.isQuote(destination.cityKey)) CalcSupport.format(result) else numberFormat.format(result)
        binding.resultUnit.text = outputUnit(destination)
        binding.resultUnit.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_bold
        )
    }

    private fun extractNumber(value: String): Double? {
        val normalized = normalizeDigits(value)
        val match = Regex("[0-9]+(?:[,.][0-9]+)*").find(normalized) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    /** المقياس النسبي هو وحدات الخيار لكل 100 دولار. */
    private fun ratePer100Usd(item: PriceItem): Double? {
        if (item.cityKey == usdBaseKey) return 100.0
        val price = extractNumber(item.price) ?: return null
        if (CalcSupport.isQuote(item.cityKey)) return CalcSupport.ratePer100Usd(item.cityKey, price)
        return if (item.cityKey == "FX_EUR" || item.cityKey == "FX_GBP") {
            if (price == 0.0) null else 10_000.0 / price
        } else {
            price
        }
    }

    private fun outputUnit(item: PriceItem): String = CalcSupport.outputUnit(item) ?: when (item.cityKey) {
        "CALC_USD" -> getString(R.string.currency_us_dollar)
        "TRY_INVESTING" -> getString(R.string.currency_try_unit)
        "IRR_TELEGRAM" -> getString(R.string.currency_irr_unit)
        "FX_EUR" -> getString(R.string.currency_eur_unit)
        "FX_GBP" -> getString(R.string.currency_gbp_unit)
        "FX_JOD" -> getString(R.string.currency_jod_unit)
        "FX_AED" -> getString(R.string.currency_aed_unit)
        "FX_KWD" -> getString(R.string.currency_kwd_unit)
        "FX_EGP" -> getString(R.string.currency_egp_unit)
        "FX_CNY" -> getString(R.string.currency_cny_unit)
        "FX_SAR" -> getString(R.string.currency_sar_unit)
        "FX_SYP" -> getString(R.string.currency_syp_unit)
        else -> getString(R.string.currency_iqd_unit)
    }

    private fun flagResource(flag: String): Int = resources.getIdentifier(
        "flag_${flag.lowercase(Locale.US)}", "drawable", packageName
    ).takeIf { it != 0 } ?: R.drawable.flag_iq

    private fun normalizeDigits(value: String): String = buildString {
        for (c in value) append(when (c) {
            in '٠'..'٩' -> ('0'.code + c.code - '٠'.code).toChar()
            in '۰'..'۹' -> ('0'.code + c.code - '۰'.code).toChar()
            else -> c
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

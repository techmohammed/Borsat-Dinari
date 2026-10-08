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
    private var selectedIndex = 0
    private var citiesLoaded = false
    private var currenciesLoaded = false
    private var cityItems: List<PriceItem> = emptyList()
    private var currencyItems: List<PriceItem> = emptyList()
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
        binding.toolbarTitle.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        binding.toolbarTitle.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_bold
        )
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = null
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupNumberPad()
        binding.exchangeSelector.setOnClickListener {
            hideKeyboardForExchange()
            showExchangePopup()
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
    }

    private fun rebuildExchangeItems() {
        if (!citiesLoaded || !currenciesLoaded) return
        val favorites = FavoriteCityStore.getAll(this)
        // لا نعتمد على اسم مترجم كمفتاح؛ حتى لا تختفي عناصر عند تغيير اللغة.
        items = (cityItems + currencyItems).distinctBy {
            it.cityKey ?: "${it.flagDrawable}:${it.name}:${it.unit}"
        }
            .sortedWith(compareBy<PriceItem> {
                val key = it.cityKey ?: ""
                val favoriteIndex = favorites.indexOf(key)
                if (favoriteIndex >= 0) favoriteIndex else Int.MAX_VALUE
            }.thenBy { it.name })
        selectedIndex = 0
        updateSelectedView()
        updateResult()
    }

    private fun showExchangePopup() {
        if (items.isEmpty()) return
        val content = layoutInflater.inflate(R.layout.dialog_calculator_exchange, null) as LinearLayout
        val isKurdish = AppLanguage.currentTag() == "ku"
        val itemFont = resources.getFont(if (isKurdish) R.font.peshang_des_5_bold else R.font.cairo_semibold)
        items.forEachIndexed { _, item ->
            val row = LinearLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                minimumHeight = (82 * resources.displayMetrics.density).toInt()
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
                setPadding((10 * resources.displayMetrics.density).toInt(), 0,
                    (10 * resources.displayMetrics.density).toInt(), 0)
                isClickable = true
                isFocusable = true
                // لا نستخدم attr كخلفية مباشرة؛ ذلك كان سبب الكراش عند فتح القائمة.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            val flag = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (46 * resources.displayMetrics.density).toInt(),
                    (46 * resources.displayMetrics.density).toInt()
                ).apply { marginEnd = (12 * resources.displayMetrics.density).toInt() }
                setImageResource(flagResource(item.flagDrawable))
            }
            val name = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTextColor(getColor(R.color.textPrimary))
                textSize = 18f
                typeface = itemFont
                paint.isFakeBoldText = true
                text = item.name
            }
            row.addView(flag)
            row.addView(name)
            content.addView(row)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundResource(R.drawable.bg_dialog_card)
        }
        val popupHeight = (resources.displayMetrics.heightPixels * 0.56f).toInt()
        val popup = PopupWindow(
            scroll,
            binding.exchangeSelector.width,
            popupHeight,
            true
        )
        popup.elevation = 16f
        // نحتاج مرجع الإغلاق داخل مستمع الصفوف بعد إنشائه.
        for (i in 0 until content.childCount) {
            val row = content.getChildAt(i)
            row.setOnClickListener {
                selectedIndex = i
                updateSelectedView()
                updateResult()
                popup.dismiss()
            }
        }
        popup.showAsDropDown(binding.exchangeSelector, 0, 6)
    }

    private fun hideKeyboardForExchange() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.amountInput.windowToken, 0)
        binding.amountInput.clearFocus()
        binding.exchangeSelector.requestFocus()
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
            if (amountDigits.isNotEmpty() && !amountDigits.contains('.')) {
                amountDigits += "."
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

    private fun updateSelectedView() {
        val item = items.getOrNull(selectedIndex) ?: return
        binding.exchangeName.text = item.name
        binding.exchangeName.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_semibold
        )
        binding.exchangeFlag.setImageResource(flagResource(item.flagDrawable))
    }

    private fun updateResult() {
        val amount = amountDigits.toDoubleOrNull()
        val item = items.getOrNull(selectedIndex)
        val price = item?.let { extractNumber(it.price) }
        if (amount == null || price == null || price == 0.0) {
            binding.resultNumber.text = "—"
            binding.resultUnit.text = ""
            return
        }
        val result = if (item.cityKey == "FX_EUR" || item.cityKey == "FX_GBP") {
            amount * 100.0 / price
        } else {
            amount * price / 100.0
        }
        if (!result.isFinite()) {
            binding.resultNumber.text = "—"
            binding.resultUnit.text = ""
            return
        }
        binding.resultNumber.text = numberFormat.format(result)
        binding.resultUnit.text = outputUnit(item)
        binding.resultUnit.typeface = resources.getFont(
            if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_bold
        )
    }

    private fun extractNumber(value: String): Double? {
        val normalized = normalizeDigits(value)
        val match = Regex("[0-9]+(?:[,.][0-9]+)*").find(normalized) ?: return null
        return match.value.replace(",", "").toDoubleOrNull()
    }

    private fun outputUnit(item: PriceItem): String = when (item.cityKey) {
        "TRY_INVESTING" -> getString(R.string.currency_try_unit)
        "IRR_TELEGRAM" -> getString(R.string.currency_irr_unit)
        "FX_EUR" -> getString(R.string.currency_eur_unit)
        "FX_GBP" -> getString(R.string.currency_gbp_unit)
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

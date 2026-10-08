package com.mohammed.currencyapp

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.view.ViewGroup

class CalculatorWidgetPickerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        if (widgetId < 0) { finish(); return }
        loadItems { items ->
            val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            items.forEachIndexed { index, item ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(24, 8, 24, 8)
                    setOnClickListener {
                        sendChoice(widgetId, index)
                        finish()
                    }
                }
                val flag = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 48).apply { marginEnd = 20 }
                    setImageResource(resources.getIdentifier("flag_${item.flagDrawable}", "drawable", packageName))
                }
                val name = TextView(this).apply {
                    text = item.name
                    textSize = 18f
                    setTextColor(getColor(R.color.textPrimary))
                    typeface = resources.getFont(if (AppLanguage.currentTag() == "ku") R.font.peshang_des_5_bold else R.font.cairo_semibold)
                }
                row.addView(flag); row.addView(name); rows.addView(row)
            }
            val scroll = ScrollView(this).apply {
                addView(rows)
                isVerticalScrollBarEnabled = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (168 * resources.displayMetrics.density).toInt()
                )
            }
            val dialog = AlertDialog.Builder(this)
                .setView(scroll)
                .setOnCancelListener { finish() }
                .show()
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.90f).toInt(),
                (190 * resources.displayMetrics.density).toInt()
            )
        }
    }

    private fun sendChoice(widgetId: Int, index: Int) {
        sendBroadcast(Intent(this, CalculatorWidgetProvider::class.java).apply {
            action = "com.mohammed.currencyapp.ACTION_CALCULATOR_SELECT"
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra("calculator_index", index)
        })
    }

    private fun loadItems(callback: (List<PriceItem>) -> Unit) {
        var cities: List<PriceItem>? = null
        var currencies: List<PriceItem>? = null
        fun done() {
            if (cities == null || currencies == null) return
            callback((cities!! + currencies!!).distinctBy { it.cityKey ?: "${it.flagDrawable}:${it.name}" })
        }
        TelegramScraperRepository.fetch(this) { cities = it; done() }
        CurrenciesRepository.fetch(this) { currencies = it; done() }
    }
}

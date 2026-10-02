package com.mohammed.currencyapp

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * اختصارات أيقونة التطبيق (ضغطة مطولة): نفس المفضلات الثلاث بترتيبها، وبجانب
 * كل وحدة علمها. تنتحدّث عند فتح التطبيق وبكل مرة تغيّر المفضلة. الضغط على أي
 * اختصار يفتح صفحة سجل أسعار تلك البورصة (نفس منطق الاختصارات القديمة).
 */
object FavoriteShortcuts {

    fun update(context: Context) {
        val app = context.applicationContext
        val shortcuts = FavoriteCityStore.getAll(app).mapIndexedNotNull { index, key ->
            val meta = WidgetPriceProvider.metaFor(key) ?: return@mapIndexedNotNull null
            val flagRes = app.resources.getIdentifier("flag_${meta.flag}", "drawable", app.packageName)
            if (flagRes == 0) return@mapIndexedNotNull null
            val flagBitmap = BitmapFactory.decodeResource(app.resources, flagRes)
                ?: return@mapIndexedNotNull null

            val intent = Intent(app, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_SHORTCUT_CITY_KEY, key)
                putExtra(MainActivity.EXTRA_SHORTCUT_CITY_NAME, meta.name)
                putExtra(MainActivity.EXTRA_SHORTCUT_CITY_FLAG, meta.flag)
            }
            ShortcutInfoCompat.Builder(app, "fav_$key")
                .setShortLabel(meta.name)
                .setLongLabel(meta.name)
                .setIcon(IconCompat.createWithBitmap(flagBitmap))
                .setIntent(intent)
                .setRank(index)
                .build()
        }
        try {
            ShortcutManagerCompat.setDynamicShortcuts(app, shortcuts)
        } catch (_: Exception) {
            // بعض الـ Launchers تحدّ الاختصارات أو تفشل — نتجاهل بدل ما نكرّش التطبيق.
        }
    }
}

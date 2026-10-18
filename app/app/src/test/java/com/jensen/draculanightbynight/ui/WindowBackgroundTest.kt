package com.jensen.draculanightbynight.ui

import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.jensen.draculanightbynight.MainActivity
import android.content.ComponentName
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What shows before Compose draws its first frame matches the palette, not the platform's theme (#192). */
@RunWith(RobolectricTestRunner::class)
class WindowBackgroundTest {
    private fun windowBackground(): Long {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), PackageManager.GET_META_DATA).themeResource
        val attrs = ContextThemeWrapper(context, theme).obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        val drawable = attrs.getDrawable(0)
        attrs.recycle()
        return (drawable as ColorDrawable).color.toLong() and 0xFFFFFFFFL
    }

    @Test
    fun dayIsParchment() = assertEquals(Palettes.Light.background, windowBackground())

    @Test
    @Config(qualifiers = "night")
    fun nightIsCandlelight() = assertEquals(Palettes.Dark.background, windowBackground())
}

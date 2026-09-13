package com.kazumaproject.markdownhelperkeyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.symbol_keyboard.CustomSymbolKeyboardView
import com.kazumaproject.symbol_keyboard.R as SymbolR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaterialSymbolPanelTest {
    private fun verifyPanel(widthDp: Int) {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
        )
        val panel = CustomSymbolKeyboardView(context)
        val density = context.resources.displayMetrics.density
        panel.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((280 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        assertNotNull(panel.findViewById<View>(SymbolR.id.symbol_panel_title))
        assertNotNull(panel.findViewById<View>(SymbolR.id.symbol_empty_state))
        for (id in listOf(SymbolR.id.return_jp_keyboard_button, SymbolR.id.emoji_search_button,
            SymbolR.id.symbol_keyboard_delete_key)) {
            val button = panel.findViewById<View>(id)
            assertTrue(button.width / density >= 47.5f)
            assertTrue(button.height / density >= 47.5f)
            assertTrue(button.contentDescription.isNotEmpty())
        }
        val grid = panel.findViewById<View>(SymbolR.id.symbol_candidate_recycler_view)
        assertTrue("The controls must leave room for browsing", grid.height / density >= 120f)
        panel.setSymbolLists(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), SymbolMode.SYMBOL)
        val modes = panel.findViewById<TabLayout>(SymbolR.id.mode_tab_layout)
        assertEquals(4, modes.tabCount)
        assertEquals(SymbolMode.SYMBOL.ordinal, modes.selectedTabPosition)
        assertTrue((0 until modes.tabCount).all { !modes.getTabAt(it)?.text.isNullOrEmpty() })
        assertEquals(context.getString(SymbolR.string.symbol_mode_symbol),
            panel.findViewById<TextView>(SymbolR.id.symbol_panel_title).text.toString())
    }

    @Test @Config(qualifiers = "port")
    fun compactPortraitCanInflateAndBrowse() = verifyPanel(360)

    @Test @Config(qualifiers = "sw600dp-land")
    fun unfoldedLandscapeCanInflateAndBrowse() = verifyPanel(840)
}

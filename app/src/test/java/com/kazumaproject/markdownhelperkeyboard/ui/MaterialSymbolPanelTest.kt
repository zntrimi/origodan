package com.kazumaproject.markdownhelperkeyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.snippet.SnippetItem
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
        assertEquals(SymbolMode.entries.size, modes.tabCount)
        assertEquals(SymbolMode.SYMBOL.ordinal, modes.selectedTabPosition)
        assertTrue((0 until modes.tabCount).all { !modes.getTabAt(it)?.text.isNullOrEmpty() })
        assertEquals(context.getString(SymbolR.string.symbol_mode_symbol),
            panel.findViewById<TextView>(SymbolR.id.symbol_panel_title).text.toString())

        // Snippets open on their own tab and fall back to a hint when nothing is registered.
        panel.setSymbolLists(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), SymbolMode.SNIPPET)
        assertEquals(SymbolMode.SNIPPET.ordinal, modes.selectedTabPosition)
        assertEquals(context.getString(SymbolR.string.symbol_mode_snippet),
            panel.findViewById<TextView>(SymbolR.id.symbol_panel_title).text.toString())
    }

    @Test @Config(qualifiers = "port")
    fun snippetTabListsRegisteredItems() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
        )
        val panel = CustomSymbolKeyboardView(context)
        var inserted: SnippetItem? = null
        panel.setOnSnippetItemClickListener { inserted = it }
        panel.setSymbolLists(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), SymbolMode.SNIPPET,
            snippets = listOf(SnippetItem(1, "work", "me@example.com")),
        )
        val density = context.resources.displayMetrics.density
        panel.measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((280 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        val grid = panel.findViewById<RecyclerView>(SymbolR.id.symbol_candidate_recycler_view)
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(grid.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(grid.height, View.MeasureSpec.EXACTLY),
        )
        grid.layout(grid.left, grid.top, grid.right, grid.bottom)
        assertEquals(1, grid.adapter?.itemCount)
        assertEquals(View.GONE, panel.findViewById<View>(SymbolR.id.symbol_empty_state).visibility)
        grid.getChildAt(0).performClick()
        assertEquals("me@example.com", inserted?.text)
    }

    @Test @Config(qualifiers = "port")
    fun compactPortraitCanInflateAndBrowse() = verifyPanel(360)

    @Test @Config(qualifiers = "sw600dp-land")
    fun unfoldedLandscapeCanInflateAndBrowse() = verifyPanel(840)
}

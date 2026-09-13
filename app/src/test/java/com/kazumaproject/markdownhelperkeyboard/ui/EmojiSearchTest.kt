package com.kazumaproject.markdownhelperkeyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.emoji_search.EmojiSearchIndex
import com.kazumaproject.markdownhelperkeyboard.emoji_search.EmojiSearchKeyboardView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmojiSearchTest {
    private val context get() = ContextThemeWrapper(
        ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
    )
    private fun index() = EmojiSearchIndex(context.assets.open("emoji_search/keywords.tsv").reader())

    @Test fun searchesEnglishNamesKeywordsAndPrefixes() {
        val index = index()
        assertTrue(index.search("smile").contains("😀"))
        assertTrue(index.search("dog").contains("🐶"))
        assertTrue(index.search("coffee").contains("☕"))
        assertTrue(index.search("smi").contains("😀"))
        assertEquals("❤️", index.search("red heart").first())
        assertEquals(index.search("dog"), index.search("ＤＯＧ"))
    }

    @Test fun searchesJapaneseAndNormalizesKanaPresentation() {
        val index = index()
        assertTrue(index.search("犬").contains("🐶"))
        assertTrue(index.search("はーと").contains("❤️"))
        assertEquals(index.search("ハート"), index.search("ﾊｰﾄ"))
        assertEquals(listOf("❤️"), index.search("❤"))
        assertTrue(index.search("zzzzunknown").isEmpty())
    }

    @Test fun defaultsAreUniqueAndHaveNoSkinToneVariantFlood() {
        val results = index().search("", 3000)
        assertTrue(results.size > 1800)
        assertEquals(results.size, results.distinct().size)
        assertFalse(results.any { emoji -> emoji.codePoints().anyMatch { it in 0x1F3FB..0x1F3FF } })
        assertEquals(12, index().search("", 12).size)
        assertTrue(index().search("", 0).isEmpty())
    }

    private fun panel(width: Int, height: Int): EmojiSearchKeyboardView {
        val panel = EmojiSearchKeyboardView(context)
        val density = panel.resources.displayMetrics.density
        panel.measure(
            View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        return panel
    }

    @Test @Config(qualifiers = "port") fun repeatedEmojiSelectionKeepsQueryAndResults() {
        val panel = panel(360, 432)
        val committed = mutableListOf<String>()
        panel.onEmoji = { committed.add(it) }
        panel.showResults("smile", listOf("😀", "😃"))
        val grid = panel.findViewById<RecyclerView>(R.id.emoji_search_results)
        // Bind a real result holder: two clicks must still use the same result.
        val adapter = grid.adapter!!
        @Suppress("UNCHECKED_CAST")
        val typed = adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        val holder = typed.createViewHolder(grid, typed.getItemViewType(0))
        typed.bindViewHolder(holder, 0)
        holder.itemView.performClick()
        holder.itemView.performClick()
        assertEquals(listOf("😀", "😀"), committed)
        assertEquals("smile", panel.findViewById<TextView>(R.id.emoji_search_query).text.toString())
        assertEquals(2, grid.adapter!!.itemCount)
        assertTrue(grid.height / panel.resources.displayMetrics.density >= 112f)
    }

    @Test @Config(qualifiers = "sw600dp-land") fun unfoldedLayoutAllowsEnglishTypingAndClear() {
        val panel = panel(840, 432)
        var language: Boolean? = null
        val text = StringBuilder()
        var cleared = false
        panel.onLanguageChanged = { language = it }
        panel.onText = { text.append(it) }
        panel.onClear = { cleared = true }
        panel.findViewById<View>(R.id.emoji_search_language).performClick()
        assertEquals(false, language)
        for (letter in "smile") panel.findViewWithTag<View>("emoji-search-key-$letter").performClick()
        assertEquals("smile", text.toString())
        panel.showResults(text.toString(), listOf("😀"))
        panel.findViewById<View>(R.id.emoji_search_clear).performClick()
        assertTrue(cleared)
        assertFalse(panel.hasFocus())
        for (id in listOf(R.id.emoji_search_close, R.id.emoji_search_clear, R.id.emoji_search_language,
            R.id.emoji_search_done, R.id.emoji_search_delete)) {
            val button = panel.findViewById<View>(id)
            assertTrue(button.height / panel.resources.displayMetrics.density >= 47.5f)
        }
    }
}

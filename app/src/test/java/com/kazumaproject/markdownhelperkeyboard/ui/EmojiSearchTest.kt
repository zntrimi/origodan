package com.kazumaproject.markdownhelperkeyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.LayoutInflater
import com.kazumaproject.custom_keyboard.view.FlickKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
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
        // A width change rebuilds GODAN into its mirrored placement.
        panel.measure(
            View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        return panel
    }

    @Test @Config(qualifiers = "port") fun repeatedEmojiSelectionKeepsQueryAndResults() {
        val panel = panel(360, 208)
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

    @Test @Config(qualifiers = "sw600dp-land") fun switchingSearchLanguagePreservesQueryAndResults() {
        val panel = panel(840, 208)
        panel.showResults("いぬ", listOf("🐶", "🐕"))
        panel.onLanguageChanged = { language -> panel.setJapanese(language) }
        panel.findViewById<View>(R.id.emoji_search_language).performClick()
        assertEquals("あ", panel.findViewById<TextView>(R.id.emoji_search_language).text.toString())
        assertEquals("いぬ", panel.findViewById<TextView>(R.id.emoji_search_query).text.toString())
        assertEquals(2, panel.findViewById<RecyclerView>(R.id.emoji_search_results).adapter!!.itemCount)
        panel.findViewById<View>(R.id.emoji_search_language).performClick()
        assertEquals("ABC", panel.findViewById<TextView>(R.id.emoji_search_language).text.toString())
        assertEquals("いぬ", panel.findViewById<TextView>(R.id.emoji_search_query).text.toString())
        assertFalse(panel.hasFocus())
        // The panel has no replacement typing keyboard; IMEService uses the normal surfaces.
        assertEquals(3, panel.childCount) // Search bar, status and results only.
        for (id in listOf(R.id.emoji_search_close, R.id.emoji_search_clear, R.id.emoji_search_language)) {
            assertTrue(panel.findViewById<View>(id).height / panel.resources.displayMetrics.density >= 47.5f)
        }
    }
    private fun assertNormalKeyboardSiblings() {
        val root = LayoutInflater.from(context).inflate(R.layout.main_layout, null)
        val panel = root.findViewById<EmojiSearchKeyboardView>(R.id.emoji_search_keyboard)
        val godan = root.findViewById<FlickKeyboardView>(R.id.custom_layout_default)
        val qwerty = root.findViewById<QWERTYKeyboardView>(R.id.qwerty_view)
        assertSame(panel.parent, godan.parent)
        assertSame(panel.parent, qwerty.parent)
        assertEquals(3, panel.childCount)
    }

    @Test @Config(qualifiers = "port") fun compactSearchUsesNormalKeyboardSiblings() = assertNormalKeyboardSiblings()
    @Test @Config(qualifiers = "sw600dp-land") fun unfoldedSearchUsesNormalKeyboardSiblings() = assertNormalKeyboardSiblings()

}

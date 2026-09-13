package com.kazumaproject.markdownhelperkeyboard.emoji_search

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R

/** Search controls and results above the existing typing keyboard. */
class EmojiSearchKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var onClear: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onLanguageChanged: (Boolean) -> Unit = {}
    var onEmoji: (String) -> Unit = {}

    private val onSurfaceColor = color(com.google.android.material.R.attr.colorOnSurface)
    private val keyColor = color(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    private val query = MaterialTextView(context)
    private val status = MaterialTextView(context)
    private val grid = RecyclerView(context)
    private val gridManager = GridLayoutManager(context, 6)
    private val language = button("ABC")
    private val resultsAdapter = ResultsAdapter()
    private var japanese = true

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainer))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(keyColor, 24)
        }
        val close = button("‹").apply {
            id = R.id.emoji_search_close
            contentDescription = context.getString(R.string.emoji_search_back)
            textSize = 30f
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onClose() }
        }
        header.addView(close, LayoutParams(dp(48), dp(48)))
        query.apply {
            id = R.id.emoji_search_query
            textSize = 16f
            setTextColor(onSurfaceColor)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        header.addView(query, LayoutParams(0, dp(48), 1f))
        val clear = button("×").apply {
            id = R.id.emoji_search_clear
            textSize = 24f
            contentDescription = context.getString(R.string.emoji_search_clear)
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
            setOnClickListener { onClear() }
        }
        header.addView(clear, LayoutParams(dp(48), dp(48)))
        language.id = R.id.emoji_search_language
        language.textSize = 14f
        language.setOnClickListener { onLanguageChanged(!japanese) }
        header.addView(language, LayoutParams(dp(56), dp(48)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        status.apply {
            textSize = 12f
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, dp(28)))
        grid.apply {
            id = R.id.emoji_search_results
            layoutManager = gridManager
            adapter = resultsAdapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, 0, dp(4))
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        setJapanese(true)
    }

    fun setJapanese(value: Boolean) {
        japanese = value
        language.text = if (value) "ABC" else "あ"
        language.contentDescription = context.getString(
            if (value) R.string.emoji_search_switch_english else R.string.emoji_search_switch_japanese
        )
    }

    fun showResults(text: String, results: List<String>, loading: Boolean = false, cursor: Int = text.length) {
        val position = cursor.coerceIn(0, text.length)
        val displayed = if (position < text.length) text.substring(0, position) + "│" + text.substring(position) else text
        query.text = displayed.ifEmpty { context.getString(
            if (japanese) R.string.emoji_search_hint_japanese else R.string.emoji_search_hint_english
        ) }
        query.contentDescription = context.getString(R.string.emoji_search_query_label, text)
        findViewById<View>(R.id.emoji_search_clear).isEnabled = text.isNotEmpty()
        status.text = when {
            loading -> context.getString(R.string.emoji_search_loading)
            results.isEmpty() -> context.getString(R.string.emoji_search_empty)
            else -> context.getString(R.string.emoji_search_result_hint, results.size)
        }
        if (resultsAdapter.items != results) {
            resultsAdapter.items = results
            resultsAdapter.notifyDataSetChanged()
            gridManager.scrollToPositionWithOffset(0, 0)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gridManager.spanCount = ((w - paddingLeft - paddingRight) / dp(56)).coerceIn(4, 18)
    }

    private fun button(label: String) = MaterialButton(context).apply {
        text = label
        isAllCaps = false
        textSize = 18f
        setTypeface(typeface, Typeface.NORMAL)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(10)
        backgroundTintList = ColorStateList.valueOf(keyColor)
        setTextColor(onSurfaceColor)
        stateListAnimator = null
    }

    private inner class ResultsAdapter : RecyclerView.Adapter<ResultsAdapter.Holder>() {
        var items: List<String> = emptyList()
        inner class Holder(val button: MaterialButton) : RecyclerView.ViewHolder(button)
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(button("").apply {
            textSize = 28f
            layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
        })
        override fun getItemCount() = items.size
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val emoji = items[position]
            holder.button.text = emoji
            holder.button.contentDescription = context.getString(R.string.emoji_search_insert, emoji)
            holder.button.setOnClickListener { onEmoji(emoji) }
        }
    }

    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
    }
}

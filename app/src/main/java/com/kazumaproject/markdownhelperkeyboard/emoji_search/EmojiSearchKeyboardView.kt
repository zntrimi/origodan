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

/** A self-contained search surface: typing never changes the destination editor. */
class EmojiSearchKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    var onText: (String) -> Unit = {}
    var onDelete: () -> Unit = {}
    var onClear: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onDone: () -> Unit = {}
    var onLanguageChanged: (Boolean) -> Unit = {}
    var onEmoji: (String) -> Unit = {}

    private val onSurfaceColor = color(com.google.android.material.R.attr.colorOnSurface)
    private val keyColor = color(com.google.android.material.R.attr.colorSurfaceContainerHigh)
    private val query = MaterialTextView(context)
    private val status = MaterialTextView(context)
    private val grid = RecyclerView(context)
    private val gridManager = GridLayoutManager(context, 6)
    private val keyboard = LinearLayout(context)
    private val language = button("ABC")
    private val shift = button("⇧")
    private val letters = mutableListOf<Pair<MaterialButton, Char>>()
    private val resultsAdapter = ResultsAdapter()
    private var shifted = false
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
        keyboard.orientation = VERTICAL
        for (row in listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")) {
            val keys = LinearLayout(context).apply { orientation = HORIZONTAL }
            if (row == "zxcvbnm") {
                shift.contentDescription = context.getString(R.string.emoji_search_shift)
                shift.setOnClickListener {
                    shifted = !shifted
                    updateLetterLabels()
                }
                keys.addView(shift, keyParams())
            }
            for (letter in row) {
                val key = button(letter.toString()).apply {
                    tag = "emoji-search-key-$letter"
                    setOnClickListener {
                        onText(if (shifted && !japanese) letter.uppercase() else letter.toString())
                        if (shifted) { shifted = false; updateLetterLabels() }
                    }
                }
                letters.add(key to letter)
                keys.addView(key, keyParams())
            }
            if (row == "zxcvbnm") keys.addView(button("⌫").apply {
                id = R.id.emoji_search_delete
                contentDescription = context.getString(R.string.emoji_search_delete)
                setOnClickListener { onDelete() }
            }, keyParams())
            keyboard.addView(keys, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
        }
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        language.apply {
            id = R.id.emoji_search_language
            textSize = 14f
            setOnClickListener { setJapanese(!japanese); onLanguageChanged(japanese) }
        }
        bottom.addView(language, LayoutParams(dp(88), dp(48)).apply { marginEnd = dp(4) })
        bottom.addView(button(context.getString(R.string.emoji_search_space)).apply {
            id = R.id.emoji_search_space
            textSize = 14f
            setOnClickListener { onText(" ") }
        }, LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) })
        bottom.addView(button(context.getString(R.string.emoji_search_done)).apply {
            id = R.id.emoji_search_done
            textSize = 14f
            setOnClickListener { onDone() }
        }, LayoutParams(dp(72), dp(48)))
        keyboard.addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(50)))
        addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, dp(200)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        setJapanese(true)
    }

    fun setJapanese(value: Boolean) {
        japanese = value
        shifted = false
        language.text = if (value) "あ → ABC" else "ABC → あ"
        language.contentDescription = context.getString(
            if (value) R.string.emoji_search_switch_english else R.string.emoji_search_switch_japanese
        )
        updateLetterLabels()
    }

    fun showResults(text: String, results: List<String>, loading: Boolean = false) {
        query.text = text.ifEmpty { context.getString(
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
        keyboard.layoutParams = keyboard.layoutParams.apply {
            width = (w - paddingLeft - paddingRight).coerceAtMost(dp(680))
        }
    }

    private fun updateLetterLabels() {
        letters.forEach { (button, letter) ->
            button.text = if (shifted && !japanese) letter.uppercase() else letter.toString()
        }
        shift.isEnabled = !japanese
        shift.alpha = if (japanese) 0.4f else 1f
    }

    private fun keyParams() = LayoutParams(0, dp(48), 1f).apply {
        marginStart = dp(1)
        marginEnd = dp(1)
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

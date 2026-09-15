package com.kazumaproject.symbol_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.domain.skin.KeyboardSkinId
import com.kazumaproject.core.ui.skin.KeyboardSkinRegistry
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.snippet.SnippetItem
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.data.symbol.SymbolCategory
import com.kazumaproject.domain.EmojiSkinToneSupport
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.ClipboardItemLongClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ImageItemClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val panelTitle: TextView
    private val emptyState: TextView
    private val symbolAdapter = SymbolAdapter()
    private val clipboardAdapter = ClipboardAdapter()
    private val snippetAdapter = SnippetAdapter()
    private var clipboardScrollResetPending = false
    private val gridLM = GridLayoutManager(context, 7, RecyclerView.VERTICAL, false)

    // View References for functional keys
    private val returnButton: ShapeableImageView
    private val searchButton: ShapeableImageView
    private val deleteButton: ShapeableImageView

    // Theme Colors (Default values)
    private var themeBackgroundColor: Int = Color.WHITE
    private var themeIconColor: Int = Color.GRAY
    private var themeSelectedIconColor: Int = Color.BLUE
    private var themeKeyBackgroundColor: Int = Color.LTGRAY
    private var liquidGlassEnable: Boolean = false

    // Flag to check if custom theme is applied
    private var isCustomThemeApplied = false

    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticonMap: Map<EmoticonCategory, List<String>> = emptyMap()
    private var symbolMap: Map<SymbolCategory, List<String>> = emptyMap()

    private var historyEmojiList: MutableList<String> = mutableListOf()
    private var historyEmoticonList: MutableList<String> = mutableListOf()
    private var historySymbolList: MutableList<String> = mutableListOf()

    private var symbolsHistory: List<ClickedSymbol> = emptyList()
    private var clipBoardItems: List<ClipboardItem> = emptyList()
    private var snippetItems: List<SnippetItem> = emptyList()
    private var currentMode: SymbolMode = SymbolMode.EMOJI

    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var skinTonePopup: PopupWindow? = null
    private var defaultEmojiSkinTone: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE

    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var emojiSearchClickListener: (() -> Unit)? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null
    private var itemLongClickListener: SymbolRecyclerViewItemLongClickListener? = null
    private var imageItemClickListener: ImageItemClickListener? = null
    private var clipboardItemClickListener: ((ClipboardItem) -> Unit)? = null
    private var clipboardItemLongClickListener: ClipboardItemLongClickListener? = null
    private var clipboardHistoryToggleListener: ClipboardHistoryToggleListener? = null
    private var snippetItemClickListener: ((SnippetItem) -> Unit)? = null
    private var defaultEmojiSkinToneChangeListener: ((String) -> Unit)? = null
    private var isClipboardHistoryEnabled: Boolean = false
    private var onDeleteFingerUpListener: (() -> Unit)? = null

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)
        panelTitle = findViewById(R.id.symbol_panel_title)
        emptyState = findViewById(R.id.symbol_empty_state)
        returnButton = findViewById(R.id.return_jp_keyboard_button)
        searchButton = findViewById(R.id.emoji_search_button)
        deleteButton = findViewById(R.id.symbol_keyboard_delete_key)

        // Initialize default colors
        themeIconColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        themeSelectedIconColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        themeKeyBackgroundColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)

        recycler.apply {
            layoutManager = gridLM
            adapter = symbolAdapter
            itemAnimator = null
        }

        symbolAdapter.setOnItemClickListener { str ->
            itemClickListener?.onClick(ClickedSymbol(mode = currentMode, symbol = str))
        }

        clipboardAdapter.addOnPagesUpdatedListener {
            if (clipboardScrollResetPending && recycler.adapter === clipboardAdapter &&
                clipboardAdapter.itemCount > 0
            ) {
                recycler.stopScroll()
                gridLM.scrollToPositionWithOffset(0, 0)
                clipboardScrollResetPending = false
            }
        }
        clipboardAdapter.setOnItemClickListener { item ->
            clipboardItemClickListener?.invoke(item)
        }

        clipboardAdapter.setOnItemActionListener { item, action ->
            clipboardItemLongClickListener?.onAction(item, action)
        }

        snippetAdapter.setOnItemClickListener { item ->
            snippetItemClickListener?.invoke(item)
        }

        symbolAdapter.setOnItemLongClickListener { str, pos, anchor ->
            if (
                currentMode == SymbolMode.EMOJI &&
                !isHistoryCategorySelected() &&
                EmojiSkinToneSupport.hasSkinToneVariants(str)
            ) {
                showSkinTonePopup(str, anchor)
                return@setOnItemLongClickListener
            }

            val historyList = when (currentMode) {
                SymbolMode.EMOJI -> historyEmojiList
                SymbolMode.EMOTICON -> historyEmoticonList
                SymbolMode.SYMBOL -> historySymbolList
                else -> emptyList()
            }
            if (historyList.isNotEmpty()
                && categoryTab.selectedTabPosition == 0
                && pos in historyList.indices
            ) {
                itemLongClickListener?.onLongClick(
                    ClickedSymbol(mode = currentMode, symbol = str),
                    position = pos
                )
                when (currentMode) {
                    SymbolMode.EMOJI -> historyEmojiList =
                        historyEmojiList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.EMOTICON -> historyEmoticonList =
                        historyEmoticonList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.SYMBOL -> historySymbolList =
                        historySymbolList.toMutableList().apply { removeAt(pos) }

                    else -> {}
                }
                updateSymbolsForCategory(0)
            }
        }

        returnButton.setOnClickListener {
            returnListener?.onClick()
        }
        searchButton.setOnClickListener {
            emojiSearchClickListener?.invoke()
        }

        deleteButton.apply {
            val handler = Handler(Looper.getMainLooper())
            var isLongPressed = false

            val longPressRunnable = Runnable {
                isLongPressed = true
                deleteLongListener?.onLongClickListener()
            }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressed = false
                        handler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (isLongPressed) {
                            onDeleteFingerUpListener?.invoke()
                        } else {
                            deleteClickListener?.onClick()
                        }
                        true
                    }

                    else -> false
                }
            }
            setOnClickListener(null)
        }

        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = SymbolMode.entries[tab?.position ?: 0]
                buildCategoryTabs()
                categoryTab.getTabAt(0)?.select()
                updateSymbolsForCategory(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateSymbolsForCategory(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * 動的にテーマカラーを適用するメソッド
     */
    // Capture the existing XML appearance before the first override, including tab geometry.
    private class OriginalAppearance(val view: View) {
        val background = view.background
        val tint = view.backgroundTintList
        val padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
        val margins = (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            intArrayOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)
        }
        val clipChildren = (view as? ViewGroup)?.clipChildren
        val clipPadding = (view as? ViewGroup)?.clipToPadding
        val iconTint = (view as? TabLayout)?.tabIconTint
        val textColors = (view as? TabLayout)?.tabTextColors
        val ripple = (view as? TabLayout)?.tabRippleColor
        val imageTint = (view as? android.widget.ImageView)?.imageTintList
        fun restore() {
            view.background = background
            view.backgroundTintList = tint
            view.setPadding(padding[0], padding[1], padding[2], padding[3])
            margins?.let { m -> (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.setMargins(m[0],m[1],m[2],m[3]); view.layoutParams = it
            } }
            (view as? ViewGroup)?.let { it.clipChildren = clipChildren!!; it.clipToPadding = clipPadding!! }
            (view as? TabLayout)?.let { it.tabIconTint = iconTint; it.tabTextColors = textColors; it.tabRippleColor = ripple }
            (view as? android.widget.ImageView)?.let { it.clearColorFilter(); it.imageTintList = imageTint }
        }
    }
    private val originalAppearance = mutableMapOf<View, OriginalAppearance>()
    private var themeRevision = 0
    private fun rememberAppearance(view: View) {
        originalAppearance.getOrPut(view) { OriginalAppearance(view) }
    }

    fun restoreDefaultKeyboardTheme() {
        if (!isCustomThemeApplied) return
        themeRevision++
        skinTonePopup?.dismiss()
        keyboardSkinId = KeyboardSkinId.DEFAULT
        isCustomThemeApplied = false
        liquidGlassEnable = false
        originalAppearance.values.forEach { it.restore() }
        originalAppearance.clear()
        themeBackgroundColor = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)
        themeIconColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
        themeSelectedIconColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
        themeKeyBackgroundColor = ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)
        symbolAdapter.setThemeColors(null, null)
        panelTitle.setTextColor(themeIconColor)
        emptyState.setTextColor(themeIconColor)
        // Rebuild selection colors using the same path as a fresh Default view.
        val mode = currentMode
        buildModeTabs()
        modeTab.getTabAt(mode.ordinal)?.select()
        buildCategoryTabs()
    }

    private var keyboardSkinId = KeyboardSkinId.DEFAULT

    fun setKeyboardTheme(
        @ColorInt backgroundColor: Int,
        @ColorInt iconColor: Int,
        @ColorInt selectedIconColor: Int,
        @ColorInt keyBackgroundColor: Int,
        liquidGlassEnable: Boolean,
        skinId: KeyboardSkinId = KeyboardSkinId.DEFAULT,
    ) {
        themeRevision++
        skinTonePopup?.dismiss()
        listOf(this, categoryTab, modeTab, returnButton, searchButton, deleteButton).forEach(::rememberAppearance)
        listOf(categoryTab, modeTab).forEach { it.getChildAt(0)?.let(::rememberAppearance) }
        keyboardSkinId = skinId
        this.themeBackgroundColor = backgroundColor
        this.themeIconColor = iconColor
        this.themeSelectedIconColor = selectedIconColor
        this.themeKeyBackgroundColor = keyBackgroundColor
        this.isCustomThemeApplied = true
        this.liquidGlassEnable = liquidGlassEnable

        // 1. 全体の背景色
        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        } else {
            this.setBackgroundColor(backgroundColor)
        }

        // 2. ColorStateList の作成
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected)
        )
        val colors = intArrayOf(
            selectedIconColor,
            iconColor
        )
        val tabColorStateList = ColorStateList(states, colors)
        val bgTintList = ColorStateList.valueOf(backgroundColor)

        // 3. Category Tab の全体設定
        categoryTab.backgroundTintList = bgTintList

        categoryTab.tabIconTint = tabColorStateList
        categoryTab.setTabTextColors(iconColor, selectedIconColor)
        categoryTab.setSelectedTabIndicatorColor(selectedIconColor)
        categoryTab.tabRippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(selectedIconColor, 32))

        // ★重要: タブの生成完了を待ってから背景を適用 (postを使用)
        postTabTheme(categoryTab)

        // 4. Mode Tab (Bottom Bar) の全体設定
        modeTab.backgroundTintList = bgTintList
        modeTab.tabIconTint = tabColorStateList
        modeTab.setSelectedTabIndicatorColor(selectedIconColor)
        modeTab.setTabTextColors(iconColor, selectedIconColor)
        modeTab.tabRippleColor = categoryTab.tabRippleColor

        postTabTheme(modeTab)

        // 5. Functional keys use flat, rounded ripples.
        val keyRadius = dpToPx(24).toFloat()
        returnButton.background = utilityRippleDrawable(keyBackgroundColor, keyRadius)
        searchButton.background = utilityRippleDrawable(keyBackgroundColor, keyRadius)
        deleteButton.background = utilityRippleDrawable(keyBackgroundColor, keyRadius)

        val p = dpToPx(8)
        returnButton.setPadding(p, p, p, p)
        searchButton.setPadding(p, p, p, p)
        deleteButton.setPadding(p, p, p, p)

        returnButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        searchButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        deleteButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        panelTitle.setTextColor(iconColor)
        emptyState.setTextColor(iconColor)

        if (currentMode == SymbolMode.CLIPBOARD) {
            buildCategoryTabs()
        }

        symbolAdapter.setThemeColors(
            textColor = iconColor,
            highlightColor = selectedIconColor
        )
    }

    private fun postTabTheme(tabLayout: TabLayout) {
        val revision = themeRevision
        tabLayout.post {
            if (!isCustomThemeApplied || revision != themeRevision) return@post
            val strip = tabLayout.getChildAt(0) as? ViewGroup ?: return@post
            for (i in 0 until strip.childCount) {
                val tabView = strip.getChildAt(i)
                rememberAppearance(tabView)
                tabView.background = utilityRippleDrawable(themeBackgroundColor, dpToPx(12).toFloat())
            }
        }
    }

    // Utility surfaces use flat tonal selection and ripples, independently of key skins.
    private fun utilityRippleDrawable(@ColorInt baseColor: Int, radius: Float): Drawable {
        fun surface(color: Int) = GradientDrawable().apply {
            cornerRadius = radius
            setColor(color)
        }
        val content = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), surface(
                ColorUtils.blendARGB(baseColor, themeSelectedIconColor, 0.12f)
            ))
            addState(intArrayOf(), surface(Color.TRANSPARENT))
        }
        return RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(themeSelectedIconColor, 32)),
            content,
            surface(Color.WHITE),
        )
    }

    fun setOnDeleteButtonFingerUpListener(listener: () -> Unit) {
        onDeleteFingerUpListener = listener
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            private val SWIPE_DISTANCE_THRESHOLD = 30f
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y

                if (kotlin.math.abs(dx) > SWIPE_DISTANCE_THRESHOLD
                    && kotlin.math.abs(vx) > SWIPE_VELOCITY_THRESHOLD
                    && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                ) {
                    if (dx > 0) selectPreviousCategory()
                    else selectNextCategory()
                    return true
                }
                return false
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val loc = IntArray(2).also { recycler.getLocationOnScreen(it) }
        val y = ev.rawY
        if (y >= loc[1] && y <= loc[1] + recycler.height) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    fun setOnReturnToTenKeyButtonClickListener(l: ReturnToTenKeyButtonClickListener) {
        returnListener = l
    }

    fun setOnEmojiSearchClickListener(listener: () -> Unit) {
        emojiSearchClickListener = listener
    }

    fun setOnDeleteButtonSymbolViewClickListener(l: DeleteButtonSymbolViewClickListener) {
        deleteClickListener = l
    }

    fun setOnDeleteButtonSymbolViewLongClickListener(l: DeleteButtonSymbolViewLongClickListener) {
        deleteLongListener = l
    }

    fun setOnSymbolRecyclerViewItemClickListener(l: SymbolRecyclerViewItemClickListener) {
        itemClickListener = l
    }

    fun setOnSymbolRecyclerViewItemLongClickListener(l: SymbolRecyclerViewItemLongClickListener) {
        itemLongClickListener = l
    }

    fun setOnImageItemClickListener(l: ImageItemClickListener) {
        imageItemClickListener = l
    }

    fun setOnClipboardItemClickListener(l: (ClipboardItem) -> Unit) {
        clipboardItemClickListener = l
    }

    fun setOnClipboardItemLongClickListener(l: ClipboardItemLongClickListener) {
        clipboardItemLongClickListener = l
    }

    fun updateClipboardItems(newItems: List<ClipboardItem>) {
        this.clipBoardItems = newItems
        if (currentMode == SymbolMode.CLIPBOARD) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    fun setOnSnippetItemClickListener(l: (SnippetItem) -> Unit) {
        snippetItemClickListener = l
    }

    fun updateSnippetItems(newItems: List<SnippetItem>) {
        this.snippetItems = newItems
        if (currentMode == SymbolMode.SNIPPET) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    fun setClipboardHistoryEnabled(isEnabled: Boolean) {
        this.isClipboardHistoryEnabled = isEnabled
        if (currentMode == SymbolMode.CLIPBOARD) {
            categoryTab.getTabAt(0)?.customView?.let {
                val switch = it.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                switch?.isChecked = isEnabled
            }
        }
    }

    fun setOnClipboardHistoryToggleListener(l: ClipboardHistoryToggleListener) {
        this.clipboardHistoryToggleListener = l
    }

    fun setOnDefaultEmojiSkinToneChangeListener(l: (String) -> Unit) {
        defaultEmojiSkinToneChangeListener = l
    }

    fun setDefaultEmojiSkinTone(skinTone: String) {
        defaultEmojiSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(skinTone)) {
                skinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }
        if (currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<Emoticon>,
        symbols: List<Symbol>,
        clipBoardItems: List<ClipboardItem>,
        symbolsHistory: List<ClickedSymbol>,
        symbolMode: SymbolMode = SymbolMode.EMOJI,
        defaultEmojiSkinTone: String = EmojiSkinToneSupport.DEFAULT_SKIN_TONE,
        snippets: List<SnippetItem> = emptyList(),
    ) {
        this.symbolsHistory = symbolsHistory
        this.clipBoardItems = clipBoardItems
        this.snippetItems = snippets
        this.defaultEmojiSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(defaultEmojiSkinTone)) {
                defaultEmojiSkinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }

        historyEmojiList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOJI }
            .map { it.symbol }
            .toMutableList()
        historyEmoticonList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOTICON }
            .map { it.symbol }
            .toMutableList()
        historySymbolList = symbolsHistory
            .filter { it.mode == SymbolMode.SYMBOL }
            .map { it.symbol }
            .toMutableList()

        this.emoticonMap = emoticons
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.symbolMap = symbols
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.emojiMap = emojiList
            .groupBy { it.category }
            .toSortedMap(categoryOrder)

        currentMode = symbolMode
        buildModeTabs()
        buildCategoryTabs()
        modeTab.getTabAt(symbolMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)
    }

    private fun buildModeTabs() {
        modeTab.removeAllTabs()
        listOf(
            R.string.symbol_mode_emoji,
            R.string.symbol_mode_emoticon,
            R.string.symbol_mode_symbol,
            R.string.symbol_mode_clipboard,
            R.string.symbol_mode_snippet,
        ).forEach { res ->
            modeTab.addTab(modeTab.newTab().setText(res), false)
        }
        updateModeTabMode()
        modeTab.setTabTextColors(themeIconColor, themeSelectedIconColor)
        modeTab.setSelectedTabIndicatorColor(themeSelectedIconColor)

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            // postを使って描画後に適用
            postTabTheme(modeTab)
        }
    }

    private fun buildCategoryTabs() {
        categoryTab.removeAllTabs()
        val historyIcon = com.kazumaproject.core.R.drawable.history_24dp

        val normalColor = themeIconColor
        val selectedColor = themeSelectedIconColor

        categoryTab.setTabTextColors(normalColor, selectedColor)
        categoryTab.setSelectedTabIndicatorColor(selectedColor)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected)
        )
        val colors = intArrayOf(
            selectedColor,
            normalColor
        )
        val tabColorStateList = ColorStateList(states, colors)
        categoryTab.tabIconTint = tabColorStateList

        when (currentMode) {
            SymbolMode.EMOJI -> {
                if (historyEmojiList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon).setContentDescription(R.string.symbol_history))
                }
                emojiMap.keys.forEach { cat ->
                    categoryTab.addTab(
                        categoryTab.newTab().setIcon(
                            categoryIconRes[cat] ?: com.kazumaproject.core.R.drawable.logo_key
                        ).setContentDescription(emojiCategoryLabelRes.getValue(cat))
                    )
                }
            }

            SymbolMode.EMOTICON -> {
                if (historyEmoticonList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon).setContentDescription(R.string.symbol_history))
                }
                val orderedKeys = EmoticonCategory.entries
                orderedKeys.forEach { category ->
                    if (emoticonMap.containsKey(category)) {
                        val tabText = when (category) {
                            EmoticonCategory.SMILE -> "笑顔"
                            EmoticonCategory.SWEAT -> "焦っている顔"
                            EmoticonCategory.SURPRISE -> "驚いている顔"
                            EmoticonCategory.SADNESS -> "泣いている顔"
                            EmoticonCategory.DISPLEASURE -> "不満げな顔"
                            EmoticonCategory.UNKNOWN -> "その他"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.SYMBOL -> {
                if (historySymbolList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon).setContentDescription(R.string.symbol_history))
                }
                val orderedKeys = SymbolCategory.entries
                orderedKeys.forEach { category ->
                    if (symbolMap.containsKey(category)) {
                        val tabText = when (category) {
                            SymbolCategory.BRACKETS_AND_QUOTES -> "括弧と引用符"
                            SymbolCategory.PUNCTUATION_AND_DIACRITICS -> "区切り文字と発音区別符号"
                            SymbolCategory.Hankaku -> "半角"
                            SymbolCategory.GENERAL -> "全般"
                            SymbolCategory.ARROWS -> "矢印"
                            SymbolCategory.MATH_AND_UNITS -> "数学と単位"
                            SymbolCategory.GEOMETRIC_SHAPES -> "図形"
                            SymbolCategory.ALPHABET_LATIN -> "ラテン文字"
                            SymbolCategory.ALPHABET_GREEK -> "ギリシャ文字"
                            SymbolCategory.ALPHABET_CYRILLIC -> "キリル文字"
                            SymbolCategory.BOX_DRAWING -> "罫線"
                            SymbolCategory.PICTOGRAPHS_AND_ICONS -> "アイコン"
                            SymbolCategory.ROMAN_NUMERALS -> "ローマ数字"
                            SymbolCategory.ENCLOSED_CHARACTERS -> "囲み文字"
                            SymbolCategory.PHONETIC_SYMBOLS -> "発音記号"
                            SymbolCategory.JAPANESE_KANA_AND_VARIANTS -> "日本語仮名・特殊文字"
                            SymbolCategory.CJK_AND_RADICALS -> "CJK・部首"
                            SymbolCategory.CONTROL_CHARACTERS -> "制御文字"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.CLIPBOARD -> {
                val tab = categoryTab.newTab().setCustomView(R.layout.custom_tab_clipboard)
                categoryTab.addTab(tab)
                tab.customView?.let { customView ->
                    val switch = customView.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                    switch.isChecked = isClipboardHistoryEnabled
                    switch.setOnCheckedChangeListener { _, isChecked ->
                        isClipboardHistoryEnabled = isChecked
                        clipboardHistoryToggleListener?.onToggled(isChecked)
                    }
                    customView.findViewById<TextView>(R.id.clipboard_tab_text)
                        .setTextColor(normalColor)
                }
            }

            SymbolMode.SNIPPET -> {
                categoryTab.addTab(categoryTab.newTab().setText(R.string.symbol_mode_snippet))
            }
        }

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            // postを使って描画後に適用
            postTabTheme(categoryTab)
        }
    }

    private fun buildClipboardListItems(items: List<ClipboardItem>): List<ClipboardListItem> {
        val pinned = items.filter { it.isPinned() }
        val unpinned = items.filterNot { it.isPinned() }
        return buildList {
            if (pinned.isNotEmpty()) {
                add(ClipboardListItem.Header(resources.getString(R.string.symbol_clipboard_section_pinned)))
                addAll(pinned.map { ClipboardListItem.Content(it) })
            }
            if (unpinned.isNotEmpty()) {
                add(ClipboardListItem.Header(resources.getString(R.string.symbol_clipboard_section_unpinned)))
                addAll(unpinned.map { ClipboardListItem.Content(it) })
            }
        }
    }

    private fun ClipboardItem.isPinned(): Boolean {
        return when (this) {
            is ClipboardItem.Image -> isPinned
            is ClipboardItem.Text -> isPinned
            ClipboardItem.Empty -> false
        }
    }

    private fun updateSymbolsForCategory(index: Int) {
        clipboardScrollResetPending = currentMode == SymbolMode.CLIPBOARD
        val modeLabel = when (currentMode) {
            SymbolMode.EMOJI -> R.string.symbol_mode_emoji
            SymbolMode.EMOTICON -> R.string.symbol_mode_emoticon
            SymbolMode.SYMBOL -> R.string.symbol_mode_symbol
            SymbolMode.CLIPBOARD -> R.string.symbol_mode_clipboard
            SymbolMode.SNIPPET -> R.string.symbol_mode_snippet
        }
        val selectedTab = categoryTab.getTabAt(index)
        panelTitle.text = selectedTab?.text ?: selectedTab?.contentDescription ?: context.getString(modeLabel)
        searchButton.visibility = if (currentMode == SymbolMode.EMOJI) View.VISIBLE else View.GONE
        emptyState.visibility = View.GONE
        skinTonePopup?.dismiss()
        pagingJob?.cancel()
        if (currentMode == SymbolMode.SNIPPET) {
            // スニペットは少数なので Paging を使わず ListAdapter で直接表示する。
            recycler.adapter = snippetAdapter
            gridLM.spanSizeLookup = GridLayoutManager.DefaultSpanSizeLookup()
            updateGridColumns()
            emptyState.setText(R.string.symbol_empty_snippet)
            emptyState.visibility = if (snippetItems.isEmpty()) View.VISIBLE else View.GONE
            snippetAdapter.submitList(snippetItems)
            recycler.scrollToPosition(0)
            return
        }
        lifecycleOwner?.let { owner ->
            pagingJob = owner.lifecycleScope.launch {
                symbolAdapter.submitData(PagingData.empty())
                clipboardAdapter.submitData(PagingData.empty())
                recycler.scrollToPosition(0)

                when (currentMode) {
                    SymbolMode.CLIPBOARD -> {
                        recycler.adapter = clipboardAdapter
                        gridLM.spanCount =
                            (availableGridWidthDp() / 200).coerceIn(1, 4)
                        gridLM.orientation = RecyclerView.VERTICAL
                        gridLM.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                            override fun getSpanSize(position: Int): Int {
                                return if (clipboardAdapter.isHeader(position)) gridLM.spanCount else 1
                            }
                        }
                        val clipboardListItems = buildClipboardListItems(clipBoardItems)
                        emptyState.setText(R.string.symbol_empty_clipboard)
                        emptyState.visibility = if (clipboardListItems.isEmpty()) View.VISIBLE else View.GONE
                        Pager(
                            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                            pagingSourceFactory = { ClipboardPagingSource(clipboardListItems) }
                        ).flow.collectLatest { clipboardAdapter.submitData(it) }
                    }

                    else -> {
                        recycler.adapter = symbolAdapter
                        gridLM.spanSizeLookup = GridLayoutManager.DefaultSpanSizeLookup()
                        val listForPaging = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                val hasHistory = historyEmojiList.isNotEmpty()
                                if (hasHistory && index == 0) historyEmojiList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    emojiMap.keys.elementAtOrNull(adj)
                                        ?.let {
                                            emojiMap[it]?.map { e ->
                                                EmojiSkinToneSupport.withSkinTone(
                                                    e.symbol,
                                                    this@CustomSymbolKeyboardView.defaultEmojiSkinTone
                                                )
                                            }
                                        } ?: emptyList()
                                }
                            }

                            SymbolMode.EMOTICON -> {
                                val hasHistory = historyEmoticonList.isNotEmpty()
                                if (hasHistory && index == 0) historyEmoticonList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = EmoticonCategory.entries
                                        .filter { emoticonMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { emoticonMap[it] }
                                        ?: emptyList()
                                }
                            }

                            SymbolMode.SYMBOL -> {
                                val hasHistory = historySymbolList.isNotEmpty()
                                if (hasHistory && index == 0) historySymbolList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = SymbolCategory.entries
                                        .filter { symbolMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { symbolMap[it] }
                                        ?: emptyList()
                                }
                            }

                            else -> emptyList()
                        }

                        symbolAdapter.setItemMargins(2, 2, context)
                        symbolAdapter.showSkinToneIndicators =
                            currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()
                        symbolAdapter.symbolTextSize = when (currentMode) {
                            SymbolMode.EMOJI -> 30f
                            SymbolMode.EMOTICON -> 16f
                            SymbolMode.SYMBOL -> 20f
                            SymbolMode.CLIPBOARD, SymbolMode.SNIPPET -> 16f
                        }
                        updateGridColumns()
                        emptyState.setText(R.string.symbol_empty_history)
                        emptyState.visibility = if (listForPaging.isEmpty()) View.VISIBLE else View.GONE

                        Pager(
                            config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                            pagingSourceFactory = { SymbolPagingSource(listForPaging) }
                        ).flow.collectLatest { symbolAdapter.submitData(it) }
                    }
                }
            }
        }
    }

    private fun availableGridWidthDp(): Int {
        val pixels = recycler.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return ((pixels - recycler.paddingLeft - recycler.paddingRight) / resources.displayMetrics.density).toInt()
    }

    private fun updateGridColumns() {
        gridLM.orientation = RecyclerView.VERTICAL
        gridLM.spanCount = when (currentMode) {
            SymbolMode.EMOJI -> (availableGridWidthDp() / 56).coerceIn(4, 18)
            SymbolMode.EMOTICON -> (availableGridWidthDp() / 140).coerceIn(1, 6)
            SymbolMode.SYMBOL -> (availableGridWidthDp() / 64).coerceIn(3, 16)
            SymbolMode.CLIPBOARD, SymbolMode.SNIPPET -> (availableGridWidthDp() / 200).coerceIn(1, 4)
        }
    }

    /**
     * モードタブは等幅 (fixed) を基本にするが、狭い画面で 5 タブ分のラベルが収まらない場合は
     * 折り返しや省略で崩れるのを避けるためスクロール可能にする。
     */
    private fun updateModeTabMode() {
        val tabCount = modeTab.tabCount.takeIf { it > 0 } ?: return
        val widthPx = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val perTabDp = widthPx / resources.displayMetrics.density / tabCount
        val mode = if (perTabDp >= MODE_TAB_MIN_WIDTH_DP) TabLayout.MODE_FIXED else TabLayout.MODE_SCROLLABLE
        if (modeTab.tabMode != mode) {
            modeTab.tabMode = mode
            modeTab.tabGravity =
                if (mode == TabLayout.MODE_FIXED) TabLayout.GRAVITY_FILL else TabLayout.GRAVITY_START
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            updateModeTabMode()
            recycler.post { updateGridColumns() }
        }
    }

    private fun showSkinTonePopup(symbol: String, anchor: View) {
        val variants = EmojiSkinToneSupport.skinToneVariants(symbol)
        if (variants.isEmpty() || !anchor.isAttachedToWindow) return

        skinTonePopup?.dismiss()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(6), dpToPx(5), dpToPx(6), dpToPx(5))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12).toFloat()
                setColor(themeKeyBackgroundColor)
            }
        }

        KeyboardSkinRegistry.find(keyboardSkinId)?.let { skin ->
            content.background = skin.popupDrawable(resources, com.kazumaproject.core.ui.skin.PopupDirection.CENTER)
            skin.showPopup(content)
        }
        variants.forEach { variant ->
            content.addView(
                TextView(context).apply {
                    text = variant
                    textSize =
                        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 30f else 26f
                    gravity = android.view.Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(themeIconColor)
                    layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(44))
                    setOnClickListener {
                        skinTonePopup?.dismiss()
                        updateDefaultEmojiSkinTone(
                            EmojiSkinToneSupport.skinToneValueFromEmoji(variant)
                        )
                        itemClickListener?.onClick(
                            ClickedSymbol(mode = SymbolMode.EMOJI, symbol = variant)
                        )
                    }
                }
            )
        }

        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        skinTonePopup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = if (keyboardSkinId == KeyboardSkinId.DEFAULT) dpToPx(8).toFloat() else 0f
            if (keyboardSkinId != KeyboardSkinId.DEFAULT) animationStyle = 0
            showAsDropDown(
                anchor,
                (anchor.width - content.measuredWidth) / 2,
                -anchor.height - content.measuredHeight
            )
        }
    }

    private fun updateDefaultEmojiSkinTone(skinTone: String) {
        val supportedSkinTone =
            if (EmojiSkinToneSupport.isSupportedSkinToneValue(skinTone)) {
                skinTone
            } else {
                EmojiSkinToneSupport.DEFAULT_SKIN_TONE
            }
        if (defaultEmojiSkinTone == supportedSkinTone) return

        defaultEmojiSkinTone = supportedSkinTone
        defaultEmojiSkinToneChangeListener?.invoke(supportedSkinTone)
        if (currentMode == SymbolMode.EMOJI && !isHistoryCategorySelected()) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    private fun isHistoryCategorySelected(): Boolean {
        return when (currentMode) {
            SymbolMode.EMOJI -> historyEmojiList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.EMOTICON -> historyEmoticonList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.SYMBOL -> historySymbolList.isNotEmpty() && categoryTab.selectedTabPosition == 0
            SymbolMode.CLIPBOARD, SymbolMode.SNIPPET -> false
        }
    }

    private fun selectPreviousCategory() {
        val i = categoryTab.selectedTabPosition
        if (i > 0) categoryTab.getTabAt(i - 1)?.select()
    }

    private fun selectNextCategory() {
        val i = categoryTab.selectedTabPosition
        val last = categoryTab.tabCount - 1
        if (i < last) categoryTab.getTabAt(i + 1)?.select()
    }

    /**
     * TenKeyと同じ色計算ロジック
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    fun release() {
        skinTonePopup?.dismiss()
        skinTonePopup = null
        pagingJob?.cancel()
        pagingJob = null
        lifecycleOwner = null
        returnListener = null
        deleteClickListener = null
        deleteLongListener = null
        itemClickListener = null
        itemLongClickListener = null
        imageItemClickListener = null
        clipboardItemClickListener = null
        clipboardItemLongClickListener = null
        clipboardHistoryToggleListener = null
        defaultEmojiSkinToneChangeListener = null
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private val emojiCategoryLabelRes = mapOf(
        EmojiCategory.SMILEYS_EMOTION to R.string.emoji_category_smileys_emotion,
        EmojiCategory.PEOPLE_BODY to R.string.emoji_category_people_body,
        EmojiCategory.ANIMALS_NATURE to R.string.emoji_category_animals_nature,
        EmojiCategory.FOOD_DRINK to R.string.emoji_category_food_drink,
        EmojiCategory.TRAVEL_PLACES to R.string.emoji_category_travel_places,
        EmojiCategory.ACTIVITIES to R.string.emoji_category_activities,
        EmojiCategory.OBJECTS to R.string.emoji_category_objects,
        EmojiCategory.SYMBOLS to R.string.emoji_category_symbols,
        EmojiCategory.FLAGS to R.string.emoji_category_flags,
        EmojiCategory.UNKNOWN to R.string.emoji_category_unknown,
    )

    private val categoryIconRes = mapOf(
        EmojiCategory.SMILEYS_EMOTION to com.kazumaproject.core.R.drawable.mood_24px,
        EmojiCategory.PEOPLE_BODY to com.kazumaproject.core.R.drawable.person_24dp,
        EmojiCategory.ANIMALS_NATURE to com.kazumaproject.core.R.drawable.pets_24dp,
        EmojiCategory.FOOD_DRINK to com.kazumaproject.core.R.drawable.fastfood_24dp,
        EmojiCategory.TRAVEL_PLACES to com.kazumaproject.core.R.drawable.travel_explore_24dp,
        EmojiCategory.ACTIVITIES to com.kazumaproject.core.R.drawable.celebration_24dp,
        EmojiCategory.OBJECTS to com.kazumaproject.core.R.drawable.lightbulb_24dp,
        EmojiCategory.SYMBOLS to com.kazumaproject.core.R.drawable.emoji_symbols,
        EmojiCategory.FLAGS to com.kazumaproject.core.R.drawable.flag_24dp,
        EmojiCategory.UNKNOWN to com.kazumaproject.core.R.drawable.question_mark_24dp
    )

    private val categoryOrder = compareBy<EmojiCategory> { it.ordinal }

    fun switchToClipboardMode(selectCategoryIndex: Int = 0) {
        // Modeを切り替え
        currentMode = SymbolMode.CLIPBOARD

        // ModeTab / CategoryTab を作り直す（CLIPBOARD用タブを生成するため）
        buildModeTabs()
        buildCategoryTabs()

        // UI上の選択状態も同期
        modeTab.getTabAt(SymbolMode.CLIPBOARD.ordinal)?.select()

        // CLIPBOARDのカテゴリは基本1つなので 0 を選択
        categoryTab.getTabAt(selectCategoryIndex.coerceIn(0, categoryTab.tabCount - 1))?.select()

        // Recycler表示更新
        updateSymbolsForCategory(categoryTab.selectedTabPosition)
    }

    fun switchToSymbolMode(mode: SymbolMode, selectCategoryIndex: Int = 0) {
        currentMode = mode
        buildModeTabs()
        buildCategoryTabs()
        modeTab.getTabAt(mode.ordinal)?.select()
        categoryTab.getTabAt(selectCategoryIndex.coerceIn(0, categoryTab.tabCount - 1))?.select()
        updateSymbolsForCategory(categoryTab.selectedTabPosition)
    }

    companion object {
        /** 「スニペット」「Emoticons」程度のラベルが 14sp で折り返さずに収まる 1 タブあたりの幅。 */
        private const val MODE_TAB_MIN_WIDTH_DP = 88f
    }
}

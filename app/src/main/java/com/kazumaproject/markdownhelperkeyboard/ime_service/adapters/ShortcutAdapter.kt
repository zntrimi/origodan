package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.animation.ValueAnimator
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.R as CoreR
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

internal class ShortcutIconColorState {
    var iconColor: Int? = null
        private set

    fun setIconColor(color: Int?): Boolean {
        if (iconColor == color) return false
        iconColor = color
        return true
    }
}

class ShortcutAdapter : ListAdapter<ShortcutType, ShortcutAdapter.ViewHolder>(DiffCallback) {

    /**
     * A listener that gets called when an item is clicked.
     * The listener receives the resource ID of the clicked item.
     */
    var onItemClicked: ((ShortcutType) -> Unit)? = null

    private val iconColorState = ShortcutIconColorState()
    private var activeShortcutTypes: Set<ShortcutType> = emptySet()
    private var toolbarHeightPx: Int = 0
    private var iconSizePx: Int = 0

    /**
     * ViewHolder now captures clicks and calls the adapter's listener.
     * It's an 'inner class' to access the adapter's onItemClicked property.
     */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.item_image)
        private var voicePulseAnimator: ValueAnimator? = null

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClicked?.invoke(getItem(position))
                }
            }
        }

        fun setVoiceInputActive(active: Boolean) {
            voicePulseAnimator?.cancel()
            voicePulseAnimator = null
            imageView.alpha = 1f
            imageView.scaleX = 1f
            imageView.scaleY = 1f
            if (!active) return

            imageView.setColorFilter(
                ContextCompat.getColor(imageView.context, CoreR.color.red),
                PorterDuff.Mode.SRC_IN,
            )
            voicePulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 560L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    imageView.alpha = 1f - (0.42f * progress)
                    val scale = 1f + (0.16f * progress)
                    imageView.scaleX = scale
                    imageView.scaleY = scale
                }
                start()
            }
        }

        fun recycle() {
            voicePulseAnimator?.cancel()
            voicePulseAnimator = null
            imageView.clearAnimation()
            imageView.alpha = 1f
            imageView.scaleX = 1f
            imageView.scaleY = 1f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut_toolbar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        applyShortcutToolbarSize(holder)
        holder.imageView.setImageResource(item.resolveIconResId()) // Enumからアイコン取得

        // ★追加: 色が設定されていれば適用し、なければ解除する
        iconColorState.iconColor?.let { color ->
            holder.imageView.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        } ?: run {
            holder.imageView.clearColorFilter()
        }
        holder.setVoiceInputActive(
            active = item == ShortcutType.VOICE_INPUT && item in activeShortcutTypes,
        )
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.recycle()
        super.onViewRecycled(holder)
    }

    fun setShortcutToolbarSize(
        toolbarHeightPx: Int,
        iconSizePx: Int
    ) {
        if (
            this.toolbarHeightPx == toolbarHeightPx &&
            this.iconSizePx == iconSizePx
        ) {
            return
        }
        this.toolbarHeightPx = toolbarHeightPx
        this.iconSizePx = iconSizePx
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    // ★追加: 外部から色を設定するメソッド
    fun setIconColor(color: Int?) {
        if (!iconColorState.setIconColor(color)) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun setActiveShortcutTypes(activeTypes: Set<ShortcutType>) {
        if (activeShortcutTypes == activeTypes) return
        val oldActive = activeShortcutTypes
        activeShortcutTypes = activeTypes

        (oldActive union activeTypes).forEach { type ->
            val index = currentList.indexOf(type)
            if (index >= 0) {
                notifyItemChanged(index)
            }
        }
    }

    fun setKeyboardLayoutEditActive(active: Boolean) {
        setActiveShortcutTypes(
            if (active) {
                activeShortcutTypes + ShortcutType.KEYBOARD_LAYOUT_EDIT
            } else {
                activeShortcutTypes - ShortcutType.KEYBOARD_LAYOUT_EDIT
            }
        )
    }

    private fun applyShortcutToolbarSize(holder: ViewHolder) {
        if (toolbarHeightPx <= 0 || iconSizePx <= 0) return
        val context = holder.itemView.context
        val itemMinWidthPx = context.dpToPx(64)
        val horizontalPaddingPx = context.dpToPx(36)
        val itemWidthPx = maxOf(itemMinWidthPx, iconSizePx + horizontalPaddingPx)

        holder.itemView.layoutParams = holder.itemView.layoutParams.apply {
            width = itemWidthPx
            height = toolbarHeightPx
        }
        holder.imageView.layoutParams = holder.imageView.layoutParams.apply {
            width = iconSizePx
            height = iconSizePx
        }
    }

    private fun ShortcutType.resolveIconResId(): Int {
        return if (this in activeShortcutTypes) {
            activeIconResId ?: iconResId
        } else {
            iconResId
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ShortcutType>() {
        override fun areItemsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ShortcutType, newItem: ShortcutType): Boolean =
            oldItem == newItem
    }
}

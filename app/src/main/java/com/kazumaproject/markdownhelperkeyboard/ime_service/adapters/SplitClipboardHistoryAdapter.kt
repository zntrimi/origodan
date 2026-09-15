package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.core.data.snippet.SnippetItem
import com.kazumaproject.markdownhelperkeyboard.R

/**
 * Mirror GODAN の中央に出す一覧の 1 行。
 * スニペットを先頭に、続けてクリップボード履歴を同じカードで並べる。
 */
sealed class SplitUtilityItem {
    data class Snippet(val item: SnippetItem) : SplitUtilityItem()
    data class Clipboard(val item: ClipboardItem) : SplitUtilityItem()
}

class SplitClipboardHistoryAdapter :
    ListAdapter<SplitUtilityItem, SplitClipboardHistoryAdapter.ViewHolder>(DiffCallback) {

    var onItemClick: ((SplitUtilityItem) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.split_clipboard_image)
        private val label: MaterialTextView = view.findViewById(R.id.split_clipboard_label)
        private val text: MaterialTextView = view.findViewById(R.id.split_clipboard_text)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onItemClick?.invoke(getItem(position))
            }
        }

        fun bind(entry: SplitUtilityItem) {
            when (entry) {
                is SplitUtilityItem.Snippet -> {
                    setIcon(com.kazumaproject.core.R.drawable.text_snippet_24px)
                    label.text = entry.item.label
                    label.isVisible = entry.item.label.isNotBlank()
                    text.text = entry.item.text
                    text.visibility = View.VISIBLE
                }

                is SplitUtilityItem.Clipboard -> {
                    label.isVisible = false
                    bindClipboard(entry.item)
                }
            }
        }

        private fun bindClipboard(item: ClipboardItem) {
            when (item) {
                is ClipboardItem.Text -> {
                    setIcon(com.kazumaproject.core.R.drawable.content_paste_24px)
                    text.text = item.text
                    text.visibility = View.VISIBLE
                }

                is ClipboardItem.Image -> {
                    image.clearColorFilter()
                    image.setImageBitmap(item.bitmap)
                    image.scaleType = ImageView.ScaleType.CENTER_CROP
                    text.text = itemView.context.getString(R.string.clipboard_image_preview_label)
                    text.visibility = View.VISIBLE
                }

                ClipboardItem.Empty -> {
                    image.setImageDrawable(null)
                    text.visibility = View.GONE
                }
            }
        }

        private fun setIcon(resId: Int) {
            image.setImageResource(resId)
            image.scaleType = ImageView.ScaleType.CENTER_INSIDE
            image.setColorFilter(
                itemView.context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color),
                PorterDuff.Mode.SRC_IN,
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_split_clipboard_history, parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object DiffCallback : DiffUtil.ItemCallback<SplitUtilityItem>() {
        override fun areItemsTheSame(oldItem: SplitUtilityItem, newItem: SplitUtilityItem): Boolean =
            oldItem.stableKey() == newItem.stableKey()

        override fun areContentsTheSame(oldItem: SplitUtilityItem, newItem: SplitUtilityItem): Boolean =
            oldItem == newItem

        private fun SplitUtilityItem.stableKey(): Pair<Int, Long> = when (this) {
            is SplitUtilityItem.Snippet -> 0 to item.id
            is SplitUtilityItem.Clipboard -> 1 to when (val c = item) {
                is ClipboardItem.Text -> c.id
                is ClipboardItem.Image -> c.id
                ClipboardItem.Empty -> Long.MIN_VALUE
            }
        }
    }
}

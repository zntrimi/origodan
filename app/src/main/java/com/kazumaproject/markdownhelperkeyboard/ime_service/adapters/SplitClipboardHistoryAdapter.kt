package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.markdownhelperkeyboard.R

class SplitClipboardHistoryAdapter :
    ListAdapter<ClipboardItem, SplitClipboardHistoryAdapter.ViewHolder>(DiffCallback) {

    var onItemClick: ((ClipboardItem) -> Unit)? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image: ImageView = view.findViewById(R.id.split_clipboard_image)
        private val text: MaterialTextView = view.findViewById(R.id.split_clipboard_text)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onItemClick?.invoke(getItem(position))
            }
        }

        fun bind(item: ClipboardItem) {
            when (item) {
                is ClipboardItem.Text -> {
                    image.setImageResource(com.kazumaproject.core.R.drawable.content_paste_24px)
                    image.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    image.setColorFilter(
                        itemView.context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color),
                        PorterDuff.Mode.SRC_IN,
                    )
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_split_clipboard_history, parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object DiffCallback : DiffUtil.ItemCallback<ClipboardItem>() {
        override fun areItemsTheSame(oldItem: ClipboardItem, newItem: ClipboardItem): Boolean =
            oldItem.itemId() == newItem.itemId()

        override fun areContentsTheSame(oldItem: ClipboardItem, newItem: ClipboardItem): Boolean =
            oldItem == newItem

        private fun ClipboardItem.itemId(): Long = when (this) {
            is ClipboardItem.Text -> id
            is ClipboardItem.Image -> id
            ClipboardItem.Empty -> Long.MIN_VALUE
        }
    }
}

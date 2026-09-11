package com.kazumaproject.markdownhelperkeyboard.ime_service.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.short_cut.ShortcutType

class ShortcutPanelAdapter : RecyclerView.Adapter<ShortcutPanelAdapter.ViewHolder>() {
    private var items: List<ShortcutType> = emptyList()
    var onItemClick: ((ShortcutType) -> Unit)? = null

    fun submitList(newItems: List<ShortcutType>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.shortcut_panel_icon)
        private val label: MaterialTextView = view.findViewById(R.id.shortcut_panel_label)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onItemClick?.invoke(items[position])
            }
        }

        fun bind(item: ShortcutType) {
            icon.setImageResource(item.iconResId)
            label.text = item.description
            itemView.contentDescription = item.description
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut_panel, parent, false),
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}

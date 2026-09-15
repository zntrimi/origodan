package com.kazumaproject.symbol_keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.snippet.SnippetItem

class SnippetAdapter : ListAdapter<SnippetItem, SnippetAdapter.SnippetViewHolder>(DIFF_CALLBACK) {

    private var onItemClickListener: ((SnippetItem) -> Unit)? = null

    fun setOnItemClickListener(listener: (SnippetItem) -> Unit) {
        this.onItemClickListener = listener
    }

    inner class SnippetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val labelView: MaterialTextView = itemView.findViewById(R.id.snippet_label_view)
        private val textView: MaterialTextView = itemView.findViewById(R.id.snippet_text_view)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                onItemClickListener?.invoke(getItem(position))
            }
        }

        fun bind(item: SnippetItem) {
            labelView.text = item.label
            labelView.visibility = if (item.label.isBlank()) View.GONE else View.VISIBLE
            textView.text = item.text
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.snippet_item_view, parent, false)
        return SnippetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SnippetItem>() {
            override fun areItemsTheSame(oldItem: SnippetItem, newItem: SnippetItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SnippetItem, newItem: SnippetItem): Boolean =
                oldItem == newItem
        }
    }
}

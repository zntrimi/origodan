package com.kazumaproject.markdownhelperkeyboard.snippet.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.snippet.database.Snippet

class SnippetAdapter(
    private val onClick: (Snippet) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : ListAdapter<Snippet, SnippetAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.text_view_snippet_label)
        private val text: TextView = view.findViewById(R.id.text_view_snippet_text)
        private val handle: ImageView = view.findViewById(R.id.snippet_drag_handle)

        @SuppressLint("ClickableViewAccessibility")
        fun bind(snippet: Snippet) {
            label.text = snippet.label
            label.isVisible = snippet.label.isNotBlank()
            text.text = snippet.text
            itemView.setOnClickListener { onClick(snippet) }
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(this)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_snippet, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    /** ドラッグ中の見た目を即時に動かすため、DB 保存前にローカルのリストを並び替える。 */
    fun moveItem(from: Int, to: Int): List<Snippet> {
        val list = currentList.toMutableList()
        if (from !in list.indices || to !in list.indices) return list
        list.add(to, list.removeAt(from))
        submitList(list)
        return list
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Snippet>() {
            override fun areItemsTheSame(oldItem: Snippet, newItem: Snippet) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Snippet, newItem: Snippet) = oldItem == newItem
        }
    }
}

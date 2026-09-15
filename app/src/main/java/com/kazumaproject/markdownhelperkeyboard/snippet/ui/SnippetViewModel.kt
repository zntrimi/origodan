package com.kazumaproject.markdownhelperkeyboard.snippet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.repository.SnippetRepository
import com.kazumaproject.markdownhelperkeyboard.snippet.database.Snippet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SnippetViewModel @Inject constructor(
    private val repository: SnippetRepository,
) : ViewModel() {

    val snippets: StateFlow<List<Snippet>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun save(snippet: Snippet): Long = repository.save(snippet)

    suspend fun reorder(orderedIds: List<Long>) = repository.reorder(orderedIds)

    suspend fun delete(id: Long) = repository.deleteById(id)

    suspend fun deleteAll() = repository.deleteAll()

    suspend fun exportJson(): String {
        val entries = repository.getAll().map { SnippetBackupEntry(label = it.label, text = it.text) }
        return Gson().toJson(SnippetBackup(snippets = entries))
    }

    /** @return 追加した件数 */
    suspend fun importJson(json: String): Int {
        val type = object : TypeToken<SnippetBackup>() {}.type
        val backup: SnippetBackup = Gson().fromJson(json, type)
            ?: throw IllegalArgumentException("Empty snippet backup")
        val entries = backup.snippets.orEmpty()
            .filter { it.text.isNotBlank() }
        repository.appendAll(
            entries.map { Snippet(label = it.label.orEmpty(), text = it.text, sortOrder = 0) }
        )
        return entries.size
    }
}

data class SnippetBackupEntry(
    val label: String?,
    val text: String,
)

data class SnippetBackup(
    val version: Int = VERSION,
    val snippets: List<SnippetBackupEntry>?,
) {
    companion object {
        const val VERSION = 1
    }
}

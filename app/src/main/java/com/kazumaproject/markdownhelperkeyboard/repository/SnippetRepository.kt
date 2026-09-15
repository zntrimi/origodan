package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.core.data.snippet.SnippetItem
import com.kazumaproject.markdownhelperkeyboard.snippet.database.Snippet
import com.kazumaproject.markdownhelperkeyboard.snippet.database.SnippetDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnippetRepository @Inject constructor(
    private val dao: SnippetDao,
) {
    fun observeAll(): Flow<List<Snippet>> = dao.observeAll()

    /** キーボード側 (symbol_keyboard / IME) が使う表示用モデルに変換した Flow。 */
    fun observeItems(): Flow<List<SnippetItem>> = dao.observeAll().map { list ->
        list.map { it.toItem() }
    }

    suspend fun getAll(): List<Snippet> = dao.getAll()

    suspend fun getById(id: Long): Snippet? = dao.getById(id)

    /**
     * 新規なら末尾に追加、既存なら内容を更新する。
     * @return 保存後の id
     */
    suspend fun save(snippet: Snippet): Long {
        val normalized = normalizeAndValidate(snippet)
        return if (normalized.id == 0L) {
            dao.insert(normalized.copy(sortOrder = dao.maxSortOrder() + 1))
        } else {
            dao.update(normalized)
            normalized.id
        }
    }

    suspend fun reorder(orderedIds: List<Long>) = dao.reorder(orderedIds)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    /** バックアップ復元用。既存の並びの後ろに追加する。 */
    suspend fun appendAll(snippets: List<Snippet>) {
        if (snippets.isEmpty()) return
        val start = dao.maxSortOrder() + 1
        dao.insertAll(
            snippets.mapIndexed { index, snippet ->
                normalizeAndValidate(snippet).copy(id = 0, sortOrder = start + index)
            }
        )
    }

    private fun normalizeAndValidate(snippet: Snippet): Snippet {
        val label = snippet.label.trim()
        val text = snippet.text
        require(text.isNotBlank()) { "Snippet text must not be blank" }
        return snippet.copy(label = label, text = text)
    }

    private fun Snippet.toItem(): SnippetItem = SnippetItem(id = id, label = label, text = text)
}

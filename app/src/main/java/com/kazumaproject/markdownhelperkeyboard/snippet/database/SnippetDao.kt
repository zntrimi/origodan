package com.kazumaproject.markdownhelperkeyboard.snippet.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippet ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippet ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<Snippet>

    @Query("SELECT * FROM snippet WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Snippet?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM snippet")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(snippet: Snippet): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(snippets: List<Snippet>)

    @Update
    suspend fun update(snippet: Snippet)

    @Query("UPDATE snippet SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM snippet WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM snippet")
    suspend fun deleteAll()

    /** 並び替え後の id の順番をそのまま sortOrder として保存する。 */
    @Transaction
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }
}

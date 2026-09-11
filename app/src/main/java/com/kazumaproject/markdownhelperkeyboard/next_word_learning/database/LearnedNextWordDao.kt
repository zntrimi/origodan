package com.kazumaproject.markdownhelperkeyboard.next_word_learning.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface LearnedNextWordDao {
    @Query(
        """
        SELECT * FROM learned_next_words
        WHERE previousText = :previousText
        ORDER BY usageCount DESC, lastUsedAt DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun lookup(previousText: String, limit: Int): List<LearnedNextWordEntity>

    @Query(
        """
        SELECT * FROM learned_next_words
        WHERE previousText = :previousText AND nextText = :nextText
        LIMIT 1
        """
    )
    suspend fun find(previousText: String, nextText: String): LearnedNextWordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LearnedNextWordEntity): Long

    @Update
    suspend fun update(entry: LearnedNextWordEntity)

    @Query(
        """
        DELETE FROM learned_next_words
        WHERE previousText = :previousText AND id NOT IN (
            SELECT id FROM learned_next_words
            WHERE previousText = :previousText
            ORDER BY usageCount DESC, lastUsedAt DESC, id DESC
            LIMIT :keepPerKey
        )
        """
    )
    suspend fun trimKey(previousText: String, keepPerKey: Int)

    @Query(
        """
        DELETE FROM learned_next_words
        WHERE id NOT IN (
            SELECT id FROM learned_next_words
            ORDER BY lastUsedAt DESC
            LIMIT :keepTotal
        )
        """
    )
    suspend fun trimTotal(keepTotal: Int)

    @Query("DELETE FROM learned_next_words")
    suspend fun deleteAll()

    @Transaction
    suspend fun reinforce(
        previousText: String,
        nextText: String,
        timestamp: Long,
        increment: Int,
        keepPerKey: Int,
        keepTotal: Int,
    ) {
        val existing = find(previousText, nextText)
        if (existing == null) {
            insert(
                LearnedNextWordEntity(
                    previousText = previousText,
                    nextText = nextText,
                    usageCount = increment.coerceAtLeast(1),
                    lastUsedAt = timestamp,
                )
            )
        } else {
            update(
                existing.copy(
                    usageCount = existing.usageCount.toLong()
                        .plus(increment.coerceAtLeast(1).toLong())
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    lastUsedAt = maxOf(existing.lastUsedAt, timestamp),
                )
            )
        }
        trimKey(previousText, keepPerKey)
        trimTotal(keepTotal)
    }
}

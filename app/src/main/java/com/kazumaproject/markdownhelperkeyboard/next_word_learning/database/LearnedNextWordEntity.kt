package com.kazumaproject.markdownhelperkeyboard.next_word_learning.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learned_next_words",
    indices = [
        Index(value = ["previousText"]),
        Index(value = ["previousText", "nextText"], unique = true),
    ],
)
data class LearnedNextWordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val previousText: String,
    val nextText: String,
    val usageCount: Int = 1,
    val lastUsedAt: Long,
)

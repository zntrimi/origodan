package com.kazumaproject.markdownhelperkeyboard.repository

import com.kazumaproject.markdownhelperkeyboard.next_word_learning.NextWordLearningPolicy
import com.kazumaproject.markdownhelperkeyboard.next_word_learning.database.LearnedNextWordDao
import com.kazumaproject.markdownhelperkeyboard.next_word_learning.database.LearnedNextWordEntity
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

@Singleton
class LearnedNextWordRepository @Inject constructor(
    private val dao: LearnedNextWordDao,
) {
    private val lookupCache = ConcurrentHashMap<String, List<LearnedNextWordEntity>>()

    suspend fun lookup(rawPreviousText: String, limit: Int = 6): List<LearnedNextWordEntity> {
        val previousText = NextWordLearningPolicy.normalize(rawPreviousText) ?: return emptyList()
        val normalizedLimit = limit.coerceIn(1, 12)
        return lookupCache[previousText]
            ?.take(normalizedLimit)
            ?: dao.lookup(previousText, 12).also { lookupCache[previousText] = it }
                .take(normalizedLimit)
    }

    suspend fun learn(
        rawPreviousText: String,
        rawNextText: String,
        timestamp: Long,
        weight: Int = 1,
    ) {
        val previousText = NextWordLearningPolicy.normalize(rawPreviousText) ?: return
        val nextText = NextWordLearningPolicy.normalize(rawNextText) ?: return
        if (previousText == nextText) return
        dao.reinforce(
            previousText = previousText,
            nextText = nextText,
            timestamp = timestamp,
            increment = weight.coerceIn(1, 4),
            keepPerKey = 12,
            keepTotal = 5_000,
        )
        lookupCache.remove(previousText)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
        lookupCache.clear()
    }
}

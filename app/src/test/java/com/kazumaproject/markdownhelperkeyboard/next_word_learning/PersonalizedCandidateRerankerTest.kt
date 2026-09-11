package com.kazumaproject.markdownhelperkeyboard.next_word_learning

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.next_word_learning.database.LearnedNextWordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalizedCandidateRerankerTest {
    @Test
    fun learnedOrderPromotesMatchingCandidatesAndPreservesTheRest() {
        val candidates = listOf(candidate("です"), candidate("でした"), candidate("ですね"))
        val learned = listOf(
            learned("ですね", count = 5),
            learned("です", count = 2),
        )

        assertEquals(
            listOf("ですね", "です", "でした"),
            PersonalizedCandidateReranker.rerank(candidates, learned).map { it.string },
        )
    }

    @Test
    fun noMatchKeepsOriginalOrder() {
        val candidates = listOf(candidate("です"), candidate("でした"))
        assertEquals(
            candidates,
            PersonalizedCandidateReranker.rerank(candidates, listOf(learned("ます", 3))),
        )
    }

    private fun candidate(text: String) = Candidate(
        string = text,
        type = 1,
        length = 1u.toUByte(),
        score = 100,
    )

    private fun learned(text: String, count: Int) = LearnedNextWordEntity(
        previousText = "よろしく",
        nextText = text,
        usageCount = count,
        lastUsedAt = count.toLong(),
    )
}

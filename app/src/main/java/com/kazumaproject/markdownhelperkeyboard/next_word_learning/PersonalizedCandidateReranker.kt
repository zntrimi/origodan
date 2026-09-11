package com.kazumaproject.markdownhelperkeyboard.next_word_learning

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.next_word_learning.database.LearnedNextWordEntity

object PersonalizedCandidateReranker {
    fun rerank(
        candidates: List<Candidate>,
        learned: List<LearnedNextWordEntity>,
    ): List<Candidate> {
        if (candidates.size < 2 || learned.isEmpty()) return candidates

        val learnedRank = learned.mapIndexed { index, entry -> entry.nextText to index }.toMap()
        if (candidates.none { candidate ->
                candidate.string in learnedRank || candidate.commitText in learnedRank
            }
        ) return candidates

        return candidates.withIndex()
            .sortedWith(
                compareBy<IndexedValue<Candidate>> { indexed ->
                    minOf(
                        learnedRank[indexed.value.string] ?: Int.MAX_VALUE,
                        learnedRank[indexed.value.commitText] ?: Int.MAX_VALUE,
                    )
                }.thenBy { it.index }
            )
            .map { it.value }
    }
}

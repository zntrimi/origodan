package com.kazumaproject.markdownhelperkeyboard.zeroquery

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.repository.CustomZeroQueryRepository
import com.kazumaproject.markdownhelperkeyboard.repository.LearnedNextWordRepository
import com.kazumaproject.markdownhelperkeyboard.zeroquery.custom.CustomZeroQueryEntry

class ZeroQueryLookupUseCase(
    private val customZeroQueryRepository: CustomZeroQueryRepository,
    private val learnedNextWordRepository: LearnedNextWordRepository,
    private val bundledProviderHolder: LazyZeroQueryProvider,
) {
    suspend fun lookup(key: String): List<Candidate> {
        if (key.isBlank()) return emptyList()

        // Personal history is intentionally first: repeated choices should outrank static data.
        val learnedCandidates = learnedNextWordRepository.lookup(key)
            .map { entry ->
                Candidate(
                    string = entry.nextText,
                    type = ZERO_QUERY_CANDIDATE_TYPE,
                    length = entry.nextText.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
                    score = -entry.usageCount.coerceAtMost(10_000),
                    yomi = entry.nextText,
                )
            }
        val customCandidates = customZeroQueryRepository.lookup(key)
            .map { it.toCandidate() }
        val bundledCandidates = bundledProviderHolder.getIfEnabled(enabled = true)
            ?.lookup(key)
            ?.map { it.toCandidate() }
            .orEmpty()

        val seen = LinkedHashSet<String>()
        return (learnedCandidates + customCandidates + bundledCandidates).mapNotNull { candidate ->
            val value = candidate.string
            if (value.isBlank() || !seen.add(value)) {
                null
            } else {
                candidate
            }
        }
    }
}

private fun CustomZeroQueryEntry.toCandidate(): Candidate =
    Candidate(
        string = candidate,
        type = ZERO_QUERY_CANDIDATE_TYPE,
        length = candidate.length.coerceAtMost(UByte.MAX_VALUE.toInt()).toUByte(),
        score = 0,
        yomi = candidate,
    )

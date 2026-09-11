package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.english.EnglishTypoScorer

object EnglishAutoCorrectionPolicy {
    fun select(
        typed: String,
        candidates: List<Candidate>,
        isKnownWord: Boolean,
    ): Candidate? {
        if (typed.length < 3 || !typed.all(Char::isLetter)) return null

        val hasOrdinaryExactMatch = candidates.any {
            it.type == QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE &&
                it.string.equals(typed, ignoreCase = true)
        }
        val hasIntentionalOmissionAlias = candidates.any {
            it.type == QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE &&
                it.string.equals(typed, ignoreCase = true)
        }
        val contraction = candidates
            .asSequence()
            .filter {
                it.type == QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE ||
                    it.type == QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE
            }
            .filter { it.string.isEnglishWordOrContraction() }
            .filter { it.string.isSingleApostropheInsertionFor(typed) }
            .minByOrNull(Candidate::score)
        if (
            contraction != null &&
            typed.length >= MIN_AUTOMATIC_CONTRACTION_LENGTH &&
            contraction.score <= MAX_CONFIDENT_NATIVE_RANK_COST &&
            !hasOrdinaryExactMatch &&
            (
                contraction.type ==
                    QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE ||
                    hasIntentionalOmissionAlias ||
                    !isKnownWord
                )
        ) {
            return contraction
        }
        if (isKnownWord) return null

        val latinImeChoice = candidates
            .asSequence()
            .filter { it.type == QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE }
            .filter { it.string.isEnglishWordOrContraction() }
            .filter { !it.string.equals(typed, ignoreCase = true) }
            .minByOrNull(Candidate::score)
        if (latinImeChoice != null) {
            val distance = EnglishTypoScorer.editDistance(
                typed.lowercase(),
                latinImeChoice.string.lowercase(),
            )
            val allowedDistance = if (typed.length >= 5) 2 else 1
            if (distance in 1..allowedDistance) return latinImeChoice
        }

        val ranked = candidates
            .asSequence()
            .filter { it.type == QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE }
            .filter { it.string.all(Char::isLetter) }
            .filter { !it.string.equals(typed, ignoreCase = true) }
            .filter { EnglishTypoScorer.editDistance(typed.lowercase(), it.string.lowercase()) == 1 }
            .groupBy { it.string.lowercase() }
            .mapNotNull { (_, variants) -> variants.minByOrNull(Candidate::score) }
            .sortedBy(Candidate::score)
            .toList()

        val best = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)
        val scoreLead = runnerUp?.let { it.score - best.score } ?: Int.MAX_VALUE
        val adjacentTranspose = isSingleAdjacentTranspose(typed, best.string)

        // Three-letter words are especially ambiguous. Only an obvious transposition (teh -> the)
        // or a clearly dominant dictionary result is safe enough to apply automatically.
        if (typed.length == 3 && !adjacentTranspose && scoreLead < 2_000) return null
        if (typed.length > 3 && scoreLead < 500) return null
        return best
    }

    /** Allows dictionary-backed English contractions while rejecting spaces and punctuation. */
    private fun String.isEnglishWordOrContraction(): Boolean {
        val apostropheIndex = indexOf('\'')
        return when {
            all(Char::isLetter) -> true
            apostropheIndex <= 0 || apostropheIndex >= lastIndex -> false
            indexOf('\'', startIndex = apostropheIndex + 1) >= 0 -> false
            else -> filterIndexed { index, _ -> index != apostropheIndex }.all(Char::isLetter)
        }
    }

    private fun String.isSingleApostropheInsertionFor(typed: String): Boolean {
        if (length != typed.length + 1) return false
        val apostropheIndex = indexOf('\'')
        if (apostropheIndex <= 0 || apostropheIndex >= lastIndex) return false
        if (indexOf('\'', startIndex = apostropheIndex + 1) >= 0) return false
        return removeRange(apostropheIndex, apostropheIndex + 1)
            .equals(typed, ignoreCase = true)
    }

    private fun isSingleAdjacentTranspose(first: String, second: String): Boolean {
        if (first.length != second.length) return false
        val left = first.lowercase()
        val right = second.lowercase()
        val mismatch = left.indices.filter { left[it] != right[it] }
        return mismatch.size == 2 &&
            mismatch[1] == mismatch[0] + 1 &&
            left[mismatch[0]] == right[mismatch[1]] &&
            left[mismatch[1]] == right[mismatch[0]]
    }

    private const val MIN_AUTOMATIC_CONTRACTION_LENGTH = 4

    // LatinIME candidates start at 700 and advance by 220 per rank. Restrict automatic
    // punctuation insertion to its first two native choices; lower-confidence forms stay visible.
    private const val MAX_CONFIDENT_NATIVE_RANK_COST = 920
}

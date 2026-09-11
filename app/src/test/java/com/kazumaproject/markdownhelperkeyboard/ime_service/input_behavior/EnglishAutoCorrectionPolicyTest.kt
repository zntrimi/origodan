package com.kazumaproject.markdownhelperkeyboard.ime_service.input_behavior

import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnglishAutoCorrectionPolicyTest {
    @Test
    fun selectsHighConfidenceTransposition() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "teh",
            candidates = listOf(typo("the", 2_000), typo("ten", 3_000)),
            isKnownWord = false,
        )

        assertEquals("the", selected?.string)
    }

    @Test
    fun neverChangesKnownWord() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "good",
            candidates = listOf(typo("food", 1_000)),
            isKnownWord = true,
        )

        assertNull(selected)
    }

    @Test
    fun leavesAmbiguousShortSubstitutionForCandidateBar() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "car",
            candidates = listOf(typo("cat", 2_000), typo("can", 2_300)),
            isKnownWord = false,
        )

        assertNull(selected)
    }

    @Test
    fun acceptsLatinImeApprovedTwoEditCorrectionForLongWord() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "keybaordd",
            candidates = listOf(
                Candidate(
                    string = "keyboard",
                    type = QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE,
                    length = 9u.toUByte(),
                    score = 700,
                )
            ),
            isKnownWord = false,
        )

        assertEquals("keyboard", selected?.string)
    }

    @Test
    fun rejectsLatinImeTwoEditCorrectionForShortWord() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "cta",
            candidates = listOf(
                Candidate(
                    string = "cut",
                    type = QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE,
                    length = 3u.toUByte(),
                    score = 700,
                )
            ),
            isKnownWord = false,
        )

        assertNull(selected)
    }

    @Test
    fun acceptsDictionaryBackedContraction() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "whats",
            candidates = listOf(
                Candidate(
                    string = "whats",
                    type = QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE,
                    length = 5u.toUByte(),
                    score = 350,
                ),
                Candidate(
                    string = "what's",
                    type = QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE,
                    length = 5u.toUByte(),
                    score = 700,
                )
            ),
            // AOSP also contains the unpunctuated token; the preferred contraction must win.
            isKnownWord = true,
        )

        assertEquals("what's", selected?.string)
    }

    @Test
    fun algorithmHandlesUnlistedContractionWithoutWordTable() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "foobars",
            candidates = listOf(
                Candidate(
                    string = "foobar's",
                    type = QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE,
                    length = 7u.toUByte(),
                    score = 700,
                )
            ),
            isKnownWord = true,
        )

        assertEquals("foobar's", selected?.string)
    }

    @Test
    fun keepsOrdinaryExactWordInsteadOfAmbiguousContraction() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "well",
            candidates = listOf(
                Candidate(
                    string = "well",
                    type = QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE,
                    length = 4u.toUByte(),
                    score = 350,
                ),
                Candidate(
                    string = "we'll",
                    type = QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE,
                    length = 4u.toUByte(),
                    score = 700,
                ),
            ),
            isKnownWord = true,
        )

        assertNull(selected)
    }

    @Test
    fun leavesLowConfidenceContractionInCandidateBar() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "youre",
            candidates = listOf(
                Candidate(
                    string = "you're",
                    type = QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE,
                    length = 5u.toUByte(),
                    score = 1_580,
                )
            ),
            isKnownWord = true,
        )

        assertNull(selected)
    }

    @Test
    fun rejectsUnsafePunctuationFromNativeCandidate() {
        val selected = EnglishAutoCorrectionPolicy.select(
            typed = "whats",
            candidates = listOf(
                Candidate(
                    string = "what s!",
                    type = QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE,
                    length = 5u.toUByte(),
                    score = 700,
                )
            ),
            isKnownWord = false,
        )

        assertNull(selected)
    }

    private fun typo(value: String, score: Int) = Candidate(
        string = value,
        type = QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE,
        length = 3u.toUByte(),
        score = score,
    )
}

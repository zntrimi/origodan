package com.kazumaproject.markdownhelperkeyboard.converter.english

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishTypoScorerTest {
    @Test
    fun editDistance_handlesCommonMobileTypos() {
        assertEquals(1, EnglishTypoScorer.editDistance("teh", "the"))
        assertEquals(1, EnglishTypoScorer.editDistance("helo", "hello"))
        assertEquals(1, EnglishTypoScorer.editDistance("helllo", "hello"))
        assertEquals(1, EnglishTypoScorer.editDistance("giod", "good"))
    }

    @Test
    fun rankingPenalty_prefersAdjacentKeySubstitution() {
        assertTrue(
            EnglishTypoScorer.rankingPenalty("giod", "good") <
                EnglishTypoScorer.rankingPenalty("giod", "gaod")
        )
    }

    @Test
    fun rankingPenalty_prefersAdjacentTransposeToTwoIndependentChanges() {
        assertTrue(
            EnglishTypoScorer.rankingPenalty("teh", "the") <
                EnglishTypoScorer.rankingPenalty("teh", "toe")
        )
    }
}

package com.kazumaproject.markdownhelperkeyboard.learning.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningScorePolicyTest {
    @Test
    fun correctedCandidateGetsStrongerInitialScoreThanFirstCandidate() {
        val normal = LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 0)
        val corrected = LearningScorePolicy.initial(candidateScore = 10_000, candidateIndex = 1)

        assertTrue(corrected < normal)
        assertEquals(3, LearningScorePolicy.usageWeight(candidateIndex = 1))
        assertEquals(1, LearningScorePolicy.usageWeight(candidateIndex = 0))
    }

    @Test
    fun weightedReinforcementAppliesMultipleSignalsAtOnce() {
        assertEquals(
            5_500,
            LearningScorePolicy.reinforce(
                existingScore = 10_000,
                incomingScore = 12_000,
                repetitions = 3,
            ),
        )
    }
}

package com.kazumaproject.markdownhelperkeyboard.learning.database

object LearningScorePolicy {
    private const val REINFORCEMENT = 1500

    fun initial(candidateScore: Int, candidateIndex: Int): Int =
        (candidateScore - correctionBoost(candidateIndex))
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()

    fun usageWeight(candidateIndex: Int, explicitlySelected: Boolean = false): Int =
        if (candidateIndex > 0 || explicitlySelected) 3 else 1

    fun phrase(fragmentScores: List<Int>): Int =
        fragmentScores.takeIf { it.isNotEmpty() }
            ?.map(Int::toLong)
            ?.average()
            ?.toLong()
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())
            ?.toInt()
            ?: 3000

    fun reinforce(existingScore: Int, incomingScore: Int, repetitions: Int = 1): Int =
        (minOf(existingScore, incomingScore).toLong() -
            REINFORCEMENT.toLong() * repetitions.coerceIn(1, 4))
            .coerceAtLeast(0L)
            .toInt()

    private fun correctionBoost(candidateIndex: Int): Long = when {
        candidateIndex <= 0 -> 0L
        else -> 1_500L + 500L * (candidateIndex - 1)
    }
}

package com.kazumaproject.markdownhelperkeyboard.converter.english

/**
 * Small, allocation-bounded typo scorer for physical QWERTY taps.
 *
 * Search uses ordinary edit distance to keep the dictionary traversal cheap. Ranking then uses
 * these weights so an adjacent-key miss or swapped pair beats an unrelated substitution.
 */
object EnglishTypoScorer {
    private const val INSERT_OR_DELETE_COST = 1_250
    private const val TRANSPOSE_COST = 850
    private const val NEAR_SUBSTITUTION_COST = 1_000
    private const val FAR_SUBSTITUTION_COST = 2_800

    private data class Position(val x: Float, val y: Float)

    private val positions: Map<Char, Position> = buildMap {
        listOf(
            "qwertyuiop" to 0.0f,
            "asdfghjkl" to 0.25f,
            "zxcvbnm" to 0.75f,
        ).forEachIndexed { rowIndex, (row, offset) ->
            row.forEachIndexed { columnIndex, character ->
                put(character, Position(columnIndex + offset, rowIndex.toFloat()))
            }
        }
    }

    fun editDistance(first: String, second: String): Int {
        if (first == second) return 0
        if (first.isEmpty()) return second.length
        if (second.isEmpty()) return first.length

        var previousPrevious: IntArray? = null
        var previous = IntArray(second.length + 1) { it }

        for (i in 1..first.length) {
            val current = IntArray(second.length + 1)
            current[0] = i
            for (j in 1..second.length) {
                val substitution = previous[j - 1] +
                    if (first[i - 1] == second[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
                if (
                    i > 1 && j > 1 &&
                    first[i - 1] == second[j - 2] &&
                    first[i - 2] == second[j - 1]
                ) {
                    value = minOf(value, checkNotNull(previousPrevious)[j - 2] + 1)
                }
                current[j] = value
            }
            previousPrevious = previous
            previous = current
        }
        return previous[second.length]
    }

    fun rankingPenalty(typed: String, candidate: String): Int {
        val first = typed.lowercase()
        val second = candidate.lowercase()
        if (first == second) return 0

        var previousPrevious: IntArray? = null
        var previous = IntArray(second.length + 1) { it * INSERT_OR_DELETE_COST }

        for (i in 1..first.length) {
            val current = IntArray(second.length + 1)
            current[0] = i * INSERT_OR_DELETE_COST
            for (j in 1..second.length) {
                val substitutionCost = substitutionCost(first[i - 1], second[j - 1])
                var value = minOf(
                    previous[j] + INSERT_OR_DELETE_COST,
                    current[j - 1] + INSERT_OR_DELETE_COST,
                    previous[j - 1] + substitutionCost,
                )
                if (
                    i > 1 && j > 1 &&
                    first[i - 1] == second[j - 2] &&
                    first[i - 2] == second[j - 1]
                ) {
                    value = minOf(value, checkNotNull(previousPrevious)[j - 2] + TRANSPOSE_COST)
                }
                current[j] = value
            }
            previousPrevious = previous
            previous = current
        }
        return previous[second.length]
    }

    private fun substitutionCost(first: Char, second: Char): Int {
        if (first == second) return 0
        val firstPosition = positions[first] ?: return FAR_SUBSTITUTION_COST
        val secondPosition = positions[second] ?: return FAR_SUBSTITUTION_COST
        val dx = firstPosition.x - secondPosition.x
        val dy = firstPosition.y - secondPosition.y
        return if (dx * dx + dy * dy <= 2.0f) {
            NEAR_SUBSTITUTION_COST
        } else {
            FAR_SUBSTITUTION_COST
        }
    }
}

package dev.imaizentarou.latinime

data class LatinImeKey(
    val codePoint: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class LatinImeKeyboardGeometry(
    val width: Int,
    val height: Int,
    val keys: List<LatinImeKey>,
)

data class LatinImeTap(
    val x: Int,
    val y: Int,
    val timeMillis: Int,
)

data class LatinImeSuggestion(
    val word: String,
    val score: Int,
    val type: Int,
) {
    val kind: Int get() = type and 0xff
    val isAppropriateForAutoCorrection: Boolean
        get() = type and APPROPRIATE_FOR_AUTOCORRECTION != 0
    val isExactMatchWithIntentionalOmission: Boolean
        get() = type and EXACT_MATCH_WITH_INTENTIONAL_OMISSION != 0

    companion object {
        private const val APPROPRIATE_FOR_AUTOCORRECTION = 0x10000000
        private const val EXACT_MATCH_WITH_INTENTIONAL_OMISSION = 0x20000000
    }
}

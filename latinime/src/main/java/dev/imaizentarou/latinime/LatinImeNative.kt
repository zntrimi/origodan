package dev.imaizentarou.latinime

internal object LatinImeNative {
    const val MAX_RESULTS = 18
    const val MAX_WORD_LENGTH = 48

    init {
        System.loadLibrary("mirror_latinime")
    }

    external fun openDictionary(path: String, size: Long): Long
    external fun closeDictionary(handle: Long)

    external fun createProximityInfo(
        keyboardWidth: Int,
        keyboardHeight: Int,
        gridWidth: Int,
        gridHeight: Int,
        commonKeyWidth: Int,
        commonKeyHeight: Int,
        proximityChars: IntArray,
        keyXs: IntArray,
        keyYs: IntArray,
        keyWidths: IntArray,
        keyHeights: IntArray,
        keyCodes: IntArray,
    ): Long

    external fun releaseProximityInfo(handle: Long)
    external fun getProbability(dictionaryHandle: Long, word: IntArray): Int

    external fun getSuggestions(
        dictionaryHandle: Long,
        proximityHandle: Long,
        input: IntArray,
        xCoordinates: IntArray,
        yCoordinates: IntArray,
        times: IntArray,
        previousWord: IntArray,
        outputCodePoints: IntArray,
        outputScores: IntArray,
        outputTypes: IntArray,
    ): Int
}

package dev.imaizentarou.latinime

import android.content.Context
import java.io.File
import kotlin.math.hypot

/**
 * Small Android-facing wrapper around AOSP LatinIME's native typing suggestion core.
 *
 * The wrapper intentionally owns no UI state. Callers may provide real tap positions and keyboard
 * geometry; when those are unavailable it uses the center of the matching QWERTY key.
 */
class LatinImeEngine(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var dictionaryHandle = 0L
    private var proximityHandle = 0L
    private var geometrySignature = 0

    fun isKnownWord(word: String): Boolean = synchronized(lock) {
        if (!ensureDictionaryLocked() || word.isEmpty()) return@synchronized false
        LatinImeNative.getProbability(dictionaryHandle, word.toCodePoints()) >= 0
    }

    fun suggest(
        typed: String,
        previousWord: String? = null,
        taps: List<LatinImeTap> = emptyList(),
        geometry: LatinImeKeyboardGeometry? = null,
    ): List<LatinImeSuggestion> = synchronized(lock) {
        if (typed.isEmpty() || !ensureDictionaryLocked()) return@synchronized emptyList()
        val activeGeometry = geometry?.takeIf { it.keys.isNotEmpty() } ?: defaultGeometry()
        if (!ensureProximityLocked(activeGeometry)) return@synchronized emptyList()

        val input = typed.lowercase().toCodePoints().take(LatinImeNative.MAX_WORD_LENGTH - 1).toIntArray()
        val resolvedTaps = resolveTaps(input, taps, activeGeometry)
        val outputCodePoints = IntArray(LatinImeNative.MAX_RESULTS * LatinImeNative.MAX_WORD_LENGTH)
        val outputScores = IntArray(LatinImeNative.MAX_RESULTS)
        val outputTypes = IntArray(LatinImeNative.MAX_RESULTS)
        val count = LatinImeNative.getSuggestions(
            dictionaryHandle = dictionaryHandle,
            proximityHandle = proximityHandle,
            input = input,
            xCoordinates = resolvedTaps.map(LatinImeTap::x).toIntArray(),
            yCoordinates = resolvedTaps.map(LatinImeTap::y).toIntArray(),
            times = resolvedTaps.map(LatinImeTap::timeMillis).toIntArray(),
            previousWord = previousWord
                ?.lowercase()
                ?.takeIf { it.all(Char::isLetter) }
                ?.toCodePoints()
                ?: IntArray(0),
            outputCodePoints = outputCodePoints,
            outputScores = outputScores,
            outputTypes = outputTypes,
        ).coerceIn(0, LatinImeNative.MAX_RESULTS)

        buildList(count) {
            for (index in 0 until count) {
                val offset = index * LatinImeNative.MAX_WORD_LENGTH
                var length = 0
                while (
                    length < LatinImeNative.MAX_WORD_LENGTH &&
                    outputCodePoints[offset + length] != 0
                ) {
                    length++
                }
                if (length > 0) {
                    add(
                        LatinImeSuggestion(
                            word = String(outputCodePoints, offset, length),
                            score = outputScores[index],
                            type = outputTypes[index],
                        )
                    )
                }
            }
        }.distinctBy { it.word.lowercase() }
    }

    override fun close() = synchronized(lock) {
        if (proximityHandle != 0L) LatinImeNative.releaseProximityInfo(proximityHandle)
        if (dictionaryHandle != 0L) LatinImeNative.closeDictionary(dictionaryHandle)
        proximityHandle = 0L
        dictionaryHandle = 0L
        geometrySignature = 0
    }

    private fun ensureDictionaryLocked(): Boolean {
        if (dictionaryHandle != 0L) return true
        val destination = File(appContext.noBackupFilesDir, "latinime/main_en_US.dict")
        if (!destination.isFile || destination.length() == 0L) {
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            appContext.assets.open(DICTIONARY_ASSET).use { input ->
                temporary.outputStream().buffered().use(input::copyTo)
            }
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
        }
        dictionaryHandle = LatinImeNative.openDictionary(destination.absolutePath, destination.length())
        return dictionaryHandle != 0L
    }

    private fun ensureProximityLocked(geometry: LatinImeKeyboardGeometry): Boolean {
        val signature = geometry.hashCode()
        if (proximityHandle != 0L && signature == geometrySignature) return true
        if (proximityHandle != 0L) LatinImeNative.releaseProximityInfo(proximityHandle)
        proximityHandle = 0L

        val gridWidth = 10
        val gridHeight = 3
        val proximity = IntArray(gridWidth * gridHeight * MAX_PROXIMITY_CHARS) { NOT_A_CODE }
        val cellWidth = (geometry.width + gridWidth - 1) / gridWidth
        val cellHeight = (geometry.height + gridHeight - 1) / gridHeight
        for (gridY in 0 until gridHeight) {
            for (gridX in 0 until gridWidth) {
                val centerX = gridX * cellWidth + cellWidth / 2f
                val centerY = gridY * cellHeight + cellHeight / 2f
                val nearest = geometry.keys
                    .sortedBy { key ->
                        hypot(
                            centerX - (key.x + key.width / 2f),
                            centerY - (key.y + key.height / 2f),
                        )
                    }
                    .take(MAX_PROXIMITY_CHARS)
                val offset = (gridY * gridWidth + gridX) * MAX_PROXIMITY_CHARS
                nearest.forEachIndexed { index, key -> proximity[offset + index] = key.codePoint }
            }
        }
        val commonWidth = geometry.keys.map(LatinImeKey::width).average().toInt().coerceAtLeast(1)
        val commonHeight = geometry.keys.map(LatinImeKey::height).average().toInt().coerceAtLeast(1)
        proximityHandle = LatinImeNative.createProximityInfo(
            keyboardWidth = geometry.width.coerceAtLeast(1),
            keyboardHeight = geometry.height.coerceAtLeast(1),
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            commonKeyWidth = commonWidth,
            commonKeyHeight = commonHeight,
            proximityChars = proximity,
            keyXs = geometry.keys.map(LatinImeKey::x).toIntArray(),
            keyYs = geometry.keys.map(LatinImeKey::y).toIntArray(),
            keyWidths = geometry.keys.map(LatinImeKey::width).toIntArray(),
            keyHeights = geometry.keys.map(LatinImeKey::height).toIntArray(),
            keyCodes = geometry.keys.map(LatinImeKey::codePoint).toIntArray(),
        )
        geometrySignature = signature
        return proximityHandle != 0L
    }

    private fun resolveTaps(
        input: IntArray,
        taps: List<LatinImeTap>,
        geometry: LatinImeKeyboardGeometry,
    ): List<LatinImeTap> {
        if (taps.size == input.size) return taps
        return input.mapIndexed { index, codePoint ->
            val key = geometry.keys.firstOrNull {
                Character.toLowerCase(it.codePoint) == Character.toLowerCase(codePoint)
            }
            LatinImeTap(
                x = key?.let { it.x + it.width / 2 } ?: -1,
                y = key?.let { it.y + it.height / 2 } ?: -1,
                timeMillis = index * 60,
            )
        }
    }

    private fun defaultGeometry(): LatinImeKeyboardGeometry {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keyWidth = 100
        val keyHeight = 100
        val offsets = listOf(0, 50, 150)
        val keys = rows.flatMapIndexed { rowIndex, row ->
            row.mapIndexed { columnIndex, character ->
                LatinImeKey(
                    codePoint = character.code,
                    x = offsets[rowIndex] + columnIndex * keyWidth,
                    y = rowIndex * keyHeight,
                    width = keyWidth,
                    height = keyHeight,
                )
            }
        }
        return LatinImeKeyboardGeometry(width = 1_000, height = 300, keys = keys)
    }

    private fun String.toCodePoints(): IntArray = codePoints().toArray()

    private companion object {
        const val DICTIONARY_ASSET = "latinime/main_en_US.dict"
        const val MAX_PROXIMITY_CHARS = 16
        const val NOT_A_CODE = -1
    }
}

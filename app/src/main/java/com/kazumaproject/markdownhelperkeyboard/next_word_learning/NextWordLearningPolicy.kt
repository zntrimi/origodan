package com.kazumaproject.markdownhelperkeyboard.next_word_learning

object NextWordLearningPolicy {
    private const val MAX_CODE_POINTS = 64

    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.codePointCount(0, value.length) > MAX_CODE_POINTS) return null
        if ('\n' in value || '\r' in value || '\t' in value) return null
        if (value.contains("://") || '@' in value) return null
        if (!value.any(::isJapaneseCharacter)) return null
        return value
    }

    private fun isJapaneseCharacter(character: Char): Boolean =
        character in '\u3040'..'\u30ff' ||
            character in '\u3400'..'\u4dbf' ||
            character in '\u4e00'..'\u9fff' ||
            character == '\u3005' ||
            character == '\u3006'
}

package com.kazumaproject.markdownhelperkeyboard.emoji_search

import java.io.Reader
import java.text.Normalizer
import java.util.Locale

/** Offline Unicode CLDR keywords. No query is sent to a server. */
class EmojiSearchIndex(reader: Reader) {
    private data class Entry(
        val emoji: String, val normalizedEmoji: String,
        val terms: List<String>, val words: List<String>
    )
    private val wordSeparator = Regex("[^a-z0-9]+")
    private val whitespace = Regex("\\s+")

    private val entries = reader.buffered().useLines { lines ->
        lines.filter(String::isNotBlank).map { line ->
            val fields = line.split('\t')
            val terms = fields.drop(1).map(::normalize).distinct()
            Entry(fields.first(), normalize(fields.first()), terms,
                terms.flatMap { it.split(wordSeparator) }.filter(String::isNotEmpty).distinct())
        }.toList()
    }

    fun search(query: String, limit: Int = 96): List<String> {
        if (limit <= 0) return emptyList()
        val normalized = normalize(query).trim()
        if (normalized.isEmpty()) return entries.take(limit).map { it.emoji }
        val words = normalized.split(whitespace)
        return entries.mapNotNull { entry ->
            val rank = when {
                entry.normalizedEmoji == normalized -> 0
                normalized in entry.terms -> 1
                words.all { word ->
                    if (word.any { it in 'a'..'z' }) {
                        entry.words.any { it.startsWith(word) }
                    } else entry.terms.any { it.contains(word) }
                } -> 2
                else -> return@mapNotNull null
            }
            rank to entry.emoji
        }.sortedBy { it.first }.take(limit).map { it.second }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .filter { it != '\uFE0F' && it != '\uFE0E' }
            .map { if (it in '\u30A1'..'\u30F6') (it.code - 0x60).toChar() else it }
            .joinToString("")
}

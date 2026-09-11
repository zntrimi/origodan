package com.kazumaproject.markdownhelperkeyboard.converter.english

import com.kazumaproject.Louds.with_term_id.ConverterWithTermId
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.english.louds.louds_with_term_id.LOUDSWithTermId
import com.kazumaproject.prefix.with_term_id.PrefixTreeWithTermId
import org.junit.Assert.assertTrue
import org.junit.Test

class EnglishFuzzyTrieSearchTest {
    @Test
    fun findsSwapMissingExtraAndSubstitutedLetter() {
        val common = ConverterWithTermId().convert(
            PrefixTreeWithTermId().apply {
                insert("the")
                insert("hello")
                insert("good")
            }.root
        ).apply { convertListToBitSet() }
        val trie = LOUDSWithTermId(
            common.LBS,
            common.getAllLabels(),
            common.isLeaf,
            common.getAllTermIds(),
        )
        val succinct = SuccinctBitVector(trie.LBS)

        assertTrue(trie.fuzzySearch("teh", succinct).any { it.yomi == "the" })
        assertTrue(trie.fuzzySearch("helo", succinct).any { it.yomi == "hello" })
        assertTrue(trie.fuzzySearch("helllo", succinct).any { it.yomi == "hello" })
        assertTrue(trie.fuzzySearch("giod", succinct).any { it.yomi == "good" })
    }
}

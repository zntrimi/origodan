package com.kazumaproject.markdownhelperkeyboard.next_word_learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextWordLearningPolicyTest {
    @Test
    fun acceptsJapaneseTextAndTrimsOuterWhitespace() {
        assertEquals("よろしく", NextWordLearningPolicy.normalize("  よろしく "))
        assertEquals("お願いします", NextWordLearningPolicy.normalize("お願いします"))
    }

    @Test
    fun rejectsSensitiveOrNonJapaneseValues() {
        assertNull(NextWordLearningPolicy.normalize("https://example.com/秘密"))
        assertNull(NextWordLearningPolicy.normalize("name@example.jp"))
        assertNull(NextWordLearningPolicy.normalize("password"))
        assertNull(NextWordLearningPolicy.normalize("一行目\n二行目"))
    }
}

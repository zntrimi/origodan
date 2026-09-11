package dev.imaizentarou.latinime

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatinImeEngineInstrumentedTest {
    @Test
    fun bundledDictionaryProvidesKnownWordsAndTypoSuggestions() {
        LatinImeEngine(ApplicationProvider.getApplicationContext()).use { engine ->
            assertTrue(engine.isKnownWord("keyboard"))
            assertFalse(engine.isKnownWord("keybaordd"))

            mapOf(
                "keybaord" to "keyboard",
                "teh" to "the",
                "recieve" to "receive",
                "becuase" to "because",
                "whats" to "what's",
                "dont" to "don't",
                "youre" to "you're",
                "ive" to "I've",
            ).forEach { (typed, expected) ->
                val suggestions = engine.suggest(typed)
                assertTrue(
                    "Expected $expected for $typed in " +
                        suggestions.map { Triple(it.word, it.score, it.isAppropriateForAutoCorrection) },
                    suggestions.any { it.word.equals(expected, ignoreCase = true) },
                )
                assertTrue(
                    "Expected $expected to be eligible for autocorrection for $typed in " +
                        suggestions.map { Triple(it.word, it.score, it.isAppropriateForAutoCorrection) },
                    suggestions.any {
                        it.word.equals(expected, ignoreCase = true) &&
                            it.isAppropriateForAutoCorrection
                    },
                )
                if (typed == "whats") {
                    assertTrue(
                        "Expected $expected to be marked as an intentional omission for $typed",
                        suggestions.any {
                            it.word.equals(expected, ignoreCase = true) &&
                                it.isExactMatchWithIntentionalOmission
                        },
                    )
                }
            }

            val ambiguousWordSuggestions = engine.suggest("well")
            assertTrue(
                "The ordinary exact word must remain distinguishable from we'll",
                ambiguousWordSuggestions.any {
                    it.word.equals("well", ignoreCase = true) &&
                        !it.isExactMatchWithIntentionalOmission
                },
            )
        }
    }
}

package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorGodanRomajiConversionTest {
    private val converter = RomajiKanaConverter(
        mapOf(
            "a" to ("あ" to 1),
            "i" to ("い" to 1),
            "o" to ("お" to 1),
            "ka" to ("か" to 2),
            "ga" to ("が" to 2),
            "kya" to ("きゃ" to 3),
            "ko" to ("こ" to 2),
            "na" to ("な" to 2),
            "ni" to ("に" to 2),
            "ro" to ("ろ" to 2),
            "ta" to ("た" to 2),
            "u" to ("う" to 1),
            "ze" to ("ぜ" to 2),
            "chi" to ("ち" to 3),
            "ha" to ("は" to 2),
            "nn" to ("ん" to 2),
        ),
    )

    @Test
    fun incrementalGodanTapsComposeKana() {
        assertEquals("こんにちは", compose("konnichiha"))
    }

    @Test
    fun secondNDoesNotLeakLatinTextAndRemainsAvailableForTheNextVowel() {
        assertEquals("こん", compose("konn"))
        assertEquals("こんに", compose("konni"))
    }

    @Test
    fun trailingNIsRenderedAsHiraganaButCanStillBecomeNSyllable() {
        assertEquals("こん", compose("kon"))
        assertEquals("こな", compose("kona"))
    }

    @Test
    fun nBeforeAnotherConsonantStaysHatsuon() {
        assertEquals("ぜん", compose("zen"))
        assertEquals("ぜんt", compose("zent"))
        assertEquals("ぜんたろう", compose("zentarou"))
    }

    @Test
    fun alternateConsonantFlickComposesVoicedKana() {
        assertEquals("が", compose("ga"))
    }

    @Test
    fun consonantSequenceComposesContractedSound() {
        assertEquals("きゃ", compose("kya"))
    }

    private fun compose(keys: String): String {
        val composer = MirrorGodanInputComposer()
        return keys.fold("") { text, key ->
            composer.append(text, key.toString(), converter::convertCustomLayout)
        }
    }
}

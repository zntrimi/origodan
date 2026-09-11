package com.kazumaproject.markdownhelperkeyboard.converter.candidate

import com.kazumaproject.markdownhelperkeyboard.converter.utility.FormulaCandidatePresentation

/**
 * @see 1:NBest 2:Part of letters 3:Hirakana 4:Katakana 5:Combine part of letter 6. Single Kanji
 **/
data class Candidate(
    val string: String,
    val type: Byte,
    val length: UByte,
    val score: Int,
    val yomi: String? = null,
    val leftId: Short? = null,
    val rightId: Short? = null,
    /** Stable source identity for action candidates whose display string must never be committed. */
    val sourceId: Long? = null,
    /** Text sent to InputConnection. Defaults to the legacy candidate string. */
    val commitText: String = string,
    /** Optional non-text presentation, currently used by formula candidates. */
    val presentation: FormulaCandidatePresentation? = null,
)

const val QWERTY_ENGLISH_TYPO_CANDIDATE_TYPE: Byte = 35

/** AOSP LatinIME marked this correction as safe enough for automatic replacement. */
const val QWERTY_ENGLISH_LATINIME_AUTOCORRECT_CANDIDATE_TYPE: Byte = 36

/** AOSP LatinIME recognized an exact word with intentionally omitted punctuation. */
const val QWERTY_ENGLISH_LATINIME_INTENTIONAL_OMISSION_CANDIDATE_TYPE: Byte = 37

/** The native dictionary contains the user's exact spelling as an ordinary word. */
const val QWERTY_ENGLISH_LATINIME_EXACT_CANDIDATE_TYPE: Byte = 38

/** The native dictionary treats the exact spelling as an intentional-omission alias. */
const val QWERTY_ENGLISH_LATINIME_EXACT_OMISSION_CANDIDATE_TYPE: Byte = 39

package com.kazumaproject.markdownhelperkeyboard.ime_service.romaji_kana

/**
 * Keeps the raw GODAN key sequence separate from the kana shown to the editor.
 * This lets a trailing N appear immediately as ん while still allowing the next
 * vowel to reinterpret it (N + I -> に).
 */
internal class MirrorGodanInputComposer {
    private var rawInput = ""
    private var lastRenderedInput = ""

    fun append(
        currentRenderedInput: String,
        text: String,
        convert: (String) -> String,
    ): String {
        if (currentRenderedInput != lastRenderedInput) {
            rawInput = currentRenderedInput
        }
        rawInput += text

        val converted = convert(rawInput)
        val rendered = if (converted.endsWith('n')) {
            converted.dropLast(1) + "ん"
        } else {
            converted
        }
        lastRenderedInput = rendered
        return rendered
    }
}

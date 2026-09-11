package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.GridPlacement
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.data.KeyboardLayoutItem
import com.kazumaproject.custom_keyboard.data.SpacerItem

/**
 * GODAN layout for foldables.
 *
 * The cover display renders the familiar five-column Google GODAN arrangement.
 * On a wide display the two three-column letter blocks are mirrored while the
 * utility columns stay at the two outer edges. This removes duplicated controls
 * next to the hinge and leaves a generous thumb gap in the middle.
 */
object MirrorGodanLayouts {
    const val MIRROR_MIN_WIDTH_DP = 600

    private const val COMPACT_UNITS = 10
    private const val LETTER_PANEL_UNITS = 6
    private const val SPECIAL_COLUMN_UNITS = 2
    private const val GAP_UNITS = 4
    private const val ROW_UNITS = 10

    fun shouldMirror(screenWidthDp: Int): Boolean = screenWidthDp >= MIRROR_MIN_WIDTH_DP

    fun create(mirrored: Boolean): KeyboardLayout {
        val items = buildList {
            if (mirrored) {
                addLeftSpecialColumn(column = 0)
                addLetterPanel(prefix = "left", firstColumn = 1)
                add(
                    SpacerItem(
                        id = "hinge_gap",
                        placement = GridPlacement(
                            rowUnits = 0,
                            columnUnits = SPECIAL_COLUMN_UNITS + LETTER_PANEL_UNITS,
                            rowSpanUnits = ROW_UNITS,
                            columnSpanUnits = GAP_UNITS,
                        ),
                    ),
                )
                addLetterPanel(prefix = "right", firstColumn = 6)
                addRightSpecialColumn(column = 9)
            } else {
                addLeftSpecialColumn(column = 0)
                addLetterPanel(prefix = "left", firstColumn = 1)
                addRightSpecialColumn(column = 4)
            }
        }
        val columnUnits = if (mirrored) {
            SPECIAL_COLUMN_UNITS * 2 + LETTER_PANEL_UNITS * 2 + GAP_UNITS
        } else {
            COMPACT_UNITS
        }
        return KeyboardLayout(
            keys = items.filterIsInstance<KeyItem>().map { it.keyData },
            flickKeyMaps = godanFlickMaps,
            columnCount = columnUnits / 2,
            rowCount = ROW_UNITS / 2,
            isRomaji = true,
            items = items,
            columnUnitCount = columnUnits,
            rowUnitCount = ROW_UNITS,
            isFlexiblePlacementLayout = true,
        )
    }

    private fun MutableList<KeyboardLayoutItem>.addLeftSpecialColumn(column: Int) {
        addSpecial(
            "outer_left",
            "emoji",
            "",
            0,
            column,
            KeyAction.ShowEmojiKeyboard,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24,
        )
        addSpecial(
            "outer_left",
            "cursor_left",
            "",
            1,
            column,
            KeyAction.MoveCursorLeft,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_left_24,
        )
        addSpecial("outer_left", "symbols", "?123", 2, column, KeyAction.SwitchToNumberLayout)
        addSpecial("outer_left", "mode", "ABC", 3, column, KeyAction.ChangeInputMode)
        addSpecial(
            "outer_left",
            "switch_ime",
            "",
            4,
            column,
            KeyAction.SwitchToNextIme,
            drawableResId = com.kazumaproject.core.R.drawable.language_24dp,
        )
    }

    private fun MutableList<KeyboardLayoutItem>.addRightSpecialColumn(column: Int) {
        addSpecial(
            "outer_right",
            "delete",
            "",
            0,
            column,
            KeyAction.Delete,
            drawableResId = com.kazumaproject.core.R.drawable.backspace_24px,
        )
        addSpecial(
            "outer_right",
            "cursor_right",
            "",
            1,
            column,
            KeyAction.MoveCursorRight,
            drawableResId = com.kazumaproject.core.R.drawable.baseline_arrow_right_24,
        )
        addSpecial("outer_right", "space", "空白", 2, column, KeyAction.Space)
        addSpecial(
            "outer_right",
            "enter",
            "確定",
            3,
            column,
            KeyAction.Enter,
            rowSpan = 2,
        )
    }

    private fun MutableList<KeyboardLayoutItem>.addLetterPanel(prefix: String, firstColumn: Int) {
        addRomaji(prefix, "a", "A", "a", 0, firstColumn)
        addRomaji(prefix, "k", "K", "k", 0, firstColumn + 1)
        addRomaji(prefix, "h", "H", "h", 0, firstColumn + 2)

        addRomaji(prefix, "i", "I", "i", 1, firstColumn)
        addRomaji(prefix, "s", "S", "s", 1, firstColumn + 1)
        addRomaji(prefix, "m", "M", "m", 1, firstColumn + 2)

        addRomaji(prefix, "u", "U", "u", 2, firstColumn)
        addRomaji(prefix, "t", "T", "t", 2, firstColumn + 1)
        addRomaji(prefix, "y", "Y", "y", 2, firstColumn + 2)

        addRomaji(prefix, "e", "E", "e", 3, firstColumn)
        addRomaji(prefix, "n", "N", "n", 3, firstColumn + 1)
        addRomaji(prefix, "r", "R", "r", 3, firstColumn + 2)

        addRomaji(prefix, "o", "O", "o", 4, firstColumn)
        addNormalAction(
            prefix,
            "small",
            "小",
            4,
            firstColumn + 1,
            KeyAction.InputText("ひらがな小文字"),
        )
        addRomaji(prefix, "w", "W", "w", 4, firstColumn + 2)
    }

    private fun MutableList<KeyboardLayoutItem>.addRomaji(
        prefix: String,
        id: String,
        label: String,
        text: String,
        row: Int,
        column: Int,
    ) {
        val keyId = "godan_${prefix}_$id"
        val data = KeyData(
            label = label,
            row = row,
            column = column,
            isFlickable = true,
            action = KeyAction.Text(text),
            keyId = keyId,
            keyType = KeyType.CROSS_FLICK,
            isSpecialKey = false,
        )
        add(
            KeyItem(
                id = keyId,
                keyData = data,
                placement = GridPlacement(row * 2, column * 2, 2, 2),
            ),
        )
    }

    private fun MutableList<KeyboardLayoutItem>.addSpecial(
        prefix: String,
        id: String,
        label: String,
        row: Int,
        column: Int,
        action: KeyAction,
        rowSpan: Int = 1,
        drawableResId: Int? = null,
    ) {
        val keyId = "godan_${prefix}_$id"
        val data = KeyData(
            label = label,
            row = row,
            column = column,
            isFlickable = false,
            action = action,
            rowSpan = rowSpan,
            drawableResId = drawableResId,
            keyId = keyId,
            keyType = KeyType.NORMAL,
            isSpecialKey = true,
        )
        add(
            KeyItem(
                id = keyId,
                keyData = data,
                placement = GridPlacement(row * 2, column * 2, rowSpan * 2, 2),
            ),
        )
    }

    private fun MutableList<KeyboardLayoutItem>.addNormalAction(
        prefix: String,
        id: String,
        label: String,
        row: Int,
        column: Int,
        action: KeyAction,
    ) {
        val keyId = "godan_${prefix}_$id"
        val data = KeyData(
            label = label,
            row = row,
            column = column,
            isFlickable = false,
            action = action,
            keyId = keyId,
            keyType = KeyType.NORMAL,
            isSpecialKey = false,
        )
        add(
            KeyItem(
                id = keyId,
                keyData = data,
                placement = GridPlacement(row * 2, column * 2, 2, 2),
            ),
        )
    }

    private fun flick(
        tap: String,
        left: String? = null,
        up: String? = null,
        upLabel: String? = null,
        right: String? = null,
        down: String? = null,
    ): List<Map<FlickDirection, FlickAction>> = listOf(
        buildMap {
            put(FlickDirection.TAP, FlickAction.Input(tap))
            left?.let { put(FlickDirection.UP_LEFT_FAR, guideInput(it)) }
            up?.let { put(FlickDirection.UP, guideInput(it, upLabel)) }
            right?.let { put(FlickDirection.UP_RIGHT_FAR, guideInput(it)) }
            down?.let { put(FlickDirection.DOWN, guideInput(it)) }
        },
    )

    private fun guideInput(output: String, label: String? = null): FlickAction.Input {
        val displayLabel = label ?: output
            .takeIf { it.length == 1 && it.single() in 'a'..'z' }
            ?.uppercase()
        return FlickAction.Input(output, label = displayLabel)
    }

    private val godanFlickMaps = mapOf(
        "A" to flick("a", down = "1"),
        "K" to flick("k", up = "q", right = "g", down = "2"),
        "H" to flick("h", left = "p", up = "f", right = "b", down = "3"),
        "I" to flick("i", down = "4"),
        "S" to flick("s", up = "j", right = "z", down = "5"),
        "M" to flick("m", left = "/", up = "l", right = "-", down = "6"),
        "U" to flick("u", down = "7"),
        // GODAN labels this direction as C, but it must feed "ch" into the
        // romaji composer so C + I produces ち instead of a literal "cい".
        "T" to flick("t", up = "ch", upLabel = "C", right = "d", down = "8"),
        "Y" to flick("y", left = "(", up = "x", right = ")", down = "9"),
        "E" to flick("e"),
        "N" to flick("n", left = ":", right = ".", down = "0"),
        "R" to flick("r", left = "。", up = "?", right = "!", down = "、"),
        "O" to flick("o"),
        "W" to flick("w", left = "「", up = "v", right = "」"),
    )
}

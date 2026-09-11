package com.kazumaproject.custom_keyboard.layout

import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyItem
import com.kazumaproject.custom_keyboard.data.SpacerItem
import com.kazumaproject.custom_keyboard.data.hasPlacementIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorGodanLayoutsTest {
    @Test
    fun compactLayoutContainsOneCompleteGodanPanel() {
        val layout = MirrorGodanLayouts.create(mirrored = false)

        assertEquals(5, layout.columnCount)
        assertEquals(5, layout.rowCount)
        assertEquals(24, layout.keys.size)
        assertTrue(layout.isRomaji)
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
        assertEquals(
            KeyAction.Text("k"),
            layout.keys.single { it.keyId == "godan_left_k" }.action,
        )
    }

    @Test
    fun mirroredLayoutContainsTwoEquivalentPanelsAndHingeGap() {
        val layout = MirrorGodanLayouts.create(mirrored = true)
        val keyItems = layout.items.filterIsInstance<KeyItem>()
        val left = keyItems.filter { it.id.startsWith("godan_left_") }
            .associateBy { it.id.removePrefix("godan_left_") }
        val right = keyItems.filter { it.id.startsWith("godan_right_") }
            .associateBy { it.id.removePrefix("godan_right_") }

        assertEquals(10, layout.columnCount)
        assertEquals(left.keys, right.keys)
        left.forEach { (id, leftItem) ->
            val rightItem = requireNotNull(right[id])
            assertEquals(leftItem.keyData.label, rightItem.keyData.label)
            assertEquals(leftItem.keyData.action, rightItem.keyData.action)
            assertEquals(leftItem.placement.columnUnits + 10, rightItem.placement.columnUnits)
        }
        assertEquals(39, layout.keys.size)
        assertTrue(keyItems.none { it.id.startsWith("godan_left_delete") })
        assertTrue(keyItems.none { it.id.startsWith("godan_right_emoji") })
        assertTrue(keyItems.any { it.id == "godan_outer_left_emoji" })
        assertTrue(keyItems.any { it.id == "godan_outer_right_delete" })
        assertTrue(layout.items.any { it is SpacerItem && it.id == "hinge_gap" })
        assertFalse(hasPlacementIssues(layout.items, layout.rowUnitCount, layout.columnUnitCount))
    }

    @Test
    fun foldableThresholdUsesSinglePanelBelow600dp() {
        assertFalse(MirrorGodanLayouts.shouldMirror(599))
        assertTrue(MirrorGodanLayouts.shouldMirror(600))
    }

    @Test
    fun consonantFlicksExposeVoicedAndSemiVoicedRows() {
        val layout = MirrorGodanLayouts.create(mirrored = false)
        val k = requireNotNull(layout.flickKeyMaps["K"]?.firstOrNull())
        val h = requireNotNull(layout.flickKeyMaps["H"]?.firstOrNull())
        val t = requireNotNull(layout.flickKeyMaps["T"]?.firstOrNull())

        assertEquals(FlickAction.Input("g", label = "G"), k[FlickDirection.UP_RIGHT_FAR])
        assertEquals(FlickAction.Input("p", label = "P"), h[FlickDirection.UP_LEFT_FAR])
        assertEquals(FlickAction.Input("b", label = "B"), h[FlickDirection.UP_RIGHT_FAR])
        assertEquals(FlickAction.Input("ch", label = "C"), t[FlickDirection.UP])
    }

    @Test
    fun symbolsKeyOpensNumberLayoutInsteadOfEmoji() {
        val layout = MirrorGodanLayouts.create(mirrored = true)

        assertEquals(
            KeyAction.SwitchToNumberLayout,
            layout.keys.single { it.keyId == "godan_outer_left_symbols" }.action,
        )
        assertEquals(
            KeyAction.ShowEmojiKeyboard,
            layout.keys.single { it.keyId == "godan_outer_left_emoji" }.action,
        )
    }
}

package com.kazumaproject.markdownhelperkeyboard.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ApplicationProvider
import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.domain.state.QWERTYMode
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.qwerty_keyboard.R as QwertyR
import com.kazumaproject.qwerty_keyboard.ui.QWERTYKeyboardView
import com.kazumaproject.qwerty_keyboard.ui.QwertyKeyboardUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 英語 QWERTY では "." を常に出し、その分 Return を縮めて最下段の合計幅を保つ。
 * 日本語ローマ字モードの「、。」は従来どおり「句読点ボタン」設定に従う。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QwertyPeriodKeyLayoutTest {

    private fun view(): QWERTYKeyboardView {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_MarkdownKeyboard
        )
        return QWERTYKeyboardView(context)
    }

    private fun QWERTYKeyboardView.render(romaji: Boolean, showKutouten: Boolean) {
        setSpecialKeyVisibility(
            showCursors = false,
            showSwitchKey = true,
            showKutouten = showKutouten,
        )
        renderUiState(
            QwertyKeyboardUiState(
                qwertyMode = QWERTYMode.Default,
                capsLockState = CapsLockState(),
                romajiMode = romaji,
                enterKeyText = "Done",
                spaceKeyText = "space",
                showRomajiEnglishSwitchKey = true,
            )
        )
        val density = resources.displayMetrics.density
        measure(
            View.MeasureSpec.makeMeasureSpec((360 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((260 * density).toInt(), View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, measuredWidth, measuredHeight)
    }

    private fun View.weight(): Float = (layoutParams as ConstraintLayout.LayoutParams).horizontalWeight

    @Test
    fun englishModeAlwaysShowsPeriodAndShrinksReturn() {
        val v = view()
        v.render(romaji = false, showKutouten = false)
        val period = v.findViewById<View>(QwertyR.id.key_kuten)
        val comma = v.findViewById<View>(QwertyR.id.key_touten)
        val enter = v.findViewById<View>(QwertyR.id.key_return)
        val space = v.findViewById<View>(QwertyR.id.key_space)

        assertEquals(View.VISIBLE, period.visibility)
        assertEquals(View.GONE, comma.visibility)
        assertEquals(0.5f, enter.weight())
        assertEquals(0.5f, period.weight())
        // "." と Return を合わせても元の Return (1.0) と同じ幅なので、スペースは元の比率を保つ
        assertEquals(1f, space.weight())
        assertTrue("period key must be laid out", period.width > 0)
        assertTrue("return key must be laid out", enter.width > 0)
        // "." を Return の直左に置き、Return 側が広くなりすぎないこと
        assertTrue(period.right <= enter.left)
        assertTrue(enter.width <= space.width)
    }

    @Test
    fun romajiModeKeepsPunctuationBehindPreferenceAndFullWidthReturn() {
        val v = view()
        v.render(romaji = true, showKutouten = false)
        assertEquals(View.GONE, v.findViewById<View>(QwertyR.id.key_kuten).visibility)
        assertEquals(View.GONE, v.findViewById<View>(QwertyR.id.key_touten).visibility)
        assertEquals(1f, v.findViewById<View>(QwertyR.id.key_return).weight())

        v.render(romaji = true, showKutouten = true)
        assertEquals(View.VISIBLE, v.findViewById<View>(QwertyR.id.key_kuten).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(QwertyR.id.key_touten).visibility)
        assertEquals(1f, v.findViewById<View>(QwertyR.id.key_return).weight())
    }

    @Test
    fun switchingBackToEnglishRestoresCompactReturn() {
        val v = view()
        v.render(romaji = true, showKutouten = false)
        v.render(romaji = false, showKutouten = false)
        assertEquals(View.VISIBLE, v.findViewById<View>(QwertyR.id.key_kuten).visibility)
        assertEquals(0.5f, v.findViewById<View>(QwertyR.id.key_return).weight())
    }
}

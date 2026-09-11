package com.kazumaproject.markdownhelperkeyboard.ime_service.voice

import android.os.Build
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceInputAvailabilityInstrumentedTest {
    @Test
    fun deviceProvidesSpeechRecognitionBackend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val onDeviceAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

        Log.i(
            TAG,
            "sdk=${Build.VERSION.SDK_INT} " +
                "systemAvailable=$systemAvailable onDeviceAvailable=$onDeviceAvailable",
        )
        assertTrue("No Android speech recognition service is available", systemAvailable)
    }

    private companion object {
        const val TAG = "VoiceInputProbe"
    }
}

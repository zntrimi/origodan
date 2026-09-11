package com.kazumaproject.markdownhelperkeyboard.ime_service.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Transparent permission bridge used because an InputMethodService cannot show a permission UI. */
class VoiceInputPermissionActivity : ComponentActivity() {
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        finishWithResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (resultReceiver() == null) {
            finish()
            return
        }
        if (savedInstanceState != null) return

        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            finishWithResult(granted = true)
        } else {
            permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun finishWithResult(granted: Boolean) {
        resultReceiver()?.send(if (granted) RESULT_GRANTED else RESULT_DENIED, Bundle.EMPTY)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun resultReceiver(): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_RECEIVER)
        }

    companion object {
        const val EXTRA_RESULT_RECEIVER =
            "com.kazumaproject.markdownhelperkeyboard.extra.VOICE_PERMISSION_RESULT_RECEIVER"
        const val RESULT_GRANTED = 1
        const val RESULT_DENIED = 0
    }
}

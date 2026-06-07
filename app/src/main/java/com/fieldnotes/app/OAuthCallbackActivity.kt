// FieldNotes — OAuthCallbackActivity.kt
// Authored by: drive-sync module | Implements: 07_DRIVE_SYNC_MODULE.md (OAuth redirect handling)
package com.fieldnotes.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fieldnotes.app.core.sync.DriveAuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject lateinit var driveAuthManager: DriveAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            finishToApp(false)
            return
        }
        lifecycleScope.launch {
            val ok = driveAuthManager.handleOAuthCallback(code)
            finishToApp(ok)
        }
    }

    private fun finishToApp(success: Boolean) {
        Toast.makeText(
            this,
            if (success) "Google Drive connected" else "Drive sign-in failed",
            Toast.LENGTH_SHORT,
        ).show()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}

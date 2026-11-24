package com.tamadon.smsmessenger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.AppState
import com.tamadon.smsmessenger.core.LockManager
import com.tamadon.smsmessenger.core.Loc
import com.tamadon.smsmessenger.data.Prefs
import com.tamadon.smsmessenger.data.ProviderSync
import kotlinx.coroutines.launch

data class Target(val address: String, val body: String?)

/** FragmentActivity (not ComponentActivity) because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {
    private val target: MutableState<Target?> = mutableStateOf(null)

    /** Applies the in-app language (English / Persian / system) to this activity's resources. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Loc.wrap(newBase, Prefs(newBase).language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) target.value = parse(intent)
        setContent {
            AppTheme {
                AppLockGate(this) { AppNav(this, target) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target.value = parse(intent)
    }

    override fun onStart() {
        super.onStart()
        AppState.uiVisible = true
    }

    override fun onResume() {
        super.onResume()
        // Another SMS app may have added messages (or changed read state) while we were away.
        val app = App.instance
        app.appScope.launch { ProviderSync.syncInto(applicationContext, app.repo) }
    }

    override fun onStop() {
        super.onStop()
        AppState.uiVisible = false
        // Locked conversations lock again as soon as the app leaves the foreground.
        if (!isChangingConfigurations) {
            LockManager.relockAll()
            if (App.instance.prefs.appLockEnabled) LockManager.relockApp()
        }
    }

    private fun parse(i: Intent?): Target? {
        i ?: return null
        i.getStringExtra(EXTRA_ADDRESS)?.let { return Target(it, null) }
        val data = i.data ?: return null
        val scheme = data.scheme ?: return null
        if (scheme in listOf("sms", "smsto", "mms", "mmsto")) {
            val number = Uri.decode(data.schemeSpecificPart).substringBefore('?').trim()
            val body = i.getStringExtra("sms_body")
                ?: i.getStringExtra(Intent.EXTRA_TEXT)
                ?: Uri.decode(data.schemeSpecificPart).substringAfter("body=", "").substringBefore('&').ifEmpty { null }
            if (number.isNotEmpty()) return Target(number, body)
        }
        return null
    }

    companion object {
        const val EXTRA_ADDRESS = "address"
    }
}

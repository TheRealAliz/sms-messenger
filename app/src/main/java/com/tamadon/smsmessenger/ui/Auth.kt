package com.tamadon.smsmessenger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.LockManager

/**
 * Returns a function that runs [onOk] only after the user authenticates
 * (biometric first, PIN as fallback). Used for sensitive actions such as removing a lock.
 */
@Composable
fun rememberAuthenticator(activity: FragmentActivity): (onOk: () -> Unit) -> Unit {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPin by remember { mutableStateOf(false) }

    if (showPin) {
        VerifyPinDialog(
            onSuccess = {
                showPin = false
                val action = pending
                pending = null
                action?.invoke()
            },
            onDismiss = { showPin = false; pending = null }
        )
    }

    return remember(activity) {
        { onOk: () -> Unit ->
            pending = onOk
            val prefs = App.instance.prefs
            if (prefs.biometricEnabled && canUseBiometric(activity)) {
                promptBiometric(
                    activity,
                    onSuccess = {
                        val action = pending
                        pending = null
                        action?.invoke()
                    },
                    onFallback = { showPin = true }
                )
            } else {
                showPin = true
            }
        }
    }
}

/**
 * Covers the whole app with a lock screen while [com.tamadon.smsmessenger.data.Prefs.appLockEnabled]
 * is on and this foreground session has not been unlocked yet.
 */
@Composable
fun AppLockGate(activity: FragmentActivity, content: @Composable () -> Unit) {
    val prefs = App.instance.prefs
    val locked = prefs.appLockEnabled && !LockManager.appUnlocked
    if (!locked) {
        content()
        return
    }

    val biometric = prefs.biometricEnabled && canUseBiometric(activity)
    var showPin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (biometric) {
            promptBiometric(
                activity,
                onSuccess = { LockManager.markAppUnlocked() },
                onFallback = { showPin = true }
            )
        } else {
            showPin = true
        }
    }

    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.app_locked_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_locked_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            if (biometric) {
                Button(onClick = {
                    promptBiometric(
                        activity,
                        onSuccess = { LockManager.markAppUnlocked() },
                        onFallback = { showPin = true }
                    )
                }) { Text(stringResource(R.string.biometric_button)) }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { showPin = true }) { Text(stringResource(R.string.enter_pin)) }
        }
    }

    if (showPin) {
        VerifyPinDialog(
            onSuccess = { showPin = false; LockManager.markAppUnlocked() },
            onDismiss = { showPin = false }
        )
    }
}

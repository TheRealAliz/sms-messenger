package com.tamadon.smsmessenger.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.os.Build
import android.provider.Telephony
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.Contacts
import com.tamadon.smsmessenger.core.Images
import com.tamadon.smsmessenger.core.LockManager
import com.tamadon.smsmessenger.core.Loc
import com.tamadon.smsmessenger.core.Phone
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

// ---------------------------------------------------------------- formatting (Jalali / Gregorian)

object Fmt {
    private val cache = HashMap<String, SimpleDateFormat>()

    /** Persian digits for Persian, Latin for English; calendar from settings ("auto" follows language). */
    private fun locale(): ULocale {
        val p = App.instance.prefs
        val fa = Loc.isPersian(p.language)
        val cal = when (p.calendar) {
            "jalali" -> "persian"
            "gregorian" -> "gregorian"
            else -> if (fa) "persian" else "gregorian"
        }
        return ULocale((if (fa) "fa_IR" else "en_US") + "@calendar=" + cal)
    }

    private fun fmt(pattern: String, ts: Long): String {
        val loc = locale()
        val f = cache.getOrPut("$loc|$pattern") { SimpleDateFormat(pattern, loc) }
        return f.format(Date(ts))
    }

    fun time(ts: Long) = fmt("HH:mm", ts)

    /** Full date and time, used by the message details dialog. */
    fun full(ts: Long) = fmt("yyyy/MM/dd HH:mm", ts)

    fun sameDay(a: Long, b: Long): Boolean {
        val x = Calendar.getInstance().apply { timeInMillis = a }
        val y = Calendar.getInstance().apply { timeInMillis = b }
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    }

    fun listStamp(ts: Long): String {
        val now = System.currentTimeMillis()
        return when {
            sameDay(ts, now) -> time(ts)
            fmt("yyyy", ts) == fmt("yyyy", now) -> fmt("d MMM", ts)
            else -> fmt("yyyy/MM/dd", ts)
        }
    }

    fun dayHeader(ctx: Context, ts: Long): String {
        val now = System.currentTimeMillis()
        return when {
            sameDay(ts, now) -> ctx.getString(R.string.today)
            sameDay(ts, now - 86_400_000L) -> ctx.getString(R.string.yesterday)
            fmt("yyyy", ts) == fmt("yyyy", now) -> fmt("EEEE d MMMM", ts)
            else -> fmt("d MMMM yyyy", ts)
        }
    }
}

// ---------------------------------------------------------------- small helpers

fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.READ_PHONE_STATE) // to list SIM cards and choose which one sends
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

fun hasPermission(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Intent that asks the user to make this app the default SMS app, or null if already default. */
fun defaultSmsIntent(ctx: Context): Intent? =
    if (Build.VERSION.SDK_INT >= 29) {
        ctx.getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_SMS) && !it.isRoleHeld(RoleManager.ROLE_SMS) }
            ?.createRequestRoleIntent(RoleManager.ROLE_SMS)
    } else {
        Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, ctx.packageName)
    }

@Composable
fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(block)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) current() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Contact name for [address]; synchronous (contacts live in memory) and refreshed when they reload. */
@Composable
fun rememberName(address: String): String {
    val version = Contacts.version
    return remember(address, version) { Contacts.nameOf(address) ?: address }
}

/** True when [address] belongs to a saved contact; refreshed when contacts are reloaded. */
@Composable
fun rememberIsContact(address: String): Boolean {
    val version = Contacts.version
    return remember(address, version) { Contacts.nameOf(address) != null }
}

/** Profile photo URI of the contact behind [address], when available. */
@Composable
fun rememberPhoto(address: String): String? {
    val version = Contacts.version
    return remember(address, version) { Contacts.photoOf(address) }
}

/**
 * Shows a snackbar that disappears after [millis] (the stock Short / Long durations are 4 s / 10 s,
 * which is too long for a simple "moved to trash" confirmation).
 */
suspend fun SnackbarHostState.showBrief(
    message: String,
    actionLabel: String? = null,
    millis: Long = 2200L
): SnackbarResult = coroutineScope {
    val timer = launch {
        delay(millis)
        currentSnackbarData?.dismiss()
    }
    val result = showSnackbar(message, actionLabel, duration = SnackbarDuration.Indefinite)
    timer.cancel()
    result
}

@Composable
fun Avatar(
    name: String,
    locked: Boolean,
    size: Dp = 46.dp,
    contact: Boolean = false,
    photoUri: String? = null
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (contact) cs.tertiaryContainer else cs.primaryContainer
    val fg = if (contact) cs.onTertiaryContainer else cs.onPrimaryContainer
    val ctx = LocalContext.current
    val photo by produceState<Bitmap?>(initialValue = null, photoUri) {
        value = if (!locked && photoUri != null) Images.local(ctx, photoUri, maxPx = 256) else null
    }
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        val letter = name.firstOrNull { it.isLetter() }
        val bitmap = photo
        when {
            locked -> Icon(Icons.Filled.Lock, null, tint = fg)
            bitmap != null -> Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            letter != null -> Text(letter.uppercase(), style = MaterialTheme.typography.titleMedium, color = fg)
            else -> Icon(Icons.Filled.Person, null, tint = fg)
        }
    }
}

/**
 * Rounded, low-height search field used by the search screens (keeps the stock look but lighter).
 */
@Composable
fun ModernSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = cs.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().heightIn(min = 42.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = cs.onSurface,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.cancel), Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** [text] with every occurrence of [query] highlighted (works for Persian digits too). */
@Composable
fun highlightedText(text: String, query: String): AnnotatedString {
    val q = Phone.asciiDigits(query).trim()
    if (q.isEmpty()) return AnnotatedString(text)
    val cs = MaterialTheme.colorScheme
    val hay = Phone.asciiDigits(text)
    val style = SpanStyle(
        color = cs.primary,
        fontWeight = FontWeight.Bold,
        background = cs.primary.copy(alpha = 0.16f)
    )
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (from <= hay.length - q.length) {
            val i = hay.indexOf(q, from, ignoreCase = true)
            if (i < 0) break
            addStyle(style, i, i + q.length)
            from = i + q.length.coerceAtLeast(1)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------- PIN & biometric

fun canUseBiometric(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(activity: FragmentActivity, onSuccess: () -> Unit, onFallback: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFallback()
        }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.unlock_conversation))
        .setNegativeButtonText(activity.getString(R.string.use_pin))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    prompt.authenticate(info)
}

@Composable
fun SetPinDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val tooShort = stringResource(R.string.pin_too_short)
    val mismatch = stringResource(R.string.pin_mismatch)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_backup_pin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pin_explain))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) { pin = v; error = null } },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = again,
                    onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) { again = v; error = null } },
                    label = { Text(stringResource(R.string.pin_repeat)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error!!) } } else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = Phone.asciiDigits(pin)
                when {
                    a.length < 4 -> error = tooShort
                    a != Phone.asciiDigits(again) -> error = mismatch
                    else -> onDone(a)
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun VerifyPinDialog(onSuccess: () -> Unit, onDismiss: () -> Unit) {
    val prefs = App.instance.prefs
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val wrong = stringResource(R.string.pin_wrong)
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_pin)) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> if (v.length <= 8 && v.all { it.isDigit() }) { pin = v; error = null } },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                supportingText = if (error != null) { { Text(error!!) } } else null
            )
        },
        confirmButton = {
            TextButton(onClick = {
                when (val r = LockManager.verify(prefs, Phone.asciiDigits(pin))) {
                    is LockManager.Verify.Ok -> onSuccess()
                    is LockManager.Verify.Wrong -> { error = wrong; pin = "" }
                    is LockManager.Verify.Locked -> {
                        error = ctx.getString(R.string.pin_wait, (r.remainingMs / 1000) + 1)
                        pin = ""
                    }
                }
            }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Full-screen gate shown in place of a locked conversation until the user authenticates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockGate(activity: FragmentActivity, onUnlocked: () -> Unit, onBack: () -> Unit) {
    val prefs = App.instance.prefs
    val biometric = prefs.biometricEnabled && canUseBiometric(activity)
    var showPin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (biometric) promptBiometric(activity, onSuccess = onUnlocked, onFallback = { showPin = true })
        else showPin = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.locked_conversation)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.unlock_to_view), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            if (biometric) {
                Button(onClick = {
                    promptBiometric(activity, onSuccess = onUnlocked, onFallback = { showPin = true })
                }) { Text(stringResource(R.string.biometric_button)) }
            }
            TextButton(onClick = { showPin = true }) { Text(stringResource(R.string.enter_pin)) }
        }
    }
    if (showPin) VerifyPinDialog(onSuccess = onUnlocked, onDismiss = { showPin = false })
}

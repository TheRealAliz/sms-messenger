package com.tamadon.smsmessenger.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.Contacts
import com.tamadon.smsmessenger.core.LockManager
import com.tamadon.smsmessenger.data.ConversationRow
import com.tamadon.smsmessenger.data.ProviderSync
import com.tamadon.smsmessenger.data.Status
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private val importing = AtomicBoolean(false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(nav: NavHostController, activity: FragmentActivity, mode: ListMode) {
    val app = App.instance
    val repo = app.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val source = when (mode) {
        ListMode.HOME -> repo.homeRows
        ListMode.ARCHIVE -> repo.archivedRows
        ListMode.SPAM -> repo.spamRows
        ListMode.BLOCKED -> repo.blockedRows
    }
    // null = still loading; nothing is drawn (no "empty" flash) until the first result arrives
    val rows: List<ConversationRow>? by source.collectAsState()

    val selected = remember { mutableStateListOf<String>() }
    val snack = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }
    var selMenuOpen by remember { mutableStateOf(false) }
    var showSetPin by remember { mutableStateOf(false) }
    var pendingLockKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var isDefault by remember { mutableStateOf(ProviderSync.isDefault(ctx)) }
    val auth = rememberAuthenticator(activity)

    val trashedText = stringResource(R.string.moved_to_trash)
    val undoText = stringResource(R.string.undo)

    fun startImport() {
        if (!hasPermission(ctx, android.Manifest.permission.READ_SMS)) return
        if (!importing.compareAndSet(false, true)) return
        scope.launch {
            try {
                ProviderSync.syncInto(ctx, repo)
                app.prefs.imported = true
            } finally {
                importing.set(false)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch { Contacts.reload(ctx) }
        startImport()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = ProviderSync.isDefault(ctx)
        startImport()
    }

    LaunchedEffect(Unit) {
        repo.seedQuickRepliesIfNeeded(
            listOf(R.string.quick_default_1, R.string.quick_default_2, R.string.quick_default_3, R.string.quick_default_4)
                .map { ctx.getString(it) }
        )
        val missing = requiredPermissions().filter { !hasPermission(ctx, it) }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray()) else startImport()
    }
    // Re-check on every return to the app (the user may have changed the default app in system settings)
    OnResume {
        isDefault = ProviderSync.isDefault(ctx)
        scope.launch { Contacts.reload(ctx) }
    }

    BackHandler(selected.isNotEmpty()) { selected.clear() }

    // Keep the selection valid when rows disappear (archived, blocked, trashed ...)
    LaunchedEffect(rows) { rows?.let { r -> selected.retainAll(r.map { it.threadKey }.toSet()) } }

    val list = rows.orEmpty()
    val sel = list.filter { it.threadKey in selected }
    val inSelection = selected.isNotEmpty()
    val needsDefaultPrompt = mode == ListMode.HOME && !isDefault
    val inArchive = mode == ListMode.ARCHIVE
    val normalMode = mode == ListMode.HOME || mode == ListMode.ARCHIVE

    // First lock ever: ask for the backup PIN, then lock the chats that were selected.
    if (showSetPin) {
        SetPinDialog(
            onDone = { pin ->
                LockManager.setPin(app.prefs, pin)
                val keys = pendingLockKeys
                scope.launch { repo.setLocked(keys, true) }
                keys.forEach { LockManager.markUnlocked(it) }
                showSetPin = false
                pendingLockKeys = emptyList()
                selected.clear()
            },
            onDismiss = { showSetPin = false; pendingLockKeys = emptyList() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            if (inSelection) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selected.clear() }) { Icon(Icons.Filled.Close, stringResource(R.string.deselect)) }
                    },
                    actions = {
                        if (normalMode) {
                            val allPinned = sel.isNotEmpty() && sel.all { it.pinned }
                            IconButton(onClick = {
                                val keys = selected.toList()
                                scope.launch { repo.setPinned(keys, !allPinned) }
                                selected.clear()
                            }) { Icon(Icons.Filled.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin)) }

                            IconButton(onClick = {
                                val keys = selected.toList()
                                scope.launch { repo.setArchived(keys, !inArchive) }
                                selected.clear()
                            }) {
                                Icon(
                                    if (inArchive) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                    stringResource(if (inArchive) R.string.unarchive else R.string.archive)
                                )
                            }
                        }

                        val allLocked = sel.isNotEmpty() && sel.all { it.locked }
                        IconButton(onClick = {
                            val keys = selected.toList()
                            if (allLocked) {
                                // Removing a lock needs authentication
                                auth {
                                    scope.launch { repo.setLocked(keys, false) }
                                    selected.clear()
                                }
                            } else if (!LockManager.hasPin(app.prefs)) {
                                pendingLockKeys = keys
                                showSetPin = true
                            } else {
                                scope.launch { repo.setLocked(keys, true) }
                                keys.forEach { LockManager.markUnlocked(it) }
                                selected.clear()
                            }
                        }) {
                            Icon(
                                if (allLocked) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                stringResource(if (allLocked) R.string.unlock else R.string.lock)
                            )
                        }

                        IconButton(onClick = { selMenuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = selMenuOpen, onDismissRequest = { selMenuOpen = false }) {
                            if (normalMode) {
                                val allHidden = sel.isNotEmpty() && sel.all { it.hideNotif }
                                val allMuted = sel.isNotEmpty() && sel.all { it.muted }
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (allHidden) R.string.show_in_notif else R.string.hide_in_notif)) },
                                    leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                                    onClick = {
                                        selMenuOpen = false
                                        val keys = selected.toList()
                                        scope.launch { repo.setHideNotif(keys, !allHidden) }
                                        selected.clear()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (allMuted) R.string.unmute else R.string.mute)) },
                                    leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                                    onClick = {
                                        selMenuOpen = false
                                        val keys = selected.toList()
                                        scope.launch { repo.setMuted(keys, !allMuted) }
                                        selected.clear()
                                    }
                                )
                            }
                            // Spam: report from the normal lists, restore from the spam folder
                            if (mode != ListMode.BLOCKED) {
                                val toSpam = mode != ListMode.SPAM
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (toSpam) R.string.mark_spam else R.string.not_spam)) },
                                    leadingIcon = { Icon(Icons.Filled.Report, null) },
                                    onClick = {
                                        selMenuOpen = false
                                        val keys = selected.toList()
                                        scope.launch { repo.setSpam(keys, toSpam) }
                                        selected.clear()
                                    }
                                )
                            }
                            // Block / unblock
                            val unblock = mode == ListMode.BLOCKED
                            DropdownMenuItem(
                                text = { Text(stringResource(if (unblock) R.string.unblock else R.string.block)) },
                                leadingIcon = { Icon(Icons.Filled.Block, null) },
                                onClick = {
                                    selMenuOpen = false
                                    val keys = selected.toList()
                                    scope.launch { repo.setBlocked(keys, !unblock) }
                                    selected.clear()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_to_trash)) },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                onClick = {
                                    selMenuOpen = false
                                    val keys = selected.toList()
                                    selected.clear()
                                    scope.launch {
                                        repo.trashThreads(keys)
                                        val r = snack.showBrief(trashedText, undoText)
                                        if (r == SnackbarResult.ActionPerformed) repo.restoreThreads(keys)
                                    }
                                }
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ListMode.HOME -> R.string.app_name
                                    ListMode.ARCHIVE -> R.string.title_archive
                                    ListMode.SPAM -> R.string.title_spam
                                    ListMode.BLOCKED -> R.string.title_blocked
                                }
                            )
                        )
                    },
                    navigationIcon = {
                        if (mode != ListMode.HOME) IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (mode == ListMode.HOME) {
                            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.menu)) }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                modifier = Modifier.widthIn(min = 236.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_archive)) },
                                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                    onClick = { menuOpen = false; nav.navigate("archive") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_spam)) },
                                    leadingIcon = { Icon(Icons.Filled.Report, null) },
                                    onClick = { menuOpen = false; nav.navigate("spam") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_blocked)) },
                                    leadingIcon = { Icon(Icons.Filled.Block, null) },
                                    onClick = { menuOpen = false; nav.navigate("blocked") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.starred_messages)) },
                                    leadingIcon = { Icon(Icons.Filled.Star, null) },
                                    onClick = { menuOpen = false; nav.navigate("starred") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_trash)) },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = { menuOpen = false; nav.navigate("trash") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_quick)) },
                                    leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                                    onClick = { menuOpen = false; nav.navigate("quick") })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_settings)) },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = { menuOpen = false; nav.navigate("settings") })
                            }
                        } else {
                            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (mode == ListMode.HOME && !inSelection && !needsDefaultPrompt) {
                FloatingActionButton(onClick = { nav.navigate("new") }) { Icon(Icons.Filled.Edit, stringResource(R.string.new_message)) }
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when {
                // Not the default SMS app: the prompt takes the middle of the screen and disappears once set
                needsDefaultPrompt -> DefaultSmsPrompt(onClick = { defaultSmsIntent(ctx)?.let { roleLauncher.launch(it) } })
                rows == null -> Unit
                list.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ListMode.HOME -> R.string.no_conversations
                                    ListMode.ARCHIVE -> R.string.archive_empty
                                    ListMode.SPAM -> R.string.spam_empty
                                    ListMode.BLOCKED -> R.string.blocked_empty
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.threadKey }) { row ->
                        ConversationItem(
                            row = row,
                            selected = row.threadKey in selected,
                            onClick = {
                                if (inSelection) {
                                    if (row.threadKey in selected) selected.remove(row.threadKey) else selected.add(row.threadKey)
                                } else nav.navigate(chatRoute(row.address))
                            },
                            onLongClick = {
                                if (row.threadKey in selected) selected.remove(row.threadKey) else selected.add(row.threadKey)
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSmsPrompt(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Sms, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.default_sms_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.default_sms_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(stringResource(R.string.default_sms_button)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    row: ConversationRow,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val name = rememberName(row.address)
    val you = stringResource(R.string.you_prefix).trimEnd()
    val locked = stringResource(R.string.locked_conversation)
    val preview = when {
        row.locked -> locked
        else -> (if (!row.lastIncoming) "$you: " else "") + row.lastBody.replace('\n', ' ')
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) cs.secondaryContainer else cs.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            name,
            row.locked,
            contact = rememberIsContact(row.address),
            photoUri = rememberPhoto(row.address)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (row.pinned) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.PushPin, null, Modifier.size(14.dp), tint = cs.primary) }
                if (row.muted) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.NotificationsOff, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant) }
                if (row.hideNotif) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.VisibilityOff, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant) }
                Spacer(Modifier.width(8.dp))
                // Time sits on the same line as the chat name for every row.
                Text(Fmt.listStamp(row.lastDate), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Row(
                Modifier.padding(top = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.unread > 0 && !row.locked) cs.onSurface else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Delivery mark shares the preview line, Telegram-style.
                if (!row.lastIncoming && !row.locked) {
                    Spacer(Modifier.width(6.dp))
                    LastStatusMark(row.lastStatus)
                }
                if (row.unread > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge { Text(if (row.unread > 99) "99+" else row.unread.toString()) }
                }
            }
        }
    }
}

@Composable
private fun LastStatusMark(status: Int) {
    val cs = MaterialTheme.colorScheme
    when (status) {
        Status.SENDING -> Icon(Icons.Filled.Schedule, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant)
        Status.SENT -> Icon(Icons.Filled.Done, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
        Status.DELIVERED -> Icon(Icons.Filled.DoneAll, null, Modifier.size(13.dp), tint = cs.primary)
        Status.FAILED, Status.DELIVERY_FAILED ->
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(12.dp), tint = cs.error)
        // Messages imported from the provider have no status; a single check is the honest mark.
        else -> Icon(Icons.Filled.Done, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
    }
}

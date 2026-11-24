package com.tamadon.smsmessenger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.Contacts
import com.tamadon.smsmessenger.core.LockManager
import com.tamadon.smsmessenger.core.Loc
import com.tamadon.smsmessenger.core.Notifier
import com.tamadon.smsmessenger.core.Phone
import com.tamadon.smsmessenger.core.Sims
import com.tamadon.smsmessenger.data.QuickReply
import com.tamadon.smsmessenger.data.SearchRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import android.os.Build

// =============================================================== new conversation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(nav: NavHostController) {
    val prefs = App.instance.prefs
    var query by remember { mutableStateOf("") }
    val persianFirst = Loc.isPersian(prefs.language)
    val version = Contacts.version
    // Contacts are in memory; filtering and sorting hundreds of them per keystroke is instant.
    // Contacts written in the app's language are listed first.
    val hits = remember(query, version, persianFirst) { Contacts.search(query, persianFirst) }

    val typed = Phone.asciiDigits(query.trim())
    val looksLikeNumber = typed.isNotEmpty() && typed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } &&
        typed.count { it.isDigit() } >= 3
    val numberOnly = typed.filter { it.isDigit() || it == '+' }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.title_new_message)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ModernSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.name_or_number),
                modifier = Modifier.padding(12.dp)
            )
            LazyColumn {
                if (looksLikeNumber) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.send_to_number, numberOnly)) },
                            modifier = Modifier.clickable {
                                nav.navigate(chatRoute(numberOnly)) { popUpTo("home") }
                            }
                        )
                        HorizontalDivider()
                    }
                }
                items(hits, key = { "${it.name}|${it.number}" }) { c ->
                    ListItem(
                        leadingContent = {
                            Avatar(
                                name = c.name,
                                locked = false,
                                size = 40.dp,
                                contact = true,
                                photoUri = c.photoUri
                            )
                        },
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(c.number) },
                        modifier = Modifier.clickable {
                            nav.navigate(chatRoute(c.number)) { popUpTo("home") }
                        }
                    )
                }
            }
        }
    }
}

// =============================================================== search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(nav: NavHostController) {
    val app = App.instance
    val repo = app.repo
    val prefs = app.prefs
    var query by remember { mutableStateOf("") }
    val q = Phone.asciiDigits(query).trim()
    val results by remember(q) {
        if (q.length >= 2) repo.search(q) else flowOf<List<SearchRow>>(emptyList())
    }.collectAsState(initial = emptyList())

    val persianFirst = Loc.isPersian(prefs.language)
    val version = Contacts.version
    val contacts = remember(query, version, persianFirst) {
        val text = query.trim()
        if (text.isEmpty()) emptyList() else Contacts.search(text, persianFirst).take(6)
    }
    val history = prefs.searchHistory

    fun rememberQuery() {
        if (query.isNotBlank()) prefs.addSearchHistory(query)
    }

    fun openChat(address: String, msgId: Long? = null) {
        rememberQuery()
        nav.navigate(chatRoute(address, msgId = msgId))
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                ModernSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.padding(end = 8.dp),
                    onSearch = { rememberQuery() }
                )
            },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                stringResource(R.string.search_locked_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            LazyColumn {
                // Recent searches are shown while the field is empty.
                if (query.isBlank() && history.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.search_history),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { prefs.clearSearchHistory() }) {
                                Text(stringResource(R.string.clear_search_history))
                            }
                        }
                    }
                    items(history, key = { "history:$it" }) { h ->
                        ListItem(
                            leadingContent = {
                                Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            headlineContent = { Text(h) },
                            trailingContent = {
                                IconButton(onClick = { prefs.removeSearchHistory(h) }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.delete), Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier.clickable {
                                query = h
                                rememberQuery()
                            }
                        )
                    }
                }

                // Contacts matching what is typed at the top are offered before message hits.
                if (contacts.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.search_contacts),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp)
                        )
                    }
                    items(contacts, key = { "contact:${it.name}|${it.number}" }) { c ->
                        ListItem(
                            leadingContent = {
                                Avatar(
                                    name = c.name,
                                    locked = false,
                                    size = 40.dp,
                                    contact = true,
                                    photoUri = c.photoUri
                                )
                            },
                            headlineContent = { Text(highlightedText(c.name, query)) },
                            supportingContent = { Text(c.number) },
                            modifier = Modifier.clickable { openChat(c.number) }
                        )
                        HorizontalDivider()
                    }
                }

                if (q.length >= 2) {
                    if (results.isEmpty() && contacts.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_search_results),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(results, key = { "message:${it.id}" }) { r ->
                        val name = rememberName(r.address)
                        ListItem(
                            headlineContent = { Text(highlightedText(name, query)) },
                            supportingContent = {
                                Text(
                                    highlightedText(r.body.replace('\n', ' '), query),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            trailingContent = { Text(Fmt.listStamp(r.date), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.clickable { openChat(r.address, r.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// =============================================================== starred

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredScreen(nav: NavHostController) {
    val rows by App.instance.repo.starredMessages.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.starred_messages)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { pad ->
        if (rows.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Star, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.no_starred),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(rows, key = { it.id }) { r ->
                    val name = rememberName(r.address)
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(name) },
                        supportingContent = {
                            Text(r.body.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Text(Fmt.listStamp(r.date), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.clickable { nav.navigate(chatRoute(r.address, msgId = r.id)) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// =============================================================== trash

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(nav: NavHostController) {
    val app = App.instance
    val repo = app.repo
    val scope = rememberCoroutineScope()
    val threads by repo.trashedThreads.collectAsState()
    val msgs by repo.trashedMessages.collectAsState()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmThread by remember { mutableStateOf<String?>(null) }
    var confirmMsg by remember { mutableStateOf<Long?>(null) }
    val days = app.prefs.trashRetentionDays
    val cannotUndo = stringResource(R.string.cannot_undo)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.title_trash)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                if (threads.isNotEmpty() || msgs.isNotEmpty()) {
                    TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.empty_trash)) }
                }
            }
        )
    }) { pad ->
        if (threads.isEmpty() && msgs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.trash_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                item {
                    Text(
                        if (days > 0) stringResource(R.string.trash_auto_days, days) else stringResource(R.string.trash_no_auto),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(threads, key = { "t" + it.threadKey }) { t ->
                    val name = rememberName(t.address)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.trash_conversation, name)) },
                        supportingContent = {
                            Text(
                                if (t.locked) stringResource(R.string.locked_conversation) else t.lastBody.replace('\n', ' '),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { scope.launch { repo.restoreThreads(listOf(t.threadKey)) } }) {
                                    Icon(Icons.Filled.RestoreFromTrash, stringResource(R.string.restore))
                                }
                                IconButton(onClick = { confirmThread = t.threadKey }) {
                                    Icon(Icons.Filled.DeleteForever, stringResource(R.string.delete_forever))
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
                items(msgs, key = { "m" + it.id }) { m ->
                    val name = rememberName(m.address)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.trash_message, name)) },
                        supportingContent = {
                            Text(
                                if (m.locked) stringResource(R.string.trash_locked_msg) else m.body.replace('\n', ' '),
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { scope.launch { repo.restoreMessage(m.id) } }) {
                                    Icon(Icons.Filled.RestoreFromTrash, stringResource(R.string.restore))
                                }
                                IconButton(onClick = { confirmMsg = m.id }) {
                                    Icon(Icons.Filled.DeleteForever, stringResource(R.string.delete_forever))
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmEmpty) ConfirmDialog(
        stringResource(R.string.confirm_empty_trash), cannotUndo,
        onConfirm = { confirmEmpty = false; scope.launch { repo.emptyTrash() } },
        onDismiss = { confirmEmpty = false }
    )
    confirmThread?.let { k ->
        ConfirmDialog(stringResource(R.string.confirm_delete_thread), cannotUndo,
            onConfirm = { confirmThread = null; scope.launch { repo.deleteThreadsForever(listOf(k)) } },
            onDismiss = { confirmThread = null })
    }
    confirmMsg?.let { id ->
        ConfirmDialog(stringResource(R.string.confirm_delete_message), cannotUndo,
            onConfirm = { confirmMsg = null; scope.launch { repo.deleteMessageForever(id) } },
            onDismiss = { confirmMsg = null })
    }
}

// =============================================================== quick replies

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRepliesScreen(nav: NavHostController) {
    val repo = App.instance.repo
    val scope = rememberCoroutineScope()
    val list by repo.quickReplies.collectAsState()
    var editing by remember { mutableStateOf<QuickReply?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_quick)) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text(
                stringResource(R.string.quick_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                itemsIndexed(list, key = { _, q -> q.id }) { i, q ->
                    ListItem(
                        headlineContent = { Text(q.text) },
                        trailingContent = {
                            Row {
                                IconButton(enabled = i > 0, onClick = { scope.launch { repo.moveQuick(list, i, -1) } }) {
                                    Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                                }
                                IconButton(enabled = i < list.lastIndex, onClick = { scope.launch { repo.moveQuick(list, i, 1) } }) {
                                    Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                                }
                                IconButton(onClick = { editing = q }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                                IconButton(onClick = { scope.launch { repo.deleteQuick(q) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (adding) QuickEditDialog(initial = "", onSave = { t -> adding = false; scope.launch { repo.addQuick(t) } }, onDismiss = { adding = false })
    editing?.let { q ->
        QuickEditDialog(initial = q.text, onSave = { t -> editing = null; scope.launch { repo.updateQuick(q, t) } }, onDismiss = { editing = null })
    }
}

@Composable
private fun QuickEditDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial.isEmpty()) R.string.new_quick else R.string.edit_quick)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                label = { Text(stringResource(R.string.quick_text)) },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// =============================================================== settings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController, activity: FragmentActivity) {
    val app = App.instance
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = rememberAuthenticator(activity)

    var delivery by remember { mutableStateOf(prefs.deliveryReports) }
    var hideAll by remember { mutableStateOf(prefs.hideAllContent) }
    var bio by remember { mutableStateOf(prefs.biometricEnabled) }
    var days by remember { mutableStateOf(prefs.trashRetentionDays) }
    var hasPin by remember { mutableStateOf(LockManager.hasPin(prefs)) }
    var showSetPin by remember { mutableStateOf(false) }
    var confirmClearPin by remember { mutableStateOf(false) }
    var corner by remember { mutableStateOf(prefs.messageCornerDp) }
    var autoCopy by remember { mutableStateOf(prefs.autoCopyOtp) }
    var lockApp by remember { mutableStateOf(prefs.appLockEnabled) }
    var pendingEnableLock by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val pInfo = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    }
    val versionName = pInfo?.versionName ?: "1.0"
    val versionCode = pInfo?.let {
        if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
    } ?: 1L
    var sims by remember { mutableStateOf(Sims.list(ctx)) }
    OnResume { sims = Sims.list(ctx) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.title_settings)) },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } }
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())) {

            // ---------------- language
            SectionTitle(stringResource(R.string.sec_language))
            ChipRow {
                listOf("system" to R.string.lang_system, "fa" to R.string.lang_fa, "en" to R.string.lang_en).forEach { (v, label) ->
                    FilterChip(
                        selected = prefs.language == v,
                        onClick = {
                            if (prefs.language != v) {
                                prefs.updateLanguage(v)
                                // Channel name follows the language; then rebuild the UI with the new resources
                                app.appScope.launch { Notifier.createChannels(app.localized()) }
                                activity.recreate()
                            }
                        },
                        label = { Text(stringResource(label)) }
                    )
                }
            }

            // ---------------- appearance
            SectionTitle(stringResource(R.string.sec_appearance))
            Label(stringResource(R.string.theme))
            ChipRow {
                listOf(0 to R.string.theme_system, 1 to R.string.theme_light, 2 to R.string.theme_dark).forEach { (v, label) ->
                    FilterChip(selected = prefs.themeMode == v, onClick = { prefs.updateThemeMode(v) }, label = { Text(stringResource(label)) })
                }
            }
            Label(stringResource(R.string.primary_color))
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryChoices.forEach { c ->
                    val color = Color(c)
                    val isSel = prefs.primaryColor == c.toInt()
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else BorderStroke(0.dp, Color.Transparent),
                                CircleShape
                            )
                            .clickable { prefs.updatePrimaryColor(c.toInt()) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSel) Icon(Icons.Filled.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
                    }
                }
            }
            Label(stringResource(R.string.calendar))
            ChipRow {
                listOf("auto" to R.string.cal_auto, "jalali" to R.string.cal_jalali, "gregorian" to R.string.cal_gregorian).forEach { (v, label) ->
                    FilterChip(selected = prefs.calendar == v, onClick = { prefs.updateCalendar(v) }, label = { Text(stringResource(label)) })
                }
            }
            Label(stringResource(R.string.chat_font_size) + " (" + prefs.chatFontSp.toInt() + ")")
            Slider(
                value = prefs.chatFontSp,
                onValueChange = { prefs.updateChatFontSp(Math.round(it).toFloat()) },
                valueRange = 12f..26f,
                steps = 13,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Label(stringResource(R.string.message_corners) + " (" + corner + ")")
            Slider(
                value = corner.toFloat(),
                onValueChange = { v -> corner = Math.round(v); prefs.updateMessageCorner(corner) },
                valueRange = 0f..26f,
                steps = 25,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            // The preview sits below both sliders and reflects the text size and the corner radius.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    stringResource(R.string.font_preview),
                    fontSize = prefs.chatFontSp.sp,
                    lineHeight = (prefs.chatFontSp * 1.45f).sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(corner.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // ---------------- sending
            SectionTitle(stringResource(R.string.sec_sending))
            SwitchRow(stringResource(R.string.delivery_reports), stringResource(R.string.delivery_reports_desc), delivery) {
                delivery = it; prefs.deliveryReports = it
            }
            Label(stringResource(R.string.quick_reply_mode))
            ChipRow {
                FilterChip(
                    selected = !prefs.quickSendDirect,
                    onClick = { prefs.updateQuickSendDirect(false) },
                    label = { Text(stringResource(R.string.quick_mode_insert)) }
                )
                FilterChip(
                    selected = prefs.quickSendDirect,
                    onClick = { prefs.updateQuickSendDirect(true) },
                    label = { Text(stringResource(R.string.quick_mode_send)) }
                )
            }
            if (sims.size >= 2) {
                Label(stringResource(R.string.sim_default))
                ChipRow {
                    FilterChip(
                        selected = prefs.preferredSub < 0 || sims.none { it.subId == prefs.preferredSub },
                        onClick = { prefs.updatePreferredSub(-1) },
                        label = { Text(stringResource(R.string.sim_system)) }
                    )
                    sims.forEach { s ->
                        FilterChip(
                            selected = prefs.preferredSub == s.subId,
                            onClick = { prefs.updatePreferredSub(s.subId) },
                            label = { Text(stringResource(R.string.sim_n, s.slotNumber) + if (s.name.isNotBlank()) " · ${s.name}" else "") }
                        )
                    }
                }
            }

            // ---------------- notifications
            SectionTitle(stringResource(R.string.sec_notifications))
            SwitchRow(stringResource(R.string.hide_all_content), stringResource(R.string.hide_all_content_desc), hideAll) {
                hideAll = it; prefs.hideAllContent = it
            }
            SwitchRow(stringResource(R.string.auto_copy_otp), stringResource(R.string.auto_copy_otp_desc), autoCopy) {
                autoCopy = it; prefs.autoCopyOtp = it
            }

            // ---------------- lock
            SectionTitle(stringResource(R.string.sec_lock))
            SwitchRow(stringResource(R.string.app_lock), stringResource(R.string.app_lock_desc), lockApp) { v ->
                if (!v) {
                    prefs.updateAppLock(false)
                    lockApp = false
                } else if (!LockManager.hasPin(prefs)) {
                    pendingEnableLock = true
                    showSetPin = true
                } else {
                    prefs.updateAppLock(true)
                    lockApp = true
                    // Enabling the lock from inside settings must not lock the current screen.
                    LockManager.markAppUnlocked()
                }
            }
            SwitchRow(stringResource(R.string.biometric_button), stringResource(R.string.biometric_desc), bio) {
                bio = it; prefs.biometricEnabled = it
            }
            ListItem(
                headlineContent = { Text(stringResource(if (hasPin) R.string.change_pin else R.string.set_pin)) },
                modifier = Modifier.clickable {
                    if (hasPin) auth { showSetPin = true } else showSetPin = true
                }
            )
            if (hasPin) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.remove_pin)) },
                    modifier = Modifier.clickable { auth { confirmClearPin = true } }
                )
            }

            // ---------------- trash
            SectionTitle(stringResource(R.string.sec_trash))
            Label(stringResource(R.string.trash_retention))
            ChipRow {
                listOf(7 to R.string.days_7, 30 to R.string.days_30, 0 to R.string.forever).forEach { (d, label) ->
                    FilterChip(selected = days == d, onClick = { days = d; prefs.trashRetentionDays = d }, label = { Text(stringResource(label)) })
                }
            }
            // ---------------- about
            SectionTitle(stringResource(R.string.sec_about))
            ListItem(
                leadingContent = { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.about_app)) },
                supportingContent = { Text("TheRealAliz · v$versionName ($versionCode)") },
                modifier = Modifier.clickable { showAbout = true }
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
        }
    }

    if (showSetPin) SetPinDialog(
        onDone = { pin ->
            LockManager.setPin(prefs, pin)
            hasPin = true
            showSetPin = false
            if (pendingEnableLock) {
                pendingEnableLock = false
                prefs.updateAppLock(true)
                lockApp = true
                LockManager.markAppUnlocked()
            }
        },
        onDismiss = { showSetPin = false; pendingEnableLock = false }
    )
    if (showAbout) {
        val opener = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.app_name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Filled.Info, null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.about_created_by))
                    Text("TheRealAliz", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        Text(
                            "github.com/TheRealAliz/sms-messenger",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                runCatching { opener.openUri("https://github.com/TheRealAliz/sms-messenger") }
                            }
                        )
                    }
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.version_fmt, versionName, versionCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.confirm)) } }
        )
    }
    if (confirmClearPin) AlertDialog(
        onDismissRequest = { confirmClearPin = false },
        title = { Text(stringResource(R.string.remove_pin_title)) },
        text = { Text(stringResource(R.string.remove_pin_desc)) },
        confirmButton = {
            TextButton(onClick = {
                confirmClearPin = false
                scope.launch { app.repo.unlockAllThreads() }
                LockManager.clearPin(prefs)
                prefs.updateAppLock(false)
                lockApp = false
                hasPin = false
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = { confirmClearPin = false }) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun Label(t: String) {
    Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) }
    )
}

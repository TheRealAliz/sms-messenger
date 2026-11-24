package com.tamadon.smsmessenger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.AppState
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.core.Images
import com.tamadon.smsmessenger.core.LinkPreview
import com.tamadon.smsmessenger.core.LinkPreviews
import com.tamadon.smsmessenger.core.LockManager
import com.tamadon.smsmessenger.core.Notifier
import com.tamadon.smsmessenger.core.Phone
import com.tamadon.smsmessenger.core.Sim
import com.tamadon.smsmessenger.core.Sims
import com.tamadon.smsmessenger.core.SmsSender
import com.tamadon.smsmessenger.core.TextTools
import com.tamadon.smsmessenger.data.MessageEntity
import com.tamadon.smsmessenger.data.QuickReply
import com.tamadon.smsmessenger.data.Status
import com.tamadon.smsmessenger.data.ThreadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: NavHostController,
    activity: FragmentActivity,
    addr: String,
    prefill: String?,
    highlightId: Long = -1L
) {
    val app = App.instance
    val repo = app.repo
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember(addr) { Phone.threadKey(addr) }

    // (loaded, thread): "loaded" stays false until the first database emission, so a locked
    // conversation is never flashed on screen before we know it is locked.
    val threadState = produceState(initialValue = false to (null as ThreadEntity?), key) {
        repo.observeThread(key).collect { value = true to it }
    }
    val loaded = threadState.value.first
    val thread: ThreadEntity? = threadState.value.second

    val locked = thread?.locked == true
    var unlockedNow by remember(key) { mutableStateOf(LockManager.isUnlocked(key)) }
    val canShow = loaded && (!locked || unlockedNow || LockManager.isUnlocked(key))

    if (!loaded) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (!canShow) {
        LockGate(
            activity = activity,
            onUnlocked = { LockManager.markUnlocked(key); unlockedNow = true },
            onBack = { nav.popBackStack() }
        )
        return
    }

    val address = thread?.address ?: addr
    val name = rememberName(address)
    val isContact = rememberIsContact(address)
    val photoUri = rememberPhoto(address)
    val blocked = thread?.blocked == true
    val spam = thread?.spam == true
    val replyable = Phone.canReply(address)
    val canType = replyable && !blocked

    val messages by remember(key) { repo.messages(key) }.collectAsState(initial = emptyList())
    val quick by repo.quickReplies.collectAsState()
    val snack = remember { SnackbarHostState() }
    var input by remember { mutableStateOf(TextFieldValue(prefill.orEmpty(), TextRange(prefill.orEmpty().length))) }
    var menuOpen by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var detailsOf by remember { mutableStateOf<MessageEntity?>(null) }
    var selectOf by remember { mutableStateOf<MessageEntity?>(null) }
    var deleteOf by remember { mutableStateOf<MessageEntity?>(null) }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val appDirection = LocalLayoutDirection.current

    // SIM cards (only meaningful with two or more)
    var sims by remember { mutableStateOf(Sims.list(ctx)) }
    OnResume { sims = Sims.list(ctx) }
    val selectedSub = remember(sims, prefs.preferredSub) { Sims.resolve(ctx, prefs.preferredSub) }

    val copiedText = stringResource(R.string.copied)
    val trashedText = stringResource(R.string.message_moved_to_trash)
    val undoText = stringResource(R.string.undo)
    val setPinFirst = stringResource(R.string.set_pin_first)

    // True only while the screen is actually visible / focused. Used to avoid marking messages
    // read (and hiding their notification) while the app sits in the background with this chat open.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            else if (event == Lifecycle.Event.ON_PAUSE) resumed = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // While this chat is open (and visible): no notification for it, and incoming messages count as read.
    DisposableEffect(key) {
        AppState.viewingKey = key
        Notifier.cancel(ctx, key)
        onDispose { if (AppState.viewingKey == key) AppState.viewingKey = null }
    }
    LaunchedEffect(messages.size, key, resumed) {
        if (!resumed) return@LaunchedEffect
        if (messages.any { it.incoming && !it.read }) repo.markRead(key)
        Notifier.cancel(ctx, key)
    }

    // Scroll to the newest message when one arrives; always when it is one we just sent,
    // and for incoming ones only if the user is already near the bottom.
    val newest = messages.firstOrNull()
    LaunchedEffect(newest?.id) {
        val n = newest ?: return@LaunchedEffect
        if (!n.incoming || listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    // A search result was tapped: jump to that message and keep a border around it.
    LaunchedEffect(highlightId, messages.size) {
        if (highlightId <= 0) return@LaunchedEffect
        val index = messages.indexOfFirst { it.id == highlightId }
        if (index >= 0) listState.scrollToItem(index)
    }

    // One-time code of the newest message, for the in-bubble copy button.
    val newestOtp = remember(newest?.id, newest?.body) { newest?.let { TextTools.otp(it.body) } }
    val clipboard = remember { ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var clipboardText by remember { mutableStateOf<String?>(null) }
    // The code whose clipboard state has actually been read. Until the check finishes (which is
    // asynchronous) this is null, so the copy chip is not flashed on screen first.
    var checkedOtp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newestOtp, resumed) {
        checkedOtp = null
        clipboardText = if (resumed && newestOtp != null) TextTools.clipboardText(ctx) else null
        if (resumed && newestOtp != null) checkedOtp = newestOtp
    }

    fun send(body: String) {
        val b = body.trim()
        if (b.isEmpty() || !canType) return
        val sub = selectedSub
        scope.launch(Dispatchers.IO) { SmsSender.send(ctx.applicationContext, address, b, sub) }
    }

    fun onQuickTap(q: QuickReply) {
        if (prefs.quickSendDirect) {
            send(q.text)
        } else {
            // First tap: put the phrase into the input (after what is already typed); user presses send.
            val cur = input.text
            val merged = if (cur.isBlank()) q.text else cur.trimEnd() + " " + q.text
            input = TextFieldValue(merged, TextRange(merged.length))
            runCatching { focus.requestFocus() }
        }
    }

    val blurRadius by animateDpAsState(if (profileOpen) 18.dp else 0.dp, tween(220), label = "blur")
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .clickable(enabled = isContact) { profileOpen = true }
                        ) {
                            Avatar(
                                name = name,
                                locked = false,
                                size = 40.dp,
                                contact = isContact,
                                photoUri = photoUri
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(
                            Modifier.clickable(enabled = isContact) { profileOpen = true }
                        ) {
                            Text(
                                name,
                                maxLines = 1,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                            )
                            if (name != address) {
                                Text(address, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (locked) Icon(Icons.Filled.Lock, null, Modifier.padding(horizontal = 4.dp))
                    if (Phone.canCall(address)) {
                        IconButton(onClick = {
                            // ACTION_DIAL opens the dialer with the number filled in: no call permission needed
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null))) }
                        }) { Icon(Icons.Filled.Call, stringResource(R.string.call)) }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.menu)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val t = thread
                        DropdownMenuItem(
                            text = { Text(stringResource(if (locked) R.string.unlock_conversation else R.string.lock_conversation)) },
                            leadingIcon = { Icon(if (locked) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                            onClick = {
                                menuOpen = false
                                // The user already authenticated to be here, so unlocking needs no second prompt.
                                if (locked) scope.launch { repo.setLocked(listOf(key), false) }
                                else if (!LockManager.hasPin(prefs)) {
                                    scope.launch { snack.showBrief(setPinFirst) }
                                } else {
                                    scope.launch { repo.setLocked(listOf(key), true) }
                                    LockManager.markUnlocked(key)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (t?.hideNotif == true) R.string.show_in_notif else R.string.hide_in_notif)) },
                            leadingIcon = { Icon(Icons.Filled.VisibilityOff, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.setHideNotif(listOf(key), t?.hideNotif != true) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (t?.muted == true) R.string.unmute else R.string.mute)) },
                            leadingIcon = { Icon(Icons.Filled.NotificationsOff, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.setMuted(listOf(key), t?.muted != true) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (t?.archived == true) R.string.unarchive else R.string.archive)) },
                            leadingIcon = {
                                Icon(if (t?.archived == true) Icons.Filled.Unarchive else Icons.Filled.Archive, null)
                            },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.setArchived(listOf(key), t?.archived != true) }
                                nav.popBackStack()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (spam) R.string.not_spam else R.string.mark_spam)) },
                            leadingIcon = { Icon(Icons.Filled.Report, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.setSpam(listOf(key), !spam) }
                                nav.popBackStack()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (blocked) R.string.unblock else R.string.block)) },
                            leadingIcon = { Icon(Icons.Filled.Block, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.setBlocked(listOf(key), !blocked) }
                                if (!blocked) nav.popBackStack()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_to_trash)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = {
                                menuOpen = false
                                scope.launch { repo.trashThreads(listOf(key)) }
                                nav.popBackStack()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding()) {
                when {
                    // Alphanumeric senders (banks, services) cannot receive messages: no input at all
                    !replyable -> Unit
                    blocked -> BlockedBar(onUnblock = { scope.launch { repo.setBlocked(listOf(key), false) } })
                    else -> {
                        QuickReplyBar(quick, onTap = { onQuickTap(it) }, onEdit = { nav.navigate("quick") })
                        MessageInput(
                            value = input,
                            onChange = { input = it },
                            onSend = { send(input.text); input = TextFieldValue("") },
                            focus = focus,
                            appDirection = appDirection,
                            sims = sims,
                            selectedSub = selectedSub,
                            onPickSim = { prefs.updatePreferredSub(it) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        val showSimLabel = sims.size >= 2
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // messages are newest-first; with reverseLayout the first item sits at the bottom
            items(messages.size, key = { messages[it].id }) { i ->
                val m = messages[i]
                val older = messages.getOrNull(i + 1)
                Column {
                    // The day header goes above the first message of each day
                    if (older == null || !Fmt.sameDay(older.date, m.date)) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    Fmt.dayHeader(ctx, m.date),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    MessageBlock(
                        m = m,
                        fontSp = prefs.chatFontSp,
                        cornerDp = prefs.messageCornerDp,
                        simLabel = if (showSimLabel) sims.firstOrNull { it.subId == m.subId }?.slotNumber else null,
                        canResend = replyable,
                        highlighted = m.id == highlightId,
                        otpCode = if (i == 0) newestOtp else null,
                        otpVisible = i == 0 && newestOtp != null && checkedOtp == newestOtp &&
                            clipboardText?.trim() != newestOtp,
                        onCopy = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("sms", m.body))
                            scope.launch { snack.showBrief(copiedText, millis = 1200L) }
                        },
                        onCopyOtp = { code ->
                            TextTools.copy(ctx, "otp", code)
                            clipboardText = code
                            scope.launch { snack.showBrief(copiedText, millis = 1200L) }
                        },
                        onDelete = { deleteOf = m },
                        onResend = {
                            scope.launch(Dispatchers.IO) { SmsSender.resend(ctx.applicationContext, m) }
                        },
                        onStar = {
                            scope.launch {
                                repo.setStarred(m.id, !m.starred)
                                snack.showBrief(
                                    ctx.getString(if (m.starred) R.string.unstarred else R.string.starred),
                                    millis = 1200L
                                )
                            }
                        },
                        onDetails = { detailsOf = m },
                        onSelectText = { selectOf = m }
                    )
                }
            }
        }
    }

    if (profileOpen) {
        ContactProfileOverlay(
            name = name,
            photoUri = photoUri,
            onClose = { profileOpen = false }
        )
    }
    }

    detailsOf?.let { m -> MessageDetailsDialog(m, key, onDismiss = { detailsOf = null }) }

    deleteOf?.let { m ->
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_message_short),
            message = stringResource(R.string.delete_message_desc),
            onConfirm = {
                deleteOf = null
                scope.launch {
                    repo.trashMessage(m.id)
                    val r = snack.showBrief(trashedText, undoText)
                    if (r == SnackbarResult.ActionPerformed) repo.restoreMessage(m.id)
                }
            },
            onDismiss = { deleteOf = null }
        )
    }

    selectOf?.let { m ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selectOf = null }, sheetState = sheet) {
            SelectTextSheet(m.body, selectOf = { selectOf = null })
        }
    }
}

/**
 * Full-screen contact card that grows out of the top-bar avatar: the small circle travels to the
 * centre of the page while its corners open into a rounded square, then the name and actions fade
 * in. Only reachable for saved contacts.
 */
@Composable
private fun ContactProfileOverlay(
    name: String,
    photoUri: String?,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    BackHandler { onClose() }

    val scrim by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "scrim")
    val photo by produceState<Bitmap?>(initialValue = null, photoUri) {
        // Full-size decode: the overlay is almost screen-wide, a thumbnail would look blurry.
        value = photoUri?.let { Images.local(ctx, it, maxPx = 1600) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Passive back cover: no ripple or button feedback, just a soft dark layer that
            // closes the preview when tapped.
            .background(Color.Black.copy(alpha = 0.38f * scrim))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        val target = (maxWidth - 20.dp).coerceAtLeast(120.dp)
        // Starts roughly where the top-bar avatar sits, then settles in the centre.
        val startX = -(maxWidth / 2 - 60.dp)
        val startY = -(maxHeight / 2 - 110.dp)
        val dx by animateDpAsState(if (shown) 0.dp else startX, tween(300), label = "dx")
        val dy by animateDpAsState(if (shown) 0.dp else startY, tween(300), label = "dy")
        val side by animateDpAsState(if (shown) target else 44.dp, tween(300), label = "size")
        val corner by animateDpAsState(if (shown) 26.dp else 100.dp, tween(300), label = "corner")
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = dx, y = dy)
                .size(side)
                .clip(RoundedCornerShape(corner))
                .background(cs.primaryContainer)
                // Swallow taps on the picture: only the cover closes the preview.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = photo
            when {
                bitmap != null -> Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                else -> Text(
                    name.firstOrNull { it.isLetter() }?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displayLarge,
                    color = cs.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun SelectTextSheet(body: String, selectOf: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(stringResource(R.string.select_text), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh) {
            SelectionContainer {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.padding(14.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                TextTools.copy(ctx, "sms", body)
                selectOf()
            }) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.copy))
            }
            TextButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                runCatching { ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.share))) }
                selectOf()
            }) {
                Icon(Icons.Filled.Share, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.share))
            }
        }
    }
}

@Composable
private fun MessageDetailsDialog(m: MessageEntity, threadKey: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val name = rememberName(m.address)
    val status = when (m.status) {
        Status.SENDING -> stringResource(R.string.sending)
        Status.SENT -> stringResource(R.string.details_sent)
        Status.DELIVERED -> stringResource(R.string.details_delivered)
        Status.FAILED -> stringResource(R.string.failed_tap)
        Status.DELIVERY_FAILED -> stringResource(R.string.delivery_failed)
        else -> stringResource(R.string.details_received)
    }
    val sim = Sims.list(ctx).firstOrNull { it.subId == m.subId }?.let { stringResource(R.string.sim_n, it.slotNumber) }
        ?: stringResource(R.string.details_unknown)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine(stringResource(if (m.incoming) R.string.from else R.string.to), name)
                if (name != m.address) DetailLine(stringResource(R.string.number), m.address)
                DetailLine(stringResource(R.string.date), Fmt.full(m.date))
                DetailLine(stringResource(R.string.sim), sim)
                DetailLine(stringResource(R.string.status), status)
                DetailLine(stringResource(R.string.parts), m.totalParts.toString())
                DetailLine(stringResource(R.string.conversation), threadKey)
                if (m.starred) DetailLine(stringResource(R.string.starred), "★")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm)) } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label + ":",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 96.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The preview attached under a bubble. Its width is the measured bubble width, its top corners
 * are square so it reads as one surface with the bubble, and the picture is downloaded once and
 * then served from the on-disk cache.
 */
@Composable
private fun LinkPreviewCard(
    p: LinkPreview,
    url: String,
    width: Dp,
    cornerDp: Int,
    appDirection: LayoutDirection
) {
    val ctx = LocalContext.current
    val picture by produceState<Bitmap?>(initialValue = null, p.imageUrl) {
        value = p.imageUrl?.let { Images.remote(ctx, it) }
    }
    val cs = MaterialTheme.colorScheme
    val opener = LocalUriHandler.current
    val host = remember(url) { runCatching { Uri.parse(url).host ?: url }.getOrDefault(url) }
    val shape = RoundedCornerShape(
        topStart = 0.dp, topEnd = 0.dp,
        bottomStart = cornerDp.dp, bottomEnd = cornerDp.dp
    )
    Surface(
        shape = shape,
        color = cs.surfaceContainerHigh,
        modifier = Modifier.width(width).clickable {
            runCatching { opener.openUri(url) }
        }
    ) {
        Column {
            // A hairline where the bubble ends and the preview begins, so the shared edge stays
            // visible even when the bubble and the card have the same background tone.
            HorizontalDivider(
                thickness = 0.6.dp,
                color = cs.outlineVariant.copy(alpha = 0.7f)
            )
            val bitmap = picture
            if (bitmap != null && bitmap.height > 0) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val imageHeight = (width / ratio.coerceIn(1.15f, 2.6f)).coerceAtMost(190.dp)
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(imageHeight),
                    contentScale = ContentScale.Crop
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(host, style = MaterialTheme.typography.labelSmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    p.title?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    p.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedBar(onUnblock: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.blocked_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUnblock) { Text(stringResource(R.string.unblock)) }
        }
    }
}

/** Short user-defined phrases shown right above the text input. */
@Composable
private fun QuickReplyBar(list: List<QuickReply>, onTap: (QuickReply) -> Unit, onEdit: () -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(list, key = { it.id }) { q ->
            QuickChip(text = q.text, onClick = { onTap(q) })
        }
        item {
            QuickChip(
                text = stringResource(if (list.isEmpty()) R.string.add_quick else R.string.edit),
                leading = { Icon(Icons.Filled.Edit, null, Modifier.size(16.dp)) },
                onClick = onEdit
            )
        }
    }
}

/** Compact chip with tighter horizontal padding than the Material default. */
@Composable
private fun QuickChip(text: String, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cs.surfaceContainerHigh,
        border = BorderStroke(1.dp, cs.outlineVariant),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
        }
    }
}

/**
 * The row is laid out left-to-right on purpose so the send button is always on the right edge,
 * whatever the app language; the text field itself keeps the language's direction.
 */
@Composable
private fun MessageInput(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    focus: FocusRequester,
    appDirection: LayoutDirection,
    sims: List<Sim>,
    selectedSub: Int,
    onPickSim: (Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
                if (sims.size >= 2) SimPicker(sims, selectedSub, onPickSim)
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = cs.surfaceContainerHigh,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                stringResource(R.string.message_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            maxLines = 5,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = cs.onSurface,
                                textDirection = TextDirection.Content
                            ),
                            cursorBrush = SolidColor(cs.primary)
                        )
                    }
                }
            }
            IconButton(onClick = onSend, enabled = value.text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
        }
    }
}

@Composable
private fun SimPicker(sims: List<Sim>, selected: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = sims.firstOrNull { it.subId == selected } ?: sims.first()
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.SimCard, null, Modifier.size(18.dp))
            Text(" ${current.slotNumber}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sims.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.sim_n, s.slotNumber) + if (s.name.isNotBlank()) " · ${s.name}" else "")
                    },
                    leadingIcon = { Icon(Icons.Filled.SimCard, null) },
                    onClick = { open = false; onPick(s.subId) }
                )
            }
        }
    }
}

/**
 * A bubble plus, when its message contains a link, the preview attached underneath it. The
 * preview is loaded when this item becomes visible (LazyColumn composes only visible items) and
 * is then cached permanently by [LinkPreviews] / [Images].
 */
@Composable
private fun MessageBlock(
    m: MessageEntity,
    fontSp: Float,
    cornerDp: Int,
    simLabel: Int?,
    canResend: Boolean,
    highlighted: Boolean,
    otpCode: String?,
    otpVisible: Boolean,
    onCopy: () -> Unit,
    onCopyOtp: (String) -> Unit,
    onDelete: () -> Unit,
    onResend: () -> Unit,
    onStar: () -> Unit,
    onDetails: () -> Unit,
    onSelectText: () -> Unit
) {
    val ctx = LocalContext.current
    val appDirection = LocalLayoutDirection.current
    val link = remember(m.id, m.body) { TextTools.links(m.body).firstOrNull() }
    val preview by produceState<LinkPreview?>(initialValue = null, link) {
        value = link?.let { LinkPreviews.load(ctx, it) }
    }
    var bubbleWidthPx by remember { mutableIntStateOf(0) }

    // Sent messages always sit on the physical right edge and incoming ones on the left, in every
    // language. Only the text inside the bubble follows its own script direction.
    val alignRight = !m.incoming

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = if (alignRight) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Column(horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start) {
                MessageBubble(
                    m = m,
                    fontSp = fontSp,
                    cornerDp = cornerDp,
                    simLabel = simLabel,
                    canResend = canResend,
                    highlighted = highlighted,
                    otpCode = otpCode,
                    otpVisible = otpVisible,
                    alignRight = alignRight,
                    attachedBottom = preview != null,
                    appDirection = appDirection,
                    onWidthChanged = { w -> if (w != bubbleWidthPx) bubbleWidthPx = w },
                    onCopy = onCopy,
                    onCopyOtp = onCopyOtp,
                    onDelete = onDelete,
                    onResend = onResend,
                    onStar = onStar,
                    onDetails = onDetails,
                    onSelectText = onSelectText
                )
                val p = preview
                if (p != null && link != null && bubbleWidthPx > 0) {
                    val width = with(LocalDensity.current) { bubbleWidthPx.toDp() }
                    LinkPreviewCard(
                        p = p,
                        url = link,
                        width = width,
                        cornerDp = cornerDp,
                        appDirection = appDirection
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: MessageEntity,
    fontSp: Float,
    cornerDp: Int,
    simLabel: Int?,
    canResend: Boolean,
    highlighted: Boolean,
    otpCode: String?,
    otpVisible: Boolean,
    alignRight: Boolean,
    attachedBottom: Boolean,
    appDirection: LayoutDirection,
    onWidthChanged: (Int) -> Unit,
    onCopy: () -> Unit,
    onCopyOtp: (String) -> Unit,
    onDelete: () -> Unit,
    onResend: () -> Unit,
    onStar: () -> Unit,
    onDetails: () -> Unit,
    onSelectText: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val failed = m.status == Status.FAILED
    // When a preview is attached, the shared edge between bubble and card is square.
    val shape = if (attachedBottom) {
        RoundedCornerShape(
            topStart = cornerDp.dp, topEnd = cornerDp.dp,
            bottomStart = 0.dp, bottomEnd = 0.dp
        )
    } else {
        RoundedCornerShape(cornerDp.dp)
    }
    val body = linkText(m.body)
    Box {
        Surface(
            shape = shape,
            color = when {
                failed -> cs.errorContainer
                m.incoming -> cs.surfaceVariant
                else -> cs.primaryContainer
            },
            contentColor = when {
                failed -> cs.onErrorContainer
                m.incoming -> cs.onSurface
                else -> cs.onPrimaryContainer
            },
            modifier = Modifier
                .widthIn(max = 300.dp)
                .onSizeChanged { onWidthChanged(it.width) }
                .then(
                    if (highlighted) Modifier.border(2.dp, cs.primary, shape) else Modifier
                )
                .combinedClickable(onClick = { if (failed && canResend) menu = true }, onLongClick = { menu = true })
        ) {
            Column(
                Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .animateContentSize(),
                horizontalAlignment = if (alignRight) Alignment.End else Alignment.Start
            ) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Content,
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.45f).sp
                    )
                )
                AnimatedVisibility(
                    visible = otpCode != null && otpVisible,
                    enter = fadeIn(tween(260)),
                    exit = fadeOut(tween(150))
                ) {
                    if (otpCode != null) {
                        Box(
                            Modifier
                                .padding(top = 8.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cs.primary.copy(alpha = 0.10f))
                                .border(BorderStroke(1.dp, cs.primary.copy(alpha = 0.30f)), RoundedCornerShape(8.dp))
                                .clickable { onCopyOtp(otpCode) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ContentCopy, null, Modifier.size(14.dp), tint = cs.primary)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    stringResource(R.string.copy_code_fmt, otpCode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.primary
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Fmt.time(m.date), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    if (simLabel != null) {
                        Icon(Icons.Filled.SimCard, null, Modifier.size(12.dp), tint = cs.onSurfaceVariant)
                        Text(simLabel.toString(), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    if (m.starred) {
                        Icon(Icons.Filled.Star, null, Modifier.size(12.dp), tint = cs.primary)
                    }
                    if (!m.incoming) StatusMark(m)
                }
            }
        }
        CompositionLocalProvider(LocalLayoutDirection provides appDirection) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy)) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                    onClick = { menu = false; onCopy() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_text)) },
                    leadingIcon = { Icon(Icons.Filled.TextFields, null) },
                    onClick = { menu = false; onSelectText() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (m.starred) R.string.unstar else R.string.star)) },
                    leadingIcon = { Icon(if (m.starred) Icons.Filled.Star else Icons.Filled.StarBorder, null) },
                    onClick = { menu = false; onStar() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.details)) },
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                    onClick = { menu = false; onDetails() }
                )
                if (failed && canResend) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.resend)) },
                        onClick = { menu = false; onResend() }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

/** [text] with http(s) / www links and USSD/MMI codes made accent-coloured and clickable. */
@Composable
private fun linkText(text: String): androidx.compose.ui.text.AnnotatedString {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val style = SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline)
    val urlRegex = remember { Regex("""(?i)(?:https?://|www\.)[^\s<>"']+""") }
    val ussdRegex = remember { Regex("[*#][0-9*#]+[*#]") }
    if (!urlRegex.containsMatchIn(text) && !ussdRegex.containsMatchIn(text)) {
        return androidx.compose.ui.text.AnnotatedString(text)
    }
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val url = urlRegex.find(text, i)
            val ussd = ussdRegex.find(text, i)
            val next = listOfNotNull(url, ussd).minByOrNull { it.range.first } ?: break
            if (next.range.first > i) append(text.substring(i, next.range.first))
            val raw = next.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛')
            if (raw.startsWith("http", true) || raw.startsWith("www.", true)) {
                val target = TextTools.links(raw).firstOrNull() ?: raw
                withLink(LinkAnnotation.Url(target, TextLinkStyles(style = style))) { append(raw) }
            } else {
                // *780# and friends open the dialer, just like tapping a phone number.
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "ussd",
                        styles = TextLinkStyles(style = style),
                        linkInteractionListener = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(raw)))
                                )
                            }
                        }
                    )
                ) { append(raw) }
            }
            i = next.range.first + raw.length
        }
        if (i < text.length) append(text.substring(i))
    }
}

/** Delivery report indicator for outgoing messages. Text is only kept for "sending" and failures. */
@Composable
private fun StatusMark(m: MessageEntity) {
    val cs = MaterialTheme.colorScheme
    when (m.status) {
        Status.SENDING -> {
            Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            Text(stringResource(R.string.sending), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Status.SENT -> Icon(Icons.Filled.Done, null, Modifier.size(15.dp), tint = cs.onSurfaceVariant)
        Status.DELIVERED -> Icon(Icons.Filled.DoneAll, null, Modifier.size(15.dp), tint = cs.primary)
        Status.FAILED -> {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(14.dp), tint = cs.error)
            Text(stringResource(R.string.failed_tap), style = MaterialTheme.typography.labelSmall, color = cs.error)
        }
        Status.DELIVERY_FAILED -> {
            Icon(Icons.Filled.ErrorOutline, null, Modifier.size(14.dp), tint = cs.error)
            Text(stringResource(R.string.delivery_failed), style = MaterialTheme.typography.labelSmall, color = cs.error)
        }
        else -> Unit
    }
}

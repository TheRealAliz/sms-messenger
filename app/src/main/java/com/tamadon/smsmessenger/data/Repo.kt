package com.tamadon.smsmessenger.data

import android.content.Context
import androidx.room.withTransaction
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.core.Notifier
import com.tamadon.smsmessenger.core.Phone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class Repo(private val ctx: Context, val db: AppDb, scope: CoroutineScope) {
    val dao = db.dao()

    // The two streams needed on the first screen are collected eagerly from process start, so by the
    // time the UI composes the list is usually already computed. `null` means "still loading".
    val homeRows: StateFlow<List<ConversationRow>?> =
        dao.homeRows().stateIn(scope, SharingStarted.Eagerly, null)
    val quickReplies: StateFlow<List<QuickReply>> =
        dao.observeQuickReplies().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val lazy = SharingStarted.WhileSubscribed(5_000)
    val archivedRows: StateFlow<List<ConversationRow>?> = dao.archivedRows().stateIn(scope, lazy, null)
    val spamRows: StateFlow<List<ConversationRow>?> = dao.spamRows().stateIn(scope, lazy, null)
    val blockedRows: StateFlow<List<ConversationRow>?> = dao.blockedRows().stateIn(scope, lazy, null)
    val trashedThreads: StateFlow<List<ConversationRow>> = dao.trashedThreadRows().stateIn(scope, lazy, emptyList())
    val trashedMessages: StateFlow<List<TrashedMessageRow>> = dao.trashedMessageRows().stateIn(scope, lazy, emptyList())
    val starredMessages: StateFlow<List<SearchRow>> = dao.starredRows().stateIn(scope, lazy, emptyList())

    fun observeThread(key: String): Flow<ThreadEntity?> = dao.observeThread(key)
    fun messages(key: String): Flow<List<MessageEntity>> = dao.observeMessages(key)
    fun search(q: String): Flow<List<SearchRow>> = dao.search(q)

    // ---- incoming / outgoing ----
    suspend fun ensureThread(address: String): String {
        val a = address.trim()
        val key = Phone.threadKey(a)
        dao.insertThreadIfAbsent(ThreadEntity(threadKey = key, address = a))
        return key
    }

    suspend fun receive(address: String, body: String, date: Long, subId: Int): String {
        val key = ensureThread(address)
        dao.revive(key)
        val pid = withContext(Dispatchers.IO) {
            ProviderSync.insert(ctx, address, body, date, incoming = true, read = false)
        }
        dao.insertMessage(
            MessageEntity(
                threadKey = key, address = address.trim(), body = body, date = date,
                incoming = true, read = false, providerId = pid, subId = subId
            )
        )
        return key
    }

    suspend fun insertOutgoing(address: String, body: String, parts: Int, subId: Int): Long {
        val key = ensureThread(address)
        dao.revive(key)
        val now = System.currentTimeMillis()
        val pid = withContext(Dispatchers.IO) {
            ProviderSync.insert(
                ctx, address, body, now, incoming = false, read = true,
                pendingDeliveryReport = App.instance.prefs.deliveryReports
            )
        }
        return dao.insertMessage(
            MessageEntity(
                threadKey = key, address = address.trim(), body = body, date = now,
                incoming = false, read = true, status = Status.SENDING,
                totalParts = parts.coerceAtLeast(1), providerId = pid, subId = subId
            )
        )
    }

    suspend fun onSentResult(id: Long, ok: Boolean) {
        if (!ok) dao.markFailed(id) else {
            dao.incSent(id)
            dao.finishSent(id)
        }
        syncProviderState(id)
    }

    /**
     * [tpStatus] is the raw status from the delivery-report PDU (0 = delivered, 0x20..0x3F =
     * still pending, >= 0x40 = failed). Other SMS apps store that same value in the provider.
     */
    suspend fun onDeliveryResult(id: Long, tpStatus: Int) {
        val ok = tpStatus < 0x20
        if (!ok) dao.markDeliveryFailed(id) else {
            dao.incDelivered(id)
            dao.finishDelivered(id, System.currentTimeMillis())
        }
        syncProviderState(id, tpStatus)
    }

    /** Pushes the local sent / delivered / failed state to the shared provider for other SMS apps. */
    private suspend fun syncProviderState(id: Long, tpStatus: Int? = null) {
        val m = dao.message(id) ?: return
        if (m.incoming || m.providerId <= 0) return
        val status = when (m.status) {
            Status.DELIVERED -> ProviderSync.PROVIDER_STATUS_COMPLETE
            Status.FAILED, Status.DELIVERY_FAILED ->
                tpStatus?.takeIf { it >= 0x40 } ?: ProviderSync.PROVIDER_STATUS_FAILED
            Status.SENT ->
                if (App.instance.prefs.deliveryReports) ProviderSync.PROVIDER_STATUS_PENDING
                else ProviderSync.PROVIDER_STATUS_NONE
            else -> ProviderSync.PROVIDER_STATUS_PENDING
        }
        withContext(Dispatchers.IO) { ProviderSync.setStatus(ctx, m.providerId, status, m.date) }
    }

    /** Marks a conversation read locally and in the shared provider. */
    suspend fun markRead(key: String) {
        val providerIds = dao.unreadIncomingProviderIds(key)
        dao.markRead(key)
        if (providerIds.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                if (ProviderSync.isDefault(ctx)) ProviderSync.markRead(ctx, providerIds)
                else App.instance.prefs.pendingReadSync = true
            }
        }
    }

    /** Skips rows already imported, so an interrupted import can safely be re-run. */
    suspend fun bulkImport(list: List<MessageEntity>) {
        db.withTransaction {
            val have = dao.existingProviderIds(list.map { it.providerId }).toSet()
            val fresh = list.filter { it.providerId !in have }
            fresh.distinctBy { it.threadKey }.forEach {
                dao.insertThreadIfAbsent(ThreadEntity(threadKey = it.threadKey, address = it.address))
            }
            dao.insertMessages(fresh)
        }
    }

    // ---- thread flags ----
    suspend fun setArchived(keys: List<String>, v: Boolean) = dao.setArchived(keys, v)
    suspend fun setPinned(keys: List<String>, v: Boolean) = dao.setPinned(keys, v)
    suspend fun setLocked(keys: List<String>, v: Boolean) = dao.setLocked(keys, v)
    suspend fun setHideNotif(keys: List<String>, v: Boolean) = dao.setHideNotif(keys, v)
    suspend fun setMuted(keys: List<String>, v: Boolean) = dao.setMuted(keys, v)
    suspend fun unlockAllThreads() = dao.unlockAllThreads()

    /** Blocked and spam are mutually exclusive; both silence notifications. */
    suspend fun setBlocked(keys: List<String>, v: Boolean) {
        db.withTransaction {
            dao.setBlocked(keys, v)
            if (v) dao.setSpam(keys, false)
        }
        if (v) keys.forEach { Notifier.cancel(ctx, it) }
    }

    suspend fun setSpam(keys: List<String>, v: Boolean) {
        db.withTransaction {
            dao.setSpam(keys, v)
            if (v) dao.setBlocked(keys, false)
        }
        if (v) keys.forEach { Notifier.cancel(ctx, it) }
    }

    // ---- trash ----
    suspend fun trashThreads(keys: List<String>) {
        dao.setTrashed(keys, System.currentTimeMillis())
        keys.forEach { Notifier.cancel(ctx, it) }
    }

    suspend fun restoreThreads(keys: List<String>) = dao.setTrashed(keys, null)

    suspend fun trashMessage(id: Long) = dao.setMessageTrashed(id, System.currentTimeMillis())
    suspend fun restoreMessage(id: Long) = dao.setMessageTrashed(id, null)

    suspend fun setStarred(id: Long, v: Boolean) = dao.setStarred(id, v)

    suspend fun deleteThreadsForever(keys: List<String>) {
        if (keys.isEmpty()) return
        val pids = dao.providerIds(keys)
        withContext(Dispatchers.IO) { ProviderSync.delete(ctx, pids) }
        db.withTransaction {
            dao.deleteMessagesOfThreads(keys)
            dao.deleteThreads(keys)
        }
    }

    suspend fun deleteMessageForever(id: Long) {
        val m = dao.message(id) ?: return
        withContext(Dispatchers.IO) { ProviderSync.delete(ctx, listOf(m.providerId)) }
        dao.deleteMessage(id)
    }

    private suspend fun deleteMessagesForever(list: List<MessageEntity>) {
        if (list.isEmpty()) return
        withContext(Dispatchers.IO) { ProviderSync.delete(ctx, list.map { it.providerId }) }
        dao.deleteMessages(list.map { it.id })
    }

    suspend fun emptyTrash() {
        deleteThreadsForever(dao.trashedThreadKeys())
        deleteMessagesForever(dao.allTrashedMessages())
    }

    suspend fun purgeOld() {
        val days = App.instance.prefs.trashRetentionDays
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        deleteThreadsForever(dao.trashedThreadKeysBefore(cutoff))
        deleteMessagesForever(dao.trashedMessagesBefore(cutoff))
    }

    // ---- quick replies ----
    suspend fun seedQuickRepliesIfNeeded(defaults: List<String>) {
        val prefs = App.instance.prefs
        // An empty table can also happen when the database was recreated while the preference
        // survived; in that case the defaults must be inserted again.
        if (!prefs.seededQuickReplies || dao.quickCount() == 0) {
            if (dao.quickCount() == 0) {
                defaults.forEachIndexed { i, s -> dao.insertQuick(QuickReply(text = s, position = i + 1)) }
            }
            prefs.seededQuickReplies = true
            prefs.quickDefaultsPersian = true
            return
        }
        // Older builds seeded the English defaults. Replace them only when the first four rows are
        // still exactly the untouched defaults, so user-edited phrases are never overwritten.
        if (prefs.quickDefaultsPersian) {
            // The fourth Persian default was reworded ("در راه هستم" -> "تو راهم").
            dao.allQuickReplies().forEach { q ->
                if (q.text == "در راه هستم") dao.updateQuick(q.copy(text = "تو راهم"))
            }
            return
        }
        val oldEnglish = listOf("OK", "Thanks", "Can't talk now, I'll call you later", "On my way")
        val rows = dao.allQuickReplies()
        if (rows.take(4).map { it.text } == oldEnglish && defaults.size >= 4) {
            rows.take(4).forEachIndexed { i, q -> dao.updateQuick(q.copy(text = defaults[i])) }
        }
        prefs.quickDefaultsPersian = true
    }

    suspend fun addQuick(text: String) {
        dao.insertQuick(QuickReply(text = text.trim(), position = dao.quickMaxPosition() + 1))
    }

    suspend fun updateQuick(q: QuickReply, text: String) = dao.updateQuick(q.copy(text = text.trim()))
    suspend fun deleteQuick(q: QuickReply) = dao.deleteQuick(q)

    suspend fun moveQuick(list: List<QuickReply>, index: Int, delta: Int) {
        val other = index + delta
        if (index !in list.indices || other !in list.indices) return
        val a = list[index]
        val b = list[other]
        db.withTransaction {
            dao.setQuickPosition(a.id, b.position)
            dao.setQuickPosition(b.id, a.position)
        }
    }
}

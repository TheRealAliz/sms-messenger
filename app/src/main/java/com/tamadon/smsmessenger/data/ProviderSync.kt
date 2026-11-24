package com.tamadon.smsmessenger.data

import android.Manifest
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.core.Phone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mirrors messages and their state to the system SMS provider (only writable while we are the
 * default SMS app), so that nothing is lost and the other installed SMS apps (Samsung Messages,
 * Google Messages, ...) see the same read flags and delivery reports.
 */
object ProviderSync {

    // Values of Telephony.Sms.STATUS_*. These are the values the other SMS apps read from the
    // shared provider to decide whether a message is pending, delivered or failed. Failure
    // statuses coming from a delivery report (TP status) are >= 64 as well.
    const val PROVIDER_STATUS_NONE = -1
    const val PROVIDER_STATUS_COMPLETE = 0
    const val PROVIDER_STATUS_PENDING = 32
    const val PROVIDER_STATUS_FAILED = 64

    private const val TAG = "ProviderSync"

    /** Guards the foreground sync so two callers cannot import the same provider rows at once. */
    private val syncing = AtomicBoolean(false)

    /** True when this app is the default SMS app (role holder on Android 10+). */
    fun isDefault(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_SMS) && rm.isRoleHeld(RoleManager.ROLE_SMS)) return true
        }
        return Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName
    }

    private fun canRead(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Adds a message to the provider.
     *
     * Outgoing rows are written with [Telephony.Sms.DATE_SENT] and a status of pending when a
     * delivery report was requested, or "none" otherwise. Later [setStatus] calls turn that into
     * complete / failed, which is what Samsung Messages and Google Messages display.
     */
    fun insert(
        ctx: Context,
        address: String,
        body: String,
        date: Long,
        incoming: Boolean,
        read: Boolean,
        pendingDeliveryReport: Boolean = false
    ): Long {
        if (!isDefault(ctx)) return 0L
        val v = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, if (read) 1 else 0)
            put(Telephony.Sms.SEEN, if (read) 1 else 0)
            if (!incoming) {
                put(Telephony.Sms.DATE_SENT, date)
                put(
                    Telephony.Sms.STATUS,
                    if (pendingDeliveryReport) PROVIDER_STATUS_PENDING else PROVIDER_STATUS_NONE
                )
            }
        }
        val uri = if (incoming) Telephony.Sms.Inbox.CONTENT_URI else Telephony.Sms.Sent.CONTENT_URI
        return runCatching {
            ctx.contentResolver.insert(uri, v)?.lastPathSegment?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    /** Marks the provider rows read, so the other SMS apps stop showing them as unread. */
    fun markRead(ctx: Context, ids: List<Long>) {
        if (ids.isEmpty() || !isDefault(ctx)) return
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        ids.asSequence().filter { it > 0 }.chunked(400).forEach { chunk ->
            val selection = Telephony.Sms._ID + " IN (" + chunk.joinToString(",") { "?" } + ")"
            val args = chunk.map { it.toString() }.toTypedArray()
            runCatching {
                ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, values, selection, args)
            }.onFailure { e ->
                Log.w(TAG, "markRead batch failed (${chunk.size} ids), trying one by one", e)
                // Some vendor providers reject one big IN(...) update; fall back to single rows.
                chunk.forEach { id ->
                    runCatching {
                        ctx.contentResolver.update(
                            Telephony.Sms.CONTENT_URI, values,
                            Telephony.Sms._ID + " = ?", arrayOf(id.toString())
                        )
                    }.onFailure { Log.w(TAG, "markRead failed for provider id $id", it) }
                }
            }
        }
    }

    /** Mirrors a sent / delivered / failed state of an outgoing message into the shared provider. */
    fun setStatus(ctx: Context, providerId: Long, providerStatus: Int, dateSent: Long) {
        if (providerId <= 0 || !isDefault(ctx)) return
        val v = ContentValues().apply {
            put(Telephony.Sms.STATUS, providerStatus)
            put(Telephony.Sms.DATE_SENT, dateSent)
        }
        runCatching {
            ctx.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                v,
                Telephony.Sms._ID + " = ?",
                arrayOf(providerId.toString())
            )
        }
    }

    fun delete(ctx: Context, ids: List<Long>) {
        if (ids.isEmpty() || !isDefault(ctx)) return
        for (id in ids) {
            if (id <= 0) continue
            runCatching {
                ctx.contentResolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null)
            }
        }
    }

    /**
     * Syncs with the shared SMS provider. It imports rows that another SMS app added while it was
     * the default app (which is why a message sent from Samsung / Google Messages appeared to be
     * missing here), and it pulls read flags that were changed in those apps.
     *
     * Importing is incremental by provider id, so this is cheap to call on every return to the
     * foreground. Runs only once at a time.
     */
    suspend fun syncInto(ctx: Context, repo: Repo) = withContext(Dispatchers.IO) {
        if (!canRead(ctx) || !syncing.compareAndSet(false, true)) return@withContext
        try {
            val lastId = repo.dao.maxProviderId()

            // Old builds marked messages read only in our own database (or while another app was
            // the default one). Push those read flags so the other apps stop showing them as
            // unread. Only rows the provider still has as unread are touched.
            val prefs = App.instance.prefs
            if (isDefault(ctx) && (prefs.pendingReadSync || !prefs.readStatesRepaired)) {
                val providerUnread = providerIdsWithReadFlag(ctx, repo.dao.readProviderIds(), read = false)
                if (providerUnread.isNotEmpty()) markRead(ctx, providerUnread)
                prefs.pendingReadSync = false
                prefs.readStatesRepaired = true
            }

            // Read state changed while another app was the default one: reflect it locally.
            val locallyUnread = repo.dao.unreadIncomingProviderIds()
            if (locallyUnread.isNotEmpty()) {
                val nowRead = providerIdsWithReadFlag(ctx, locallyUnread, true)
                if (nowRead.isNotEmpty()) repo.dao.markReadByProviderIds(nowRead)
            }

            val proj = arrayOf(
                Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
                Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
                Telephony.Sms.STATUS, Telephony.Sms.SUBSCRIPTION_ID
            )
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                proj,
                Telephony.Sms._ID + " > ?",
                arrayOf(lastId.toString()),
                Telephony.Sms._ID + " ASC"
            )?.use { c ->
                val batch = ArrayList<MessageEntity>(500)
                while (c.moveToNext()) {
                    val type = c.getInt(4)
                    if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                    val address = c.getString(1)?.trim().orEmpty()
                    if (address.isEmpty()) continue
                    val incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX
                    val carrierStatus = c.getInt(6)
                    val status = when {
                        incoming -> Status.NONE
                        type == Telephony.Sms.MESSAGE_TYPE_SENT -> when {
                            carrierStatus == PROVIDER_STATUS_COMPLETE -> Status.DELIVERED
                            carrierStatus >= PROVIDER_STATUS_FAILED -> Status.DELIVERY_FAILED
                            else -> Status.SENT // includes pending delivery reports
                        }
                        type == Telephony.Sms.MESSAGE_TYPE_FAILED -> Status.FAILED
                        type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                            type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> Status.SENDING
                        else -> Status.SENT
                    }
                    val date = c.getLong(3)
                    batch.add(
                        MessageEntity(
                            threadKey = Phone.threadKey(address),
                            address = address,
                            body = c.getString(2).orEmpty(),
                            date = date,
                            incoming = incoming,
                            read = c.getInt(5) == 1,
                            status = status,
                            totalParts = 1,
                            sentParts = if (incoming || status == Status.SENDING) 0 else 1,
                            deliveredParts = if (status == Status.DELIVERED) 1 else 0,
                            deliveredAt = if (status == Status.DELIVERED) date else 0L,
                            providerId = c.getLong(0),
                            subId = c.getInt(7)
                        )
                    )
                    if (batch.size >= 500) {
                        repo.bulkImport(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) repo.bulkImport(batch.toList())
            }
        } finally {
            syncing.set(false)
        }
    }

    /** Of [ids], the provider rows whose read flag is [read]. */
    private fun providerIdsWithReadFlag(ctx: Context, ids: List<Long>, read: Boolean): List<Long> {
        val result = ArrayList<Long>()
        val flag = if (read) 1 else 0
        ids.asSequence().filter { it > 0 }.chunked(400).forEach { chunk ->
            val selection = Telephony.Sms._ID + " IN (" + chunk.joinToString(",") { "?" } +
                ") AND " + Telephony.Sms.READ + " = $flag"
            runCatching {
                ctx.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    selection,
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )?.use { c -> while (c.moveToNext()) result.add(c.getLong(0)) }
            }
        }
        return result
    }
}

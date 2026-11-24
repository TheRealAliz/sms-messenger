package com.tamadon.smsmessenger.core

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.AppState
import com.tamadon.smsmessenger.R
import com.tamadon.smsmessenger.ui.MainActivity

object Notifier {
    private const val CHANNEL = "messages"
    const val KEY_REPLY = "reply_text"

    /** [ctx] should be a localized context so the channel name follows the app language. */
    fun createChannels(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun cancel(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(key.hashCode())

    private fun mutableFlag() = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0

    /**
     * Posts (or refreshes) the notification of one conversation.
     *  - locked / "hide in notification" conversations show neither sender nor text
     *  - the global "hide content" setting hides the text but keeps the sender
     *  - blocked / spam / muted conversations never notify
     *  - the notification has "mark as read" and an inline "reply" action
     */
    @SuppressLint("MissingPermission")
    suspend fun show(ctx: Context, key: String) {
        val app = App.instance
        val repo = app.repo
        val t = repo.dao.thread(key) ?: return

        if (AppState.isViewing(key)) {
            repo.markRead(key)
            return
        }
        if (t.muted || t.blocked || t.spam) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return

        val unread = repo.dao.recentUnread(key)
        if (unread.isEmpty()) return
        val count = repo.dao.unreadCount(key)
        Contacts.ensureLoaded(ctx)

        val lc = app.localized()
        val hideAll = t.hideNotif || t.locked
        val hideBody = hideAll || app.prefs.hideAllContent

        val tap = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ADDRESS, t.address)
        }
        val tapPi = PendingIntent.getActivity(
            ctx, key.hashCode(), tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val readIntent = Intent(ctx, MarkReadReceiver::class.java).putExtra("key", key)
        val readPi = PendingIntent.getBroadcast(
            ctx, key.hashCode(), readIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(lc.getString(R.string.notif_new_message))
            .build()

        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(tapPi)
            .setWhen(unread.first().date)
            .addAction(0, lc.getString(R.string.mark_read), readPi)

        // One-time code: a "Copy code" action right on the notification.
        val newestBody = unread.first().body
        if (!hideAll && !app.prefs.hideAllContent) {
            val code = TextTools.otp(newestBody)
            if (code != null) {
                val copyIntent = Intent(ctx, CopyCodeReceiver::class.java).putExtra("code", code)
                val copyPi = PendingIntent.getBroadcast(
                    ctx, key.hashCode() xor 0x0c0de, copyIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                b.addAction(
                    R.drawable.ic_notification, lc.getString(R.string.copy_code), copyPi
                )
            }
        }

        // Inline reply: only for senders that can receive a message (not alphanumeric sender IDs)
        if (Phone.canReply(t.address)) {
            val remote = RemoteInput.Builder(KEY_REPLY).setLabel(lc.getString(R.string.reply)).build()
            val replyIntent = Intent(ctx, ReplyReceiver::class.java).putExtra("key", key)
            val replyPi = PendingIntent.getBroadcast(
                ctx, key.hashCode(), replyIntent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag()
            )
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, lc.getString(R.string.reply), replyPi)
                    .addRemoteInput(remote)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false)
                    .setAllowGeneratedReplies(true)
                    .build()
            )
        }

        if (hideAll) {
            b.setContentTitle(lc.getString(R.string.notif_new_message))
                .setContentText(
                    if (count > 1) lc.getString(R.string.notif_unread_count, count)
                    else lc.getString(R.string.notif_one_new)
                )
        } else {
            val name = Contacts.nameOf(t.address) ?: t.address
            val sender = Person.Builder().setName(name).build()
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName(lc.getString(R.string.me)).build())
            for (m in unread.reversed()) {
                style.addMessage(if (hideBody) lc.getString(R.string.notif_new_message) else m.body, m.date, sender)
            }
            b.setContentTitle(name)
                .setContentText(if (hideBody) lc.getString(R.string.notif_new_message) else unread.first().body)
                .setStyle(style)
        }
        nm.notify(key.hashCode(), b.build())
    }
}

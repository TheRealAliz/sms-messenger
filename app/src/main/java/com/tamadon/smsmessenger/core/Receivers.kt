package com.tamadon.smsmessenger.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SmsMessage
import android.widget.Toast
import androidx.core.app.RemoteInput
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Incoming SMS (delivered only to the default SMS app). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return
        val address = msgs[0].displayOriginatingAddress ?: msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.displayMessageBody ?: "" }
        val date = System.currentTimeMillis()
        // Which SIM received it ("subscription" is the extra the telephony stack puts on SMS_DELIVER)
        val subId = intent.getIntExtra("subscription", intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1))
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = App.instance.repo
                val key = repo.receive(address, body, date, subId)
                // Auto-copy one-time codes (opt-in in settings), but never from spam / blocked senders.
                if (App.instance.prefs.autoCopyOtp) {
                    val t = repo.dao.thread(key)
                    if (t?.blocked != true && t?.spam != true) {
                        TextTools.otp(body)?.let { TextTools.copy(ctx, "otp", it) }
                    }
                }
                Notifier.show(ctx.applicationContext, key)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Placeholder: MMS is not implemented, but the receiver is required for the default-SMS role. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {}
}

/** Receives "sent" and "delivered" callbacks from the radio for every message part. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        if (id < 0) return
        val action = intent.action ?: return
        val code = resultCode
        val pdu = intent.getByteArrayExtra("pdu")
        val format = intent.getStringExtra("format")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = App.instance.repo
                when (action) {
                    SmsSender.ACTION_SENT -> repo.onSentResult(id, code == Activity.RESULT_OK)
                    SmsSender.ACTION_DELIVERED -> {
                        val st = parseStatus(pdu, format, code)
                        val stillTrying = st in 0x20..0x3F // temporary condition, a final report will follow
                        if (!stillTrying) repo.onDeliveryResult(id, st)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** TP-Status per 3GPP TS 23.040: <0x20 delivered, 0x20-0x3F pending, >=0x40 failed. */
    @Suppress("DEPRECATION")
    private fun parseStatus(pdu: ByteArray?, format: String?, code: Int): Int {
        if (pdu != null) {
            val s = runCatching {
                (if (format != null) SmsMessage.createFromPdu(pdu, format) else SmsMessage.createFromPdu(pdu)).status
            }.getOrNull()
            if (s != null) return s
        }
        return if (code == Activity.RESULT_OK) 0 else 0x40
    }
}

/** "Mark as read" action on notifications. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                App.instance.repo.markRead(key)
                Notifier.cancel(ctx, key)
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Copy code" action on notifications: puts the one-time code on the clipboard. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val code = intent.getStringExtra("code") ?: return
        TextTools.copy(ctx, "otp", code)
        Toast.makeText(ctx, ctx.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}

/** Inline "Reply" action on notifications: sends the typed text on the SIM the message arrived on. */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = App.instance.repo
                val thread = repo.dao.thread(key)
                if (thread != null && !text.isNullOrEmpty() && Phone.canReply(thread.address)) {
                    val sub = repo.dao.lastIncomingSub(key) ?: -1
                    SmsSender.send(ctx.applicationContext, thread.address, text, sub)
                    repo.markRead(key)
                }
                // Cancelling also stops the "sending" spinner the reply UI shows on the notification
                Notifier.cancel(ctx, key)
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Reply with message" from the incoming-call screen. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.data
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (uri != null && !text.isNullOrBlank()) {
            val number = Uri.decode(uri.schemeSpecificPart).substringBefore('?').trim()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsSender.send(applicationContext, number, text)
                } finally {
                    stopSelf(startId)
                }
            }
        } else {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }
}

/** Daily cleanup of old trash items. */
class PurgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        App.instance.repo.purgeOld()
        return Result.success()
    }
}

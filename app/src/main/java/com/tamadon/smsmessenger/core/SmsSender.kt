package com.tamadon.smsmessenger.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.tamadon.smsmessenger.App
import com.tamadon.smsmessenger.data.MessageEntity

object SmsSender {
    const val ACTION_SENT = "com.tamadon.smsmessenger.SMS_SENT"
    const val ACTION_DELIVERED = "com.tamadon.smsmessenger.SMS_DELIVERED"

    @Suppress("DEPRECATION")
    private fun smsManager(ctx: Context, subId: Int): SmsManager? =
        if (Build.VERSION.SDK_INT >= 31) {
            val base = ctx.getSystemService(SmsManager::class.java)
            if (subId >= 0) base?.createForSubscriptionId(subId) else base
        } else {
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }

    /**
     * Stores the message locally, then hands it to the radio. [subId] is the wanted SIM
     * (-1 = the preferred / default one). Returns the local message id.
     */
    suspend fun send(ctx: Context, address: String, body: String, subId: Int = -1): Long {
        val app = App.instance
        val text = body.trim()
        val to = address.trim()
        if (text.isEmpty() || to.isEmpty()) return -1L

        val sims = Sims.list(ctx)
        val sub = if (subId >= 0 && sims.any { it.subId == subId }) subId
        else Sims.resolve(ctx, app.prefs.preferredSub)

        val sms = smsManager(ctx, sub)
        val parts: ArrayList<String> = sms?.divideMessage(text) ?: arrayListOf(text)
        val id = app.repo.insertOutgoing(to, text, parts.size, sub)
        if (sms == null) {
            app.repo.onSentResult(id, false)
            return id
        }
        try {
            val wantReport = app.prefs.deliveryReports
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent>()
            for (i in parts.indices) {
                sent.add(statusIntent(ctx, ACTION_SENT, id, i))
                if (wantReport) delivered.add(statusIntent(ctx, ACTION_DELIVERED, id, i))
            }
            sms.sendMultipartTextMessage(to, null, parts, sent, if (wantReport) delivered else null)
        } catch (e: Exception) {
            app.repo.onSentResult(id, false)
        }
        return id
    }

    /** Retry a failed message on the same SIM: the failed row is removed and a fresh one is sent. */
    suspend fun resend(ctx: Context, m: MessageEntity) {
        App.instance.repo.deleteMessageForever(m.id)
        send(ctx, m.address, m.body, m.subId)
    }

    private fun statusIntent(ctx: Context, action: String, id: Long, part: Int): PendingIntent {
        val intent = Intent(ctx, SmsStatusReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("smsm://status/$id/$part"))
            .putExtra("id", id)
            .putExtra("part", part)
        // Must be mutable: the framework adds the delivery-report PDU to the intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }
}

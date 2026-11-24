package com.tamadon.smsmessenger.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class Sim(val subId: Int, val slot: Int, val name: String) {
    /** "SIM 1" - slot indexes are zero based inside Android. */
    val slotNumber: Int get() = slot + 1
}

object Sims {
    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Active SIMs, ordered by slot. Empty when permission is missing or there is no SIM. */
    @SuppressLint("MissingPermission") // guarded by granted() above
    fun list(ctx: Context): List<Sim> {
        if (!granted(ctx)) return emptyList()
        return runCatching {
            val sm = ctx.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            sm.activeSubscriptionInfoList.orEmpty()
                .map { Sim(it.subscriptionId, it.simSlotIndex, (it.displayName ?: it.carrierName)?.toString().orEmpty()) }
                .sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    fun systemDefaultSms(): Int =
        if (Build.VERSION.SDK_INT >= 24) SubscriptionManager.getDefaultSmsSubscriptionId() else -1

    /**
     * The subscription to send with: the user's preferred SIM if it is still inserted, otherwise
     * the system default SMS SIM. -1 means "let the system decide" (single SIM or unknown).
     */
    fun resolve(ctx: Context, preferred: Int): Int {
        val sims = list(ctx)
        if (sims.size < 2) return sims.firstOrNull()?.subId ?: -1
        if (preferred >= 0 && sims.any { it.subId == preferred }) return preferred
        val def = systemDefaultSms()
        return if (sims.any { it.subId == def }) def else sims.first().subId
    }
}

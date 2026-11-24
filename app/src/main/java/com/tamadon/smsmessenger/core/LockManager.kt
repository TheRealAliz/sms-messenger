package com.tamadon.smsmessenger.core

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tamadon.smsmessenger.data.Prefs
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Holds which locked conversations are currently unlocked (in memory only) and manages the
 * fallback PIN. The PIN is stored as a salted PBKDF2 hash, never in clear text.
 */
object LockManager {
    sealed class Verify {
        object Ok : Verify()
        object Wrong : Verify()
        data class Locked(val remainingMs: Long) : Verify()
    }

    private val unlocked = mutableStateMapOf<String, Boolean>()

    /** Whether the whole app has been unlocked in this process / foreground session. */
    var appUnlocked: Boolean by mutableStateOf(false)
        private set

    fun markAppUnlocked() { appUnlocked = true }
    fun relockApp() { appUnlocked = false }

    fun isUnlocked(key: String) = unlocked[key] == true
    fun markUnlocked(key: String) { unlocked[key] = true }
    fun relockAll() = unlocked.clear()

    fun hasPin(p: Prefs) = p.pinHash != null

    fun setPin(p: Prefs, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.pinSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        p.pinHash = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP)
        p.failedPinAttempts = 0
        p.pinLockoutUntil = 0L
    }

    fun clearPin(p: Prefs) {
        p.pinHash = null
        p.pinSalt = null
        p.failedPinAttempts = 0
        p.pinLockoutUntil = 0L
        relockAll()
    }

    fun verify(p: Prefs, pin: String): Verify {
        val now = System.currentTimeMillis()
        if (p.pinLockoutUntil > now) return Verify.Locked(p.pinLockoutUntil - now)
        val stored = p.pinHash ?: return Verify.Ok
        val salt = Base64.decode(p.pinSalt ?: return Verify.Wrong, Base64.NO_WRAP)
        val candidate = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP)
        return if (MessageDigest.isEqual(candidate.toByteArray(), stored.toByteArray())) {
            p.failedPinAttempts = 0
            Verify.Ok
        } else {
            val failed = p.failedPinAttempts + 1
            p.failedPinAttempts = failed
            if (failed >= 5) {
                // 30 s after the 5th miss, doubling with every further miss
                val wait = 30_000L * (1L shl (failed - 5).coerceAtMost(8))
                p.pinLockoutUntil = now + wait
                Verify.Locked(wait)
            } else Verify.Wrong
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

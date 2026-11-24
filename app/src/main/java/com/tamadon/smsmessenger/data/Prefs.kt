package com.tamadon.smsmessenger.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Settings. The ones that change what the UI looks like are Compose state, so screens update
 * immediately when they are changed from the settings page.
 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()

    // ------------------------------------------------------------ search history (observable)

    /** Most recent first; shared by every search screen. */
    val searchHistory = mutableStateListOf<String>().apply {
        sp.getString("search_history", "").orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { add(it) }
    }

    fun addSearchHistory(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchHistory.remove(q)
        searchHistory.add(0, q)
        while (searchHistory.size > 12) searchHistory.removeAt(searchHistory.lastIndex)
        persistSearchHistory()
    }

    fun removeSearchHistory(query: String) {
        if (searchHistory.remove(query)) persistSearchHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        persistSearchHistory()
    }

    private fun persistSearchHistory() {
        sp.edit().putString("search_history", searchHistory.joinToString("\n")).apply()
    }

    // ------------------------------------------------------------ appearance (observable)

    /** "system", "fa" or "en". Changing it requires recreating the activity. */
    var language: String by mutableStateOf(sp.getString("language", "system") ?: "system")
        private set

    fun updateLanguage(v: String) {
        language = v
        sp.edit().putString("language", v).apply()
    }

    /** "auto" (Jalali for Persian, Gregorian for English), "jalali" or "gregorian". */
    var calendar: String by mutableStateOf(sp.getString("calendar", "auto") ?: "auto")
        private set

    fun updateCalendar(v: String) {
        calendar = v
        sp.edit().putString("calendar", v).apply()
    }

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode: Int by mutableIntStateOf(sp.getInt("theme_mode", 0))
        private set

    fun updateThemeMode(v: Int) {
        themeMode = v
        sp.edit().putInt("theme_mode", v).apply()
    }

    /** Primary (seed) colour as ARGB. */
    var primaryColor: Int by mutableIntStateOf(sp.getInt("primary_color", 0xFF1E6BD6.toInt()))
        private set

    fun updatePrimaryColor(v: Int) {
        primaryColor = v
        sp.edit().putInt("primary_color", v).apply()
    }

    /** Message text size inside chats, in sp. */
    var chatFontSp: Float by mutableFloatStateOf(sp.getFloat("chat_font_sp", 16f))
        private set

    fun updateChatFontSp(v: Float) {
        chatFontSp = v
        sp.edit().putFloat("chat_font_sp", v).apply()
    }

    /** Corner radius of message bubbles, in dp. */
    var messageCornerDp: Int by mutableIntStateOf(sp.getInt("message_corner_dp", 16))
        private set

    fun updateMessageCorner(v: Int) {
        messageCornerDp = v.coerceIn(0, 26)
        sp.edit().putInt("message_corner_dp", messageCornerDp).apply()
    }

    /** false: tapping a quick reply puts the text in the input; true: it sends immediately. */
    var quickSendDirect: Boolean by mutableStateOf(sp.getBoolean("quick_direct", false))
        private set

    fun updateQuickSendDirect(v: Boolean) {
        quickSendDirect = v
        putBool("quick_direct", v)
    }

    /** Subscription id used for sending; -1 = the system default SMS SIM. */
    var preferredSub: Int by mutableIntStateOf(sp.getInt("preferred_sub", -1))
        private set

    fun updatePreferredSub(v: Int) {
        preferredSub = v
        sp.edit().putInt("preferred_sub", v).apply()
    }

    // ------------------------------------------------------------ behaviour

    /** Ask the carrier for delivery reports on outgoing messages. */
    var deliveryReports: Boolean
        get() = bool("delivery_reports", true)
        set(v) = putBool("delivery_reports", v)

    /** Hide message text in notifications for all conversations (sender stays visible). */
    var hideAllContent: Boolean
        get() = bool("hide_all_content", false)
        set(v) = putBool("hide_all_content", v)

    /** Copy a detected one-time code to the clipboard as soon as the message arrives. */
    var autoCopyOtp: Boolean
        get() = bool("auto_copy_otp", false)
        set(v) = putBool("auto_copy_otp", v)

    /** Ask for biometrics / PIN every time the app is opened. */
    var appLockEnabled: Boolean by mutableStateOf(bool("app_lock", false))
        private set

    fun updateAppLock(v: Boolean) {
        appLockEnabled = v
        putBool("app_lock", v)
    }

    var biometricEnabled: Boolean
        get() = bool("biometric", true)
        set(v) = putBool("biometric", v)

    /** Days to keep items in trash. 0 = never purge automatically. */
    var trashRetentionDays: Int
        get() = sp.getInt("trash_days", 30)
        set(v) = sp.edit().putInt("trash_days", v).apply()

    var imported: Boolean
        get() = bool("imported", false)
        set(v) = putBool("imported", v)

    var seededQuickReplies: Boolean
        get() = bool("seeded_quick", false)
        set(v) = putBool("seeded_quick", v)

    /** The default quick replies were switched from English to Persian (one-time migration). */
    var quickDefaultsPersian: Boolean
        get() = bool("quick_defaults_fa_v1", false)
        set(v) = putBool("quick_defaults_fa_v1", v)

    /** One-time push of our read flags into the shared SMS provider (upgrade from older builds). */
    var readStatesRepaired: Boolean
        get() = bool("read_states_repaired_v2", false)
        set(v) = putBool("read_states_repaired_v2", v)

    /** A conversation was read while another app was the default one; push it when we get the role back. */
    var pendingReadSync: Boolean
        get() = bool("pending_read_sync", false)
        set(v) = putBool("pending_read_sync", v)

    // PIN storage (hash + salt only; the PIN itself is never stored)
    var pinHash: String?
        get() = sp.getString("pin_hash", null)
        set(v) = sp.edit().putString("pin_hash", v).apply()

    var pinSalt: String?
        get() = sp.getString("pin_salt", null)
        set(v) = sp.edit().putString("pin_salt", v).apply()

    var failedPinAttempts: Int
        get() = sp.getInt("pin_failed", 0)
        set(v) = sp.edit().putInt("pin_failed", v).apply()

    var pinLockoutUntil: Long
        get() = sp.getLong("pin_lockout", 0L)
        set(v) = sp.edit().putLong("pin_lockout", v).apply()
}

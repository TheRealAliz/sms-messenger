package com.tamadon.smsmessenger.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

object Phone {
    /**
     * Groups "0912 345 6789", "+98 912 345 6789" and "00989123456789" into the same conversation
     * by using the last 10 digits. Alphanumeric sender IDs and short codes are kept as-is.
     */
    fun threadKey(address: String): String {
        val a = address.trim()
        val digits = asciiDigits(a).filter { it.isDigit() }
        val hasLetters = a.any { it.isLetter() }
        return if (hasLetters || digits.length < 7) a.lowercase() else digits.takeLast(10)
    }

    /** Alphanumeric sender IDs ("BankMelli", "Snapp") can only send, never receive replies. */
    fun canReply(address: String): Boolean = address.none { it.isLetter() }

    /** True when the address is something that can be dialled. */
    fun canCall(address: String): Boolean =
        address.none { it.isLetter() } && asciiDigits(address).count { it.isDigit() } >= 3

    /** Converts Persian / Arabic-Indic digits to ASCII. */
    fun asciiDigits(s: String): String = buildString {
        for (c in s) {
            append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
    }

    /** Normalises text for searching: Arabic/Persian letter variants, digits, case, ZWNJ. */
    fun normalize(s: String): String =
        asciiDigits(s).lowercase()
            .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
            .replace("‌", "").replace("‏", "").replace("‎", "")
}

data class ContactHit(val name: String, val number: String, val photoUri: String? = null)

/**
 * All phone contacts are loaded into memory once (and refreshed when the app comes to the
 * foreground), so list rows, notifications and the contact picker never touch the contacts
 * provider on the UI path.
 */
object Contacts {
    @Volatile private var items: List<ContactHit> = emptyList()
    @Volatile private var byKey: Map<String, String> = emptyMap()
    @Volatile private var photoByKey: Map<String, String> = emptyMap()
    @Volatile var loaded: Boolean = false
        private set

    /** Bumps whenever the contact list was reloaded, so Compose can refresh displayed names. */
    var version: Int by mutableIntStateOf(0)
        private set

    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    suspend fun reload(ctx: Context) = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext
        val list = ArrayList<ContactHit>(512)
        val map = HashMap<String, String>(512)
        val photos = HashMap<String, String>(512)
        // [version] is Compose state: it must only be written from the main thread.
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                null, null, null
            )?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty()
                    val number = c.getString(1)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val key = Phone.threadKey(number)
                    // Prefer the full photo: avatars downsample it on load, while the profile
                    // preview needs the high-resolution original to stay sharp when zoomed.
                    val photo = c.getString(2)?.takeIf { it.isNotBlank() }
                        ?: c.getString(3)?.takeIf { it.isNotBlank() }
                    if (name.isNotEmpty()) {
                        map.putIfAbsent(key, name)
                        if (photo != null) photos.putIfAbsent(key, photo)
                    }
                    if (seen.add("$name|$key")) {
                        list.add(ContactHit(name.ifEmpty { number }, number, photo))
                    }
                }
            }
        }
        items = list
        byKey = map
        photoByKey = photos
        loaded = true
        // Published on the main thread: a snapshot state written off the UI thread can be read
        // inside a stale composition snapshot and crash ("state created after the snapshot").
        withContext(Dispatchers.Main) { version += 1 }
    }

    suspend fun ensureLoaded(ctx: Context) {
        if (!loaded) reload(ctx)
    }

    fun nameOf(address: String): String? = byKey[Phone.threadKey(address)]

    /** Profile photo (content URI) of the contact behind [address], when the phone has one. */
    fun photoOf(address: String): String? = photoByKey[Phone.threadKey(address)]

    /** Touches [version] on the main thread so its Compose state is never created on a worker. */
    fun warmUp() {
        version
    }

    private fun group(name: String): Int {
        val ch = name.firstOrNull { it.isLetter() } ?: return 2
        return when (Character.UnicodeScript.of(ch.code)) {
            Character.UnicodeScript.ARABIC -> 0
            Character.UnicodeScript.LATIN -> 1
            else -> 2
        }
    }

    /**
     * Contacts matching [query] (all of them when empty). Contacts written in the app's language
     * come first: Persian names first in Persian mode, Latin names first in English mode.
     */
    fun search(query: String, persianFirst: Boolean): List<ContactHit> {
        val qn = Phone.normalize(query).trim()
        val qDigits = qn.filter { it.isDigit() }
        val src = if (qn.isEmpty()) items else items.filter { c ->
            Phone.normalize(c.name).contains(qn) ||
                (qDigits.isNotEmpty() && Phone.asciiDigits(c.number).filter { it.isDigit() }.let { n ->
                    n.contains(qDigits) || (qDigits.trimStart('0').isNotEmpty() && n.contains(qDigits.trimStart('0')))
                })
        }
        val collator = Collator.getInstance(Locale(if (persianFirst) "fa" else "en"))
        val rank = { c: ContactHit ->
            when (val g = group(c.name)) {
                0 -> if (persianFirst) 0 else 1
                1 -> if (persianFirst) 1 else 0
                else -> g
            }
        }
        return src.sortedWith(compareBy<ContactHit> { rank(it) }.thenComparator { a, b -> collator.compare(a.name, b.name) })
    }
}

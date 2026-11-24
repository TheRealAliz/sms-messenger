package com.tamadon.smsmessenger.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Detection helpers for one-time codes, links and clipboard shortcuts. */
object TextTools {

    private val keywordCode = Regex(
        "(?i)(?:code|کد|رمز|otp)[^0-9]{0,24}([0-9]{4,6})"
    )
    private val anyCode = Regex("(?<![0-9])([0-9]{4,6})(?![0-9])")

    /** USSD / MMI style tokens such as *1234* or #1234#; their digits are not login codes. */
    private val starHashToken = Regex("[*#][0-9*#]*[*#]")

    /** Dates such as 1405.08.12, 2024-11-13 or 12/08/1405 are not login codes either. */
    private val dateToken = Regex("[0-9]{1,4}([./-])[0-9]{1,2}\\1[0-9]{1,4}")

    /**
     * The one-time code in [text], if any. A number that follows "code" / "کد" / "رمز" / "otp"
     * wins over a random 4 to 6 digit number. Persian and Arabic digits are accepted. Numbers
     * wrapped in * or # (USSD / MMI codes) and dates are ignored.
     */
    fun otp(text: String): String? {
        val s = Phone.asciiDigits(text)
            .replace(starHashToken, " ")
            .replace(dateToken, " ")
        keywordCode.find(s)?.groupValues?.getOrNull(1)?.let { return it }
        return anyCode.find(s)?.groupValues?.getOrNull(1)
    }

    private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")

    /** Links found in [text]; trailing punctuation is not part of the address. */
    fun links(text: String): List<String> {
        val out = ArrayList<String>()
        for (m in urlRegex.findAll(text)) {
            var u = m.groupValues[1].trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '،', '؛')
            if (u.startsWith("www.", ignoreCase = true)) u = "https://$u"
            if (u.length > 9 && u !in out) out.add(u)
        }
        return out
    }

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun clipboardText(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    }.getOrNull()
}

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?
)

/**
 * Very small Open-Graph reader: no dependency, one request per link. Metadata is written to
 * files/link_previews and the picture is cached by [Images], so a link is fetched only once ever.
 */
object LinkPreviews {
    private val cache = HashMap<String, LinkPreview?>()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun cacheFile(ctx: Context, url: String): File =
        File(ctx.filesDir, "link_previews").apply { mkdirs() }.resolve(hash(url) + ".json")

    suspend fun load(ctx: Context, url: String): LinkPreview? {
        synchronized(cache) {
            if (cache.containsKey(url)) return cache[url]
        }
        val stored = withContext(Dispatchers.IO) { read(ctx, url) }
        if (stored != null) {
            synchronized(cache) { cache[url] = stored }
            return stored
        }
        val fetched = withContext(Dispatchers.IO) { runCatching { fetch(url) }.getOrNull() }
        if (fetched != null) withContext(Dispatchers.IO) { write(ctx, url, fetched) }
        synchronized(cache) { cache[url] = fetched }
        return fetched
    }

    private fun read(ctx: Context, url: String): LinkPreview? = runCatching {
        val o = JSONObject(cacheFile(ctx, url).readText())
        LinkPreview(
            url = url,
            title = str(o, "title"),
            description = str(o, "description"),
            imageUrl = str(o, "image")
        ).takeIf { it.title != null || it.description != null || it.imageUrl != null }
    }.getOrNull()

    private fun write(ctx: Context, url: String, p: LinkPreview) {
        runCatching {
            val o = JSONObject()
                .put("title", p.title ?: JSONObject.NULL)
                .put("description", p.description ?: JSONObject.NULL)
                .put("image", p.imageUrl ?: JSONObject.NULL)
            cacheFile(ctx, url).writeText(o.toString())
        }
    }

    private fun str(o: JSONObject, key: String): String? =
        if (o.isNull(key)) null else o.optString(key).trim().ifEmpty { null }

    private fun fetch(url: String): LinkPreview? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "en-US,en;q=0.8,fa;q=0.6")
        }
        try {
            if (conn.responseCode !in 200..399) return null
            val type = conn.contentType.orEmpty()
            if (!type.contains("html", ignoreCase = true) && type.isNotEmpty()) return null
            val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val sb = StringBuilder()
                val buf = CharArray(8192)
                while (sb.length < 300_000) {
                    val n = r.read(buf)
                    if (n < 0) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }

            fun attr(tag: String, name: String): String? {
                val re = Regex(
                    """(?i)\b""" + Regex.escape(name) +
                        """\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))"""
                )
                val m = re.find(tag) ?: return null
                return m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            }

            val metas = HashMap<String, String>()
            Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
                val tag = m.value
                val key = attr(tag, "property") ?: attr(tag, "name") ?: return@forEach
                val content = attr(tag, "content") ?: return@forEach
                metas.putIfAbsent(key.lowercase(), decodeEntities(content).trim())
            }

            fun meta(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { metas[it]?.takeIf { v -> v.isNotEmpty() } }

            val title = meta("og:title", "twitter:title")
                ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(html)?.groupValues?.getOrNull(1)?.let { decodeEntities(it).trim() }
            val desc = meta("og:description", "twitter:description", "description")
            val rawImage = meta("og:image", "og:image:url", "og:image:secure_url", "twitter:image", "twitter:image:src")
            val image = rawImage?.let { absolute(url, it) }

            if (title.isNullOrBlank() && desc.isNullOrBlank() && image.isNullOrBlank()) return null
            return LinkPreview(url, title?.take(200), desc?.take(300), image)
        } finally {
            conn.disconnect()
        }
    }

    private fun absolute(base: String, ref: String): String? = runCatching {
        when {
            ref.startsWith("http://", true) || ref.startsWith("https://", true) -> ref
            ref.startsWith("//") -> URL(base).protocol + ":" + ref
            else -> URL(URL(base), ref).toString()
        }
    }.getOrNull()

    private fun decodeEntities(s: String): String {
        var out = s
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
            .replace("&nbsp;", " ")
        out = Regex("""&#(\d+);""").replace(out) { m ->
            runCatching { m.groupValues[1].toInt().toChar().toString() }.getOrDefault(m.value)
        }
        out = Regex("""&#[xX]([0-9a-fA-F]+);""").replace(out) { m ->
            runCatching { m.groupValues[1].toInt(16).toChar().toString() }.getOrDefault(m.value)
        }
        return out
    }
}

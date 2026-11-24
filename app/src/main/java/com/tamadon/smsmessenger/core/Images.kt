package com.tamadon.smsmessenger.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Tiny image loader used for contact photos and link-preview pictures.
 *
 * Nothing here depends on an image library: contact photos are read through the content resolver,
 * remote pictures are downloaded once, written to the app's private cache and decoded downsampled.
 * Decoded bitmaps are kept in a byte-bounded LruCache so scrolling a chat does not re-decode them.
 */
object Images {
    private val maxCacheBytes =
        (Runtime.getRuntime().maxMemory() / 8L).coerceIn(8L * 1024 * 1024, 64L * 1024 * 1024).toInt()

    private val mem = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private const val MAX_DOWNLOAD = 8 * 1024 * 1024

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun cacheDir(ctx: Context): File =
        File(ctx.filesDir, "image_cache").apply { mkdirs() }

    /** A decoded image already in memory, if there is one. */
    fun peek(key: String): Bitmap? = mem.get(key)

    /** Loads a local image (contacts provider, file URI ...). */
    suspend fun local(ctx: Context, uri: String, maxPx: Int = 640): Bitmap? {
        // The size is part of the key: a 256 px avatar must not be returned when the profile
        // preview asks for a large, sharp copy of the same picture.
        val key = "local:$uri@$maxPx"
        mem.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    decodeSampled(input.readBytesBounded(12 * 1024 * 1024), maxPx)
                }
            }.getOrNull()
        }
        if (bitmap != null) mem.put(key, bitmap)
        return bitmap
    }

    /**
     * Loads a remote image, downloading it at most once. The bytes are kept in
     * files/image_cache, so the next app launch does not touch the network again.
     */
    suspend fun remote(ctx: Context, url: String, maxPx: Int = 1024): Bitmap? {
        val key = "remote:$url"
        mem.get(key)?.let { return it }
        val file = File(cacheDir(ctx), "r_" + hash(url))
        val bitmap = withContext(Dispatchers.IO) {
            val bytes = if (file.exists()) {
                runCatching { file.readBytes() }.getOrNull()
            } else {
                val downloaded = runCatching { download(url) }.getOrNull()
                if (downloaded != null && downloaded.isNotEmpty()) {
                    runCatching { file.writeBytes(downloaded) }
                    downloaded
                } else {
                    null
                }
            }
            bytes?.let { decodeSampled(it, maxPx) }
        }
        if (bitmap != null) mem.put(key, bitmap)
        return bitmap
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun download(url: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) SmsMessenger")
            setRequestProperty("Accept", "image/*")
        }
        return try {
            if (conn.responseCode !in 200..399) return null
            conn.inputStream.use { it.readBytesBounded(MAX_DOWNLOAD) }
        } finally {
            conn.disconnect()
        }
    }

    private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }
}

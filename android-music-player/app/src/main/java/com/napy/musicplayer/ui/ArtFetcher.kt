package com.napy.musicplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Dati per ricavare una locandina: brano (uri/percorso) e tag per la ricerca online. */
data class ArtKey(
    val uri: String, val path: String = "", val artist: String = "",
    val album: String = "", val title: String = ""
) {
    /** Brani dello stesso album condividono la stessa locandina in cache. */
    val cacheKey: String
        get() = if (album.isNotBlank()) "a:${artist.lowercase()}|${album.lowercase()}" else "u:$uri"
}

/**
 * Ordine di ricerca: cache su disco -> copertina incorporata/MediaStore -> immagine nella cartella
 * (cover.jpg, folder.jpg...) -> download da iTunes Search (poi salvata in cache).
 */
class ArtFetcher(private val ctx: Context, private val key: ArtKey) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val hash = sha1(key.cacheKey)
        val dir = File(ctx.filesDir, "covers").apply { mkdirs() }
        val cacheFile = File(dir, "$hash.jpg")

        var bmp: Bitmap? = null
        var src = DataSource.DISK
        if (cacheFile.exists()) bmp = BitmapFactory.decodeFile(cacheFile.path)
        if (bmp == null) bmp = embedded()
        if (bmp == null) bmp = folderImage()
        if (bmp == null) {
            bmp = online(dir, hash, cacheFile)
            src = DataSource.NETWORK
        }
        bmp?.let { DrawableResult(BitmapDrawable(ctx.resources, it), false, src) }
    }

    private fun embedded(): Bitmap? {
        if (key.uri.isEmpty()) return null
        val uri = Uri.parse(key.uri)
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(600, 600), null) }.getOrNull()?.let { return it }
        }
        return runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, uri)
                r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } finally { r.release() }
        }.getOrNull()
    }

    private fun folderImage(): Bitmap? {
        if (key.path.isEmpty()) return null
        val dir = File(key.path).parentFile ?: return null
        val names = listOf("cover", "folder", "front", "albumart", "album", "artwork", "thumb")
        val exts = listOf("jpg", "jpeg", "png", "webp")
        val files = runCatching { dir.listFiles() }.getOrNull() ?: return null
        for (n in names) for (f in files) {
            val low = f.name.lowercase()
            if (exts.any { low == "$n.$it" } || (low.startsWith("albumartsmall") && n == "albumart")) {
                decodeSampled(f.path)?.let { return it }
            }
        }
        return null
    }

    private fun decodeSampled(path: String): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 700) s *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()

    private suspend fun online(dir: File, hash: String, cacheFile: File): Bitmap? {
        val miss = File(dir, "$hash.none")
        if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < 7L * 24 * 3600 * 1000) return null

        val folderName = File(key.path).parentFile?.name ?: ""
        val (term, entity) = when {
            key.album.isNotBlank() -> "${key.artist} ${key.album}" to "album"
            key.artist.isNotBlank() && key.title.isNotBlank() -> "${key.artist} ${key.title}" to "song"
            folderName.isNotBlank() -> folderName to "album"
            else -> return null
        }
        return runCatching {
            gate.withPermit {
                val api = "https://itunes.apple.com/search?media=music&limit=5&entity=$entity&term=" +
                    URLEncoder.encode(term.trim(), "UTF-8")
                val results = JSONObject(httpGet(api).toString(Charsets.UTF_8)).getJSONArray("results")
                if (results.length() == 0) { miss.writeText("x"); return@withPermit null }
                var pick = results.getJSONObject(0)
                if (key.album.isNotBlank()) {
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        if (r.optString("collectionName").contains(key.album, ignoreCase = true)) { pick = r; break }
                    }
                }
                val art = pick.optString("artworkUrl100")
                if (art.isEmpty()) { miss.writeText("x"); return@withPermit null }
                val bytes = httpGet(art.replace("100x100bb", "600x600bb"))
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                bmp?.also { b -> cacheFile.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            }
        }.getOrNull() // errori di rete: nessun marcatore, si riprova alla prossima apertura
    }

    private fun httpGet(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "NardiTune/1.0")
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    class Factory(private val ctx: Context) : Fetcher.Factory<ArtKey> {
        override fun create(data: ArtKey, options: Options, imageLoader: ImageLoader): Fetcher = ArtFetcher(ctx, data)
    }

    private companion object { val gate = Semaphore(3) }
}

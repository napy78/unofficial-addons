package com.napy.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanResult(val added: Int, val removed: Int, val total: Int)

/**
 * Aggiorna il database locale leggendo MediaStore. E' incrementale: inserisce solo i brani
 * nuovi/modificati e rimuove quelli spariti, poi ricostruisce l'albero delle cartelle.
 */
class Scanner(private val ctx: Context, private val db: LibraryDb) {

    suspend fun scan(onProgress: (Int) -> Unit = {}): ScanResult = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val known = dao.stamps().associate { it.id to it.modified }
        val seen = HashSet<Long>()
        val batch = ArrayList<Track>()
        var added = 0

        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_MODIFIED
        )
        // niente filtro IS_MUSIC: includiamo anche audiolibri, registrazioni, podcast...
        ctx.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null, null
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                seen += id
                val mod = c.getLong(iMod)
                if (known[id] == mod) continue
                val path = c.getString(iData) ?: continue
                val slash = path.lastIndexOf('/')
                val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" } ?: ""
                batch += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    path = path,
                    folder = if (slash > 0) path.substring(0, slash) else "/",
                    fileName = c.getString(iName) ?: path.substring(slash + 1),
                    title = c.getString(iTitle) ?: path.substring(slash + 1).substringBeforeLast('.'),
                    artist = artist,
                    album = c.getString(iAlbum) ?: "",
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    mime = c.getString(iMime) ?: "",
                    size = c.getLong(iSize),
                    modified = mod
                )
                if (batch.size >= 500) {
                    added += batch.size
                    dao.upsertTracks(batch.toList()); batch.clear(); onProgress(added)
                }
            }
        }
        if (batch.isNotEmpty()) { added += batch.size; dao.upsertTracks(batch.toList()); onProgress(added) }

        val gone = known.keys.filter { it !in seen }
        gone.chunked(500).forEach { dao.deleteTracks(it) }

        rebuildFolders()
        ScanResult(added, gone.size, seen.size)
    }

    /** Costruisce l'albero: ogni cartella con brani + tutti i suoi antenati, con conteggio ricorsivo. */
    private suspend fun rebuildFolders() {
        val dao = db.dao()
        val counts = HashMap<String, Int>()
        for (fc in dao.folderCounts()) {
            var p: String? = fc.folder
            while (!p.isNullOrEmpty() && p != "/") {
                counts[p] = (counts[p] ?: 0) + fc.n
                p = p.substringBeforeLast('/', "")
            }
        }
        val folders = counts.map { (path, n) ->
            val parent = path.substringBeforeLast('/', "").let { if (it.isEmpty() || it == path) "" else it }
            Folder(path, parent, path.substringAfterLast('/'), n)
        }
        db.withTransaction {
            dao.clearFolders()
            folders.chunked(500).forEach { dao.insertFolders(it) }
        }
    }
}

package com.napy.musicplayer.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.napy.musicplayer.MusicApp
import com.napy.musicplayer.data.Excluded
import com.napy.musicplayer.data.Folder
import com.napy.musicplayer.data.Scanner
import com.napy.musicplayer.data.Track
import com.napy.musicplayer.playback.PlaybackService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlayerUi(
    val hasMedia: Boolean = false,
    val playing: Boolean = false,
    val mediaId: String = "",
    val uri: String = "",
    val path: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val format: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
)

sealed interface TreeNode { val key: String; val depth: Int }
data class FolderNode(val folder: Folder, override val depth: Int, val open: Boolean) : TreeNode {
    override val key get() = "f" + folder.path
}
data class TrackNode(val track: Track, override val depth: Int) : TreeNode {
    override val key get() = "t" + track.id
}

data class ScanUi(val running: Boolean = false, val message: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as MusicApp).db
    private val dao = db.dao()
    private val scanner = Scanner(app, db)
    private var controller: MediaController? = null

    // ---------- libreria ----------
    /** Cartelle aperte nell'albero. */
    val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val expandedTracks = expanded.flatMapLatest { dao.tracksInFolders(it.toList()) }

    /** Albero appiattito (solo righe visibili), pronto per una LazyColumn. */
    val tree: StateFlow<List<TreeNode>> =
        combine(dao.allFolders(), expanded, expandedTracks) { f, e, t -> buildTree(f, e, t) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val trackCount = dao.trackCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Emesso quando parte una riproduzione: la UI passa al player a tutto schermo. */
    val openPlayer = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun buildTree(folders: List<Folder>, open: Set<String>, tracks: List<Track>): List<TreeNode> {
        val kids = folders.groupBy { it.parent }.mapValues { (_, v) -> v.sortedBy { it.name.lowercase() } }
        val byFolder = tracks.groupBy { it.folder }
        // salta i livelli "di passaggio" (es. /storage/emulated/0) con una sola sottocartella e nessun brano
        var root = ""
        while (true) {
            val k = kids[root] ?: break
            if (k.size == 1 && k[0].ownCount == 0) root = k[0].path else break
        }
        val out = ArrayList<TreeNode>()
        fun walk(parent: String, depth: Int) {
            for (f in kids[parent].orEmpty()) {
                val isOpen = f.path in open
                out += FolderNode(f, depth, isOpen)
                if (isOpen) {
                    walk(f.path, depth + 1)
                    for (t in byFolder[f.path].orEmpty()) out += TrackNode(t, depth + 1)
                }
            }
        }
        walk(root, 0)
        return out
    }

    // ---------- cartelle escluse ----------
    val excluded = dao.excluded().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun exclude(path: String) {
        viewModelScope.launch {
            dao.exclude(Excluded(path))
            dao.deleteTracksUnder(path)
            expanded.update { e -> e.filterNot { it == path || it.startsWith("$path/") }.toSet() }
            scanner.rebuildFolders()
        }
    }

    fun restore(path: String) {
        viewModelScope.launch { dao.include(path); rescan() }
    }

    fun toggleFolder(path: String) { expanded.update { if (path in it) it - path else it + path } }
    fun collapseAll() { expanded.value = emptySet() }

    val query = MutableStateFlow("")
    val searchResults = query.debounce(150).flatMapLatest { dao.search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<Track>())

    private val _scan = MutableStateFlow(ScanUi())
    val scan = _scan.asStateFlow()

    // ---------- player ----------
    private val _ui = MutableStateFlow(PlayerUi())
    val ui = _ui.asStateFlow()

    init {
        connectPlayer()
        viewModelScope.launch {
            while (true) {
                delay(400)
                val c = controller ?: continue
                if (c.isPlaying) _ui.update { it.copy(positionMs = c.currentPosition) }
            }
        }
    }

    fun rescan() {
        if (_scan.value.running) return
        viewModelScope.launch {
            _scan.value = ScanUi(true, "Scansione...")
            val r = runCatching { scanner.scan { n -> _scan.value = ScanUi(true, "$n brani letti") } }
            r.onSuccess {
                _scan.value = ScanUi(false, "+${it.added}  -${it.removed}  (${it.total} totali)")
            }.onFailure { _scan.value = ScanUi(false, "Errore: ${it.message}") }
        }
    }

    // ---------- riproduzione ----------
    private fun connectPlayer() {
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = refresh()
            })
            refresh()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val md = item?.mediaMetadata
        _ui.value = PlayerUi(
            hasMedia = item != null,
            playing = c.isPlaying,
            mediaId = item?.mediaId ?: "",
            uri = item?.localConfiguration?.uri?.toString() ?: "",
            path = md?.extras?.getString("path") ?: "",
            title = md?.title?.toString() ?: "",
            artist = md?.artist?.toString() ?: "",
            album = md?.albumTitle?.toString() ?: "",
            format = md?.extras?.getString("fmt") ?: "",
            durationMs = if (c.duration == C.TIME_UNSET) 0 else c.duration,
            positionMs = c.currentPosition,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode
        )
    }

    private fun Track.toItem() = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.ifBlank { null })
                .setAlbumTitle(album.ifBlank { null })
                .setArtworkUri(android.net.Uri.parse("content://media/external/audio/albumart/$albumId"))
                .setExtras(Bundle().apply {
                    putString("fmt", fileName.substringAfterLast('.', "").uppercase())
                    putString("path", path)
                    putString("folder", folder)
                })
                .build()
        ).build()

    fun play(tracks: List<Track>, index: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(tracks.map { it.toItem() }, if (shuffle) (tracks.indices).random() else index, 0L)
        c.prepare()
        c.play()
        openPlayer.tryEmit(Unit)
    }

    /**
     * Tocco su un brano nell'albero: la coda contiene tutta la cartella "genitore" (se esiste nell'albero),
     * cosi' i pulsanti cartella successiva/precedente passano alle cartelle sorelle.
     */
    fun playFromTree(t: Track) {
        viewModelScope.launch {
            val parent = t.folder.substringBeforeLast('/', "")
            val hasParent = tree.value.any { it is FolderNode && it.folder.path == parent }
            val queue = if (hasParent) dao.tracksRecursive(parent) else dao.tracksRecursive(t.folder)
            play(queue, queue.indexOfFirst { it.id == t.id }.coerceAtLeast(0))
        }
    }

    fun playFolder(path: String, shuffle: Boolean) {
        viewModelScope.launch { play(dao.tracksRecursive(path), 0, shuffle) }
    }

    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() {
        controller?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms); _ui.update { it.copy(positionMs = ms) } }

    private fun folderOf(c: MediaController, i: Int) =
        c.getMediaItemAt(i).mediaMetadata.extras?.getString("folder") ?: ""

    /** Salta al primo brano della cartella successiva nella coda. */
    fun nextFolder() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        val f = folderOf(c, cur)
        for (i in cur + 1 until c.mediaItemCount) if (folderOf(c, i) != f) { c.seekTo(i, 0); return }
    }

    /** Salta al primo brano della cartella precedente nella coda. */
    fun prevFolder() {
        val c = controller ?: return
        var start = c.currentMediaItemIndex
        val f = folderOf(c, start)
        while (start > 0 && folderOf(c, start - 1) == f) start--
        if (start == 0) { c.seekTo(0, 0); return }
        val pf = folderOf(c, start - 1)
        var ps = start - 1
        while (ps > 0 && folderOf(c, ps - 1) == pf) ps--
        c.seekTo(ps, 0)
    }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() { controller?.release(); super.onCleared() }
}

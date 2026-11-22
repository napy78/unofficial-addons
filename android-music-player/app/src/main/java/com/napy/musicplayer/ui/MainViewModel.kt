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
import com.napy.musicplayer.data.Folder
import com.napy.musicplayer.data.Scanner
import com.napy.musicplayer.data.Track
import com.napy.musicplayer.playback.PlaybackService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlayerUi(
    val hasMedia: Boolean = false,
    val playing: Boolean = false,
    val mediaId: String = "",
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val format: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
)

data class ScanUi(val running: Boolean = false, val message: String = "")

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as MusicApp).db
    private val dao = db.dao()
    private val scanner = Scanner(app, db)
    private var controller: MediaController? = null

    // ---------- libreria ----------
    private val _stack = MutableStateFlow(listOf(""))
    val stack = _stack.asStateFlow()
    val current = _stack.map { it.last() }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val folders = current.flatMapLatest { dao.folders(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<Folder>())
    val folderTracks = current.flatMapLatest { dao.tracksIn(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<Track>())
    val trackCount = dao.trackCount().stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val query = MutableStateFlow("")
    val searchResults = query.debounce(150).flatMapLatest { dao.search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList<Track>())

    private val _scan = MutableStateFlow(ScanUi())
    val scan = _scan.asStateFlow()

    // ---------- player ----------
    private val _ui = MutableStateFlow(PlayerUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch { resetRoot() }
        connectPlayer()
        viewModelScope.launch {
            while (true) {
                delay(400)
                val c = controller ?: continue
                if (c.isPlaying) _ui.update { it.copy(positionMs = c.currentPosition) }
            }
        }
    }

    /** Scende finche' c'e' una sola sottocartella e nessun brano: salta /storage/emulated/0. */
    private suspend fun resetRoot() {
        var p = ""
        while (true) {
            val kids = dao.foldersOnce(p)
            if (kids.size == 1 && dao.countIn(p) == 0) p = kids[0].path else break
        }
        _stack.value = listOf(p)
    }

    fun openFolder(path: String) { _stack.update { it + path } }
    fun goUp(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.update { it.dropLast(1) }
        return true
    }

    fun rescan() {
        if (_scan.value.running) return
        viewModelScope.launch {
            _scan.value = ScanUi(true, "Scansione...")
            val r = runCatching { scanner.scan { n -> _scan.value = ScanUi(true, "$n brani letti") } }
            r.onSuccess {
                _scan.value = ScanUi(false, "+${it.added}  -${it.removed}  (${it.total} totali)")
                if (_stack.value.size <= 1) resetRoot()
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
    fun skipBy(ms: Long) { controller?.let { seekTo((it.currentPosition + ms).coerceIn(0, it.duration.coerceAtLeast(0))) } }
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

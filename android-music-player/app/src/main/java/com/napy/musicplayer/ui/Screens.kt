package com.napy.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.napy.musicplayer.data.Track

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ------------------------------------------------------------------ copertina
@Composable
fun Cover(uri: String, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(Surface2, Surface1))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = TextDim, modifier = Modifier.size(size * 0.5f))
        if (uri.isNotEmpty()) AsyncImage(ArtKey(uri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Mini equalizzatore animato accanto al brano in riproduzione. */
@Composable
fun EqBars(animate: Boolean, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(420, 560, 340).forEach { d ->
            val h by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d, easing = LinearEasing), RepeatMode.Reverse), label = "b$d")
            Box(Modifier.width(3.dp).fillMaxHeight(if (animate) h else 0.3f).clip(RoundedCornerShape(2.dp)).background(AccentBrush))
        }
    }
}

// ------------------------------------------------------------------ app
@Composable
fun AppScreen(vm: MainViewModel, granted: Boolean, askPermission: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showPlayer by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val stack by vm.stack.collectAsStateWithLifecycle()

    BackHandler(showPlayer) { showPlayer = false }
    BackHandler(!showPlayer && tab == 0 && stack.size > 1) { vm.goUp() }

    Box(Modifier.fillMaxSize().background(BgBrush)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(vm)
            if (!granted) {
                PermissionCard(askPermission)
            } else {
                TabBar(tab) { tab = it }
                Box(Modifier.weight(1f)) {
                    if (tab == 0) FolderBrowser(vm, ui) else AllTracks(vm, ui)
                }
            }
            if (ui.hasMedia) MiniPlayer(vm, ui) { showPlayer = true }
            Spacer(Modifier.navigationBarsPadding())
        }
        AnimatedVisibility(
            showPlayer && ui.hasMedia,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) { NowPlaying(vm, ui) { showPlayer = false } }
    }
}

@Composable
fun Header(vm: MainViewModel) {
    val count by vm.trackCount.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Napy Player",
                style = TextStyle(brush = AccentBrush, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            )
            Text(
                if (scan.message.isNotEmpty()) scan.message else "$count brani in libreria",
                color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = vm::rescan, modifier = Modifier.size(52.dp).clip(CircleShape).background(Surface1)) {
            Icon(Icons.Rounded.Refresh, "Aggiorna libreria", tint = Cyan, modifier = Modifier.rotate(if (scan.running) angle else 0f))
        }
    }
    if (scan.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(CircleShape), color = Pink, trackColor = Surface1)
}

@Composable
fun PermissionCard(ask: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = Violet, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Serve l'accesso ai file audio per creare la libreria.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = TextDim)
        Spacer(Modifier.height(16.dp))
        Button(ask, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Consenti accesso") }
    }
}

@Composable
fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp)).background(Surface1).padding(4.dp)
    ) {
        listOf("Cartelle" to Icons.Rounded.Folder, "Tutti i brani" to Icons.Rounded.QueueMusic).forEachIndexed { i, (label, icon) ->
            val on = i == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .background(if (on) AccentBrush else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (on) Color.White else TextDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = if (on) Color.White else TextDim, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ------------------------------------------------------------------ cartelle
@Composable
fun FolderBrowser(vm: MainViewModel, ui: PlayerUi) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tracks by vm.folderTracks.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val stack by vm.stack.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (stack.size > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.goUp() }) { Icon(Icons.Rounded.ArrowBack, "Su", tint = Color.White) }
                Text(current.substringAfterLast('/'), fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        if (folders.isNotEmpty() || tracks.isNotEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Riproduci tutto", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { vm.playFolder(current, false) }
                PillButton("Casuale", Icons.Rounded.Shuffle, Modifier.weight(1f)) { vm.playFolder(current, true) }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(folders, key = { "f" + it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(Surface1)
                        .clickable { vm.openFolder(f.path) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(AccentBrush), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Folder, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${f.trackCount} brani", color = TextDim, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = TextDim)
                }
            }
            items(tracks, key = { "t" + it.id }) { t ->
                TrackRow(t, ui) { vm.play(tracks, tracks.indexOf(t)) }
            }
        }
    }
}

@Composable
fun PillButton(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(CircleShape).background(Surface2).clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Cyan); Spacer(Modifier.width(6.dp)); Text(label, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------------ brani
@Composable
fun AllTracks(vm: MainViewModel, ui: PlayerUi) {
    val q by vm.query.collectAsStateWithLifecycle()
    val list by vm.searchResults.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TextField(
            q, { vm.query.value = it }, singleLine = true,
            placeholder = { Text("Cerca titolo, artista, album, file...", color = TextDim) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextDim) },
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(list, key = { it.id }) { t -> TrackRow(t, ui) { vm.play(list, list.indexOf(t)) } }
        }
    }
}

@Composable
fun TrackRow(t: Track, ui: PlayerUi, onClick: () -> Unit) {
    val active = ui.mediaId == t.id.toString()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (active) Surface2 else Surface1).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.uri, 48.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Cyan else Color.White)
            Text(
                listOf(t.artist, t.fileName.substringAfterLast('.', "").uppercase(), fmt(t.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (active) EqBars(ui.playing)
    }
}

// ------------------------------------------------------------------ mini player
@Composable
fun MiniPlayer(vm: MainViewModel, ui: PlayerUi, open: () -> Unit) {
    val progress = if (ui.durationMs > 0) (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Violet, spotColor = Pink)
            .clip(RoundedCornerShape(24.dp)).background(Surface2).clickable(onClick = open)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(ui.uri, 52.dp, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.artist.ifBlank { ui.album }, color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BigIcon(Icons.Rounded.SkipPrevious, "Precedente", 52.dp, 32.dp) { vm.prev() }
            Box(Modifier.size(56.dp).clip(CircleShape).background(AccentBrush).clickable { vm.toggle() }, contentAlignment = Alignment.Center) {
                Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            BigIcon(Icons.Rounded.SkipNext, "Successivo", 52.dp, 32.dp) { vm.next() }
        }
        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(3.dp), color = Pink, trackColor = Color.Transparent)
    }
}

@Composable
fun BigIcon(icon: ImageVector, desc: String, box: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, tint: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(box).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// ------------------------------------------------------------------ now playing
@Composable
fun NowPlaying(vm: MainViewModel, ui: PlayerUi, close: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val pos = drag ?: ui.positionMs.toFloat()
    val dur = ui.durationMs.coerceAtLeast(1).toFloat()
    // alone che pulsa dietro la locandina mentre suona
    val glow by rememberInfiniteTransition(label = "g").animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")

    Column(
        Modifier.fillMaxSize().background(BgBrush).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BigIcon(Icons.Rounded.KeyboardArrowDown, "Chiudi", 52.dp, 36.dp, onClick = close)
            Spacer(Modifier.weight(1f))
            if (ui.format.isNotBlank()) {
                Text(ui.format, Modifier.clip(CircleShape).background(Surface2).padding(horizontal = 14.dp, vertical = 6.dp), color = Cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // locandina PICCOLA + titoli
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).alpha(if (ui.playing) glow else 0.25f).background(Brush.radialGradient(listOf(Pink, Color.Transparent)), CircleShape))
                Cover(ui.uri, 96.dp, 18.dp, Modifier.shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Violet))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(ui.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (ui.artist.isNotBlank()) Text(ui.artist, color = Pink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ui.album.isNotBlank()) Text(ui.album, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.weight(1f))

        // barra di avanzamento
        Slider(
            value = pos.coerceIn(0f, dur), onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
            valueRange = 0f..dur,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pink, inactiveTrackColor = Surface2)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos.toLong()), color = TextDim); Text(fmt(ui.durationMs), color = TextDim)
        }

        Spacer(Modifier.height(24.dp))

        // pulsanti GRANDI
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            BigIcon(Icons.Rounded.SkipPrevious, "Precedente", 88.dp, 64.dp) { vm.prev() }
            Box(
                Modifier.size(128.dp).shadow(24.dp, CircleShape, ambientColor = Pink, spotColor = Violet)
                    .clip(CircleShape).background(AccentBrush).clickable { vm.toggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pausa", tint = Color.White, modifier = Modifier.size(84.dp))
            }
            BigIcon(Icons.Rounded.SkipNext, "Successivo", 88.dp, 64.dp) { vm.next() }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            BigIcon(Icons.Rounded.Shuffle, "Casuale", 72.dp, 40.dp, if (ui.shuffle) Cyan else TextDim) { vm.toggleShuffle() }
            BigIcon(Icons.Rounded.Replay10, "-10s", 72.dp, 44.dp) { vm.skipBy(-10_000) }
            BigIcon(Icons.Rounded.Forward10, "+10s", 72.dp, 44.dp) { vm.skipBy(10_000) }
            BigIcon(
                if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Ripeti", 72.dp, 40.dp, if (ui.repeat == Player.REPEAT_MODE_OFF) TextDim else Cyan
            ) { vm.cycleRepeat() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

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
import com.napy.musicplayer.data.Folder
import com.napy.musicplayer.data.Track

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

// ------------------------------------------------------------------ copertina
fun Track.art() = ArtKey(uri, path, artist, album, title)
fun Folder.art() = ArtKey(artUri, artPath, artArtist, artAlbum, artTitle)
fun PlayerUi.art() = ArtKey(uri, path, artist, album, title)

@Composable
fun Cover(
    key: ArtKey?, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp = 12.dp,
    modifier: Modifier = Modifier, placeholder: ImageVector = Icons.Rounded.MusicNote
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(Brush.linearGradient(listOf(Surface2, Surface1)))
            .border(1.dp, Border, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(placeholder, null, tint = TextDim, modifier = Modifier.size(size * 0.5f))
        if (key != null && key.uri.isNotEmpty()) {
            AsyncImage(key, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
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
    LaunchedEffect(Unit) { vm.openPlayer.collect { showPlayer = true } }
    BackHandler(showPlayer) { showPlayer = false }

    Box(Modifier.fillMaxSize().background(BgBrush)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(vm)
            if (!granted) {
                PermissionCard(askPermission)
            } else {
                TabBar(tab) { tab = it }
                Box(Modifier.weight(1f)) {
                    if (tab == 0) TreeBrowser(vm, ui) else AllTracks(vm, ui)
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
    val excluded by vm.excluded.collectAsStateWithLifecycle()
    var showEx by remember { mutableStateOf(false) }
    if (showEx) ExcludedDialog(vm, excluded.map { it.path }) { showEx = false }
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
        IconButton(onClick = { showEx = true }, modifier = Modifier.size(52.dp).clip(CircleShape).background(Surface1)) {
            BadgedBox(badge = { if (excluded.isNotEmpty()) Badge { Text("${excluded.size}") } }) {
                Icon(Icons.Rounded.VisibilityOff, "Cartelle escluse", tint = Color.White)
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = vm::rescan, modifier = Modifier.size(52.dp).clip(CircleShape).background(Surface1)) {
            Icon(Icons.Rounded.Refresh, "Aggiorna libreria", tint = Cyan, modifier = Modifier.rotate(if (scan.running) angle else 0f))
        }
    }
    if (scan.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(CircleShape), color = Pink, trackColor = Surface1)
}

@Composable
fun ExcludedDialog(vm: MainViewModel, paths: List<String>, close: () -> Unit) {
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Cartelle escluse") },
        text = {
            if (paths.isEmpty()) Text("Nessuna cartella esclusa. Usa il menu \u22EE su una cartella per escluderla dalla scansione.")
            else LazyColumn {
                items(paths, key = { it }) { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.substringAfterLast('/'), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(p, fontSize = 11.sp, color = TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton({ vm.restore(p) }) { Text("Ripristina", color = Cyan) }
                    }
                }
            }
        },
        confirmButton = { TextButton(close) { Text("Chiudi") } }
    )
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
        listOf("Cartelle" to Icons.Rounded.AccountTree, "Cerca" to Icons.Rounded.Search).forEachIndexed { i, (label, icon) ->
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

// ------------------------------------------------------------------ albero cartelle
private val INDENT = 22.dp

@Composable
fun TreeBrowser(vm: MainViewModel, ui: PlayerUi) {
    val rows by vm.tree.collectAsStateWithLifecycle()
    val open by vm.expanded.collectAsStateWithLifecycle()
    var toExclude by remember { mutableStateOf<Folder?>(null) }
    toExclude?.let { f ->
        AlertDialog(
            onDismissRequest = { toExclude = null },
            title = { Text("Escludere \"${f.name}\"?") },
            text = { Text("La cartella e le sue sottocartelle (${f.trackCount} brani) verranno tolte dall'archivio e non saranno piu' scansionate. I file non vengono cancellati. Puoi ripristinarla dall'icona occhio in alto.") },
            confirmButton = { TextButton({ vm.exclude(f.path); toExclude = null }) { Text("Escludi", color = Pink) } },
            dismissButton = { TextButton({ toExclude = null }) { Text("Annulla") } }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tocca una cartella per aprirla", color = TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (open.isNotEmpty()) {
                Text(
                    "Comprimi tutto", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable { vm.collapseAll() }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun brano trovato.\nPremi il pulsante di aggiornamento in alto.", color = TextDim, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            items(rows, key = { it.key }) { node ->
                when (node) {
                    is FolderNode -> FolderRow(node, onToggle = { vm.toggleFolder(node.folder.path) }, onPlay = { vm.playFolder(node.folder.path, false) }, onExclude = { toExclude = node.folder })
                    is TrackNode -> TreeTrackRow(node, ui) { vm.playFromTree(node.track) }
                }
            }
        }
    }
}

/** Disegna le linee verticali guida dell'albero, una per ogni livello di profondita'. */
private fun Modifier.treeGuides(depth: Int) = drawBehind {
    val step = INDENT.toPx()
    for (d in 0 until depth) {
        val x = step * d + step / 2
        drawLine(Border, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 2.dp.toPx())
    }
}

@Composable
fun FolderRow(n: FolderNode, onToggle: () -> Unit, onPlay: () -> Unit, onExclude: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rot by animateFloatAsState(if (n.open) 90f else 0f, label = "chev")
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().treeGuides(n.depth).padding(start = INDENT * n.depth, top = 3.dp, bottom = 3.dp)) {
        Row(
            Modifier.weight(1f).clip(shape).background(if (n.open) Surface2 else Surface1)
                .border(1.dp, if (n.open) Cyan else Border, shape)
                .clickable(onClick = onToggle).padding(start = 6.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.ChevronRight, if (n.open) "Chiudi" else "Apri", tint = Color.White, modifier = Modifier.size(30.dp).rotate(rot))
            Cover(n.folder.art(), 54.dp, 12.dp, placeholder = Icons.Rounded.Folder)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.folder.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${n.folder.trackCount} brani", color = TextDim, fontSize = 13.sp)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(AccentBrush).clickable(onClick = onPlay), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Riproduci cartella", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Altro", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Escludi dalla libreria") },
                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                        onClick = { menu = false; onExclude() }
                    )
                }
            }
        }
    }
}

@Composable
fun TreeTrackRow(n: TrackNode, ui: PlayerUi, onClick: () -> Unit) {
    val t = n.track
    val active = ui.mediaId == t.id.toString()
    val shape = RoundedCornerShape(14.dp)
    Row(Modifier.fillMaxWidth().treeGuides(n.depth).padding(start = INDENT * n.depth, top = 2.dp, bottom = 2.dp)) {
        Row(
            Modifier.weight(1f).clip(shape).background(if (active) Surface2 else Bg.copy(alpha = 0.6f))
                .border(1.dp, if (active) Cyan else Border.copy(alpha = 0.6f), shape)
                .clickable(onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Cover(t.art(), 44.dp, 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Cyan else Color.White)
                Text(
                    listOf(t.artist, t.fileName.substringAfterLast('.', "").uppercase(), fmt(t.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                    color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (active) EqBars(ui.playing)
        }
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
            textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
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
            .background(if (active) Surface2 else Surface1).border(1.dp, if (active) Cyan else Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.art(), 48.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Cyan else Color.White)
            Text(
                listOf(t.artist, t.fileName.substringAfterLast('.', "").uppercase(), fmt(t.durationMs)).filter { it.isNotBlank() }.joinToString(" · "),
                color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
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
            Cover(ui.art(), 52.dp, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.artist.ifBlank { ui.album }, color = TextDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun FolderSkip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Surface2).border(1.dp, Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label.replace("\n", " "), tint = Cyan, modifier = Modifier.size(40.dp))
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
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

        // locandina PICCOLA affiancata ad album e formato
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(108.dp).alpha(if (ui.playing) glow else 0.25f).background(Brush.radialGradient(listOf(Pink, Color.Transparent)), CircleShape))
                Cover(ui.art(), 88.dp, 16.dp, Modifier.shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Violet))
            }
            Spacer(Modifier.width(16.dp))
            if (ui.album.isNotBlank()) Text(ui.album, color = TextDim, fontSize = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }

        // TITOLO e AUTORE grandi
        Spacer(Modifier.height(16.dp))
        Text(ui.title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (ui.artist.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(ui.artist, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = Pink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        // analizzatore di spettro in tempo reale
        SpectrumView(ui.playing, Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp))

        // barra di avanzamento
        Slider(
            value = pos.coerceIn(0f, dur), onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
            valueRange = 0f..dur,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Pink, inactiveTrackColor = Border)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos.toLong()), color = Color.White, fontSize = 16.sp); Text(fmt(ui.durationMs), color = Color.White, fontSize = 16.sp)
        }

        Spacer(Modifier.height(12.dp))

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
            BigIcon(Icons.Rounded.Shuffle, "Casuale", 64.dp, 38.dp, if (ui.shuffle) Cyan else TextDim) { vm.toggleShuffle() }
            FolderSkip(Icons.Rounded.FastRewind, "Cartella\nprecedente") { vm.prevFolder() }
            FolderSkip(Icons.Rounded.FastForward, "Cartella\nsuccessiva") { vm.nextFolder() }
            BigIcon(
                if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Ripeti", 64.dp, 38.dp, if (ui.repeat == Player.REPEAT_MODE_OFF) TextDim else Cyan
            ) { vm.cycleRepeat() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

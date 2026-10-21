package app.preciseplayer.ui.home

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.preciseplayer.Prefs
import app.preciseplayer.Recents
import app.preciseplayer.ui.Format
import app.preciseplayer.ui.player.PlayerActivity
import app.preciseplayer.ui.settings.SettingsActivity
import app.preciseplayer.ui.theme.PreciseTheme
import app.preciseplayer.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PreciseTheme { HomeScreen() } }
    }
}

data class Video(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val size: Long,
    val folder: String,
    val width: Int,
    val height: Int,
)

private val thumbCache = LruCache<Uri, ImageBitmap>(160)

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** FULL, PARTIAL (Android 14 "selected videos only") or NONE. */
private enum class Access { FULL, PARTIAL, NONE }

private fun access(ctx: android.content.Context): Access {
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 33 && has(Manifest.permission.READ_MEDIA_VIDEO) -> Access.FULL
        Build.VERSION.SDK_INT >= 34 && has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
        Build.VERSION.SDK_INT < 33 && has(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
        else -> Access.NONE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(if (Recents.items.isEmpty()) 1 else 0) }
    var urlOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            PlayerActivity.open(ctx, uri)
        }
    }
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Precise Player") },
                    actions = {
                        IconButton(onClick = { urlOpen = true }) { Icon(Icons.Rounded.Link, "Open URL") }
                        IconButton(onClick = { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }) { Icon(Icons.Rounded.Settings, "Settings") }
                    },
                    scrollBehavior = scroll,
                    colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Recent") }, icon = { Icon(Icons.Rounded.History, null) })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Videos") }, icon = { Icon(Icons.Rounded.VideoLibrary, null) })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("video/*", "audio/*", "application/x-matroska", "application/octet-stream")) },
                icon = { Icon(Icons.Rounded.FolderOpen, null) },
                text = { Text("Open file") },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) RecentList() else VideoLibrary()
        }
    }
    if (urlOpen) UrlDialog(onDismiss = { urlOpen = false }) { url -> urlOpen = false; PlayerActivity.open(ctx, Uri.parse(url)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentList() {
    val ctx = LocalContext.current
    val items = Recents.items
    if (items.isEmpty()) {
        EmptyState(Icons.Rounded.History, "Nothing played yet", "Videos you open show up here, with where you left off.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.uri }) { item ->
            val uri = Uri.parse(item.uri)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .combinedClickable(onClick = { PlayerActivity.open(ctx, uri) })
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(uri, Modifier.width(120.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val dur = item.duration
                    Text(
                        if (dur > 0) Format.time(item.position, false) + " / " + Format.time(dur, false) else uri.scheme ?: "",
                        style = Tabular.merge(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (dur > 0 && item.position > 0) {
                        LinearProgressIndicator(
                            progress = { (item.position / dur).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { Recents.remove(item.uri) }) { Icon(Icons.Rounded.Close, "Remove from recent", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            TextButton(onClick = { Recents.clear() }, modifier = Modifier.fillMaxWidth()) { Text("Clear history") }
        }
    }
}

@Composable
private fun VideoLibrary() {
    val ctx = LocalContext.current
    var acc by remember { mutableStateOf(access(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        acc = access(ctx); refresh++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { val a = access(ctx); if (a != acc) { acc = a; refresh++ } }

    if (acc == Access.NONE) {
        EmptyState(Icons.Rounded.VideoLibrary, "See the videos on this phone",
            "Allow access to your videos to browse them here. You can always open single files with “Open file” instead.") {
            FilledTonalButton(onClick = { launcher.launch(mediaPermissions()) }) { Text("Allow access") }
        }
        return
    }

    val videos by produceState<List<Video>?>(null, refresh) { value = withContext(Dispatchers.IO) { queryVideos(ctx) } }
    val list = videos
    if (list == null) return
    if (list.isEmpty()) {
        EmptyState(Icons.Rounded.Movie, "No videos found", if (acc == Access.PARTIAL) "You've shared only some videos with the app." else "Videos saved on this phone will show up here.") {
            if (acc == Access.PARTIAL) FilledTonalButton(onClick = { launcher.launch(mediaPermissions()) }) { Text("Choose videos") }
        }
        return
    }
    val folders = remember(list) { list.groupingBy { it.folder }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    var folder by remember { mutableStateOf(Prefs.folderFilter.value.takeIf { it in folders } ?: "") }
    val shown = remember(list, folder) { if (folder.isEmpty()) list else list.filter { it.folder == folder } }

    Column {
        if (acc == Access.PARTIAL) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Showing only the videos you picked", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { launcher.launch(mediaPermissions()) }) { Text("Change") }
            }
        }
        if (folders.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(folder.isEmpty(), { folder = ""; Prefs.folderFilter.value = "" }, label = { Text("All · ${list.size}") }) }
                items(folders) { f ->
                    FilterChip(folder == f, { folder = f; Prefs.folderFilter.value = f }, label = { Text(f.ifBlank { "Other" }) })
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown, key = { it.uri.toString() }) { v ->
                Column(Modifier.clip(RoundedCornerShape(14.dp)).combinedClickableCompat { PlayerActivity.open(ctx, v.uri) }) {
                    Box {
                        Thumb(v.uri, Modifier.fillMaxWidth())
                        Text(
                            Format.time(v.durationMs / 1000.0, false), style = Tabular.merge(MaterialTheme.typography.labelSmall), color = Color.White,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                        val progress = Recents.get(v.uri.toString())?.let { r -> if (r.duration > 0) (r.position / r.duration).toFloat() else 0f } ?: 0f
                        if (progress > 0.01f) {
                            LinearProgressIndicator(
                                progress = { progress }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                                trackColor = Color.Black.copy(alpha = 0.4f),
                            )
                        }
                    }
                    Text(v.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
                    Text(
                        listOfNotNull(if (v.height > 0) "${v.height}p" else null, Format.size(v.size)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = this.combinedClickable(onClick = onClick)

@Composable
private fun Thumb(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState(thumbCache.get(uri), uri) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.loadThumbnail(uri, Size(384, 216), null).asImageBitmap().also { thumbCache.put(uri, it) }
                } catch (_: Exception) { null }
            }
        }
    }
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val valid = text.trim().let { Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.+").matches(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open URL") },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onOpen(text.trim()) }, enabled = valid) { Text("Play") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun queryVideos(ctx: android.content.Context): List<Video> {
    val out = ArrayList<Video>()
    val proj = arrayOf(
        MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT,
    )
    try {
        ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(proj[0]); val name = c.getColumnIndexOrThrow(proj[1])
            val dur = c.getColumnIndexOrThrow(proj[2]); val size = c.getColumnIndexOrThrow(proj[3])
            val bucket = c.getColumnIndexOrThrow(proj[4]); val w = c.getColumnIndexOrThrow(proj[5]); val h = c.getColumnIndexOrThrow(proj[6])
            while (c.moveToNext()) {
                out += Video(
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(id)),
                    c.getString(name) ?: "Video", c.getLong(dur), c.getLong(size), c.getString(bucket) ?: "",
                    c.getInt(w), c.getInt(h),
                )
            }
        }
    } catch (_: Exception) { }
    return out
}

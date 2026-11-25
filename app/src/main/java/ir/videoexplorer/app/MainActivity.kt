package ir.videoexplorer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: FolderStore
    private var refresh: (() -> Unit)? = null

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                store.addFolder(uri)
                refresh?.invoke()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FolderStore(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                VideoExplorerScreen(
                    store = store,
                    onAddFolder = { folderPicker.launch(null) },
                    registerRefresh = { callback -> refresh = callback }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh?.invoke()
    }
}

@Composable
private fun VideoExplorerScreen(
    store: FolderStore,
    onAddFolder: () -> Unit,
    registerRefresh: ((() -> Unit) -> Unit)
) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var folders by remember { mutableStateOf(store.folders()) }
    var showFolders by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val reload: () -> Unit = {
        if (!scanning) {
            scanning = true
            scope.launch {
                videos = store.scan()
                folders = store.folders()
                scanning = false
            }
        }
    }

    SideEffect { registerRefresh(reload) }
    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (videos.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (scanning) "در حال بررسی ویدیوها…" else "ویدیویی پیدا نشد",
                    color = Color.White
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAddFolder) { Text("انتخاب پوشه") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = reload) { Text("بررسی دوباره", color = Color.White) }
            }
        } else {
            VideoFeed(videos = videos)
        }

        Row(
            Modifier.fillMaxWidth().align(Alignment.TopEnd).padding(12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { showFolders = true }) {
                Text("پوشه‌ها", color = Color.White)
            }
            TextButton(onClick = reload) {
                Text("↻", color = Color.White)
            }
            TextButton(onClick = onAddFolder) {
                Text("+ پوشه", color = Color.White)
            }
        }
    }

    if (showFolders) {
        AlertDialog(
            onDismissRequest = { showFolders = false },
            title = { Text("پوشه‌های انتخاب‌شده") },
            text = {
                if (folders.isEmpty()) {
                    Text("پوشه‌ای انتخاب نشده است")
                } else {
                    LazyColumn {
                        items(folders, key = { it.toString() }) { uri ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    uri.lastPathSegment ?: "پوشه",
                                    Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    store.removeFolder(uri)
                                    folders = store.folders()
                                    reload()
                                }) { Text("حذف") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolders = false }) { Text("بستن") }
            },
            dismissButton = {
                TextButton(onClick = onAddFolder) { Text("افزودن") }
            }
        )
    }
}

@Composable
private fun VideoFeed(videos: List<VideoItem>) {
    val pagerState = rememberPagerState(pageCount = { videos.size })
    val context = LocalContext.current
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    LaunchedEffect(pagerState.currentPage, videos) {
        val video = videos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(video.uri))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .pointerInput(page) {
                        detectTapGestures(
                            onLongPress = { player.pause() },
                            onTap = {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        )
                    }
            ) {
                if (page == pagerState.currentPage) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                            }
                        },
                        update = { view ->
                            view.player = player
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

package ir.videoexplorer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var store: FolderStore
    private var refresh: (() -> Unit)? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
                    onAddFolder = { folderPicker.launch(null) }
                ) { callback -> refresh = callback }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoExplorerScreen(
    store: FolderStore,
    onAddFolder: () -> Unit,
    registerRefresh: ((() -> Unit) -> Unit)
) {
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var selected by remember { mutableIntStateOf(-1) }
    var tab by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf(store.folders()) }
    val scope = rememberCoroutineScope()

    val reload: () -> Unit = {
        scope.launch {
            videos = store.scan()
            folders = store.folders()
            if (selected >= videos.size) selected = -1
        }
    }
    SideEffect { registerRefresh(reload) }
    LaunchedEffect(Unit) { reload() }

    if (selected >= 0 && videos.isNotEmpty()) {
        VideoFeed(videos = videos, startIndex = selected, onBack = { selected = -1 })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Video Explorer") },
                    actions = {
                        TextButton(onClick = { tab = !tab }) {
                            Text(if (tab) "نمایش ویدیوها" else "پوشه‌ها")
                        }
                        TextButton(onClick = onAddFolder) { Text("+ پوشه") }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = reload) { Text("↻") }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (tab) {
                    if (folders.isEmpty()) {
                        EmptyState("هنوز پوشه‌ای انتخاب نشده است")
                    } else {
                        folders.forEach { uri ->
                            ListItem(
                                headlineContent = { Text(Uri.parse(uri.toString()).lastPathSegment ?: "پوشه") },
                                supportingContent = { Text(uri.toString(), maxLines = 1) },
                                trailingContent = {
                                    TextButton(onClick = {
                                        store.removeFolder(uri)
                                        reload()
                                    }) { Text("حذف") }
                                }
                            )
                        }
                    }
                } else if (videos.isEmpty()) {
                    EmptyState("ویدیویی پیدا نشد؛ از + پوشه، یک یا چند پوشه انتخاب کن.")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(3.dp)
                    ) {
                        itemsIndexed(videos, key = { _, item -> item.uri.toString() }) { index, video ->
                            Box(
                                Modifier.aspectRatio(0.72f).padding(2.dp)
                                    .background(Color.DarkGray)
                                    .clickable { selected = index },
                                contentAlignment = Alignment.BottomStart
                            ) {
                                AsyncImage(
                                    model = video.uri,
                                    contentDescription = video.name,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    video.name,
                                    color = Color.White,
                                    maxLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                        .background(Color.Black.copy(alpha = .55f)).padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Color.LightGray)
    }
}

@Composable
private fun VideoFeed(videos: List<VideoItem>, startIndex: Int, onBack: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex) { videos.size }
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = ExoPlayer.REPEAT_MODE_ONE }
    }

    LaunchedEffect(pagerState.currentPage, videos) {
        videos.getOrNull(pagerState.currentPage)?.let {
            player.setMediaItem(MediaItem.fromUri(it.uri))
            player.prepare()
            player.playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    videos[page].name,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }
        }
        TextButton(onClick = {
            player.pause()
            onBack()
        }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("بازگشت", color = Color.White)
        }
    }
}

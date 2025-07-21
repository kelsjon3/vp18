package com.vp18.mediaplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.ImageLoader
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: MediaViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: () -> Unit,
    onNavigateToCreator: (String) -> Unit = {},
    onNavigateToSearch: () -> Unit = {}
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val queuedItems by viewModel.queuedItems.collectAsState()
    val isInQueueMode by viewModel.isInQueueMode.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val currentSourceIndex by viewModel.currentSourceIndex.collectAsState()
    
    val currentItems = if (isInQueueMode) queuedItems else mediaItems
    val currentIndex by viewModel.currentMediaIndex.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    val currentSource = sources.getOrNull(currentSourceIndex)
    
    val pagerState = rememberPagerState(
        initialPage = currentIndex,
        pageCount = { currentItems.size }
    )
    
    LaunchedEffect(currentIndex) {
        if (currentIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(currentIndex)
        }
    }
    
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.setCurrentMediaIndex(page)
        }
    }
    
    BackHandler {
        if (isInQueueMode) {
            viewModel.exitQueueMode()
        } else {
            onNavigateToSettings()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        } else if (currentItems.isNotEmpty()) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var totalDragX = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                            },
                            onDragEnd = {
                                // Check if we have a significant left swipe
                                if (totalDragX < -200) {
                                    val currentMediaItem = currentItems[pagerState.currentPage]
                                    viewModel.setSelectedModel(currentMediaItem)
                                    
                                    if (isInQueueMode) {
                                        // Player Screen (Preview Images) -> Creator Detail
                                        onNavigateToCreator(currentMediaItem.creator)
                                    } else {
                                        // Player Screen (Models) -> Model Detail
                                        onNavigateToDetail()
                                    }
                                }
                            }
                        ) { change, dragAmount ->
                            // Only count horizontal drag when it's clearly horizontal
                            if (abs(dragAmount.x) > abs(dragAmount.y) * 1.5) {
                                totalDragX += dragAmount.x
                                change.consume()
                            }
                        }
                    }
            ) { page ->
                MediaItemView(
                    mediaItem = currentItems[page],
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            IconButton(
                onClick = onNavigateToSettings
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            currentSource?.let { source ->
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = source.name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = onNavigateToSearch,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.White
                )
            }
            
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        viewModel.cycleToNextSource()
                    }
                },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Cycle Source",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun MediaItemView(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader(context)
    Box(
        modifier = modifier
    ) {
        AsyncImage(
            model = mediaItem.imageUrl,
            imageLoader = imageLoader,
            contentDescription = mediaItem.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        MediaItemOverlay(
            mediaItem = mediaItem,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
fun MediaItemOverlay(
    mediaItem: MediaItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.3f)
            )
            .padding(16.dp)
    ) {
        Column {
            androidx.compose.material3.Text(
                text = mediaItem.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            androidx.compose.material3.Text(
                text = "@${mediaItem.creator}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            androidx.compose.material3.Text(
                text = mediaItem.type,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
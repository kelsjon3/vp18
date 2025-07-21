package com.vp18.mediaplayer.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.ImageLoader
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.viewmodel.MediaViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    viewModel: MediaViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToCreator: (String) -> Unit
) {
    val selectedModel by viewModel.selectedModel.collectAsState()
    val currentMediaItem = viewModel.getCurrentMediaItem()
    
    val modelToShow = selectedModel ?: currentMediaItem
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    var totalDragX = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDragX = 0f
                        },
                        onDragEnd = {
                            when {
                                // Swipe left to creator (only in queue mode)
                                totalDragX < -200 -> {
                                    modelToShow?.let { model ->
                                        if (viewModel.isInQueueMode.value) {
                                            onNavigateToCreator(model.creator)
                                        }
                                    }
                                }
                                // Swipe right back to player
                                totalDragX > 200 -> {
                                    onNavigateToPlayer()
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
        ) {
            modelToShow?.let { model ->
                DetailContent(
                    mediaItem = model,
                    onImageClick = { selectedImage ->
                        viewModel.setQueueFromModel(selectedImage)
                        onNavigateToPlayer()
                    }
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No content to display",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun DetailContent(
    mediaItem: MediaItem,
    onImageClick: (MediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = mediaItem.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "@${mediaItem.creator}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = mediaItem.type,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (mediaItem.model != null) {
            Text(
                text = "All Images",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            val allImages = mediaItem.model.modelVersions.flatMap { version ->
                version.images.map { image ->
                    MediaItem(
                        id = "${mediaItem.model.id}_${image.id}",
                        title = mediaItem.model.name,
                        imageUrl = image.url,
                        creator = mediaItem.model.creator.username,
                        type = mediaItem.model.type,
                        source = mediaItem.source,
                        model = mediaItem.model
                    )
                }
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(allImages) { image ->
                    ImageThumbnail(
                        mediaItem = image,
                        onClick = { onImageClick(image) }
                    )
                }
            }
        } else {
            ImageThumbnail(
                mediaItem = mediaItem,
                onClick = { onImageClick(mediaItem) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ImageThumbnail(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader(context)
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        AsyncImage(
            model = mediaItem.imageUrl,
            imageLoader = imageLoader,
            contentDescription = mediaItem.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentScale = ContentScale.Crop
        )
    }
}
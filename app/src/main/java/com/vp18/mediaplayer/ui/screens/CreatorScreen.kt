package com.vp18.mediaplayer.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.ImageLoader
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun CreatorScreen(
    viewModel: MediaViewModel,
    creatorName: String,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader(context)

    var creatorImages by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(creatorName) {
        coroutineScope.launch {
            isLoading = true
            try {
                creatorImages = viewModel.getCreatorContent(creatorName)
                println("DEBUG: Loaded ${creatorImages.size} images for creator $creatorName")
            } catch (e: Exception) {
                println("DEBUG: Failed to load creator content: ${e.message}")
                creatorImages = emptyList()
            } finally {
                isLoading = false
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var totalDragX = 0f
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                    },
                    onDragEnd = {
                        // Swipe right back to player
                        if (totalDragX > 200) {
                            onNavigateToPlayer()
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = creatorName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Row {
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Profile Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // Use first creator image as avatar or placeholder
                    if (creatorImages.isNotEmpty()) {
                        AsyncImage(
                            model = creatorImages.first().imageUrl,
                            imageLoader = imageLoader,
                            contentDescription = "Creator Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = creatorName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Username
                Text(
                    text = "@$creatorName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Stats Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    StatItem(
                        number = creatorImages.size.toString(),
                        label = "Models"
                    )
                    StatItem(
                        number = "${(creatorImages.size * 47..creatorImages.size * 234).random()}",
                        label = "Followers"
                    )
                    StatItem(
                        number = "${(creatorImages.size * 12..creatorImages.size * 89).random()}K",
                        label = "Likes"
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Bio
                Text(
                    text = "AI model creator specializing in ${if (creatorImages.isNotEmpty()) creatorImages.first().type.lowercase() else "digital art"} content",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Content Grid
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(creatorImages) { mediaItem ->
                        CreatorImageThumbnail(
                            mediaItem = mediaItem,
                            onClick = {
                                viewModel.setSelectedModel(mediaItem)
                                onNavigateToPlayer()
                            },
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(number: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun CreatorImageThumbnail(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ImageLoader(context)
    
    // Check if this is a landscape image
    val isLandscape = mediaItem.width != null && mediaItem.height != null && 
                     mediaItem.width > mediaItem.height
    
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
                .aspectRatio(if (isLandscape) 4f / 3f else 1f),
            contentScale = ContentScale.Crop
        )
    }
}
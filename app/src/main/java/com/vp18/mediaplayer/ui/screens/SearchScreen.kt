package com.vp18.mediaplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.data.FollowingUser
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.vp18.mediaplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<MediaItem>,
    isSearching: Boolean,
    onSearchSubmit: (String) -> Unit,
    onResultClick: (MediaItem) -> Unit,
    onNavigateBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    onCreatorClick: (String) -> Unit = {},
    followingUsers: List<FollowingUser> = emptyList(),
    onLoadFollowingUsers: () -> Unit = {},
    isLoadingFollowingUsers: Boolean = false
) {
    var localQuery by remember { mutableStateOf(searchQuery) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = localQuery,
            onValueChange = { 
                localQuery = it
                onSearchQueryChange(it)
            },
            label = { Text("Search models, @username, or #tag") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (localQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        localQuery = ""
                        onSearchQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (localQuery.isNotEmpty()) {
                        onSearchSubmit(localQuery)
                        keyboardController?.hide()
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Creators You Follow Section - show as regular results when loaded
        if (searchResults.isEmpty() && localQuery.isEmpty()) {
            if (followingUsers.isEmpty()) {
                // Show simple clickable link when no data loaded
                Text(
                    text = "Creators You Follow",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { 
                            println("DEBUG: Creators You Follow clicked!")
                            onLoadFollowingUsers() 
                        }
                        .padding(vertical = 8.dp)
                )
            } else {
                // Show the creators as regular search results
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val validUsers = followingUsers.filter { it.username != null }
                    println("DEBUG: Total users: ${followingUsers.size}, Valid users: ${validUsers.size}")
                    
                    items(validUsers) { user ->
                        println("DEBUG: Creating card for user: ${user.username}")
                        CreatorCard(
                            creatorName = user.username!!, // Safe to use !! since we filtered
                            profilePicture = user.profilePicture?.let { 
                                "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/${it.url}/width=450,optimized=true/${it.name}"
                            },
                            onClick = { onCreatorClick(user.username!!) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Results
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isSearching -> {
                    CircularProgressIndicator()
                }
                searchResults.isEmpty() && localQuery.isNotEmpty() -> {
                    Text(
                        text = "No results found for \"$localQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                searchResults.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = searchResults,
                            key = { it.id },
                            span = { item ->
                                // Check if this is a landscape image
                                val isLandscape = item.width != null && item.height != null && 
                                                 item.width > item.height
                                
                                if (isLandscape) {
                                    GridItemSpan(2) // Landscape images span both columns
                                } else {
                                    GridItemSpan(1) // Portrait images use one column
                                }
                            }
                        ) { item ->
                            // Check if this is a landscape image
                            val isLandscape = item.width != null && item.height != null && 
                                             item.width > item.height
                            
                            // Trigger pagination when reaching the last few items
                            val index = searchResults.indexOf(item)
                            if (index >= searchResults.size - 3) {
                                LaunchedEffect(index) {
                                    onLoadMore()
                                }
                            }
                            
                            SearchResultCard(
                                mediaItem = item,
                                onClick = { onResultClick(item) },
                                isLandscape = isLandscape
                            )
                        }
                        
                        // Show loading indicator if searching for more results
                        if (isSearching && searchResults.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Enter a search term to find models",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}



@Composable
fun CreatorCard(
    creatorName: String,
    profilePicture: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile picture or placeholder avatar
            if (profilePicture != null) {
                AsyncImage(
                    model = profilePicture,
                    contentDescription = "Profile picture of $creatorName",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    error = null
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = creatorName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "@$creatorName",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SearchResultCard(
    mediaItem: MediaItem,
    onClick: () -> Unit,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            AsyncImage(
                model = mediaItem.imageUrl,
                contentDescription = mediaItem.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        if (isLandscape && mediaItem.width != null && mediaItem.height != null) {
                            // Use natural aspect ratio for landscape images that span full width
                            mediaItem.width.toFloat() / mediaItem.height.toFloat()
                        } else {
                            3f / 4f
                        }
                    ),
                contentScale = ContentScale.Crop
            )
            
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = mediaItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2
                )
                
                Text(
                    text = "by ${mediaItem.creator}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = mediaItem.type,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
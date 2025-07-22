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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.vp18.mediaplayer.ui.components.CreatorCard
import com.vp18.mediaplayer.ui.components.SearchResultCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    searchQuery: String,
    searchResults: List<MediaItem>,
    isSearching: Boolean,
    onResultClick: (MediaItem) -> Unit,
    onNavigateBack: () -> Unit,
    onLoadMore: () -> Unit = {},
    onCreatorClick: (String) -> Unit = {},
    followingUsers: List<FollowingUser> = emptyList(),
    isLoadingFollowingUsers: Boolean = false,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (searchQuery.isEmpty()) "Creators You Follow" else "Search Results",
                        maxLines = 1
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Only show search field when there's a manual search query (not a creator search)
            if (searchQuery.isNotEmpty() && !searchQuery.startsWith("@")) {
                // Search field
                var localQuery by remember { mutableStateOf("") }
                val keyboardController = LocalSoftwareKeyboardController.current
                
                // Initialize localQuery with searchQuery only once
                LaunchedEffect(Unit) {
                    localQuery = searchQuery
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
            
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            when {
                isSearching -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                searchResults.isEmpty() && searchQuery.isNotEmpty() -> {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                searchResults.isNotEmpty() -> {
                    // Show search results
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
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
                    // Show "Creators You Follow" results
                    if (followingUsers.isEmpty()) {
                        Text(
                            text = "No creators found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val validUsers = followingUsers.filter { it.username != null }
                            
                            items(validUsers) { user ->
                                CreatorCard(
                                    creatorName = user.username!!,
                                    profilePicture = user.profilePicture?.let { 
                                        "https://image.civitai.com/xG1nkqKTMzGDvpLrqFT7WA/${it.url}/width=450,optimized=true/${it.name}"
                                    },
                                    onClick = { onCreatorClick(user.username!!) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
} 
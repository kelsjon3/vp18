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
import com.vp18.mediaplayer.ui.components.CreatorCard
import com.vp18.mediaplayer.ui.components.SearchResultCard
import androidx.compose.ui.res.painterResource
import com.vp18.mediaplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onNavigateBack: () -> Unit,
    followingUsers: List<FollowingUser> = emptyList(),
    onLoadFollowingUsers: () -> Unit = {},
    isLoadingFollowingUsers: Boolean = false,
    onNavigateToResults: (String) -> Unit = {}
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
                        onNavigateToResults(localQuery)
                        keyboardController?.hide()
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Creators You Follow Section
        if (localQuery.isEmpty()) {
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
                            onNavigateToResults("") // Navigate to results with empty query for "Creators You Follow"
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
                            onClick = { 
                                onNavigateToResults("@${user.username!!}") // Navigate to results with creator search
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Show search prompt when query is entered
            Text(
                text = "Press search to find models, @username, or #tag",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}
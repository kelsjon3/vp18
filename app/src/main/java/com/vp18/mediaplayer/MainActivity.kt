package com.vp18.mediaplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vp18.mediaplayer.repository.MediaRepository
import com.vp18.mediaplayer.ui.screens.CreatorScreen
import com.vp18.mediaplayer.ui.screens.DetailScreen
import com.vp18.mediaplayer.ui.screens.PlayerScreen
import com.vp18.mediaplayer.ui.screens.SearchScreen
import com.vp18.mediaplayer.ui.screens.SettingsScreen
import com.vp18.mediaplayer.ui.theme.VP18Theme
import com.vp18.mediaplayer.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        
        setContent {
            VP18Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaPlayerApp()
                }
            }
        }
    }
}

@Composable
fun MediaPlayerApp() {
    val context = LocalContext.current
    val repository = MediaRepository(context)
    val viewModel: MediaViewModel = viewModel { MediaViewModel(repository) }
    val navController = rememberNavController()
    
    val sources by viewModel.sources.collectAsState()
    
    LaunchedEffect(sources) {
        if (sources.isEmpty() && navController.currentDestination?.route != "settings") {
            navController.navigate("settings") {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = "settings"
    ) {
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateToPlayer = {
                    navController.navigate("player") {
                        popUpTo("settings") { inclusive = true }
                    }
                }
            )
        }
        
        composable("player") {
            PlayerScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToDetail = {
                    navController.navigate("detail")
                },
                onNavigateToCreator = { creatorName ->
                    navController.navigate("creator/$creatorName")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("detail") {
            DetailScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("player") {
                        popUpTo("detail") { inclusive = true }
                    }
                },
                onNavigateToCreator = { creatorName ->
                    navController.navigate("creator/$creatorName")
                }
            )
        }
        
        composable("creator/{creatorName}") { backStackEntry ->
            val creatorName = backStackEntry.arguments?.getString("creatorName") ?: ""
            CreatorScreen(
                viewModel = viewModel,
                creatorName = creatorName,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("player") {
                        popUpTo("creator/{creatorName}") { inclusive = true }
                    }
                }
            )
        }
        
                    composable("search") {
                val searchResults by viewModel.searchResults.collectAsState()
                val isSearching by viewModel.isSearching.collectAsState()
                val followingUsers by viewModel.followingUsers.collectAsState()
                val isLoadingFollowingUsers by viewModel.isLoadingFollowingUsers.collectAsState()
                
                SearchScreen(
                    searchQuery = viewModel.currentSearchQuery.value,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    searchResults = searchResults,
                    isSearching = isSearching,
                    onSearchSubmit = { query ->
                        viewModel.searchContent(query)
                    },
                    onResultClick = { mediaItem ->
                        viewModel.setSelectedModel(mediaItem)
                        viewModel.setSearchResultsAsCurrentMedia(mediaItem)
                        navController.navigate("player")
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onLoadMore = {
                        viewModel.loadMoreSearchResults()
                    },
                    onCreatorClick = { creatorName ->
                        // Search for the creator and navigate to their gallery
                        viewModel.searchContent("@$creatorName")
                    },
                    followingUsers = followingUsers,
                    onLoadFollowingUsers = {
                        viewModel.loadFollowingUsers()
                    },
                    isLoadingFollowingUsers = isLoadingFollowingUsers
                )
            }
    }
}
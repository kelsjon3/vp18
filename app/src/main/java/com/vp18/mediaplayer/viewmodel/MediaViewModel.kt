package com.vp18.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.data.MediaSource
import com.vp18.mediaplayer.data.SourceType
import com.vp18.mediaplayer.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.vp18.mediaplayer.data.FollowingUser

class MediaViewModel(private val repository: MediaRepository) : ViewModel() {
    
    private val _sources = MutableStateFlow<List<MediaSource>>(emptyList())
    val sources: StateFlow<List<MediaSource>> = _sources.asStateFlow()
    
    private val _currentSourceIndex = MutableStateFlow(0)
    val currentSourceIndex: StateFlow<Int> = _currentSourceIndex.asStateFlow()
    
    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems: StateFlow<List<MediaItem>> = _mediaItems.asStateFlow()
    
    private val _currentMediaIndex = MutableStateFlow(0)
    val currentMediaIndex: StateFlow<Int> = _currentMediaIndex.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _selectedModel = MutableStateFlow<MediaItem?>(null)
    val selectedModel: StateFlow<MediaItem?> = _selectedModel.asStateFlow()
    
    private val _queuedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val queuedItems: StateFlow<List<MediaItem>> = _queuedItems.asStateFlow()
    
    private val _isInQueueMode = MutableStateFlow(false)
    val isInQueueMode: StateFlow<Boolean> = _isInQueueMode.asStateFlow()
    
    private val _civitaiUsername = MutableStateFlow<String?>(null)
    val civitaiUsername: StateFlow<String?> = _civitaiUsername.asStateFlow()
    
    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()
    
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    
    private val _searchCursor = MutableStateFlow<String?>(null)
    val searchCursor: StateFlow<String?> = _searchCursor.asStateFlow()
    
    private val _currentSearchQuery = MutableStateFlow<String>("")
    val currentSearchQuery: StateFlow<String> = _currentSearchQuery.asStateFlow()
    
    // Search caching
    private var searchResultsLastLoaded: Long = 0
    private val searchResultsCacheTimeout = 2 * 60 * 1000L // 2 minutes
    private var lastSearchQuery: String = ""

    private val _followingUsers = MutableStateFlow<List<FollowingUser>>(emptyList())
    val followingUsers: StateFlow<List<FollowingUser>> = _followingUsers.asStateFlow()
    
    private val _isLoadingFollowingUsers = MutableStateFlow(false)
    val isLoadingFollowingUsers: StateFlow<Boolean> = _isLoadingFollowingUsers.asStateFlow()
    
    private var followingUsersLastLoaded: Long = 0
    private val followingUsersCacheTimeout = 5 * 60 * 1000L // 5 minutes

    init {
        loadSources()
        loadCivitaiUsername()
    }
    
    override fun onCleared() {
        super.onCleared()
        // Clear SMB cache when ViewModel is destroyed
        repository.clearSmbCache()
    }
    
    private fun loadCivitaiUsername() {
        viewModelScope.launch {
            _civitaiUsername.value = repository.getCivitaiUsername()
        }
    }
    
    private fun loadSources() {
        viewModelScope.launch {
            repository.getSources().collect { sourcesList ->
                println("DEBUG: ViewModel loadSources() received ${sourcesList.size} sources")
                sourcesList.forEachIndexed { index, source ->
                    println("DEBUG: Source $index - type: ${source.type}, name: ${source.name}, host: ${source.host}, username: ${source.username}, path: ${source.path}")
                }
                _sources.value = sourcesList
                if (sourcesList.isNotEmpty()) {
                    val validIndex = if (_currentSourceIndex.value < sourcesList.size) {
                        _currentSourceIndex.value
                    } else {
                        0.also { _currentSourceIndex.value = it }
                    }
                    println("DEBUG: Loading media items for source index $validIndex: ${sourcesList[validIndex].name}")
                    loadMediaItems(sourcesList[validIndex])
                }
            }
        }
    }
    
    fun addCivitaiSource(apiKey: String) {
        viewModelScope.launch {
            repository.saveCivitaiApiKey(apiKey)
            val civitaiSource = MediaSource(
                id = "civitai",
                name = "Civitai",
                type = SourceType.CIVITAI
            )
            repository.addSource(civitaiSource)
            // Refresh the username after adding the source
            loadCivitaiUsername()
        }
    }
    
    fun addDefaultCivitaiSource() {
        viewModelScope.launch {
            val civitaiSource = MediaSource(
                id = "civitai",
                name = "Civitai",
                type = SourceType.CIVITAI
            )
            repository.addSource(civitaiSource)
        }
    }
    
    fun addDeviceFolder() {
        viewModelScope.launch {
            val deviceSource = MediaSource(
                id = "device_${System.currentTimeMillis()}",
                name = "Device Folder",
                type = SourceType.DEVICE_FOLDER,
                path = "/storage/emulated/0/Downloads"
            )
            repository.addSource(deviceSource)
        }
    }
    
    fun addNetworkFolder(
        name: String,
        host: String,
        path: String,
        username: String,
        password: String,
        domain: String = "",
        includeSubfolders: Boolean = false
    ) {
        viewModelScope.launch {
            val networkSource = MediaSource(
                id = "network_${System.currentTimeMillis()}",
                name = name,
                type = SourceType.NETWORK_FOLDER,
                path = path,
                host = host,
                username = username,
                password = password,
                domain = domain,
                includeSubfolders = includeSubfolders
            )
            
            // Add the source first
            repository.addSource(networkSource)
            
            // Then save credentials using the exact same sourceString format as addSource
            val sourceString = "${networkSource.type.name}:${networkSource.name}:${networkSource.path ?: ""}:${networkSource.includeSubfolders}"
            repository.saveSmbCredentials(sourceString, host, username, password, domain)
        }
    }
    
    fun removeSource(source: MediaSource) {
        viewModelScope.launch {
            repository.removeSource(source)
        }
    }
    
    fun cycleToNextSource() {
        val currentSources = _sources.value
        if (currentSources.isNotEmpty()) {
            val nextIndex = (_currentSourceIndex.value + 1) % currentSources.size
            _currentSourceIndex.value = nextIndex
            loadMediaItems(currentSources[nextIndex])
        }
    }
    
    private fun loadMediaItems(source: MediaSource) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val items = repository.getMediaItems(source)
                _mediaItems.value = items
                _currentMediaIndex.value = 0
                _isInQueueMode.value = false
                _queuedItems.value = emptyList()
            } catch (e: Exception) {
                _mediaItems.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setCurrentMediaIndex(index: Int) {
        if (index >= 0 && index < _mediaItems.value.size) {
            _currentMediaIndex.value = index
        }
    }
    
    fun setSelectedModel(mediaItem: MediaItem) {
        _selectedModel.value = mediaItem
    }
    
    fun setSearchResultsAsCurrentMedia(selectedMediaItem: MediaItem) {
        val searchResults = _searchResults.value
        if (searchResults.isNotEmpty()) {
            // Set search results as current media items
            _mediaItems.value = searchResults
            
            // Find the index of the selected item
            val selectedIndex = searchResults.indexOfFirst { it.id == selectedMediaItem.id }
            if (selectedIndex != -1) {
                _currentMediaIndex.value = selectedIndex
                println("DEBUG: Set search result as current media, index: $selectedIndex, title: ${selectedMediaItem.title}")
            } else {
                println("DEBUG: Selected media item not found in search results")
                _currentMediaIndex.value = 0
            }
            
            // Exit queue mode if we were in it
            _isInQueueMode.value = false
            _queuedItems.value = emptyList()
        }
    }
    
    fun setQueueFromModel(selectedImage: MediaItem) {
        val model = selectedImage.model
        if (model != null) {
            val modelImages = model.modelVersions.flatMap { version ->
                version.images.map { image ->
                    MediaItem(
                        id = "${model.id}_${image.id}",
                        title = model.name,
                        imageUrl = image.url,
                        creator = model.creator.username,
                        type = model.type,
                        source = selectedImage.source,
                        model = model
                    )
                }
            }
            _queuedItems.value = modelImages
            _isInQueueMode.value = true
            
            val selectedIndex = modelImages.indexOfFirst { it.id == selectedImage.id }
            if (selectedIndex != -1) {
                _currentMediaIndex.value = selectedIndex
            }
        }
    }
    
    fun exitQueueMode() {
        _isInQueueMode.value = false
        _queuedItems.value = emptyList()
        val currentSources = _sources.value
        if (currentSources.isNotEmpty() && _currentSourceIndex.value < currentSources.size) {
            loadMediaItems(currentSources[_currentSourceIndex.value])
        }
    }
    
    fun getCurrentMediaItem(): MediaItem? {
        return if (_isInQueueMode.value) {
            _queuedItems.value.getOrNull(_currentMediaIndex.value)
        } else {
            _mediaItems.value.getOrNull(_currentMediaIndex.value)
        }
    }
    
    fun getCurrentMediaList(): List<MediaItem> {
        return if (_isInQueueMode.value) {
            _queuedItems.value
        } else {
            _mediaItems.value
        }
    }
    
    suspend fun getCreatorContent(creatorName: String): List<MediaItem> {
        return try {
            repository.getCivitaiCreatorContent(creatorName)
        } catch (e: Exception) {
            println("DEBUG: Failed to get creator content: ${e.message}")
            emptyList()
        }
    }
    
    fun setQueueFromCreatorImages(selectedImage: MediaItem, allCreatorImages: List<MediaItem>) {
        // Find the index of the selected image
        val selectedIndex = allCreatorImages.indexOfFirst { it.id == selectedImage.id }
        
        if (selectedIndex != -1) {
            // Create queue starting from selected image
            val queueFromSelected = allCreatorImages.drop(selectedIndex) + allCreatorImages.take(selectedIndex)
            
            _queuedItems.value = queueFromSelected
            _isInQueueMode.value = true
            _currentMediaIndex.value = 0 // Start at the selected image (now at index 0)
            
            println("DEBUG: Set creator queue with ${queueFromSelected.size} images, starting from: ${selectedImage.title}")
        }
    }
    
    fun searchContent(query: String, forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        val isSameQuery = query == lastSearchQuery
        val shouldLoad = forceRefresh || 
                        !isSameQuery || 
                        _searchResults.value.isEmpty() || 
                        (now - searchResultsLastLoaded) > searchResultsCacheTimeout
        
        if (!shouldLoad && isSameQuery) {
            println("DEBUG: Using cached search results for '$query' (${_searchResults.value.size} results)")
            return
        }
        
        if (_isSearching.value && isSameQuery) {
            println("DEBUG: Already searching for '$query', skipping duplicate call")
            return
        }
        
        viewModelScope.launch {
            _isSearching.value = true
            _currentSearchQuery.value = query
            if (!isSameQuery) {
                _searchCursor.value = null // Reset cursor for new search
            }
            try {
                val (results, nextCursor) = repository.searchCivitaiContent(query, _searchCursor.value)
                if (isSameQuery && !forceRefresh) {
                    _searchResults.value = _searchResults.value + results
                } else {
                    _searchResults.value = results
                }
                _searchCursor.value = nextCursor
                searchResultsLastLoaded = now
                lastSearchQuery = query
                println("DEBUG: Search completed for '$query': ${results.size} results")
            } catch (e: Exception) {
                println("DEBUG: Search failed for '$query': ${e.message}")
                if (!isSameQuery) {
                    _searchResults.value = emptyList()
                    _searchCursor.value = null
                }
            } finally {
                _isSearching.value = false
            }
        }
    }
    
    fun loadMoreSearchResults() {
        val cursor = _searchCursor.value
        val query = _currentSearchQuery.value
        
        if (cursor != null && query.isNotEmpty() && !_isSearching.value) {
            viewModelScope.launch {
                _isSearching.value = true
                try {
                    val (newResults, nextCursor) = repository.searchCivitaiContent(query, cursor)
                    _searchResults.value = _searchResults.value + newResults
                    _searchCursor.value = nextCursor
                } catch (e: Exception) {
                    println("DEBUG: Load more search failed: ${e.message}")
                } finally {
                    _isSearching.value = false
                }
            }
        }
    }

    fun loadFollowingUsers(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        val shouldLoad = forceRefresh || 
                        _followingUsers.value.isEmpty() || 
                        (now - followingUsersLastLoaded) > followingUsersCacheTimeout
        
        if (!shouldLoad) {
            println("DEBUG: Using cached following users (${_followingUsers.value.size} users)")
            return
        }
        
        if (_isLoadingFollowingUsers.value) {
            println("DEBUG: Already loading following users, skipping duplicate call")
            return
        }
        
        println("DEBUG: loadFollowingUsers() called, forceRefresh: $forceRefresh")
        viewModelScope.launch {
            _isLoadingFollowingUsers.value = true
            try {
                println("DEBUG: Starting to fetch following users...")
                val users = repository.getFollowingUsers()
                _followingUsers.value = users
                followingUsersLastLoaded = now
                println("DEBUG: Loaded ${users.size} followed users")
            } catch (e: Exception) {
                println("DEBUG: Error loading following users: ${e.message}")
                e.printStackTrace()
                if (_followingUsers.value.isEmpty()) {
                    _followingUsers.value = emptyList()
                }
            } finally {
                _isLoadingFollowingUsers.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _currentSearchQuery.value = query
    }
    
    fun clearSearchResults() {
        _searchResults.value = emptyList()
        _searchCursor.value = null
        lastSearchQuery = ""
        searchResultsLastLoaded = 0
    }
}
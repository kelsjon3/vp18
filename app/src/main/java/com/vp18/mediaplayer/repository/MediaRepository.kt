package com.vp18.mediaplayer.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vp18.mediaplayer.api.CivitaiApi
import com.vp18.mediaplayer.data.CivitaiModel
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.data.MediaSource
import com.vp18.mediaplayer.data.SourceType
import com.vp18.mediaplayer.service.SmbService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MediaRepository(private val context: Context) {
    private val civitaiApi: CivitaiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://civitai.com/api/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CivitaiApi::class.java)
    }
    
    companion object {
        private val CIVITAI_API_KEY = stringPreferencesKey("civitai_api_key")
        private val CIVITAI_USERNAME = stringPreferencesKey("civitai_username")
        private val SOURCES = stringSetPreferencesKey("sources")
        private val SMB_CREDENTIALS = stringSetPreferencesKey("smb_credentials")
    }
    
    suspend fun saveCivitaiApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[CIVITAI_API_KEY] = apiKey
        }
        
        // Fetch and store the username
        fetchAndStoreUsername(apiKey)
    }
    
    private suspend fun fetchAndStoreUsername(apiKey: String) {
        try {
            val response = civitaiApi.getCurrentUser("Bearer $apiKey")
            if (response.isSuccessful) {
                val user = response.body()
                user?.let {
                    context.dataStore.edit { preferences ->
                        preferences[CIVITAI_USERNAME] = it.username
                    }
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to fetch username: ${e.message}")
        }
    }
    
    suspend fun getCivitaiApiKey(): String? {
        return context.dataStore.data.first()[CIVITAI_API_KEY]
    }
    
    suspend fun getCivitaiUsername(): String? {
        return context.dataStore.data.first()[CIVITAI_USERNAME]
    }

    suspend fun saveSmbCredentials(sourceId: String, host: String, username: String, password: String, domain: String = "") {
        context.dataStore.edit { preferences ->
            val currentCredentials = preferences[SMB_CREDENTIALS] ?: emptySet()
            val credentialString = "$sourceId:$host:$username:$password:$domain"
            preferences[SMB_CREDENTIALS] = currentCredentials + credentialString
        }
    }

    suspend fun getSmbCredentials(sourceId: String): MediaSource? {
        val credentials = context.dataStore.data.first()[SMB_CREDENTIALS] ?: emptySet()
        val credentialString = credentials.find { it.startsWith("$sourceId:") }
        
        return credentialString?.let {
            val parts = it.split(":")
            if (parts.size >= 4) {
                MediaSource(
                    id = parts[0],
                    name = "SMB Credentials",
                    type = SourceType.NETWORK_FOLDER,
                    host = parts[1],
                    username = parts[2],
                    password = parts[3],
                    domain = if (parts.size > 4) parts[4] else ""
                )
            } else null
        }
    }

    suspend fun removeSmbCredentials(sourceId: String) {
        context.dataStore.edit { preferences ->
            val currentCredentials = preferences[SMB_CREDENTIALS] ?: emptySet()
            preferences[SMB_CREDENTIALS] = currentCredentials.filter { !it.startsWith("$sourceId:") }.toSet()
        }
    }
    
    suspend fun addSource(source: MediaSource) {
        context.dataStore.edit { preferences ->
            val currentSources = preferences[SOURCES] ?: emptySet()
            val sourceString = "${source.type.name}:${source.name}:${source.path ?: ""}:${source.includeSubfolders}"
            preferences[SOURCES] = currentSources + sourceString
        }
    }
    
    fun getSources(): Flow<List<MediaSource>> {
        return context.dataStore.data.map { preferences ->
            val sourceStrings = preferences[SOURCES] ?: emptySet()
            val smbCredentials = preferences[SMB_CREDENTIALS] ?: emptySet()
            
            sourceStrings.mapNotNull { sourceString ->
                println("DEBUG: Processing sourceString: $sourceString")
                val parts = sourceString.split(":")
                if (parts.size >= 2) {
                    val type = try {
                        SourceType.valueOf(parts[0])
                    } catch (e: IllegalArgumentException) {
                        return@mapNotNull null
                    }
                    val name = parts[1]
                    val path = if (parts.size > 2) parts[2].takeIf { it.isNotEmpty() } else null
                    val includeSubfolders = if (parts.size > 3) parts[3].toBoolean() else false
                    println("DEBUG: Parsed - type: $type, name: $name, path: $path, includeSubfolders: $includeSubfolders")
                    
                    // For network folders, merge with stored credentials
                    if (type == SourceType.NETWORK_FOLDER) {
                        // Look for credentials by sourceId (which is stored as the first part of the credential string)
                        println("DEBUG: Looking for credentials for sourceString: $sourceString")
                        println("DEBUG: Available credentials: $smbCredentials")
                        val credentialString = smbCredentials.find { it.startsWith("${sourceString}:") }
                        println("DEBUG: Found credential string: $credentialString")
                        credentialString?.let {
                            val credParts = it.split(":")
                            println("DEBUG: Credential parts: $credParts")
                            if (credParts.size >= 7) { // NETWORK_FOLDER:name:path:includeSubfolders:host:username:password:domain
                                MediaSource(
                                    id = sourceString,
                                    name = name,
                                    type = type,
                                    path = path,
                                    host = credParts[4], // host is 5th part
                                    username = credParts[5], // username is 6th part
                                    password = credParts[6], // password is 7th part
                                    domain = if (credParts.size > 7) credParts[7] else "", // domain is 8th part
                                    includeSubfolders = includeSubfolders
                                )
                            } else {
                                println("DEBUG: Credential parts insufficient: ${credParts.size}")
                                MediaSource(id = sourceString, name = name, type = type, path = path, includeSubfolders = includeSubfolders)
                            }
                        } ?: run {
                            println("DEBUG: No credentials found, creating MediaSource without host")
                            MediaSource(id = sourceString, name = name, type = type, path = path, includeSubfolders = includeSubfolders)
                        }
                    } else {
                        MediaSource(id = sourceString, name = name, type = type, path = path, includeSubfolders = includeSubfolders)
                    }
                } else null
            }
        }
    }
    
    suspend fun removeSource(source: MediaSource) {
        context.dataStore.edit { preferences ->
            val currentSources = preferences[SOURCES] ?: emptySet()
            val sourceString = "${source.type.name}:${source.name}:${source.path ?: ""}:${source.includeSubfolders}"
            preferences[SOURCES] = currentSources - sourceString
        }
    }
    
    suspend fun getCivitaiCreatorContent(creatorName: String): List<MediaItem> {
        val apiKey = getCivitaiApiKey()
        
        return try {
            println("DEBUG: Fetching creator images for: $creatorName using /images endpoint")
            
            // Simple approach: Use /images endpoint with username parameter
            val response = civitaiApi.getImages(
                authorization = apiKey?.let { "Bearer $it" },
                username = creatorName,
                sort = "Newest",
                nsfw = true
            )
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                println("DEBUG: Images API response successful for creator $creatorName")
                
                if (responseBody?.items != null) {
                    println("DEBUG: Found ${responseBody.items.size} images in response")
                    
                    // Convert images directly to MediaItems
                    val mediaItems = responseBody.items.map { image ->
                        MediaItem(
                            id = "creator_image_${image.id}",
                            title = image.metadata?.prompt ?: "Creator Image",
                            imageUrl = image.url,
                            creator = creatorName,
                            type = "Image",
                            source = MediaSource(
                                id = "civitai_creator_$creatorName",
                                name = "Creator: $creatorName",
                                type = SourceType.CIVITAI
                            )
                        )
                    }
                    
                    println("DEBUG: Created ${mediaItems.size} media items for creator $creatorName")
                    mediaItems
                } else {
                    println("DEBUG: No items found in response for creator $creatorName")
                    emptyList()
                }
            } else {
                println("DEBUG: Creator images API error: ${response.code()} - ${response.message()}")
                println("DEBUG: Response body: ${response.errorBody()?.string()}")
                emptyList()
            }
        } catch (e: Exception) {
            println("DEBUG: Creator images API exception: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getCivitaiModels(): List<CivitaiModel> {
        val apiKey = getCivitaiApiKey()
        
        return try {
            println("DEBUG: Fetching Civitai models with API key: ${apiKey?.take(10)}...")
            
            // Use API key if available, limit to 20 models for prefetching
            val response = civitaiApi.getModels(
                authorization = apiKey?.let { "Bearer $it" },
                types = listOf("Checkpoint", "LORA"),
                sort = "Newest",
                nsfw = true,
                limit = 20
            )
            
            if (response.isSuccessful) {
                val models = response.body()?.items ?: emptyList()
                println("DEBUG: Successfully fetched ${models.size} models from Civitai")
                models
            } else {
                println("DEBUG: Civitai API error: ${response.code()} - ${response.message()}")
                println("DEBUG: Response body: ${response.errorBody()?.string()}")
                
                // If API key is available, try with different auth format
                if (apiKey != null) {
                    println("DEBUG: Retrying with API key as query parameter...")
                    val retryResponse = civitaiApi.getModelsWithApiKey(
                        apiKey = apiKey,
                        types = listOf("Checkpoint", "LORA"),
                        sort = "Newest",
                        nsfw = true,
                        limit = 20
                    )
                    if (retryResponse.isSuccessful) {
                        val models = retryResponse.body()?.items ?: emptyList()
                        println("DEBUG: Successfully fetched ${models.size} models with API key")
                        models
                    } else {
                        println("DEBUG: Retry also failed: ${retryResponse.code()}")
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Civitai API exception: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun getMediaItems(source: MediaSource): List<MediaItem> {
        return when (source.type) {
            SourceType.CIVITAI -> {
                val models = getCivitaiModels()
                models.mapNotNull { model ->
                    val firstImage = model.modelVersions.firstOrNull()?.images?.firstOrNull()
                    if (firstImage != null) {
                        MediaItem(
                            id = "${model.id}_${firstImage.id}",
                            title = model.name,
                            imageUrl = firstImage.url,
                            creator = model.creator.username,
                            type = model.type,
                            source = source,
                            model = model
                        )
                    } else null
                }
            }
            SourceType.DEVICE_FOLDER -> {
                listOf(
                    MediaItem(
                        id = "device_sample_1",
                        title = "Sample Device Image",
                        imageUrl = "https://picsum.photos/400/600",
                        creator = "Local",
                        type = "Image",
                        source = source
                    )
                )
            }
            SourceType.NETWORK_FOLDER -> {
                try {
                    println("DEBUG: getMediaItems - MediaSource details: name=${source.name}, host=${source.host}, username=${source.username}, path=${source.path}")
                    val smbService = SmbService(context)
                    // Connect to server first
                    if (smbService.connect(source, connectToShare = false)) {
                        // Extract share name from path
                        val selectedPath = source.path ?: ""
                        val shareName = selectedPath.split("/").firstOrNull() ?: ""
                        
                        if (shareName.isNotEmpty() && smbService.connectToShare(shareName)) {
                            val mediaItems = smbService.listMediaFiles(source)
                            smbService.close()
                            mediaItems
                        } else {
                            println("DEBUG: Failed to connect to SMB share: $shareName")
                            smbService.close()
                            emptyList()
                        }
                    } else {
                        println("DEBUG: Failed to connect to SMB server: ${source.host}")
                        emptyList()
                    }
                } catch (e: Exception) {
                    println("DEBUG: SMB error: ${e.message}")
                    e.printStackTrace()
                    emptyList()
                }
            }
        }
    }
    
    fun clearSmbCache() {
        SmbService.clearSmbCache(context)
    }
    
    suspend fun searchCivitaiContent(query: String, cursor: String? = null): Pair<List<MediaItem>, String?> {
        val apiKey = getCivitaiApiKey()
        
        return try {
            println("DEBUG: Searching Civitai for: $query ${if (cursor != null) "with cursor: $cursor" else ""}")
            
            // Determine if query is a username, tag, or general search
            val (searchType, searchValue) = when {
                query.startsWith("@") -> "username" to query.drop(1)
                query.startsWith("#") -> "tag" to query.drop(1)
                else -> "general" to query
            }
            
            println("DEBUG: Search type: $searchType, search value: '$searchValue'")
            
            val response = when (searchType) {
                "username" -> {
                    println("DEBUG: Searching for username (case-sensitive): '$searchValue'")
                    // Search by username - requires exact case-sensitive match
                    civitaiApi.getModels(
                        authorization = apiKey?.let { "Bearer $it" },
                        username = searchValue,
                        nsfw = true,
                        cursor = cursor,
                        limit = 20
                    )
                }
                "tag" -> {
                    println("DEBUG: Searching for tag: '$searchValue'")
                    // Search by tag
                    civitaiApi.getModels(
                        authorization = apiKey?.let { "Bearer $it" },
                        tag = searchValue,
                        nsfw = true,
                        cursor = cursor,
                        limit = 20
                    )
                }
                else -> {
                    println("DEBUG: Searching for general query: '$searchValue'")
                    // General search (model name/description)
                    civitaiApi.getModels(
                        authorization = apiKey?.let { "Bearer $it" },
                        query = searchValue,
                        nsfw = true,
                        cursor = cursor,
                        limit = 20
                    )
                }
            }
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                val models = responseBody?.items ?: emptyList()
                val nextCursor = responseBody?.metadata?.nextCursor
                
                // Convert to MediaItems
                val mediaItems = models.mapNotNull { model ->
                    val firstImage = model.modelVersions.firstOrNull()?.images?.firstOrNull()
                    if (firstImage != null) {
                        MediaItem(
                            id = "${model.id}_${firstImage.id}",
                            title = model.name,
                            imageUrl = firstImage.url,
                            creator = model.creator.username,
                            type = model.type,
                            source = MediaSource(
                                id = "civitai_search",
                                name = "Search Results",
                                type = SourceType.CIVITAI
                            ),
                            model = model
                        )
                    } else null
                }
                
                println("DEBUG: Found ${mediaItems.size} search results for: $query, nextCursor: $nextCursor")
                Pair(mediaItems, nextCursor)
            } else {
                println("DEBUG: Search API error: ${response.code()} - ${response.message()}")
                println("DEBUG: Search response body: ${response.errorBody()?.string()}")
                
                // Add fallback mechanism like in getCivitaiModels()
                if (apiKey != null && response.code() == 400) {
                    println("DEBUG: Retrying search with API key as query parameter...")
                    val retryResponse = when (searchType) {
                        "username" -> {
                            civitaiApi.getModelsWithApiKey(
                                apiKey = apiKey,
                                username = searchValue,
                                nsfw = true,
                                cursor = cursor,
                                limit = 20
                            )
                        }
                        "tag" -> {
                            civitaiApi.getModelsWithApiKey(
                                apiKey = apiKey,
                                tag = searchValue,
                                nsfw = true,
                                cursor = cursor,
                                limit = 20
                            )
                        }
                        else -> {
                            civitaiApi.getModelsWithApiKey(
                                apiKey = apiKey,
                                query = searchValue,
                                nsfw = true,
                                cursor = cursor,
                                limit = 20
                            )
                        }
                    }
                    
                    if (retryResponse.isSuccessful) {
                        val responseBody = retryResponse.body()
                        val models = responseBody?.items ?: emptyList()
                        val nextCursor = responseBody?.metadata?.nextCursor
                        
                        // Convert to MediaItems
                        val mediaItems = models.mapNotNull { model ->
                            val firstImage = model.modelVersions.firstOrNull()?.images?.firstOrNull()
                            if (firstImage != null) {
                                MediaItem(
                                    id = "${model.id}_${firstImage.id}",
                                    title = model.name,
                                    imageUrl = firstImage.url,
                                    creator = model.creator.username,
                                    type = model.type,
                                    source = MediaSource(
                                        id = "civitai_search",
                                        name = "Search Results",
                                        type = SourceType.CIVITAI
                                    ),
                                    model = model
                                )
                            } else null
                        }
                        
                        println("DEBUG: Retry search found ${mediaItems.size} results for: $query")
                        Pair(mediaItems, nextCursor)
                    } else {
                        println("DEBUG: Search retry also failed: ${retryResponse.code()}")
                        Pair(emptyList(), null)
                    }
                } else {
                Pair(emptyList(), null)
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Search exception: ${e.message}")
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }
}
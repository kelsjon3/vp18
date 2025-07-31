package com.vp18.mediaplayer.service

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import android.content.Context
import com.vp18.mediaplayer.data.MediaItem
import com.vp18.mediaplayer.data.MediaSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

// Direct jcifs-ng imports for testing
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.security.MessageDigest
import java.util.EnumSet

data class SmbFolderItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String
)

class SmbService(private val context: Context? = null) : Closeable {
    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    
    // Store credentials for jcifs-ng share enumeration
    private var storedHost: String? = null
    private var storedUsername: String? = null
    private var storedPassword: String? = null
    private var storedDomain: String? = null
    
    companion object {
        fun clearSmbCache(context: Context) {
            try {
                val cacheDir = File(context.cacheDir, "smb_cache")
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursively()
                    println("DEBUG: SMB cache cleared")
                }
            } catch (e: Exception) {
                println("DEBUG: Failed to clear SMB cache: ${e.message}")
            }
        }
    }

    suspend fun connect(mediaSource: MediaSource, connectToShare: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        try {
            val host = mediaSource.host ?: return@withContext false
            println("DEBUG: MediaSource details - name: ${mediaSource.name}, host: ${mediaSource.host}, username: ${mediaSource.username}")
            println("DEBUG: Connecting to SMB server at $host")
            
            // Store credentials for jcifs-ng share enumeration
            storedHost = host
            storedUsername = mediaSource.username ?: "guest"
            storedPassword = mediaSource.password ?: ""
            storedDomain = mediaSource.domain ?: ""
            
            client = SMBClient()
            
            // Try to connect with the provided host (could be hostname or IP)
            connection = try {
                client!!.connect(host)
            } catch (e: java.net.UnknownHostException) {
                println("DEBUG: Failed to resolve hostname '$host', try using IP address instead")
                throw e
            }
            
            val authContext = AuthenticationContext(
                storedUsername!!,
                storedPassword!!.toCharArray(),
                storedDomain!!
            )
            
            session = connection!!.authenticate(authContext)
            println("DEBUG: Successfully authenticated to SMB server")
            
            if (connectToShare) {
                // Extract share name from path (e.g., "media" from "smb://host/media/folder")
                val shareName = extractShareName(mediaSource.path ?: "")
                if (shareName.isEmpty()) {
                    println("DEBUG: Could not extract share name from path: ${mediaSource.path}")
                    return@withContext false
                }
                
                share = session!!.connectShare(shareName) as DiskShare
                println("DEBUG: Successfully connected to SMB share: $shareName")
            }
            
            true
        } catch (e: Exception) {
            println("DEBUG: SMB connection failed: ${e.message}")
            e.printStackTrace()
            close()
            false
        }
    }

    suspend fun listShares(): List<SmbFolderItem> = withContext(Dispatchers.IO) {
        println("DEBUG: SmbService.listShares() - Using DIRECT jcifs-ng approach")
        
        // Try the direct approach first for testing
        try {
            val host = storedHost ?: return@withContext emptyList()
            val username = storedUsername ?: "guest"
            val password = storedPassword ?: ""
            val domain = storedDomain ?: ""
            
            println("DEBUG: Attempting DIRECT jcifs enumeration for $host with user $username")
            
            // Create simple jcifs-ng context with Nova Player settings
            val props = java.util.Properties().apply {
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.useSMB311", "true")
                setProperty("jcifs.smb.client.useSMB2Negotiation", "false")
                setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
                setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
                setProperty("jcifs.smb.client.dfs.disabled", "true")
                setProperty("jcifs.smb.useRawNTLM", "true")
                setProperty("jcifs.resolveOrder", "DNS,BCAST")
                setProperty("jcifs.smb.lmCompatibility", "3")
                setProperty("jcifs.smb.client.useExtendedSecurity", "true")
                setProperty("jcifs.smb.client.responseTimeout", "15000")
                setProperty("jcifs.smb.client.soTimeout", "20000")
                setProperty("jcifs.smb.client.connTimeout", "5000")
            }
            
            println("DEBUG: Creating jcifs PropertyConfiguration...")
            val config = jcifs.config.PropertyConfiguration(props)
            println("DEBUG: Creating jcifs BaseContext...")
            val context = jcifs.context.BaseContext(config)
            println("DEBUG: Creating jcifs NtlmPasswordAuthenticator...")
            val auth = jcifs.smb.NtlmPasswordAuthenticator(domain, username, password)
            println("DEBUG: Creating auth context...")
            val authContext = context.withCredentials(auth)
            
            val serverUrl = "smb://$host/"
            println("DEBUG: Creating SmbFile for: $serverUrl")
            val serverFile = jcifs.smb.SmbFile(serverUrl, authContext)
            
            println("DEBUG: Calling serverFile.listFiles()...")
            val jcifsShares = serverFile.listFiles()
            println("DEBUG: jcifs listFiles() returned ${jcifsShares?.size ?: 0} items")
            
            val shares = mutableListOf<SmbFolderItem>()
            jcifsShares?.forEach { share ->
                try {
                    val shareName = share.name.removeSuffix("/")
                    println("DEBUG: Processing share: $shareName, isDirectory: ${share.isDirectory}")
                    
                    if (share.isDirectory && !isAdministrativeShare(shareName)) {
                        shares.add(SmbFolderItem(shareName, true, shareName))
                        println("DEBUG: ✓ Added share: $shareName")
                    } else {
                        println("DEBUG: ✗ Skipped share: $shareName")
                    }
                } catch (e: Exception) {
                    println("DEBUG: Error processing share: ${e.message}")
                }
            }
            
            println("DEBUG: Final result: ${shares.size} accessible shares")
            return@withContext if (shares.isNotEmpty()) shares else listSharesWithFallback()
            
        } catch (e: Exception) {
            println("DEBUG: DIRECT enumeration failed: ${e.javaClass.simpleName}")
            println("DEBUG: Exception message: ${e.message}")
            e.printStackTrace()
            return@withContext listSharesWithFallback()
        }
    }
    
    private fun isAdministrativeShare(shareName: String): Boolean {
        return shareName.endsWith("$") || 
               shareName.equals("IPC", ignoreCase = true) ||
               shareName.equals("ADMIN", ignoreCase = true) ||
               shareName.equals("print$", ignoreCase = true) ||
               shareName.equals("NETLOGON", ignoreCase = true) ||
               shareName.equals("SYSVOL", ignoreCase = true)
    }
    
    /**
     * Create a special URL that indicates this SMB file should be cached on-demand
     */
    private fun createCacheableUrl(smbUrl: String, filePath: String): String {
        // Create a special format that indicates it needs caching
        return "smb-cache://$smbUrl|$filePath"
    }
    
    /**
     * Load an image URL, handling on-demand caching if needed
     * Call this from image loading components
     */
    suspend fun loadImageUrl(imageUrl: String): String? = withContext(Dispatchers.IO) {
        return@withContext if (imageUrl.startsWith("smb-cache://")) {
            // Large image that needs on-demand caching
            cacheFileOnDemand(imageUrl)
        } else {
            // Already cached or regular URL
            imageUrl
        }
    }
    
    /**
     * Cache a file on-demand when it's actually needed for playback or display
     * Call this when the user wants to play a file or display a large image
     */
    suspend fun cacheFileOnDemand(cacheableUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            if (!cacheableUrl.startsWith("smb-cache://")) {
                println("DEBUG: Not a cacheable URL: $cacheableUrl")
                return@withContext cacheableUrl // Return as-is if not a special URL
            }
            
            // Parse the special URL format
            val urlData = cacheableUrl.removePrefix("smb-cache://")
            val parts = urlData.split("|")
            if (parts.size != 2) {
                println("DEBUG: Invalid cacheable URL format: $cacheableUrl")
                return@withContext null
            }
            
            val smbUrl = parts[0]
            val filePath = parts[1]
            
            println("DEBUG: Caching file on-demand: $filePath")
            return@withContext downloadAndCacheFile(smbUrl, filePath)
            
        } catch (e: Exception) {
            println("DEBUG: Failed to cache file on-demand: ${e.message}")
            return@withContext null
        }
    }
    
    private suspend fun listSharesWithFallback(): List<SmbFolderItem> = withContext(Dispatchers.IO) {
        println("DEBUG: ==> ENTERED FALLBACK METHOD - listSharesWithFallback()")
        println("DEBUG: Executing fallback share enumeration strategies")
        
        // Strategy 1: Try common share names
        val commonShares = listOf("media", "share", "shared", "public", "home", "users", "documents", "downloads")
        val discoveredShares = mutableListOf<SmbFolderItem>()
        
        try {
            val currentSession = session ?: return@withContext emptyList()
            
            for (shareName in commonShares) {
                try {
                    println("DEBUG: Testing common share: $shareName")
                    val testConnection = currentSession.connectShare(shareName)
                    testConnection.close()
                    
                    discoveredShares.add(
                        SmbFolderItem(
                            name = shareName,
                            isDirectory = true,
                            path = shareName
                        )
                    )
                    println("DEBUG: ✓ Confirmed share: $shareName")
                    
                    if (discoveredShares.size >= 5) break // Limit to prevent excessive testing
                } catch (e: Exception) {
                    // Share doesn't exist or not accessible
                }
            }
            
            return@withContext if (discoveredShares.isNotEmpty()) {
                discoveredShares.add(0, SmbFolderItem(
                    name = "--- Discovered shares ---",
                    isDirectory = false,
                    path = ""
                ))
                discoveredShares.sortedBy { if (it.name.startsWith("---")) "" else it.name }
            } else {
                listOf(
                    SmbFolderItem(
                        name = "No shares found",
                        isDirectory = false,
                        path = ""
                    ),
                    SmbFolderItem(
                        name = "Try entering share name manually",
                        isDirectory = false,
                        path = ""
                    ),
                    SmbFolderItem(
                        name = "Check server credentials and network",
                        isDirectory = false,
                        path = ""
                    )
                )
            }
            
        } catch (e: Exception) {
            println("DEBUG: Fallback enumeration also failed: ${e.message}")
            return@withContext listOf(
                SmbFolderItem(
                    name = "Share discovery failed",
                    isDirectory = false,
                    path = ""
                ),
                SmbFolderItem(
                    name = "Error: ${e.message?.take(40) ?: "Connection failed"}",
                    isDirectory = false,
                    path = ""
                )
            )
        }
    }
    


    suspend fun connectToShare(shareName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentSession = session ?: return@withContext false
            share = currentSession.connectShare(shareName) as DiskShare
            println("DEBUG: Successfully connected to SMB share: $shareName")
            true
        } catch (e: Exception) {
            println("DEBUG: Failed to connect to share $shareName: ${e.message}")
            false
        }
    }

    suspend fun listFolders(mediaSource: MediaSource, currentPath: String = ""): List<SmbFolderItem> = withContext(Dispatchers.IO) {
        try {
            // If no share is connected, list available shares
            val currentShare = share
            if (currentShare == null) {
                return@withContext listShares()
            }
            
            println("DEBUG: Listing folders in SMB path: $currentPath")
            
            val files = currentShare.list(currentPath)
            val folderItems = mutableListOf<SmbFolderItem>()
            
            // Add parent directory option if not at root
            if (currentPath.isNotEmpty()) {
                val parentPath = currentPath.substringBeforeLast("/", "")
                folderItems.add(
                    SmbFolderItem(
                        name = "..",
                        isDirectory = true,
                        path = parentPath
                    )
                )
            } else {
                // Add option to go back to share selection
                folderItems.add(
                    SmbFolderItem(
                        name = "← Back to shares",
                        isDirectory = true,
                        path = "SHARES_ROOT"
                    )
                )
            }
            
            for (file in files) {
                if (file.fileName == "." || file.fileName == "..") continue
                
                val filePath = if (currentPath.isEmpty()) file.fileName else "$currentPath/${file.fileName}"
                
                // Simple check: try to list as directory
                val isDir = try {
                    currentShare.list(filePath)
                    true
                } catch (e: Exception) {
                    false
                }
                
                folderItems.add(
                    SmbFolderItem(
                        name = file.fileName,
                        isDirectory = isDir,
                        path = filePath
                    )
                )
            }
            
            println("DEBUG: Found ${folderItems.size} items in SMB folder")
            folderItems.sortedWith(compareBy<SmbFolderItem> { !it.isDirectory }.thenBy { it.name })
        } catch (e: Exception) {
            println("DEBUG: Failed to list SMB folders: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun listMediaFiles(mediaSource: MediaSource, folderPath: String = ""): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val currentShare = share ?: return@withContext emptyList()
            
            // Parse the path - it should be "shareName/folderPath" format
            val selectedPath = mediaSource.path ?: ""
            val pathParts = selectedPath.split("/")
            val folderPathInShare = if (pathParts.size > 1) pathParts.drop(1).joinToString("/") else ""
            val fullPath = if (folderPath.isEmpty()) folderPathInShare else "$folderPathInShare/$folderPath"
            
            println("DEBUG: Listing files in SMB path: $fullPath, includeSubfolders: ${mediaSource.includeSubfolders}")
            
            val mediaItems = mutableListOf<MediaItem>()
            
            if (mediaSource.includeSubfolders) {
                // Recursively scan subfolders
                scanFolderRecursively(currentShare, fullPath, mediaSource, mediaItems)
            } else {
                // Only scan current folder
                scanFolder(currentShare, fullPath, mediaSource, mediaItems)
            }
            
            println("DEBUG: Found ${mediaItems.size} media files in SMB share")
            mediaItems
        } catch (e: Exception) {
            println("DEBUG: Failed to list SMB files: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun scanFolder(share: DiskShare, folderPath: String, mediaSource: MediaSource, mediaItems: MutableList<MediaItem>) {
        try {
            val files = share.list(folderPath)
            
            // Extract share name from mediaSource.path
            val selectedPath = mediaSource.path ?: ""
            val shareName = selectedPath.split("/").firstOrNull() ?: ""
            
            for (file in files) {
                if (file.fileName == "." || file.fileName == "..") continue
                
                if (isMediaFile(file.fileName)) {
                    val filePath = if (folderPath.isEmpty()) file.fileName else "$folderPath/${file.fileName}"
                    val smbUrl = "smb://${mediaSource.host}/$shareName/$filePath"
                    
                    println("DEBUG: Found media file: ${file.fileName}")
                    
                    val fileType = getFileType(file.fileName)
                    val isVideo = fileType == "Video"
                    val isImage = fileType == "Image"
                    
                    // Get file size for smart caching decisions
                    val fileSize = try { file.endOfFile } catch (e: Exception) { 0L }
                    val isSmallImage = isImage && fileSize < 10 * 1024 * 1024 // 10MB limit for smoother scrolling
                    
                    println("DEBUG: File: ${file.fileName}, Type: $fileType, Size: ${fileSize}B, SmallImage: $isSmallImage")
                    
                    // Smart caching strategy:
                    // - Small images: Cache immediately for instant display
                    // - Large images & videos: Use on-demand caching
                    val imageUrl = when {
                        isVideo -> "https://via.placeholder.com/400x600/cccccc/666666?text=Video+File"
                        isSmallImage -> {
                            // Cache small images immediately
                            downloadAndCacheFile(smbUrl, filePath) 
                                ?: "https://via.placeholder.com/400x600/cccccc/666666?text=Loading..."
                        }
                        isImage -> {
                            // Large images use on-demand caching
                            createCacheableUrl(smbUrl, filePath)
                        }
                        else -> "https://via.placeholder.com/400x600/cccccc/666666?text=Unknown"
                    }
                    
                    val videoUrl = if (isVideo) createCacheableUrl(smbUrl, filePath) else null
                    
                    val mediaItem = MediaItem(
                        id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                        title = file.fileName,
                        imageUrl = imageUrl,
                        videoUrl = videoUrl,
                        creator = "Network",
                        type = fileType,
                        source = mediaSource
                    )
                    
                    mediaItems.add(mediaItem)
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to scan folder $folderPath: ${e.message}")
        }
    }

    private suspend fun scanFolderRecursively(share: DiskShare, folderPath: String, mediaSource: MediaSource, mediaItems: MutableList<MediaItem>) {
        try {
            val files = share.list(folderPath)
            
            for (file in files) {
                if (file.fileName == "." || file.fileName == "..") continue
                
                val filePath = if (folderPath.isEmpty()) file.fileName else "$folderPath/${file.fileName}"
                
                // Simple check: try to list as directory
                val isDir = try {
                    share.list(filePath)
                    true
                } catch (e: Exception) {
                    false
                }
                
                if (isDir) {
                    // It's a directory, recurse into it
                    scanFolderRecursively(share, filePath, mediaSource, mediaItems)
                } else if (isMediaFile(file.fileName)) {
                    // It's a media file - smart caching strategy
                    val selectedPath = mediaSource.path ?: ""
                    val shareName = selectedPath.split("/").firstOrNull() ?: ""
                    val smbUrl = "smb://${mediaSource.host}/$shareName/$filePath"
                    
                    println("DEBUG: Found media file: ${file.fileName}")
                    
                    val fileType = getFileType(file.fileName)
                    val isVideo = fileType == "Video"
                    val isImage = fileType == "Image"
                    
                    // Get file size for smart caching decisions
                    val fileSize = try { file.endOfFile } catch (e: Exception) { 0L }
                    val isSmallImage = isImage && fileSize < 10 * 1024 * 1024 // 10MB limit for smoother scrolling
                    
                    println("DEBUG: File: ${file.fileName}, Type: $fileType, Size: ${fileSize}B, SmallImage: $isSmallImage")
                    
                    // Smart caching strategy:
                    // - Small images: Cache immediately for instant display
                    // - Large images & videos: Use on-demand caching
                    val imageUrl = when {
                        isVideo -> "https://via.placeholder.com/400x600/cccccc/666666?text=Video+File"
                        isSmallImage -> {
                            // Cache small images immediately
                            downloadAndCacheFile(smbUrl, filePath) 
                                ?: "https://via.placeholder.com/400x600/cccccc/666666?text=Loading..."
                        }
                        isImage -> {
                            // Large images use on-demand caching
                            createCacheableUrl(smbUrl, filePath)
                        }
                        else -> "https://via.placeholder.com/400x600/cccccc/666666?text=Unknown"
                    }
                    
                    val videoUrl = if (isVideo) createCacheableUrl(smbUrl, filePath) else null
                    
                    val mediaItem = MediaItem(
                        id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                        title = file.fileName,
                        imageUrl = imageUrl,
                        videoUrl = videoUrl,
                        creator = "Network",
                        type = fileType,
                        source = mediaSource
                    )
                    
                    mediaItems.add(mediaItem)
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Failed to scan folder recursively $folderPath: ${e.message}")
        }
    }

    private fun extractShareName(path: String): String {
        // Extract share name from paths like "smb://host/sharename/folder" or "/sharename/folder"
        val cleanPath = path.removePrefix("smb://").substringAfter("/")
        return cleanPath.split("/").firstOrNull() ?: ""
    }

    private fun extractFolderPath(path: String): String {
        // Extract folder path after share name
        val cleanPath = path.removePrefix("smb://").substringAfter("/")
        val parts = cleanPath.split("/")
        return if (parts.size > 1) parts.drop(1).joinToString("/") else ""
    }

    private fun isMediaFile(filename: String): Boolean {
        val mediaExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", // Images
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", // Videos
            "mp3", "wav", "flac", "aac", "ogg", "m4a" // Audio
        )
        val extension = filename.substringAfterLast('.', "").lowercase()
        return extension in mediaExtensions
    }

    private fun getFileType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            in setOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> "Image"
            in setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm") -> "Video"
            in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a") -> "Audio"
            else -> "Unknown"
        }
    }
    
    private suspend fun downloadAndCacheFile(smbUrl: String, filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            println("DEBUG: Starting on-demand cache for: $filePath")
            println("DEBUG: SMB URL: $smbUrl")

            val androidContext = context ?: return@withContext null

            // Create cache directory
            val cacheDir = File(androidContext.cacheDir, "smb_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                println("DEBUG: Created cache directory: ${cacheDir.absolutePath}")
            }
            
            // Check available space before caching
            val freeSpace = cacheDir.freeSpace
            val cacheDirSize = getCacheDirSize(cacheDir)
            val maxCacheSize = 500 * 1024 * 1024L // 500MB max cache
            
            println("DEBUG: Cache dir size: ${cacheDirSize / 1024 / 1024}MB, Free space: ${freeSpace / 1024 / 1024}MB")
            
            if (cacheDirSize > maxCacheSize) {
                println("DEBUG: Cache size limit exceeded, cleaning old files...")
                cleanOldCacheFiles(cacheDir)
            }
            
            if (freeSpace < 100 * 1024 * 1024) { // Need at least 100MB free
                println("DEBUG: Insufficient free space for caching")
                return@withContext null
            }

            // Create unique filename based on SMB URL
            val fileName = filePath.replace("/", "_").replace("\\", "_")
            val cacheFile = File(cacheDir, "smb_$fileName")

            println("DEBUG: Cache file path: ${cacheFile.absolutePath}")

            // Check if file is already cached and not too old (24 hours)
            if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) < 24 * 60 * 60 * 1000) {
                println("DEBUG: Using cached file: ${cacheFile.absolutePath}")
                return@withContext "file://${cacheFile.absolutePath}"
            }

            // Download file from SMB
            val currentShare = share ?: return@withContext null

            // Extract share name and file path from SMB URL
            val urlParts = smbUrl.removePrefix("smb://").split("/")
            if (urlParts.size < 3) {
                println("DEBUG: Invalid SMB URL format: $smbUrl")
                return@withContext null
            }

            val shareName = urlParts[1]
            val smbFilePath = urlParts.drop(2).joinToString("/")

            println("DEBUG: Downloading from SMB: share=$shareName, path=$smbFilePath")

            // Open and download the file using proper access control parameters
            val accessMasks = setOf(AccessMask.FILE_READ_DATA)
            val shareAccesses = setOf(SMB2ShareAccess.FILE_SHARE_READ)
            val createDisposition = SMB2CreateDisposition.FILE_OPEN
            
            val smbFile = currentShare.openFile(smbFilePath, accessMasks, null, shareAccesses, createDisposition, null)
            val inputStream = smbFile.inputStream
            val outputStream = FileOutputStream(cacheFile)

            println("DEBUG: Starting file transfer...")

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            println("DEBUG: Successfully downloaded to: ${cacheFile.absolutePath}")
            "file://${cacheFile.absolutePath}"

        } catch (e: Exception) {
            println("DEBUG: Failed to download file $filePath: ${e.message}")
            e.printStackTrace()
            null
        }
        }
    
    /**
     * Calculate total size of cache directory
     */
    private fun getCacheDirSize(cacheDir: File): Long {
        return try {
            cacheDir.walkTopDown()
                .filter { it.isFile }
                .map { it.length() }
                .sum()
        } catch (e: Exception) {
            println("DEBUG: Error calculating cache size: ${e.message}")
            0L
        }
    }
    
    /**
     * Clean old cache files when cache size limit is exceeded
     */
    private fun cleanOldCacheFiles(cacheDir: File) {
        try {
            val files = cacheDir.listFiles() ?: return
            val sortedFiles = files.filter { it.isFile && it.name.startsWith("smb_") }
                .sortedBy { it.lastModified() } // Oldest first
            
            // Remove oldest 30% of files
            val filesToDelete = sortedFiles.take((sortedFiles.size * 0.3).toInt())
            
            println("DEBUG: Cleaning ${filesToDelete.size} old cache files")
            filesToDelete.forEach { file ->
                try {
                    if (file.delete()) {
                        println("DEBUG: Deleted old cache file: ${file.name}")
                    }
                } catch (e: Exception) {
                    println("DEBUG: Failed to delete cache file ${file.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("DEBUG: Error cleaning cache: ${e.message}")
        }
    }
    
    override fun close() {
        try {
            share?.close()
            session?.close()
            connection?.close()
            client?.close()
        } catch (e: Exception) {
            println("DEBUG: Error closing SMB connection: ${e.message}")
        } finally {
            share = null
            session = null
            connection = null
            client = null
            
            // Clear stored credentials
            storedHost = null
            storedUsername = null
            storedPassword = null
            storedDomain = null
        }
    }
}
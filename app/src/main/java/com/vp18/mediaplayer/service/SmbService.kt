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
            
            client = SMBClient()
            
            // Try to connect with the provided host (could be hostname or IP)
            connection = try {
                client!!.connect(host)
            } catch (e: java.net.UnknownHostException) {
                println("DEBUG: Failed to resolve hostname '$host', try using IP address instead")
                throw e
            }
            
            val authContext = AuthenticationContext(
                mediaSource.username ?: "guest",
                mediaSource.password?.toCharArray() ?: charArrayOf(),
                mediaSource.domain ?: ""
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
        try {
            println("DEBUG: Listing common SMB shares")
            
            // List common share names to try
            val commonShares = listOf("media", "share", "public", "home", "documents", "music", "videos", "pictures", "downloads")
            val folderItems = mutableListOf<SmbFolderItem>()
            
            for (shareName in commonShares) {
                folderItems.add(
                    SmbFolderItem(
                        name = shareName,
                        isDirectory = true,
                        path = shareName
                    )
                )
            }
            
            println("DEBUG: Listed ${folderItems.size} common SMB shares")
            folderItems.sortedBy { it.name }
        } catch (e: Exception) {
            println("DEBUG: Failed to list SMB shares: ${e.message}")
            e.printStackTrace()
            emptyList()
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
                    
                    // Download and cache the file, or use existing cache
                    val cachedUrl = downloadAndCacheFile(smbUrl, filePath)
                    
                    println("DEBUG: File: ${file.fileName}, Cached URL: $cachedUrl")
                    
                    val isVideo = getFileType(file.fileName) == "Video"
                    val mediaItem = MediaItem(
                        id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                        title = file.fileName,
                        imageUrl = if (isVideo) "https://via.placeholder.com/400x600/cccccc/666666?text=Video+Thumbnail" else (cachedUrl ?: "https://via.placeholder.com/400x600/cccccc/666666?text=Image+Not+Available"),
                        videoUrl = if (isVideo) cachedUrl else null,
                        creator = "Network",
                        type = getFileType(file.fileName),
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
                    // It's a media file
                    // Extract share name from mediaSource.path
                    val selectedPath = mediaSource.path ?: ""
                    val shareName = selectedPath.split("/").firstOrNull() ?: ""
                    val smbUrl = "smb://${mediaSource.host}/$shareName/$filePath"
                    
                    // Download and cache the file, or use existing cache
                    val cachedUrl = downloadAndCacheFile(smbUrl, filePath)
                    
                    println("DEBUG: File: ${file.fileName}, Cached URL: $cachedUrl")
                    
                    val isVideo = getFileType(file.fileName) == "Video"
                    val mediaItem = MediaItem(
                        id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                        title = file.fileName,
                        imageUrl = if (isVideo) "https://via.placeholder.com/400x600/cccccc/666666?text=Video+Thumbnail" else (cachedUrl ?: "https://via.placeholder.com/400x600/cccccc/666666?text=Image+Not+Available"),
                        videoUrl = if (isVideo) cachedUrl else null,
                        creator = "Network",
                        type = getFileType(file.fileName),
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
            println("DEBUG: Starting download for: $filePath")
            println("DEBUG: SMB URL: $smbUrl")

            val androidContext = context ?: return@withContext null

            // Create cache directory
            val cacheDir = File(androidContext.cacheDir, "smb_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
                println("DEBUG: Created cache directory: ${cacheDir.absolutePath}")
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
        }
    }
}
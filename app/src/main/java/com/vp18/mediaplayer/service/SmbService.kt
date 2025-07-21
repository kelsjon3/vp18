package com.vp18.mediaplayer.service

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
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
                    val cachedUrl = getCachedFileUrl(smbUrl, file.fileName)
                    
                    mediaItems.add(
                        MediaItem(
                            id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                            title = file.fileName,
                            imageUrl = cachedUrl ?: smbUrl, // Use cached URL if available, otherwise SMB URL as fallback
                            creator = "Network",
                            type = getFileType(file.fileName),
                            source = mediaSource
                        )
                    )
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
                    val cachedUrl = getCachedFileUrl(smbUrl, file.fileName)
                    
                    mediaItems.add(
                        MediaItem(
                            id = "smb_${mediaSource.id}_${filePath.replace("/", "_")}",
                            title = file.fileName,
                            imageUrl = cachedUrl ?: smbUrl, // Use cached URL if available, otherwise SMB URL as fallback
                            creator = "Network",
                            type = getFileType(file.fileName),
                            source = mediaSource
                        )
                    )
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
    
    private fun getCachedFileUrl(smbUrl: String, fileName: String): String? {
        val appContext = context ?: return null
        
        // Create a unique cache filename based on SMB URL hash
        val urlHash = smbUrl.hashCode().toString()
        val extension = fileName.substringAfterLast('.', "")
        val cacheFileName = "${urlHash}.${extension}"
        
        val cacheDir = File(appContext.cacheDir, "smb_cache")
        val cachedFile = File(cacheDir, cacheFileName)
        
        return if (cachedFile.exists()) {
            "file://${cachedFile.absolutePath}"
        } else {
            null
        }
    }
    
    suspend fun downloadAndCacheFile(smbUrl: String, filePath: String): String? = withContext(Dispatchers.IO) {
        // TODO: Implement file caching in future version
        // For now, just return the SMB URL as fallback
        println("DEBUG: Cache download not implemented yet for: $filePath")
        null
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
package com.vp18.mediaplayer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vp18.mediaplayer.data.MediaSource
import com.vp18.mediaplayer.service.SmbFolderItem
import com.vp18.mediaplayer.service.SmbService
import kotlinx.coroutines.launch

@Composable
fun SmbFolderBrowserDialog(
    mediaSource: MediaSource,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf("") }
    var currentShare by remember { mutableStateOf<String?>(null) }
    var folderItems by remember { mutableStateOf<List<SmbFolderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    val smbService = remember { SmbService() }
    
    // Load initial folder contents (start with shares list)
    LaunchedEffect(mediaSource) {
        scope.launch {
            isLoading = true
            connectionError = null
            try {
                // Connect to server without specifying a share
                if (smbService.connect(mediaSource, connectToShare = false)) {
                    val items = smbService.listFolders(mediaSource, currentPath)
                    folderItems = items
                } else {
                    connectionError = "Failed to connect to SMB server"
                }
            } catch (e: Exception) {
                connectionError = "Connection error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Function to navigate to a folder or share
    fun navigateToFolder(path: String) {
        scope.launch {
            isLoading = true
            try {
                when {
                    path == "SHARES_ROOT" -> {
                        // Go back to shares listing
                        currentShare = null
                        currentPath = ""
                        val items = smbService.listShares()
                        folderItems = items
                    }
                    currentShare == null -> {
                        // This is a share selection
                        if (smbService.connectToShare(path)) {
                            currentShare = path
                            currentPath = ""
                            val items = smbService.listFolders(mediaSource, "")
                            folderItems = items
                        } else {
                            connectionError = "Failed to connect to share: $path"
                        }
                    }
                    else -> {
                        // This is folder navigation within a share
                        val items = smbService.listFolders(mediaSource, path)
                        folderItems = items
                        currentPath = path
                    }
                }
            } catch (e: Exception) {
                connectionError = "Failed to browse: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            smbService.close()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Browse Network Folder")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        currentShare == null -> "Select a share"
                        currentPath.isEmpty() -> "/$currentShare"
                        else -> "/$currentShare/$currentPath"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    connectionError != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = connectionError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    folderItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No folders found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(folderItems) { item ->
                                FolderItem(
                                    item = item,
                                    onClick = {
                                        if (item.isDirectory) {
                                            navigateToFolder(item.path)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullPath = when {
                        currentShare == null -> ""
                        currentPath.isEmpty() -> currentShare!!
                        else -> "$currentShare/$currentPath"
                    }
                    onFolderSelected(fullPath)
                    onDismiss()
                },
                enabled = currentShare != null
            ) {
                Text("Select This Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FolderItem(
    item: SmbFolderItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDirectory) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.name == "..") {
                    Icons.Default.FolderOpen
                } else if (item.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.Folder
                },
                contentDescription = if (item.isDirectory) "Folder" else "File",
                tint = if (item.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.name == "..") FontWeight.Bold else FontWeight.Normal,
                color = if (item.isDirectory) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
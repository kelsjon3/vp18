package com.vp18.mediaplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vp18.mediaplayer.R
import com.vp18.mediaplayer.data.MediaSource
import com.vp18.mediaplayer.data.SourceType
import com.vp18.mediaplayer.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    
    val sources by viewModel.sources.collectAsState()
    val civitaiUsername by viewModel.civitaiUsername.collectAsState()
    var showCivitaiDialog by remember { mutableStateOf(false) }
    var civitaiApiKey by remember { mutableStateOf("") }
    var sourceToDelete by remember { mutableStateOf<MediaSource?>(null) }
    var sourceToEdit by remember { mutableStateOf<MediaSource?>(null) }
    
    var showNetworkDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showFolderBrowser by remember { mutableStateOf(false) }
    var networkName by remember { mutableStateOf("") }
    var networkHost by remember { mutableStateOf("") }
    var networkPath by remember { mutableStateOf("") }
    var networkUsername by remember { mutableStateOf("") }
    var networkPassword by remember { mutableStateOf("") }
    var networkDomain by remember { mutableStateOf("") }
    var includeSubfolders by remember { mutableStateOf(false) }
    
    // Edit dialog state variables
    var editNetworkName by remember { mutableStateOf("") }
    var editNetworkHost by remember { mutableStateOf("") }
    var editNetworkPath by remember { mutableStateOf("") }
    var editNetworkUsername by remember { mutableStateOf("") }
    var editNetworkPassword by remember { mutableStateOf("") }
    var editNetworkDomain by remember { mutableStateOf("") }
    var editIncludeSubfolders by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.add_sources),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "v$versionName (Build $versionCode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.addDeviceFolder() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.device_folder))
                }
                
                Button(
                    onClick = { showNetworkDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.network_folder))
                }
                
                Button(
                    onClick = { showCivitaiDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.civitai))
                }
            }
            
            if (sources.isNotEmpty()) {
                Text(
                    text = "Current Sources:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources) { source ->
                        SourceItem(
                            source = source,
                            civitaiUsername = civitaiUsername,
                            onEdit = { 
                                if (source.type == SourceType.NETWORK_FOLDER) {
                                    sourceToEdit = source
                                    // Pre-fill edit dialog with current values
                                    editNetworkName = source.name
                                    editNetworkHost = source.host ?: ""
                                    editNetworkPath = source.path ?: ""
                                    editNetworkUsername = source.username ?: ""
                                    editNetworkPassword = source.password ?: ""
                                    editNetworkDomain = source.domain ?: ""
                                    editIncludeSubfolders = source.includeSubfolders
                                    showEditDialog = true
                                }
                            },
                            onRemove = { sourceToDelete = source }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNavigateToPlayer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }
                
                Button(
                    onClick = onNavigateToPlayer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
    
    if (showCivitaiDialog) {
        AlertDialog(
            onDismissRequest = { showCivitaiDialog = false },
            title = {
                Text(stringResource(R.string.civitai_api_key))
            },
            text = {
                OutlinedTextField(
                    value = civitaiApiKey,
                    onValueChange = { civitaiApiKey = it },
                    label = { Text(stringResource(R.string.enter_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (civitaiApiKey.isNotBlank()) {
                            viewModel.addCivitaiSource(civitaiApiKey)
                            showCivitaiDialog = false
                            civitaiApiKey = ""
                        }
                    }
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCivitaiDialog = false
                        civitaiApiKey = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    if (showNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            title = {
                Text("Add Network Folder")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = networkName,
                        onValueChange = { networkName = it },
                        label = { Text("Connection Name") },
                        placeholder = { Text("My Server") },
                        supportingText = { Text("Display name for this connection") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = networkHost,
                        onValueChange = { networkHost = it },
                        label = { Text("Host (IP Address or Hostname)") },
                        placeholder = { Text("192.168.1.100") },
                        supportingText = { Text("Server's IP address or hostname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (networkPath.isEmpty()) "Root folder" else networkPath,
                            onValueChange = { },
                            label = { Text("Selected Folder") },
                            singleLine = true,
                            readOnly = true,
                            modifier = Modifier.weight(1f)
                        )
                        
                        OutlinedButton(
                            onClick = {
                                if (networkHost.isNotBlank() && networkUsername.isNotBlank()) {
                                    showFolderBrowser = true
                                }
                            },
                            enabled = networkHost.isNotBlank() && networkUsername.isNotBlank()
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Browse")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Browse")
                        }
                    }
                    
                    OutlinedTextField(
                        value = networkUsername,
                        onValueChange = { networkUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = networkPassword,
                        onValueChange = { networkPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = networkDomain,
                        onValueChange = { networkDomain = it },
                        label = { Text("Domain (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = includeSubfolders,
                            onCheckedChange = { includeSubfolders = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Include subfolders",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (networkName.isNotBlank() && networkHost.isNotBlank()) {
                            viewModel.addNetworkFolder(
                                name = networkName,
                                host = networkHost,
                                path = networkPath,
                                username = networkUsername,
                                password = networkPassword,
                                domain = networkDomain,
                                includeSubfolders = includeSubfolders
                            )
                            showNetworkDialog = false
                            networkName = ""
                            networkHost = ""
                            networkPath = ""
                            networkUsername = ""
                            networkPassword = ""
                            networkDomain = ""
                            includeSubfolders = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNetworkDialog = false
                        networkName = ""
                        networkHost = ""
                        networkPath = ""
                        networkUsername = ""
                        networkPassword = ""
                        networkDomain = ""
                        includeSubfolders = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showFolderBrowser) {
        // Use edit mode values if in edit dialog, otherwise use add mode values
        val hostToUse = if (showEditDialog) editNetworkHost else networkHost
        val usernameToUse = if (showEditDialog) editNetworkUsername else networkUsername
        val passwordToUse = if (showEditDialog) editNetworkPassword else networkPassword
        val domainToUse = if (showEditDialog) editNetworkDomain else networkDomain
        
        if (hostToUse.isNotBlank()) {
            SmbFolderBrowserDialog(
                mediaSource = MediaSource(
                    id = "temp",
                    name = "temp", 
                    type = SourceType.NETWORK_FOLDER,
                    host = hostToUse,
                    username = usernameToUse,
                    password = passwordToUse,
                    domain = domainToUse
                ),
                onFolderSelected = { selectedPath ->
                    if (showEditDialog) {
                        editNetworkPath = selectedPath
                    } else {
                        networkPath = selectedPath
                    }
                    showFolderBrowser = false
                },
                onDismiss = {
                    showFolderBrowser = false
                }
            )
        }
    }
    
    sourceToDelete?.let { source ->
        AlertDialog(
            onDismissRequest = { sourceToDelete = null },
            title = {
                Text("Remove Source")
            },
            text = {
                Text("Are you sure you want to remove ${source.name}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSource(source)
                        sourceToDelete = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sourceToDelete = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Edit Network Folder Dialog
    if (showEditDialog && sourceToEdit != null) {
        AlertDialog(
            onDismissRequest = { 
                showEditDialog = false
                sourceToEdit = null
            },
            title = {
                Text("Edit Network Folder")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editNetworkName,
                        onValueChange = { editNetworkName = it },
                        label = { Text("Connection Name") },
                        placeholder = { Text("My Server") },
                        supportingText = { Text("Display name for this connection") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = editNetworkHost,
                        onValueChange = { editNetworkHost = it },
                        label = { Text("Host (IP Address or Hostname)") },
                        placeholder = { Text("192.168.1.100") },
                        supportingText = { Text("Server's IP address or hostname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editNetworkPath,
                            onValueChange = { editNetworkPath = it },
                            label = { Text("Path") },
                            placeholder = { Text("share/folder") },
                            supportingText = { Text("Folder path on the server") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { showFolderBrowser = true },
                            enabled = editNetworkHost.isNotBlank()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Browse folders"
                            )
                        }
                    }
                    
                    OutlinedTextField(
                        value = editNetworkUsername,
                        onValueChange = { editNetworkUsername = it },
                        label = { Text("Username") },
                        placeholder = { Text("username") },
                        supportingText = { Text("SMB/CIFS username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = editNetworkPassword,
                        onValueChange = { editNetworkPassword = it },
                        label = { Text("Password") },
                        placeholder = { Text("password") },
                        supportingText = { Text("SMB/CIFS password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = editNetworkDomain,
                        onValueChange = { editNetworkDomain = it },
                        label = { Text("Domain (Optional)") },
                        placeholder = { Text("domain") },
                        supportingText = { Text("Windows domain, if required") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = editIncludeSubfolders,
                            onCheckedChange = { editIncludeSubfolders = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Include subfolders",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editNetworkName.isNotBlank() && editNetworkHost.isNotBlank() && sourceToEdit != null) {
                            viewModel.updateNetworkFolder(
                                source = sourceToEdit!!,
                                name = editNetworkName,
                                host = editNetworkHost,
                                path = editNetworkPath,
                                username = editNetworkUsername,
                                password = editNetworkPassword,
                                domain = editNetworkDomain,
                                includeSubfolders = editIncludeSubfolders
                            )
                            showEditDialog = false
                            sourceToEdit = null
                            // Reset edit fields
                            editNetworkName = ""
                            editNetworkHost = ""
                            editNetworkPath = ""
                            editNetworkUsername = ""
                            editNetworkPassword = ""
                            editNetworkDomain = ""
                            editIncludeSubfolders = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        sourceToEdit = null
                        // Reset edit fields
                        editNetworkName = ""
                        editNetworkHost = ""
                        editNetworkPath = ""
                        editNetworkUsername = ""
                        editNetworkPassword = ""
                        editNetworkDomain = ""
                        editIncludeSubfolders = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SourceItem(
    source: MediaSource,
    civitaiUsername: String?,
    onEdit: () -> Unit = {},
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (source.path != null) {
                    Text(
                        text = source.path,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = when (source.type) {
                        SourceType.DEVICE_FOLDER -> "Device Folder"
                        SourceType.NETWORK_FOLDER -> "Network Folder"
                        SourceType.CIVITAI -> if (civitaiUsername != null) "Civitai API (@$civitaiUsername)" else "Civitai API"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (source.type == SourceType.CIVITAI && civitaiUsername == null) {
                    Text(
                        text = "No valid API Key provided. Expect filtered content.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Row {
                // Show edit button only for network folders
                if (source.type == SourceType.NETWORK_FOLDER) {
                    IconButton(
                        onClick = onEdit
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit source",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                IconButton(
                    onClick = onRemove
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove source",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
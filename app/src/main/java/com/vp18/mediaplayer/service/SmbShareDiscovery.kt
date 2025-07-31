package com.vp18.mediaplayer.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties

/**
 * SMB Share Discovery Service using jcifs-ng
 * 
 * This class uses jcifs-ng library specifically for share enumeration,
 * following the Nova Player approach where:
 * - jcifs-ng is used for share discovery (because it can actually list shares)
 * - smbj is used for file operations (better SMB 2.x/3.x performance)
 */
class SmbShareDiscovery private constructor(private val context: Context) {
    
    companion object {
        fun create(context: Context): SmbShareDiscovery? {
            println("DEBUG: SmbShareDiscovery.create() CALLED")
            return try {
                println("DEBUG: About to call SmbShareDiscovery constructor...")
                val instance = SmbShareDiscovery(context)
                println("DEBUG: SmbShareDiscovery constructor completed successfully")
                instance
            } catch (e: Exception) {
                println("DEBUG: SmbShareDiscovery creation FAILED: ${e.message}")
                println("DEBUG: Exception class: ${e.javaClass.simpleName}")
                e.printStackTrace()
                null
            }
        }
    }
    
    data class ShareInfo(
        val name: String,
        val type: String,
        val comment: String
    )
    
    private var cifsContext: CIFSContext? = null
    
    init {
        println("DEBUG: SmbShareDiscovery constructor STARTED")
        println("DEBUG: SmbShareDiscovery constructor completed - no initialization here")
    }
    
    suspend fun listShares(host: String, username: String, password: String, domain: String = ""): List<SmbFolderItem> = withContext(Dispatchers.IO) {
        println("DEBUG: SmbShareDiscovery.listShares() - Nova Player approach")
        println("DEBUG: Parameters - host: $host, username: $username, domain: $domain")
        
        try {
            // Step 1: Detect server protocol capability
            println("DEBUG: Getting SmbProtocolDetector instance...")
            val protocolDetector = SmbProtocolDetector.getInstance(context)
            println("DEBUG: SmbProtocolDetector instance created, starting protocol detection...")
            
            val supportsSMB2 = protocolDetector.detectProtocol(host, username, password, domain)
            
            println("DEBUG: Protocol detection completed for $host: ${when(supportsSMB2) {
                true -> "SMB2+ supported"
                false -> "SMB1 only"
                null -> "Detection failed"
            }}")
            
            // Step 2: Create appropriate context based on detection
            val context = createOptimalContext(supportsSMB2)
            val auth = NtlmPasswordAuthenticator(domain, username, password)
            val authContext = context.withCredentials(auth)
            
            // Step 3: Attempt share enumeration
            val serverUrl = "smb://$host/"
            println("DEBUG: Attempting share enumeration for: $serverUrl")
            
            val serverFile = SmbFile(serverUrl, authContext)
            val shares = serverFile.listFiles()
            
            println("DEBUG: Retrieved ${shares?.size ?: 0} items from server")
            
            // Step 4: Process and filter shares
            val folderItems = mutableListOf<SmbFolderItem>()
            
            shares?.forEach { share ->
                try {
                    val shareName = share.name.removeSuffix("/")
                    
                    if (share.isDirectory && !isAdministrativeShare(shareName)) {
                        folderItems.add(
                            SmbFolderItem(
                                name = shareName,
                                isDirectory = true,
                                path = shareName
                            )
                        )
                        println("DEBUG: ✓ Added share: $shareName")
                    } else {
                        println("DEBUG: ✗ Filtered out: $shareName")
                    }
                } catch (e: Exception) {
                    println("DEBUG: Error processing share ${share.name}: ${e.message}")
                }
            }
            
            return@withContext if (folderItems.isNotEmpty()) {
                println("DEBUG: Successfully enumerated ${folderItems.size} shares")
                folderItems.sortedBy { it.name }
            } else {
                println("DEBUG: No accessible shares found, providing fallback options")
                listOf(
                    SmbFolderItem(
                        name = "No accessible shares found",
                        isDirectory = false,
                        path = ""
                    ),
                    SmbFolderItem(
                        name = "Try entering share name manually",
                        isDirectory = false,
                        path = ""
                    )
                )
            }
            
        } catch (e: Exception) {
            println("DEBUG: Share enumeration failed with exception: ${e.javaClass.simpleName}")
            println("DEBUG: Exception message: ${e.message}")
            println("DEBUG: Full stack trace:")
            e.printStackTrace()
            
            // Fallback: Return error information
            return@withContext listOf(
                SmbFolderItem(
                    name = "Share enumeration failed",
                    isDirectory = false,
                    path = ""
                ),
                SmbFolderItem(
                    name = "Error: ${e.javaClass.simpleName}",
                    isDirectory = false,
                    path = ""
                ),
                SmbFolderItem(
                    name = "${e.message?.take(40) ?: "Unknown error"}",
                    isDirectory = false,
                    path = ""
                )
            )
        }
    }
    
    private fun createOptimalContext(supportsSMB2: Boolean?): CIFSContext {
        val props = Properties().apply {
            when (supportsSMB2) {
                true -> {
                    // SMB2+ optimized settings
                    setProperty("jcifs.smb.client.maxVersion", "SMB311")
                    setProperty("jcifs.smb.client.minVersion", "SMB202")
                    setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
                }
                false -> {
                    // SMB1 only settings
                    setProperty("jcifs.smb.client.maxVersion", "SMB1")
                    setProperty("jcifs.smb.client.minVersion", "SMB1")
                    setProperty("jcifs.smb.client.useSMB2Negotiation", "false")
                    setProperty("jcifs.smb.useRawNTLM", "true")
                }
                null -> {
                    // Hybrid mode - support both protocols
                    setProperty("jcifs.smb.client.enableSMB2", "true")
                    setProperty("jcifs.smb.client.useSMB311", "true")
                    setProperty("jcifs.smb.client.useSMB2Negotiation", "false")
                    setProperty("jcifs.smb.useRawNTLM", "true")
                }
            }
            
            // Universal compatibility settings
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.lmCompatibility", "3")
            setProperty("jcifs.smb.client.useExtendedSecurity", "true")
            setProperty("jcifs.netbios.hostname", "VP18_CLIENT")
            
            // Shorter timeouts for share enumeration
            setProperty("jcifs.smb.client.responseTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "5000")
        }
        
        val config = PropertyConfiguration(props)
        return BaseContext(config)
    }
    
    private fun isAdministrativeShare(shareName: String): Boolean {
        return shareName.endsWith("$") || 
               shareName.equals("IPC", ignoreCase = true) ||
               shareName.equals("ADMIN", ignoreCase = true) ||
               shareName.equals("print$", ignoreCase = true) ||
               shareName.equals("NETLOGON", ignoreCase = true) ||
               shareName.equals("SYSVOL", ignoreCase = true)
    }
    
    fun close() {
        cifsContext = null
        println("DEBUG: SmbShareDiscovery closed")
    }
}
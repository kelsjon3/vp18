package com.vp18.mediaplayer.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbException
import jcifs.smb.SmbAuthException
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * SMB Protocol Detector - Inspired by Nova Player's approach
 * 
 * Detects whether a server supports SMB1, SMB2, or both, and caches results
 * to avoid repeated probing of the same servers.
 */
class SmbProtocolDetector private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: SmbProtocolDetector? = null
        
        fun getInstance(context: Context): SmbProtocolDetector {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmbProtocolDetector(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Cache server capabilities: true = SMB2+, false = SMB1 only, null = unknown/failed
    private val serverCapabilities = ConcurrentHashMap<String, Boolean?>()
    private val serversBeingProbed = ConcurrentHashMap<String, Boolean>()
    
    // Cached contexts for different protocols
    private var smb1Context: CIFSContext? = null
    private var smb2Context: CIFSContext? = null
    
    /**
     * Detect SMB protocol version for a server
     * @param host Server hostname or IP
     * @param username Username (optional, defaults to guest)
     * @param password Password (optional)
     * @param domain Domain (optional)
     * @return true if SMB2+ supported, false if SMB1 only, null if detection failed
     */
    suspend fun detectProtocol(
        host: String, 
        username: String = "guest", 
        password: String = "", 
        domain: String = ""
    ): Boolean? = withContext(Dispatchers.IO) {
        
        val cacheKey = host.lowercase()
        
        // Return cached result if available
        serverCapabilities[cacheKey]?.let { return@withContext it }
        
        // Prevent multiple simultaneous probes of the same server
        if (serversBeingProbed[cacheKey] == true) {
            println("DEBUG: Server $host already being probed, returning cached result")
            return@withContext serverCapabilities[cacheKey]
        }
        
        serversBeingProbed[cacheKey] = true
        
        try {
            println("DEBUG: Detecting SMB protocol for $host")
            
            // Try SMB2+ first (most common these days)
            val smb2Result = tryProtocol(host, true, username, password, domain)
            if (smb2Result) {
                println("DEBUG: Server $host supports SMB2+")
                serverCapabilities[cacheKey] = true
                return@withContext true
            }
            
            // Fallback to SMB1
            val smb1Result = tryProtocol(host, false, username, password, domain)
            if (smb1Result) {
                println("DEBUG: Server $host supports SMB1 only")
                serverCapabilities[cacheKey] = false
                return@withContext false
            }
            
            println("DEBUG: Protocol detection failed for $host")
            serverCapabilities[cacheKey] = null
            return@withContext null
            
        } finally {
            serversBeingProbed[cacheKey] = false
        }
    }
    
    private suspend fun tryProtocol(
        host: String, 
        isSmb2: Boolean, 
        username: String, 
        password: String, 
        domain: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val context = getContext(isSmb2)
            val auth = NtlmPasswordAuthenticator(domain, username, password)
            val authContext = context.withCredentials(auth)
            
            val smbFile = SmbFile("smb://$host/", authContext)
            smbFile.listFiles() // This will throw if protocol not supported
            
            println("DEBUG: ${if (isSmb2) "SMB2+" else "SMB1"} works for $host")
            return@withContext true
            
        } catch (e: SmbAuthException) {
            // Authentication error doesn't mean protocol doesn't work
            println("DEBUG: ${if (isSmb2) "SMB2+" else "SMB1"} auth failed for $host, but protocol may work")
            return@withContext true
            
        } catch (e: SmbException) {
            println("DEBUG: ${if (isSmb2) "SMB2+" else "SMB1"} failed for $host: ${e.message}")
            return@withContext false
            
        } catch (e: Exception) {
            println("DEBUG: ${if (isSmb2) "SMB2+" else "SMB1"} error for $host: ${e.message}")
            return@withContext false
        }
    }
    
    private fun getContext(isSmb2: Boolean): CIFSContext {
        return if (isSmb2) {
            smb2Context ?: createSmb2Context().also { smb2Context = it }
        } else {
            smb1Context ?: createSmb1Context().also { smb1Context = it }
        }
    }
    
    private fun createSmb2Context(): CIFSContext {
        val props = Properties().apply {
            // SMB2+ only settings
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
            
            // Compatibility settings
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            
            // Network settings
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "5000")
        }
        
        val config = PropertyConfiguration(props)
        return BaseContext(config)
    }
    
    private fun createSmb1Context(): CIFSContext {
        val props = Properties().apply {
            // SMB1 only settings
            setProperty("jcifs.smb.client.maxVersion", "SMB1")
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.useSMB2Negotiation", "false")
            
            // Compatibility settings
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.disablePlainTextPasswords", "false")
            setProperty("jcifs.smb.useRawNTLM", "true") // Critical for SMB1
            
            // Network settings
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "20000")
            setProperty("jcifs.smb.client.connTimeout", "5000")
        }
        
        val config = PropertyConfiguration(props)
        return BaseContext(config)
    }
    
    /**
     * Get cached protocol result for a server
     */
    fun getCachedProtocol(host: String): Boolean? {
        return serverCapabilities[host.lowercase()]
    }
    
    /**
     * Clear the protocol cache (useful for testing or if network configuration changes)
     */
    fun clearCache() {
        serverCapabilities.clear()
        serversBeingProbed.clear()
    }
}
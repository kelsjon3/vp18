package com.vp18.mediaplayer.service

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Example usage of the improved SMB implementation
 * 
 * This demonstrates how to use the Nova Player-inspired SMB enumeration
 */
object SmbTestExample {
    
    fun testSmbEnumeration(context: Context, serverIP: String) {
        runBlocking {
            println("=== SMB Share Enumeration Test ===")
            println("Testing server: $serverIP")
            
            try {
                // Create SMB service instance
                val smbService = SmbService(context)
                
                // Create media source with server details
                val mediaSource = com.vp18.mediaplayer.data.MediaSource(
                    id = "test-smb",
                    name = "Test SMB Server",
                    type = com.vp18.mediaplayer.data.SourceType.NETWORK_FOLDER,
                    host = serverIP,
                    username = "guest", // or your username
                    password = "",      // or your password
                    domain = ""         // or your domain
                )
                
                // Step 1: Connect to the server
                println("Connecting to SMB server...")
                val connected = smbService.connect(mediaSource, connectToShare = false)
                
                if (connected) {
                    println("✓ Successfully connected to server")
                    
                    // Step 2: List available shares
                    println("Enumerating shares...")
                    val shares = smbService.listShares()
                    
                    if (shares.isNotEmpty()) {
                        println("✓ Found ${shares.size} shares:")
                        shares.forEach { share ->
                            if (share.isDirectory) {
                                println("  📁 ${share.name}")
                            } else {
                                println("  ℹ️  ${share.name}")
                            }
                        }
                    } else {
                        println("ℹ️  No shares found")
                    }
                    
                    // Step 3: Test connecting to the first available share
                    val firstShare = shares.firstOrNull { it.isDirectory }
                    if (firstShare != null) {
                        println("Testing connection to share: ${firstShare.name}")
                        val shareConnected = smbService.connectToShare(firstShare.name)
                        if (shareConnected) {
                            println("✓ Successfully connected to share: ${firstShare.name}")
                            
                            // Step 4: Test media file enumeration (no pre-caching)
                            println("Testing media file enumeration...")
                            val updatedMediaSource = mediaSource.copy(path = firstShare.name)
                            val mediaFiles = smbService.listMediaFiles(updatedMediaSource)
                            
                            println("Found ${mediaFiles.size} media files (not cached yet)")
                            
                            // Step 5: Test on-demand caching for first video
                            val firstVideo = mediaFiles.firstOrNull { it.type == "Video" && it.videoUrl != null }
                            if (firstVideo != null) {
                                println("Testing on-demand caching for: ${firstVideo.title}")
                                val cachedUrl = smbService.cacheFileOnDemand(firstVideo.videoUrl!!)
                                if (cachedUrl != null) {
                                    println("✓ File cached successfully: $cachedUrl")
                                } else {
                                    println("❌ Failed to cache file")
                                }
                            }
                        } else {
                            println("❌ Failed to connect to share: ${firstShare.name}")
                        }
                    }
                    
                } else {
                    println("❌ Failed to connect to SMB server")
                }
                
                // Clean up
                smbService.close()
                
            } catch (e: Exception) {
                println("❌ Test failed: ${e.message}")
                e.printStackTrace()
            }
            
            println("=== Test Complete ===")
        }
    }
    
    /**
     * Test protocol detection separately
     */
    fun testProtocolDetection(context: Context, serverIP: String) {
        runBlocking {
            println("=== SMB Protocol Detection Test ===")
            
            try {
                val detector = SmbProtocolDetector.getInstance(context)
                val result = detector.detectProtocol(serverIP, "guest", "", "")
                
                when (result) {
                    true -> println("✓ Server supports SMB2+")
                    false -> println("✓ Server supports SMB1 only")
                    null -> println("❌ Protocol detection failed")
                }
                
            } catch (e: Exception) {
                println("❌ Protocol detection error: ${e.message}")
            }
            
            println("=== Protocol Test Complete ===")
        }
    }
}
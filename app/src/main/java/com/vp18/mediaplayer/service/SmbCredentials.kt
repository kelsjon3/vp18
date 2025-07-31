package com.vp18.mediaplayer.service

/**
 * Simple credential container for SMB connections
 */
data class SmbCredentials(
    val domain: String = "",
    val username: String = "guest",
    val password: String = ""
) {
    companion object {
        fun guest() = SmbCredentials()
        
        fun create(domain: String?, username: String?, password: String?) = SmbCredentials(
            domain = domain?.trim() ?: "",
            username = username?.trim()?.ifEmpty { "guest" } ?: "guest",
            password = password ?: ""
        )
    }
    
    val isGuest: Boolean
        get() = username.equals("guest", ignoreCase = true) && password.isEmpty()
    
    override fun toString(): String {
        return if (isGuest) {
            "Guest credentials"
        } else {
            "User: ${if (domain.isNotEmpty()) "$domain\\" else ""}$username"
        }
    }
}
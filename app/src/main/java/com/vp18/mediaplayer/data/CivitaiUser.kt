package com.vp18.mediaplayer.data

data class FollowingUsersResponse(
    val result: FollowingUsersResult
)

data class FollowingUsersResult(
    val data: FollowingUsersData
)

data class FollowingUsersData(
    val json: List<FollowingUser>
)

data class FollowingUser(
    val id: Int,
    val username: String,
    val image: String?,
    val deletedAt: String?,
    val profilePicture: ProfilePicture?
)

data class ProfilePicture(
    val id: Int,
    val name: String,
    val url: String,
    val nsfwLevel: Int,
    val hash: String,
    val userId: Int,
    val ingestion: String,
    val type: String,
    val width: Int,
    val height: Int,
    val metadata: ProfilePictureMetadata
)

data class ProfilePictureMetadata(
    val hash: String,
    val size: Int,
    val width: Int,
    val height: Int,
    val userId: Int,
    val profilePicture: Boolean
) 
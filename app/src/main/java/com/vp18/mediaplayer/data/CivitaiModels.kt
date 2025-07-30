package com.vp18.mediaplayer.data

import com.google.gson.annotations.SerializedName

data class CivitaiResponse(
    @SerializedName("items") val items: List<CivitaiModel>,
    @SerializedName("metadata") val metadata: CivitaiMetadata
)

data class CivitaiImageResponse(
    @SerializedName("items") val items: List<CivitaiImage>,
    @SerializedName("metadata") val metadata: CivitaiMetadata
)

data class CivitaiMetadata(
    @SerializedName("totalItems") val totalItems: Int,
    @SerializedName("currentPage") val currentPage: Int,
    @SerializedName("pageSize") val pageSize: Int,
    @SerializedName("totalPages") val totalPages: Int,
    @SerializedName("nextCursor") val nextCursor: String?,
    @SerializedName("nextPage") val nextPage: String?
)

data class CivitaiModel(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("type") val type: String,
    @SerializedName("creator") val creator: CivitaiCreator,
    @SerializedName("modelVersions") val modelVersions: List<CivitaiModelVersion>
)

data class CivitaiCreator(
    @SerializedName("username") val username: String,
    @SerializedName("image") val image: String?
)

data class CivitaiModelVersion(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String?,
    @SerializedName("images") val images: List<CivitaiImage>
)

data class CivitaiImage(
    @SerializedName("id") val id: Int,
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("hash") val hash: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("metadata") val metadata: CivitaiImageMetadata?
)

data class CivitaiImageMetadata(
    @SerializedName("prompt") val prompt: String?,
    @SerializedName("negativePrompt") val negativePrompt: String?,
    @SerializedName("seed") val seed: Long?,
    @SerializedName("steps") val steps: Int?,
    @SerializedName("sampler") val sampler: String?
)

data class MediaSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val path: String? = null,
    val host: String? = null,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val includeSubfolders: Boolean = false
)

enum class SourceType {
    DEVICE_FOLDER,
    NETWORK_FOLDER,
    CIVITAI
}

data class CivitaiUser(
    @SerializedName("username") val username: String,
    @SerializedName("id") val id: Int
)

data class MediaItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val videoUrl: String? = null,
    val creator: String,
    val type: String,
    val source: MediaSource,
    val model: CivitaiModel? = null,
    val width: Int? = null,
    val height: Int? = null
) {
    val isVideo: Boolean
        get() = type == "Video" || videoUrl != null
}
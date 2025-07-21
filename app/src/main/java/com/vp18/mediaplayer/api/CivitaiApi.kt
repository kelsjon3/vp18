package com.vp18.mediaplayer.api

import com.vp18.mediaplayer.data.CivitaiResponse
import com.vp18.mediaplayer.data.CivitaiImageResponse
import com.vp18.mediaplayer.data.CivitaiUser
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CivitaiApi {
    @GET("models")
    suspend fun getModels(
        @Header("Authorization") authorization: String?,
        @Query("types") types: List<String>? = null,
        @Query("username") username: String? = null,
        @Query("sort") sort: String? = null,
        @Query("period") period: String? = null,
        @Query("nsfw") nsfw: Boolean = true,
        @Query("query") query: String? = null,
        @Query("tag") tag: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<CivitaiResponse>
    
    @GET("models")
    suspend fun getModelsWithApiKey(
        @Query("token") apiKey: String,
        @Query("types") types: List<String>? = null,
        @Query("username") username: String? = null,
        @Query("sort") sort: String? = null,
        @Query("period") period: String? = null,
        @Query("nsfw") nsfw: Boolean = true,
        @Query("query") query: String? = null,
        @Query("tag") tag: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<CivitaiResponse>
    
    @GET("images")
    suspend fun getImages(
        @Header("Authorization") authorization: String?,
        @Query("username") username: String? = null,
        @Query("sort") sort: String? = null,
        @Query("period") period: String? = null,
        @Query("nsfw") nsfw: Boolean = true
    ): Response<CivitaiImageResponse>
    
    @GET("me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String
    ): Response<CivitaiUser>
}
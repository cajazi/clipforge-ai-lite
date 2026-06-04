package com.clipforge.ai.data.remote.api
import com.clipforge.ai.data.remote.dto.MediaAssetDto
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*
interface MediaApi {
    @Multipart @POST("media/upload")
    suspend fun uploadMedia(@Part file: MultipartBody.Part, @Part("project_id") projectId: String, @Part("media_type") mediaType: String): Response<MediaAssetDto>
    @GET("media/{assetId}") suspend fun getAsset(@Path("assetId") assetId: String): Response<MediaAssetDto>
    @DELETE("media/{assetId}") suspend fun deleteAsset(@Path("assetId") assetId: String): Response<Unit>
}

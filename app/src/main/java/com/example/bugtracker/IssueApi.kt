package com.example.bugtracker

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class IssueDto(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val status: String? = null,
    val creationDate: Long? = null,
    val lastUpdated: Long? = null
)

interface IssueApi {

    @GET("issues")
    suspend fun getIssues(): Response<List<IssueDto>>

    @POST("issues")
    suspend fun createIssue(@Body issue: IssueDto): Response<IssueDto>

    @PUT("issues/{id}")
    suspend fun updateIssue(@Path("id") id: String, @Body issue: IssueDto): Response<IssueDto>

    @DELETE("issues/{id}")
    suspend fun deleteIssue(@Path("id") id: String): Response<ResponseBody>
}

object ApiClient {

    // CHANGE THIS to your own mockapi base URL. It must end with a slash.
    const val BASE_URL = "https://6aaf698dee9c55c910bf4699.mockapi.io/api/"

    val api: IssueApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IssueApi::class.java)
    }
}

fun Issue.toDto(): IssueDto = IssueDto(
    id = serverId,
    title = title,
    description = description,
    priority = priority,
    status = status,
    creationDate = creationDate,
    lastUpdated = lastUpdated
)

fun IssueDto.toIssue(localId: Int = 0): Issue = Issue(
    localId = localId,
    serverId = id,
    title = title ?: "",
    description = description ?: "",
    priority = priority ?: Priority.MEDIUM,
    status = status ?: Status.OPEN,
    creationDate = creationDate ?: System.currentTimeMillis(),
    lastUpdated = lastUpdated ?: 0L,
    syncStatus = SyncState.SYNCED
)
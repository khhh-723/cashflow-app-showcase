package com.codex.suishouledger.data.remote

import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.CategoryEntity
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ========== Auth ==========
    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    // ========== Transactions ==========
    @GET("api/transactions")
    suspend fun getTransactions(
        @Header("Authorization") token: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 50,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null
    ): Response<TransactionPage>

    @POST("api/transactions")
    suspend fun createTransaction(
        @Header("Authorization") token: String,
        @Body entry: TransactionRequest
    ): Response<LedgerEntryEntity>

    @PUT("api/transactions/{clientId}")
    suspend fun updateTransaction(
        @Header("Authorization") token: String,
        @Path("clientId") clientId: String,
        @Body entry: TransactionRequest
    ): Response<LedgerEntryEntity>

    @DELETE("api/transactions/{clientId}")
    suspend fun deleteTransaction(
        @Header("Authorization") token: String,
        @Path("clientId") clientId: String
    ): Response<Map<String, String>>

    @GET("api/transactions/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): Response<Map<String, Any>>

    // ========== Sync ==========
    @POST("api/sync/upload")
    suspend fun syncUpload(
        @Header("Authorization") token: String,
        @Body entries: List<LedgerEntryEntity>,
        @Header("X-CashFlow-Sync-Version") syncVersion: String = "2"
    ): Response<SyncResult>

    @GET("api/sync/download")
    suspend fun syncDownload(
        @Header("Authorization") token: String,
        @Query("since") since: Long,
        @Header("X-CashFlow-Sync-Version") syncVersion: String = "2"
    ): Response<List<LedgerEntryEntity>>

    // ========== Categories ==========
    @GET("api/categories")
    suspend fun getCategories(
        @Header("Authorization") token: String,
        @Query("isIncome") isIncome: Boolean = false
    ): Response<List<CategoryEntity>>

    // ========== Chat ==========
    @POST("api/chat/session")
    suspend fun createChatSession(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Response<ChatSessionResponse>

    @GET("api/chat/sessions")
    suspend fun getChatSessions(
        @Header("Authorization") token: String
    ): Response<List<ChatSessionResponse>>

    @GET("api/chat/session/{id}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Long
    ): Response<List<ChatMessageDto>>

    @POST("api/chat/session/{id}/message")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Path("id") sessionId: Long,
        @Body request: ChatMessageRequest
    ): Response<ChatMessageResponse>

    // ========== Health ==========
    @GET("api/health")
    suspend fun health(): Response<Map<String, Any>>
}

// ========== Data classes ==========

data class AuthRequest(
    val email: String,
    val password: String,
    val username: String? = null
)

data class AuthResponse(
    val token: String,
    val userId: Long,
    val username: String,
    val email: String
)

data class TransactionRequest(
    val clientId: String,
    val amountCents: Long,
    val transactionType: String,
    val categoryCode: String? = null,
    val categoryName: String? = null,
    val accountCode: String? = null,
    val accountName: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val sourceType: String? = "MANUAL",
    val reviewState: String? = "CONFIRMED",
    val occurredAt: Long
)

data class TransactionPage(
    val records: List<LedgerEntryEntity> = emptyList(),
    val total: Long = 0,
    val size: Long = 0,
    val current: Long = 0
)

data class SyncResult(
    val created: Int = 0,
    val updated: Int = 0
)

data class ChatSessionResponse(
    val id: Long = 0,
    val title: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)

data class ChatMessageRequest(
    val content: String,
    val ledgerContext: String? = null
)

data class ChatMessageDto(
    val id: Long = 0,
    val sessionId: Long = 0,
    val role: String = "",
    val content: String = "",
    val toolCalls: String? = null,
    val tokenCount: Int = 0,
    val createdAt: String = ""
)

data class ChatMessageResponse(
    val userMessage: ChatMessageDto? = null,
    val assistantMessage: ChatMessageDto? = null
)

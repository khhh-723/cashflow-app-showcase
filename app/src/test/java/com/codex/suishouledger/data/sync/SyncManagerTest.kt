package com.codex.suishouledger.data.sync

import com.codex.suishouledger.data.local.CategoryEntity
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.data.remote.ApiService
import com.codex.suishouledger.data.remote.AuthRequest
import com.codex.suishouledger.data.remote.AuthResponse
import com.codex.suishouledger.data.remote.ChatMessageDto
import com.codex.suishouledger.data.remote.ChatMessageRequest
import com.codex.suishouledger.data.remote.ChatMessageResponse
import com.codex.suishouledger.data.remote.ChatSessionResponse
import com.codex.suishouledger.data.remote.SyncResult
import com.codex.suishouledger.data.remote.TransactionPage
import com.codex.suishouledger.data.remote.TransactionRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SyncManagerTest {

    @Test
    fun uploadFilterOnlyIncludesConfirmedNonTransferEntries() {
        val draft = entry("draft", ReviewState.DRAFT, TransactionType.EXPENSE)
        val confirmedExpense = entry("expense", ReviewState.CONFIRMED, TransactionType.EXPENSE)
        val confirmedIncome = entry("income", ReviewState.CONFIRMED, TransactionType.INCOME)
        val confirmedTransfer = entry("transfer", ReviewState.CONFIRMED, TransactionType.TRANSFER)

        val result = filterUploadableSyncEntries(
            listOf(draft, confirmedExpense, confirmedIncome, confirmedTransfer)
        )

        assertEquals(listOf(confirmedExpense, confirmedIncome), result)
        assertFalse(result.contains(draft))
        assertFalse(result.contains(confirmedTransfer))
    }

    @Test
    fun backgroundSyncStopsBeforeDownloadWhenUploadFails() = runBlocking {
        val store = FakeSyncLedgerStore(
            listOf(entry("confirmed", ReviewState.CONFIRMED, TransactionType.EXPENSE))
        )
        val api = FakeApiService(uploadResponse = Response.error(500, "upload failed".toResponseBody()))
        val manager = syncManager(store, api)

        manager.syncInBackground()

        assertEquals(1, api.uploadCalls)
        assertEquals(0, api.downloadCalls)
    }

    @Test
    fun syncNowStopsBeforeDownloadWhenUploadFailsAndRecordsStatus() = runBlocking {
        val store = FakeSyncLedgerStore(
            listOf(entry("confirmed", ReviewState.CONFIRMED, TransactionType.EXPENSE))
        )
        val stateStore = FakeSyncStateStore(lastSync = 123L)
        val api = FakeApiService(uploadResponse = Response.error(500, "upload failed".toResponseBody()))
        val manager = syncManager(store, api, stateStore)

        val result = manager.syncNow()

        assertTrue(result.isFailure)
        assertEquals(1, api.uploadCalls)
        assertEquals(0, api.downloadCalls)
        assertEquals("同步失败", stateStore.lastSyncStatus.first())
        assertTrue(stateStore.lastSyncError.first().contains("Sync upload failed"))
    }

    @Test
    fun downloadUpsertsEntriesWithSameFingerprintWithoutDuplicating() = runBlocking {
        val local = entry("local-id", ReviewState.CONFIRMED, TransactionType.EXPENSE).copy(
            sourceFingerprint = "remote:fingerprint",
            amountCents = 100L,
            updatedAt = 100L
        )
        val remote = entry("remote-id", ReviewState.CONFIRMED, TransactionType.EXPENSE).copy(
            sourceFingerprint = "remote:fingerprint",
            amountCents = 2800L,
            updatedAt = 200L
        )
        val store = FakeSyncLedgerStore(listOf(local))
        val api = FakeApiService(downloadResponse = Response.success(listOf(remote)))
        val manager = syncManager(store, api)

        val result = manager.downloadRemoteChanges()
        val entries = store.allEntries.first()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow())
        assertEquals(1, entries.size)
        assertEquals("local-id", entries.single().id)
        assertEquals("remote:fingerprint", entries.single().sourceFingerprint)
        assertEquals(2800L, entries.single().amountCents)
    }

    @Test
    fun downloadUsesPersistedSinceAndAdvancesTimestamp() = runBlocking {
        val remote = entry("remote-id", ReviewState.CONFIRMED, TransactionType.EXPENSE).copy(
            updatedAt = 500L
        )
        val stateStore = FakeSyncStateStore(lastSync = 123L)
        val api = FakeApiService(downloadResponse = Response.success(listOf(remote)))
        val manager = syncManager(FakeSyncLedgerStore(emptyList()), api, stateStore)

        val result = manager.downloadRemoteChanges()

        assertTrue(result.isSuccess)
        assertEquals(123L, api.lastDownloadSince)
        assertEquals(500L, stateStore.lastSyncTimestampMillis.first())
    }

    @Test
    fun syncNowUploadsThenDownloadsAndRecordsSuccess() = runBlocking {
        val store = FakeSyncLedgerStore(
            listOf(entry("confirmed", ReviewState.CONFIRMED, TransactionType.EXPENSE))
        )
        val stateStore = FakeSyncStateStore(lastSync = 200L)
        val api = FakeApiService(
            uploadResponse = Response.success(SyncResult(created = 1, updated = 0)),
            downloadResponse = Response.success(
                listOf(entry("remote", ReviewState.CONFIRMED, TransactionType.EXPENSE).copy(updatedAt = 300L))
            )
        )
        val manager = syncManager(store, api, stateStore)

        val result = manager.syncNow()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().uploaded)
        assertEquals(1, result.getOrThrow().downloaded)
        assertEquals(200L, api.lastDownloadSince)
        assertEquals("同步完成", stateStore.lastSyncStatus.first())
        assertEquals("", stateStore.lastSyncError.first())
    }

    private fun syncManager(
        store: SyncLedgerStore,
        api: ApiService,
        stateStore: SyncStateStore = FakeSyncStateStore()
    ) = SyncManager(
        context = null,
        store = store,
        tokenProvider = FakeSyncAuthStore(),
        api = api,
        scope = CoroutineScope(Dispatchers.Unconfined),
        syncStateStore = stateStore
    )

    private fun entry(
        id: String,
        reviewState: ReviewState,
        transactionType: TransactionType
    ) = LedgerEntryEntity(
        id = id,
        reviewState = reviewState,
        ingestionState = IngestionState.RAW,
        transactionType = transactionType,
        sourceType = SourceType.MANUAL,
        sourceFingerprint = "sync-test:$id",
        occurredAt = 1_700_000_000_000L,
        amountCents = 100L,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )
}

private class FakeSyncLedgerStore(entries: List<LedgerEntryEntity>) : SyncLedgerStore {
    override val allEntries = MutableStateFlow(entries)

    override suspend fun upsertSyncedEntry(entry: LedgerEntryEntity): Boolean {
        val current = allEntries.value.toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
            .takeIf { it >= 0 }
            ?: current.indexOfFirst { it.sourceFingerprint == entry.sourceFingerprint }
        if (index < 0) {
            allEntries.value = current + entry
            return true
        }
        val existing = current[index]
        if (existing.updatedAt > entry.updatedAt) return false
        current[index] = entry.copy(
            id = existing.id,
            sourceFingerprint = existing.sourceFingerprint,
            createdAt = existing.createdAt
        )
        allEntries.value = current
        return true
    }
}

private class FakeSyncAuthStore : SyncAuthStore {
    override suspend fun getBearerToken(): String? = "Bearer token"
    override suspend fun clearExpiredAuth() = Unit
    override suspend fun isLoggedIn(): Boolean = true
}

private class FakeApiService(
    private val uploadResponse: Response<SyncResult> = Response.success(SyncResult()),
    private val downloadResponse: Response<List<LedgerEntryEntity>> = Response.success(emptyList())
) : ApiService {
    var uploadCalls = 0
        private set
    var downloadCalls = 0
        private set
    var lastDownloadSince: Long? = null
        private set
    var lastSyncVersion: String? = null
        private set

    override suspend fun syncUpload(
        token: String,
        entries: List<LedgerEntryEntity>,
        syncVersion: String
    ): Response<SyncResult> {
        uploadCalls += 1
        lastSyncVersion = syncVersion
        return uploadResponse
    }

    override suspend fun syncDownload(
        token: String,
        since: Long,
        syncVersion: String
    ): Response<List<LedgerEntryEntity>> {
        downloadCalls += 1
        lastDownloadSince = since
        lastSyncVersion = syncVersion
        return downloadResponse
    }

    override suspend fun register(request: AuthRequest): Response<AuthResponse> = unsupported()
    override suspend fun login(request: AuthRequest): Response<AuthResponse> = unsupported()
    override suspend fun getTransactions(
        token: String,
        page: Int,
        size: Int,
        startTime: Long?,
        endTime: Long?
    ): Response<TransactionPage> = unsupported()

    override suspend fun createTransaction(
        token: String,
        entry: TransactionRequest
    ): Response<LedgerEntryEntity> = unsupported()

    override suspend fun updateTransaction(
        token: String,
        clientId: String,
        entry: TransactionRequest
    ): Response<LedgerEntryEntity> = unsupported()

    override suspend fun deleteTransaction(token: String, clientId: String): Response<Map<String, String>> = unsupported()
    override suspend fun getStats(token: String): Response<Map<String, Any>> = unsupported()
    override suspend fun getCategories(token: String, isIncome: Boolean): Response<List<CategoryEntity>> = unsupported()
    override suspend fun createChatSession(token: String, body: Map<String, String>): Response<ChatSessionResponse> =
        unsupported()

    override suspend fun getChatSessions(token: String): Response<List<ChatSessionResponse>> = unsupported()
    override suspend fun getChatMessages(token: String, sessionId: Long): Response<List<ChatMessageDto>> = unsupported()
    override suspend fun sendChatMessage(
        token: String,
        sessionId: Long,
        request: ChatMessageRequest
    ): Response<ChatMessageResponse> = unsupported()

    override suspend fun health(): Response<Map<String, Any>> = unsupported()

    private fun <T> unsupported(): T = error("Not used in this test")
}

private class FakeSyncStateStore(lastSync: Long = 0L) : SyncStateStore {
    override val lastSyncTimestampMillis = MutableStateFlow(lastSync)
    override val lastSyncStatus = MutableStateFlow("Waiting")
    override val lastSyncError = MutableStateFlow("")

    override suspend fun setLastSyncTimestampMillis(value: Long) {
        lastSyncTimestampMillis.value = value
    }

    override suspend fun setLastSyncResult(status: String, error: String?) {
        lastSyncStatus.value = status
        lastSyncError.value = error.orEmpty()
    }
}

package com.codex.suishouledger.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.data.remote.ApiService
import com.codex.suishouledger.data.remote.RetrofitClient
import com.codex.suishouledger.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncManager(
    private val context: Context?,
    private val store: SyncLedgerStore,
    private val tokenProvider: SyncAuthStore,
    private val api: ApiService = RetrofitClient.apiService,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val syncStateStore: SyncStateStore = InMemorySyncStateStore()
) {
    suspend fun uploadLocalChanges(): Result<Int> {
        val token = tokenProvider.getBearerToken() ?: return Result.failure(Exception("Please login first"))
        return try {
            val allEntries = store.allEntries.first()
            val toUpload = filterUploadableSyncEntries(allEntries)
            if (toUpload.isEmpty()) return Result.success(0)

            val result = api.syncUpload(token, toUpload)
            if (result.isSuccessful) {
                val syncResult = result.body()
                Result.success((syncResult?.created ?: 0) + (syncResult?.updated ?: 0))
            } else if (result.code().isAuthExpiredCode()) {
                tokenProvider.clearExpiredAuth()
                Result.failure(Exception("Login expired, please sign in again"))
            } else {
                Result.failure(Exception("Sync upload failed: ${result.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadRemoteChanges(since: Long? = null): Result<Int> {
        val token = tokenProvider.getBearerToken() ?: return Result.failure(Exception("Please login first"))
        return try {
            val effectiveSince = since ?: syncStateStore.lastSyncTimestampMillis.first()
            val result = api.syncDownload(token, effectiveSince)
            if (result.isSuccessful) {
                val entries = result.body() ?: emptyList()
                val changed = entries.count { entry ->
                    store.upsertSyncedEntry(entry)
                }
                val latestRemoteUpdatedAt = entries.maxOfOrNull { it.updatedAt }
                if (latestRemoteUpdatedAt != null) {
                    syncStateStore.setLastSyncTimestampMillis(maxOf(effectiveSince, latestRemoteUpdatedAt))
                }
                Result.success(changed)
            } else if (result.code().isAuthExpiredCode()) {
                tokenProvider.clearExpiredAuth()
                Result.failure(Exception("Login expired, please sign in again"))
            } else {
                Result.failure(Exception("Sync download failed: ${result.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncNow(): Result<SyncSummary> {
        if (!tokenProvider.isLoggedIn()) {
            val error = "Please login first"
            syncStateStore.setLastSyncResult("同步失败", error)
            return Result.failure(Exception(error))
        }

        syncStateStore.setLastSyncResult("同步中", null)
        val uploadResult = uploadLocalChanges()
        if (uploadResult.isFailure) {
            val error = uploadResult.exceptionOrNull()?.localizedMessage ?: "Upload failed"
            syncStateStore.setLastSyncResult("同步失败", error)
            return Result.failure(Exception(error))
        }

        val downloadResult = downloadRemoteChanges()
        if (downloadResult.isFailure) {
            val error = downloadResult.exceptionOrNull()?.localizedMessage ?: "Download failed"
            syncStateStore.setLastSyncResult("同步失败", error)
            return Result.failure(Exception(error))
        }

        val summary = SyncSummary(
            uploaded = uploadResult.getOrDefault(0),
            downloaded = downloadResult.getOrDefault(0)
        )
        syncStateStore.setLastSyncResult("同步完成", null)
        return Result.success(summary)
    }

    fun syncInBackground() {
        val appContext = context
        if (appContext != null) {
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                SyncWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>().build()
            )
            return
        }

        scope.launch {
            syncNow()
        }
    }

    private fun Int.isAuthExpiredCode(): Boolean = this == 401 || this == 403
}

data class SyncSummary(
    val uploaded: Int,
    val downloaded: Int
)

internal fun filterUploadableSyncEntries(entries: List<LedgerEntryEntity>) =
    entries.filter {
        it.reviewState == ReviewState.CONFIRMED &&
            it.transactionType != TransactionType.TRANSFER
    }

interface SyncLedgerStore {
    val allEntries: Flow<List<LedgerEntryEntity>>

    suspend fun upsertSyncedEntry(entry: LedgerEntryEntity): Boolean
}

interface SyncAuthStore {
    suspend fun getBearerToken(): String?

    suspend fun clearExpiredAuth()

    suspend fun isLoggedIn(): Boolean
}

interface SyncStateStore {
    val lastSyncTimestampMillis: Flow<Long>

    val lastSyncStatus: Flow<String>

    val lastSyncError: Flow<String>

    suspend fun setLastSyncTimestampMillis(value: Long)

    suspend fun setLastSyncResult(status: String, error: String?)
}

private class InMemorySyncStateStore : SyncStateStore {
    private val timestamp = MutableStateFlow(0L)
    private val status = MutableStateFlow("等待同步")
    private val error = MutableStateFlow("")

    override val lastSyncTimestampMillis: Flow<Long> = timestamp
    override val lastSyncStatus: Flow<String> = status
    override val lastSyncError: Flow<String> = error

    override suspend fun setLastSyncTimestampMillis(value: Long) {
        timestamp.value = value.coerceAtLeast(0L)
    }

    override suspend fun setLastSyncResult(status: String, error: String?) {
        this.status.value = status
        this.error.value = error.orEmpty()
    }
}

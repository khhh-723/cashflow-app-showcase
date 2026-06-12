package com.codex.suishouledger.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.suishouledger.ServiceLocator
import com.codex.suishouledger.ocr.HuaweiOcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OcrWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val entryId = inputData.getString(KEY_ENTRY_ID) ?: return@withContext Result.failure()
        val uriText = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val uri = Uri.parse(uriText)

        processOcrDraft(
            entryId = entryId,
            recognize = { HuaweiOcrEngine(applicationContext).recognize(uri) },
            store = ServiceLocator.repository
        )
    }

    companion object {
        const val KEY_ENTRY_ID = "entry_id"
        const val KEY_URI = "uri"
    }
}

internal suspend fun processOcrDraft(
    entryId: String,
    recognize: suspend () -> com.codex.suishouledger.ocr.OcrResult,
    store: com.codex.suishouledger.data.local.OcrDraftStore
): androidx.work.ListenableWorker.Result = withContext(Dispatchers.IO) {
    try {
        val result = recognize()
        if (result.text.isBlank()) {
            store.markOcrFailed(entryId)
        } else {
            store.replaceDraftFromOcr(entryId, result.text)
        }
        androidx.work.ListenableWorker.Result.success()
    } catch (_: Throwable) {
        store.markOcrFailed(entryId)
        androidx.work.ListenableWorker.Result.success()
    }
}

package com.codex.suishouledger.work

import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.OcrDraftStore
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrWorkerTest {
    @Test
    fun emptyTextMarksDraftFailedWithoutRetrying() = runBlocking {
        val store = FakeOcrDraftStore()
        val result = processOcrDraft(
            entryId = "entry-1",
            recognize = { OcrResult(text = "") },
            store = store
        )

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(listOf("entry-1"), store.failedIds)
        assertTrue(store.replacedTexts.isEmpty())
    }

    @Test
    fun engineExceptionMarksDraftFailedWithoutRetrying() = runBlocking {
        val store = FakeOcrDraftStore()
        val result = processOcrDraft(
            entryId = "entry-2",
            recognize = { throw IllegalStateException("boom") },
            store = store
        )

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertEquals(listOf("entry-2"), store.failedIds)
        assertTrue(store.replacedTexts.isEmpty())
    }

    @Test
    fun successfulTextUpdatesDraftOnce() = runBlocking {
        val store = FakeOcrDraftStore()
        val result = processOcrDraft(
            entryId = "entry-3",
            recognize = { OcrResult(text = "支付成功\n楼12.80") },
            store = store
        )

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        assertTrue(store.failedIds.isEmpty())
        assertEquals(listOf("支付成功\n楼12.80"), store.replacedTexts)
    }
}

private class FakeOcrDraftStore : OcrDraftStore {
    val failedIds = mutableListOf<String>()
    val replacedTexts = mutableListOf<String>()

    override suspend fun markOcrFailed(entryId: String) {
        failedIds += entryId
    }

    override suspend fun replaceDraftFromOcr(entryId: String, rawText: String): LedgerEntryEntity? {
        replacedTexts += rawText
        return LedgerEntryEntity(
            id = entryId,
            reviewState = ReviewState.DRAFT,
            ingestionState = IngestionState.OCR_DONE,
            transactionType = TransactionType.EXPENSE,
            sourceType = SourceType.ALBUM_IMAGE,
            sourceFingerprint = "test:$entryId",
            occurredAt = 1_700_000_000_000L,
            amountCents = 1L,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
    }
}

package com.codex.suishouledger.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryAiChatDraftTest {
    @Test
    fun confirmAiChatDraftDoesNotRewriteConfirmedEntryOnDuplicateConfirm() {
        val now = 1_700_000_000_000L
        val existing = draft(
            id = "existing",
            fingerprint = "ai_chat:123",
            reviewState = ReviewState.CONFIRMED,
            needsReview = false,
            merchant = "原始商户",
            amountCents = 1_280L
        )
        val incoming = existing.copy(
            merchant = "被覆盖商户",
            amountCents = 9_990L,
            sourceFingerprint = "ai_chat:123:new"
        )

        val merged = mergeAiChatDraft(existing, incoming, now)

        assertEquals(existing.id, merged.id)
        assertEquals("原始商户", merged.merchant)
        assertEquals(1_280L, merged.amountCents)
        assertEquals(existing.sourceFingerprint, merged.sourceFingerprint)
        assertEquals(ReviewState.CONFIRMED, merged.reviewState)
        assertFalse(merged.needsReview)
        assertEquals(existing.createdAt, merged.createdAt)
    }

    @Test
    fun confirmAiChatDraftKeepsNewDraftAsDraftForFirstConfirm() {
        val now = 1_700_000_000_000L
        val incoming = draft(
            id = "new",
            fingerprint = "ai_chat:new",
            reviewState = ReviewState.CONFIRMED,
            needsReview = false,
            merchant = "新商户",
            amountCents = 2_350L
        )

        val merged = mergeAiChatDraft(null, incoming, now)

        assertEquals("new", merged.id)
        assertEquals("新商户", merged.merchant)
        assertEquals(2_350L, merged.amountCents)
        assertEquals(ReviewState.DRAFT, merged.reviewState)
        assertTrue(merged.needsReview)
        assertEquals(now, merged.updatedAt)
    }

    @Test
    fun confirmAiChatDraftRefreshesExistingDraftWithoutChangingFingerprint() {
        val now = 1_700_000_000_000L
        val existing = draft(
            id = "draft",
            fingerprint = "ai_chat:draft",
            reviewState = ReviewState.DRAFT,
            needsReview = true,
            merchant = "旧商户",
            amountCents = 1_000L
        )
        val incoming = existing.copy(
            merchant = "新商户",
            amountCents = 1_500L,
            sourceFingerprint = "ai_chat:draft:new"
        )

        val merged = mergeAiChatDraft(existing, incoming, now)

        assertEquals(existing.id, merged.id)
        assertEquals("新商户", merged.merchant)
        assertEquals(1_500L, merged.amountCents)
        assertEquals(existing.sourceFingerprint, merged.sourceFingerprint)
        assertEquals(ReviewState.DRAFT, merged.reviewState)
        assertTrue(merged.needsReview)
    }

    private fun draft(
        id: String,
        fingerprint: String,
        reviewState: ReviewState,
        needsReview: Boolean,
        merchant: String,
        amountCents: Long
    ) = LedgerEntryEntity(
        id = id,
        reviewState = reviewState,
        ingestionState = IngestionState.RAW,
        transactionType = TransactionType.EXPENSE,
        sourceType = SourceType.AI_CHAT,
        sourceFingerprint = fingerprint,
        occurredAt = 1_700_000_000_000L,
        amountCents = amountCents,
        merchant = merchant,
        needsReview = needsReview,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )
}

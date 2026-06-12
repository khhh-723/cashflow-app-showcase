package com.codex.suishouledger

import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerUiCalculationsTest {
    @Test
    fun amountExpressionAddsMultipleSmallExpenses() {
        assertEquals(2350L, evaluateAmountExpressionCents("12+8.5+3"))
        assertEquals(2500L, evaluateAmountExpressionCents("30-5"))
        assertEquals(1200L, evaluateAmountExpressionCents("6*2"))
        assertEquals(800L, evaluateAmountExpressionCents("24/3"))
    }

    @Test
    fun amountExpressionRejectsInvalidValues() {
        assertNull(evaluateAmountExpressionCents(""))
        assertNull(evaluateAmountExpressionCents("5+"))
        assertNull(evaluateAmountExpressionCents("5/0"))
        assertNull(evaluateAmountExpressionCents("5-8"))
        assertNull(evaluateAmountExpressionCents("1;2"))
    }

    @Test
    fun monthSelectionOptionsIncludeCurrentAdjacentSelectedAndEntryMonths() {
        val current = uiMonthStartMillis(1717200000000L)
        val selected = uiShiftMonth(current, -3)
        val entryMonth = uiShiftMonth(current, -7)
        val options = buildMonthSelectionOptions(
            entries = listOf(entry("old", entryMonth, categoryCode = "food")),
            selectedMonthStartMillis = selected,
            nowMillis = current
        )

        assertTrue(options.contains(current))
        assertTrue(options.contains(uiShiftMonth(current, -1)))
        assertTrue(options.contains(uiShiftMonth(current, 1)))
        assertTrue(options.contains(selected))
        assertTrue(options.contains(uiShiftMonth(selected, -1)))
        assertTrue(options.contains(uiShiftMonth(selected, 1)))
        assertTrue(options.contains(entryMonth))
        assertEquals(options.distinct().size, options.size)
    }

    @Test
    fun yearPickerOptionsIncludeCurrentRangeSelectedAndEntryYears() {
        val current = uiMonthStartMillis(2026, 6)
        val selected = uiMonthStartMillis(2022, 8)
        val entryMonth = uiMonthStartMillis(2020, 1)

        val options = buildYearPickerOptions(
            entries = listOf(entry("entry-year", entryMonth, categoryCode = "food")),
            selectedMonthStartMillis = selected,
            nowMillis = current
        )

        assertTrue(options.contains(2026))
        assertTrue(options.contains(2021))
        assertTrue(options.contains(2027))
        assertTrue(options.contains(2022))
        assertTrue(options.contains(2020))
        assertEquals(options.distinct().size, options.size)
    }

    @Test
    fun categoryDetailOnlyShowsConfirmedCurrentMonthExpenses() {
        val month = uiMonthStartMillis(1717200000000L)
        val nextMonth = uiShiftMonth(month, 1)
        val entries = listOf(
            entry("match", month + 1000L, categoryCode = "food"),
            entry("draft", month + 2000L, categoryCode = "food", reviewState = ReviewState.DRAFT),
            entry("income", month + 3000L, categoryCode = "food", type = TransactionType.INCOME),
            entry("other-category", month + 4000L, categoryCode = "coffee"),
            entry("other-month", nextMonth + 5000L, categoryCode = "food")
        )

        val result = filterMonthCategoryExpenses(entries, "food", month)

        assertEquals(listOf("match"), result.map { it.id })
        assertFalse(result.any { it.reviewState != ReviewState.CONFIRMED })
    }

    private fun entry(
        id: String,
        occurredAt: Long,
        categoryCode: String,
        reviewState: ReviewState = ReviewState.CONFIRMED,
        type: TransactionType = TransactionType.EXPENSE
    ) = LedgerEntryEntity(
        id = id,
        reviewState = reviewState,
        ingestionState = IngestionState.RAW,
        transactionType = type,
        sourceType = SourceType.MANUAL,
        sourceFingerprint = "fingerprint-$id",
        occurredAt = occurredAt,
        amountCents = 100L,
        merchant = "测试商户",
        categoryCode = categoryCode,
        categoryNameSnapshot = categoryCode,
        needsReview = reviewState != ReviewState.CONFIRMED,
        createdAt = occurredAt,
        updatedAt = occurredAt
    )
}

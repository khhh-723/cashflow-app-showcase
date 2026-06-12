package com.codex.suishouledger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_entries",
    indices = [
        Index(value = ["sourceFingerprint"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["reviewState"]),
        Index(value = ["categoryCode"]),
        Index(value = ["accountCode"])
    ]
)
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val reviewState: ReviewState = ReviewState.DRAFT,
    val ingestionState: IngestionState = IngestionState.RAW,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val sourceType: SourceType = SourceType.MANUAL,
    val sourceFingerprint: String,
    val sourcePackage: String = "",
    val sourceAppName: String = "",
    val occurredAt: Long,
    val amountCents: Long = 0L,
    val currency: String = "CNY",
    val merchant: String = "",
    val categoryCode: String? = null,
    val categoryNameSnapshot: String? = null,
    @ColumnInfo(defaultValue = "'cash'") val accountCode: String = "cash",
    @ColumnInfo(defaultValue = "'现金'") val accountNameSnapshot: String = "现金",
    val note: String = "",
    val rawText: String = "",
    val imageUri: String? = null,
    val confidence: Float = 0f,
    val needsReview: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ReviewState {
    DRAFT,
    CONFIRMED,
    IGNORED
}

enum class IngestionState {
    RAW,
    OCR_PENDING,
    OCR_DONE,
    OCR_FAILED
}

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class SourceType {
    NOTIFICATION,
    ALBUM_IMAGE,
    SCREENSHOT_IMAGE,
    AI_CHAT,
    MANUAL
}

data class PeriodSummary(
    val label: String,
    val expenseCents: Long,
    val incomeCents: Long,
    val transferCents: Long,
    val count: Int
)

data class CategoryTotal(
    val categoryCode: String,
    val categoryName: String,
    val amountCents: Long,
    val count: Int
)

data class BudgetProgress(
    val categoryCode: String,
    val categoryName: String,
    val budgetCents: Long,
    val spentCents: Long
)

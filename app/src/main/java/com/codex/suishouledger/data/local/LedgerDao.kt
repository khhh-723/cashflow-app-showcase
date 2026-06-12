package com.codex.suishouledger.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY occurredAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE reviewState = 'DRAFT' ORDER BY occurredAt DESC, createdAt DESC")
    fun observeDrafts(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE reviewState = 'CONFIRMED' ORDER BY occurredAt DESC, createdAt DESC")
    fun observeConfirmed(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entries WHERE sourceFingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): LedgerEntryEntity?

    @Query("SELECT * FROM ledger_entries WHERE imageUri = :imageUri ORDER BY updatedAt DESC, createdAt DESC LIMIT 1")
    suspend fun findByImageUri(imageUri: String): LedgerEntryEntity?

    @Query(
        """
        SELECT * FROM ledger_entries
        WHERE sourceType IN ('ALBUM_IMAGE', 'SCREENSHOT_IMAGE')
            AND reviewState != 'IGNORED'
            AND amountCents = :amountCents
            AND merchant = :merchant
            AND ABS(occurredAt - :occurredAt) <= :windowMillis
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun findNearbyOcrDuplicate(
        amountCents: Long,
        merchant: String,
        occurredAt: Long,
        windowMillis: Long
    ): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<LedgerEntryEntity>)

    @Update
    suspend fun update(entry: LedgerEntryEntity)

    @Delete
    suspend fun delete(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        UPDATE ledger_entries
        SET reviewState = :state,
            needsReview = :needsReview,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateReviewState(id: String, state: ReviewState, needsReview: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE ledger_entries
        SET ingestionState = :state,
            reviewState = 'DRAFT',
            needsReview = 1,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateIngestionState(id: String, state: IngestionState, updatedAt: Long)

    @Query(
        """
        SELECT
            COALESCE(categoryCode, 'other') AS categoryCode,
            COALESCE(categoryNameSnapshot, '其他') AS categoryName,
            SUM(amountCents) AS amountCents,
            COUNT(*) AS count
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
            AND transactionType = 'EXPENSE'
            AND strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) =
                strftime('%Y-%m', datetime('now', 'localtime'))
        GROUP BY COALESCE(categoryCode, 'other'), COALESCE(categoryNameSnapshot, '其他')
        ORDER BY amountCents DESC
        """
    )
    fun observeExpenseCategoryTotals(): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT
            COALESCE(categoryCode, 'other_income') AS categoryCode,
            COALESCE(categoryNameSnapshot, '其他收入') AS categoryName,
            SUM(amountCents) AS amountCents,
            COUNT(*) AS count
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
            AND transactionType = 'INCOME'
            AND strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) =
                strftime('%Y-%m', datetime('now', 'localtime'))
        GROUP BY COALESCE(categoryCode, 'other_income'), COALESCE(categoryNameSnapshot, '其他收入')
        ORDER BY amountCents DESC
        """
    )
    fun observeIncomeCategoryTotals(): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT
            strftime('%Y-%m-%d', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS label,
            SUM(CASE WHEN transactionType = 'EXPENSE' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS expenseCents,
            SUM(CASE WHEN transactionType = 'INCOME' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS incomeCents,
            SUM(CASE WHEN transactionType = 'TRANSFER' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS transferCents,
            COUNT(*) AS count
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
        GROUP BY strftime('%Y-%m-%d', datetime(occurredAt / 1000, 'unixepoch', 'localtime'))
        ORDER BY label DESC
        """
    )
    fun observeDailySummaries(): Flow<List<PeriodSummary>>

    @Query(
        """
        SELECT
            strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS label,
            SUM(CASE WHEN transactionType = 'EXPENSE' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS expenseCents,
            SUM(CASE WHEN transactionType = 'INCOME' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS incomeCents,
            SUM(CASE WHEN transactionType = 'TRANSFER' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS transferCents,
            COUNT(*) AS count
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
        GROUP BY strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime'))
        ORDER BY label DESC
        """
    )
    fun observeMonthlySummaries(): Flow<List<PeriodSummary>>

    @Query(
        """
        SELECT
            strftime('%Y', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) AS label,
            SUM(CASE WHEN transactionType = 'EXPENSE' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS expenseCents,
            SUM(CASE WHEN transactionType = 'INCOME' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS incomeCents,
            SUM(CASE WHEN transactionType = 'TRANSFER' AND reviewState = 'CONFIRMED' THEN amountCents ELSE 0 END) AS transferCents,
            COUNT(*) AS count
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
        GROUP BY strftime('%Y', datetime(occurredAt / 1000, 'unixepoch', 'localtime'))
        ORDER BY label DESC
        """
    )
    fun observeYearlySummaries(): Flow<List<PeriodSummary>>

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0)
        FROM ledger_entries
        WHERE reviewState = 'CONFIRMED'
            AND transactionType = 'EXPENSE'
            AND strftime('%Y-%m', datetime(occurredAt / 1000, 'unixepoch', 'localtime')) =
                strftime('%Y-%m', datetime('now', 'localtime'))
        """
    )
    suspend fun getCurrentMonthConfirmedExpenseCents(): Long

    @Query(
        """
        UPDATE ledger_entries
        SET categoryCode = :newCode,
            categoryNameSnapshot = :newName,
            updatedAt = :updatedAt
        WHERE categoryCode = :oldCode
        """
    )
    suspend fun moveCategory(oldCode: String, newCode: String, newName: String, updatedAt: Long)
}

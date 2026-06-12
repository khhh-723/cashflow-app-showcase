package com.codex.suishouledger.data.local

import android.net.Uri
import com.codex.suishouledger.data.sync.SyncLedgerStore
import com.codex.suishouledger.domain.PaymentParser
import com.codex.suishouledger.domain.ParsedDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

interface OcrDraftStore {
    suspend fun markOcrFailed(entryId: String)
    suspend fun replaceDraftFromOcr(entryId: String, rawText: String): LedgerEntryEntity?
}

class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao
) : SyncLedgerStore, OcrDraftStore {
    override val allEntries: Flow<List<LedgerEntryEntity>> = ledgerDao.observeAll()
    val draftEntries: Flow<List<LedgerEntryEntity>> = ledgerDao.observeDrafts()
    val confirmedEntries: Flow<List<LedgerEntryEntity>> = ledgerDao.observeConfirmed()
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
    val expenseCategoryTotals: Flow<List<CategoryTotal>> = ledgerDao.observeExpenseCategoryTotals()
    val incomeCategoryTotals: Flow<List<CategoryTotal>> = ledgerDao.observeIncomeCategoryTotals()
    val dailySummaries: Flow<List<PeriodSummary>> = ledgerDao.observeDailySummaries()
    val monthlySummaries: Flow<List<PeriodSummary>> = ledgerDao.observeMonthlySummaries()
    val yearlySummaries: Flow<List<PeriodSummary>> = ledgerDao.observeYearlySummaries()

    suspend fun getCategoriesOnce(): List<CategoryEntity> = categoryDao.getAllOnce()

    suspend fun getAccountsOnce(): List<AccountEntity> = accountDao.getAllOnce()

    suspend fun getCurrentMonthConfirmedExpenseCents(): Long = ledgerDao.getCurrentMonthConfirmedExpenseCents()

    suspend fun findEntry(entryId: String): LedgerEntryEntity? = ledgerDao.findById(entryId)

    suspend fun insertNotificationDraft(
        sourcePackage: String,
        sourceAppName: String,
        title: String,
        body: String,
        postedAt: Long
    ): LedgerEntryEntity? = withContext(Dispatchers.IO) {
        val allCategories = getCategoriesOnce()
        val account = inferAccount(sourcePackage, sourceAppName)
        val parsed = PaymentParser.parseNotification(sourcePackage, title, body, postedAt, allCategories) ?: return@withContext null
        val rawText = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
        val fingerprint = PaymentParser.buildNotificationFingerprint(sourcePackage, postedAt, parsed, rawText)
        if (ledgerDao.findByFingerprint(fingerprint) != null) return@withContext null
        val enrichedParsed = applyAutoCategoryIfNeeded(parsed, rawText, allCategories)
        val entry = PaymentParser.buildDraftEntry(
            sourceType = SourceType.NOTIFICATION,
            sourceFingerprint = fingerprint,
            sourcePackage = sourcePackage,
            sourceAppName = sourceAppName,
            occurredAt = postedAt,
            parsed = enrichedParsed,
            rawText = rawText,
            accountCode = account.code,
            accountName = account.name
        )
        ledgerDao.insert(entry)
        entry
    }

    suspend fun insertImageDraft(
        imageUri: Uri,
        sourceType: SourceType,
        sourcePackage: String = "",
        sourceAppName: String = "",
        occurredAt: Long = System.currentTimeMillis()
    ): LedgerEntryEntity? = withContext(Dispatchers.IO) {
        val account = inferAccount(sourcePackage, sourceAppName, sourceType)
        val uriText = imageUri.toString()
        val existingByUri = ledgerDao.findByImageUri(uriText)
        if (existingByUri != null) {
            if (
                sourceType == SourceType.SCREENSHOT_IMAGE &&
                existingByUri.ingestionState != IngestionState.OCR_PENDING
            ) {
                return@withContext existingByUri
            }
            if (existingByUri.reviewState == ReviewState.DRAFT) {
                val refreshed = existingByUri.copy(
                    reviewState = ReviewState.DRAFT,
                    ingestionState = IngestionState.OCR_PENDING,
                    sourceType = sourceType,
                    sourcePackage = sourcePackage.ifBlank { existingByUri.sourcePackage },
                    sourceAppName = sourceAppName.ifBlank { existingByUri.sourceAppName },
                    accountCode = account.code,
                    accountNameSnapshot = account.name,
                    occurredAt = occurredAt,
                    amountCents = 0L,
                    merchant = "",
                    categoryCode = null,
                    categoryNameSnapshot = null,
                    transactionType = TransactionType.EXPENSE,
                    rawText = "",
                    confidence = 0f,
                    needsReview = true,
                    updatedAt = System.currentTimeMillis()
                )
                ledgerDao.update(refreshed)
                return@withContext refreshed
            }
            if (sourceType != SourceType.ALBUM_IMAGE) {
                return@withContext null
            }
        }
        val fingerprint = buildImageFingerprint(imageUri, sourceType)
        val entry = LedgerEntryEntity(
            id = UUID.randomUUID().toString(),
            reviewState = ReviewState.DRAFT,
            ingestionState = IngestionState.OCR_PENDING,
            transactionType = TransactionType.EXPENSE,
            sourceType = sourceType,
            sourceFingerprint = fingerprint,
            sourcePackage = sourcePackage,
            sourceAppName = sourceAppName,
            accountCode = account.code,
            accountNameSnapshot = account.name,
            occurredAt = occurredAt,
            amountCents = 0L,
            currency = "CNY",
            merchant = "",
            categoryCode = null,
            categoryNameSnapshot = null,
            note = "",
            rawText = "",
            imageUri = uriText,
            confidence = 0f,
            needsReview = true,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        ledgerDao.insert(entry)
        entry
    }

    suspend fun addManualEntry(entry: LedgerEntryEntity) {
        ledgerDao.insert(entry)
    }

    override suspend fun upsertSyncedEntry(entry: LedgerEntryEntity): Boolean = withContext(Dispatchers.IO) {
        val existing = ledgerDao.findById(entry.id)
            ?: ledgerDao.findByFingerprint(entry.sourceFingerprint)
        when {
            existing == null -> {
                ledgerDao.insert(entry)
                true
            }

            existing.updatedAt > entry.updatedAt -> false

            else -> {
                ledgerDao.update(
                    entry.copy(
                        id = existing.id,
                        sourceFingerprint = existing.sourceFingerprint,
                        createdAt = existing.createdAt
                    )
                )
                true
            }
        }
    }

    suspend fun confirmAiChatDraft(entry: LedgerEntryEntity): LedgerEntryEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = ledgerDao.findById(entry.id)
            ?: ledgerDao.findByFingerprint(entry.sourceFingerprint)

        if (existing?.reviewState == ReviewState.CONFIRMED) {
            return@withContext existing
        }

        val target = mergeAiChatDraft(existing, entry, now)
        if (existing == null) {
            ledgerDao.insert(target)
        } else {
            ledgerDao.update(target)
        }

        if (target.reviewState != ReviewState.CONFIRMED || target.needsReview) {
            ledgerDao.updateReviewState(target.id, ReviewState.CONFIRMED, false, now)
        }
        ledgerDao.findById(target.id)?.copy(
            reviewState = ReviewState.CONFIRMED,
            needsReview = false,
            updatedAt = now
        ) ?: target.copy(reviewState = ReviewState.CONFIRMED, needsReview = false, updatedAt = now)
    }

    suspend fun saveCategory(category: CategoryEntity) {
        categoryDao.insert(category)
    }

    suspend fun moveCategory(code: String, direction: Int) {
        if (direction == 0) return
        val current = categoryDao.findByCode(code) ?: return
        val siblings = categoryDao.getAllOnce()
            .filter { it.isIncome == current.isIncome }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name })
        val currentIndex = siblings.indexOfFirst { it.code == code }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + direction).coerceIn(0, siblings.lastIndex)
        if (targetIndex == currentIndex) return
        val reordered = siblings.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        val baseOrder = if (current.isIncome) 1000 else 0
        categoryDao.insertAll(
            reordered.mapIndexed { index, item ->
                item.copy(sortOrder = baseOrder + (index + 1) * 10)
            }
        )
    }

    suspend fun moveCategoryToIndex(code: String, targetIndex: Int) {
        val current = categoryDao.findByCode(code) ?: return
        val siblings = categoryDao.getAllOnce()
            .filter { it.isIncome == current.isIncome }
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name })
        val currentIndex = siblings.indexOfFirst { it.code == code }
        if (currentIndex == -1) return
        val boundedTarget = targetIndex.coerceIn(0, siblings.lastIndex)
        if (boundedTarget == currentIndex) return
        val reordered = siblings.toMutableList().apply {
            add(boundedTarget, removeAt(currentIndex))
        }
        val baseOrder = if (current.isIncome) 1000 else 0
        categoryDao.insertAll(
            reordered.mapIndexed { index, item ->
                item.copy(sortOrder = baseOrder + (index + 1) * 10)
            }
        )
    }

    suspend fun updateCategoryBudget(code: String, monthlyBudgetCents: Long?) {
        val current = categoryDao.findByCode(code) ?: return
        categoryDao.update(
            current.copy(
                monthlyBudgetCents = monthlyBudgetCents?.coerceAtLeast(0L)?.takeIf { it > 0L }
            )
        )
    }

    suspend fun saveAccount(account: AccountEntity) {
        accountDao.insert(account)
    }

    suspend fun moveAccount(code: String, direction: Int) {
        if (direction == 0) return
        val accounts = accountDao.getAllOnce().sortedWith(compareBy<AccountEntity> { it.sortOrder }.thenBy { it.name })
        val currentIndex = accounts.indexOfFirst { it.code == code }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + direction).coerceIn(0, accounts.lastIndex)
        if (targetIndex == currentIndex) return
        val reordered = accounts.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        accountDao.insertAll(
            reordered.mapIndexed { index, item ->
                item.copy(sortOrder = (index + 1) * 10)
            }
        )
    }

    suspend fun removeAccount(code: String) {
        accountDao.deleteCustomByCode(code)
    }

    suspend fun removeCategory(code: String) {
        categoryDao.deleteByCode(code)
    }

    suspend fun mergeCategory(oldCode: String, target: CategoryEntity) {
        ledgerDao.moveCategory(oldCode, target.code, target.name, System.currentTimeMillis())
        categoryDao.deleteByCode(oldCode)
    }

    suspend fun confirmEntry(entryId: String) {
        ledgerDao.updateReviewState(entryId, ReviewState.CONFIRMED, false, System.currentTimeMillis())
    }

    suspend fun ignoreEntry(entryId: String) {
        ledgerDao.updateReviewState(entryId, ReviewState.IGNORED, false, System.currentTimeMillis())
    }

    suspend fun updateEntry(entry: LedgerEntryEntity) {
        ledgerDao.update(entry)
    }

    suspend fun deleteEntry(entryId: String) {
        ledgerDao.deleteById(entryId)
    }

    suspend fun updateEntryFromOcr(entryId: String, rawText: String) {
        replaceDraftFromOcr(entryId, rawText)
    }

    override suspend fun markOcrFailed(entryId: String) = withContext(Dispatchers.IO) {
        ledgerDao.updateIngestionState(entryId, IngestionState.OCR_FAILED, System.currentTimeMillis())
    }

    override suspend fun replaceDraftFromOcr(entryId: String, rawText: String): LedgerEntryEntity? {
        val entry = ledgerDao.findById(entryId) ?: return null
        if (entry.sourceType == SourceType.SCREENSHOT_IMAGE &&
            !PaymentParser.looksLikePaymentScreenshotText(rawText)
        ) {
            val failed = entry.copy(
                ingestionState = IngestionState.OCR_FAILED,
                reviewState = ReviewState.DRAFT,
                rawText = rawText.ifBlank { entry.rawText },
                confidence = 0f,
                needsReview = true,
                updatedAt = System.currentTimeMillis()
            )
            ledgerDao.update(failed)
            return failed
        }
        val allCategories = getCategoriesOnce()
        val account = inferAccountFromOcrText(rawText, entry)
        val parsedEntries = PaymentParser.parseTextEntries(rawText, allCategories, allowSingleListEntry = true)
        val primaryParsed = parsedEntries.firstOrNull()
        val additionalParsed = parsedEntries.drop(1)
        val enrichedPrimary = applyAutoCategoryIfNeeded(primaryParsed, rawText, allCategories)
        val updated = PaymentParser.enrichFromOcr(entry, rawText, allCategories, enrichedPrimary).copy(
            accountCode = account.code,
            accountNameSnapshot = account.name
        )
        if (rawText.isBlank()) {
            val failed = updated.copy(reviewState = ReviewState.DRAFT)
            ledgerDao.update(failed)
            return failed
        }
        val contentFingerprint = PaymentParser.buildOcrFingerprint(
            rawText = updated.rawText,
            amountCents = updated.amountCents,
            merchant = updated.merchant
        )
        val existing = ledgerDao.findByFingerprint(contentFingerprint)
        if (existing != null && existing.id != entryId) {
            if (existing.reviewState == ReviewState.DRAFT || entry.sourceType != SourceType.ALBUM_IMAGE) {
                insertAdditionalOcrDrafts(existing, rawText, additionalParsed, account)
                ledgerDao.deleteById(entryId)
                return existing
            }
            val duplicateDraft = updated.copy(
                reviewState = ReviewState.DRAFT,
                sourceFingerprint = "$contentFingerprint:retry:${entryId.take(8)}"
            )
            ledgerDao.update(duplicateDraft)
            insertAdditionalOcrDrafts(duplicateDraft, rawText, additionalParsed, account)
            return duplicateDraft
        }
        insertAdditionalOcrDrafts(entry, rawText, additionalParsed, account)
        val nearbyDuplicate = if (updated.amountCents > 0 && updated.merchant.isNotBlank()) {
            ledgerDao.findNearbyOcrDuplicate(
                amountCents = updated.amountCents,
                merchant = updated.merchant,
                occurredAt = updated.occurredAt,
                windowMillis = OCR_DUPLICATE_WINDOW_MILLIS
            )
        } else {
            null
        }
        if (nearbyDuplicate != null && nearbyDuplicate.id != entryId) {
            if (nearbyDuplicate.reviewState == ReviewState.DRAFT || entry.sourceType != SourceType.ALBUM_IMAGE) {
                ledgerDao.deleteById(entryId)
                return nearbyDuplicate
            }
        }
        val deduped = updated.copy(
            reviewState = ReviewState.DRAFT,
            sourceFingerprint = contentFingerprint
        )
        ledgerDao.update(deduped)
        return deduped
    }

    private suspend fun insertAdditionalOcrDrafts(
        sourceEntry: LedgerEntryEntity,
        rawText: String,
        parsedEntries: List<ParsedDraft>,
        account: AccountEntity
    ) {
        if (parsedEntries.isEmpty()) return
        val now = System.currentTimeMillis()
        val newDrafts = mutableListOf<LedgerEntryEntity>()
        parsedEntries.forEachIndexed { index, parsed ->
            val amount = parsed.amountCents ?: return@forEachIndexed
            if (amount <= 0L || parsed.merchant.isBlank()) return@forEachIndexed
            val baseFingerprint = PaymentParser.buildOcrFingerprint(
                rawText = parsed.normalizedText,
                amountCents = amount,
                merchant = parsed.merchant
            )
            val fingerprint = "$baseFingerprint:item:${index + 2}"
            if (ledgerDao.findByFingerprint(fingerprint) != null) return@forEachIndexed
            val occurredAt = parsed.occurredAt ?: sourceEntry.occurredAt
            val nearbyDuplicate = ledgerDao.findNearbyOcrDuplicate(
                amountCents = amount,
                merchant = parsed.merchant,
                occurredAt = occurredAt,
                windowMillis = OCR_DUPLICATE_WINDOW_MILLIS
            )
            if (nearbyDuplicate != null) return@forEachIndexed
            newDrafts += PaymentParser.buildDraftEntry(
                sourceType = sourceEntry.sourceType,
                sourceFingerprint = fingerprint,
                sourcePackage = sourceEntry.sourcePackage,
                sourceAppName = sourceEntry.sourceAppName,
                occurredAt = sourceEntry.occurredAt,
                parsed = applyAutoCategoryIfNeeded(parsed, rawText, getCategoriesOnce()),
                rawText = rawText,
                imageUri = sourceEntry.imageUri,
                accountCode = account.code,
                accountName = account.name
            ).copy(
                ingestionState = IngestionState.OCR_DONE,
                reviewState = ReviewState.DRAFT,
                createdAt = now + index + 1,
                updatedAt = now + index + 1
            )
        }
        if (newDrafts.isNotEmpty()) {
            ledgerDao.insertAll(newDrafts)
        }
    }

    private fun buildImageFingerprint(uri: Uri, sourceType: SourceType): String {
        return when (sourceType) {
            SourceType.ALBUM_IMAGE -> "image:${sourceType.name.lowercase()}:$uri:${UUID.randomUUID()}"
            else -> "image:${sourceType.name.lowercase()}:$uri"
        }
    }

    private suspend fun applyAutoCategoryIfNeeded(
        parsed: ParsedDraft?,
        sourceText: String,
        categories: List<CategoryEntity>
    ): ParsedDraft? {
        if (parsed == null) return null
        val autoCategoryEnabled = com.codex.suishouledger.ServiceLocator.settings.autoCategoryEnabled.first()
        if (!autoCategoryEnabled) return parsed
        val suggestedCategory = com.codex.suishouledger.domain.CategorySuggestionEngine.suggestCode(sourceText, categories)
        return parsed.copy(
            categoryCode = parsed.categoryCode ?: suggestedCategory.categoryCode,
            categoryName = parsed.categoryName
                ?: parsed.categoryCode?.let { code -> categories.firstOrNull { it.code == code }?.name }
                ?: categories.firstOrNull { it.code == suggestedCategory.categoryCode }?.name
        )
    }

    private suspend fun inferAccount(
        sourcePackage: String,
        sourceAppName: String,
        sourceType: SourceType = SourceType.NOTIFICATION
    ): AccountEntity {
        val accounts = getAccountsOnce()
        val key = "$sourcePackage $sourceAppName".lowercase()
        val code = when {
            key.contains("tencent") || key.contains("wechat") || key.contains("微信") -> "wechat"
            key.contains("alipay") || key.contains("支付宝") -> "alipay"
            sourceType == SourceType.ALBUM_IMAGE || sourceType == SourceType.SCREENSHOT_IMAGE -> "wechat"
            else -> "cash"
        }
        return accounts.firstOrNull { it.code == code }
            ?: accounts.firstOrNull { it.code == "cash" }
            ?: AccountEntity("cash", "现金", "payments", "#64748B", AccountType.CASH, sortOrder = 40)
    }

    private suspend fun inferAccountFromOcrText(rawText: String, fallback: LedgerEntryEntity): AccountEntity {
        val accounts = getAccountsOnce()
        val key = rawText.lowercase()
        val code = when {
            key.contains("支付宝") ||
                key.contains("alipay") ||
                key.contains("支付消息") ||
                key.contains("账单分类") ||
                key.contains("计入收支") ||
                key.contains("蚂蚁") -> "alipay"
            key.contains("微信") || key.contains("wechat") || key.contains("财付通") -> "wechat"
            else -> fallback.accountCode.ifBlank { "cash" }
        }
        return accounts.firstOrNull { it.code == code }
            ?: accounts.firstOrNull { it.code == fallback.accountCode }
            ?: accounts.firstOrNull { it.code == "cash" }
            ?: AccountEntity("cash", "现金", "payments", "#64748B", AccountType.CASH, sortOrder = 40)
    }

    private companion object {
        const val OCR_DUPLICATE_WINDOW_MILLIS = 10 * 60 * 1000L
    }
}

internal fun mergeAiChatDraft(
    existing: LedgerEntryEntity?,
    entry: LedgerEntryEntity,
    now: Long
): LedgerEntryEntity {
    return when {
        existing == null -> entry.copy(
            reviewState = ReviewState.DRAFT,
            needsReview = true,
            updatedAt = now
        )
        existing.reviewState == ReviewState.CONFIRMED -> existing
        else -> entry.copy(
            id = existing.id,
            sourceFingerprint = existing.sourceFingerprint,
            reviewState = ReviewState.DRAFT,
            needsReview = true,
            createdAt = existing.createdAt,
            updatedAt = now
        )
    }
}


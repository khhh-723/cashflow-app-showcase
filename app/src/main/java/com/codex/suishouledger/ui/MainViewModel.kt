package com.codex.suishouledger.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codex.suishouledger.ServiceLocator
import com.codex.suishouledger.data.local.AccountEntity
import com.codex.suishouledger.data.local.AccountType
import com.codex.suishouledger.data.local.CategoryEntity
import com.codex.suishouledger.data.local.CategoryTotal
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.PeriodSummary
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import com.codex.suishouledger.domain.PaymentParser
import com.codex.suishouledger.monitoring.ScreenshotMonitorService
import com.codex.suishouledger.work.OcrWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MainUiState(
    val drafts: List<LedgerEntryEntity> = emptyList(),
    val confirmed: List<LedgerEntryEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val expenseCategoryTotals: List<CategoryTotal> = emptyList(),
    val incomeCategoryTotals: List<CategoryTotal> = emptyList(),
    val dailySummaries: List<PeriodSummary> = emptyList(),
    val monthlySummaries: List<PeriodSummary> = emptyList(),
    val yearlySummaries: List<PeriodSummary> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val notificationParsingEnabled: Boolean = true,
    val screenshotMonitoringEnabled: Boolean = false,
    val autoCategoryEnabled: Boolean = true,
    val budgetAlertEnabled: Boolean = true,
    val monthlyBudgetCents: Long = 0L,
    val monthlyTotalCents: Long = 0L,
    val monthlyBudgetLeftCents: Long = 0L,
    val monthlyBudgetUsage: Float = 0f,
    val draftCount: Int = 0,
    val confirmedCount: Int = 0
)

class MainViewModel : ViewModel() {
    private companion object {
        const val TAG = "MainViewModel"
    }

    private val repository = ServiceLocator.repository
    private val settings = ServiceLocator.settings

    private data class EntryBundle(
        val drafts: List<LedgerEntryEntity>,
        val confirmed: List<LedgerEntryEntity>,
        val categories: List<CategoryEntity>,
        val accounts: List<AccountEntity>
    )

    private data class SummaryBundle(
        val expenseCategoryTotals: List<CategoryTotal>,
        val incomeCategoryTotals: List<CategoryTotal>,
        val daily: List<PeriodSummary>,
        val monthly: List<PeriodSummary>,
        val yearly: List<PeriodSummary>
    )

    private data class CategoryTotalBundle(
        val expense: List<CategoryTotal>,
        val income: List<CategoryTotal>
    )

    private data class SettingBundle(
        val notificationParsingEnabled: Boolean,
        val screenshotMonitoringEnabled: Boolean,
        val autoCategoryEnabled: Boolean,
        val budgetAlertEnabled: Boolean,
        val monthlyBudgetCents: Long
    )

    private val entryBundle = combine(
        repository.draftEntries,
        repository.confirmedEntries,
        repository.categories,
        repository.accounts
    ) { drafts, confirmed, categories, accounts ->
        EntryBundle(drafts, confirmed, categories, accounts)
    }

    private val categoryTotalBundle = combine(
        repository.expenseCategoryTotals,
        repository.incomeCategoryTotals
    ) { expenseCategoryTotals, incomeCategoryTotals ->
        CategoryTotalBundle(expenseCategoryTotals, incomeCategoryTotals)
    }

    private val summaryBundle = combine(
        categoryTotalBundle,
        repository.dailySummaries,
        repository.monthlySummaries,
        repository.yearlySummaries
    ) { categoryTotals, daily, monthly, yearly ->
        SummaryBundle(categoryTotals.expense, categoryTotals.income, daily, monthly, yearly)
    }

    private val settingBundle = combine(
        settings.notificationParsingEnabled,
        settings.screenshotMonitoringEnabled,
        settings.autoCategoryEnabled,
        settings.budgetAlertEnabled,
        settings.monthlyBudgetCents
    ) { notifEnabled, shotEnabled, autoCategory, budgetAlertEnabled, monthlyBudgetCents ->
        SettingBundle(notifEnabled, shotEnabled, autoCategory, budgetAlertEnabled, monthlyBudgetCents)
    }

    val uiState = combine(entryBundle, summaryBundle, settingBundle) { entries, summaries, appSettings ->
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date())
        val monthlyTotal = summaries.monthly.firstOrNull { it.label == currentMonth }?.expenseCents ?: entries.confirmed.sumOf { entry ->
            if (
                entry.transactionType == TransactionType.EXPENSE &&
                SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(entry.occurredAt)) == currentMonth
            ) {
                entry.amountCents
            } else {
                0L
            }
        }
        MainUiState(
            drafts = entries.drafts,
            confirmed = entries.confirmed,
            categories = entries.categories,
            accounts = entries.accounts,
            expenseCategoryTotals = summaries.expenseCategoryTotals,
            incomeCategoryTotals = summaries.incomeCategoryTotals,
            dailySummaries = summaries.daily,
            monthlySummaries = summaries.monthly,
            yearlySummaries = summaries.yearly,
            notificationParsingEnabled = appSettings.notificationParsingEnabled,
            screenshotMonitoringEnabled = appSettings.screenshotMonitoringEnabled,
            autoCategoryEnabled = appSettings.autoCategoryEnabled,
            budgetAlertEnabled = appSettings.budgetAlertEnabled,
            monthlyBudgetCents = appSettings.monthlyBudgetCents,
            monthlyTotalCents = monthlyTotal,
            monthlyBudgetLeftCents = (appSettings.monthlyBudgetCents - monthlyTotal).coerceAtLeast(0L),
            monthlyBudgetUsage = if (appSettings.monthlyBudgetCents > 0L) {
                monthlyTotal.toFloat() / appSettings.monthlyBudgetCents.toFloat()
            } else {
                0f
            },
            draftCount = entries.drafts.size,
            confirmedCount = entries.confirmed.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            maybeNotifyBudgetAlert()
        }
    }

    fun toggleNotificationParsing(enabled: Boolean) {
        viewModelScope.launch { settings.setNotificationParsingEnabled(enabled) }
    }

    fun toggleScreenshotMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settings.setScreenshotMonitoringEnabled(enabled)
            val context = ServiceLocator.context
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            if (enabled) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to start screenshot monitor", throwable)
                    settings.setScreenshotMonitoringEnabled(false)
                }
            } else {
                context.stopService(intent)
            }
        }
    }

    fun toggleAutoCategory(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoCategoryEnabled(enabled) }
    }

    fun toggleBudgetAlert(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBudgetAlertEnabled(enabled)
            maybeNotifyBudgetAlert()
        }
    }

    fun setMonthlyBudget(cents: Long) {
        viewModelScope.launch {
            settings.setMonthlyBudgetCents(cents)
            maybeNotifyBudgetAlert()
        }
    }

    fun confirm(entryId: String) {
        viewModelScope.launch {
            repository.confirmEntry(entryId)
            maybeNotifyBudgetAlert()
        }
    }

    fun ignore(entryId: String) {
        viewModelScope.launch { repository.ignoreEntry(entryId) }
    }

    fun delete(entryId: String) {
        viewModelScope.launch {
            repository.deleteEntry(entryId)
            maybeNotifyBudgetAlert()
        }
    }

    fun saveEditedEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            repository.updateEntry(entry.copy(updatedAt = System.currentTimeMillis()))
            if (entry.reviewState == com.codex.suishouledger.data.local.ReviewState.CONFIRMED) {
                maybeNotifyBudgetAlert()
            }
        }
    }

    fun confirmEditedEntry(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            val updated = entry.copy(updatedAt = System.currentTimeMillis())
            repository.updateEntry(updated)
            repository.confirmEntry(updated.id)
            maybeNotifyBudgetAlert()
        }
    }

    fun retryOcr(entry: LedgerEntryEntity) {
        viewModelScope.launch {
            val uriText = entry.imageUri ?: return@launch
            val reset = entry.copy(
                ingestionState = IngestionState.OCR_PENDING,
                rawText = "",
                confidence = 0f,
                needsReview = true,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateEntry(reset)
            enqueueOcr(reset.id, Uri.parse(uriText))
        }
    }

    fun handlePickedImage(uri: Uri) {
        importImage(uri, SourceType.ALBUM_IMAGE)
    }

    fun startScreenshotMonitoringIfNeeded() {
        viewModelScope.launch {
            if (settings.screenshotMonitoringEnabled.first()) {
                val context = ServiceLocator.context
                val intent = Intent(context, ScreenshotMonitorService::class.java)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (throwable: Throwable) {
                    Log.e(TAG, "Failed to restart screenshot monitor", throwable)
                    settings.setScreenshotMonitoringEnabled(false)
                }
            }
        }
    }

    fun createManualEntry(
        amountCents: Long,
        merchant: String,
        categoryCode: String?,
        accountCode: String?,
        transactionType: TransactionType,
        note: String
    ) {
        viewModelScope.launch {
            val categories = repository.getCategoriesOnce()
            val accounts = repository.getAccountsOnce()
            val account = accounts.firstOrNull { it.code == accountCode }
                ?: accounts.firstOrNull { it.code == "cash" }
            repository.addManualEntry(
                PaymentParser.createManualDraft(
                    amountCents = amountCents,
                    merchant = merchant,
                    categoryCode = categoryCode,
                    categoryName = categories.firstOrNull { it.code == categoryCode }?.name,
                    transactionType = transactionType,
                    note = note,
                    categories = categories,
                    accountCode = account?.code ?: "cash",
                    accountName = account?.name ?: "现金"
                )
            )
            maybeNotifyBudgetAlert()
        }
    }

    fun saveCategory(name: String, isIncome: Boolean, colorHex: String = "#64748B", iconKey: String = "label") {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val nextSortOrder = repository.getCategoriesOnce()
                .filter { it.isIncome == isIncome }
                .maxOfOrNull { it.sortOrder }
                ?.plus(10)
                ?: if (isIncome) 1010 else 10
            repository.saveCategory(
                CategoryEntity(
                    code = "custom_${UUID.randomUUID().toString().take(8)}",
                    name = cleanName,
                    iconKey = iconKey.ifBlank { "label" },
                    colorHex = colorHex,
                    sortOrder = nextSortOrder,
                    isIncome = isIncome,
                    isSystem = false
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity, name: String, colorHex: String, iconKey: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank() || category.isSystem) return
        viewModelScope.launch {
            repository.saveCategory(
                category.copy(
                    name = cleanName,
                    iconKey = iconKey.ifBlank { category.iconKey },
                    colorHex = colorHex
                )
            )
        }
    }

    fun removeCategory(category: CategoryEntity) {
        if (category.isSystem) return
        viewModelScope.launch { repository.removeCategory(category.code) }
    }

    fun moveCategory(category: CategoryEntity, direction: Int) {
        viewModelScope.launch {
            repository.moveCategory(category.code, direction)
        }
    }

    fun moveCategoryToIndex(category: CategoryEntity, targetIndex: Int) {
        viewModelScope.launch {
            repository.moveCategoryToIndex(category.code, targetIndex)
        }
    }

    fun setCategoryBudget(code: String, cents: Long?) {
        viewModelScope.launch { repository.updateCategoryBudget(code, cents) }
    }

    fun saveAccount(
        name: String,
        type: AccountType = AccountType.OTHER,
        colorHex: String = "#64748B",
        iconKey: String = "account_balance_wallet"
    ) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val nextSortOrder = repository.getAccountsOnce()
                .maxOfOrNull { it.sortOrder }
                ?.plus(10)
                ?: 10
            repository.saveAccount(
                AccountEntity(
                    code = "custom_${UUID.randomUUID().toString().take(8)}",
                    name = cleanName,
                    iconKey = iconKey.ifBlank { "account_balance_wallet" },
                    colorHex = colorHex,
                    type = type,
                    sortOrder = nextSortOrder,
                    isSystem = false
                )
            )
        }
    }

    fun updateAccount(account: AccountEntity, name: String, colorHex: String, balanceCents: Long?, iconKey: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank() && !account.isSystem) return
        viewModelScope.launch {
            repository.saveAccount(
                account.copy(
                    name = if (account.isSystem) account.name else cleanName,
                    iconKey = iconKey.ifBlank { account.iconKey },
                    colorHex = colorHex,
                    balanceCents = balanceCents?.coerceAtLeast(0L)?.takeIf { it > 0L },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeAccount(code: String) {
        viewModelScope.launch { repository.removeAccount(code) }
    }

    fun moveAccount(account: AccountEntity, direction: Int) {
        viewModelScope.launch {
            repository.moveAccount(account.code, direction)
        }
    }

    fun mergeCategory(oldCode: String, targetCode: String) {
        viewModelScope.launch {
            val target = repository.getCategoriesOnce().firstOrNull { it.code == targetCode } ?: return@launch
            repository.mergeCategory(oldCode, target)
        }
    }

    fun importImage(uri: Uri, sourceType: SourceType) {
        viewModelScope.launch {
            val ocrUri = if (sourceType == SourceType.ALBUM_IMAGE) {
                copyImageIntoAppStorage(uri)
            } else {
                uri
            }
            val entry = repository.insertImageDraft(ocrUri, sourceType) ?: return@launch
            enqueueOcr(entry.id, ocrUri)
        }
    }

    private fun enqueueOcr(entryId: String, uri: Uri) {
        val work = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(
                workDataOf(
                    OcrWorker.KEY_ENTRY_ID to entryId,
                    OcrWorker.KEY_URI to uri.toString()
                )
            )
            .build()
        WorkManager.getInstance(ServiceLocator.context).enqueueUniqueWork(
            "ocr-$entryId",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    private suspend fun copyImageIntoAppStorage(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val context = ServiceLocator.context
        val importDir = File(context.filesDir, "ocr_imports").apply { mkdirs() }
        val target = File(importDir, "${UUID.randomUUID()}.jpg")
        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                val inputStream = requireNotNull(input) { "Unable to open image URI: $uri" }
                target.outputStream().use { output -> inputStream.copyTo(output) }
            }
            Uri.fromFile(target)
        }.getOrElse {
            target.delete()
            uri
        }
    }

    private suspend fun maybeNotifyBudgetAlert() {
        val monthlyBudgetCents = settings.monthlyBudgetCents.first()
        val monthlyExpenseCents = repository.getCurrentMonthConfirmedExpenseCents()
        ServiceLocator.budgetAlertNotifier.notifyIfNeeded(
            monthlyBudgetCents = monthlyBudgetCents,
            monthlyExpenseCents = monthlyExpenseCents
        )
    }
}

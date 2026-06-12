package com.codex.suishouledger

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.codex.suishouledger.data.local.AppDatabase
import com.codex.suishouledger.data.local.DefaultAccounts
import com.codex.suishouledger.data.local.DefaultCategories
import com.codex.suishouledger.data.local.LedgerRepository
import com.codex.suishouledger.data.settings.LedgerSettingsRepository
import com.codex.suishouledger.monitoring.BudgetAlertNotifier
import com.codex.suishouledger.ocr.HuaweiOcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ServiceLocator {
    private const val TAG = "ServiceLocator"

    private lateinit var appContext: Context
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultsSeedMutex = Mutex()
    @Volatile private var defaultsSeedingStarted = false

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "suishou_ledger.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val settings: LedgerSettingsRepository by lazy { LedgerSettingsRepository(appContext) }
    val budgetAlertNotifier: BudgetAlertNotifier by lazy { BudgetAlertNotifier(appContext, settings) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(database.ledgerDao(), database.categoryDao(), database.accountDao())
    }
    val ocrEngine: HuaweiOcrEngine by lazy { HuaweiOcrEngine(appContext) }
    val authTokenProvider: com.codex.suishouledger.data.remote.AuthTokenProvider by lazy {
        com.codex.suishouledger.data.remote.AuthTokenProvider(appContext)
    }
    val syncManager: com.codex.suishouledger.data.sync.SyncManager by lazy {
        com.codex.suishouledger.data.sync.SyncManager(appContext, repository, authTokenProvider, syncStateStore = settings)
    }
    val context: Context
        get() = appContext

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        if (!defaultsSeedingStarted) {
            defaultsSeedingStarted = true
            appScope.launch {
                runCatching { ensureDefaultsSeeded() }
                    .onFailure { throwable ->
                        defaultsSeedingStarted = false
                        Log.e(TAG, "Failed to seed default data on startup", throwable)
                    }
            }
        }
    }

    private suspend fun ensureDefaultsSeeded() {
        defaultsSeedMutex.withLock {
            val categoryDao = database.categoryDao()
            if (categoryDao.count() == 0) {
                categoryDao.insertAll(DefaultCategories.all())
            } else {
                DefaultCategories.all().filter { it.isSystem }.forEach { category ->
                    if (categoryDao.findByCode(category.code) == null) {
                        categoryDao.insert(category)
                    }
                }
            }
            database.ledgerDao().moveCategory("income", "other_income", "其他收入", System.currentTimeMillis())
            categoryDao.deleteByCode("income")
            val accountDao = database.accountDao()
            if (accountDao.count() == 0) {
                accountDao.insertAll(DefaultAccounts.all())
            }
        }
    }
}

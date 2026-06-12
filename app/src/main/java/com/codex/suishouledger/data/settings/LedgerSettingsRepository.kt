package com.codex.suishouledger.data.settings

import android.content.Context
import com.codex.suishouledger.data.sync.SyncStateStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ledgerDataStore by preferencesDataStore(name = "ledger_settings")

class LedgerSettingsRepository(private val context: Context) : SyncStateStore {
    private val dataStore = context.ledgerDataStore

    private object Keys {
        val notificationParsingEnabled = booleanPreferencesKey("notification_parsing_enabled")
        val screenshotMonitoringEnabled = booleanPreferencesKey("screenshot_monitoring_enabled")
        val autoCategoryEnabled = booleanPreferencesKey("auto_category_enabled")
        val budgetAlertEnabled = booleanPreferencesKey("budget_alert_enabled")
        val lastScreenshotScanSeconds = longPreferencesKey("last_screenshot_scan_seconds")
        val preferredCurrency = stringPreferencesKey("preferred_currency")
        val monthlyBudgetCents = longPreferencesKey("monthly_budget_cents")
        val lastBudgetAlertMarker = stringPreferencesKey("last_budget_alert_marker")
        val lastSyncTimestampMillis = longPreferencesKey("last_sync_timestamp_millis")
        val lastSyncStatus = stringPreferencesKey("last_sync_status")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    val notificationParsingEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.notificationParsingEnabled] ?: true }

    val screenshotMonitoringEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.screenshotMonitoringEnabled] ?: false }

    val autoCategoryEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.autoCategoryEnabled] ?: true }

    val budgetAlertEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.budgetAlertEnabled] ?: true }

    val lastScreenshotScanSeconds: Flow<Long> =
        dataStore.data.map { it[Keys.lastScreenshotScanSeconds] ?: 0L }

    val preferredCurrency: Flow<String> =
        dataStore.data.map { it[Keys.preferredCurrency] ?: "CNY" }

    val monthlyBudgetCents: Flow<Long> =
        dataStore.data.map { it[Keys.monthlyBudgetCents] ?: 0L }

    val lastBudgetAlertMarker: Flow<String> =
        dataStore.data.map { it[Keys.lastBudgetAlertMarker] ?: "" }

    override val lastSyncTimestampMillis: Flow<Long> =
        dataStore.data.map { it[Keys.lastSyncTimestampMillis] ?: 0L }

    override val lastSyncStatus: Flow<String> =
        dataStore.data.map { it[Keys.lastSyncStatus] ?: "等待同步" }

    override val lastSyncError: Flow<String> =
        dataStore.data.map { it[Keys.lastSyncError] ?: "" }

    suspend fun setNotificationParsingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.notificationParsingEnabled] = enabled }
    }

    suspend fun setScreenshotMonitoringEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.screenshotMonitoringEnabled] = enabled }
    }

    suspend fun setAutoCategoryEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.autoCategoryEnabled] = enabled }
    }

    suspend fun setBudgetAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.budgetAlertEnabled] = enabled }
    }

    suspend fun setLastScreenshotScanSeconds(value: Long) {
        dataStore.edit { it[Keys.lastScreenshotScanSeconds] = value }
    }

    suspend fun setPreferredCurrency(value: String) {
        dataStore.edit { it[Keys.preferredCurrency] = value }
    }

    suspend fun setMonthlyBudgetCents(value: Long) {
        dataStore.edit { it[Keys.monthlyBudgetCents] = value.coerceAtLeast(0L) }
    }

    suspend fun setLastBudgetAlertMarker(value: String) {
        dataStore.edit { it[Keys.lastBudgetAlertMarker] = value }
    }

    override suspend fun setLastSyncTimestampMillis(value: Long) {
        dataStore.edit { it[Keys.lastSyncTimestampMillis] = value.coerceAtLeast(0L) }
    }

    override suspend fun setLastSyncResult(status: String, error: String?) {
        dataStore.edit {
            it[Keys.lastSyncStatus] = status
            it[Keys.lastSyncError] = error.orEmpty()
        }
    }
}

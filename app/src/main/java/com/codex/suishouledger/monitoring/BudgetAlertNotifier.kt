package com.codex.suishouledger.monitoring

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codex.suishouledger.MainActivity
import com.codex.suishouledger.data.settings.LedgerSettingsRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

class BudgetAlertNotifier(
    private val context: Context,
    private val settings: LedgerSettingsRepository
) {
    suspend fun notifyIfNeeded(monthlyBudgetCents: Long, monthlyExpenseCents: Long) {
        if (monthlyBudgetCents <= 0L) return
        if (!settings.budgetAlertEnabled.first()) return
        if (!canPostNotifications()) return
        val level = when {
            monthlyExpenseCents >= monthlyBudgetCents -> BudgetAlertLevel.OVER
            monthlyExpenseCents * 100 >= monthlyBudgetCents * 80 -> BudgetAlertLevel.WARNING
            else -> null
        } ?: return
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date())
        val existingMarker = settings.lastBudgetAlertMarker.first()
        if (existingMarker.satisfies(currentMonth, monthlyBudgetCents, level)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(level, monthlyBudgetCents, monthlyExpenseCents))
        settings.setLastBudgetAlertMarker(buildMarker(currentMonth, monthlyBudgetCents, level))
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(
        level: BudgetAlertLevel,
        monthlyBudgetCents: Long,
        monthlyExpenseCents: Long
    ) = NotificationCompat.Builder(context, ensureChannel())
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(
            when (level) {
                BudgetAlertLevel.WARNING -> "本月预算已达 80%"
                BudgetAlertLevel.OVER -> "本月预算已超支"
            }
        )
        .setContentText(
            when (level) {
                BudgetAlertLevel.WARNING -> {
                    val left = (monthlyBudgetCents - monthlyExpenseCents).coerceAtLeast(0L)
                    "已支出 ${formatMoney(monthlyExpenseCents)} / 预算 ${formatMoney(monthlyBudgetCents)}，剩余 ${formatMoney(left)}"
                }
                BudgetAlertLevel.OVER -> {
                    val over = (monthlyExpenseCents - monthlyBudgetCents).coerceAtLeast(0L)
                    "已支出 ${formatMoney(monthlyExpenseCents)} / 预算 ${formatMoney(monthlyBudgetCents)}，超出 ${formatMoney(over)}"
                }
            }
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(buildContentIntent())
        .build()

    private fun buildContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            context,
            3002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                NotificationChannel(
                        CHANNEL_ID,
                        "预算提醒",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                    description = "当月预算达到预警或超支阈值时提醒"
                }
            )
        }
        }
        return CHANNEL_ID
    }

    private fun buildMarker(
        month: String,
        monthlyBudgetCents: Long,
        level: BudgetAlertLevel
    ): String = listOf(month, monthlyBudgetCents.toString(), level.value).joinToString("|")

    private fun String.satisfies(
        month: String,
        monthlyBudgetCents: Long,
        targetLevel: BudgetAlertLevel
    ): Boolean {
        val parts = split("|")
        if (parts.size != 3) return false
        val oldMonth = parts[0]
        val oldBudget = parts[1].toLongOrNull()
        val oldLevel = BudgetAlertLevel.from(parts[2]) ?: return false
        return oldMonth == month && oldBudget == monthlyBudgetCents && oldLevel.priority >= targetLevel.priority
    }

    private fun formatMoney(cents: Long): String {
        return NumberFormat.getCurrencyInstance(Locale.CHINA).format(cents / 100.0)
    }

    private enum class BudgetAlertLevel(val value: String, val priority: Int) {
        WARNING("warning", 1),
        OVER("over", 2);

        companion object {
            fun from(value: String): BudgetAlertLevel? = values().firstOrNull { it.value == value }
        }
    }

    companion object {
        private const val CHANNEL_ID = "budget_alerts"
        private const val NOTIFICATION_ID = 3001
    }
}

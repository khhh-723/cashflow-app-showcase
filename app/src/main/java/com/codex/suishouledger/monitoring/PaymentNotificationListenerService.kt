package com.codex.suishouledger.monitoring

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codex.suishouledger.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class PaymentNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Payment notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Payment notification listener disconnected")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            requestRebind(android.content.ComponentName(this, PaymentNotificationListenerService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!shouldHandlePackage(sbn.packageName)) return
        if (!notificationRateLimiter.tryAcquire(sbn.packageName, System.currentTimeMillis())) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val subText = extras.getCharSequence("android.subText")?.toString().orEmpty()
        val combinedText = listOf(title, text, bigText, subText)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        if (!com.codex.suishouledger.domain.PaymentParser.looksLikePaymentNotification(sbn.packageName, combinedText)) {
            return
        }
        val appName = packageManager.getApplicationLabel(
            runCatching { packageManager.getApplicationInfo(sbn.packageName, 0) }.getOrNull() ?: return
        ).toString()

        scope.launch {
            if (!ServiceLocator.settings.notificationParsingEnabled.first()) return@launch
            Log.d(TAG, "Payment notification captured from ${sbn.packageName}")
            ServiceLocator.repository.insertNotificationDraft(
                sourcePackage = sbn.packageName,
                sourceAppName = appName,
                title = title,
                body = listOf(text, bigText, subText)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString("\n"),
                postedAt = sbn.postTime
            )
        }
    }

    companion object {
        private const val TAG = "PaymentNotification"

        internal val PaymentPackageWhitelist = setOf(
            "com.tencent.mm",
            "com.eg.android.AlipayGphone"
        )

        internal fun shouldHandlePackage(packageName: String?): Boolean {
            return packageName in PaymentPackageWhitelist
        }

        internal val notificationRateLimiter = NotificationRateLimiter(
            maxEvents = 6,
            windowMillis = 60_000L
        )
    }
}

internal class NotificationRateLimiter(
    private val maxEvents: Int,
    private val windowMillis: Long
) {
    private val eventsByPackage = ConcurrentHashMap<String, ArrayDeque<Long>>()

    fun tryAcquire(packageName: String, nowMillis: Long): Boolean {
        val queue = eventsByPackage.getOrPut(packageName) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && nowMillis - (queue.peekFirst() ?: nowMillis) >= windowMillis) {
                queue.removeFirst()
            }
            if (queue.size >= maxEvents) return false
            queue.addLast(nowMillis)
            return true
        }
    }
}

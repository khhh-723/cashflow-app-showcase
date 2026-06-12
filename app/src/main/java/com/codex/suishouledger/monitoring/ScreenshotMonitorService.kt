package com.codex.suishouledger.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codex.suishouledger.ServiceLocator
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.work.OcrWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ScreenshotMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var observer: ContentObserver
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private val recentHandledUris = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        try {
            thread = HandlerThread("screenshot-monitor").apply { start() }
            handler = Handler(thread.looper)
            observer = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    if (uri == null) {
                        launchSafely { scanRecentScreenshots() }
                        return
                    }
                    launchSafely { handlePossibleScreenshot(uri) }
                }
            }
            startForeground(NOTIFICATION_ID, buildNotification())
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            launchSafely { bootstrapRecentScreenshots() }
        } catch (throwable: Throwable) {
            handleMonitorFailure("Failed to create screenshot monitor", throwable)
        }
    }

    override fun onDestroy() {
        runCatching {
            if (::observer.isInitialized) {
                contentResolver.unregisterContentObserver(observer)
            }
        }
        runCatching {
            if (::thread.isInitialized) {
                thread.quitSafely()
            }
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        launchSafely { bootstrapRecentScreenshots() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun handlePossibleScreenshot(uri: Uri) {
        if (isCollectionUri(uri)) {
            scanRecentScreenshots()
            return
        }
        val cursor = try {
            contentResolver.query(uri, screenshotProjection(), null, null, null)
        } catch (throwable: Throwable) {
            handleMonitorFailure("Failed to query screenshot uri", throwable)
            return
        } ?: return
        cursor.use {
            if (!it.moveToFirst()) return
            val row = readMediaRow(it)
            if (!looksLikePaymentScreenshot(row.name, row.bucket, row.relativePath)) return
            processScreenshot(
                imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, row.id),
                dateAddedSeconds = row.dateAddedSeconds
            )
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
        WorkManager.getInstance(this).enqueueUniqueWork(
            "ocr-$entryId",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }

    private suspend fun bootstrapRecentScreenshots() {
        val lastScanSeconds = ServiceLocator.settings.lastScreenshotScanSeconds.first()
        if (lastScanSeconds <= 0L) {
            ServiceLocator.settings.setLastScreenshotScanSeconds(currentEpochSeconds() - INITIAL_LOOKBACK_SECONDS)
        }
        scanRecentScreenshots()
    }

    private suspend fun scanRecentScreenshots() {
        val lastScanSeconds = ServiceLocator.settings.lastScreenshotScanSeconds.first()
        val minDateAddedSeconds = if (lastScanSeconds > 0L) {
            (lastScanSeconds - RESCAN_OVERLAP_SECONDS).coerceAtLeast(0L)
        } else {
            currentEpochSeconds() - INITIAL_LOOKBACK_SECONDS
        }
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(minDateAddedSeconds.toString())
        val cursor = try {
            queryRecentScreenshots(selection, selectionArgs)
        } catch (throwable: Throwable) {
            handleMonitorFailure("Failed to scan recent screenshots", throwable)
            return
        } ?: return
        var latestProcessedSeconds = lastScanSeconds
        cursor.use {
            while (it.moveToNext()) {
                val row = readMediaRow(it)
                if (!looksLikePaymentScreenshot(row.name, row.bucket, row.relativePath)) continue
                val imageUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, row.id)
                processScreenshot(imageUri, row.dateAddedSeconds)
                if (row.dateAddedSeconds > latestProcessedSeconds) {
                    latestProcessedSeconds = row.dateAddedSeconds
                }
            }
        }
        if (latestProcessedSeconds > 0L) {
            ServiceLocator.settings.setLastScreenshotScanSeconds(latestProcessedSeconds)
        }
    }

    private suspend fun processScreenshot(imageUri: Uri, dateAddedSeconds: Long) {
        val uriKey = imageUri.toString()
        if (shouldSkipRecentlyHandled(uriKey)) return
        val entry = ServiceLocator.repository.insertImageDraft(
            imageUri = imageUri,
            sourceType = SourceType.SCREENSHOT_IMAGE,
            sourcePackage = "",
            sourceAppName = "系统截图",
            occurredAt = if (dateAddedSeconds > 0L) dateAddedSeconds * 1000L else System.currentTimeMillis()
        ) ?: return
        markHandled(uriKey)
        if (entry.ingestionState == IngestionState.OCR_PENDING) {
            enqueueOcr(entry.id, imageUri)
        }
        val nextScanSeconds = maxOf(dateAddedSeconds, currentEpochSeconds())
        ServiceLocator.settings.setLastScreenshotScanSeconds(nextScanSeconds)
    }

    private fun screenshotProjection(): Array<String> {
        return buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()
    }

    private fun readMediaRow(cursor: android.database.Cursor): MediaImageRow {
        return MediaImageRow(
            id = cursor.getLong(MediaStore.Images.Media._ID),
            name = cursor.getStringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME),
            dateAddedSeconds = cursor.getLong(MediaStore.Images.Media.DATE_ADDED),
            bucket = cursor.getStringOrEmpty(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getStringOrEmpty(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                ""
            }
        )
    }

    private fun queryRecentScreenshots(
        selection: String,
        selectionArgs: Array<String>
    ): android.database.Cursor? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                screenshotProjection(),
                Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.Images.Media.DATE_ADDED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                    putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_RECENT_SCREENSHOT_SCAN)
                },
                null
            )
        } else {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                screenshotProjection(),
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
        }
    }

    private fun android.database.Cursor.getStringOrEmpty(columnName: String): String {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getString(index).orEmpty() else ""
    }

    private fun android.database.Cursor.getLong(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getLong(index) else 0L
    }

    private data class MediaImageRow(
        val id: Long,
        val name: String,
        val dateAddedSeconds: Long,
        val bucket: String,
        val relativePath: String
    )

    private fun isCollectionUri(uri: Uri): Boolean {
        return uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI ||
            uri.lastPathSegment.equals("media", ignoreCase = true)
    }

    private fun shouldSkipRecentlyHandled(uriKey: String): Boolean {
        cleanupRecentHandledUris()
        val now = System.currentTimeMillis()
        val lastHandledAt = recentHandledUris[uriKey] ?: return false
        return now - lastHandledAt < DUPLICATE_CALLBACK_WINDOW_MILLIS
    }

    private fun markHandled(uriKey: String) {
        recentHandledUris[uriKey] = System.currentTimeMillis()
        cleanupRecentHandledUris()
    }

    private fun cleanupRecentHandledUris() {
        val threshold = System.currentTimeMillis() - DUPLICATE_CALLBACK_WINDOW_MILLIS
        recentHandledUris.entries.removeIf { it.value < threshold }
    }

    private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (throwable: Throwable) {
                handleMonitorFailure("Screenshot monitor background task failed", throwable)
            }
        }
    }

    private fun handleMonitorFailure(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ServiceLocator.settings.setScreenshotMonitoringEnabled(false) }
        }
        runCatching { stopSelf() }
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("随手记账截图监听")
            .setContentText("正在监听系统截图并自动识别")
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel(): String {
        val id = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(id) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(id, "截图监听", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return id
    }

    companion object {
        private const val TAG = "ScreenshotMonitor"
        private const val CHANNEL_ID = "screenshot_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val INITIAL_LOOKBACK_SECONDS = 15L
        private const val RESCAN_OVERLAP_SECONDS = 2L
        private const val MAX_RECENT_SCREENSHOT_SCAN = 8
        private const val DUPLICATE_CALLBACK_WINDOW_MILLIS = 15_000L
    }
}

internal fun looksLikePaymentScreenshot(name: String, bucket: String, relativePath: String): Boolean {
    val combined = "$name $bucket $relativePath".lowercase()
    return combined.contains("screenshot") ||
        combined.contains("screen_shot") ||
        combined.contains("screen-shot") ||
        combined.contains("screenshots") ||
        combined.contains("截屏") ||
        combined.contains("截图")
}

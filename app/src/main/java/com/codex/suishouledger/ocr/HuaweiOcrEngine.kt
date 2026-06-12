package com.codex.suishouledger.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.huawei.hmf.tasks.Task
import com.huawei.hms.mlsdk.MLAnalyzerFactory
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLLocalTextSetting
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HuaweiOcrEngine(
    private val context: Context
) : OcrEngine {
    private val analyzer: MLTextAnalyzer by lazy {
        val setting = MLLocalTextSetting.Factory()
            .setOCRMode(MLLocalTextSetting.OCR_DETECT_MODE)
            .setLanguage("zh")
            .create()
        MLAnalyzerFactory.getInstance().getLocalTextAnalyzer(setting)
    }

    override suspend fun recognize(imageUri: Uri): OcrResult = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmap(imageUri)
        val frame = MLFrame.Creator().setBitmap(bitmap).create()
        val text = suspendCancellableCoroutine<String> { continuation ->
            val task: Task<MLText> = analyzer.asyncAnalyseFrame(frame)
            task.addOnSuccessListener { mlText ->
                continuation.resume(mlText.stringValueOrEmpty())
            }.addOnFailureListener { error ->
                continuation.resumeWithException(error)
            }
        }
        OcrResult(
            text = text,
            lines = text.lines().filter { it.isNotBlank() }
        )
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open image URI: $uri" }
            return BitmapFactory.decodeStream(input, null, options)
                ?: throw IllegalStateException("Failed to decode bitmap from $uri")
        }
    }

    private fun MLText.stringValueOrEmpty(): String {
        return try {
            getStringValue().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
}

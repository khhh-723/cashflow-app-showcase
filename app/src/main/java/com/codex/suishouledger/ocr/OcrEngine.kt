package com.codex.suishouledger.ocr

import android.net.Uri

data class OcrResult(
    val text: String,
    val lines: List<String> = emptyList()
)

interface OcrEngine {
    suspend fun recognize(imageUri: Uri): OcrResult
}

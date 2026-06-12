package com.codex.suishouledger.domain

import com.codex.suishouledger.data.local.CategoryEntity
import com.codex.suishouledger.data.local.IngestionState
import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.SourceType
import com.codex.suishouledger.data.local.TransactionType
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

data class ParsedDraft(
    val amountCents: Long?,
    val merchant: String,
    val categoryCode: String?,
    val categoryName: String?,
    val transactionType: TransactionType,
    val confidence: Float,
    val normalizedText: String,
    val occurredAt: Long? = null
)

object PaymentParser {
    private const val AMOUNT_PATTERN = """(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]{1,7})(?:[.,][0-9]{1,2})?"""
    private const val MONEY_PATTERN = """[-+]?\s*(?:[¥￥]|rmb|cny|人民币)?\s*$AMOUNT_PATTERN"""

    private data class AmountCandidate(
        val cents: Long,
        val score: Int,
        val index: Int,
        val signedNegative: Boolean
    )

    private data class WechatBillRow(
        val merchant: String,
        val timeText: String,
        val occurredAt: Long?,
        val lineIndex: Int
    )

    private data class AlipayTransactionRow(
        val merchant: String,
        val timeText: String,
        val occurredAt: Long?,
        val lineIndex: Int,
        val merchantLineIndex: Int
    )

    private data class ListAmount(
        val amountText: String,
        val cents: Long
    )

    private val labeledAmountRegex = Regex(
        """(?:支付金额|付款金额|收款金额|实付款|实付|实际付款|实际支付|消费金额|订单金额|转账金额|退款金额|总金额|总计|金额|合计|共计|需付款|已支付|付款|支出|收入)\s*[:：]?\s*($MONEY_PATTERN)""",
        RegexOption.IGNORE_CASE
    )
    private val currencyAmountRegex = Regex(
        """([-+]?\s*(?:[¥￥]|rmb|cny|人民币)\s*$AMOUNT_PATTERN)""",
        RegexOption.IGNORE_CASE
    )
    private val yuanAmountRegex = Regex("""([-+]?\s*$AMOUNT_PATTERN)\s*(?:元|块)""")
    private val standaloneAmountRegex = Regex(
        """^($MONEY_PATTERN)\s*(?:元|块)?$""",
        RegexOption.IGNORE_CASE
    )
    private val listSignedAmountRegex = Regex("""(?<!\d)(-\s*(?:[¥￥])?\s*$AMOUNT_PATTERN)""")
    private val chineseDateTimeRegex = Regex(
        """(?:(\d{4})年\s*)?(\d{1,2})月\s*(\d{1,2})日\s*(?:(上午|下午|晚上|中午|凌晨|早上)\s*)?(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val fullDateTimeRegex = Regex(
        """(\d{4})[-/年](\d{1,2})[-/月](\d{1,2})(?:日)?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val alipayListDateTimeRegex = Regex(
        """(?:(20\d{2})[-/])?(\d{1,2})[-/](\d{1,2})\s*(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val relativeTimeRegex = Regex(
        """(?:(今天|昨天|前天)\s*)?(上午|下午|晚上|中午|凌晨|早上)?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )

    private val merchantCleanup = listOf(
        "微信", "微信支付", "支付宝", "支付成功", "付款成功", "收款成功", "交易成功", "当前状态",
        "账单详情", "全部账单", "账单", "账单管理", "账单服务", "交易", "订单", "订单详情", "交易详情",
        "扫码", "付款", "消费", "查看明细", "查看详情", "日报设置", "使用零钱通支付", "零钱通",
        "银行卡", "余额", "支付时间", "付款方式", "支付方式", "收单机构", "支付奖励", "本次奖励",
        "抵扣金额", "闪购支付红包", "服务消息", "支付消息", "查找交易", "收支统计", "更多",
        "简称", "全称", "全部", "待确认草稿", "编辑", "确认", "忽略", "删除",
        "首页", "图表", "设置", "记账", "本月", "分类", "分析页", "商户", "商户简称", "商户全称"
    )
    private val amountPositiveKeywords = listOf(
        "实付", "实际付款", "实际支付", "支付金额", "付款金额", "收款金额", "消费金额", "转账金额",
        "退款金额", "总金额", "总计", "金额", "合计", "共计", "需付款", "已支付", "支付成功",
        "付款成功", "交易成功", "付款", "支出", "收入"
    )
    private val amountNegativeKeywords = listOf(
        "优惠", "红包", "折扣", "抵扣", "减免", "满减", "随机立减", "原价", "余额", "账户余额",
        "可用余额", "零钱", "积分", "卡号", "订单号", "交易单号", "商户单号", "流水号", "日期", "时间"
    )
    private val merchantLabelRegex = Regex(
        """(?:商户\s*(?:全称|简称)?|商家|店铺|商品说明|商品|收款方|付款给|支付给|对方账户|对方)\s*(?:名称)?\s*[:：]?\s*([^\s，。,;；]{2,40})"""
    )
    private val transactionIdRegex = Regex(
        """(?:订单号|交易单号|商户单号|流水号|交易号|订单编号)\s*[:：]?\s*([A-Za-z0-9_-]{8,80})"""
    )

    fun parseNotification(
        sourcePackage: String,
        title: String,
        body: String,
        postedAt: Long,
        categories: List<CategoryEntity>
    ): ParsedDraft? {
        val combined = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
        if (combined.isBlank()) return null
        val amount = findAmountCents(combined)
        if (amount == null || amount <= 0L) return null
        if (!looksLikePaymentNotification(sourcePackage, combined)) return null
        val merchant = findMerchant(title, body)
        val category = CategorySuggestionEngine.suggestCode(combined, categories)
        val type = inferTransactionType(combined)
        val confidence = if (merchant.isNotBlank()) 0.88f else 0.72f
        return ParsedDraft(
            amountCents = amount,
            merchant = merchant.ifBlank { cleanSourcePackage(sourcePackage) },
            categoryCode = category.categoryCode,
            categoryName = categories.firstOrNull { it.code == category.categoryCode }?.name,
            transactionType = type,
            confidence = confidence,
            normalizedText = combined.trim(),
            occurredAt = postedAt
        )
    }

    fun parseText(text: String, categories: List<CategoryEntity>): ParsedDraft {
        val normalized = normalizeOcrText(text).trim()
        val listEntries = parseTextEntries(normalized, categories, allowSingleListEntry = true)
        if (listEntries.isNotEmpty()) return listEntries.first()
        val amount = findAmountCents(normalized)
        val category = CategorySuggestionEngine.suggestCode(normalized, categories)
        return ParsedDraft(
            amountCents = amount,
            merchant = findMerchant("", normalized),
            categoryCode = category.categoryCode,
            categoryName = categories.firstOrNull { it.code == category.categoryCode }?.name,
            transactionType = inferTransactionType(normalized),
            confidence = if (amount != null) 0.8f else 0.46f,
            normalizedText = normalized,
            occurredAt = findOccurredAtMillis(normalized)
        )
    }

    fun parseTextEntries(
        text: String,
        categories: List<CategoryEntity>,
        allowSingleListEntry: Boolean = false
    ): List<ParsedDraft> {
        val normalized = normalizeOcrText(text).trim()
        if (normalized.isBlank()) return emptyList()
        val listEntries = parsePaymentListEntries(normalized, categories)
        if (listEntries.size > 1 || allowSingleListEntry && listEntries.isNotEmpty()) {
            return listEntries
        }
        return listOf(parsePaymentDetailOrGeneric(normalized, categories))
    }

    fun buildOcrFingerprint(rawText: String, amountCents: Long, merchant: String): String {
        val normalized = normalizeForFingerprint(rawText)
        val transactionId = transactionIdRegex.find(normalized)?.groupValues?.getOrNull(1)
        if (!transactionId.isNullOrBlank()) {
            return "ocr:txn:${transactionId.takeLast(48)}"
        }
        val paymentTime = findPaymentTimeKey(normalizeOcrText(rawText))
        if (paymentTime.isNotBlank()) {
            return "ocr:$amountCents:${merchant.compactKey()}:$paymentTime"
        }
        return "ocr:$amountCents:${merchant.compactKey()}:${sha256(normalized).take(24)}"
    }

    fun buildNotificationFingerprint(sourcePackage: String, postedAt: Long, parsed: ParsedDraft, rawText: String): String {
        val bucket = postedAt / (2 * 60 * 1000L)
        return "notif:$sourcePackage:$bucket:${parsed.amountCents ?: 0}:${parsed.merchant.compactKey()}:${sha256(normalizeForFingerprint(rawText)).take(16)}"
    }

    internal fun looksLikePaymentNotification(sourcePackage: String, text: String): Boolean {
        val normalized = normalizeOcrText(text).lowercase()
        if (normalized.isBlank()) return false
        val hasMoney = findAmountCents(normalized) != null
        if (!hasMoney) return false
        val paymentSignals = listOf(
            "微信支付",
            "支付成功",
            "付款成功",
            "收款成功",
            "收款到账",
            "到账",
            "已支付",
            "已付款",
            "交易成功",
            "消费",
            "付款",
            "支付",
            "收款",
            "退款",
            "转账",
            "红包",
            "alipay",
            "wechat pay",
            "rmb",
            "cny",
            "¥",
            "￥",
            "楼"
        )
        val chatNoiseSignals = listOf(
            "条新消息",
            "有人@我",
            "撤回了一条消息",
            "语音通话",
            "视频通话",
            "图片",
            "表情",
            "朋友圈",
            "订阅号",
            "服务通知"
        )
        if (chatNoiseSignals.any { normalized.contains(it) } &&
            paymentSignals.none { normalized.contains(it) }
        ) {
            return false
        }
        return when (sourcePackage) {
            "com.tencent.mm" -> paymentSignals.any { normalized.contains(it) } ||
                normalized.contains("商户") && (normalized.contains("¥") || normalized.contains("￥") || normalized.contains("楼"))
            "com.eg.android.AlipayGphone" -> paymentSignals.any { normalized.contains(it) } ||
                normalized.contains("订单") && (normalized.contains("¥") || normalized.contains("￥") || normalized.contains("楼"))
            else -> false
        }
    }

    internal fun looksLikePaymentScreenshotText(text: String): Boolean {
        val normalized = normalizeOcrText(text).lowercase()
        if (normalized.isBlank()) return false
        val hasMoney = findAmountCents(normalized)?.let { it > 0L } == true
        if (!hasMoney) return false
        val paymentSignals = listOf(
            "支付成功",
            "付款成功",
            "收款成功",
            "已支付",
            "已付款",
            "支付金额",
            "付款金额",
            "实付",
            "实收",
            "订单",
            "交易",
            "商户",
            "账单",
            "微信支付",
            "支付宝",
            "转账",
            "退款",
            "消费",
            "支付时间",
            "交易时间",
            "交易单号",
            "订单号"
        )
        val noiseSignals = listOf(
            "聊天",
            "群聊",
            "公众号",
            "订阅号",
            "朋友圈",
            "浏览器",
            "网页",
            "文章",
            "新闻",
            "视频",
            "相册",
            "图片",
            "截图编辑",
            "分享"
        )
        if (noiseSignals.any { normalized.contains(it) } && paymentSignals.none { normalized.contains(it) }) {
            return false
        }
        return paymentSignals.any { normalized.contains(it) }
    }

    fun createManualDraft(
        amountCents: Long,
        merchant: String,
        categoryCode: String?,
        categoryName: String?,
        transactionType: TransactionType,
        note: String,
        categories: List<CategoryEntity>,
        sourceType: SourceType = SourceType.MANUAL,
        sourceFingerprint: String = "manual:${UUID.randomUUID()}",
        occurredAt: Long = System.currentTimeMillis(),
        accountCode: String = "cash",
        accountName: String = "现金"
    ): LedgerEntryEntity {
        return LedgerEntryEntity(
            id = UUID.randomUUID().toString(),
            reviewState = ReviewState.CONFIRMED,
            ingestionState = IngestionState.RAW,
            transactionType = transactionType,
            sourceType = sourceType,
            sourceFingerprint = sourceFingerprint,
            occurredAt = occurredAt,
            amountCents = amountCents,
            merchant = merchant,
            categoryCode = categoryCode ?: CategorySuggestionEngine.suggestCode(note.ifBlank { merchant }, categories).categoryCode,
            categoryNameSnapshot = categoryName,
            accountCode = accountCode,
            accountNameSnapshot = accountName,
            note = note,
            rawText = note,
            confidence = 1f,
            needsReview = false,
            createdAt = occurredAt,
            updatedAt = occurredAt
        )
    }

    fun buildDraftEntry(
        sourceType: SourceType,
        sourceFingerprint: String,
        sourcePackage: String,
        sourceAppName: String,
        occurredAt: Long,
        parsed: ParsedDraft?,
        rawText: String,
        imageUri: String? = null,
        accountCode: String = "cash",
        accountName: String = "现金"
    ): LedgerEntryEntity {
        val now = System.currentTimeMillis()
        return LedgerEntryEntity(
            id = UUID.randomUUID().toString(),
            reviewState = ReviewState.DRAFT,
            ingestionState = IngestionState.RAW,
            transactionType = parsed?.transactionType ?: TransactionType.EXPENSE,
            sourceType = sourceType,
            sourceFingerprint = sourceFingerprint,
            sourcePackage = sourcePackage,
            sourceAppName = sourceAppName,
            occurredAt = parsed?.occurredAt ?: occurredAt,
            amountCents = parsed?.amountCents ?: 0L,
            merchant = parsed?.merchant.orEmpty(),
            categoryCode = parsed?.categoryCode,
            categoryNameSnapshot = parsed?.categoryName,
            accountCode = accountCode,
            accountNameSnapshot = accountName,
            note = "",
            rawText = rawText,
            imageUri = imageUri,
            confidence = parsed?.confidence ?: 0.4f,
            needsReview = true,
            createdAt = now,
            updatedAt = now
        )
    }

    fun enrichFromOcr(
        entry: LedgerEntryEntity,
        ocrText: String,
        categories: List<CategoryEntity>,
        parsedDraft: ParsedDraft? = null
    ): LedgerEntryEntity {
        val parsed = parsedDraft ?: parseText(ocrText, categories)
        val now = System.currentTimeMillis()
        return entry.copy(
            ingestionState = if (ocrText.isBlank()) IngestionState.OCR_FAILED else IngestionState.OCR_DONE,
            amountCents = parsed.amountCents ?: entry.amountCents,
            merchant = parsed.merchant.ifBlank { entry.merchant },
            transactionType = parsed.transactionType,
            occurredAt = parsed.occurredAt ?: entry.occurredAt,
            categoryCode = parsed.categoryCode ?: entry.categoryCode,
            categoryNameSnapshot = parsed.categoryName ?: entry.categoryNameSnapshot,
            rawText = ocrText.ifBlank { entry.rawText },
            confidence = maxOf(entry.confidence, parsed.confidence),
            updatedAt = now,
            needsReview = true
        )
    }

    private fun parsePaymentDetailOrGeneric(normalized: String, categories: List<CategoryEntity>): ParsedDraft {
        val lines = normalized.linesForParsing()
        val amount = findAmountCents(normalized)
        val merchant = findDetailMerchant(lines).ifBlank { findMerchant("", normalized) }
        val category = CategorySuggestionEngine.suggestCode(normalized, categories)
        val isDetail = normalized.contains("账单详情") ||
            normalized.contains("当前状态") ||
            normalized.contains("交易单号") ||
            normalized.contains("商户单号") ||
            normalized.contains("付款成功") ||
            normalized.contains("支付成功")
        return ParsedDraft(
            amountCents = amount,
            merchant = merchant,
            categoryCode = category.categoryCode,
            categoryName = categories.firstOrNull { it.code == category.categoryCode }?.name,
            transactionType = inferTransactionType(normalized),
            confidence = when {
                amount != null && merchant.isNotBlank() && isDetail -> 0.9f
                amount != null && merchant.isNotBlank() -> 0.82f
                amount != null -> 0.72f
                else -> 0.46f
            },
            normalizedText = normalized,
            occurredAt = findOccurredAtMillis(normalized)
        )
    }

    private fun parsePaymentListEntries(text: String, categories: List<CategoryEntity>): List<ParsedDraft> {
        val lines = text.linesForParsing()
        val entries = parseWechatBillList(lines, categories) +
            parseAlipayPaymentMessages(lines, categories) +
            parseAlipayTransactionList(lines, categories)
        return entries.distinctBy {
            "${it.occurredAt ?: 0L}:${it.amountCents ?: 0L}:${it.merchant.compactKey()}"
        }
    }

    private fun parseWechatBillList(lines: List<String>, categories: List<CategoryEntity>): List<ParsedDraft> {
        val joined = lines.joinToString("\n")
        val timeLineCount = lines.count { findWechatListTimeMatch(it) != null }
        val looksLikeWechatBill = joined.contains("全部账单") ||
            joined.contains("查找交易") ||
            joined.contains("收支统计") ||
            joined.contains("202") && timeLineCount >= 2 ||
            timeLineCount >= 2 && lines.any { listSignedAmountRegex.containsMatchIn(it) }
        if (!looksLikeWechatBill) return emptyList()

        val defaultYear = Regex("""(20\d{2})年\s*\d{1,2}月""")
            .find(joined)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: LocalDate.now().year

        val rows = lines.mapIndexedNotNull { index, line ->
            val timeMatch = findWechatListTimeMatch(line) ?: return@mapIndexedNotNull null
            val merchant = findMerchantNearDateLine(lines, index, timeMatch.range.first)
            if (!merchant.isLikelyMerchant()) return@mapIndexedNotNull null
            WechatBillRow(
                merchant = merchant,
                timeText = timeMatch.value,
                occurredAt = parseWechatListTime(timeMatch.value, defaultYear),
                lineIndex = index
            )
        }
        val orderedAmounts = lines
            .filterNot { it.looksLikeMonthlySummaryLine() }
            .flatMap { line ->
                listSignedAmountRegex.findAll(line).mapNotNull { match ->
                    val amount = match.value.toAmountCentsOrNull() ?: return@mapNotNull null
                    ListAmount(match.value, amount)
                }.toList()
            }
        if (rows.size >= 2 && orderedAmounts.size >= rows.size) {
            return rows.zip(orderedAmounts).map { (row, amount) ->
                val block = listOf(row.merchant, row.timeText, amount.amountText).joinToString("\n")
                buildParsedDraft(
                    amountCents = amount.cents,
                    merchant = row.merchant,
                    rawText = block,
                    transactionType = TransactionType.EXPENSE,
                    occurredAt = row.occurredAt,
                    categories = categories,
                    confidence = 0.92f
                )
            }
        }

        return rows.mapIndexedNotNull { index, row ->
            val amount = orderedAmounts.getOrNull(index)
            val amountText = amount?.amountText
                ?: findSignedAmountNear(lines, row.lineIndex)?.value
                ?: return@mapIndexedNotNull null
            val amountCents = amount?.cents ?: amountText.toAmountCentsOrNull() ?: return@mapIndexedNotNull null
            val block = listOfNotNull(
                row.merchant,
                row.timeText,
                amountText
            ).joinToString("\n")
            buildParsedDraft(
                amountCents = amountCents,
                merchant = row.merchant,
                rawText = block,
                transactionType = TransactionType.EXPENSE,
                occurredAt = row.occurredAt,
                categories = categories,
                confidence = 0.9f
            )
        }
    }

    private fun parseAlipayTransactionList(lines: List<String>, categories: List<CategoryEntity>): List<ParsedDraft> {
        val joined = lines.joinToString("\n")
        val dateLineCount = lines.count { findAlipayListTimeMatch(it) != null }
        val looksLikeAlipayList = joined.contains("搜索交易记录") ||
            joined.contains("收支分析") ||
            joined.contains("本月已省") ||
            joined.contains("筛选") && dateLineCount >= 2 ||
            dateLineCount >= 3 && joined.contains("订单")
        if (!looksLikeAlipayList) return emptyList()

        val rows = lines.mapIndexedNotNull { index, line ->
            val timeMatch = findAlipayListTimeMatch(line) ?: return@mapIndexedNotNull null
            if (rowWindowHasClosedStatus(lines, index)) return@mapIndexedNotNull null
            val merchantIndex = findAlipayListMerchantIndex(lines, index, timeMatch.range.first)
                ?: return@mapIndexedNotNull null
            val merchant = cleanMerchantCandidate(lines[merchantIndex])
            if (!merchant.isLikelyAlipayListMerchant()) return@mapIndexedNotNull null
            AlipayTransactionRow(
                merchant = merchant,
                timeText = timeMatch.value,
                occurredAt = parseAlipayListTime(timeMatch.value),
                lineIndex = index,
                merchantLineIndex = merchantIndex
            )
        }
        val orderedAmounts = lines
            .filterNot { it.looksLikeMonthlySummaryLine() || it.looksLikeAlipayAuxiliaryAmountLine() }
            .flatMap { line ->
                listSignedAmountRegex.findAll(line).mapNotNull { match ->
                    val amount = match.value.toAmountCentsOrNull() ?: return@mapNotNull null
                    ListAmount(match.value, amount)
                }.toList()
            }

        if (rows.size >= 2 && orderedAmounts.size >= rows.size) {
            return rows.zip(orderedAmounts).map { (row, amount) ->
                val block = alipayListBlock(lines, row, amount.amountText)
                buildParsedDraft(
                    amountCents = amount.cents,
                    merchant = row.merchant,
                    rawText = block,
                    transactionType = TransactionType.EXPENSE,
                    occurredAt = row.occurredAt,
                    categories = categories,
                    confidence = 0.91f
                )
            }
        }

        return rows.mapNotNull { row ->
            val amountMatch = findAlipayListAmountNear(lines, row.lineIndex, row.merchantLineIndex)
                ?: return@mapNotNull null
            val amount = amountMatch.value.toAmountCentsOrNull() ?: return@mapNotNull null
            val block = alipayListBlock(lines, row, amountMatch.value)
            buildParsedDraft(
                amountCents = amount,
                merchant = row.merchant,
                rawText = block,
                transactionType = TransactionType.EXPENSE,
                occurredAt = row.occurredAt,
                categories = categories,
                confidence = 0.88f
            )
        }
    }

    private fun parseAlipayPaymentMessages(lines: List<String>, categories: List<CategoryEntity>): List<ParsedDraft> {
        val joined = lines.joinToString("\n")
        val looksLikeAlipayMessages = joined.contains("支付消息") ||
            lines.count { it.contains("付款成功") || it.contains("支付成功") } >= 2
        if (!looksLikeAlipayMessages) return emptyList()

        return lines.mapIndexedNotNull { index, line ->
            if (!line.contains("付款成功") && !line.contains("支付成功")) return@mapIndexedNotNull null
            val amountMatch = findCurrencyAmountAfter(lines, index) ?: return@mapIndexedNotNull null
            val timeIndex = (index - 1 downTo (index - 4).coerceAtLeast(0))
                .firstOrNull { parseFlexibleMessageTimeFromLine(lines[it]) != null }
            val timeLine = timeIndex?.let { lines[it] }.orEmpty()
            val merchantFromTimeLine = timeLine.merchantPrefixBeforeTime()
            val merchantIndex = if (merchantFromTimeLine.isBlank() && timeIndex != null) {
                (timeIndex - 1 downTo (timeIndex - 4).coerceAtLeast(0))
                    .firstOrNull { lineLooksLikeListMerchant(lines[it]) }
            } else {
                (index - 1 downTo (index - 4).coerceAtLeast(0))
                    .firstOrNull { lineLooksLikeListMerchant(lines[it]) }
            }
            val merchant = merchantFromTimeLine.ifBlank {
                merchantIndex?.let { cleanMerchantCandidate(lines[it]) }.orEmpty()
            }
            if (!merchant.isLikelyMerchant()) return@mapIndexedNotNull null
            val amount = amountMatch.value.toAmountCentsOrNull() ?: return@mapIndexedNotNull null
            val block = listOf(merchant, timeLine, line, amountMatch.value)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            buildParsedDraft(
                amountCents = amount,
                merchant = merchant,
                rawText = block,
                transactionType = TransactionType.EXPENSE,
                occurredAt = parseFlexibleMessageTimeFromLine(timeLine),
                categories = categories,
                confidence = 0.88f
            )
        }
    }

    private fun buildParsedDraft(
        amountCents: Long,
        merchant: String,
        rawText: String,
        transactionType: TransactionType,
        occurredAt: Long?,
        categories: List<CategoryEntity>,
        confidence: Float
    ): ParsedDraft {
        val category = CategorySuggestionEngine.suggestCode(rawText, categories)
        return ParsedDraft(
            amountCents = amountCents,
            merchant = merchant,
            categoryCode = category.categoryCode,
            categoryName = categories.firstOrNull { it.code == category.categoryCode }?.name,
            transactionType = transactionType,
            confidence = confidence,
            normalizedText = rawText,
            occurredAt = occurredAt
        )
    }

    private fun findSignedAmountNear(lines: List<String>, index: Int): MatchResult? {
        val window = ((index - 2).coerceAtLeast(0)..(index + 3).coerceAtMost(lines.lastIndex))
        return window
            .asSequence()
            .mapNotNull { lineIndex ->
                listSignedAmountRegex.find(lines[lineIndex])?.takeUnless {
                    lines[lineIndex].looksLikeMonthlySummaryLine()
                }
            }
            .firstOrNull()
    }

    private fun findAlipayListAmountNear(lines: List<String>, timeIndex: Int, merchantIndex: Int): MatchResult? {
        val window = (merchantIndex..(timeIndex + 3).coerceAtMost(lines.lastIndex))
        return window
            .asSequence()
            .mapNotNull { lineIndex ->
                listSignedAmountRegex.find(lines[lineIndex])?.takeUnless {
                    lines[lineIndex].looksLikeMonthlySummaryLine() ||
                        lines[lineIndex].looksLikeAlipayAuxiliaryAmountLine()
                }
            }
            .firstOrNull()
    }

    private fun findCurrencyAmountAfter(lines: List<String>, index: Int): MatchResult? {
        val window = (index..(index + 3).coerceAtMost(lines.lastIndex))
        return window
            .asSequence()
            .mapNotNull { lineIndex ->
                currencyAmountRegex.find(lines[lineIndex])?.takeUnless {
                    lines[lineIndex].contains("红包") ||
                        lines[lineIndex].contains("抵扣") ||
                        lines[lineIndex].contains("奖励") ||
                        lines[lineIndex].contains("积分")
                }
            }
            .firstOrNull()
    }

    private fun findMerchantNearDateLine(lines: List<String>, index: Int, dateStart: Int): String {
        val sameLinePrefix = lines[index].take(dateStart).trim()
        if (sameLinePrefix.isLikelyMerchant()) return cleanMerchantCandidate(sameLinePrefix)
        return (index - 1 downTo (index - 5).coerceAtLeast(0))
            .asSequence()
            .map { cleanMerchantCandidate(lines[it]) }
            .firstOrNull { it.isLikelyMerchant() }
            .orEmpty()
    }

    private fun findAlipayListMerchantIndex(lines: List<String>, index: Int, dateStart: Int): Int? {
        val sameLinePrefix = lines[index].take(dateStart).trim()
        if (sameLinePrefix.isLikelyAlipayListMerchant()) return index
        return (index - 1 downTo (index - 7).coerceAtLeast(0))
            .asSequence()
            .firstOrNull { lineIndex ->
                cleanMerchantCandidate(lines[lineIndex]).isLikelyAlipayListMerchant()
            }
    }

    private fun lineLooksLikeListMerchant(line: String): Boolean {
        val candidate = cleanMerchantCandidate(line)
        return candidate.isLikelyMerchant() && parseFlexibleMessageTimeFromLine(candidate) == null
    }

    private fun cleanMerchantCandidate(line: String): String {
        return currencyAmountRegex.replace(listSignedAmountRegex.replace(line, ""), "")
            .replace(Regex("""[>›…·•]+"""), "")
            .trim()
    }

    private fun findAlipayListTimeMatch(line: String): MatchResult? {
        return alipayListDateTimeRegex.find(line) ?: relativeTimeRegex.find(line)?.takeIf(::hasRelativeDayToken)
    }

    private fun findWechatListTimeMatch(line: String): MatchResult? {
        return chineseDateTimeRegex.find(line) ?:
            alipayListDateTimeRegex.find(line) ?:
            relativeTimeRegex.find(line)?.takeIf(::hasRelativeDayToken)
    }

    private fun hasRelativeDayToken(match: MatchResult): Boolean {
        return match.groupValues.getOrNull(1).orEmpty().isNotBlank()
    }

    private fun parseAlipayListTime(value: String): Long? {
        alipayListDateTimeRegex.find(value)?.let { match ->
            return parseMonthDayTimeMatch(match, fallbackYear = null)
        }
        return parseFlexibleMessageTime(value)
    }

    private fun parseWechatListTime(value: String, defaultYear: Int): Long? {
        chineseDateTimeRegex.find(value)?.let { match ->
            return parseChineseDateTime(match.value, defaultYear)
        }
        alipayListDateTimeRegex.find(value)?.let { match ->
            return parseMonthDayTimeMatch(match, fallbackYear = defaultYear)
        }
        return parseFlexibleMessageTime(value)
    }

    private fun rowWindowHasClosedStatus(lines: List<String>, timeIndex: Int): Boolean {
        val nextTimeIndex = (timeIndex + 1..lines.lastIndex).firstOrNull { findAlipayListTimeMatch(lines[it]) != null }
            ?: (timeIndex + 5).coerceAtMost(lines.lastIndex + 1)
        return (timeIndex until nextTimeIndex).any { lines[it].contains("交易关闭") }
    }

    private fun alipayListBlock(lines: List<String>, row: AlipayTransactionRow, amountText: String): String {
        val nextTimeIndex = (row.lineIndex + 1..lines.lastIndex).firstOrNull { findAlipayListTimeMatch(lines[it]) != null }
            ?: (row.lineIndex + 4).coerceAtMost(lines.lastIndex + 1)
        val endIndex = (nextTimeIndex - 1).coerceAtLeast(row.lineIndex)
        return (row.merchantLineIndex..endIndex)
            .mapNotNull { lines.getOrNull(it) }
            .plus(amountText)
            .distinct()
            .joinToString("\n")
    }

    private fun findAmountCents(text: String): Long? {
        val normalized = normalizeOcrText(text)
        val lines = normalized
            .split('\n', ' ', '\t', '|', '｜')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val candidates = buildList {
            lines.forEachIndexed { index, line ->
                val context = (index - 2..index + 2)
                    .mapNotNull { lines.getOrNull(it) }
                    .joinToString(" ")
                collectAmountCandidates(line, context, index, this)
            }
            collectAmountCandidates(normalized.replace('\n', ' '), normalized.replace('\n', ' '), lines.size, this)
        }
            .distinctBy { it.cents to it.index to it.signedNegative }
            .filter { it.cents > 0 && it.score >= 12 }

        return candidates
            .sortedWith(
                compareByDescending<AmountCandidate> { it.score }
                    .thenByDescending { it.signedNegative }
                    .thenByDescending { it.cents }
                    .thenBy { it.index }
            )
            .firstOrNull()
            ?.cents
    }

    private fun collectAmountCandidates(segment: String, context: String, index: Int, output: MutableList<AmountCandidate>) {
        val line = segment.trim()
        if (line.isBlank() || line.looksLikeMonthlySummaryLine()) return
        labeledAmountRegex.findAll(line).forEach { match ->
            addAmountCandidate(match.groupValues.getOrNull(1), line, context, index, 18, output)
        }
        currencyAmountRegex.findAll(line).forEach { match ->
            addAmountCandidate(match.groupValues.getOrNull(1), line, context, index, 14, output)
        }
        yuanAmountRegex.findAll(line).forEach { match ->
            addAmountCandidate(match.groupValues.getOrNull(1), line, context, index, 10, output)
        }
        standaloneAmountRegex.matchEntire(line)?.let { match ->
            val rawAmount = match.groupValues.getOrNull(1)
            if (amountPositiveKeywords.any { context.contains(it) } || rawAmount?.isNegativeAmountText() == true) {
                addAmountCandidate(rawAmount, line, context, index, 12, output)
            }
        }
    }

    private fun addAmountCandidate(
        rawValue: String?,
        segment: String,
        context: String,
        index: Int,
        baseScore: Int,
        output: MutableList<AmountCandidate>
    ) {
        val amountText = rawValue ?: return
        val cents = amountText.toAmountCentsOrNull() ?: return
        if (looksLikeIdentifierOrDate(segment) && baseScore < 14) return
        val positive = amountPositiveKeywords.count { context.contains(it) } * 3
        val negative = amountNegativeKeywords.count { segment.contains(it) } * 6
        val decimalBonus = if (amountText.contains('.') || amountText.contains(',')) 2 else 0
        val signBonus = if (amountText.isNegativeAmountText()) 4 else 0
        output += AmountCandidate(cents, baseScore + positive + decimalBonus + signBonus - negative, index, amountText.isNegativeAmountText())
    }

    private fun looksLikeIdentifierOrDate(context: String): Boolean {
        return Regex("""\d{4}[-/.年]\d{1,2}""").containsMatchIn(context) ||
            Regex("""\d{1,2}:\d{2}""").containsMatchIn(context) ||
            context.contains("订单") ||
            context.contains("单号") ||
            context.contains("流水")
    }

    private fun findMerchant(title: String, body: String): String {
        val merged = listOf(title, body).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (merged.isBlank()) return ""

        val lines = merged.split('\n', ' ', '\t').map { it.trim() }.filter { it.isNotBlank() }
        val candidateFromTitle = title.trim().takeIf {
            it.isNotBlank() && it.isLikelyMerchant()
        }
        if (candidateFromTitle != null) return candidateFromTitle

        lines.forEachIndexed { index, line ->
            val labeled = merchantLabelRegex.find(line)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (labeled.isLikelyMerchant()) return labeled
            if (line.isMerchantLabelLine()) {
                val nextLine = lines.getOrNull(index + 1).orEmpty()
                if (nextLine.isLikelyMerchant()) return nextLine
            }
        }

        lines.firstOrNull { line -> line.isNamedMerchantLine() && line.isLikelyMerchant() }?.let { return it }
        Regex("""([^\s，。,;；]{2,30}(?:商户|商家|店铺))""")
            .find(merged)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isLikelyMerchant() }
            ?.let { return it }

        val regexCandidates = listOf(
            Regex("""(?:向|给|支付给|收款来自|商户[:：]?)\s*([^\s，。,;；]{2,30})"""),
            Regex("""([^\s，。,;；]{2,30})\s*(?:支付|消费|收款|退款|充值)""")
        )
        for (regex in regexCandidates) {
            val found = regex.find(merged)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (found.isLikelyMerchant()) {
                return found
            }
        }

        return lines.firstOrNull { line ->
            line.isLikelyMerchant()
        }.orEmpty()
    }

    private fun findDetailMerchant(lines: List<String>): String {
        val amountIndex = lines.indexOfFirst { line ->
            standaloneAmountRegex.matchEntire(line.trim()) != null ||
                currencyAmountRegex.find(line)?.value == line.trim()
        }
        if (amountIndex > 0) {
            (amountIndex - 1 downTo (amountIndex - 5).coerceAtLeast(0))
                .map { cleanMerchantCandidate(lines[it]) }
                .firstOrNull { it.isLikelyMerchant() && !it.isAmountLabelLine() }
                ?.let { return it }
        }
        lines.forEachIndexed { index, line ->
            if (line.isMerchantLabelLine()) {
                findMerchantAfterLabel(lines, index)?.let { return it }
            }
            val labeled = merchantLabelRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
            if (labeled.isLikelyMerchant()) return labeled
        }
        return ""
    }

    private fun findMerchantAfterLabel(lines: List<String>, labelIndex: Int): String? {
        return (labelIndex + 1..(labelIndex + 4).coerceAtMost(lines.lastIndex))
            .asSequence()
            .map { cleanMerchantCandidate(lines[it]) }
            .firstOrNull { it.isLikelyMerchant() }
    }

    private fun String.isLikelyMerchant(): Boolean {
        val value = trim()
        val namedMerchantLabel = value.isNamedMerchantLine()
        val hasMerchantLetters = value.any { it.isLetter() || it in '\u4e00'..'\u9fff' }
        val digitCount = value.count { it.isDigit() }
        val visibleCharCount = value.count { !it.isWhitespace() }
        return value.length in 2..40 &&
            hasMerchantLetters &&
            (namedMerchantLabel || merchantCleanup.none { cleanup -> value.contains(cleanup) }) &&
            amountNegativeKeywords.none { keyword -> value.contains(keyword) } &&
            !value.looksLikeMonthlySummaryLine() &&
            !standaloneAmountRegex.matches(value) &&
            !chineseDateTimeRegex.containsMatchIn(value) &&
            !relativeTimeRegex.matches(value) &&
            !value.matches(Regex("""[-+\p{Sc}.,:/\\\d\s]+""")) &&
            !(digitCount > 0 && digitCount >= visibleCharCount - 1)
    }

    private fun String.isLikelyAlipayListMerchant(): Boolean {
        val value = cleanMerchantCandidate(this)
        if (value.length !in 2..64) return false
        if (value.looksLikeAlipayListNoiseLine()) return false
        if (value.looksLikeMonthlySummaryLine()) return false
        if (standaloneAmountRegex.matches(value)) return false
        if (findAlipayListTimeMatch(value) != null || chineseDateTimeRegex.containsMatchIn(value)) return false
        if (value.contains("¥") || value.contains("￥") || value.contains("元")) return false
        if (merchantCleanup.any { cleanup -> value == cleanup }) return false
        return value.any { it.isLetter() || it in '\u4e00'..'\u9fff' } || value.startsWith("订单")
    }

    private fun String.looksLikeAlipayListNoiseLine(): Boolean {
        val value = trim()
        return value in setOf(
            "转账", "支出", "收入", "全部", "退款", "筛选", "搜索", "订单", "闪购", "收支分析",
            "自动扣款成功", "交易关闭", "客服", "本月", "功能", "搜索交易记录", "贴新能功能"
        ) ||
            value.matches(Regex("""\d+月V?""")) ||
            value.matches(Regex("""\d{1,2}:\d{2}""")) ||
            value in setOf("餐饮美食", "交通出行", "教育培训", "文化休闲", "商业服务", "生活服务", "日用百货", "购物消费")
    }

    private fun String.looksLikeAlipayAuxiliaryAmountLine(): Boolean {
        return contains("本月已省") ||
            contains("已省") ||
            contains("红包") ||
            contains("抵扣") ||
            contains("优惠") ||
            contains("奖励") ||
            contains("积分")
    }

    private fun String.isNamedMerchantLine(): Boolean {
        return Regex(""".{2,}(?:商户|商家|店铺)$""").matches(trim())
    }

    private fun String.isMerchantLabelLine(): Boolean {
        val value = trim().trimEnd(':', '：')
        return value in setOf("商户", "商户全称", "商户简称", "商家", "店铺", "商品", "商品说明", "收款方", "付款给", "支付给", "对方账户", "对方")
    }

    private fun String.isAmountLabelLine(): Boolean {
        val value = trim().trimEnd(':', '：')
        return value in setOf(
            "支付金额",
            "付款金额",
            "收款金额",
            "实付款",
            "实付",
            "实际付款",
            "实际支付",
            "消费金额",
            "订单金额",
            "转账金额",
            "退款金额",
            "总金额",
            "总计",
            "金额",
            "合计",
            "共计",
            "需付款",
            "已支付",
            "支出",
            "收入"
        )
    }

    private fun inferTransactionType(text: String): TransactionType {
        val normalized = normalizeOcrText(text).lowercase()
        return when {
            normalized.contains("收款成功") ||
                normalized.contains("已收款") ||
                normalized.contains("二维码收款") ||
                normalized.contains("退款") ||
                normalized.contains("返现") ||
                normalized.contains("收入") ||
                normalized.contains("工资") ||
                normalized.contains("薪资") ||
                normalized.contains("奖金") ||
                normalized.contains("副业") ||
                normalized.contains("兼职") ||
                normalized.contains("投资收益") ||
                normalized.contains("理财收益") ||
                normalized.contains("股票收益") -> TransactionType.INCOME
            normalized.contains("付款成功") ||
                normalized.contains("支付成功") ||
                normalized.contains("交易成功") ||
                normalized.contains("已支付") ||
                Regex("""(^|\s)-\s*(?:¥)?\s*\d""").containsMatchIn(normalized) -> TransactionType.EXPENSE
            normalized.contains("转账") ||
                normalized.contains("红包") &&
                !normalized.contains("抵扣") &&
                !normalized.contains("优惠") -> TransactionType.TRANSFER
            else -> TransactionType.EXPENSE
        }
    }

    private fun cleanSourcePackage(sourcePackage: String): String = when (sourcePackage) {
        "com.tencent.mm" -> "微信"
        "com.eg.android.AlipayGphone" -> "支付宝"
        else -> sourcePackage.ifBlank { "系统" }
    }

    private fun findOccurredAtMillis(text: String): Long? {
        fullDateTimeRegex.find(text)?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let null
            val month = match.groupValues[2].toIntOrNull() ?: return@let null
            val day = match.groupValues[3].toIntOrNull() ?: return@let null
            val hour = match.groupValues[4].toIntOrNull() ?: return@let null
            val minute = match.groupValues[5].toIntOrNull() ?: return@let null
            val second = match.groupValues.getOrNull(6)?.toIntOrNull() ?: 0
            return toMillis(year, month, day, hour, minute, second)
        }
        chineseDateTimeRegex.find(text)?.let { match ->
            val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: LocalDate.now().year
            return parseChineseDateTime(match.value, year)
        }
        alipayListDateTimeRegex.find(text)?.let { match ->
            return parseMonthDayTimeMatch(match, fallbackYear = null)
        }
        return parseFlexibleMessageTimeFromLine(text)
    }

    private fun parseChineseDateTime(value: String, defaultYear: Int): Long? {
        val match = chineseDateTimeRegex.find(value) ?: return null
        val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: defaultYear
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val period = match.groupValues.getOrNull(4).orEmpty()
        val hour = adjustChineseHour(match.groupValues[5].toIntOrNull() ?: return null, period)
        val minute = match.groupValues[6].toIntOrNull() ?: return null
        val second = match.groupValues.getOrNull(7)?.toIntOrNull() ?: 0
        return toMillis(year, month, day, hour, minute, second)
    }

    private fun parseFlexibleMessageTime(value: String): Long? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        chineseDateTimeRegex.find(clean)?.let { match ->
            val now = LocalDate.now()
            val month = match.groupValues[2].toIntOrNull() ?: return@let null
            val defaultYear = if (month > now.monthValue && now.monthValue == 1) now.year - 1 else now.year
            return parseChineseDateTime(match.value, defaultYear)
        }
        val match = relativeTimeRegex.matchEntire(clean) ?: return null
        val dayOffset = when (match.groupValues.getOrNull(1).orEmpty()) {
            "昨天" -> 1L
            "前天" -> 2L
            else -> 0L
        }
        val period = match.groupValues.getOrNull(2).orEmpty()
        val hour = adjustChineseHour(match.groupValues[3].toIntOrNull() ?: return null, period)
        val minute = match.groupValues[4].toIntOrNull() ?: return null
        val second = match.groupValues.getOrNull(5)?.toIntOrNull() ?: 0
        return LocalDate.now()
            .minusDays(dayOffset)
            .atTime(LocalTime.of(hour, minute, second))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun parseFlexibleMessageTimeFromLine(value: String): Long? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        parseFlexibleMessageTime(clean)?.let { return it }
        chineseDateTimeRegex.find(clean)?.let { match ->
            val now = LocalDate.now()
            val month = match.groupValues[2].toIntOrNull() ?: return@let null
            val defaultYear = if (month > now.monthValue && now.monthValue == 1) now.year - 1 else now.year
            return parseChineseDateTime(match.value, defaultYear)
        }
        relativeTimeRegex.find(clean)?.let { match ->
            return parseFlexibleMessageTime(match.value)
        }
        return null
    }

    private fun parseMonthDayTimeMatch(match: MatchResult, fallbackYear: Int?): Long? {
        val now = LocalDate.now()
        val month = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        val year = match.groupValues.getOrNull(1)?.toIntOrNull()
            ?: fallbackYear
            ?: if (month > now.monthValue && now.monthValue == 1) now.year - 1 else now.year
        val day = match.groupValues.getOrNull(3)?.toIntOrNull() ?: return null
        val hour = match.groupValues.getOrNull(4)?.toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(5)?.toIntOrNull() ?: return null
        val second = match.groupValues.getOrNull(6)?.toIntOrNull() ?: 0
        return toMillis(year, month, day, hour, minute, second)
    }

    private fun String.merchantPrefixBeforeTime(): String {
        val clean = trim()
        val timeStart = listOfNotNull(
            fullDateTimeRegex.find(clean)?.range?.first,
            chineseDateTimeRegex.find(clean)?.range?.first,
            alipayListDateTimeRegex.find(clean)?.range?.first,
            relativeTimeRegex.find(clean)?.range?.first
        ).minOrNull() ?: return ""
        return cleanMerchantCandidate(clean.take(timeStart))
    }

    private fun adjustChineseHour(hour: Int, period: String): Int {
        return when (period) {
            "下午", "晚上" -> if (hour < 12) hour + 12 else hour
            "中午" -> if (hour < 11) hour + 12 else hour
            else -> hour
        }.coerceIn(0, 23)
    }

    private fun toMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long? {
        return runCatching {
            LocalDateTime.of(year, month, day, hour.coerceIn(0, 23), minute.coerceIn(0, 59), second.coerceIn(0, 59))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun normalizeOcrText(text: String): String = buildString(text.length) {
        text.forEach { char ->
            append(
                when (char) {
                    in '０'..'９' -> '0' + (char - '０')
                    '．', '。' -> '.'
                    '，' -> ','
                    '：' -> ':'
                    '￥' -> '¥'
                    '－', '﹣', '–', '—', '−' -> '-'
                    else -> char
                }
            )
        }
    }

    private fun normalizeForFingerprint(text: String): String {
        return normalizeOcrText(text)
            .lowercase()
            .replace(Regex("""\s+"""), "")
            .replace(Regex("""[，。,:：；;|｜]"""), "")
    }

    private fun findPaymentTimeKey(text: String): String {
        fullDateTimeRegex.find(text)?.let {
            return it.value.replace(Regex("""\s+"""), "")
        }
        chineseDateTimeRegex.find(text)?.let {
            return it.value.replace(Regex("""\s+"""), "")
        }
        return relativeTimeRegex.find(text)
            ?.value
            ?.replace(Regex("""\s+"""), "")
            .orEmpty()
    }

    private fun String.compactKey(): String {
        return normalizeOcrText(this)
            .lowercase()
            .replace(Regex("""\s+"""), "")
            .take(24)
    }

    private fun String.toAmountCentsOrNull(): Long? {
        val clean = normalizeOcrText(this)
            .trim()
            .lowercase()
            .replace("人民币", "")
            .replace("rmb", "")
            .replace("cny", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("+", "")
            .replace("-", "")
            .replace(Regex("""\s+"""), "")
        val normalized = when {
            clean.contains(",") && clean.contains(".") -> clean.replace(",", "")
            Regex("""^\d{1,3}(?:,\d{3})+$""").matches(clean) -> clean.replace(",", "")
            clean.contains(",") -> clean.replace(",", ".")
            else -> clean
        }
        val parts = normalized.split('.')
        if (parts.firstOrNull().orEmpty().length > 7) return null
        return normalized
            .toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.movePointRight(2)
            ?.longValueExact()
            ?.takeIf { it in 1L..99_999_999L }
    }

    private fun String.isNegativeAmountText(): Boolean {
        return normalizeOcrText(this).trim().startsWith("-")
    }

    private fun String.looksLikeMonthlySummaryLine(): Boolean {
        return contains("支出") && contains("收入") ||
            contains("收支统计")
    }

    private fun String.linesForParsing(): List<String> {
        return normalizeOcrText(this)
            .replace('\r', '\n')
            .split('\n', '|', '｜')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

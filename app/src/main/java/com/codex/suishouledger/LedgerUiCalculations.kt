package com.codex.suishouledger

import com.codex.suishouledger.data.local.LedgerEntryEntity
import com.codex.suishouledger.data.local.ReviewState
import com.codex.suishouledger.data.local.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun evaluateAmountExpressionCents(input: String): Long? {
    val clean = input.trim()
    if (clean.isBlank() || clean.any { it !in "0123456789.+-*/ " }) return null
    val parser = AmountExpressionParser(clean)
    val amount = parser.parse() ?: return null
    if (amount <= BigDecimal.ZERO) return null
    return runCatching {
        amount.setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}

internal fun buildMonthSelectionOptions(
    entries: List<LedgerEntryEntity>,
    selectedMonthStartMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): List<Long> {
    val currentMonth = uiMonthStartMillis(nowMillis)
    return buildSet {
        add(currentMonth)
        add(uiShiftMonth(currentMonth, -1))
        add(uiShiftMonth(currentMonth, 1))
        add(selectedMonthStartMillis)
        add(uiShiftMonth(selectedMonthStartMillis, -1))
        add(uiShiftMonth(selectedMonthStartMillis, 1))
        entries.forEach { add(uiMonthStartMillis(it.occurredAt)) }
    }.sortedDescending()
}

internal fun buildYearPickerOptions(
    entries: List<LedgerEntryEntity>,
    selectedMonthStartMillis: Long,
    nowMillis: Long = System.currentTimeMillis()
): List<Int> {
    val currentYear = uiYearOf(nowMillis)
    return buildSet {
        (currentYear - 5..currentYear + 1).forEach { add(it) }
        add(uiYearOf(selectedMonthStartMillis))
        entries.forEach { add(uiYearOf(it.occurredAt)) }
    }.sortedDescending()
}

internal fun filterMonthCategoryExpenses(
    entries: List<LedgerEntryEntity>,
    categoryCode: String,
    monthStartMillis: Long
): List<LedgerEntryEntity> {
    return entries
        .filter {
            it.reviewState == ReviewState.CONFIRMED &&
                it.transactionType == TransactionType.EXPENSE &&
                it.categoryCode == categoryCode &&
                uiIsSameMonth(it.occurredAt, monthStartMillis)
        }
        .sortedByDescending { it.occurredAt }
}

internal fun uiMonthStartMillis(timeMillis: Long): Long {
    return Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = timeMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun uiShiftMonth(monthStartMillis: Long, delta: Int): Long {
    return Calendar.getInstance(Locale.CHINA).apply {
        timeInMillis = monthStartMillis
        add(Calendar.MONTH, delta)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun uiMonthStartMillis(year: Int, month: Int): Long {
    return Calendar.getInstance(Locale.CHINA).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

internal fun uiYearOf(timeMillis: Long): Int {
    return Calendar.getInstance(Locale.CHINA).apply { timeInMillis = timeMillis }.get(Calendar.YEAR)
}

internal fun uiMonthOf(timeMillis: Long): Int {
    return Calendar.getInstance(Locale.CHINA).apply { timeInMillis = timeMillis }.get(Calendar.MONTH) + 1
}

private fun uiIsSameMonth(timeMillis: Long, monthStartMillis: Long): Boolean {
    val calendar = Calendar.getInstance(Locale.CHINA)
    val target = calendar.apply { time = Date(timeMillis) }
    val targetYear = target.get(Calendar.YEAR)
    val targetMonth = target.get(Calendar.MONTH)
    calendar.time = Date(monthStartMillis)
    return targetYear == calendar.get(Calendar.YEAR) && targetMonth == calendar.get(Calendar.MONTH)
}

private class AmountExpressionParser(private val input: String) {
    private var index = 0

    fun parse(): BigDecimal? {
        val value = parseExpression() ?: return null
        skipSpaces()
        return value.takeIf { index == input.length }
    }

    private fun parseExpression(): BigDecimal? {
        var value = parseTerm() ?: return null
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '+' -> {
                    index++
                    value + (parseTerm() ?: return null)
                }
                '-' -> {
                    index++
                    value - (parseTerm() ?: return null)
                }
                else -> return value
            }
        }
    }

    private fun parseTerm(): BigDecimal? {
        var value = parseNumber() ?: return null
        while (true) {
            skipSpaces()
            value = when (peek()) {
                '*' -> {
                    index++
                    value * (parseNumber() ?: return null)
                }
                '/' -> {
                    index++
                    val divisor = parseNumber() ?: return null
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) return null
                    value.divide(divisor, 8, RoundingMode.HALF_UP)
                }
                else -> return value
            }
        }
    }

    private fun parseNumber(): BigDecimal? {
        skipSpaces()
        val start = index
        var dotCount = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char.isDigit() -> index++
                char == '.' -> {
                    dotCount++
                    if (dotCount > 1) return null
                    index++
                }
                else -> break
            }
        }
        if (start == index) return null
        val raw = input.substring(start, index)
        if (raw == ".") return null
        return raw.toBigDecimalOrNull()
    }

    private fun skipSpaces() {
        while (index < input.length && input[index].isWhitespace()) index++
    }

    private fun peek(): Char? = input.getOrNull(index)
}

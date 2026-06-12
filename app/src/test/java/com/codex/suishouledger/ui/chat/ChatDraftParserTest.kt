package com.codex.suishouledger.ui.chat

import com.codex.suishouledger.data.local.AccountEntity
import com.codex.suishouledger.data.local.AccountType
import com.codex.suishouledger.data.local.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class ChatDraftParserTest {
    @Test
    fun bookkeepingIntentSupportsPayAndBuyPhrases() {
        assertTrue(looksLikeBookkeepingInput("帮我记一下昨天买机票650 微信"))
        assertTrue(looksLikeBookkeepingInput("补记5号话费28块"))
        assertTrue(looksLikeBookkeepingInput("支付宝付款咖啡 18"))
    }

    @Test
    fun communicationKeywordsWinOverFallbackCategory() {
        val draft = parseTransactionDraftFromContent(
            messageId = 1L,
            content = "帮我补一笔5号的话费 28块",
            categories = categories(),
            accounts = accounts()
        )

        assertNotNull(draft)
        assertEquals("communication", draft!!.categoryCode)
        assertEquals("通讯", draft.categoryName)
    }

    @Test
    fun unknownExpenseFallsBackToOtherNotFirstCategory() {
        val draft = parseTransactionDraftFromContent(
            messageId = 2L,
            content = "帮我记一笔奇怪商户 12块",
            categories = categories(),
            accounts = accounts()
        )

        assertNotNull(draft)
        assertEquals("other", draft!!.categoryCode)
        assertEquals("其他", draft.categoryName)
    }

    @Test
    fun dayOnlyDateUsesCurrentMonth() {
        val millis = parseDraftDateText("5号晚上 8:30")

        assertNotNull(millis)
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis!!))
        val expectedPrefix = LocalDate.now(ZoneId.systemDefault()).withDayOfMonth(5).toString()
        assertEquals("$expectedPrefix 20:30", formatted)
    }

    @Test
    fun previousWeekdayDateCanBeParsed() {
        val millis = parseDraftDateText("上周五晚上")

        assertNotNull(millis)
        val date = Date(millis!!)
        val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(date)
        assertTrue(formatted.endsWith("20:00"))
    }

    private fun categories() = listOf(
        CategoryEntity("food", "餐饮", "restaurant", "#EF4444", 10, isIncome = false),
        CategoryEntity("communication", "通讯", "phone", "#06B6D4", 20, isIncome = false),
        CategoryEntity("travel", "旅行", "flight", "#6366F1", 30, isIncome = false),
        CategoryEntity("other", "其他", "more", "#64748B", 99, isIncome = false),
        CategoryEntity("salary", "工资", "wallet", "#22C55E", 10, isIncome = true),
        CategoryEntity("other_income", "其他收入", "more", "#16A34A", 99, isIncome = true)
    )

    private fun accounts() = listOf(
        AccountEntity("wechat", "微信", "wechat", "#22C55E", AccountType.WECHAT, sortOrder = 10),
        AccountEntity("alipay", "支付宝", "alipay", "#1677FF", AccountType.ALIPAY, sortOrder = 20),
        AccountEntity("cash", "现金", "payments", "#64748B", AccountType.CASH, sortOrder = 30)
    )
}

package com.codex.suishouledger.domain

import com.codex.suishouledger.data.local.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {
    private val categories = DefaultCategories.all()

    @Test
    fun parseText_supportsMerchantNamesWithDigitsAndRelativeDetailTime() {
        val text = """
            微信支付
            付款成功
            商户全称
            7-ELEVEN便利店
            支付金额
            ¥12.50
            支付时间
            昨天 23:13
        """.trimIndent()

        val parsed = PaymentParser.parseText(text, categories)

        assertEquals(1250L, parsed.amountCents)
        assertEquals("7-ELEVEN便利店", parsed.merchant)
        assertNotNull(parsed.occurredAt)
    }

    @Test
    fun parseTextEntries_supportsWechatBillRowsWithMonthDayTimeOnSameLine() {
        val text = """
            全部账单
            美团外卖 05-17 12:34
            -¥32.80
        """.trimIndent()

        val parsed = PaymentParser.parseTextEntries(text, categories, allowSingleListEntry = true)

        assertEquals(1, parsed.size)
        assertEquals("美团外卖", parsed.first().merchant)
        assertEquals(3280L, parsed.first().amountCents)
        assertNotNull(parsed.first().occurredAt)
    }

    @Test
    fun parseTextEntries_supportsWechatBillRowsWithRelativeTimeOnSameLine() {
        val text = """
            全部账单
            瑞幸咖啡 昨天 08:15
            -¥19.90
        """.trimIndent()

        val parsed = PaymentParser.parseTextEntries(text, categories, allowSingleListEntry = true)

        assertEquals(1, parsed.size)
        assertEquals("瑞幸咖啡", parsed.first().merchant)
        assertEquals(1990L, parsed.first().amountCents)
        assertTrue(parsed.first().occurredAt != null)
    }

    @Test
    fun looksLikePaymentScreenshotText_rejectsGenericChatScreenshots() {
        val text = """
            群聊
            昨天 21:08
            这个截图好看吗
            图片已发送
        """.trimIndent()

        assertFalse(PaymentParser.looksLikePaymentScreenshotText(text))
    }

    @Test
    fun looksLikePaymentScreenshotText_acceptsPaymentReceiptText() {
        val text = """
            微信支付
            支付金额
            ¥18.00
            交易时间 2026-06-10 12:34
        """.trimIndent()

        assertTrue(PaymentParser.looksLikePaymentScreenshotText(text))
    }
}

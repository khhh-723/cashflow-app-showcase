package com.codex.suishouledger.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentNotificationListenerServiceTest {

    @Test
    fun allowsOnlyPaymentPackageWhitelist() {
        assertTrue(PaymentNotificationListenerService.shouldHandlePackage("com.tencent.mm"))
        assertTrue(PaymentNotificationListenerService.shouldHandlePackage("com.eg.android.AlipayGphone"))
        assertFalse(PaymentNotificationListenerService.shouldHandlePackage("com.example.fakepayment"))
        assertFalse(PaymentNotificationListenerService.shouldHandlePackage("android"))
        assertFalse(PaymentNotificationListenerService.shouldHandlePackage(null))
    }

    @Test
    fun rejectsWechatChatMessagesBeforeDraftCreation() {
        assertFalse(
            com.codex.suishouledger.domain.PaymentParser.looksLikePaymentNotification(
                "com.tencent.mm",
                "张琳琳丸\n备注"
            )
        )
        assertFalse(
            com.codex.suishouledger.domain.PaymentParser.looksLikePaymentNotification(
                "com.tencent.mm",
                "【阿里巴巴校园招聘】群消息"
            )
        )
    }

    @Test
    fun allowsWechatAndAlipayPaymentNotificationsWithAmounts() {
        assertTrue(
            com.codex.suishouledger.domain.PaymentParser.looksLikePaymentNotification(
                "com.tencent.mm",
                "微信支付\n付款成功 ¥14.00\n淘宝平台商户"
            )
        )
        assertTrue(
            com.codex.suishouledger.domain.PaymentParser.looksLikePaymentNotification(
                "com.eg.android.AlipayGphone",
                "支付宝\n支付成功 ￥36.80\n子肉卷"
            )
        )
    }

    @Test
    fun rateLimiterRejectsEventsBeyondWindowLimit() {
        val limiter = NotificationRateLimiter(maxEvents = 2, windowMillis = 1_000L)

        assertTrue(limiter.tryAcquire("com.tencent.mm", 1_000L))
        assertTrue(limiter.tryAcquire("com.tencent.mm", 1_100L))
        assertFalse(limiter.tryAcquire("com.tencent.mm", 1_200L))
        assertTrue(limiter.tryAcquire("com.tencent.mm", 2_100L))
    }

    @Test
    fun rateLimiterSeparatesPackages() {
        val limiter = NotificationRateLimiter(maxEvents = 1, windowMillis = 1_000L)

        assertTrue(limiter.tryAcquire("com.tencent.mm", 1_000L))
        assertFalse(limiter.tryAcquire("com.tencent.mm", 1_100L))
        assertTrue(limiter.tryAcquire("com.eg.android.AlipayGphone", 1_100L))
    }
}

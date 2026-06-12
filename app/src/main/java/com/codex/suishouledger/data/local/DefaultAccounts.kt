package com.codex.suishouledger.data.local

object DefaultAccounts {
    fun all(): List<AccountEntity> = listOf(
        AccountEntity("wechat", "微信", "wechat", "#22C55E", AccountType.WECHAT, sortOrder = 10),
        AccountEntity("alipay", "支付宝", "alipay", "#1677FF", AccountType.ALIPAY, sortOrder = 20),
        AccountEntity("bank", "银行卡", "credit_card", "#6366F1", AccountType.BANK_CARD, sortOrder = 30),
        AccountEntity("cash", "现金", "payments", "#64748B", AccountType.CASH, sortOrder = 40)
    )
}

package com.codex.suishouledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val type: AccountType = AccountType.OTHER,
    val balanceCents: Long? = null,
    val syncEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val isSystem: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AccountType {
    WECHAT,
    ALIPAY,
    BANK_CARD,
    CASH,
    OTHER
}

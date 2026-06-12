package com.codex.suishouledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val code: String,
    val name: String,
    val iconKey: String,
    val colorHex: String,
    val sortOrder: Int = 0,
    val monthlyBudgetCents: Long? = null,
    val isIncome: Boolean = false,
    val isSystem: Boolean = true
)

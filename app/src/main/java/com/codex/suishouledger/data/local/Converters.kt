package com.codex.suishouledger.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromReviewState(value: ReviewState): String = value.name

    @TypeConverter
    fun toReviewState(value: String): ReviewState = ReviewState.valueOf(value)

    @TypeConverter
    fun fromIngestionState(value: IngestionState): String = value.name

    @TypeConverter
    fun toIngestionState(value: String): IngestionState = IngestionState.valueOf(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)
}

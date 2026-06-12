package com.codex.suishouledger.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CategoryEntity::class, LedgerEntryEntity::class, AccountEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao

    companion object {
        private const val CASH_NAME = "\u73b0\u91d1"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "categories", "monthlyBudgetCents", "monthlyBudgetCents INTEGER")
                addColumnIfMissing(db, "ledger_entries", "accountCode", "accountCode TEXT NOT NULL DEFAULT 'cash'")
                addColumnIfMissing(
                    db,
                    "ledger_entries",
                    "accountNameSnapshot",
                    "accountNameSnapshot TEXT NOT NULL DEFAULT '$CASH_NAME'"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_accountCode ON ledger_entries(accountCode)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        code TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        iconKey TEXT NOT NULL,
                        colorHex TEXT NOT NULL,
                        type TEXT NOT NULL,
                        balanceCents INTEGER,
                        syncEnabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        isSystem INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                val now = System.currentTimeMillis()
                insertSystemAccount(db, "wechat", "\u5fae\u4fe1", "wechat", "#22C55E", "WECHAT", 10, now)
                insertSystemAccount(db, "alipay", "\u652f\u4ed8\u5b9d", "alipay", "#1677FF", "ALIPAY", 20, now)
                insertSystemAccount(db, "bank", "\u94f6\u884c\u5361", "credit_card", "#6366F1", "BANK_CARD", 30, now)
                insertSystemAccount(db, "cash", CASH_NAME, "payments", "#64748B", "CASH", 40, now)
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return
                }
            }
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnDefinition")
        }

        private fun insertSystemAccount(
            db: SupportSQLiteDatabase,
            code: String,
            name: String,
            iconKey: String,
            colorHex: String,
            type: String,
            sortOrder: Int,
            now: Long
        ) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts (
                    code,
                    name,
                    iconKey,
                    colorHex,
                    type,
                    balanceCents,
                    syncEnabled,
                    sortOrder,
                    isSystem,
                    createdAt,
                    updatedAt
                ) VALUES (?, ?, ?, ?, ?, NULL, 0, ?, 1, ?, ?)
                """.trimIndent(),
                arrayOf(code, name, iconKey, colorHex, type, sortOrder, now, now)
            )
        }
    }
}

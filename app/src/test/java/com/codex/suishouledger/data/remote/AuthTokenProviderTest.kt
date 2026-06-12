package com.codex.suishouledger.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthTokenProviderTest {

    @Test
    fun legacyDataStoreTokenMigratesToSecureAuthSnapshot() {
        val migrated = migrateLegacyAuthSnapshot(
            token = "legacy-token",
            userId = "42",
            username = "Kai",
            email = "kai@example.com"
        )

        assertEquals("legacy-token", migrated.token)
        assertEquals("42", migrated.userIdText)
        assertEquals(42L, migrated.userId)
        assertEquals("Kai", migrated.username)
        assertEquals("kai@example.com", migrated.email)
    }

    @Test
    fun legacyMigrationKeepsEmailPrefillButDropsBlankUserFields() {
        val migrated = migrateLegacyAuthSnapshot(
            token = "legacy-token",
            userId = "not-a-number",
            username = "",
            email = "kai@example.com"
        )

        assertEquals("legacy-token", migrated.token)
        assertEquals("not-a-number", migrated.userIdText)
        assertNull(migrated.userId)
        assertNull(migrated.username)
        assertEquals("kai@example.com", migrated.email)
    }
}

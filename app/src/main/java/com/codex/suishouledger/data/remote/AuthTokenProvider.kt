package com.codex.suishouledger.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codex.suishouledger.data.sync.SyncAuthStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

class AuthTokenProvider internal constructor(
    private val context: Context,
    private val authCipher: AuthCipher
) : SyncAuthStore {

    constructor(context: Context) : this(context, AndroidKeyStoreAuthCipher())

    companion object {
        private const val SECURE_PREFS_NAME = "secure_auth_prefs"
        private const val TOKEN_PREF = "auth_token"
        private const val USER_ID_PREF = "user_id"
        private const val USERNAME_PREF = "username"
        private const val EMAIL_PREF = "email"

        internal val TOKEN_KEY = stringPreferencesKey("auth_token")
        internal val USER_ID_KEY = stringPreferencesKey("user_id")
        internal val USERNAME_KEY = stringPreferencesKey("username")
        internal val EMAIL_KEY = stringPreferencesKey("email")
    }

    private val securePrefs by lazy {
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val tokenState = MutableStateFlow(readSecureString(TOKEN_PREF))
    private val userIdState = MutableStateFlow(readSecureString(USER_ID_PREF)?.toLongOrNull())
    private val usernameState = MutableStateFlow(readSecureString(USERNAME_PREF))
    private val emailState = MutableStateFlow(readSecureString(EMAIL_PREF))

    val tokenFlow: Flow<String?> = migratedFlow(tokenState)

    val userIdFlow: Flow<Long?> = migratedFlow(userIdState)

    val usernameFlow: Flow<String?> = migratedFlow(usernameState)

    val emailFlow: Flow<String?> = migratedFlow(emailState)

    suspend fun saveAuth(token: String, userId: Long, username: String, email: String) {
        securePrefs.edit()
            .putString(TOKEN_PREF, authCipher.encrypt(token))
            .putString(USER_ID_PREF, authCipher.encrypt(userId.toString()))
            .putString(USERNAME_PREF, authCipher.encrypt(username))
            .putString(EMAIL_PREF, authCipher.encrypt(email))
            .apply()
        tokenState.value = token
        userIdState.value = userId
        usernameState.value = username
        emailState.value = email
        context.authDataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs[EMAIL_KEY] = email
        }
    }

    suspend fun clearAuth() {
        securePrefs.edit()
            .remove(TOKEN_PREF)
            .remove(USER_ID_PREF)
            .remove(USERNAME_PREF)
            .apply()
        tokenState.value = null
        userIdState.value = null
        usernameState.value = null
        context.authDataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
        }
    }

    suspend fun getToken(): String? {
        tokenState.value?.takeIf { it.isNotBlank() }?.let { return it }
        migrateLegacyAuthIfNeeded()
        return tokenState.value?.takeIf { it.isNotBlank() }
    }

    override suspend fun getBearerToken(): String? {
        return getToken()?.let { "Bearer $it" }
    }

    override suspend fun clearExpiredAuth() {
        clearAuth()
    }

    override suspend fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    private fun readSecureString(key: String): String? {
        return runCatching {
            securePrefs.getString(key, null)?.let { authCipher.decrypt(it) }
        }.getOrNull()
    }

    private suspend fun migrateLegacyAuthIfNeeded() {
        val prefs = context.authDataStore.data.first()
        val legacyToken = prefs[TOKEN_KEY]?.takeIf { it.isNotBlank() }
        val legacyUserIdText = prefs[USER_ID_KEY]
        val legacyUsername = prefs[USERNAME_KEY].orEmpty()
        val legacyEmail = prefs[EMAIL_KEY].orEmpty()
        if (legacyToken == null) {
            if (emailState.value == null && legacyEmail.isNotBlank()) {
                securePrefs.edit()
                    .putString(EMAIL_PREF, authCipher.encrypt(legacyEmail))
                    .apply()
                emailState.value = legacyEmail
            }
            return
        }
        val migrated = migrateLegacyAuthSnapshot(
            token = legacyToken,
            userId = legacyUserIdText,
            username = legacyUsername,
            email = legacyEmail
        )
        securePrefs.edit()
            .putString(TOKEN_PREF, authCipher.encrypt(migrated.token))
            .putString(USER_ID_PREF, authCipher.encrypt(migrated.userIdText))
            .putString(USERNAME_PREF, authCipher.encrypt(migrated.username.orEmpty()))
            .putString(EMAIL_PREF, authCipher.encrypt(migrated.email.orEmpty()))
            .apply()
        tokenState.value = migrated.token
        userIdState.value = migrated.userId
        usernameState.value = migrated.username
        emailState.value = migrated.email
        context.authDataStore.edit { editable ->
            editable.remove(TOKEN_KEY)
            editable.remove(USER_ID_KEY)
            editable.remove(USERNAME_KEY)
            migrated.email?.let {
                editable[EMAIL_KEY] = it
            }
        }
    }

    private fun <T> migratedFlow(state: MutableStateFlow<T>): Flow<T> = flow {
        migrateLegacyAuthIfNeeded()
        emitAll(state)
    }
}

internal interface AuthCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String?
}

internal data class LegacyAuthMigration(
    val token: String,
    val userIdText: String,
    val userId: Long?,
    val username: String?,
    val email: String?
)

internal fun migrateLegacyAuthSnapshot(
    token: String,
    userId: String?,
    username: String?,
    email: String?
) = LegacyAuthMigration(
    token = token,
    userIdText = userId.orEmpty(),
    userId = userId?.toLongOrNull(),
    username = username?.ifBlank { null },
    email = email?.ifBlank { null }
)

private class AndroidKeyStoreAuthCipher : AuthCipher {
    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String? {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        if (payload.size <= GCM_IV_BYTES) return null
        val iv = payload.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cashflow_auth_key"
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
    }
}

package com.gravityos.assistant.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiMode {
    AUTO,
    ONLINE,
    OFFLINE
}

enum class OnlineProviderPreference {
    GROQ_FIRST,
    GEMINI_FIRST
}

class SecureVault(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gravity_secure_vault_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "gravity_os_aes_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_GROQ = "enc_groq_api_key"
        private const val KEY_GEMINI = "enc_gemini_api_key"
        private const val KEY_AI_MODE = "pref_ai_mode"
        private const val KEY_PROVIDER = "pref_online_provider"
        private const val KEY_USER_NAME = "pref_user_name"
    }

    fun saveGroqApiKey(key: String) {
        saveEncrypted(KEY_GROQ, key.trim())
    }

    fun getGroqApiKey(): String = readEncrypted(KEY_GROQ)

    fun saveGeminiApiKey(key: String) {
        saveEncrypted(KEY_GEMINI, key.trim())
    }

    fun getGeminiApiKey(): String = readEncrypted(KEY_GEMINI)

    fun hasGroqKey(): Boolean = getGroqApiKey().isNotBlank()

    fun hasGeminiKey(): Boolean = getGeminiApiKey().isNotBlank()

    fun setAiMode(mode: AiMode) {
        prefs.edit().putString(KEY_AI_MODE, mode.name).apply()
    }

    fun getAiMode(): AiMode {
        val raw = prefs.getString(KEY_AI_MODE, AiMode.AUTO.name)
        return try {
            AiMode.valueOf(raw ?: AiMode.AUTO.name)
        } catch (_: Throwable) {
            AiMode.AUTO
        }
    }

    fun setOnlineProvider(pref: OnlineProviderPreference) {
        prefs.edit().putString(KEY_PROVIDER, pref.name).apply()
    }

    fun getOnlineProvider(): OnlineProviderPreference {
        val raw = prefs.getString(KEY_PROVIDER, OnlineProviderPreference.GROQ_FIRST.name)
        return try {
            OnlineProviderPreference.valueOf(raw ?: OnlineProviderPreference.GROQ_FIRST.name)
        } catch (_: Throwable) {
            OnlineProviderPreference.GROQ_FIRST
        }
    }

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "")?.trim().orEmpty()

    fun clearAllSecrets() {
        prefs.edit()
            .remove(KEY_GROQ)
            .remove(KEY_GEMINI)
            .apply()
    }

    private fun saveEncrypted(prefKey: String, plainText: String) {
        if (plainText.isEmpty()) {
            prefs.edit().remove(prefKey).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            prefs.edit().putString(prefKey, combined).apply()
        } catch (_: Throwable) {
            prefs.edit().remove(prefKey).apply()
        }
    }

    private fun readEncrypted(prefKey: String): String {
        val stored = prefs.getString(prefKey, null) ?: return ""
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}

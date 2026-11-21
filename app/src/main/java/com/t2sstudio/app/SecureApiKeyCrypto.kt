package com.t2sstudio.app

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/** Shared Android Keystore AES-GCM helper for provider API keys. */
internal object SecureApiKeyCrypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    fun get(context: Context, prefs: String, value: String, alias: String): String? {
        val packed = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(value, null) ?: return null
        return runCatching {
            val parts = packed.split(':', limit = 2); require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(alias), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrNull()
    }
    fun save(context: Context, prefs: String, value: String, alias: String, clean: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, secretKey(alias))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(clean.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(value, packed).commit()) {
            "Could not save the encrypted API key."
        }
    }
    fun clear(context: Context, prefs: String, value: String, alias: String) {
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().remove(value).apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
    private fun secretKey(alias: String): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

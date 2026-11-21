package com.t2sstudio.app

import android.content.Context

/** Encrypted per-device storage for the optional Vakyam free-tier API key. */
object VakyamApiKeyStore {
    private const val PREFS = "vakyam_secure_settings"
    private const val VALUE = "encrypted_api_key"
    private const val KEY_ALIAS = "t2s_vakyam_api_aes_key"

    fun get(context: Context): String? = SecureApiKeyCrypto.get(context, PREFS, VALUE, KEY_ALIAS)

    fun save(context: Context, apiKey: String) {
        val clean = apiKey.trim()
        require(clean.startsWith("vak_live_") && clean.length > 20) {
            "Enter a valid Vakyam key beginning with vak_live_."
        }
        SecureApiKeyCrypto.save(context, PREFS, VALUE, KEY_ALIAS, clean)
    }

    fun clear(context: Context) = SecureApiKeyCrypto.clear(context, PREFS, VALUE, KEY_ALIAS)
}

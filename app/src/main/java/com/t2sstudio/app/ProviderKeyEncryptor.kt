package com.t2sstudio.app

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/** Encrypts provider credentials for the backend. No private/decryption key exists in the APK. */
object ProviderKeyEncryptor {
    // RSA-2048 with OAEP-SHA256 can encrypt at most 190 bytes per operation.
    private const val MAX_KEY_BYTES = 190

    fun encrypt(apiKey: String, publicKeyPem: String): String {
        require(apiKey.isNotBlank()) { "Enter a provider API key." }
        val keyBytesToEncrypt = apiKey.trim().toByteArray(Charsets.UTF_8)
        require(keyBytesToEncrypt.size <= MAX_KEY_BYTES) {
            "This provider key is too long for the current secure-key format."
        }

        val encodedKey = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val publicKeyBytes = Base64.decode(encodedKey, Base64.DEFAULT)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        val spec = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, spec)
        return Base64.encodeToString(cipher.doFinal(keyBytesToEncrypt), Base64.NO_WRAP)
    }
}

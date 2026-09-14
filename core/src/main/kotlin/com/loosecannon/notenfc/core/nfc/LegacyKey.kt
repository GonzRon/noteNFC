package com.loosecannon.notenfc.core.nfc

import java.security.MessageDigest

/**
 * The identifier the 2023–2024 app wrote to tags: MD5 of the shared text's UTF-8 bytes,
 * lowercase hex, first 8 characters. Preserved verbatim from `mainActivity.kt` so that
 * re-sharing the same note reproduces the same key (D6 §5). Never change this.
 */
object LegacyKey {
    fun compute(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.fold("") { str, it -> str + "%02x".format(it) }.substring(0, 8)
    }
}

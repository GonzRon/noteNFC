package com.loosecannon.notenfc.core.links

import com.loosecannon.notenfc.core.model.LinkKind

sealed interface LinkCheck {
    data class Accepted(val kind: LinkKind, val uri: String) : LinkCheck

    /** A scheme outside the allowlist: launchable only after one explicit user confirmation. */
    data class NeedsConfirmation(val scheme: String, val uri: String) : LinkCheck

    data class Rejected(val reason: String) : LinkCheck
}

/**
 * Outbound-link gate (D3 §10; security doc "Outbound URI launching"). Runs at save time and again
 * at launch time, so a URI that arrives through a backup gets the same treatment as one typed in.
 */
object LinkLaunchPolicy {
    val ALLOWED_SCHEMES: Set<String> = setOf("joplin", "obsidian", "logseq", "http", "https")
    val BLOCKED_SCHEMES: Set<String> = setOf("javascript", "file", "content", "intent", "android-app", "tel", "sms", "mailto")

    private val uriToken = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://[^\s<>"']+""")
    private val schemePrefix = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*):""")
    private const val TRAILING_PUNCTUATION = ".,;:)]}>'\""

    /** The first `scheme://…` token in shared text; the surrounding title/prose is never stored. */
    fun extractUri(sharedText: String?): String? {
        val text = sharedText ?: return null
        return uriToken.find(text)?.value?.trimEnd { it in TRAILING_PUNCTUATION }?.takeIf { it.isNotEmpty() }
    }

    fun schemeOf(uri: String): String? = schemePrefix.find(uri)?.groupValues?.get(1)?.lowercase()

    fun kindOf(scheme: String): LinkKind = when (scheme.lowercase()) {
        "joplin" -> LinkKind.JOPLIN
        "obsidian" -> LinkKind.OBSIDIAN
        "logseq" -> LinkKind.LOGSEQ
        "http", "https" -> LinkKind.WEB
        else -> LinkKind.OTHER
    }

    fun check(uri: String): LinkCheck {
        val candidate = uri.trim()
        if (candidate.any { it.isWhitespace() || it.isISOControl() }) {
            return LinkCheck.Rejected("URI contains whitespace or control characters")
        }
        val scheme = schemeOf(candidate) ?: return LinkCheck.Rejected("not a URI: no scheme")
        if (scheme in BLOCKED_SCHEMES) return LinkCheck.Rejected("scheme '$scheme' is never launched")
        return if (scheme in ALLOWED_SCHEMES) LinkCheck.Accepted(kindOf(scheme), candidate)
        else LinkCheck.NeedsConfirmation(scheme, candidate)
    }
}

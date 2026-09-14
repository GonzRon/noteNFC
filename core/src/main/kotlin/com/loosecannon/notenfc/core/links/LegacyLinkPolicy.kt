package com.loosecannon.notenfc.core.links

/**
 * The 2024 share gate, preserved verbatim: any shared text containing "joplin" (case-sensitive)
 * is accepted as the link, unchanged. Phase 1 replaces this with LinkLaunchPolicy (D3 §10).
 */
object LegacyLinkPolicy {
    fun accept(sharedText: String?): String? =
        if (sharedText != null && sharedText.contains("joplin")) sharedText else null
}

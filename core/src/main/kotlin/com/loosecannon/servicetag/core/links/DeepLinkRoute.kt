package com.loosecannon.servicetag.core.links

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.nfc.TagRoute

/** The `servicetag://` contract (D3 §13): navigation only, validated by shape here and by existence in the UI. */
sealed interface DeepLink {
    data class Asset(val id: AssetId) : DeepLink
    data class Link(val id: LinkId) : DeepLink
    data class Tag(val payload: TagPayload) : DeepLink
    data class Malformed(val reason: String) : DeepLink
}

object DeepLinkRoute {
    const val SCHEME = "servicetag"
    private val canonicalUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    fun parse(scheme: String?, host: String?, pathSegments: List<String>): DeepLink? {
        if (scheme != SCHEME) return null
        return when (host) {
            TagRoute.HOST -> TagRoute.parse(scheme, host, pathSegments)?.let { p ->
                if (p is TagPayload.Malformed) DeepLink.Malformed(p.reason) else DeepLink.Tag(p)
            }
            "asset" -> single(pathSegments)?.let { DeepLink.Asset(AssetId(it)) } ?: DeepLink.Malformed("servicetag://asset needs one tag id segment")
            "link" -> single(pathSegments)?.let { DeepLink.Link(LinkId(it)) } ?: DeepLink.Malformed("servicetag://link needs one id segment")
            else -> null
        }
    }

    private fun single(segments: List<String>): String? = segments.singleOrNull()?.takeIf { canonicalUuid.matches(it) }
}

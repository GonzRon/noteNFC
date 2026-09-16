package com.loosecannon.servicetag.core.nfc

import com.loosecannon.servicetag.core.model.TagId

/**
 * `notenfc://tag/<uuid>` — resolves exactly as if the tag had been scanned (deep-link contract).
 * Navigation only; the id is validated by shape here and by existence in `ResolveTag`.
 */
object TagRoute {
    const val SCHEME: String = "notenfc"
    const val HOST: String = "tag"

    private val canonicalUuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    /** Null when the URI is not this route at all; `Malformed` when it is but the id is unusable. */
    fun parse(scheme: String?, host: String?, pathSegments: List<String>): TagPayload? {
        if (scheme != SCHEME || host != HOST) return null
        val id = pathSegments.singleOrNull()
            ?: return TagPayload.Malformed("notenfc://tag needs exactly one path segment, got ${pathSegments.size}")
        return if (canonicalUuid.matches(id)) TagPayload.V1(TagId(id)) else TagPayload.Malformed("not a tag id: '$id'")
    }
}

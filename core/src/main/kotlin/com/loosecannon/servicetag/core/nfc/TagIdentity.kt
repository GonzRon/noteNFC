package com.loosecannon.servicetag.core.nfc

/**
 * What identifies this product's tags on the wire. These strings are the only product knowledge
 * the codec holds, and the caller supplies them.
 *
 * Phase F moves this type verbatim into `nfc-tag-core`'s `com.loosecannon.nfc.tagcore` (target
 * §4.2); nothing here may grow a ServiceTag-specific member in the meantime.
 *
 * @param externalDomain NFC Forum external-type domain. Lower-case: `NdefRecord.createExternal`
 *   lower-cases both halves before joining, so a mixed-case domain would not match the bytes
 *   actually on the tag (arch §2.9).
 * @param typeName external-type name, e.g. "tag".
 * @param aarPackage the applicationId to pin with an Application Record, or **null for no AAR**.
 *   A separate parameter from [externalDomain] even when the two strings are equal, because they
 *   are different things: an NFC Forum domain and an Android package name (C9, arch §6.3).
 *   Example: `TagIdentity("com.example.app", "tag", "com.example.app")`.
 */
data class TagIdentity(
    val externalDomain: String,
    val typeName: String,
    val aarPackage: String? = null,
) {
    val externalType: String = "$externalDomain:$typeName"

    init {
        require(externalDomain.isNotBlank() && typeName.isNotBlank()) {
            "an external type needs a domain and a name: '$externalDomain':'$typeName'"
        }
        require(externalType == externalType.lowercase()) {
            "an external type is lower-case on the wire: '$externalType'"
        }
    }
}

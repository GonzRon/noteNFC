package com.loosecannon.notenfc.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import com.loosecannon.notenfc.core.nfc.NdefRecordData

/** The only place Android NDEF types meet the pure-bytes codec (D3 §9). */

fun NdefMessage?.toRecordData(): List<NdefRecordData> =
    this?.records.orEmpty().map { NdefRecordData(it.tnf.toInt(), it.type, it.payload) }

fun List<NdefRecordData>.toNdefMessage(): NdefMessage {
    require(isNotEmpty()) { "an NDEF message needs at least one record" }
    return NdefMessage(map { NdefRecord(it.tnf.toShort(), it.type, ByteArray(0), it.payload) }.toTypedArray())
}

fun ByteArray?.toHexOrNull(): String? =
    this?.takeIf { it.isNotEmpty() }?.joinToString("") { "%02x".format(it) }

/** Records of the first message in `EXTRA_NDEF_MESSAGES`; null when the extra is absent. */
fun Intent.ndefRecords(): List<NdefRecordData>? {
    val raw = (
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }
        ) ?: return null
    return (raw.firstOrNull() as? NdefMessage).toRecordData()
}

fun Intent.nfcTag(): Tag? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }

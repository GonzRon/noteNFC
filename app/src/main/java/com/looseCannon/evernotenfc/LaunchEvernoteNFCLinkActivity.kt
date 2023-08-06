package com.looseCannon.evernotenfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.widget.Toast
import java.net.URL

class LaunchEvernoteNFCLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.also { rawMessages ->
                val messages: List<NdefMessage> = rawMessages.map { it as NdefMessage }
                val customData = String(messages[0].records[0].payload)
                val noteGuid = lookupEvernoteUrl(customData)

                if (noteGuid != null) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(noteGuid))
                    startActivity(browserIntent)

                } else {
                    Toast.makeText(this, "Evernote URL not found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        finish()
    }

    private fun lookupEvernoteUrl(customData: String): String? {
        val sharedPreferences = getSharedPreferences("EvernoteURLs", Context.MODE_PRIVATE)
        return sharedPreferences.getString(customData, null)
    }
}

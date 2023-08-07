package com.looseCannon.evernotenfc

import android.app.Activity
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.content.Intent
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.io.IOException

class NFCHandlerActivity : Activity() {
        private var nfcAdapter: NfcAdapter? = null
        private var pendingIntent: PendingIntent? = null

        override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.write_nfc_link) // Adjust this to your layout

                if (isNFCIntent(intent)) {
                        handleNFCIntent(intent)
                }

                // Extract the Evernote link from the shared variable
                val uniqueId = intent?.getStringExtra("uniqueId")
                Log.d("onCreate", "uniqueId = $uniqueId")

                nfcAdapter = NfcAdapter.getDefaultAdapter(this)
                if (nfcAdapter == null) {
                        Toast.makeText(this, "NFC is not available", Toast.LENGTH_LONG).show()
                        finish()
                        return
                }

                val intentForPendingIntent = Intent(this, javaClass).apply {
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("uniqueId", uniqueId)
                }
                pendingIntent = PendingIntent.getActivity(this, 0, intentForPendingIntent, FLAG_MUTABLE)
        }

        private fun isNFCIntent(intent: Intent?): Boolean {
                Log.d("isNFCIntent", "intent?.action = ${intent?.action}")
                return intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
                        intent?.action == NfcAdapter.ACTION_TECH_DISCOVERED ||
                        intent?.action == NfcAdapter.ACTION_TAG_DISCOVERED
        }

        override fun onResume() {
                super.onResume()
                Log.d("onResume", "onResume called")
                nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
        }

        override fun onPause() {
                super.onPause()
                Log.d("onPause", "onPause called")
                nfcAdapter?.disableForegroundDispatch(this)
        }

        override fun onNewIntent(intent: Intent?) {
                super.onNewIntent(intent)
                Log.d("onNewIntent", "intent = $intent")
                Log.d("IntentDebug", "Intent Action: ${intent?.action}")
                Log.d("IntentDebug", "Intent Categories: ${intent?.categories}")
                Log.d("IntentDebug", "Intent Data: ${intent?.data}")
                Log.d("IntentDebug", "Intent Extras: ${intent?.extras}")
                val extras = intent?.extras
                if (extras != null) {
                        for (key in extras.keySet()) {
                                val value = extras.get(key)
                                Log.d("IntentDebug", "Key: $key Value: $value")
                        }
                }

                if (isNFCIntent(intent)) {
                        handleNFCIntent(intent)
                }
        }

        private fun handleNFCIntent(intent: Intent?) {
                // Write the link to the NFC tag
                // get the evernoteLink from the intent extras
                val uniqueId = intent?.getStringExtra("uniqueId")
                Log.d("handleNFCIntent", "uniqueId = $uniqueId")
                if (uniqueId != null) {
                        if (writeLinkToTag(uniqueId, intent)) {
                                Toast.makeText(this, "Link written to NFC tag", Toast.LENGTH_LONG)
                                        .show()
                        } else {
                                Toast.makeText(
                                        this,
                                        "Failed to write link to NFC tag",
                                        Toast.LENGTH_LONG
                                ).show()
                        }
                } else {
                        Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
                }
                finish()
        }

        private fun writeLinkToTag(link: String, intent: Intent?): Boolean {
                Log.d("writeLinkToTag", "writeLinkToTag called")
                val tag: Tag? = intent?.extras?.getParcelable(NfcAdapter.EXTRA_TAG)
                // val ndefRecord = NdefRecord.createUri(link)
                val payload = link.toByteArray(Charsets.UTF_8)
                val domain ="com.loosecannon.evernotenfc" // Replace with your app's domain/package name.
                val type = "uuid8_link" // Replace with your specific type name.
                val ndefRecord =  NdefRecord.createExternal(domain, type, payload)
                val ndefMessage = NdefMessage(arrayOf(ndefRecord))

                try {
                        val ndef = Ndef.get(tag)
                        ndef?.let {
                                it.connect()
                                if (it.isWritable) {
                                        it.writeNdefMessage(ndefMessage)
                                        it.close()
                                        return true
                                }
                        }

                        val ndefFormatable = NdefFormatable.get(tag)
                        ndefFormatable?.let {
                                try {
                                        it.connect()
                                        it.format(ndefMessage)
                                        it.close()
                                        return true
                                } catch (e: IOException) {
                                        return false
                                } catch (e: FormatException) {
                                        return false
                                }
                        }
                } catch (e: Exception) {
                        // Handle all other exceptions
                        return false
                }
                return false
        }
}

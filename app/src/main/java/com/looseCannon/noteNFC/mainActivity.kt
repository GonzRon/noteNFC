package com.looseCannon.noteNFC

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.loosecannon.notenfc.core.links.LegacyLinkPolicy
import com.loosecannon.notenfc.core.nfc.LegacyKey

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sharedPreferences = getSharedPreferences("noteNFCURLs", Context.MODE_PRIVATE)

        val noteLink = LegacyLinkPolicy.accept(intent?.getStringExtra(Intent.EXTRA_TEXT))
        if (noteLink == null) {
            Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
            return
        }

        val uniqueId = LegacyKey.compute(noteLink)
        sharedPreferences.edit().putString(uniqueId, noteLink).apply()

        val nfcIntent = Intent(this, NFCHandlerActivity::class.java)
        nfcIntent.putExtra("uniqueId", uniqueId)
        startActivity(nfcIntent)
        finish()
    }
}

package com.looseCannon.noteNFC

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.security.MessageDigest

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val sharedPreferences = getSharedPreferences("noteNFCURLs", Context.MODE_PRIVATE)

        // Extracting the link from the intent
        val noteLink = intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { transformLink(it) }

        // Check if the link is present
        if (noteLink == null) {
            Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
            return
        }

        val uniqueId = getShortHash(noteLink)
        sharedPreferences.edit().putString(uniqueId, noteLink).apply()

        val nfcIntent = Intent(this, NFCHandlerActivity::class.java)
        nfcIntent.putExtra("uniqueId", uniqueId)
        startActivity(nfcIntent)
        finish()
    }

    private fun getShortHash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }.substring(0, 8)
    }

    private fun transformLink(link: String): String? {
        if (link.contains("joplin")) {
            return link
        }
        return null
    }
}

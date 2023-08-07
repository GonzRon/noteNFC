package com.looseCannon.evernotenfc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Extracting the link from the intent
        val evernoteLink = intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { transformLink(it) }

        // Check if the link is present
        if (evernoteLink == null) {
            Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
            return
        }
        val uniqueId = UUID.randomUUID().toString().substring(0, 8)
        val sharedPreferences = getSharedPreferences("EvernoteURLs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(uniqueId, evernoteLink).apply()

        val nfcIntent = Intent(this, NFCHandlerActivity::class.java)
        nfcIntent.putExtra("uniqueId", uniqueId)
        startActivity(nfcIntent)
        finish()
    }

    fun transformLink(link: String): String? {
        // If the link contains /nl/
        if (link.contains("/nl/")) {
            val pattern = """.*/shard/(\w+)/nl/(\w+[_-].*)/(\w+[-_].*)""".toRegex()
            val match = pattern.find(link)
            match?.let {
                val (shardId, userId, noteGuid) = it.destructured
                return noteGuid
            }
        }
        else if (link.contains("/sh/")) {
            val pattern = """.*/shard/(\w+)/sh/(\w+[_-].*)/(\w+[-_].*)""".toRegex()
            val match = pattern.find(link)
            match?.let {
                val (shardId, noteGuid, shareKey) = it.destructured
                return "evernote://share-note-via-link/$noteGuid/$shareKey/$shardId/"
            }
        }
        return null
    }
}

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
//        evernoteLink = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val evernoteLink = intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { transformLink(it) }

        // Check if the link is present
        if (evernoteLink == null) {
            Toast.makeText(this, "No link received", Toast.LENGTH_LONG).show()
            return
        }
        val uniqueId = UUID.randomUUID().toString().substring(0, 8) // This will give you the first 8 characters of a UUID.
        val sharedPreferences = getSharedPreferences("EvernoteURLs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(uniqueId, evernoteLink).apply()

        val nfcIntent = Intent(this, NFCHandlerActivity::class.java)
        nfcIntent.putExtra("uniqueId", uniqueId)
        startActivity(nfcIntent)
    }
    fun transformLink(link: String): String? {
        // If the link contains /nl/
        if (link.contains("/nl/")) {
            val pattern = """.*/shard/(\w+)/nl/(\w+[_-].*)/(\w+[-_].*)""".toRegex()
            val match = pattern.find(link)

            match?.let {
                val (shardId, userId, noteGuid) = it.destructured
                return noteGuid
//                return "evernote://view/$userId/$shardId/$noteGuid/$noteGuid/"
            }
        }
        // If the link contains /sh/
        else if (link.contains("/sh/")) {
            val pattern = """.*/shard/(\w+)/sh/(\w+[_-].*)/(\w+[-_].*)""".toRegex()
            val match = pattern.find(link)

            match?.let {
                val (shardId, noteGuid, shareKey) = it.destructured
//                return noteGuid
                return "evernote://share-note-via-link/$noteGuid/$shareKey/$shardId/"
            }
        }

        return null
    }

    // Other potential functions or lifecycle methods related to this activity, if needed.
}

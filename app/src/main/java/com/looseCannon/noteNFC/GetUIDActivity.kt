package com.looseCannon.noteNFC

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class GetUIDActivity : Activity() {

    private lateinit var userIdEditText: EditText
    private lateinit var confirmButton: Button

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("noteNFCPrefs", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_get_uid)

        if (sharedPreferences.contains("EvernoteUserID")) {
            finish()
        }

        userIdEditText = findViewById(R.id.userIdEditText)
        confirmButton = findViewById(R.id.confirmButton)

        confirmButton.setOnClickListener {
            val userId = userIdEditText.text.toString()
            if (userId.isNotEmpty()) {
                sharedPreferences.edit().putString("EvernoteUserID", userId).apply()
                Toast.makeText(this, "User ID saved!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Please enter your User ID.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

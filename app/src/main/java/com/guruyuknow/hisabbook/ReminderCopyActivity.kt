package com.guruyuknow.hisabbook

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast

class ReminderCopyActivity : Activity() {
    companion object { const val EXTRA_TEXT = "extra_text" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("Loan Reminder", text))
        Toast.makeText(this, "Reminder copied to clipboard", Toast.LENGTH_SHORT).show()
        finish() // immediately close
    }
}

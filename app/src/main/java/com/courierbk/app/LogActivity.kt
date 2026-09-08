package com.courierbk.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LogActivity : AppCompatActivity() {

    companion object {
        private const val BLOCK_SIZE = 10000
    }

    private lateinit var logTextView: TextView
    private lateinit var blockInfoTextView: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    private var fullLog = ""
    private var currentBlock = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        logTextView = findViewById(R.id.logTextView)
        blockInfoTextView = findViewById(R.id.blockInfoTextView)
        previousButton = findViewById(R.id.previousLogBlockButton)
        nextButton = findViewById(R.id.nextLogBlockButton)

        findViewById<Button>(R.id.copyLogButton).setOnClickListener { copyCurrentBlock() }
        findViewById<Button>(R.id.copyAllLogButton).setOnClickListener { copyAllLog() }
        findViewById<Button>(R.id.clearLogButton).setOnClickListener { clearLog() }
        findViewById<Button>(R.id.refreshLogButton).setOnClickListener { refreshLog() }

        previousButton.setOnClickListener {
            if (currentBlock > 0) {
                currentBlock--
                showCurrentBlock()
            }
        }

        nextButton.setOnClickListener {
            val count = blockCount()
            if (currentBlock + 1 < count) {
                currentBlock++
                showCurrentBlock()
            }
        }

        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {
        try {
            fullLog = LogUtil.getAll()
            currentBlock = 0
            showCurrentBlock()
        } catch (e: Exception) {
            fullLog = "Ошибка загрузки журнала:\n${e.javaClass.name}\n${e.message ?: ""}"
            currentBlock = 0
            showCurrentBlock()
        }
    }

    private fun blockCount(): Int {
        if (fullLog.isEmpty()) return 1
        return (fullLog.length + BLOCK_SIZE - 1) / BLOCK_SIZE
    }

    private fun showCurrentBlock() {
        if (fullLog.isEmpty()) {
            logTextView.text = "Журнал пока пуст."
            blockInfoTextView.text = "Блок 0 / 0"
            previousButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }

        val count = blockCount()
        if (currentBlock >= count) currentBlock = count - 1
        if (currentBlock < 0) currentBlock = 0

        val start = currentBlock * BLOCK_SIZE
        val end = minOf(fullLog.length, start + BLOCK_SIZE)
        logTextView.text = fullLog.substring(start, end)
        blockInfoTextView.text = "Блок ${currentBlock + 1} / $count  •  символов: ${fullLog.length}"
        previousButton.isEnabled = currentBlock > 0
        nextButton.isEnabled = currentBlock + 1 < count
    }

    private fun currentBlockText(): String {
        if (fullLog.isEmpty()) return ""
        val start = currentBlock * BLOCK_SIZE
        val end = minOf(fullLog.length, start + BLOCK_SIZE)
        return fullLog.substring(start, end)
    }

    private fun copyCurrentBlock() {
        copyText(currentBlockText(), "Блок журнала ${currentBlock + 1}", "Блок скопирован")
    }

    private fun copyAllLog() {
        copyText(fullLog, "Courier BK журнал", "Весь журнал скопирован")
    }

    private fun copyText(text: String, label: String, successMessage: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка копирования: ${e.message ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun clearLog() {
        LogUtil.clear()
        fullLog = ""
        currentBlock = 0
        showCurrentBlock()
        Toast.makeText(this, "Журнал очищен", Toast.LENGTH_SHORT).show()
    }
}

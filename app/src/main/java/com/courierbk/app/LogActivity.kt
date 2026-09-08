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

    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_log)

        logTextView = findViewById(R.id.logTextView)

        val copyButton =
            findViewById<Button>(R.id.copyLogButton)

        val clearButton =
            findViewById<Button>(R.id.clearLogButton)

        val refreshButton =
            findViewById<Button>(R.id.refreshLogButton)

        refreshLog()

        copyButton.setOnClickListener {
            copyLog()
        }

        clearButton.setOnClickListener {

            LogUtil.clear()

            refreshLog()

            Toast.makeText(
                this,
                "Журнал очищен",
                Toast.LENGTH_SHORT
            ).show()
        }

        refreshButton.setOnClickListener {

            refreshLog()

            Toast.makeText(
                this,
                "Журнал обновлён",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLog()
    }

    private fun refreshLog() {

        try {

            val text =
                LogUtil.getAll()

            if (text.isBlank()) {

                logTextView.text =
                    "Журнал пока пуст."

            } else {

                logTextView.text =
                    text
            }

        } catch (e: Exception) {

            logTextView.text =
                "Ошибка загрузки журнала:\n" +
                        e.javaClass.name +
                        "\n" +
                        (e.message ?: "")
        }
    }

    private fun copyLog() {

        try {

            val text =
                LogUtil.getAll()

            val clipboard =
                getSystemService(
                    Context.CLIPBOARD_SERVICE
                ) as ClipboardManager

            val clip =
                ClipData.newPlainText(
                    "Courier BK журнал",
                    text
                )

            clipboard.setPrimaryClip(clip)

            Toast.makeText(
                this,
                "Журнал скопирован",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Ошибка копирования: " +
                        (e.message
                            ?: "неизвестная ошибка"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
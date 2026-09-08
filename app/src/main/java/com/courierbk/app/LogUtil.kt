package com.courierbk.app

import android.content.Context
import android.util.Log

object LogUtil {

    private const val PREFS_NAME = "courier_bk_logs"
    private const val KEY_LOG_TEXT = "log_text"
    private const val MAX_LOG_LENGTH = 250000

    private lateinit var appContext: Context

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return

        appContext = context.applicationContext
        initialized = true

        info("APP", "LogUtil инициализирован")

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            try {
                error(
                    "CRASH",
                    "Необработанная ошибка в потоке: ${thread.name}\n" +
                            throwableToString(throwable)
                )
            } catch (_: Exception) {
                // Ничего не делаем, чтобы журнал не вызвал дополнительный crash.
            }

            try {
                previousHandler?.uncaughtException(thread, throwable)
            } catch (_: Exception) {
                // Игнорируем ошибку системного обработчика.
            }
        }
    }

    fun info(tag: String, message: String) {
        write("INFO", tag, message)
    }

    fun warning(tag: String, message: String) {
        write("WARNING", tag, message)
    }

    fun error(tag: String, message: String) {
        write("ERROR", tag, message)
    }

    fun getAll(): String {
        if (!initialized) {
            return "LogUtil ещё не инициализирован."
        }

        return try {
            val prefs = appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            prefs.getString(KEY_LOG_TEXT, "") ?: ""
        } catch (e: Exception) {
            "Ошибка чтения журнала:\n${throwableToString(e)}"
        }
    }

    fun clear() {
        if (!initialized) return

        try {
            appContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LOG_TEXT)
                .apply()

            Log.i("CourierBK", "Журнал очищен")
        } catch (e: Exception) {
            Log.e("CourierBK", "Ошибка очистки журнала", e)
        }
    }

    private fun write(
        level: String,
        tag: String,
        message: String
    ) {
        val safeMessage = message.ifEmpty {
            "(пустое сообщение)"
        }

        when (level) {
            "INFO" -> Log.i(tag, safeMessage)
            "WARNING" -> Log.w(tag, safeMessage)
            "ERROR" -> Log.e(tag, safeMessage)
            else -> Log.d(tag, safeMessage)
        }

        if (!initialized) return

        try {
            val prefs = appContext.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            val oldText = prefs.getString(KEY_LOG_TEXT, "") ?: ""

            val time = java.text.SimpleDateFormat(
                "dd.MM.yyyy HH:mm:ss.SSS",
                java.util.Locale.getDefault()
            ).format(java.util.Date())

            val entry =
                "[$time] [$level] [$tag]\n$safeMessage\n\n"

            var newText = oldText + entry

            if (newText.length > MAX_LOG_LENGTH) {
                newText = newText.takeLast(MAX_LOG_LENGTH)
            }

            prefs.edit()
                .putString(KEY_LOG_TEXT, newText)
                .apply()

        } catch (e: Exception) {
            Log.e("CourierBK", "Ошибка записи журнала", e)
        }
    }

    private fun throwableToString(throwable: Throwable?): String {
        if (throwable == null) {
            return "Throwable = null"
        }

        val builder = StringBuilder()

        builder.append(throwable.javaClass.name)
        builder.append(": ")
        builder.append(throwable.message ?: "")
        builder.append("\n")

        val stack = throwable.stackTrace

        for (element in stack) {
            builder.append("\tat ")
            builder.append(element.toString())
            builder.append("\n")
        }

        var cause = throwable.cause

        if (cause != null) {
            builder.append("\nCaused by:\n")

            var depth = 0

            while (cause != null && depth < 5) {

                builder.append(cause.javaClass.name)
                builder.append(": ")
                builder.append(cause.message ?: "")
                builder.append("\n")

                for (element in cause.stackTrace) {
                    builder.append("\tat ")
                    builder.append(element.toString())
                    builder.append("\n")
                }

                cause = cause.cause
                depth++
            }
        }

        return builder.toString()
    }
}
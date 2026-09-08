package com.courierbk.app

import android.content.Context
import android.net.Uri
import java.io.File

object UriHelper {

    fun fileUri(
        context: Context,
        file: File
    ): String {

        /*
         * Для временного файла в cache
         * используем file://.
         *
         * OcrActivity получает URI и читает
         * его через ContentResolver.
         */
        return Uri.fromFile(file).toString()
    }
}
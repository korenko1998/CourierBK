package com.courierbk.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class OcrActivity : AppCompatActivity() {

    private lateinit var orderNumberEditText: EditText
    private lateinit var customerNameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var addressEditText: EditText
    private lateinit var totalEditText: EditText
    private lateinit var paymentEditText: EditText
    private lateinit var changeEditText: EditText
    private lateinit var deliveryDateEditText: EditText
    private lateinit var certificateEditText: EditText
    private lateinit var itemsEditText: EditText
    private lateinit var drinksWarningText: TextView
    private lateinit var continueOcrButton: Button

    private var recognizedText = ""
    private var recognizedCashGiven = ""
    private var recognizedDrinks = ""
    private var receiptImagePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_ocr)

        orderNumberEditText =
            findViewById(R.id.orderNumberEditText)

        customerNameEditText =
            findViewById(R.id.customerNameEditText)

        phoneEditText =
            findViewById(R.id.phoneEditText)

        addressEditText =
            findViewById(R.id.addressEditText)

        totalEditText =
            findViewById(R.id.totalEditText)

        paymentEditText =
            findViewById(R.id.paymentEditText)

        changeEditText =
            findViewById(R.id.changeEditText)

        deliveryDateEditText =
            findViewById(R.id.deliveryDateEditText)

        certificateEditText =
            findViewById(R.id.certificateEditText)

        itemsEditText =
            findViewById(R.id.itemsEditText)

        drinksWarningText =
            findViewById(R.id.drinksWarningText)

        continueOcrButton =
            findViewById(R.id.continueOcrButton)

        continueOcrButton.setOnClickListener {
            saveAndContinue()
        }

        val directOcrText =
            intent.getStringExtra("ocr_text") ?: ""

        val imagePath =
            intent.getStringExtra("receipt_image_path")

        val imageUri =
            intent.getStringExtra("receipt_image_uri")

        receiptImagePath =
            imagePath ?: ""

        LogUtil.info(
            "OCR_ACTIVITY",
            "OcrActivity запущен. " +
                    "ocr_text символов: ${directOcrText.length}, " +
                    "imagePath: $imagePath, " +
                    "imageUri: $imageUri"
        )

        when {
            directOcrText.trim().isNotEmpty() -> {

                recognizedText =
                    prepareCleanOcrText(
                        directOcrText
                    )

                parseOcr(
                    recognizedText
                )
            }

            !imagePath.isNullOrEmpty() -> {

                runOcrFromFile(
                    imagePath
                )
            }

            !imageUri.isNullOrEmpty() -> {

                runOcrFromUri(
                    imageUri
                )
            }

            else -> {

                LogUtil.warning(
                    "OCR_ACTIVITY",
                    "Не получены данные для OCR"
                )

                showEmptyOcr()
            }
        }
    }

    private fun runOcrFromFile(
        path: String
    ) {
        continueOcrButton.isEnabled = false

        Toast.makeText(
            this,
            "Распознавание чека...",
            Toast.LENGTH_SHORT
        ).show()

        LogUtil.info(
            "OCR_ACTIVITY",
            "Получен путь к фотографии чека: $path"
        )

        Thread {
            try {

                val bitmap =
                    BitmapFactory.decodeFile(path)

                if (bitmap == null) {
                    throw IllegalStateException(
                        "Не удалось загрузить изображение"
                    )
                }

                receiptImagePath = path

                LogUtil.info(
                    "OCR_ACTIVITY",
                    "Изображение загружено: " +
                            "${bitmap.width}x${bitmap.height}"
                )

                runPaddleOcr(bitmap)

            } catch (e: Exception) {

                LogUtil.error(
                    "OCR_ACTIVITY",
                    "Ошибка загрузки изображения:\n" +
                            throwableToString(e)
                )

                runOnUiThread {

                    continueOcrButton.isEnabled = true

                    Toast.makeText(
                        this@OcrActivity,
                        "Ошибка загрузки изображения: " +
                                "${e.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    showEmptyOcr()
                }
            }
        }.start()
    }

    private fun runOcrFromUri(
        uriString: String
    ) {
        continueOcrButton.isEnabled = false

        Toast.makeText(
            this,
            "Распознавание чека...",
            Toast.LENGTH_SHORT
        ).show()

        LogUtil.info(
            "OCR_ACTIVITY",
            "Получен URI фотографии: $uriString"
        )

        Thread {
            try {

                val uri =
                    Uri.parse(uriString)

                val inputStream =
                    contentResolver.openInputStream(uri)

                if (inputStream == null) {
                    throw IllegalStateException(
                        "Не удалось открыть изображение"
                    )
                }

                val bitmap =
                    try {
                        BitmapFactory.decodeStream(
                            inputStream
                        )
                    } finally {
                        try {
                            inputStream.close()
                        } catch (_: Exception) {
                        }
                    }

                if (bitmap == null) {
                    throw IllegalStateException(
                        "Не удалось декодировать изображение"
                    )
                }

                LogUtil.info(
                    "OCR_ACTIVITY",
                    "Изображение из URI загружено: " +
                            "${bitmap.width}x${bitmap.height}"
                )

                runPaddleOcr(bitmap)

            } catch (e: Exception) {

                LogUtil.error(
                    "OCR_ACTIVITY",
                    "Ошибка загрузки URI:\n" +
                            throwableToString(e)
                )

                runOnUiThread {

                    continueOcrButton.isEnabled = true

                    Toast.makeText(
                        this@OcrActivity,
                        "Ошибка открытия изображения: " +
                                "${e.message}",
                        Toast.LENGTH_LONG
                    ).show()

                    showEmptyOcr()
                }
            }
        }.start()
    }

    private fun runPaddleOcr(
        bitmap: android.graphics.Bitmap
    ) {
        try {

            LogUtil.info(
                "OCR_ACTIVITY",
                "Запуск PaddleOCR"
            )

            val engine =
                PaddleOcrEngine(
                    this@OcrActivity
                )

            /*
             * НЕ вызываем engine.close().
             * В текущем PaddleOcrEngine такого метода нет.
             */

            val rawText =
                engine.recognize(bitmap)

            val cleanText =
                prepareCleanOcrText(
                    rawText
                )

            recognizedText =
                cleanText

            LogUtil.info(
                "OCR_ACTIVITY",
                "OCR завершён. " +
                        "Символов чистого результата: " +
                        "${cleanText.length}"
            )

            runOnUiThread {

                continueOcrButton.isEnabled = true

                if (
                    cleanText.trim().isEmpty()
                ) {
                    showEmptyOcr()
                } else {
                    parseOcr(cleanText)
                }
            }

        } catch (e: Exception) {

            LogUtil.error(
                "OCR_ACTIVITY",
                "Ошибка PaddleOCR:\n" +
                        throwableToString(e)
            )

            runOnUiThread {

                continueOcrButton.isEnabled = true

                Toast.makeText(
                    this@OcrActivity,
                    "Ошибка распознавания: " +
                            "${e.message}",
                    Toast.LENGTH_LONG
                ).show()

                showEmptyOcr()
            }
        }
    }

    private fun prepareCleanOcrText(
        source: String
    ): String {

        if (
            source.trim().isEmpty()
        ) {
            return ""
        }

        var text =
            source.replace(
                "\r",
                "\n"
            )

        val marker =
            text.indexOf(
                "ИТОГ OCR:",
                ignoreCase = true
            )

        if (marker >= 0) {
            text =
                text.substring(
                    marker +
                            "ИТОГ OCR:".length
                )
        }

        val lines =
            text
                .split("\n")
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotEmpty()
                }

        return lines
            .filter {
                !isDiagnosticLine(it)
            }
            .joinToString("\n")
    }

    private fun isDiagnosticLine(
        line: String
    ): Boolean {

        val value =
            line
                .toLowerCase(
                    Locale.getDefault()
                )
                .trim()

        return value.startsWith("rec output") ||
                value.startsWith("rec #") ||
                value.startsWith("box #") ||
                value.startsWith("text #") ||
                value.startsWith("det input") ||
                value.startsWith("det output") ||
                value.startsWith("det probability") ||
                value.startsWith("det positive pixels") ||
                value.startsWith("det valid boxes") ||
                value.startsWith("det найдено") ||
                value.startsWith("ocr_activity:") ||
                value.startsWith("ocr_parse:") ||
                value.startsWith("paddleocr") ||
                value.startsWith("paddleocrengine") ||
                value.startsWith("логutil") ||
                value.startsWith("[info]") ||
                value.startsWith("[warning]") ||
                value.startsWith("[error]")
    }

    private fun parseOcr(
        text: String
    ) {
        try {

            val lines =
                text
                    .replace(
                        "\r",
                        "\n"
                    )
                    .split("\n")
                    .map {
                        it.trim()
                            .replace(
                                Regex("\\s+"),
                                " "
                            )
                    }
                    .filter {
                        it.isNotEmpty()
                    }

            LogUtil.info(
                "OCR_PARSE",
                "Получено чистых строк OCR: ${lines.size}"
            )

            val orderNumber =
                extractOrderNumber(lines)

            val customerName =
                extractCustomerName(lines)

            val phone =
                extractPhone(lines)

            val address =
                extractAddress(lines)

            val total =
                extractTotal(lines)

            val payment =
                extractPayment(lines)

            val cashGiven =
                extractCashGiven(lines)

            recognizedCashGiven =
                cashGiven

            val change =
                extractChange(
                    lines,
                    total,
                    cashGiven
                )

            val deliveryDate =
                extractDeliveryDate(lines)

            val certificate =
                extractCertificate(lines)

            val items =
                extractItems(lines)

            val drinks =
                extractDrinks(lines)

            recognizedDrinks =
                drinks

            orderNumberEditText.setText(
                orderNumber
            )

            customerNameEditText.setText(
                customerName
            )

            phoneEditText.setText(
                phone
            )

            addressEditText.setText(
                address
            )

            totalEditText.setText(
                total
            )

            paymentEditText.setText(
                payment
            )

            changeEditText.setText(
                change
            )

            deliveryDateEditText.setText(
                deliveryDate
            )

            certificateEditText.setText(
                certificate
            )

            itemsEditText.setText(
                items
            )

            if (
                drinks.isNotEmpty()
            ) {
                drinksWarningText.text =
                    "⚠ ПРОВЕРЬТЕ НАПИТКИ\n$drinks"
            } else {
                drinksWarningText.text =
                    "⚠ ПРОВЕРЬТЕ НАПИТКИ"
            }

            LogUtil.info(
                "OCR_PARSE",
                "Разбор завершён.\n" +
                        "Номер='$orderNumber'\n" +
                        "клиент='$customerName'\n" +
                        "телефон='$phone'\n" +
                        "адрес='$address'\n" +
                        "сумма='$total'\n" +
                        "оплата='$payment'\n" +
                        "сдача='$change'\n" +
                        "сумма от клиента='$cashGiven'\n" +
                        "напитки='$drinks'"
            )

        } catch (e: Exception) {

            LogUtil.error(
                "OCR_PARSE",
                "Ошибка разбора:\n" +
                        throwableToString(e)
            )

            Toast.makeText(
                this,
                "Ошибка разбора результата OCR",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun extractOrderNumber(
        lines: List<String>
    ): String {

        val patterns =
            listOf(
                Regex(
                    "(?i)номер\\s*заказа\\s*[:№#-]*\\s*(\\d{4,12})"
                ),
                Regex(
                    "(?i)номерзаказа\\s*[:№#-]*\\s*(\\d{4,12})"
                ),
                Regex(
                    "(?i)номер\\s*заказ\\s*[:№#-]*\\s*(\\d{4,12})"
                )
            )

        for (line in lines) {
            for (pattern in patterns) {

                val match =
                    pattern.find(line)

                if (match != null) {
                    return match.groupValues[1]
                }
            }
        }

        for (line in lines) {

            val normalized =
                normalizeOcrText(line)

            if (
                normalized.contains(
                    "номерзаказа"
                )
            ) {

                val number =
                    Regex(
                        "\\d{4,12}"
                    ).find(line)

                if (number != null) {
                    return number.value
                }
            }
        }

        for (line in lines) {

            val match =
                Regex(
                    "(?i)[№#]\\s*(\\d{4,12})"
                ).find(line)

            if (match != null) {
                return match.groupValues[1]
            }
        }

        return ""
    }

    private fun extractCustomerName(
        lines: List<String>
    ): String {

        for (i in lines.indices) {

            val normalized =
                normalizeOcrText(
                    lines[i]
                )

            if (
                normalized.contains(
                    "клиент"
                )
            ) {

                val sameLine =
                    lines[i]
                        .replace(
                            Regex(
                                "(?i)клиент"
                            ),
                            ""
                        )
                        .replace(
                            ":",
                            ""
                        )
                        .trim()

                if (
                    looksLikePersonName(
                        sameLine
                    )
                ) {
                    return sameLine
                }

                if (
                    i + 1 < lines.size
                ) {

                    val next =
                        cleanCustomerCandidate(
                            lines[i + 1]
                        )

                    if (
                        looksLikePersonName(
                            next
                        )
                    ) {
                        return next
                    }
                }
            }
        }

        for (line in lines) {

            val candidate =
                cleanCustomerCandidate(
                    line
                )

            if (
                looksLikePersonName(
                    candidate
                )
            ) {
                return candidate
            }
        }

        return ""
    }

    private fun cleanCustomerCandidate(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "(?i)^(клиент|имя)\\s*[:\\-]*"
                ),
                ""
            )
            .trim()
    }

    private fun looksLikePersonName(
        value: String
    ): Boolean {

        if (value.length < 3) return false
        if (value.length > 60) return false

        if (
            Regex("\\d")
                .containsMatchIn(value)
        ) {
            return false
        }

        val normalized =
            normalizeOcrText(value)

        val forbidden =
            listOf(
                "доставка",
                "burgerking",
                "курьер",
                "заказ",
                "номер",
                "оплата",
                "сумма",
                "телефон",
                "адрес",
                "улица",
                "проспект",
                "переулок",
                "сдача",
                "сертификат",
                "блюдо",
                "колво"
            )

        for (word in forbidden) {
            if (
                normalized.contains(word)
            ) {
                return false
            }
        }

        return value.count {
            Character.isLetter(it)
        } >= 3
    }

    private fun extractPhone(
        lines: List<String>
    ): String {

        for (line in lines) {

            val digits =
                normalizePhoneDigits(line)

            if (
                digits.startsWith("375") &&
                digits.length >= 12
            ) {
                return formatBelarusPhone(
                    digits.substring(
                        0,
                        12
                    )
                )
            }
        }

        val allText =
            lines.joinToString(" ")

        val match =
            Regex(
                "(?:\\+?375\\s*)?(\\d{2})\\s*(\\d{3})\\s*(\\d{2})\\s*(\\d{2})"
            ).find(allText)

        if (match != null) {

            return "+375 " +
                    match.groupValues[1] +
                    " " +
                    match.groupValues[2] +
                    "-" +
                    match.groupValues[3] +
                    "-" +
                    match.groupValues[4]
        }

        return ""
    }

    private fun normalizePhoneDigits(
        value: String
    ): String {

        var digits =
            value.filter {
                it.isDigit()
            }

        if (
            digits.length == 11 &&
            digits.startsWith("8")
        ) {
            digits =
                "375" +
                        digits.substring(1)
        }

        return digits
    }

    private fun formatBelarusPhone(
        digits: String
    ): String {

        if (
            digits.length != 12 ||
            !digits.startsWith("375")
        ) {
            return ""
        }

        return "+375 " +
                digits.substring(3, 5) +
                " " +
                digits.substring(5, 8) +
                "-" +
                digits.substring(8, 10) +
                "-" +
                digits.substring(10, 12)
    }

    private fun extractAddress(
        lines: List<String>
    ): String {

        val candidates =
            mutableListOf<String>()

        for (line in lines) {

            val normalized =
                normalizeOcrText(line)

            if (
                looksLikeAddress(
                    line,
                    normalized
                )
            ) {

                val cleaned =
                    cleanAddress(line)

                if (
                    cleaned.isNotEmpty()
                ) {
                    candidates.add(cleaned)
                }
            }
        }

        if (
            candidates.isNotEmpty()
        ) {

            var longest =
                candidates[0]

            for (candidate in candidates) {
                if (
                    candidate.length >
                    longest.length
                ) {
                    longest = candidate
                }
            }

            return longest
        }

        return ""
    }

    private fun looksLikeAddress(
        line: String,
        normalized: String
    ): Boolean {

        if (line.length < 4) {
            return false
        }

        val words =
            listOf(
                "улица",
                "ул",
                "проспект",
                "просп",
                "пр-т",
                "прт",
                "переулок",
                "пер",
                "бульвар",
                "шоссе",
                "дом"
            )

        for (word in words) {
            if (
                normalized.contains(word)
            ) {
                return true
            }
        }

        return Regex(
            "(?i)(ул\\.?|просп\\.?|пр-т|пер\\.?|улица|проспект|переулок)\\s*[а-яa-zё0-9]"
        ).containsMatchIn(line)
    }

    private fun cleanAddress(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "(?i)^адрес\\s*[:\\-]*"
                ),
                ""
            )
            .trim()
    }

    private fun extractTotal(
        lines: List<String>
    ): String {

        for (i in lines.indices) {

            val normalized =
                normalizeOcrText(
                    lines[i]
                )

            if (
                normalized.contains(
                    "суммазаказа"
                ) ||
                normalized.contains(
                    "уммазаказа"
                )
            ) {

                val same =
                    extractMoneyFromText(
                        lines[i]
                    )

                if (
                    same.isNotEmpty()
                ) {
                    return same
                }

                if (
                    i + 1 < lines.size
                ) {

                    val next =
                        extractMoneyFromText(
                            lines[i + 1]
                        )

                    if (
                        next.isNotEmpty()
                    ) {
                        return next
                    }
                }
            }
        }

        for (
            i in (lines.size - 1) downTo 0
        ) {

            val normalized =
                normalizeOcrText(
                    lines[i]
                )

            if (
                normalized.contains(
                    "сумма"
                )
            ) {

                val money =
                    extractMoneyFromText(
                        lines[i]
                    )

                if (
                    money.isNotEmpty()
                ) {
                    return money
                }
            }
        }

        return ""
    }

    private fun extractPayment(
        lines: List<String>
    ): String {

        for (line in lines) {

            val normalized =
                normalizeOcrText(line)

            if (
                normalized.contains(
                    "способоплаты"
                ) ||
                normalized.contains(
                    "способплаты"
                )
            ) {
                return normalizePayment(line)
            }
        }

        return ""
    }

    private fun normalizePayment(
        value: String
    ): String {

        val normalized =
            value
                .toLowerCase(
                    Locale.getDefault()
                )
                .replace(
                    " ",
                    ""
                )

        if (
            normalized.contains("cash") ||
            normalized.contains("сасh") ||
            normalized.contains("сash") ||
            normalized.contains("casн") ||
            normalized.contains("налич")
        ) {
            return "Наличные"
        }

        if (
            normalized.contains("card") ||
            normalized.contains("карт") ||
            normalized.contains("visa") ||
            normalized.contains("master") ||
            normalized.contains("безнал")
        ) {
            return "Карта"
        }

        if (
            normalized.contains("online") ||
            normalized.contains("онлайн")
        ) {
            return "Онлайн"
        }

        return value
            .substringAfter(
                "оплаты",
                value
            )
            .trim()
    }

    private fun extractCashGiven(
        lines: List<String>
    ): String {

        for (i in lines.indices) {

            val normalized =
                normalizeOcrText(
                    lines[i]
                )

            if (
                normalized.contains("отклиента") ||
                normalized.contains("полученоотклиента") ||
                normalized.contains("внесено")
            ) {

                val money =
                    extractMoneyFromText(
                        lines[i]
                    )

                if (
                    money.isNotEmpty()
                ) {
                    return money
                }

                if (
                    i + 1 < lines.size
                ) {

                    val next =
                        extractMoneyFromText(
                            lines[i + 1]
                        )

                    if (
                        next.isNotEmpty()
                    ) {
                        return next
                    }
                }
            }
        }

        return ""
    }

    private fun extractChange(
        lines: List<String>,
        total: String,
        cashGiven: String
    ): String {

        for (i in lines.indices) {

            val normalized =
                normalizeOcrText(
                    lines[i]
                )

            if (
                normalized.contains("сдача") ||
                normalized.contains("сдачи")
            ) {

                val money =
                    extractMoneyFromText(
                        lines[i]
                    )

                if (
                    money.isNotEmpty()
                ) {
                    return money
                }

                if (
                    i + 1 < lines.size
                ) {

                    val next =
                        extractMoneyFromText(
                            lines[i + 1]
                        )

                    if (
                        next.isNotEmpty()
                    ) {
                        return next
                    }
                }
            }
        }

        if (
            total.isNotEmpty() &&
            cashGiven.isNotEmpty()
        ) {

            val totalValue =
                moneyToDouble(total)

            val cashValue =
                moneyToDouble(cashGiven)

            if (
                totalValue != null &&
                cashValue != null &&
                cashValue >= totalValue
            ) {

                return formatMoney(
                    cashValue -
                            totalValue
                )
            }
        }

        return ""
    }

    private fun extractDeliveryDate(
        lines: List<String>
    ): String {

        for (line in lines) {

            val match =
                Regex(
                    "\\d{2}[./-]\\d{2}[./-]\\d{4}"
                ).find(line)

            if (match != null) {
                return match.value
            }
        }

        return ""
    }

    private fun extractCertificate(
        lines: List<String>
    ): String {

        for (line in lines) {

            val normalized =
                normalizeOcrText(line)

            if (
                normalized.contains(
                    "сертификат"
                )
            ) {
                return line
                    .substringAfter(
                        ":",
                        line
                    )
                    .trim()
            }
        }

        return ""
    }

    private fun extractItems(
        lines: List<String>
    ): String {

        val result =
            mutableListOf<String>()

        var inItems =
            false

        var i = 0

        while (
            i < lines.size
        ) {

            val line =
                lines[i]

            val normalized =
                normalizeOcrText(line)

            if (
                normalized == "блюдо" ||
                normalized == "блод"
            ) {

                inItems = true
                i++
                continue
            }

            if (
                normalized.contains(
                    "суммадоскидки"
                ) ||
                normalized.contains(
                    "суммазаказа"
                ) ||
                normalized.contains(
                    "уммазаказа"
                )
            ) {
                break
            }

            if (!inItems) {
                i++
                continue
            }

            if (
                !looksLikeItem(line)
            ) {
                i++
                continue
            }

            val itemName =
                cleanItemName(line)

            if (
                itemName.isEmpty()
            ) {
                i++
                continue
            }

            var quantity = ""
            var price = ""

            var j = i + 1
            var checked = 0

            while (
                j < lines.size &&
                checked < 4
            ) {

                val next =
                    lines[j]

                if (
                    quantity.isEmpty() &&
                    isQuantityOnly(next)
                ) {

                    quantity =
                        normalizeQuantity(next)

                    j++
                    checked++
                    continue
                }

                if (
                    price.isEmpty() &&
                    isMoneyOnly(next)
                ) {

                    price =
                        extractMoneyFromText(
                            next
                        )

                    j++
                    checked++
                    continue
                }

                if (
                    looksLikeItem(next)
                ) {
                    break
                }

                j++
                checked++
            }

            val entry =
                when {

                    quantity.isNotEmpty() &&
                            price.isNotEmpty() ->
                        "$itemName ×$quantity — $price"

                    quantity.isNotEmpty() ->
                        "$itemName ×$quantity"

                    price.isNotEmpty() ->
                        "$itemName — $price"

                    else ->
                        itemName
                }

            if (
                entry.isNotEmpty() &&
                !result.contains(entry)
            ) {
                result.add(entry)
            }

            i++
        }

        return result.joinToString(
            "\n"
        )
    }

    private fun extractDrinks(
        lines: List<String>
    ): String {

        val result =
            mutableListOf<String>()

        for (line in lines) {

            val normalized =
                normalizeOcrText(line)

            if (
                isHeaderLine(line) ||
                looksLikeOrderLabel(line)
            ) {
                continue
            }

            val hasDrinkWord =
                normalized.contains("напит") ||
                        normalized.contains("кола") ||
                        normalized.contains("пепси") ||
                        normalized.contains("спрайт") ||
                        normalized.contains("фанта") ||
                        normalized.contains("вода") ||
                        normalized.contains("сок") ||
                        normalized.contains("лимонад") ||
                        normalized.contains("кофе") ||
                        normalized.contains("чай")

            val hasVolume =
                Regex(
                    "(?i)\\d+[,.]?\\d*\\s*(л|л\\.|литр|литра|литров|мл)"
                ).containsMatchIn(line)

            if (
                hasDrinkWord ||
                hasVolume
            ) {
                result.add(line)
            }
        }

        return result
            .distinct()
            .joinToString("\n")
    }

    private fun looksLikeItem(
        value: String
    ): Boolean {

        val text =
            value.trim()

        if (
            text.length < 3 ||
            text.length > 100
        ) {
            return false
        }

        if (
            isQuantityOnly(text) ||
            isMoneyOnly(text) ||
            looksLikeOrderLabel(text) ||
            isHeaderLine(text)
        ) {
            return false
        }

        val normalized =
            normalizeOcrText(text)

        if (
            normalized.contains("доставкаburgerking") ||
            normalized.contains("курьердоставки") ||
            normalized.contains("доставленвполномобъеме") ||
            normalized.contains("суммадоскидки") ||
            normalized.contains("суммазаказа")
        ) {
            return false
        }

        if (
            looksLikeAddress(
                text,
                normalized
            )
        ) {
            return false
        }

        return text.count {
            Character.isLetter(it)
        } >= 2
    }

    private fun cleanItemName(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    "^[•*\\-]+"
                ),
                ""
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun isHeaderLine(
        value: String
    ): Boolean {

        val normalized =
            normalizeOcrText(value)

        return normalized == "блюдо" ||
                normalized == "блод" ||
                normalized == "колво" ||
                normalized == "количество" ||
                normalized == "сумма"
    }

    private fun looksLikeOrderLabel(
        value: String
    ): Boolean {

        val normalized =
            normalizeOcrText(value)

        return normalized.contains("номерзаказа") ||
                normalized.contains("номерзаказ") ||
                normalized.contains("способоплаты") ||
                normalized.contains("суммазаказа") ||
                normalized.contains("уммазаказа") ||
                normalized.contains("суммадоскидки") ||
                normalized.contains("сдача") ||
                normalized.contains("отклиента") ||
                normalized.contains("сертификат") ||
                normalized.contains("курьердоставки") ||
                normalized.contains("доставленвполномобъеме")
    }

    private fun isQuantityOnly(
        value: String
    ): Boolean {

        val normalized =
            value
                .trim()
                .replace(",", ".")

        return Regex(
            "^\\d+(?:\\.\\d+)?$"
        ).matches(normalized)
    }

    private fun normalizeQuantity(
        value: String
    ): String {

        return value
            .trim()
            .replace(",", ".")
            .removeSuffix(".0")
    }

    private fun isMoneyOnly(
        value: String
    ): Boolean {

        val normalized =
            value
                .trim()
                .replace(",", ".")

        return Regex(
            "^\\d+(?:\\.\\d{1,2})?$"
        ).matches(normalized)
    }

    private fun extractMoneyFromText(
        value: String
    ): String {

        val decimal =
            Regex(
                "(?<!\\d)(\\d{1,6}[,.]\\d{1,2})(?!\\d)"
            ).find(value)

        if (
            decimal != null
        ) {
            return decimal
                .groupValues[1]
                .replace(",", ".")
        }

        return ""
    }

    private fun moneyToDouble(
        value: String
    ): Double? {

        return try {

            value
                .replace(" ", "")
                .replace(",", ".")
                .toDouble()

        } catch (_: Exception) {
            null
        }
    }

    private fun formatMoney(
        value: Double
    ): String {

        return String.format(
            Locale.US,
            "%.2f",
            value
        )
    }

    private fun normalizeOcrText(
        value: String
    ): String {

        return value
            .toLowerCase(
                Locale.getDefault()
            )
            .replace("ё", "е")
            .replace("№", "номер")
            .replace(" ", "")
            .replace("\t", "")
            .replace(":", "")
            .replace(";", "")
            .replace(",", "")
            .replace(".", "")
            .replace("-", "")
            .replace("_", "")
    }

    private fun showEmptyOcr() {

        orderNumberEditText.setText("")
        customerNameEditText.setText("")
        phoneEditText.setText("")
        addressEditText.setText("")
        totalEditText.setText("")
        paymentEditText.setText("")
        changeEditText.setText("")
        deliveryDateEditText.setText("")
        certificateEditText.setText("")
        itemsEditText.setText("")

        recognizedCashGiven = ""
        recognizedDrinks = ""

        drinksWarningText.text =
            "⚠ НЕ УДАЛОСЬ РАСПОЗНАТЬ ЧЕК\n" +
                    "Проверьте данные вручную."

        continueOcrButton.isEnabled = true
    }

    private fun saveAndContinue() {

        val orderNumber =
            orderNumberEditText.text
                .toString()
                .trim()

        if (
            orderNumber.isEmpty()
        ) {

            Toast.makeText(
                this,
                "⚠ Укажите номер заказа",
                Toast.LENGTH_LONG
            ).show()

            orderNumberEditText.requestFocus()

            return
        }

        val customerName =
            customerNameEditText.text
                .toString()
                .trim()

        val phone =
            phoneEditText.text
                .toString()
                .trim()

        val address =
            addressEditText.text
                .toString()
                .trim()

        val total =
            totalEditText.text
                .toString()
                .trim()

        val payment =
            paymentEditText.text
                .toString()
                .trim()

        val change =
            changeEditText.text
                .toString()
                .trim()

        val deliveryDate =
            deliveryDateEditText.text
                .toString()
                .trim()

        val certificate =
            certificateEditText.text
                .toString()
                .trim()

        val items =
            itemsEditText.text
                .toString()
                .trim()

        if (
            address.isEmpty()
        ) {
            Toast.makeText(
                this,
                "⚠ Проверьте адрес",
                Toast.LENGTH_LONG
            ).show()
        }

        if (
            phone.isEmpty()
        ) {
            Toast.makeText(
                this,
                "⚠ Проверьте телефон",
                Toast.LENGTH_LONG
            ).show()
        }

        if (
            total.isEmpty()
        ) {
            Toast.makeText(
                this,
                "⚠ Проверьте сумму",
                Toast.LENGTH_LONG
            ).show()
        }

        if (
            recognizedDrinks.isEmpty()
        ) {
            Toast.makeText(
                this,
                "⚠ Проверьте напитки",
                Toast.LENGTH_LONG
            ).show()
        }

        if (
            payment == "Наличные" &&
            change.isEmpty()
        ) {
            Toast.makeText(
                this,
                "⚠ Сдача не определена — проверьте чек",
                Toast.LENGTH_LONG
            ).show()
        }

        val resultIntent =
            Intent()

        resultIntent.putExtra(
            "order_number",
            orderNumber
        )

        resultIntent.putExtra(
            "customer_name",
            customerName
        )

        resultIntent.putExtra(
            "phone",
            normalizePhoneForSave(phone)
        )

        resultIntent.putExtra(
            "address",
            address
        )

        resultIntent.putExtra(
            "apartment",
            ""
        )

        resultIntent.putExtra(
            "comment",
            ""
        )

        resultIntent.putExtra(
            "total",
            total
        )

        resultIntent.putExtra(
            "payment",
            payment
        )

        resultIntent.putExtra(
            "cash_given",
            recognizedCashGiven
        )

        resultIntent.putExtra(
            "change",
            change
        )

        resultIntent.putExtra(
            "delivery_date",
            deliveryDate
        )

        resultIntent.putExtra(
            "certificate",
            certificate
        )

        resultIntent.putExtra(
            "items",
            items
        )

        resultIntent.putExtra(
            "drinks",
            recognizedDrinks
        )

        resultIntent.putExtra(
            "ocr_text",
            recognizedText
        )

        resultIntent.putExtra(
            "receipt_image_path",
            receiptImagePath
        )

        LogUtil.info(
            "OCR_ACTIVITY",
            "OCR-результат подготовлен для проверки.\n" +
                    "Номер: $orderNumber\n" +
                    "Клиент: $customerName\n" +
                    "Телефон: ${normalizePhoneForSave(phone)}\n" +
                    "Адрес: $address\n" +
                    "Сумма: $total\n" +
                    "Оплата: $payment\n" +
                    "Сумма от клиента: $recognizedCashGiven\n" +
                    "Сдача: $change\n" +
                    "Напитки: $recognizedDrinks"
        )

        try {

            val nextIntent =
                Intent(
                    this,
                    OrderCheckActivity::class.java
                )

            copyResultExtras(
                resultIntent,
                nextIntent
            )

            startActivity(
                nextIntent
            )

            finish()

        } catch (e: Exception) {

            LogUtil.error(
                "OCR_ACTIVITY",
                "Ошибка открытия проверки заказа:\n" +
                        throwableToString(e)
            )

            Toast.makeText(
                this,
                "Не удалось открыть проверку заказа: " +
                        "${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun copyResultExtras(
        source: Intent,
        target: Intent
    ) {

        val keys =
            arrayOf(
                "order_number",
                "customer_name",
                "phone",
                "address",
                "apartment",
                "comment",
                "total",
                "payment",
                "cash_given",
                "change",
                "delivery_date",
                "certificate",
                "items",
                "drinks",
                "ocr_text",
                "receipt_image_path"
            )

        for (key in keys) {

            target.putExtra(
                key,
                source.getStringExtra(key) ?: ""
            )
        }
    }

    private fun normalizePhoneForSave(
        value: String
    ): String {

        val digits =
            normalizePhoneDigits(value)

        if (
            digits.length == 12 &&
            digits.startsWith("375")
        ) {
            return formatBelarusPhone(digits)
        }

        return value
    }

    private fun throwableToString(
        throwable: Throwable?
    ): String {

        if (
            throwable == null
        ) {
            return "Throwable = null"
        }

        val builder =
            StringBuilder()

        builder.append(
            throwable.javaClass.name
        )

        builder.append(": ")

        builder.append(
            throwable.message ?: ""
        )

        builder.append("\n")

        for (
            element in throwable.stackTrace
        ) {

            builder.append("\tat ")
            builder.append(
                element.toString()
            )
            builder.append("\n")
        }

        var cause =
            throwable.cause

        var depth = 0

        while (
            cause != null &&
            depth < 5
        ) {

            builder.append(
                "\nCaused by:\n"
            )

            builder.append(
                cause.javaClass.name
            )

            builder.append(": ")

            builder.append(
                cause.message ?: ""
            )

            builder.append("\n")

            for (
                element in cause.stackTrace
            ) {

                builder.append("\tat ")
                builder.append(
                    element.toString()
                )
                builder.append("\n")
            }

            cause =
                cause.cause

            depth++
        }

        return builder.toString()
    }

    override fun onDestroy() {

        LogUtil.info(
            "OCR_ACTIVITY",
            "OcrActivity закрывается"
        )

        super.onDestroy()
    }
}
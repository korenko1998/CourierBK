package com.courierbk.app

import android.content.Intent
import android.graphics.Bitmap
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

        orderNumberEditText = findViewById(R.id.orderNumberEditText)
        customerNameEditText = findViewById(R.id.customerNameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        addressEditText = findViewById(R.id.addressEditText)
        totalEditText = findViewById(R.id.totalEditText)
        paymentEditText = findViewById(R.id.paymentEditText)
        changeEditText = findViewById(R.id.changeEditText)
        deliveryDateEditText = findViewById(R.id.deliveryDateEditText)
        certificateEditText = findViewById(R.id.certificateEditText)
        itemsEditText = findViewById(R.id.itemsEditText)
        drinksWarningText = findViewById(R.id.drinksWarningText)
        continueOcrButton = findViewById(R.id.continueOcrButton)

        continueOcrButton.setOnClickListener { saveAndContinue() }

        val directOcrText = intent.getStringExtra("ocr_text") ?: ""
        val imagePath = intent.getStringExtra("receipt_image_path")
        val imageUri = intent.getStringExtra("receipt_image_uri")

        receiptImagePath = imagePath ?: ""

        LogUtil.info(
            "OCR_ACTIVITY",
            "OcrActivity запущен. " +
                    "ocr_text символов: ${directOcrText.length}, " +
                    "imagePath: $imagePath, imageUri: $imageUri"
        )

        when {
            directOcrText.trim().isNotEmpty() -> {
                recognizedText = prepareCleanOcrText(directOcrText)
                parseOcr(recognizedText)
            }
            !imagePath.isNullOrEmpty() -> runOcrFromFile(imagePath)
            !imageUri.isNullOrEmpty() -> runOcrFromUri(imageUri)
            else -> {
                LogUtil.warning("OCR_ACTIVITY", "Не получены данные для OCR")
                showEmptyOcr()
            }
        }
    }

    private fun runOcrFromFile(path: String) {
        continueOcrButton.isEnabled = false
        Toast.makeText(this, "Распознавание чека...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                    ?: throw IllegalStateException("Не удалось загрузить изображение")

                receiptImagePath = path
                LogUtil.info(
                    "OCR_ACTIVITY",
                    "Изображение загружено: ${bitmap.width}x${bitmap.height}"
                )
                runPaddleOcr(bitmap)
            } catch (e: Exception) {
                LogUtil.error(
                    "OCR_ACTIVITY",
                    "Ошибка загрузки изображения:\n${throwableToString(e)}"
                )
                runOnUiThread {
                    continueOcrButton.isEnabled = true
                    Toast.makeText(
                        this@OcrActivity,
                        "Ошибка загрузки изображения: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmptyOcr()
                }
            }
        }.start()
    }

    private fun runOcrFromUri(uriString: String) {
        continueOcrButton.isEnabled = false
        Toast.makeText(this, "Распознавание чека...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Не удалось открыть изображение")

                val bitmap: Bitmap?
                try {
                    bitmap = BitmapFactory.decodeStream(inputStream)
                } finally {
                    try { inputStream.close() } catch (_: Exception) {}
                }

                if (bitmap == null) {
                    throw IllegalStateException("Не удалось декодировать изображение")
                }

                LogUtil.info(
                    "OCR_ACTIVITY",
                    "Изображение из URI загружено: ${bitmap.width}x${bitmap.height}"
                )
                runPaddleOcr(bitmap)
            } catch (e: Exception) {
                LogUtil.error(
                    "OCR_ACTIVITY",
                    "Ошибка загрузки URI:\n${throwableToString(e)}"
                )
                runOnUiThread {
                    continueOcrButton.isEnabled = true
                    Toast.makeText(
                        this@OcrActivity,
                        "Ошибка открытия изображения: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    showEmptyOcr()
                }
            }
        }.start()
    }

    private fun runPaddleOcr(bitmap: Bitmap) {
        try {
            LogUtil.info("OCR_ACTIVITY", "Запуск PaddleOCR")

            val engine = PaddleOcrEngine(this@OcrActivity)
            // Не вызываем close()/use(): текущий движок их не предоставляет.
            val rawText = engine.recognize(bitmap)
            val cleanText = prepareCleanOcrText(rawText)

            recognizedText = cleanText

            LogUtil.info(
                "OCR_ACTIVITY",
                "OCR завершён. Символов чистого результата: ${cleanText.length}"
            )

            runOnUiThread {
                continueOcrButton.isEnabled = true
                if (cleanText.trim().isEmpty()) showEmptyOcr()
                else parseOcr(cleanText)
            }
        } catch (e: Exception) {
            LogUtil.error(
                "OCR_ACTIVITY",
                "Ошибка PaddleOCR:\n${throwableToString(e)}"
            )
            runOnUiThread {
                continueOcrButton.isEnabled = true
                Toast.makeText(
                    this@OcrActivity,
                    "Ошибка распознавания: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                showEmptyOcr()
            }
        }
    }

    private fun prepareCleanOcrText(source: String): String {
        if (source.trim().isEmpty()) return ""

        var text = source.replace("\r\n", "\n").replace("\r", "\n")

        val marker = text.indexOf("ИТОГ OCR:", ignoreCase = true)
        if (marker >= 0) text = text.substring(marker + "ИТОГ OCR:".length)

        val cleanLines = mutableListOf<String>()

        for (line in text.split("\n")) {
            val value = line.trim()
            if (value.isEmpty()) continue
            if (isDiagnosticLine(value)) continue
            if (isPureOcrMarker(value)) continue
            cleanLines.add(value)
        }

        return cleanLines.joinToString("\n")
    }

    private fun isDiagnosticLine(line: String): Boolean {
        val value = line.toLowerCase(Locale.getDefault()).trim()

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
                value.startsWith("[error]") ||
                value.startsWith("source ") ||
                value.startsWith("input ") ||
                value.startsWith("output ")
    }

    private fun isPureOcrMarker(line: String): Boolean {
        val value = line.trim().toLowerCase(Locale.getDefault())
        return value == "ocr" ||
                value == "== ocr ==" ||
                value == "=== ocr ===" ||
                value == "итог ocr:" ||
                value == "итог ocr"
    }

    private fun parseOcr(text: String) {
        try {
            val lines = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .split("\n")
                .map { it.trim().replace(Regex("\\s+"), " ") }
                .filter { it.isNotEmpty() }

            LogUtil.info("OCR_PARSE", "Получено чистых строк OCR: ${lines.size}")

            val orderNumber = extractOrderNumber(lines)
            val customerName = extractCustomerName(lines)
            val phone = extractPhone(lines)
            val address = extractAddress(lines)
            val total = extractTotal(lines)
            val payment = extractPayment(lines)
            val cashGiven = extractCashGiven(lines)

            recognizedCashGiven = cashGiven

            val change = extractChange(lines, total, cashGiven)
            val deliveryDate = extractDeliveryDate(lines)
            val certificate = extractCertificate(lines)
            val items = extractItems(lines)
            val drinks = extractDrinks(lines)

            recognizedDrinks = drinks

            orderNumberEditText.setText(orderNumber)
            customerNameEditText.setText(customerName)
            phoneEditText.setText(phone)
            addressEditText.setText(address)
            totalEditText.setText(total)
            paymentEditText.setText(payment)
            changeEditText.setText(change)
            deliveryDateEditText.setText(deliveryDate)
            certificateEditText.setText(certificate)
            itemsEditText.setText(items)

            drinksWarningText.text =
                if (drinks.isNotEmpty()) "⚠ ПРОВЕРЬТЕ НАПИТКИ\n$drinks"
                else "⚠ ПРОВЕРЬТЕ НАПИТКИ"

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
                "Ошибка разбора:\n${throwableToString(e)}"
            )
            Toast.makeText(
                this,
                "Ошибка разбора результата OCR",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun extractOrderNumber(lines: List<String>): String {
        val patterns = listOf(
            Regex("(?i)номер\\s*заказа\\s*[:№#-]*\\s*(\\d{4,12})"),
            Regex("(?i)номерзаказа\\s*[:№#-]*\\s*(\\d{4,12})"),
            Regex("(?i)номер\\s*заказ\\s*[:№#-]*\\s*(\\d{4,12})"),
            Regex("(?i)заказ\\s*[:№#-]*\\s*(\\d{4,12})")
        )

        for (line in lines) {
            for (pattern in patterns) {
                val match = pattern.find(line)
                if (match != null) return match.groupValues[1]
            }
        }

        for (line in lines) {
            if (normalizeOcrText(line).contains("номерзаказа")) {
                val number = Regex("\\d{4,12}").find(line)
                if (number != null) return number.value
            }
        }

        for (line in lines) {
            val match = Regex("(?i)[№#]\\s*(\\d{4,12})").find(line)
            if (match != null) return match.groupValues[1]
        }

        return ""
    }

    private fun extractCustomerName(lines: List<String>): String {
        for (i in lines.indices) {
            val normalized = normalizeOcrText(lines[i])

            if (
                normalized.contains("клиент") ||
                normalized.contains("имяклиента")
            ) {
                var sameLine = lines[i]
                    .replace(Regex("(?i)имя\\s*клиента"), "")
                    .replace(Regex("(?i)клиент"), "")
                    .replace(":", "")
                    .trim()

                if (isBadCustomerValue(sameLine)) sameLine = ""

                if (looksLikePersonName(sameLine)) return sameLine

                if (i + 1 < lines.size) {
                    val next = cleanCustomerCandidate(lines[i + 1])
                    if (looksLikePersonName(next)) return next
                }

                if (i + 2 < lines.size) {
                    val next = cleanCustomerCandidate(lines[i + 2])
                    if (looksLikePersonName(next)) return next
                }
            }
        }

        return ""
    }

    private fun cleanCustomerCandidate(value: String): String {
        var result = value
            .replace(Regex("(?i)имя\\s*[:=-]?"), "")
            .replace(Regex("(?i)клиент\\s*[:=-]?"), "")
            .trim()

        if (isBadCustomerValue(result)) result = ""
        return result
    }

    private fun isBadCustomerValue(value: String): Boolean {
        if (value.isEmpty()) return true

        val normalized = normalizeOcrText(value)

        return normalized == "ocr" ||
                normalized == "клиент" ||
                normalized == "имяклиента" ||
                normalized.contains("paddleocr") ||
                normalized.contains("recoutput") ||
                normalized.contains("box") ||
                normalized.contains("text")
    }

    private fun looksLikePersonName(value: String): Boolean {
        if (value.isEmpty() || value.length > 60) return false
        if (isBadCustomerValue(value)) return false

        if (Regex("\\d").containsMatchIn(value)) return false

        val normalized = normalizeOcrText(value)

        if (
            normalized.contains("улица") ||
            normalized.contains("проспект") ||
            normalized.contains("дом") ||
            normalized.contains("квартира") ||
            normalized.contains("телефон") ||
            normalized.contains("заказ")
        ) return false

        if (
            value.any {
                !it.isLetter() &&
                        !it.isWhitespace() &&
                        it != '-' &&
                        it != '\''
            }
        ) return false

        val words = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.size <= 4
    }

    private fun extractPhone(lines: List<String>): String {
        val fullDigitsPattern = Regex(
            "(?<!\\d)375\\s*\\d{2}\\s*\\d{3}\\s*\\d{2}\\s*\\d{2}(?!\\d)"
        )

        for (line in lines) {
            val match = fullDigitsPattern.find(line)
            if (match != null) {
                val digits = match.value.filter { it.isDigit() }
                if (digits.length == 12) return formatBelarusPhone(digits)
            }
        }

        val phonePattern = Regex(
            "(?:\\+?375[\\s-]*)?(?:\\(?\\d{2}\\)?[\\s-]*)?\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}"
        )

        for (line in lines) {
            val match = phonePattern.find(line)
            if (match != null) {
                val digits = match.value.filter { it.isDigit() }
                if (digits.startsWith("375") && digits.length >= 12) {
                    return formatBelarusPhone(digits.substring(0, 12))
                }
            }
        }

        return ""
    }

    private fun formatBelarusPhone(digits: String): String {
        if (digits.length != 12) return ""

        return "+" +
                digits.substring(0, 3) + " " +
                digits.substring(3, 5) + " " +
                digits.substring(5, 8) + "-" +
                digits.substring(8, 10) + "-" +
                digits.substring(10, 12)
    }

    private fun extractAddress(lines: List<String>): String {
        val candidates = mutableListOf<String>()

        for (line in lines) {
            val normalized = normalizeOcrText(line)

            if (
                normalized.contains("улица") ||
                normalized.startsWith("ул") ||
                normalized.contains("проспект") ||
                normalized.contains("просп") ||
                normalized.contains("переулок") ||
                normalized.startsWith("пер") ||
                normalized.contains("дом")
            ) {
                if (!containsTooMuchNoise(line)) candidates.add(line)
            }
        }

        if (candidates.isEmpty()) return ""

        // Kotlin 1.3.72: maxBy, не maxByOrNull.
        return candidates.maxBy { it.length }!!
    }

    private fun containsTooMuchNoise(value: String): Boolean {
        val letters = value.count { it.isLetter() }
        val digits = value.count { it.isDigit() }

        if (letters + digits == 0) return true
        return value.length > 120
    }

    private fun extractTotal(lines: List<String>): String {
        for (i in lines.indices) {
            val normalized = normalizeOcrText(lines[i])

            if (
                normalized.contains("суммазаказа") ||
                normalized.contains("уммазаказа")
            ) {
                val same = extractMoneyFromText(lines[i])
                if (same.isNotEmpty()) return same

                if (i + 1 < lines.size) {
                    val next = extractMoneyFromText(lines[i + 1])
                    if (next.isNotEmpty()) return next
                }
            }
        }

        for (i in (lines.size - 1) downTo 0) {
            val normalized = normalizeOcrText(lines[i])

            if (normalized.contains("сумма")) {
                val money = extractMoneyFromText(lines[i])
                if (money.isNotEmpty()) return money
            }
        }

        return ""
    }

    private fun extractPayment(lines: List<String>): String {
        for (line in lines) {
            val normalized = normalizeOcrText(line)

            if (
                normalized.contains("способоплаты") ||
                normalized.contains("способплаты") ||
                normalized.contains("способоплат")
            ) return normalizePayment(line)
        }

        return ""
    }

    private fun normalizePayment(value: String): String {
        val normalized = value
            .toLowerCase(Locale.getDefault())
            .replace(" ", "")

        if (
            normalized.contains("cash") ||
            normalized.contains("сасh") ||
            normalized.contains("сash") ||
            normalized.contains("casн") ||
            normalized.contains("налич")
        ) return "Наличные"

        if (
            normalized.contains("card") ||
            normalized.contains("карт") ||
            normalized.contains("visa") ||
            normalized.contains("master") ||
            normalized.contains("безнал")
        ) return "Карта"

        if (
            normalized.contains("online") ||
            normalized.contains("онлайн")
        ) return "Онлайн"

        val lower = value.toLowerCase(Locale.getDefault())
        val position = lower.indexOf("оплаты")

        if (position >= 0) {
            val start = position + "оплаты".length
            if (start < value.length) return value.substring(start).trim()
        }

        return value.trim()
    }

    private fun extractCashGiven(lines: List<String>): String {
        for (i in lines.indices) {
            val normalized = normalizeOcrText(lines[i])

            if (
                normalized.contains("отклиента") ||
                normalized.contains("полученоотклиента") ||
                normalized.contains("внесено") ||
                normalized.contains("клиентвнес")
            ) {
                val money = extractMoneyFromText(lines[i])
                if (money.isNotEmpty()) return money

                if (i + 1 < lines.size) {
                    val next = extractMoneyFromText(lines[i + 1])
                    if (next.isNotEmpty()) return next
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
            val normalized = normalizeOcrText(lines[i])

            if (
                normalized.contains("сдача") ||
                normalized.contains("сдачи")
            ) {
                val money = extractMoneyFromText(lines[i])
                if (money.isNotEmpty()) return money

                if (i + 1 < lines.size) {
                    val next = extractMoneyFromText(lines[i + 1])
                    if (next.isNotEmpty()) return next
                }
            }
        }

        val totalValue = parseMoney(total)
        val cashValue = parseMoney(cashGiven)

        if (
            totalValue >= 0.0 &&
            cashValue >= 0.0 &&
            cashValue >= totalValue &&
            cashGiven.isNotEmpty()
        ) return formatMoney(cashValue - totalValue)

        return ""
    }

    private fun extractDeliveryDate(lines: List<String>): String {
        val datePattern = Regex(
            "\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}(?:\\s+\\d{1,2}:\\d{2})?"
        )

        for (line in lines) {
            val match = datePattern.find(line)
            if (match != null) return match.value
        }

        return ""
    }

    private fun extractCertificate(lines: List<String>): String {
        for (line in lines) {
            val normalized = normalizeOcrText(line)

            if (
                normalized.contains("сертифик") ||
                normalized.contains("сертиф")
            ) return line
        }

        return ""
    }

    private fun extractItems(lines: List<String>): String {
        val result = mutableListOf<String>()
        var started = false

        for (line in lines) {
            val normalized = normalizeOcrText(line)

            if (
                normalized == "блюд" ||
                normalized == "блюда" ||
                normalized.contains("наименование") ||
                normalized.contains("колво")
            ) {
                started = true
                continue
            }

            if (!started) continue

            if (
                normalized.contains("суммазаказа") ||
                normalized.contains("уммазаказа") ||
                normalized.contains("доставленвполномобъеме") ||
                normalized.contains("итого")
            ) break

            if (
                normalized.contains("скидка") ||
                normalized.contains("суммадоскидки")
            ) continue

            if (isQuantityOnly(line) || isMoneyOnly(line)) continue
            if (looksLikeItem(line)) result.add(line)
        }

        if (result.isEmpty()) {
            for (line in lines) {
                if (looksLikeItem(line) && !isLikelyHeader(line)) result.add(line)
            }
        }

        return result.distinct().joinToString("\n")
    }

    private fun isLikelyHeader(value: String): Boolean {
        val normalized = normalizeOcrText(value)

        return normalized.contains("доставка") ||
                normalized.contains("номер") ||
                normalized.contains("клиент") ||
                normalized.contains("телефон") ||
                normalized.contains("способоплаты") ||
                normalized.contains("сертифик") ||
                normalized.contains("колво") ||
                normalized.contains("сумма") ||
                normalized.contains("заказдоставлен")
    }

    private fun extractDrinks(lines: List<String>): String {
        val result = mutableListOf<String>()

        for (line in lines) {
            val normalized = normalizeOcrText(line)

            val hasDrinkKeyword =
                normalized.contains("напит") ||
                        normalized.contains("кола") ||
                        normalized.contains("пепси") ||
                        normalized.contains("спрайт") ||
                        normalized.contains("фанта") ||
                        normalized.contains("вода") ||
                        normalized.contains("сок") ||
                        normalized.contains("кофе") ||
                        normalized.contains("капуч") ||
                        normalized.contains("латте")

            val hasVolume = Regex(
                "(?i)\\d+[,.]?\\d*\\s*(л|литр|литра|мл|ml)"
            ).containsMatchIn(line)

            if (hasDrinkKeyword || hasVolume) result.add(line)
        }

        return result.distinct().joinToString("\n")
    }

    private fun isQuantityOnly(value: String): Boolean {
        return Regex("^\\d+[,.]?\\d*$").matches(value.trim())
    }

    private fun isMoneyOnly(value: String): Boolean {
        return Regex("^\\d+[,.]\\d{1,2}$").matches(value.trim())
    }

    private fun looksLikeItem(value: String): Boolean {
        if (value.length < 3 || value.length > 100) return false

        if (isQuantityOnly(value) || isMoneyOnly(value)) return false

        val normalized = normalizeOcrText(value)

        if (
            normalized.contains("сумма") ||
            normalized.contains("итого") ||
            normalized.contains("скидк") ||
            normalized.contains("оплат") ||
            normalized.contains("сертифик") ||
            normalized.contains("доставленвполномобъеме") ||
            normalized == "блод" ||
            normalized == "колво"
        ) return false

        return value.any { it.isLetter() }
    }

    private fun extractMoneyFromText(value: String): String {
        val matches = Regex("\\d+[,.]\\d{1,2}").findAll(value)

        var result = ""
        for (match in matches) result = match.value

        return result.replace(",", ".")
    }

    private fun parseMoney(value: String): Double {
        if (value.trim().isEmpty()) return -1.0

        return try {
            value
                .replace(" ", "")
                .replace(",", ".")
                .toDouble()
        } catch (_: Exception) {
            -1.0
        }
    }

    private fun formatMoney(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }

    private fun normalizeOcrText(value: String): String {
        return value
            .toLowerCase(Locale.getDefault())
            .replace(" ", "")
            .replace("\t", "")
            .replace(":", "")
            .replace("-", "")
            .replace("_", "")
    }

    private fun saveAndContinue() {
        val nextIntent = Intent(
            this,
            OrderCheckActivity::class.java
        )

        nextIntent.putExtra(
            "order_number",
            orderNumberEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "customer_name",
            customerNameEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "phone",
            phoneEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "address",
            addressEditText.text.toString().trim()
        )
        nextIntent.putExtra("apartment", "")
        nextIntent.putExtra("comment", "")
        nextIntent.putExtra(
            "total",
            totalEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "payment",
            paymentEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "cash_given",
            recognizedCashGiven
        )
        nextIntent.putExtra(
            "change",
            changeEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "items",
            itemsEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "drinks",
            recognizedDrinks
        )
        nextIntent.putExtra(
            "delivery_date",
            deliveryDateEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "certificate",
            certificateEditText.text.toString().trim()
        )
        nextIntent.putExtra(
            "ocr_text",
            recognizedText
        )
        nextIntent.putExtra(
            "receipt_image_path",
            receiptImagePath
        )

        LogUtil.info(
            "OCR_ACTIVITY",
            "Переход к проверке заказа. " +
                    "Номер=${orderNumberEditText.text}, " +
                    "адрес=${addressEditText.text}"
        )

        startActivity(nextIntent)
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
            "⚠ Не удалось автоматически распознать данные. Проверьте чек вручную."

        continueOcrButton.isEnabled = true
    }

    private fun throwableToString(
        throwable: Throwable?
    ): String {
        if (throwable == null) return "Throwable = null"

        val builder = StringBuilder()

        builder.append(throwable.javaClass.name)
        builder.append(": ")
        builder.append(throwable.message ?: "")
        builder.append("\n")

        for (element in throwable.stackTrace) {
            builder.append("\tat ")
            builder.append(element.toString())
            builder.append("\n")
        }

        var cause = throwable.cause
        var depth = 0

        while (cause != null && depth < 5) {
            builder.append("\nCaused by:\n")
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

        return builder.toString()
    }
}

package com.courierbk.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PaddleOcrEngine(
    private val context: Context
) {

    companion object {

        private const val DET_MODEL =
            "ocr_models/ch_PP-OCRv5_det_mobile.onnx"

        private const val REC_MODEL =
            "ocr_models/eslav_PP-OCRv5_rec_mobile.onnx"

        private const val DICT =
            "ocr_models/ppocrv5_eslav_dict.txt"

        private const val REC_HEIGHT = 48
        private const val REC_WIDTH = 320

        private const val DET_MAX_SIDE = 960

        /*
         * DB detector threshold.
         *
         * 0.25 is intentionally a little lower than the usual
         * 0.30-0.50 range because this is the first real integration.
         */
        private const val DET_THRESHOLD = 0.25f

        /*
         * Very small connected components are usually noise.
         */
        private const val MIN_COMPONENT_PIXELS = 12

        /*
         * Minimum bounding-box dimensions after detector scaling.
         */
        private const val MIN_BOX_WIDTH = 4
        private const val MIN_BOX_HEIGHT = 4

        /*
         * A component occupying almost the entire image is usually
         * a detector background artifact, not a text line.
         */
        private const val MAX_BOX_IMAGE_RATIO = 0.90f
    }

    private val environment: OrtEnvironment =
        OrtEnvironment.getEnvironment()

    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null

    private var dictionary: List<String> = emptyList()

    init {
        try {

            LogUtil.info(
                "OCR",
                "Инициализация PaddleOcrEngine"
            )

            loadDictionary()
            loadModels()

            LogUtil.info(
                "OCR",
                "PaddleOcrEngine инициализирован"
            )

        } catch (e: Exception) {

            LogUtil.error(
                "OCR",
                "Ошибка инициализации:\n${exceptionText(e)}"
            )
        }
    }

    private fun loadDictionary() {

        try {

            val result =
                ArrayList<String>()

            context.assets.open(DICT).use { input ->

                BufferedReader(
                    InputStreamReader(
                        input,
                        Charsets.UTF_8
                    )
                ).use { reader ->

                    while (true) {

                        val line =
                            reader.readLine()
                                ?: break

                        result.add(line)
                    }
                }
            }

            /*
             * Empty lines in a dictionary are meaningful only in very
             * unusual models. For normal PP-OCR dictionaries we remove
             * accidental blank lines.
             */
            dictionary =
                result.filter {
                    it.isNotEmpty()
                }

            LogUtil.info(
                "OCR",
                "Dictionary загружен. Символов: ${dictionary.size}"
            )

        } catch (e: Exception) {

            dictionary =
                emptyList()

            LogUtil.error(
                "OCR",
                "Ошибка загрузки dictionary:\n${exceptionText(e)}"
            )
        }
    }

    private fun loadModels() {

        try {

            val detBytes =
                context.assets
                    .open(DET_MODEL)
                    .use {
                        it.readBytes()
                    }

            val recBytes =
                context.assets
                    .open(REC_MODEL)
                    .use {
                        it.readBytes()
                    }

            LogUtil.info(
                "OCR",
                "DET модель загружена. Размер: ${detBytes.size} bytes"
            )

            LogUtil.info(
                "OCR",
                "REC модель загружена. Размер: ${recBytes.size} bytes"
            )

            detSession =
                environment.createSession(
                    detBytes,
                    OrtSession.SessionOptions()
                )

            recSession =
                environment.createSession(
                    recBytes,
                    OrtSession.SessionOptions()
                )

            logSessionInfo(
                "DET",
                detSession
            )

            logSessionInfo(
                "REC",
                recSession
            )

        } catch (e: Exception) {

            LogUtil.error(
                "OCR",
                "Ошибка загрузки ONNX моделей:\n${exceptionText(e)}"
            )
        }
    }

    private fun logSessionInfo(
        name: String,
        session: OrtSession?
    ) {

        if (session == null) {

            LogUtil.error(
                "OCR",
                "$name session = null"
            )

            return
        }

        try {

            LogUtil.info(
                "OCR",
                "$name input names: ${session.inputNames}"
            )

            LogUtil.info(
                "OCR",
                "$name output names: ${session.outputNames}"
            )

            for (inputName in session.inputNames) {

                try {

                    val info =
                        session.inputInfo[inputName]?.info

                    if (info is TensorInfo) {

                        LogUtil.info(
                            "OCR",
                            "$name input '$inputName' shape: " +
                                    info.shape.contentToString()
                        )
                    }

                } catch (e: Exception) {

                    LogUtil.error(
                        "OCR",
                        "$name input info error:\n${exceptionText(e)}"
                    )
                }
            }

            for (outputName in session.outputNames) {

                try {

                    val info =
                        session.outputInfo[outputName]?.info

                    if (info is TensorInfo) {

                        LogUtil.info(
                            "OCR",
                            "$name output '$outputName' shape: " +
                                    info.shape.contentToString()
                        )
                    }

                } catch (e: Exception) {

                    LogUtil.error(
                        "OCR",
                        "$name output info error:\n${exceptionText(e)}"
                    )
                }
            }

        } catch (e: Exception) {

            LogUtil.error(
                "OCR",
                "$name session diagnostic error:\n${exceptionText(e)}"
            )
        }
    }

    fun recognize(
        bitmap: Bitmap
    ): String {

        val report =
            StringBuilder()

        report.append(
            "=== OCR ===\n"
        )

        report.append(
            "SOURCE: ${bitmap.width} x ${bitmap.height}\n"
        )

        LogUtil.info(
            "OCR",
            "Начало OCR. SOURCE: " +
                    "${bitmap.width} x ${bitmap.height}"
        )

        if (bitmap.width <= 0 || bitmap.height <= 0) {

            report.append(
                "ERROR: неправильный размер изображения\n"
            )

            LogUtil.error(
                "OCR",
                "Неправильный размер изображения"
            )

            return report.toString()
        }

        if (dictionary.isEmpty()) {

            report.append(
                "ERROR: dictionary пуст\n"
            )

            LogUtil.error(
                "OCR",
                "Dictionary пуст"
            )

            return report.toString()
        }

        val det =
            detSession

        if (det == null) {

            report.append(
                "ERROR: DET session = null\n"
            )

            LogUtil.error(
                "OCR",
                "DET session = null"
            )

            return report.toString()
        }

        val rec =
            recSession

        if (rec == null) {

            report.append(
                "ERROR: REC session = null\n"
            )

            LogUtil.error(
                "OCR",
                "REC session = null"
            )

            return report.toString()
        }

        val source =
            try {

                bitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )

            } catch (e: Exception) {

                report.append(
                    "SOURCE COPY ERROR:\n"
                )

                report.append(
                    exceptionText(e)
                )

                LogUtil.error(
                    "OCR",
                    "Ошибка копирования SOURCE:\n" +
                            exceptionText(e)
                )

                return report.toString()
            }

        try {

            /*
             * 1. DET
             */
            val boxes =
                runDetector(
                    det,
                    source,
                    report
                )

            report.append(
                "DET boxes: ${boxes.size}\n"
            )

            LogUtil.info(
                "OCR",
                "DET найдено областей: ${boxes.size}"
            )

            if (boxes.isEmpty()) {

                report.append(
                    "\nИТОГ: текстовые области не найдены.\n"
                )

                LogUtil.warning(
                    "OCR",
                    "DET не нашёл текстовых областей"
                )

                return report.toString()
            }

            /*
             * 2. REC
             */
            val recognizedLines =
                ArrayList<String>()

            for (index in boxes.indices) {

                val box =
                    boxes[index]

                report.append(
                    "\nBOX #${index + 1}: " +
                            "${box.left},${box.top} - " +
                            "${box.right},${box.bottom}\n"
                )

                LogUtil.info(
                    "OCR",
                    "BOX #${index + 1}: " +
                            "${box.left},${box.top} - " +
                            "${box.right},${box.bottom}"
                )

                val crop =
                    cropBitmap(
                        source,
                        box
                    )

                if (crop == null) {

                    report.append(
                        "BOX #${index + 1}: crop ERROR\n"
                    )

                    LogUtil.warning(
                        "OCR",
                        "BOX #${index + 1}: crop ERROR"
                    )

                    continue
                }

                try {

                    report.append(
                        "CROP #${index + 1}: " +
                                "${crop.width} x ${crop.height}\n"
                    )

                    val text =
                        runRecognizer(
                            rec,
                            crop,
                            index + 1,
                            report
                        )

                    if (text.isNotBlank()) {

                        recognizedLines.add(
                            text.trim()
                        )

                        report.append(
                            "TEXT #${index + 1}: $text\n"
                        )

                        LogUtil.info(
                            "OCR",
                            "TEXT #${index + 1}: $text"
                        )

                    } else {

                        report.append(
                            "TEXT #${index + 1}: пусто\n"
                        )

                        LogUtil.warning(
                            "OCR",
                            "REC #${index + 1}: пустой результат"
                        )
                    }

                } finally {

                    try {
                        crop.recycle()
                    } catch (_: Exception) {
                    }
                }
            }

            /*
             * 3. Final result
             */
            val finalText =
                recognizedLines
                    .joinToString("\n")
                    .trim()

            report.append(
                "\n=== РЕЗУЛЬТАТ ===\n"
            )

            if (finalText.isBlank()) {

                report.append(
                    "OCR не распознал текст.\n"
                )

                LogUtil.warning(
                    "OCR",
                    "ИТОГ: OCR не распознал текст"
                )

            } else {

                report.append(
                    finalText
                )

                report.append(
                    "\n"
                )

                LogUtil.info(
                    "OCR",
                    "ИТОГ OCR:\n$finalText"
                )
            }

            LogUtil.info(
                "OCR",
                "OCR завершён. Строк распознано: " +
                        recognizedLines.size
            )

            return report.toString()

        } catch (e: Exception) {

            report.append(
                "\nOCR EXCEPTION:\n"
            )

            report.append(
                exceptionText(e)
            )

            LogUtil.error(
                "OCR",
                "Ошибка OCR:\n" +
                        exceptionText(e)
            )

            return report.toString()

        } finally {

            try {
                source.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun runDetector(
        session: OrtSession,
        source: Bitmap,
        report: StringBuilder
    ): List<TextBox> {

        val largestSide =
            max(
                source.width,
                source.height
            )

        val scale =
            min(
                1f,
                DET_MAX_SIDE.toFloat() /
                        largestSide.toFloat()
            )

        var width =
            max(
                32,
                (source.width * scale).toInt()
            )

        var height =
            max(
                32,
                (source.height * scale).toInt()
            )

        width =
            roundUp32(width)

        height =
            roundUp32(height)

        /*
         * Do not allow the rounded dimensions to exceed the maximum side
         * by a large amount.
         */
        if (width > DET_MAX_SIDE) {
            width = DET_MAX_SIDE
        }

        if (height > DET_MAX_SIDE) {
            height = DET_MAX_SIDE
        }

        report.append(
            "\nDET INPUT: ${height} x ${width}\n"
        )

        LogUtil.info(
            "OCR",
            "DET input: ${height} x ${width}"
        )

        val inputName =
            session.inputNames
                .iterator()
                .next()

        val data =
            bitmapToNormalizedTensor(
                source,
                width,
                height
            )

        val shape =
            longArrayOf(
                1L,
                3L,
                height.toLong(),
                width.toLong()
            )

        var tensor: OnnxTensor? =
            null

        var outputs: OrtSession.Result? =
            null

        try {

            tensor =
                OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(data),
                    shape
                )

            outputs =
                session.run(
                    mapOf(
                        inputName to tensor
                    )
                )

            if (outputs.size() <= 0) {

                throw IllegalStateException(
                    "DET не вернул outputs"
                )
            }

            val output =
                outputs[0]

            val info =
                output.info

            if (info !is TensorInfo) {

                throw IllegalStateException(
                    "DET output не является TensorInfo"
                )
            }

            val outputShape =
                info.shape

            report.append(
                "DET OUTPUT: " +
                        outputShape.contentToString() +
                        "\n"
            )

            LogUtil.info(
                "OCR",
                "DET output shape: " +
                        outputShape.contentToString()
            )

            val raw =
                output.value

            val values =
                ArrayList<Float>()

            flattenNumeric(
                raw,
                values
            )

            report.append(
                "DET values: ${values.size}\n"
            )

            if (values.isEmpty()) {

                throw IllegalStateException(
                    "DET output пустой"
                )
            }

            /*
             * PP-OCR DB output is normally:
             *
             * [1, 1, H, W]
             *
             * Some exported models can expose equivalent dimensions.
             * We take the last two dimensions as map height/width.
             */
            if (outputShape.size < 2) {

                throw IllegalStateException(
                    "DET output shape слишком короткий: " +
                            outputShape.contentToString()
                )
            }

            val mapHeight =
                outputShape[
                    outputShape.size - 2
                ].toInt()

            val mapWidth =
                outputShape[
                    outputShape.size - 1
                ].toInt()

            if (mapHeight <= 0 || mapWidth <= 0) {

                throw IllegalStateException(
                    "Неверный DET map: " +
                            "$mapHeight x $mapWidth"
                )
            }

            val expectedMapSize =
                mapHeight * mapWidth

            if (values.size < expectedMapSize) {

                throw IllegalStateException(
                    "DET values меньше ожидаемого: " +
                            "${values.size} < $expectedMapSize"
                )
            }

            report.append(
                "DET MAP: ${mapHeight} x ${mapWidth}\n"
            )

            /*
             * Convert detector probability map to text components.
             */
            val boxes =
                extractTextBoxes(
                    values,
                    mapWidth,
                    mapHeight,
                    source.width,
                    source.height,
                    width,
                    height,
                    report
                )

            return boxes

        } finally {

            try {
                outputs?.close()
            } catch (_: Exception) {
            }

            try {
                tensor?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractTextBoxes(
        values: List<Float>,
        mapWidth: Int,
        mapHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        detectorWidth: Int,
        detectorHeight: Int,
        report: StringBuilder
    ): List<TextBox> {

        val total =
            mapWidth * mapHeight

        val binary =
            BooleanArray(total)

        var positiveCount =
            0

        var maxProbability =
            0f

        var minProbability =
            Float.MAX_VALUE

        for (index in 0 until total) {

            val value =
                values[index]

            if (value > maxProbability) {
                maxProbability = value
            }

            if (value < minProbability) {
                minProbability = value
            }

            if (value >= DET_THRESHOLD) {

                binary[index] = true
                positiveCount++
            }
        }

        report.append(
            "DET threshold: $DET_THRESHOLD\n"
        )

        report.append(
            "DET probability min/max: " +
                    "$minProbability / $maxProbability\n"
        )

        report.append(
            "DET positive pixels: $positiveCount / $total\n"
        )

        LogUtil.info(
            "OCR",
            "DET probability min/max: " +
                    "$minProbability / $maxProbability"
        )

        LogUtil.info(
            "OCR",
            "DET positive pixels: " +
                    "$positiveCount / $total"
        )

        if (positiveCount == 0) {

            /*
             * The model can occasionally produce a lower probability map.
             * Try a second threshold without changing the model input.
             */
            val fallbackThreshold =
                0.15f

            LogUtil.warning(
                "OCR",
                "DET при threshold $DET_THRESHOLD пуст. " +
                        "Пробуем $fallbackThreshold"
            )

            report.append(
                "DET fallback threshold: " +
                        "$fallbackThreshold\n"
            )

            for (index in 0 until total) {

                binary[index] =
                    values[index] >=
                            fallbackThreshold
            }
        }

        val visited =
            BooleanArray(total)

        val boxes =
            ArrayList<TextBox>()

        val queueX =
            IntArray(total)

        val queueY =
            IntArray(total)

        for (startY in 0 until mapHeight) {

            for (startX in 0 until mapWidth) {

                val startIndex =
                    startY * mapWidth +
                            startX

                if (!binary[startIndex]) {
                    continue
                }

                if (visited[startIndex]) {
                    continue
                }

                var queueStart =
                    0

                var queueEnd =
                    0

                queueX[queueEnd] =
                    startX

                queueY[queueEnd] =
                    startY

                queueEnd++

                visited[startIndex] =
                    true

                var minX =
                    startX

                var maxX =
                    startX

                var minY =
                    startY

                var maxY =
                    startY

                var pixelCount =
                    0

                while (queueStart < queueEnd) {

                    val x =
                        queueX[queueStart]

                    val y =
                        queueY[queueStart]

                    queueStart++

                    pixelCount++

                    if (x < minX) {
                        minX = x
                    }

                    if (x > maxX) {
                        maxX = x
                    }

                    if (y < minY) {
                        minY = y
                    }

                    if (y > maxY) {
                        maxY = y
                    }

                    for (dy in -1..1) {

                        for (dx in -1..1) {

                            if (dx == 0 && dy == 0) {
                                continue
                            }

                            val nx =
                                x + dx

                            val ny =
                                y + dy

                            if (nx < 0 ||
                                nx >= mapWidth ||
                                ny < 0 ||
                                ny >= mapHeight
                            ) {
                                continue
                            }

                            val neighbourIndex =
                                ny * mapWidth +
                                        nx

                            if (!binary[neighbourIndex]) {
                                continue
                            }

                            if (visited[neighbourIndex]) {
                                continue
                            }

                            visited[neighbourIndex] =
                                true

                            queueX[queueEnd] =
                                nx

                            queueY[queueEnd] =
                                ny

                            queueEnd++
                        }
                    }
                }

                if (
                    pixelCount <
                    MIN_COMPONENT_PIXELS
                ) {
                    continue
                }

                val boxWidth =
                    maxX - minX + 1

                val boxHeight =
                    maxY - minY + 1

                if (
                    boxWidth <
                    MIN_BOX_WIDTH
                ) {
                    continue
                }

                if (
                    boxHeight <
                    MIN_BOX_HEIGHT
                ) {
                    continue
                }

                val imageRatio =
                    (
                        boxWidth.toFloat() *
                                boxHeight.toFloat()
                    ) /
                            (
                                mapWidth.toFloat() *
                                        mapHeight.toFloat()
                            )

                if (
                    imageRatio >
                    MAX_BOX_IMAGE_RATIO
                ) {
                    continue
                }

                /*
                 * Map probability-map coordinates back to detector-image
                 * coordinates and then to original SOURCE coordinates.
                 */
                val leftDetector =
                    (
                        minX.toFloat() /
                                mapWidth.toFloat() *
                                detectorWidth.toFloat()
                    ).toInt()

                val rightDetector =
                    (
                        (maxX + 1).toFloat() /
                                mapWidth.toFloat() *
                                detectorWidth.toFloat()
                    ).toInt()

                val topDetector =
                    (
                        minY.toFloat() /
                                mapHeight.toFloat() *
                                detectorHeight.toFloat()
                    ).toInt()

                val bottomDetector =
                    (
                        (maxY + 1).toFloat() /
                                mapHeight.toFloat() *
                                detectorHeight.toFloat()
                    ).toInt()

                val left =
                    (
                        leftDetector.toFloat() /
                                detectorWidth.toFloat() *
                                sourceWidth.toFloat()
                    ).toInt()

                val right =
                    (
                        rightDetector.toFloat() /
                                detectorWidth.toFloat() *
                                sourceWidth.toFloat()
                    ).toInt()

                val top =
                    (
                        topDetector.toFloat() /
                                detectorHeight.toFloat() *
                                sourceHeight.toFloat()
                    ).toInt()

                val bottom =
                    (
                        bottomDetector.toFloat() /
                                detectorHeight.toFloat() *
                                sourceHeight.toFloat()
                    ).toInt()

                val safeLeft =
                    max(
                        0,
                        min(
                            sourceWidth - 1,
                            left
                        )
                    )

                val safeTop =
                    max(
                        0,
                        min(
                            sourceHeight - 1,
                            top
                        )
                    )

                val safeRight =
                    max(
                        safeLeft + 1,
                        min(
                            sourceWidth,
                            right
                        )
                    )

                val safeBottom =
                    max(
                        safeTop + 1,
                        min(
                            sourceHeight,
                            bottom
                        )
                    )

                /*
                 * Add a little padding around text.
                 */
                val padX =
                    max(
                        2,
                        (
                            safeRight -
                                    safeLeft
                        ) / 20
                    )

                val padY =
                    max(
                        2,
                        (
                            safeBottom -
                                    safeTop
                        ) / 4
                    )

                val paddedLeft =
                    max(
                        0,
                        safeLeft - padX
                    )

                val paddedTop =
                    max(
                        0,
                        safeTop - padY
                    )

                val paddedRight =
                    min(
                        sourceWidth,
                        safeRight + padX
                    )

                val paddedBottom =
                    min(
                        sourceHeight,
                        safeBottom + padY
                    )

                if (
                    paddedRight >
                    paddedLeft &&
                    paddedBottom >
                    paddedTop
                ) {

                    boxes.add(
                        TextBox(
                            paddedLeft,
                            paddedTop,
                            paddedRight,
                            paddedBottom
                        )
                    )
                }
            }
        }

        /*
         * Sort approximately by reading order:
         * top-to-bottom, then left-to-right.
         */
        boxes.sortWith(
            Comparator { first, second ->

                val firstCenter =
                    (
                        first.top +
                                first.bottom
                    ) / 2

                val secondCenter =
                    (
                        second.top +
                                second.bottom
                    ) / 2

                val firstHeight =
                    max(
                        1,
                        first.bottom -
                                first.top
                    )

                val secondHeight =
                    max(
                        1,
                        second.bottom -
                                second.top
                    )

                val lineTolerance =
                    max(
                        8,
                        min(
                            firstHeight,
                            secondHeight
                        ) / 2
                    )

                if (
                    abs(
                        firstCenter -
                                secondCenter
                    ) <= lineTolerance
                ) {

                    first.left.compareTo(
                        second.left
                    )

                } else {

                    firstCenter.compareTo(
                        secondCenter
                    )
                }
            }
        )

        report.append(
            "DET valid boxes: ${boxes.size}\n"
        )

        LogUtil.info(
            "OCR",
            "DET valid boxes: ${boxes.size}"
        )

        return boxes
    }

    private fun cropBitmap(
        source: Bitmap,
        box: TextBox
    ): Bitmap? {

        val width =
            box.right - box.left

        val height =
            box.bottom - box.top

        if (width <= 0 || height <= 0) {
            return null
        }

        return try {

            Bitmap.createBitmap(
                source,
                box.left,
                box.top,
                width,
                height
            )

        } catch (e: Exception) {

            LogUtil.error(
                "OCR",
                "Ошибка crop: ${exceptionText(e)}"
            )

            null
        }
    }

    private fun runRecognizer(
        session: OrtSession,
        crop: Bitmap,
        lineNumber: Int,
        report: StringBuilder
    ): String {

        val prepared =
            prepareRecognitionBitmap(
                crop
            )

        try {

            report.append(
                "REC INPUT #$lineNumber: " +
                        "${prepared.height} x " +
                        "${prepared.width}\n"
            )

            LogUtil.info(
                "OCR",
                "REC #$lineNumber input: " +
                        "${prepared.height} x " +
                        "${prepared.width}"
            )

            val data =
                bitmapToNormalizedTensor(
                    prepared,
                    prepared.width,
                    prepared.height
                )

            val shape =
                longArrayOf(
                    1L,
                    3L,
                    prepared.height.toLong(),
                    prepared.width.toLong()
                )

            val inputName =
                session.inputNames
                    .iterator()
                    .next()

            var tensor: OnnxTensor? =
                null

            var outputs: OrtSession.Result? =
                null

            try {

                tensor =
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(data),
                        shape
                    )

                outputs =
                    session.run(
                        mapOf(
                            inputName to tensor
                        )
                    )

                if (outputs.size() <= 0) {

                    throw IllegalStateException(
                        "REC не вернул outputs"
                    )
                }

                val output =
                    outputs[0]

                val info =
                    output.info

                if (info !is TensorInfo) {

                    throw IllegalStateException(
                        "REC output не является TensorInfo"
                    )
                }

                val outputShape =
                    info.shape

                report.append(
                    "REC OUTPUT #$lineNumber: " +
                            outputShape.contentToString() +
                            "\n"
                )

                LogUtil.info(
                    "OCR",
                    "REC #$lineNumber output shape: " +
                            outputShape.contentToString()
                )

                val raw =
                    output.value

                val values =
                    ArrayList<Float>()

                flattenNumeric(
                    raw,
                    values
                )

                if (values.isEmpty()) {

                    throw IllegalStateException(
                        "REC output пустой"
                    )
                }

                /*
                 * Expected PP-OCR recognition output:
                 *
                 * [1, time, classes]
                 *
                 * The model reported the third dimension as 519.
                 */
                if (outputShape.size < 2) {

                    throw IllegalStateException(
                        "REC output shape слишком короткий: " +
                                outputShape.contentToString()
                    )
                }

                var classCount =
                    outputShape[
                        outputShape.size - 1
                    ].toInt()

                var timeSteps =
                    if (outputShape.size >= 2) {
                        outputShape[
                            outputShape.size - 2
                        ].toInt()
                    } else {
                        0
                    }

                /*
                 * In case the exported model returns [classes, time]
                 * rather than [time, classes], detect it from the known
                 * class count / dictionary.
                 */
                if (
                    classCount <= 0 ||
                    timeSteps <= 0 ||
                    classCount * timeSteps >
                    values.size
                ) {

                    throw IllegalStateException(
                        "Некорректный REC output: " +
                                outputShape.contentToString() +
                                ", values=${values.size}"
                    )
                }

                /*
                 * PP-OCRv5 exported models normally put classes last.
                 */
                if (
                    classCount != 519 &&
                    timeSteps == 519
                ) {

                    val oldClassCount =
                        classCount

                    classCount =
                        timeSteps

                    timeSteps =
                        oldClassCount

                    report.append(
                        "REC: транспонированный формат output\n"
                    )

                    LogUtil.warning(
                        "OCR",
                        "REC использует транспонированный output"
                    )

                    return decodeCtcTransposed(
                        values,
                        timeSteps,
                        classCount,
                        lineNumber,
                        report
                    )
                }

                report.append(
                    "REC time: $timeSteps, classes: $classCount\n"
                )

                val text =
                    decodeCtc(
                        values,
                        timeSteps,
                        classCount,
                        report
                    )

                return text

            } finally {

                try {
                    outputs?.close()
                } catch (_: Exception) {
                }

                try {
                    tensor?.close()
                } catch (_: Exception) {
                }
            }

        } finally {

            if (prepared !== crop) {

                try {
                    prepared.recycle()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun decodeCtc(
        values: List<Float>,
        timeSteps: Int,
        classCount: Int,
        report: StringBuilder
    ): String {

        /*
         * Most Paddle CTC dictionaries use:
         *
         * index 0 = blank
         * dictionary characters = indices 1..N
         *
         * Some exports have an extra special class.
         *
         * We therefore handle:
         * - blank = 0
         * - dictionary index = classIndex - 1
         */
        val builder =
            StringBuilder()

        var previousClass =
            -1

        var emitted =
            0

        var bestAverage =
            0f

        for (time in 0 until timeSteps) {

            var bestIndex =
                0

            var bestValue =
                Float.NEGATIVE_INFINITY

            val base =
                time * classCount

            for (classIndex in 0 until classCount) {

                val position =
                    base + classIndex

                if (position >= values.size) {
                    break
                }

                val value =
                    values[position]

                if (value > bestValue) {

                    bestValue =
                        value

                    bestIndex =
                        classIndex
                }
            }

            if (bestValue > Float.NEGATIVE_INFINITY) {

                bestAverage +=
                    bestValue
            }

            /*
             * CTC collapse:
             * repeated same character = one character
             * blank = ignored
             */
            if (
                bestIndex == 0
            ) {

                previousClass =
                    bestIndex

                continue
            }

            if (
                bestIndex ==
                previousClass
            ) {
                continue
            }

            val dictionaryIndex =
                bestIndex - 1

            if (
                dictionaryIndex >= 0 &&
                dictionaryIndex <
                dictionary.size
            ) {

                val symbol =
                    dictionary[
                        dictionaryIndex
                    ]

                builder.append(
                    symbol
                )

                emitted++
            }

            previousClass =
                bestIndex
        }

        if (timeSteps > 0) {

            bestAverage /=
                timeSteps.toFloat()
        }

        val text =
            builder
                .toString()
                .trim()

        report.append(
            "REC decoded chars: $emitted\n"
        )

        report.append(
            "REC average best score: $bestAverage\n"
        )

        LogUtil.info(
            "OCR",
            "REC decoded chars: $emitted, " +
                    "score=$bestAverage, " +
                    "text='$text'"
        )

        return text
    }

    private fun decodeCtcTransposed(
        values: List<Float>,
        timeSteps: Int,
        classCount: Int,
        lineNumber: Int,
        report: StringBuilder
    ): String {

        val builder =
            StringBuilder()

        var previousClass =
            -1

        var emitted =
            0

        for (time in 0 until timeSteps) {

            var bestIndex =
                0

            var bestValue =
                Float.NEGATIVE_INFINITY

            for (classIndex in 0 until classCount) {

                val position =
                    classIndex *
                            timeSteps +
                            time

                if (position >= values.size) {
                    continue
                }

                val value =
                    values[position]

                if (value > bestValue) {

                    bestValue =
                        value

                    bestIndex =
                        classIndex
                }
            }

            if (bestIndex == 0) {

                previousClass =
                    0

                continue
            }

            if (
                bestIndex ==
                previousClass
            ) {
                continue
            }

            val dictionaryIndex =
                bestIndex - 1

            if (
                dictionaryIndex >= 0 &&
                dictionaryIndex <
                dictionary.size
            ) {

                builder.append(
                    dictionary[
                        dictionaryIndex
                    ]
                )

                emitted++
            }

            previousClass =
                bestIndex
        }

        val text =
            builder
                .toString()
                .trim()

        report.append(
            "REC #$lineNumber decoded chars: " +
                    "$emitted\n"
        )

        LogUtil.info(
            "OCR",
            "REC #$lineNumber text='$text'"
        )

        return text
    }

    private fun prepareRecognitionBitmap(
        source: Bitmap
    ): Bitmap {

        /*
         * Keep the original aspect ratio.
         *
         * PP-OCR recognition is not supposed to stretch every text line
         * to 320 px regardless of its original aspect ratio.
         */
        val sourceWidth =
            max(
                1,
                source.width
            )

        val sourceHeight =
            max(
                1,
                source.height
            )

        val targetHeight =
            REC_HEIGHT

        var targetWidth =
            (
                sourceWidth.toFloat() /
                        sourceHeight.toFloat() *
                        targetHeight.toFloat()
            ).toInt()

        targetWidth =
            max(
                1,
                targetWidth
            )

        targetWidth =
            min(
                REC_WIDTH,
                targetWidth
            )

        /*
         * White canvas, because receipt text normally has a light
         * background and this is also what we want around a short line.
         */
        val canvasBitmap =
            Bitmap.createBitmap(
                REC_WIDTH,
                REC_HEIGHT,
                Bitmap.Config.ARGB_8888
            )

        val canvas =
            Canvas(canvasBitmap)

        canvas.drawColor(
            Color.WHITE
        )

        val scaled =
            Bitmap.createScaledBitmap(
                source,
                targetWidth,
                targetHeight,
                true
            )

        val left =
            0

        val top =
            0

        canvas.drawBitmap(
            scaled,
            left.toFloat(),
            top.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )

        if (scaled !== source) {

            try {
                scaled.recycle()
            } catch (_: Exception) {
            }
        }

        return canvasBitmap
    }

    private fun bitmapToNormalizedTensor(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int
    ): FloatArray {

        val resized =
            if (
                source.width == targetWidth &&
                source.height == targetHeight
            ) {

                source

            } else {

                Bitmap.createScaledBitmap(
                    source,
                    targetWidth,
                    targetHeight,
                    true
                )
            }

        val pixels =
            IntArray(
                targetWidth *
                        targetHeight
            )

        resized.getPixels(
            pixels,
            0,
            targetWidth,
            0,
            0,
            targetWidth,
            targetHeight
        )

        val planeSize =
            targetWidth *
                    targetHeight

        val result =
            FloatArray(
                planeSize * 3
            )

        var index =
            0

        for (y in 0 until targetHeight) {

            for (x in 0 until targetWidth) {

                val color =
                    pixels[index]

                val r =
                    (
                        (color shr 16) and 0xFF
                    ) / 255f

                val g =
                    (
                        (color shr 8) and 0xFF
                    ) / 255f

                val b =
                    (
                        color and 0xFF
                    ) / 255f

                result[index] =
                    (r - 0.485f) /
                            0.229f

                result[
                    planeSize + index
                ] =
                    (g - 0.456f) /
                            0.224f

                result[
                    planeSize * 2 + index
                ] =
                    (b - 0.406f) /
                            0.225f

                index++
            }
        }

        if (resized !== source) {

            try {
                resized.recycle()
            } catch (_: Exception) {
            }
        }

        return result
    }

    private fun flattenNumeric(
        value: Any?,
        output: MutableList<Float>
    ) {

        when (value) {

            is FloatArray -> {

                for (item in value) {
                    output.add(item)
                }
            }

            is DoubleArray -> {

                for (item in value) {
                    output.add(item.toFloat())
                }
            }

            is IntArray -> {

                for (item in value) {
                    output.add(item.toFloat())
                }
            }

            is LongArray -> {

                for (item in value) {
                    output.add(item.toFloat())
                }
            }

            is ShortArray -> {

                for (item in value) {
                    output.add(item.toFloat())
                }
            }

            is Array<*> -> {

                for (item in value) {

                    flattenNumeric(
                        item,
                        output
                    )
                }
            }

            is List<*> -> {

                for (item in value) {

                    flattenNumeric(
                        item,
                        output
                    )
                }
            }

            is Number -> {

                output.add(
                    value.toFloat()
                )
            }
        }
    }

    private fun roundUp32(
        value: Int
    ): Int {

        if (value <= 32) {
            return 32
        }

        return (
            (value + 31) / 32
        ) * 32
    }

    private fun exceptionText(
        e: Exception
    ): String {

        val builder =
            StringBuilder()

        builder.append(
            e.javaClass.name
        )

        builder.append(
            ": "
        )

        builder.append(
            e.message ?: ""
        )

        builder.append(
            "\n"
        )

        for (
            element in e.stackTrace
        ) {

            builder.append(
                "\tat "
            )

            builder.append(
                element.toString()
            )

            builder.append(
                "\n"
            )
        }

        var cause =
            e.cause

        var depth =
            0

        while (
            cause != null &&
            depth < 5
        ) {

            builder.append(
                "\nCaused by: "
            )

            builder.append(
                cause.javaClass.name
            )

            builder.append(
                ": "
            )

            builder.append(
                cause.message ?: ""
            )

            builder.append(
                "\n"
            )

            for (
                element in cause.stackTrace
            ) {

                builder.append(
                    "\tat "
                )

                builder.append(
                    element.toString()
                )

                builder.append(
                    "\n"
                )
            }

            cause =
                cause.cause

            depth++
        }

        return builder.toString()
    }

    private data class TextBox(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
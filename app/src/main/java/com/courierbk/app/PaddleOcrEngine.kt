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

/**
 * Local PP-OCRv5 engine.
 * recognize() deliberately returns ONLY OCR text.
 * Diagnostics are written to LogUtil and are not mixed into the OCR result.
 */
class PaddleOcrEngine(private val context: Context) {

    companion object {
        private const val DET_MODEL = "ocr_models/ch_PP-OCRv5_det_mobile.onnx"
        private const val REC_MODEL = "ocr_models/eslav_PP-OCRv5_rec_mobile.onnx"
        private const val DICT = "ocr_models/ppocrv5_eslav_dict.txt"
        private const val REC_HEIGHT = 48
        private const val REC_WIDTH = 320
        private const val DET_MAX_SIDE = 960
        private const val DET_THRESHOLD = 0.25f
        private const val MIN_COMPONENT_PIXELS = 12
        private const val MIN_BOX_WIDTH = 4
        private const val MIN_BOX_HEIGHT = 4
        private const val MAX_BOX_IMAGE_RATIO = 0.90f
    }

    private val environment = OrtEnvironment.getEnvironment()
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var dictionary: List<String> = emptyList()

    init {
        try {
            LogUtil.info("OCR", "Инициализация PaddleOcrEngine")
            loadDictionary()
            loadModels()
            LogUtil.info("OCR", "PaddleOcrEngine инициализирован")
        } catch (e: Exception) {
            LogUtil.error("OCR", "Ошибка инициализации:\n${exceptionText(e)}")
        }
    }

    private fun loadDictionary() {
        try {
            val result = ArrayList<String>()
            context.assets.open(DICT).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isNotEmpty()) result.add(line)
                    }
                }
            }
            dictionary = result
            LogUtil.info("OCR", "Dictionary загружен. Символов: ${dictionary.size}")
        } catch (e: Exception) {
            dictionary = emptyList()
            LogUtil.error("OCR", "Ошибка загрузки dictionary:\n${exceptionText(e)}")
        }
    }

    private fun loadModels() {
        try {
            val detBytes = context.assets.open(DET_MODEL).use { it.readBytes() }
            val recBytes = context.assets.open(REC_MODEL).use { it.readBytes() }
            LogUtil.info("OCR", "DET модель загружена. Размер: ${detBytes.size} bytes")
            LogUtil.info("OCR", "REC модель загружена. Размер: ${recBytes.size} bytes")
            detSession = environment.createSession(detBytes, OrtSession.SessionOptions())
            recSession = environment.createSession(recBytes, OrtSession.SessionOptions())
            logSessionInfo("DET", detSession)
            logSessionInfo("REC", recSession)
        } catch (e: Exception) {
            LogUtil.error("OCR", "Ошибка загрузки ONNX моделей:\n${exceptionText(e)}")
        }
    }

    private fun logSessionInfo(name: String, session: OrtSession?) {
        if (session == null) {
            LogUtil.error("OCR", "$name session = null")
            return
        }
        try {
            LogUtil.info("OCR", "$name input names: ${session.inputNames}")
            LogUtil.info("OCR", "$name output names: ${session.outputNames}")
            for (inputName in session.inputNames) {
                try {
                    val info = session.inputInfo[inputName]?.info
                    if (info is TensorInfo) LogUtil.info("OCR", "$name input '$inputName' shape: ${info.shape.contentToString()}")
                } catch (e: Exception) {
                    LogUtil.error("OCR", "$name input info error:\n${exceptionText(e)}")
                }
            }
            for (outputName in session.outputNames) {
                try {
                    val info = session.outputInfo[outputName]?.info
                    if (info is TensorInfo) LogUtil.info("OCR", "$name output '$outputName' shape: ${info.shape.contentToString()}")
                } catch (e: Exception) {
                    LogUtil.error("OCR", "$name output info error:\n${exceptionText(e)}")
                }
            }
        } catch (e: Exception) {
            LogUtil.error("OCR", "$name session diagnostic error:\n${exceptionText(e)}")
        }
    }

    fun recognize(bitmap: Bitmap): String {
        LogUtil.info("OCR", "Начало OCR. SOURCE: ${bitmap.width} x ${bitmap.height}")
        if (bitmap.width <= 0 || bitmap.height <= 0) return ""
        if (dictionary.isEmpty()) return ""

        val det = detSession ?: return ""
        val rec = recSession ?: return ""

        val source = try {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (e: Exception) {
            LogUtil.error("OCR", "Ошибка копирования SOURCE:\n${exceptionText(e)}")
            return ""
        }

        try {
            val boxes = runDetector(det, source)
            LogUtil.info("OCR", "DET найдено областей: ${boxes.size}")
            if (boxes.isEmpty()) return ""

            val recognizedLines = ArrayList<String>()
            for (index in boxes.indices) {
                val box = boxes[index]
                LogUtil.info("OCR", "BOX #${index + 1}: ${box.left},${box.top} - ${box.right},${box.bottom}")
                val crop = cropBitmap(source, box) ?: continue
                try {
                    val text = runRecognizer(rec, crop, index + 1)
                    if (text.isNotBlank()) {
                        recognizedLines.add(text.trim())
                        LogUtil.info("OCR", "TEXT #${index + 1}: $text")
                    }
                } finally {
                    try { crop.recycle() } catch (_: Exception) {}
                }
            }

            val finalText = recognizedLines.joinToString("\n").trim()
            LogUtil.info("OCR", "OCR завершён. Строк распознано: ${recognizedLines.size}")
            LogUtil.info("OCR", "ИТОГ OCR:\n$finalText")
            return finalText
        } catch (e: Exception) {
            LogUtil.error("OCR", "Ошибка OCR:\n${exceptionText(e)}")
            return ""
        } finally {
            try { source.recycle() } catch (_: Exception) {}
        }
    }

    private fun runDetector(session: OrtSession, source: Bitmap): List<TextBox> {
        val largestSide = max(source.width, source.height)
        val scale = min(1f, DET_MAX_SIDE.toFloat() / largestSide.toFloat())
        var width = max(32, (source.width * scale).toInt())
        var height = max(32, (source.height * scale).toInt())
        width = min(DET_MAX_SIDE, roundUp32(width))
        height = min(DET_MAX_SIDE, roundUp32(height))

        LogUtil.info("OCR", "DET input: ${height} x ${width}")
        val inputName = session.inputNames.iterator().next()
        val data = bitmapToNormalizedTensor(source, width, height)
        val shape = longArrayOf(1L, 3L, height.toLong(), width.toLong())
        var tensor: OnnxTensor? = null
        var outputs: OrtSession.Result? = null

        try {
            tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape)
            outputs = session.run(mapOf(inputName to tensor))
            if (outputs.size() <= 0) throw IllegalStateException("DET не вернул outputs")
            val output = outputs[0]
            val info = output.info
            if (info !is TensorInfo) throw IllegalStateException("DET output не TensorInfo")
            val outputShape = info.shape
            LogUtil.info("OCR", "DET output shape: ${outputShape.contentToString()}")
            val values = ArrayList<Float>()
            flattenNumeric(output.value, values)
            if (outputShape.size < 2) throw IllegalStateException("DET output shape слишком короткий")
            val mapHeight = outputShape[outputShape.size - 2].toInt()
            val mapWidth = outputShape[outputShape.size - 1].toInt()
            val expected = mapHeight * mapWidth
            if (mapHeight <= 0 || mapWidth <= 0 || values.size < expected) throw IllegalStateException("Некорректный DET output")
            return extractTextBoxes(values, mapWidth, mapHeight, source.width, source.height, width, height)
        } finally {
            try { outputs?.close() } catch (_: Exception) {}
            try { tensor?.close() } catch (_: Exception) {}
        }
    }

    private fun extractTextBoxes(values: List<Float>, mapWidth: Int, mapHeight: Int, sourceWidth: Int, sourceHeight: Int, detectorWidth: Int, detectorHeight: Int): List<TextBox> {
        val total = mapWidth * mapHeight
        val binary = BooleanArray(total)
        var positiveCount = 0
        var maxProbability = 0f
        var minProbability = Float.MAX_VALUE

        for (i in 0 until total) {
            val value = values[i]
            if (value > maxProbability) maxProbability = value
            if (value < minProbability) minProbability = value
            if (value >= DET_THRESHOLD) {
                binary[i] = true
                positiveCount++
            }
        }

        LogUtil.info("OCR", "DET probability min/max: $minProbability / $maxProbability")
        LogUtil.info("OCR", "DET positive pixels: $positiveCount / $total")

        if (positiveCount == 0) {
            val fallback = 0.15f
            for (i in 0 until total) binary[i] = values[i] >= fallback
            LogUtil.warning("OCR", "DET при threshold $DET_THRESHOLD пуст. Пробуем $fallback")
        }

        val visited = BooleanArray(total)
        val boxes = ArrayList<TextBox>()
        val queueX = IntArray(total)
        val queueY = IntArray(total)

        for (startY in 0 until mapHeight) {
            for (startX in 0 until mapWidth) {
                val startIndex = startY * mapWidth + startX
                if (!binary[startIndex] || visited[startIndex]) continue

                var queueStart = 0
                var queueEnd = 0
                queueX[queueEnd] = startX
                queueY[queueEnd] = startY
                queueEnd++
                visited[startIndex] = true

                var minX = startX
                var maxX = startX
                var minY = startY
                var maxY = startY
                var pixelCount = 0

                while (queueStart < queueEnd) {
                    val x = queueX[queueStart]
                    val y = queueY[queueStart]
                    queueStart++
                    pixelCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y

                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = x + dx
                        val ny = y + dy
                        if (nx < 0 || nx >= mapWidth || ny < 0 || ny >= mapHeight) continue
                        val ni = ny * mapWidth + nx
                        if (!binary[ni] || visited[ni]) continue
                        visited[ni] = true
                        queueX[queueEnd] = nx
                        queueY[queueEnd] = ny
                        queueEnd++
                    }
                }

                if (pixelCount < MIN_COMPONENT_PIXELS) continue
                val boxWidth = maxX - minX + 1
                val boxHeight = maxY - minY + 1
                if (boxWidth < MIN_BOX_WIDTH || boxHeight < MIN_BOX_HEIGHT) continue
                val imageRatio = boxWidth.toFloat() * boxHeight.toFloat() / (mapWidth.toFloat() * mapHeight.toFloat())
                if (imageRatio > MAX_BOX_IMAGE_RATIO) continue

                val leftDetector = (minX.toFloat() / mapWidth.toFloat() * detectorWidth.toFloat()).toInt()
                val rightDetector = ((maxX + 1).toFloat() / mapWidth.toFloat() * detectorWidth.toFloat()).toInt()
                val topDetector = (minY.toFloat() / mapHeight.toFloat() * detectorHeight.toFloat()).toInt()
                val bottomDetector = ((maxY + 1).toFloat() / mapHeight.toFloat() * detectorHeight.toFloat()).toInt()
                val left = (leftDetector.toFloat() / detectorWidth.toFloat() * sourceWidth.toFloat()).toInt()
                val right = (rightDetector.toFloat() / detectorWidth.toFloat() * sourceWidth.toFloat()).toInt()
                val top = (topDetector.toFloat() / detectorHeight.toFloat() * sourceHeight.toFloat()).toInt()
                val bottom = (bottomDetector.toFloat() / detectorHeight.toFloat() * sourceHeight.toFloat()).toInt()

                val safeLeft = max(0, min(sourceWidth - 1, left))
                val safeTop = max(0, min(sourceHeight - 1, top))
                val safeRight = max(safeLeft + 1, min(sourceWidth, right))
                val safeBottom = max(safeTop + 1, min(sourceHeight, bottom))
                val padX = max(2, (safeRight - safeLeft) / 20)
                val padY = max(2, (safeBottom - safeTop) / 4)

                boxes.add(TextBox(
                    max(0, safeLeft - padX),
                    max(0, safeTop - padY),
                    min(sourceWidth, safeRight + padX),
                    min(sourceHeight, safeBottom + padY)
                ))
            }
        }

        boxes.sortWith(Comparator { first, second ->
            val firstCenter = (first.top + first.bottom) / 2
            val secondCenter = (second.top + second.bottom) / 2
            val firstHeight = max(1, first.bottom - first.top)
            val secondHeight = max(1, second.bottom - second.top)
            val tolerance = max(8, min(firstHeight, secondHeight) / 2)
            if (abs(firstCenter - secondCenter) <= tolerance) first.left.compareTo(second.left)
            else firstCenter.compareTo(secondCenter)
        })

        LogUtil.info("OCR", "DET valid boxes: ${boxes.size}")
        return boxes
    }

    private fun cropBitmap(source: Bitmap, box: TextBox): Bitmap? {
        val width = box.right - box.left
        val height = box.bottom - box.top
        if (width <= 0 || height <= 0) return null
        return try { Bitmap.createBitmap(source, box.left, box.top, width, height) }
        catch (e: Exception) {
            LogUtil.error("OCR", "Ошибка crop: ${exceptionText(e)}")
            null
        }
    }

    private fun runRecognizer(session: OrtSession, crop: Bitmap, lineNumber: Int): String {
        val prepared = prepareRecognitionBitmap(crop)
        try {
            LogUtil.info("OCR", "REC #$lineNumber input: ${prepared.height} x ${prepared.width}")
            val data = bitmapToNormalizedTensor(prepared, prepared.width, prepared.height)
            val shape = longArrayOf(1L, 3L, prepared.height.toLong(), prepared.width.toLong())
            val inputName = session.inputNames.iterator().next()
            var tensor: OnnxTensor? = null
            var outputs: OrtSession.Result? = null
            try {
                tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape)
                outputs = session.run(mapOf(inputName to tensor))
                if (outputs.size() <= 0) throw IllegalStateException("REC не вернул outputs")
                val output = outputs[0]
                val info = output.info
                if (info !is TensorInfo) throw IllegalStateException("REC output не TensorInfo")
                val outputShape = info.shape
                val values = ArrayList<Float>()
                flattenNumeric(output.value, values)
                if (outputShape.size < 2) throw IllegalStateException("REC output shape слишком короткий")

                var classCount = outputShape[outputShape.size - 1].toInt()
                var timeSteps = outputShape[outputShape.size - 2].toInt()
                if (classCount <= 0 || timeSteps <= 0 || classCount * timeSteps > values.size) throw IllegalStateException("Некорректный REC output")

                if (classCount != 519 && timeSteps == 519) {
                    val oldClassCount = classCount
                    classCount = timeSteps
                    timeSteps = oldClassCount
                    return decodeCtcTransposed(values, timeSteps, classCount)
                }
                return decodeCtc(values, timeSteps, classCount)
            } finally {
                try { outputs?.close() } catch (_: Exception) {}
                try { tensor?.close() } catch (_: Exception) {}
            }
        } finally {
            if (prepared !== crop) try { prepared.recycle() } catch (_: Exception) {}
        }
    }

    private fun decodeCtc(values: List<Float>, timeSteps: Int, classCount: Int): String {
        val builder = StringBuilder()
        var previousClass = -1
        var emitted = 0
        var score = 0f

        for (time in 0 until timeSteps) {
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            val base = time * classCount
            for (classIndex in 0 until classCount) {
                val position = base + classIndex
                if (position >= values.size) break
                val value = values[position]
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = classIndex
                }
            }
            if (bestValue > Float.NEGATIVE_INFINITY) score += bestValue
            if (bestIndex == 0) {
                previousClass = 0
                continue
            }
            if (bestIndex == previousClass) continue
            val dictionaryIndex = bestIndex - 1
            if (dictionaryIndex >= 0 && dictionaryIndex < dictionary.size) {
                builder.append(dictionary[dictionaryIndex])
                emitted++
            }
            previousClass = bestIndex
        }

        if (timeSteps > 0) score /= timeSteps.toFloat()
        val text = builder.toString().trim()
        LogUtil.info("OCR", "REC decoded chars: $emitted, score=$score, text='$text'")
        return text
    }

    private fun decodeCtcTransposed(values: List<Float>, timeSteps: Int, classCount: Int): String {
        val builder = StringBuilder()
        var previousClass = -1
        var emitted = 0
        for (time in 0 until timeSteps) {
            var bestIndex = 0
            var bestValue = Float.NEGATIVE_INFINITY
            for (classIndex in 0 until classCount) {
                val position = classIndex * timeSteps + time
                if (position >= values.size) continue
                val value = values[position]
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = classIndex
                }
            }
            if (bestIndex == 0) {
                previousClass = 0
                continue
            }
            if (bestIndex == previousClass) continue
            val dictionaryIndex = bestIndex - 1
            if (dictionaryIndex >= 0 && dictionaryIndex < dictionary.size) {
                builder.append(dictionary[dictionaryIndex])
                emitted++
            }
            previousClass = bestIndex
        }
        val text = builder.toString().trim()
        LogUtil.info("OCR", "REC transposed decoded chars: $emitted, text='$text'")
        return text
    }

    private fun prepareRecognitionBitmap(source: Bitmap): Bitmap {
        val sourceWidth = max(1, source.width)
        val sourceHeight = max(1, source.height)
        var targetWidth = (sourceWidth.toFloat() / sourceHeight.toFloat() * REC_HEIGHT.toFloat()).toInt()
        targetWidth = max(1, min(REC_WIDTH, targetWidth))

        val canvasBitmap = Bitmap.createBitmap(REC_WIDTH, REC_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(Color.WHITE)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, REC_HEIGHT, true)
        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        if (scaled !== source) try { scaled.recycle() } catch (_: Exception) {}
        return canvasBitmap
    }

    private fun bitmapToNormalizedTensor(source: Bitmap, targetWidth: Int, targetHeight: Int): FloatArray {
        val resized = if (source.width == targetWidth && source.height == targetHeight) source
        else Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        val pixels = IntArray(targetWidth * targetHeight)
        resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        val planeSize = targetWidth * targetHeight
        val result = FloatArray(planeSize * 3)
        var index = 0
        for (y in 0 until targetHeight) {
            for (x in 0 until targetWidth) {
                val color = pixels[index]
                val r = ((color shr 16) and 0xFF) / 255f
                val g = ((color shr 8) and 0xFF) / 255f
                val b = (color and 0xFF) / 255f
                result[index] = (r - 0.485f) / 0.229f
                result[planeSize + index] = (g - 0.456f) / 0.224f
                result[planeSize * 2 + index] = (b - 0.406f) / 0.225f
                index++
            }
        }
        if (resized !== source) try { resized.recycle() } catch (_: Exception) {}
        return result
    }

    private fun flattenNumeric(value: Any?, output: MutableList<Float>) {
        when (value) {
            is FloatArray -> for (item in value) output.add(item)
            is DoubleArray -> for (item in value) output.add(item.toFloat())
            is IntArray -> for (item in value) output.add(item.toFloat())
            is LongArray -> for (item in value) output.add(item.toFloat())
            is ShortArray -> for (item in value) output.add(item.toFloat())
            is Array<*> -> for (item in value) flattenNumeric(item, output)
            is List<*> -> for (item in value) flattenNumeric(item, output)
            is Number -> output.add(value.toFloat())
        }
    }

    private fun roundUp32(value: Int): Int {
        if (value <= 32) return 32
        return ((value + 31) / 32) * 32
    }

    private fun exceptionText(e: Throwable): String {
        val b = StringBuilder()
        b.append(e.javaClass.name).append(": ").append(e.message ?: "").append("\n")
        for (element in e.stackTrace) b.append("\tat ").append(element).append("\n")
        var cause = e.cause
        var depth = 0
        while (cause != null && depth < 5) {
            b.append("Caused by: ").append(cause.javaClass.name).append(": ").append(cause.message ?: "").append("\n")
            for (element in cause.stackTrace) b.append("\tat ").append(element).append("\n")
            cause = cause.cause
            depth++
        }
        return b.toString()
    }

    private data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int)
}

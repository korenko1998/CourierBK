package com.courierbk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Arrays

class CameraReceiptActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private lateinit var captureButton: Button

    /*
     * ReceiptFrameView наследуется от View,
     * поэтому здесь должен быть именно ReceiptFrameView,
     * а не FrameLayout.
     */
    private lateinit var frameView: ReceiptFrameView

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val cameraPermissionRequest =
        1001

    companion object {

        private const val IMAGE_WIDTH =
            1920

        private const val IMAGE_HEIGHT =
            1080
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_camera_receipt
        )

        textureView =
            findViewById(
                R.id.cameraTextureView
            )

        captureButton =
            findViewById(
                R.id.captureReceiptButton
            )

        frameView =
            findViewById(
                R.id.receiptFrameView
            )

        captureButton.setOnClickListener {

            takePicture()
        }

        textureView.surfaceTextureListener =
            object :
                TextureView.SurfaceTextureListener {

                override fun onSurfaceTextureAvailable(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {

                    openCamera()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(
                    surface: android.graphics.SurfaceTexture
                ): Boolean {

                    return true
                }

                override fun onSurfaceTextureUpdated(
                    surface: android.graphics.SurfaceTexture
                ) {
                }
            }
    }

    override fun onResume() {

        super.onResume()

        if (
            textureView.isAvailable
        ) {

            openCamera()
        }
    }

    override fun onPause() {

        closeCamera()

        super.onPause()
    }

    private fun openCamera() {

        if (
            cameraDevice != null
        ) {

            return
        }

        if (
            checkSelfPermission(
                Manifest.permission.CAMERA
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                cameraPermissionRequest
            )

            return
        }

        try {

            val cameraManager =
                getSystemService(
                    CAMERA_SERVICE
                ) as CameraManager

            var selectedCameraId:
                String? = null

            for (
                cameraId in
                cameraManager.cameraIdList
            ) {

                val characteristics =
                    cameraManager
                        .getCameraCharacteristics(
                            cameraId
                        )

                val facing =
                    characteristics.get(
                        CameraCharacteristics
                            .LENS_FACING
                    )

                if (
                    facing ==
                    CameraCharacteristics
                        .LENS_FACING_BACK
                ) {

                    selectedCameraId =
                        cameraId

                    break
                }
            }

            if (
                selectedCameraId == null
            ) {

                Toast.makeText(
                    this,
                    "Основная камера не найдена",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            imageReader =
                ImageReader.newInstance(
                    IMAGE_WIDTH,
                    IMAGE_HEIGHT,
                    ImageFormat.JPEG,
                    2
                )

            imageReader
                ?.setOnImageAvailableListener(
                    { reader ->

                        val image =
                            reader.acquireLatestImage()

                        if (
                            image != null
                        ) {

                            processImage(
                                image
                            )
                        }
                    },
                    mainHandler
                )

            cameraManager.openCamera(
                selectedCameraId,
                object :
                    CameraDevice.StateCallback() {

                    override fun onOpened(
                        camera: CameraDevice
                    ) {

                        cameraDevice =
                            camera

                        startPreview()
                    }

                    override fun onDisconnected(
                        camera: CameraDevice
                    ) {

                        camera.close()

                        if (
                            cameraDevice ==
                            camera
                        ) {

                            cameraDevice =
                                null
                        }
                    }

                    override fun onError(
                        camera: CameraDevice,
                        error: Int
                    ) {

                        camera.close()

                        if (
                            cameraDevice ==
                            camera
                        ) {

                            cameraDevice =
                                null
                        }

                        runOnUiThread {

                            Toast.makeText(
                                this@CameraReceiptActivity,
                                "Ошибка камеры: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                mainHandler
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Не удалось открыть камеру: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startPreview() {

        val camera =
            cameraDevice
                ?: return

        val texture =
            textureView.surfaceTexture
                ?: return

        val readerSurface =
            imageReader?.surface
                ?: return

        try {

            texture.setDefaultBufferSize(
                IMAGE_WIDTH,
                IMAGE_HEIGHT
            )

            val previewSurface =
                Surface(
                    texture
                )

            val requestBuilder =
                camera.createCaptureRequest(
                    CameraDevice
                        .TEMPLATE_PREVIEW
                )

            requestBuilder.addTarget(
                previewSurface
            )

            camera.createCaptureSession(
                Arrays.asList(
                    previewSurface,
                    readerSurface
                ),
                object :
                    CameraCaptureSession.StateCallback() {

                    override fun onConfigured(
                        session:
                        CameraCaptureSession
                    ) {

                        if (
                            cameraDevice == null
                        ) {

                            return
                        }

                        captureSession =
                            session

                        try {

                            requestBuilder.set(
                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .CONTROL_AF_MODE,
                                android.hardware
                                    .camera2
                                    .CaptureRequest
                                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )

                            session.setRepeatingRequest(
                                requestBuilder.build(),
                                null,
                                mainHandler
                            )

                        } catch (e: Exception) {

                            runOnUiThread {

                                Toast.makeText(
                                    this@CameraReceiptActivity,
                                    "Ошибка предпросмотра: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    override fun onConfigureFailed(
                        session:
                        CameraCaptureSession
                    ) {

                        runOnUiThread {

                            Toast.makeText(
                                this@CameraReceiptActivity,
                                "Не удалось запустить камеру",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                mainHandler
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "Ошибка запуска камеры: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun takePicture() {

        val camera =
            cameraDevice

        val session =
            captureSession

        val reader =
            imageReader

        if (
            camera == null ||
            session == null ||
            reader == null
        ) {

            Toast.makeText(
                this,
                "Камера ещё не готова",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val captureBuilder =
                camera.createCaptureRequest(
                    CameraDevice
                        .TEMPLATE_STILL_CAPTURE
                )

            captureBuilder.addTarget(
                reader.surface
            )

            captureBuilder.set(
                android.hardware
                    .camera2
                    .CaptureRequest
                    .CONTROL_AF_MODE,
                android.hardware
                    .camera2
                    .CaptureRequest
                    .CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )

            session.capture(
                captureBuilder.build(),
                object :
                    CameraCaptureSession
                    .CaptureCallback() {
                },
                mainHandler
            )

            captureButton.isEnabled =
                false

            Toast.makeText(
                this,
                "Фото чека...",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {

            captureButton.isEnabled =
                true

            Toast.makeText(
                this,
                "Не удалось сфотографировать чек: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun processImage(
        image: Image
    ) {

        try {

            val buffer =
                image
                    .planes[0]
                    .buffer

            val bytes =
                ByteArray(
                    buffer.remaining()
                )

            buffer.get(
                bytes
            )

            image.close()

            val originalBitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )

            if (
                originalBitmap == null
            ) {

                runOnUiThread {

                    captureButton.isEnabled =
                        true

                    Toast.makeText(
                        this,
                        "Не удалось обработать фото",
                        Toast.LENGTH_LONG
                    ).show()
                }

                return
            }

            val rotatedBitmap =
                rotateBitmap(
                    originalBitmap,
                    getCameraRotation()
                )

            if (
                rotatedBitmap !==
                originalBitmap
            ) {

                originalBitmap.recycle()
            }

            val croppedBitmap =
                cropReceipt(
                    rotatedBitmap
                )

            if (
                croppedBitmap !==
                rotatedBitmap
            ) {

                rotatedBitmap.recycle()
            }

            val file =
                File(
                    cacheDir,
                    "receipt_camera_${System.currentTimeMillis()}.jpg"
                )

            FileOutputStream(
                file
            ).use { output ->

                croppedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    output
                )
            }

            croppedBitmap.recycle()

            /*
             * Передаём абсолютный путь к фотографии.
             * OcrActivity сможет загрузить файл напрямую.
             */
            val intent =
                Intent(
                    this@CameraReceiptActivity,
                    OcrActivity::class.java
                )

            intent.putExtra(
                "receipt_image_path",
                file.absolutePath
            )

            startActivity(
                intent
            )

            finish()

        } catch (e: Exception) {

            try {

                image.close()

            } catch (_: Exception) {
            }

            runOnUiThread {

                captureButton.isEnabled =
                    true

                Toast.makeText(
                    this,
                    "Ошибка обработки фото: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCameraRotation():
        Int {

        try {

            val cameraManager =
                getSystemService(
                    CAMERA_SERVICE
                ) as CameraManager

            for (
                cameraId in
                cameraManager.cameraIdList
            ) {

                val characteristics =
                    cameraManager
                        .getCameraCharacteristics(
                            cameraId
                        )

                val facing =
                    characteristics.get(
                        CameraCharacteristics
                            .LENS_FACING
                    )

                if (
                    facing ==
                    CameraCharacteristics
                        .LENS_FACING_BACK
                ) {

                    return characteristics.get(
                        CameraCharacteristics
                            .SENSOR_ORIENTATION
                    ) ?: 0
                }
            }

        } catch (_: Exception) {
        }

        return 0
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotation: Int
    ): Bitmap {

        if (
            rotation == 0
        ) {

            return bitmap
        }

        return try {

            val matrix =
                Matrix()

            matrix.postRotate(
                rotation.toFloat()
            )

            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

        } catch (_: Exception) {

            bitmap
        }
    }

    private fun cropReceipt(
        bitmap: Bitmap
    ): Bitmap {

        return try {

            val width =
                bitmap.width

            val height =
                bitmap.height

            if (
                width <= 0 ||
                height <= 0
            ) {

                return bitmap
            }

            val cropWidth =
                (width * 0.82f)
                    .toInt()

            val cropHeight =
                (height * 0.82f)
                    .toInt()

            val left =
                (
                    (width - cropWidth) / 2
                ).coerceAtLeast(0)

            val top =
                (
                    (height - cropHeight) / 2
                ).coerceAtLeast(0)

            val safeWidth =
                cropWidth.coerceAtMost(
                    width - left
                )

            val safeHeight =
                cropHeight.coerceAtMost(
                    height - top
                )

            if (
                safeWidth <= 0 ||
                safeHeight <= 0
            ) {

                return bitmap
            }

            Bitmap.createBitmap(
                bitmap,
                left,
                top,
                safeWidth,
                safeHeight
            )

        } catch (_: Exception) {

            bitmap
        }
    }

    private fun closeCamera() {

        try {

            captureSession?.close()

        } catch (_: Exception) {
        }

        captureSession =
            null

        try {

            cameraDevice?.close()

        } catch (_: Exception) {
        }

        cameraDevice =
            null

        try {

            imageReader?.close()

        } catch (_: Exception) {
        }

        imageReader =
            null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode !=
            cameraPermissionRequest
        ) {

            return
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            openCamera()

        } else {

            Toast.makeText(
                this,
                "Для фотографирования чека нужен доступ к камере",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
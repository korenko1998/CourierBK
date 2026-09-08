package com.courierbk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ScanReceiptActivity :
    AppCompatActivity() {

    companion object {

        private const val CAMERA_PERMISSION_CODE =
            100

        private const val GALLERY_REQUEST_CODE =
            102
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_scan_receipt
        )

        val cameraButton =
            findViewById<Button>(
                R.id.cameraButton
            )

        val galleryButton =
            findViewById<Button>(
                R.id.galleryButton
            )

        cameraButton.setOnClickListener {
            openCamera()
        }

        galleryButton.setOnClickListener {
            openGallery()
        }
    }

    private fun openCamera() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA
                ),
                CAMERA_PERMISSION_CODE
            )

            return
        }

        startActivity(
            Intent(
                this,
                CameraReceiptActivity::class.java
            )
        )
    }

    private fun openGallery() {

        try {

            val intent =
                Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                )

            intent.type =
                "image/*"

            intent.addCategory(
                Intent.CATEGORY_OPENABLE
            )

            startActivityForResult(
                intent,
                GALLERY_REQUEST_CODE
            )

        } catch (_: Exception) {

            Toast.makeText(
                this,
                "Не удалось открыть галерею",
                Toast.LENGTH_LONG
            ).show()
        }
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
            CAMERA_PERMISSION_CODE
        ) {
            return
        }

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startActivity(
                Intent(
                    this,
                    CameraReceiptActivity::class.java
                )
            )

        } else {

            Toast.makeText(
                this,
                "Для фотографирования чека нужен доступ к камере",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated(
        "Deprecated in Android API"
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode ==
            GALLERY_REQUEST_CODE &&
            resultCode ==
            RESULT_OK
        ) {

            val imageUri =
                data?.data

            if (
                imageUri != null
            ) {

                openOcrScreen(
                    imageUri
                )
            }
        }
    }

    private fun openOcrScreen(
        imageUri: Uri
    ) {

        val intent =
            Intent(
                this,
                OcrActivity::class.java
            )

        intent.putExtra(
            "receipt_image_uri",
            imageUri.toString()
        )

        startActivity(
            intent
        )
    }
}
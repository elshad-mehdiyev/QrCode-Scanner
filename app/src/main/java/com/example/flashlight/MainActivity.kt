package com.example.flashlight

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.flashlight.databinding.ActivityMainBinding
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : AppCompatActivity() {
    lateinit var imageReader: ImageReader
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var previewBuilder: CaptureRequest.Builder
    private lateinit var binding: ActivityMainBinding
    private lateinit var session2: CameraCaptureSession
    var width = 1080
    var height = 1920
    private var detector: BarcodeDetector? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val displayMetrics = Resources.getSystem().displayMetrics
        width = displayMetrics.widthPixels
        height = displayMetrics.heightPixels
        detector = BarcodeDetector.Builder(applicationContext)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()
        binding.button.setOnClickListener {
            startCameraPreview(binding.surfaceView)
        }
        binding.flash.setOnClickListener {
            toggleFlashMode(true)
        }
    }



    private fun startCameraPreview(previewView: SurfaceView) {
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList[0]

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                createPreviewSession(previewView)
            }

            override fun onDisconnected(camera: CameraDevice) {
                // Handle camera disconnection
            }

            override fun onError(camera: CameraDevice, error: Int) {
                // Handle camera error
            }
        }, null)
    }
    private fun createPreviewSession(previewView: SurfaceView) {
        val surfaceHolder = previewView.holder
        surfaceHolder.setKeepScreenOn(true)
        val surface = surfaceHolder.surface
        imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
            val file = File(getExternalFilesDir(null), "image.jpg")
            var output: FileOutputStream? = null
                try {
                    output = FileOutputStream(file)
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    output.write(bytes)
                    val bitmap = BitmapFactory.decodeFile(file.path)
                    val frame = Frame.Builder().setBitmap(bitmap).build()
                    val barcodes = detector!!.detect(frame)
                    binding.textView2.text = ""
                    for (index in 0 until barcodes.size()) {
                        val code = barcodes.valueAt(index)
                        binding.textView2.text = binding.textView2.text.toString() + code.displayValue
                    }
                    println(bitmap.height)
                } catch (e: IOException) {
                    e.printStackTrace()
                } finally {
                    image.close()
                    if (output != null) {
                        try {
                            output.close()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

        }, null)


        previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewBuilder.addTarget(surface)
        previewBuilder.addTarget(imageReader.surface)

        cameraDevice!!.createCaptureSession(listOf(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                session2 = session
                session2.setRepeatingRequest(previewBuilder.build(), null, null)

            }

            override fun onConfigureFailed(session: CameraCaptureSession) {

            }
        }, null)
    }
    private fun toggleFlashMode(enable: Boolean) {
        try {
                if (enable) {
                    previewBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH
                    )
                    previewBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON
                    )
                } else {
                    previewBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_OFF
                    )
                    previewBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF
                    )
                }
                session2.setRepeatingRequest(previewBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
}
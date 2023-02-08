package com.example.qrcodescanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.qrcodescanner.databinding.ActivityMainBinding
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 *  Using  technology  Camera2  and  Google  Vision  Api.
 *  This  code  for  lower compile  Sdk  versions. (SDK.Int < 31)
 *  If  Compile  Version  newer  then  use  ML kit
 */
class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {
    private lateinit var imageReader: ImageReader
    private var cameraDevice: CameraDevice? = null
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var previewBuilder: CaptureRequest.Builder
    private lateinit var binding: ActivityMainBinding
    private lateinit var session2: CameraCaptureSession
    private var width = 1080
    private var height = 1920
    private var detector: BarcodeDetector? = null
    private var flashBoolean = true
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    private var imageUri: Uri? = null
    private var bitmap: Bitmap? = null
    lateinit var surfaceHolder: SurfaceHolder


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpDetector()
        setUpSurfaceHolder()
        resultLauncherRegister()
        binding.flash.setOnClickListener {
            if (flashBoolean) {
                flashBoolean = false
                enableFlash()
            } else {
                flashBoolean = true
                disableFlash()
            }
        }
        binding.gallery.setOnClickListener {
            getImageFromGallery()
        }
    }

    private fun enableFlash() {

        session2.stopRepeating()

        val captureRequest = cameraDevice?.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        )?.apply { addTarget(surfaceHolder.surface) }

        captureRequest?.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)

        if (captureRequest != null) {
            session2.setRepeatingRequest(captureRequest.build(), null, null)
        }
    }

    private fun disableFlash() {

        session2.stopRepeating()

        val captureRequest = cameraDevice?.createCaptureRequest(
            CameraDevice.TEMPLATE_PREVIEW
        )?.apply { addTarget(surfaceHolder.surface) }

        if (captureRequest != null) {
            session2.setRepeatingRequest(captureRequest.build(), null, null)
        }
    }

    /**
     * Set up  SurfaceHolder  check  surface  for  ready
     */
    private fun setUpSurfaceHolder() {
        val surfaceView = binding.surfaceView
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)
    }

    /**
     * Set up  Barcode Detector  for  QrCode
     */
    private fun setUpDetector() {
        detector = BarcodeDetector.Builder(applicationContext)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build()
    }

    /**
     * Open camera
     */
    private fun startCameraPreview(surfaceHolder: SurfaceHolder) {
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
                createPreviewSession(surfaceHolder)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()

            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
            }
        }, null)
    }

    private fun createPreviewSession(surfaceHolder: SurfaceHolder) {
        surfaceHolder.setKeepScreenOn(true)
        val surface = surfaceHolder.surface
        val displayMetrics = Resources.getSystem().displayMetrics
        width = displayMetrics.widthPixels
        height = displayMetrics.heightPixels
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
                    bitmap = BitmapFactory.decodeFile(file.path)
                    bitmap?.let {
                        handleQrCode(it)
                    }
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
        previewBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )

        previewBuilder.addTarget(surface)
        previewBuilder.addTarget(imageReader.surface)

        cameraDevice!!.createCaptureSession(
            listOf(surface, imageReader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    session2 = session
                    session2.setRepeatingRequest(previewBuilder.build(), null, null)

                }

                override fun onConfigureFailed(session: CameraCaptureSession) {

                }
            },
            null
        )
    }

    /**
     * Decrypt Qr Code
     */
    private fun handleQrCode(bitmap: Bitmap) {
        val frame = Frame.Builder().setBitmap(bitmap).build()
        val barcodes = detector!!.detect(frame)
        binding.scanResult.text = ""
        for (index in 0 until barcodes.size()) {
            val code = barcodes.valueAt(index)
            binding.scanResult.text =
                binding.scanResult.text.toString() + code.displayValue
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startCameraPreview(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        cameraDevice?.close()
    }

    /**
     * Pick  image request for gallery
     */
    private fun getImageFromGallery() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                Snackbar.make(binding.root, "Permission  needed", Snackbar.LENGTH_INDEFINITE)
                    .setAction("apply permission") {
                        // request  permission
                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }.show()
            } else {
                // request  permission
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT

            activityResultLauncher.launch(intent)
//            val intentGallery =
//                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
//            // intent   request
//            activityResultLauncher.launch(intentGallery)
        }
    }

    /**
     *  Get  result  from  gallery
     */
    private fun resultLauncherRegister() {
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
                    flashBoolean = true
                    val intentFromResult = it.data
                    if (intentFromResult != null) {
                        imageUri = intentFromResult.data
                        imageUri?.let { uri ->
                            bitmap = if (Build.VERSION.SDK_INT >= 28) {
                                val source = ImageDecoder.createSource(contentResolver, uri)
                                ImageDecoder.decodeBitmap(source)
                            } else {
                                MediaStore.Images.Media.getBitmap(contentResolver, uri)
                            }
                            bitmap?.let { bitmap -> handleQrCode(bitmap) }
                        }
                    }
                }
            }
        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) {
                    val intent =
                        Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    activityResultLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Permission  denied", Toast.LENGTH_LONG).show()
                }
            }
    }
}
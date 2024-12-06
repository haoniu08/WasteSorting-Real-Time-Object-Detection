package com.programminghut.waste_sorting_assistant

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.programminghut.waste_sorting_assistant.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class MainActivity : AppCompatActivity() {

    lateinit var labels: List<String>
    val paint = Paint()
    lateinit var imageProcessor: ImageProcessor
    lateinit var bitmap: Bitmap
    lateinit var imageView: ImageView
    lateinit var resultTextView: TextView
    lateinit var cameraDevice: CameraDevice
    lateinit var handler: Handler
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var model: SsdMobilenetV11Metadata1

    // Mapping of detected objects to waste categories
    private val wasteCategoryMapping = mapOf(
        // Compost
        "banana" to "compost",
        "apple" to "compost",
        "orange" to "compost",
        "broccoli" to "compost",
        "carrot" to "compost",
        "hot dog" to "compost",
        "sandwich" to "compost",
        "pizza" to "compost",
        "donut" to "compost",
        "cake" to "compost",
        "potted plant" to "compost",

        // Recyclable
        "bottle" to "recycle",
        "wine glass" to "recycle",
        "cup" to "recycle",
        "book" to "recycle",
        "laptop" to "recycle",
        "cell phone" to "recycle",
        "keyboard" to "recycle",
        "mouse" to "recycle",
        "remote" to "recycle",
        "tv" to "recycle",
        "microwave" to "recycle",
        "refrigerator" to "recycle",
        "oven" to "recycle",
        "toaster" to "recycle",
        "sink" to "recycle",
        "bicycle" to "recycle",
        "backpack" to "recycle",
        "suitcase" to "recycle",
        "umbrella" to "recycle",
        "handbag" to "recycle",
        "tie" to "recycle",
        "scissors" to "recycle",
        "vase" to "recycle",
        "clock" to "recycle",

        // Waste
        "fork" to "waste",
        "knife" to "waste",
        "spoon" to "waste",
        "bowl" to "waste",
        "chair" to "waste",
        "couch" to "waste",
        "bed" to "waste",
        "dining table" to "waste",
        "toilet" to "waste",
        "teddy bear" to "waste",
        "hair drier" to "waste",
        "toothbrush" to "waste"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        get_permission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder().add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR)).build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        imageView = findViewById(R.id.imageView)
        resultTextView = findViewById(R.id.resultTextView)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }
            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {}
            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean = false
            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap = textureView.bitmap!!
                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray

                var mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                var highestConfidenceScore = 0f
                var detectedObject = "none"
                var detectedCategory = "waste" // default category

                scores.forEachIndexed { index, score ->
                    if (score > highestConfidenceScore && score > 0.5) {
                        detectedObject = labels[classes[index].toInt()]
                        detectedCategory = wasteCategoryMapping[detectedObject] ?: "waste"
                        highestConfidenceScore = score
                    }
                }

                // Update the TextView with the result
                runOnUiThread {
                    resultTextView.text = "Item: ${detectedObject.uppercase()}\nBin: ${detectedCategory.uppercase()}"
                }

                imageView.setImageBitmap(mutable)
            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                var surfaceTexture = textureView.surfaceTexture
                var surface = Surface(surfaceTexture)

                var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object: CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null, null)
                    }
                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {}
            override fun onError(p0: CameraDevice, p1: Int) {}
        }, handler)
    }

    fun get_permission(){
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
}
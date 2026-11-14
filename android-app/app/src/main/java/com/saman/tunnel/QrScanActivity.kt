package com.saman.tunnel

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class QrScanActivity : Activity(), SurfaceHolder.Callback {
    companion object { const val EXTRA_TEXT = "qr_text"; private const val CAMERA_PERMISSION = 2203 }
    private lateinit var preview: SurfaceView
    private var camera: Camera? = null
    private var resumed = false
    private var surfaceReady = false
    private var finished = false
    private var generation = 0
    private val handler = Handler(Looper.getMainLooper())
    private val decoder = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val ui = UiPrefs(this)
        val dark = ui.theme == "dark" || (ui.theme == "system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        val tk = Tk(dark, ui.palette)
        preview = SurfaceView(this).apply { holder.addCallback(this@QrScanActivity); contentDescription = "QR camera preview" }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(tk.bg)
            addView(TextView(this@QrScanActivity).apply { text = "QR — offline / آفلاین"; textSize = 20f; setTextColor(tk.ink); setPadding(24, 32, 24, 24) })
            addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
    }
    override fun onResume() { super.onResume(); resumed = true; openCamera() }
    override fun onPause() { resumed = false; closeCamera(); super.onPause() }
    override fun onDestroy() { closeCamera(); decoder.shutdownNow(); super.onDestroy() }
    override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true; openCamera() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { closeCamera(); openCamera() }
    override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceReady = false; closeCamera() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
            else fail("Camera permission denied / مجوز دوربین داده نشد")
        }
    }
    private fun openCamera() {
        if (!resumed || !surfaceReady || finished || camera != null || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val id = (0 until Camera.getNumberOfCameras()).firstOrNull {
                val info = Camera.CameraInfo(); Camera.getCameraInfo(it, info); info.facing == Camera.CameraInfo.CAMERA_FACING_BACK
            } ?: throw IllegalStateException()
            val cam = Camera.open(id)
            camera = cam
            val info = Camera.CameraInfo(); Camera.getCameraInfo(id, info)
            val degrees = when (windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            cam.setDisplayOrientation((info.orientation - degrees + 360) % 360)
            val params = cam.parameters
            val sizes = params.supportedPreviewSizes
            val size = sizes.filter { it.width <= 1280 && it.height <= 1280 }.maxByOrNull { it.width * it.height } ?: sizes.minByOrNull { it.width * it.height }!!
            params.setPreviewSize(size.width, size.height)
            params.previewFormat = android.graphics.ImageFormat.NV21
            if (params.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            cam.parameters = params
            cam.setPreviewDisplay(preview.holder)
            cam.startPreview()
            nextFrame()
        } catch (_: Exception) { closeCamera(); fail("Camera unavailable / دوربین در دسترس نیست") }
    }
    private fun nextFrame() {
        val cam = camera ?: return
        val token = generation
        try { cam.setOneShotPreviewCallback { bytes, source ->
            if (!resumed || token != generation || bytes == null) return@setOneShotPreviewCallback
            val size = source.parameters.previewSize
            val image = bytes.copyOf(size.width * size.height)
            decoder.submit {
                val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)) }
                val text = try {
                    val luminance = PlanarYUVLuminanceSource(image, size.width, size.height, 0, 0, size.width, size.height, false)
                    reader.decodeWithState(BinaryBitmap(HybridBinarizer(luminance))).text
                } catch (_: Exception) { null } finally { reader.reset() }
                handler.post {
                    if (token == generation && resumed && !finished) {
                        if (text != null) {
                            finished = true; closeCamera()
                            setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text)); finish()
                        } else handler.postDelayed({ if (token == generation) nextFrame() }, 150)
                    }
                }
            }
        } } catch (_: Exception) { closeCamera(); fail("Camera preview failed / خطای پیش‌نمایش دوربین") }
    }
    private fun closeCamera() {
        generation++
        handler.removeCallbacksAndMessages(null)
        camera?.let { cam -> runCatching { cam.setPreviewCallback(null); cam.stopPreview() }; runCatching { cam.release() } }
        camera = null
    }
    private fun fail(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); setResult(RESULT_CANCELED); finish() }
}

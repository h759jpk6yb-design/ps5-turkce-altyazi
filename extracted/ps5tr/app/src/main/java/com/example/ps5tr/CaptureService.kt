package com.example.ps5tr

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "ps5tr_capture"
        private const val NOTIFICATION_ID = 42
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var subtitleView: TextView? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val processing = AtomicBoolean(false)
    private var lastSource = ""
    private var lastTranslated = ""
    private var lastProcessedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java) else @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
        if (resultCode != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            startCapture()
            showOverlay()
        } catch (e: Exception) {
            updateOverlay("Ekran yakalama başlatılamadı: ${e.message ?: "bilinmeyen hata"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(640)
        val height = metrics.heightPixels.coerceAtLeast(360)
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = try { imageReader.acquireLatestImage() } catch (_: Exception) { null } ?: return@setOnImageAvailableListener
            val now = System.currentTimeMillis()
            if (now - lastProcessedAt < 700 || !processing.compareAndSet(false, true)) { image.close(); return@setOnImageAvailableListener }
            lastProcessedAt = now
            worker.execute {
                try { processImage(image) } catch (_: Exception) { } finally { try { image.close() } catch (_: Exception) {}; processing.set(false) }
            }
        }, mainHandler)
        virtualDisplay = projection?.createVirtualDisplay("PS5TR-Capture", width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, mainHandler)
    }

    private fun processImage(image: Image) {
        val bitmap = imageToBitmap(image) ?: return
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input).addOnSuccessListener { result ->
            // Bias toward the lower third where most game subtitles appear.
            val lines = result.textBlocks.flatMap { it.lines }.filter { line ->
                line.boundingBox?.centerY()?.let { it > bitmap.height * 0.52 } == true
            }
            val text = lines.joinToString(" ") { it.text }.trim().replace(Regex("\\s+"), " ")
            if (text.length >= 3 && text != lastSource && text != lastTranslated) {
                lastSource = text
                worker.execute {
                    val translated = translate(text)
                    if (!translated.isNullOrBlank()) {
                        lastTranslated = translated
                        updateOverlay(translated)
                    }
                }
            }
            bitmap.recycle()
        }.addOnFailureListener { bitmap.recycle() }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    // Prototype translator. Replace endpoint or implement your preferred translation provider.
    // MyMemory is a public demo endpoint and has rate/usage limits; do not rely on it for production.
    private fun translate(text: String): String? {
        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=en|tr")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            JSONObject(body).optJSONObject("responseData")?.optString("translatedText")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        subtitleView = TextView(this).apply {
            text = "İngilizce altyazı bekleniyor…"
            textSize = 21f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") (WindowManager.LayoutParams.TYPE_PHONE)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (resources.displayMetrics.heightPixels * 0.08).toInt()
        }
        try { windowManager?.addView(subtitleView, params) } catch (_: Exception) { }
    }

    private fun updateOverlay(text: String) {
        mainHandler.post { subtitleView?.text = text }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "PS5 Türkçe Altyazı", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("PS5 Türkçe Altyazı çalışıyor")
        .setContentText("Ekran yakalama ve altyazı algılama etkin")
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        try { virtualDisplay?.release() } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        try { projection?.stop() } catch (_: Exception) { }
        try { subtitleView?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        recognizer.close()
        worker.shutdownNow()
        super.onDestroy()
    }
}

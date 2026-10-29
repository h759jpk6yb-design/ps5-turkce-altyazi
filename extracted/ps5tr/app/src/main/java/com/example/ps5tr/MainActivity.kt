package com.example.ps5tr

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val captureRequest = 7401
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
        }
        root.addView(TextView(this).apply {
            text = "PS5 Türkçe Altyazı — prototip"
            textSize = 24f
            setPadding(0, 0, 0, 18)
        })
        root.addView(TextView(this).apply {
            text = "Ön koşul: PS Remote Play bu cihazda görüntüyü gösterebilmeli. Android, başka uygulamanın görüntüsünü yakalamak için izin ister."
            textSize = 16f
            setPadding(0, 0, 0, 20)
        })
        status = TextView(this).apply {
            text = "Durum: hazır"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "1 — Altyazı penceresi izni"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else status.text = "Durum: pencere izni verildi"
            }
        })
        root.addView(Button(this).apply {
            text = "2 — Ekran yakalamayı başlat"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Önce altyazı penceresi iznini ver."
                    return@setOnClickListener
                }
                val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Durdur"
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                status.text = "Durum: durduruldu"
            }
        })
        root.addView(TextView(this).apply {
            text = "Not: Bu prototip OCR ve çeviri akışını test eder. Android sürümüne ve üretici kısıtlarına göre Remote Play ekranı yakalama engellenebilir."
            textSize = 13f
            setPadding(0, 24, 0, 0)
        })
        setContentView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @Deprecated("Deprecated by Android; kept for broad device compatibility in this prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(CaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            status.text = "Durum: ekran yakalama başlatıldı"
        } else if (requestCode == captureRequest) status.text = "Ekran yakalama izni reddedildi."
    }
}

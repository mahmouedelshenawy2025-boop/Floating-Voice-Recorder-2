package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File

class FloatingRecorderService : Service() {

    companion object {
        const val ACTION_RECORDING_SAVED = "com.example.floatingvoicerecorder.ACTION_RECORDING_SAVED"
        const val EXTRA_FILE_PATH = "extra_file_path"
        var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        startForegroundNotification()
        createFloatingWidget()
    }

    private fun startForegroundNotification() {
        val channelId = "floating_recorder_channel_v2"
        val channelName = "Floating Voice Recorder"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Floating Recorder Active")
            .setContentText("Overlay controls are visible on screen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // إنشاء الواجهة برمجياً بشكل مباشر لتجنب أي مشكلة في ملفات ה-XML
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val btnRecord = Button(this).apply {
            text = "🔴 Record"
            setTextColor(Color.WHITE)
        }

        val btnStop = Button(this).apply {
            text = "❌ Close"
            setTextColor(Color.WHITE)
        }

        container.addView(btnRecord)
        container.addView(btnStop)
        floatingView = container

        btnRecord.setOnClickListener {
            if (!isRecording) {
                if (startAudioRecording()) {
                    btnRecord.text = "⏹ Stop"
                }
            } else {
                stopAudioRecording()
                btnRecord.text = "🔴 Record"
            }
        }

        btnStop.setOnClickListener {
            stopAudioRecording()
            stopSelf()
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error showing overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAudioRecording(): Boolean {
        return try {
            val file = File(getExternalFilesDir(null), "rec_${System.currentTimeMillis()}.3gp")
            outputFile = file.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile)
                prepare()
                start()
            }
            isRecording = true
            Toast.makeText(this, "Recording started!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Record error: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun stopAudioRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false

                val intent = Intent(ACTION_RECORDING_SAVED).apply {
                    putExtra(EXTRA_FILE_PATH, outputFile)
                }
                sendBroadcast(intent)

                Toast.makeText(this, "Saved: $outputFile", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopAudioRecording()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}

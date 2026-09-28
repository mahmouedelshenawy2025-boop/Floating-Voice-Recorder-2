package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var outputFile: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        setupFloatingWindow()
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "floating_recorder_channel"
        val channelName = "Voice Recorder Service"

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
            .setContentTitle("Floating Voice Recorder")
            .setContentText("Service is running in background...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun setupFloatingWindow() {
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        try {
            val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            floatingView = inflater.inflate(R.layout.layout_floating_control, null)

            val btnRecord = floatingView?.findViewById<Button>(R.id.btn_record)
            val btnFinish = floatingView?.findViewById<Button>(R.id.btn_finish)

            btnRecord?.setOnClickListener {
                if (!isRecording) {
                    startAudioRecording()
                    btnRecord.text = "Recording..."
                } else {
                    stopAudioRecording()
                    btnRecord.text = "Record"
                }
            }

            btnFinish?.setOnClickListener {
                stopAudioRecording()
                stopSelf()
            }

            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to display overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startAudioRecording() {
        val file = File(getExternalFilesDir(null), "recording_${System.currentTimeMillis()}.3gp")
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
            try {
                prepare()
                start()
                isRecording = true
                Toast.makeText(applicationContext, "Recording started", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(applicationContext, "Record failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopAudioRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                Toast.makeText(applicationContext, "Recording saved: $outputFile", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioRecording()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}

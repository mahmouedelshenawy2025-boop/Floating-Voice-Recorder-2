package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
    private var mediaPlayer: MediaPlayer? = null

    private var isRecording = false
    private var isPaused = false
    private var isPlayingPreview = false

    private var tempFile: File? = null
    private var secondsElapsed = 0
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    private lateinit var tvTimer: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnPause: Button
    private lateinit var btnPlayPreview: Button
    private lateinit var btnSave: Button
    private lateinit var btnDiscard: Button

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
            .setContentTitle("مسجل الصوت العائم")
            .setContentText("الفقاعة نشطة على الشاشة")
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(15, 10, 15, 10)
            gravity = Gravity.CENTER_VERTICAL
            
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#DD222222"))
                cornerRadius = 60f
                setStroke(2, Color.parseColor("#44FFFFFF"))
            }
            background = backgroundDrawable
        }

        tvTimer = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(10, 0, 10, 0)
        }

        // الأزرار الرئيسية
        btnRecord = Button(this).apply {
            text = "🔴"
            textSize = 16f
            setBackgroundColor(Color.TRANSPARENT)
        }

        btnPause = Button(this).apply {
            text = "⏸"
            textSize = 14f
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        // أزرار المعاينة والتسجيل
        btnPlayPreview = Button(this).apply {
            text = "▶ المعاينة"
            textSize = 12f
            setTextColor(Color.GREEN)
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        btnSave = Button(this).apply {
            text = "💾"
            textSize = 14f
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        btnDiscard = Button(this).apply {
            text = "🗑"
            textSize = 14f
            setBackgroundColor(Color.TRANSPARENT)
            visibility = View.GONE
        }

        val btnClose = Button(this).apply {
            text = "✕"
            textSize = 14f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.LTGRAY)
        }

        container.addView(btnRecord)
        container.addView(btnPause)
        container.addView(tvTimer)
        container.addView(btnPlayPreview)
        container.addView(btnSave)
        container.addView(btnDiscard)
        container.addView(btnClose)

        floatingView = container

        // أحداث الضغط
        btnRecord.setOnClickListener {
            if (!isRecording) {
                if (startAudioRecording()) {
                    btnRecord.text = "⏹"
                    btnPause.visibility = View.VISIBLE
                    hidePreviewControls()
                    startTimer()
                }
            } else {
                stopAudioRecording()
                btnRecord.text = "🔴"
                btnPause.visibility = View.GONE
                showPreviewControls()
                stopTimer()
            }
        }

        btnPause.setOnClickListener {
            if (isRecording) {
                if (!isPaused) {
                    pauseAudioRecording()
                    btnPause.text = "▶"
                    pauseTimer()
                } else {
                    resumeAudioRecording()
                    btnPause.text = "⏸"
                    resumeTimer()
                }
            }
        }

        btnPlayPreview.setOnClickListener {
            togglePreviewPlayback()
        }

        btnSave.setOnClickListener {
            saveRecordingToPublicFolder()
            resetUI()
        }

        btnDiscard.setOnClickListener {
            discardTempRecording()
            resetUI()
            Toast.makeText(this, "تم حذف التسجيل المؤقت", Toast.LENGTH_SHORT).show()
        }

        btnClose.setOnClickListener {
            stopAudioRecording()
            stopPreviewPlayback()
            stopSelf()
        }

        // إمكانية السحب والتحريك
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAudioRecording(): Boolean {
        return try {
            stopPreviewPlayback()
            tempFile = File(cacheDir, "temp_rec_${System.currentTimeMillis()}.3gp")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(tempFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            isPaused = false
            Toast.makeText(this, "جاري التسجيل الان...", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل بدء التسجيل: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun pauseAudioRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                Toast.makeText(this, "تم الإيقاف المؤقت", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeAudioRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                Toast.makeText(this, "تم استئناف التسجيل", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
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
                isPaused = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun togglePreviewPlayback() {
        if (tempFile == null || !tempFile!!.exists()) return

        if (isPlayingPreview) {
            stopPreviewPlayback()
        } else {
            startPreviewPlayback()
        }
    }

    private fun startPreviewPlayback() {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile!!.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPreviewPlayback()
                }
            }
            isPlayingPreview = true
            btnPlayPreview.text = "⏹ إيقاف المعاينة"
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل تشغيل المعاينة", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPreviewPlayback() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingPreview = false
        btnPlayPreview.text = "▶ المعاينة"
    }

    private fun showPreviewControls() {
        btnPlayPreview.visibility = View.VISIBLE
        btnSave.visibility = View.VISIBLE
        btnDiscard.visibility = View.VISIBLE
    }

    private fun hidePreviewControls() {
        btnPlayPreview.visibility = View.GONE
        btnSave.visibility = View.GONE
        btnDiscard.visibility = View.GONE
    }

    private fun saveRecordingToPublicFolder() {
        if (tempFile == null || !tempFile!!.exists()) return

        stopPreviewPlayback()
        val fileName = "REC_${System.currentTimeMillis()}.3gp"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/3gpp")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/FloatingRecordings")
            }

            val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    tempFile!!.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(this, "تم الحفظ بنجاح في Music/FloatingRecordings", Toast.LENGTH_LONG).show()
            }
        } else {
            val musicFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "FloatingRecordings")
            if (!musicFolder.exists()) musicFolder.mkdirs()

            val destFile = File(musicFolder, fileName)
            tempFile!!.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "تم الحفظ بنجاح!", Toast.LENGTH_LONG).show()
        }

        discardTempRecording()
    }

    private fun discardTempRecording() {
        stopPreviewPlayback()
        tempFile?.delete()
        tempFile = null
    }

    private fun resetUI() {
        hidePreviewControls()
        stopTimer()
        btnRecord.text = "🔴"
        btnPause.visibility = View.GONE
    }

    private fun startTimer() {
        secondsElapsed = 0
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isPaused) {
                    val mins = secondsElapsed / 60
                    val secs = secondsElapsed % 60
                    tvTimer.text = String.format("%02d:%02d", mins, secs)
                    secondsElapsed++
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun pauseTimer() {
        // يتم الاحتفاظ بالقيمة كما هي
    }

    private fun resumeTimer() {
        // يستأنف الحساب تلقائياً في Runnable
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        tvTimer.text = "00:00"
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopAudioRecording()
        stopPreviewPlayback()
        discardTempRecording()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}

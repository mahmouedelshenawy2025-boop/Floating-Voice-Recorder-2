package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File

class FloatingRecorderService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null

    private var isRecording: Boolean = false
    private var isPaused: Boolean = false
    private var isPlaying: Boolean = false
    private var audioFilePath: String = ""

    private var btnRecord: ImageButton? = null
    private var btnPause: ImageButton? = null
    private var btnPlay: ImageButton? = null
    private var btnSave: ImageButton? = null
    private var btnClose: ImageButton? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        createFloatingWidget()
    }

    private fun startForegroundService() {
        val channelId = "floating_recorder_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "مسجل الصوت العائم", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("المسجل العائم")
            .setContentText("الفقاعة متوفرة على الشاشة")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun createFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f
            setColor(Color.parseColor("#CC000000"))
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = shape
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER
        }

        btnRecord = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }

        btnPause = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
            visibility = View.GONE
        }

        btnPlay = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.CYAN, PorterDuff.Mode.SRC_IN)
            visibility = View.GONE
        }

        btnSave = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_save)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
            visibility = View.GONE
        }

        btnClose = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
        }

        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(8, 0, 8, 0)
        }

        layout.addView(btnRecord, buttonParams)
        layout.addView(btnPause, buttonParams)
        layout.addView(btnPlay, buttonParams)
        layout.addView(btnSave, buttonParams)
        layout.addView(btnClose, buttonParams)
        floatingView = layout

        val layoutParamsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutParamsType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager?.addView(floatingView, params)

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
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

        btnRecord?.setOnClickListener {
            if (!isRecording) {
                startRecording()
                btnRecord?.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                btnPause?.visibility = View.VISIBLE
                btnPlay?.visibility = View.GONE
                btnSave?.visibility = View.GONE
            } else {
                stopRecording()
                btnRecord?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                btnPause?.visibility = View.GONE
                btnPlay?.visibility = View.VISIBLE
                btnSave?.visibility = View.VISIBLE
            }
        }

        btnPause?.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
                if (!isPaused) {
                    mediaRecorder?.pause()
                    isPaused = true
                    btnPause?.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    Toast.makeText(this, "موقف مؤقتاً", Toast.LENGTH_SHORT).show()
                } else {
                    mediaRecorder?.resume()
                    isPaused = false
                    btnPause?.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN)
                    Toast.makeText(this, "جاري الاستئناف", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPlay?.setOnClickListener {
            if (!isPlaying) {
                playAudio()
                btnPlay?.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                stopAudio()
                btnPlay?.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        btnSave?.setOnClickListener {
            stopAudio()
            Toast.makeText(this, "تم حفظ الصوت بنجاح", Toast.LENGTH_SHORT).show()
            btnPlay?.visibility = View.GONE
            btnSave?.visibility = View.GONE
        }

        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    private fun startRecording() {
        val outputFile = File(externalCacheDir ?: cacheDir, "rec_${System.currentTimeMillis()}.3gp")
        audioFilePath = outputFile.absolutePath

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            try {
                prepare()
                start()
                isRecording = true
                isPaused = false
                Toast.makeText(this@FloatingRecorderService, "بدأ التسجيل", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@FloatingRecorderService, "خطأ في التسجيل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                isPaused = false
            } catch (e: Exception) { }
        }
    }

    private fun playAudio() {
        if (audioFilePath.isNotEmpty()) {
            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(audioFilePath)
                    prepare()
                    start()
                    isPlaying = true
                    setOnCompletionListener {
                        isPlaying = false
                        btnPlay?.setImageResource(android.R.drawable.ic_media_play)
                    }
                } catch (e: Exception) { }
            }
        }
    }

    private fun stopAudio() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
        stopRecording()
        stopAudio()
    }
}

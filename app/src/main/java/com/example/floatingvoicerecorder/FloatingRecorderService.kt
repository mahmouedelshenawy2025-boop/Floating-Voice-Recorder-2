package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground Service that uses WindowManager to display a floating overlay
 * on top of all applications with 3 recorder control buttons:
 * [Record, Pause, Finish].
 */
class FloatingRecorderService : Service() {

    companion object {
        const val CHANNEL_ID = "floating_recorder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SERVICE = "com.example.floatingvoicerecorder.ACTION_STOP"
        const val ACTION_RECORDING_SAVED = "com.example.floatingvoicerecorder.RECORDING_SAVED"
        const val EXTRA_FILE_PATH = "extra_file_path"

        var isRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // UI elements in the floating bar
    private var btnRecord: MaterialButton? = null
    private var btnPause: MaterialButton? = null
    private var btnFinish: MaterialButton? = null
    private var btnClose: ImageView? = null
    private var tvTimer: TextView? = null
    private var viewStatusDot: View? = null

    // Recording state
    private enum class RecorderState {
        IDLE, RECORDING, PAUSED
    }
    private var currentState = RecorderState.IDLE

    // Audio capture
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    // Timer handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var elapsedSeconds = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (currentState == RecorderState.RECORDING) {
                elapsedSeconds++
                updateTimerDisplay()
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Floating Voice Recorder ready"))
        initFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    /**
     * Initializes the WindowManager overlay view and attaches touch/drag listeners
     * and button click handlers for [Record, Pause, Finish].
     */
    private fun initFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_recorder_overlay, null)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 200
        }

        floatingView?.let { view ->
            // Bind view references
            btnRecord = view.findViewById(R.id.btn_record)
            btnPause = view.findViewById(R.id.btn_pause)
            btnFinish = view.findViewById(R.id.btn_finish)
            btnClose = view.findViewById(R.id.btn_close_overlay)
            tvTimer = view.findViewById(R.id.tv_timer)
            viewStatusDot = view.findViewById(R.id.view_status_dot)

            // Button 1: [Record]
            btnRecord?.setOnClickListener {
                if (currentState == RecorderState.IDLE) {
                    startRecording()
                }
            }

            // Button 2: [Pause]
            btnPause?.setOnClickListener {
                when (currentState) {
                    RecorderState.RECORDING -> pauseRecording()
                    RecorderState.PAUSED -> resumeRecording()
                    RecorderState.IDLE -> { /* No-op */ }
                }
            }

            // Button 3: [Finish]
            btnFinish?.setOnClickListener {
                if (currentState != RecorderState.IDLE) {
                    finishRecording()
                }
            }

            // Close button
            btnClose?.setOnClickListener {
                if (currentState != RecorderState.IDLE) {
                    finishRecording()
                }
                stopSelf()
            }

            // Setup draggable behavior on the floating pill
            setupDraggableOverlay(view)

            // Add the floating view to the WindowManager
            windowManager?.addView(view, layoutParams)
        }
    }

    /**
     * Enables dragging the floating pill across the screen with touch events.
     */
    private fun setupDraggableOverlay(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    isDragging
                }
                else -> false
            }
        }
    }

    /**
     * Action: [Record]
     * Configures and starts audio capture via MediaRecorder.
     */
    private fun startRecording() {
        val storageDir = getExternalFilesDir("VoiceRecordings") ?: filesDir
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentOutputFile = File(storageDir, "REC_$timestamp.m4a")

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentOutputFile?.absolutePath)
                prepare()
                start()
            }

            currentState = RecorderState.RECORDING
            elapsedSeconds = 0L
            updateTimerDisplay()
            mainHandler.postDelayed(timerRunnable, 1000)

            // Update UI state for [Record, Pause, Finish]
            btnRecord?.isEnabled = false
            btnRecord?.alpha = 0.5f

            btnPause?.isEnabled = true
            btnPause?.alpha = 1.0f
            btnPause?.text = getString(R.string.btn_pause)

            btnFinish?.isEnabled = true
            btnFinish?.alpha = 1.0f

            viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_recording)

            updateNotification("Recording voice note...")
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            resetRecorderState()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            Toast.makeText(this, "Recorder error: ${e.message}", Toast.LENGTH_LONG).show()
            resetRecorderState()
        }
    }

    /**
     * Action: [Pause]
     * Pauses the active recording (API 24+).
     */
    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                currentState = RecorderState.PAUSED
                mainHandler.removeCallbacks(timerRunnable)

                btnPause?.text = getString(R.string.btn_resume)
                viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_ready)

                updateNotification("Recording paused (${formatTimer(elapsedSeconds)})")
                Toast.makeText(this, "Recording paused", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, "Pause requires Android 7.0+", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Resumes a paused recording (API 24+).
     */
    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                currentState = RecorderState.RECORDING
                mainHandler.postDelayed(timerRunnable, 1000)

                btnPause?.text = getString(R.string.btn_pause)
                viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_recording)

                updateNotification("Recording voice note...")
                Toast.makeText(this, "Recording resumed", Toast.LENGTH_SHORT).show()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Action: [Finish]
     * Stops the MediaRecorder, flushes output to storage, and resets UI controls.
     */
    private fun finishRecording() {
        mainHandler.removeCallbacks(timerRunnable)

        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val savedPath = currentOutputFile?.absolutePath
        if (currentOutputFile != null && currentOutputFile!!.exists()) {
            Toast.makeText(this, "Recording saved: ${currentOutputFile!!.name}", Toast.LENGTH_LONG).show()

            // Broadcast to MainActivity so recordings list refreshes
            val broadcastIntent = Intent(ACTION_RECORDING_SAVED).apply {
                putExtra(EXTRA_FILE_PATH, savedPath)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)
        }

        resetRecorderState()
        updateNotification("Floating Voice Recorder ready")
    }

    private fun resetRecorderState() {
        currentState = RecorderState.IDLE
        elapsedSeconds = 0L
        updateTimerDisplay()

        btnRecord?.isEnabled = true
        btnRecord?.alpha = 1.0f

        btnPause?.isEnabled = false
        btnPause?.alpha = 0.5f
        btnPause?.text = getString(R.string.btn_pause)

        btnFinish?.isEnabled = false
        btnFinish?.alpha = 0.5f

        viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_ready)
    }

    private fun updateTimerDisplay() {
        tvTimer?.text = formatTimer(elapsedSeconds)
    }

    private fun formatTimer(seconds: Long): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
    }

    // --- Foreground Notification Management ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingRecorderService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Floating Voice Recorder")
            .setContentText(statusText)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close Overlay", pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacks(timerRunnable)

        // Release recorder
        try {
            if (currentState != RecorderState.IDLE) {
                mediaRecorder?.stop()
            }
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        // Remove floating view from WindowManager
        if (floatingView != null && windowManager != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }

        super.onDestroy()
    }
}

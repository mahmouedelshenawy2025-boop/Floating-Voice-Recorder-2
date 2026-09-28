package com.example.floatingvoicerecorder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MainActivity handles:
 * 1. Checking and requesting Overlay (SYSTEM_ALERT_WINDOW) & Audio (RECORD_AUDIO) permissions.
 * 2. Starting and stopping the FloatingRecorderService.
 * 3. Browsing and playing back recorded voice files.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAudioStatus: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var btnRequestOverlay: MaterialButton
    private lateinit var btnRequestAudio: MaterialButton
    private lateinit var btnStartService: MaterialButton
    private lateinit var btnStopService: MaterialButton
    private lateinit var tvEmptyRecordings: TextView
    private lateinit var layoutRecordingsList: LinearLayout
    private lateinit var btnRefreshRecordings: ImageView

    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPath: String? = null

    // ActivityResultLauncher for SYSTEM_ALERT_WINDOW (Draw over other apps)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatuses()
        if (hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Overlay permission is required for the floating bar", Toast.LENGTH_LONG).show()
        }
    }

    // ActivityResultLauncher for RECORD_AUDIO and POST_NOTIFICATIONS
    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        updatePermissionStatuses()
        if (audioGranted) {
            Toast.makeText(this, "Audio recording permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Audio recording permission is required to capture sound", Toast.LENGTH_LONG).show()
        }
    }

    // Broadcast receiver to update recordings when saved from floating overlay
    private val recordingSavedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingRecorderService.ACTION_RECORDING_SAVED) {
                loadSavedRecordings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updatePermissionStatuses()
        updateServiceStatus()
        loadSavedRecordings()

        // Auto-prompt missing permissions on first launch
        checkAndPromptPermissionsOnFirstLaunch()

        // Register receiver for new recordings saved from the floating overlay
        val filter = IntentFilter(FloatingRecorderService.ACTION_RECORDING_SAVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingSavedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordingSavedReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
        updateServiceStatus()
        loadSavedRecordings()
    }

    private fun initViews() {
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvAudioStatus = findViewById(R.id.tv_audio_status)
        tvServiceStatus = findViewById(R.id.tv_service_status)
        btnRequestOverlay = findViewById(R.id.btn_request_overlay)
        btnRequestAudio = findViewById(R.id.btn_request_audio)
        btnStartService = findViewById(R.id.btn_start_service)
        btnStopService = findViewById(R.id.btn_stop_service)
        tvEmptyRecordings = findViewById(R.id.tv_empty_recordings)
        layoutRecordingsList = findViewById(R.id.layout_recordings_list)
        btnRefreshRecordings = findViewById(R.id.btn_refresh_recordings)
    }

    private fun setupListeners() {
        // Request Overlay Permission
        btnRequestOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        // Request Audio Permission
        btnRequestAudio.setOnClickListener {
            requestAudioPermissions()
        }

        // Start Floating Service
        btnStartService.setOnClickListener {
            startFloatingService()
        }

        // Stop Floating Service
        btnStopService.setOnClickListener {
            stopFloatingService()
        }

        // Refresh recordings list
        btnRefreshRecordings.setOnClickListener {
            loadSavedRecordings()
            Toast.makeText(this, "Recordings refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Permission Checks and Requests ---

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun requestAudioPermissions() {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        runtimePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    private fun checkAndPromptPermissionsOnFirstLaunch() {
        if (!hasAudioPermission()) {
            requestAudioPermissions()
        } else if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
    }

    private fun updatePermissionStatuses() {
        // Overlay Status
        if (hasOverlayPermission()) {
            tvOverlayStatus.text = "Status: Granted ✓"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.finish_emerald))
            btnRequestOverlay.isEnabled = false
            btnRequestOverlay.alpha = 0.5f
        } else {
            tvOverlayStatus.text = "Status: Not Granted ✗"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.recording_red))
            btnRequestOverlay.isEnabled = true
            btnRequestOverlay.alpha = 1.0f
        }

        // Audio Status
        if (hasAudioPermission()) {
            tvAudioStatus.text = "Status: Granted ✓"
            tvAudioStatus.setTextColor(ContextCompat.getColor(this, R.color.finish_emerald))
            btnRequestAudio.isEnabled = false
            btnRequestAudio.alpha = 0.5f
        } else {
            tvAudioStatus.text = "Status: Not Granted ✗"
            tvAudioStatus.setTextColor(ContextCompat.getColor(this, R.color.recording_red))
            btnRequestAudio.isEnabled = true
            btnRequestAudio.alpha = 1.0f
        }
    }

    // --- Service Control ---

    private fun startFloatingService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Please grant 'Draw over other apps' permission first", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        if (!hasAudioPermission()) {
            Toast.makeText(this, "Please grant microphone permission first", Toast.LENGTH_LONG).show()
            requestAudioPermissions()
            return
        }

        val serviceIntent = Intent(this, FloatingRecorderService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "Floating Recorder started!", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }

    private fun stopFloatingService() {
        val serviceIntent = Intent(this, FloatingRecorderService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Floating Recorder stopped", Toast.LENGTH_SHORT).show()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        val running = FloatingRecorderService.isRunning
        if (running) {
            tvServiceStatus.text = "● Floating Overlay is Active"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.finish_emerald))
            btnStartService.isEnabled = false
            btnStopService.isEnabled = true
        } else {
            tvServiceStatus.text = "○ Floating Overlay is Inactive"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            btnStartService.isEnabled = true
            btnStopService.isEnabled = false
        }
    }

    // --- Saved Recordings Management ---

    private fun loadSavedRecordings() {
        val storageDir = getExternalFilesDir("VoiceRecordings") ?: filesDir
        val audioFiles = storageDir.listFiles { file ->
            file.extension.equals("m4a", ignoreCase = true) || file.extension.equals("aac", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()

        layoutRecordingsList.removeAllViews()

        if (audioFiles.isEmpty()) {
            tvEmptyRecordings.visibility = View.VISIBLE
            layoutRecordingsList.visibility = View.GONE
        } else {
            tvEmptyRecordings.visibility = View.GONE
            layoutRecordingsList.visibility = View.VISIBLE

            for (file in audioFiles) {
                val itemView = createRecordingItemView(file)
                layoutRecordingsList.addView(itemView)
            }
        }
    }

    private fun createRecordingItemView(file: File): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface_card))
            val marginParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            layoutParams = marginParams
        }

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = file.name
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val dateFormatted = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        val sizeKb = file.length() / 1024
        val metaView = TextView(this).apply {
            text = "$dateFormatted • ${sizeKb}KB"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 12f
        }

        infoLayout.addView(nameView)
        infoLayout.addView(metaView)

        // Play / Pause Button
        val btnPlay = MaterialButton(this).apply {
            text = if (currentlyPlayingPath == file.absolutePath) "Stop" else "Play"
            textSize = 11f
            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = btnParams
            setOnClickListener {
                togglePlayback(file.absolutePath, this)
            }
        }

        // Delete Button
        val btnDelete = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            setPadding(16, 16, 16, 16)
            setOnClickListener {
                if (currentlyPlayingPath == file.absolutePath) {
                    stopPlayback()
                }
                file.delete()
                loadSavedRecordings()
                Toast.makeText(this@MainActivity, "Recording deleted", Toast.LENGTH_SHORT).show()
            }
        }

        card.addView(infoLayout)
        card.addView(btnPlay)
        card.addView(btnDelete)

        return card
    }

    private fun togglePlayback(filePath: String, button: MaterialButton) {
        if (currentlyPlayingPath == filePath) {
            stopPlayback()
            button.text = "Play"
        } else {
            stopPlayback()
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopPlayback()
                        loadSavedRecordings()
                    }
                }
                currentlyPlayingPath = filePath
                button.text = "Stop"
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to play recording", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        currentlyPlayingPath = null
    }

    override fun onDestroy() {
        stopPlayback()
        try {
            unregisterReceiver(recordingSavedReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}

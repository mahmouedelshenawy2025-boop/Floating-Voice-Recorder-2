/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  Mic,
  Pause,
  Square,
  Play,
  Copy,
  Check,
  FileCode,
  Smartphone,
  ShieldCheck,
  Layers,
  Trash2,
  RefreshCw,
  Move,
  X,
  Volume2,
  FolderTree,
  AlertCircle
} from 'lucide-react';

interface AndroidFile {
  path: string;
  name: string;
  type: 'kotlin' | 'gradle' | 'xml' | 'properties';
  content: string;
}

const ANDROID_FILES: AndroidFile[] = [
  {
    path: 'settings.gradle',
    name: 'settings.gradle',
    type: 'gradle',
    content: `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FloatingVoiceRecorder"
include ':app'`,
  },
  {
    path: 'build.gradle',
    name: 'build.gradle (root)',
    type: 'gradle',
    content: `// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}

tasks.register('clean', Delete) {
    delete rootProject.layout.buildDirectory
}`,
  },
  {
    path: 'app/build.gradle',
    name: 'app/build.gradle',
    type: 'gradle',
    content: `plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.example.floatingvoicerecorder'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.floatingvoicerecorder"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
        debug {
            applicationIdSuffix ".debug"
            debuggable true
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = '17'
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.activity:activity-ktx:1.8.2'
    testImplementation 'junit:junit:4.13.2'
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
}`,
  },
  {
    path: 'app/src/main/AndroidManifest.xml',
    name: 'AndroidManifest.xml',
    type: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.floatingvoicerecorder">

    <!-- Permissions required by the application -->
    <!-- Allows displaying the floating overlay over other running apps -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    
    <!-- Allows recording microphone audio -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- Allows running as a persistent foreground service with ongoing notification -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    
    <!-- Required for Android 14+ (API 34+) when foreground service accesses the microphone -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    
    <!-- Required for Android 13+ (API 33+) to post notifications for the foreground service -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Storage permissions for saving/accessing audio files on legacy Android versions -->
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_btn_speak_now"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.FloatingVoiceRecorder">

        <!-- Main Launcher Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Foreground Service for the Floating Overlay -->
        <service
            android:name=".FloatingRecorderService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="microphone" />

    </application>

</manifest>`,
  },
  {
    path: 'app/src/main/java/com/example/floatingvoicerecorder/FloatingRecorderService.kt',
    name: 'FloatingRecorderService.kt',
    type: 'kotlin',
    content: `package com.example.floatingvoicerecorder

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

    // Recording state: IDLE, RECORDING, PAUSED
    private enum class RecorderState { IDLE, RECORDING, PAUSED }
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

            btnClose?.setOnClickListener {
                if (currentState != RecorderState.IDLE) {
                    finishRecording()
                }
                stopSelf()
            }

            setupDraggableOverlay(view)
            windowManager?.addView(view, layoutParams)
        }
    }

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
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> isDragging
                else -> false
            }
        }
    }

    private fun startRecording() {
        val storageDir = getExternalFilesDir("VoiceRecordings") ?: filesDir
        if (!storageDir.exists()) storageDir.mkdirs()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        currentOutputFile = File(storageDir, "REC_$timestamp.m4a")

        try {
            mediaRecorder = MediaRecorder().apply {
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

            btnRecord?.isEnabled = false
            btnPause?.isEnabled = true
            btnPause?.text = getString(R.string.btn_pause)
            btnFinish?.isEnabled = true
            viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_recording)
            updateNotification("Recording voice note...")
        } catch (e: Exception) {
            e.printStackTrace()
            resetRecorderState()
        }
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            currentState = RecorderState.PAUSED
            mainHandler.removeCallbacks(timerRunnable)
            btnPause?.text = getString(R.string.btn_resume)
            viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_ready)
            updateNotification("Recording paused")
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            currentState = RecorderState.RECORDING
            mainHandler.postDelayed(timerRunnable, 1000)
            btnPause?.text = getString(R.string.btn_pause)
            viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_recording)
            updateNotification("Recording voice note...")
        }
    }

    private fun finishRecording() {
        mainHandler.removeCallbacks(timerRunnable)
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder = null
        }

        val savedPath = currentOutputFile?.absolutePath
        if (currentOutputFile != null && currentOutputFile!!.exists()) {
            Toast.makeText(this, "Recording saved: \${currentOutputFile!!.name}", Toast.LENGTH_LONG).show()
            val broadcastIntent = Intent(ACTION_RECORDING_SAVED).apply {
                putExtra(EXTRA_FILE_PATH, savedPath)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)
        }
        resetRecorderState()
    }

    private fun resetRecorderState() {
        currentState = RecorderState.IDLE
        elapsedSeconds = 0L
        updateTimerDisplay()
        btnRecord?.isEnabled = true
        btnPause?.isEnabled = false
        btnPause?.text = getString(R.string.btn_pause)
        btnFinish?.isEnabled = false
        viewStatusDot?.setBackgroundResource(R.drawable.bg_indicator_ready)
    }

    private fun updateTimerDisplay() {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        tvTimer?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacks(timerRunnable)
        try {
            if (currentState != RecorderState.IDLE) mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (floatingView != null && windowManager != null) {
            windowManager?.removeView(floatingView)
        }
        super.onDestroy()
    }
}`,
  },
  {
    path: 'app/src/main/java/com/example/floatingvoicerecorder/MainActivity.kt',
    name: 'MainActivity.kt',
    type: 'kotlin',
    content: `package com.example.floatingvoicerecorder

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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * MainActivity handles:
 * 1. Checking and requesting Overlay (SYSTEM_ALERT_WINDOW) & Audio (RECORD_AUDIO) permissions.
 * 2. Starting and stopping the FloatingRecorderService.
 * 3. Browsing and playing back recorded voice files.
 */
class MainActivity : AppCompatActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatuses()
    }

    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updatePermissionStatuses()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndPromptPermissionsOnFirstLaunch()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndPromptPermissionsOnFirstLaunch() {
        if (!hasAudioPermission()) {
            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            runtimePermissionsLauncher.launch(permissions.toTypedArray())
        } else if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun startFloatingService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAudioPermission()) {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
            return
        }
        val serviceIntent = Intent(this, FloatingRecorderService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopFloatingService() {
        val serviceIntent = Intent(this, FloatingRecorderService::class.java)
        stopService(serviceIntent)
    }
}`,
  },
  {
    path: 'app/src/main/res/layout/floating_recorder_overlay.xml',
    name: 'floating_recorder_overlay.xml',
    type: 'xml',
    content: `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/layout_overlay_root"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_floating_overlay"
    android:elevation="8dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingStart="12dp"
    android:paddingTop="8dp"
    android:paddingEnd="12dp"
    android:paddingBottom="8dp">

    <!-- Drag Handle -->
    <ImageView
        android:layout_width="16dp"
        android:layout_height="16dp"
        android:contentDescription="Drag Handle"
        android:src="@drawable/ic_drag_handle" />

    <!-- Status dot & Timer -->
    <View
        android:id="@+id/view_status_dot"
        android:layout_width="10dp"
        android:layout_height="10dp"
        android:layout_marginStart="8dp"
        android:background="@drawable/bg_indicator_ready" />

    <TextView
        android:id="@+id/tv_timer"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="6dp"
        android:fontFamily="monospace"
        android:text="00:00"
        android:textColor="@color/text_primary"
        android:textSize="14sp"
        android:textStyle="bold" />

    <View
        android:layout_width="1dp"
        android:layout_height="24dp"
        android:layout_marginStart="10dp"
        android:layout_marginEnd="10dp"
        android:background="@color/overlay_border" />

    <!-- Button 1: [Record] -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_record"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:layout_marginEnd="6dp"
        android:backgroundTint="@color/recording_red"
        android:text="@string/btn_record" />

    <!-- Button 2: [Pause] -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_pause"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:layout_marginEnd="6dp"
        android:backgroundTint="@color/pause_amber"
        android:enabled="false"
        android:text="@string/btn_pause" />

    <!-- Button 3: [Finish] -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btn_finish"
        android:layout_width="wrap_content"
        android:layout_height="36dp"
        android:layout_marginEnd="6dp"
        android:backgroundTint="@color/finish_emerald"
        android:enabled="false"
        android:text="@string/btn_finish" />

    <!-- Close button -->
    <ImageView
        android:id="@+id/btn_close_overlay"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:src="@drawable/ic_close" />

</LinearLayout>`,
  },
];

interface SavedRecording {
  id: string;
  name: string;
  duration: number;
  blobUrl: string;
  timestamp: string;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'codebase'>('simulator');
  const [selectedFile, setSelectedFile] = useState<AndroidFile>(ANDROID_FILES[4]); // FloatingRecorderService.kt
  const [copied, setCopied] = useState(false);

  // Android Simulation State
  const [overlayPermission, setOverlayPermission] = useState(true);
  const [audioPermission, setAudioPermission] = useState(true);
  const [serviceRunning, setServiceRunning] = useState(true);

  // Overlay state: 'idle' | 'recording' | 'paused'
  const [overlayState, setOverlayState] = useState<'idle' | 'recording' | 'paused'>('idle');
  const [timerSeconds, setTimerSeconds] = useState(0);

  // Draggable floating bar position
  const [pos, setPos] = useState({ x: 30, y: 140 });
  const [isDragging, setIsDragging] = useState(false);
  const dragStartRef = useRef({ mouseX: 0, mouseY: 0, posX: 0, posY: 0 });

  // Web Audio Recording
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const [savedRecordings, setSavedRecordings] = useState<SavedRecording[]>([
    {
      id: 'rec_1',
      name: 'REC_20260928_121500.m4a',
      duration: 14,
      blobUrl: '',
      timestamp: 'Sep 28, 2026 12:15',
    },
    {
      id: 'rec_2',
      name: 'REC_20260928_114210.m4a',
      duration: 32,
      blobUrl: '',
      timestamp: 'Sep 28, 2026 11:42',
    }
  ]);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement | null>(null);

  // Simulated background app in phone view
  const [simulatedApp, setSimulatedApp] = useState<'home' | 'notes' | 'browser'>('home');

  // Timer tick
  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;
    if (overlayState === 'recording') {
      interval = setInterval(() => {
        setTimerSeconds((prev) => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [overlayState]);

  // Handle Dragging
  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    dragStartRef.current = {
      mouseX: e.clientX,
      mouseY: e.clientY,
      posX: pos.x,
      posY: pos.y,
    };
  };

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      const dx = e.clientX - dragStartRef.current.mouseX;
      const dy = e.clientY - dragStartRef.current.mouseY;
      setPos({
        x: Math.max(10, Math.min(220, dragStartRef.current.posX + dx)),
        y: Math.max(60, Math.min(520, dragStartRef.current.posY + dy)),
      });
    };

    const handleMouseUp = () => {
      setIsDragging(false);
    };

    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    }
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDragging]);

  // Overlay Actions: [Record, Pause, Finish]
  const handleRecordClick = async () => {
    if (!audioPermission) {
      alert("Microphone permission required! Click 'Grant' in MainActivity checklist.");
      return;
    }
    try {
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const mediaRecorder = new MediaRecorder(stream);
        mediaRecorderRef.current = mediaRecorder;
        audioChunksRef.current = [];

        mediaRecorder.ondataavailable = (event) => {
          if (event.data.size > 0) {
            audioChunksRef.current.push(event.data);
          }
        };

        mediaRecorder.onstop = () => {
          const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/webm' });
          const audioUrl = URL.createObjectURL(audioBlob);
          const now = new Date();
          const timestampStr = now.toISOString().replace(/[-:T.]/g, '').slice(0, 15);
          const newRecording: SavedRecording = {
            id: 'rec_' + Date.now(),
            name: `REC_${timestampStr}.m4a`,
            duration: timerSeconds || 5,
            blobUrl: audioUrl,
            timestamp: now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          };
          setSavedRecordings((prev) => [newRecording, ...prev]);
          stream.getTracks().forEach((track) => track.stop());
        };

        mediaRecorder.start();
      }
    } catch {
      // Fallback virtual recording if microphone blocked in iframe
    }
    setTimerSeconds(0);
    setOverlayState('recording');
  };

  const handlePauseClick = () => {
    if (overlayState === 'recording') {
      if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
        try {
          mediaRecorderRef.current.pause();
        } catch {}
      }
      setOverlayState('paused');
    } else if (overlayState === 'paused') {
      if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'paused') {
        try {
          mediaRecorderRef.current.resume();
        } catch {}
      }
      setOverlayState('recording');
    }
  };

  const handleFinishClick = () => {
    if (overlayState !== 'idle') {
      if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
        try {
          mediaRecorderRef.current.stop();
        } catch {}
      } else {
        const now = new Date();
        const timestampStr = now.toISOString().replace(/[-:T.]/g, '').slice(0, 15);
        const newRecording: SavedRecording = {
          id: 'rec_' + Date.now(),
          name: `REC_${timestampStr}.m4a`,
          duration: timerSeconds || 8,
          blobUrl: '',
          timestamp: now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        };
        setSavedRecordings((prev) => [newRecording, ...prev]);
      }
      setOverlayState('idle');
      setTimerSeconds(0);
    }
  };

  const handlePlayToggle = (rec: SavedRecording) => {
    if (playingId === rec.id) {
      if (audioPlayerRef.current) {
        audioPlayerRef.current.pause();
      }
      setPlayingId(null);
    } else {
      if (rec.blobUrl) {
        if (!audioPlayerRef.current) {
          audioPlayerRef.current = new Audio(rec.blobUrl);
        } else {
          audioPlayerRef.current.src = rec.blobUrl;
        }
        audioPlayerRef.current.play();
        audioPlayerRef.current.onended = () => setPlayingId(null);
      }
      setPlayingId(rec.id);
    }
  };

  const copyCode = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-zinc-950 text-zinc-100 font-sans">
      {/* Sidebar Navigation */}
      <aside className="w-80 border-r border-zinc-800 bg-zinc-900/80 backdrop-blur-md flex flex-col shrink-0">
        <div className="p-4 border-b border-zinc-800">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-indigo-500 to-rose-500 flex items-center justify-center shadow-lg shadow-indigo-500/20">
              <Mic className="w-5 h-5 text-white" />
            </div>
            <div>
              <h1 className="font-semibold text-zinc-100 text-sm leading-tight">
                Floating Voice Recorder
              </h1>
              <p className="text-xs text-zinc-400">Native Android (Kotlin / Gradle)</p>
            </div>
          </div>
        </div>

        {/* View Switcher */}
        <div className="p-3 border-b border-zinc-800 flex gap-1.5">
          <button
            onClick={() => setActiveTab('simulator')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium transition ${
              activeTab === 'simulator'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-zinc-800/60 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <Smartphone className="w-4 h-4" />
            OS Simulator
          </button>
          <button
            onClick={() => setActiveTab('codebase')}
            className={`flex-1 flex items-center justify-center gap-2 py-2 px-3 rounded-lg text-xs font-medium transition ${
              activeTab === 'codebase'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'bg-zinc-800/60 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            <FileCode className="w-4 h-4" />
            Android Code
          </button>
        </div>

        {/* File Browser list */}
        <div className="flex-1 overflow-y-auto p-3">
          <div className="text-[11px] font-semibold tracking-wider text-zinc-500 uppercase px-2 mb-2 flex items-center gap-1.5">
            <FolderTree className="w-3.5 h-3.5" />
            Project Structure
          </div>
          <div className="space-y-1">
            {ANDROID_FILES.map((file) => (
              <button
                key={file.path}
                onClick={() => {
                  setSelectedFile(file);
                  setActiveTab('codebase');
                }}
                className={`w-full text-left px-3 py-2 rounded-lg text-xs font-mono transition flex items-center justify-between ${
                  selectedFile.path === file.path && activeTab === 'codebase'
                    ? 'bg-zinc-800 text-indigo-400 font-semibold border-l-2 border-indigo-500'
                    : 'text-zinc-400 hover:bg-zinc-800/50 hover:text-zinc-200'
                }`}
              >
                <div className="truncate">{file.name}</div>
                <span className="text-[10px] uppercase px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-500 border border-zinc-700/50">
                  {file.type}
                </span>
              </button>
            ))}
          </div>

          <div className="mt-6 p-3 rounded-xl bg-zinc-950/60 border border-zinc-800/80 text-xs">
            <div className="font-semibold text-zinc-300 flex items-center gap-1.5 mb-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              Manifest Permissions
            </div>
            <ul className="space-y-1 text-zinc-400 text-[11px]">
              <li className="flex items-center gap-1.5 text-emerald-400 font-mono">
                ✓ SYSTEM_ALERT_WINDOW
              </li>
              <li className="flex items-center gap-1.5 text-emerald-400 font-mono">
                ✓ RECORD_AUDIO
              </li>
              <li className="flex items-center gap-1.5 text-emerald-400 font-mono">
                ✓ FOREGROUND_SERVICE
              </li>
              <li className="flex items-center gap-1.5 text-zinc-500 font-mono">
                ✓ FOREGROUND_SERVICE_MICROPHONE
              </li>
            </ul>
          </div>
        </div>

        {/* Quick Overlay Switch */}
        <div className="p-3 border-t border-zinc-800 bg-zinc-900/90 text-xs">
          <div className="flex items-center justify-between mb-2">
            <span className="text-zinc-400">Foreground Overlay:</span>
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                serviceRunning
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                  : 'bg-zinc-800 text-zinc-500'
              }`}
            >
              {serviceRunning ? 'Active' : 'Stopped'}
            </span>
          </div>
          <button
            onClick={() => setServiceRunning(!serviceRunning)}
            className={`w-full py-2 rounded-lg font-medium text-xs transition ${
              serviceRunning
                ? 'bg-red-500/20 text-red-300 hover:bg-red-500/30 border border-red-500/30'
                : 'bg-indigo-600 hover:bg-indigo-500 text-white'
            }`}
          >
            {serviceRunning ? 'Stop Floating Service' : 'Start Floating Service'}
          </button>
        </div>
      </aside>

      {/* Main Panel Content */}
      <main className="flex-1 flex flex-col overflow-hidden bg-zinc-950">
        {activeTab === 'simulator' ? (
          <div className="flex-1 flex items-center justify-center p-6 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-zinc-900 via-zinc-950 to-black overflow-y-auto">
            {/* Phone Container */}
            <div className="relative w-[360px] h-[720px] rounded-[48px] bg-black p-3.5 shadow-2xl border-4 border-zinc-800 ring-1 ring-zinc-700/50 flex flex-col select-none">
              {/* Dynamic Island / Notch */}
              <div className="absolute top-6 left-1/2 -translate-x-1/2 w-28 h-5 rounded-full bg-zinc-900 z-50 flex items-center justify-center">
                <div className="w-2.5 h-2.5 rounded-full bg-zinc-800 mr-2" />
                <div className="w-2 h-2 rounded-full bg-indigo-950 ring-1 ring-indigo-500/30" />
              </div>

              {/* Screen Area */}
              <div className="relative flex-1 rounded-[38px] bg-zinc-950 overflow-hidden flex flex-col border border-zinc-900">
                {/* Android Status Bar */}
                <div className="h-10 px-6 pt-2 flex items-center justify-between text-[11px] text-zinc-300 font-medium z-40 bg-zinc-950/60 backdrop-blur-sm">
                  <span>12:45</span>
                  <div className="flex items-center gap-2">
                    <span className="text-[10px]">5G</span>
                    <div className="w-5 h-2.5 border border-zinc-400 rounded-sm p-0.5 flex items-center">
                      <div className="h-full w-4 bg-emerald-400 rounded-xs" />
                    </div>
                  </div>
                </div>

                {/* App Background switcher bar inside phone */}
                <div className="px-4 py-2 border-b border-zinc-800/80 bg-zinc-900/90 flex items-center justify-between z-30">
                  <span className="text-[10px] text-zinc-400">Background Context:</span>
                  <div className="flex gap-1 text-[10px]">
                    <button
                      onClick={() => setSimulatedApp('home')}
                      className={`px-2 py-0.5 rounded transition ${
                        simulatedApp === 'home'
                          ? 'bg-zinc-800 text-white font-medium'
                          : 'text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      Main App
                    </button>
                    <button
                      onClick={() => setSimulatedApp('notes')}
                      className={`px-2 py-0.5 rounded transition ${
                        simulatedApp === 'notes'
                          ? 'bg-zinc-800 text-white font-medium'
                          : 'text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      Notes
                    </button>
                    <button
                      onClick={() => setSimulatedApp('browser')}
                      className={`px-2 py-0.5 rounded transition ${
                        simulatedApp === 'browser'
                          ? 'bg-zinc-800 text-white font-medium'
                          : 'text-zinc-500 hover:text-zinc-300'
                      }`}
                    >
                      Browser
                    </button>
                  </div>
                </div>

                {/* Background content under overlay */}
                <div className="flex-1 overflow-y-auto p-4 relative">
                  {simulatedApp === 'home' ? (
                    /* MainActivity.kt simulation */
                    <div className="space-y-4">
                      <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-xl bg-red-500/20 border border-red-500/30 flex items-center justify-center">
                          <Mic className="w-5 h-5 text-red-400" />
                        </div>
                        <div>
                          <h2 className="text-sm font-bold text-zinc-100">Floating Voice Recorder</h2>
                          <p className="text-[11px] text-zinc-400">MainActivity Control Panel</p>
                        </div>
                      </div>

                      {/* Permissions Checklist Card */}
                      <div className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 space-y-2.5">
                        <div className="text-xs font-semibold text-zinc-200">Required Permissions</div>

                        {/* Overlay permission */}
                        <div className="flex items-center justify-between p-2 rounded-xl bg-zinc-950/60 border border-zinc-800/80">
                          <div>
                            <div className="text-xs font-medium text-zinc-200">Draw Over Apps</div>
                            <div
                              className={`text-[10px] ${
                                overlayPermission ? 'text-emerald-400' : 'text-amber-400'
                              }`}
                            >
                              {overlayPermission ? 'Granted ✓' : 'Required ✗'}
                            </div>
                          </div>
                          {!overlayPermission && (
                            <button
                              onClick={() => setOverlayPermission(true)}
                              className="px-2 py-1 rounded bg-indigo-600 text-[10px] font-medium text-white hover:bg-indigo-500"
                            >
                              Grant
                            </button>
                          )}
                        </div>

                        {/* Audio permission */}
                        <div className="flex items-center justify-between p-2 rounded-xl bg-zinc-950/60 border border-zinc-800/80">
                          <div>
                            <div className="text-xs font-medium text-zinc-200">Microphone Audio</div>
                            <div
                              className={`text-[10px] ${
                                audioPermission ? 'text-emerald-400' : 'text-amber-400'
                              }`}
                            >
                              {audioPermission ? 'Granted ✓' : 'Required ✗'}
                            </div>
                          </div>
                          {!audioPermission && (
                            <button
                              onClick={() => setAudioPermission(true)}
                              className="px-2 py-1 rounded bg-indigo-600 text-[10px] font-medium text-white hover:bg-indigo-500"
                            >
                              Grant
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Service Controls Card */}
                      <div className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 space-y-3">
                        <div className="text-xs font-semibold text-zinc-200">Service Control</div>
                        <div className="flex gap-2">
                          <button
                            onClick={() => {
                              if (!overlayPermission || !audioPermission) {
                                alert("Grant both permissions first!");
                                return;
                              }
                              setServiceRunning(true);
                            }}
                            disabled={serviceRunning}
                            className={`flex-1 py-2 px-3 rounded-xl text-xs font-medium transition ${
                              serviceRunning
                                ? 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
                                : 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-lg'
                            }`}
                          >
                            Start Overlay
                          </button>
                          <button
                            onClick={() => setServiceRunning(false)}
                            disabled={!serviceRunning}
                            className={`flex-1 py-2 px-3 rounded-xl text-xs font-medium transition border ${
                              !serviceRunning
                                ? 'border-zinc-800 text-zinc-600 cursor-not-allowed'
                                : 'border-red-500/40 text-red-400 hover:bg-red-500/10'
                            }`}
                          >
                            Stop Overlay
                          </button>
                        </div>
                      </div>

                      {/* Saved Recordings Card */}
                      <div className="p-3.5 rounded-2xl bg-zinc-900 border border-zinc-800 space-y-2.5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-zinc-200">Saved Recordings</span>
                          <span className="text-[10px] text-zinc-500">{savedRecordings.length} files</span>
                        </div>
                        <div className="space-y-1.5 max-h-36 overflow-y-auto">
                          {savedRecordings.map((rec) => (
                            <div
                              key={rec.id}
                              className="p-2 rounded-xl bg-zinc-950/70 border border-zinc-800/80 flex items-center justify-between text-xs"
                            >
                              <div className="truncate mr-2">
                                <div className="font-mono text-[11px] text-zinc-200 truncate">{rec.name}</div>
                                <div className="text-[10px] text-zinc-500">
                                  {rec.timestamp} • {rec.duration}s
                                </div>
                              </div>
                              <button
                                onClick={() => handlePlayToggle(rec)}
                                className={`p-1.5 rounded-lg border transition ${
                                  playingId === rec.id
                                    ? 'bg-emerald-500/20 border-emerald-500/30 text-emerald-400'
                                    : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'
                                }`}
                              >
                                {playingId === rec.id ? (
                                  <Pause className="w-3.5 h-3.5" />
                                ) : (
                                  <Play className="w-3.5 h-3.5" />
                                )}
                              </button>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  ) : simulatedApp === 'notes' ? (
                    <div className="space-y-3">
                      <div className="text-xs font-bold text-amber-300">Quick Notes App</div>
                      <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/20 text-xs text-amber-200/90 leading-relaxed">
                        Meeting notes with client: Discussed mobile widget overlay UX, microphone audio levels, and background sync.
                      </div>
                      <div className="p-3 rounded-xl bg-zinc-900 border border-zinc-800 text-xs text-zinc-400">
                        Notice how the Floating Voice Recorder stays accessible directly over this notes screen!
                      </div>
                    </div>
                  ) : (
                    <div className="space-y-3">
                      <div className="text-xs font-bold text-blue-400">Chrome Browser</div>
                      <div className="p-3 rounded-xl bg-blue-500/10 border border-blue-500/20 text-xs text-blue-200/90 leading-relaxed">
                        Reading article: "Android 14 Foreground Service Microphone types and WindowManager overlay patterns".
                      </div>
                      <div className="p-3 rounded-xl bg-zinc-900 border border-zinc-800 text-xs text-zinc-400">
                        Record voice memos instantly while browsing any website or reading docs.
                      </div>
                    </div>
                  )}
                </div>

                {/* THE FLOATING OVERLAY BAR (Rendered via WindowManager TYPE_APPLICATION_OVERLAY) */}
                {serviceRunning && (
                  <div
                    style={{
                      transform: `translate(${pos.x}px, ${pos.y}px)`,
                      touchAction: 'none',
                    }}
                    className={`absolute z-50 cursor-grab active:cursor-grabbing transition-shadow ${
                      isDragging ? 'shadow-2xl scale-102 ring-2 ring-indigo-500' : ''
                    }`}
                  >
                    {/* Floating Overlay Pill as defined in floating_recorder_overlay.xml */}
                    <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-zinc-900/95 backdrop-blur-md border border-white/20 shadow-2xl shadow-black/80">
                      {/* Drag Handle */}
                      <div
                        onMouseDown={handleMouseDown}
                        className="p-1 -ml-1 text-zinc-400 hover:text-zinc-200 cursor-grab"
                        title="Drag to reposition"
                      >
                        <Move className="w-3.5 h-3.5" />
                      </div>

                      {/* Blinking Dot & Timer */}
                      <div className="flex items-center gap-1.5 mr-1">
                        <div
                          className={`w-2.5 h-2.5 rounded-full transition-all ${
                            overlayState === 'recording'
                              ? 'bg-red-500 animate-ping'
                              : overlayState === 'paused'
                              ? 'bg-amber-400'
                              : 'bg-zinc-600'
                          }`}
                        />
                        <span className="font-mono text-xs font-bold text-white tracking-wider">
                          {formatTime(timerSeconds)}
                        </span>
                      </div>

                      {/* Divider */}
                      <div className="w-[1px] h-5 bg-zinc-700" />

                      {/* Button 1: [Record] */}
                      <button
                        onClick={handleRecordClick}
                        disabled={overlayState !== 'idle'}
                        className={`px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center gap-1 transition ${
                          overlayState === 'idle'
                            ? 'bg-red-600 hover:bg-red-500 text-white shadow-sm'
                            : 'bg-zinc-800 text-zinc-500 cursor-not-allowed opacity-50'
                        }`}
                        title="Start Recording"
                      >
                        <Mic className="w-3 h-3" />
                        Record
                      </button>

                      {/* Button 2: [Pause] */}
                      <button
                        onClick={handlePauseClick}
                        disabled={overlayState === 'idle'}
                        className={`px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center gap-1 transition ${
                          overlayState !== 'idle'
                            ? overlayState === 'paused'
                              ? 'bg-amber-500 text-black'
                              : 'bg-amber-600 hover:bg-amber-500 text-white'
                            : 'bg-zinc-800 text-zinc-500 cursor-not-allowed opacity-50'
                        }`}
                        title="Pause or Resume"
                      >
                        {overlayState === 'paused' ? (
                          <Play className="w-3 h-3" />
                        ) : (
                          <Pause className="w-3 h-3" />
                        )}
                        {overlayState === 'paused' ? 'Resume' : 'Pause'}
                      </button>

                      {/* Button 3: [Finish] */}
                      <button
                        onClick={handleFinishClick}
                        disabled={overlayState === 'idle'}
                        className={`px-2.5 py-1 rounded-full text-[11px] font-semibold flex items-center gap-1 transition ${
                          overlayState !== 'idle'
                            ? 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-sm'
                            : 'bg-zinc-800 text-zinc-500 cursor-not-allowed opacity-50'
                        }`}
                        title="Finish & Save Recording"
                      >
                        <Square className="w-3 h-3" />
                        Finish
                      </button>

                      {/* Close overlay icon */}
                      <button
                        onClick={() => setServiceRunning(false)}
                        className="p-1 text-zinc-400 hover:text-zinc-200 transition ml-0.5"
                        title="Dismiss Overlay"
                      >
                        <X className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Quick Overview Card alongside Phone */}
            <div className="ml-8 max-w-sm hidden lg:block space-y-4 text-xs">
              <div className="p-4 rounded-2xl bg-zinc-900 border border-zinc-800 space-y-3">
                <div className="font-semibold text-zinc-200 text-sm flex items-center gap-2">
                  <Layers className="w-4 h-4 text-indigo-400" />
                  Android Overlay Mechanics
                </div>
                <p className="text-zinc-400 leading-relaxed">
                  This Native Android application implements a persistent <span className="text-zinc-200 font-mono font-medium">ForegroundService</span> that attaches directly to the system <span className="text-zinc-200 font-mono font-medium">WindowManager</span> with <span className="text-zinc-200 font-mono font-medium">TYPE_APPLICATION_OVERLAY</span>.
                </p>
                <div className="space-y-2 pt-2 border-t border-zinc-800/80">
                  <div className="flex items-start gap-2">
                    <span className="w-4 h-4 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center shrink-0 font-bold text-[10px]">1</span>
                    <span className="text-zinc-300">
                      <strong>[Record]</strong> launches MediaRecorder with AAC encoding.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <span className="w-4 h-4 rounded-full bg-amber-500/20 text-amber-400 flex items-center justify-center shrink-0 font-bold text-[10px]">2</span>
                    <span className="text-zinc-300">
                      <strong>[Pause]</strong> halts recording without resetting timestamps.
                    </span>
                  </div>
                  <div className="flex items-start gap-2">
                    <span className="w-4 h-4 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0 font-bold text-[10px]">3</span>
                    <span className="text-zinc-300">
                      <strong>[Finish]</strong> flushes file to app storage and triggers broadcast.
                    </span>
                  </div>
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-zinc-900 border border-zinc-800 space-y-2">
                <div className="font-semibold text-zinc-200 flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 text-amber-400" />
                  First-Launch Permissions Flow
                </div>
                <p className="text-zinc-400 leading-relaxed">
                  <span className="font-mono text-zinc-200">MainActivity</span> verifies <span className="font-mono text-zinc-200">Settings.canDrawOverlays</span> and <span className="font-mono text-zinc-200">RECORD_AUDIO</span>. On first launch, it invokes native Android permission contracts before starting the service.
                </p>
              </div>
            </div>
          </div>
        ) : (
          /* Codebase Browser */
          <div className="flex-1 flex flex-col overflow-hidden">
            {/* Code Header */}
            <div className="p-4 border-b border-zinc-800 bg-zinc-900/60 backdrop-blur-sm flex items-center justify-between">
              <div>
                <div className="font-mono text-sm font-semibold text-zinc-200">{selectedFile.path}</div>
                <div className="text-xs text-zinc-500">Android Studio Ready • {selectedFile.type.toUpperCase()}</div>
              </div>
              <button
                onClick={copyCode}
                className="flex items-center gap-1.5 py-1.5 px-3 rounded-lg bg-zinc-800 hover:bg-zinc-700 text-xs font-medium text-zinc-200 transition"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                {copied ? 'Copied!' : 'Copy Code'}
              </button>
            </div>

            {/* Code Content */}
            <div className="flex-1 overflow-auto p-4 bg-zinc-950 font-mono text-xs text-zinc-300 leading-relaxed">
              <pre className="select-text whitespace-pre overflow-x-auto">{selectedFile.content}</pre>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

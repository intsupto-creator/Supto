package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioInputManager
import com.example.audio.AudioOutputManager
import com.example.audio.LocalVoiceEngine
import com.example.gemini.GeminiLiveClient
import com.example.model.PersonaVibe
import com.example.model.VoiceSettings
import com.example.model.VoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MayraUiState(
    val voiceState: VoiceState = VoiceState.IDLE,
    val amplitude: Float = 0.0f,
    val settings: VoiceSettings = VoiceSettings(),
    val isUsingGeminiLive: Boolean = false,
    val isMuted: Boolean = false,
    val statusDetail: String = "Tap orb to begin conversation",
    val lastRecognizedText: String = ""
)

class MayraVoiceService : Service() {

    companion object {
        private const val TAG = "MayraVoiceService"
        const val CHANNEL_ID = "mayra_voice_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_TOGGLE_MUTE = "com.example.service.ACTION_TOGGLE_MUTE"
        const val ACTION_INTERRUPT = "com.example.service.ACTION_INTERRUPT"

        // Singleton instance access for Compose UI integration
        private var instance: MayraVoiceService? = null
        private val _serviceState = MutableStateFlow(MayraUiState())
        val serviceState: StateFlow<MayraUiState> = _serviceState.asStateFlow()

        fun getInstance(): MayraVoiceService? = instance
    }

    inner class LocalBinder : Binder() {
        fun getService(): MayraVoiceService = this@MayraVoiceService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioInput: AudioInputManager? = null
    private var audioOutput: AudioOutputManager? = null
    private var liveClient: GeminiLiveClient? = null
    private var localVoiceEngine: LocalVoiceEngine? = null

    private var interruptionResetJob: Job? = null
    private var currentSettings = VoiceSettings()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "MayraVoiceService onCreate")

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mayra::VoiceWakeLock").apply {
            setReferenceCounted(false)
        }

        createNotificationChannel()
        initAudioSubsystems()
    }

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

    private fun buildNotification(stateText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, MayraVoiceService::class.java).apply { action = ACTION_TOGGLE_MUTE }
        val pendingMute = PendingIntent.getService(
            this, 1, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MayraVoiceService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isMuted = _serviceState.value.isMuted
        val muteActionTitle = if (isMuted) getString(R.string.action_unmute) else getString(R.string.action_mute)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mayra • Voice Active")
            .setContentText(stateText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(0, muteActionTitle, pendingMute)
            .addAction(0, getString(R.string.action_stop), pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun startInForeground() {
        val notification = buildNotification("Mayra is active and listening...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun updateNotification(stateText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(stateText))
    }

    private fun initAudioSubsystems() {
        audioOutput = AudioOutputManager(
            onPlaybackAmplitude = { amp ->
                if (_serviceState.value.voiceState == VoiceState.SPEAKING) {
                    _serviceState.update { it.copy(amplitude = amp) }
                }
            },
            onPlaybackStateChanged = { isPlaying ->
                if (isPlaying) {
                    audioInput?.isAssistantSpeaking = true
                    _serviceState.update { it.copy(voiceState = VoiceState.SPEAKING, statusDetail = VoiceState.SPEAKING.cueText) }
                    updateNotification("Mayra is speaking...")
                } else {
                    audioInput?.isAssistantSpeaking = false
                    if (_serviceState.value.voiceState == VoiceState.SPEAKING) {
                        _serviceState.update { it.copy(voiceState = VoiceState.LISTENING, statusDetail = VoiceState.LISTENING.cueText) }
                        updateNotification("Mayra is listening...")
                    }
                }
            }
        )

        audioInput = AudioInputManager(
            onPcmChunk = { pcm ->
                liveClient?.sendAudioChunk(pcm)
            },
            onAmplitude = { amp ->
                if (_serviceState.value.voiceState == VoiceState.LISTENING) {
                    _serviceState.update { it.copy(amplitude = amp) }
                }
            },
            onUserInterruptionDetected = {
                handleUserInterruption()
            }
        )

        liveClient = GeminiLiveClient(
            scope = serviceScope,
            onAudioReceived = { pcm ->
                audioOutput?.enqueueAudio(pcm)
            },
            onInterrupted = {
                handleUserInterruption()
            },
            onTurnComplete = {
                audioInput?.isAssistantSpeaking = false
                _serviceState.update { it.copy(voiceState = VoiceState.LISTENING, statusDetail = VoiceState.LISTENING.cueText) }
            },
            onConnectionStateChanged = { state ->
                when (state) {
                    is GeminiLiveClient.ConnectionState.Connected -> {
                        _serviceState.update {
                            it.copy(
                                isUsingGeminiLive = true,
                                voiceState = VoiceState.LISTENING,
                                statusDetail = "Gemini Live connected • Listening..."
                            )
                        }
                        updateNotification("Gemini Live active")
                    }
                    is GeminiLiveClient.ConnectionState.Connecting -> {
                        _serviceState.update {
                            it.copy(voiceState = VoiceState.CONNECTING, statusDetail = "Connecting to Gemini Live...")
                        }
                    }
                    is GeminiLiveClient.ConnectionState.Disconnected -> {
                        _serviceState.update {
                            it.copy(isUsingGeminiLive = false)
                        }
                    }
                    is GeminiLiveClient.ConnectionState.Error -> {
                        Log.w(TAG, "Gemini Live error: ${state.message}")
                        _serviceState.update {
                            it.copy(
                                isUsingGeminiLive = false,
                                statusDetail = "Using Voice Simulation (${state.message})"
                            )
                        }
                    }
                }
            }
        )

        localVoiceEngine = LocalVoiceEngine(
            context = applicationContext,
            scope = serviceScope,
            onSpeechRecognized = { text ->
                handleLocalSpeechRecognized(text)
            },
            onSpeakingStarted = {
                audioInput?.isAssistantSpeaking = true
                _serviceState.update { it.copy(voiceState = VoiceState.SPEAKING, statusDetail = VoiceState.SPEAKING.cueText) }
            },
            onSpeakingFinished = {
                audioInput?.isAssistantSpeaking = false
                _serviceState.update { it.copy(voiceState = VoiceState.LISTENING, statusDetail = VoiceState.LISTENING.cueText) }
                // Continue listening in zero-touch loop
                if (!_serviceState.value.isUsingGeminiLive && _serviceState.value.voiceState != VoiceState.MUTED) {
                    localVoiceEngine?.startListening()
                }
            },
            onSimulatedAmplitude = { amp ->
                _serviceState.update { it.copy(amplitude = amp) }
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSession()
            ACTION_STOP -> stopSession()
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_INTERRUPT -> handleUserInterruption()
        }
        return START_STICKY
    }

    fun startSession(apiKey: String = "", settings: VoiceSettings = currentSettings) {
        currentSettings = settings
        wakeLock?.acquire(2 * 60 * 60 * 1000L) // 2 hours max
        startInForeground()

        audioOutput?.start()
        val recordStarted = audioInput?.start() ?: false
        if (!recordStarted) {
            Log.w(TAG, "Record permission or init failed")
        }

        val effectiveApiKey = apiKey.ifBlank { settings.customApiKey }
        if (effectiveApiKey.isNotBlank() && effectiveApiKey != "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Starting Gemini Live WebSocket with API key")
            liveClient?.connect(effectiveApiKey, settings)
        } else {
            Log.d(TAG, "No valid live API key; activating Local Voice Engine fallback")
            _serviceState.update {
                it.copy(
                    isUsingGeminiLive = false,
                    voiceState = VoiceState.LISTENING,
                    statusDetail = "Mayra is listening • Speak to her!"
                )
            }
            localVoiceEngine?.startListening()
        }
    }

    fun stopSession() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        audioInput?.stop()
        audioOutput?.stop()
        liveClient?.disconnect()
        localVoiceEngine?.interrupt()
        localVoiceEngine?.stopListening()

        _serviceState.update {
            it.copy(
                voiceState = VoiceState.IDLE,
                amplitude = 0.0f,
                statusDetail = "Mayra is resting • Tap orb to awaken"
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun toggleMute() {
        val newMuted = !_serviceState.value.isMuted
        audioInput?.setMuted(newMuted)
        _serviceState.update {
            it.copy(
                isMuted = newMuted,
                voiceState = if (newMuted) VoiceState.MUTED else VoiceState.LISTENING,
                statusDetail = if (newMuted) VoiceState.MUTED.cueText else VoiceState.LISTENING.cueText
            )
        }
        updateNotification(if (newMuted) "Microphone muted" else "Mayra is listening...")
    }

    fun handleUserInterruption() {
        Log.d(TAG, "User interrupted Mayra")
        audioOutput?.interrupt()
        localVoiceEngine?.interrupt()
        liveClient?.sendInterruptionSignal()

        _serviceState.update {
            it.copy(
                voiceState = VoiceState.INTERRUPTED,
                statusDetail = "Interrupted! Listening to you..."
            )
        }

        interruptionResetJob?.cancel()
        interruptionResetJob = serviceScope.launch {
            delay(800)
            if (_serviceState.value.voiceState == VoiceState.INTERRUPTED) {
                _serviceState.update {
                    it.copy(
                        voiceState = VoiceState.LISTENING,
                        statusDetail = VoiceState.LISTENING.cueText
                    )
                }
            }
        }
    }

    fun updateSettings(settings: VoiceSettings, apiKey: String) {
        currentSettings = settings
        audioInput?.interruptionThreshold = settings.interruptionThreshold
        _serviceState.update { it.copy(settings = settings) }

        if (liveClient?.isLive() == true) {
            // Re-establish setup with updated vibe/voice if connected
            liveClient?.connect(apiKey.ifBlank { settings.customApiKey }, settings)
        }
    }

    private fun handleLocalSpeechRecognized(text: String) {
        _serviceState.update {
            it.copy(
                voiceState = VoiceState.THINKING,
                statusDetail = "Thinking of something witty...",
                lastRecognizedText = text
            )
        }

        serviceScope.launch {
            delay(400) // Brief natural pause
            val reply = localVoiceEngine?.generateMayraReply(text, currentSettings.vibe) ?: "You have my full attention."
            localVoiceEngine?.speak(reply)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        stopSession()
        localVoiceEngine?.release()
        serviceScope.cancel()
    }
}

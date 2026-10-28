package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.model.PersonaVibe
import com.example.model.VoiceSettings
import com.example.model.VoiceState
import com.example.service.MayraUiState
import com.example.service.MayraVoiceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val mayraState: MayraUiState = MayraUiState(),
    val isVibeSheetOpen: Boolean = false,
    val isSettingsSheetOpen: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val showPermissionRationale: Boolean = false,
    val hasValidGeminiKey: Boolean = false
)

class MayraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val effectiveApiKey: String
        get() {
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                ""
            }
            return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
                buildConfigKey
            } else {
                _uiState.value.mayraState.settings.customApiKey
            }
        }

    init {
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
        val hasKey = buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY"
        _uiState.update { it.copy(hasValidGeminiKey = hasKey) }

        // Observe background service state
        viewModelScope.launch {
            MayraVoiceService.serviceState.collect { serviceState ->
                _uiState.update { current ->
                    current.copy(
                        mayraState = serviceState,
                        hasValidGeminiKey = hasKey || serviceState.settings.customApiKey.isNotBlank()
                    )
                }
            }
        }
    }

    fun onAudioPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = isGranted) }
        if (isGranted) {
            startVoiceSession()
        }
    }

    fun onNotificationPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = isGranted) }
    }

    fun toggleSession() {
        val context = getApplication<Application>()
        val currentState = _uiState.value.mayraState.voiceState
        if (currentState == VoiceState.IDLE) {
            startVoiceSession()
        } else {
            val stopIntent = Intent(context, MayraVoiceService::class.java).apply {
                action = MayraVoiceService.ACTION_STOP
            }
            context.startService(stopIntent)
        }
    }

    fun startVoiceSession() {
        val context = getApplication<Application>()
        val startIntent = Intent(context, MayraVoiceService::class.java).apply {
            action = MayraVoiceService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startIntent)
        } else {
            context.startService(startIntent)
        }

        // Direct call to running service instance if already bound
        MayraVoiceService.getInstance()?.startSession(
            apiKey = effectiveApiKey,
            settings = _uiState.value.mayraState.settings
        )
    }

    fun toggleMute() {
        val context = getApplication<Application>()
        val muteIntent = Intent(context, MayraVoiceService::class.java).apply {
            action = MayraVoiceService.ACTION_TOGGLE_MUTE
        }
        context.startService(muteIntent)
    }

    fun interruptMayra() {
        val context = getApplication<Application>()
        val interruptIntent = Intent(context, MayraVoiceService::class.java).apply {
            action = MayraVoiceService.ACTION_INTERRUPT
        }
        context.startService(interruptIntent)
    }

    fun selectVibe(vibe: PersonaVibe) {
        val currentSettings = _uiState.value.mayraState.settings.copy(vibe = vibe)
        MayraVoiceService.getInstance()?.updateSettings(currentSettings, effectiveApiKey)
    }

    fun updateSettings(settings: VoiceSettings) {
        MayraVoiceService.getInstance()?.updateSettings(settings, effectiveApiKey)
    }

    fun setVibeSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isVibeSheetOpen = isOpen) }
    }

    fun setSettingsSheetOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsSheetOpen = isOpen) }
    }
}

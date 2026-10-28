package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VoiceState
import com.example.ui.MayraViewModel
import com.example.ui.components.AudioSpectrumBar
import com.example.ui.components.MayraVoiceOrb
import com.example.ui.components.PersonalityVibeBottomSheet
import com.example.ui.components.VoiceSettingsSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonPink
import com.example.ui.theme.Obsidian
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun MayraVoiceScreen(
    viewModel: MayraViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val mayra = uiState.mayraState

    // Permission launcher for microphone
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onAudioPermissionResult(isGranted)
    }

    // Permission launcher for notifications (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onNotificationPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Obsidian,
        modifier = modifier.fillMaxSize()
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Obsidian,
                            Color(0xFF130622),
                            Obsidian
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Bar: Branding, Live Mode Badge & Action Controls
                TopBar(
                    isUsingLive = mayra.isUsingGeminiLive,
                    voiceState = mayra.voiceState,
                    vibeEmoji = mayra.settings.vibe.emoji,
                    vibeTitle = mayra.settings.vibe.title,
                    onOpenVibe = { viewModel.setVibeSheetOpen(true) },
                    onOpenSettings = { viewModel.setSettingsSheetOpen(true) }
                )

                // 2. Central Section: Voice Orb & Reactive Audio Experience
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    // Persona Tagline
                    Text(
                        text = "MAYRA",
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = NeonPink,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = mayra.settings.vibe.subtitle,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // The Glowing Orb
                    MayraVoiceOrb(
                        voiceState = mayra.voiceState,
                        amplitude = mayra.amplitude,
                        isMuted = mayra.isMuted,
                        onOrbTapped = { viewModel.toggleSession() },
                        onOrbLongPressed = { viewModel.interruptMayra() }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Dynamic State Status Cue
                    Text(
                        text = mayra.statusDetail,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("status_cue_text")
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Audio Spectrum Equalizer
                    val isAudioActive = mayra.voiceState == VoiceState.LISTENING ||
                            mayra.voiceState == VoiceState.SPEAKING
                    AudioSpectrumBar(
                        amplitude = mayra.amplitude,
                        isSpeakingOrListening = isAudioActive,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    // Touch Cue Hint
                    Text(
                        text = if (mayra.voiceState == VoiceState.IDLE) {
                            "Tap the orb to awaken Mayra"
                        } else {
                            "Hands-free voice loop • Tap orb to pause • Hold to interrupt"
                        },
                        fontSize = 11.sp,
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                // 3. Bottom Controls: Mute, Quick Interrupt & Session Controls
                BottomControls(
                    voiceState = mayra.voiceState,
                    isMuted = mayra.isMuted,
                    onToggleMute = { viewModel.toggleMute() },
                    onInterrupt = { viewModel.interruptMayra() },
                    onToggleSession = { viewModel.toggleSession() }
                )
            }
        }

        // Bottom Sheets
        if (uiState.isVibeSheetOpen) {
            PersonalityVibeBottomSheet(
                currentVibe = mayra.settings.vibe,
                onVibeSelected = { viewModel.selectVibe(it) },
                onDismiss = { viewModel.setVibeSheetOpen(false) }
            )
        }

        if (uiState.isSettingsSheetOpen) {
            VoiceSettingsSheet(
                settings = mayra.settings,
                hasBuildConfigApiKey = uiState.hasValidGeminiKey,
                onSaveSettings = { viewModel.updateSettings(it) },
                onDismiss = { viewModel.setSettingsSheetOpen(false) }
            )
        }
    }
}

@Composable
private fun TopBar(
    isUsingLive: Boolean,
    voiceState: VoiceState,
    vibeEmoji: String,
    vibeTitle: String,
    onOpenVibe: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vibe Chip
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDark)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                .clickable { onOpenVibe() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("top_vibe_chip"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(vibeEmoji, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = vibeTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Change Vibe",
                tint = NeonPink,
                modifier = Modifier.size(14.dp)
            )
        }

        // Engine Status Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUsingLive) HotMagenta.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = if (isUsingLive) HotMagenta else ElectricCyan,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isUsingLive) "GEMINI LIVE 3.1" else "VOICE ENGINE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUsingLive) NeonPink else ElectricCyan
            )
        }

        // Settings Button
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .clip(CircleShape)
                .background(SurfaceDark)
                .size(40.dp)
                .testTag("settings_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Voice Settings",
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BottomControls(
    voiceState: VoiceState,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onInterrupt: () -> Unit,
    onToggleSession: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute / Unmute Button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isMuted) Color(0xFF4A1010) else SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (isMuted) Color(0xFFFF4D4D) else Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
                .testTag("mute_toggle_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute Microphone" else "Mute Microphone",
                tint = if (isMuted) Color(0xFFFF4D4D) else TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Smooth Interrupt Button (Quick Tap)
        Button(
            onClick = onInterrupt,
            enabled = voiceState == VoiceState.SPEAKING || voiceState == VoiceState.THINKING,
            colors = ButtonDefaults.buttonColors(
                containerColor = HotMagenta,
                disabledContainerColor = SurfaceVariantDark.copy(alpha = 0.4f),
                contentColor = TextPrimary,
                disabledContentColor = TextTertiary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("interrupt_button")
        ) {
            Text(
                text = "Interrupt",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        // Power / Session Stop Button
        IconButton(
            onClick = onToggleSession,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (voiceState != VoiceState.IDLE) SurfaceDark else HotMagenta)
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                .testTag("session_toggle_button")
        ) {
            Icon(
                imageVector = if (voiceState != VoiceState.IDLE) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (voiceState != VoiceState.IDLE) "Stop Session" else "Start Session",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

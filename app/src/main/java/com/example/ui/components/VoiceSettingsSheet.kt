package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VoiceSettings
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonPink
import com.example.ui.theme.Obsidian
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSheet(
    settings: VoiceSettings,
    hasBuildConfigApiKey: Boolean,
    onSaveSettings: (VoiceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedModel by remember { mutableStateOf(settings.model) }
    var selectedVoice by remember { mutableStateOf(settings.voiceName) }
    var isBackgroundEnabled by remember { mutableStateOf(settings.isBackgroundServiceEnabled) }
    var interruptionThreshold by remember { mutableFloatStateOf(settings.interruptionThreshold) }
    var customApiKey by remember { mutableStateOf(settings.customApiKey) }

    val availableVoices = listOf("Aoede", "Kore", "Fenrir", "Puck", "Charon")
    val availableModels = listOf("gemini-2.0-flash-exp", "gemini-2.5-flash-live-preview", "gemini-3.1-flash-live-preview")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .testTag("voice_settings_sheet")
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextTertiary.copy(alpha = 0.5f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Voice & Engine Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Configure Gemini Live audio parameters, voices, and background running.",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Voice Selection
            Text(
                text = "Gemini Live Prebuilt Voice",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "Aoede is recommended for Mayra's lively, charming tone",
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                availableVoices.forEach { voice ->
                    val isSelected = voice == selectedVoice
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) HotMagenta else SurfaceVariantDark)
                            .clickable {
                                selectedVoice = voice
                                onSaveSettings(settings.copy(voiceName = voice))
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("voice_option_$voice")
                    ) {
                        Text(
                            text = voice + if (voice == "Aoede") " (Default)" else "",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Live Model
            Text(
                text = "Live Model Target",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                availableModels.forEach { model ->
                    val isSelected = model == selectedModel
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) ElectricCyan.copy(alpha = 0.25f) else SurfaceVariantDark)
                            .border(
                                width = if (isSelected) 1.dp else 0.dp,
                                color = if (isSelected) ElectricCyan else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedModel = model
                                onSaveSettings(settings.copy(model = model))
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("model_option_$model")
                    ) {
                        Text(
                            text = model,
                            fontSize = 12.sp,
                            color = if (isSelected) ElectricCyan else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Background Service Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceVariantDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Run in Background",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Keeps voice stream alive via Foreground Service with notification",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Switch(
                    checked = isBackgroundEnabled,
                    onCheckedChange = {
                        isBackgroundEnabled = it
                        onSaveSettings(settings.copy(isBackgroundServiceEnabled = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonPink,
                        checkedTrackColor = HotMagenta.copy(alpha = 0.5f),
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = Obsidian
                    ),
                    modifier = Modifier.testTag("background_service_toggle")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Interruption Sensitivity
            Text(
                text = "Interruption Sensitivity",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "How easily Mayra stops speaking when you start talking",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Slider(
                value = interruptionThreshold,
                onValueChange = {
                    interruptionThreshold = it
                    onSaveSettings(settings.copy(interruptionThreshold = it))
                },
                valueRange = 0.03f..0.20f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonPink,
                    activeTrackColor = NeonPink,
                    inactiveTrackColor = SurfaceVariantDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interruption_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Instant (Soft voice)", fontSize = 11.sp, color = TextTertiary)
                Text("Normal", fontSize = 11.sp, color = TextTertiary)
                Text("Firm (Loud only)", fontSize = 11.sp, color = TextTertiary)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // API Key Status & Custom Key
            Text(
                text = "Gemini API Key",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            val keyStatus = when {
                hasBuildConfigApiKey -> "Connected via Secrets panel (BuildConfig)"
                customApiKey.isNotBlank() -> "Using custom API key"
                else -> "No key found • Using Local Voice Engine with Mayra banter"
            }
            Text(
                text = keyStatus,
                fontSize = 12.sp,
                color = if (hasBuildConfigApiKey || customApiKey.isNotBlank()) ElectricCyan else TextTertiary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            OutlinedTextField(
                value = customApiKey,
                onValueChange = {
                    customApiKey = it
                    onSaveSettings(settings.copy(customApiKey = it))
                },
                placeholder = { Text("Paste custom Gemini API key (optional)", fontSize = 13.sp, color = TextTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonPink,
                    unfocusedBorderColor = SurfaceVariantDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

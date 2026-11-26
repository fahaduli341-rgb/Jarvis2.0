package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.data.AssistantState
import com.jarvis.assistant.data.JarvisUiSnapshot
import com.jarvis.assistant.data.ListeningMode
import com.jarvis.assistant.ui.components.ReactorCore
import com.jarvis.assistant.ui.theme.JarvisAmber
import com.jarvis.assistant.ui.theme.JarvisBlack
import com.jarvis.assistant.ui.theme.JarvisCrimson
import com.jarvis.assistant.ui.theme.JarvisCyan
import com.jarvis.assistant.ui.theme.JarvisNavy
import com.jarvis.assistant.ui.theme.JarvisNavySurface
import com.jarvis.assistant.ui.theme.JarvisTextPrimary
import com.jarvis.assistant.ui.theme.JarvisTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisScreen(
    snapshot: JarvisUiSnapshot,
    isBatteryOptimizationIgnored: Boolean,
    initialBackendUrl: String,
    initialAccessToken: String,
    initialConfirmActions: Boolean,
    onTogglePower: () -> Unit,
    onConfirmAction: () -> Unit,
    onCancelAction: () -> Unit,
    onChangeListeningMode: (ListeningMode) -> Unit,
    onChangeConfirmActions: (Boolean) -> Unit,
    onSaveBackendConfig: (String, String) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenMiuiAppSettings: () -> Unit
) {
    var showSettingsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisBlack)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Minimal Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JARVIS",
                    color = JarvisTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Text(
                    text = if (snapshot.isMicrophoneActive) {
                        "Microphone active"
                    } else {
                        "Microphone off"
                    },
                    color = if (snapshot.isMicrophoneActive) JarvisCyan else JarvisTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            OutlinedButton(
                onClick = { showSettingsSheet = true },
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = JarvisNavy,
                    contentColor = JarvisTextPrimary
                )
            ) {
                Text(
                    text = if (isBatteryOptimizationIgnored) "Settings" else "Setup",
                    fontSize = 13.sp
                )
            }
        }

        // Central Reactor + Status Readout
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ReactorCore(
                state = snapshot.state,
                audioLevel = snapshot.audioLevel
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = snapshot.state.label,
                color = when (snapshot.state) {
                    AssistantState.OFFLINE -> JarvisTextSecondary
                    AssistantState.STANDBY -> JarvisCyan.copy(alpha = 0.8f)
                    AssistantState.LISTENING -> JarvisCyan
                    AssistantState.THINKING -> JarvisAmber
                    AssistantState.SPEAKING -> Color(0xFF38BDF8)
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val modeCaption = when {
                snapshot.state == AssistantState.OFFLINE ->
                    "Tap Turn On for hands-free background listening"
                snapshot.listeningMode == ListeningMode.WAKE_PHRASE &&
                    snapshot.state == AssistantState.STANDBY ->
                    "Say \"Hey JARVIS\" anytime"
                else -> snapshot.listeningMode.label
            }
            Text(
                text = modeCaption,
                color = JarvisTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Concise live transcript / reply box
            val displayText = when {
                snapshot.statusNotice.isNotEmpty() -> snapshot.statusNotice
                snapshot.partialTranscript.isNotEmpty() -> "\"${snapshot.partialTranscript}\""
                snapshot.lastAssistantReply.isNotEmpty() -> snapshot.lastAssistantReply
                snapshot.lastUserTranscript.isNotEmpty() -> "You: ${snapshot.lastUserTranscript}"
                else -> ""
            }

            if (displayText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisNavy)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = displayText,
                        color = if (snapshot.statusNotice.isNotEmpty()) JarvisAmber else JarvisTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Sensitive Action Confirmation Card
            snapshot.pendingAction?.let { action ->
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(JarvisNavySurface)
                        .border(1.dp, JarvisAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Confirm Action",
                        color = JarvisAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = action.summary,
                        color = JarvisTextPrimary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelAction,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text("Cancel", color = JarvisTextPrimary)
                        }
                        Button(
                            onClick = onConfirmAction,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisBlack
                            )
                        ) {
                            Text("Confirm", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Large Single ON / OFF Power Control
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val isRunning = snapshot.isServiceRunning
            Button(
                onClick = onTogglePower,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) JarvisCrimson else JarvisCyan,
                    contentColor = if (isRunning) JarvisTextPrimary else JarvisBlack
                )
            ) {
                Text(
                    text = if (isRunning) "TURN OFF" else "TURN ON",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isBatteryOptimizationIgnored) {
                TextButton(onClick = { showSettingsSheet = true }) {
                    Text(
                        text = "Battery saver may pause background voice — Tap to configure",
                        color = JarvisAmber,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = "Background service ready for screen-off & pocket use",
                    color = JarvisTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var urlInput by remember { mutableStateOf(initialBackendUrl) }
        var tokenInput by remember { mutableStateOf(initialAccessToken) }
        var confirmToggle by remember { mutableStateOf(initialConfirmActions) }

        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = JarvisNavy,
            contentColor = JarvisTextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "JARVIS Settings & Redmi A5 Setup",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = JarvisTextPrimary
                )

                // Listening Mode Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Voice Activation Mode",
                        fontSize = 13.sp,
                        color = JarvisTextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ListeningMode.entries.forEach { mode ->
                            val selected = snapshot.listeningMode == mode
                            OutlinedButton(
                                onClick = { onChangeListeningMode(mode) },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) JarvisCyan else Color(0xFF334155)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) JarvisCyan.copy(alpha = 0.14f) else Color.Transparent,
                                    contentColor = if (selected) JarvisCyan else JarvisTextSecondary
                                )
                            ) {
                                Text(
                                    text = if (mode == ListeningMode.WAKE_PHRASE) "Hey JARVIS" else "Continuous",
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Sensitive Action Confirmation Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Confirm Sensitive Actions",
                            fontSize = 14.sp,
                            color = JarvisTextPrimary
                        )
                        Text(
                            text = "Ask before opening dialer, alarms, or external links",
                            fontSize = 12.sp,
                            color = JarvisTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = confirmToggle,
                        onCheckedChange = {
                            confirmToggle = it
                            onChangeConfirmActions(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = JarvisBlack,
                            checkedTrackColor = JarvisCyan
                        )
                    )
                }

                // Redmi A5 Battery Optimization & MIUI/HyperOS Guidance
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(JarvisBlack)
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Redmi A5 Background Reliability",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = JarvisCyan
                    )
                    Text(
                        text = if (isBatteryOptimizationIgnored) {
                            "1. Android Battery Optimization: Unrestricted (Active)"
                        } else {
                            "1. Disable Android battery optimization so the OS does not kill the microphone service when the screen locks."
                        },
                        fontSize = 12.sp,
                        color = JarvisTextSecondary
                    )
                    if (!isBatteryOptimizationIgnored) {
                        Button(
                            onClick = onRequestBatteryExemption,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = JarvisBlack
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Unrestricted Battery", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        text = "2. On Xiaomi/Redmi: Open App Info -> Battery saver -> select \"No restrictions\", and lock JARVIS in Recent Apps if available.",
                        fontSize = 12.sp,
                        color = JarvisTextSecondary
                    )
                    OutlinedButton(
                        onClick = onOpenMiuiAppSettings,
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open App Info Settings", color = JarvisTextPrimary)
                    }
                }

                // Secure Backend Configuration (never stores Gemini key on device)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Secure AI Backend Endpoint",
                        fontSize = 13.sp,
                        color = JarvisTextSecondary
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("HTTPS Backend URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Optional Access Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSaveBackendConfig(urlInput, tokenInput)
                            showSettingsSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JarvisCyan,
                            contentColor = JarvisBlack
                        )
                    ) {
                        Text("Save Encrypted Settings", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

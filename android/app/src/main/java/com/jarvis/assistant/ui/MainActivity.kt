package com.jarvis.assistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.ListeningMode
import com.jarvis.assistant.data.SecureSettingsManager
import com.jarvis.assistant.service.JarvisVoiceService
import com.jarvis.assistant.ui.screens.JarvisScreen
import com.jarvis.assistant.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {

    private lateinit var settings: SecureSettingsManager
    private var isBatteryIgnored by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true || hasMicPermission()
        if (micGranted) {
            startJarvisService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SecureSettingsManager(this)
        refreshBatteryState()

        setContent {
            JarvisTheme {
                val snapshot by JarvisVoiceService.uiState.collectAsState()
                JarvisScreen(
                    snapshot = snapshot,
                    isBatteryOptimizationIgnored = isBatteryIgnored,
                    initialBackendUrl = settings.backendUrl,
                    initialAccessToken = settings.accessToken,
                    initialConfirmActions = settings.requireActionConfirmation,
                    onTogglePower = {
                        if (snapshot.isServiceRunning) {
                            stopJarvisService()
                        } else {
                            requestPermissionsAndStart()
                        }
                    },
                    onConfirmAction = { sendServiceAction(JarvisVoiceService.ACTION_CONFIRM_PENDING) },
                    onCancelAction = { sendServiceAction(JarvisVoiceService.ACTION_CANCEL_PENDING) },
                    onChangeListeningMode = { mode: ListeningMode ->
                        settings.listeningMode = mode
                        sendServiceAction(JarvisVoiceService.ACTION_REFRESH_SETTINGS)
                    },
                    onChangeConfirmActions = { enabled ->
                        settings.requireActionConfirmation = enabled
                    },
                    onSaveBackendConfig = { url, token ->
                        settings.backendUrl = url
                        settings.accessToken = token
                    },
                    onRequestBatteryExemption = { requestBatteryOptimizationExemption() },
                    onOpenMiuiAppSettings = { openAppDetailsSettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryState()
    }

    private fun refreshBatteryState() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryIgnored = pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsAndStart() {
        val missing = mutableListOf<String>()
        if (!hasMicPermission()) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (missing.isEmpty()) {
            startJarvisService()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startJarvisService() {
        val intent = Intent(this, JarvisVoiceService::class.java).apply {
            action = JarvisVoiceService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopJarvisService() {
        sendServiceAction(JarvisVoiceService.ACTION_STOP)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, JarvisVoiceService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        }
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }
}

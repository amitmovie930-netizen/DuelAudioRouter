package com.example.dualaudiorouter

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

@Composable
fun DualAudioUI(
    dualActive: Boolean,
    serviceConnected: Boolean,
    shizukuPermissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onCheckShizuku: () -> Unit,
    onRefreshState: () -> Unit
) {
    val context = LocalContext.current
    var btConnected by remember { mutableStateOf(false) }
    var shizukuRunning by remember { mutableStateOf(false) }

    // Refresh BT + Shizuku status periodically using a simple loop with delay
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val am = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                btConnected = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            } catch (_: Exception) {
                btConnected = false
            }

            shizukuRunning = try {
                Shizuku.pingBinder()
            } catch (_: Exception) {
                false
            }

            onRefreshState()
            // Use Thread.sleep via withContext would need coroutines-android;
            // kotlinx.coroutines is pulled in transitively by lifecycle-runtime-ktx
            kotlinx.coroutines.delay(2000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Dual Audio Router",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Shizuku status
        if (!shizukuRunning || !shizukuPermissionGranted) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (!shizukuRunning) {
                            "⚠️ Shizuku is not running"
                        } else {
                            "⚠️ Shizuku permission not granted"
                        }
                    )
                    Text(
                        text = if (!shizukuRunning) {
                            "Start Shizuku via ADB, then tap below to retry."
                        } else {
                            "Grant permission when prompted, then tap below."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCheckShizuku) {
                        Text("Check Shizuku")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Service connection status
        if (shizukuRunning && shizukuPermissionGranted && !serviceConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⏳ Connecting to Dual Audio service…")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onCheckShizuku) {
                        Text("Retry Bind")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // BT status
        if (!btConnected) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "⚠️ No Bluetooth A2DP device connected. Pair your speaker first.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dual Audio (Speaker + Bluetooth)")
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = dualActive,
                onCheckedChange = { enabled ->
                    onToggle(enabled)
                },
                enabled = shizukuRunning &&
                        shizukuPermissionGranted &&
                        serviceConnected &&
                        btConnected
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "When enabled, all media audio (Netflix, YouTube, music) plays on both the internal speaker and the Bluetooth speaker simultaneously.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Status summary
        Text(
            text = buildString {
                append("Status: ")
                when {
                    dualActive -> append("Dual routing ACTIVE")
                    !shizukuRunning -> append("Shizuku offline")
                    !shizukuPermissionGranted -> append("Permission needed")
                    !serviceConnected -> append("Service not bound")
                    !btConnected -> append("No BT A2DP")
                    else -> append("Ready")
                }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

package com.example.dualaudiorouter

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var service: IDualAudioService? = null
    private var dualActiveState by mutableStateOf(false)
    private var serviceConnected by mutableStateOf(false)
    private var shizukuPermissionGranted by mutableStateOf(false)

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE_SHIZUKU) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    shizukuPermissionGranted = true
                    bindUserService()
                } else {
                    shizukuPermissionGranted = false
                }
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuAndBind()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        serviceConnected = false
        dualActiveState = false
        shizukuPermissionGranted = false
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IDualAudioService.Stub.asInterface(binder)
            serviceConnected = true
            try {
                dualActiveState = service?.isDualActive() == true
            } catch (_: Exception) {
                dualActiveState = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            serviceConnected = false
            dualActiveState = false
        }
    }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, DualAudioService::class.java.name)
        )
            .processNameSuffix("dual-audio")
            .tag(SERVICE_TAG)
            .daemon(false)
            .debuggable(false)
            .version(1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        checkShizukuAndBind()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DualAudioUI(
                        dualActive = dualActiveState,
                        serviceConnected = serviceConnected,
                        shizukuPermissionGranted = shizukuPermissionGranted,
                        onToggle = { enabled ->
                            if (enabled) {
                                try {
                                    service?.setDualOutput(
                                        AudioDeviceInfo.TYPE_SPEAKER,
                                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                                    )
                                    dualActiveState = true
                                } catch (_: Exception) {
                                    dualActiveState = false
                                }
                            } else {
                                try {
                                    service?.clearDualOutput()
                                    dualActiveState = false
                                } catch (_: Exception) {
                                    // ignore
                                }
                            }
                        },
                        onCheckShizuku = {
                            checkShizukuAndBind()
                        },
                        onRefreshState = {
                            try {
                                dualActiveState = service?.isDualActive() == true
                            } catch (_: Exception) {
                                dualActiveState = false
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkShizukuAndBind() {
        if (!Shizuku.pingBinder()) {
            shizukuPermissionGranted = false
            serviceConnected = false
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            shizukuPermissionGranted = true
            bindUserService()
        } else {
            shizukuPermissionGranted = false
            Shizuku.requestPermission(REQUEST_CODE_SHIZUKU)
        }
    }

    private fun bindUserService() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
        if (Shizuku.isPreV13()) {
            // This implementation requires Shizuku v13+ (Context constructor)
            return
        }

        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
            serviceConnected = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)

        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {
            // ignore
        }
        service = null
    }

    companion object {
        private const val REQUEST_CODE_SHIZUKU = 1001
        private const val SERVICE_TAG = "dual-audio-service"
    }
}

package com.example.dualaudiorouter

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.annotation.Keep
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Shizuku User Service that runs as shell UID 2000.
 *
 * Calls the @SystemApi methods on AudioManager via reflection:
 *   setPreferredDevicesForStrategy(AudioProductStrategy, List<AudioDeviceAttributes>)
 *   removePreferredDeviceForStrategy(AudioProductStrategy)
 *   getPreferredDevicesForStrategy(AudioProductStrategy)
 *
 * And discovers the MEDIA strategy via:
 *   AudioProductStrategy.getAudioProductStrategies()
 *   AudioProductStrategy.supportsAudioAttributes(AudioAttributes)
 */
class DualAudioService : IDualAudioService.Stub {

    private var audioManager: AudioManager? = null
    private var deviceCallback: AudioManager.OnAudioDevicesChangedListener? = null

    /** Default constructor required by Shizuku (older versions). */
    constructor()

    /**
     * Constructor with Context — available from Shizuku v13.
     * Annotated with @Keep so R8 does not strip it.
     */
    @Keep
    constructor(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupDeviceCallback()
    }

    override fun destroy() {
        clearDualOutput()
        deviceCallback?.let { callback ->
            try {
                audioManager?.removeOnAudioDevicesChangedListener(callback)
            } catch (_: Exception) {
                // ignore
            }
        }
        deviceCallback = null
        audioManager = null
        System.exit(0)
    }

    override fun setDualOutput(speakerType: Int, btType: Int) {
        val am = audioManager ?: return

        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val speaker = devices.firstOrNull { it.type == speakerType } ?: return
        val btDevice = devices.firstOrNull { it.type == btType } ?: return

        val strategy = findMediaStrategy() ?: return

        val attrs = listOf(
            AudioDeviceAttributes(speaker),
            AudioDeviceAttributes(btDevice)
        )
        invokeSetPreferredDevicesForStrategy(am, strategy, attrs)
    }

    override fun clearDualOutput() {
        val am = audioManager ?: return
        val strategy = findMediaStrategy() ?: return
        invokeRemovePreferredDeviceForStrategy(am, strategy)
    }

    override fun isDualActive(): Boolean {
        val am = audioManager ?: return false
        val strategy = findMediaStrategy() ?: return false
        val preferred = invokeGetPreferredDevicesForStrategy(am, strategy)
        return preferred != null && preferred.size > 1
    }

    private fun setupDeviceCallback() {
        val am = audioManager ?: return
        deviceCallback = AudioManager.OnAudioDevicesChangedListener { devices ->
            if (isDualActive()) {
                val btDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                if (btDevice == null) {
                    clearDualOutput()
                }
            }
        }
        try {
            am.addOnAudioDevicesChangedListener(
                Executors.newSingleThreadExecutor(),
                deviceCallback!!
            )
        } catch (_: Exception) {
            // Listener may not work fully in User Service Context
        }
    }

    // -------------------------------------------------------------------------
    // Discover MEDIA AudioProductStrategy via reflection
    // -------------------------------------------------------------------------

    /**
     * Returns the AudioProductStrategy that handles USAGE_MEDIA, or null.
     */
    private fun findMediaStrategy(): Any? {
        return try {
            val strategyClass = Class.forName(
                "android.media.audiopolicy.AudioProductStrategy"
            )
            val getStrategies: Method = strategyClass.getMethod("getAudioProductStrategies")
            @Suppress("UNCHECKED_CAST")
            val strategies = getStrategies.invoke(null) as? List<Any> ?: return null

            val mediaAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            // Prefer supportsAudioAttributes(AudioAttributes) if present
            val supportsMethod = try {
                strategyClass.getMethod("supportsAudioAttributes", AudioAttributes::class.java)
            } catch (_: NoSuchMethodException) {
                null
            }

            if (supportsMethod != null) {
                for (strategy in strategies) {
                    val supports = supportsMethod.invoke(strategy, mediaAttrs) as? Boolean
                    if (supports == true) return strategy
                }
            }

            // Fallback: pick strategy whose getAudioAttributes() usage is USAGE_MEDIA
            val getAttrsMethod = try {
                strategyClass.getMethod("getAudioAttributes")
            } catch (_: NoSuchMethodException) {
                null
            }
            if (getAttrsMethod != null) {
                for (strategy in strategies) {
                    val attrs = getAttrsMethod.invoke(strategy) as? AudioAttributes
                    if (attrs?.usage == AudioAttributes.USAGE_MEDIA) return strategy
                }
            }

            // Last resort: first strategy in the list
            strategies.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // -------------------------------------------------------------------------
    // Reflection helpers for @SystemApi AudioManager methods
    // -------------------------------------------------------------------------

    private fun invokeSetPreferredDevicesForStrategy(
        am: AudioManager,
        strategy: Any,
        devices: List<AudioDeviceAttributes>
    ) {
        try {
            val strategyClass = Class.forName(
                "android.media.audiopolicy.AudioProductStrategy"
            )
            val method = AudioManager::class.java.getMethod(
                "setPreferredDevicesForStrategy",
                strategyClass,
                java.util.List::class.java
            )
            method.invoke(am, strategy, devices)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun invokeRemovePreferredDeviceForStrategy(
        am: AudioManager,
        strategy: Any
    ) {
        try {
            val strategyClass = Class.forName(
                "android.media.audiopolicy.AudioProductStrategy"
            )
            // Preferred clear method
            val method = try {
                AudioManager::class.java.getMethod(
                    "removePreferredDeviceForStrategy",
                    strategyClass
                )
            } catch (_: NoSuchMethodException) {
                // Older name used in some builds
                AudioManager::class.java.getMethod(
                    "clearPreferredDevicesForStrategy",
                    strategyClass
                )
            }
            method.invoke(am, strategy)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeGetPreferredDevicesForStrategy(
        am: AudioManager,
        strategy: Any
    ): List<AudioDeviceAttributes>? {
        return try {
            val strategyClass = Class.forName(
                "android.media.audiopolicy.AudioProductStrategy"
            )
            val method = AudioManager::class.java.getMethod(
                "getPreferredDevicesForStrategy",
                strategyClass
            )
            method.invoke(am, strategy) as? List<AudioDeviceAttributes>
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

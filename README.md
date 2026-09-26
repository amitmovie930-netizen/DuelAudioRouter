# DualAudioRouter

Minimal Android app that routes **USAGE_MEDIA** audio simultaneously to the internal speaker **and** a paired Bluetooth A2DP device. Replicates Samsung’s “Dual Audio” feature on non-Samsung devices (tested target: Honor Pad 8, Android 12).

Uses **Shizuku** (shell UID 2000) to call the Android 12 system API `AudioManager.setPreferredDevicesForStrategy()` via reflection. **No root required.**

## Requirements

- Android 12+ (API 31)
- [Shizuku](https://github.com/RikkaApps/Shizuku) v13+
- A paired Bluetooth A2DP device

## Build

```bash
# From project root
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Setup & Test

1. Install Shizuku v13+ on the device.
2. Start Shizuku via ADB:
   ```bash
   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. Pair a Bluetooth A2DP speaker/receiver.
4. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
5. Open the app → grant Shizuku permission when prompted.
6. Toggle **Dual Audio** ON.
7. Play media (Netflix, YouTube, music) → audio should come from **both** the tablet speakers and the Bluetooth device.
8. Toggle OFF → audio reverts to single output.
9. Unpair BT mid-playback → routing clears, no crash.

## Architecture

| Component | Process / UID | Role |
|-----------|---------------|------|
| `MainActivity` + Compose UI | App UID | Toggle, device detection, Shizuku permission, bind to User Service |
| `DualAudioService` (AIDL) | Shizuku User Service, shell UID 2000 | Calls hidden `AudioManager` APIs via reflection |

## Key technical points

- `setPreferredDevicesForStrategy` / `clearPreferredDevicesForStrategy` / `getPreferredDevicesForStrategy` are `@SystemApi`. They exist at runtime on Android 12+ but are not in the public SDK → called via reflection.
- The User Service runs as shell and therefore holds `MODIFY_AUDIO_ROUTING`.
- Only `USAGE_MEDIA` is affected (notifications, calls, etc. stay on their normal routes).
- No audio capture / MediaProjection / DRM interception.

## Project structure

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── aidl/com/example/dualaudiorouter/
│   │   └── IDualAudioService.aidl
│   ├── java/com/example/dualaudiorouter/
│   │   ├── MainActivity.kt
│   │   ├── DualAudioService.kt
│   │   └── DualAudioUI.kt
│   └── res/values/strings.xml
```

## License

Personal / educational use.

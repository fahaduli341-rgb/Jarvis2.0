# JARVIS — Android Personal Voice Assistant (Redmi A5 Edition)

Genuine Kotlin + Jetpack Compose Android application with a Foreground Microphone Service (`foregroundServiceType="microphone"`), US English speech recognition, male US TTS preference, and secure Gemini AI backend integration.

## Build & Install APK

### Option 1: Build via Android Studio
1. Open the `android/` folder in **Android Studio Ladybug (or newer)**.
2. Let Gradle sync (JDK 17, Android SDK 35).
3. Connect your **Redmi A5** via USB with **USB Debugging** enabled in Developer Options.
4. Click **Run** (`app`), or generate a debug APK via **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

### Option 2: Build via Command Line + ADB
```bash
# Optional: override backend URL at build time
export JARVIS_BACKEND_URL="https://your-jarvis-backend.run.app"

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Option 3: Build in the Cloud (GitHub Actions)
Push this `android/` directory to a GitHub repository. The included `.github/workflows/build-apk.yml` workflow builds `app-debug.apk` on GitHub runners so you can download and install it directly on your Redmi A5 without needing Android Studio on a local PC.

## Redmi A5 Background Reliability Setup
1. Open JARVIS, tap **Setup**, and grant **Allow Unrestricted Battery**.
2. In Xiaomi/Redmi **App Info > Battery saver**, select **No restrictions**.
3. Lock JARVIS in the **Recent Apps** overview if your HyperOS/MIUI build offers the lock icon.
4. Keep **Google** / **Speech Recognition and Synthesis from Google** updated on the device.

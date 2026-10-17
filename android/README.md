# VYRON for Android

The phone app opens to a standalone Gemini chat. It does not need the Mac to answer text prompts. The **API** button accepts a personal Gemini key override, encrypted with Android Keystore. **USE STARTER** clears the override. The **MAC** tab connects to the paired Mac dashboard for desktop control; its HTTPS certificate is pinned in the app.

## Private test APK

Run `python3 build_local_debug.py` on the Mac after installing Android SDK platform 35. The included Gradle wrapper handles the build. The script reads the working Mac key from `~/Library/Application Support/Vyron/config/api_keys.json` and injects it into the **debug** APK only. Its output is `app/build/outputs/apk/debug/app-debug.apk`. The **release** build has no starter key. A bundled key can be extracted from any APK, so keep the debug APK private, monitor its usage, and rotate the key after testing. No key or APK is committed to Git.

To build without a starter key, use `./gradlew :app:assembleDebug` directly, then enter your key through the app's API setting.

## Android Auto

VYRON exposes a separate Android Auto weather screen using the Car App Library weather category and a host-provided Pane template. It shows current conditions from Open-Meteo using the phone's last known location. Grant location permission in the phone app before connecting to the vehicle. The general chat and Mac dashboard are phone screens; Android Auto does not project arbitrary layouts or general assistant chat to the head unit.

For the car screen, install from a trusted source such as Google Play Internal App Sharing or an internal test track. Android Auto's **Unknown sources** developer option does not enable Car App Library apps from a sideloaded APK. The test APK can still be installed directly for phone testing. Confirm visibility and behavior on the actual Fortuner and phone before relying on it in the vehicle.

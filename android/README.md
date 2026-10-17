# VYRON for Android

The phone app opens to a standalone Gemini chat. It does not need the Mac to answer text prompts. The **API** button accepts a personal Gemini key override, encrypted with Android Keystore. **USE STARTER** clears the override. The **MAC** tab connects to the paired Mac dashboard for desktop control; its HTTPS certificate is pinned in the app.

## Private installable APK

Run `python3 build_local_release.py` on this Mac. It reads the working key from `~/Library/Application Support/Vyron/config/api_keys.json`, creates a local release signing key on the first run, and produces the signed `dist/Vyron-Android-1.0.0.apk`. This APK includes the starter Gemini key and can be transferred to another Android phone. The API setting on that phone can override the starter key. The included Gradle wrapper and installed Android SDK platform 35 handle the build.

Keep the APK and `.local-release/` signing files private. Anyone with the APK can extract and use the bundled Gemini key. Back up the signing files securely; future updates to this package require the same signing key. Installing this release over the earlier debug APK may require uninstalling the debug version first because their signing keys differ. No key, signing file, or APK is committed to Git.

For local debug builds, run `python3 build_local_debug.py`. Its output is `app/build/outputs/apk/debug/app-debug.apk` and also includes the starter key. Monitor usage and rotate the key if an APK is shared beyond trusted devices.

To build without a starter key, use `./gradlew :app:assembleDebug` directly, then enter your key through the app's API setting.

## Android Auto

VYRON exposes a separate Android Auto weather screen using the Car App Library weather category and a host-provided Pane template. It shows current conditions from Open-Meteo using the phone's last known location. Grant location permission in the phone app before connecting to the vehicle. The general chat and Mac dashboard are phone screens; Android Auto does not project arbitrary layouts or general assistant chat to the head unit.

For the car screen, upload the signed APK through Google Play Internal App Sharing or an internal test track, then install it on the phone through the Play link. Android Auto's **Unknown sources** developer option does not enable Car App Library apps from a sideloaded APK. Direct file transfer still works for phone testing. Confirm visibility and behavior on the actual Fortuner and phone before relying on it in the vehicle.

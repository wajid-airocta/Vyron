# VYRON for Android

The phone app opens to a native circular VYRON HUD with actual CPU or VYRON CPU, memory, network, battery, battery temperature, uptime, visible process count, Android version, and storage readings arranged around the reactor. Tap **START VOICE** for a real-time Gemini Live conversation through the phone microphone and speaker. Speech transcripts appear on the HUD. With audio routed to wired or Bluetooth earbuds, the microphone stays open so the user can interrupt a spoken reply. With the phone speaker, VYRON uses acoustic echo cancellation where available and pauses mic transmission during playback and its echo tail to prevent it hearing itself. Gemini Live can call phone tools to read current system status and requested usage history, list and open installed apps, and control the visible screen through the optional Accessibility service. A microphone foreground service keeps the conversation active while another app is open. The bottom navigation opens a live system page, weather, the paired Mac dashboard, and Settings. Typed prompts remain available. The Mac dashboard's HTTPS certificate is pinned in the app.

Live voice enables Gemini context compression and session resumption, reconnects after server connection rotation or transient transport failure, and shows a clear error when an Android audio device fails. It sends an audio stream end marker after a long speaker-output pause so Gemini can finish the pending speech turn. Speaker muting uses the duration of each audio chunk; it no longer accumulates an ever-growing mute interval across turns.

Settings stores a preferred name for natural voice greetings and a personal Gemini key override encrypted with Android Keystore. Leaving the key field empty preserves the active key; **USE STARTER** clears the override. The starter key is only included in locally built APKs. New voice sessions use the current name and key.

## Private installable APK

Run `python3 build_local_release.py` on this Mac. It reads the working key from `~/Library/Application Support/Vyron/config/api_keys.json`, creates a local release signing key on the first run, and produces the signed `dist/Vyron-Android-1.2.2.apk`. This APK includes the starter Gemini key and can be transferred to another Android phone. The Settings screen on that phone can override the starter key. The included Gradle wrapper and installed Android SDK platform 35 handle the build.

## Phone tools and permissions

The **SYSTEM** page shows real device, CPU, VYRON process, RAM, storage, battery, network, and permission data. Android may restrict system-wide CPU and other apps' process information; VYRON labels those readings as unavailable instead of inventing values. Recent app history needs **Usage Access**. The **OPEN APP** button launches a matching installed app. **PHONE ACTIONS** offers local Home, Back, recent apps, notification shade, screen reading, tapping, scrolling, and text entry after the phone owner enables **VYRON phone control** in Android Accessibility settings. Password fields are excluded. The **PERMISSIONS & PHONE CONTROL** button opens the relevant Android settings; VYRON cannot grant these permissions itself.

When asked during a Gemini Live conversation, VYRON sends the selected phone status, usage, or visible screen data to Google Gemini and sends back the tool result so the assistant can answer. Opening apps and phone controls also run through these tools. Grant Accessibility only on a phone you trust, and turn it off in Android Settings when it is not needed. Android's OS restrictions still apply to background launches and protected actions.

Keep the APK and `.local-release/` signing files private. Anyone with the APK can extract and use the bundled Gemini key. Back up the signing files securely; future updates to this package require the same signing key. Installing this release over the earlier debug APK may require uninstalling the debug version first because their signing keys differ. No key, signing file, or APK is committed to Git.

For local debug builds, run `python3 build_local_debug.py`. Its output is `app/build/outputs/apk/debug/app-debug.apk` and also includes the starter key. Monitor usage and rotate the key if an APK is shared beyond trusted devices.

To build without a starter key, use `./gradlew :app:assembleDebug` directly, then enter your key through the app's API setting.

## Android Auto

VYRON exposes a separate Android Auto weather screen using the Car App Library weather category and a host-provided Pane template. It shows current conditions from Open-Meteo using the phone's last known location. The microphone action records one weather question using the car's microphone, sends it to Gemini Live, and shows a concise text answer in the weather template. Grant location and microphone permissions in the phone app before connecting to the vehicle. Android Auto hosts render templates and do not project the phone's circular HUD. General assistant audio playback while driving is outside this weather category.

For the car screen, upload the signed APK through Google Play Internal App Sharing or an internal test track, then install it on the phone through the Play link. Android Auto's **Unknown sources** developer option does not enable Car App Library apps from a sideloaded APK. Direct file transfer still works for phone testing. Confirm visibility and behavior on the actual Fortuner and phone before relying on it in the vehicle.

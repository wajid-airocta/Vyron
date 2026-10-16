# VYRON for Android

The phone app displays the paired Mac dashboard in a native Android shell. The Mac's Gemini key remains on the Mac. A user-entered override key is encrypted with Android Keystore and sends that user's text prompts directly to Gemini.

The Android Auto surface is a separate, driving-safe weather app. Android Auto only accepts supported categories and host-provided templates; it does not project arbitrary phone layouts to the head unit. It fetches the current weather using the phone's last known location and Open-Meteo. The phone app must receive location permission first.

Connect your phone to the Fortuner, open VYRON on the phone, and enter the HTTPS URL shown by VYRON Remote Control on the Mac. The public certificate of this Mac is pinned in the APK. If the Mac reissues its certificate, rebuild the APK with the new public certificate. The private key and Gemini key are never included.

Build with Android Studio or Gradle using gradle :app:assembleDebug. The APK is app/build/outputs/apk/debug/app-debug.apk. For a real vehicle, install the app from a trusted source such as Play Internal App Sharing or an Internal Test Track. Android Auto’s “Unknown sources” developer option does not apply to Car App Library apps, so this sideloaded debug APK is for phone testing only. Testing on the actual vehicle and Google Play review remain necessary before claiming public compatibility.

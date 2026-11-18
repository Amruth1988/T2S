# T2S Studio

Native Android Text-to-Speech application built with Kotlin and Jetpack Compose.

## Current features
- Orange & White Material 3 design
- Home, Projects, Create, Recent and Profile navigation
- Create and locally persist project names between app launches
- On-device speech generation using Android TextToSpeech
- English (US), Telugu (India) and Hindi (India) language options
- Select from matching voices installed on the device
- Adjustable speaking speed and pitch
- Generate WAV audio and keep it in app-specific Music storage
- Play, pause and stop generated audio
- Export audio to Downloads on Android 10+ (app-specific Music storage on older Android versions)
- Share generated WAV files through Android's secure share sheet
- Persist generated-audio history locally on the device
- Saved Audio library with playback, download, share and delete actions
- Recent tab displays saved speech recordings, with missing audio files automatically excluded

## Android configuration
- Package: `com.t2sstudio.app`
- Minimum SDK: 24
- Compile / Target SDK: 35
- Kotlin: 2.0.21
- Jetpack Compose with Material 3

## Speech engine notes
Speech generation is performed locally by the Android device's configured text-to-speech engine. Available languages and voices depend on the device and installed voice data. No paid API or cloud service is required for the current implementation.

## Open
Open this repository in Android Studio and allow Gradle sync to complete. The GitHub Actions workflow builds the debug APK with `gradle :app:assembleDebug`.

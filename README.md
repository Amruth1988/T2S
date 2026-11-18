# T2S Studio

Native Android Text-to-Speech application built with Kotlin and Jetpack Compose.

## Current features
- Orange & White Material 3 design
- Home, Projects, Create, Recent and Profile navigation
- Create and locally persist project names between app launches
- Online speech generation through TTS.ai anonymous free access for supported English and Hindi voices
- Automatic fallback to Android TextToSpeech when offline, online service fails, text exceeds the free request limit, or online voices are unavailable
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
For English and Hindi, the app first attempts TTS.ai's anonymous free API and falls back automatically to Android TextToSpeech if the online service is unavailable. The documented anonymous limit is 500 characters per request and 5,000 characters per day per IP. Telugu remains on-device until compatible online voices are verified. Online output depends on TTS.ai availability and its free-tier terms. No API key is embedded in the APK; no paid API, subscription, billing, or cloud resource is configured.

## Open
Open this repository in Android Studio and allow Gradle sync to complete. The GitHub Actions workflow builds the debug APK with `gradle :app:assembleDebug`.


Build note: Personal voice features use the `TTS_API_KEY` GitHub Actions secret at build time. Never commit API keys to source control.

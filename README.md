# T2S Studio

Native Android Text-to-Speech application built with Kotlin and Jetpack Compose.

## Current features
- Orange & White Material 3 design
- Home, Projects, Create, Recent and Profile navigation
- Create and locally persist project names between app launches
- Free online Piper speech generation for English, Hindi and Telugu, with MP3, WAV and OGG output
- Automatic Android TextToSpeech fallback when online voices are unavailable or a request cannot be completed
- On-device speech generation using Android TextToSpeech
- English (US), Telugu (India) and Hindi (India) language options
- Select from matching voices installed on the device
- Adjustable speaking speed and pitch
- Generate audio and keep it in app-specific Music storage
- Play, pause and stop generated audio
- Export audio to Downloads on Android 10+ (app-specific Music storage on older Android versions)
- Share generated audio through Android's secure share sheet
- Persist generated-audio history locally on the device
- Saved Audio library with playback, download, share and delete actions
- Recent tab displays saved speech recordings, with missing audio files automatically excluded
- Save and manage local voice recordings; these are sample recordings, not AI voice clones

## Telugu offline voice packs

Speech Studio includes an app-managed Sherpa-ONNX Piper runtime for converted Telugu voice packs. To use it:

1. Open the **Actions** tab in this repository and download the `t2s-telugu-piper-validation` artifact from a successful **Validate Telugu Piper voice packs** run.
2. Keep the downloaded artifact ZIP intact.
3. In T2S Studio, open **Create**, select **Telugu**, and choose **Import converted voice ZIP**.
4. Select an installed voice pack, preview it, then generate Telugu WAV audio. Synthesis runs on-device; no online TTS service is used for this Telugu path.

The validation artifact contains converted Venkatesh, Padmavathi and Maya packs plus sample WAV files. Models are not bundled in the APK. Their individual dataset licences and attribution terms differ; the upstream Piper repository licence does not replace those terms. Do not redistribute model weights until the exact model card and dataset licence have been reviewed.

The Android Sherpa-ONNX/Piper synthesis path is implemented and connected to Speech Studio. Successful Python validation and an Android build do not replace real-device playback, Telugu-speaker intelligibility review, or licence review. Those remain release gates.

**Purohit / Vedic recitation:** no genuine Telugu priest-recitation model has passed model, quality and licence checks. Purohit presets therefore remain unavailable. T2S does not relabel a generic Android male voice or pitch-shifted voice as a Purohit voice. The SYSPIN Telugu male candidate is a different VITS format and is not selectable until a separate compatible Android adapter and device test are completed.

## Android configuration
- Package: `com.t2sstudio.app`
- Minimum SDK: 24
- Compile / Target SDK: 35
- Kotlin: 2.0.21
- Jetpack Compose with Material 3

## Speech engine notes
The app uses Android TextToSpeech as its local fallback and a free online Piper TTS endpoint for English, Hindi, and Telugu when an online voice is selected. Online requests support up to 3,000 characters and can return MP3, WAV, or OGG audio. Telugu offline playback is available through imported, converted Sherpa-ONNX Piper voice packs. Saved voice profiles are local recordings only; they are not AI voice clones. No paid API, subscription, billing, or cloud resource is configured.
## Open
Open this repository in Android Studio and allow Gradle sync to complete. GitHub Actions builds both debug and release APKs. A successful run publishes a direct `T2S.apk` download on the repository's Releases page.


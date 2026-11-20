# T2S Studio

Native Android Text-to-Speech application built with Kotlin and Jetpack Compose.

## Current features
- Orange & White Material 3 design
- Create, Projects, Recent, My Voices and Profile navigation
- Create and locally persist project names between app launches
- Free online Piper speech generation for English, Hindi and Telugu, with MP3, WAV and OGG output
- Optional free India API key stored encrypted with Android Keystore for online TTS authentication
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

1. Open the **Actions** tab in this repository and download the `t2s-telugu-piper-import-packs` artifact from a successful **Validate Telugu Piper voice packs** run.
2. Keep the downloaded artifact ZIP intact.
3. In T2S Studio, open **Create**, select **Telugu**, and choose **Import converted voice ZIP**.
4. Select an installed voice pack, preview it, then generate Telugu WAV audio. Synthesis runs on-device; no online TTS service is used for this Telugu path.

The import artifact contains only Venkatesh and Padmavathi, with each pack's original model card, attribution notice and CC BY 4.0 legal text alongside the converted model. Maya is synthesis-tested in CI but deliberately excluded from import artifacts and the app importer because its model card points to the IIT Madras IndicTTS licence and the exact derivative-model redistribution terms have not been cleared. Models are not bundled in the APK. See docs/TELUGU_VOICE_MODEL_PROVENANCE.md before redistributing any voice pack.

The Android Sherpa-ONNX/Piper synthesis path is implemented and connected to Speech Studio. Successful Python validation and an Android build do not replace real-device playback, Telugu-speaker intelligibility review, or licence review. Those remain release gates.

**Purohit / Vedic recitation:** three Purohit presets are available as speed-and-pitch profiles for an installed male Telugu Android voice. They are not a dedicated priest-recitation model and do not guarantee authentic Vedic modulation. DhVaani-0.5 MNN is being evaluated as an Android-local zero-shot TTS option; it requires a clean 3–10 second reference recording and its exact transcript. Use only a voice sample whose speaker has explicitly consented to this use. It is not selectable in T2S until the model/runtime, licensing, Android integration and real-device audio quality are verified. See `docs/TELUGU_VOICE_CANDIDATE_EVALUATION_2026-11-20.md`. DhVaani-0.5 currently has a CC BY-NC 4.0 model-card licence and is not approved for commercial T2S distribution.

## Android configuration
- Package: `com.t2sstudio.app`
- Minimum SDK: 24
- Compile / Target SDK: 35
- Kotlin: 2.0.21
- Jetpack Compose with Material 3

## Speech engine notes
The app uses Android TextToSpeech as its local fallback and a free online Piper TTS endpoint for English, Hindi, and Telugu when an online voice is selected. Online requests support up to 3,000 characters and can return MP3, WAV, or OGG audio. From 15 December 2026, online TTS requires a free India API key. Add it in **Profile → Online TTS access**; the key is encrypted with Android Keystore and sent only in the HTTPS Authorization header. Obtain a key at https://api.dharun.dev/keys. The provider currently documents 500 model requests per day per free key, with no paid billing requirement. Telugu offline playback is available through imported, converted Sherpa-ONNX Piper voice packs. Saved voice profiles are local recordings only; they are not AI voice clones. No paid API, subscription, billing, or cloud resource is configured.
## Open
Open this repository in Android Studio and allow Gradle sync to complete. GitHub Actions builds both debug and release APKs. A successful run publishes separate direct APK downloads for `arm64-v8a` (most modern Android phones) and `armeabi-v7a` (32-bit ARM devices). Install only the APK matching the device CPU architecture. Splitting native ABIs avoids shipping every architecture's Sherpa-ONNX runtime in one oversized universal APK.


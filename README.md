# T2S Studio

T2S Studio is an Android text-to-speech studio built with Kotlin and Jetpack Compose.

## Speech generation

- Online-only speech generation for English, Hindi and Telugu through the configured online Piper TTS service.
- MP3, WAV and OGG audio output.
- Online voice catalogue and voice preview.
- Generated audio is saved on the device for playback, sharing and download.
- Internet access is required for speech generation. If the online service is unavailable, the app reports an error and does not switch to Android on-device speech.
- The current free online endpoint accepts up to 3,000 characters per request. Longer scripts must be split into smaller requests by the user.
- An optional free India API key can be configured in Profile → Online TTS access. The key is encrypted with Android Keystore and sent only in the HTTPS Authorization header. No paid API, subscription, billing or cloud resource is configured.

## Voices and privacy

- Online voices are the only supported speech-generation voices.
- No offline voice models, voice-pack downloads, ZIP imports or local TTS engines are required for speech generation.
- Saved audio files remain available locally after generation; this is audio storage, not offline speech synthesis.
- Do not send private or sensitive text to a third-party speech service unless you are comfortable with that provider's data handling.

## Build

Open this repository in Android Studio and allow Gradle sync to complete. GitHub Actions builds debug and release APKs. Release signing depends on the repository's configured signing secrets.

# T2S Studio

T2S Studio is an Android text-to-speech studio built with Kotlin and Jetpack Compose. Application ID: `com.t2sstudio.app`.

## Guest and signed-in workspaces

- Guests can use Home and create speech locally with Android system TTS voices already installed on the device.
- Guest output is WAV, stored in the separate guest folder, and does not create Firebase account history, projects or drafts. Guest text is limited to 2,000 characters per file.
- Signed-in users can manage account-scoped projects, drafts, Recent audio and My Voices.
- Project metadata, scripts and voice-profile metadata may sync through Firebase under the signed-in UID. Generated audio and personal voice samples are stored in account-scoped local directories; voice samples use Android's no-backup storage.
- The app includes account data export and recent-auth protected account deletion. Files copied to public Downloads or shared outside the app, and provider-hosted cloned voices, require separate deletion.

## Online speech and voice services

- India TTS and Vakyam online speech routes use the authenticated Cloudflare Worker. Requests require a Firebase Authentication ID token and Firebase App Check token.
- Sarvam voice registration and cloned speech use the existing authenticated Google Apps Script gateway. The Sarvam credential remains server-side in Apps Script Script Properties; it is not embedded in the Android client.
- Online speech supports English, Telugu and Hindi, subject to the configured provider's supported voices and output formats.
- The Worker enforces per-account limits of 10,000 characters/day, 100,000 characters/month and 30 requests/day for each India/Vakyam provider. Missing D1 configuration fails closed.
- Sarvam additionally has configured global budgets and per-account quotas. Missing/disabled Firebase provider limits fail closed.
- Online generation sends submitted text to the selected provider. Voice registration sends the selected recording to Sarvam for provider-hosted voice creation. Review the Privacy Policy and provider terms before using these features.

## Offline voices and Telugu model status

- Guest offline speech uses Android system voices installed by the user/device manufacturer.
- Piper Telugu validation/import archives are generated separately; they are not an Android model importer and are not enabled as selectable in-app voices.
- Venkatesh and Padmavathi Piper packs retain CC BY 4.0 attribution and provenance. Maya and DhVaani-0.5 remain excluded from commercial distribution pending/under non-commercial rights restrictions. SYSPIN and Purohit candidates require separate Android implementation and device/audio review.
- Do not describe generic Android pitch/speed presets as a dedicated Purohit/Vedic voice.

## Cloud setup and release

- Cloudflare D1 provisioning, Worker secrets and deployment steps are in [the Worker README](cloudflare/t2s-tts-worker/README.md).
- Firebase provider credential, App Check, quota and deployment prerequisites are in [Central API Keys](docs/CENTRAL_API_KEYS.md).
- [Release checklist](docs/FINAL_RELEASE_TEST_CHECKLIST.md) separates source work, owner-side cloud setup and the final real-device test batch.
- [T2S Studio 1.0 release notes](docs/RELEASE_NOTES_1.0.0.md) are prepared but do not constitute release sign-off.

## Build

Open this repository in Android Studio and allow Gradle sync to complete. GitHub Actions builds Debug and Release APKs. Pull-request APKs are unsigned and for testing. Main-branch release publishing requires the configured signing secrets and a successful release workflow. The Android build targets ARM64 and ARMv7.

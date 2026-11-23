# T2S Studio final release test checklist

Release branch: main  
Application ID: com.t2sstudio.app  
Release build reference before this checklist commit: [Android build #298](https://github.com/Amruth1988/T2S/actions/runs/44680894416)  
Build commit: 05601858833d4229a1631fb1e9edbdf4edadbc69

This checklist distinguishes CI evidence from manual device verification. Do not mark a manual item passed based only on a successful Gradle build.

## A. Source and CI checks

- [x] Debug APK assembly passed in GitHub Actions run #298.
- [x] Release APK assembly passed in GitHub Actions run #298.
- [x] ABI-specific release APK preparation passed for arm64-v8a and armeabi-v7a.
- [x] Direct release assets were published for both ABIs.
- [x] Point 2 generated-audio persistence changes are included in build #298.
- [ ] Re-run the Android workflow after the latest source/checklist commits and confirm the new HEAD has a successful Debug + Release build.
- [x] Telugu voice-pack validation workflow #14 passed: pinned Venkatesh/Padmavathi source downloads, conversion, CI synthesis, attribution/licence packaging, SHA-256 source manifests and versioned release publication.
- [ ] Run automated unit/instrumentation tests if/when test suites are added; the current Android workflow is an assemble-and-publish workflow, not a full device test suite.

## B. Release artifact checks

- [x] Build #298 published T2S-arm64-v8a.apk (88.5 MB).
- [x] Build #298 published T2S-armeabi-v7a.apk (79.0 MB).
- [ ] Install the ARM64 APK on a real arm64 Android device.
- [ ] Install the ARMv7 APK on a compatible 32-bit ARM device/emulator.
- [ ] Confirm app ID, app label, launch icon, version and upgrade behavior.
- [ ] Confirm APK signature verification with Android SDK apksigner verify --verbose --print-certs.
- [ ] Confirm install/upgrade from the previous public build without losing projects or audio history.

## C. Core app workflows

- [ ] Fresh install → launch → navigate Create, Projects, Recent, My Voices and Profile.
- [ ] Create, rename, select and delete a project; verify persistence after force-stop/relaunch.
- [ ] Generate short and long text in English, Hindi and Telugu.
- [ ] Verify online TTS success and expected MP3/WAV/OGG handling.
- [ ] Disable network and verify Android TextToSpeech fallback; confirm failure states are clear.
- [ ] Save generated audio; confirm it appears in Recent/Saved Audio after relaunch.
- [ ] Verify recordings retain the selected project ID/name, including after project rename and delete.
- [ ] Play, pause, resume, stop, share, download and delete saved audio.
- [ ] Confirm Downloads export on Android 10+ and app-specific storage behavior on older supported Android versions.
- [ ] Test empty text, long text, rapid repeated taps, interrupted generation, storage-full and permission-denied paths.
- [ ] Test audio output with screen rotation, background/foreground transitions and process recreation.

## D. Telugu offline voice pack

- [ ] Download the t2s-telugu-piper-import-packs artifact from a successful voice-validation run.
- [ ] Confirm each Venkatesh/Padmavathi pack contains model, tokens, JSON config, original model card, ATTRIBUTION.md and CC BY 4.0 licence text.
- [ ] Import each pack on device and confirm malformed/duplicate/oversized ZIP entries are rejected safely.
- [ ] Preview and synthesize Telugu on device for both voices; verify non-empty playable WAV files.
- [ ] Have native Telugu speakers review pronunciation, intelligibility, naturalness, punctuation, numerals and common conjuncts.
- [ ] Verify both ABIs and memory/performance on representative lower-memory devices.
- [x] Maya is excluded from the app importer and public import artifact until IITM IndicTTS derivative-model redistribution terms are cleared.
- [ ] SYSPIN candidate remains separate from Piper; do not expose it as selectable until a dedicated Android implementation and device review pass.

## E. Licensing, provenance and privacy

- [x] Venkatesh and Padmavathi are attributed to PravalX and AI4Bharat IndicVoices-R under CC BY 4.0 in pack-generation workflow.
- [x] Original model cards and CC BY 4.0 legal text are included in generated import packs.
- [x] Maya's exact IITM IndicTTS derivative-model redistribution terms remain unverified; its weights are excluded from distribution.
- [x] DhVaani-0.5 is marked CC BY-NC 4.0 and excluded from commercial T2S release.
- [x] Archive exact source revision and SHA-256 checksums for each released voice model/configuration, model card, attribution and licence file in each pack's SOURCE_MANIFEST.sha256; ZIP archives also have SHA-256 sidecars.
- [ ] Confirm the final legal notices match the exact model and dataset revisions used for the release.
- [x] Code review: reference-voice recording flow requires the user to confirm the recording is their own voice or that they have permission before registration; real-device confirmation remains pending.
- [ ] Verify API keys never appear in logs, analytics, crash reports or exported project files.
- [x] Android automatic backup excludes `files/voice_samples/` while retaining the separate `voice_profiles.json` index; verified against the current `MyVoicesClient.kt` storage paths. Build #859 passed with these rules.
- [ ] Verify backup/restore on real Android devices, including that raw voice samples are not included in automatic cloud backup or device transfer.
- [ ] Add and verify a complete user-data export. The current My Voices ZIP export covers voice profiles and their local recordings only; it does not export projects, scripts/drafts, profile details, or cloud metadata.
- [ ] Add and verify an authenticated account-deletion workflow that removes the Firebase Auth account and all UID-owned Firestore data (including nested projects, scripts, voiceProfiles and settings) without leaving orphaned records. No in-app account-deletion flow was found in the current Account page/repository audit.
- [ ] Define and verify local-data cleanup on sign-out/account deletion. Projects and audio history are currently stored in app-private SharedPreferences/files and must not be accidentally mixed across signed-in users on a shared device.
- [ ] Resolve persistent per-user quota enforcement for direct Cloudflare Worker TTS requests; the current Worker configuration has no persistent KV/Durable Object quota binding. Do not replace the route or enable paid infrastructure without approval.


## F. Release sign-off

- [ ] CI is green on the exact release commit.
- [ ] Both release APKs install and launch on real supported devices.
- [ ] Critical Create → Generate → Save → Reopen → Play/Export workflows pass.
- [ ] Telugu offline voices pass native-speaker listening review on real devices.
- [ ] Licence/provenance review is complete for every model actually distributed.
- [ ] Release notes, version and checksums are recorded.

## Current verification boundary

Build #298 proves the Gradle Debug/Release APK assembly and publishing steps succeeded for commit 05601858833d4229a1631fb1e9edbdf4edadbc69. It does not prove real-device installation, playback, native-speaker quality, signature inspection on the downloaded bytes, or completion of the remaining manual checklist. The next build must be run after the changes in this checklist are committed.

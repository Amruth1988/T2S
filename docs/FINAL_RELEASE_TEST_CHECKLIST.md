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

- [ ] Fresh install → launch → verify signed-in navigation to Create, Projects, Recent, My Voices and Profile.
- [ ] Signed-out guest sees Home only; account entry remains available for sign-in.
- [ ] Create, rename, select and delete a project; verify persistence after force-stop/relaunch.
- [ ] Generate short and long text in English, Hindi and Telugu while signed in.
- [ ] As guest, generate WAV locally with an installed offline Android TTS voice; verify voice selection, speech rate/pitch and clear error when no offline voice is installed.
- [ ] As guest, download generated WAV and verify it is stored under the guest audio directory; confirm generation does not create cloud records, account history, projects or drafts.
- [ ] Verify online TTS success and expected MP3/WAV/OGG handling.
- [ ] Disable network while signed in and verify online-provider failure/fallback behavior; guest local TTS must remain available when a compatible installed voice is present.
- [ ] Save generated audio; confirm it appears in Recent/Saved Audio after relaunch.
- [ ] Verify recordings retain the selected project ID/name, including after project rename and delete.
- [ ] Play, pause, resume, stop, share, download and delete saved audio.
- [ ] Confirm Downloads export on Android 10+ and app-specific storage behavior on older supported Android versions.
- [ ] Test empty text, long text, rapid repeated taps, interrupted generation, storage-full and permission-denied paths.
- [ ] Test audio output with screen rotation, background/foreground transitions and process recreation.

## D. Telugu offline voice packs (deferred; not currently integrated into the Android app)

The current guest offline path uses Android's installed system TTS voices. The Piper pack generation workflow is not an Android import/playback feature at this time. Do not treat the import steps below as release-ready functionality.

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
- [x] My Voices profile JSON and sample audio are now stored in Firebase-UID-specific local directories; sample audio uses `noBackupFilesDir`. Legacy shared profile/sample files are preserved but are not automatically assigned to an account. Verify this on a real device by switching between two accounts.
- [ ] Verify backup/restore on real Android devices, including that raw voice samples are not included in automatic cloud backup or device transfer.
- [ ] Add and verify a complete user-data export. The current My Voices ZIP export covers voice profiles and their local recordings only; it does not export projects, scripts/drafts, profile details, or cloud metadata.
- [ ] Add and verify an authenticated account-deletion workflow that removes the Firebase Auth account and all UID-owned Firestore data (including nested projects, scripts, voiceProfiles and settings) without leaving orphaned records. No in-app account-deletion flow was found in the current Account page/repository audit.
- [ ] Define and verify local-data cleanup on sign-out/account deletion. Project/audio history preferences and generated audio paths are UID-scoped; verify switching accounts on one device never exposes another account's data. Guest audio remains in its separate guest directory.
- [ ] Verify generated audio for a signed-in user is stored under that user's UID-derived directory and never linked to a project unless the user explicitly selects one.
- [ ] Verify My Voices profiles and samples remain isolated when switching between two Firebase accounts; verify legacy shared files remain untouched until an explicit migration flow exists.
- [ ] Resolve persistent per-user quota enforcement for direct Cloudflare Worker TTS requests; the current Worker configuration has no persistent KV/Durable Object quota binding. Do not replace the route or enable paid infrastructure without approval.


## F. Release sign-off

- [ ] CI is green on the exact release commit.
- [ ] Both release APKs install and launch on real supported devices.
- [ ] Critical Create → Generate → Save → Reopen → Play/Export workflows pass.
- [ ] Telugu offline voices pass native-speaker listening review on real devices.
- [ ] Licence/provenance review is complete for every model actually distributed.
- [ ] Release notes, version and checksums are recorded.

## Per-account provider generation quotas

- [ ] Verify each authenticated account is capped at 10,000 characters/day, 100,000 characters/month and 30 requests/day for each central India TTS, Vakyam TTS and Sarvam clone generation route.
- [ ] Verify provider-wide daily/monthly character and request limits still apply across all users.
- [ ] Run concurrent requests at the quota boundary and confirm Firestore transactions prevent over-reservation.
- [ ] Confirm quota reservations happen before provider calls and are not refunded on provider errors/timeouts (conservative cost protection).
- [ ] Confirm guest local Android TTS does not consume server-side provider quotas.

## Firebase App Check configuration (required before enabling enforcement)

- [ ] In Firebase Console, register the Android app with the Play Integrity App Check provider and complete the Play Integrity/API project linkage.
- [ ] Add the release signing certificate fingerprints used by the distributed APK to the Firebase Android app settings.
- [ ] Install a debug build, copy its Firebase App Check debug token from Logcat, and register that token in Firebase Console. Never ship or publish a debug token.
- [ ] Verify debug and release builds can obtain App Check tokens and invoke callable Functions.
- [ ] Verify an unregistered/debug-token-free client is rejected by Functions configured with `enforceAppCheck: true`.
- [ ] Do not deploy or change Firebase Console enforcement/provider settings until the above checks are complete.

The Android client initializes the debug provider only for debug builds (loaded reflectively so the debug-only SDK is not required by release compilation) and Play Integrity for release builds. Firebase Console registration and release signing fingerprints remain manual prerequisites.

## Current verification boundary

Build #298 proves the Gradle Debug/Release APK assembly and publishing steps succeeded for commit 05601858833d4229a1631fb1e9edbdf4edadbc69. It does not prove real-device installation, playback, native-speaker quality, signature inspection on the downloaded bytes, or completion of the remaining manual checklist. The next build must be run after the changes in this checklist are committed.

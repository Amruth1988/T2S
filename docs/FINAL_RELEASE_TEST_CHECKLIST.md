# T2S Studio release work tracker

Application ID: `com.t2sstudio.app`  
Release line: `1.0`  
Active implementation branch: `fix/release-workflow-and-guest-scope`  
Pull request: [#3 — Enable guest audio and isolate account data](https://github.com/Amruth1988/T2S/pull/3)

This tracker separates repository work, owner-side cloud configuration and the final hands-on test batch. Real-device and scenario testing is intentionally deferred until all code and configuration preparation is complete.

## 1. Repository work completed

- [x] Guest access is limited to Home/local audio creation; account workspace destinations remain signed-in only.
- [x] Guest audio generation uses installed Android system TTS voices and local WAV output; no Firebase account or cloud history is created for guests.
- [x] Signed-in project, audio-history and My Voices data use Firebase-UID-derived storage namespaces.
- [x] Legacy shared voice files are not automatically assigned to an account; old 32-bit preference namespaces are not auto-migrated because ownership cannot be proven.
- [x] Account export creates a ZIP of known cloud metadata, account-local projects, saved audio, My Voices profiles and owned recordings; selected saved-voice preference is included.
- [x] Account deletion requires recent authentication, recursively removes UID-owned Firestore data, clears hashed Firestore usage records, removes Cloudflare D1 quota rows before Firebase Auth deletion, and clears UID-owned local files/preferences.
- [x] Account deletion notice and privacy/terms text explain that public Downloads, shared copies and provider-hosted cloned voice records require separate handling.
- [x] India and Vakyam Worker routes validate Firebase ID tokens and Firebase App Check tokens.
- [x] Cloudflare D1 per-account quotas use one atomic SQLite UPSERT per provider/UID hash: 10,000 characters/day, 100,000 characters/month and 30 requests/day. Missing D1 fails closed.
- [x] Sarvam Android operations now call the authenticated Firebase Functions endpoints directly; the old hard-coded Apps Script deployment URL was removed from the Android client.
- [x] Sarvam provider usage guard includes global configured budgets and per-account daily/monthly limits plus the additional 10-call/10,000-character daily Sarvam allowance.
- [x] Worker quota SQL is shared between production code and automated Node/SQLite regression tests for daily/monthly reset, quota boundaries, account/provider isolation, burst reservations and account deletion.
- [x] Added automated high-confidence provider/private-key scanning to pull-request and push CI. The public Firebase app-config key is excluded and documented as a public identifier, not a provider secret.
- [x] Release workflow prepares ARM64 and ARMv7 APKs, verifies the signing certificate on main releases, publishes SHA-256 checksums and includes prepared release notes.
- [x] Prepared T2S Studio 1.0 release notes.
- [x] Updated Privacy Policy, Terms & Conditions, provider routing/setup documentation and Telugu model provenance decisions.
- [x] Voice distribution decisions are documented: Venkatesh/Padmavathi packs retain CC BY 4.0 attribution; Maya remains excluded pending exact rights confirmation; DhVaani-0.5 remains excluded from commercial distribution; SYSPIN and Purohit candidates remain unshipped pending separate Android implementation and listening gates.

## 2. Owner-side cloud and release configuration still required

These are account operations, not source-code tasks. They are not marked complete because no Cloudflare/Firebase account changes or billing approvals have been made.

### Cloudflare (existing plan only; no paid upgrade)

- [ ] Confirm D1 is available on the current Cloudflare plan without upgrading.
- [ ] Create the free-tier `t2s-usage` D1 database and put its real ID in `cloudflare/t2s-tts-worker/wrangler.toml`.
- [ ] Apply `migrations/0001_user_provider_usage.sql` to the remote D1 database.
- [ ] Set `VAKYAM_API_KEY` and, when required/available, `INDIA_API_KEY` as Worker secrets.
- [ ] Configure GitHub Actions `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` secrets with only the required Workers/D1 permissions.
- [ ] Run the manual Worker deployment after all prerequisites are satisfied; verify `/health` reports `usageQuota: true`.
- [ ] Run live D1 quota boundary and concurrent-request checks after deployment.

### Firebase (do not enable billing without explicit owner approval)

- [ ] Confirm Firebase project `t2s-studio` and Functions region `asia-south1`.
- [ ] Configure Android Firebase App Check with Play Integrity and the correct Cloud project linkage.
- [ ] Register the actual release signing fingerprints and a private debug token for local testing; never ship a debug token.
- [ ] Configure `T2S_PROVIDER_PRIVATE_KEY` in Functions Secret Manager and `T2S_PROVIDER_PUBLIC_KEY` in the untracked Functions environment file.
- [ ] Confirm the trusted owner account has the `admin: true` custom claim.
- [ ] Save central Sarvam provider credentials using the encrypted admin workflow and configure enabled `provider_limits/{sarvam}` budgets.
- [ ] Review Firebase Functions deployment billing requirements. Do not move the project to Blaze or add a payment method unless explicitly approved.
- [ ] Deploy updated Functions only after App Check, secrets, admin claims, provider keys, quota documents and billing approval are ready.
- [ ] Verify live callable functions and provider usage enforcement with a disposable Firebase test account.

### Legacy gateway and signing

- [ ] Once older released APKs no longer use it, disable/delete the legacy Google Apps Script Sarvam deployment and remove obsolete Script Properties from its owner account.
- [ ] Confirm GitHub Actions signing secrets exist and point to the intended release keystore. Never place keystore files/passwords in the repository.
- [ ] After the release branch is approved for merge, run the main-branch release workflow and verify both signed ABI APKs and their SHA-256 checksums.

## 3. Final real-device/scenario test batch — intentionally deferred

Do not begin these until the repository work and owner-side configuration are complete. Run the scenarios together in one final pass.

- [ ] Install the latest signed ARM64 APK on a supported Android phone; use ARMv7 only if a compatible device is available.
- [ ] Verify app ID, app label, launch icon, version, signature and upgrade behavior.
- [ ] Test sign-in, sign-out, guest access and switching between two Firebase accounts; confirm no projects, drafts, audio or voice samples leak between accounts.
- [ ] Test project create/rename/select/delete and persistence after closing/reopening.
- [ ] Test Create → Generate → Save → Reopen → Play → Share/Export for English, Hindi and Telugu.
- [ ] Test guest WAV generation using installed offline Android voices, including the no-offline-voice error and no-cloud-data guarantee.
- [ ] Test India TTS, Vakyam and Sarvam only after their owner-side configuration and deployments are complete.
- [ ] Test account export ZIP contents and account deletion with a disposable account, including stale-auth rejection and cleanup of Firestore, Auth, D1 and local account data.
- [ ] Test offline/network interruption, rapid taps, long text, low storage, background/foreground and process recreation.
- [ ] Review backup/restore and confirm raw voice samples are excluded from Android backup/device transfer.
- [ ] Have a native Telugu speaker review any installed Telugu voice for pronunciation, intelligibility, naturalness, punctuation and numerals.
- [ ] Confirm provider-hosted voice records and external provider retention/deletion behavior separately.

## 4. Current verification boundary

- Source changes and automated CI are not equivalent to live cloud deployment or Android runtime verification.
- The Worker must remain fail-closed until a real D1 ID is configured and the migration is applied.
- Firebase callable Functions and App Check must not be treated as production-ready until the owner-side setup and deployment steps above pass.
- The public Firebase app config key is not a private provider secret; restrict its API usage and Android application identity in Google Cloud.
- No production Worker deployment, Firebase Functions deployment, billing change, signed release publication or real-device test is claimed by this checklist.
- The exact latest commit and CI run statuses are recorded in the PR and the final update after the current workflow completes.

## 5. Voice model distribution policy

The Android app's guest offline path uses Android system TTS. The repository's Piper validation workflow is not an Android model importer and must not be described as one. Do not enable Piper, SYSPIN, Hima, Maya, DhVaani or a Purohit-specific model in the app until its exact licence, Android runtime/ABI support and voice-quality gates are satisfied.

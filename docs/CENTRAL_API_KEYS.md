# T2S Studio central provider credentials and usage controls

Last reviewed: 24 November 2026

## Active Android routing

| Feature | Android route | Credential location | Quota storage |
|---|---|---|---|
| India TTS | Cloudflare Worker `/v1/india/*` | Cloudflare Worker secret `INDIA_API_KEY` (optional for the provider's current keyless allowance) | Cloudflare D1, hashed Firebase UID |
| Vakyam TTS | Cloudflare Worker `/v1/vakyam/*` | Cloudflare Worker secret `VAKYAM_API_KEY` | Cloudflare D1, hashed Firebase UID |
| Sarvam voice registration and cloned speech | Firebase callable Functions | Firebase Functions Secret Manager + encrypted Firestore provider-key record | Firestore provider usage ledgers |

The Android Sarvam client now calls `createCentralSarvamVoice`, `listCentralSarvamVoices` and `generateCentralSarvamClone` through Firebase callable Functions. It no longer calls the legacy Google Apps Script URL. The Apps Script source under `scripts/sarvam-apps-script` is retained for reference only and is not the current Android route. Disable any old Apps Script deployment after confirming no older installed client still depends on it.

## Security boundaries

- The Android app does not contain provider API credentials.
- India/Vakyam requests carry a Firebase Authentication ID token and Firebase App Check token. The Worker validates both, including project/app identity and token signatures.
- Sarvam provider credentials are decrypted only inside Firebase Functions. The RSA private key is a Secret Manager secret; Firestore stores only RSA-OAEP-SHA256 ciphertext.
- Do not commit, log, export or paste provider credentials, private keys, Firebase ID tokens or App Check debug tokens.
- The Firebase Web API key in `google-services.json` is an app identifier, not a provider secret. Restrict it to the required Firebase APIs and Android app where supported.
- Existing device-local provider-key and self-hosted voice-clone settings remain for compatibility, but the active central online TTS and Sarvam flows do not need end-user provider keys.

## India and Vakyam quota policy

The Cloudflare Worker reserves usage atomically in D1 before each provider call. Per Firebase account and provider, the source limits are:
- 10,000 characters per UTC day;
- 100,000 characters per UTC month;
- 30 requests per UTC day.

D1 stores a SHA-256 hash of the Firebase UID rather than the raw UID. Failed upstream requests remain reserved to prevent retries from bypassing cost controls. Online generation fails closed with HTTP 503 if D1 is absent or unavailable. The account-deletion callable asks the Worker to delete the caller's hashed usage rows before deleting Firebase Auth; a failed D1 cleanup stops deletion so it can be retried.

The database is not yet provisioned: `wrangler.toml` still contains the all-zero placeholder database ID. Do not deploy the Worker until the owner creates the free-tier D1 database, inserts its real ID, applies the checked-in migration and verifies the Worker health response reports `usageQuota: true`.

## Sarvam quota policy

Firebase Functions reserve both:
- configured provider-wide daily/monthly character and daily-request budgets from `provider_limits/{provider}`; and
- per-account caps of 10,000 characters/day, 100,000 characters/month and 30 requests/day for central provider operations.

Sarvam voice registration and cloned generation also use a separate per-account allowance of 10 requests/day and 10,000 characters/day. Reservations occur before contacting the provider and are not refunded after provider failures. The Firebase Functions guard fails closed unless each provider's `provider_limits` document is enabled with positive integer `dailyCharacters`, `monthlyCharacters` and `dailyRequests`.

Provider-wide limits are application-side ceilings, not a live view of provider wallet balances. Set them below the known provider allowance, monitor provider dashboards and disable a provider before its actual balance is exhausted. Do not assume a top-up resets app-side quota counters.

## Cloudflare owner setup (not yet performed)

Do not upgrade Cloudflare or enable paid services for T2S. Confirm D1 is available on the existing account plan first.

From `cloudflare/t2s-tts-worker`:
```bash
npm install
npx wrangler login
npx wrangler d1 create t2s-usage
```

Copy the returned database ID into `wrangler.toml`, replacing the all-zero placeholder, then run:
```bash
npx wrangler d1 migrations apply t2s-usage --remote
npm run typecheck
npm test
npx wrangler deploy --dry-run --outdir .wrangler-build
```

Configure Worker secrets interactively:
```bash
npx wrangler secret put VAKYAM_API_KEY
npx wrangler secret put INDIA_API_KEY
```

The deploy workflow also needs GitHub repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`, with Workers and D1 migration permissions. Do not paste these secrets into chat or commit them. The deploy workflow is manual and has not been run from this PR branch.

## Firebase owner setup (not yet performed)

Firebase Functions source is prepared, but deployment and Console configuration are not complete. Functions deployment may require Firebase Blaze billing. Do not enable billing or upgrade the project without the owner's explicit approval.

Before enabling the Sarvam callable path:
1. Confirm the Firebase project and `asia-south1` region.
2. Configure Firebase App Check for the Android app with Play Integrity and register the actual release signing fingerprints. Register a debug token only for a private debug build; never ship it.
3. Create an RSA-2048 key pair on a trusted computer:
   ```bash
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out t2s-provider-private.pem
   openssl pkey -in t2s-provider-private.pem -pubout -out t2s-provider-public.pem
   ```
4. Store the private PEM in Functions Secret Manager as `T2S_PROVIDER_PRIVATE_KEY`. Configure `T2S_PROVIDER_PUBLIC_KEY` in the untracked Functions environment file using literal `\\n` separators.
5. Grant the owner account the `admin: true` custom claim using a trusted Admin SDK environment.
6. Save the Sarvam credential through the authenticated admin key workflow and configure enabled `provider_limits/sarvam` ceilings. Never write plaintext credentials to Firestore or logs.
7. Review Firebase billing implications and approve them before any Functions deployment.

The callable code uses recent-auth protection for account deletion and App Check enforcement. Console registration, secrets, custom claims, provider credentials, quota documents and deployment are owner-side actions; they are not marked complete by a source build.

## Automated checks

The Worker build workflow runs:
- TypeScript type-check;
- Wrangler bundle dry-run;
- Node's built-in SQLite regression suite for the exact D1 quota reservation and deletion SQL, including daily/monthly resets, per-account isolation, boundary rejection and burst attempts.

These tests validate the SQLite statement semantics locally in CI. They do not provision a remote D1 database, exercise Cloudflare account credentials, or replace the final live/device verification batch.

## Legacy Apps Script gateway

The app no longer routes Sarvam operations through `scripts/sarvam-apps-script/Code.gs`. Do not add its URL back to Android. If a previous Apps Script deployment remains live, disable or delete that deployment from the Google Apps Script project after confirming that older released APKs no longer use it. Its Script Properties may contain a legacy Firebase web key or provider credential; review and remove obsolete properties in the Apps Script owner account. This cannot be completed from the repository.

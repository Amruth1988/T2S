# T2S TTS Cloudflare Worker

Secure proxy for T2S Studio's Vakyam and India TTS providers. Sarvam is deliberately routed through Firebase Functions instead.

## Routes

- `GET /health` — public health/configuration check; does not expose credentials
- `GET /v1/india/voices?language=te|hi|en`
- `POST /v1/india/tts` — JSON: `text, voice, language, speed, format`
- `GET /v1/vakyam/voices?language=te|hi|en`
- `POST /v1/vakyam/tts` — JSON: `text, voice, language, speed, format`
- `DELETE /v1/account/usage` — authenticated account-deletion cleanup; removes only the caller's hashed D1 quota rows

Every provider route requires both:
- A Firebase Authentication ID token in `Authorization: Bearer <token>`
- A Firebase App Check token in `X-Firebase-AppCheck`

The Worker validates the Firebase project issuer/audience and App Check signature, issuer, audience, expiry, JWT type, RS256 algorithm and this Android app's Firebase App ID. Provider credentials remain in Cloudflare Worker secrets.

The account-usage DELETE route uses the same Firebase ID token and App Check checks as generation routes. Firebase Functions calls it before deleting the Firebase Authentication identity, so the Worker can authenticate the still-valid caller and erase only that account's SHA-256 quota rows. If the D1 delete fails, account deletion stops and can be retried; the Firebase identity is not removed prematurely.

## Per-account usage limits

D1 stores a SHA-256 hash of the Firebase UID, not the raw UID. Atomic reservations enforce a maximum of 10,000 characters and 30 requests per provider per UTC day, plus 100,000 characters per provider per UTC month. Requests fail closed when D1 is missing or unavailable. Failed upstream provider requests remain charged to prevent retry-based quota bypass.

## One-time Cloudflare setup

Do not upgrade the Cloudflare plan or enable paid services for this project. First confirm D1 is available under the account's existing plan and that no paid upgrade is required.

From this directory:

```bash
npm install
npx wrangler login
npx wrangler d1 create t2s-usage
```

Copy the `database_id` returned by Wrangler into `wrangler.toml`, replacing the all-zero placeholder. The database name must remain `t2s-usage`. Then apply the checked-in schema:

```bash
npx wrangler d1 migrations apply t2s-usage --remote
```

The deploy workflow also applies pending migrations before deploying. It refuses to deploy while the placeholder database ID remains configured.

## Configure provider secrets

From this directory, set secrets through Wrangler's interactive prompts:

```bash
npx wrangler secret put VAKYAM_API_KEY
npx wrangler secret put INDIA_API_KEY
```

Vakyam is required for Vakyam routes. India TTS currently accepts keyless requests, but its provider notice says a key will be required from 15 December 2026. Never commit provider keys or paste them into chat.

## Firebase App Check setup

In Firebase Console, register the Android app with Play Integrity and configure the Play Integrity provider. Add the release signing certificate fingerprints used by the release APK. For local debug builds, obtain the debug App Check token from Logcat and register that token in Firebase Console. Keep debug tokens private.

The Android client obtains a fresh App Check token for each Worker request. Firebase callable Functions also enforce App Check in their function definitions. Do not turn off App Check enforcement to work around a failed request.

## Local checks

```bash
npm install
npm run typecheck
npm test
npx wrangler deploy --dry-run --outdir .wrangler-build
npm run dev
```

## Deployment

The repository includes **Actions → Deploy Cloudflare TTS Worker**. Before running it:

1. Complete the D1 setup above and commit the real database ID in `wrangler.toml`.
2. Configure `VAKYAM_API_KEY` and, when available, `INDIA_API_KEY` as Cloudflare Worker secrets.
3. Add GitHub Actions repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. The token needs permission to manage Workers and D1 migrations for this account.
4. Run the manual deployment workflow on the `develop` branch.

The workflow type-checks the Worker, applies remote D1 migrations, deploys, and verifies that the public health endpoint reports both Vakyam configuration and an active D1 binding. A successful health check does not prove provider credentials are valid or that live audio generation succeeds; test voice listing and speech generation from the Android app.

Never add provider keys to GitHub repository secrets, commit them, or paste them into chat. Keep them in Cloudflare Worker secrets only.

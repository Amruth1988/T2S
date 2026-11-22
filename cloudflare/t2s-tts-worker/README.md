# T2S TTS Cloudflare Worker

Secure proxy for T2S Studio's Vakyam and India TTS providers. Sarvam is deliberately not routed through this Worker.

## Routes
- `GET /health` — public health check
- `GET /v1/india/voices?language=te|hi|en`
- `POST /v1/india/tts` — JSON: `text, voice, language, speed, format`
- `GET /v1/vakyam/voices?language=te|hi|en`
- `POST /v1/vakyam/tts` — JSON: `text, voice, language, speed, format`

All provider routes require a Firebase ID token in `Authorization: Bearer <token>`. The Worker verifies it against Firebase project `t2s-studio`. Provider keys are Cloudflare Worker secrets and never shipped in the Android app.

## Configure secrets
From this directory, run:
```bash
npm install
npx wrangler secret put VAKYAM_API_KEY
npx wrangler secret put INDIA_API_KEY
npm run deploy
```
Paste each provider key only into the Wrangler prompt. Never commit keys or paste them into chat. India API currently supports keyless requests, but its published notice says a key will be required from 15 December 2026; configure the free key before then.

## Local checks
```bash
npm install
npm run typecheck
npm run dev
```

## Android integration

The Android app is already configured to call `https://t2s.amruth4official.workers.dev` through `CentralOnlineTtsClient`. It obtains the signed-in user's current Firebase ID token and sends it as `Authorization: Bearer <token>`. The Worker verifies the token's signature, issuer, audience and subject against Firebase project `t2s-studio`. Provider credentials are never included in the Android app.

## Deployment

The repository includes a manual GitHub Actions workflow: **Deploy Cloudflare TTS Worker**. Before running it:

1. Add GitHub Actions repository secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. The API token needs permission to edit/deploy Workers for the target account.
2. In the Cloudflare account, set the Worker secrets from this directory:
   ```bash
   npx wrangler secret put VAKYAM_API_KEY
   npx wrangler secret put INDIA_API_KEY
   ```
   Vakyam is required. India TTS currently accepts keyless requests, but its published notice says a key will be required from 15 December 2026.
3. Run **Actions → Deploy Cloudflare TTS Worker → Run workflow** on the `develop` branch.

The workflow type-checks and deploys the Worker, then checks the public health endpoint. The health response reports only whether each provider secret is configured; it never returns secret values. A successful health check confirms deployment and Vakyam secret presence, not provider account validity or live audio generation. Test both voice-list and speech-generation routes from the Android app after deployment.

Never commit provider keys, add them to GitHub repository secrets, or paste them into chat. Keep provider credentials in Cloudflare Worker secrets only.

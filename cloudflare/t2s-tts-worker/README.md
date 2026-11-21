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

The Android app must be updated to call this Worker URL and attach the current Firebase ID token. Do not deploy until the provider keys have been set as secrets.

# T2S Studio Web

A browser-first companion workspace in the `web/` folder. It is deliberately separate from the Android Gradle project.

## Local development

```bash
cd web
npm install
npm run dev
```

## Build

```bash
npm ci
npm run build
```

## Firebase Web App configuration

Register a **Web app** inside the existing Firebase project `t2s-studio` (do not create another Firebase project). Configure these build-time environment variables in the deployment environment:

- `VITE_FIREBASE_API_KEY`
- `VITE_FIREBASE_AUTH_DOMAIN`
- `VITE_FIREBASE_PROJECT_ID` (must be `t2s-studio`)
- `VITE_FIREBASE_APP_ID` (the registered Web App ID, not the Android App ID)
- `VITE_FIREBASE_APPCHECK_SITE_KEY` (the reCAPTCHA v3 site key registered for Firebase App Check on the Web app)
- `VITE_T2S_WORKER_URL` (optional; defaults to `https://t2s.amruth4official.workers.dev`)

Add the deployed web hostname to Firebase Authentication authorized domains and register the Web app with Firebase App Check using reCAPTCHA v3. Enable the existing Email/Password and Google providers. Add the Web App ID to the existing Cloudflare Worker FIREBASE_APP_IDS variable alongside the Android App ID, then deploy the existing Worker. Do not replace the Android app ID or weaken App Check. Do not commit private credentials. Firebase web API keys and the reCAPTCHA site key are public client configuration, but should be restricted to the required Firebase services.

## Current capability boundary

The browser app includes responsive navigation, browser-installed speech preview, projects and drafts, microphone recording, secure online voice catalogue and speech-generation screens for India API/Vakyam, and Sarvam voice registration/generation through the existing authenticated gateway. Cloud voice functions require the Web Firebase app, App Check site key, authorized domain, and the Web App ID added to the existing Worker allow-list. Firebase sign-in and Firestore draft sync also require the Web App configuration.

Browser speech synthesis does not expose an audio file, so it cannot honestly offer an MP3/WAV export. Browser-installed voices are not the Android voice catalogue. Android-private recordings are not accessible to the browser.

The web app uses the existing Cloudflare Worker for India/Vakyam calls and a Worker-to-Apps-Script proxy for Sarvam. Provider keys remain server-side. This web release does not claim cross-device transfer of Android-private recordings or local browser recordings. Do not bypass App Check, expose provider keys, deploy new backend services, or enable paid billing.

## Hosting

The Vite build outputs to `dist/`. GitHub Pages is configured to publish this static site using the workflow in `.github/workflows/web-pages.yml`. The public site is served from the repository's GitHub Pages hostname after a successful deployment. Firebase's authorized domain must include the final Pages hostname before web authentication can work.

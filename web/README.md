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

Add the deployed web hostname to Firebase Authentication's authorized domains and enable the existing Email/Password and Google providers. Do not commit private credentials. Firebase web API keys are client identifiers, but should still be restricted to the required Firebase APIs.

## Current capability boundary

The initial browser release includes responsive navigation, browser-installed speech preview (English, Telugu and Hindi where the browser provides voices), speed/pitch controls, local projects/drafts, microphone recording, and recording download. Firebase sign-in and Firestore draft sync become available once the Web App configuration and authorized domain are supplied.

Browser speech synthesis does not expose an audio file, so it cannot honestly offer MP3/WAV export. Browser-installed voices are not the Android voice catalogue. Android-private recordings are not accessible to the browser.

Online India/Vakyam TTS, Sarvam cloned voice registration/generation, cloud voice catalogue, and cross-device audio storage are not enabled in this initial scaffold. They require a web App Check registration and a reviewed browser authentication/CORS path for the existing Cloudflare Worker and Apps Script gateway. Do not bypass App Check, expose provider keys, deploy new backend services, or enable paid billing to make these features appear enabled.

## Hosting

The Vite build outputs to `dist/`. The repository's Pages workflow can publish this static site after GitHub Pages is configured to use GitHub Actions. Firebase's authorized domain must then include the final Pages hostname.

# T2S Sarvam Apps Script gateway (Firebase Spark)

This keeps the Sarvam key server-side without deploying Firebase Cloud Functions or changing Firebase billing.

## Setup
1. Create a standalone project at https://script.google.com/ named T2S Sarvam Gateway.
2. Paste Code.gs from this folder.
3. Project Settings → Script Properties: add SARVAM_API_KEY (your private Sarvam key) and FIREBASE_WEB_API_KEY (current_key from app/google-services.json for project t2s-studio).
4. Deploy → New deployment → Web app. Execute as Me; access Anyone. Authorize UrlFetch and copy the /exec URL.
5. The app must be configured with this URL in SarvamVoiceCloneClient.kt (SCRIPT_URL). The URL is an endpoint identifier, not a secret.

## Security
The gateway verifies each Firebase ID token with Firebase Identity Toolkit before using the Sarvam key. The key is stored only in Script Properties, never in the APK or Firestore. Daily limits per Firebase user: 10 generations/10,000 characters and 3 voice registrations. Apps Script quotas still apply. Sarvam voice cloning can incur Sarvam charges; this does not change Firebase billing.

Only clone a voice with its speaker's explicit consent. Never share Script Properties or grant script-editor access to app users.

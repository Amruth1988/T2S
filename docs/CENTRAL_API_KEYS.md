# T2S Studio central provider keys (develop)

## Security model
- Provider secrets live in Google Secret Manager, not Firestore, Remote Config, source control, or the APK.
- Callable functions require Firebase Authentication, the `admin: true` custom claim, and Firebase App Check.
- The Android client submits a provider key only to the authenticated admin callable and receives a saved/status response; status never returns secret material.
- Firestore's existing deny-by-default rules remain in place. Do not add client-readable key documents.
- Never log request data, provider keys, authorization headers, or provider responses that may echo credentials.

## Required one-time cloud setup (not performed by this code change)
1. Confirm Firebase project `t2s-studio` and explicitly approve Blaze billing before deploying Cloud Functions. No billing plan is changed by this repository.
2. Create Secret Manager secrets in `asia-south1`: `t2s-sarvam-api-key`, `t2s-vakyam-api-key`, `t2s-india-tts-api-key`.
3. Grant the deployed Functions runtime service account `roles/secretmanager.secretVersionAdder` on those three secrets only. Do not grant project-wide Secret Manager Admin.
4. Set the T2S owner account's Firebase Auth custom claim `admin: true` using a trusted Admin SDK environment. Never let the app set its own claim.
5. Enable Firebase App Check for the Android app and configure its production provider before callable functions are used.
6. Install dependencies and deploy from this directory using the Firebase CLI after reviewing project/billing:
   `cd functions && npm ci && npm run deploy`

## Key migration
- Existing device-local encrypted keys remain untouched.
- After backend deployment, the admin enters each existing key once in the Admin-only central-key screen; the callable stores a new Secret Manager version.
- Verify the provider status reports configured. Then test each provider through a server-side provider proxy before optionally removing local copies.
- Do not automatically upload users' local keys, read them without consent, or delete them during migration.
- Rotation appends a new Secret Manager version. Disable/revoke old provider keys in the provider dashboard after successful validation.

## Current scope and limitations
This first backend slice implements admin-only secret ingestion and non-sensitive configuration status. It deliberately does not return central keys to Android. Provider generation/voice-registration requests must be migrated to authenticated callable/HTTP proxy functions before end users can use central keys without personal keys. That proxy work is a separate stage and must not be bypassed by fetching secret values into the app.

# T2S Sarvam Apps Script gateway (Firebase Spark)

This is the active Sarvam backend for the Android app. It keeps the Sarvam key server-side without deploying Firebase Cloud Functions or changing Firebase billing. The gateway has already been deployed; the Android client uses the existing `/exec` URL.

## Existing deployment

- Reuse the existing Google Apps Script project and deployment. Do not create a duplicate project.
- Keep the existing Script Properties: `SARVAM_API_KEY` and `FIREBASE_WEB_API_KEY`.
- The endpoint URL is already configured in `SarvamVoiceCloneClient.kt`; it is an endpoint identifier, not a secret.
- If `Code.gs` is changed in a future update, update the existing deployment using Deploy → Manage deployments → Edit → New version → Deploy. Keep the same URL and Script Properties.

## Security

The gateway verifies each Firebase ID token with Firebase Identity Toolkit before using the Sarvam key. The key is stored only in Script Properties, never in the APK or Firestore. Daily limits per Firebase user: 10 successful generations/10,000 characters and 3 successful voice registrations. Quota reservations are locked atomically before provider calls, rolled back on failure, and abandoned reservations expire after 10 minutes. Apps Script quotas still apply. Sarvam voice cloning can incur Sarvam charges; this does not change Firebase billing.

Only clone a voice with the speaker's explicit consent. Never share Script Properties or grant script-editor access to app users.

## Recovering a user's exhausted registration allowance

The gateway counts only successful provider registrations/generations in the latest code. Older versions counted failed attempts too. To restore one user's registration allowance immediately, find that user's Firebase Authentication UID in Firebase Console → Authentication → Users. In the Apps Script editor, select and run `adminResetVoiceRegistrationQuota` after temporarily adding a one-line wrapper with that UID, for example `function runOneTimeQuotaReset(){ adminResetVoiceRegistrationQuota("FIREBASE_UID_HERE"); }`. Run it as the script owner, then remove the temporary wrapper and save. This resets only that user's current UTC day's registration count; it does not clear API keys or generation counters. Never publish the UID or share editor access.

### Quota counter migration

The gateway uses `quota:v3:` counters with timestamped in-flight reservations. On first use for a UTC day, it imports existing counters from that user's `quota:v2:` key so successful usage already recorded that day is not lost. Failed attempts from older versions may have been counted; they remain in the carried-forward count until the next UTC day. The owner-only reset helper targets the `quota:v3:` key.

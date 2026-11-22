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

## Updating an existing deployment

When `scripts/sarvam-apps-script/Code.gs` changes, the repository commit does not update the separately deployed Google Apps Script automatically. Open the existing Apps Script project, replace `Code.gs` with the current repository version, save, then Deploy → Manage deployments → Edit the web-app deployment → select **New version** → Deploy. Keep the existing `/exec` deployment URL and Script Properties (`SARVAM_API_KEY`, `FIREBASE_WEB_API_KEY`) unchanged. Do not create a new project or expose Script Properties.

## Recovering a user's exhausted registration allowance

The gateway counts only successful provider registrations/generations in the latest code. Older versions counted failed attempts too. To restore one user's registration allowance immediately, find that user's Firebase Authentication UID in Firebase Console → Authentication → Users. In the Apps Script editor, select and run `adminResetVoiceRegistrationQuota` after temporarily adding a one-line wrapper with that UID, for example `function runOneTimeQuotaReset(){ adminResetVoiceRegistrationQuota("FIREBASE_UID_HERE"); }`. Run it as the script owner, then remove the temporary wrapper and save. This resets only that user's current UTC day's registration count; it does not clear API keys or generation counters. Never publish the UID or share editor access.

### Legacy quota migration

The current gateway stores counters under the versioned `quota:v2:` namespace. This intentionally ignores counters written by older gateway versions, including failed attempts that were incorrectly counted as registrations. After deploying the latest `Code.gs` as a new web-app version, the user's new registration allowance starts clean automatically; no manual UID lookup or quota-reset helper is needed. The same daily limits still apply to successful operations.

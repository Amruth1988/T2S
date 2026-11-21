# T2S Studio central provider keys

## Current implementation
- Admin-only callable functions accept Sarvam, Vakyam and India TTS keys.
- Each key is encrypted with Google Cloud KMS before any Firestore write.
- Firestore collection: `provider_keys`; document IDs: `sarvam`, `vakyam`, `india`.
- Stored fields: provider ID/name, KMS ciphertext, KMS key version, active state, created/updated/rotated timestamps, and admin UIDs.
- The admin status callable returns names, active/configured state and dates only. It never returns ciphertext or plaintext.
- The Android admin screen supports saving/rotating keys, viewing dates and enabling/disabling providers.
- Firestore client rules explicitly deny all access to `provider_keys`. Cloud Functions use the Admin SDK.
- A backend-only `decryptProviderKey` helper is exported for server-side provider proxy handlers. There is no callable endpoint that returns a decrypted key.

## One-time cloud setup (not performed)
1. Confirm Firebase project `t2s-studio`. Cloud Functions and Cloud KMS may require the Firebase project to use the Blaze billing plan. Do not enable billing without the owner's explicit approval.
2. Enable Cloud KMS API and create a symmetric encryption key ring/key in the same project and a supported region. Use purpose `ENCRYPT_DECRYPT`.
3. Set Functions environment variable `T2S_KMS_KEY_NAME` to the full KMS CryptoKey resource name:
   `projects/PROJECT_ID/locations/LOCATION/keyRings/KEY_RING/cryptoKeys/KEY_NAME`.
4. Grant the deployed Functions runtime service account only `roles/cloudkms.cryptoKeyEncrypterDecrypter` on that CryptoKey.
5. Ensure Firestore is initialized in the Firebase project.
6. Grant the T2S owner account the Firebase Auth custom claim `admin: true` from a trusted Admin SDK environment. The app cannot grant this claim.
7. Configure Firebase App Check for the Android app before enabling callable functions.
8. From the repository root, install Functions dependencies and deploy only after reviewing billing and IAM:
   `cd functions && npm install && npm run deploy`.

## Important integration status
- Keys are centrally stored and encrypted, with metadata and rotation dates.
- Android clients do not download/decrypt shared keys.
- End-user provider calls still need to be routed through authenticated backend proxy handlers before central keys can power speech generation, voice listing or Sarvam voice registration. Until those handlers are implemented and deployed, existing device-local key flows remain in effect.
- Existing device-local keys are not automatically uploaded, read, or deleted. Migrate only keys the administrator deliberately enters in the admin screen.
- Never log key material, authorization headers, ciphertext payloads, or provider responses that may echo credentials.

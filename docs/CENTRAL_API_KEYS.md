# T2S Studio central provider keys

## Encryption design
- The Android admin screen fetches the RSA public key from `getProviderEncryptionPublicKey`.
- Android encrypts the provider API key with RSA-OAEP using SHA-256 for both OAEP and MGF1.
- Only the Base64 ciphertext is submitted to `saveCentralProviderKey` and stored in Firestore.
- The RSA private key is a Firebase Functions Secret Manager secret. It is never shipped in the APK or returned by a callable.
- Firestore client rules deny all direct reads and writes to `provider_keys`.
- The backend-only `decryptProviderKey` helper decrypts credentials for future server-side provider proxy handlers. No callable returns plaintext.

## One-time key-pair setup (owner action; not performed)
Use OpenSSL on a trusted computer. Keep the private key private and back it up securely.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out t2s-provider-private.pem
openssl pkey -in t2s-provider-private.pem -pubout -out t2s-provider-public.pem
```

Set the private key as a Functions secret:
```bash
firebase functions:secrets:set T2S_PROVIDER_PRIVATE_KEY
```
When prompted, paste the complete contents of `t2s-provider-private.pem`.

For the public key, create `functions/.env.t2s-studio` (do not commit it) with:
```
T2S_PROVIDER_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\\n...base64 lines...\\n-----END PUBLIC KEY-----"
```
Replace the example with the actual public PEM, putting literal `\\n` between PEM lines. Firebase Functions parameter configuration reads this file during deploy.

## Firebase requirements
- Confirm project `t2s-studio` and region `asia-south1`.
- Cloud Functions deployment generally requires the Firebase project to be on the Blaze plan and a billing account. This repository change does not enable billing or deploy anything. Do not upgrade or enable billing without the owner's explicit approval.
- Set up Firebase Authentication, grant the owner account the `admin: true` custom claim using a trusted Admin SDK environment, and configure Firebase App Check for Android.
- Deploy only after the project owner has approved the billing implications and reviewed IAM:
  `cd functions && npm install && npm run deploy`.

## Current scope and limitations
- The Android admin key-save flow now performs encryption before upload.
- Existing KMS-encrypted documents from the previous prototype are not compatible with RSA-OAEP. Re-enter/rotate those credentials after the new backend is configured; do not delete old data until verified.
- The backend helper is ready for server-side provider integration, but provider voice-list, speech-generation and Sarvam registration proxy endpoints are not yet implemented. Until those endpoints are built and deployed, existing device-local provider key flows remain active.
- No provider key, plaintext or ciphertext should be written to logs.
- RSA-2048 OAEP-SHA256 supports plaintext up to 190 bytes. Provider API keys longer than this must use a hybrid envelope (AES-GCM data key wrapped by RSA) before enabling such a provider.

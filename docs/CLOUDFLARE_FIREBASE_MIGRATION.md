# Firebase Functions to Cloudflare migration plan

Status: implementation in progress on `feat/cloudflare-sarvam-migration`.

## Scope and non-negotiable constraints

- Keep the Firebase project on its existing free plan. Never enable Blaze, attach billing, or deploy Firebase Functions.
- Keep Firebase Authentication, Firestore user data, Firebase App Check, and the Android package `com.t2sstudio.app`.
- Keep `main` unchanged until the migration is reviewed, tested, and explicitly approved for merge.
- Keep provider credentials out of Android, Git, D1 plaintext fields, logs, and user-visible errors.
- Preserve authentication and authorization on every private endpoint: verify Firebase ID token signature, issuer, audience, expiry; verify App Check signature, issuer, audience, expiry, token type, and exact Firebase Android App ID.
- Require the Firebase custom claim `admin: true` for provider credential administration and quota configuration. Never trust a client-supplied admin flag.
- Preserve fail-closed usage controls: 10,000 characters/provider/account/UTC day, 100,000 characters/provider/account/UTC month, and 30 requests/provider/account/UTC day. Reserve usage atomically before provider calls; failed upstream calls remain charged.
- Do not change Cloudflare plan or enable paid services. Use the already configured Worker and D1 binding only.

## Current architecture confirmed on main

- India TTS and Vakyam TTS already route through the Cloudflare Worker and D1.
- Sarvam voice registration, voice listing, cloned speech generation, provider-key administration, and provider-wide quota controls currently use Firebase callable Functions.
- Account deletion currently calls a Firebase callable that clears Worker quota rows, recursively deletes Firestore user data, deletes usage rows, and then deletes the Firebase Auth identity.
- The Firebase Functions GitHub workflow only compiles TypeScript; it does not deploy or verify live Functions.

## Target architecture

### Cloudflare Worker

Add authenticated routes for:
- `GET /v1/sarvam/voices`
- `POST /v1/sarvam/voices` (consented sample registration)
- `POST /v1/sarvam/tts` (cloned speech generation)
- `GET /v1/admin/providers`
- `PUT /v1/admin/providers/:provider/key`
- `PATCH /v1/admin/providers/:provider/status`
- `PUT /v1/admin/providers/:provider/limits`
- `DELETE /v1/account/data` (account deletion orchestration; only after parity and safety review)

The admin endpoints must verify the Firebase `admin: true` custom claim server-side. Do not add a client-provided admin parameter.

### Credential storage

Use a server-only encryption boundary. The Android app must encrypt provider keys with an RSA-OAEP-SHA256 public key before upload. The Worker must decrypt only in memory using a Cloudflare Worker secret private key. D1 may store only ciphertext and non-secret metadata. Never return ciphertext or plaintext from status/list endpoints. Prefer per-provider active flags and rotation timestamps. The public key must be supplied as a non-secret Worker variable; the private key must be a Worker secret.

### Quota storage

Extend D1 with provider-wide daily/monthly/request ceilings and atomic per-account/provider reservations. Keep hashed Firebase UIDs only. Admin limits must be positive safe integers before enabling a provider. Reject generation when no valid enabled provider budget exists. Keep existing per-account ceilings in force even if provider-wide limits are configured.

### Account deletion

Do not remove the Firebase callable from the Android flow until a replacement is implemented and tested. The replacement must:
1. Require a fresh Firebase sign-in (auth_time no older than five minutes).
2. Verify Firebase ID token and App Check.
3. Delete only the caller's hashed Worker quota records.
4. Delete all Firestore data under `users/{uid}`, including nested collections, using a supported trusted server-side mechanism.
5. Delete the caller's Firebase Authentication identity only after data cleanup succeeds.
6. Stop and return a retryable error if any cleanup step fails; never delete the Auth identity before data cleanup completes.

If safe recursive Firestore deletion and Auth user deletion cannot be performed on the existing free plan, leave account deletion on the existing callable path and clearly mark it as requiring a separately approved backend option. Do not weaken cleanup or security to avoid billing.

## Implementation and verification sequence

1. Add D1 migration(s) and tested SQL for provider credential metadata, provider-wide limits, and atomic quota reservations.
2. Add Worker JWT/App Check/admin verification helpers and unit tests, including expired/wrong-project/wrong-app/non-admin cases.
3. Add Sarvam routes and tests for input validation, provider failures, invalid payloads, quotas, and credential secrecy.
4. Migrate Android Sarvam client from callable Functions to authenticated Worker HTTP endpoints.
5. Migrate admin UI key/status/limits operations to Worker endpoints while retaining RSA-OAEP-SHA256 encryption.
6. Evaluate account deletion parity. Do not switch the Android deletion flow until the full safety contract is met.
7. Run Worker typecheck/tests/dry-run and Android build. Inspect all diffs and CI logs.
8. Open a draft PR; do not merge or deploy without explicit approval.

## Release and live-account boundaries

Repository CI can validate source and tests but cannot prove Cloudflare secrets, remote D1 schema, Firebase custom claims, or live provider credentials. Never claim live verification from a passing build. No production deployment, billing change, plan upgrade, or merge is authorized by this migration request.

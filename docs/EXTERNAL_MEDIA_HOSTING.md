# T2S external media hosting architecture

## Billing boundary

Firebase remains on Spark. The media delivery path does not use Firebase Storage and does not require a Firebase plan change. Authentication and existing Firestore metadata remain unchanged.

## Components

- Firebase Auth / Firestore: user identity, app data, optional catalogue metadata.
- Static HTTPS catalogue: public, versioned `manifest.json`.
- Cloudflare Pages Free: primary static host for previews and smaller files.
- GitHub Releases: optional mirror for larger voice archives.
- `ExternalMediaClient`: provider-neutral Android manifest parser, HTTPS downloader, mirror fallback, SHA-256 validation and app-private cache.

## Android integration contract

Call `ExternalMediaClient.fetchCatalogue(manifestUrl)` and `ExternalMediaClient.download(context, asset)` from a background dispatcher or worker. Do not call network methods on the main thread. The returned file is app-private; use it as a preview source or pass a validated archive to the existing voice-pack importer.

The client intentionally does not upload user audio, request Firebase Storage, embed host credentials, or configure billing. It rejects non-HTTPS URLs and redirects, bounds manifest/download sizes, verifies exact byte count and SHA-256, and only promotes a completed verified file.

## Deployment still requiring account-owner action

A Cloudflare Pages project cannot be created from this repository integration without the account owner's Cloudflare authorization. Create a separate public media repository, connect it to Cloudflare Pages, and publish the `media-catalog` directory contents as the site root. Then configure the final HTTPS manifest URL in the app. No account credentials should be committed.

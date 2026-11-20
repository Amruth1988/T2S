# T2S external media catalogue

This folder is the deployable static-media site root. It contains only public assets and a versioned JSON catalogue. Do not put credentials, private user recordings, Firebase configuration secrets, or copyrighted voice models here without redistribution permission.

## Hosting

1. Create a separate public repository for T2S media assets (recommended name: `T2S-Media`).
2. Copy the contents of this folder into that repository's root.
3. In Cloudflare Pages, create a project connected to that repository. Use no build command and set the output directory to `/` (or the repository root, as shown by the Pages setup UI).
4. Publish the resulting HTTPS site URL as the catalogue base URL in the Android app configuration.
5. Keep GitHub Releases as an optional second URL for larger packs; each asset's `urls` array is tried in order.

Cloudflare account/project creation and DNS setup require the account owner. No Firebase settings or billing are involved.

## Manifest schema

Each item in `manifest.json` must include:
- `id`: stable identifier, unique in this manifest
- `title`, `language`, `kind` (for example `preview_audio` or `voice_pack`)
- `version`: positive integer
- `sizeBytes`: exact downloaded file size
- `sha256`: lowercase SHA-256 hex digest
- `urls`: ordered HTTPS mirror URLs
- `license`: licence name and/or attribution reference

Example (replace every example URL and checksum with real published asset values):

```json
{
  "schemaVersion": 1,
  "catalogueVersion": 1,
  "updatedAt": "2026-11-20T00:00:00Z",
  "assets": [{
    "id": "telugu-voice-preview-01",
    "title": "Telugu voice preview",
    "language": "te-IN",
    "kind": "preview_audio",
    "version": 1,
    "sizeBytes": 12345,
    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "urls": [
      "https://media.example.com/audio/telugu-preview.mp3",
      "https://github.com/OWNER/T2S-Media/releases/download/v1/telugu-preview.mp3"
    ],
    "license": "Replace with verified redistribution licence"
  }]
}
```

The sample above is documentation only; its URL and hash are placeholders and must not be shipped as a real asset.

## Safety and delivery rules

- Use HTTPS URLs only. The app rejects HTTP.
- Keep each static Pages file within the provider's current per-file limit. Put larger model archives in GitHub Releases or another approved mirror.
- Publish immutable, versioned asset paths; update the manifest only after every mirror is live.
- Calculate exact file size and SHA-256 after upload.
- The Android client validates size and SHA-256, uses app-private storage, and tries mirrors in order.
- User-generated recordings remain private and local by default. Never upload them to this public catalogue.
- Check model and voice licences before redistributing. Do not publish third-party voice models without explicit redistribution rights.

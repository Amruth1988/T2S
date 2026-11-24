# T2S Studio 1.0 — release notes (prepared; final device sign-off pending)

## Included

- Android text-to-speech workspace for English, Telugu and Hindi.
- Guest access to Home and local audio creation using voices already installed in Android; guest output is saved locally as WAV.
- Signed-in account workspace with account-scoped projects, drafts, saved audio history and My Voices profiles.
- Authenticated online TTS routing for India TTS and Vakyam through the Cloudflare Worker, with Firebase Authentication and App Check validation.
- Sarvam voice registration and cloned speech through authenticated Firebase callable Functions.
- Per-account usage reservations and fail-closed limits for centrally managed online providers.
- Account data export ZIP and recent-auth protected account deletion, including account-owned local files and quota records.

## Privacy and safety notes

- Script text is sent to the selected online speech provider when online generation is used.
- Voice samples are sent to Sarvam only when the user explicitly registers a voice for cloning. Provider-hosted voice records may require separate provider-side deletion.
- Guest offline TTS uses Android's installed system voices; no bundled Piper Telugu model is enabled in this release.
- Files copied to public Downloads or shared outside the app are not removed by in-app account deletion.

## Release conditions

This document is prepared for the first release candidate. Do not publish a production release until the final CI run, signing verification, Firebase/Cloudflare owner configuration, licence review and the separately scheduled real-device test batch have passed.

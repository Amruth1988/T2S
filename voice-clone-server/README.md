# T2S Studio self-hosted voice cloning (preview)

This optional service powers genuine reference-audio voice cloning for Telugu and English. The Android app's normal Android TTS and offline Telugu Piper paths remain available without this service.

## Requirements

- Linux host with NVIDIA CUDA and a GPU with approximately 8 GB VRAM.
- Docker with NVIDIA Container Toolkit, or Python 3.10+ and a compatible CUDA PyTorch runtime.
- Network access for the first model download. Model weights are cached on the host.
- Use a private network or HTTPS reverse proxy. Do not expose an unauthenticated clone endpoint to the public internet.

## Run

From this directory:

```bash
docker build -t t2s-voice-clone .
docker run --rm --gpus all -p 8080:8080 \
  -e T2S_ALLOWED_ORIGINS='*' \
  -v t2s-volt-cache:/root/.cache/huggingface \
  t2s-voice-clone
```

Check `GET /health`. The first generation downloads and loads Volt-TTS and can take time.

## API

`POST /v1/clone/tts` as multipart form data:

- `text`: text to synthesize (max 3,000 characters)
- `language`: `te` (Telugu) or `en` (English)
- `audio`: reference voice recording (5–15 seconds recommended, max 20 MB)
- `ref_text`: optional exact transcript of the reference recording
- `speed`: reserved for app compatibility; current Volt-TTS inference wrapper uses model defaults

Returns WAV audio. WAV, MP3, M4A, and FLAC reference uploads are normalized to mono 24 kHz WAV before inference. Temporary files are removed automatically after each request; the original recording is not retained. Production deployments should add authentication, rate limits, TLS and user-specific access controls.

## Model and licence

This server uses `openarcane/Volt-TTS`, an Apache-2.0 preview fine-tune of Qwen3-TTS. It supports 25 Indian languages including Telugu and includes English generation examples. Voice cloning uses a 5–15 second reference clip. Quality varies; test Telugu and English recordings with native speakers before relying on output.

This is self-hosted software, not a free hosted GPU. The GPU host, electricity and internet connection are the operator's responsibility. No paid TTS API or Firebase billing is used by this project.

Only upload a voice recording when the speaker has explicitly agreed to voice cloning. Never clone another person's voice without permission.

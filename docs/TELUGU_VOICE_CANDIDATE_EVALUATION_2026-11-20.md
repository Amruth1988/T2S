# Telugu voice candidate evaluation — 2026-11-20

## SYSPIN Telugu male (bolo conversion)

Candidate: https://huggingface.co/codebucketsolutions/bolo-syspin-telugu-male

- Model card identifies it as a Telugu male Coqui VITS checkpoint converted to ONNX for the bolo / sherpa-onnx ecosystem.
- Published weights are MIT; training corpus is CC BY 4.0.
- Provenance points to SYSPIN Telugu Male, SPIRE Lab, IISc Bengaluru.
- Repository contains `model.onnx`, `tokens.txt`, `voice.json`, `LICENSE`, and `ATTRIBUTION`.
- Approximate repository size: 114 MB.

### T2S decision

Do not add it to the existing Piper voice-pack importer or claim it works in T2S yet. It is a Coqui VITS conversion, not a Piper voice. The current T2S pipeline explicitly uses Piper phonemization and Piper-specific metadata conversion. A separate adapter and phonemization path may be required. The exact `voice.json` schema and Kotlin runtime compatibility still need direct inspection and a real synthesis test.

## Hima Telugu Nano ONNX

Candidate: https://huggingface.co/Prahaladha/hima-tts-nano-te-ONNX

- Model card describes six voices: Bhavana, Lakshmi, Sravani, Srinivas, Karthik, and Pranay.
- The model uses a multi-graph ONNX architecture plus tokenizer/configuration and voice adapters.
- The repository metadata currently reports license `other`; no redistribution permission has been established.

### T2S decision

Do not redistribute or package it until the exact license terms are clarified. Its multi-graph runtime is not compatible with the current single-model Piper VITS adapter without a separate implementation. Treat it as research only.

## DhVaani-0.5 MNN — Android zero-shot TTS candidate

Candidate model: https://huggingface.co/ARTPARK-IISc/DhVaani-0.5  
Android MNN port: https://huggingface.co/Bbkblo/DhVaani-0.5-MNN

- The official DhVaani model card lists Telugu among its well-represented languages and describes zero-shot synthesis from a reference recording plus its exact transcript.
- The Android MNN port advertises a complete Kotlin/JNI/C++ integration and Apache-2.0 metadata. Its model assets total approximately 140 MB.
- The port's documented prompt requirement is a clean 3–10 second single-speaker recording with an exact transcript. It reports Android builds for arm64-v8a and armeabi-v7a.
- This is a voice-cloning engine, not a ready-made priest voice. A Vedic-recitation sample would need to come from a speaker who has explicitly consented to this use. The generated output can only imitate the provided sample's delivery to the extent the model preserves it.
- The official model repository is gated behind acceptance of its access conditions. The MNN port is a third-party conversion with a single published contributor/commit, so the conversion, dependency and model provenance need an independent review before bundling.
- Training-corpus terms still need to be respected even though the model card reports Apache-2.0.

### T2S decision

Promising Android-local candidate; not yet integrated. Do not label it as a Purohit model or enable it in production until the MNN source and binaries are reviewed, the model assets are reproducibly obtained, a consent-based reference-audio/transcript flow is added, and the result passes a real-device Telugu recitation listening test. Do not use a public figure's recording or the Chaganti CC BY-NC dataset as a substitute for explicit voice-owner consent.

## Release rule

A candidate becomes a selectable T2S voice only after:
1. License and attribution are verified for redistribution.
2. Exact model files and configuration are inspected.
3. Android ABI/runtime compatibility is demonstrated.
4. Telugu text synthesis is tested on-device, with audio quality checked by a Telugu speaker.
5. It is shown as a distinct voice based on its actual model identity, not pitch shifting.

No Purohit / Vedic recitation model was validated in this evaluation.

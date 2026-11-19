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

## Release rule

A candidate becomes a selectable T2S voice only after:
1. License and attribution are verified for redistribution.
2. Exact model files and configuration are inspected.
3. Android ABI/runtime compatibility is demonstrated.
4. Telugu text synthesis is tested on-device, with audio quality checked by a Telugu speaker.
5. It is shown as a distinct voice based on its actual model identity, not pitch shifting.

No Purohit / Vedic recitation model was validated in this evaluation.

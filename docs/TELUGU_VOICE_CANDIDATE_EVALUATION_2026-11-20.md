# Telugu voice candidate evaluation — 2026-11-20

## SYSPIN Telugu male (Bolo conversion)

Candidate: https://huggingface.co/codebucketsolutions/bolo-syspin-telugu-male

- Model card states MIT for the converted weights and CC BY 4.0 for the SYSPIN training corpus.
- Attribution identifies the source checkpoint as [SYSPIN/vits_Telugu_Male](https://huggingface.co/SYSPIN/vits_Telugu_Male), revision cf04058b94123801f7742ebe1b604af160c4f677, by SPIRE Lab, IISc Bengaluru.
- The Bolo model card says this is a format conversion; the weights were not retrained.
- T2S has a pinned download and Python Sherpa-ONNX character-input smoke test.

### T2S decision

Do not add this voice to the Piper archive importer. It is a Coqui VITS/character-input model, not a Piper phoneme model. A separate Android adapter, token/normalization strategy and phonemization path are required. Python validation does not establish Kotlin Android runtime compatibility or acceptable Telugu pronunciation. Keep it as a candidate until ABI, real-device synthesis and Telugu-speaker listening tests pass.

## Hima Telugu Nano ONNX

Candidate: https://huggingface.co/Prahaladha/hima-tts-nano-te-ONNX

- Model card describes six voices and a multi-graph ONNX architecture with tokenizer/configuration and voice adapters.
- Repository metadata reports other; no clear redistribution permission has been established.

### T2S decision

Research only. Do not package or redistribute until the exact licence terms are clarified and a compatible runtime is implemented and tested.

## DhVaani-0.5 — zero-shot voice cloning

Official model: https://huggingface.co/ARTPARK-IISc/DhVaani-0.5

- Model card lists Telugu among its supported languages and describes synthesis from reference audio plus its transcript.
- Current official model-card metadata declares **CC BY-NC 4.0** and identifies k2-fsa/ZipVoice as the base model.
- In the model authors' licensing discussion, ARTPARK confirms that the pretrained ZipVoice weights derive from a non-commercially licensed base and that this restriction carries into DhVaani-0.5: https://huggingface.co/ARTPARK-IISc/DhVaani-0.5/discussions/1
- A third-party Android MNN conversion does not remove the upstream weight restriction; its code, conversion and model assets also require separate provenance review.

### T2S decision

**Not approved for a commercial T2S release.** Do not bundle or enable DhVaani-0.5 weights unless the rights holder publishes a commercially compatible version or grants written permission. Any future voice-cloning feature must require a clean 3–10 second single-speaker reference, its exact transcript, and explicit informed consent from the speaker.

## Purohit / Vedic recitation

No dedicated Telugu priest-recitation model has been approved. Generic male voices with pitch/speed changes are not Purohit voices and do not guarantee authentic Vedic modulation. The Chaganti discourse dataset previously reviewed is marked CC BY-NC 4.0; do not redistribute it in a commercial release without separate permission. A Sanskrit chant model is not a Telugu priest voice and must not be substituted.

## Release rule

A candidate becomes a selectable T2S voice only after:
1. exact model weights and immutable source revision are recorded;
2. model and dataset licences permit the intended use and redistribution;
3. required attribution and licence notices ship with the model;
4. Android ABI/runtime compatibility is demonstrated;
5. Telugu text normalization and phoneme/token coverage pass;
6. real-device synthesis is tested and audio is reviewed by native Telugu speakers;
7. consent is recorded for any cloned/reference voice.

See [TELUGU_VOICE_MODEL_PROVENANCE.md](TELUGU_VOICE_MODEL_PROVENANCE.md) for the distribution decision.

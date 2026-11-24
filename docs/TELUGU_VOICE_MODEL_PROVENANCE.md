# Telugu voice model provenance and distribution gates

Last reviewed: 2026-11-24

The release workflow now pins the eligible Venkatesh and Padmavathi source files to `rhasspy/piper-voices@ff43c1e79f98a765202dcfb03710851f9ec4ff2e`. Each generated import pack records SHA-256 hashes for its model, model configuration, model card, attribution and CC BY 4.0 legal text in `SOURCE_MANIFEST.sha256`; the ZIP archive also receives a `.sha256` sidecar.

This document separates model-code licensing from training-data/model-weight rights. A repository-level MIT badge does not override the licence stated by an individual voice's model card or the upstream dataset terms. This is a technical provenance review, not legal advice.

## Telugu Piper voices

| Voice | Model / dataset provenance | Stated terms | T2S release decision |
|---|---|---|---|
| Venkatesh | [Piper model card](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/venkatesh/medium/MODEL_CARD); trained by [PravalX](https://github.com/PravalX); dataset [AI4Bharat IndicVoices-R](https://huggingface.co/datasets/ai4bharat/indicvoices_r). | Model card states CC BY 4.0 for the dataset. The dataset card also states CC BY 4.0. Preserve attribution, licence, notices and dataset citation. | Eligible for a clearly attributed validation/import pack; not yet cleared by real-device audio testing. |
| Padmavathi | [Piper model card](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/padmavathi/medium/MODEL_CARD); trained by [PravalX](https://github.com/PravalX); dataset [AI4Bharat IndicVoices-R](https://huggingface.co/datasets/ai4bharat/indicvoices_r). | Model card states CC BY 4.0. The dataset card states CC BY 4.0. Preserve attribution, licence, notices and dataset citation. | Eligible for a clearly attributed validation/import pack; not yet cleared by real-device audio testing. |
| Maya | [Piper model card](https://huggingface.co/rhasspy/piper-voices/blob/main/te/te_IN/maya/medium/MODEL_CARD) points to the [IIT Madras IndicTTS licence](https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf). | The exact IITM terms govern this voice. The IITM voice page requires acceptance of its licence before download. The exact commercial redistribution and derivative-model conditions have not been independently confirmed for this converted model. | **Validation only. Excluded from public import artifacts and the app's accepted voice-pack list until the exact licence and redistribution rights are verified.** |

### Attribution retained for Venkatesh and Padmavathi

The model cards identify the trainer as PravalX and the dataset as AI4Bharat IndicVoices-R under CC BY 4.0. Each distributed pack must carry:
- its original MODEL_CARD;
- an ATTRIBUTION.md identifying the voice, trainer, dataset and upstream model-card URL;
- the complete CC BY 4.0 legal code or an equally clear licence copy/link;
- the dataset citation and upstream provenance links.

The Piper voices repository's top-level MIT badge describes the repository/code and must not be used as a substitute for the individual voice's dataset licence.

## SYSPIN Telugu male candidate

[bolo-syspin-telugu-male](https://huggingface.co/codebucketsolutions/bolo-syspin-telugu-male) identifies its weights as MIT and its training corpus as CC BY 4.0. Its attribution points to [SYSPIN/vits_Telugu_Male](https://huggingface.co/SYSPIN/vits_Telugu_Male), revision cf04058b94123801f7742ebe1b604af160c4f677, from SPIRE Lab, IISc Bengaluru. The Bolo repository says it is a format conversion, not a retraining.

T2S currently has a Python smoke-validation workflow for the pinned ONNX conversion. It is a Coqui VITS/character-input model, not a Piper model; it is not accepted by the current Piper pack importer. Do not present it as an installed T2S voice until a separate Android adapter, phonemization/tokenization path, ABI compatibility, real-device synthesis and listening review pass.

## DhVaani and voice-cloning candidates

The official [ARTPARK DhVaani-0.5 model card](https://huggingface.co/ARTPARK-IISc/DhVaani-0.5) currently declares CC BY-NC 4.0 and identifies ZipVoice as its base. The authors' [licensing clarification](https://huggingface.co/ARTPARK-IISc/DhVaani-0.5/discussions/1) states that the fine-tuned weights inherit a non-commercial restriction from the pretrained base. **Do not use or redistribute these weights in a commercial T2S release.** An MNN conversion's separate code licence does not remove the upstream model-weight restriction.

Any future voice-cloning flow must use a clean reference recording and exact transcript supplied with the speaker's explicit, informed consent for this specific use. Do not use public-figure, celebrity, priest or other third-party recordings without permission. A cloned reference voice is not automatically a Purohit/Vedic voice.

## Purohit / Vedic recitation

No dedicated Telugu Purohit/Vedic-recitation model has passed T2S's model, licence, Android and listening gates. Pitch/speed presets applied to a generic Android voice must remain labelled as presets, not as a priest-recitation model. The Chaganti discourse dataset previously reviewed is marked CC BY-NC 4.0 and is not an acceptable commercial redistribution source without separate permission. Sanskrit chant models are not substitutes for Telugu priest recitation.

## Distribution gate

Before publishing any voice weights:
1. retain the exact model card, dataset licence, attribution, required notices and source revision;
2. verify commercial use, derivative-model and redistribution permission for the exact weights and training data;
3. pin model downloads to immutable revisions and record checksums;
4. validate conversion and synthesis with the exact Android Sherpa-ONNX and phonemizer versions;
5. verify both supported Android ABIs and perform real-device playback;
6. have native Telugu speakers review intelligibility, pronunciation, prosody and clipping;
7. ship the model's attribution and licence materials inside the same archive as its weights.

A successful Python smoke test or APK build does not satisfy the Android/device or audio-quality gates.

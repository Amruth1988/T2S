# Telugu voice model provenance and distribution gates

This file records the upstream provenance of Telugu models currently referenced by the validation workflow. Passing conversion/synthesis CI does not itself grant redistribution rights or prove Android runtime compatibility.

## Piper Telugu voices

| Voice | Upstream model | Dataset / stated licence | T2S status |
|---|---|---|---|
| Venkatesh | [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices/tree/main/te/te_IN/venkatesh) | The Piper model card identifies the Venkatesh dataset; the upstream Piper voice catalogue groups this model under CC BY-SA. Obtain and retain the authoritative dataset licence and attribution before shipping weights. | Converted and Python synthesis validated; Android runtime/device playback not yet verified. |
| Padmavathi | [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices/tree/main/te/te_IN/padmavathi) | IndicVoices-R dataset, CC BY 4.0 per model card. Preserve attribution and licence notice. | Converted and Python synthesis validated; Android runtime/device playback not yet verified. |
| Maya | [rhasspy/piper-voices](https://huggingface.co/rhasspy/piper-voices/tree/main/te/te_IN/maya) | IndicTTS dataset; model card points to the IIT Madras dataset licence. The exact applicable terms must be reviewed before redistributing the model. | Converted and Python synthesis validated; Android runtime/device playback not yet verified. |

The upstream repository's MIT licence does not replace the individual dataset licence attached to each voice. Do not bundle or redistribute a voice until its own dataset terms and attribution requirements have been reviewed.

## Additional male Telugu candidate

[SYSPIN Telugu Male ONNX conversion](https://huggingface.co/codebucketsolutions/bolo-syspin-telugu-male) is published as an ONNX/Sherpa-ONNX Telugu male voice. Its model card states MIT for weights and CC BY 4.0 for the SYSPIN training corpus, and attributes the source to IISc Bengaluru. It is a candidate for a second distinct male voice, but it has not yet been tested with T2S's exact Piper phonemizer + Sherpa VITS configuration. Do not list it as an installed/working T2S voice until that compatibility test passes.

## Purohit / Vedic recitation

No Telugu priest-recitation model has been approved for this app. Generic male voices with pitch or speed changes are not Purohit voices and must not be presented as such. The Chaganti discourse dataset found during research is marked CC BY-NC 4.0, so it is not suitable for commercial redistribution without separate permission. A Sanskrit chant model is not a Telugu priest voice and must not be substituted.

The Vāgdhenu project was also reviewed. It is a metered Sanskrit classical-chant system, not a Telugu Purohit model; its published model notes that it does not produce Vedic svaras. It requires a CUDA 12.1/Python inference stack and is not an Android Sherpa-ONNX voice pack. It is therefore not substituted into T2S.

## Release gate

Before any voice pack is shipped in a public release:
1. retain the model card, dataset licence, attribution and required notices;
2. verify commercial-use and redistribution permission for the exact weights and dataset;
3. test the converted model with the exact Android Sherpa-ONNX + phonemizer versions and supported ABIs;
4. verify Telugu text normalization, phoneme coverage, intelligibility and audio output on real devices.

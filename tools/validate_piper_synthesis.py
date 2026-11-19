#!/usr/bin/env python3
"""Validate converted Piper ONNX packs with Sherpa-ONNX and piper-phonemize."""
from __future__ import annotations

import argparse
import struct
import wave
from pathlib import Path

import sherpa_onnx
from piper_phonemize import phonemize_espeak


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--tokens", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--text", required=True)
    args = parser.parse_args()

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=args.model,
                tokens=args.tokens,
            ),
            num_threads=2,
        ),
    )
    if not config.validate():
        raise RuntimeError("Sherpa-ONNX rejected the converted Piper model configuration")

    tts = sherpa_onnx.OfflineTts(config)
    try:
        language = tts.lang or "te"
        phoneme_sentences = phonemize_espeak(args.text, language)
        if not phoneme_sentences:
            raise RuntimeError("Piper phonemizer returned no Telugu phonemes")
        phoneme_codepoints = [[ord(char) for char in sentence] for sentence in phoneme_sentences]
        gen_config = sherpa_onnx.GenerationConfig()
        gen_config.sid = 0
        gen_config.speed = 1.0
        gen_config.phoneme_codepoints = phoneme_codepoints
        audio = tts.generate("", gen_config)
        if audio.samples is None or len(audio.samples) < audio.sample_rate // 4:
            raise RuntimeError("Synthesis returned empty or implausibly short audio")

        pcm = bytearray()
        for sample in audio.samples:
            clipped = max(-1.0, min(1.0, float(sample)))
            pcm.extend(struct.pack("<h", int(clipped * 32767)))
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        with wave.open(str(output), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(audio.sample_rate)
            wav.writeframes(pcm)
        print(f"PASS: {output} ({len(audio.samples) / audio.sample_rate:.2f}s at {audio.sample_rate} Hz)")
    finally:
        del tts


if __name__ == "__main__":
    main()

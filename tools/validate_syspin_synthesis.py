#!/usr/bin/env python3
"""Smoke-test the SYSPIN Telugu character-input VITS model with Sherpa-ONNX."""
from __future__ import annotations

import argparse
import struct
import wave
from pathlib import Path

import sherpa_onnx


def load_tokens(path: Path) -> set[str]:
    symbols: set[str] = set()
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        parts = line.rsplit(" ", 1)
        if len(parts) != 2:
            raise ValueError(f"Malformed token line {line_no}: {line!r}")
        symbol, raw_id = parts
        int(raw_id)
        if symbol in symbols:
            raise ValueError(f"Duplicate token symbol on line {line_no}: {symbol!r}")
        symbols.add(symbol)
    if not symbols:
        raise ValueError("tokens.txt is empty")
    return symbols


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--tokens", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--text",
        default="నమస్కారం, ఈ రోజు వాతావరణం చాలా బాగుంది.",
    )
    args = parser.parse_args()

    if not args.model.is_file() or args.model.stat().st_size < 10_000_000:
        raise RuntimeError("SYSPIN ONNX model is missing or incomplete")
    symbols = load_tokens(args.tokens)
    missing = sorted({char for char in args.text if char not in symbols})
    if missing:
        raise RuntimeError("Text contains characters absent from tokens.txt: " + repr(missing))

    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(args.model),
                tokens=str(args.tokens),
            ),
            num_threads=2,
            provider="cpu",
        ),
    )
    if not config.validate():
        raise RuntimeError("Sherpa-ONNX rejected the SYSPIN VITS configuration")

    tts = sherpa_onnx.OfflineTts(config)
    try:
        if tts.sample_rate != 22050:
            raise RuntimeError(f"Unexpected sample rate: {tts.sample_rate}")
        # SYSPIN declares character input with no phonemiser. Pass Unicode
        # characters directly; do not use the Piper/eSpeak phonemizer here.
        generation = sherpa_onnx.GenerationConfig()
        generation.sid = 0
        generation.speed = 1.0
        generation.phoneme_codepoints = [[ord(char) for char in args.text]]
        audio = tts.generate("", generation)
        if audio.samples is None or len(audio.samples) < audio.sample_rate // 4:
            raise RuntimeError("SYSPIN synthesis returned empty or implausibly short audio")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        pcm = bytearray()
        for sample in audio.samples:
            clipped = max(-1.0, min(1.0, float(sample)))
            pcm.extend(struct.pack("<h", int(clipped * 32767)))
        with wave.open(str(args.output), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(audio.sample_rate)
            wav.writeframes(pcm)
        duration = len(audio.samples) / audio.sample_rate
        print(f"PASS: {args.output} ({duration:.2f}s at {audio.sample_rate} Hz)")
    finally:
        del tts


if __name__ == "__main__":
    main()

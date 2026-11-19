#!/usr/bin/env python3
"""Convert a Piper ONNX voice into the model format expected by Sherpa-ONNX.

Usage:
  python tools/convert_piper_model.py path/to/voice.onnx

The matching config must be next to the model as voice.onnx.json.
Requires: pip install onnx==1.17.0
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import onnx


def convert(model_path: Path) -> Path:
    config_path = Path(str(model_path) + ".json")
    if not model_path.is_file():
        raise FileNotFoundError(model_path)
    if not config_path.is_file():
        raise FileNotFoundError(config_path)

    config = json.loads(config_path.read_text(encoding="utf-8"))
    if config.get("phoneme_type") != "espeak" or config.get("alphabet") != "ipa":
        raise ValueError("Only Piper models with phoneme_type='espeak' and alphabet='ipa' are supported.")

    phoneme_map = config.get("phoneme_id_map")
    if not isinstance(phoneme_map, dict) or not phoneme_map:
        raise ValueError("Piper config has no valid phoneme_id_map.")

    tokens_path = model_path.with_name("tokens.txt")
    with tokens_path.open("w", encoding="utf-8", newline="\n") as output:
        for symbol, ids in phoneme_map.items():
            # Piper's newline entry is not a valid Sherpa token.
            if symbol == "\n":
                continue
            if not isinstance(ids, list) or not ids:
                raise ValueError(f"Invalid token ID entry for {symbol!r}.")
            output.write(f"{symbol} {int(ids[0])}\n")

    language = config.get("language", {}).get("name_english")
    voice = config.get("espeak", {}).get("voice")
    sample_rate = config.get("audio", {}).get("sample_rate")
    speakers = config.get("num_speakers")
    if not all((language, voice, sample_rate, speakers)):
        raise ValueError("Piper config is missing language, espeak voice, sample rate, or speaker count.")

    metadata = {
        "model_type": "vits",
        "comment": "piper",
        "language": language,
        "voice": voice,
        "has_espeak": "1",
        "n_speakers": str(int(speakers)),
        "sample_rate": str(int(sample_rate)),
    }

    model = onnx.load(str(model_path))
    # Replace these keys instead of appending duplicates if conversion is rerun.
    retained = [item for item in model.metadata_props if item.key not in metadata]
    del model.metadata_props[:]
    model.metadata_props.extend(retained)
    for key, value in metadata.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = value
    onnx.checker.check_model(model)
    onnx.save(model, str(model_path))
    return tokens_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path, help="Piper .onnx model; matching .onnx.json must sit beside it")
    args = parser.parse_args()
    tokens = convert(args.model)
    print(f"Converted {args.model}")
    print(f"Generated {tokens}")


if __name__ == "__main__":
    main()

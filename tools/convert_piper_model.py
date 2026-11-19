#!/usr/bin/env python3
"""Convert a Piper ONNX model for Sherpa-ONNX (official Piper conversion format).

Usage:
  python tools/convert_piper_model.py path/to/voice.onnx

The matching config must be beside the model as voice.onnx.json.
Requires: pip install onnx==1.17.0
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import onnx


def convert(model_path: Path) -> tuple[Path, Path]:
    config_path = Path(str(model_path) + ".json")
    if not model_path.is_file():
        raise FileNotFoundError(model_path)
    if not config_path.is_file():
        raise FileNotFoundError(config_path)

    config = json.loads(config_path.read_text(encoding="utf-8"))
    required = ("phoneme_id_map", "language", "espeak", "num_speakers", "audio")
    missing = [key for key in required if key not in config]
    if missing:
        raise ValueError(f"Piper config is missing required fields: {', '.join(missing)}")

    id_map = config["phoneme_id_map"]
    if not isinstance(id_map, dict) or not id_map:
        raise ValueError("Piper config has no valid phoneme_id_map.")

    tokens_path = model_path.with_name("tokens.txt")
    with tokens_path.open("w", encoding="utf-8", newline="\n") as output:
        # Keep every entry exactly as Sherpa-ONNX's official Piper conversion does.
        for symbol, ids in id_map.items():
            if not isinstance(ids, list) or not ids:
                raise ValueError(f"Invalid token ID entry for {symbol!r}.")
            output.write(f"{symbol} {int(ids[0])}\n")

    language = config["language"].get("name_english")
    voice = config["espeak"].get("voice")
    sample_rate = config["audio"].get("sample_rate")
    speakers = config.get("num_speakers")
    if not all((language, voice, sample_rate, speakers)):
        raise ValueError("Piper config is missing language name, eSpeak voice, sample rate, or speaker count.")

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
    # Sherpa's official conversion writes only the metadata required for Piper.
    del model.metadata_props[:]
    for key, value in metadata.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = value
    onnx.checker.check_model(model)
    onnx.save(model, str(model_path))
    return tokens_path, config_path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path, help="Piper .onnx model; matching .onnx.json must sit beside it")
    args = parser.parse_args()
    tokens, config = convert(args.model)
    print(f"Converted {args.model}")
    print(f"Generated {tokens}")
    print(f"Original config retained for audit: {config}")


if __name__ == "__main__":
    main()

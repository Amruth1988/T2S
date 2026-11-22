"""Self-hosted Volt-TTS voice-cloning API for T2S Studio.

Run on a trusted Linux host with an NVIDIA CUDA GPU (about 8 GB VRAM).
Voice recordings are processed in memory and are not persisted by this server.
"""
from __future__ import annotations

import io
import os
import threading
import tempfile
import subprocess
from typing import Optional

import soundfile as sf
import torch
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from qwen_tts import Qwen3TTSModel

MODEL_ID = os.getenv("T2S_VOICE_MODEL", "openarcane/Volt-TTS")
MAX_REFERENCE_BYTES = 20 * 1024 * 1024
MAX_TEXT_CHARS = 3000
SUPPORTED_LANGUAGES = {"te": "telugu", "en": "english"}
_generation_lock = threading.Lock()
_model = None

app = FastAPI(title="T2S Studio Voice Clone", version="0.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("T2S_ALLOWED_ORIGINS", "*").split(","),
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)


def get_model():
    global _model
    if _model is None:
        if not torch.cuda.is_available():
            raise RuntimeError("Volt-TTS requires an NVIDIA CUDA GPU. This server has no CUDA device.")
        _model = Qwen3TTSModel.from_pretrained(
            MODEL_ID, device_map="cuda:0", dtype=torch.bfloat16
        )
    return _model


@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_ID,
        "cuda": torch.cuda.is_available(),
        "languages": ["te", "en"],
    }


@app.post("/v1/clone/tts")
async def clone_tts(
    text: str = Form(...),
    language: str = Form(...),
    ref_text: Optional[str] = Form(None),
    speed: float = Form(1.0),
    audio: UploadFile = File(...),
):
    clean_text = text.strip()
    lang = language.strip().lower()
    if not clean_text:
        raise HTTPException(400, "Enter text to generate.")
    if len(clean_text) > MAX_TEXT_CHARS:
        raise HTTPException(413, "Maximum 3,000 characters per request.")
    if lang not in SUPPORTED_LANGUAGES:
        raise HTTPException(400, "Only Telugu (te) and English (en) are enabled.")
    reference = await audio.read()
    if len(reference) < 1024 or len(reference) > MAX_REFERENCE_BYTES:
        raise HTTPException(400, "Reference recording must be between 1 KB and 20 MB.")
    try:
        model = get_model()
        suffix = os.path.splitext(audio.filename or "reference.wav")[1] or ".wav"
        with tempfile.NamedTemporaryFile(suffix=suffix) as reference_file, \
             tempfile.NamedTemporaryFile(suffix=".wav") as normalized_file:
            reference_file.write(reference)
            reference_file.flush()
            try:
                subprocess.run(
                    [
                        "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                        "-i", reference_file.name, "-vn", "-ac", "1", "-ar", "24000",
                        "-c:a", "pcm_s16le", normalized_file.name,
                    ],
                    check=True,
                    capture_output=True,
                    timeout=30,
                )
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired, FileNotFoundError) as exc:
                detail = getattr(exc, "stderr", b"") or b""
                raise HTTPException(400, "Could not decode the reference recording. Upload a clear WAV, MP3, M4A, or FLAC audio file.") from exc
            if os.path.getsize(normalized_file.name) < 1024:
                raise HTTPException(400, "Reference recording has no usable audio.")
            with _generation_lock:
                kwargs = {
                    "text": clean_text,
                    "language": SUPPORTED_LANGUAGES[lang],
                    "ref_audio": normalized_file.name,
                }
                if ref_text and ref_text.strip():
                    kwargs["ref_text"] = ref_text.strip()
                else:
                    kwargs["x_vector_only_mode"] = True
                wavs, sample_rate = model.generate_voice_clone(**kwargs)
        output = io.BytesIO()
        sf.write(output, wavs[0], sample_rate, format="WAV", subtype="PCM_16")
        return Response(
            content=output.getvalue(),
            media_type="audio/wav",
            headers={"Cache-Control": "no-store", "X-T2S-Language": lang},
        )
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(502, f"Voice generation failed: {type(exc).__name__}: {exc}") from exc

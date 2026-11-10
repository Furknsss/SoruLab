"""Optional high-quality original science illustration generator.

Google AI Pro is *not* Gemini API billing. This module is OFF by default.
It never runs without the server owner's explicit environment enable flag,
a server-side API key and an image-capable model chosen from an allowlist.
Images are provisional until two blind multimodal reviewers accept them.

No Google credentials are shipped to or read from the Android app.
"""
from __future__ import annotations

import base64
import io
import os
from typing import Optional

from PIL import Image, UnidentifiedImageError

from math_gate import VerificationError

DEFAULT_MODEL = "gemini-nano-banana-2.1"
ALLOWED_MODELS = {
    "gemini-nano-banana-2.1",   # preferred newer workhorse; Interactions API
    "gemini-3-pro-image",       # Nano Banana Pro; separate paid Gemini API
    "gemini-3.1-flash-image",  # previous generation, may still be available
}
MAX_INPUT_BYTES = 5_500_000
MAX_OUTPUT_BYTES = 1_100_000
MAX_SIDE = 1400


def configured() -> bool:
    return (
        os.getenv("SORULAB_NANO_BANANA_ENABLE") == "1"
        and bool(os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY"))
        and os.getenv("SORULAB_IMAGE_MODEL", DEFAULT_MODEL) in ALLOWED_MODELS
    )


def normalize_illustration(raw_base64: str) -> str:
    """Validate and convert to a small, metadata-free RGB JPEG for Android."""
    if not isinstance(raw_base64, str) or len(raw_base64) > MAX_INPUT_BYTES * 2:
        raise VerificationError("Image payload absent or too large")
    try:
        raw = base64.b64decode(raw_base64, validate=True)
        if len(raw) > MAX_INPUT_BYTES:
            raise VerificationError("Generated image exceeds input budget")
        with Image.open(io.BytesIO(raw)) as source:
            if source.format not in ("PNG", "JPEG", "WEBP"):
                raise VerificationError("Unsupported generated image format")
            if source.width < 300 or source.height < 240:
                raise VerificationError("Generated figure is too small to verify")
            if source.width * source.height > 20_000_000:
                raise VerificationError("Generated figure has unsafe dimensions")
            source.load()
            rgb = source.convert("RGB")
        rgb.thumbnail((MAX_SIDE, MAX_SIDE), Image.Resampling.LANCZOS)
        output = io.BytesIO()
        rgb.save(output, format="JPEG", quality=83, optimize=True, progressive=False)
        encoded = output.getvalue()
        if len(encoded) > MAX_OUTPUT_BYTES:
            raise VerificationError("Generated image remains too large")
        return base64.b64encode(encoded).decode("ascii")
    except (UnidentifiedImageError, OSError, ValueError, OverflowError) as exc:
        raise VerificationError("Invalid generated science image") from exc


def generate_science_illustration(
    subject: str, question_stem: str, visual_description: str,
    required_labels: str,
) -> str:
    """Create a *new* illustration and return compact base64 JPEG.

    Illustrations are NOT automatically reliable. A separate blind vision
    review must inspect the actual generated pixels before acceptance.
    """
    if not configured():
        raise VerificationError(
            "Optional Nano Banana provider disabled, unconfigured or disallowed"
        )
    if not 20 <= len(visual_description.strip()) <= 2200:
        raise VerificationError("Missing illustrated visual specification")
    if not 4 <= len(question_stem.strip()) <= 3000:
        raise VerificationError("Missing question")
    if len(required_labels) > 1000:
        raise VerificationError("Too many required labels")
    try:
        from google import genai
    except ImportError as exc:
        raise VerificationError("Optional google-genai dependency not installed") from exc

    prompt = (
        "Create a NEW original scientific black-on-white textbook illustration "
        "for a Turkish YKS multiple-choice question. It is NOT a replica of "
        "a publisher's copyrighted original. Scientifically accurate, "
        "minimal, clearly labelled and proportionate, white background. "
        "No question text, no multiple choice answers, no publisher branding, "
        "no decorative objects, no fictional data, no watermarks added by you. "
        "Respect every specified biological, chemical or physical process, "
        "relationship, arrow, orientation, instrument part and legend. "
        "Don't invent numbers or labels. Visual-specific facts: "
        + visual_description + "\nLabels or arrows EXACTLY: " + required_labels +
        "\nSubject: " + subject + "\nNew question for visual context: " +
        question_stem + "\nReturn ONE image."
    )
    model = os.getenv("SORULAB_IMAGE_MODEL", DEFAULT_MODEL)
    try:
        client = genai.Client(
            api_key=os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
        )
        # Nano Banana 2.1 uses the Interactions API, other image models
        # retain the models.generate_content() API.
        if model == "gemini-nano-banana-2.1":
            reply = client.interactions.create(model=model, input=prompt)
            artwork = getattr(reply, "output_image", None)
            raw = getattr(artwork, "data", None)
        else:
            from google.genai import types
            reply = client.models.generate_content(
                model=model,
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_modalities=["IMAGE"],
                    image_config=types.ImageConfig(
                        aspect_ratio="4:3", image_size="1K"
                    ),
                ),
            )
            raw = None
            for item in getattr(reply, "parts", []) or []:
                data = getattr(item, "inline_data", None)
                if data is not None and getattr(data, "data", None):
                    val = data.data
                    raw = base64.b64encode(val).decode("ascii") if isinstance(
                        val, bytes
                    ) else val
                    break
        if not raw:
            raise VerificationError("Image model did not return an illustration")
        return normalize_illustration(raw)
    except VerificationError:
        raise
    except Exception as exc:
        # Do not expose API keys, request headers or exception URLs.
        raise VerificationError("Image service failed or quota is unavailable") from exc

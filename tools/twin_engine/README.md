# SoruLab Twin Engine — local Qwen + SymPy (optional)

This is an optional **self-hosted** service. It does not provide a free,
unlimited cloud API. Running an open model uses your computer's CPU/GPU,
RAM and electricity. The Android app prefers audited, offline templates;
without a configured private endpoint it keeps the existing Gemini AI
Logic implementation and therefore inherits its usage limits.

## Run locally

Install Ollama (https://ollama.com) and a vision-capable Qwen model.
The suggested tag is qwen3.8:27b; verify the actual installed tag with
ollama list and override OLLAMA_TWIN_MODEL as needed.

From tools/twin_engine:

1. python -m venv .venv
2. Activate the virtual environment and run pip install -r requirements.txt
3. python -m unittest -v test_math_gate
4. OLLAMA_TWIN_MODEL=qwen3.8:27b uvicorn app:api --host 127.0.0.1 --port 8765

Health check: curl http://127.0.0.1:8765/health

## Connect Android

Set the build environment variable
SORULAB_TWIN_ENGINE_URL=https://your-private-twin-endpoint.example
(or the same Gradle project property) and rebuild. Without this HTTPS URL,
the private service is disabled.

ONLY expose through a private authenticated HTTPS reverse proxy/VPN.
The Python service itself has no user login, API token or TLS. Do not
bind it to the public internet or put passwords in compiled URLs.

## Supported and unsupported cases

The device-local template recognises a narrow verified linear-function
pattern. The optional Qwen server proposes new questions and diagram
primitives for triangle, circle, graph, vector and schematic cases.
It checks numeric-answer consistency using restricted arithmetic and
SymPy rational algebra, plus a separate blind LLM review. Non-numeric
answers, illegible source images, unsupported diagrams and uncertain
answers fail closed.

A mathematical numeric certificate is NOT a full proof that the new stem
is semantically equivalent to the original. Blind LLM reviewing can still
make mistakes. Complex biology figures, chemistry molecular drawings,
non-parametric physics situations and all question types are NOT yet
guaranteed. Extensive real-book validation is required before release.

No APK should be shipped until Android compilation, rendered figure
comparisons, actual YKS problem tests and same-signature update tests
have passed. The test-page changes in another branch must be merged
separately after conflict review.

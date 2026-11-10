# SoruLab Twin Engine — local Qwen + SymPy (optional)

This is an optional **self-hosted** service. It does not provide a free,
unlimited cloud API. Running an open model uses your computer's CPU/GPU,
RAM and electricity. The Android app prefers audited, offline templates;
without a configured private endpoint it keeps the existing Gemini AI
Logic implementation and therefore inherits its usage limits.

## Full TYT and AYT coverage (subject-agnostic engine)

The full visual AI path is **not restricted to math**: it accepts Turkish,
mathematics/geometry, physics, chemistry, biology, history, geography,
philosophy and other TYT/AYT questions. It tries to preserve the **specific
question architecture, learning objective, ordered reasoning and difficulty**
and transforms the inputs into a genuinely new question.

The verifier chooses between:
- **Numeric questions:** 5 distinct options, an independent arithmetic answer
  recomputation, a second blind numeric solution and exact SymPy verification.
- **Conceptual and verbal questions:** 5 distinct textual options, TWO blind
  answer-and-reasoning reviews (neither sees the proposed correct choice).
  Both have a subject-specific rubric and must agree on one answer.
- **Shapes/graphs/tables:** a constrained native drawing representation for
  triangle/circle/graph/vectors/schematic/table/flowchart/timeline with
  structural constraints and an independent source-to-figure review.

The local on-device **unlimited templates** are performance optimizations,
not a subject restriction. All other supported sources go through the AI
pipeline. Science/biology truth and complex images are not mechanically
proven by LLM consensus; questions requiring an unverifiable illustration or
containing ambiguous source information MUST fail closed. No finite system
can promise 100% success or accuracy across arbitrary published questions.
A "rejected" question must NEVER be shown as an accepted twin.

## Owner-hosted Docker Compose setup

The optional Qwen + SymPy service is packaged, but **has not been deployed to
a user-owned PC/server**. The suggested 27B multimodal model needs substantial
disk and memory resources and cannot generally run on a typical Android phone.

From this folder:

1. `docker compose up -d --build`
2. `docker compose exec ollama ollama pull qwen3.8:27b`
3. `curl http://127.0.0.1:8765/health`
4. Set up a **private, authenticated TLS/VPN reverse proxy** so Android can use it.

The Compose file binds the service only to loopback by default. Never expose
port 8765/11434 directly to the public internet. Hosting and electricity
are not free even though there is no model API per-request charge.

## Run locally

Install Ollama (https://ollama.com) and a vision-capable Qwen model.
The suggested tag is qwen3.8:27b; verify the actual installed tag with
ollama list and override OLLAMA_TWIN_MODEL as needed.

From tools/twin_engine:

1. python -m venv .venv
2. Activate the virtual environment and run pip install -r requirements.txt
3. python -m unittest -v test_math_gate test_service
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

Device-local templates currently recognize narrow verified linear-function,
Pythagorean and double-angle trigonometry patterns. The optional Qwen server
works across all TYT/AYT subjects, including conceptual A-E questions, and
supports new diagrams for triangle, circle, graph, vector, schematic, table,
flowchart and timeline question families.
It checks numeric-answer consistency with restricted arithmetic and SymPy,
plus a separate blind solution review. Conceptual/non-numeric answers use
two blind subject-specialist audits instead. Illegible source images,
unsupported visual transformations and uncertain answers fail closed.

A mathematical numeric certificate is NOT a full proof that the new stem
is semantically equivalent to the original. Blind LLM reviewing can still
make mistakes. Complex biology figures, chemistry molecular drawings,
non-parametric physics situations and all question types are NOT yet
guaranteed. Extensive real-book validation is required before release.

No APK should be shipped until Android compilation, rendered figure
comparisons, actual YKS problem tests and same-signature update tests
have passed. The test-page changes in another branch must be merged
separately after conflict review.

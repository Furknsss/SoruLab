#!/usr/bin/env bash
# SoruLab PDF Vision Lab — reproducible PaddleOCR-VL-1.6 inference environment
# Requirements: Linux x86_64, NVIDIA GPU CC 8.x/9.x, CUDA 12.6 driver,
# Docker + Docker Compose v2, >= 20 GB disk, enough VRAM for the model.
# IMPORTANT: This script does not expose the inference port publicly.
set -euo pipefail
ROOT="${SORULAB_VISION_HOME:-$HOME/sorulab-vision-lab}"
SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  echo "Requires Linux x86_64 NVIDIA GPU host" >&2; exit 2
fi
for exe in docker git nvidia-smi python3 curl; do
  command -v "$exe" >/dev/null || { echo "Missing $exe" >&2; exit 2; }
done
docker compose version >/dev/null || { echo "Docker Compose v2 required" >&2; exit 2; }
nvidia-smi --query-gpu=name,compute_cap,memory.total --format=csv,noheader
mkdir -p "$ROOT"
if [[ ! -d "$ROOT/PaddleOCR/.git" ]]; then
  git clone --depth=1 https://github.com/PaddlePaddle/PaddleOCR.git "$ROOT/PaddleOCR"
fi
cd "$ROOT/PaddleOCR/deploy/paddleocr_vl_docker/hps"
if [[ ! -f .env ]]; then
  cp .env.example .env
fi
if grep -q '^HPS_PIPELINE_NAME=' .env; then
  sed -i 's/^HPS_PIPELINE_NAME=.*/HPS_PIPELINE_NAME=PaddleOCR-VL-1.6/' .env
else
  printf '\nHPS_PIPELINE_NAME=PaddleOCR-VL-1.6\n' >> .env
fi
echo "PaddleOCR upstream commit: $(git -C "$ROOT/PaddleOCR" rev-parse HEAD)"
echo "Preparing official PaddleOCR-VL-1.6 serving pipeline..."
bash prepare.sh
echo "Starting local-only service: gateway 127.0.0.1:8080"
# Upstream compose port mapping may bind to 0.0.0.0. Require a
# loopback-only override to prevent publishing private textbook images.
cat > docker-compose.sorulab-loopback.yml <<'YAML'
services:
  paddleocr-vl-api:
    ports: !override
      - "127.0.0.1:8080:8080"
YAML
# The PaddleOCR upstream file is compose.yaml, NOT docker-compose.yml.
# Validate the final merged Compose ports before launching anything.
test -s compose.yaml || { echo "PaddleOCR upstream compose.yaml missing" >&2; exit 3; }
COMPOSE_JSON="$(docker compose -f compose.yaml -f docker-compose.sorulab-loopback.yml config --format json)"
printf '%s' "$COMPOSE_JSON" | python3 -c '
import json,sys
c=json.load(sys.stdin)
p=c["services"]["paddleocr-vl-api"].get("ports",[])
if len(p)!=1 or str(p[0].get("published"))!="8080" or p[0].get("host_ip") not in ("127.0.0.1", "::1"):
    raise SystemExit("Unsafe Paddle gateway exposure; refusing to start")
print("Validated loopback-only PaddleOCR gateway port")
'
docker compose -f compose.yaml -f docker-compose.sorulab-loopback.yml up -d --build
echo "Checking real Triton + VLM model readiness (not only container uptime)..."
ready=0
for attempt in $(seq 1 90); do
  if curl --silent --show-error --fail --max-time 5 http://127.0.0.1:8080/health/ready > /dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 10
done
if [[ "$ready" != "1" ]]; then
  echo "ERROR: PaddleOCR-VL-1.6 did not become inference-ready" >&2
  docker compose -f compose.yaml -f docker-compose.sorulab-loopback.yml ps >&2 || true
  exit 4
fi
# Do not confuse a healthy HTTP server with an inference-capable model.
# A real model call with an original (nonpublisher) image is mandatory.
python3 "$SCRIPTS_DIR/smoke_paddle_endpoint.py" --url http://127.0.0.1:8080
echo "PaddleOCR-VL-1.6 READY and local live inference validated."
echo "An authenticated TLS proxy and independent inference tests are still required before Android integration."

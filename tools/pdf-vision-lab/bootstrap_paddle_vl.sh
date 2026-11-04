#!/usr/bin/env bash
# SoruLab PDF Vision Lab — reproducible PaddleOCR-VL-1.6 inference environment
# Requirements: Linux x86_64, NVIDIA GPU CC 8.x/9.x, CUDA 12.6 driver,
# Docker + Docker Compose v2, >= 20 GB disk, enough VRAM for the model.
# IMPORTANT: This script does not expose the inference port publicly.
set -euo pipefail
ROOT="${SORULAB_VISION_HOME:-$HOME/sorulab-vision-lab}"
if [[ "$(uname -s)" != "Linux" || "$(uname -m)" != "x86_64" ]]; then
  echo "Requires Linux x86_64 NVIDIA GPU host" >&2; exit 2
fi
for exe in docker git nvidia-smi; do
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
docker compose -f docker-compose.yml -f docker-compose.sorulab-loopback.yml up -d
echo "Service bootstrapped. Inspect: docker compose ps"
echo "DO NOT submit copyrighted/user PDF pages until network, access and data retention are approved."

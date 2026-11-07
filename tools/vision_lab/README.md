# SoruLab Vision Lab — experimental, private

### What is ready
CPU-only QA harness and a reproducible PaddleOCR-VL-1.6 runner. This is NOT a deployed GPU service, a successful PaddleOCR model inference, or a release APK.
Never commit real book PDF pages, image pixels, extracted copyrighted passages, private model output or API keys.

### Local evaluation
Run: `python3 -m unittest discover -s tools/vision_lab -p 'test_*.py' -v`.
Given a private QA reference and model-generated complete question boxes:
`python3 tools/vision_lab/benchmark.py --reference /private/geometry.json --predictions /private/predictions.json --strict`
Prediction schema: documents -> document_id -> pages -> {index (0-based), questions:[{number,left,top,right,bottom}]}; all coordinates 0..1000.
Benchmark rejects missing pages, missing/duplicated numbers, overlaps and cropped annotated last-ink points.

### Install PaddleOCR-VL-1.6 locally
Use the official PaddlePaddle CPU/GPU installation matching your machine, then `python3 -m pip install -U 'paddleocr[doc-parser]' pymupdf`.
`python3 tools/vision_lab/run_paddle_vl.py --pdf '/private/book.pdf' --pages 4,5 --device cpu --out /private/paddle-results`.
GPU: use `--device gpu` only on a compatible NVIDIA host with correctly installed CUDA/PaddlePaddle.
The first run downloads weights. This server container currently has NO NVIDIA GPU and NO working external package download; NO real Paddle model inference was performed here.

### Deploy a private GPU service when capacity is available
Official PaddleOCR documentation supports Docker Compose with NVIDIA GPU / CUDA >=12.6 for the VLM full pipeline. Clone PaddlePaddle/PaddleOCR, use its documented `deploy/paddleocr_vl_docker/accelerators/nvidia-gpu/compose.yaml` and `.env`, and run `docker compose up`.
Do not expose port 8080 to the public internet. Use VPN and authenticated API access. No paid instance may be created without hosting credentials and explicit budget approval.

### Architecture / limits
PDFBox/PDF native glyphs give printed-number anchors on searchable PDFs. PaddleOCR-VL parses scanned document text, tables and figures, while Gemini 3.8 Flash separately proposes complete question rectangles. Independent geometry and printed key readers must agree; otherwise abstain.
PaddleOCR's raw document blocks are NOT themselves complete question boxes. The YKS block-to-question association and full answer-key consensus still require real book validation. Synthetic green tests do NOT establish accuracy.

References: https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/pipeline_usage/PaddleOCR-VL.en.md and https://github.com/opendatalab/OmniDocBench
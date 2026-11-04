# SoruLab PDF Vision Lab (experimental)

This directory prepares **independent model evaluation**, not a production
server or an Android integration. Do not claim that PaddleOCR-VL is running in
SoruLab merely because these scripts are committed.

## Model selection
- **PaddleOCR-VL-1.6 + PP-DocLayoutV3:** specialized document structure,
  tables, formulas and layout. The official high-performance service uses
  FastAPI gateway + Triton + vLLM. See
  https://github.com/PaddlePaddle/PaddleOCR/blob/main/deploy/paddleocr_vl_docker/hps/README_en.md
- **Gemini vision:** separate whole-page multimodal question-region candidate
  implemented on the Android development branch. This requires Firebase AI
  authorization, quotas and user opt-in.
- **PDFBox + independent printed anchors:** deterministic source coordinates
  for checking question numbers and scope. Never replace this ground truth
  with model-generated answers.
- **Printed answer-key reader:** compare independent native PDF extraction
  with image/table OCR. When two sources conflict, quarantine rather than
  invent an answer.

## Provisioning a real GPU server
1. Obtain a Linux x86_64 machine with NVIDIA compute capability 8.x or 9.x,
   CUDA 12.6-compatible driver, Docker and Compose v2. This environment is
   **not provided** by GitHub Actions Android emulators.
2. Review privacy, textbook processing rights, API security and costs.
3. Run `bash tools/pdf-vision-lab/bootstrap_paddle_vl.sh` on that host.
   It clones upstream official PaddleOCR, runs `prepare.sh`, and starts the
   loopback-only service. Upstream containers/models may download gigabytes.
4. Do **not** expose port 8080 publicly; put authentication/TLS and rate limits
   in front of any eventual remote gateway.
5. Pin the upstream Git commit and Docker image digests for repeatable
   deployment before promoting any results.

## Acceptance
Build manually reviewed ground truth from **both real biology books**, with
every printed question, complete stem/figure/options rectangle, source topic,
printed test and answer-key cell. Do not commit user/publisher PDF content.
Run:

```bash
python3 tools/pdf-vision-lab/score_candidates.py /private/reviewed_gold_predictions.json --iou 0.90
```

The scorer fails if *any* question has missing/duplicate source number,
insufficient bounding-box overlap, wrong source scope, wrong answer, or extra
prediction. A high bounding-box IoU is a necessary but not sufficient visual
acceptance condition: human visual review must additionally verify that
diagrams and options are not cut. Evaluate latency, page failures and
cost per 100 pages. Promote only a measured winner.

## Measured status (2026-11-05)
- **Actual PaddleOCR-VL-1.6 CPU inference PASSED** in [GitHub Actions run 41929769084](https://github.com/Furknsss/SoruLab/actions/runs/41929769084). The official PaddleOCR 3.6.0 and PaddlePaddle 3.2.1 packages and 0.9B model weights were downloaded; a fully original 640x400 synthetic image produced a real JSON artifact. This was an **ephemeral CI CPU runner**, not a persistent Paddle server, Android SDK, or GPU deployment.
- The synthetic layout was recognized as **one display_formula block**, not individually segmented test questions. Thus running Paddle successfully does **not** establish accurate question splitting.
- GPU runtime provisioning script: fixed `compose.yaml` upstream path, loopback-only port check, `/health/ready` and a real POST `/layout-parsing` synthetic inference gate. **Not run on NVIDIA hardware yet.**
- PaddleOCR API contract and bootstrap tests: [run 41929342671](https://github.com/Furknsss/SoruLab/actions/runs/41929342671) passed, but that was a **mock local HTTP gateway** and syntax check, not the model.
- Firebase Gemini: real Android Firebase canary [run 41928809549](https://github.com/Furknsss/SoruLab/actions/runs/41928809549) **FAILED with Firebase App Check token is invalid**. The test now explicitly requires a Console-registered debug token supplied securely as `SORULAB_APPCHECK_DEBUG_TOKEN` in Actions secrets; never disable App Check or commit tokens.
- Two real source books, 272-page selectable-text Biyotik and 256-page image-only Dr DOKTOR: **not yet fully model-inferred or end-to-end certified**.
- The Android PDF bulk-import code currently calls **Gemini** directly and does **not** send any page to a Paddle inference endpoint. Do not distribute this build as a combined Paddle + Gemini solution. Benchmark scorer still needs reviewed gold and predictions.

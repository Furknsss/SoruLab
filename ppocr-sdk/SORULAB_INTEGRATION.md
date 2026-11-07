# SoruLab offline PaddleOCR v6
Source: [PaddleOCR Android SDK](https://github.com/PaddlePaddle/PaddleOCR/tree/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf/deploy/ppocr-android/ppocr-sdk) at pinned revision `dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf` (Apache-2.0; see UPSTREAM_LICENSE.txt).

This is **PP-OCRv6_small**, a genuinely on-device ONNX OCR detector and recognizer; **not** the server-scale PaddleOCR-VL-1.6 0.9B multimodal model.

`scripts/prepare_ppocrv6_models.py` downloads the two freely licensed official PaddlePaddle ONNX models from Hugging Face into `src/main/assets/models`; they are excluded from Git history but packaged in the built APK. The build fails instead of shipping an APK that silently lacks weights. Network is needed only at build time.

Upstream SDK requires Android API >=26. The module manifest uses minSdk24 only to preserve SoruLab's existing APK support, and calls to Paddle must have an API>=26 runtime guard. Confirm on an emulator, preserve ML Kit as independent OCR, and compare source geometry before using any numbers.

Model files are approximately 10MB detection + 21MB recognition before native libraries. No paid API/server is needed for Paddle mobile OCR. Gemini is distinct; its free-tier usage is provider and quota dependent.

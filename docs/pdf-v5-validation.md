# PDF V5 preview validation

This branch integrates PR #18 (including scoped answer keys, adaptive OCR rows,
horizontal answer-grid geometry and crop-boundary regressions) with the premium UI.
No publisher-specific answer sequences or page spans are used.

The bundled local ML Kit recognizer remains the OCR backend. PaddleOCR/RapidOCR
were not benchmarked on actual publisher PDFs or target tablets: no representative
PDF corpus or device is attached. Choosing an additional model without those
measurements would not establish improved accuracy or acceptable RAM/APK cost.
The current hybrid path combines PDF text, local OCR cells, geometric answer-grid
extraction, scoped test identities and conflict rejection. Conflicts remain
ungraded; the review screen allows explicit manual answer confirmation.

Changes include content-hashed PDF identities, stable question IDs for repeat
imports, variable test length down to one available question, explicit verification
for legacy answers, retained unresolved crops, UID-scoped atomic caches, protected
creation timestamps and non-destructive legacy migration.

Acceptance still requires real PDFs from at least two publishers, independent
answer labels, crop inspection and a physical tablet/emulator run. Synthetic
parser tests are not a substitute for those acceptance results. Do not claim 100%
real-book accuracy, OCR engine benchmark results, or visual parity from compilation.

Preview APK version: 1.2.5-rc2, versionCode 25. The locally generated development
key is not the installed production key. In-place upgrade compatibility cannot be
claimed without the original signing key/certificate. The CI workflow optionally
uses DEBUG_KEYSTORE_BASE64 for a stable development certificate. It never publishes
a release. Production signing must use the existing authorized key workflow.

## Current validation

70 targeted regression tests passed with no failures/errors/skips, including four
Compose phone/tablet optical-form and finish-confirmation tests. These render with
Robolectric; they are not a physical-device visual acceptance run. The initial
merged suite found a legacy mixed-owner fallback mismatch, now fixed by filtering
individual records by explicit UID. Test JVMs are isolated to avoid Robolectric's
native font ZIP filesystem collision across SDK sandboxes.

Git push succeeded to `feat/premium-pdf-v5`. GitHub GraphQL requests returned
`Forbidden` at api.github.com. A PR and Actions artifact were not verified/created
through that unavailable API. Local APK signature verification passed. Real
publisher PDFs and the original installed signing key were not available.

Final checks: 70 PDF/data/UI regression tests + 25 classification/snapshot baseline
checks + one new legacy-snapshot verification regression passed (96 distinct tests,
zero failures/skips). The snapshot suite was rerun after the final change and all
four passed. assembleDebug completed successfully for the final source. lintDebug
completed with 0 errors and 72 warnings before the final status-report text and
legacy-snapshot default adjustment. Existing deprecation warnings remain.

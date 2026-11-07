# SoruLab: multi-book offline library and optional owner-only cloud backup

Status as of 2026-11-08: multi-ZIP picker and sequential import are in the
development branch. Cloud Storage backup is **NOT ACTIVATED**. No user should
assume that image files have been remotely backed up.

## Multi-book import

- OpenMultipleDocuments selects multiple ZIPs in one system file picker.
- Import strictly sequentially: one ZIP extracted and locally committed
  before another one is opened. The UI reports book count and failures.
- bookId is stable and unique to a source book. The same displayed
  publisher/book title does not imply identical book identity.
- Existing bookId with conflicting publisher/title must fail closed.
- Re-selecting identical ZIP within the same batch must not insert twice.
- All paths are owner-scoped and verified by SHA-256.
- No partial book should be marked successful after a failed local commit.

## Critical current-state warning

Current custom_questions Firestore documents sync metadata but not the complete
image bytes; crop paths refer to filesDir on the original Android device.
Firestore metadata sync alone **DOES NOT** constitute cross-device recovery.
The local custom-question cache is still an entire-owner JSON snapshot, which
requires O(total questions) rewrite and read-back on every new book.
Do not promise zero frame drops or unlimited book counts based on present tests.

## Safe staged cloud backup plan

1. Explicitly enable Cloud Storage for Firebase in the existing project and
   approve the Blaze plan/budget alert; verify the default bucket name.
   Do not guess OAuth IDs, credential files, or create a public bucket.
2. Deploy storage.rules from this repository, scoped to Firebase Auth UID.
   Test cross-user access denial and oversized payload rejection first.
3. Keep the *validated original ZIP* as the single cloud backup object:
   users/{uid}/question_packs/{bookId}/{sha256-of-zip}.zip
   Use application/zip, SHA-256, per-user authentication and one file/book
   version, not thousands of separate JPEG uploads.
4. On import, atomically save locally, then enqueue a durable WorkManager
   upload from an app-private pending file with unmetered-network constraint,
   exponential retry, explicit upload status, and persistent resume metadata.
   Never call a local-only save "cloud backed up".
5. After server confirms the object upload, commit a per-user book index
   (bookId, title, publisher, count, SHA-256, object path, backup status)
   and show "Cloud backed up" only after verification.
6. On new device, sign in, list the owner's book index, download ZIPs on
   demand to a file (never to an unbounded byte array), validate SHA-256,
   run the same SoruLabQuestionZipImporter and durable local save.
   A failed download or corrupted book must not erase existing data.
7. Preserve original ZIPs on the user device until cloud acknowledgment;
   allow manual export/backup to another location before any deletion.

## Performance architecture for many books

- Replace monolithic user-wide JSON cache with Room tables keyed by bookId,
  sourceTestId and question identity; add indexes for queries by book and topic.
  Migrate legacy JSON in verified, restartable chunks; retain the original
  until all books pass counts+image validation.
- Query only visible book metadata; do not build or observe every question
  across 100 books at launch.
- Lazy load thumbnail files, downsample question images to the display size,
  and use an LRU bitmap cache with a bounded memory budget.
- Keep unpacking, hashes, JSON/SQLite writes, and image decoding off the main
  thread; avoid decoding the same question twice.
- Keep one import at a time; tune upload concurrency to one on devices, use
  a Wi-Fi-only backup preference and user-facing storage usage.
- Validate realistic stress cases: 1, 10, 30 and 100 books; offline startup,
  search, book paging, S Pen drawing, optical drawer, import+cancel+resume,
  cloud restore with no image gaps, low storage, low RAM and airplane mode.
  Report cold-start time, p95 frame duration/jank, peak PSS memory,
  battery/thermal state, and correctness. Never promise literally zero heat.

## Firebase restrictions and billing

Cloud Storage for Firebase requires Blaze as of 2026. Billing, quota and
storage location are external account decisions. A user must deliberately
enable them. Do not deploy/configure storage or assume it is operational
until the bucket and rules have been verified in the actual project.

References:
- https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024
- https://firebase.google.com/docs/storage/android/upload-files
- https://firebase.google.com/docs/storage/android/download-files
- https://firebase.google.com/docs/firestore/quotas
- https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenMultipleDocuments

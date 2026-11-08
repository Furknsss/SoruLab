# SoruLab question ZIP pack — version 1

This format allows ChatGPT or another offline worker to crop a question bank
into images and make a ZIP which the Android application can import without
performing PDF OCR on the phone.

ZIP root (no outer directory):
  manifest.json
  images/q0001.png
  images/q0002.jpg
  cover.jpg               (optional)

The manifest.json file uses UTF-8 JSON with this structure:

    {
      "format": "sorulab.question-pack",
      "version": 1,
      "bookId": "publisher-book-2026-a",
      "bookTitle": "Sample AYT Biology Question Bank",
      "publisher": "Publisher Name",
      "examType": "AYT",
      "subject": "Biyoloji",
      "questionCount": 2,
      "questions": [
        {
          "image": "images/q0001.png",
          "sha256": "<actual 64-character lowercase sha256 of image bytes>",
          "pageIndex": 8,
          "questionNumberInTest": 1,
          "sourceTestId": "sinir-sistemi~test-1",
          "testName": "Sinir Sistemi - Test 1",
          "topic": "Sinir Sistemi",
          "learningOutcome": "Nöronun yapısı ve görevleri",
          "questionType": "Öncüllü bilgi",
          "difficulty": "Orta",
          "targetLevel": "OSYM",
          "answer": "B",
          "answerVerified": true,
          "text": "Optional OCR text",
          "bounds": [0.12, 0.07, 0.42, 0.47]
        },
        {
          "image": "images/q0002.jpg",
          "sha256": "<actual lowercase sha256>",
          "pageIndex": 8,
          "questionNumberInTest": 2,
          "sourceTestId": "sinir-sistemi~test-1",
          "topic": "Sinir Sistemi",
          "answer": "",
          "answerVerified": false
        }
      ]
    }

If a book cover is supplied, add coverImage:"cover.jpg" and
coverSha256:<lowercase 64-hex SHA256 of cover bytes> to the manifest.

pageIndex is zero-based (the PDF page with index 8 is its ninth page).
bounds = [top,left,bottom,right] normalized to the page, each in [0,1].
bounds may be omitted if unavailable.

INTEGRITY AND ACCURACY:
- Every question has exactly one cropped image with complete stem, graphics
  and every answer choice, checked at source-image level.
- The manifest questionCount must match the array length.
- Each (pageIndex, sourceTestId, questionNumberInTest) is unique.
- The sha256 field must match the exact image bytes. The importer verifies it.
- Do not invent or guess answers. When the original printed key has not
  been independently checked, use answer:"" and answerVerified:false.
  Such answers remain blank in SoruLab.
- Optional but important: learningOutcome is the EXACT, source-verified
  alt kazanım label; questionType is the exact question-type label.
  These fields control Hata Frekansı branches and the scope of Güçlen
  practice tests. Match the same string consistently across the book.
  When not certain, use an empty string; NEVER invent an official MEB
  kazanım or infer one solely from the length/diagram of a question.
- Do not claim a real difficulty evaluation based on a placeholder.
  If unknown, omit difficulty (the UI can display "Orta" as a default).
- Reimports of the same source book should preserve bookId.
- Use only ASCII letters, digits, underscore and hyphen for image filenames.
- No original PDF is included: only manifest.json and referenced images.

To create this ZIP after preparing manifest.json and image files:
  python3 scripts/build_sorulab_question_zip.py --manifest manifest.json \
      --images-root ./book-workspace --output SoruLab-Sorular.zip

On Android: Kitaplığım -> ZIP'ten Soruları Ekle -> choose ZIP.

Limits: 6000 questions; 300 MiB compressed ZIP; 12 MiB per image;
420 MiB total uncompressed image files; 7 MiB manifest.
Any unreferenced or unsafe file path is rejected. This ZIP importer is
a separate fast path and does not require the device PDF OCR engine.

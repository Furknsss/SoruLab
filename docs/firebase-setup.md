# Firebase setup for SoruLab Android

The app must use Firebase project `gen-lang-client-0143749618`, Android package
`com.aistudio.sorulab.ykslab`, and Firestore database
`ai-studio-android-sorulab-60eab89c-ce94-4f3c-b430-c4b1470c39d1`.

## Google sign-in

1. In Firebase Console, open **Authentication → Sign-in method** and enable
   **Google** for `gen-lang-client-0143749618`.
2. Register the Android app with the exact package above. Add the SHA-1
   fingerprints for both the debug key and the release upload/signing key under
   **Project settings → Your apps → SHA certificate fingerprints**. Google
   sign-in will not work until the release certificate used by the APK is
   registered.
3. Ensure the project has a Web OAuth client. Download a fresh
   `google-services.json` for this Android app after enabling Google sign-in;
   the Android client must include its signing certificate and the Web client
   must appear as `client_type: 3`.
4. Replace `app/google-services.json` with that downloaded file. Do not hand
   edit or invent OAuth client IDs. The Android build now fails early if the
   project/package/API key or either OAuth client is missing.

The release log-in screen obtains the Web client ID generated from this file.
If the Google provider or OAuth clients are changed, download the file again
and build a new APK.

## Firestore

The app, `firebase.json`, and `.firebaserc` target the database ID listed above,
not the `(default)` database, in project `gen-lang-client-0143749618`. Confirm
that this named database exists in that project before deploying. Deploy
[firestore.rules](../firestore.rules) to that database:

```sh
firebase deploy --only firestore:rules --project gen-lang-client-0143749618
```

The rules restrict test results and custom questions to their authenticated
owner and publisher logos to the owner's account. Do not replace them with
public read/write rules. The unit test workflow runs these rules against the
Firestore and Auth emulators; that verifies the rules file, but does not prove
that Firebase Console has the same rules deployed.

## App Check

The release app uses the reCAPTCHA App Check provider configured by
`recaptcha_site_key` in `app/src/main/res/values/firebase_applet_config.xml`.
The Firebase Console Android app must have a matching App Check provider. If
Firestore App Check enforcement is enabled, verify production app requests
first; debug builds use the Firebase App Check debug provider and need a
registered debug token when enforcement is active.

## CI App Check activation

For the live canary, register a debug token under the exact Android app
`1:656943524635:android:8ff497c04d59f36e5e01be` in this project, using
**App Check → Apps → Manage debug tokens**. Store the same raw token in the
repository Actions secret `SORULAB_APPCHECK_DEBUG_TOKEN`; do not paste it into
issues, chat, source files, or an APK. A token belonging to a different app
does not activate this Android app. The token name is not the token value.

The live workflow first exchanges the secret with the App Check REST service.
`403 PERMISSION_DENIED: App attestation failed` means this registration gate
has not passed; a successful local OCR or emulator UI test cannot replace it.
The CI secret is injected only into instrumentation and is never bundled in
the distributable APK. A debug installation on a separate device has its own
token; a release APK uses the release provider described above instead.

Reference: https://firebase.google.com/docs/app-check/android/debug-provider

## PDF question cropping and answer matching

The current verified-import path uses Firebase AI / Gemini for visual page
rectangles and printed answer-table transcription. PDF page images are sent
to that service; cloud quotas, availability, and provider pricing apply.
App Check access is verified before copying, archiving, rendering, or scanning
the PDF, so an unavailable attestation fails promptly without importing a book.
Passing that preflight verifies attestation only, not model access or accuracy.

The embedded PDF text layer, bundled on-device ML Kit Latin OCR, and free
PP-OCRv6_small on-device inference supply independent printed-source evidence.
The PP-OCRv6 SDK is not the server-side PaddleOCR-VL product. Geometry checks
reject missing/overlapping/misnumbered questions. Answer-key matches require
independent reader agreement on chapter, test identity, and printed number;
page order and model-generated solutions are never used as an answer key.

Missing, extra, duplicated, ambiguous, or disputed source questions/answers
prevent the book from being accepted as a complete verified import. Synthetic
regressions and native device tests do not certify every question in a real
book. Complete real-book imports still require successful live model access
and their own source-based verification.

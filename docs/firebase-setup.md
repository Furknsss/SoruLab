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

## Free offline PDF processing

PDF import has no Firebase AI dependency, token exchange, HTTP endpoint, cloud
consent gate, or API quota. The Firebase AI SDK and live-generation canary have
been removed. Existing account/storage configuration above remains separate.
AI controls return existing stored solutions or an explicit unavailable message;
they do not invoke a billed model or fabricate a new solution.

The installed engines are PDFBox/native PDF text extraction, bundled ML Kit
Latin OCR, official Apache-2.0 PP-OCRv6_small Android ONNX inference, and local
layout/crop/ink checks. Models are bundled at build time. Question identity
and printed answer matching use chapter + test kind + test number + question
number. A separate ML Kit reading must agree with native PDF/Paddle evidence;
missing and conflicting answers are not guessed. Crop and source-key coverage
checks still reject an incomplete book rather than falsely marking it complete.

Device QA includes a full original synthetic PDF import with the emulator's
network disabled. Representative user-source pages may be tested using a
per-run encrypted fixture; the decryption key and plaintext stay on the ephemeral
runner and are excluded from artifacts. Do not commit plaintext textbook pages
or place them in screenshots/test report artifacts.

Neither synthetic tests nor selected real-page checks certify every question
in every book. Keep per-book evidence and report remaining failures honestly.

Sources:
- https://developers.google.com/ml-kit/guides
- https://www.paddleocr.ai/latest/en/version3.x/inference_deployment/cross_platform/android_deployment.html

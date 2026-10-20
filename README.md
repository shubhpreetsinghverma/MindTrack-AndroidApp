# MindTrack

A calm wellbeing companion for Android: daily mood check-ins, gentle insights, short calming exercises and an AI companion. Built with Kotlin and Jetpack Compose, Firebase (Auth, Firestore, Crashlytics, AI Logic with Gemini) and a premium navy / lavender design with a white-and-lavender light theme.

MindTrack is a wellbeing tool. It is not a medical service and cannot contact emergency services.

## Features
- **Check-ins:** mood, stress, energy, tags and a private note. Edit, delete, browse as a list or a calendar, and add missed days (up to 30 days back).
- **Insights:** weekly summary, pattern observations (with "how this was worked out"), and a cautious experimental mood outlook that only appears if it beats a plain average.
- **Companion:** Gemini-powered chat with scope limits, fixed crisis help, a 7-day on-device memory, and a free-tier message counter.
- **Calm:** box, 4-7-8 and calm breathing, 5-4-3-2-1 grounding, body relaxation, with optional voice guidance (an English or Hindi voice that follows the app language, using the phone's own text-to-speech).
- **Reminders:** several times, chosen weekdays, smart timing, gentle nudge after missed days.
- **Privacy:** consent screen, app lock (fingerprint / screen lock), crash-report toggle, PDF export for a doctor, account deletion.
- **Sign-in:** email and password, Google, password reset, email verification.
- **Languages:** English and Hindi (machine-assisted). `translations/hindi_review.csv` is a sheet for a native speaker to review; see `translations/README.md`.
- **Themes:** Auto, Light, Dark.

## Build and run
Open the folder in Android Studio (JDK 17 from Android Studio works), then run the `app` configuration. From a terminal:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Screen tests run on an emulator or phone (they never touch your account or saved data):

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.mindtrack.app.test/androidx.test.runner.AndroidJUnitRunner
```

(Avoid `connectedAndroidTest` on a device you are signed in on: it uninstalls the app afterwards, which clears your session and App Check debug token.)

## Firebase setup checklist
1. **Firestore rules:** paste `firestore.rules` into Firestore → Rules → Publish.
2. **App Check:** register your debug token (printed in Logcat as "Firebase App Check debug token") under App Check → Apps → Manage debug tokens.
3. **Google sign-in:** Authentication → Sign-in method → enable Google, add your debug SHA-1 (`./gradlew signingReport`), then re-download `google-services.json`.
4. **Crashlytics:** open Crashlytics in the console and enable it. Use "Send a test crash" in Profile (debug builds) to check.
5. **Privacy policy:** `firebase deploy --only hosting` publishes `public/privacy.html`.

## Gemini free tier
The free tier allows a small number of requests per day per model (about 20 for the primary model). The app estimates what is left, answers "hi" and "thanks" locally, reuses the day's Insights suggestions, and falls back to a second model. Quotas reset at midnight Pacific time. See `AiQuota.kt`.

## Release
`./gradlew :app:assembleRelease` builds a shrunk (R8) APK. Release builds use Play Integrity for App Check and stay unsigned unless a `keystore.properties` file exists (storeFile, storePassword, keyAlias, keyPassword; it is git-ignored).

## More documents
`PRIVACY_POLICY.md`, `DATA_SAFETY.md` (Play Console answers) and `CRISIS_REVIEW.md` (sheet for a counsellor to review the safety wording).

# MindTrack

A calm wellbeing companion for Android: daily mood check-ins, gentle insights, short calming exercises and an AI companion, wrapped in a quiet pixel-art look with a small moon for company.

Built with Kotlin and Jetpack Compose, Firebase (Auth, Firestore, Crashlytics, App Check, AI Logic with Gemini) and a flat "Quiet pixel" design: warm paper and dusty lavender in light mode, deep ink and soft lavender in dark mode.

> **MindTrack is a wellbeing tool, not a medical service.** It does not diagnose or treat anything and cannot contact emergency services. Its reports are self-reported summaries, not clinical assessments.

**Status:** version 1.2.0, a college and portfolio project. It is not published on Google Play.

## Features

**Home**
- A time-aware greeting with your first name (kinder on a Low or Rough day), a gentle reminder that changes between visits, and a week-at-a-glance row.
- One small thing for today, chosen from how you feel (it never repeats two days running), a pattern from your own check-ins once there is enough data, a look-back at good days only, and shortcuts to Breathe and ground and History.
- A small pixel moon whose face follows your mood. It stays still if the phone has animations off.

**Check-ins and history**
- Mood, stress and energy (tap-the-number blocks), tags and a private note. Edit, delete, browse as a list or a calendar, and add missed days (up to 30 days back, counted towards your streak).
- Insights: weekly summary, pattern observations that explain how they were worked out, and a cautious experimental mood outlook that appears only if it beats a plain average.
- PDF report with charts, plain-language explanations of each graphic, clear "not a clinical assessment, please show a doctor" notices and a MindTrack watermark.

**Companion**
- Gemini-powered chat with scope limits and a free-tier message counter.
- A chat-history sidebar. Chats stay on the device and are deleted automatically 7 days after the last message. Delete one chat or all of them.
- A fixed safety net that does not depend on the AI: clear signs of self-harm, abuse or assault always bring up real helplines (India, US, UK and findahelpline.com), in English and Hindi.

**Calm**
- Box, 4-7-8 and calm breathing, 5-4-3-2-1 grounding and body relaxation, each with a short "good for" note, plus optional voice guidance (English or Hindi, using the phone's own text-to-speech). During breathing the moon sits inside a ring of pixels that grows and shrinks with your breath.

**Privacy and account**
- First-run consent, app lock (fingerprint or screen lock), crash-report toggle, reminders (several times, chosen weekdays, smart timing), account deletion and a privacy policy generated from the same text the app shows.
- Sign-up is the first screen. Email accounts must confirm their address before the app opens; Google sign-in goes straight in.
- Notes are never shown on the Home screen.

**Languages and themes:** English and Hindi (machine-assisted, not yet reviewed by a native speaker), and Auto, Light and Dark themes.

## Build and run

You need Android Studio (its bundled JDK 17 works) and a Firebase project.

1. Clone the repository and open the folder in Android Studio.
2. Add your own `app/google-services.json` from the Firebase console (Project settings, your Android app, package `com.mindtrack.app`). This file is deliberately not in the repository.
3. Run the `app` configuration, or from a terminal:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Screen tests run on an emulator or phone and never touch your account or saved data:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.mindtrack.app.test/androidx.test.runner.AndroidJUnitRunner
```

Avoid `connectedAndroidTest` on a device you are signed in on: it uninstalls the app afterwards, which clears your session and App Check debug token.

## Firebase setup checklist
1. **Firestore rules:** paste `firestore.rules` into Firestore, Rules, Publish.
2. **App Check:** register your debug token (printed in Logcat as "Firebase App Check debug token") under App Check, Apps, Manage debug tokens. Friends' installs have their own tokens, so enforcement for Firebase AI Logic has to be relaxed for testing, or the app has to come from Play internal testing.
3. **Google sign-in:** Authentication, Sign-in method, enable Google, add your debug SHA-1 (`./gradlew signingReport`), then re-download `google-services.json`.
4. **Email verification:** Authentication, Templates, check the verification email wording.
5. **Crashlytics:** open Crashlytics in the console and enable it. Use "Send a test crash" in Profile (debug builds) to check.
6. **Privacy policy:** `firebase deploy --only hosting` publishes `public/privacy.html`.

## Gemini free tier
The free tier allows a small number of requests per day per model. The app estimates what is left, answers "hi" and "thanks" locally, reuses the day's Insights suggestions and falls back to a second model. Quotas reset at midnight Pacific time. See `AiQuota.kt`.

## Project layout
```
app/src/main/java/com/mindtrack/app/   screens, view models, logic
app/src/main/res/font/                 bundled Pixelify Sans font
app/src/test/                          unit tests (logic, translations, safety, storage)
app/src/androidTest/                   Compose screen tests
tools/                                 privacy page generator, Hindi review helper
translations/                          Hindi review sheet and instructions
licenses/                              third-party licences
```

Look and feel live in `Theme.kt` (palettes), `Components.kt` (shared pieces), `PixelMoon.kt` (the moon and the label style) and `HomeWidgets.kt` (Home sections).

## Release
`./gradlew :app:assembleRelease` builds a shrunk (R8) APK. Release builds use Play Integrity for App Check and stay unsigned unless a `keystore.properties` file exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; it is git-ignored). Publishing to Google Play is not part of this project yet.

## Known limits
- Hindi text and the safety phrases (English and Hindi) have not been reviewed by a native speaker or a counsellor. See `translations/README.md` and `CRISIS_REVIEW.md`.
- The PDF report is English only.
- The Gemini free tier is small; heavy use runs out for the day.
- Health Connect (sleep and steps) is not built yet.

## More documents
`PRIVACY_POLICY.md`, `DATA_SAFETY.md` (Play Console answers) and `CRISIS_REVIEW.md` (sheet for a counsellor to review the safety wording).

## Credits
Pixelify Sans by The Pixelify Sans Project Authors, used under the SIL Open Font License 1.1 (see `licenses/PixelifySans-OFL.txt`).

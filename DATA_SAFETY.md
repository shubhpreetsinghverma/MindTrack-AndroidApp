# Google Play "Data safety" answers (draft for MindTrack)

Use these as a starting point when you fill in the Data safety form in Play Console. Check each answer
against the app as it ships; Google holds the developer responsible for their accuracy.

## Data collected and shared
| Data type | Collected | Shared with third parties | Purpose | Optional? |
|---|---|---|---|---|
| Email address | Yes | No (stored in Firebase, a service provider) | Account management | No |
| Name | Yes | No | Account management, personalisation | Yes (display name) |
| Health info (mood, stress, energy ratings, tags) | Yes | Ratings and tags are sent to Google Gemini as context for AI replies | App functionality, personalisation | Yes |
| Other user content (journal notes) | Yes (stored in your account) | No | App functionality | Yes |
| Messages (companion chat) | Yes (sent to Gemini; kept on device 7 days) | Yes, with Google (Gemini) | App functionality | Yes |
| Crash logs / diagnostics | Yes (Firebase Crashlytics) | No (service provider) | Analytics / bug fixing | Yes (toggle in Profile) |
| Device or other IDs | Yes (Firebase installation ID) | No | Diagnostics, security (App Check) | No |

## Practices to declare
- **Data encrypted in transit:** Yes (HTTPS to Firebase and Google APIs).
- **Users can request data deletion:** Yes, in-app (Profile → Delete my account and data). Also provide a web URL if Play asks for one.
- **Data is not sold.** No advertising.
- **Account creation:** email/password and Google sign-in.
- **Health apps declaration:** MindTrack is a wellbeing tool, not a medical device. Fill in Play's "Health apps" declaration accordingly, and say it provides no diagnosis or emergency service.

## Before you publish
- Host the privacy policy (`public/privacy.html`, e.g. with `firebase deploy --only hosting`) and paste its URL into Play Console.
- If you later enable the paid Gemini tier or add analytics, update this table and the policy.

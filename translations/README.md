# Hindi review

`hindi_review.csv` lists everything the app shows or reads aloud in Hindi, next to the English.

## For the reviewer (a native Hindi speaker)
1. Open `hindi_review.csv` in Google Sheets or Excel (it is UTF-8).
2. Read the **English** and **Hindi (current)** columns. Check meaning, tone and natural wording. The app is a calm wellbeing app, so warm, simple Hindi is better than formal Hindi.
3. If a line should change, type the better version in **Hindi (your correction)**. Leave the line empty if it's fine. Use the **Notes** column for any comment.
4. Please look hardest at:
   - the row **"Safety: message shown when someone writes crisis words"**;
   - the **privacy policy** rows;
   - the row **"Hindi words that trigger the crisis message"**: add words people really use, remove any that could cause false alarms;
   - the **"Calm exercises"** rows, which are also read aloud by a voice.
5. Keep placeholders exactly as they are: `%1$s`, `%2$d` and so on stand for names, numbers and times that the app fills in. You can move them within the sentence, but don't change or remove them.

## For the developer
```bash
python3 tools/hindi_review.py export   # rebuild the sheet from the app's current Hindi
python3 tools/hindi_review.py import   # apply corrections from the sheet
```
Import updates the short texts automatically. Corrections to the privacy policy, crisis message, quick replies and crisis words are listed so they can be applied by hand. After importing, run the unit tests: they check every Hindi line has the same placeholders as its English.

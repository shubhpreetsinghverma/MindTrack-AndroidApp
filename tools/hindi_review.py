#!/usr/bin/env python3
"""Hindi review sheet for a native speaker.

  python3 tools/hindi_review.py export   -> writes translations/hindi_review.csv
  python3 tools/hindi_review.py import [file.csv]  -> applies corrections back into the app
      (use a CSV downloaded from the Google Sheet: File -> Download -> CSV)

Open the CSV in Google Sheets or Excel. The reviewer only needs to fill the column
"Hindi (your correction)" on rows they want changed, and may add a note. Leave the other columns alone.

Import updates the single-line translations (kind "text") in TranslationsHindi.kt and TranslationsHindiLong.kt.
Rows of other kinds (privacy policy, crisis message, quick replies, crisis words) are listed at the end of the
import so they can be applied by hand.
"""
import csv
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java/com/mindtrack/app")
CSV_PATH = os.path.join(ROOT, "translations/hindi_review.csv")
MAP_FILES = ["TranslationsHindi.kt", "TranslationsHindiLong.kt"]

SCREEN_OF_FILE = {
    "HomeScreen.kt": "Home", "AboutScreen.kt": "Home and About", "MoodScreen.kt": "Mood check-in",
    "InsightsScreen.kt": "Insights", "InsightLogic.kt": "Insights", "PatternInsights.kt": "Insights",
    "Components.kt": "Insights / shared", "CompanionScreen.kt": "Companion", "ViewModels.kt": "Companion / messages",
    "GeminiCompanion.kt": "Companion / errors", "ProfileScreen.kt": "Profile", "Appearance.kt": "Profile",
    "Account.kt": "Profile (account)", "AppLock.kt": "Profile (app lock)", "Reminders.kt": "Profile (reminders)",
    "HistoryScreen.kt": "History", "MindTrackApp.kt": "Resource centre / dialogs", "Safety.kt": "Safety and helplines",
    "Privacy.kt": "Privacy", "FirebaseAuthScreen.kt": "Sign in", "AuthViewModel.kt": "Sign in / messages",
    "GoogleAuth.kt": "Sign in", "Exercises.kt": "Calm exercises (also read aloud)", "Splash.kt": "Intro",
    "DesignSystem.kt": "Mood names, tags, tabs",
}


def read_literal(t, i):
    assert t[i] == '"'
    i += 1
    out = []
    while t[i] != '"':
        if t[i] == "\\":
            n = t[i + 1]
            out.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$"}.get(n, n))
            i += 2
        else:
            out.append(t[i]); i += 1
    return "".join(out), i + 1


def source_form(value):  # real text -> how it is written between quotes in Kotlin
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("\n", "\\n")


def parse_maps():
    """Returns {key_text: (hindi_text, file)} for every `"key" to "value"` entry."""
    entries = {}
    for name in MAP_FILES:
        t = open(os.path.join(SRC, name), encoding="utf-8").read()
        start = t.index("mapOf(")
        end = t.index("internal val privacyPolicyHindi") if "privacyPolicyHindi" in t else len(t)
        i = start
        while i < end:
            if t[i] == '"':
                key, j = read_literal(t, i)
                m = re.match(r"\s+to\s+", t[j:])
                if m:
                    k = j + m.end()
                    if t[k] == '"':
                        parts = []
                        while True:
                            v, k = read_literal(t, k)
                            parts.append(v)
                            m2 = re.match(r"\s*\+\s*", t[k:])
                            if not m2 or t[k + m2.end()] != '"':
                                break
                            k += m2.end()
                        entries[key] = ("".join(parts), name)
                        i = k
                        continue
                i = j
            else:
                i += 1
        # entries keyed by a constant (the crisis message)
        m = re.search(r"CrisisSupport\.CHAT_MESSAGE_EN to\s+((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+)", t)
        if m:
            parts = [read_literal(m.group(1), p.start())[0] for p in re.finditer(r'"', m.group(1)) if False]
            body = m.group(1)
            vals, k = [], 0
            while k < len(body):
                if body[k] == '"':
                    v, k = read_literal(body, k)
                    vals.append(v)
                else:
                    k += 1
            entries["__CRISIS__"] = ("".join(vals), name)
    return entries


def policy_sections(path, list_name):
    t = open(path, encoding="utf-8").read()
    start = t.index(list_name)
    sections, pos = [], start
    while True:
        k = t.find("PolicySection(", pos)
        if k < 0:
            break
        i = k + len("PolicySection(")
        parts = []
        while True:
            while t[i] in " \n\t,+":
                i += 1
            if t[i] == '"':
                v, i = read_literal(t, i); parts.append(v)
            else:
                break
        sections.append((parts[0], "".join(parts[1:])))
        pos = i
    return sections


def literals_in_list(text, name):
    m = re.search(r"val " + name + r"\s*=\s*listOf\((.*?)\n\)", text, re.S)
    if not m:
        return []
    body, out, k = m.group(1), [], 0
    while k < len(body):
        if body[k] == '"':
            v, k = read_literal(body, k); out.append(v)
        else:
            k += 1
    return out


def where_used():
    used = {}
    for path in glob.glob(os.path.join(SRC, "*.kt")):
        name = os.path.basename(path)
        if name.startswith("Translations") or name == "Strings.kt":
            continue
        t = open(path, encoding="utf-8").read()
        for m in re.finditer(r'\btrf?\(\s*"((?:[^"\\]|\\.)*)"', t):
            used.setdefault(m.group(1), name)
        if name in ("DesignSystem.kt", "Safety.kt", "Exercises.kt"):
            for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', t):
                used.setdefault(m.group(1), name)
    return used


def export():
    entries = parse_maps()
    used = where_used()
    rows = []
    for key_src in [k for k in entries if k != "__CRISIS__"]:
        pass
    # Keys in the maps are real text now; look up usage by source form.
    for key, (hindi, fname) in entries.items():
        if key == "__CRISIS__":
            continue
        screen = SCREEN_OF_FILE.get(used.get(source_form(key), ""), "Other")
        rows.append(("text", screen, key, key, hindi))
    rows.sort(key=lambda r: (r[1], r[3]))

    # Long texts reviewed on the sheet but applied by hand.
    safety = open(os.path.join(SRC, "Safety.kt"), encoding="utf-8").read()
    m = re.search(r"const val CHAT_MESSAGE_EN =\s*((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+)", safety)
    en_crisis, k = [], 0
    while m and k < len(m.group(1)):
        if m.group(1)[k] == '"':
            v, k = read_literal(m.group(1), k); en_crisis.append(v)
        else:
            k += 1
    extra = [("crisis", "Safety: message shown when someone writes crisis words (IMPORTANT)", "", "".join(en_crisis), entries.get("__CRISIS__", ("", ""))[0])]

    en_pol = policy_sections(os.path.join(SRC, "Privacy.kt"), "internal val privacyPolicy = listOf(")
    hi_pol = policy_sections(os.path.join(SRC, "TranslationsHindiLong.kt"), "internal val privacyPolicyHindi")
    for (et, eb), (ht, hb) in zip(en_pol, hi_pol):
        extra.append(("policy", "Privacy policy: heading", "", et, ht))
        extra.append(("policy", f"Privacy policy: {et}", "", eb, hb))

    tr_text = open(os.path.join(SRC, "TrivialReplies.kt"), encoding="utf-8").read()
    for en_name, hi_name, label in [("greetingReplies", "greetingRepliesHi", "greeting"), ("thanksReplies", "thanksRepliesHi", "thanks"),
                                    ("goodbyeReplies", "goodbyeRepliesHi", "goodbye"), ("acknowledgementReplies", "acknowledgementRepliesHi", "ok / got it")]:
        for en, hi in zip(literals_in_list(tr_text, en_name), literals_in_list(tr_text, hi_name)):
            extra.append(("reply", f"Companion quick reply ({label})", "", en, hi))

    safety_hi = re.search(r"hindiCrisisPhrases = listOf\((.*?)\n\)", safety, re.S)
    words = []
    if safety_hi:
        body, k = safety_hi.group(1), 0
        while k < len(body):
            if body[k] == '"':
                v, k = read_literal(body, k); words.append(v)
            else:
                k += 1
    extra.append(("crisiswords", "Safety: Hindi words that trigger the crisis message (add/remove words)", "",
                  "(English list is in CRISIS_REVIEW.md)", ", ".join(words)))

    os.makedirs(os.path.dirname(CSV_PATH), exist_ok=True)
    with open(CSV_PATH, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["Kind", "Where it appears", "Key (do not edit)", "English", "Hindi (current)", "Hindi (your correction)", "Notes"])
        for kind, screen, key, en, hi in rows + extra:
            w.writerow([kind, screen, source_form(key) if kind == "text" else "", en, hi, "", ""])
    sheet_path = CSV_PATH.replace("hindi_review.csv", "hindi_review_sheet.csv")
    with open(sheet_path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["Where it appears", "English", "Hindi (current)", "Hindi (your correction)", "Notes", "Type (do not edit)"])
        for kind, screen, key, en, hi in rows + extra:
            w.writerow([screen, en, hi, "", "", kind])
    print(f"Wrote {len(rows) + len(extra)} rows to {os.path.relpath(CSV_PATH, ROOT)} and {os.path.relpath(sheet_path, ROOT)}")
    print(f"  {len(rows)} short texts, {len(extra)} long texts / replies / crisis items")


def kt(v):
    return source_form(v)


def import_corrections(path=None):
    rows = list(csv.DictReader(open(path or CSV_PATH, encoding="utf-8-sig")))
    if not rows or "Hindi (your correction)" not in rows[0]:
        sys.exit(
            "This file is not the review sheet: it has no 'Hindi (your correction)' column.\n"
            "In Google Sheets, click the 'Hindi review' tab (not 'How to review') and choose\n"
            "File -> Download -> Comma-separated values (.csv), then run the import again with that file."
        )
    for r in rows:   # the Google Sheet layout has no key column: the key is the English text
        r.setdefault("Kind", r.get("Type (do not edit)", ""))
        if "Key (do not edit)" not in r:
            r["Key (do not edit)"] = source_form(r["English"]) if r["Kind"] == "text" else ""
    texts = {name: open(os.path.join(SRC, name), encoding="utf-8").read() for name in MAP_FILES}
    applied, manual = 0, []
    for r in rows:
        fix = (r["Hindi (your correction)"] or "").strip()
        if not fix:
            continue
        if r["Kind"] != "text":
            manual.append(r)
            continue
        key = r["Key (do not edit)"]
        done = False
        for name, t in texts.items():
            pat = re.compile(r'(^\s*"' + re.escape(key) + r'" to ")((?:[^"\\\n]|\\.)*)(",?\s*$)', re.M)
            if pat.search(t):
                texts[name] = pat.sub(lambda m: m.group(1) + kt(fix) + m.group(3), t, count=1)
                applied += 1
                done = True
                break
        if not done:
            manual.append(r)
    for name, t in texts.items():
        open(os.path.join(SRC, name), "w", encoding="utf-8").write(t)
    print(f"Applied {applied} corrections.")
    if manual:
        print(f"\n{len(manual)} corrections need to be applied by hand (send them to the developer):")
        for r in manual:
            print(f"- [{r['Kind']}] {r['Where it appears']}\n    English: {r['English'][:80]}\n    Correction: {r['Hindi (your correction)'][:120]}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "export":
        export()
    elif cmd == "import":
        import_corrections(sys.argv[2] if len(sys.argv) > 2 else None)
    else:
        sys.exit(__doc__)

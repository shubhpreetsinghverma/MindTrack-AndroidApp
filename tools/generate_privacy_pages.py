#!/usr/bin/env python3
"""Rebuilds PRIVACY_POLICY.md and public/privacy.html from the policy text in Privacy.kt.

Privacy.kt is the single source of truth, so the app, the markdown file and the hosted page never drift apart.
Run from the project root:  python3 tools/generate_privacy_pages.py
"""
import html
import re
import sys

SOURCE = "app/src/main/java/com/mindtrack/app/Privacy.kt"


def read_literal(text, i):
    """Reads one Kotlin string literal starting at text[i] == '"'. Returns (value, next_index)."""
    assert text[i] == '"'
    i += 1
    out = []
    while text[i] != '"':
        if text[i] == "\\":
            nxt = text[i + 1]
            out.append({"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'"}.get(nxt, nxt))
            i += 2
        else:
            out.append(text[i])
            i += 1
    return "".join(out), i + 1


def parse_policy(text):
    start = text.index("internal val privacyPolicy = listOf(")
    end = text.index("private fun consentKey")
    block = text[start:end]
    sections = []
    pos = 0
    while True:
        k = block.find("PolicySection(", pos)
        if k < 0:
            break
        i = k + len("PolicySection(")
        parts = []
        # Read: "title", then "a" + "b" + ... until the closing parenthesis.
        while True:
            while block[i] in " \n\t,+":
                i += 1
            if block[i] == '"':
                value, i = read_literal(block, i)
                parts.append(value)
            else:
                break   # reached ')'
        sections.append((parts[0], "".join(parts[1:])))
        pos = i
    return sections


def to_markdown(sections):
    out = ["# MindTrack privacy policy\n",
           "_Last updated: October 2026. Hosted copy: public/privacy.html (see the README for how to publish it)._\n"]
    for title, body in sections:
        out.append(f"## {title}\n\n{body}\n")
    return "\n".join(out)


def to_html(sections):
    parts = []
    for title, body in sections:
        blocks = []
        bullets = []
        for line in body.split("\n"):
            if line.startswith("•"):
                bullets.append(line[1:].strip())
                continue
            if bullets:
                blocks.append("<ul>" + "".join(f"<li>{html.escape(b)}</li>" for b in bullets) + "</ul>")
                bullets = []
            if line.strip():
                blocks.append(f"<p>{html.escape(line.strip())}</p>")
        if bullets:
            blocks.append("<ul>" + "".join(f"<li>{html.escape(b)}</li>" for b in bullets) + "</ul>")
        parts.append(f"<h2>{html.escape(title)}</h2>\n" + "\n".join(blocks))
    return PAGE.replace("{{BODY}}", "\n".join(parts))


PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>MindTrack privacy policy</title>
<style>
body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0b1020;color:#e9e6ff;margin:0;line-height:1.6}
main{max-width:720px;margin:0 auto;padding:32px 20px 64px}
h1{font-size:28px;margin-bottom:4px}h2{font-size:18px;margin-top:32px;color:#b8a5ff}
p,li{color:#c9cde0}li{margin:6px 0}small{color:#8b93ad}a{color:#b8a5ff}
@media (prefers-color-scheme: light){body{background:#fbfaff;color:#1b1f3b}h2,a{color:#5b46c4}p,li{color:#3a3f5c}small{color:#6b7090}}
</style></head><body><main>
<h1>MindTrack privacy policy</h1><small>Last updated: October 2026</small>
{{BODY}}
</main></body></html>
"""

if __name__ == "__main__":
    sections = parse_policy(open(SOURCE, encoding="utf-8").read())
    if len(sections) < 6:
        sys.exit(f"Only found {len(sections)} sections; check {SOURCE}")
    open("PRIVACY_POLICY.md", "w", encoding="utf-8").write(to_markdown(sections))
    open("public/privacy.html", "w", encoding="utf-8").write(to_html(sections))
    for title, body in sections:
        print(f"{title}: {len(body)} characters")

#!/usr/bin/env python3
"""Build a responsive GitLab Pages website from the repository README."""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "public"
README = ROOT / "README.md"
PRIVACY_POLICY = ROOT / "privacy-policy.html"
LICENSE = ROOT / "LICENSE"
APP_ICON = ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp"

STYLES = r"""
:root {
  color-scheme: light;
  --navy: #011b39;
  --blue: #1769aa;
  --ink: #263238;
  --muted: #607080;
  --line: #d9e2ec;
  --paper: #fff;
  --page: #f2f6fb;
}
* { box-sizing: border-box; }
html { scroll-behavior: smooth; }
body {
  margin: 0;
  padding: 28px 16px 48px;
  color: var(--ink);
  background: var(--page);
  font: 16px/1.75 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
}
.page-shell { width: min(100%, 1000px); margin: 0 auto; }
.topbar {
  margin-bottom: 20px;
  padding: 15px 22px;
  border-radius: 16px;
  color: #fff;
  background: var(--navy);
  box-shadow: 0 8px 24px rgb(1 27 57 / 14%);
}
.brand { color: inherit; text-decoration: none; font-size: 1.12rem; font-weight: 750; }
.content {
  overflow: hidden;
  padding: clamp(22px, 5vw, 54px);
  border: 1px solid #e4ebf2;
  border-radius: 18px;
  background: var(--paper);
  box-shadow: 0 10px 34px rgb(29 56 84 / 7%);
}
.content h1, .content h2, .content h3, .content h4 { color: var(--navy); line-height: 1.3; }
.content h1 { margin-top: .25em; font-size: clamp(2rem, 5vw, 3rem); }
.content h2 { margin-top: 2em; padding-bottom: .35em; border-bottom: 1px solid var(--line); }
.content a { color: var(--blue); text-decoration-thickness: 1px; text-underline-offset: 3px; }
.content a:hover { color: #0b477d; }
.content img { max-width: 100%; height: auto; }
.content table { display: block; width: 100%; overflow-x: auto; border-collapse: collapse; }
.content th, .content td { padding: 9px 12px; border: 1px solid var(--line); text-align: start; }
.content th { background: #f4f8fc; }
.content blockquote { margin-inline: 0; padding: 4px 18px; border-inline-start: 4px solid #d6a42a; color: var(--muted); }
.content pre { overflow-x: auto; padding: 16px; border-radius: 10px; background: #f3f6fa; }
.content code { overflow-wrap: anywhere; }
.content hr { margin: 30px 0; border: 0; border-top: 1px solid var(--line); }
@media (max-width: 600px) {
  body { padding: 12px 10px 28px; font-size: 15px; }
  .topbar { margin-bottom: 12px; padding: 12px 16px; border-radius: 12px; }
  .content { padding: 20px 16px; border-radius: 13px; }
  .content th, .content td { padding: 8px; }
}
"""


def build(output: Path) -> None:
    for path in (README, PRIVACY_POLICY, LICENSE, APP_ICON):
        if not path.is_file():
            raise FileNotFoundError(f"Required Pages input is missing: {path.relative_to(ROOT)}")

    output.mkdir(parents=True, exist_ok=True)
    readme_html = markdown.markdown(
        README.read_text(encoding="utf-8"),
        extensions=["tables", "fenced_code", "sane_lists"],
        output_format="html5",
    )

    page = f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="Borsat Dinari — Iraqi exchange rates, global currencies, market news, widgets, and downloads.">
  <meta name="theme-color" content="#011b39">
  <title>Borsat Dinari — بورصة ديناري</title>
  <style>{STYLES}</style>
</head>
<body>
  <div class="page-shell">
    <header class="topbar">
      <a class="brand" href="./">Borsat Dinari <span lang="ar" dir="rtl">— بورصة ديناري</span></a>
    </header>
    <main class="content" aria-label="Borsat Dinari project information">
      {readme_html}
    </main>
  </div>
</body>
</html>
"""

    (output / "index.html").write_text(page, encoding="utf-8")
    shutil.copy2(PRIVACY_POLICY, output / "privacy-policy.html")
    shutil.copy2(LICENSE, output / "LICENSE")
    icon_rel = Path("app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp")
    icon_dest = output / icon_rel
    icon_dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(APP_ICON, icon_dest)
    print(f"Built GitLab Pages site in {output}")
    print("Included README, privacy policy, license, and app icon")


if __name__ == "__main__":
    target = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else DEFAULT_OUTPUT
    build(target)

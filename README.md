# Borsat Dinari — بورصة ديناري

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Borsat Dinari" />
</p>

<p align="center"><b>Live Iraqi dinar exchange rates, world currencies, crypto, metals and market news — free, ad-free, open source.</b></p>

<p align="center">
  <img alt="version" src="https://img.shields.io/badge/version-1.1.7-blue" />
  <img alt="platform" src="https://img.shields.io/badge/platform-Android-green" />
  <img alt="language" src="https://img.shields.io/badge/Kotlin-100%25-purple" />
  <img alt="license" src="https://img.shields.io/badge/license-open%20source-lightgrey" />
</p>

Borsat Dinari shows the price of the US dollar and other currencies against the Iraqi dinar (IQD) in every Iraqi governorate and the Kurdistan Region. Prices refresh automatically every 15 minutes while the app is in use, and you can refresh manually at any time.

---

## Features

- Free, ad-free and non-profit — forever
- No sensitive permissions, no intrusive notifications, no personal data collection
- Very light and fast
- Local markets (by city), world currencies, cryptocurrencies and metals, each in its own tab
- Market news ("تنويه" notices) with a 60-day archive
- Price history for the last 60 days, with charts and PDF export
- Built-in currency calculator and home-screen widgets (calculator and up to 3 favorites)
- Haptic feedback across the app
- Four languages: Arabic (default), English, Türkçe, Kurdî/کوردی — layout stays RTL in every language
- Light and dark themes
- Open source

## What's new in 1.1.7

### Interface
- **Unified header on every sub-page.** *Terms of Use*, *App Guide*, *Help & Support* and *Social Channels* now use exactly the same header as the home screen: the navy `#011B39` bar, the flags / gold / Bitcoin / dollar / dinar artwork on the left (tap it to go back) and the page title on the right.
- **Calculator dropdown icons.** Every market icon in the calculator — both the dropdown list and the source/destination selectors — now has the same thin outer ring as the icons in the price list.
- **Price reveal animation fixed.** It now survives page re-renders during a refresh (state is kept per price, not per list), so every changed price animates visibly.
- **Price reveal (details).** The scrambling-digits animation in the price list now actually plays (the changed-price flag was being cleared before rows were bound) and uses the same rhythm as the widgets — 12 steps × 75 ms — only for prices that really changed.
- **Stadium-wave header title.** When prices refresh (pull-to-refresh or the 15-minute auto refresh) the page title plays a single "stadium wave": each letter lifts, grows slightly and flashes green in sequence, then settles back. It replaces the old thin blue refresh line, which was removed from every page. The price history page plays the same wave when syncing and when switching between day / week / month / two months or picking a range.
- **Enable news setting.** New switch under *App appearance* in Settings. Turning news off hides the news card on the home page and raises the favorites limit from 5 to 8 (the four quick-action squares slide under the eight favorites). Default: news on.
- **Gradle wrapper added.** `gradlew`, `gradlew.bat` and the official `gradle-wrapper.jar` (distribution pinned to Gradle 8.13 with its SHA-256) are now part of the project, so builds are reproducible. The app has no tracker or proprietary dependencies: `releaseRuntimeClasspath` contains only AndroidX and Material libraries.
- **Perfectly round metal icons.** The seven metal icons (aluminum, copper, gold, nickel, palladium, platinum, silver) now have the circle baked into the image itself, so they are round everywhere — the price list, the in-app calculator, the calculator widget, favorites widgets 1 and 3, home-screen shortcuts and PDF exports — instead of only in the list.

### Carried over from 1.1.6
- Redesigned price rows with vertical dividers, a time-over-date column with clock and calendar icons, and the unit shown next to the currency symbol.
- New header artwork (5 flags) with a smooth fade into the header color; status bar and bottom navigation share the same blue.
- Shorter navigation labels in all languages and a slimmer navigation bar.
- Haptic feedback for refresh, drag and taps.
- Smarter news fetching: the archive is back-filled only where there are gaps, with a 30-minute cooldown, instead of re-scanning old pages.
- Price history back-fill for crypto and metals, same as for cities.
- Chinese yuan (CNY) added to world currencies, the calculator and the calculator widget.
- Build hygiene: Kotlin warnings fixed, APK Signature Scheme v3 enabled, VCS metadata and noisy `META-INF` entries stripped from release builds.

## Color guide

| Color | Meaning |
|---|---|
| 🔴 Red | Price went down |
| 🟢 Green | Price went up |
| ⚪ Gray | Price is unchanged or no update is available |

## Widgets

Add a widget from your home screen to see up to 3 favorite markets with automatic refresh every 15 minutes and an instant-refresh button, or use the calculator widget for quick conversions.

## Building

```bash
# the project ships the Gradle wrapper (Gradle 8.13, checksum-pinned)
# debug build
./gradlew assembleDebug

# release build (requires keystore.properties — see keystore.properties.template)
./gradlew assembleRelease
```

- Package: `com.mohammed.currencyapp` · Application ID: `com.mokhtar.com`
- Version: **1.1.7** (versionCode 19)
- Release signing uses APK Signature Scheme v3.
- Data source: the public Telegram channel [`t.me/s/BorsatDinari`](https://t.me/s/BorsatDinari), scraped on-device.

## Repository mirrors

The GitHub repository is mirrored automatically to GitLab (full mirror, LFS, releases and APKs) by `.github/workflows/mirror.yml` on every push.

- GitHub: <https://github.com/techmohammed/Borsat-Dinari>
- GitLab: <https://gitlab.com/techmohammed/Borsat-Dinari>

## Disclaimer

Currency prices are provided for information only. The app takes no responsibility for any financial decision made while trading or exchanging.

## App info

- **Version:** 1.1.7
- **Designed by:** Eng. Mohammed Mokhtar Faiq
- **Release year:** 2026
- **Last update:** November 2026

## Follow us

| Platform | Link |
|---|---|
| Telegram | [t.me/BorsatDinari](https://t.me/BorsatDinari) |
| WhatsApp channel | [Borsat Dinari](https://whatsapp.com/channel/0029VbDz68h7YSd54EV6rw0R) |
| Instagram | [@borsatdinari](https://www.instagram.com/borsatdinari) |
| Facebook | [Borsat Dinari](https://www.facebook.com/share/1Bv8F5CUZE/) |
| X | [@Borsatdinari](https://x.com/Borsatdinari) |
| Threads | [@borsatdinari](https://www.threads.com/@borsatdinari) |
| TikTok | [@borsatdinari](https://tiktok.com/@borsatdinari) |
| Pinterest | [borsatdinari](https://www.pinterest.com/borsatdinari/) |
| GitHub | [techmohammed/Borsat-Dinari](https://github.com/techmohammed/Borsat-Dinari) |
| GitLab | [techmohammed/Borsat-Dinari](https://gitlab.com/techmohammed/Borsat-Dinari) |
| Email | [BorsatDinari@gmail.com](mailto:BorsatDinari@gmail.com) |

### Download

| Store | Link |
|---|---|
| APKPure | [com.mokhtar.com](https://apkpure.com/dp/com.mokhtar.com) |
| F-Droid | Coming soon |
| Google Play Store | Coming soon |
| Aptoide | Coming soon |
| Uptodown | Coming soon |

## License

No copyright and no distribution restrictions — use the app in any way you like. See [LICENSE](LICENSE).

---

> "In a world full of noise, make your mark in silence —
> the rain that makes crops grow makes no thunder."

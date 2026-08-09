<div align="center">

# 🎬 Litube Enhanced

**A fixed and enhanced version of [Litube](https://github.com/HydeYYHH/litube) — an advanced WebView wrapper for YouTube.**

[![Release](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapi.github.com%2Frepos%2Fdwicao%2FLitubeEnhanced%2Freleases%2Flatest&query=%24.tag_name&style=for-the-badge&label=Release&color=red)](https://github.com/dwicao/LitubeEnhanced/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg?style=for-the-badge)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg?style=for-the-badge)](https://www.android.com/)

</div>

---

## 🛠️ What's Fixed

| Area | Fix |
|---|---|
| Playback | HTTP 403 video playback |
| Playback | Queue & autoplay |
| Playback | 4K/1440p crash — hard cap to 1080p |
| Playback | Color issues (blue-ish skin) |
| Downloads | SABR support + retry & resume |
| Player | Fullscreen rotation lock |
| WebView | Voice search (manual permission) |

## ✨ Features

- **Ad-free playback** — enjoy YouTube without ads
- **Sponsor-block** — skip sponsor segments automatically
- **Background & Picture-in-Picture** — keep watching while using other apps
- **Mini-player support**
- **Local queue & autoplay**
- **Built-in video and playlist downloader** (video/audio)
- **Live stream chat support**
- **Samsung DeX support**

## 📸 Screenshots

<img title="" src="https://github.com/HydeYYHH/litube/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" alt="" width="200"><img title="" src="https://github.com/HydeYYHH/litube/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" alt="" width="200"><img title="" src="https://github.com/HydeYYHH/litube/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" alt="" width="200">

## 🔨 Build from Source

**Prerequisites:** JDK 17+, Android SDK, and a recent Gradle.

```bash
./gradlew assembleDebug
```

The debug APK will be generated at `app/build/outputs/apk/debug/`.

## 📥 Download

Pre-built APKs are available on the [Releases page](https://github.com/dwicao/LitubeEnhanced/releases).

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

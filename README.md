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
| Downloads | SABR support + retry & resume |
| Player | Fullscreen rotation lock |
| WebView | Voice search (manual permission) |
| DeX | Full desktop experience (auto-fullscreen, shortcuts, mouse, download button) |
| Home feed | Community posts & shorts shelf hidden; faster infinite scroll |

## 🖥️ Samsung DeX Mode

Litube now feels like a desktop app on Samsung DeX:

- **Auto-detected** — DeX Mode toggle in settings (auto-detect on by default; turn it off to force the mobile layout).
- **Desktop layout** — desktop User-Agent so YouTube serves the desktop site: multi-column feed, side-by-side player + comments.
- **Auto-fullscreen** — every video opens fullscreen immediately and stays there (no small/overlay mode, F disabled, **Esc = Back** which closes the video and returns to the previous page).
- **Keyboard shortcuts** — `Space` play/pause · `←`/`→` seek ±5s · `↑`/`↓` volume · `M` mute · `Shift+←/→` queue prev/next · `Ctrl+Shift+R` reload.
- **Mouse support** — scroll wheel over the player adjusts volume, right-click opens a context menu (play/pause, fullscreen, PiP, download, reload).
- **Download button** — the fullscreen button (useless in forced fullscreen) becomes a **Download** button.
- **Bottom-center controls** — speed / resolution / fullscreen(→download) buttons centered; the quality popup can no longer "click through" and accidentally switch videos.
- **Fast feed** — the feed preloads ~10 rows ahead so infinite scroll never stalls.
- **Window memory** — DeX window size/position is remembered across launches.

## 📺 Local Watch History

- Every played video is recorded **locally** (video ID, title, thumbnail, timestamp) — no account, fully private, capped at 100 entries.
- **History button** (clock icon) in the player opens a bottom sheet: newest first, tap to replay, delete single entries, or **Clear all** with confirmation.

## 🧹 Home Feed Cleanup

- **Community posts are hidden** from the home feed (they rendered huge and pushed videos out of view).
- **Shorts shelf is hidden** from the home feed.
- **Faster infinite scroll** — the feed stays several rows ahead so the bottom never stalls.

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

## 📋 Changelog

*Changes authored by **dwicao**.*

### 2026-08-09

**Downloads (fixes #291: >360p downloads failing on SABR)**
- Per-client request headers in the downloader (VR / Android / iOS / web User-Agents) so googlevideo stops answering 403 on >360p streams.
- Downloads fall back to a muxed MPEG-4 stream when the selected stream can't be downloaded, and failed chunks are retried (3 attempts) with resume from the last successful chunk.

**Playback**
- 403-proofing: the stream datasource retries once with the alternate client profile (browser GET ↔ app POST + matching User-Agent) before failing.
- 4K/1440p crash fix: hard cap to 1080p on devices whose hardware decoder can't play 4K (with an "Allow 4K playback" setting on capable devices).
- Fullscreen rotation lock setting (keep video orientation in fullscreen).

**Samsung DeX Mode**
- DeX detection + "DeX Mode" setting (auto-detect, default on; off forces mobile layout).
- Desktop layout via desktop User-Agent; 70/30 split replaced by forced auto-fullscreen (no small mode).
- Full keyboard shortcuts, mouse scroll-volume, right-click player menu, Esc = Back.
- Fullscreen button repurposed as a Download button; speed/resolution controls moved to bottom-center.
- Phantom-click protection (blank overlay + event consumption) so changing quality can't switch videos; WebView surface refresh on player close (no more blank screens).

**Home feed**
- Community posts and the shorts shelf hidden; aggressive feed preloading in DeX (10 rows ahead).

**Watch history**
- New: local watch history with a history button, bottom sheet UI, per-item delete, and clear-all.

**Voice search**
- Runtime microphone permission request with a "go to Settings" fallback when denied.

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

# VEX MUSIC

**Your music. Your lyrics. Your control.**

A native Android music player written in Kotlin with Jetpack Compose, Media3 ExoPlayer, Room
and a multi-provider lyrics engine with **mandatory manual lyrics source selection**.

- Package: `com.vexmusic.player`
- Min SDK: 26 (Android 8.0) · Target/Compile SDK: 34
- Build system: Gradle 8.7 (Kotlin DSL) · AGP 8.4.2 · Kotlin 1.9.24
- Design: VEX Purple dark theme (`#A855F7` on `#0D0C10`) with compact square-edged components

This repository contains a real Android Studio project. There is no WebView, no PWA and no
browser playback anywhere in the app.

---

## 1. Download the debug APK

1. Open the repository on GitHub → **Actions**
2. Select the latest successful **Build VEX MUSIC Debug APK** run
3. Download the artifact **`VEX-MUSIC-Android-Debug`**
4. Unzip it and install `app-debug.apk` on an Android 8.0+ device
   (you may need to allow "install from unknown sources")

The workflow (`.github/workflows/android-build.yml`) runs on every push to `main`/`master`,
on pull requests and on manual dispatch. It runs the unit tests, builds with
`./gradlew assembleDebug`, verifies the APK is non-empty and uploads it. No signing secrets
are required.

## 2. Build locally

```bash
git clone <this-repo>
cd VEX-MUSIC
./gradlew assembleDebug          # output: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests (lyrics parsers + match scorer)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK platform 34 + build-tools 34.0.0.
Create `local.properties` with `sdk.dir=/path/to/Android/sdk` (Android Studio does this for you).

> `gradle.properties` ships with conservative JVM heap settings so the project also builds on
> small CI containers. On a workstation you can raise `org.gradle.jvmargs` freely.

---

## 3. Features

### Library
- Import single/multiple audio files through the **Storage Access Framework** (persistable URI permissions)
- Optional **MediaStore scan** of audio already indexed by Android
- Metadata via `MediaMetadataRetriever` (title, artist, album, duration, year, track no., embedded artwork)
- Embedded artwork is extracted once into the app's private `files/artwork` folder
- Duplicate detection (same URI, or same title+artist with <2s duration delta)
- Edit metadata, delete single or batch entries, sort (title/artist/album/duration/recently added),
  browse by artist and album, instant search with search history
- **Audio files are never copied, uploaded or Base64-encoded** — only a content URI + metadata is stored in Room

### Playback (Media3)
- `ExoPlayer` inside a `MediaSessionService` → real background playback, lock-screen controls,
  system media notification, headset + Bluetooth media buttons
- Audio focus handling (`setAudioAttributes(..., handleAudioFocus = true)`) and
  "becoming noisy" (headphone unplug) handling, `WAKE_MODE_LOCAL`
- Play/pause/next/previous/seek, shuffle, repeat off/one/all, playback speed, volume
- Full queue management: add, play next, remove, reorder, clear, jump to index
- Resume position per track, recently played, play counts, listening history
- Player errors (corrupted/unsupported files) skip to the next item instead of killing the session

### Multi-provider lyrics engine
Each provider is an independent adapter behind the `LyricsProvider` interface.

| Provider | Status | Notes |
|---|---|---|
| **LRCLIB** | Implemented, verified live | Primary. `GET /api/search` with title/artist, fallback free-text query. Returns synced LRC **and** plain variants as separate results. |
| **KuGou** | Implemented, verified live | `krcs.kugou.com/search` → `lyrics.kugou.com/download` (Base64 LRC). Public endpoints, no auth bypass. |
| **NetEase** | Implemented, verified live | `music.163.com/api/search/get` → `/api/song/lyric`. Synced LRC. |
| **Lyrics.ovh** | Implemented, verified live | Key-free plain-text endpoint. |
| **Embedded (file tag)** | Implemented, offline | Minimal ID3v2 `USLT` reader built into the app (`util/Id3LyricsExtractor.kt`). |
| **Paxsenix Lyrically** | Adapter present, **disabled by default** | Verified against `https://lyrics.paxsenix.org/openapi.json`: every search endpoint (`/musixmatch`, `/netease`, `/qq`, `/spotify`, `/kugou`, `/youtube`) now returns HTTP 403 *"endpoint no longer available"*. Only `/apple-music/lyrics?id=` is open and it needs an Apple Music catalogue id that cannot be derived from a local file. The adapter reports this exact reason instead of faking results. |
| ArchiveTune / SimpMusic / BetterLyrics / Unison | **Not vendored** | These are GPL-3.0 YouTube-Music-oriented applications. Their lyrics features depend on YouTube/InnerTube ids that a local MP3 does not contain, and copying their code would impose GPL-3.0 on this project. VEX MUSIC instead talks to the same underlying public services (LRCLIB, KuGou, NetEase) through its own independent adapters. Adding a new source only requires implementing `LyricsProvider`. |

Formats: standard LRC, **enhanced (word-level) LRC**, multiple timestamps per line,
TTML (including word `<span>` timings), and plain text. Nothing is truncated —
long rap/drill lyrics are kept in full. Plain text is **never** displayed as synchronized.

### Manual lyrics selection (mandatory behaviour)
- A search **offers** results; it never activates one.
- Results list shows provider, title, artist, album, duration, language (when available),
  synchronization type, a preview, and a relevance score.
- The score is a documented method — normalised token overlap of title and artist plus a
  duration-proximity bonus (`MatchScorer`) — not an invented "AI confidence" value.
  The reason string ("title 100%, artist 100%, duration ±0s") is shown in the UI.
- Similar results are all listed; nothing is silently discarded and results are never merged.
- "Use this" persists provider + result id + content in Room; the next playback reuses it offline.
- Controls: *Preview*, *Search again*, *Change source*, *Return to automatic search*,
  *Import .lrc/.txt*, *Edit lyrics*, per-track timing offset (±0.25 s).
- No results → clear message plus manual import / manual writing. The app never fabricates lyrics.

### Translation
- Modular `TranslationProvider`; default implementation is **MyMemory** (anonymous, **no API key in the APK**).
- Targets: Turkish, English, Spanish, German, French, Portuguese, Italian, Japanese, Korean, Arabic, Russian.
- Translation is off by default and requires the user to enable the online provider. Display
  modes: Original / Translation / Both. Line order and timestamps are preserved,
  originals are never overwritten, translations are cached in their own Room table and
  can be refreshed or retried. Failures are shown as "Translation unavailable: …".
- Heuristic source-language detection (script ranges + stop words) — clearly a heuristic.

### Playlists, favorites, history
Create/rename/delete playlists, add/remove/reorder tracks, play/shuffle a playlist,
favorites, listening history with play dates, listening time, most played tracks and artists,
clearable history and search history.

### Equalizer
Real `android.media.audiofx.Equalizer` + `BassBoost` + `Virtualizer` attached to the live
ExoPlayer audio session. Band count and gain range are read from the device; presets
(Flat, Bass Boost, Treble Boost, Rock, Pop, Hip-Hop, Classical, Electronic, Vocal) are
resampled onto the device's actual band layout. Unsupported devices get an explicit
"not available" message instead of fake sliders.

### Visualizer
Real `android.media.audiofx.Visualizer` (FFT + waveform capture) on the playback session,
rendered with Compose Canvas in Bars / Spectrum / Waveform / Minimal / Circular modes.
Capture starts only while the Now Playing screen is visible, playback is running and the
feature is enabled; it is released on dispose. `RECORD_AUDIO` is requested only after the
user enables the visualizer in Settings, and the real error is reported when the device blocks it.

### Storage manager & backup
Track count, referenced music size, artwork cache, lyrics/translation cache sizes and counts,
playlist and history counts, database size and free device space. Actions: clear lyrics cache,
clear translation cache, clear history, clear **all** app data (with confirmation), and a
versioned JSON backup/restore (v1) covering playlists, favorites, lyrics, translations,
history, metadata and selected lyrics sources. Audio files are excluded by design and are
never deleted.

### Privacy
Audio never leaves the device. Only title/artist/album/duration are sent to the lyrics
providers you enable, over HTTPS. If the user enables translation, lyric text is sent to
MyMemory over HTTPS. No analytics, no tracking, no API keys in the APK.

---

## 4. Project structure

```
VEX-MUSIC/
├── .github/workflows/android-build.yml
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/vexmusic/player/
│       │   │   ├── MainActivity.kt          VexMusicApp.kt (Application + AppContainer DI)
│       │   │   ├── data/database/           Entities, DAOs, VexDatabase (Room)
│       │   │   ├── data/preferences/        SettingsRepository (DataStore)
│       │   │   ├── data/repository/         Music, Playlist, History, Backup/Storage
│       │   │   ├── domain/model/            Track, Playlist, lyrics models
│       │   │   ├── player/service/          PlaybackService (MediaSessionService)
│       │   │   ├── player/controller/       PlayerController (MediaController wrapper)
│       │   │   ├── lyrics/provider/         LyricsProvider + LRCLIB/KuGou/NetEase/ovh/Embedded/Paxsenix
│       │   │   ├── lyrics/parser/           LrcParser, TtmlParser, LyricsParser
│       │   │   ├── lyrics/translation/      TranslationProvider, MyMemory, LanguageDetector
│       │   │   ├── lyrics/repository/       LyricsRepository (multi-search + selection + cache)
│       │   │   ├── audio/                   EqualizerController, VisualizerController
│       │   │   ├── ui/theme|components|screens
│       │   │   └── util/                    Id3LyricsExtractor
│       │   └── res/                         values, values-tr, drawables, adaptive icon
│       └── test/java/com/vexmusic/player/   LyricsParserTest + EqualizerPresetTest
├── build.gradle.kts · settings.gradle.kts · gradle.properties · gradlew
├── PROMPT.md   (full master specification)
└── README.md
```

Architecture: MVVM + repositories, a manual `AppContainer` for dependency injection
(no extra DI framework), Kotlin Coroutines/Flow everywhere, all I/O off the main thread.

---

## 5. Verification status (honest)

The upstream revision documented an earlier APK build, nine JVM unit tests, and live lyrics-provider
probes. Those checks predate the current working changes and do not verify this tree.

Current working changes were not Gradle-verified in this environment:

- `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lint` are blocked because
  no Java runtime/JDK is installed. Android SDK tooling is also unavailable.
- The added equalizer preset tests have not been executed.
- No physical device or emulator is available for playback, AudioEffect, permission, or UI testing.

Do not treat a new APK as produced by this session; build verification remains pending.

---

## 6. License & third-party notes

The code in this repository is original work written for VEX MUSIC. No GPL-3.0 source from
ArchiveTune, SimpMusic, BetterLyrics or Unison has been copied or linked. Lyrics are fetched
from public provider endpoints at the user's request; VEX MUSIC stores them only in the local
cache on the user's device.

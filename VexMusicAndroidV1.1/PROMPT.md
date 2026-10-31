# VEX MUSIC — MASTER PROMPT / FULL TECHNICAL SPECIFICATION

> This file is the authoritative specification of the VEX MUSIC Android project.
> It is written so that an AI coding agent (or a new developer) can continue the project in a
> future session without any additional context. Keep it synchronised with the real code.
> **Documentation rule: never describe an unfinished feature as implemented.**
> Status markers used below: `[DONE]`, `[PARTIAL]`, `[NOT DONE]`, `[BLOCKED – reason]`.

---

## 0. Identity

- Application name: **VEX MUSIC**
- Tagline: *Your music. Your lyrics. Your control.*
- Package / applicationId: `com.vexmusic.player`
- Platform: Android only. Native. Kotlin. Jetpack Compose. Material 3.
- **Forbidden:** WebView, PWA, React/Vite, Capacitor, GitHub Pages, browser playback,
  fake/placeholder functionality, fabricated lyrics, invented provider integrations.
- Primary deliverable: an installable **debug APK** built by GitHub Actions
  (`app/build/outputs/apk/debug/app-debug.apk`, artifact `VEX-MUSIC-Android-Debug`).

## 1. Toolchain (pinned, verified building)

| Item | Version |
|---|---|
| Gradle | 8.7 (Kotlin DSL, wrapper committed) |
| Android Gradle Plugin | 8.4.2 |
| Kotlin | 1.9.24 |
| Compose compiler extension | 1.5.14 |
| Compose BOM | 2024.06.00 |
| Media3 (ExoPlayer + session) | 1.3.1 |
| Room (+ KSP 1.9.24-1.0.20) | 2.6.1 |
| DataStore Preferences | 1.1.1 |
| OkHttp / kotlinx.serialization | 4.12.0 / 1.6.3 |
| Coil Compose | 2.6.0 |
| Navigation Compose | 2.7.7 |
| JDK | 17 |
| minSdk / targetSdk / compileSdk | 26 / 34 / 34 |

No Hilt/Dagger: dependency injection is a hand-written `AppContainer` created in
`VexMusicApp.onCreate()`. WorkManager is **not** used — nothing in the current feature set
needs deferred/guaranteed background work (playback uses a foreground MediaSessionService,
lyrics/translation run in viewModelScope). Add it only if a real need appears.

## 2. Design system `[DONE]`

| Token | Value |
|---|---|
| Primary (VEX Purple) | `#A855F7` |
| Background | `#0D0C10` |
| Secondary background | `#141319` |
| Card | `#1A1920` |
| Text primary | `#FFFFFF` |
| Text secondary | `#A1A1AA` |
| Divider | `#2A2933` |

Dark-only Material 3 scheme (`ui/theme/Theme.kt`), compact 3–12 dp corners, monospaced display
and control typography, vertical purple gradient background, restrained motion, mobile-first
layouts, loading/empty/error states.

## 3. Navigation `[DONE]`

Bottom bar: Home · Library · Search · Playlists · Settings.
Secondary destinations: Favorites, History, Now Playing, Lyrics, Lyrics Sources, Equalizer, Storage.
A persistent **mini player** sits above the bottom bar on every chrome screen; Now Playing,
Lyrics and Lyrics Sources are full-screen (no chrome).

## 4. Architecture

```
MainActivity ─ VexApp(NavHost) ─ Screens(Compose)
                     │
      MainViewModel / LyricsViewModel      (StateFlow only, no blocking work)
                     │
  MusicRepository · PlaylistRepository · HistoryRepository · BackupRepository
  LyricsRepository(providers[], translationProvider) · SettingsRepository(DataStore)
                     │
  Room(VexDatabase) · PlayerController→MediaController→PlaybackService(ExoPlayer)
  EqualizerController · VisualizerController (android.media.audiofx)
```

### Room schema (v1; schema changes must use non-destructive migrations)
- `tracks` — id(md5 of uri), uri, title, artist, album, durationMs, mimeType, sizeBytes, year,
  trackNumber, artworkPath, dateAdded, isFavorite, playCount, lastPlayedAt, lastPositionMs
- `playlists`, `playlist_tracks` (playlistId+trackId PK, position)
- `lyrics` — trackId PK, providerId, providerName, resultId, content, format, syncType,
  language, userSelected, userEdited, offsetMs, updatedAt
- `translations` — (trackId, targetLanguage) PK, sourceHash, providerName, translatedLines, updatedAt
- `history`, `search_history`

> Room SQL note: `key`/`count` are reserved — alias columns as `statKey`/`statCount`.

## 5. Library `[DONE]`

SAF `OpenMultipleDocuments` with persistable read permission + optional MediaStore scan.
`MediaMetadataRetriever` for tags and embedded artwork (saved to `filesDir/artwork/<id>.jpg`).
Formats rely on the ExoPlayer/platform decoders: MP3, WAV, FLAC, M4A, AAC, OGG, OPUS.
Duplicate detection, metadata editing, batch delete, sorting, artist/album grouping, search.
**Never** copy audio, never upload audio, never Base64 audio.

## 6. Playback `[DONE]`

`PlaybackService : MediaSessionService` with ExoPlayer, `AudioAttributes(USAGE_MEDIA,
CONTENT_TYPE_MUSIC)` + `handleAudioFocus=true`, `setHandleAudioBecomingNoisy(true)`,
`WAKE_MODE_LOCAL`, session activity PendingIntent, `onTaskRemoved` stops only when idle.
Manifest: `foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
`POST_NOTIFICATIONS`, `MediaButtonReceiver`.
`PlayerController` wraps a `MediaController`, exposes stable controls in `PlaybackState` and
playhead/buffer information in a separate `PlaybackProgress` flow updated every 500 ms while
playing. Track changes feed history + play counts. Resume positions are checkpointed every
5 seconds and flushed on pause or track changes instead of being written on every UI tick.
Errors skip to the next item. The service also publishes `audioSessionIdFlow` so the
equalizer/visualizer can attach to the real output session.

## 7. Lyrics engine

`LyricsProvider { id, displayName, requiresNetwork, suspend search(LyricsQuery) }`.
`LyricsRepository.searchAll()` fans out with `async` per enabled provider, catches per-provider
errors into `ProviderFailure`, records providers that returned nothing, and ranks results with
`MatchScorer` (documented token-overlap + duration proximity, 0-100 with a reason string).

| Provider | File | Status |
|---|---|---|
| LRCLIB | `LrclibProvider` | `[DONE]` primary; synced + plain offered separately |
| KuGou | `KugouProvider` | `[DONE]` krcs search → base64 LRC download |
| NetEase | `NetEaseProvider` | `[DONE]` search → `/api/song/lyric` |
| Lyrics.ovh | `LyricsOvhProvider` | `[DONE]` plain text |
| Embedded ID3 USLT | `EmbeddedLyricsProvider` + `util/Id3LyricsExtractor` | `[DONE]` offline |
| Paxsenix Lyrically | `PaxsenixProvider` | `[BLOCKED – upstream HTTP 403 on all search endpoints; only /apple-music/lyrics?id= remains and needs an Apple Music id a local file does not have]` disabled by default |
| ArchiveTune / SimpMusic / BetterLyrics / Unison | — | `[NOT DONE – deliberate]` GPL-3.0 projects whose lyrics paths need YouTube/InnerTube ids; code not vendored to avoid licence contamination. Equivalent public services are reached through our own adapters. |

**To add a provider:** implement `LyricsProvider`, register it in `AppContainer.providers`,
add its id to `defaultEnabledProviders` if it should be on by default. It automatically appears
in Settings and in the multi-search. Never bypass auth/DRM/rate limits; always fail with a
readable message.

### Parsers `[DONE]`
`LrcParser` (standard LRC, metadata tags, `[offset:]`, multiple timestamps per line, enhanced
`<mm:ss.xx>` word timings, `toLrc` serialisation) and `TtmlParser` (`<p begin/end>` + `<span>`
word timings, entity unescaping, `hh:mm:ss.mmm` / `mm:ss.mmm` / `12.5s` clocks).
`LyricsParser.detectFormat/detectSyncType` classify PLAIN / LRC / ENHANCED_LRC / TTML.
No truncation anywhere. QRC is `[NOT DONE]`.

### Manual selection `[DONE]` — non-negotiable behaviour
1. Opening a track loads the **saved** selection (offline, no network).
2. If none exists and "automatic search" is on, all enabled providers are queried — results are
   only *offered*.
3. The user previews/compares and presses **Use this**; only then is the result stored
   (`selectResult`) with provider id + result id.
4. Later playbacks reuse it. Controls: Change source, Search again, Return to automatic search,
   Import .lrc/.txt, Edit lyrics, ±0.25 s offset, clear selection.
5. Never merge providers, never auto-finalise, never fabricate lyrics. No results ⇒ explicit
   message + manual import/write.

### Translation `[DONE]`
`TranslationProvider` interface; `MyMemoryTranslationProvider` (anonymous, chunked to the
documented 500-char limit, `|||` separator with per-line fallback). Translation is off by default;
the user must enable it before lyrics are sent to the online provider. 11 target languages.
Modes Original / Translation / Both. Originals never overwritten; translations cached per
(trackId, targetLanguage, sourceHash) and refreshable. Heuristic `LanguageDetector`.
**No API keys in the APK.** If a future provider needs a key, put it behind a backend proxy and
keep the app usable without translation.

## 8. Equalizer & visualizer `[DONE]`

`EqualizerController` — real `Equalizer`/`BassBoost`/`Virtualizer` on the ExoPlayer session;
device band count + gain range detected at runtime; 9 presets resampled to the device layout;
settings persisted in DataStore and re-applied when the session id changes; unsupported devices
show the real reason. (A fixed 10-band UI is intentionally **not** forced: Android exposes
whatever the device supports, usually 5.)
`VisualizerController` — real `android.media.audiofx.Visualizer` FFT + waveform capture,
Compose Canvas rendering in BARS / SPECTRUM / WAVEFORM / MINIMAL / CIRCULAR, started only when
enabled + playing + screen visible, released on dispose. Needs `RECORD_AUDIO`, requested only
after the user enables the visualizer in Settings.

## 9. Playlists / favorites / search / history `[DONE]`
Create, rename, delete, add, remove, reorder (persisted positions), play, shuffle; favorites
toggle; instant local search over title/artist/album plus playlists, search history with clear;
history with play date, listened duration, most played tracks/artists, clearable.

## 10. Storage & backup `[DONE]`
Usage report (tracks, music bytes, artwork, lyrics/translation cache counts+bytes, playlists,
history, db size, free space). Clear lyrics/translations/history/all data (confirmed dialog,
audio files never deleted). Versioned JSON backup v1 (`VexBackup`, validated on import:
`app == "VEX MUSIC"`, `formatVersion in 1..1`) containing playlists, favorites, lyrics,
translations, history, track metadata and selected lyrics sources — audio excluded by default.
Optional audio backup is `[NOT DONE]`.

## 11. Settings `[DONE]`
Language (System/EN/TR — `values/` + `values-tr/` resources), automatic lyrics search toggle,
per-provider enable switches, default display mode, translation enable + target language,
resume position, visualizer enable + mode, links to Equalizer and Storage, privacy explanation.
A runtime in-app locale switch that overrides the system language is `[PARTIAL]` — the setting is
stored and the string resources exist, but `AppCompatDelegate.setApplicationLocales` is not wired yet.

## 12. Privacy rules (hard requirements)
Audio stays on device · no uploads · only title/artist/album/duration leave the device for a
lyrics search · lyrics text goes to MyMemory only after the user enables translation · HTTPS only
· no API keys in source · no analytics/tracking · imported backups validated · provider failures
must never crash the app.

## 13. CI `[DONE]`
`.github/workflows/android-build.yml`: push to main/master, PRs, `workflow_dispatch`;
ubuntu-latest; `actions/setup-java@v4` (Temurin 17); detects the runner Android SDK and installs
platform 34/build-tools 34.0.0 with `sdkmanager`; `gradle/actions/setup-gradle@v4` caching;
regenerates the wrapper if the jar is missing; runs `./gradlew testDebugUnitTest` then
`./gradlew assembleDebug`; verifies the APK exists and uploads `VEX-MUSIC-Android-Debug` plus
the unit-test report. `permissions: contents: read`. No secrets, no release signing.
No secrets, no release signing.

## 14. Verification log (only executed checks may be listed here)
- Historical upstream revision documented: `./gradlew assembleDebug` succeeded and produced a
  22 MB APK; `aapt2` reported `com.vexmusic.player`, minSdk 26, targetSdk 34.
- Historical upstream revision documented: `./gradlew testDebugUnitTest` passed 9/9
  `LyricsParserTest` cases.
- Historical provider probes documented: LRCLIB, KuGou, NetEase, Lyrics.ovh and MyMemory returned
  payloads; Paxsenix search endpoints returned HTTP 403.
- Current working tree: `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and
  `./gradlew lint` were attempted but could not start because no Java/JDK is installed; Android
  SDK tooling is also unavailable. The added `EqualizerPresetTest` is not verified, and no APK
  was produced in this session.
- **Not executed:** on-device/emulator runtime testing (playback, background playback,
  notification + lock-screen controls, Bluetooth/headset buttons, Room persistence at runtime,
  equalizer/visualizer hardware behaviour, SAF import, backup/restore round-trip).

## 15. Backlog for the next session
1. Instrumented tests (Room DAO + MediaSession smoke test) and a device test pass.
2. Wire `AppCompatDelegate.setApplicationLocales` for the in-app language switch.
3. QRC parsing; word-by-word karaoke highlighting in the lyrics renderer (word timings are already parsed).
4. Optional audio backup; folder browsing; playlist artwork picker.
5. Release signing workflow (only when requested — debug APK is the current target).
6. Add a non-destructive migration and upgrade test for each future Room schema change.

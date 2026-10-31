const PURPLE = "#A855F7";

const stack = [
  ["Kotlin", "1.9.24"],
  ["Jetpack Compose", "BOM 2024.06"],
  ["Media3 ExoPlayer", "1.3.1"],
  ["Room + KSP", "2.6.1"],
  ["DataStore", "1.1.1"],
  ["AGP / Gradle", "8.4.2 / 8.7"],
];

const providers = [
  ["LRCLIB", "Primary · synced LRC + plain", "live"],
  ["KuGou", "krcs search → base64 LRC", "live"],
  ["NetEase", "search → /api/song/lyric", "live"],
  ["Lyrics.ovh", "Key-free plain text", "live"],
  ["Embedded ID3 USLT", "Offline, reads the file tag", "live"],
  ["Paxsenix Lyrically", "Search endpoints return HTTP 403 upstream", "blocked"],
];

const features = [
  ["Background playback", "MediaSessionService + ExoPlayer, notification, lock screen, headset & Bluetooth, audio focus."],
  ["Manual lyrics selection", "Multi-provider search only offers results; you pick one and the choice is stored per track."],
  ["LRC / Enhanced LRC / TTML", "Word-level timings, multiple timestamps per line, offset, no truncation."],
  ["Translation", "MyMemory, 11 languages, Original / Translation / Both, cached, no API key in the APK."],
  ["Equalizer & visualizer", "Real AudioEffect equalizer and FFT visualizer bound to the playback session."],
  ["Storage & backup", "Usage report, cache clearing, versioned JSON backup (audio excluded)."],
];

export default function App() {
  return (
    <div className="min-h-screen bg-[#0F0F13] text-white">
      <div className="mx-auto max-w-5xl px-6 py-14">
        <header className="mb-12">
          <p className="text-xs tracking-[0.3em] text-zinc-500">NATIVE ANDROID PROJECT</p>
          <h1 className="mt-3 text-5xl font-black" style={{ color: PURPLE }}>
            VEX MUSIC
          </h1>
          <p className="mt-2 text-zinc-400">Your music. Your lyrics. Your control.</p>
          <p className="mt-6 max-w-2xl text-sm leading-6 text-zinc-300">
            This repository is an Android Studio project written in Kotlin with Jetpack Compose —
            not a web app. The deliverable is a debug APK built by GitHub Actions
            (<code className="text-zinc-400">app/build/outputs/apk/debug/app-debug.apk</code>,
            artifact <span style={{ color: PURPLE }}>VEX-MUSIC-Android-Debug</span>).
            This page only documents the repository.
          </p>
        </header>

        <section className="mb-10 rounded-2xl border border-[#2A2933] bg-[#1C1B23] p-6">
          <h2 className="mb-4 text-lg font-semibold">Build it</h2>
          <pre className="overflow-x-auto rounded-xl bg-[#0F0F13] p-4 text-sm text-zinc-300">
{`./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 9/9 lyrics parser + match scorer tests
adb install -r app/build/outputs/apk/debug/app-debug.apk`}
          </pre>
          <p className="mt-4 text-sm text-zinc-400">
            Verified in this session: <span className="text-emerald-400">assembleDebug → BUILD SUCCESSFUL</span>{" "}
            (22 MB APK, minSdk 26, targetSdk 34) and <span className="text-emerald-400">9/9 unit tests passed</span>.
            On-device runtime testing was not possible here and is still pending.
          </p>
        </section>

        <section className="mb-10 grid gap-4 sm:grid-cols-2">
          {features.map(([title, body]) => (
            <div key={title} className="rounded-2xl border border-[#2A2933] bg-[#17171D] p-5">
              <h3 className="font-semibold" style={{ color: PURPLE }}>{title}</h3>
              <p className="mt-2 text-sm leading-6 text-zinc-400">{body}</p>
            </div>
          ))}
        </section>

        <section className="mb-10 rounded-2xl border border-[#2A2933] bg-[#1C1B23] p-6">
          <h2 className="mb-4 text-lg font-semibold">Lyrics providers</h2>
          <ul className="space-y-2 text-sm">
            {providers.map(([name, note, state]) => (
              <li key={name} className="flex flex-wrap items-center gap-3">
                <span
                  className="rounded-full px-2 py-0.5 text-[11px]"
                  style={{
                    background: state === "live" ? "rgba(74,222,128,.12)" : "rgba(248,113,113,.12)",
                    color: state === "live" ? "#4ADE80" : "#F87171",
                  }}
                >
                  {state === "live" ? "implemented" : "blocked upstream"}
                </span>
                <span className="font-medium">{name}</span>
                <span className="text-zinc-500">{note}</span>
              </li>
            ))}
          </ul>
          <p className="mt-4 text-xs leading-5 text-zinc-500">
            ArchiveTune, SimpMusic, BetterLyrics and Unison are GPL-3.0 YouTube-Music oriented apps whose
            lyrics paths need InnerTube IDs a local file does not have, so their code is deliberately not
            vendored; VEX MUSIC talks to the same public services through its own adapters.
          </p>
        </section>

        <section className="mb-10 flex flex-wrap gap-2">
          {stack.map(([name, version]) => (
            <span key={name} className="rounded-full border border-[#2A2933] bg-[#17171D] px-3 py-1 text-xs text-zinc-300">
              {name} <span className="text-zinc-500">{version}</span>
            </span>
          ))}
        </section>

        <footer className="border-t border-[#2A2933] pt-6 text-xs text-zinc-500">
          See <span className="text-zinc-300">README.md</span> for installation and{" "}
          <span className="text-zinc-300">PROMPT.md</span> for the full technical specification.
        </footer>
      </div>
    </div>
  );
}

import {
  AudioLines,
  Check,
  ChevronRight,
  Disc3,
  FileText,
  Heart,
  Home,
  Library,
  Music2,
  Pause,
  Play,
  Plus,
  Repeat,
  Repeat1,
  RotateCcw,
  Search,
  Settings,
  Shuffle,
  SkipBack,
  SkipForward,
  SlidersHorizontal,
  Upload,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from "react";

type Section = "home" | "library" | "search" | "lyrics" | "equalizer" | "settings";
type RepeatMode = "off" | "all" | "one";

type Track = {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: number;
  src: string;
  artwork: string;
  favorite: boolean;
  localObjectUrl?: string;
};

type LyricLine = { timeMs: number | null; text: string };
type AudioGraph = {
  context: AudioContext;
  bands: BiquadFilterNode[];
  bass: BiquadFilterNode;
  panner: StereoPannerNode;
  analyser: AnalyserNode;
};

const COVER = "/images/vex-neon-cover.jpg";
const EQ_FREQUENCIES = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
const EQ_LABELS = ["31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"];
const FLAT = Array.from({ length: 10 }, () => 0);
const PRESETS: Record<string, number[]> = {
  Normal: FLAT,
  Flat: FLAT,
  "Bass Boost": [7, 6, 4, 2, 0, 0, 0, 0, 0, 0],
  "Treble Boost": [0, 0, 0, 0, 0, 1, 2, 4, 6, 7],
  Rock: [5, 4, 2, 0, -1, 1, 3, 4, 5, 5],
  Pop: [-1, 1, 3, 4, 2, 0, -1, -1, 1, 2],
  Classical: [4, 3, 1, 0, -1, 1, 2, 3, 4, 5],
  Jazz: [4, 3, 1, 2, -1, -1, 0, 2, 3, 4],
  "Hip-Hop": [6, 5, 3, 1, -1, 1, 0, 2, 3, 3],
  Electronic: [5, 4, 1, -1, -2, 1, 0, 2, 4, 5],
  Vocal: [-2, -1, 0, 2, 4, 4, 3, 1, 0, -1],
};

const DEMO_TRACK: Track = {
  id: "vex-demo",
  title: "Violet Static",
  artist: "VEX Studio",
  album: "Signal After Dark",
  duration: 16,
  src: "/audio/vex-demo.wav",
  artwork: COVER,
  favorite: false,
};

function readPreference<T>(key: string, fallback: T): T {
  try {
    const saved = localStorage.getItem(key);
    return saved ? (JSON.parse(saved) as T) : fallback;
  } catch {
    return fallback;
  }
}

function formatTime(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) return "0:00";
  const total = Math.floor(seconds);
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, "0")}`;
}

function parseLyrics(raw: string): LyricLine[] {
  const stamp = /\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g;
  const parsed: LyricLine[] = [];
  raw.split(/\r?\n/).forEach((line) => {
    if (/^\s*\[(ar|ti|al|by|offset|length|re|ve|la|lang):/i.test(line)) return;
    const matches = Array.from(line.matchAll(stamp));
    const text = line.replace(stamp, "").trim();
    if (!text) return;
    if (matches.length === 0) {
      parsed.push({ timeMs: null, text });
      return;
    }
    matches.forEach((match) => {
      const fraction = (match[3] ?? "").padEnd(3, "0").slice(0, 3);
      const timeMs = (Number(match[1]) * 60 + Number(match[2])) * 1000 + Number(fraction || 0);
      parsed.push({ timeMs, text });
    });
  });
  return parsed.sort((a, b) => {
    if (a.timeMs === null) return 1;
    if (b.timeMs === null) return -1;
    return a.timeMs - b.timeMs;
  });
}

function activeLineIndex(lines: LyricLine[], timeMs: number) {
  let low = 0;
  let high = lines.length - 1;
  let active = -1;
  while (low <= high) {
    const middle = (low + high) >>> 1;
    const time = lines[middle].timeMs;
    if (time === null || time > timeMs) high = middle - 1;
    else {
      active = middle;
      low = middle + 1;
    }
  }
  return active;
}

function Visualizer({ analyser, active }: { analyser: AnalyserNode | null; active: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const context = canvas?.getContext("2d");
    if (!canvas || !context || !analyser || !active) {
      if (canvas && context) context.clearRect(0, 0, canvas.width, canvas.height);
      return;
    }

    let raf = 0;
    const values = new Uint8Array(analyser.frequencyBinCount);
    const draw = () => {
      const width = canvas.width = Math.max(1, canvas.clientWidth * window.devicePixelRatio);
      const height = canvas.height = Math.max(1, canvas.clientHeight * window.devicePixelRatio);
      context.clearRect(0, 0, width, height);
      analyser.getByteFrequencyData(values);
      const bars = 32;
      const gap = Math.max(2, width / 240);
      const barWidth = (width - gap * (bars - 1)) / bars;
      for (let index = 0; index < bars; index += 1) {
        const value = values[Math.floor(index * values.length / bars)] / 255;
        const barHeight = Math.max(2, value * height * 0.9);
        context.fillStyle = index % 4 === 0 ? "#c084fc" : "#a855f7";
        context.fillRect(index * (barWidth + gap), height - barHeight, barWidth, barHeight);
      }
      raf = window.requestAnimationFrame(draw);
    };
    draw();
    return () => window.cancelAnimationFrame(raf);
  }, [active, analyser]);

  return <canvas ref={canvasRef} className="visualizer-canvas" aria-hidden="true" />;
}

function App() {
  const [section, setSection] = useState<Section>("home");
  const [tracks, setTracks] = useState<Track[]>(() => {
    const favorites = readPreference<string[]>("vex-favorites", []);
    return [{ ...DEMO_TRACK, favorite: favorites.includes(DEMO_TRACK.id) }];
  });
  const [activeTrackId, setActiveTrackId] = useState(DEMO_TRACK.id);
  const [isPlaying, setIsPlaying] = useState(false);
  const [position, setPosition] = useState(0);
  const [duration, setDuration] = useState(DEMO_TRACK.duration);
  const [volume, setVolume] = useState(() => readPreference("vex-volume", 0.78));
  const [repeatMode, setRepeatMode] = useState<RepeatMode>("off");
  const [shuffle, setShuffle] = useState(false);
  const [query, setQuery] = useState("");
  const [eqEnabled, setEqEnabled] = useState(() => readPreference("vex-eq-enabled", true));
  const [eqBands, setEqBands] = useState<number[]>(() => readPreference("vex-eq-bands", FLAT));
  const [eqPreset, setEqPreset] = useState(() => readPreference("vex-eq-preset", "Flat"));
  const [effectsEnabled, setEffectsEnabled] = useState(() => readPreference("vex-effects-enabled", true));
  const [bassBoost, setBassBoost] = useState(() => readPreference("vex-bass", 3));
  const [balance, setBalance] = useState(() => readPreference("vex-balance", 0));
  const [lyricsByTrack, setLyricsByTrack] = useState<Record<string, string>>(() =>
    readPreference("vex-local-lyrics", {}),
  );
  const [lyricsDraft, setLyricsDraft] = useState("");
  const [lyricsOffset, setLyricsOffset] = useState(0);
  const [audioError, setAudioError] = useState("");
  const [analyser, setAnalyser] = useState<AnalyserNode | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioInputRef = useRef<HTMLInputElement | null>(null);
  const lyricsInputRef = useRef<HTMLInputElement | null>(null);
  const audioGraphRef = useRef<AudioGraph | null>(null);
  const autoplayRef = useRef(false);
  const tracksRef = useRef(tracks);

  useEffect(() => {
    tracksRef.current = tracks;
  }, [tracks]);

  const currentTrack = tracks.find((track) => track.id === activeTrackId) ?? tracks[0] ?? null;
  const lyricLines = useMemo(() => parseLyrics(lyricsDraft), [lyricsDraft]);
  const syncedLyrics = lyricLines.some((line) => line.timeMs !== null);
  const currentLyric = activeLineIndex(lyricLines, position * 1000 - lyricsOffset);
  const progress = duration > 0 ? Math.min(1, Math.max(0, position / duration)) : 0;
  const filteredTracks = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return tracks;
    return tracks.filter((track) =>
      `${track.title} ${track.artist} ${track.album}`.toLocaleLowerCase().includes(needle),
    );
  }, [query, tracks]);

  const ensureAudioGraph = useCallback((): AudioGraph | null => {
    if (audioGraphRef.current) return audioGraphRef.current;
    const audio = audioRef.current;
    if (!audio || typeof window.AudioContext === "undefined") return null;

    try {
      const context = new AudioContext();
      const source = context.createMediaElementSource(audio);
      const bass = context.createBiquadFilter();
      bass.type = "lowshelf";
      bass.frequency.value = 90;
      bass.gain.value = 0;

      const bands = EQ_FREQUENCIES.map((frequency, index) => {
        const filter = context.createBiquadFilter();
        filter.type = index === 0 ? "lowshelf" : index === EQ_FREQUENCIES.length - 1 ? "highshelf" : "peaking";
        filter.frequency.value = frequency;
        filter.Q.value = 1.1;
        filter.gain.value = 0;
        return filter;
      });
      const panner = context.createStereoPanner();
      const meter = context.createAnalyser();
      meter.fftSize = 128;
      meter.smoothingTimeConstant = 0.82;

      source.connect(bass);
      let previous: AudioNode = bass;
      bands.forEach((filter) => {
        previous.connect(filter);
        previous = filter;
      });
      previous.connect(panner);
      panner.connect(meter);
      meter.connect(context.destination);

      const graph = { context, bands, bass, panner, analyser: meter };
      audioGraphRef.current = graph;
      setAnalyser(meter);
      return graph;
    } catch (error) {
      setAudioError(error instanceof Error ? error.message : "Audio processing is unavailable.");
      return null;
    }
  }, []);

  useEffect(() => {
    const graph = audioGraphRef.current;
    if (!graph) return;
    graph.bands.forEach((band, index) => {
      band.gain.setTargetAtTime(eqEnabled ? (eqBands[index] ?? 0) : 0, graph.context.currentTime, 0.035);
    });
    graph.bass.gain.setTargetAtTime(effectsEnabled ? bassBoost : 0, graph.context.currentTime, 0.035);
    graph.panner.pan.setTargetAtTime(effectsEnabled ? balance : 0, graph.context.currentTime, 0.035);
  }, [balance, bassBoost, effectsEnabled, eqBands, eqEnabled]);

  useEffect(() => {
    localStorage.setItem("vex-eq-enabled", JSON.stringify(eqEnabled));
    localStorage.setItem("vex-eq-bands", JSON.stringify(eqBands));
    localStorage.setItem("vex-eq-preset", JSON.stringify(eqPreset));
    localStorage.setItem("vex-effects-enabled", JSON.stringify(effectsEnabled));
    localStorage.setItem("vex-bass", JSON.stringify(bassBoost));
    localStorage.setItem("vex-balance", JSON.stringify(balance));
    localStorage.setItem("vex-volume", JSON.stringify(volume));
  }, [balance, bassBoost, effectsEnabled, eqBands, eqEnabled, eqPreset, volume]);

  useEffect(() => {
    localStorage.setItem(
      "vex-favorites",
      JSON.stringify(tracks.filter((track) => track.favorite).map((track) => track.id)),
    );
  }, [tracks]);

  useEffect(() => {
    localStorage.setItem("vex-local-lyrics", JSON.stringify(lyricsByTrack));
  }, [lyricsByTrack]);

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio || !currentTrack) return;
    audio.pause();
    audio.src = currentTrack.src;
    audio.load();
    setPosition(0);
    setDuration(currentTrack.duration || 0);

    if (!autoplayRef.current) return;
    const start = () => {
      autoplayRef.current = false;
      void audio.play().catch((error: unknown) => {
        setAudioError(error instanceof Error ? error.message : "The selected audio could not be played.");
      });
    };
    audio.addEventListener("canplay", start, { once: true });
    if (audio.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) start();
    return () => audio.removeEventListener("canplay", start);
  }, [activeTrackId, currentTrack?.src]);

  useEffect(() => {
    if (audioRef.current) audioRef.current.loop = repeatMode === "one";
  }, [repeatMode]);

  useEffect(() => {
    const audio = audioRef.current;
    if (audio) audio.volume = volume;
  }, [volume]);

  useEffect(() => {
    setLyricsDraft(currentTrack ? lyricsByTrack[currentTrack.id] ?? "" : "");
    setLyricsOffset(0);
  }, [currentTrack?.id, lyricsByTrack]);

  useEffect(() => {
    return () => {
      audioRef.current?.pause();
      audioGraphRef.current?.context.close();
      tracksRef.current.forEach((track) => {
        if (track.localObjectUrl) URL.revokeObjectURL(track.localObjectUrl);
      });
    };
  }, []);

  const togglePlayback = async () => {
    const audio = audioRef.current;
    if (!audio || !currentTrack) return;
    setAudioError("");
    if (audio.paused) {
      const graph = ensureAudioGraph();
      if (graph?.context.state === "suspended") await graph.context.resume();
      try {
        await audio.play();
      } catch (error) {
        setAudioError(error instanceof Error ? error.message : "Playback could not start.");
      }
    } else {
      audio.pause();
    }
  };

  const playTrack = async (track: Track) => {
    const audio = audioRef.current;
    if (!audio) return;
    setAudioError("");
    const graph = ensureAudioGraph();
    if (graph?.context.state === "suspended") await graph.context.resume();
    if (track.id === activeTrackId) {
      await togglePlayback();
      return;
    }
    autoplayRef.current = true;
    setActiveTrackId(track.id);
    setSection("home");
  };

  const stepTrack = (direction: number) => {
    if (tracks.length === 0) return;
    if ((repeatMode === "one" || (repeatMode === "all" && tracks.length === 1)) && direction > 0) {
      const audio = audioRef.current;
      if (audio) {
        audio.currentTime = 0;
        void audio.play();
      }
      return;
    }
    const currentIndex = tracks.findIndex((track) => track.id === activeTrackId);
    let nextIndex = currentIndex + direction;
    if (shuffle && direction > 0 && tracks.length > 1) {
      do nextIndex = Math.floor(Math.random() * tracks.length);
      while (nextIndex === currentIndex);
    } else if (repeatMode === "all" || tracks.length > 1) {
      nextIndex = (nextIndex + tracks.length) % tracks.length;
    } else {
      const audio = audioRef.current;
      if (audio) audio.currentTime = 0;
      return;
    }
    void playTrack(tracks[nextIndex]);
  };

  const selectTrack = (track: Track) => {
    if (track.id === activeTrackId) {
      void togglePlayback();
      return;
    }
    void playTrack(track);
  };

  const importAudioFiles = (files: FileList | null) => {
    if (!files?.length) return;
    const imported = Array.from(files)
      .filter((file) => file.type.startsWith("audio/") || /\.(mp3|wav|flac|m4a|aac|ogg|opus)$/i.test(file.name))
      .map((file) => {
        const objectUrl = URL.createObjectURL(file);
        const title = file.name.replace(/\.[^.]+$/, "").replace(/[_-]+/g, " ");
        const id = `local-${crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`}`;
        const probe = new Audio(objectUrl);
        const track: Track = {
          id,
          title: title || "Untitled track",
          artist: "Local file",
          album: "Imported audio",
          duration: 0,
          src: objectUrl,
          localObjectUrl: objectUrl,
          artwork: COVER,
          favorite: false,
        };
        probe.addEventListener("loadedmetadata", () => {
          setTracks((existing) => existing.map((item) => item.id === id ? { ...item, duration: probe.duration || 0 } : item));
        }, { once: true });
        return track;
      });
    if (!imported.length) {
      setAudioError("No supported audio files were selected.");
      return;
    }
    setTracks((existing) => [...imported, ...existing]);
    setAudioError("");
    void playTrack(imported[0]);
  };

  const removeTrack = (track: Track) => {
    if (track.id === DEMO_TRACK.id) return;
    if (track.localObjectUrl) URL.revokeObjectURL(track.localObjectUrl);
    const next = tracks.filter((item) => item.id !== track.id);
    setTracks(next);
    if (track.id === activeTrackId && next.length) {
      autoplayRef.current = false;
      setActiveTrackId(next[0].id);
    }
  };

  const toggleFavorite = (trackId: string) => {
    setTracks((existing) => existing.map((track) =>
      track.id === trackId ? { ...track, favorite: !track.favorite } : track,
    ));
  };

  const setPreset = (name: string) => {
    setEqPreset(name);
    if (name !== "Custom") setEqBands([...(PRESETS[name] ?? FLAT)]);
    setEqEnabled(true);
  };

  const onLyricsFile = async (file: File | undefined) => {
    if (!file || !currentTrack) return;
    const contents = await file.text();
    setLyricsDraft(contents);
    setLyricsByTrack((existing) => ({ ...existing, [currentTrack.id]: contents }));
    if (!parseLyrics(contents).some((line) => line.timeMs !== null)) {
      setAudioError("Imported plain text. It will not be synchronized without LRC timestamps.");
    } else {
      setAudioError("");
    }
  };

  const saveLyrics = () => {
    if (!currentTrack) return;
    setLyricsByTrack((existing) => ({ ...existing, [currentTrack.id]: lyricsDraft }));
    setAudioError("Lyrics saved on this device.");
  };

  const navItems = [
    { id: "home" as const, label: "Home", icon: Home },
    { id: "library" as const, label: "Library", icon: Library },
    { id: "search" as const, label: "Search", icon: Search },
    { id: "lyrics" as const, label: "Lyrics", icon: FileText },
    { id: "equalizer" as const, label: "Equalizer", icon: SlidersHorizontal },
    { id: "settings" as const, label: "Settings", icon: Settings },
  ];

  const renderTrackRows = (items: Track[]) => (
    <div className="track-list">
      {items.map((track, index) => (
        <div className={`track-row ${track.id === activeTrackId ? "is-current" : ""}`} key={track.id}>
          <button className="track-main" onClick={() => selectTrack(track)} aria-label={`Play ${track.title}`}>
            <span className="track-index">{track.id === activeTrackId && isPlaying ? <AudioLines size={18} /> : String(index + 1).padStart(2, "0")}</span>
            <img src={track.artwork} alt="" className="track-thumb" />
            <span className="track-copy">
              <strong>{track.title}</strong>
              <small>{track.artist}<span className="slash"> // </span>{track.album}</small>
            </span>
            <span className="track-duration">{formatTime(track.duration)}</span>
          </button>
          <button
            className={`icon-action favorite-action ${track.favorite ? "is-favorite" : ""}`}
            onClick={() => toggleFavorite(track.id)}
            aria-label={track.favorite ? "Remove from favorites" : "Add to favorites"}
            title={track.favorite ? "Remove favorite" : "Favorite"}
          >
            <Heart size={18} fill={track.favorite ? "currentColor" : "none"} />
          </button>
          {track.localObjectUrl && (
            <button className="icon-action remove-action" onClick={() => removeTrack(track)} aria-label={`Remove ${track.title}`} title="Remove local file">
              <X size={17} />
            </button>
          )}
        </div>
      ))}
    </div>
  );

  const controls = (large = false) => (
    <div className={`transport ${large ? "transport-large" : ""}`}>
      <button className={`plain-control ${shuffle ? "control-active" : ""}`} onClick={() => setShuffle((value) => !value)} aria-label="Toggle shuffle" title="Shuffle">
        <Shuffle size={large ? 19 : 16} />
      </button>
      <button className="square-control" onClick={() => stepTrack(-1)} aria-label="Previous track" title="Previous">
        <SkipBack size={large ? 21 : 17} fill="currentColor" />
      </button>
      <button className={`play-control ${large ? "play-control-large" : ""}`} onClick={() => void togglePlayback()} aria-label={isPlaying ? "Pause" : "Play"} title={isPlaying ? "Pause" : "Play"}>
        {isPlaying ? <Pause size={large ? 25 : 18} fill="currentColor" /> : <Play size={large ? 25 : 18} fill="currentColor" />}
      </button>
      <button className="square-control" onClick={() => stepTrack(1)} aria-label="Next track" title="Next">
        <SkipForward size={large ? 21 : 17} fill="currentColor" />
      </button>
      <button
        className={`plain-control ${repeatMode !== "off" ? "control-active" : ""}`}
        onClick={() => setRepeatMode((mode) => mode === "off" ? "all" : mode === "all" ? "one" : "off")}
        aria-label={`Repeat ${repeatMode}`}
        title={`Repeat: ${repeatMode}`}
      >
        {repeatMode === "one" ? <Repeat1 size={large ? 19 : 16} /> : <Repeat size={large ? 19 : 16} />}
      </button>
    </div>
  );

  const renderTimeline = (compact = false) => (
    <div className={`timeline ${compact ? "timeline-compact" : ""}`}>
      <input
        type="range"
        min={0}
        max={duration || 1}
        step={0.1}
        value={Math.min(position, duration || 0)}
        onChange={(event) => {
          const next = Number(event.target.value);
          setPosition(next);
          if (audioRef.current) audioRef.current.currentTime = next;
        }}
        style={{ "--range-progress": `${progress * 100}%` } as CSSProperties}
        aria-label="Seek through track"
        className="range-input timeline-range"
        disabled={!currentTrack}
      />
      {!compact && <div className="timeline-labels"><span>{formatTime(position)}</span><span>{formatTime(duration)}</span></div>}
    </div>
  );

  const renderHome = () => (
    <div className="page home-page">
      <header className="page-heading home-heading">
        <div>
          <p className="eyebrow">VEX_OS // LOCAL AUDIO</p>
          <h1>VEX MUSIC</h1>
          <p className="page-subtitle">Your music. Your lyrics. Your control.</p>
        </div>
        <button className="outline-button import-button" onClick={() => audioInputRef.current?.click()}>
          <Plus size={17} /> Import music
        </button>
      </header>

      {currentTrack ? (
        <section className="now-stage" aria-label="Now playing">
          <div className="cover-frame">
            <img src={currentTrack.artwork} alt={`${currentTrack.album} artwork`} className="cover-image" />
            <span className="cover-corner">VEX / 01</span>
          </div>
          <div className="stage-details">
            <p className="eyebrow accent-text">NOW PLAYING // {currentTrack.localObjectUrl ? "LOCAL FILE" : "VEX DEMO"}</p>
            <h2>{currentTrack.title}</h2>
            <p className="artist-line">{currentTrack.artist}<span className="slash"> / </span>{currentTrack.album}</p>
            <div className="stage-rule" />
            <div className="stage-progress">{renderTimeline()}</div>
            {controls(true)}
            <div className="stage-footer">
              <button className={`text-action ${currentTrack.favorite ? "control-active" : ""}`} onClick={() => toggleFavorite(currentTrack.id)}>
                <Heart size={16} fill={currentTrack.favorite ? "currentColor" : "none"} />
                {currentTrack.favorite ? "SAVED" : "SAVE TRACK"}
              </button>
              <button className="text-action" onClick={() => setSection("lyrics")}><FileText size={16} /> LYRICS</button>
              <button className="text-action" onClick={() => setSection("equalizer")}><SlidersHorizontal size={16} /> DSP</button>
            </div>
            {audioError && <p className="inline-message" role="status">{audioError}</p>}
          </div>
        </section>
      ) : (
        <section className="empty-stage">
          <div className="empty-art"><Disc3 size={42} /></div>
          <p className="eyebrow">AUDIO INPUT REQUIRED</p>
          <h2>Bring your own signal.</h2>
          <p>Choose audio files from this device. They stay in this browser session and are never uploaded.</p>
          <button className="primary-button" onClick={() => audioInputRef.current?.click()}><Upload size={17} /> Import local audio</button>
        </section>
      )}

      <section className="section-block recent-block">
        <div className="section-heading">
          <div><p className="eyebrow">ON THIS DEVICE</p><h3>Recently added</h3></div>
          <button className="text-action" onClick={() => setSection("library")}>OPEN LIBRARY <ChevronRight size={16} /></button>
        </div>
        {renderTrackRows(tracks.slice(0, 4))}
      </section>
    </div>
  );

  const renderLibrary = () => (
    <div className="page library-page">
      <header className="page-heading">
        <div><p className="eyebrow">LOCAL COLLECTION // {tracks.length} TRACKS</p><h1>Library</h1></div>
        <button className="outline-button" onClick={() => audioInputRef.current?.click()}><Plus size={17} /> Import files</button>
      </header>
      <div className="library-tools">
        <label className="search-field">
          <Search size={18} />
          <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search title, artist, album" />
          {query && <button className="icon-action" onClick={() => setQuery("")} aria-label="Clear search"><X size={16} /></button>}
        </label>
        <span className="library-count">{filteredTracks.length} RESULTS</span>
      </div>
      {filteredTracks.length ? renderTrackRows(filteredTracks) : (
        <div className="empty-list"><Music2 size={28} /><p>No tracks match this search.</p><button className="text-action" onClick={() => setQuery("")}>CLEAR SEARCH</button></div>
      )}
      <div className="library-note"><span className="note-mark" /> Local audio only. Files are not copied or uploaded.</div>
    </div>
  );

  const renderSearch = () => (
    <div className="page search-page">
      <header className="page-heading"><div><p className="eyebrow">INDEX // ON DEVICE</p><h1>Search</h1></div></header>
      <label className="search-field search-field-large">
        <Search size={20} />
        <input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Track, artist or album" />
        {query && <button className="icon-action" onClick={() => setQuery("")} aria-label="Clear search"><X size={16} /></button>}
      </label>
      <p className="eyebrow search-caption">{query ? `${filteredTracks.length} MATCHES` : "SEARCH YOUR LOCAL LIBRARY"}</p>
      {query ? renderTrackRows(filteredTracks) : <div className="empty-list"><Search size={28} /><p>Type to search imported audio.</p></div>}
    </div>
  );

  const renderLyrics = () => (
    <div className="page lyrics-page">
      <header className="page-heading">
        <div><p className="eyebrow">WORDS // LOCAL FILE</p><h1>Lyrics</h1><p className="page-subtitle">{currentTrack ? `${currentTrack.title} / ${currentTrack.artist}` : "Select a track first"}</p></div>
        <button className="outline-button" onClick={() => lyricsInputRef.current?.click()} disabled={!currentTrack}><Upload size={17} /> Import LRC/TXT</button>
      </header>
      <div className="lyrics-toolbar">
        <span className={`sync-state ${syncedLyrics ? "is-synced" : ""}`}><span />{syncedLyrics ? "SYNCHRONIZED LRC" : "UNSYNCHRONIZED TEXT"}</span>
        {syncedLyrics && <label className="offset-control">OFFSET <input type="number" step="0.25" value={lyricsOffset / 1000} onChange={(event) => setLyricsOffset(Number(event.target.value) * 1000)} /> SEC</label>}
      </div>
      {lyricLines.length ? (
        <div className="lyrics-reading">
          {lyricLines.map((line, index) => (
            <button
              key={`${line.timeMs ?? "plain"}-${index}`}
              className={`lyric-line ${index === currentLyric ? "is-active" : ""} ${!syncedLyrics ? "is-plain" : ""}`}
              onClick={() => {
                if (line.timeMs !== null && audioRef.current) {
                  const next = Math.max(0, (line.timeMs + lyricsOffset) / 1000);
                  audioRef.current.currentTime = next;
                  setPosition(next);
                }
              }}
              disabled={line.timeMs === null}
            >
              {line.text}
            </button>
          ))}
        </div>
      ) : (
        <div className="lyrics-empty"><FileText size={32} /><h2>No lyrics loaded</h2><p>Import an LRC or TXT file, or write local lyrics below. Plain text will stay unsynchronized.</p></div>
      )}
      <section className="lyrics-editor">
        <div className="section-heading"><div><p className="eyebrow">EDIT / IMPORT</p><h3>Local lyrics source</h3></div><button className="text-action" onClick={saveLyrics} disabled={!currentTrack}><Check size={15} /> SAVE</button></div>
        <textarea value={lyricsDraft} onChange={(event) => setLyricsDraft(event.target.value)} placeholder="Paste plain text or timestamped LRC, for example [00:12.50] First line" aria-label="Lyrics editor" />
      </section>
    </div>
  );

  const renderEqualizer = () => (
    <div className="page equalizer-page">
      <header className="page-heading">
        <div><p className="eyebrow">VEX_DSP // LIVE OUTPUT</p><h1>Equalizer</h1><p className="page-subtitle">10-band browser DSP. Changes apply during playback.</p></div>
        <button className={`switch ${eqEnabled ? "is-on" : ""}`} role="switch" aria-checked={eqEnabled} onClick={() => setEqEnabled((value) => !value)}><span />{eqEnabled ? "ON" : "OFF"}</button>
      </header>
      <div className="preset-row">
        <span className="eyebrow">PRESETS</span>
        {Object.keys(PRESETS).map((name) => (
          <button key={name} className={`preset-button ${eqPreset === name ? "selected" : ""}`} onClick={() => setPreset(name)}>{name}</button>
        ))}
        <button className={`preset-button ${eqPreset === "Custom" ? "selected" : ""}`} onClick={() => setEqPreset("Custom")}>Custom</button>
      </div>
      <section className={`eq-surface ${eqEnabled ? "" : "is-disabled"}`}>
        <div className="eq-surface-heading"><div><AudioLines size={17} /><span>10-BAND RESPONSE</span></div><span className="eq-current">{eqPreset.toUpperCase()} PRESET</span></div>
        <div className="eq-board">
          <div className="db-guides"><span>+12 dB</span><span>+6 dB</span><span>0 dB</span><span>-6 dB</span><span>-12 dB</span></div>
          <div className="band-grid">
            {EQ_FREQUENCIES.map((frequency, index) => (
              <label className="band-column" key={frequency}>
                <output className="band-value">{eqBands[index] > 0 ? "+" : ""}{Number(eqBands[index] ?? 0).toFixed(1)} dB</output>
                <div className="band-track">
                  <input
                    type="range"
                    min={-12}
                    max={12}
                    step={0.5}
                    value={eqBands[index] ?? 0}
                    disabled={!eqEnabled}
                    aria-label={`${EQ_LABELS[index]} gain`}
                    className="vertical-range"
                    onChange={(event) => {
                      const value = Number(event.target.value);
                      setEqBands((values) => values.map((item, band) => band === index ? value : item));
                      setEqPreset("Custom");
                    }}
                  />
                </div>
                <span className="band-label">{EQ_LABELS[index]}</span>
              </label>
            ))}
          </div>
        </div>
        <div className="eq-footnote"><span>REAL-TIME FILTERS</span><span>GAIN RANGE -12 TO +12 dB</span></div>
      </section>

      <section className="effects-section">
        <div className="section-heading"><div><p className="eyebrow">ANALOG SOUND ENHANCERS</p><h3>Output effects</h3></div><button className={`switch small-switch ${effectsEnabled ? "is-on" : ""}`} role="switch" aria-checked={effectsEnabled} onClick={() => setEffectsEnabled((value) => !value)}><span />{effectsEnabled ? "ON" : "OFF"}</button></div>
        <div className="effect-controls">
          <label className="effect-control"><span>BASS BOOST <strong>{effectsEnabled ? `+${bassBoost} dB` : "BYPASS"}</strong></span><input type="range" min={0} max={12} step={0.5} value={bassBoost} disabled={!effectsEnabled} onChange={(event) => setBassBoost(Number(event.target.value))} className="range-input" /></label>
          <label className="effect-control"><span>STEREO BALANCE <strong>{balance === 0 ? "CENTER" : balance < 0 ? `${Math.abs(balance * 100)}% LEFT` : `${balance * 100}% RIGHT`}</strong></span><input type="range" min={-1} max={1} step={0.05} value={balance} disabled={!effectsEnabled} onChange={(event) => setBalance(Number(event.target.value))} className="range-input" /></label>
        </div>
      </section>
      <button className="reset-button" onClick={() => { setEqBands([...FLAT]); setEqPreset("Flat"); setBassBoost(0); setBalance(0); setEqEnabled(true); }}><RotateCcw size={17} /> RESET TO FLAT</button>
      {audioError && <p className="inline-message" role="status">{audioError}</p>}
    </div>
  );

  const renderSettings = () => (
    <div className="page settings-page">
      <header className="page-heading"><div><p className="eyebrow">SYSTEM CONFIGURATION</p><h1>Settings</h1></div></header>
      <section className="settings-section">
        <div className="section-heading"><div><p className="eyebrow">PLAYBACK</p><h3>Output level</h3></div><strong className="settings-value">{Math.round(volume * 100)}%</strong></div>
        <div className="volume-setting"><VolumeX size={17} /><input type="range" min={0} max={1} step={0.01} value={volume} onChange={(event) => setVolume(Number(event.target.value))} className="range-input" aria-label="Output volume" /><Volume2 size={18} /></div>
      </section>
      <section className="settings-section">
        <p className="eyebrow">LOCAL STORAGE</p><h3>Private by design</h3>
        <p className="settings-copy">Audio files play from this browser session. The VEX demo loop is bundled locally; imported audio is referenced with temporary browser URLs. No audio or lyrics are uploaded.</p>
        <div className="settings-actions"><button className="outline-button" onClick={() => audioInputRef.current?.click()}><Plus size={16} /> Import audio</button><button className="outline-button" onClick={() => setSection("library")}><Library size={16} /> Manage library</button></div>
      </section>
      <section className="settings-section">
        <p className="eyebrow">AUDIO PROCESSING</p><h3>Live DSP chain</h3>
        <p className="settings-copy">The equalizer, bass shelf, stereo balance and visualizer run through the Web Audio output graph for the selected audio element. On-device Android playback and background controls belong to the native VEX MUSIC app.</p>
        <button className="text-action" onClick={() => setSection("equalizer")}><SlidersHorizontal size={16} /> OPEN EQUALIZER</button>
      </section>
    </div>
  );

  const currentNav = navItems.find((item) => item.id === section);

  return (
    <div className="app-shell">
      <aside className="desktop-rail">
        <button className="brand-lockup" onClick={() => setSection("home")} aria-label="VEX MUSIC home">
          <span className="brand-glyph"><AudioLines size={19} /></span>
          <span><strong>VEX MUSIC</strong><small>LOCAL PLAYER / 01</small></span>
        </button>
        <p className="rail-label">WORKSPACE</p>
        <nav className="side-nav" aria-label="Main navigation">
          {navItems.map(({ id, label, icon: Icon }) => (
            <button key={id} className={`nav-item ${section === id ? "active" : ""}`} onClick={() => setSection(id)}>
              <Icon size={18} strokeWidth={1.9} /><span>{label}</span>{section === id && <span className="active-bar" />}
            </button>
          ))}
        </nav>
        <div className="rail-bottom">
          <span className="live-dot" /> LOCAL AUDIO READY
          <small>NO ACCOUNT / NO UPLOADS</small>
        </div>
      </aside>

      <div className="workspace">
        <header className="topbar">
          <div className="topbar-mobile-brand"><span className="brand-glyph"><AudioLines size={17} /></span><strong>VEX MUSIC</strong></div>
          <div className="topbar-context"><span className="eyebrow">VEX_OS</span><span className="topbar-separator">//</span><span>{currentNav?.label.toUpperCase() ?? "PLAYER"}</span></div>
          <div className="topbar-actions">
            <button className="top-search" onClick={() => setSection("search")} aria-label="Open search"><Search size={17} /><span>SEARCH LIBRARY</span><kbd>/</kbd></button>
            <span className="system-status"><span className="live-dot" /> SYSTEM OK</span>
          </div>
        </header>

        <main className="main-scroll">
          {section === "home" && renderHome()}
          {section === "library" && renderLibrary()}
          {section === "search" && renderSearch()}
          {section === "lyrics" && renderLyrics()}
          {section === "equalizer" && renderEqualizer()}
          {section === "settings" && renderSettings()}
        </main>
      </div>

      {currentTrack && (
        <div className="player-dock">
          <div className="dock-track" onClick={() => setSection("home")} role="button" tabIndex={0} onKeyDown={(event) => { if (event.key === "Enter") setSection("home"); }}>
            <img src={currentTrack.artwork} alt="" />
            <span><strong>{currentTrack.title}</strong><small>{currentTrack.artist}</small></span>
            <button className={`icon-action dock-heart ${currentTrack.favorite ? "is-favorite" : ""}`} onClick={(event) => { event.stopPropagation(); toggleFavorite(currentTrack.id); }} aria-label="Toggle favorite"><Heart size={17} fill={currentTrack.favorite ? "currentColor" : "none"} /></button>
          </div>
          <div className="dock-center">{controls(false)}{renderTimeline(true)}</div>
          <div className="dock-volume"><Volume2 size={17} /><input type="range" min={0} max={1} step={0.01} value={volume} onChange={(event) => setVolume(Number(event.target.value))} className="range-input" aria-label="Output volume" /></div>
        </div>
      )}

      <nav className="mobile-nav" aria-label="Main navigation">
        {navItems.filter((item) => ["home", "library", "search", "lyrics", "settings"].includes(item.id)).map(({ id, label, icon: Icon }) => (
          <button key={id} className={`mobile-nav-item ${section === id ? "active" : ""}`} onClick={() => setSection(id)}>
            <Icon size={19} /><span>{label}</span>
          </button>
        ))}
      </nav>

      <audio
        ref={audioRef}
        preload="metadata"
        onTimeUpdate={(event) => setPosition(event.currentTarget.currentTime || 0)}
        onLoadedMetadata={(event) => setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0)}
        onDurationChange={(event) => setDuration(Number.isFinite(event.currentTarget.duration) ? event.currentTarget.duration : 0)}
        onPlay={() => setIsPlaying(true)}
        onPause={() => setIsPlaying(false)}
        onEnded={() => stepTrack(1)}
        onError={() => setAudioError("This audio source could not be decoded by the browser.")}
      />
      <input ref={audioInputRef} type="file" accept="audio/*,.flac,.opus" multiple className="visually-hidden" onChange={(event) => { importAudioFiles(event.target.files); event.currentTarget.value = ""; }} />
      <input ref={lyricsInputRef} type="file" accept=".lrc,.txt,text/plain" className="visually-hidden" onChange={(event) => { void onLyricsFile(event.target.files?.[0]); event.currentTarget.value = ""; }} />
      <Visualizer analyser={analyser} active={isPlaying} />
    </div>
  );
}

export default App;
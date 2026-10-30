# Renami Architecture

Renami's module map is modeled on the *observed subsystem boundaries* of commercial media
renamers (see the reverse-engineering report), but every line of code is original.

```
app.renami
├── Main                 picocli entry point (CLI-first; GUI arrives in 0.4)
├── cli                  subcommands: rename, undo, probe
├── media                file scanning + technical probing
│   ├── MediaScanner     recursive walk, extension-based media detection
│   ├── MediaProbe       interface
│   ├── MediaInfoCliProbe   shells out to `mediainfo --Output=JSON`
│   └── FFProbeCliProbe     fallback via `ffprobe -of json`
├── parse                release/filename parsing
│   ├── FileNameParser   delimiter normalization, SxxEyy / 1x02 / 102, year, quality tokens
│   └── ReleaseInfo      parsed facts (name guess, season, episode, year)
├── web                  metadata datasources
│   ├── Datasource       lookup interface (search TV/movie, fetch season)
│   ├── tmdb.TmdbClient  TMDb v3 API via java.net.http + Gson
│   └── model            ShowInfo / EpisodeInfo / MovieInfo records
├── similarity           matching engine
│   ├── NameScorer       normalized Levenshtein + token Jaccard → 0..1
│   └── EpisodeMatcher   show scoring + episode assignment
├── format               FormatEngine: {groovy expressions} against a Binding
├── rename               RenameOperation / ConflictPolicy / RenameExecutor
│   └── HistoryStore     JSONL journal in ~/.renami + batch undo
└── util                 Config (env + ~/.renami/config.properties), Json helpers
```

## Data flow

```
scan → parse (ReleaseInfo) → group (movie | per-show episodes)
     → Datasource.search → NameScorer picks best candidate (threshold-gated)
     → EpisodeMatcher assigns episodes (number match + title similarity)
     → FormatEngine renders target names
     → RenameExecutor (dry-run default, conflict policy)
     → HistoryStore.journal  ──►  renami undo
```

## Design decisions

- **CLI first, Swing later** — the engine is UI-independent; the 0.4 GUI wraps the same pipeline.
- **User-supplied API keys** — commercial vendors bundle provider keys; Renami requires the user's
  own TMDb/TVDB/OpenSubtitles credentials (env var or config file). This is the single biggest
  behavioral difference from the commercial original.
- **Groovy as expression language** — `{n} - {s00e00} - {t}` evaluated via `GroovyShell` with a
  binding, mirroring the ergonomics of established rename formats without copying their code.
- **Undo via journal, not transactions** — append-only JSONL batches; `undo` replays in reverse.
- **No license gate** — everything is free and open.

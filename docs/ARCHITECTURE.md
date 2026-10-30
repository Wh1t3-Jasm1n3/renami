# Renami Architecture

Renami's module map is modeled on the *observed subsystem boundaries* of commercial media
renamers (see the reverse-engineering report), but every line of code is original.

```
app.renami
├── Main                 picocli entry point; bare launch / Finder open-with opens the GUI
├── cli                  rename, undo, probe, checksum, dupe, extract, list, filter,
│                        post-process, subtitles, track, gui
├── pipeline             RenameService: the UI-independent scan→match→format→plan pipeline
├── media                file scanning + technical probing
│   ├── MediaScanner     recursive walk, extension-based media detection
│   ├── CompanionFiles   same-base sidecars (subs/nfo/artwork) follow the media file
│   ├── MediaProbe       interface
│   ├── MediaInfoCliProbe   shells out to `mediainfo --Output=JSON`
│   ├── FFProbeCliProbe     fallback via `ffprobe -of json`
│   └── MediaInfoVars  MediaInfo/ffprobe facts exposed to format templates
├── parse                release/filename parsing
│   ├── FileNameParser   delimiter normalization, SxxEyy / 1x02 / 102, anime absolute, year, quality tokens
│   ├── EpisodeNumber    season/episode pair with SxxEyy rendering
│   └── ReleaseInfo      parsed facts (name guess, season, episode, absolute, year)
├── web                  metadata datasources behind one interface
│   ├── Datasource       lookup interface (search TV/movie, fetch season, map absolute episode)
│   ├── Datasources      --db selection + API-key checks
│   ├── HttpJson         java.net.http + Gson, 24 h on-disk response cache
│   ├── tmdb.TmdbClient  TMDb v3        · tvdb.TheTVDBClient  TheTVDB v4 (JWT, episode orders)
│   ├── omdb.OmdbClient  OMDb          · kitsu.KitsuClient    Kitsu JSON:API (anime, key-free)
│   ├── xem.XemClient    TheXEM anime episode mappings       · acoustid.AcoustIDClient
│   ├── opensubtitles.OpenSubtitlesClient (v2 REST + moviehash search)
│   └── local.LocalDatasource  offline fallback
├── similarity           matching engine
│   ├── NameScorer       normalized Levenshtein + token Jaccard → 0..1
│   └── EpisodeMatcher   show scoring + episode assignment
├── format               FormatEngine: {groovy expressions} against a Binding
├── rename               RenameOperation / ConflictPolicy / TransferAction / RenameExecutor
│   └── HistoryStore     JSONL journal in ~/.renami + batch undo (ops carry the transfer verb)
├── hash                 HashType (MD5/SHA/ED2K/CRC32), SFV-style sidecars, OpenSubtitles moviehash
├── archive              ArchiveTool: list/extract via 7z/tar/unzip
├── audio                Fingerprinter: fpcalc wrapper for AcouSTID lookups
├── script               ScriptRuntime: Groovy --script hooks + --def coercion
├── gui                  Swing panels (Rename / Subtitles / Duplicates), FileDrop, AppIcon
└── util                 Config (env + ~/.renami/config.properties), NativeTools, Version, Json helpers
```

Build-time assets: `tools/GenerateIcon.java` renders the macOS iconset with Java2D
(`gradle appIcon` → `iconutil`), and the same PNG is packaged as a classpath resource
so the Swing window and Dock icon work in plain `installDist` builds. No binary assets
are checked in.

## Data flow

```
scan → parse (ReleaseInfo) → group (movie | per-show episodes)
     → Datasource.search → NameScorer picks best candidate (threshold-gated)
     → EpisodeMatcher assigns episodes (number match + title similarity)
     → FormatEngine renders target names
     → RenameExecutor (dry-run default, conflict policy, TransferAction mode)
     → HistoryStore.journal  ──►  renami undo (verb-aware: restores moves, deletes copies/links)
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
- **Kitsu instead of AniDB** — the commercial original queries AniDB, but AniDB's legacy HTTP API
  has been retired (`anime_query.php` answers 404, `api.anidb.net` is unreachable) and its
  replacement requires a registered, cryptographically signed client. Kitsu needs no credentials,
  carries AniDB-derived anime data, and exposes `titles` (romaji/english/native),
  `abbreviatedTitles`, `seasonNumber`, `number` and `numberAbsolute` per episode — everything the
  anime pipeline needs. Because anime are published under several names, every known title of a
  search hit becomes its own match candidate, which is what lets a romaji filename match an
  English-language record.
- **Transfer mode is part of the journal** — `--action rename|move|copy|hardlink|symlink`
  (FileBot-style) is recorded as a verb per op, so `undo` knows whether to move a file back
  (rename/move) or delete the created entry (copy/hardlink/symlink). Undo refuses to delete a
  copy when the source has since disappeared. Hardlinks degrade to a copy across filesystems
  instead of failing the batch; a case-only target on a case-insensitive filesystem is refused
  for non-consuming modes because it would overwrite the source with itself.

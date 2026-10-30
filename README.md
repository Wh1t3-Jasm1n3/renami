# Renami

**Renami** is an open-source media file renamer and organizer — a clean-room reimplementation of the
*concept* of tools like FileBot: match movie/TV files against online metadata databases, score the
candidates with a fuzzy-matching engine, and rename/move them using expression templates.

> **Legal disclaimer.** Renami shares **no code** with FileBot. It is written from scratch against
> publicly documented behavior and open-source third-party components. "FileBot" is a trademark of
> its respective owner; Renami is an independent project.

## Features (current MVP)

- 🎬 **Media scanning** — recursive folder scan, video/audio detection
- 🔍 **Filename parsing** — `S01E02`, `1x02`, `102` patterns, year, quality-token stripping
- 📡 **Metadata lookup** — TMDb (TV + Movies) via user-supplied API key
- 🧠 **Fuzzy matching** — normalized Levenshtein + token-overlap scoring
- 📝 **Format engine** — Groovy-backed `{expressions}` (e.g. `{n} - {s00e00} - {t}`)
- 🔁 **Safe renaming** — dry-run by default, conflict policies, JSONL history + `undo`
- 🛠 **Probe command** — MediaInfo/ffprobe technical metadata dump

## Quick start

```bash
# requires JDK 21+
export RENAMI_TMDB_API_KEY="your-tmdb-v4-read-token"   # or ~/.renami/config.properties

gradle installDist
./build/install/renami/bin/renami rename /path/to/tv --dry-run
./build/install/renami/bin/renami rename /path/to/tv --apply
./build/install/renami/bin/renami undo
```

## Roadmap

| Phase | Scope |
|---|---|
| **0.1 (now)** | CLI core: scan → parse → TMDb lookup → match → format → rename/undo |
| 0.2 | TheTVDB/OMDb datasources, anime (XEM mappings), subtitle search (OpenSubtitles) |
| 0.3 | Archive handling (sevenzipjbinding/7z), checksum verification (SFV/MD5/SHA/ED2K) |
| 0.4 | Swing GUI: rename panel with live match table, presets, dark theme |
| 0.5 | Groovy scripting layer (script shell + postprocess pipeline), macOS integration (xattr, Finder tags, APFS clones) |
| 1.0 | jpackage `.app` bundling, auto-update, parity with MVP feature set of commercial tools |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map.

## License

GPL-3.0-or-later. Third-party components (Groovy, picocli, Gson, MediaInfo, 7-Zip, Chromaprint)
remain under their own licenses.

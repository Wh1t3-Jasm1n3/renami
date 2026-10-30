# Renami

**Renami** is an open-source media file renamer and organizer — a clean-room reimplementation of the
*concept* of tools like FileBot: match movie/TV files against online metadata databases, score the
candidates with a fuzzy-matching engine, and rename/move them using expression templates.

> **Legal disclaimer.** Renami shares **no code** with FileBot. It is written from scratch against
> publicly documented behavior and open-source third-party components. "FileBot" is a trademark of
> its respective owner; Renami is an independent project.

## Features

- 🎬 **Media scanning** — recursive folder scan, video/audio detection
- 🔍 **Filename parsing** — `S01E02`, `1x02`, `102`, anime absolute `Show - 05` patterns,
  year, quality-token stripping
- 📡 **Metadata lookup** — TMDb, TheTVDB v4 (incl. aired/dvd/absolute episode orders),
  OMDb, Kitsu (anime, **no API key needed**), plus an offline `local` datasource;
  24 h HTTP response cache
- 🧠 **Fuzzy matching** — normalized Levenshtein + token-overlap scoring
- 📝 **Format engine** — Groovy-backed `{expressions}` (e.g. `{n} - {s00e00} - {t}`),
  `/` in templates creates folder hierarchies
- 🔁 **Safe renaming** — dry-run by default, conflict policies, JSONL history + `undo`;
  `--action rename|move|copy|hardlink|symlink` transfer modes (undo deletes created
  copies/links instead of moving files back)
- 🖥 **Desktop UI** — `renami gui` (Swing rename panel); bare `renami` opens it too
- 📦 **Archives** — list/extract zip/tar/7z via native tools; `post-process` renames
  contents directly and can `--clean` processed archives
- ✅ **Checksums** — write/verify SFV, `md5sum`-style, SHA-1/SHA-256, CRC32 sidecars
- 🧮 **Duplicates** — 3-phase dupe finder (size → partial hash → full SHA-256)
- 💬 **Subtitles** — OpenSubtitles.com v2 search + download (`--lang`)
- 🎵 **Music** — audio fingerprinting with fpcalc + AcouSTID/MusicBrainz lookup (`track`)
- 📜 **Scripting** — Groovy `--script` hooks with `fn`/`info`/`source`/`target` bindings and `--def` vars
- 🛠 **Probe** — MediaInfo/ffprobe technical metadata dump

## Quick start

```bash
# requires JDK 21+
export RENAMI_TMDB_API_KEY="your-tmdb-v4-read-token"   # or ~/.renami/config.properties

gradle installDist
BIN=./build/install/renami/bin/renami

$BIN rename /path/to/tv            # dry-run preview
$BIN rename /path/to/tv --apply
$BIN rename ~/AniMe --db kitsu --order absolute   # anime: key-free, romaji or english titles
$BIN post-process ~/Downloads --apply --clean -o ~/Media   # archives + structured output
$BIN post-process ~/Downloads --apply --action hardlink -o ~/Media  # build a library without duplicating data
$BIN subtitles /path/to/tv --lang en --apply
$BIN track ~/Music --apply         # needs `brew install chromaprint`
$BIN dupe /path/to/media
$BIN checksum /path/to/media --write checksums.sfv
$BIN undo
$BIN gui                           # desktop rename panel
```

No API key? Anime works out of the box with `--db kitsu` (Kitsu needs no credentials), and
everything still works offline with `--db local` (rename purely from filename facts) —
and `--help` on any command documents its options.

Configuration is read from `~/.renami/config.properties` or `RENAMI_*` environment
variables: `RENAMI_TMDB_API_KEY`, `RENAMI_TVDB_API_KEY`, `RENAMI_OMDB_API_KEY`,
`RENAMI_OPENSUBTITLES_API_KEY`, `RENAMI_ACOUSTID_API_KEY`.

## Building

```bash
gradle build          # compile + tests
gradle installDist    # CLI at build/install/renami/bin/renami
gradle jpackageApp    # macOS Renami.app (unsigned app-image) in build/jpackage
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0.1 | CLI core: scan → parse → TMDb lookup → match → format → rename/undo | ✅ |
| 0.2 | TheTVDB/OMDb datasources, `--db` selection, HTTP cache | ✅ |
| 0.3 | Archives, checksums, dupe finder | ✅ |
| 0.4 | Swing GUI rename panel | ✅ |
| 0.5 | Groovy scripting, post-process pipeline | ✅ |
| 0.6 | Subtitles (OpenSubtitles v2) | ✅ |
| 0.7 | Music fingerprinting (fpcalc + AcouSTID) | ✅ |
| 0.8 | Anime absolute episode ordering | ✅ |
| 0.9 | ED2K hashes, `filter` command, `list --format`, GUI Subtitles/Duplicates tabs, drag & drop | ✅ |
| 1.0 | jpackage `.app` + generated icon, anime datasource (Kitsu) and TheXEM mappings, subtitle hash matching | ✅ |
| 1.1 | `--action rename/move/copy/hardlink/symlink` transfer modes with verb-aware undo | ✅ |
| 1.2 | companion-file import, folder watch | 🔜 |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map.

## License

GPL-3.0-or-later. Third-party components (Groovy, picocli, Gson, MediaInfo, 7-Zip, Chromaprint)
remain under their own licenses.

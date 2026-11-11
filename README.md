# Renami

**Renami** is an open-source media file renamer and organizer for macOS — a clean-room
reimplementation of the *concept* of tools like FileBot. Point it at a folder of movies or TV
episodes (archives included), and it matches each file against online metadata databases, scores
the candidates with a fuzzy-matching engine, and renames/moves them into a clean library using
expression templates. Undo anything, anytime.

> **Legal disclaimer.** Renami shares **no code** with FileBot. It is written from scratch against
> publicly documented behavior and open-source third-party components. "FileBot" is a trademark of
> its respective owner; Renami is an independent project.

## Download

Grab the latest build from the [releases page](https://github.com/Wh1t3-Jasm1n3/renami/releases/latest):

- **Renami-1.4.0-macos.zip** — native macOS app with bundled Java runtime. Unsigned, so first
  launch: right-click → Open.
- **renami-1.4.0-cli.zip** — command line (requires JDK 21+), or build from source below.

## The desktop app

`Renami.app` is a native-feeling macOS app: SF Pro typography, the system accent colour, light/dark
mode that follows the desktop, drag & drop everywhere, and one sidebar with six views:

| View | What it does |
|---|---|
| **Rename** | Drop files or a folder → **Match** against TMDb/TVDB/OMDb/Kitsu → preview → **Rename**. Mode (auto/movie/episode/local), transfer action, format template, companions. |
| **Post-Process** | The one-shot FileBot flow: pick a folder, archives are extracted automatically, everything is matched and moved into a structured output tree (`{n}/Season {s}/…`). Optionally delete processed archives. |
| **Watch** | Pick a folder and press Start — new files are processed the moment their download settles. Live activity log. |
| **Subtitles** | Search OpenSubtitles for every media file in a folder and download matches next to them. |
| **Duplicates** | 3-phase dupe finder (size → partial hash → full SHA-256) with groups side by side. |
| **Checksum** | Generate SHA-256/SHA-1/MD5/eD2K/CRC32 hashes, save SFV/MD5-style sidecars, verify with OK/MISMATCH/MISSING at a glance. |
| **History** | Every batch is journalled — browse it and **Undo** any batch, even copies and links (those get deleted). |

Every view follows the same rhythm: options in a card, a flat preview table with color-coded
status, one prominent action. Return always triggers the view's primary action.

## Command line

The GUI and the CLI run the **same pipeline** — pick whichever you like.

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
$BIN watch ~/Downloads --apply        # auto-process finished downloads as they land
$BIN subtitles /path/to/tv --lang en --apply
$BIN track ~/Music --apply         # needs `brew install chromaprint`
$BIN dupe /path/to/media
$BIN checksum /path/to/media --write checksums.sfv
$BIN undo
$BIN gui                           # open the desktop app from the terminal
```

No API key? Anime works out of the box with `--db kitsu` (Kitsu needs no credentials), and
everything still works offline with `--db local` (rename purely from filename facts) —
and `--help` on any command documents its options.

Configuration is read from `~/.renami/config.properties` or `RENAMI_*` environment
variables: `RENAMI_TMDB_API_KEY`, `RENAMI_TVDB_API_KEY`, `RENAMI_OMDB_API_KEY`,
`RENAMI_OPENSUBTITLES_API_KEY`, `RENAMI_ACOUSTID_API_KEY`.

## Engine features

- 🎬 **Media scanning** — recursive folder scan, video/audio detection
- 🔍 **Filename parsing** — `S01E02`, `1x02`, `102`, anime absolute `Show - 05` patterns,
  year, quality-token stripping
- 📡 **Metadata lookup** — TMDb, TheTVDB v4 (incl. aired/dvd/absolute episode orders),
  OMDb, Kitsu (anime, **no API key needed**), plus an offline `local` datasource;
  24 h HTTP response cache
- 🧠 **Fuzzy matching** — normalized Levenshtein + token-overlap scoring
- 📝 **Format engine** — Groovy-backed `{expressions}` (e.g. `{n} - {s00e00} - {t}`),
  `/` in templates creates folder hierarchies
- 🔁 **Safe renaming** — dry-run by default in the CLI, conflict policies, JSONL history + `undo`;
  `rename|move|copy|hardlink|symlink` transfer modes (undo deletes created copies/links
  instead of moving files back)
- 🧲 **Companion files** — sidecars sharing the media base name (`Movie.en.srt`, `Movie.nfo`,
  `Movie.jpg`) follow the media file through rename/move/copy/link
- 📦 **Archives** — zip/tar/7z extracted transparently by Post-Process; archives can be
  deleted after their contents are processed
- 🎵 **Music** — audio fingerprinting with fpcalc + AcouSTID/MusicBrainz lookup (`track`)
- 📜 **Scripting** — Groovy `--script` hooks with `fn`/`info`/`source`/`target` bindings and `--def` vars
- 🛠 **Probe** — MediaInfo/ffprobe technical metadata dump

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
| 1.2 | companion-file import, folder watch (`renami watch`) | ✅ |
| 1.3 | GUI history view, dark theme, subtitle search parity | ✅ |
| 1.4 | 2026-macOS redesign (sidebar chrome, SF Pro, accent, dark mode) + GUI-first Post-Process, Watch and Checksum views | ✅ |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map.

## License

GPL-3.0-or-later. Third-party components (Groovy, picocli, Gson, FlatLaf, MediaInfo, 7-Zip,
Chromaprint) remain under their own licenses.

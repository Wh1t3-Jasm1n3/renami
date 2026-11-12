<img src="docs/app-icon.png" alt="Renami app icon" width="128" align="left" hspace="16" />

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

- **Renami-1.6.2-macos.zip** — native macOS app with bundled Java runtime. Developer ID signed;
  if Gatekeeper still complains (build not notarized), right-click → Open.
- **Renami-1.6.2.dmg** — the same app in a disk-image installer (drag to Applications).
- **renami-1.6.2-cli.zip** — command line (requires JDK 21+), or build from source below.

Already installed? Renami checks GitHub on launch and offers **Update & Restart** in one click —
or run `renami update` from the terminal.

## The desktop app

`Renami.app` is a native-feeling macOS app: SF Pro typography, the system accent colour, light/dark
mode that follows the desktop, drag & drop everywhere, and one sidebar with FileBot's views:

| View | What it does |
|---|---|
| **Rename** | FileBot's proven layout: **Original Files** and **New Names** side by side with a **Match / Rename** column between them. Drop files → Match against TMDb/TVDB/TVmaze/OMDb/Kitsu (TVmaze needs no key) → edit names (double-click, Load Names, Edit Format with live preview, F1–F12 presets) → Rename. Per-list tool strips for align, exclude, clear, history; action and conflict selectors below. |
| **Episodes** | Pick a show and season, get the episode list from the datasource, and rename downloaded episodes against it. |
| **Subtitles** | Search OpenSubtitles for every media file in a folder and download matches next to them. |
| **Filter** | Regex/glob file filter with live preview — the same engine as `renami filter`. |
| **Checksum** | Generate SHA-256/SHA-1/MD5/eD2K/CRC32 hashes, save SFV/MD5-style sidecars, verify with OK/MISMATCH/MISSING at a glance. |
| **History** | Every batch is journalled — browse it and **Undo** any batch, even copies and links (those get deleted). |
| **Post-Process** | The one-shot FileBot flow: pick a folder, archives are extracted automatically, everything is matched and moved into a structured output tree (`{n}/Season {s}/…`). Optionally delete processed archives. |
| **Watch** | Pick a folder and press Start — new files are processed the moment their download settles. Live activity log. |
| **Duplicates** | 3-phase dupe finder (size → partial hash → full SHA-256) with groups side by side. |

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

No API key? **TV shows work out of the box** — `--db auto` tries the keyed providers you
configured and then falls back to the key-free ones: **TVmaze** (TV shows and episodes,
no credentials at all) and **Kitsu** (anime). Everything still works offline with
`--db local` (rename purely from filename facts) — and `--help` on any command
documents its options.

Configuration is read from `~/.renami/config.properties` or `RENAMI_*` environment
variables: `RENAMI_TMDB_API_KEY`, `RENAMI_TVDB_API_KEY`, `RENAMI_OMDB_API_KEY`,
`RENAMI_OPENSUBTITLES_API_KEY`, `RENAMI_ACOUSTID_API_KEY`.

## Datasources and API keys

Renami is built so that **a fresh install needs no key at all** for TV. The GUI's
*Help ▸ API Keys…* panel lists every service with a **Get key…** button (opens the
sign-up page) and a **Test** button (validates the key with a live lookup).

| Service | Key? | Cost for personal use | Covers |
|---|---|---|---|
| **TVmaze** | none | free | TV shows + episodes (no movie API) |
| **Kitsu** | none | free | anime, AniDB-derived data |
| **TMDb** | free, instant | free for personal use | movies **and** TV — the one key worth adding |
| **OMDb** | free, by email | free, 1 000 lookups/day | IMDb data |
| **TheTVDB** | project key only | effectively paid: each user needs a TVDB subscription + PIN (`tvdb.user.pin`) | TV shows |
| **OpenSubtitles** | free account | free | the Subtitles view only |

`--db auto` consults the keyed providers you configured first, then TVmaze, then Kitsu —
it never fails just because a key is missing. Movies still want one free TMDb (or OMDb)
key, because TVmaze has no public movie endpoint.

## Engine features

- 🎬 **Media scanning** — recursive folder scan, video/audio detection
- 🔍 **Filename parsing** — `S01E02`, `1x02`, `102`, anime absolute `Show - 05` patterns,
  year, quality-token stripping
- 📡 **Metadata lookup** — TMDb, TheTVDB v4 (incl. aired/dvd/absolute episode orders),
  OMDb, TVmaze (**no API key needed**), Kitsu (anime, **no API key needed**), plus an
  offline `local` datasource; `--db auto` chains keyed providers first, then the
  key-free ones, so a fresh install resolves TV with zero configuration;
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
| 1.5 | FileBot-parity Rename view: Original Files / New Names dual panels, per-list tool strips, center Match/Rename column, Edit Format dialog, presets | ✅ |
| 1.6 | Key-free datasources (TVmaze + auto fallback chain), FileBot-style API-key panel with Get key/Test, first-run setup, full FileBot customer-journey parity (Load → Match → Rename → History → Undo) | ✅ |
| 1.6.1 | Auto-update: startup version check, one-click Update & Restart in the GUI, `renami update` CLI | ✅ |
| 1.7 | Full-screen TUI (Lanterna) with all modes as tabs | 🚧 |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the module map.

## License

GPL-3.0-or-later. Third-party components (Groovy, picocli, Gson, FlatLaf, MediaInfo, 7-Zip,
Chromaprint) remain under their own licenses.

# Customer-journey parity report — FileBot 5.2.1 vs Renami (2026-11-12)

Method: the same customer journey was driven click-by-click in both apps with real
mouse control (cliclick + screencapture), on the same fixtures:

- `journey/HL_SQ_DATA/` — `Up.2009.2160p.UHD.BluRay.x264-SPARKS.mkv` + `.nfo` (movie)
- `journey/TV_FIXTURE/` — `The.Office.US.S05E12.1080p.WEB-DL.DD5.1.H.264.mkv` + `.en.srt` (episode)

Evidence: numbered screenshots `70`–`115` in the audit folder
(`…/T/opencode/fb-audit/`). FileBot shots `78`–`79r`, Renami pre-fix `80`–`84b`,
Renami post-fix `90`–`115`.

## Journey steps compared

| # | Step | FileBot 5.2.1 | Renami (after fixes) | Verdict |
|---|------|---------------|----------------------|---------|
| 1 | Load (toolbar) | Native NSOpenPanel; files land **left side only**; New Names stays empty; labels strip the extension (badge carries it); rows plain black | Same: native FileDialog, left-only, empty New Names (zero-height hidden cells), stripped labels, plain rows | **PASS** |
| 2 | Match (big button) | Opens **Fetch & Match Data** popup (datasources grouped by mode + Edit Format/Preferences) | Same popup structure: Episode/Movie/Music/ID3/Smart groups, Automatic, Attributes (offline), Edit Format…, Preferences… | **PASS** |
| 3 | Pick datasource → match | Matched rows turn green, partner preview under each row, status "N of N names planned" | TVmaze (key-free) → "The Office - S05E12 - The Duel", green rows, "2 of 2 names planned" | **PASS** |
| 4 | Rename (big button) | Executes **immediately, no confirm dialog**; applied rows drop out of both lists; status reports result. (Rename itself is behind FileBot's license gate — see Deliberate differences.) | Executes immediately, no confirm; applied rows drop out; "2 files renamed. Use `renami undo` to revert." Disk verified changed (mkv + companion srt) | **PASS** |
| 5 | Rename with nothing planned | No-op guard toast: "Files and Names are already optimally aligned." | Same wording in status bar; disk verified unchanged | **PASS** |
| 6 | History (toolbar clock) | History dialog: batch table (Time/Files/Actions/Status) + per-op Elements table (From/To/Action) | Same dialog: batch table + From/To/Action ops table; selecting an active batch enables Undo batch | **PASS** |
| 7 | Revert | "Revert Selection" restores original names | "Undo batch" → confirm dialog → **2 of 2 files restored**, batch flips to `undone`, disk verified restored | **PASS** |

## Bugs found by walking the journey (all fixed)

1. **Dead toolbar icon buttons (critical).** `RenamePanel.iconButton()` accepted an
   `ActionListener` but never registered it — History, Align Up/Down, Exclude and the
   New Names Match icon did nothing, silently. Fixed; regression test
   `ToolbarButtonsTest` now fails if any labeled button in the rename view has no action.
2. **New Names mirrored originals after Load** (the user-reported finding). Unmatched
   rows are now hidden (zero-height cells) so New Names stays empty until Match, like FileBot.
3. **Match button matched directly** instead of opening the Fetch & Match Data popup.
4. **Rename required a confirm dialog** and was disabled until a plan existed. FileBot's
   Rename is always live once files are loaded and executes without confirmation; the
   no-op guard reports "Files and Names are already optimally aligned." instead.
5. **Unmatched rows rendered red.** Red is reserved for conflicts; loaded rows are plain.
6. **Labels included the extension** while the badge also showed it. Labels now strip it.
7. **Undo result message was overwritten** by the post-undo refresh; "x of y files
   restored" now survives (HistoryPanel ordering fix).

## Deliberate differences (not parity gaps)

- **Free vs paid.** FileBot's Rename/History engine sits behind a license gate
  ("License Required" popup at step 4). Renami ships the same workflow free under GPL-3.0 —
  this is the project's raison d'être, confirmed by the maintainer.
- **Key-free datasources.** TV/anime match with no API key (TVmaze + Kitsu fallback
  chain). Movie match needs a user TMDB/OMDb key (`RENAMI_TMDB_API_KEY` or
  `~/.renami/config.properties`); FileBot proxies this through its licensed service.
  The error message points at the env var/config path.

## Verdict

**Customer-journey parity: PASS.** Every step of Load → Match → Rename → History →
Undo behaves like FileBot 5.2.1 with the same layout and wording, in the 2026-macOS
design language. `gradle test` green (including the new toolbar regression guard);
verified on the installed jpackage build at `/Applications/Renami.app`.

# GUI parity audit — live screen-control walkthrough (2026-11-12)

Method: FileBot 5.2.1 (`/Applications/FileBot.app`) and Renami 1.5.0-installed were driven
with real mouse clicks (`cliclick` CGEvents; System Events `click at` proved unreliable on
Java windows) and every control was captured with `screencapture`.
Evidence: 30 numbered screenshots in
`/private/var/folders/8z/n9ttwq8559l6y4myttpkymr40000gn/T/opencode/fb-audit/`
(FileBot `00`–`46`, Renami `50`–`55`).

## 1. FileBot 5.2.1 ground truth (verified by clicking)

### App chrome
- macOS menu bar is minimal: `FileBot` (About/Services/Hide/Quit) and `Help`
  (Getting Started, FAQ, Forums, Discord Channel, Report Bugs, Request Help,
  **Quick Actions**, **Folder Actions**). No File/Edit/View menus. (`fb Help` menu dump)
- Mode rail (AX-exposed list): **Rename, Episodes, Subtitles, SFV, Filter, List**.
  There is **no History view** — History is a toolbar dialog. There is no
  Post-Process/Watch/Duplicates view.
- **No API-key GUI anywhere.** `PreferencesPanel` (decompiled) = Language + Match Mode +
  Episode Order only; `grep api.key` over `filebot.jar` hits only `TMDbCore`,
  `FanartTVClient` and a Groovy example. Keys are embedded by the developer or set via
  CLI/properties. (The API-key panel the user remembers is FileBot **4**.)
  → Renami's Help ▸ API Keys… panel is a deliberate superset for the GPL build.

### Rename view
- **Load** (Original Files toolbar) opens the **native macOS NSOpenPanel** (`03-load.png`).
- Dropping a folder lists **every entry** in it — media *and* images/nfo
  (`02-window.png`: backdrop, folder, landscape, logo, movie.nfo, mkv, mkv.nfo).
- Rows carry **extension badges** (nfo/mkv/jpg/png pills) and matched rows are
  **re-ordered to the top** after a match (`09-matched.png`).
- **New Names shows the display name** `Up (2009)` — *not* the final filename; the
  format is applied only at Rename time.
- Original Files toolbar: Align Down, Align Up, Exclude (red), **Load**, **Clear**
  dropdown (Original Files / New Names / Clear All, `46-clear-dropdown.png`),
  **History** dialog (`37-history.png`: Sequences + Elements tables, Filter, Import,
  Export, Revert Selection, Close), **Presets** popup (preset scripts + Edit Preset,
  `36-of-icon7.png`).
- Center: **Match** (re-align; silent no-op when already matched — verified 3×),
  **Rename** (applies).
- New Names toolbar: Align Down, Align Up, **Match** (green diamond), **Fetch Data**
  (text button → opens the *Fetch & Match Data* popup, `07/08`), **Load Names** popup
  (Load from Directory Index / Load from Text Content / Paste from Clipboard,
  `42-loadnames-popup.png`), **Settings** popup (Extension: Preserve/Override; Action:
  Rename/Copy/Keeplink/Symlink/Hardlink; Options: Post Process, `14-gear-popup.png`).
- **Fetch & Match Data popup**: Episode Mode (TheMovieDB::TV, AniDB, TheTVDB, TVmaze),
  Movie Mode (TheMovieDB, OMDb), Music Mode (AcoustID, ID3 Tags), Smart Mode (Automatic,
  Attributes), Options: **Edit Format** (→ `Movie Format` dialog: live preview, format
  field, Syntax box, live Examples, Cancel/Use Format, `44-edit-format.png`),
  **Preferences** (→ `Match Preferences` dialog: Language list w/ flags, Match Mode
  Opportunistic/Strict, Episode Order Airdate/DVD/Absolute/Digital/Story Arc/Production/
  Official/Date and Title, `43-editformat-state.png`).
- **Right-click a row** (either list) → **Select Match** popup: same datasource groups +
  "N selected item" footer (`34/35`).
- Movie match result for `Up.2009…mkv` via TheMovieDB: **Up (2009)** — correct.

### Other modes
- **Episodes**: datasource dropdown, search field, All Seasons, Airdate, English, Find;
  results table TV Series / Number of Episodes / Duration; History link (`28`).
- **Subtitles**: provider dropdown, search, All Languages, +, Find; provider badges
  (OpenSubtitles, BSUBs) top-right; table Search / Number of Subtitles / Duration (`29`).
- **SFV**: State/Name table; Load, Save as…, Clear + **SFV/MD5/SHA1/SHA2/SHA3** (`30`).
- **Filter**: Files panel (Load/Clear) + Archives/Types/Parts/Attributes/MediaInfo tabs,
  File/Path/Size table, **Extract All** (`31`).
- **List**: format field (`Sequence — {i.pad(2)}`), From/To spinners, Sequence button,
  Load/Copy/Save as… (`27b`).

## 2. Renami as found (screenshots 50–55)

- Load opened a **JFileChooser** styled like Aqua — NOT the native Finder panel. Fixed in
  this round: `loadFiles()` now uses `java.awt.FileDialog` (LOAD, multipleMode), which on
  macOS is the real NSOpenPanel and on Windows the native Explorer dialog.
- Drop of the same folder listed **only the 2 media files** (mkv+nfo) — FileBot lists all 7.
- Auto-match produced the **false movie match** "From Up on Poppy Hill" (Kitsu in the
  key-free chain) where FileBot/TheMovieDB gives "Up (2009)".
- **Fetch Data executes `match()` directly** instead of opening the datasource popup —
  the exact behavior difference the user reported.
- No extension badges; New Names shows full filenames (`From Up on Poppy Hill.mkv`)
  instead of display names; matched rows not re-ordered.
- Rail: Rename, Episodes, Subtitles, Filter, **Checksum**, **History** — vs FileBot's
  Rename, Episodes, Subtitles, **SFV**, Filter, **List** (+History as toolbar dialog).
- Fetch popup (right-click Match) uses lowercase ids (`tvdb`, `tmdb`…) not branded
  names+icons; has "Local Mode" instead of FileBot's Music Mode; Smart Mode lacks
  Attributes.
- Settings popup: Mode/Database/Min.score/Action/Conflict/companion/Edit Format — missing
  FileBot's **Extension Preserve/Override** and **Post Process**.
- Original Files toolbar missing the **History** button (History lives in the rail).

## 3. Fix list (implemented this round)

1. `Fetch Data` button opens the Fetch & Match Data popup (FileBot parity); the center
   Match button and the new green-diamond Match in the New Names toolbar do the matching.
2. Folder drop/Load lists **all** entries (FileBot parity), media-first ordering kept.
3. Kitsu removed from the **movie** search chain (anime-only) → honest no-match instead
   of false "From Up on Poppy Hill".
4. Row renderer: **extension badges** + display names in New Names + matched-first
   re-ordering after match.
5. Rail parity: **SFV** (was Checksum), add **List** mode, move **History** to a toolbar
   dialog button in Original Files.
6. Fetch popup: branded datasource names (TheMovieDB, TheTVDB, TVmaze, AniDB, OMDb…)
   with provider icons; Music Mode (AcoustID/ID3 Tags) + Smart Mode Attributes.
7. Settings popup: add Extension Preserve/Override and Post Process.
8. App icon: rebuild on the macOS 1024 px grid with standard padding.

Deferred (documented, not blocking parity): GroovyPad hidden label, Quick/Folder Actions,
BSUBs/OpenSubtitles login badges, Music Mode matching backend (AcoustID).

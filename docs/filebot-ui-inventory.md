# FileBot 5.2.1 — button & functionality inventory

Source of truth: every UI class of `filebot.jar` (FileBot 5.2.1, `/Applications/FileBot.app`)
decompiled with CFR 0.152 (159 classes under `net.filebot.ui.**`), cross-checked against a
live screenshot (`/tmp/fb-00-full.png`). FileBot 5 replaced its old JavaFX UI with a Swing UI:
**left mode rail + big centered mode title + dual-panel Rename view with per-list toolbars**.

## 1. App chrome (`MainFrame`, `Mode`, `HeaderPanel`, `FileBotMenuBar`)

| Control | Where | Function |
|---|---|---|
| Mode rail (vertical icon list) | left edge, ~95 px | Switches the single visible panel; selection persisted. Modes: **Rename, Episodes, Subtitles, SFV, Filter, List** (Subtitles hidden in Mac App Store sandbox) |
| Mode icons | rail cells | icon + label under, big tap target; drag-over a mode for 300 ms auto-switches and transfers the dropped files to that mode |
| Header panel | top, full width | Centered 24 pt mode title ("Rename"), separator line, subtle gradient |
| Hidden label | top-right corner (invisible) | Left-click: reveals app id / version / live memory usage. Left-click again: opens **GroovyPad** (script console). Right-click: hides |
| Help menu | macOS menu bar | Getting Started, FAQ, Forums, Discord Channel, Report Bugs, Request Help (all open URLs) |
| Ctrl+Shift+Delete | global | Clear Cache (disk store) |
| F5 | global | Open GroovyPad (script console / "Run") |

## 2. Rename mode (`RenamePanel`) — the dual-panel layout

Layout: `[Original Files list]  [Match / Rename buttons]  [New Names list]`, each list is a
titled border (`TitledBorder`) with its own **button bar docked south**. Match/Rename are
icon-over-text buttons stacked in the center column.

### 2.1 Center column

| Control | Function |
|---|---|
| **Match** (icon = current MatchMode) | Re-aligns "New Names" with "Original Files" (optimal 1-to-N assignment; Shift = strict N-to-N). Progress monitor; logs "already optimally aligned". If lists empty → opens Fetch popup. **Right-click → Fetch & Match Data popup** |
| **Rename** (icon = current action, default "Rename"/MOVE) | Executes rename: validate names → conflict dialog → `StandardRenameWorker`/`NativeRenameWorker` → post-process. Removes done rows. **Right-click → Settings popup** |

### 2.2 Original Files list (left)

| Control (bar order) | Function |
|---|---|
| Align Down / Align Up (icon) | Move selected row(s) down/up (also drag rows to reorder) |
| Exclude Selected Items (icon, red) | Remove selected pairing (Shift = remove row entirely). Delete/Shift+Delete keys |
| **Load** (text button) | File chooser → import files/folder into the list |
| Clear (icon) | Dropdown: **Original Files** / **New Names** / **Clear All** |
| Open History (icon) | Dialog with the complete rename history (date, file, new name, status) |
| Presets (icon) | Dropdown of user presets (group 1 = F1–F12 bound) + **Edit Preset** → preset editor (name, keystroke, format, datasource, order, match mode, language, action) |
| Drop zone (empty state) | "Drop files or a folder here / or pick a folder to scan / Choose" |
| Double-click row | Fetches media info, popup: **Open, Reveal, Rename…, Move to Trash, Set Attributes** |
| Right-click row | Fetch & Match popup scoped to selection ("Select Match": Episode/Movie/Music/Smart datasources) |

### 2.3 New Names list (right)

| Control (bar order) | Function |
|---|---|
| Align Down / Align Up (icon) | Reorder selected rows |
| **Fetch Data** (text button) | **Fetch & Match Data** popup: *Episode Mode* (TheTVDB, TheMovieDB::TV, AniDB…), *Movie Mode* (TheMovieDB, IMDb…), *Music Mode* (MusicBrainz…), *Smart Mode* (Automatic, Attributes), Options: **Edit Format** (opens FormatDialog), **Preferences** (Match Mode Opportunistic/Strict, Episode Order, Language) |
| Load Names (icon) | Popup: **Load from Directory Index**, **Load from Text Content**, **Paste from Clipboard** |
| Settings (icon) | Popup: *Extension:* Preserve / Override; *Action:* Move, Copy, Keep Link, Symlink, Hardlink; *Options:* **Post Process** (checkbox grid: Artwork, Cover, NFO, URL, Metadata, Import, SRT, Subtitles, Finder Tags, Tags, Date, Chmod, Touch, Thumbnail, Prune, Clean, Reveal, Refresh + Groovy scripts) |
| Edit Match (icon) | Popup: **Edit Format** (FormatDialog with expression editor + samples), **Edit Match** (pick the matched movie/episode object), **Edit Name** (free-text override), **Inspect** (match details panel) |
| Double-click row | Same Edit popup titled by match type |
| Right-click row | "Select Match" popup scoped to selection |

### 2.4 Match details panel (`MatchDetailPanel`, toggled by F6 / Space)

Docked above the lists: tabs File / Match / Data — file metadata, matched object properties,
binding table; used for debugging matches.

### 2.5 Keyboard (Rename view)

| Key | Action |
|---|---|
| F1–F12 | Apply preset #1–#12 |
| F2 | Quick "Edit Name": match by filename via local datasource |
| F3 | Quick "Edit Match": match via extended attributes (xattr) |
| F6 / Space | Toggle Match Details |
| F7 | Copy Debug Information (snapshot of model; paste restores it) |
| Ctrl+N / Ctrl+M | Edit Name / Edit Match on selected row |
| Ctrl+O / Ctrl+L | Open / Reveal selected file |
| Delete / Shift+Delete | Exclude item / remove row |

## 3. Episodes mode (`EpisodeListPanel`)

| Control | Function |
|---|---|
| Search engine select-button | Pick datasource (TheTVDB, TheMovieDB::TV, AniDB, …) |
| Search field + **Find** | Autocomplete over the full series index; Enter submits; results open as a closable tab |
| Season spinner | All / specific season (locked when provider has no season support) |
| Order combo | Airdate / DVD / Absolute |
| Language combo | Series-language for lookups |
| Tab strip | **History** tab (TV Series, #Episodes, link, status, duration) + one tab per search |
| Copy (button) | Send to Rename / Send to List / Copy to Clipboard |
| Group (button) | Multi Episode, Double/Triple/Quadruple Episodes, By Date, Ungroup (Ctrl+Q group / Ctrl+U ungroup) |
| Save (button) | Export episode list to file |
| Row context menu | Send to ▸, Group ▸, Duplicate, Delete, Copy, Save, Select All |

## 4. Subtitles mode (`SubtitlePanel`)

Search field + engine select + **Find**; Language combo; **Login** icon (OpenSubtitles).
Result tab: subtitle list with filter field + clear-filter icon; context menu **Download**,
**Preview**, **Save As…**, **Export…**; Enter = Fetch. Separate **upload drop target**
(drag video files to upload subtitles). History tab columns: Search, #Subtitles, link, status, duration.

## 5. SFV / Checksum mode (`SfvPanel`)

| Control | Function |
|---|---|
| Load | Import files/folder into checksum table |
| Save as … | Write checksum file (md5sum-style) |
| Clear | Empty table |
| Hash-type toggle buttons | CRC32 / MD5 / SHA-1 / SHA-256 / SHA-512 (radio group, recomputes) |
| Table | File / hash / state icon (verified/failed); Delete removes rows; total progress bar |

## 6. Filter mode (`FilterPanel`)

Left: folder **file tree**. Right: tool tabs — **Extract** (list archive entries, *Extract All*),
**Type** (filter by file type), **Split** (split by pattern), **Attribute** (filter by media
attributes), **MediaInfo** (probe video/audio properties). Tools export filtered files for drag & drop.

## 7. List mode (`ListPanel`)

Single list + format button **Sequence** (edit format expression for the list), **Load**,
**Copy** (Send to Rename / SFV / Clipboard), **Save** (export). List modes: Sequence, Episode, File.

## 8. Cross-mode transfer

Dragging files onto a rail mode switches mode and pipes the drop into that panel
(`TargetTransferable` on the event bus). Episode lists / file lists can be "Sent to" Rename/List/SFV.

---

## Renami mapping (what we build)

| FileBot control | Renami equivalent |
|---|---|
| Mode rail | Sidebar (Rename, Post-Process, Watch, Subtitles, Duplicates, Checksum, History) — keep our 2026 design |
| Header title | Keep our sidebar+card chrome but add the centered mode title |
| Original Files / New Names dual panel + center Match/Rename | **Rebuild Rename view exactly like this** |
| Load / Clear / History / Presets bar | Same actions, restyled |
| Fetch Data / Load Names / Settings / Edit Match bar | Same actions (our sources: local + OpenSubtitles etc.) |
| Format field inline | Moves into **Edit Format** dialog (FileBot has no inline format field in 5.x) |
| Action/Conflict selectors | Inside **Settings** popup (Action) + conflict dialog at Rename time |
| Match details (F6/Space) | Later milestone |

---

## Per-button audit (FileBot 5.2.1 → Renami 1.6.0)

Legend: ✅ present · 🆕 added in this pass · 🔶 simplified equivalent · ⛔ intentionally not built.

### 1. App chrome

| FileBot control | Status | Renami |
|---|---|---|
| Mode rail (icon + label, selection persisted) | ✅ | Sidebar rail, colored icons, selection plate |
| Drag-over rail mode → switch + transfer files | 🆕 | `Sidebar` TransferHandler → `RenamiFrame.transferFiles` |
| Centered mode title header | ✅ | `Ui.header` centered in every view |
| Hidden label → GroovyPad / version | ⛔ | no script console; About box instead (`showAbout`) |
| Help menu (macOS menu bar) | 🆕 | `installMenuBar`: Getting Started / FAQ / Report Bugs / Request Help / Check for Updates |
| Ctrl+Shift+Delete clear cache | ⛔ | cache managed automatically |
| F5 GroovyPad | ⛔ | — |
| Light/dark/auto theme | ✅ | theme picker in rail footer, persisted |
| Collapsible rail | ✅ | header collapse button |

### 2. Rename view

| FileBot control | Status | Renami |
|---|---|---|
| Dual panel + center Match/Rename | ✅ | icon-over-text center column; Rename is accent-filled default button |
| Match right-click → Fetch popup | ✅ | `buildFetchPopup` |
| Rename right-click → Settings popup | ✅ | `buildSettingsPopup` (action, conflict, companion) |
| Align Down/Up both lists | ✅ | up/down strip buttons |
| Exclude Selected Items (Delete/Shift+Delete) | ✅ | exclude button + both keys |
| Load | ✅ | Load button + drop zone + Choose |
| Clear dropdown (Original/New/All) | ✅ | clear icon popup |
| Open History | ✅ | History rail view |
| Presets (F1–F12 + editor) | ✅ | presets popup, F1–F12 keys, local presets |
| Double-click row → Open/Reveal/Rename…/Move to Trash | 🆕 | `buildOriginalRowPopup` + `renameFileDirect`/`moveToTrash` |
| Right-click row → Fetch & Match popup | ✅ | scoped to selection |
| Fetch Data popup (datasource + Edit Format + Preferences) | ✅ | `buildFetchPopup` |
| Load Names: Directory Index / Text File / Clipboard | 🆕 | Directory Index added |
| Settings popup (extension, action, post-process) | 🔶 | action + conflict + companions; no post-process grid (separate Post-Process view) |
| Edit popup: Edit Format / Edit Match / Edit Name / Inspect | 🆕 | Edit Match added (`editMatch` + candidate picker) |
| Match details panel (F6/Space) | ⛔ | later milestone; Inspect dialog covers it |
| Keys Ctrl+N / Ctrl+M / Ctrl+O / Ctrl+L | 🆕 | all four wired |

### 3. Episodes view

| FileBot control | Status | Renami |
|---|---|---|
| Datasource select | ✅ | Source combo (tvdb/tmdb) |
| Search + Find | ✅ | Search field + button, Enter submits |
| Season spinner | ✅ | spinner |
| Order combo (aired/dvd/absolute) | 🆕 | wired to `getSeason(id,season,order,language)` |
| Language combo | 🆕 | persisted as `datasource.language`, sent to TMDb/TVDB |
| Search history tabs | 🔶 | single result table |
| Copy / Send to Rename | ✅ | both buttons |
| Group (multi-episode) | ⛔ | later milestone |
| Save (export list) | 🆕 | Save… file chooser |
| Row context menu (Send to/Copy/Save/Select All/Delete) | 🆕 | added |

### 4. Subtitles view

| FileBot control | Status | Renami |
|---|---|---|
| Search + language | ✅ | folder scan + language combo |
| Login (OpenSubtitles) | 🆕 | Login… dialog, key persisted |
| Filter field + clear icon | 🆕 | live refilter + clear button |
| Download / double-click download | ✅ | Download button + double-click single row |
| Preview / Save As / Export | ⛔ | later milestone |
| Upload drop target | ⛔ | OpenSubtitles upload API not built |

### 5. Checksum (SFV) view

| FileBot control | Status | Renami |
|---|---|---|
| Load | ✅ | folder/file field + Choose + drop |
| Save as… | ✅ | Save sidecar… |
| Clear | 🆕 | Clear button |
| Hash toggle CRC32/MD5/SHA-1/SHA-256/SHA-512 | 🆕 | SHA-512 added to `HashType` + segmented control |
| Table + Delete removes rows | 🆕 | Delete key wired |
| Verify against sidecar | ✅ | Verify… (OK/MISMATCH/MISSING) |

### 6. Filter view

| FileBot control | Status | Renami |
|---|---|---|
| Folder tree | 🔶 | folder field + scan |
| Extract/Type/Split/Attribute/MediaInfo tabs | 🔶 | Groovy predicate + type chips (Archives/Video/Audio/Images) |
| Send to Rename | ✅ | button |

### 7. List view

| FileBot control | Status | Renami |
|---|---|---|
| List mode | ⛔ | Original Files list covers browsing |

### 8. Cross-mode transfer

| FileBot control | Status | Renami |
|---|---|---|
| Drop files on rail mode → switch + pipe in | 🆕 | rail TransferHandler + `acceptFiles`/`preload` per panel |
| Episodes "Send to" Rename | ✅ | button + context menu |

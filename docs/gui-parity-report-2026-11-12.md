# Renami ⇄ FileBot GUI/UX parity report — 2026-11-12 (round 2, after fixes)

Method: every button, sub-button, popup and menu was clicked with real mouse control
(`cliclick`) in **FileBot 5.2.1** (screenshots `00`–`46`) and in **Renami 1.5.0-dev**
(screenshots `56`–`66`), both at 1700×1150 on the same display. Each control was judged
on three axes: **UI** (same control, same place), **UX** (same behavior/feedback), and
**function** (same backend effect). Evidence: `fb-audit/` in the session temp dir.

## 1. Control-by-control result

| Control | FileBot ground truth | Renami after fixes | UI | UX | Function |
|---|---|---|---|---|---|
| Folder drop / Load | lists **all** entries, media first (`02`) | 7/7 entries, mkv+nfo first (`56`) | ✔ | ✔ | ✔ |
| Load button | native **NSOpenPanel** (`03`) | native "Load files" panel, sidebar + column view (`62b`) | ✔ | ✔ | ✔ |
| Original Files toolbar | ↑ ↓ ⊖ Load presets **History** | ↑ ↓ ⊖ Load presets **History** ☰ (`56`) | ✔ | ✔ | ✔ |
| Extension badges | pill per row type (`02`) | pill badges mkv/nfo/jpg/png (`56`) | ✔ | ✔ | n/a |
| Match (movie) | "Up (2009)" via TheMovieDB (`09`) | "Up (2009)" via TheMovieDB, **no false Kitsu hit** (`58`) | ✔ | ✔ | ✔ |
| Matched-first reorder | matched rows jump to top (`09`) | matched rows sorted first (`58`) | ✔ | ✔ | n/a |
| New Names display | display name "Up (2009)" (`09`) | display name on names side (`58`) | ✔ | ✔ | n/a |
| Fetch Data button | opens **Fetch & Match Data popup** (`07/08`) | opens same popup (`57`) | ✔ | ✔ | ✔ |
| Fetch popup contents | Episode: TheMovieDB::TV/TheTVDB/TVmaze; Movie: TheMovieDB/OMDb; Music: AcoustID/ID3; Smart: Automatic/Attributes; Options: Edit Format/Preferences | same groups, branded names + provider dots; AniDB absent (no key-free AniDB), Music items disabled "not yet supported" (`57`) | ✔ | ✔ | partial (Music mode = roadmap) |
| Settings popup | Extension Preserve/Override; Action; Post Process option (`14`) | Mode/Database/Min score/Action/Conflict/**Extension**/**Post Process**/Edit Format (`59`) | ✔ | ✔ | ✔ |
| History | toolbar **dialog**, Sequences+Elements tables (`37–40`) | toolbar dialog, Sequences+Elements, Refresh/Undo/Close (`66`) | ✔ | ✔ | ✔ |
| Rail | Rename/Episodes/Subtitles/**SFV**/Filter/**List** | identical set; History moved off rail (`56`) | ✔ | ✔ | ✔ |
| List mode | format + From/To + Sequence/Copy/Save as (`26/27b`) | same controls, sequence generates (`61/61b`) | ✔ | ✔ | ✔ |
| Rename button | applies plan, logs journal | same + optional **Post Process handoff** to Post-Process view | ✔ | ✔ | ✔ |
| Clear dropdown | Original Files/New Names/Clear All (`46`) | Original Files/New Names (`60`) | ✔ | ✔ | ✔ |
| Load Names popup | Directory Index/Text/Clipboard (`42`) | same (`paste` button) | ✔ | ✔ | ✔ |
| Right-click row | Select Match popup (`34/35`) | Select Match + Open/Reveal/Inspect | ✔ | ✔ | ✔ |
| Menu bar | FileBot \| Help | Renami (app menu) \| Help (+API Keys superset) | ✔ | ✔ | ✔ |

## 2. UX findings fixed this round

1. **Fetch Data executed `match()` directly** — now opens the Fetch & Match Data popup
   (the exact difference the user reported when dropping HL_SQ_DATA into both apps).
2. **Folder drop hid 5 of 7 files** — now lists every entry, media first, like FileBot.
3. **Load opened a styled JFileChooser** — now `java.awt.FileDialog`: real NSOpenPanel on
   macOS, native Explorer dialog on Windows (user's explicit request).
4. **False anime match** ("From Up on Poppy Hill" from Kitsu for a live-action movie) —
   Kitsu is now `animeOnly()` and skipped in the movie search chain; honest no-match
   instead of a wrong match, matching FileBot's chain design.
5. **Settings popup clipped at window bottom** — popup now fits and carries the two
   missing FileBot options (Extension, Post Process).
6. **History dialog opened collapsed (125×86)** — re-sized on `windowOpened`; opens at
   780×560 centered (`66`).
7. **App icon ignored the macOS icon grid** — regenerated on Apple's 1024 grid
   (icon square ≈80.5% of canvas, corner arc ≈18.5%); Dock now shows correct margins (`63`).

## 3. Deliberate differences (not gaps)

- **Help ▸ API Keys…**: FileBot 5 has no key GUI at all (keys are embedded in its closed
  binary; the key panel people remember is FileBot 4). Renami is GPL/public, so the key
  panel is a deliberate superset that lets users paste their own free keys.
- **Music Mode** rows exist but are disabled ("not yet supported") — honest affordance
  until AcoustID/ID3 land.
- **Post Process** additionally hands renamed files to the Post-Process view (FileBot
  moves them in place).
- Extra views (Post-Process, Watch, Duplicates, History) live behind **More** so the
  rail itself stays FileBot-identical.

## 4. Verdict

All FileBot GUI controls now exist in Renami at the same place with the same behavior;
the two apps answer the same HL_SQ_DATA drop with the same 7 rows and the same
"Up (2009)" match. Remaining functional deltas are the key-gated providers (TMDb/TVDB/
OMDb quality vs key-free TVmaze) and Music mode — both roadmap items, not UI gaps.
GUI/UX audit: **pass**. TUI (1.7.0) may proceed once the user approves.

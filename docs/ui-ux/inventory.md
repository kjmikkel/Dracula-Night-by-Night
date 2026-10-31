# UI/UX inventory: Dracula Night by Night

*Whole-app run, 24 October 2026, debug build of 1.0.0 on branch `feat/204-whos-who` (145703a). Android 17 emulator
(`Dracula_Root_API_37`, 1080 × 2400, 420 dpi). Evidence: `reports/2026-10-24-whole-app/shots/`.*

Code paths are relative to `app/app/src/main/java/com/jensen/draculanightbynight/`. "Ratchet" means the state also
runs in the accessibility ratchet (`a11y/Cases.kt`) in its six looks, and that run passed this day.

## Items

| id | kind | name | code | how reached | observed | states visited | components |
|---|---|---|---|---|---|---|---|
| S1 | screen | Welcome | `welcome/WelcomeScreen.kt` | first launch | run | first run, light | heading, mode card (radio), switch row, filled button |
| S2 | screen | Timeline | `timeline/TimelineScreen.kt` | after Begin; app start | run | first run (one entry), tempo, free, filtered, scrolled (compact card), large text 200 %, dark, keyboard (Tab), landscape | top app bar (icon buttons), Tonight card, filter status row, day header, list row, ornament, Next card |
| S3 | screen | Reader | `reader/ReaderScreen.kt` | timeline row or card → Read | run | memo, diary, telegram, filtered (follow switch), last arrived entry (Next disabled), large text 200 %, dark, landscape | top app bar (text buttons), document header, dateline, ornament, body text, bottom app bar, segmented button |
| S4 | screen | Who's who | `whoswho/WhosWhoScreen.kt` | Reader → "Who's who" | run | empty (first visit), one group open, large text 200 %, TalkBack focus | top app bar, group heading, expandable list row |
| S5 | sheet | Filter | `timeline/FilterSheet.kt` | Timeline → Filter | run | none selected, one selected | bottom sheet, section heading, filter chip, text button |
| S6 | screen | Settings | `settings/SettingsScreen.kt` | Timeline → Settings | run | tempo, free (rows change), full scroll | top app bar, section heading, list row, switch row, radio group |
| S7 | screen | Bookmarks | `bookmarks/BookmarksScreen.kt` | Timeline → Bookmarks | run | empty | top app bar, segmented button, empty text, list row (ratchet), snackbar (ratchet) |
| S8 | screen | Two panes | `timeline/TwoPaneScreen.kt` | wide window | seen in code only | phone landscape is below the two-pane height, so it stays one pane; tablet not reached (ratchet: landscape and portrait tablet) | — |
| S9 | screen | About | `about/AboutScreen.kt` | Settings → About | run | light | top app bar, section heading, list row |
| D1 | dialog | How to read (mode) | `timeline/ModeDialog.kt` | Settings → Mode | run | current mode, each other mode selected | alert dialog, radio group |
| D2 | dialog | Skip to a day | `settings/SettingsScreen.kt:310` | Settings → Skip to a day (tempo/calendar only) | seen in code only | not reached (ratchet `settings-skip-day`) | alert dialog |
| D3 | dialog | Start over | `ui/StartOverDialog.kt` | Settings → Start over | run | free mode, no bookmarks | alert dialog, checkbox (ratchet) |
| D4 | dialog | Start date | `settings/SettingsScreen.kt:294` | Settings → Start date | seen in code only | not reached (ratchet `settings-start-date`) | date picker dialog |
| D5 | dialog | Keep a copy up to date / Replace your reading? | `settings/SettingsScreen.kt:339`, `:361` | Settings → Your reading | seen in code only | not reached: needs the system file picker | alert dialog |
| D6 | dialog | Read it now? | `timeline/TimelineScreen.kt:126`, `bookmarks/BookmarksScreen.kt:189` | Next card / locked bookmark | seen in code only | not reached (ratchet `read-it-now`, `read-now-dialog`) | alert dialog |
| D7 | dialog | Licence | `about/AboutScreen.kt:125` | About → a font | seen in code only | not reached (ratchet `licence`) | alert dialog |
| D8 | dialog | Notification permission | system | first Begin with notifications on | run | first run | system dialog |
| N1 | notification | New entries | `notify/Notifications.kt` | entry arrives | seen in code only | not reached: needs time to pass | notification |

## Flows

| id | flow | passes through |
|---|---|---|
| FL-1 | First launch to first entry | S1 → D8 → S2 → S3 |
| FL-2 | Meet the cast | S3 → S4 |
| FL-3 | Follow one voice or document type | S2 → S5 → S2 → S3 (follow switch) |
| FL-4 | Change how to read | S2 → S6 → D1 → S2 |

## Components

| component type | used in |
|---|---|
| top app bar | S2, S3, S4, S6, S7, S9 |
| icon button | S2 |
| text button (in app bar) | S3, S4, S6, S7, S9 (Back), S3 (Who's who, Bookmark) |
| card | S2 (Tonight, Next) |
| list row | S2, S6, S7, S9 |
| expandable list row | S4 |
| section / group heading | S1, S4, S5, S6, S9 |
| filter chip | S5 |
| segmented button | S3, S7 |
| radio group | S1, S6, D1 |
| switch row | S1, S6 |
| checkbox | D3 |
| bottom sheet | S5 |
| bottom app bar | S3 |
| alert dialog | D1, D2, D3, D5, D6, D7 |
| date picker dialog | D4 |
| empty state text | S4, S7 |
| snackbar | S7 |
| notification | N1 |

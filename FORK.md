# Modern UI fork – notes

This fork puts a modern UI layer on top of Logisim-evolution while keeping the
simulator core untouched, so upstream changes can still be merged.

## What is different from upstream

| Area | Change | Files |
|------|--------|-------|
| Build | Kotlin + Compose Multiplatform (Desktop) added next to Java | `build.gradle.kts` |
| Theme | Custom FlatLaf defaults: accent color, rounded corners, slim scrollbars, flat tabs, embedded menu bar | `src/main/resources/com/cburch/logisim/theme/FlatLaf.properties`, `Main.java` |
| Theme | Default look & feel is now *macOS Light* (only for new installs, existing prefs win) | `AppPreferences.java` |
| Attributes | The attribute table is replaced by a Compose panel (filter field, switches for yes/no, dropdowns, inline text editing, inline error messages) | `src/main/kotlin/com/cburch/logisim/gui/modern/*` |
| Hook | `AttrTable.addModelChangeListener(...)` so the Compose panel can mirror the classic table | `AttrTable.java`, `Frame.java` |

The classic `AttrTable` is still created and driven by all existing code; it is just not
shown. The Compose panel listens to it. Disable the new panel with
`-Dlogisim.modernUi=false` (handy to compare old vs. new).

## Run

```bash
./gradlew run
```

Requires JDK 21+. The first build downloads Kotlin and Compose (a few hundred MB).

## Next steps (suggested)

1. Toolbox / component explorer as Compose tree (`gui/main/Toolbox.java`)
2. Toolbar + zoom control in Compose (`gui/main/Toolbar`, `gui/generic/ZoomControl`)
3. New main window shell (Compose `Scaffold`) embedding the existing `CanvasPane` via `SwingPanel`
4. Only later: render the canvas itself with Compose/Skia (large refactor, breaks upstream merges)

## Keeping up with upstream

```bash
git remote add upstream https://github.com/logisim-evolution/logisim-evolution.git
git fetch upstream
git merge upstream/main
```

License: GPLv3, like upstream – the fork must stay open source under GPLv3.

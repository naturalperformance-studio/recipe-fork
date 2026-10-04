# The theme is one resource file the owner edits

**Status:** accepted
**Date:** 2026-10-04
**Requirements:** A11Y-002; F1 of `docs/increments/serverless-roadmap.md` (the owner, 2026-10-03: one theme file in the repository, the app offers only light / dark / as on the device)

**Context:** colours came from `lightColorScheme()` defaults in `ui/AppTheme.kt`; the owner wants to change colours without touching Kotlin, and a dark theme.

**Decision:**
- Every theme value lives in `app/src/main/res/values/app_theme.xml`: colour resources named `theme_light_<role>` and `theme_dark_<role>` for every Material 3 colour role (primary, onPrimary, … surfaceContainerHighest, error, outline …), plus a few `dimen` values for the shape scale (corner sizes) and the body/title type sizes. Android Studio shows each colour as a swatch beside its line.
- `ui/AppTheme.kt` builds both `ColorScheme`s from those resources by name, explicitly (not through `values-night`), so the app's own choice — light, dark, or as on the device — picks the scheme regardless of the system configuration.
- The choice is a device setting stored like the other book settings (`data/`), applied at once; the system bars' icons follow the scheme in use.
- `ContrastTest` reads the same resources and checks every listed pair in both schemes.
- Media overlays (the crop, a clip's controls) keep their fixed black/white: they sit over pictures, not over the theme.

**Consequences:** a colour change is an edit of one XML file and a rebuild; a missing or misnamed role fails the build or `ContrastTest`, not the device. `docs/theme.md` tells the owner how.

**Alternatives considered:** `values-night` resources — rejected: they follow the system, not the app's own choice. A JSON asset parsed at run time — rejected: errors would surface on the phone, and Android Studio would show no swatches. Generating Kotlin from a file at build time — rejected as machinery the XML resources already give.

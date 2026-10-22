# Changing the app's colours

Every colour, corner size and main text size of the app lives in one file:

    app/src/main/res/values/app_theme.xml

- Lines named `theme_light_…` are the light theme, `theme_dark_…` the dark one. Each is a colour in `#RRGGBB`; Android Studio shows a swatch beside it — click it to pick a colour.
- The names are the Material 3 colour roles: `primary` is the main accent (buttons, links), `onPrimary` the text on it, `surface` the background, `onSurface` the text on it, `error` the colour of destructive actions, and so on. Change a colour and its `on…` partner together. One name is not an M3 role: `raisedPaper` (after both schemes) is the paper of the collection spines, a little lighter than the page; it has no `on…` partner — text on it is `onSurface` and `onSurfaceVariant`.
- After editing, build (`scripts/artefact.sh`) and run the tests (`./gradlew testAll`): `ContrastTest` fails if a text colour becomes too hard to read on its background, naming the pair.
- In the app, Settings → Theme chooses Light, Dark or As on the device.

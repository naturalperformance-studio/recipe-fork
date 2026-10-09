# Google Play Data safety — answers for the serverless build

Drafted for v0.25 (T-184) from `develop` on 2026-10-09. These answers hold **only for the serverless build** (`-Precipefork.serverless=true`). The standard build talks to the product's server and needs different answers. Check the form against Play Console when you fill it in, because Google reorders its questions now and then.

How Play defines the terms (Play Console help, "Provide information for Google Play's Data safety section"): data is **collected** when it leaves the device. Data that is processed only on the device, and never sent off it, is not collected. Data is **shared** when it goes to a third party, except when the user starts the transfer in a way they expect, for example by sending a file to another app themselves. Check this wording in the help page when you fill in the form.

Marks used below: **[sure]** means checked in the code. **[check]** means it depends on Play's current form or wording, or on something this draft cannot verify.

---

## The answers to type into the form

| Form question | Answer | Confidence |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | [sure] for the code. It assumes the Play bundle is the serverless build (see check A) |
| Is all of the user data collected by your app encrypted in transit? | Not asked once the answer above is No. If it is asked: nothing is transmitted | [check] |
| Do you provide a way for users to request that their data is deleted? | Not asked once the answer above is No. If it is asked: no data leaves the device. Deleting a recipe in the app, or uninstalling the app, deletes the data | [check] |
| Account creation (if the form asks) | The app does not let users create an account | [sure] |
| Independent security review | No | [sure] |

With "No" to the first question, the Data safety section on the listing reads "No data collected" and "No data shared with third parties".

---

## Evidence

### 1. The serverless build makes no network call [sure]

- `app/src/main/kotlin/app/recipefork/app/AppContainer.kt`: `val httpClient: HttpClient? = if (mode == Mode.Standard) httpClientOf() else null`. In serverless mode no HTTP client is ever built. `dictionarySync` is built only from that client, so it is `null` too, and `start()` launches nothing.
- `app/src/main/kotlin/app/recipefork/app/MainActivity.kt`: `MODE = modeOf(BuildConfig.SERVERLESS)` is the only reader of the build flag (`docs/architecture/decisions/20260928-serverless-mode-is-a-build-flag.md`).
- `app/src/test/kotlin/app/recipefork/app/CompositionRootTest.kt` `theServerlessCompositionRootConstructsNoHttpClientAndStartsNoSync`: in serverless mode, zero clients are made and zero requests are sent.
- A search of `app/src/main/kotlin` finds no other network code: no `HttpURLConnection`, `URL(`, sockets, WebView, OkHttp, and no `http(s)://` address outside XML namespaces. The only Ktor use is `data/Network.kt` and `dictionary/DictionaryRepository.kt`, and both are reached only through `httpClient`.
- `AndroidConnectivity` (`data/Connectivity.kt`) is still built in serverless mode, but its flow is cold. It is read only by `DictionarySync`, which does not exist in serverless mode, so it never registers a network callback. It reads the network state and sends nothing.
- The ingredient and tag dictionaries ship inside the app (`app/src/main/assets/dictionaries.json`, `decisions/20261003-the-serverless-dictionary-ships-with-the-app.md`). Nothing is downloaded.

### 2. What stays on the device (not "collected") [sure]

| Data | Where it is kept | Code |
|---|---|---|
| Recipes: titles, ingredients, steps, timers, tags, notes, descriptions | Room database of the book in app-private storage | `book/BookDatabase.kt`, `recipe/RecipeRepository.kt` |
| Photos and clips | App-private files directory (`filesDir`), per book | `media/PhotoStore.kt`, `media/ClipStore.kt`, `AppContainer.kt` |
| Done marks on cards | App-private SharedPreferences | `card/DoneMarks.kt` |
| Running timers | App-private Room database | `timer/RunningTimers.kt` (`TimerDatabase`) |
| Settings: language, theme, usual servings, drag-handle side, last book view | App-private SharedPreferences | `data/AppLanguage.kt`, `data/ThemeChoice.kt`, `data/UsualServings.kt`, `data/DragHandleSide.kt`, `data/LastBookView.kt` |
| Shipped dictionary (copied into the local database) | App-private Room database | `dictionary/DictionarySnapshot.kt`, `data/AppDatabase.kt` |
| Exported files and camera shots waiting to be cropped | App cache (`cache/exports/`, `cache/capture/`), shared only through non-exported FileProviders | `res/xml/file_paths.xml`, `res/xml/capture_paths.xml`, `media/Captures.kt` |

- The app takes nothing personal: no name, e-mail, phone number, contacts, location or device identifiers. There is no account and no sign-in in the serverless build.
- Photo and clip metadata (location, capture time, device) is removed before anything is stored. `media/PhotoReencoder.kt` re-encodes every photo as a plain JPEG with no metadata. `media/ClipTransformer.kt` and `media/AnimationEncoder.kt` write MP4s through `InAppMp4Muxer` with `OrientationOnly`, keeping only the orientation. So even a recipe the user shares carries no photo location.
- `android:allowBackup="false"` (`AndroidManifest.xml`): the app's data is not uploaded to Google's cloud backup. **[check]** On Android 12 and later, `allowBackup="false"` does not stop a device-to-device transfer when the user moves to a new phone. That stays on the user's own devices and is not "collection". Note also that losing the phone loses the book (v0.25 fork F-1).

### 3. Files leave the device only by the user's own action [sure]

- Export: `file/RecipeExport.kt` writes the file to the cache and opens the system share sheet (`Intent.ACTION_SEND` + `createChooser`). The user picks the receiving app. Under Play's definitions this is a user-started transfer, so it is not "sharing". The exported file carries no author for the user's own recipes (`RecipeFileContent.of(..., ownerNickname = null)`), and its photos carry no metadata (section 2).
- Import: `file/OpenRecipeFileActivity.kt` (VIEW / SEND of a `.recipefork`) and the in-app picker (`ActivityResultContracts.OpenDocument` in `library/LibraryScreen.kt`) read a file the user chose. Nothing is sent anywhere.

### 4. Camera, gallery and files without broad permissions [sure]

- Photos and clips are taken with the phone's own camera app through `ACTION_IMAGE_CAPTURE` / video capture, which write into the app's `CaptureProvider`. The manifest **removes** `android.permission.CAMERA` (`tools:node="remove"`) so that no library can add it back (`AndroidManifest.xml`, comment at the CAMERA line; `media/PhotoSources.kt`, `media/ClipSources.kt`).
- The gallery is read through the system photo picker (`ActivityResultContracts.PickVisualMedia`). No storage or media permission is declared.

### 5. Notifications [sure]

- `POST_NOTIFICATIONS` is requested at run time, when the user first starts a timer, and not at launch (`timer/TimerPermission.kt`, `timer/NotificationAccess.kt`). Notifications announce timers that ended. They are posted on the device and are not push messages, so no server and no Firebase are involved.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` keep timers running while the app is closed (`timer/TimerService.kt`, manifest `<service … foregroundServiceType="specialUse">` with its subtype text). `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` (restore timers after a reboot or an update) and `VIBRATE` (ringing) serve the same timers. None of these collect data. The alarms are inexact (`AlarmManager.setAndAllowWhileIdle`), so no exact-alarm permission is needed.

### 6. No analytics, crash reporting, ads or Play Integrity [sure]

- `gradle/libs.versions.toml` and `app/build.gradle.kts`: the app's runtime libraries are AndroidX (core, lifecycle, activity, navigation, Compose, Material 3, Room), Media3 (transformer, common, effect, container), Ktor client (core, android, content negotiation, JSON) and kotlinx.serialization (through `:core`). There is no Firebase, Crashlytics, Google Analytics, Play Services, AdMob, Sentry or any other analytics, crash, ads or attribution SDK.
- Play Integrity: not in the app. The only mention in the code is a comment in `server/…/attestation/AttestationService.kt` ("the Play Integrity adapter is a later task"), which is server code that is not in the app.
- No advertising ID: no `com.google.android.gms.permission.AD_ID` is declared, and no library brings it in. Answer Play Console's "Advertising ID" declaration with **No**. **[check]** Confirm in the merged manifest of the bundle (Android Studio → Merged Manifest, or `bundletool dump manifest`).

---

## Findings that get in the way of a clean answer

1. **INTERNET and ACCESS_NETWORK_STATE are declared but the serverless build never uses them** (`app/src/main/AndroidManifest.xml`, first two `<uses-permission>`). This does not change the Data safety answers, because the form asks about data, not permissions. But the app's Play details will list "full network access", and the claim "no internet needed" cannot be checked from outside. **Recommendation:** remove both from the Play (serverless) build. Then the operating system itself guarantees the app cannot connect. One way that fits the ADR's "no flavors" rule is a manifest placeholder or a small serverless-only manifest overlay chosen by `recipefork.serverless`. This is the lead's change, and it needs a test that the serverless merged manifest lacks INTERNET. Nothing in the serverless build should break, because Media3 plays only local files and the camera and picker are other apps.
2. **The Ktor HTTP client library is still inside the serverless bundle** (`app/build.gradle.kts` `implementation(libs.ktor.client.*)`). It is never constructed (finding 1 above), and R8 may or may not strip it. It is not a data-collecting SDK, so it does not change the answers. It only becomes relevant if Play's SDK index ever flags it.
3. **The Data safety answers are only true if the bundle is built serverless.** `BuildConfig.SERVERLESS` defaults to `false` (`app/build.gradle.kts`). A bundle built without `-Precipefork.serverless=true` would be the standard build, and that build talks to a server. **Check A:** confirm that `scripts/play-bundle.sh` (T-181) passes the flag. A test that the bundle's `BuildConfig.SERVERLESS` is `true` would make this impossible to get wrong.
4. **`FOREGROUND_SERVICE_SPECIAL_USE` needs its own declaration in Play Console** (App content → Foreground service permissions): what the service does and why no other type fits, often with a short video. This is not part of Data safety, but Play will block the release without it. **[check]** Play may also push back on "special use" for timers. The reason the app uses it is in the manifest's `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` text ("Cooking timers the user started on recipe steps…"), and that text can be reused in the declaration.
5. **Device-to-device transfer** (section 2): not a Data safety issue. It only matters if you want to promise that the book never leaves the phone. Users would generally welcome it.

## Uncertain, to confirm when filling the form

- Whether Play Console still skips the encryption and deletion questions after "No" to collection. It did when this was written, but this draft could not open Play Console to confirm.
- Whether Play wants an in-app link to the privacy policy for an app that handles no personal or sensitive data. The Console field is required for every app. The in-app link is required for apps that handle personal or sensitive data. Today the app has no such link (see `owner-checklist.md`, open questions).

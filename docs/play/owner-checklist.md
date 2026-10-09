# Owner's checklist — putting Recipe Fork on Google Play

Only you can do these steps: they need your Google account, your identity, your money or your decision. They are in order. Each one says what it costs and what cannot be undone. Nothing here has been done for you; v0.25 only prepares the files.

Play changes its fees, rules and form wording from time to time. Where a number matters, this list says "check the Play Console" instead of guessing it.

---

## Before you start: three decisions

1. **The name.** The spec treats "Recipe Fork" as a working title that has to be cleared before the listing exists (`spec/190-legal.md` LEG-009, `spec/open-questions.md` OQ-016). Search Google Play for "Recipe Fork" and similar names, and check the trademark register of your country. Write down the result and the date.
   *Costs:* nothing. *Irreversible:* no. The listing name can change later, but a change after people have found the app confuses them.
2. **The application id `app.recipefork.app`** (`app/build.gradle.kts`). After the first upload to Play, this id belongs to this app **forever**. It can never be changed or reused, even if you delete the app. Confirm you are happy with it before the first upload.
   *Irreversible:* **yes**.
3. **Backup before strangers?** (v0.25 fork F-1.) In the serverless build, a lost or reset phone loses the whole book. The lead recommends internal testing with the current build, and a whole-book backup as the next increment before any public release. Decide whether production waits for it.

---

## 1. Create the Play Console developer account

- Go to play.google.com/console and sign in with the Google account that will own the app. Use one you will keep for years. Moving an app to another account later is possible but slow.
- Choose **personal** or **organization**. A personal account is the natural choice for a one-person project. Organization accounts need a D-U-N-S number.
- Pay the **one-time registration fee**. *Costs:* check the current amount in the Play Console sign-up page; it is shown before you pay.
- *Irreversible:* the fee is not refunded. The account type and the developer name you register are hard to change later. Play shows the developer name publicly, and for some account types also your country or address, so check what the sign-up page says will be public.

## 2. Verify your identity

- Play Console asks for identity verification (a government ID, plus a matching name and address). It may also ask for a phone number and for proof that you have access to an Android device (through the Play Console app). Follow what the Console asks for. The requirements differ by account type and date.
- *Costs:* nothing but time. It can take from hours to several days.
- *Irreversible:* the verified legal name is tied to the account.

## 3. Create the upload key

Play App Signing keeps the key that signs the app on users' phones (the **app signing key**). Google generates and holds it, and you never see it. You sign each upload with your own **upload key**. If the upload key is lost or leaked, Play support can reset it to a new one. That takes a request and some days, but the app survives. So the upload key is important, but it is not final.

Create it once, outside the repository:

```sh
mkdir -p ~/keys && chmod 700 ~/keys
keytool -genkeypair -v \
  -keystore ~/keys/recipe-fork-upload.jks \
  -alias upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000
```

- `keytool` asks for a keystore password, then your name and similar fields (these go into the certificate and are not shown on Play), and possibly a key password. Pick long passwords and put them in your password manager at once.
- **Where to keep it:** `~/keys/` on your machine. Never inside `~/Projects/recipe-fork`, never in git, never in Telegram or e-mail.
- **Backup:** copy `recipe-fork-upload.jks` to one offline place you control, for example an encrypted USB stick or an encrypted archive in your own storage. Keep the passwords in a password manager, not next to the file.
- For a later reset, you can export the certificate any time: `keytool -export -rfc -keystore ~/keys/recipe-fork-upload.jks -alias upload -file upload_certificate.pem`. Play support asks for the certificate of a *new* key; you never send the key itself.
- *Costs:* nothing. *Irreversible:* no, the upload key can be reset through Play support.

## 4. Fill `~/Projects/.env` for the bundle script

The script reads the key's location and passwords from `~/Projects/.env`, or from environment variables. It never reads them from the repository.

### variables: see scripts/play-bundle.sh

*(T-181 is writing that script now. Copy the variable names it lists here, then fill them in your `.env`: the keystore path, the alias `upload`, and the passwords. Do not paste the passwords into this file.)*

- Keep `~/Projects/.env` readable only by you: `chmod 600 ~/Projects/.env`.

## 5. Build the bundle

```sh
cd ~/Projects/recipe-fork   # or wherever the repository lives
scripts/play-bundle.sh
```

- It writes `build/play/recipe-fork-<version>.aab` (0.25 → version code 25), built serverless and minified, and signed with your upload key. If a setting is missing, it says which one and stops.
- **Check that it is the serverless build.** The Data safety answers are only true for that build (`data-safety.md`, finding 3).
- Every later upload needs a higher version code. The script takes the version from the increment.
- *Costs:* nothing.

## 6. Create the app in Play Console

Home → Create app:
- **App name:** Recipe Fork (or the cleared name). **Default language:** English (United States). **App or game:** App. **Free or paid:** Free.
- Accept the declarations (Developer Program Policies, US export laws).
- *Irreversible:* **an app published as free can never become paid.** It can still sell things inside the app later, but the app itself stays free.

## 7. Fill "App content" (Policy → App content)

Do these before the first release; Play will not let a release go out with any of them missing.

| Item | What to answer | Where it comes from |
|---|---|---|
| **Privacy policy** | The public URL where you hosted `privacy-policy.md` (fill its placeholders first) | `privacy-policy.md` |
| **App access** | All functionality is available without special access (no login) | no accounts in serverless |
| **Ads** | No, the app contains no ads | `data-safety.md` §6 |
| **Content rating** | Fill the IARC questionnaire: category "reference / all other app types" or the closest Play offers. No violence, sex, drugs, gambling or crude language. Users do not chat or post inside the app; recipes travel only as files the user sends with other apps. Share no location. No purchases. | see open question 2 |
| **Target audience** | Not designed for children. Choose the ages the terms admit: 16–17 and 18+ (spec LEG-006/LEG-007), or 18+ only. Do **not** tick any age under 13, because that brings in the Families policy. "Could it unintentionally appeal to children?": No | `spec/190-legal.md` |
| **Data safety** | As in `data-safety.md`: collects nothing, shares nothing | `data-safety.md` |
| **Advertising ID** | No, the app does not use it | `data-safety.md` §6 |
| **Foreground service permissions** | Declare `FOREGROUND_SERVICE_SPECIAL_USE`: cooking timers the user started keep running while the app is closed and ring when they end. Play may ask for a short screen video of a timer running and ringing with the app closed | `AndroidManifest.xml` service; `data-safety.md` finding 4 |
| **Government / financial / health / news** | No, not applicable | — |

- *Costs:* nothing. *Irreversible:* no. All of these can be updated, though a change can trigger a new review.

## 8. Store listing (Grow → Store presence → Main store listing)

- **Texts:** from `listing.md`, the English name, short description and full description.
- **Translation:** add Russian (ru-RU) and paste the Russian texts from `listing.md`.
- **App icon:** `build/play/icon-512.png` (512×512).
- **Feature graphic:** from `build/play/screenshots/` (1024×500).
- **Phone screenshots:** from `build/play/screenshots/`, the English ones for en-US and the Russian ones for ru-RU (Play needs at least 2 per language, and they are 1080×1920).
- **Category:** Food & Drink. **Contact e-mail:** required and shown publicly, so a dedicated address is better.
- *Costs:* nothing. *Irreversible:* no.

## 9. Countries

The spec says the first release is **not** offered in the Russian Federation (`spec/190-legal.md` LEG-010, ADR `20260921-distribution-is-postponed-access-is-not-withheld.md`). When you set the countries for a track, leave Russia out unless you change that decision. The Russian listing is for Russian speakers in other countries.

## 10. Internal testing first

Testing → Internal testing:
- Create a testers list (e-mail addresses of a few people, up to 100), create a release, upload the `.aab`, write short release notes, and roll it out.
- The first upload asks you to accept **Play App Signing**. Accept "let Google create and manage the app signing key".
- Share the opt-in link with testers. They install the app from Play.
- **Your own phone:** the build you install by hand today is signed with the debug key. A Play install **cannot update it**, and the hand-built one cannot update the Play one either. Before installing from Play, **export every recipe you want to keep** (or wait for backup), then uninstall the hand-built app, install from Play, and import the files again.
- Play's **pre-launch report** runs the build on test devices automatically. Read it before going further.
- *Costs:* nothing. *Irreversible:* Play App Signing, once accepted for an app, cannot be turned off. This is the normal setup.

## 11. Closed testing, then production

- New **personal** developer accounts must run a **closed test** with a minimum number of testers who stay opted in for a minimum number of days before they can apply for production access. **Check the current requirement in Play Console** (Dashboard shows it) because the numbers change. Testing → Closed testing: create a track, add testers (an e-mail list or a Google Group), and upload the same or a newer bundle.
- When the requirement is met, apply for production access from the Dashboard. You answer a short questionnaire about the test.
- Production: create a release, consider a staged rollout (for example 20 % first), and send it for review. Reviews can take from hours to several days.
- *Costs:* nothing. *Irreversible:* once the app is published, the application id is used up for good, and a published app can be unpublished but not deleted from your Console. Every version code you upload is used up as well.

---

## Open questions for you

1. **Remove INTERNET and ACCESS_NETWORK_STATE from the Play build?** The serverless build never uses them (`data-safety.md`, finding 1). Removing them makes "no internet needed" checkable by anyone. The lead would make that change in a small task.
2. **Content rating — "users interact or share content".** The app has no in-app chat or feed. It only hands a recipe file to the share sheet. The draft answers "no user interaction", but if the questionnaire asks about sharing content, decide whether file export counts. When unsure, answering "yes, users can share content" costs little: it may only add a notice to the rating.
3. **Privacy policy link inside the app.** Play requires one in the Console. An in-app link is required for apps that handle personal or sensitive data, which this one does not, but it is good practice. Add a "Privacy policy" row to Settings? That would be a small task, and it needs the hosted URL first.
4. **Backup before production** (F-1, above).
5. **Target audience:** 16+ (as the spec's terms say) or 18+ only.

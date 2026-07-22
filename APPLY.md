# Liberty Chat — apply guide

This turns **`torlando-tech/columba` v2.0.9** into your **`liberty-chat`** branch
with all 15 requested changes. I can't push to your GitHub from here, so you fork +
push; everything else is done.

## 1. Fork + branch (on GitHub, once)

Fork `https://github.com/torlando-tech/columba` to your account (this produces
`https://github.com/daylight-hub/columba`).

## 2. Apply the changes (Windows PowerShell, one command per line)

```powershell
git clone https://github.com/daylight-hub/columba.git
cd columba
git checkout v2.0.9
git checkout -b liberty-chat
git apply --binary --whitespace=nowarn path\to\lcs-liberty-chat.patch
git add -A
git commit -m "Liberty Chat: LCS rebrand of Columba 2.0.9 (15 changes)"
git push -u origin liberty-chat
git tag v1.0.0
git push origin v1.0.0
```

Tagging the release commit **`v1.0.0`** is what sets the version: the in-app About
screen shows `1.0.0`, versionCode becomes `10000000`, and the APK filenames read
`1.0.0`. This is the first Liberty Chat release even though it's built on Columba 2.0.9.

`lcs-liberty-chat.patch` includes text edits, new files, deletions, and the icon
binaries. If you'd rather copy files by hand, use `liberty-chat-files.tar.gz`
instead and also delete these three upstream vector files (the patch does this for you):

```
app/src/main/res/drawable/ic_launcher_foreground.xml
app/src/main/res/drawable-v24/ic_launcher_foreground.xml
app/src/main/res/drawable-anydpi-v24/ic_launcher_foreground.xml
```

## 3. Logo (item 2) — already applied

Your real LCS badge is baked into every launcher density + the About screen, with a
brand-blue (`#013E74`) adaptive-icon background. The source is kept at
`scripts/lcs-branding/lcs-logo.png`. You only need this step if you ever change the art:

```powershell
python scripts\lcs-branding\apply-lcs-logo.py scripts\lcs-branding\lcs-logo.png --bg "#013E74"
git add -A
git commit -m "Liberty Chat: update LCS logo"
```

Use a square, transparent PNG ≥ 512×512. `--bg` is the launcher background color (optional).

## 4. Build the four APKs (item 15)

On GitHub: **Actions → Build Liberty Chat APKs → Run workflow**. It builds only the
no-Sentry / Python-backend variant and publishes exactly:

```
columba-1.0.0-official-rns-py-armeabi-v7a.apk
columba-1.0.0-official-rns-py-arm64-v8a.apk
columba-1.0.0-official-rns-py-x86_64.apk
columba-1.0.0-official-rns-py-universal.apk
```

The version comes from the tag on the release commit (`v1.0.0` above). If you run the
workflow before tagging, it defaults the filenames to `1.0.0`; you can also type a
`version_override` when you click Run. Signed APKs require the usual
`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` repo secrets;
without them you get unsigned release APKs.

---

## Item-by-item

| # | Change | Where |
|---|--------|-------|
| 1 | App → **Liberty Chat** (launcher, About, offline banner) | `app/build.gradle.kts`, `strings.xml` |
| 2 | Real LCS badge across all densities + About | `res/**`, `scripts/lcs-branding/apply-lcs-logo.py` |
| 3 | Announce icon left of the search icon on Chats | `SearchableTopAppBar.kt`, `ChatsScreen.kt` |
| 4 | RNode default freq = US **slot 51 = 914.875 MHz** | `RNodeRegionalPreset.kt` |
| 5 | TX-power hint: Heltec V4 28 dBm / RAK·LILYGO 22 dBm | `ReviewConfigStep.kt` |
| 6 | "Long Fast" badge → **LCS Recommended** | `ModemPresetStep.kt` |
| 7 | Removed *Display Logo on RNode* toggle | `ReviewConfigStep.kt` |
| 8 | TCP servers → only **public.lcs.network:4245** (+ Custom) | `TcpCommunityServer.kt` |
| 9 | Share-APK rebranded, serves `liberty-chat-<ver>.apk` | `ShareColumbaCard.kt`, `ApkSharingViewModel.kt` |
| 10 | Built-in RNode flasher entry removed from Settings | `SettingsScreen.kt` |
| 11 | Check-for-Updates → `daylight-hub/columba` | `UpdateChecker.kt` |
| 12 | Removed *Report Bug* button | `AboutCard.kt` |
| 13 | About license credits torlando-tech + LCS; MPL 2.0 kept | `AboutCard.kt`, `LICENSE.md` |
| 14 | Removed GitHub / Report Issue / About Reticulum links | `AboutCard.kt` |
| 15 | One-click Actions build of the 4 no-Sentry APKs | `.github/workflows/build-liberty-chat-apks.yml` |

## Assumptions you may want to change

- **Repo/URLs (items 9, 11, 13):** I used `github.com/daylight-hub/columba`, branch
  `liberty-chat`. If your fork name/branch differ, edit `OWNER`/`REPO` in
  `UpdateChecker.kt` and the "View License" URL in `AboutCard.kt`.
- **Item 9 mechanism:** Share-APK serves the *installed* APK over local Wi-Fi (QR →
  browser download); there is no external URL. Once your LCS build is installed it
  inherently shares the current LCS APK — I only rebranded the label + filename.
- **Item 15 naming:** your four filenames have no `-no-sentry` suffix, but upstream
  adds that suffix to no-Sentry builds. Since every APK here is no-Sentry, I dropped
  the redundant suffix so the outputs match your names exactly.
- **Item 10 scope:** I removed the flasher's Settings entry (its UI door). The flasher
  screens still exist in source and can auto-open if you plug in an RNode in bootloader
  mode via USB. Say the word if you want the flasher module ripped out of the binary
  entirely (MainActivity routes + `screens/flasher/` + `FlasherViewModel`).

## Follow-up changes (theme, Buy button, icon)

Added after the initial 15 items:

- **Liberty theme (new default).** A built-in preset matching the logo — **navy**
  primary, **gold** secondary, **silver** tertiary — with **shiny red** accents. It's
  now the default for new installs (existing installs keep whatever they've chosen; it
  also appears at the top of Settings → Theme). Files: `ui/theme/Color.kt`,
  `ui/theme/AppTheme.kt` (new `LIBERTY` preset), `ui/theme/Theme.kt`,
  `repository/SettingsRepository.kt` (new-install default).
- **Shiny red buttons.** Reusable glossy-red CTA in `ui/theme/LcsButtons.kt`
  (`ShinyRedButton`). Sprinkled on: the **Buy RNode Radios** button and the
  **Add-contact** floating button. The theme's `error` role is also LCS red.
- **Buy RNode Radios button.** In About (Settings → About), a red CTA that opens
  `https://www.lcs.network`, with the URL shown beneath it. File: `AboutCard.kt`.
- **App icon** is your real LCS badge across all densities + the About screen (from
  the previous step), so nothing else to do there.

To recolor the two red accents, edit `LcsRed` / `LcsRedLight` / `LcsRedDeep` in
`ui/theme/Color.kt`. To tune the theme, edit `libertyLightScheme` / `libertyDarkScheme`
in `ui/theme/AppTheme.kt`.

## Signing & cutting a release

Release APKs must be signed to install, and the key must stay constant across all
future updates. Generate a keystore once and add four repo secrets (Settings ->
Secrets and variables -> Actions): `KEYSTORE_FILE` (the keystore base64-encoded),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```
keytool -genkeypair -v -keystore liberty-chat-release.jks -alias liberty-chat -keyalg RSA -keysize 4096 -validity 10000
# PowerShell base64 for the KEYSTORE_FILE secret:
[Convert]::ToBase64String([IO.File]::ReadAllBytes("liberty-chat-release.jks")) | Set-Content -NoNewline keystore.b64
```

The `release.yml` workflow ("Release Liberty Chat") is now **manual-only** — tag
pushes no longer trigger it. It builds only the no-Sentry / Python-backend variant
(the four `official-rns-py` APKs), signs them, and publishes a **draft** GitHub
Release you then review and publish. Run it from Actions -> Release Liberty Chat ->
Run workflow, entering the version (e.g. `1.0.0`).

If you already pushed a stale `v1.0.0` tag from an earlier attempt, delete it first
so the release attaches to the right commit:

```
git push --delete origin v1.0.0
git tag -d v1.0.0
```

The workflow re-creates the tag at the build commit. (The "Build Liberty Chat APKs"
workflow is still available for a quick artifact-only build without publishing a
Release; it uses the same signing secrets.)

## Notes

- License stays **MPL 2.0** (that's Columba's actual license in v2.0.9 — the "AGPL"
  mention on the F-Droid forum doesn't match this source). MPL requires keeping the
  upstream notice, which the About screen and `LICENSE.md` now do alongside LCS credit.
- I couldn't run a full Android/Gradle build in this environment (no SDK), so these are
  verified source edits (brace/paren-balanced, imports checked) — do one local
  `./gradlew :app:assembleNoSentryPythonBackendDebug` before you cut a release.

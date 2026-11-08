# Releasing Cadence

How the build is signed, how to cut a release, and how to move from the old committed key to a safe one. Written for the maintainer.

## 1. Why signing matters

Android only installs an update over an app signed by the **same key**. The key is the app's identity. If you lose it, nobody can update; they must uninstall and lose their data. So:

- The key must **never** be in the repository (it is in `.gitignore`; keep it that way).
- Keep **two copies offline** (password manager and a USB stick or cloud drive you trust).
- Choose a strong password and store it with the key.

## 2. Create the key (once)

```
keytool -genkeypair -v -keystore cadence-release.jks -alias cadence \
  -keyalg RSA -keysize 4096 -validity 36500
```

Answer the questions, pick a password (use the same for the store and the key, it is simpler). `keytool` ships with the JDK; in Android Studio's folder it is at `jbr/bin/keytool`.

## 3. Give it to GitHub Actions

PowerShell, to get the key as text on your clipboard:

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("cadence-release.jks")) | Set-Clipboard
```

(macOS or Linux: `base64 -w0 cadence-release.jks | pbcopy`.)

On GitHub: **Settings, Secrets and variables, Actions, New repository secret**. Create four:

| Secret | Value |
| --- | --- |
| `CADENCE_KEYSTORE_BASE64` | the base64 text |
| `CADENCE_STORE_PASSWORD` | the keystore password |
| `CADENCE_KEY_ALIAS` | `cadence` |
| `CADENCE_KEY_PASSWORD` | the key password |

The `Release build` workflow restores the key from these, builds, and the key never touches the repo. Pull requests and forks have no access to secrets, so they build with a debug key, which is what you want.

## 4. Moving off the old committed key

Earlier versions committed `app/cadence-release.jks` with its password. Once the repository is public, that key is public, so retire it. Your data is safe because Cadence has a backup file.

1. In the **old** app: Settings, Backup and restore, **Export to a file**. Copy the file off the phone (or to Drive).
2. Remove the key from the repo and its history:
   ```
   git rm --cached app/cadence-release.jks
   git commit -m "Remove signing key from the repository"
   ```
   The old key is still in earlier commits. Either start a clean history before going public (recommended, the project has no history anyone needs):
   ```
   git checkout --orphan clean
   git add -A
   git commit -m "Cadence"
   git branch -M main
   git push --force origin main
   ```
   or rewrite history with `git filter-repo --path app/cadence-release.jks --invert-paths`. Delete old tags and releases that were built with the old key.
3. Add the new key secrets (section 3), push, and let CI build.
4. On the phone: **uninstall** the old app, install the new APK, then Settings, Backup and restore, **Import from a file**. Tasks, focus history and settings come back.

From now on, updates install over each other.

## 5. Make the repository public

1. Search the history for anything private: `git log --all --oneline -- '*.jks' '*.keystore' '*.env'` should print nothing after step 4.
2. **Settings, General, Danger zone, Change visibility, Make public.**
3. Settings, General: add a description and topics (`android`, `kotlin`, `jetpack-compose`, `todo`).
4. Settings, Branches: protect `main` (require the `CI` check and a pull request).
5. Settings, Code security: enable Dependabot alerts and private vulnerability reporting.

## 6. Cutting a release

1. Move the "Unreleased" notes in `CHANGELOG.md` under a new version heading, commit.
2. Tag and push:
   ```
   git tag v2.1.0
   git push origin v2.1.0
   ```
3. The `Release build` workflow builds a signed APK, names it `Cadence-2.1.0.apk` and publishes the release (with generated notes). Edit the notes on GitHub if you like.

Version numbers: tag `vMAJOR.MINOR.PATCH`. Major for a data-format break or removed feature, minor for features, patch for fixes. The tag becomes `versionName`. `versionCode` is the workflow run number, which only ever goes up; if you ever recreate the repository or workflow, set an offset in the workflow first or phones will refuse the "older" build.

Pushes to `main` also publish a rolling pre-release called "latest" for quick testing.

## 7. Before every release

- [ ] `gradle :app:testDebugUnitTest` passes (CI does it for you)
- [ ] Install over the previous release on a real phone: tasks survive
- [ ] A reminder fires with the screen off (Settings, Reminders, send a test, or set one for 2 minutes away)
- [ ] Export a backup, import it back
- [ ] If the database changed: upgrade from the previous version without clearing data

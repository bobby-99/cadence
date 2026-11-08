# Contributing to Cadence

Thanks for helping. Cadence is small on purpose, so the most useful contributions are fixes and polish.

## What belongs in Cadence

Cadence does one job: a calm, fast to-do list. Before building something, check it against that.

**In scope:** bugs, reliability of reminders, accessibility, performance, parser coverage (more ways of saying a date, time or repeat), translations, small quality-of-life improvements.

**Out of scope, please do not send:** projects or folders, subtasks, per-task tags, accounts, cloud sync, collaboration, ads or analytics, anything that needs the network.

For anything bigger than a fix, open an issue first so we can agree before you spend the time.

## Set up

1. JDK 17 and Android Studio (or just the Android SDK with platform 35).
2. Fork, clone, open in Android Studio, run the `app` configuration.
3. Before pushing: `gradle :app:testDebugUnitTest :app:assembleRelease`. CI runs the same on your pull request.

You do not need the release key; your build is signed with the debug key.

## Rules that keep updates safe

- **Database changes need a migration.** Bump the version in `AppDatabase`, add a `Migration` and register it. Never use destructive migration; people's tasks live here.
- **Backup format stays compatible.** Read `data/BackupManager.kt` first. New fields are optional with a default; never rename or repurpose an old one. Add a case to `BackupManagerTest`. Anything you add to `AppSettings` or `Task` must also be added to the backup unless it only makes sense on one phone.
- **Parser changes come with a test** in `TaskParserTest`, and, if it is a new phrase, an example in `AboutScreen.kt` so it shows up in "How Cadence understands you".
- **Reminders must never throw.** Anything on the reminder path (`alarm/`) catches its own failures; one failing part must not stop another.
- **Release notes:** add a line to `CHANGELOG.md` under "Unreleased".

## Code style

- Kotlin official style (`.editorconfig` is provided), 4 spaces, no wildcard imports.
- Compose: stateless composables take state and callbacks; keep logic in the ViewModel or repository.
- Icons: the project only depends on `material-icons-core`. For anything missing, draw it with `Canvas` (see `ui/Glyphs.kt`) instead of adding the extended icon set.
- UI follows the existing look: dark only, the amber accent, `C.*` colours from `ui/Theme.kt`. No hard-coded colours.
- Small, focused changes. No drive-by reformatting.

## Pull requests

- One topic per pull request, with a short description of what and why. Screenshots or a short recording for UI changes.
- Fill in the checklist in the template.
- By submitting a contribution you agree it is licensed under the project's license (PolyForm Noncommercial 1.0.0) and that you have the right to submit it.

## Reporting bugs

Use the bug template. The most useful details are your phone model, Android version, the app version (Settings, About) and exact steps. For a reminder that did not show, include what Settings, Permissions says.

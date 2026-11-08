# Cadence

A quiet, single-purpose to-do app for Android. You write a task the way you would say it, and Cadence works out the date, time, repeat and priority. It reminds you reliably, and keeps everything on your phone.

> Free for personal use. Not for sale and not for use in a business: see [License](#license).

## What it does

- **Type it, don't fill it in.** "Call mom tomorrow 6pm !!", "Gym every mon wed fri 7am", "Study for 45 min tomorrow 6pm". Live highlighting shows what was understood.
- **Reliable reminders.** Exact alarms, a catch-up for reminders missed while the phone was off, and a safety net that re-arms them every 30 minutes. A priority card over any app while you use the phone, a notification when you are not. Done, Snooze and Skip work from the lock screen.
- **Swipe.** Right: complete in one pull. Left: opens on Edit, Snooze, Focus and Delete. Undo for a few seconds.
- **Repeats with streaks**, including "after completion" repeats.
- **Calendar** with search, week and month views, and your phone calendar's events alongside. Tasks can be written to the phone calendar.
- **Focus**: Pomodoro and freestyle timers, tags, daily goal, and Insights with a heatmap.
- **Home-screen widget** that adds and completes tasks without opening the app.
- **Share to Cadence** from any app.
- **Private.** No account, no sync, no ads, no network. Backup and restore to a file or an automatic folder backup.

What it deliberately does not do: projects, subtasks, task tags, collaboration, sync. See [CONTRIBUTING](CONTRIBUTING.md#what-belongs-in-cadence).

## Install

Download the APK from the [Releases](https://github.com/bobby-99/cadence/releases) page and open it. Android will ask you to allow installs from your browser or file manager the first time. Requires Android 8.0 (API 26) or newer.

Updates install over the previous version and keep your tasks, as long as they come from the same release key.

## Build from source

You need JDK 17 and the Android SDK (platform 35). With Android Studio, just open the folder. From a terminal:

```
gradle :app:testDebugUnitTest      # unit tests
gradle :app:assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

A build without the maintainer's signing key is signed with your debug key, so it installs fine but will not update the official release. See [docs/RELEASING.md](docs/RELEASING.md).

## Project layout

```
app/src/main/java/com/bobby/cadence/
  data/     Room database, repository, preferences, backup format, phone-calendar bridge
  nlp/      The task parser (plain Kotlin, unit tested)
  alarm/    Reminders: scheduler, notifications, priority card, focus timer engine
  ui/       Jetpack Compose screens: home, editor, calendar, focus, settings, tutorial
  widget/   Home-screen widget and its add dialog
```

## Contributing

Bug reports and focused pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first; it is short. Security issues: [SECURITY.md](SECURITY.md).

## Privacy

Everything stays on the device. Details in [PRIVACY.md](PRIVACY.md).

## License

[PolyForm Noncommercial 1.0.0](LICENSE). You may use, copy and change Cadence for any noncommercial purpose, including personal use, study and hobby projects. You may not sell it or use it for commercial purposes. This is a source-available license, not an OSI-approved open-source one. Third-party components are listed in [NOTICE](NOTICE).

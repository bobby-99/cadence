# Changelog

Notable changes. Newest first. Versions follow [semantic versioning](https://semver.org): a change to the data format or a removed feature is a major version, a new feature is minor, a fix is patch.

## Unreleased

## 2.1.0

- Settings rebuilt as organised pages (Appearance, Tasks, Reminders, Phone calendar, Backup and restore, Permissions) with an About page, help, and a live "how Cadence understands you" guide.
- First-run tutorial: type a task, swipe, calendar and focus, and permissions.
- New app icon: a notepad with a checked box.
- Microphone button in the new-task sheet.
- Tighter list margins.
- Swipe right completes a task in one pull. Swipe left rests open on four boxes: Edit, Snooze, Focus, Delete.
- Backups now include focus sessions and settings; older backups still import; a backup from a newer app is refused instead of half-read.
- Reminders: shown late, once, if the phone was off at the time; a 30-minute safety net re-arms alarms; the priority card falls back to drawing directly if the system refuses its service; task notifications can no longer collide with focus notifications; background failures can no longer crash the app.
- Release signing moved out of the repository (see docs/RELEASING.md). License: PolyForm Noncommercial 1.0.0.

## 2.0.1

- Glass home-screen widget with add and complete.
- Focus tab: Pomodoro and freestyle, tags, goals, Insights heatmap; link a task by swiping left.
- Task duration, streaks for repeating tasks, change-by-typing in the editor, calendar search and a new calendar look, snooze sheet, "open the app on" setting.

## 1.x

- First releases: parser, reminders, priority card, swipe actions, calendar, backup.

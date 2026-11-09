# Cadence for Windows

A small native companion to the Android app. One window, the same list, kept in step with the phone through a shared folder (see [docs/SYNC.md](../docs/SYNC.md)).

Built with Rust and [Slint](https://slint.dev), using the software renderer: one `.exe`, no installer, no runtime, no background service, and it uses the network never (the folder tool does).

## What it does today

- Your list, sorted the way the phone sorts it. Overdue in red, completed at the bottom.
- Type a task like you'd say it: `Call mom tomorrow 6pm !!`. Dates (today, tomorrow, weekdays, next week), times (6pm, 18:00, morning, evening) and priority (`!`, `!!`, `!!!`) are understood. The phone's full parser (repeats, durations and more) is still to come.
- Click the dot to complete. Repeating tasks continue with the same rules as the phone.
- Click a task to rename it or change its priority. `×` deletes.
- Sync: after every change, and when the phone writes the file.

Not yet: reminders on the PC, calendar and focus views, swipe-like gestures, the full phrase parser.

## Build

```
cd windows
cargo test -p cadence-core        # shared logic: repeat rules, merge, file format, store
cargo run -p cadence              # the app
cargo build -p cadence --release  # windows/target/release/cadence.exe
```

The shared logic in `core/` has no UI code and runs its tests on any system. `cargo run -p cadence --example shot -- out.ppm` renders the window to an image without a display.

Your list lives in `%APPDATA%\Cadence\state.json`.

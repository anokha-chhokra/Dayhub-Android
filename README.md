# Day Hub for Android

A native, **fully offline** Android app for the things that fill a day: tasks, habits, a journal, spending, music
links and a focus timer. Everything lives on your phone, in one file. There is no account, no sync, no analytics and
no network access at all: the app does not even have the INTERNET permission, and the build fails if it ever gains one.

It is a plain Java port of the Day Hub web app, drawn by hand on the canvas in a cream-paper, pen-sketch style. No
WebView, no libraries.

> **Status:** all 30 planned features are built (see [FEATURES.md](FEATURES.md)). The code compiles, the resources and
> manifest are validated with `aapt2`, the app converts to dex, and its logic is checked against the web app's own code
> (see [Checking it](#checking-it)). It has **not yet been run on a phone or emulator**, and a Gradle build has not been
> run on the machine it was written on, so expect a first-run shakedown. See [Known limits](#known-limits).

## What it does

**Home** puts the day on one screen: a greeting with your points and streak, what needs you now (overdue tasks, habits
planned for now, a budget overrun, an empty journal), today's tasks, a one-tap mood and quick note, this month's
spending against your budget, a music link, and the focus timer.

| | |
| --- | --- |
| **Tasks** | Due dates, a star for priority, done, edit, delete with Undo. |
| **Habits** | Three kinds: check it off, reach a daily goal, or stay under a limit. A week strip, reminders, points, streaks and badges. |
| **Journal** | A day timeline, a calendar and an entries list, with mood and tags. Money you mention ("spent 200 on lunch", "Rs 350") is spotted and offered as an expense. |
| **Spend** | Expenses by category and month, a monthly budget, and a pace check ("on track", "watch it", "over"). |
| **Music** | Saved YouTube links that open in the YouTube app or your browser. There is no in-app player. |
| **Settings** | Your name and currency, the daily journal reminder, backup, focus mode, notifications. |

### Focus mode

An old pocket stopwatch drawn on the canvas. Its dial is re-engraved for the length you choose (a 25 minute focus gets
numerals every 5 minutes; two hours gets numerals every 20), with presets, a typed-minutes box and ±5 buttons.

- **The timer survives the app closing.** It is saved on the phone and an alarm is set for its end, which posts a
  "Focus complete" notification. A running timer also shows a countdown notification.
- **The lock.** While a timer runs, an accessibility service keeps the phone on Day Hub, your messages app and
  WhatsApp. Anything else is sent straight back. Calls and emergency alerts always come through. The service sees only
  *which app came to the front*; it cannot read your screen.
- **A cover for stubborn phones.** If a phone will not let Day Hub jump back, a full-screen "Focus mode is on" page
  covers the other app, with a Back to Day Hub button.
- **Ending early is a decision, not a slip:** hold the button for 8 seconds.

It is a soft lock, built to protect a decision you made earlier. You can always turn the service off in Android's
settings, and the timer always ends by the clock (8 hours at most).

### Widgets

Six home-screen widgets: **Home**, **Tasks**, **Habits**, **Spend**, **Quick add** and **Focus**. They read Day Hub's
own saved file every time they are drawn, so they are never out of date. Taps on them apply instantly: tick a task, step
a habit up or down, tap a mood face, start a 15, 25 or 45 minute focus. Add buttons open the right sheet in the app.

### Reminders

The daily journal reminder and each habit's reminder time arrive as notifications even when the app is closed (if you
turn on "Also show system notifications"), and as an on-screen message while it is open. They are set again after a
restart or app update, when the date, time or time zone changes, and at midnight.

### Backup

You pick **one backup file** once, through Android's file picker. Day Hub overwrites it after your changes (and when you
leave the app), so only the latest exists. Each backup is read back to check it was written properly, and an empty Day Hub
will never overwrite a backup that has data. Restore from the same file, or from any Day Hub backup file. Export is also
available as CSV (expenses) and Markdown (journal).

## Privacy and permissions

Your data never leaves the phone unless *you* export or back it up to a place you choose.

| Permission | Why | When |
| --- | --- | --- |
| `POST_NOTIFICATIONS` | Reminders, the focus countdown and "focus complete". | Asked the first time it is needed. Everything works without it. |
| `VIBRATE` | A short buzz when a focus session ends. | Normal permission. |
| `RECEIVE_BOOT_COMPLETED` | Set reminders and the focus alarm again after a restart. | Normal permission. |
| `SCHEDULE_EXACT_ALARM` | Reminders on the minute. | Without it they may come a few minutes late. |
| `SYSTEM_ALERT_WINDOW` | Optional: lets focus mode bring Day Hub back to the front fastest. | Only if you allow "Display over other apps" in Settings. |
| Accessibility service | Focus mode's lock (see above). | Only if you turn on *Day Hub focus guard*. |

There is no `INTERNET`, no network-state, no Wi-Fi and no location permission.

## Setting it up on a phone

1. Install and open the app. A short first-run wizard asks your name, currency, an optional monthly budget, which habits
   to start with and an optional music link. Everything can be skipped and changed later.
2. **Backup:** Settings → *Backup file* → choose the backup file once. (*Your data* in the same screen has one-off
   backup, restore and export buttons.)
3. **Reminders:** Settings → Reminders → turn on "Also show system notifications" and allow notifications.
4. **Focus mode:** on the Home focus tile, tap *Open accessibility settings* and turn on **Day Hub focus guard**. On
   Android 13 and newer, an app installed from a file is blocked here until you allow it: Settings → Apps → Day Hub →
   ⋮ menu → *Allow restricted settings*. Optionally also allow *Display over other apps* from Settings → Focus mode.
5. **Widgets:** long-press the home screen → Widgets → Day Hub.

## Building

Requirements: Android Studio (or the command line tools), JDK 17, and the Android SDK with platform 35.

| | |
| --- | --- |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.14.3 (wrapper) |
| `minSdk` / `targetSdk` / `compileSdk` | 26 (Android 8.0) / 35 / 35 |
| Language | Java 17, framework APIs only |
| Dependencies | none |

```bash
./gradlew assembleDebug       # build  ->  app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # build and install on a connected phone or emulator
```

Create `local.properties` with `sdk.dir=/path/to/Android/sdk` if Android Studio has not already done so.

Every build first runs `checkNoNetwork`, which fails if any network or WebView code appears (see below).

## Checking it

Two kinds of check keep the app honest. Full detail is in [verify/README.md](verify/README.md).

**It stays offline.** The `checkNoNetwork` Gradle task fails the build if the app gains a network permission, any
WebView code, network connections or sockets, a networking library or dependency, or a bundled library. The rules are in
[`buildSrc/.../NoNetworkGuard.java`](buildSrc/src/main/java/app/dayhub/guard/NoNetworkGuard.java) and have their own test.

**It matches the web app.** `verify/run.sh` runs the web app's own code on generated data (its real backend, stats and
badges, expense detection, insights, dashboard, exports, stopwatch maths, focus rules, widget logic and reminder
check), replays the same data through this app's classes, and fails on any difference.

```bash
verify/run.sh               # everything (a few minutes; needs Node.js, the Android SDK and the web app, see verify/README.md)
verify/run.sh --guard-only  # just the offline guard, seconds, needs only a JDK
./gradlew verifyAgainstWebApp
```

Last full run: 60 checks, all passing.

## How it is built

```
app/src/main/java/app/dayhub/
  data/       the engine: pure Java, no Android code (tasks, habits, journal, spending, music, settings,
              validation, points and streaks, expense detection, insights, timeline, export, backup rules,
              the stopwatch maths, focus and reminder rules, widget rows and taps)
  widgets/    the six home-screen widgets and their list service
  *.java      screens, hand-drawn views, and the Android glue (storage, alarms, notifications,
              the accessibility service, receivers)
app/src/main/res/   layouts and drawables for the widgets, strings, colours, the launcher icon
buildSrc/           the offline guard (compiled by Gradle, used by the build)
verify/             the checks against the web app, and their runner
FEATURES.md         the 30-item checklist the app was built from
```

- **One activity, no WebView.** Screens are plain views drawn by hand (`HandDrawnCard`, `HandDrawnButton`, the stopwatch,
  the bottom bar) with bottom sheets, undo toasts and pickers of its own.
- **One data file.** Everything is a single JSON document, `dayhub.json`, in the app's private storage, saved
  atomically (write a temporary file, then rename). A failed save rolls the change back. If the file is ever
  unreadable, a recovery screen offers to restore from a backup or start fresh, and never overwrites the damaged file.
- **The engine matches the web app.** The same JSON document format, the same validation rules and error messages, the
  same money handling (minor units, so no rounding drift) and the same dates (`YYYY-MM-DD`) and times (`HH:MM`). A
  Day Hub backup from the web app opens here and the other way round.
- **Each feature is its own set of classes**, kept out of `MainActivity`, which only wires them together.

## Known limits

- **Not yet run on a device.** Everything that draws or needs Android itself (the hand-drawn views, the stopwatch
  animation, the accessibility service and cover, alarms, notifications, widgets on a real launcher) is compiled and
  its resources validated, but needs a first run on a phone. Worth checking first: the bottom bar with six tabs, the
  bottom sheets with the keyboard, the stopwatch drawing, the focus lock and cover, the notification and alarm timing,
  and how the six widgets look on your launcher.
- Focus mode relies on a service Android lets you switch off, and on phone makers' battery savers, which can stop
  accessibility services. Test it on your own phone.
- Portrait only. No tablet layout. No in-app music player. No cloud sync, by design.
- Reminders are exact only where Android allows exact alarms; otherwise they may arrive a few minutes late.
- A few deliberate differences from the web app, listed in [verify/README.md](verify/README.md): the journal reminder is
  skipped once you have written today, a widget mood tap corrects an earlier check-in by the Home screen's rule, and a
  focus label is never cut in the middle of an emoji.

## Contributing

Add a feature as new classes, keep `MainActivity` small, and tick it off in [FEATURES.md](FEATURES.md). The web app is the
source of truth for behaviour: if the two disagree, run `verify/run.sh` and fix the Java. Do not add a dependency, a
network call or a WebView; the build will stop you.

## License

No license has been chosen yet, so all rights are reserved by default. Add a `LICENSE` file to change that.

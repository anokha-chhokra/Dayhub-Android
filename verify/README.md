# Checking Day Hub

Two kinds of check keep Day Hub honest. Neither needs a phone.

## 1. It stays offline (runs on every build)

Day Hub never touches the network. It has no INTERNET permission, no WebView, and no networking code or library.
`checkNoNetwork` (in `app/build.gradle`) runs before every build and **fails the build** if any of these appear:

| It looks for | Examples |
| --- | --- |
| a network permission or setting in the manifest | `INTERNET`, `ACCESS_NETWORK_STATE`, Wi-Fi permissions, cleartext traffic, a network security config |
| WebView code | `WebView`, `WebViewClient`, `WebSettings`, `@JavascriptInterface`, `CookieManager`, a `<WebView>` in a layout |
| network connections and sockets | `HttpURLConnection`, `new URL(...)`, `openConnection()`, `java.net.http`, `Socket`, `InetAddress`, `javax.net.ssl` |
| networking libraries | OkHttp, Retrofit, Volley, Ktor, Apache HTTP, Firebase, analytics and similar, as imports or as dependencies |
| Android network APIs | `ConnectivityManager`, `DownloadManager`, `WifiManager`, `VpnService` |
| bundled libraries | anything in `app/libs`, `fileTree(...)` |

Comments are ignored, so a comment may say "no WebView". Test code is not scanned. The rules are in
`buildSrc/src/main/java/app/dayhub/guard/NoNetworkGuard.java`, plain Java that Gradle compiles and that is also tested
on its own (`verify/java/NoNetworkGuardTest.java` plants each kind of violation in a sample project and checks it is
caught, and that look-alikes such as `java.net.URI` are not).

To run just the guard: `verify/run.sh --guard-only` (needs only a JDK).

## 2. It matches the web app (run when you want to be sure)

`verify/run.sh` runs the **web app's own code** on generated data and replays the same data through Day Hub's classes,
comparing everything a person could notice. A difference fails the run.

```
verify/run.sh        (or:  ./gradlew verifyAgainstWebApp)
```

It needs a JDK, Node.js 18+, the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`), an `org.json` jar for
the JVM (`JSON_JAR`, found in `~/.m2` or `~/.gradle` if not set) and the web app (`WEB_APP`, or a folder called
`everything-app` next to this project or in `~/Downloads`). On Windows use Git Bash. Everything it writes goes to
`build/verify` (logs in `build/verify/logs`). It takes a few minutes.

What it does, in order:

1. The offline guard and its own test.
2. Compiles the Android resources and manifest with `aapt2` (this checks every XML file and gives the real `R.java`).
3. Compiles the whole app against the Android SDK, and converts it to dex with `d8`.
4. Checks the Gradle scripts parse (using the Groovy inside the Gradle distribution, if it is installed).
5. For each area, a generator in `verify/gen` runs the web app and writes its answers; a Java check in `verify/java`
   replays the same inputs and compares:

| Area | Web app code it runs | Check |
| --- | --- | --- |
| Tasks, habits, journal, spending, music, settings, setup | the real backend, hundreds of random operations each (accepted and refused) | `TasksParity`, `HabitsParity`, `JournalParity`, `SpendParity`, `MusicParity`, `SettingsParity`, `SetupParity` |
| Points, streaks, badges | `computeStats`, `earnedBadgeIds` | `StatsParity` |
| Expense detection, insights, dashboard, home | `detectExpenses`, `spendPace`, `buildAttention`, the dashboard route | `DetectParity`, `InsightsParity`, `DashParity`, `HomeParity` |
| Timeline, calendar | the timeline routes | `TimelineParity` + `cmptimeline.mjs` |
| Exports (backup JSON, CSV, Markdown), blank data | the export routes, byte for byte | `ExportParity`, `BlankParity` |
| The stopwatch dial | `dialPlan`, `wedgePath`, `handDegrees`, labels | `WatchParity` |
| The focus timer, lock and hold | `FocusSession`, `FocusPolicy`, the clock, the 8 seconds | `FocusParity`, `PolicyParity`, `HoldTest` |
| Widgets | `buildSnapshot`, `SnapshotData`, `WidgetRows` | `WidgetParity` |
| Widget taps | `applyWidgetAction` on the real backend | `TapPlan`, `applytaps.mjs`, `TapParity` |
| Reminders | the page's own reminder `check()` | `ReminderParity` |
| Saving, restoring, backing up | (self-contained) | `EngineTest`, `RestoreCommitTest`, `WizardFlowTest`, `VerifyTest`, `AutoBackupTest` |

What this does not cover: anything that draws on a screen or needs Android itself to run (the hand-drawn views, the
stopwatch drawing and animation, the accessibility service, alarms, notifications, widgets on a launcher). Those are
compiled and their resources checked, but need a phone.

## Known, deliberate differences from the web app

- The journal reminder is not announced if something is already written today (the web app announces it anyway).
- A mood tapped on a widget corrects an earlier mood-only check-in made up to 30 minutes before it, counted from when
  that check-in was made, as on the Home screen (the web queue's 30 minutes slide with each correction).
- A focus label is never cut in the middle of an emoji.

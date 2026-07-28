# SocialLimiter

A personal-use Android app (Kotlin) that enforces a **self-imposed time limit +
cooldown** on apps you choose, plus a **shared daily budget** and **scheduled
blackout windows**. Open a monitored app → it asks *"How many minutes?"* → when
the timer runs out you're sent home and the app is **locked for a cooldown
period** (15 min by default). Across the day, all monitored apps share a single
time budget (2 h by default, resets at midnight), and you can define recurring
windows (e.g. weekdays 10 AM–8 PM) during which every monitored app is blocked.
Fully local/offline — no backend, no analytics, no network calls.

> Not intended for the Play Store. Build the debug APK and sideload it.

---

## Features

- **User-managed monitored list** stored in a Room DB (`MonitoredApp`). Any
  installed app can be toggled on/off from the settings screen — there is **no
  hardcoded package list** driving behavior, so adding more apps later needs no
  code change. (Facebook / Instagram / Reddit / X / TikTok are pre-seeded on
  first launch *only if installed*, purely as a convenience.)
- **Foreground detection** via an `AccessibilityService`
  (`TYPE_WINDOW_STATE_CHANGED`) **plus a UsageStats poll every 2s** as a
  defensive fallback, in case an accessibility event is missed or the service is
  killed.
- **"How many minutes?" overlay** — a full-screen `TYPE_APPLICATION_OVERLAY`
  `WindowManager` view (not an Activity) with a number field + OK. It blocks the
  app underneath and cannot be dismissed with Back.
- **Foreground countdown service** with a persistent notification showing the
  remaining time, so it survives Doze / background limits.
- **Time's up → cooldown**: sends you to the home screen, records a cooldown in
  Room (`CooldownState`), and shows a **"locked" overlay** with a live countdown
  if you try to reopen the app.
- **Shared daily budget**: all monitored apps draw from one daily time pool
  (default 120 min, configurable). Time is accrued only while the app is actually
  in the foreground — the countdown **pauses when you leave the app** and resumes
  when you return, so backgrounding doesn't burn budget. When the budget is
  exhausted every monitored app is blocked
  until **local midnight**, when it resets automatically (`DailyUsage` keyed by
  date). The "How many minutes?" prompt is capped to the time left today.
- **Scheduled blackout windows**: define recurring windows by days-of-week +
  start/end time (`Schedule` table). During an active window every monitored app
  is blocked with a "Scheduled break" overlay. Windows whose end time is at or
  before the start time are treated as crossing midnight.
- **Persistent across kills & reboots**: active sessions and cooldowns live in
  Room and are restored on `BOOT_COMPLETED`.
- **Pause / resume from a persistent notification**: an ongoing notification
  exposes a one-tap **Pause / Resume** action, reachable from any screen without
  opening the app. Pausing stops all prompts/blocks and freezes any running
  countdown; **nothing configured is lost** — monitored apps, schedules, budget,
  cooldowns and the in-progress session all resume exactly as they were on
  resume. (This is a *soft* pause: the accessibility service stays enabled, so
  apps that reject any enabled accessibility service are unaffected by it.) The
  same toggle is mirrored by a switch at the top of the settings screen.
- **Usage dashboard**: a second tab charts your behaviour, all stored locally in
  a `UsageEvent` table. Shows today's opens / blocked attempts / time used,
  a 7-day **time-per-day** bar chart and an **opens-vs-blocked** trend chart, and
  a per-app breakdown of opens, prompts you backed out of, and blocked attempts
  split by reason (schedule / cooldown / budget). Events (and daily totals) are
  retained for ~90 days and can be cleared from the dashboard.
- **Settings UI** (Jetpack Compose): pause/resume switch, permission status +
  one-tap grant buttons, daily budget + time-left-today, global default cooldown,
  per-app cooldown override, scheduled-break editor (day chips + time pickers),
  per-app live status (idle / active session / in cooldown), and a searchable
  installed-apps list to add/remove monitored apps.

## Requirements

- Android Studio (Koala or newer) / command-line Android SDK
- JDK 17
- `compileSdk 34`, `minSdk 26`, `targetSdk 34`

---

## Build the debug APK

### From Android Studio
Open the project folder, let Gradle sync, then **Build ▸ Build Bundle(s) / APK(s)
▸ Build APK(s)**.

### From the command line
```bash
# point Gradle at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug
```
The APK is written to:
```
app/build/outputs/apk/debug/app-debug.apk
```

## Install it on your phone

**Via adb** (USB debugging enabled in Developer Options):
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Or copy the APK** to the phone and tap it (allow "install from unknown
sources" for your file manager when prompted).

---

## Grant permissions (required — do this once)

Because the app is sideloaded, every special permission must be granted by hand.
Open SocialLimiter; the home screen shows each permission with a **grant button**
that jumps to the right settings page. What each one is and where to find it:

1. **Accessibility Service** — how it detects which app is in the foreground.
   - `Settings ▸ Accessibility ▸ SocialLimiter ▸ turn On` (may be under
     *Installed apps* / *Downloaded apps*).
   - Android will warn that the service can observe your screen — that's
     expected; it only reads the foreground package name and never leaves the
     device.

2. **Display over other apps** (a.k.a. *Appear on top* / `SYSTEM_ALERT_WINDOW`) —
   lets the "how many minutes?" and "locked" overlays render on top.
   - `Settings ▸ Apps ▸ Special app access ▸ Display over other apps ▸
     SocialLimiter ▸ Allow`.

3. **Usage access** (fallback detector) — powers the every-few-seconds backup
   check.
   - `Settings ▸ Apps ▸ Special app access ▸ Usage access ▸ SocialLimiter ▸
     Allow`.

4. **Notifications** (Android 13+) — for the countdown notification.
   - Tap **Allow** on the in-app prompt, or
     `Settings ▸ Apps ▸ SocialLimiter ▸ Notifications`.

5. **Ignore battery optimization** (recommended for reliability) — stops the
   system from killing the countdown/monitor.
   - Tap **Fix** in-app, then **Allow**, or
     `Settings ▸ Apps ▸ SocialLimiter ▸ Battery ▸ Unrestricted`.

Exact menu names vary by manufacturer (Samsung/Xiaomi/etc.); search Settings for
"Accessibility", "over other apps", "usage access", and "battery" if a path
differs.

---

## How it works

When a monitored app comes to the foreground, checks apply in this order:

1. **Scheduled break active?** → bounced home + "Scheduled break" overlay until
   the window ends.
2. **In cooldown?** → bounced home + "locked, until HH:MM" overlay.
3. **Active session running?** → allowed until it expires.
4. **Daily budget used up?** → bounced home + "Daily limit reached" overlay until
   midnight.
5. **Otherwise** → the **"How many minutes?"** overlay appears (capped to the
   time left in today's budget); enter minutes, tap OK.

Then a **foreground countdown** starts (persistent notification) and in-app time
is subtracted from the daily budget. Leaving the app **pauses** the countdown
(and budget accrual); reopening it resumes from the remaining time. On zero →
home screen + a **cooldown** window opens (default 15 min, configurable globally
or per app). If the daily budget runs out mid-session, the session ends early and
everything is blocked until midnight.

### Project layout
```
app/src/main/java/com/sociallimiter/app/
├─ SocialLimiterApp.kt        # first-run seed + notification channels
├─ data/                      # Room entities, DAOs, DB, repository, settings
├─ service/
│  ├─ AppMonitorService.kt    # AccessibilityService + UsageStats poll fallback
│  ├─ LimiterEngine.kt        # core enforce/prompt/cooldown decision logic
│  └─ CountdownService.kt     # foreground countdown + notification
├─ overlay/                   # WindowManager overlays (prompt + locked)
├─ receiver/BootReceiver.kt   # restore sessions/cooldowns after reboot
├─ ui/                        # Compose settings screen + ViewModel
└─ util/                      # permissions, package listing, notifications
```

## Adding more apps
Just open SocialLimiter, scroll to **Add apps**, search, and toggle any app on.
It's added to the Room-backed monitored list immediately — no rebuild needed.

## Notes & limitations
- Overlays require the app's own process to be alive. The foreground service +
  battery-optimization exemption + accessibility service are the defenses; on
  aggressive OEM skins you may need to also "lock" the app in recents.
- This is a self-discipline tool, not a security boundary — a determined user
  with device settings access can disable it.
- Daily-budget time is accrued only while the monitored app is in the foreground:
  the countdown pauses when you leave the app and resumes when you return, so
  backgrounding it does not burn budget. Foreground detection uses accessibility
  window-state events plus a UsageStats event poll (latest `MOVE_TO_FOREGROUND`)
  as a backup, so it reflects the app that's actually on screen.
- The daily budget resets at **local** midnight; changing the device time zone
  shifts the reset accordingly.

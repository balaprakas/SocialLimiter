---
name: Testing SocialLimiter on an Android emulator
description: How to build, install, permission, and runtime-test the SocialLimiter Android app (budget/cooldown/schedule enforcement) on an emulator.
---

# Testing SocialLimiter (Android)

Offline Kotlin/Compose app that enforces self-imposed time limits via an AccessibilityService + UsageStats poll + WindowManager overlays. No login/secrets.

## Build & install
```bash
cd /home/ubuntu/repos/SocialLimiter
export ANDROID_HOME=/home/ubuntu/android-sdk ANDROID_SDK_ROOT=/home/ubuntu/android-sdk
/home/ubuntu/gradle-8.9/bin/gradle :app:assembleDebug
ADB=/home/ubuntu/android-sdk/platform-tools/adb   # adb is not on PATH
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

## Emulator gotchas
- x86_64 emulator needs KVM: `sudo gpasswd -a $USER kvm && sudo chmod 666 /dev/kvm`.
- AVD: `avdmanager create avd -n testavd -k "system-images;android-34;google_apis;x86_64" -d pixel_5`.
- Screen is 1080x2340; `uiautomator dump` bounds are in real device px (use them directly for `input tap`).

## Grant all permissions via adb (avoids on-device Settings toggling)
```bash
P=com.sociallimiter.app
$ADB shell appops set $P SYSTEM_ALERT_WINDOW allow
$ADB shell appops set $P GET_USAGE_STATS allow
$ADB shell pm grant $P android.permission.POST_NOTIFICATIONS
$ADB shell settings put secure enabled_accessibility_services $P/com.sociallimiter.app.service.AppMonitorService
$ADB shell settings put secure accessibility_enabled 1
```

## Testing tips
- Use any installed app as the monitored target (real social apps aren't installed). **Files** = `com.google.android.documentsui` works well.
- Launch the monitored app with `monkey -p <pkg> -c android.intent.category.LAUNCHER 1` to trigger the overlay.
- Overlay elements have resource-ids: `promptAppName`, `promptBudget`, `promptMinutes`, `promptError`, `promptOk` — read them via `uiautomator dump`.
- Verify the countdown via `dumpsys notification --noredact | grep -i remaining`.
- **Budget is real wall-clock time.** To exercise exhaustion fast: start a session, then lower the daily budget below already-accrued usage → session ends mid-flight and "Daily limit reached" shows.
- To test the prompt cap without a full session, set budget so only ~2 min remain, open the app, enter a value > remaining → expect "Enter 1-N minutes".
- Editing EditText fields: `input tap <field>`, `keyevent KEYCODE_MOVE_END`, several `KEYCODE_DEL`, then `input text "<val>"`. Do NOT press KEYCODE_BACK to hide the keyboard — it navigates away; tap a neutral area or the Save button (which sits above the IME) instead.
- Midnight budget reset and cross-midnight (end<=start) schedules are hard to exercise in a short run; note them as untested.

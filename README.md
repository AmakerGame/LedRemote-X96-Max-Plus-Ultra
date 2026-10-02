# LED Control

Android TV app (API 30+, Android 11+) for controlling the hardware LED indicators and the
VFD display of boxes based on the `meson-vfd` (Amlogic) driver, e.g.
`/sys/devices/platform/meson-vfd/attr/*`.

## Target device: X96 Max Plus Ultra (Amlogic S905X4), SlimBoxTV

This project targets the **X96 Max Plus Ultra** on the **Amlogic S905X4** chip running
the **SlimBoxTV** firmware:

- The `meson-vfd` driver and the `/sys/devices/platform/meson-vfd/attr/*` path are standard
  for this SoC family (S905X4/S905X3/S905X2 clones with a front-panel VFD display) — on
  SlimBoxTV the attributes are confirmed present and writable with root.
- SlimBoxTV ships with **Magisk** out of the box, so `su` is available immediately — just
  grant the app superuser access the first time the root prompt appears.
- SlimBoxTV resets custom sysfs values after every reboot (same as stock firmware on this
  chip). That's exactly why the app has a `BootReceiver` + `MonitorService` that reapply the
  LED/display configuration right after `BOOT_COMPLETED`.
- If your SlimBoxTV build exposes `meson-vfd` under a different path, check manually with
  `adb shell ls /sys/devices/platform/meson-vfd/attr/` and adjust `VFD_BASE` in
  `LedModels.kt` if needed — the rest of the code (UI, modes, MonitorService) stays the same.
- Install with `adb install app-debug.apk` (from the build artifacts) — on SlimBoxTV,
  allowing installs from unknown sources is usually enough; no separate priv-app push needed.

## Recent fixes and changes

- **Root is no longer spammed on every tick.** `MonitorService` used to run an `su` check
  on every monitoring cycle (every 5s), which could make Magisk/SuperSU keep re-prompting.
  Now `su` only runs in two cases: when the service starts after a real device reboot, and
  when a LED's state actually needs to change (a real write into sysfs). The condition
  check loop itself (App Active, time range, etc.) never touches root.
- **Fixed state loss after a reboot.** The "last applied" cache used to survive a reboot,
  while sysfs resets to its defaults — so LEDs could silently stay in the post-boot default
  state instead of the user's configuration. `BootReceiver` now marks a real reboot, and the
  service forcibly reapplies every saved setting, ignoring the stale cache.
- **Fixed a misleading "Enabled" display status.** Pressing Enable right after Disable used
  to immediately show "Display: enabled", even though the screen physically stays off until
  an actual reboot happens. The status now correctly shows "enabled, awaiting restart" until
  a real reboot clears the pending flag — the flag is also no longer cleared just by
  reopening the app (that used to happen on every service restart, not just a real reboot).
- **Script mode simplified**: every LED now has its own independent script (a single shell
  command field), each pre-filled with its own starter template relevant to that LED (e.g.
  a ping check for the Wi-Fi LED, an SD-card mount check for the Card LED). Exit code `0`
  means "turn the LED on", anything else means "off". Fully editable/replaceable.
- **Added an "About" screen**: GitHub link
  (https://github.com/AmakerGame/LedRemote-X96-Max-Plus-Ultra), build target
  (X96 Max Plus Ultra), and version read directly from the APK (`PackageInfo`) rather than
  hardcoded in code.
- Minor UI polish: card-style list items with a colored status dot per entry.
- Minor fix: the status dot drawable is now `mutate()`d before tinting, so recycled list
  rows in the RecyclerView can't end up sharing (and incorrectly repainting) the same
  drawable instance.

## What it does

- On startup, checks for root (`su`). No root → "Grant root access!" screen with a retry
  button.
- Main list: the **Display (VFD)** item on its own, plus 7 secondary LEDs
  (`greenled, wlanled, ethernetled, usbled, cardled, appled, agingled`).
- **Display**: Enable/Disable only.
  - Disable → immediately runs `echo tcd1 > .../attr/led` (confirmed to work instantly).
  - Enable after Disable → the display does not physically turn back on by itself, so a
    Toast shows "Restart the device"; the desired state is saved, and the status is shown
    as "enabled, awaiting restart" until the next real reboot.
  - If the display is disabled and the user tries to configure any other LED, a dialog
    warns that changes won't be visible (the whole VFD is off), offering "Go to display" /
    "Configure anyway".
- **Secondary LEDs**, each supporting these modes:
  - Enable / Disable (static)
  - If app is active (multi-select of apps, checked via UsageStats — actual foreground, not
    background)
  - Script: its own independent shell script per LED (e.g. `ping -c1 -W1 8.8.8.8` to check
    for internet)
  - Time (from-to)
- **MonitorService** (foreground service) checks the dynamic conditions every 5s and writes
  to sysfs only when the state actually changes (to avoid spamming `su`).
- **BootReceiver**: after `BOOT_COMPLETED`, starts the service, which reconciles the saved
  desired state of the display/LEDs against the actual post-reboot state (sysfs resets to
  defaults on every reboot) and reapplies it.
- Localization: **uk** (default) / **ru** / **en**, switchable inside the app, independent
  of the box's system language.

## Build

The project builds via GitHub Actions (`.github/workflows/build.yml`): push to `main` or a
PR produces a debug APK as a build artifact; a `release` event produces a signed release
APK attached to the release.

Before a release build, add these to the repository's Settings → Secrets:

- `KEYSTORE_BASE64` — your `.jks`/`.keystore`, base64-encoded (`certutil -encode` /
  `base64 release.keystore`)
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The debug build (`assembleDebug`) does not need any secrets.

## Permissions to grant manually on the device

The "If app is active" mode needs Usage Access — a button on the main screen opens the
relevant settings screen directly (`Settings → Apps → Special access → Usage access`).

Here is a high-quality English translation:

# LED Control / LED Management

Android TV app (API 30+, Android 11+) for controlling hardware LED indicators and the VFD display of boxes based on `meson-vfd` (Amlogic), e.g. `/sys/devices/platform/meson-vfd/attr/*`.

## Target device: X96 Max Plus Ultra (Amlogic S905X4), SlimBoxTV

The project is aimed specifically at the **X96 Max Plus Ultra** on the **Amlogic S905X4** chip with **SlimBoxTV** firmware:

- The `meson-vfd` driver and path `/sys/devices/platform/meson-vfd/attr/*` are standard for this SoC family (S905X4/S905X3/S905X2 clones with a VFD display on the front panel); on SlimBoxTV it has been verified that the attributes are present and writable via root.
- SlimBoxTV already comes with **Magisk**, so `su` is immediately available — just add the app to the allowed list in Magisk (Superuser) on first launch when the root request appears.
- SlimBoxTV automatically resets custom sysfs values after a reboot (just like stock firmware on this chip) — that is exactly why the app has `BootReceiver` + `MonitorService`, which reconfigure the LED/display immediately after `BOOT_COMPLETED`.
- If on your SlimBoxTV build the `meson-vfd` path differs (different `attr` directory name, different symlink), check manually with `adb shell ls /sys/devices/platform/meson-vfd/attr/` and, if needed, adjust `VFD_BASE` in `LedModels.kt` — the rest of the code (UI, modes, MonitorService) will not change.
- Installation: `adb install app-debug.apk` (APK from build artifacts) — on SlimBoxTV it is usually enough to allow installation from unknown sources; a separate priv-app push is not required.

## What it does

- On startup, it checks root (`su`). No root → “Grant root!” screen with a retry button.
- Main list: **Display (VFD)** separately + 7 secondary LEDs (`greenled, wlanled, ethernetled, usbled, cardled, appled, agingled`).
- **Display**: only Enable/Disable.
  - Disable → immediately `echo tcd1 > .../attr/led` (verified, works instantly).
  - Enable after Disable → the display does not physically turn itself on, so a Toast is shown: “Reboot the set-top box”; the desired state is saved, and on the next boot MonitorService does not touch anything (the display turns on by default by itself).
  - If the display is disabled and the user tries to configure any other LED — the dialog warns that the changes will not be visible (the entire VFD is disabled), with the choices “Go to display” / “Configure anyway”.
- **Secondary LEDs**, each supports the following modes:
  - Enable / Disable (static)
  - If app is active (multi-select apps, checked via UsageStats — foreground specifically, not background)
  - Script: Internet / SD card / Apps (active main launcher)
  - Time (from–to)
- **MonitorService** (foreground service) every 5 s checks dynamic conditions and writes to sysfs only when the state changes (to avoid spamming `su`).
- **BootReceiver**: after `BOOT_COMPLETED`, starts the service, which compares the saved desired state of the display/LED with the actual one (after reboot everything in sysfs is reset) and reapplies it.
- Localization: **uk** (default) / **ru** / **en**, switchable directly in the app, without changing the set-top box’s system language.

## Build

The project is built via GitHub Actions (`.github/workflows/build.yml`, the same as your `build.yml`): push to `main` or PR → debug APK in artifacts; `release` → signed release APK attached to the release.

Before a release build, add the following to the repository’s Settings → Secrets:

- `KEYSTORE_BASE64` — your `.jks`/`.keystore`, base64-encoded (`certutil -encode` / `base64 release.keystore`)
- `STORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The debug build (`assembleDebug`) does not require secrets.

## Permissions required manually on the device

For the “If app is active” and “Apps” modes, access to usage statistics is required — the button on the main screen opens the required settings section (`Settings → Apps → Special access → Usage access`).

# DustStep

DustStep is a tiny dark-theme Android step counter for local personal use.

Design constraints:

- No ads.
- No ad SDK.
- No internet permission.
- No account, login, club code, GPS, or analytics.
- Uses Android's built-in step sensor when available.
- Dark charcoal UI.
- Daily reset boundary is 4:00 AM instead of midnight.
- Shows today's steps and a rolling 7-day average.
- Stores step data locally in `SharedPreferences`.

The app is intentionally simple: it shows today's steps, the 7-day average, whether the phone has a step sensor, and a start/stop button for a foreground tracking service. The foreground service keeps Android from casually killing the listener and shows one persistent notification while tracking is enabled.

## Build

```bash
./build.sh
```

The signed debug APK is written to:

```text
build/duststep-debug.apk
```

## Install

Plug in the phone with USB debugging enabled:

```bash
adb install -r build/duststep-debug.apk
```

On Android 10+, grant Physical activity permission when prompted.

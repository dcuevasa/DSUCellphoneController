# CemuHook Phone Controller (Android)

This project keeps the original 3dsInputRedirection UI concept, but the network layer was fully converted into a generic DSU/CemuHook server.

## Current Features

- Runs a DSU server over UDP on port `26760`.
- Handles core DSU messages:
- `0x100000` (protocol version)
- `0x100001` (controller info)
- `0x100002` (data subscription)
- Exposes one virtual controller compatible with DSU clients.
- Sends on-screen input and physical Android gamepad input:
- Digital buttons
- Analog sticks
- Analog trigger pressure bytes
- Touchpad packet data
- Sends phone IMU data through DSU:
- Accelerometer (in g)
- Gyroscope (in deg/s)

## Quick Start

1. Install and open the app on your Android phone.
2. Enable `Run DSU Server`.
3. (Optional) Set `Emulator IP filter` to accept only one client IP.
4. In your emulator, configure DSU/CemuHook using your phone IP and port `26760`.
5. Use the `Control` tab to test inputs.

## UI and Status

- The top status bar shows:
- DSU server state
- Phone local IP
- Active subscriber count
- Optional IP filter
- Port
- Subscriber count refreshes continuously while the activity is visible.

## Motion (IMU) Details

- Accelerometer and gyroscope are sampled from Android sensors.
- Accelerometer values are normalized to g using `SensorManager.GRAVITY_EARTH`.
- Gyroscope values are converted from rad/s to deg/s.
- Motion values use deadzone and clamp filtering to reduce drift/noise.

## Notes About Cemu 2

- DSU connectivity in Cemu 2 can work while some input types are not exposed the same way as in DSU test tools.
- In practice, DSU is most consistently used for motion data in Cemu.
- Touchpad data can appear in DSU test utilities but may not be mapped as a gameplay input in all Cemu versions/profiles.
- If needed, keep gameplay buttons/sticks mapped in Cemu's main input source and use DSU primarily for motion.

## Configuration Notes

- `Left stick %` and `Right stick %` are sensitivity multipliers (`10..200`, default `100`).
- `Power` is mapped as DSU touch button.
- `Power Long` is mapped as guide/home compatibility behavior.
- Use `Invert Y` options if an emulator profile expects opposite axis direction.

## Main Files

- `app/src/main/java/com/example/cemuhookcellphonecontroller/MainActivity.kt`
- `app/src/main/java/com/example/cemuhookcellphonecontroller/InputRedirectionClient.kt`
- `app/src/main/java/com/example/cemuhookcellphonecontroller/InputRedirectionConfig.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout-land/activity_main.xml`

## Build

Windows:

```bat
gradlew.bat :app:assembleDebug
```

Linux/macOS:

```bash
./gradlew :app:assembleDebug
```
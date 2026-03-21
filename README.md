# 3DS Input Redirection (Android)

Android client for Luma3DS Rosalina InputRedirection.

This app turns your phone into a virtual controller and sends input frames over UDP to your Nintendo 3DS.

## What This Project Does

- Sends controller input to a 3DS running Luma3DS InputRedirection.
- Uses UDP on port `4950`.
- Sends full 20-byte frames (`hid_pad`, `touch`, `circle_pad`, `cpp_ir`, `special`).
- Supports on-screen controls and physical gamepad input.

Main control features in the app:

- Left and right virtual sticks.
- D-Pad.
- Face buttons (A/B/X/Y mapped from the UI layout).
- Shoulder buttons (`L` / `R`).
- `Select` and `Start`.
- `Home`, `Power`, `Power Long`.
- Central touchpad mapped to 3DS touchscreen coordinates.

## Requirements

- Nintendo 3DS with Luma3DS and Rosalina InputRedirection available.
- Android device (min SDK 24, Android 7.0+).
- Phone and 3DS on the same local network.

## Quick Start (Use Prebuilt APK)

If you only want to use the app, install the APK from Releases.

- Releases APK: link coming soon (I will add it later).

Steps:

1. Download the latest APK from Releases.
2. Install it on your Android device.
3. Open the app.
4. Go to `Options` and set your 3DS IP address.
5. Return to `Control` and enable `Send UDP`.
6. Use the on-screen controller.

## How To Use With Your 3DS

1. Boot your 3DS with Luma3DS.
2. Ensure InputRedirection is enabled on the 3DS side (Rosalina).
3. Make sure your phone and 3DS are on the same Wi-Fi network.
4. In the app, set the target 3DS IP.
5. Toggle `Send UDP` on.
6. Test buttons, sticks, and touchpad.

## Controls and Options

The app has two views:

- `Control`: full virtual gamepad UI for gameplay.
- `Options`: networking and behavior settings.

Options include:

- 3DS IP address.
- Circle Pad and C-Stick range bounds.
- Invert/swap stick behaviors.
- Optional right-stick remapping (D-Pad, ABXY, Smash mode).
- Button mapping to Android gamepad key codes.

## Import and Modify in Android Studio

### 1. Open the project

1. Open Android Studio.
2. Select `Open`.
3. Choose this folder:

	 `3dsInputRedirection/`

4. Let Gradle sync finish.

### 2. Build and run

1. Connect an Android device (or use an emulator).
2. Select the `app` run configuration.
3. Click `Run`.

Or from terminal:

```bash
./gradlew :app:assembleDebug
```

On Windows:

```bat
gradlew.bat :app:assembleDebug
```

### 3. Generate a release APK

In Android Studio:

1. `Build` -> `Generate Signed Bundle / APK`.
2. Choose `APK`.
3. Use your keystore to sign the release build.

### 4. Where to edit common parts

- Main UI logic: `app/src/main/java/com/example/a3dsinputredirection/MainActivity.kt`
- UDP protocol and frame packing: `app/src/main/java/com/example/a3dsinputredirection/InputRedirectionClient.kt`
- Touchpad view and touch mapping: `app/src/main/java/com/example/a3dsinputredirection/TouchPadView.kt`
- Virtual stick view: `app/src/main/java/com/example/a3dsinputredirection/JoystickView.kt`
- Portrait layout: `app/src/main/res/layout/activity_main.xml`
- Landscape layout: `app/src/main/res/layout-land/activity_main.xml`
- Config model/persistence: `app/src/main/java/com/example/a3dsinputredirection/InputRedirectionConfig.kt`

## Protocol Reference

For protocol details, see:

- `input_redirection_docs.md`

This app uses little-endian 20-byte UDP frames compatible with Rosalina InputRedirection.
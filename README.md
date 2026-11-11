# Vivo / iQOO Aura Light Controller

Android app scaffold for investigating and controlling the Vivo framework light service through Shizuku.

## What works in this version
- Requests Shizuku permission.
- Connects to the `vivo_light_service` Binder service.
- Calls `hasLight()`, `getLightCase()`, and `getLightType()`.
- Sends `stopLightAll()`.

## Important limitation
The start-effect operation is intentionally not implemented yet. The framework `VivoLightRecord` Parcelable and segment parcel formats, supported light types, and device permissions must be verified for the target firmware before sending a start command. This build does **not** claim that arbitrary RGB/effect commands work.

## Build
Push this folder to the root of your GitHub repository. GitHub Actions builds a debug APK on every push to `main`, and also supports manual runs.

Download the APK from the workflow run's **Artifacts** section.

## Install/use
1. Install Shizuku and start its service using the supported wireless debugging method.
2. Install this app.
3. Open the app and tap **Connect Shizuku**; approve the permission prompt.
4. Tap **Check Aura Light**.
5. **Stop all** calls the Vivo framework service's stop-all transaction.

This app requires Android 8.0+ and Shizuku.

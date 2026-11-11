# Vivo / iQOO Aura Light Controller

Controls the back aura light on vivo/iQOO phones (iQOO Z10x, vivo T4x) through vivo's own
`VivoLightManager` framework class, so no Parcel/transaction codes are hard-coded.

## Modes
- **Shell via Shizuku (default):** the call runs as the `shell` user (uid 2000) with package `com.android.shell`.
- **Direct:** untick the box; the call runs as this app. Works only if the service allows normal apps.

## Features
- Check light (`hasLight`, `getLightCase`, `getLightType`), Stop all
- Start by type ID: timed, N repeats, or unlimited; stop by type
- Custom JSON effect (`startLight(pkg, json)`): edit the template once the schema is known
- Scan: tries type IDs in a range, lists the ones the service accepts
- Debug: run shell commands as shell, view `DynamicLight-*` logcat lines

## Build
Push to `main`; GitHub Actions builds the APK. Download it from the run's **Artifacts** section.
The repo includes a fixed debug keystore so updates install over older builds.

## Use
1. Install Shizuku, start it (wireless debugging), install this APK.
2. Tap **Connect Shizuku**, approve.
3. **Check light**. Then **Scan** to find type IDs, then **Start**.
4. If something fails, tap **Service logs** and read the error.

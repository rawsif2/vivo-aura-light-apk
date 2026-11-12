# Vivo / iQOO Aura Light Controller

Control the back aura light on vivo / iQOO phones (iQOO Z10x, vivo T4x) with a simple UI.

## Simple screen
- Effect: Breathing, Blink (strobe) or Chase (marquee)
- Color: drag on the color plane, tap a swatch, or type a hex code (up to 4 colors for Blink/Chase)
- Timing: duration (1-60 s or until stopped), speed, brightness
- Start / Stop, plus a live preview
- My presets: save looks, tap to play, long-press to delete

## Advanced tools (collapsed)
- Shell mode toggle, caller package, **lightName**, red/blue swap, chase subType
- Effect by type ID, scanners for valid type IDs, raw JSON, shell commands, service logs

## Access modes (Advanced tools)
- **Auto (default):** tries to talk to vivo's light service directly as a normal app; falls back to Shizuku only if the service refuses.
- **Direct only:** no Shizuku at all.
- **Shizuku only:** runs as the shell user.
The status pill at the top shows which route is working. Whether direct access is allowed is up to vivo's service, so it may differ per phone/ROM.

## Setup
1. Install this APK and open it. If the pill says "Ready (no Shizuku needed)", skip to step 3.
2. Otherwise install Shizuku, start it (wireless debugging), tap the pill and allow it.
3. Open **Advanced tools** once and fill in `lightName` (it is remembered).
4. Pick a color and tap **Start**.

## Build
Push to `main`; GitHub Actions builds the APK. Download it from the run's **Artifacts**.
The repo ships a fixed debug keystore so new builds install over old ones.

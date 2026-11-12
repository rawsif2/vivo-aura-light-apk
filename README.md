# Aura Light

Control the back aura light on vivo / iQOO phones (tested on iQOO Z10x / vivo T4x).
No root, no Shizuku, no setup: install, open, pick a color, tap Start.

- Effect: Breathing, Blink or Chase
- Color: drag on the color plane, tap a swatch, or type a hex code (up to 4 colors for Blink / Chase)
- Duration (1-60 s or until stopped), speed, brightness
- **Speed 0 = steady light** (no blinking, first color)
- Save your favorite looks as presets

## Device settings
`app/src/main/java/com/tawsif/aura/AuraConfig.kt` holds two values:
- `LIGHT_NAME`: the `lightName` your phone's light service expects
- `SWAP_RED_BLUE`: set to `true` if red and blue come out swapped

## Build
Push to `main`; GitHub Actions builds the APK. Download it from the run's **Artifacts**.

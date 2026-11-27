# A Tools

Android toolbox app built with Kotlin and Jetpack Compose.

## Current features

- **Countdown tab**: Configure repeat count (N), work seconds (M), and rest seconds (X). Each round counts down M seconds, followed by X seconds of rest (except the final round). Voice announcements in Chinese report the last 10 seconds of both work and rest phases, and announce completion of each M-second round. Supports start, pause/resume, and stop/reset.

- **Tools / Settings tabs**: Placeholders for future features.

## Build

```bash
./gradlew assembleDebug
```

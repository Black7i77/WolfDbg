# 🐺 WolfDbg

**WolfDbg v0.5.1** is a dark, OllyDbg-inspired **offline Android static binary inspector** built with Kotlin. It brings a classic debugger-style workspace to a touch screen without claiming to be a live debugger.

## Current features

- Open local files using Android's document picker (up to 32 MiB)
- Inspect hexadecimal bytes, ASCII previews, and file signatures
- Search for hex sequences and printable ASCII strings
- Jump to hexadecimal file offsets
- Enter commands in an editable command bar, with an optional on-screen 0–F keypad
- Four-pane landscape workspace and tabbed portrait workspace
- Registers/stack panels explicitly show that live process information is unavailable

## Not implemented yet

Real instruction disassembly, process attachment, live registers, stack inspection, breakpoints, APK content extraction, and binary editing. The CPU pane currently shows **file bytes, not assembly instructions**.

## Requirements

- Android 8.0+ (API 26+)
- JDK 21 (used for the verified build)
- Gradle 8.13 installed locally (no Gradle wrapper included)
- Android SDK with API 35 and suitable Android Gradle Plugin tooling

## Build (Kali Linux / Linux)

```bash
cd WolfDbg
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$HOME/gradle-8.13/bin:$PATH"
gradle clean :app:assembleDebug --no-daemon
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk` after a successful build.

## Install and launch

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.scarface.reverse 1
```

The application ID is currently `com.scarface.reverse` for compatibility with earlier Scarface Reverse Studio installations. Changing it would install WolfDbg as a separate app.

## Privacy & permissions

No Internet permission, no process attachment, no file modification. Inspect only files you have the right to examine.

## Project status

This is an early, functional **static inspection** release. A successful local Gradle build and install were reported on October 11, 2026; public CI and release APK validation are not yet configured.

## Licensing

MIT — see [LICENSE](LICENSE).

# WolfDbg v0.5.0 — Android static binary inspector

A dark, OllyDbg-inspired **read-only, offline** binary inspection app. It has a four-pane workspace in landscape and a tabbed single-pane workspace in portrait.

## Features
- Open local files using Android's document picker, maximum 32 MiB
- View hexadecimal bytes, ASCII previews, and file signatures
- Find hexadecimal sequences and printable ASCII strings
- Navigate to hexadecimal file offsets
- Working editable command bar and optional on-screen 0–F keypad
- Registers and stack panes clearly indicate there is no live process

**Not yet implemented:** instruction disassembly, live registers, stack inspection, APK contents extraction, process attachment, debugging/breakpoints. The CPU pane displays *file bytes*, not assembly instructions.

## Build from Kali Linux

```bash
cd WolfDbg
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$HOME/gradle-8.13/bin:$PATH"
gradle clean :app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.scarface.reverse.v4 1
```

Keep the application ID `com.scarface.reverse.v4` to update the previously built v0.4.1 package rather than install a third copy. An older `com.scarface.reverse` package may also be installed on your phone; this package is different.

## Privacy
No Internet permission, no binary modification, no process attachment. Only inspect files you have permission to examine.

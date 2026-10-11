# WolfDbg v0.6.0 — Source release

Keeps the v0.5.1 UI layout, controls, colours and wolf icon.

## Added
- `format` / `headers` / `elf` / `pe` / `dex` / `apk`: offline file-format report.
- `arm64` / `disasm`: partial AArch64 instruction preview, not a full disassembler.
- APK central-directory listing with manifest, DEX, and native library counts (no extraction).
- File loading cap raised to 128 MiB.
- Android versionCode 8, versionName 0.6.0.

## Limitations
- No live debugging or register inspection.
- The ARM64 preview is partial and treats chosen file offsets as raw instruction bytes; on APK input these are ZIP bytes, not automatically the app's native executable code.
- PE/ELF/DEX headers are metadata inspection only.
- Loading a file uses RAM; large files may be slow on limited devices.
- No APK signature verification, bytecode decompilation, or x86 decoder.

## Build
Use JDK 17 or 21, Android SDK platform 35, Android Gradle Plugin and Kotlin plugin per `build.gradle.kts`.
Run `gradle :app:assembleDebug` from the project root.
APK generated under `app/build/outputs/apk/debug/`.
For seamless upgrade of existing release APK use your ORIGINAL signing key; never publish your keystore or passwords.

Verification: Python static consistency checks completed; no Android SDK build was performed in this environment.

# CleanDroid

An SD Maid–style, **100 % offline** Android maintenance app: bloatware detector, antivirus scanner,
storage cleaner and a permanent file shredder. Written in Kotlin, classic Views (no Compose),
deliberately tiny dependency set for low-end devices.

> **Audience**: Android 10 → 17 (API 29+, forward-compatible).
> **Security posture**: the app has **no INTERNET permission** — it physically cannot send any data anywhere.

---

## Tools

| Tool | What it does | Needs |
|---|---|---|
| **Bloatware** | Matches installed packages against a bundled offline DB of known ads frameworks / telemetry / OEM & carrier add-ons (MIUI ads, Samsung Bixby/Spage, FinShell, …). One-tap *Uninstall* (system dialog) for user apps, *App info → Disable* guidance for pure system apps. | Nothing special. Usage Access is optional (only for per-app sizes). |
| **Antivirus** | Two offline layers: (1) SHA-256 of every installed APK and of files under `Downloads/` compared to the bundled signature list; (2) risky **permission-combination** heuristics (SMS + overlay, Accessibility + SMS/installer, device-admin + Accessibility, call log + SMS, …). | Nothing special. |
| **Cleaner** | Finds well-known junk in shared storage: `DCIM/.thumbnails`, stray `.apk` files, small `.log/.tmp/.bak` files, empty folders (deleted children-first), 90-day-old files in `Downloads` (opt-in), its own caches. The `Android/` tree is **never touched**. | All-Files-Access (11+) / storage permission (10). |
| **Shredder** | Permanent deletion: 3 overwrite passes (0x00 → 0xFF → CSPRNG) with `fsync` after each pass, then rename-to-random + unlink. Plus a *wipe free space* mode (fills free space with random data, then deletes the filler). | All-Files-Access (11+) / storage permission (10). |
| **RAM boost** (Home) | Asks the system to stop background processes of user apps via the public `killBackgroundProcesses()` API — no root, no fake "1 GB freed" magic. | `KILL_BACKGROUND_PROCESSES` (normal). |

## Build

1. Install **Android Studio** (Ladybug/Meerkat or newer) with **JDK 17** (bundled).
2. *File → Open* → select this folder. Let Gradle sync (wrapper config points to Gradle 8.13; AGP 8.10.1, Kotlin 2.1.20).
3. Run configuration → device/emulator on API 29–36 → **Run**.
4. Release APK: `Build → Generate Signed Bundle / APK`, or:
   ```bash
   ./gradlew assembleRelease     # R8 + resource shrinking enabled
   ```
   (If the `gradlew` wrapper script is not executable/present in your checkout, run `gradle wrapper --gradle-version 8.13` once, or build from Android Studio.)

### Android version notes (10 → 17)

| Android | API | Notes |
|---|---|---|
| 10 | 29 | Legacy storage via `requestLegacyExternalStorage` + runtime `WRITE_EXTERNAL_STORAGE` |
| 11–12 | 30–32 | All-Files-Access via Settings (`MANAGE_EXTERNAL_STORAGE`) |
| 13 | 33 | Runtime `POST_NOTIFICATIONS`; `PackageInfoFlags` API used |
| 14 | 34 | Foreground service started with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` |
| 15 | 35 | Edge-to-edge enforced — handled with `WindowCompat` + insets |
| 16 | 36 | compileSdk/targetSdk 36 |
| 17 | 37 | No action required; the app uses only stable public APIs and will run unchanged. Bump `compileSdk` when API 37 ships in SDK Manager. |

## Permissions — full matrix

| Permission | Type | Why | Granted via |
|---|---|---|---|
| `QUERY_ALL_PACKAGES` | normal | Bloatware/Antivirus need the installed-app list | automatic |
| `KILL_BACKGROUND_PROCESSES` | normal | RAM boost | automatic |
| `REQUEST_DELETE_PACKAGES` | normal | Shows the system uninstall dialog | automatic |
| `PACKAGE_USAGE_STATS` | special (App Op) | Per-app sizes | Settings → Usage access |
| `MANAGE_EXTERNAL_STORAGE` | special (11+) | Cleaner/Shredder file operations | Settings → All files access |
| `READ/WRITE_EXTERNAL_STORAGE` | runtime (10 only, `maxSdkVersion` 29/32) | Same, legacy path | runtime dialog |
| `POST_NOTIFICATIONS` | runtime (13+) | Progress notifications for long ops | runtime dialog |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | normal | Keeps long scans alive | automatic |

**Not requested**: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`, `LOCATION`, `CONTACTS`, `SMS`,
`RECORD_AUDIO`, `READ_CALL_LOG`, `ACCESSIBILITY` — the app never asks for any of them.

## Low-end device design decisions

- **5 runtime libraries** (core-ktx, appcompat, material, recyclerview/constraintlayout, coroutines).
  No DI framework, no image loader, no network stack, no JSON library (platform `org.json`).
- Classic XML views + ViewBinding — cheaper cold start and lower memory than Compose on 2 GB devices.
- One shared operation slot: every long task runs on a single `Dispatchers.Default`/`IO` coroutine,
  yields every ~200 files, is cancellable, and is **cancelled before a new one starts**
  (no orphaned child processes/threads). The foreground service stops itself when the slot is idle.
- R8 full shrinking + resource shrinking + English-only resources (`resourceConfigurations = ["en"]`).
- No wake locks, no polling, no background receivers; the Home screen refreshes only while visible.

## Honest limitations (also shown in the app)

- The shredder is as permanent as Android allows. On F2FS/ext4 with journaling and flash wear
  leveling, *no* userspace tool can guarantee physical NAND erasure.
- The antivirus layer is a **review aid**: offline signature list (seeded with the verifiable EICAR
  test hash) + permission-combo heuristics. It is not a full commercial AV engine. See
  `SECURITY.md` for how to import a bigger hash list.
- "Removal" of pure system apps = Android's built-in **Disable** (the OS does not allow more
  without root/Shizuku; those integrations were deliberately out of scope).

## License

MIT — see `LICENSE`.

# V Client

A modular utility client **for** Minecraft Bedrock Edition on Android — built like modern
clients such as Flarial or Atlas, but with a strict rule: **it never modifies, repackages
or re-signs the installed game.**

V Client is a controlled, isolated environment that lives *around* Minecraft:

```
┌──────────────────────────────────────────────────────────┐
│                    V Client (this app)                   │
│                                                          │
│  Launcher UI ── ModuleManager ── Profiles ── V Menu      │
│        │                 │                              │
│  SignatureManager   Native runtime (C++20)               │
│  Version registry   FpsStats · WaypointMath · CrashGuard │
│        │                                                │
│        ▼            Overlay engine (SYSTEM_ALERT_WINDOW) │
│   Launch stock  ──► HUD · HUD editor · effects           │
│   Minecraft          drawn above the untouched game      │
└──────────────────────────────────────────────────────────┘
```

1. **Verify** — V Client detects the installed Minecraft, resolves its version against a
   compatibility registry, and verifies the APK's signing certificate (trust-on-first-use).
2. **Configure** — modules, settings and HUD layout are stored in isolated profiles.
3. **Launch** — the stock game starts via its own launch intent while V Client's overlay
   session renders the HUD, V Menu and effects *above* it.

> **Not affiliated with Mojang or Microsoft.** V Client is an independent overlay utility.
> Use it responsibly and follow the rules of the servers you play on.

---

## Highlights

- **Launcher** — Kotlin + Jetpack Compose (Material 3, dark-first design): status cards for
  the game/version/signature, permission onboarding, one-tap launch.
- **V Menu** — a modern in-game GUI (also Compose, hosted in an overlay window): modules,
  HUD, waypoints, touch mapping, profiles, live settings — everything configurable mid-game.
- **14 modules out of the box** — see the table below. Every module supports enable/disable
  and configurable settings; every HUD element is draggable and scalable in the **HUD editor**.
- **Module system** — `Module` / `HudModule` base classes, reactive settings, per-module
  crash guard (a failing module is auto-disabled, never kills the overlay).
- **Config/Profiles** — JSON profiles with atomic writes, debounced auto-save, export/import,
  forward-compatible format. Switch profiles live from V Menu.
- **Version compatibility** — segment-aware version parsing (`1.21.100 > 1.21.9`), a bundled
  validation registry in assets, user-importable overrides, sane defaults for unknown versions.
- **Signature manager** — SHA-256 pinning of Minecraft's signing certificate with tamper
  warnings.
- **Native runtime (C++20 + NDK)** — frame statistics, waypoint projection math, the
  motion-blur model, an async-signal-safe crash tombstone writer, and the future bridge ABI.
  Graceful Kotlin fallbacks if the library is absent.
- **Isolation** — every byte V Client writes lives under one sandboxed root:
  `<app files>/vclient/` (profiles, logs, crashes, cache, compat, signatures).
  Nothing is ever written into Minecraft's storage.
- **Logging & crash protection** — file logging with rotation, uncaught-exception reports,
  native tombstones, crash-loop detection → **Safe Mode**.

## Modules

| Module | Category | What it does |
|---|---|---|
| FPS Counter | HUD | Real FPS, frame time, 1% lows (native FpsStats) |
| Coordinates | HUD | X/Y/Z with decimals, dimension, facing |
| Armor HUD | HUD | Equipped armor with durability bars |
| Inventory HUD | HUD | Configurable item grid (columns, tile size, counts) |
| Potion HUD | HUD | Active effects with remaining time |
| Keystrokes | HUD | WASD/Jump/Sneak/Sprint — real input via touch-region mapping (Android 14+) |
| Clock | HUD | 12/24h clock with seconds, date, prefix |
| CPS Counter | HUD | Left/right/both clicks per second + peak |
| Performance HUD | HUD | CPU (/proc/stat), RAM, battery, frame stats |
| Crosshair | Visual | 5 styles, gap/thickness/size/color/outline/opacity |
| Zoom | Visual | Camera magnification via accessibility controller, floating button |
| Fullbright | Visual | Screen light filter (no game files touched) |
| Motion Blur | Visual | Gyro-driven cross-window blur (Android 12+) |
| Waypoints | Utility | Distance + bearing arrows, per-profile waypoint store |

## Building

Requirements: **Android Studio** (Ladybug or newer) with **SDK 35** and **NDK 27** +
**CMake 3.22.1** installed via the SDK manager.

```bash
# from the repository root
./gradlew :app:assembleDebug        # debug APK  (app/build/outputs/apk/debug/)
./gradlew :app:assembleRelease      # release APK
./gradlew :app:testDebugUnitTest    # JVM unit tests
```

> The wrapper *properties* are committed; if your clone lacks the wrapper jar
> (`gradle/wrapper/gradle-wrapper.jar`), either open the project in Android Studio
> (it will sync with its bundled Gradle 8.9+) or run `gradle wrapper --gradle-version 8.9`
> once with a local Gradle install.

## Permissions & privacy

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the HUD / V Menu above the game |
| `FOREGROUND_SERVICE` (+`specialUse`) | Keep the overlay session alive with a visible notification |
| `POST_NOTIFICATIONS` | The session notification with quick actions |
| Accessibility service (**optional**) | Zoom (magnification), game-focus detection, and Android 14+ touch motion for Keystrokes/CPS. It declares `canRetrieveWindowContent="false"` — it never reads screen content |

V Client requests nothing else, contains no ads/trackers/network code, and writes only to
its own sandbox. See [docs/DATA_ISOLATION.md](docs/DATA_ISOLATION.md).

## Project structure

```
app/src/main/
├── cpp/                        Native runtime (C++20): Runtime, Logger,
│   └── vclient/                CrashHandler, FpsStats, WaypointMath,
│                               MotionBlur, Bridge ABI + JNI surface
├── assets/compat/              Bundled version compatibility registry
├── java/dev/vclient/
│   ├── core/                   Engine layer (no UI)
│   │   ├── module/             Module & HudModule bases, ModuleManager, error guard
│   │   ├── hud/                HudElement, anchors, shared paint helpers
│   │   ├── settings/           Reactive setting types (bool/int/float/color/mode/text)
│   │   ├── config/             Profiles, DTOs, sync, auto-save
│   │   ├── version/            Version parsing + compatibility registry
│   │   ├── signature/          APK signature trust & verification
│   │   ├── isolation/          IsolatedPaths — one root for all data
│   │   ├── logging/            VLogger + CrashReporter (safe mode)
│   │   ├── runtime/            NativeBridge (JNI), ClientHooks, FrameStats
│   │   ├── game/               GameSession, data sources (manual/demo/bridge), launcher
│   │   └── VClientCore.kt      Composition root
│   ├── modules/                The 14 modules + ModuleRegistry
│   ├── overlay/                HUD surface, editor, bubble, effect windows,
│   │   ├── accessibility/      VAccessibilityService (zoom/focus/motion)
│   │   └── vmenu/              In-game V Menu (Compose in an overlay window)
│   ├── ui/                     Launcher Compose UI + shared setting widgets
│   ├── MainActivity.kt
│   └── VClientApp.kt
└── AndroidManifest.xml
```

Detailed docs:

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — layering, data flow, the bridge ABI, threading
- [docs/EXTENDING.md](docs/EXTENDING.md) — adding modules, settings, HUD elements, versions
- [docs/DATA_ISOLATION.md](docs/DATA_ISOLATION.md) — the isolation contract, file-by-file

## Roadmap

- Live game data through the **V Bridge** transport (ABI already stubbed on both sides)
- More HUD elements & keybinds, per-module profiles binding
- On-device HUD editor preview without launching the game
- Localization

## License / trademark

"V Client" is a fan project. Minecraft is a trademark of Mojang Synergies AB.
This repository contains no Mojang assets.
